"""Junta as coordenadas exportadas do Google My Maps à lista de farmácias do app.

Uso:  python3 scripts/coordenadas.py arquivo_exportado.kml   (ou .kmz ou .csv)
Atualiza www/farmacias.json com lat/lon de cada farmácia encontrada.
Liga pelo CNPJ quando a coluna existir; senão, pelo nome + bairro.
"""
import sys, json, re, zipfile, csv, io, unicodedata, xml.etree.ElementTree as ET
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DESTINO = RAIZ / 'www' / 'farmacias.json'

def norm(s):
    s = unicodedata.normalize('NFD', str(s or '')).encode('ascii', 'ignore').decode().upper()
    s = re.sub(r'\b(LTDA|ME|EPP|S\.?A\.?|EIRELI)\b', ' ', s)
    return re.sub(r'[^A-Z0-9]+', ' ', s).strip()

def pontos_kml(texto):
    raiz = ET.fromstring(texto)
    for pm in raiz.iter():
        if not pm.tag.endswith('Placemark'): continue
        dados = {}
        for el in pm.iter():
            t = el.tag.split('}')[-1]
            if t == 'name' and el.text: dados['nome'] = el.text
            if t == 'Data':
                v = next((x.text for x in el if x.tag.endswith('value')), '')
                dados[el.get('name', '').strip().upper()] = v or ''
            if t == 'coordinates' and el.text:
                lon, lat = el.text.strip().split(',')[:2]
                dados['lat'], dados['lon'] = float(lat), float(lon)
        if 'lat' in dados: yield dados

def pontos_csv(texto):
    for linha in csv.DictReader(io.StringIO(texto)):
        l = {k.strip().upper(): v for k, v in linha.items()}
        wkt = l.get('WKT', '')
        m = re.search(r'POINT\s*\(\s*([-\d.]+)\s+([-\d.]+)', wkt)
        if m: l['lon'], l['lat'] = float(m.group(1)), float(m.group(2))
        elif l.get('LATITUDE') and l.get('LONGITUDE'):
            try: l['lat'], l['lon'] = float(l['LATITUDE'].replace(',', '.')), float(l['LONGITUDE'].replace(',', '.'))
            except ValueError: continue
        else: continue
        l['nome'] = l.get('FARMÁCIA') or l.get('FARMACIA') or l.get('NAME') or ''
        yield l

def main(arq):
    p = Path(arq)
    if p.suffix.lower() == '.kmz':
        with zipfile.ZipFile(p) as z: texto = z.read(next(n for n in z.namelist() if n.endswith('.kml'))).decode('utf-8')
        pts = list(pontos_kml(texto))
    elif p.suffix.lower() == '.kml':
        pts = list(pontos_kml(p.read_text(encoding='utf-8')))
    else:
        pts = list(pontos_csv(p.read_text(encoding='utf-8-sig')))
    farm = json.loads(DESTINO.read_text(encoding='utf-8'))
    por_cnpj = {re.sub(r'\D', '', str(x.get('CNPJ', ''))).zfill(14): x for x in pts if x.get('CNPJ')}
    por_nome = {(norm(x.get('nome')), norm(x.get('BAIRRO'))): x for x in pts}
    ok = 0
    for f in farm:
        x = por_cnpj.get(f['cnpj']) or por_nome.get((norm(f['nome']), norm(f['bairro'])))
        if not x:
            cand = [y for y in pts if norm(y.get('nome')).startswith(norm(f['nome'])[:12])]
            x = cand[0] if len(cand) == 1 else None
        if x:
            f['lat'], f['lon'] = round(x['lat'], 6), round(x['lon'], 6); ok += 1
    DESTINO.write_text(json.dumps(farm, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    print(f'{ok} de {len(farm)} farmácias com coordenadas.')
    for f in farm:
        if 'lat' not in f: print('  sem coordenada:', f['nome'], '-', f['bairro'])

if __name__ == '__main__':
    main(sys.argv[1])
