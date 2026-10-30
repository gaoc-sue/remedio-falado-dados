package br.com.remediofalado.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    /** o app está na tela: ele mesmo confere as mensagens (Carteiro não precisa buscar) */
    static volatile boolean naTela = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(EnergiaPlugin.class);
        registerPlugin(CofrePlugin.class);
        registerPlugin(EloPlugin.class);
        registerPlugin(ReceitaPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() { super.onResume(); naTela = true; }

    @Override
    public void onPause() { naTela = false; super.onPause(); }
}
