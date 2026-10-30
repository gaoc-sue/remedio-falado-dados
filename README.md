# Remédio Falado — dados abertos

Estoque diário dos remédios de pressão e diabetes nas farmácias da Rede Municipal de Saúde do Recife,
no formato que o app **Remédio Falado** (Squad 4 · CESAR School) usa.

- `estoque.json`: postos com endereço, telefone, horário, localização e os remédios que tinham em estoque
  no dia (`data_estoque` = dia em que a Prefeitura atualizou os arquivos no portal, `data_tipo: "prefeitura"`;
  se o portal não informar, fica o dia do download, `data_tipo: "download"`).
- `pendentes.txt`: unidades do estoque que ainda não foram ligadas a um endereço. Para incluir uma,
  adicione `"<nome da unidade no estoque>": "<nome_oficial em dados/rede.csv>"` em `dados/mapa_unidades.json`
  (use `null` para deixar de fora).
- `u` e `d` de cada posto: nomes da unidade na planilha e distrito sanitário. O app usa para juntar o estoque que ele
  mesmo busca no portal; `qtd`: comprimidos de cada um dos 10 remédios (itens padronizados, apresentação COMPR).
- Atualização: o portal da Prefeitura não responde aos servidores do GitHub, então desde 30/10/2026 **o app consulta o
  estoque direto do celular** e esta lista é a reserva (quando o portal não responde). Para atualizá-la: baixe os 8 CSVs
  em dados.recife.pe.gov.br ("Estoque dos medicamentos nas farmácias da Rede Municipal de Saúde"), coloque em `brutos/`
  como `medicamentos_distrito01.csv` … `08.csv`, escreva a data em `brutos/data.txt` (AAAA-MM-DD) e rode
  `python3 scripts/gerar_estoque.py brutos/medicamentos_distrito*.csv`.

## Fontes e licença

Prefeitura do Recife, Portal de Dados Abertos (dados.recife.pe.gov.br):
"Estoque dos medicamentos nas farmácias da Rede Municipal de Saúde" e "Rede de atenção à saúde no Recife".
Os dados originais e este arquivo derivado estão sob a
[Open Database License (ODbL) 1.0](https://opendatacommons.org/licenses/odbl/1-0/).
O app só mostra o que a lista diz: o estoque muda durante o dia, então a orientação é ligar antes de ir.
