#!/usr/bin/env python3
"""Baixa os 8 arquivos de estoque (um por distrito sanitário) do Portal de Dados Abertos do Recife.

Pergunta ao portal (API do CKAN) onde estão os arquivos de hoje, em vez de usar endereços fixos:
quando a Prefeitura troca o arquivo, o endereço pode mudar. Se a API não responder, tenta os
endereços conhecidos. Tem um prazo total (PRAZO_MIN, padrão 20 minutos) para nunca estourar o
tempo da tarefa do GitHub, e diz no registro o que deu errado em cada tentativa.

Uso:  python3 scripts/baixar.py brutos
Saída: brutos/medicamentos_distrito0N.csv, brutos/data.txt (data do arquivo mais antigo, pela Prefeitura)
       e, no GitHub Actions, falhas=<n> em $GITHUB_OUTPUT.
"""
import datetime
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

PORTAL = os.environ.get('PORTAL', 'https://dados.recife.pe.gov.br')
CONJUNTO = 'c68396ad-ad0f-4255-a57a-84c97054dd37'  # Estoque dos medicamentos nas farmácias da Rede Municipal de Saúde
CONHECIDOS = {  # endereços de outubro/2026, usados só se a API não responder
    1: '537f0b95-4eb7-4912-9d7c-32caf9fd68ac', 2: '30e87813-b5a3-4cbd-b35e-b09156f52698',
    3: '832ae42d-0163-45e8-82c1-a4e00a5ec1b6', 4: 'a4d419a8-4355-4ae9-b116-9cc35b914f50',
    5: '77040c32-2a39-4416-ba93-5c7386b46dcb', 6: 'ac869f94-a655-4388-871b-9957b0a642a4',
    7: '97109f18-a189-4084-acaf-b3aff6e65d51', 8: '2e411ebc-0bef-4e03-ae34-2b792b5468c2',
}
AGENTE = 'Mozilla/5.0 (compatible; BULA-app/1.0; dados abertos; +https://github.com/gaoc-sue/remedio-falado-dados)'
RECIFE = datetime.timezone(datetime.timedelta(hours=-3))
INICIO = time.monotonic()
PRAZO = float(os.environ.get('PRAZO_MIN', '20')) * 60


def resta():
    return PRAZO - (time.monotonic() - INICIO)


def pedir(url, limite):
    req = urllib.request.Request(url, headers={'User-Agent': AGENTE, 'Accept': '*/*'})
    with urllib.request.urlopen(req, timeout=limite) as r:
        return r.read()


def tentar(oque, url, tentativas=3, limite=90, pausa=20):
    """Até 3 tentativas, sem passar do prazo total. Devolve os bytes ou None."""
    for n in range(1, tentativas + 1):
        if resta() < 15:
            print(f'::warning::{oque}: acabou o prazo total, desisti')
            return None
        try:
            t = time.monotonic()
            dados = pedir(url, min(limite, max(10, resta() - 5)))
            print(f'{oque}: ok na tentativa {n} ({len(dados)} bytes, {time.monotonic() - t:.0f} s)')
            return dados
        except urllib.error.HTTPError as e:
            print(f'{oque}: tentativa {n}: o portal respondeu HTTP {e.code}')
            if e.code in (403, 404, 410):
                return None  # endereço errado ou bloqueado: repetir não adianta
        except Exception as e:
            print(f'{oque}: tentativa {n}: sem resposta ({type(e).__name__}: {e})')
        if n < tentativas:
            time.sleep(min(pausa, max(0, resta() - 15)))
    return None


def distrito_de(rec):
    texto = f"{rec.get('name', '')} {rec.get('url', '')}"
    m = re.search(r'distrito[\s_-]*0?(\d)', texto, re.I)
    return int(m.group(1)) if m else None


def data_de(rec):
    for campo in ('last_modified', 'metadata_modified', 'created'):
        v = rec.get(campo)
        if v:
            try:
                d = datetime.datetime.fromisoformat(v.replace('Z', '+00:00'))
                if d.tzinfo is None:
                    d = d.replace(tzinfo=datetime.timezone.utc)  # o CKAN grava em UTC
                return d.astimezone(RECIFE).date()
            except ValueError:
                pass
    return None


def parece_csv(dados):
    cab = dados[:2000].decode('latin-1', 'ignore').lower()
    return '<html' not in cab and ('unidade' in cab or 'produto' in cab)


def main(pasta):
    pasta = Path(pasta)
    pasta.mkdir(parents=True, exist_ok=True)
    alvos = {}  # distrito -> (url, data)
    api = tentar('lista de arquivos (API do portal)', f'{PORTAL}/api/3/action/package_show?id={CONJUNTO}',
                 limite=60)
    if api:
        try:
            for rec in json.loads(api)['result']['resources']:
                d = distrito_de(rec)
                if d and 1 <= d <= 8 and rec.get('url'):
                    alvos[d] = (rec['url'], data_de(rec))
        except Exception as e:
            print(f'::warning::a API respondeu algo inesperado: {e}')
    for d, rid in CONHECIDOS.items():
        alvos.setdefault(d, (f'{PORTAL}/dataset/{CONJUNTO}/resource/{rid}/download/medicamentos_distrito0{d}.csv', None))

    falhas, datas = 0, []
    for d in sorted(alvos):
        url, data = alvos[d]
        print(f'distrito {d}: {url} (arquivo de {data or "data não informada"})')
        dados = tentar(f'distrito {d}', url)
        if dados and parece_csv(dados):
            (pasta / f'medicamentos_distrito0{d}.csv').write_bytes(dados)
            if data:
                datas.append(data)
        else:
            if dados:
                print(f'distrito {d}: o portal mandou uma página, não a planilha')
            print(f'::warning::distrito {d}: não consegui baixar')
            falhas += 1

    if datas and falhas == 0:
        (pasta / 'data.txt').write_text(min(datas).isoformat())
        print(f'data do estoque pela Prefeitura: {min(datas).isoformat()}')
    print(f'falhas: {falhas}; tempo total {time.monotonic() - INICIO:.0f} s')
    saida = os.environ.get('GITHUB_OUTPUT')
    if saida:
        with open(saida, 'a') as f:
            f.write(f'falhas={falhas}\n')


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'brutos')
