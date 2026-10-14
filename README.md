# Remédio Falado — dados abertos

Estoque diário dos remédios de pressão e diabetes nas farmácias da Rede Municipal de Saúde do Recife,
no formato que o app **Remédio Falado** (Squad 4 · CESAR School) usa.

- `estoque.json`: postos com endereço, telefone, horário, localização e os remédios que tinham em estoque
  no dia (`data_estoque` = dia em que o arquivo foi baixado; a Prefeitura não informa a hora do estoque).
- `pendentes.txt`: unidades do estoque que ainda não foram ligadas a um endereço. Para incluir uma,
  adicione `"<nome da unidade no estoque>": "<nome_oficial em dados/rede.csv>"` em `dados/mapa_unidades.json`
  (use `null` para deixar de fora).
- Atualização: todo dia às 05h17 pela tarefa em `.github/workflows/atualizar.yml`. Para rodar na hora:
  aba Actions → "Atualizar estoque dos postos" → Run workflow.

## Fontes e licença

Prefeitura do Recife, Portal de Dados Abertos (dados.recife.pe.gov.br):
"Estoque dos medicamentos nas farmácias da Rede Municipal de Saúde" e "Rede de atenção à saúde no Recife".
Os dados originais e este arquivo derivado estão sob a
[Open Database License (ODbL) 1.0](https://opendatacommons.org/licenses/odbl/1-0/).
O app só mostra o que a lista diz: o estoque muda durante o dia, então a orientação é ligar antes de ir.
