#!/usr/bin/env python3
"""Monta www/bulas.json (bulas que já vêm dentro do app) a partir das bulas baixadas pelo buladiff.

Uso:  python3 scripts/bulas/montar_bulas.py <pasta data/ do buladiff> [registros...]
Sem registros, usa scripts/bulas/lista.txt e mantém as bulas que já estão em www/bulas.json.

Limpeza (regra do projeto: o app não fala o nome do fabricante):
  - corta a partir de "III. DIZERES LEGAIS" (registro, endereço e SAC do fabricante);
  - tira parágrafos com o nome do fabricante (razão social do registro) e códigos de versão do PDF.
"""
import json
import re
import sys
import unicodedata
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
SAIDA = RAIZ / 'www' / 'bulas.json'
LISTA = Path(__file__).with_name('lista.txt')
GENERICAS = {'ltda', 's/a', 'sa', 'eireli', 'farmaceutica', 'farmaceutico', 'farmaceuticos', 'industria', 'industrias',
             'comercio', 'laboratorio', 'laboratorios', 'quimica', 'quimico', 'nacional', 'brasil', 'brasileira', 'produtos',
             'medicamentos', 'medicamento', 'distribuidora', 'importacao', 'exportacao', 'limitada', 'saude', 'pharma',
             'farma', 'company', 'companhia', 'grupo', 'divisao', 'filial', 'unidade', 'especialidades', 'genericos', 'e',
             'de', 'do', 'da', 'dos', 'das'}


def sem_acento(s):
    return unicodedata.normalize('NFD', s or '').encode('ascii', 'ignore').decode()


def nomes_fabricante(meta):
    rz = sem_acento((meta.get('produto') or {}).get('razaoSocial', '')).lower()
    return [w for w in re.findall(r"[a-z][a-z'&-]{3,}", rz) if w not in GENERICAS]


def limpar(texto, fabricante):
    texto = re.split(r'\bIII\.?\s*DIZERES LEGAIS\b', texto or '', flags=re.I)[0]
    paragrafos = []
    for p in re.split(r'\n\s*\n', texto):
        q = sem_acento(p).lower()
        if re.search(r'_vps?(_|\b)|\bvps?_?v?\d{2}\b', q):
            continue
        if any(re.search(r'\b' + re.escape(w) + r'\b', q) for w in fabricante):
            continue
        if re.search(r'^\s*((produzido|embalado|importado|registrado|fabricado|distribuido|comercializado)( e [a-z]+)? por\b'
                     r'|cnpj\b|sac\b|farm\. resp|responsavel tecnic|crf[- ]|registro:? ?[\dm]\.)', q):
            continue
        paragrafos.append(p.strip())
    return '\n\n'.join(x for x in paragrafos if x).strip()


def montar(pasta, registro):
    d = Path(pasta) / registro
    vps = sorted(d.glob('*_vp.json'))
    if not vps:
        return None
    v = json.loads(vps[-1].read_text(encoding='utf-8'))
    meta = json.loads((d / 'meta.json').read_text(encoding='utf-8')) if (d / 'meta.json').exists() else {}
    fab = nomes_fabricante(meta)
    doc = next((x for x in v.get('documentos', []) if x.get('tipo') == 'vp'), (v.get('documentos') or [{}])[0])
    sec = {k: limpar(doc.get('secoes', {}).get(k, ''), fab) for k in map(str, range(1, 10))}
    sec = {k: t for k, t in sec.items() if t}
    if len(sec) < 6:
        return None
    return {'registro': registro,
            'produto': (meta.get('produto') or {}).get('nomeProduto', ''),
            'principio': (meta.get('detalhe') or {}).get('principioAtivo', ''),
            'data': v.get('data', ''),
            'fonte': 'Bula do paciente oficial (Bulário Eletrônico da Anvisa)',
            'secoes': sec}


def main(args):
    pasta, regs = args[0], args[1:]
    atual = json.loads(SAIDA.read_text(encoding='utf-8')) if SAIDA.exists() else {}
    if not regs:
        regs = [ln.split('#')[0].strip() for ln in LISTA.read_text(encoding='utf-8').splitlines()]
        regs = [r for r in regs if r] + list(atual)
    novas, faltaram = 0, []
    for r in dict.fromkeys(regs):
        b = montar(pasta, r)
        if not b:
            if r not in atual:
                faltaram.append(r)
            continue
        if r not in atual or b['data'] >= atual[r].get('data', ''):
            novas += r not in atual
            atual[r] = b
    SAIDA.write_text(json.dumps(atual, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    print(f'{len(atual)} bulas no app ({novas} novas); {SAIDA.stat().st_size // 1024} KB')
    if faltaram:
        print('sem bula baixada:', ' '.join(faltaram))


if __name__ == '__main__':
    main(sys.argv[1:])
