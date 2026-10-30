"""Monta www/bulas.json com bulas do paciente oficiais já extraídas, para funcionar sem internet.

Fonte do texto: arquivo do projeto buladiff (MIT, https://github.com/vasfvitor/buladiff), que baixa as
bulas do Bulário Eletrônico da Anvisa e separa as seções. Aqui ficam só as perguntas 1 a 9 da bula do
paciente. Saem a identificação, os dizeres legais e qualquer linha com dados do fabricante.

Uso:
  python3 -I scripts/bulas_seed.py <pasta data do buladiff> www/base.json www/bulas.json
"""
import glob, json, os, re, sys

PERGUNTAS = {
    '1': 'Para que este medicamento é indicado?',
    '2': 'Como este medicamento funciona?',
    '3': 'Quando não devo usar este medicamento?',
    '4': 'O que devo saber antes de usar este medicamento?',
    '5': 'Onde, como e por quanto tempo posso guardar este medicamento?',
    '6': 'Como devo usar este medicamento?',
    '7': 'O que devo fazer quando eu me esquecer de usar este medicamento?',
    '8': 'Quais os males que este medicamento pode me causar?',
    '9': 'O que fazer se alguém usar uma quantidade maior do que a indicada deste medicamento?',
}
LIXO = re.compile(r'(_VPS?\b|VPS?_V\d|_V\d{1,3}\b|^V\d{2,3}$|CNPJ|\bSAC\b|Ltda|\bS\.?/?A\.?\b|Farm\.?\s*Resp|Respons[áa]vel\s+t[ée]cnic|'
                  r'Registrado\s+(e\s+produzido\s+)?por|Fabricado\s+por|Embalado\s+por|Produzido\s+por|Importado\s+por|'
                  r'www\.|@|Ind[úu]stria\s+Brasileira|^\s*\d+\s*(de|/)\s*\d+\s*$|^\s*\d{1,3}\s*$)', re.I)


def limpar(txt, empresa_rx):
    linhas = []
    for l in txt.split('\n'):
        t = l.strip()
        if not t:
            linhas.append('')
            continue
        if LIXO.search(t) or (empresa_rx and empresa_rx.search(t)):
            continue
        linhas.append(t)
    out = re.sub(r'\n{3,}', '\n\n', '\n'.join(linhas)).strip()
    return out


def main(dados, base_json, saida):
    base = json.load(open(base_json, encoding='utf-8'))
    regs_cmed = {str(x[4]).replace('.', '')[:9] for x in base['itens']}
    seed = {}
    for pasta in sorted(glob.glob(os.path.join(dados, '*'))):
        reg = os.path.basename(pasta)
        if reg not in regs_cmed:
            continue
        vps = sorted(glob.glob(os.path.join(pasta, '*_vp.json')))
        if not vps:
            continue
        meta = json.load(open(os.path.join(pasta, 'meta.json'), encoding='utf-8'))
        empresa = (meta.get('produto') or {}).get('razaoSocial', '')
        palavra = next((w for w in re.split(r'\W+', empresa) if len(w) >= 4 and w.upper() not in
                        {'LABORATORIOS', 'LABORATÓRIOS', 'FARMACEUTICA', 'FARMACÊUTICA', 'INDUSTRIA', 'INDÚSTRIA', 'BRASIL', 'COMERCIO', 'COMÉRCIO', 'PRODUTOS'}), '')
        empresa_rx = re.compile(re.escape(palavra), re.I) if palavra else None
        v = json.load(open(vps[-1], encoding='utf-8'))
        doc = next((d for d in v['documentos'] if all(str(n) in d['secoes'] for n in range(1, 10))), None)
        if not doc:
            continue
        secoes = {n: limpar(doc['secoes'][n], empresa_rx) for n in PERGUNTAS}
        if sum(len(t) for t in secoes.values()) < 1500:
            continue
        seed[reg] = {
            'registro': reg,
            'produto': (meta.get('produto') or {}).get('nomeProduto', ''),
            'principio': (meta.get('detalhe') or {}).get('principioAtivo', ''),
            'data': v['data'],
            'fonte': 'Bula do paciente oficial (Bulário Eletrônico da Anvisa)',
            'secoes': secoes,
        }
    json.dump(seed, open(saida, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
    print(len(seed), 'bulas;', os.path.getsize(saida) // 1024, 'KB')
    for r, b in seed.items():
        print(' ', r, b['produto'], b['data'])


if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
