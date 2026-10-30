// Ajusta o projeto Android gerado pelo Capacitor:
// permissões, leitor de código de barras, ícone do app e da notificação, motor de voz,
// cofre (Android Keystore) e a conexão com a família que roda com o app fechado (JobScheduler + alarme exato do atraso).
const fs = require('fs'), path = require('path');
const res = path.join(__dirname, '..', 'android', 'app', 'src', 'main');
const manifestPath = path.join(res, 'AndroidManifest.xml');
let m = fs.readFileSync(manifestPath, 'utf8');
const perms = [
  'android.permission.CAMERA',
  'android.permission.POST_NOTIFICATIONS',
  'android.permission.SCHEDULE_EXACT_ALARM',
  'android.permission.RECEIVE_BOOT_COMPLETED',
  'android.permission.WAKE_LOCK',
  'android.permission.READ_CONTACTS',
  'android.permission.WRITE_CONTACTS',
  'android.permission.ACCESS_COARSE_LOCATION',
  'android.permission.ACCESS_FINE_LOCATION',
  'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
  'android.permission.INTERNET',
  'android.permission.ACCESS_NETWORK_STATE',
  'android.permission.RECORD_AUDIO'
];
for (const p of perms) {
  if (!m.includes(`"${p}"`)) m = m.replace('</manifest>', `    <uses-permission android:name="${p}" />\n</manifest>`);
}
if (!m.includes('android.hardware.camera')) m = m.replace('</manifest>', '    <uses-feature android:name="android.hardware.camera" android:required="false" />\n</manifest>');
if (!m.includes('android.intent.action.TTS_SERVICE')) {
  m = m.replace('</manifest>', '    <queries>\n        <intent>\n            <action android:name="android.intent.action.TTS_SERVICE" />\n        </intent>\n    </queries>\n</manifest>');
}
// baixa o leitor de código de barras do Google junto com a instalação do app
if (!m.includes('com.google.mlkit.vision.DEPENDENCIES')) {
  m = m.replace('</application>', '        <meta-data android:name="com.google.mlkit.vision.DEPENDENCIES" android:value="barcode_ui" />\n    </application>');
}
// conexão com a família com o app fechado: tarefa do Android (JobScheduler), sem biblioteca extra
if (!m.includes('br.com.remediofalado.app.EloJob')) {
  m = m.replace('</application>', '        <service android:name="br.com.remediofalado.app.EloJob" android:permission="android.permission.BIND_JOB_SERVICE" android:exported="false" />\n    </application>');
}
// aviso de atraso para a família na hora certa: alarme exato 30 minutos depois da dose (e remarca ao ligar o celular)
if (!m.includes('br.com.remediofalado.app.EloAlarme')) {
  m = m.replace('</application>', `        <receiver android:name="br.com.remediofalado.app.EloAlarme" android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" />
            </intent-filter>
        </receiver>
    </application>`);
}
// notificação instantânea: recebe o "acorde" do carteiro (Firebase) e busca as mensagens na hora
if (!m.includes('br.com.remediofalado.app.Carteiro')) {
  m = m.replace('</application>', `        <service android:name="br.com.remediofalado.app.Carteiro" android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
    </application>`);
}
// receita pelo "Compartilhar" do Android: PDF, foto ou texto (ex.: receita digital que chegou no WhatsApp)
if (!m.includes('android.intent.action.SEND')) {
  const filtro = mime => `\n            <intent-filter>\n                <action android:name="android.intent.action.SEND" />\n                <category android:name="android.intent.category.DEFAULT" />\n                <data android:mimeType="${mime}" />\n            </intent-filter>`;
  m = m.replace('</intent-filter>', '</intent-filter>' + ['application/pdf', 'image/*', 'text/plain'].map(filtro).join(''));
}
// dados de saúde não vão para o backup automático do Google (ficam criptografados só neste aparelho)
m = m.replace(/android:allowBackup="true"/, 'android:allowBackup="false"');
if (!m.includes('android:allowBackup')) m = m.replace('<application', '<application android:allowBackup="false"');
fs.writeFileSync(manifestPath, m);
// ícone da barra de notificação: a marca (o B em forma de balão de fala), em branco
const draw = path.join(res, 'res', 'drawable');
fs.mkdirSync(draw, { recursive: true });
fs.writeFileSync(path.join(draw, 'ic_stat_icon.xml'),
`<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="64" android:viewportHeight="64">
  <group android:translateX="14" android:translateY="9">
    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="M6 0H22C29.5 0 34 4.2 34 10C34 13.4 32.3 16.2 29.6 17.7C33.8 19.3 36 22.8 36 27C36 34.2 30.6 39 23 39H11.5L3.4 45.3C1.9 46.4 0 45.7 0 43.8V6C0 2.7 2.7 0 6 0ZM9.4 12.6H21.1A2.4 2.4 0 0 1 21.1 17.4H9.4A2.4 2.4 0 0 1 9.4 12.6ZM9.4 21.2H15.6A2.4 2.4 0 0 1 15.6 26.0H9.4A2.4 2.4 0 0 1 9.4 21.2Z"/>
  </group>
</vector>
`);
// ícone do app (marca BULA): adaptativo (Android 8+), com versão monocromática (Android 13+) e PNGs para celulares antigos
(function copiar(de, para) {
  for (const n of fs.readdirSync(de)) {
    const a = path.join(de, n), b = path.join(para, n);
    if (fs.statSync(a).isDirectory()) { fs.mkdirSync(b, { recursive: true }); copiar(a, b); }
    else fs.copyFileSync(a, b);
  }
})(path.join(__dirname, '..', 'android-src', 'res'), path.join(res, 'res'));
// abertura do app: fundo azul com a marca no centro (sem a imagem padrão do Capacitor, que esticava)
for (const d of fs.readdirSync(path.join(res, 'res')).filter(n => n.startsWith('drawable'))) {
  const f = path.join(res, 'res', d, 'splash.png');
  if (fs.existsSync(f)) fs.unlinkSync(f);
}
fs.writeFileSync(path.join(draw, 'splash.xml'),
`<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@color/ic_launcher_background"/>
    <item><bitmap android:gravity="center" android:src="@mipmap/ic_launcher_foreground"/></item>
</layer-list>
`);
const stylesPath = path.join(res, 'res', 'values', 'styles.xml');
let st = fs.readFileSync(stylesPath, 'utf8');
if (!st.includes('windowSplashScreenBackground')) {
  st = st.replace('<item name="android:background">@drawable/splash</item>',
    '<item name="android:background">@drawable/splash</item>\n        <item name="windowSplashScreenBackground">@color/ic_launcher_background</item>');
  fs.writeFileSync(stylesPath, st);
}
// assinatura fixa: assim cada APK novo instala por cima do anterior, sem apagar os dados.
// A versão "release" (a que vai para as pessoas) usa a mesma chave da antiga versão de teste,
// então quem já tem o app instalado só atualiza, sem perder os remédios.
const gradlePath = path.join(__dirname, '..', 'android', 'app', 'build.gradle');
let g = fs.readFileSync(gradlePath, 'utf8');
if (!g.includes('remedio-falado-teste.jks')) {
  const chave = `
            storeFile file("../../keystore/remedio-falado-teste.jks")
            storePassword "remediofalado"
            keyAlias "remediofalado"
            keyPassword "remediofalado"`;
  g = g.replace(/buildTypes \{\s*release \{/, m0 => m0 + '\n            signingConfig signingConfigs.release');
  g = g.replace(/android \{/, `android {
    signingConfigs {
        debug {${chave}
        }
        release {${chave}
        }
    }
    // a checagem automática do Android não trava a versão final (os testes do app rodam à parte)
    lint {
        checkReleaseBuilds false
        abortOnError false
    }`);
  if (!g.includes('signingConfig signingConfigs.release')) throw new Error('Não achei buildTypes.release no build.gradle');
}
// plugins próprios: "Energia" (bateria e início automático), "Cofre" (chave no Android Keystore), "Elo" (família) e "Receita" (ler a receita)
const javaDir = path.join(res, 'java', 'br', 'com', 'remediofalado', 'app');
fs.mkdirSync(javaDir, { recursive: true });
for (const f of fs.readdirSync(path.join(__dirname, '..', 'android-src')).filter(n => n.endsWith('.java'))) {
  fs.copyFileSync(path.join(__dirname, '..', 'android-src', f), path.join(javaDir, f));
}
// leitura da receita pela foto (teste): reconhecimento de texto do Google (ML Kit), modelo dentro do app, sem internet
if (!g.includes('com.google.mlkit:text-recognition')) {
  g = g.replace(/dependencies \{/, "dependencies {\n    implementation 'com.google.mlkit:text-recognition:16.0.1'");
}
// notificação instantânea: Firebase Cloud Messaging (só funciona se o build tiver o google-services.json; sem ele, o app segue como antes)
if (!g.includes('firebase-messaging')) {
  g = g.replace(/dependencies \{/, "dependencies {\n    implementation 'com.google.firebase:firebase-messaging:24.1.0'");
}
// APK menor: só o código nativo para processadores ARM, os de praticamente todo celular Android (Samsung, Motorola,
// Xiaomi, Redmi, Poco, Realme, Oppo...), em 64 e 32 bits (este cobre os mais baratos e antigos). Sai o de processador
// Intel (x86), que hoje só aparece no emulador do computador: para testar no emulador, use uma imagem ARM.
if (!g.includes('abiFilters')) {
  g = g.replace(/defaultConfig \{/, "defaultConfig {\n        ndk { abiFilters 'arm64-v8a', 'armeabi-v7a' }");
  if (!g.includes('abiFilters')) throw new Error('Não achei defaultConfig no build.gradle');
}
// número da versão sobe a cada compilação no GitHub
const run = parseInt(process.env.GITHUB_RUN_NUMBER || '1', 10);
g = g.replace(/versionCode \d+/, 'versionCode ' + run).replace(/versionName "[^"]*"/, 'versionName "1.0.' + run + '"');
fs.writeFileSync(gradlePath, g);
console.log('Android ajustado: permissões, ícone do app e da notificação, abertura, voz, bateria, cofre, conexão com a família, leitura e compartilhar da receita.');
