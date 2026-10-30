# BULA — Base Unificada de Linguagem Acessível

O app de remédios que fala (antes chamado Remédio Falado).

App Android para idosos com baixa visão ou pouca leitura. A pessoa aponta o celular para a caixa do remédio e o app fala o que é e para que serve, segundo a bula. Ele lembra o horário com alarme, confere pela câmera se a caixa é a certa e, se a pessoa quiser, conecta o celular de alguém da família, com criptografia de ponta a ponta.

## Design baseado em estudos

A tela inicial mostra **só o momento de agora** (café da manhã, almoço, tarde, jantar ou antes de dormir), com os remédios e um botão grande **TOMEI**. O resto do dia fica num card "Depois", e embaixo há 4 atalhos grandes: Ler a caixa, Meus remédios, Remédio de graça e Família. As outras telas têm "Voltar" escrito e o botão "Ouvir".

| Decisão | Por quê |
|---|---|
| Só a próxima ação em destaque; o secundário fica em "Mais informações" e em Ajustes | Simplificar e destacar o próximo botão esperado (Gomez-Hernandez et al., 2023, *JMIR mHealth*, revisão de 40 estudos com pessoas de 60+) |
| "Voltar" escrito e "Ouvir" no topo de toda tela; sem barra de abas para quem toma o remédio | Saída segura em toda tela e navegação em linha (Gomez-Hernandez et al., 2023; Grindrod et al., 2014, *JMIR mHealth*) |
| Ícone + nome em todo botão | O acerto de idosos subiu de 45% (só ícone) para 78% (ícone + nome) (Leung, McGrenere e Graf, 2011, *Behaviour & IT*) |
| Desenhos concretos: xícara, prato, tigela, lua; foto da caixa; quantidade em desenho (1 comprimido = 1 desenho) | Imagens concretas e familiares (Gomez-Hernandez et al., 2023); pictograma de refeição entendido por 95,5% (Alves et al., 2025, *RBPS*); quantidade por pictogramas repetidos (Dick, 2025) |
| Remédio lido: nome + 3 opções numeradas (1 Ouvir, 2 Colocar nos meus remédios, 3 Ler a bula) | Poucas opções por tela (Apple, *Assistive Access*, WWDC 2025); números ajudam quem dá ajuda por telefone (CPqD Alcance) |
| Tela "Pronto!" que só fecha com um toque | Sem tempo-limite e retorno claro depois de cada ação (Apple, WWDC 2025; Gomez-Hernandez et al., 2023) |
| Doses amarradas a momentos da rotina | Pistas de rotina dão horários mais regulares (Valenzuela-Beltrán et al., 2022; van de Vijver et al., 2023) |
| Cadastro com uma pergunta por tela | Idosos se perderam em menus e submenus (Grindrod et al., 2014) |
| Botões de 64 a 104 px (TOMEI) | Alvos de ~14 mm tiveram o melhor desempenho com idosos (Leitão e Silva, 2012) |
| Paleta petróleo (#0F4C5C) + mel (#F2A33A) em fundo creme liso, texto escuro; verde só para "tomado" | NIA/NLM (texto escuro em fundo claro, sem textura atrás do texto) e WCAG 2.2 (contraste ≥ 4,5:1) |
| Cor + símbolo fixo em cada botão: laranja com ✓ = seguir; azul com alto-falante/microfone = ouvir e falar; branco = voltar; vermelho com alerta = socorro. A voz diz a cor e o símbolo | A voz consegue apontar o botão ("toque no botão laranja, com o sinal de certo"). O símbolo garante a leitura para daltônicos: conferido com simulação de deuteranopia, protanopia e tritanopia (Machado et al., 2009) |
| Ajustes ficam "para o familiar" | No modo simplificado da Apple, quem configura é a pessoa de apoio |
| Frases curtas, ordem direta, palavras comuns | Lei 15.263/2025 (Política Nacional de Linguagem Simples), art. 5º |
| Usar o app inteiro falando e ouvindo, sem precisar ler nem escrever | 51% dos brasileiros de 50 a 64 anos são analfabetos funcionais (Inaf 2024) |

Teste sugerido antes da banca: 5 a 8 idosos, "o que acontece se tocar aqui?" em cada botão, com meta de 85% de acerto (critério ISO 3864-1 usado por Alves et al., 2025).

## O que o app faz

- **Que remédio é este?** O mesmo botão lê o código de barras, o QR code e o código quadradinho de rastreio (GS1 DataMatrix, que traz o mesmo número do código de barras). Se for o QR da bula digital, mostra o site e o botão para abrir; se for o QR de uma receita digital, vai para a leitura da receita; se o link não deixar claro, pergunta numa tela só se o QR está na caixa ou na receita; se não for de remédio, diz para apontar para as listras. Lê o código de barras da caixa e consulta a lista oficial de preços da CMED/Anvisa, com mais de 27 mil códigos, sem internet. Fala o tipo de remédio e, quando há bula, o trecho literal de "para que serve".
- **Colocar nos meus remédios:** 3 perguntas, uma por tela: em que momento a receita manda tomar, quanto em cada momento e conferir com a receita (com o total do dia). Na terceira, o botão já é "Salvar", e a foto da caixa é um link opcional. A pessoa copia da receita do médico; o app não sugere dose. Quantidade diferente de 1, 2 ou meio se escolhe com os botões − e + (sem digitar); "Escrever" fica para receita fora do padrão.
- **Avisos de dose (só avisam, nunca bloqueiam):** o app soma o total do dia e avisa quando:
  - passa da maior dose por dia citada na bula (`www/doses.json`: valor + frase literal da bula + registro; e os 10 remédios da pressão e diabetes);
  - passa do que a bula diz por dose de uma vez (paracetamol e dipirona: 1.000 mg);
  - o mesmo princípio ativo já está em outra caixa (soma os dois) ou num remédio combinado (ex.: dipirona e um relaxante com dipirona);
  - são mais de 4 comprimidos de uma vez.

  O aviso cita a frase da bula, é falado e pede "Conferi: é isso mesmo que está na receita". As doses precisam ser conferidas por farmacêutico (`"conferido_por"` em `doses.json`).
- **Ler a receita (teste, desliga em Ajustes → Ler receita):** em "Ler a caixa", a pessoa fotografa a receita impressa (ou abre o PDF ou o QR code da receita digital). Também dá para mandar direto do WhatsApp, e-mail ou galeria: Compartilhar → BULA (PDF, foto ou texto). O texto é lido no próprio celular, sem internet (ML Kit Text Recognition, `android-src/ReceitaPlugin.java`); se a foto estiver deitada, o app gira e lê de novo. `www/receita.js` separa os remédios e acha cada um na lista da Anvisa (pelo princípio ativo ou pelo nome da caixa), com dose, quanto tomar e o momento: "pela manhã" = café da manhã, "após o jantar" = jantar, "ao deitar" = antes de dormir, "2 x ao dia" = café e jantar, "de 12/12 horas" = a partir do café da manhã, de 12 em 12 horas (sempre pelos horários da rotina da pessoa). Depois mostra **um remédio por vez**, com o recorte da receita ao lado, e a pessoa toca em "Está certo", "Corrigir" (abre as 3 perguntas já preenchidas) ou "Não colocar". Não existe "salvar todos". O que não ficou claro vira pergunta: "à noite" (jantar ou antes de dormir?), "Selozok ou Metoprolol" (qual caixa você tem?), dose que não deu para ler, "1 vez ao dia" sem hora. "Se dor"/"se febre" não ganha alarme; remédio de um dia da semana avisa que o app ainda não faz. Os avisos de dose valem aqui também. A foto não é guardada. Letra de mão o leitor quase não entende. Para tirar de vez: apagar `www/receita.js`, `android-src/ReceitaPlugin.java`, a linha `registerPlugin(ReceitaPlugin.class)` em `MainActivity.java` e o bloco do `text-recognition` em `scripts/patch-android.js` (sem o `receita.js`, a opção some sozinha do app).
- **Bula completa:** a bula do paciente oficial, nas 9 perguntas, com letra grande e "Ouvir esta parte". 69 bulas já vêm no app (`www/bulas.json`); as outras são baixadas do Bulário Eletrônico da Anvisa e ficam guardadas.
- **Alarme:** o Android avisa na hora, mesmo com o app fechado. A pessoa aperta TOMEI. "Conferir a caixa" confirma pela câmera se é o remédio certo.
- **Telefone da família:** aceita o número de qualquer jeito (+55, 0, DDD, espaços, traços) e guarda como (81) 99999-0000; avisa se faltar o DDD. "Escolher dos contatos" já salva o contato escolhido.
- **Teste dos avisos:** no celular de quem toma, Ajustes → Estado da conexão → "Mandar um aviso de teste" mostra uma notificação no celular da família. No celular de quem acompanha, a tela da família avisa se notificação ou bateria estiverem bloqueando os avisos.
- **Conferir a caixa no alarme:** na tela do alarme, "Conferir a caixa com a câmera" lê o código de barras e diz se é o remédio da hora ("Caixa certa") ou outro remédio, ou a mesma substância com outra dose. Caixa errada também avisa a família. Com vários remédios na mesma hora, confere um por um. É opcional: o TOMEI nunca fica bloqueado. Sem o leitor do Android, dá para digitar o número embaixo das barras.
- **Remédio acabando:** ao colocar um comprimido ou cápsula, o app já sabe quantos vêm na caixa (registro da Anvisa) e pergunta se a caixa é nova. Cada TOMEI desconta; 5 dias antes de acabar e na véspera, às 9h, chega a notificação com "Onde tem de graça", e a família recebe o aviso. Em Meus remédios: "Restam uns 12 comprimidos · acaba em 6 dias" e "Comprei mais"; ao ler de novo a caixa, "Comprei esta caixa" soma na conta. É estimativa: o app sempre diz "uns". Gota e xarope não são contados.
- **Contagem até a dose:** o cartão da hora mostra "07:00 · daqui a 20 min" (a hora fica fixa; a contagem é apoio) e a voz diz o mesmo.
- **Família, dia em números:** a tela de quem acompanha mostra "Hoje: 3 de 5 doses marcadas" e o remédio que está acabando.
- **Aviso de remédio tomado:** quando a pessoa marca TOMEI, a família escolhe, por pessoa: "A cada dose, sem som" (padrão de quem cuida dos remédios), "Só o resumo do dia, às 21h" (padrão de quem só acompanha) ou "Não avisar". A cada dose, chega em segundos (carteiro) uma notificação num canal próprio do Android, sem som e sem vibrar, sempre a mesma por pessoa (troca a anterior): "Dona Lúcia marcou que tomou o remédio da pressão · Às 07:08, com a caixa conferida pela câmera · Hoje: 3 de 5 doses". Desfazer também avisa. Atraso, caixa errada e socorro continuam no canal com som. O resumo das 21h sai do Android do familiar, mesmo com o app fechado (`Elo.resumoDoDia`).
- **Toque e movimento:** todo botão escurece enquanto o dedo está nele; com "Remover animações" ligado no Android, o app também para de animar, sem precisar do Modo calmo.
- **APK menor:** só o código nativo ARM (64 e 32 bits), o de praticamente todo celular Android. O emulador do computador precisa de imagem ARM.
- **Voltar do Android:** o botão e o gesto de arrastar da borda fecham a janela aberta ou voltam uma tela, como o botão Voltar do app. Na tela inicial, o app só vai para trás (não fecha), e o alarme não some com o Voltar (`@capacitor/app`).
- **Marca e ícone:** o B de BULA em forma de balão de fala, com duas linhas de texto dentro ("linguagem acessível"). O ícone do app é adaptativo (Android 8+), tem versão monocromática para o tema do Android 13+ e PNGs para celulares antigos (`android-src/res`, copiados por `scripts/patch-android.js`). A abertura do app e o ícone da notificação usam a mesma marca. Na voz, o nome é sempre dito "Bula" (o app troca antes de falar, para a voz não soletrar B-U-L-A).
- **Para o alarme tocar sempre:** detecta o que falta em cada marca (Xiaomi, Samsung, Motorola...) e abre a configuração certa (`android-src/EnergiaPlugin.java`).
- **Remédio de graça:** Farmácia Popular (52 farmácias do Recife) e 143 postos de saúde do SUS com endereço, horário, telefone e estoque (dados abertos da Prefeitura).
  - **Estoque dos postos atualizado todo dia:** o repositório público `remedio-falado-dados` tem uma tarefa do GitHub Actions que, todo dia às 05h17, baixa o estoque das farmácias da Rede Municipal de Saúde (8 distritos sanitários, Portal de Dados Abertos do Recife), junta com o endereço, telefone e horário das unidades e publica `estoque.json`. O app baixa esse arquivo com internet, uma vez por dia, e guarda para usar sem internet. O build do APK também copia o do dia para `www/postos.json`. Tira da lista SAMU, laboratório, depósitos dos distritos, CAPS e hospitais.
- **Família, com o celular conectado:** em Família → "Conectar o celular da família", o app mostra um código de 6 números e um QR code. No celular da família, a pessoa toca em "Sou da família" e lê o código. Quem toma o remédio vê o nome e toca em Aceitar. A partir daí, a família vê:
  - o dia de hoje, com "Ligar para ela" e "Lembrar no celular dela";
    "Lembrar no celular" não é instantâneo (não há servidor de push): enquanto a dose está atrasada, o celular de quem toma confere as mensagens de 10 em 10 minutos (alarme exato, até 3 horas depois da dose), mostra o lembrete e responde. A família vê "Lembrete enviado às…" e depois "O lembrete chegou às…" (ou "já tinha marcado");
  - a semana em grade;
  - os remédios;
  - um bloco "Vale olhar", com caixa errada e alarme que pode falhar.

  **Mensagens grandes vão em partes:** o servidor de passagem gratuito só aceita 4 KB por mensagem; acima disso, a mensagem vira um anexo que o app não lê e que some em 3 horas (era por isso que o pareamento parava depois de algumas horas, com muitos remédios). Agora o resumo, já criptografado, vai em partes de 3.000 caracteres e é montado de novo no outro celular (no app e no Android com o app fechado). Se o servidor pedir para esperar (limite de mensagens), o app espera 5 minutos e tenta de novo, sem desfazer a conexão. Ao abrir o app, a família pede o resumo atualizado e quem toma o remédio reenvia o seu se estiver velho.
  **Estado da conexão** (Família ou Ajustes): último envio, última notícia do outro celular, último erro em português e "Testar conexão agora" (o outro celular responde sozinho, até com o app fechado). Na família, depois de 3 horas sem notícia, aparece um aviso claro com o atalho para essa tela.

  Se uma dose passar 30 minutos sem ser marcada, a família recebe uma notificação sozinha, mesmo com os dois apps fechados. Um alarme exato do Android (`android-src/EloAlarme.java`, `setExactAndAllowWhileIdle`) acorda o celular de quem toma 31 minutos depois de cada dose, mesmo parado, confere e avisa na hora; ele é remarcado sozinho depois de cada aviso, quando o celular liga ou o app atualiza. A tarefa de 15 em 15 minutos (JobScheduler) continua como reserva, por exemplo se faltar internet na hora. Sem a permissão "Alarme na hora certa" (Android 12 ou mais novo pede para ligar), o Android pode atrasar alguns minutos; o passo para ligar está em Ajustes → Para o alarme tocar sempre.
  - quem acompanha também pode tomar remédios: o cartão "Você também toma remédios?", na tela de quem acompanha (ou Ajustes → Também tomo remédios), pede o aceite próprio (o aceite de acompanhar cobre só os dados da outra pessoa), o nome e leva para colocar o primeiro remédio. Quem é acompanhado continua no botão Família.
- **Modo gestor (alguém capaz cuida dos remédios):** ao conectar, a pessoa da família escolhe "Acompanhar e cuidar dos remédios" (filho, mãe, cuidadora, farmacêutica). Quem toma o remédio vê o pedido e escolhe: aceitar, aceitar só para acompanhar ou não aceitar. Depois:
  - o gestor lê a caixa no celular dele, responde as 3 perguntas copiando da receita, e a mudança vai criptografada para o celular de quem toma;
  - o celular de quem toma aplica a mudança, refaz os alarmes, **fala o que mudou** e anota com o nome de quem mudou ("Últimas mudanças");
  - quem toma fica no modo simples: vê e ouve os remédios, recebe os alarmes e marca TOMEI. Ao ler uma caixa nova, toca em "Pedir para X colocar";
  - o gestor também muda horário e quantidade, ou tira um remédio (com dois toques). Se o celular de quem toma demorar a abrir o app, a mudança fica "Esperando" e é reenviada de 6 em 6 horas pelo próprio Android (`Elo.java`), mesmo com o app do gestor fechado, até o outro celular confirmar;
  - quem toma desliga quando quiser: Ajustes → Quem cuida dos meus remédios → "Parar: eu mesmo cuido".

  Base: Apple Assistive Access (a pessoa de apoio configura, quem usa vê menos opções), com uma adaptação nossa: a pessoa que toma o remédio aceita e pode desligar, e toda mudança é falada (Agency e Responsibility).
- **Falar com o app (para quem não lê):** o botão **Falar** fica em toda tela. A pessoa diz:
  - "já tomei";
  - "qual remédio agora?";
  - "que remédio é esse?";
  - "liga pra minha filha";
  - "meus remédios";
  - "remédio de graça";
  - "o que mudou" (no modo gestor);
  - "socorro".

  O app responde em voz e **confirma antes de agir** ("Vou marcar os remédios do almoço. Diga sim ou não"). Se não entender, mostra 6 botões grandes com desenho.
- **Cadastro respondendo falando:** no "Colocar nos meus remédios", o botão "Responder falando" pergunta e ouve: em que momento ("de manhã e à noite"), quanto ("dois comprimidos", "meio"), se está igual à receita ("sim") e se quer foto. O nome e o telefone também podem ser ditos.
- **Socorro:** com "socorro" (ou o botão vermelho), o app confirma e mostra primeiro, maior, o botão do SAMU (192), que liga pela linha do celular, sem internet. Depois vem o botão da família. O aviso no celular da família vai pela internet e só aparece como entregue quando o celular dela responde "recebi" (o app aberto ou o Android em segundo plano, `Elo.java`). Sem resposta em 2 minutos, ou sem internet, o app fala que não conseguiu avisar e manda ligar. Usa o reconhecimento de voz do Android (plugin `@capacitor-community/speech-recognition`): sem IA externa, sem custo e sem gravar o áudio.
- **WhatsApp:** continua como opção, para quem não quer conectar. O app prepara a mensagem e a pessoa envia.
- **"Ver como a família vê":** mostra o painel da família com os dados do próprio celular, para apresentar sem um segundo aparelho.

## Acessibilidade

- **Sem gênero fixo:** os textos usam o nome da pessoa ("Ligar para Seu Antônio", "celular de Dona Lúcia") em vez de "ela/dela".
- **Um caminho por vez:** a bula abre por "Ler a bula"; o QR code e o site da Anvisa só aparecem se a bula não abrir. "Testar sem caixa" e "Ver como a família vê" só existem no modo demonstração. O WhatsApp só aparece quando ninguém está conectado.
- **Boas-vindas com uma pergunta só: "Quem vai usar este celular?"** Duas escolhas grandes: "Eu tomo os remédios" e "Cuido de alguém da família". Embaixo: "Estou arrumando o celular de outra pessoa" (a filha que instala no celular da mãe: a mãe aceita os termos, quem ajuda coloca o nome e os remédios dela, e no fim o app lembra de entregar o celular), "Prefiro usar falando", "Ver demonstração" e "Privacidade e termos". Sem cadastro e sem CPF.
- **Caminho da família:** a primeira pergunta é "Como você vai ajudar?": "Só acompanhar" ou "Cuidar dos remédios" (modo gestor). Quem toma o remédio não vê nada sobre gestor no início: o pedido só aparece quando a família pede, e a pessoa aceita ou não.
- **Sistema visual azul (outubro de 2026):** azul para seguir, verde para TOMEI, laranja (com texto escuro) para remédio de graça e atenção, roxo para ouvir, falar e receita, cinza para texto de apoio. Fonte Inter, dentro do app. Todas as cores conferidas pela WCAG 2.2 (texto 4,5:1, bordas 3:1); o verde e o cinza da proposta original foram escurecidos para passar.

- Letra Inter (com Atkinson Hyperlegible de reserva), de Normal a Muito grande (130%).
- Contraste alto, modo escuro e **Modo calmo** (sem animações e voz só quando a pessoa pede).
- Tudo o que aparece na tela pode ser ouvido pelo botão "Ouvir".
- Botão Voltar do Android funciona: fecha a janela aberta ou volta uma tela.
- Cada remédio tem cor e inicial (ou a foto da caixa), para não depender só da cor.
- **Sinais sem palavras:** um bipe que sobe quando o app começa a ouvir e um que desce quando para (desliga em Ajustes → Voz e velocidade, se o celular já fizer o próprio bipe). Ao ler a caixa, o QR code ou a receita, o celular vibra: uma vibração longa quando reconheceu, duas curtas quando não reconheceu (plugin `@capacitor/haptics`).
- **Ajustes do teste com usuários (outubro de 2026):**
  - **Tela de aceitar curta:** três frases grandes (os dados ficam só neste celular; a família só vê se você conectar; dá para apagar tudo), um botão "Concordo" e "Ouvir". O resto (o que é guardado, criptografia, base legal e os termos) fica em "Ver detalhes". Antes era uma caixinha e depois um botão, e a pessoa se perdia.
  - **Fala curta depois de colocar um remédio:** "Pronto. Vou lembrar o remédio da pressão na hora certa." A tela "Para o alarme tocar sempre" só abre depois que a fala termina, para não cortar a frase. A velocidade normal da voz passou para 1,0 (Devagar 0,85, Rápida 1,12).
  - **Uma confirmação só na pergunta 3:** quando aparece um aviso de dose (por exemplo, três horários que passam da maior dose citada na bula), havia duas caixinhas seguidas, uma para o aviso e outra para a receita. Agora é uma só, "Conferi: é isso mesmo que está na receita", que vale para as duas. O aviso continua aparecendo e sendo falado; o app só avisa, nunca bloqueia.
  - **Menos telas até o primeiro remédio (de 11 para 8):** as boas-vindas pedem só o nome e levam direto à tela Hoje, onde está o botão grande "Colocar o primeiro remédio". O contato da família saiu das boas-vindas: depois do primeiro remédio salvo, a tela Hoje mostra uma vez o cartão "Quem da família recebe os avisos?", com "Colocar agora" (abre Ajustes → Meus dados) e "Agora não". A foto da caixa deixou de ser a 4ª pergunta.
  - **Voz mais tolerante:** o reconhecimento do Google erra com quem fala baixo, devagar ou com sotaque. Antes de entender, o app aproxima as palavras-chave com uma letra de diferença ("tumei", "to mei" = tomei; "socoro" = socorro) e aceita mais formas de sim e não ("aham", "é", "tá bom", "num"). Depois de uma tentativa sem entender, o app pede para tocar no botão e mostra os botões grandes, em vez de insistir na voz.
- **Voz em português instalada:** o app confere se o celular tem a voz em português. Se faltar, avisa em Ajustes → Voz e velocidade, com o botão "Instalar a voz em português", e mostra no "Vale olhar" da família. As vozes que funcionam sem internet vêm primeiro, para o alarme falar mesmo sem sinal.

## Segurança e criptografia

- **No celular:** os dados ficam guardados com AES-256-GCM (`www/cofre.js`). No app instalado, a chave é guardada pelo **Android Keystore** (`android-src/Cofre.java`, `CofrePlugin.java`) e não pode ser copiada. Os dados antigos, sem criptografia, são criptografados na primeira abertura. O backup automático do Google fica desligado (`allowBackup="false"`).
- **Na conexão com a família (ponta a ponta):**
  - Os dois celulares combinam uma chave com ECDH P-256; a chave nunca passa pela internet.
  - As mensagens vão em AES-256-GCM por um servidor de passagem (ntfy, código aberto), que só vê texto embaralhado e apaga em poucas horas.
  - **Aviso na hora (notificação instantânea, opcional):** depois de mandar uma mensagem, o celular pede ao carteiro (`carteiro/carteiro.js`, Cloudflare Workers) para acordar o outro pelo Firebase Cloud Messaging, com um aviso **sem conteúdo**; o outro celular acorda (`android-src/Carteiro.java`) e busca a mensagem criptografada na hora. Vale para "Lembrar no celular", aviso de dose atrasada, socorro, resposta do lembrete e mudanças do gestor. O código de notificação de cada celular só vai para o outro, criptografado. Sem o Firebase configurado, tudo funciona como antes (busca a cada 10-15 minutos). Configuração: `docs/notificacao-instantanea.md`.
  - O código de 6 números serve só para os dois celulares se encontrarem. Ninguém é conectado sem o "Aceitar" de quem toma o remédio.
- **A família vê:** nome, remédios, horários, o que foi marcado e os avisos. **Não vê:** fotos, contatos, localização.
- Testado com dois celulares simulados e um servidor de passagem local: o servidor recebeu 7 mensagens, e nenhuma continha nome, remédio ou horário legível.

## Conformidade (LGPD e Ato Médico)

- **Consentimento específico e destacado** para dados de saúde (LGPD, art. 11, I), com versão nova quando os termos mudam. O aceite é um toque em "Concordo", depois de três frases curtas; os detalhes ficam a um toque, em "Ver detalhes". Há um consentimento próprio para quem acompanha.
- **Medidas de segurança** (LGPD, art. 46): criptografia no aparelho e de ponta a ponta.
- **Compartilhamento só com aceite** de quem toma o remédio, e qualquer um dos dois pode desligar a conexão.
- **Reproduzir, não recomendar** (Lei 12.842/2013): os textos sobre remédios são trechos literais da bula oficial; quem define dose e horário é a receita.
- **Pendente para uma versão com parceiros:**
  - validação do cadastro por farmacêutico;
  - servidor de passagem próprio no Brasil, em vez do ntfy.sh público. O ntfy pode ser instalado em servidor próprio; o endereço fica em `RELAY`, no `www/index.html`.

## Tecnologias

HTML, CSS e JavaScript empacotados como app Android com [Capacitor](https://capacitorjs.com). Leitura com ML Kit (no celular) e ZXing (na web). Voz com o motor de texto em fala e o reconhecimento de voz do Android, alarmes com notificações locais e criptografia com Web Crypto e Android Keystore. Plugins Android próprios em Java: Energia, Cofre, Elo (com EloJob, EloAlarme e Carteiro, que recebe a notificação instantânea do Firebase Cloud Messaging) e Receita (leitura do texto da receita com ML Kit Text Recognition 16.0.1, modelo dentro do app). O APK é gerado pelo GitHub Actions a cada envio para a branch `main`, na versão final (release), assinado com a chave fixa de `keystore/`: instala por cima das versões anteriores sem apagar os remédios. O carteiro (Cloudflare Workers, JavaScript com Web Crypto para assinar o acesso ao Firebase) é publicado pelo GitHub Actions a cada mudança em `carteiro/`. Para publicar na Google Play, troque essa chave por uma guardada nos Secrets do GitHub e gere também o `.aab` (`bundleRelease`).

## Estrutura

```
www/index.html        app (telas, leitura, voz, agenda, conexão com a família)
www/cofre.js          criptografia dos dados guardados no celular
www/receita.js        leitura da receita (teste): separa remédio, dose, quantidade e momento
www/base.json         lista CMED/Anvisa compactada (23/09/2026)
www/bulas.json        69 bulas do paciente oficiais (Bulário Eletrônico da Anvisa)
www/farmacias.json    farmácias da Farmácia Popular no Recife
www/postos.json       postos de saúde do Recife e remédios em estoque (dados abertos)
www/privacidade.html  privacidade e termos (link para a Google Play)
android-src/          plugins Android próprios: Energia, Cofre (Keystore), Elo, EloJob e EloAlarme (família com o app fechado), Carteiro (aviso na hora), Receita (texto da foto)
android-src/res/      ícone do app (adaptativo e monocromático)
carteiro/             carteiro (Cloudflare Workers): acorda o outro celular pelo Firebase, sem ver conteúdo
docs/                 passo a passo da notificação instantânea
scripts/              cópia de bibliotecas, ajustes do Android e conversão dos dados
.github/workflows/    geração automática do APK e publicação do carteiro
```

## Bulas que vêm dentro do app

`scripts/bulas/lista.txt` tem os registros dos remédios da Farmácia Popular (41 itens) e de alguns muito usados por idosos. Para baixar e montar `www/bulas.json`:

```sh
bash scripts/bulas/baixar-bulas.sh   # no seu computador (Linux, Mac ou WSL)
```

Precisa rodar no seu computador porque a API do Bulário da Anvisa bloqueia os servidores do GitHub. O script usa o [buladiff](https://github.com/vasfvitor/buladiff) (MIT) e tira o nome do fabricante, o endereço e o SAC. As bulas que não vêm dentro do app continuam sendo baixadas no celular, na primeira vez, e ficam guardadas.

## Avisos

O BULA é um **lembrete**: não indica nem muda dose e não substitui o médico. Protótipo acadêmico; os textos ainda precisam de revisão de farmacêutico. Os códigos de barras vêm da lista de preços CMED/Anvisa, que pode conter erros.

## Créditos de dados

- Lista de preços CMED/Anvisa e Bulário Eletrônico da Anvisa. A busca de bulas usa a API interna do Bulário, que não é documentada e pode mudar.
- Textos das bulas incluídas: extraídos pelo projeto [buladiff](https://github.com/vasfvitor/buladiff) (licença MIT).
- Postos de saúde: dados abertos da Prefeitura do Recife.

## Licença

Todos os direitos reservados. Veja o arquivo `LICENSE`.

## Autor

Criado pelo Squad 4 · CESAR School (Recife) · 2026.
