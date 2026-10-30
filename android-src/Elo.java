package br.com.remediofalado.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Conexão com a família, com o app fechado.
 * A cada 15 minutos (JobScheduler do Android):
 *  - no celular de quem toma o remédio: se uma dose passou 30 minutos sem ser marcada, manda o aviso para a família
 *    (e um alarme exato, EloAlarme, acorda o celular 31 minutos depois de cada dose para o aviso não atrasar e,
 *    enquanto a dose não for marcada, de 10 em 10 minutos por até 3 horas: assim o "Lembrar no celular" da família chega rápido);
 *  - nos dois celulares: busca mensagens novas e mostra a notificação;
 *  - no celular de quem cuida dos remédios (modo gestor): reenvia, de 6 em 6 horas, a mudança que o outro celular ainda não confirmou;
 *  - no celular de quem acompanha, se a pessoa ligou o "resumo do dia": às 21h, mostra quantas doses foram marcadas no dia;
 *  - no celular de quem acompanha, se escolheu "a cada dose": quando a pessoa marca TOMEI, uma notificação SEM SOM
 *    (canal próprio, "Remédio tomado"), sempre a mesma por pessoa, trocada a cada dose. Atraso e socorro continuam no canal com som.
 * Tudo que passa pelo servidor vai criptografado de ponta a ponta (AES-256-GCM). O servidor não consegue ler.
 * Notificação instantânea (se o carteiro estiver configurado): depois de mandar um aviso, pede ao carteiro para acordar
 * o outro celular pelo Firebase, sem conteúdo; ele busca a mensagem na hora (ver Carteiro.java).
 */
final class Elo {
    static final int JOB_PERIODICO = 7101;
    static final int JOB_AGORA = 7102;
    private static final String PREFS = "remedio_falado_elo";
    private static final String CANAL = "familia";
    private static final String CANAL_TOMOU = "tomou";
    private static final long PULSO_MS = 2L * 60 * 60 * 1000;
    private static final long REENVIO_MS = 6L * 60 * 60 * 1000;
    private static final long DESISTE_MS = 3L * 24 * 60 * 60 * 1000;
    static final int CONFERE_CADA_MIN = 10;
    static final int CONFERE_ATE_MIN = 181;

    private Elo() {}

    /* carteiro: endereço e o código de notificação (token do Firebase) do outro celular em cada canal */
    private static String carteiro = "";
    private static final java.util.Map<String, String> ptPorCanal = new java.util.HashMap<>();

    static void lerCarteiro(JSONObject cfg) {
        carteiro = cfg == null ? "" : cfg.optString("carteiro", "").replaceAll("/+$", "");
        ptPorCanal.clear();
        JSONArray c = cfg == null ? null : cfg.optJSONArray("canais");
        if (c != null) for (int i = 0; i < c.length(); i++) {
            JSONObject x = c.optJSONObject(i);
            if (x != null && !x.optString("pt").isEmpty()) ptPorCanal.put(x.optString("topic"), x.optString("pt"));
        }
    }

    /** pede ao carteiro para acordar o outro celular deste canal (sem conteúdo; falha em silêncio) */
    static void acordar(String topic) {
        String pt = ptPorCanal.get(topic);
        if (carteiro.isEmpty() || !carteiro.startsWith("https://") || pt == null || pt.isEmpty()) return;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(carteiro + "/acordar").openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            JSONObject b = new JSONObject(); b.put("token", pt);
            try (OutputStream o = c.getOutputStream()) { o.write(b.toString().getBytes(StandardCharsets.UTF_8)); }
            c.getResponseCode();
            c.disconnect();
        } catch (Exception e) { /* sem carteiro: o outro celular busca na rodada normal */ }
    }

    static void publicarEAcordar(String relay, String topic, String corpo) throws Exception {
        publicar(relay, topic, corpo);
        acordar(topic);
    }

    static void salvarConfig(Context ctx, String json) throws Exception {
        Cofre.guardar(ctx, "elo", json);
    }

    static JSONObject config(Context ctx) {
        try {
            String s = Cofre.ler(ctx, "elo");
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) { return null; }
    }

    static boolean temCanais(JSONObject cfg) {
        JSONArray c = cfg == null ? null : cfg.optJSONArray("canais");
        return c != null && c.length() > 0;
    }

    static void agendar(Context ctx) {
        try { agendarAtraso(ctx); } catch (Throwable t) { }
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!temCanais(config(ctx))) { js.cancel(JOB_PERIODICO); return; }
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_PERIODICO) return;
        js.schedule(new JobInfo.Builder(JOB_PERIODICO, new ComponentName(ctx, EloJob.class))
                .setPeriodic(15L * 60 * 1000)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build());
    }

    /* alarme exato 31 minutos depois da próxima dose ainda não marcada e, enquanto ela continuar sem marcar,
       de 10 em 10 minutos até 3 horas depois (só no celular de quem toma). O Android aceita um alarme assim a cada ~9 minutos. */
    static void agendarAtraso(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, EloAlarme.class);
        i.setAction(EloAlarme.ACAO);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 7103, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long quando = proximaConferencia(config(ctx), System.currentTimeMillis());
        if (quando <= 0) { am.cancel(pi); return; }
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi);
        }
    }

    static long proximaConferencia(JSONObject cfg, long agora) {
        if (cfg == null) return 0;
        JSONArray canais = cfg.optJSONArray("canais"), doses = cfg.optJSONArray("doses");
        if (canais == null || doses == null || doses.length() == 0) return 0;
        boolean meu = false;
        for (int i = 0; i < canais.length(); i++) {
            JSONObject c = canais.optJSONObject(i);
            if (c != null && "idoso".equals(c.optString("papel", "idoso"))) meu = true;
        }
        if (!meu) return 0;
        Set<String> tomados = new HashSet<>();
        JSONArray t = cfg.optJSONArray("tomados");
        boolean cfgHoje = hoje().equals(cfg.optString("dia"));
        if (cfgHoje && t != null) for (int i = 0; i < t.length(); i++) tomados.add(t.optString(i));
        long melhor = 0;
        for (int i = 0; i < doses.length(); i++) {
            JSONObject d = doses.optJSONObject(i);
            if (d == null) continue;
            String h = d.optString("h");
            int m = toMin(h);
            if (m < 0) continue;
            achou:
            for (int dia = 0; dia <= 1; dia++) {
                if (dia == 0 && cfgHoje && tomados.contains(h)) continue;
                // hoje: 31, 41, 51... até 181 minutos depois da dose; amanhã: só o primeiro (31 minutos)
                for (int k = 31; k <= (dia == 0 ? CONFERE_ATE_MIN : 31); k += CONFERE_CADA_MIN) {
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(agora);
                    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
                    c.add(Calendar.DAY_OF_MONTH, dia);
                    c.add(Calendar.MINUTE, m + k);
                    long q = c.getTimeInMillis();
                    if (q > agora + 5000) { if (melhor == 0 || q < melhor) melhor = q; break achou; }
                }
            }
        }
        return melhor;
    }

    static void agora(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null || !temCanais(config(ctx))) return;
        js.schedule(new JobInfo.Builder(JOB_AGORA, new ComponentName(ctx, EloJob.class))
                .setMinimumLatency(0)
                .setOverrideDeadline(10000)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build());
    }

    /* ---------------- o trabalho de cada rodada ---------------- */
    static synchronized void executar(Context ctx) {
        JSONObject cfg = config(ctx);
        if (!temCanais(cfg)) return;
        SharedPreferences st = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String relay = cfg.optString("relay", "https://ntfy.sh");
        JSONArray canais = cfg.optJSONArray("canais");
        lerCarteiro(cfg);

        // canais em que este celular é de quem toma o remédio (os outros: este celular acompanha alguém)
        JSONArray meus = new JSONArray();
        for (int i = 0; i < canais.length(); i++) {
            JSONObject c = canais.optJSONObject(i);
            if (c != null && "idoso".equals(c.optString("papel", "idoso"))) meus.put(c);
        }
        if (meus.length() > 0) avisarAtrasos(ctx, cfg, st, relay, meus);
        Set<String> tomadosHoje = new HashSet<>();
        JSONArray tj = cfg.optJSONArray("tomados");
        if (hoje().equals(cfg.optString("dia")) && tj != null) for (int i = 0; i < tj.length(); i++) tomadosHoje.add(tj.optString(i));
        String eu = cfg.optString("eu", "");

        for (int i = 0; i < canais.length(); i++) {
            JSONObject c = canais.optJSONObject(i);
            if (c == null) continue;
            String papel = c.optString("papel", "idoso");
            String topic = c.optString("topic"), key = c.optString("key");
            if (topic.isEmpty() || key.isEmpty()) continue;
            JSONArray pend = c.optJSONArray("pend");
            try { buscar(ctx, st, relay, topic, key, papel, c.optLong("visto", 0), pend, c.optString("estado", ""), tomadosHoje, eu, c.optBoolean("tomou", false)); } catch (Exception e) { /* sem internet: tenta na próxima */ }
            if ("familia".equals(papel) && pend != null && pend.length() > 0) reenviar(st, relay, topic, pend);
            // sinal de vida: reenvia o último resumo de 2 em 2 horas (já criptografado pelo app)
            String estado = c.optString("estado", "");
            if ("idoso".equals(papel) && !estado.isEmpty() && System.currentTimeMillis() - st.getLong("pulso:" + topic, 0) > PULSO_MS) {
                try { publicarTudo(relay, topic, estado); st.edit().putLong("pulso:" + topic, System.currentTimeMillis()).apply(); } catch (Exception e) { }
            }
        }
        try { resumoDoDia(ctx, cfg, st, canais); } catch (Exception e) { /* o resumo nunca atrapalha o resto */ }
    }

    /* ---------------- resumo do dia (celular de quem acompanha, só se a pessoa ligou) ---------------- */
    static final long SEM_NOTICIA_MS = 6L * 60 * 60 * 1000;

    /** o que interessa do resumo que chega do outro celular: as doses do dia ("id|hora") e as marcadas ("dia|id|hora") */
    static JSONObject resumoDeEstado(JSONObject e, String hoje, String ontem) {
        JSONObject r = new JSONObject();
        try {
            r.put("ts", e.optLong("ts", 0));
            JSONArray d = new JSONArray(), ok = new JSONArray();
            JSONArray ag = e.optJSONArray("ag");
            if (ag != null) for (int i = 0; i < ag.length(); i++) {
                JSONObject a = ag.optJSONObject(i);
                JSONArray hs = a == null ? null : a.optJSONArray("h");
                if (hs != null) for (int j = 0; j < hs.length(); j++) d.put(a.optString("id") + "|" + hs.optString(j));
            }
            JSONArray log = e.optJSONArray("log");
            if (log != null) for (int i = 0; i < log.length(); i++) {
                JSONArray l = log.optJSONArray(i);
                if (l == null) continue;
                String dia = l.optString(0);
                if (dia.equals(hoje) || dia.equals(ontem)) ok.put(dia + "|" + l.optString(1) + "|" + l.optString(2));
            }
            r.put("d", d); r.put("ok", ok);
        } catch (Exception x) { }
        return r;
    }

    private static void guardarResumo(SharedPreferences st, String topic, JSONObject estado) {
        JSONObject r = resumoDeEstado(estado, hoje(), diaAntes(1));
        SharedPreferences.Editor ed = st.edit().putLong("ult:" + topic, System.currentTimeMillis());
        try {
            JSONObject velho = new JSONObject(st.getString("hj:" + topic, "{}"));
            if (velho.optLong("ts", 0) <= r.optLong("ts", 0)) ed.putString("hj:" + topic, r.toString());
        } catch (Exception e) { ed.putString("hj:" + topic, r.toString()); }
        ed.apply();
    }

    /** {título, texto} do resumo; null quando não há dose até agora */
    static String[] textoResumo(String nome, JSONObject r, long ultima, int agoraMin, String dia, long agoraMs) {
        if (r == null) return null;
        JSONArray d = r.optJSONArray("d"), okj = r.optJSONArray("ok");
        if (d == null) return null;
        Set<String> ok = new HashSet<>();
        if (okj != null) for (int i = 0; i < okj.length(); i++) {
            String x = okj.optString(i);
            if (x.startsWith(dia + "|")) ok.add(x.substring(dia.length() + 1));
        }
        int tot = 0, feitos = 0;
        for (int i = 0; i < d.length(); i++) {
            String x = d.optString(i);
            int bar = x.lastIndexOf('|');
            if (bar < 0) continue;
            int m = toMin(x.substring(bar + 1));
            if (m < 0 || m > agoraMin) continue;
            tot++;
            if (ok.contains(x)) feitos++;
        }
        if (tot == 0) return null;
        String quem = nome == null || nome.trim().isEmpty() ? "a pessoa" : nome.trim();
        String texto = feitos == tot
                ? "Hoje, " + quem + " marcou " + (tot == 1 ? "a dose do dia" : "as " + tot + " doses do dia") + " até agora."
                : "Hoje, " + quem + " marcou " + feitos + " de " + tot + " doses. " + (tot - feitos == 1 ? "Faltou marcar 1." : "Faltou marcar " + (tot - feitos) + ".") + " Vale ligar.";
        if (ultima > 0 && agoraMs - ultima > SEM_NOTICIA_MS) texto += " O celular de " + quem + " está sem dar notícia há mais de 6 horas.";
        return new String[] { "Resumo do dia de " + quem, texto };
    }

    private static void resumoDoDia(Context ctx, JSONObject cfg, SharedPreferences st, JSONArray canais) {
        int agora = minutosAgora();
        if (agora < cfg.optInt("resumoMin", 21 * 60)) return;
        String dia = hoje();
        long ms = System.currentTimeMillis();
        for (int i = 0; i < canais.length(); i++) {
            JSONObject c = canais.optJSONObject(i);
            if (c == null || !"familia".equals(c.optString("papel")) || !c.optBoolean("resumo", false)) continue;
            String topic = c.optString("topic");
            if (dia.equals(st.getString("resumoDia:" + topic, ""))) continue;
            // o mais novo entre o que o app passou e o que chegou com o app fechado
            JSONObject r = c.optJSONObject("hj");
            try {
                JSONObject g = new JSONObject(st.getString("hj:" + topic, "{}"));
                if (r == null || g.optLong("ts", 0) > r.optLong("ts", 0)) r = g;
            } catch (Exception e) { }
            long ultima = Math.max(c.optLong("ult", 0), st.getLong("ult:" + topic, 0));
            String[] t = textoResumo(c.optString("nome"), r, ultima, agora, dia, ms);
            if (t != null) notificar(ctx, t[0], t[1], ("resumo:" + topic).hashCode());
            st.edit().putString("resumoDia:" + topic, dia).apply();
        }
    }

    private static int minutosAgora() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private static String hoje() {
        Calendar c = Calendar.getInstance();
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static int toMin(String hm) {
        try { String[] p = hm.split(":"); return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]); } catch (Exception e) { return -1; }
    }

    private static void avisarAtrasos(Context ctx, JSONObject cfg, SharedPreferences st, String relay, JSONArray canais) {
        String dia = hoje();
        int agora = minutosAgora();
        Set<String> tomados = new HashSet<>();
        if (dia.equals(cfg.optString("dia"))) {
            JSONArray t = cfg.optJSONArray("tomados");
            if (t != null) for (int i = 0; i < t.length(); i++) tomados.add(t.optString(i));
        }
        Set<String> avisados = new HashSet<>(st.getStringSet("avisados", new HashSet<String>()));
        JSONArray jsAvisados = cfg.optJSONArray("avisados");
        if (jsAvisados != null) for (int i = 0; i < jsAvisados.length(); i++) avisados.add(jsAvisados.optString(i));
        JSONArray doses = cfg.optJSONArray("doses");
        if (doses == null) return;
        boolean mudou = false;
        for (int i = 0; i < doses.length(); i++) {
            JSONObject d = doses.optJSONObject(i);
            if (d == null) continue;
            String h = d.optString("h");
            int m = toMin(h);
            if (m < 0 || agora < m + 30 || agora > m + 240 || tomados.contains(h)) continue;
            String k = dia + "|" + h;
            if (avisados.contains(k)) continue;
            try {
                JSONObject msg = new JSONObject();
                msg.put("de", "idoso"); msg.put("t", "atraso"); msg.put("dia", dia); msg.put("h", h);
                msg.put("ts", System.currentTimeMillis());
                JSONObject av = new JSONObject();
                av.put("titulo", d.optString("titulo")); av.put("texto", d.optString("texto"));
                msg.put("aviso", av);
                for (int j = 0; j < canais.length(); j++) {
                    JSONObject c = canais.optJSONObject(j);
                    if (c == null) continue;
                    publicarEAcordar(relay, c.optString("topic"), cifrar(c.optString("key"), msg.toString()));
                }
                avisados.add(k); mudou = true;
            } catch (Exception e) { /* sem internet: tenta na próxima rodada */ }
        }
        if (mudou) {
            // guarda só os dos últimos dias
            Set<String> recentes = new HashSet<>();
            for (String a : avisados) if (a.compareTo(diaAntes(3)) >= 0) recentes.add(a);
            st.edit().putStringSet("avisados", recentes).apply();
        }
    }

    private static String diaAntes(int n) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -n);
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /* modo gestor: a mudança vai de novo enquanto o celular de quem toma não confirmar (o servidor guarda só por algumas horas) */
    private static void reenviar(SharedPreferences st, String relay, String topic, JSONArray pend) {
        Set<String> conf = st.getStringSet("conf:" + topic, new HashSet<String>());
        long agora = System.currentTimeMillis();
        SharedPreferences.Editor ed = st.edit();
        for (int i = 0; i < pend.length(); i++) {
            JSONObject p = pend.optJSONObject(i);
            if (p == null) continue;
            String uid = p.optString("uid"), cif = p.optString("cif");
            long ts = p.optLong("ts", agora);
            if (uid.isEmpty() || cif.isEmpty() || conf.contains(uid) || agora - ts > DESISTE_MS) continue;
            long ultimo = st.getLong("reenv:" + uid, ts);
            if (agora - ultimo < REENVIO_MS) continue;
            try { publicarEAcordar(relay, topic, cif); ed.putLong("reenv:" + uid, agora); } catch (Exception e) { break; }
        }
        ed.apply();
    }

    /* o resumo que chega do celular de quem toma confirma o que já foi aplicado */
    private static void confirmar(SharedPreferences st, String topic, JSONObject estado, JSONArray pend) {
        if (pend == null) return;
        Set<String> ids = new HashSet<>();
        JSONArray ag = estado.optJSONArray("ag");
        if (ag != null) for (int i = 0; i < ag.length(); i++) { JSONObject a = ag.optJSONObject(i); if (a != null) ids.add(a.optString("id")); }
        long ts = estado.optLong("ts", 0);
        Set<String> conf = new HashSet<>(st.getStringSet("conf:" + topic, new HashSet<String>()));
        for (int i = 0; i < pend.length(); i++) {
            JSONObject p = pend.optJSONObject(i);
            if (p == null || ts <= p.optLong("ts", 0)) continue;
            boolean tem = ids.contains(p.optString("id"));
            if ("del".equals(p.optString("op")) ? !tem : tem) conf.add(p.optString("uid"));
        }
        if (conf.size() > 80) conf.clear();
        st.edit().putStringSet("conf:" + topic, conf).apply();
    }

    private static void buscar(Context ctx, SharedPreferences st, String relay, String topic, String key, String papel, long vistoApp, JSONArray pend, String estado,
                               Set<String> tomadosHoje, String eu, boolean avisoTomou) throws Exception {
        long desde = Math.max(st.getLong("desde:" + topic, 0), vistoApp);
        if (desde == 0) desde = System.currentTimeMillis() / 1000 - 15 * 60;
        Set<String> vistos = new HashSet<>(st.getStringSet("ids:" + topic, new HashSet<String>()));
        Set<String> uids = new HashSet<>(st.getStringSet("uids:" + topic, new HashSet<String>()));
        Set<String> acks = new HashSet<>(st.getStringSet("acks:" + topic, new HashSet<String>()));
        HttpURLConnection c = (HttpURLConnection) new URL(relay + "/" + topic + "/json?poll=1&since=" + desde).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000);
        long maior = desde;
        java.util.Map<String, String[]> partes = new java.util.HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = r.readLine()) != null) {
                if (linha.trim().isEmpty()) continue;
                JSONObject ev = new JSONObject(linha);
                if (!"message".equals(ev.optString("event"))) continue;
                String id = ev.optString("id");
                long t = ev.optLong("time", 0);
                if (t > maior) maior = t;
                if (vistos.contains(id) || t < vistoApp) continue;
                vistos.add(id);
                String corpo = ev.optString("message");
                // mensagem grande chega em partes (rfp1.<id>.<i>.<n>.<pedaço>): junta antes de abrir
                if (corpo.startsWith("rfp1.")) {
                    String[] q = corpo.split("\\.", 5);
                    if (q.length < 5) continue;
                    int i, n;
                    try { i = Integer.parseInt(q[2]); n = Integer.parseInt(q[3]); } catch (Exception e) { continue; }
                    if (n < 1 || n > 20 || i < 0 || i >= n) continue;
                    String[] ps = partes.get(q[1]);
                    if (ps == null) { ps = new String[n]; partes.put(q[1], ps); }
                    if (ps.length != n) continue;
                    ps[i] = q[4];
                    StringBuilder sb = new StringBuilder();
                    boolean completo = true;
                    for (String x : ps) { if (x == null) { completo = false; break; } sb.append(x); }
                    if (!completo) continue;
                    partes.remove(q[1]);
                    corpo = sb.toString();
                }
                String txt;
                try { txt = decifrar(key, corpo); } catch (Exception e) { continue; }
                JSONObject msg = new JSONObject(txt);
                if (papel.equals(msg.optString("de"))) continue;
                if ("familia".equals(papel) && "estado".equals(msg.optString("t"))) { guardarResumo(st, topic, msg); confirmar(st, topic, msg, pend); continue; }
                boolean recente = System.currentTimeMillis() - t * 1000 < 30L * 60 * 1000;
                // a família pediu o resumo atualizado (ao abrir o app): manda o último, com o app fechado
                if ("idoso".equals(papel) && "pedirEstado".equals(msg.optString("t")) && recente && !estado.isEmpty()
                        && System.currentTimeMillis() - st.getLong("respEstado:" + topic, 0) > 60 * 1000) {
                    try { publicarTudo(relay, topic, estado); st.edit().putLong("respEstado:" + topic, System.currentTimeMillis()).apply(); } catch (Exception e) { }
                    continue;
                }
                // teste de conexão: responde "estou aqui"
                if ("ping".equals(msg.optString("t")) && recente) {
                    try {
                        JSONObject resp = new JSONObject();
                        resp.put("de", papel); resp.put("t", "pong"); resp.put("pid", msg.optString("pid")); resp.put("ts", System.currentTimeMillis()); resp.put("via", "android");
                        publicar(relay, topic, cifrar(key, resp.toString()));
                    } catch (Exception e) { }
                    continue;
                }
                // pedido de ajuda: responde "recebi" para o celular de quem pediu saber que o aviso chegou
                if ("familia".equals(papel) && "socorro".equals(msg.optString("t")) && !msg.optString("sid").isEmpty()
                        && !acks.contains(msg.optString("sid")) && System.currentTimeMillis() - t * 1000 < 30L * 60 * 1000) {
                    try {
                        JSONObject resp = new JSONObject();
                        resp.put("de", "familia"); resp.put("t", "recebi"); resp.put("sid", msg.optString("sid")); resp.put("ts", System.currentTimeMillis());
                        publicarEAcordar(relay, topic, cifrar(key, resp.toString()));
                        acks.add(msg.optString("sid"));
                    } catch (Exception e) { /* tenta na próxima rodada */ }
                }
                // a família pediu para lembrar: mostra o lembrete (se a dose ainda não foi marcada) e conta para a família que chegou
                if ("idoso".equals(papel) && "lembrar".equals(msg.optString("t"))) {
                    if (System.currentTimeMillis() - t * 1000 > 60L * 60 * 1000) continue;
                    String lid = msg.optString("lid", "");
                    if (!lid.isEmpty() && acks.contains("l:" + lid)) continue;
                    String h = msg.optString("h");
                    boolean tomou = tomadosHoje.contains(h);
                    JSONObject av = msg.optJSONObject("aviso");
                    if (!tomou && av != null) notificar(ctx, av.optString("titulo"), av.optString("texto"), id.hashCode());
                    if (!lid.isEmpty()) {
                        try {
                            publicarEAcordar(relay, topic, cifrar(key, respostaLembrete(lid, h, tomou, eu, System.currentTimeMillis()).toString()));
                            acks.add("l:" + lid);
                        } catch (Exception e) { /* sem internet agora: a família continua vendo "esperando" */ }
                    }
                    continue;
                }
                // a pessoa marcou TOMEI (ou desfez): notificação sem som, sempre a mesma por pessoa (troca a anterior)
                if ("familia".equals(papel) && "tomou".equals(msg.optString("t"))) {
                    JSONObject inf = msg.optJSONObject("info");
                    if (avisoTomou && inf != null && recente) notificarTomou(ctx, inf.optString("titulo"), inf.optString("texto"), ("tomou:" + topic).hashCode());
                    continue;
                }
                String uid = msg.optString("uid", "");
                if (!uid.isEmpty()) { if (uids.contains(uid)) continue; uids.add(uid); } // mudança reenviada: avisa uma vez só
                JSONObject av = msg.optJSONObject("aviso");
                if (av != null) notificar(ctx, av.optString("titulo"), av.optString("texto"), id.hashCode());
            }
        } finally { c.disconnect(); }
        if (vistos.size() > 60) vistos.clear();
        if (uids.size() > 80) uids.clear();
        if (acks.size() > 40) acks.clear();
        st.edit().putLong("desde:" + topic, maior).putStringSet("ids:" + topic, vistos).putStringSet("uids:" + topic, uids)
                .putStringSet("acks:" + topic, acks).apply();
    }

    /* resposta para a família: o lembrete apareceu (ou a dose já estava marcada) */
    static JSONObject respostaLembrete(String lid, String h, boolean tomou, String eu, long agora) throws Exception {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(agora);
        String hm = String.format(java.util.Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        String quem = eu == null || eu.trim().isEmpty() ? "" : eu.trim();
        JSONObject r = new JSONObject();
        r.put("de", "idoso"); r.put("t", "lembrete-ok"); r.put("lid", lid); r.put("h", h); r.put("tomou", tomou); r.put("ts", agora); r.put("via", "android");
        r.put("uid", "ack-" + lid); // o celular da família avisa uma vez só, mesmo que o app e o Android respondam
        JSONObject av = new JSONObject();
        if (tomou) {
            av.put("titulo", quem.isEmpty() ? "Os remédios já estavam marcados" : quem + " já tinha marcado os remédios");
            av.put("texto", "Os remédios das " + h + " já estavam marcados como tomados.");
        } else {
            av.put("titulo", quem.isEmpty() ? "O lembrete chegou" : "O lembrete chegou para " + quem);
            av.put("texto", "O celular mostrou o lembrete dos remédios das " + h + " às " + hm + ".");
        }
        r.put("aviso", av);
        return r;
    }

    /* ---------------- servidor de passagem (ntfy) ---------------- */
    /** o app guarda a mensagem grande já dividida em partes, uma por linha */
    static void publicarTudo(String relay, String topic, String corpo) throws Exception {
        for (String parte : corpo.split("\n")) if (!parte.isEmpty()) publicar(relay, topic, parte);
    }

    static void publicar(String relay, String topic, String corpo) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(relay + "/" + topic).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000);
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        byte[] b = corpo.getBytes(StandardCharsets.UTF_8);
        try (OutputStream o = c.getOutputStream()) { o.write(b); }
        int code = c.getResponseCode();
        c.disconnect();
        if (code >= 300) throw new Exception("relay " + code);
    }

    /* ---------------- criptografia de ponta a ponta: rfe1.<iv>.<texto cifrado> ---------------- */
    private static final int B64 = Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP;

    static String cifrar(String chaveB64, String texto) throws Exception {
        byte[] k = Base64.decode(chaveB64, B64);
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        return "rfe1." + Base64.encodeToString(iv, B64) + "." + Base64.encodeToString(ct, B64);
    }

    static String decifrar(String chaveB64, String env) throws Exception {
        String[] p = env.split("\\.");
        if (p.length != 3 || !"rfe1".equals(p[0])) throw new Exception("formato");
        byte[] k = Base64.decode(chaveB64, B64);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(128, Base64.decode(p[1], B64)));
        return new String(c.doFinal(Base64.decode(p[2], B64)), StandardCharsets.UTF_8);
    }

    /* ---------------- notificação ---------------- */
    static void notificar(Context ctx, String titulo, String texto, int id) { notificarEm(ctx, CANAL, titulo, texto, id); }

    /** "marcou que tomou": canal sem som e sem vibrar; a família pode desligar só este nas configurações do Android */
    static void notificarTomou(Context ctx, String titulo, String texto, int id) { notificarEm(ctx, CANAL_TOMOU, titulo, texto, id); }

    private static void notificarEm(Context ctx, String canal, String titulo, String texto, int id) {
        boolean alto = CANAL.equals(canal);
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = alto
                        ? new NotificationChannel(CANAL, "Avisos da família", NotificationManager.IMPORTANCE_HIGH)
                        : new NotificationChannel(CANAL_TOMOU, "Remédio tomado (sem som)", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription(alto ? "Avisos do celular de quem você acompanha, ou da família que acompanha você."
                        : "Quando a pessoa que você acompanha marca que tomou o remédio. Não toca nem vibra.");
                nm.createNotificationChannel(ch);
            }
            Intent abrir = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            PendingIntent pi = abrir == null ? null : PendingIntent.getActivity(ctx, 0, abrir, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            int icone = ctx.getResources().getIdentifier("ic_stat_icon", "drawable", ctx.getPackageName());
            if (icone == 0) icone = android.R.drawable.ic_dialog_info;
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, canal) : new Notification.Builder(ctx);
            b.setSmallIcon(icone).setContentTitle(titulo).setContentText(texto)
                    .setStyle(new Notification.BigTextStyle().bigText(texto))
                    .setAutoCancel(true);
            if (pi != null) b.setContentIntent(pi);
            if (Build.VERSION.SDK_INT < 26) b.setPriority(alto ? Notification.PRIORITY_HIGH : Notification.PRIORITY_LOW);
            if (!alto) b.setOnlyAlertOnce(true).setShowWhen(true);
            nm.notify(id, b.build());
        } catch (Exception e) { /* sem permissão de notificação */ }
    }
}
