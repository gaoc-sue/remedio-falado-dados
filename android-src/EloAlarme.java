package br.com.remediofalado.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Alarme exato marcado para 30 minutos depois de cada dose (celular de quem toma o remédio).
 * Acorda o celular mesmo parado (modo soneca do Android) e confere se a dose foi marcada;
 * se não foi, avisa a família na hora, sem esperar a tarefa de 15 em 15 minutos.
 * Também remarca o alarme depois que o celular liga, o app atualiza ou a pessoa libera o "alarme na hora certa".
 */
public class EloAlarme extends BroadcastReceiver {
    static final String ACAO = "br.com.remediofalado.app.CONFERIR_ATRASO";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context ctx = context.getApplicationContext();
        final boolean conferir = intent != null && ACAO.equals(intent.getAction());
        final PendingResult fim = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try { if (conferir) Elo.executar(ctx); } catch (Throwable t) { }
                try { Elo.agendarAtraso(ctx); } catch (Throwable t) { }
                // se faltou internet agora, a tarefa normal tenta de novo assim que a rede voltar
                try { if (conferir) Elo.agora(ctx); } catch (Throwable t) { }
                fim.finish();
            }
        }).start();
    }
}
