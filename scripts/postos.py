"""Gera www/postos.json a partir de dois arquivos de dados abertos da Prefeitura do Recife:

  1. "Medicamentos por unidade de saúde" (qual remédio cada unidade tinha em estoque)
  2. "Rede de atenção à saúde no Recife" (endereço, horário, telefone e coordenadas)

Uso:
  python3 -I scripts/postos.py medicamentos.csv rede.csv www/postos.json

Só entram os 10 remédios com texto próprio no app e só as unidades que aparecem
na lista de medicamentos (ou seja, que entregam remédio).
"""
import csv, difflib, io, json, re, sys, unicodedata

PRINCIPIOS = {
    'losartana': r'LOSARTAN', 'captopril': r'CAPTOPRIL', 'enalapril': r'ENALAPRIL',
    'hidroclorotiazida': r'HIDROCLOROTIAZIDA', 'atenolol': r'ATENOLOL', 'propranolol': r'PROPRANOLOL',
    'anlodipino': r'ANLODIPIN', 'espironolactona': r'ESPIRONOLACTONA', 'metformina': r'METFORMINA',
    'glibenclamida': r'GLIBENCLAMIDA',
}
# palavras que não ajudam a casar o nome da unidade entre os dois arquivos
GENERICAS = {'USF', 'UBS', 'US', 'UNIDADE', 'SAUDE', 'DA', 'DE', 'DO', 'DAS', 'DOS', 'FAMILIA', 'BASICA',
             'E', 'POLICLINICA', 'MUNICIPAL', 'POSTO', 'UPINHA', 'CS', 'UBT', 'PSF', 'DIA', '24H',
             'DR', 'DRA', 'PROF', 'PROFA', 'SRA', 'NOSSA', 'SENHORA', 'MATERNIDADE', 'FARMACIA'}
ROMANOS = {'I', 'II', 'III', 'IV', 'V'}
# tipos do cadastro que não são unidade de atendimento (ficam de fora)
TIPOS_FORA = ('Academia da Cidade', 'Conselhos de Saúde', 'Mãe Coruja', 'Aqui Tem Camisinha')


def sem_acento(s):
    return ''.join(c for c in unicodedata.normalize('NFD', str(s or '')) if unicodedata.category(c) != 'Mn')


def norm(s):
    return re.sub(r'\s+', ' ', re.sub(r'[^A-Z0-9 ]', ' ', sem_acento(s).upper())).strip()


def tokens(s):
    n = re.sub(r'^US \d+( \d+)? ', '', norm(s))  # tira o código "US 334" do começo
    n = n.replace('SRA ', 'SENHORA ').replace('SAO ', 'SAO ')
    return {t for t in n.split() if t not in GENERICAS and (len(t) > 1 or t in ROMANOS)}


def ler_csv(caminho):
    raw = open(caminho, 'rb').read()
    for enc in ('utf-8-sig', 'cp1252', 'latin-1'):
        try:
            txt = raw.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    amostra = txt[:5000]
    sep = max([';', ',', '\t'], key=amostra.count)
    linhas = list(csv.DictReader(io.StringIO(txt), delimiter=sep))
    # cabeçalhos sem acento e minúsculos, para achar "endereço"/"endereco" etc.
    return [{sem_acento(k).strip().lower(): (v or '').strip() for k, v in l.items() if k} for l in linhas]


def col(linha, *nomes):
    for n in nomes:
        if n in linha and linha[n]:
            return linha[n]
    return ''


def numero(s):
    s = str(s or '').strip().replace('.', '').replace(',', '.') if re.search(r'\d,\d', str(s or '')) else str(s or '').strip()
    try:
        return float(s)
    except ValueError:
        return None


def coord(s):
    v = numero(str(s).replace(',', '.')) if s else None
    return v


def main(med_csv, rede_csv, saida):
    meds = ler_csv(med_csv)
    rede = ler_csv(rede_csv)

    # 1) remédios por unidade (só os 10, com quantidade > 0)
    unidades = {}
    data = ''
    for l in meds:
        bruto = col(l, 'produto')
        if bruto.startswith('*') or '+' in bruto:  # fora da padronização ou combinação com outro remédio
            continue
        prod = norm(bruto)
        p = next((k for k, rx in PRINCIPIOS.items() if re.search(rx, prod)), None)
        if not p:
            continue
        q = numero(col(l, 'quantidade'))
        nome = col(l, 'unidade')
        if not nome:
            continue
        chave = (norm(nome), norm(col(l, 'bairro')))
        u = unidades.setdefault(chave, {'nome': nome, 'bairro': col(l, 'bairro'), 'tem': set()})
        if q is None or q > 0:
            u['tem'].add(p)
        d = col(l, 'data_carga')[:10]
        if d > data:
            data = d

    # 2) cadastro das unidades (endereço, horário, coordenadas)
    cad, farm_familia = [], set()
    for r in rede:
        nome = col(r, 'nome_oficial', 'nome', 'unidade')
        tipo = col(r, 'tipo_unidade')
        if not nome or any(t in tipo for t in TIPOS_FORA):
            continue
        if 'Farmácias da Família' in tipo:
            farm_familia.add(norm(nome))
            continue
        if any(c['n'] == norm(nome) for c in cad):
            continue
        cad.append({
            'nome': nome, 'n': norm(nome), 't': tokens(nome), 'b': norm(col(r, 'bairro')),
            'bairro': col(r, 'bairro'), 'rua': col(r, 'endereco', 'endereço', 'logradouro'),
            'fone': col(r, 'fone', 'telefone'), 'horario': col(r, 'horario', 'horario_funcionamento'),
            'lat': coord(col(r, 'latitude', 'lat')), 'lon': coord(col(r, 'longitude', 'lon', 'long')),
        })

    def iguais(a, b):
        return a == b or (len(a) > 3 and len(b) > 3 and difflib.SequenceMatcher(None, a, b).ratio() >= 0.85)

    def casar(nome, bairro):
        n, t, b = norm(nome), tokens(nome), norm(bairro)
        exato = [c for c in cad if c['n'] == n or (t and c['t'] == t)]
        if exato:
            return next((c for c in exato if c['b'] == b), exato[0]), 'exato'
        melhor, nota = None, 0.0
        for c in cad:
            if not t or not c['t']:
                continue
            comuns = sum(1 for x in t if any(iguais(x, y) for y in c['t']))
            j = comuns / max(len(t), len(c['t']))
            # nome curto contido no outro (ex.: "Coque" e "Coque - Berilo Pernambucano"), no mesmo bairro
            curto = t if len(t) <= len(c['t']) else c['t']
            if c['b'] == b and comuns == len(curto) and all(len(x) >= 4 for x in curto):
                j = max(j, 0.9)
            if c['b'] == b:
                j += 0.1
            if j > nota:
                melhor, nota = c, j
        if melhor and nota >= 0.9:
            return melhor, 'parecido'
        return None, None

    postos, sem_par, parecidos = [], [], []
    for (n, b), u in sorted(unidades.items()):
        c, como = casar(u['nome'], u['bairro'])
        if not c:  # fora do Recife, hospital, SAMU, CAPS etc.
            sem_par.append(u['nome'])
            continue
        if not u['tem']:
            continue
        item = {'nome': c['nome'].strip(), 'bairro': c.get('bairro') or u['bairro'],
                'tem': sorted(u['tem'], key=list(PRINCIPIOS).index)}
        if c['n'] in farm_familia:
            item['ff'] = 1
        if c:
            for k in ('rua', 'fone', 'horario', 'lat', 'lon'):
                if c.get(k):
                    item[k] = c[k]
            if como == 'parecido':
                parecidos.append(f"{u['nome']}  ->  {c['nome']}")
        # coordenadas do Recife (evita ponto errado)
        if item.get('lat') and not (-8.2 < item['lat'] < -7.9 and -35.1 < item['lon'] < -34.8):
            item.pop('lat'); item.pop('lon', None)
        postos.append(item)

    out = {'fonte': 'Secretaria de Saúde do Recife: "Medicamentos por unidade de saúde" e "Rede de atenção à saúde no Recife"',
           'data_estoque': data, 'postos': postos}
    with open(saida, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    # a mesma unidade pode aparecer duas vezes na lista de estoque: junta
    junta = {}
    for p in postos:
        k = (p['nome'], p.get('rua', ''))
        if k in junta:
            junta[k]['tem'] = sorted(set(junta[k]['tem']) | set(p['tem']), key=list(PRINCIPIOS).index)
        else:
            junta[k] = p
    postos = sorted(junta.values(), key=lambda p: (p.get('bairro', ''), p['nome']))
    out['postos'] = postos
    with open(saida, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    com = sum(1 for p in postos if p.get('lat'))
    print(f'{len(postos)} unidades com algum dos 10 remédios; {com} com coordenadas; estoque de {data or "?"}')
    if parecidos:
        print(f'{len(parecidos)} nomes casados por semelhança (confira):')
        for x in parecidos[:40]:
            print('  ', x)
    if sem_par:
        print(f'{len(sem_par)} sem endereço no cadastro: ' + '; '.join(sem_par[:30]))


if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
