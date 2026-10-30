package br.com.remediofalado.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Notificação instantânea: o outro celular mandou uma mensagem e pediu ao carteiro (Cloudflare) para acordar este.
 * O aviso do Firebase não traz conteúdo nenhum ({t:"acordar"}): aqui o celular só busca, na hora, as mensagens
 * criptografadas no servidor de passagem e mostra a notificação (a mesma rodada que o app faz de 15 em 15 minutos).
 */
public class Carteiro extends FirebaseMessagingService {
    static final String PREFS = "remedio_falado_push";

    @Override
    public void onMessageReceived(RemoteMessage m) {
        Map<String, String> d = m.getData();
        if (d == null || !"acordar".equals(d.get("t"))) return;
        Context ctx = getApplicationContext();
        // com o app aberto na tela, ele mesmo confere as mensagens a cada 15 segundos
        if (MainActivity.naTela) return;
        try { Elo.executar(ctx); } catch (Throwable t) { /* tenta na rodada normal */ }
        try { Elo.agendarAtraso(ctx); } catch (Throwable t) { }
    }

    @Override
    public void onNewToken(String token) {
        // o app manda o código novo para a família na próxima mensagem (ele lê com Elo.tokenPush)
        SharedPreferences st = getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        st.edit().putString("token", token).apply();
    }
}
