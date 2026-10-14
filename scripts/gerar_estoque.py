#!/usr/bin/env python3
"""Gera estoque.json para o app Remédio Falado.

Junta o estoque diário das farmácias da Rede Municipal de Saúde do Recife (8 arquivos, um por
distrito sanitário) com o endereço, o telefone, o horário e a localização de cada unidade
(dados/rede.csv, "Rede de atenção à saúde no Recife").

Uso:  python3 scripts/gerar_estoque.py brutos/medicamentos_distrito0*.csv
Saída: estoque.json (mesmo formato do postos.json do app) e pendentes.txt (unidades sem par).

Dados: Prefeitura do Recife, Portal de Dados Abertos, licença ODbL.
"""
import csv
import datetime
import difflib
import io
import json
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent

# os 10 remédios de pressão e diabetes que o app acompanha (chaves iguais às do app)
PRINCIPIOS = {
    'losartana': r'\bLOSARTANA',
    'captopril': r'\bCAPTOPRIL',
    'enalapril': r'\bENALAPRIL',
    'hidroclorotiazida': r'\bHIDROCLOROTIAZIDA',
    'atenolol': r'\bATENOLOL',
    'propranolol': r'\bPROPRANOLOL',
    'anlodipino': r'\bANLODIPINO',
    'espironolactona': r'\bESPIRONOLACTONA',
    'metformina': r'\bMETFORMINA',
    'glibenclamida': r'\bGLIBENCLAMIDA',
}

# unidades que não entregam remédio ao público (depósitos, laboratório, SAMU...)
FORA = re.compile(r'DISTRITO SANITARIO|SAMU|LABORATORIO|VIGILANCIA|SECRETARIA|JABOATAO|IPOJUCA|'
                  r'REIDRATACAO|ALMOXARIFADO|\bCAPS\b|ALERGOLOGIA|\bSAE\b|^(US\s*[\d.]+\s*-?\s*)?(HOSPITAL|MATERNIDADE)', re.I)

# tipos da rede que atendem o público
TIPOS = ('2.1|', '2.2|', '2.5|', '3|', '4.1|', '6.1|', '6.2|', '6.3', '6.4|', '7|', '9.1')


def sem_acento(s):
    return unicodedata.normalize('NFD', s or '').encode('ascii', 'ignore').decode()


def norm(s):
    return ' '.join(re.sub(r'[^a-z0-9 ]', ' ', sem_acento(s).lower()).split())


PALAVRAS = r'\b(usf|psf|cs|us|ubt|upinha|24h|dr|dra|prof|profa|professor|de|da|do|dos|das|e|unidade|saude|familia|recife)\b'


def chave(nome):
    s = re.sub(r'^us\s*[\d.]+\s*', '', norm(nome))
    return ' '.join(re.sub(PALAVRAS, ' ', s).split())


def ler_csv(caminho):
    """Lê os CSV da Prefeitura (vírgula ou ponto e vírgula, UTF-8 ou Latin-1)."""
    bruto = Path(caminho).read_bytes()
    for enc in ('utf-8-sig', 'latin-1'):
        try:
            texto = bruto.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    primeira = texto.split('\n', 1)[0]
    sep = ';' if primeira.count(';') > primeira.count(',') else ','
    return list(csv.DictReader(io.StringIO(texto), delimiter=sep))


def numero(v):
    v = str(v or '').strip().replace('.', '').replace(',', '.') if ',' in str(v) else str(v or '').strip()
    try:
        return float(v)
    except ValueError:
        return 0.0


def principio(produto):
    p = sem_acento(produto).upper()
    if 'INJ' in p:
        return None
    for k, padrao in PRINCIPIOS.items():
        if re.search(padrao, p):
            return k
    return None


def carregar_rede():
    rede = {}
    for r in ler_csv(RAIZ / 'dados' / 'rede.csv'):
        if not r['tipo_unidade'].startswith(TIPOS):
            continue
        nome = r['nome_oficial'].strip()
        atual = rede.get(nome)
        if atual is None or (not atual['latitude'] and r['latitude']):
            rede[nome] = r
    todas = {r['nome_oficial'].strip(): r for r in ler_csv(RAIZ / 'dados' / 'rede.csv')}
    return rede, todas


def achar(unidade, distrito, rede):
    cand = {n: r for n, r in rede.items() if r['distrito_sanitario'].strip() == str(distrito).strip()}
    chaves = {chave(n): n for n in cand}
    k = chave(unidade)
    if k in chaves:
        return chaves[k], 'exato'
    m = difflib.get_close_matches(k, list(chaves), 1, 0.72)
    return (chaves[m[0]], 'aproximado') if m else (None, None)


def main(arquivos):
    rede, todas = carregar_rede()
    mapa = json.loads((RAIZ / 'dados' / 'mapa_unidades.json').read_text(encoding='utf-8'))
    estoque = defaultdict(lambda: {'distrito': '', 'tem': set(), 'catalogo': set()})
    linhas = 0
    for arq in arquivos:
        for r in ler_csv(arq):
            linhas += 1
            u = (r.get('unidade') or '').strip()
            k = principio(r.get('produto', ''))
            if not u or not k:
                continue
            e = estoque[u]
            e['distrito'] = r.get('distrito', '')
            e['catalogo'].add(k)
            if numero(r.get('quantidade')) > 0:
                e['tem'].add(k)
    if linhas == 0:
        sys.exit('Nenhuma linha lida: o download falhou? estoque.json não foi alterado.')

    postos, pendentes = {}, []
    for u, e in sorted(estoque.items()):
        if FORA.search(sem_acento(u)):
            continue
        if u in mapa:
            nome = mapa[u]
            if nome is None:
                continue
            como = 'mapa'
        else:
            nome, como = achar(u, e['distrito'], rede)
            if como != 'exato':
                pendentes.append(f'{u} (distrito {e["distrito"]}) -> sugestão: {nome or "nenhuma"}')
                continue
        r = rede.get(nome) or todas.get(nome)
        if not r:
            pendentes.append(f'{u}: "{nome}" não está em dados/rede.csv')
            continue
        p = postos.setdefault(nome, {
            'nome': nome,
            'bairro': r['bairro'].strip(),
            'rua': r['endereço'].strip(),
            'fone': r['fone'].strip(),
            'horario': r['horario'].strip(),
            'lat': float(r['latitude']) if r['latitude'] else None,
            'lon': float(r['longitude']) if r['longitude'] else None,
            'tem': set(),
        })
        p['tem'] |= e['tem']
        if 'FARMACIA DA FAMILIA' in sem_acento(u).upper():
            p['ff'] = True

    minimo = int(__import__('os').environ.get('MINIMO_POSTOS', '60'))
    if len(postos) < minimo:
        sys.exit(f'Só {len(postos)} postos (mínimo {minimo}): arquivo incompleto? estoque.json não foi alterado.')
    hoje = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=-3))).date().isoformat()
    saida = {
        'fonte': 'Prefeitura do Recife, Portal de Dados Abertos: "Estoque dos medicamentos nas farmácias '
                 'da Rede Municipal de Saúde" e "Rede de atenção à saúde no Recife". Licença ODbL.',
        'data_estoque': hoje,
        'data_tipo': 'download',
        'postos': [dict(p, tem=sorted(p['tem'])) for _, p in sorted(postos.items())],
    }
    (RAIZ / 'estoque.json').write_text(json.dumps(saida, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    (RAIZ / 'pendentes.txt').write_text(
        (f'Unidades do estoque sem par na rede ({hoje}). Para incluir, adicione em dados/mapa_unidades.json:\n'
         + '\n'.join(pendentes) + '\n') if pendentes else f'Nenhuma pendência ({hoje}).\n', encoding='utf-8')
    print(f'{linhas} linhas lidas; {len(saida["postos"])} postos; {len(pendentes)} pendentes')


if __name__ == '__main__':
    main(sys.argv[1:])
