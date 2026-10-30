# Notificação instantânea: passo a passo

Sem isto, o app funciona como antes: o outro celular busca as mensagens a cada 10–15 minutos. Com isto, "Lembrar no celular", aviso de dose atrasada, socorro e as mudanças do gestor chegam em segundos, mesmo com o app fechado.

**Como funciona:** depois de mandar uma mensagem criptografada, o celular pede ao **carteiro** (um servidor pequeno no Cloudflare) para acordar o outro. O carteiro manda pelo **Firebase** um aviso **sem conteúdo**. O outro celular acorda e busca a mensagem sozinho. Nem o Google nem o carteiro veem remédio, nome ou horário.

São 3 contas gratuitas (Firebase, Cloudflare, GitHub, que já existe) e 4 segredos no GitHub. Leva uns 40 minutos.

---

## 1. Firebase (uns 15 minutos)

1. Entre em **console.firebase.google.com** com uma conta Google do time e clique em **Criar um projeto**. Nome: `remedio-falado`. O Google Analytics pode ficar **desligado**.
2. No projeto, clique no ícone do **Android** para adicionar um app.
   - Nome do pacote: **`br.com.remediofalado.app`** (exatamente assim).
   - Apelido: `BULA`. O certificado SHA-1 pode ficar em branco.
   - Clique em **Registrar app** e **baixe o `google-services.json`**. Pule os outros passos do assistente: o projeto já está pronto para isso.
3. Ainda no Firebase, clique na engrenagem → **Configurações do projeto** → aba **Contas de serviço** → **Gerar nova chave privada** → **Gerar chave**. Baixa um arquivo `.json` (o nome começa com `remedio-falado-`).
   - **Esse arquivo é uma senha.** Não mande no WhatsApp, não suba no repositório. Ele vai só para os Secrets do GitHub.
4. Na aba **Cloud Messaging**, confira que a **API Firebase Cloud Messaging (V1)** está **ativada** (em projeto novo, já vem ativada).

## 2. Cloudflare (uns 15 minutos)

1. Crie a conta em **dash.cloudflare.com/sign-up** (não precisa de site nem de cartão).
2. No menu da esquerda, abra **Workers e Pages** (Compute → Workers). Na primeira vez, o Cloudflare pede para **escolher o seu subdomínio `workers.dev`** (por exemplo, `squad4`). Escolha e confirme.
3. Copie o **ID da conta** (Account ID): ele aparece na coluna da direita da página de Workers e Pages, ou na página inicial da conta.
4. Crie a chave de acesso para o GitHub: foto do perfil → **Meu perfil** → **Tokens de API** → **Criar token** → modelo **"Editar Cloudflare Workers"** (Edit Cloudflare Workers) → em *Recursos da conta*, escolha a sua conta; em *Recursos de zona*, deixe **Todas as zonas** → **Continuar** → **Criar token**. Copie o token: ele só aparece uma vez.

## 3. GitHub: os 4 segredos (uns 5 minutos)

No repositório `remedio-falado`: **Settings** → **Secrets and variables** → **Actions** → **New repository secret**. Crie um por um, com o nome exatamente assim:

| Nome | O que colar |
|---|---|
| `GOOGLE_SERVICES_JSON` | o conteúdo inteiro do `google-services.json` (abra no Bloco de Notas, copie tudo) |
| `FIREBASE_SA` | o conteúdo inteiro do arquivo da chave privada (passo 1.3) |
| `CLOUDFLARE_API_TOKEN` | o token do passo 2.4 |
| `CLOUDFLARE_ACCOUNT_ID` | o ID da conta do passo 2.3 |

## 4. Publicar o carteiro e gerar o APK

1. Aba **Actions** → **Publicar o carteiro (notificação instantânea)** → **Run workflow**.
2. Ele publica o carteiro, confere se está no ar, grava o endereço em `www/carteiro.json` e **gera o APK de novo sozinho** (o fluxo "Gerar APK" começa logo depois).
3. Se ficar vermelho, a primeira linha do erro diz o que falta (segredo faltando, JSON errado ou subdomínio `workers.dev` não escolhido).

> Não apague o arquivo `www/carteiro.json` do repositório. Os zips do app não trazem esse arquivo, então ele não é sobrescrito quando vocês sobem uma versão nova.

## 5. Testar com dois celulares

1. Instale o APK novo **nos dois celulares** e abra o app em cada um uma vez (é aí que cada celular manda o seu código de notificação para o outro, criptografado).
2. Em um deles: **Ajustes → Estado da conexão**. A linha **"Aviso na hora"** deve dizer **ligado**.
   - "esperando o outro celular": abra o app no outro celular e espere uns 15 segundos.
   - "não configurado neste app": o APK foi gerado antes do passo 4. Gere de novo.
3. **Bloqueie a tela do outro celular** e, no primeiro, toque em **Testar conexão agora**. Com a notificação instantânea, ele responde em poucos segundos com **"com o app fechado"**. Sem ela, demorava até 15 minutos.
4. Teste o caso real: com um remédio atrasado, toque em **Lembrar no celular** no celular da família. O lembrete deve aparecer no outro celular em segundos, e a família vê "O lembrete chegou às…".

## Custos e limites

- Firebase Cloud Messaging: gratuito.
- Cloudflare Workers, plano gratuito: 100 mil pedidos por dia. Cada aviso é um pedido.
- Só funciona em celulares com os serviços do Google (quase todo Android vendido no Brasil). Sem eles, o app continua buscando a cada 10–15 minutos.
- Marcas que economizam bateria de forma agressiva (Xiaomi, Huawei, Oppo…) podem atrasar alguns segundos. O passo **Ajustes → Para o alarme tocar sempre** continua importante.

## Segurança

- O carteiro (`carteiro/carteiro.js`) só aceita `POST /acordar` com um código de notificação. Não guarda nada e não grava log.
- A chave do Firebase fica só nos Secrets do GitHub e no Cloudflare (como segredo do carteiro). Nunca dentro do APK.
- Se a chave vazar: no Firebase, em **Contas de serviço**, apague a chave, gere outra, troque o segredo `FIREBASE_SA` e rode o passo 4 de novo.
