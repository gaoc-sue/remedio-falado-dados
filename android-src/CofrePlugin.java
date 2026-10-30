package br.com.remediofalado.app;

import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Entrega ao app a chave que criptografa os dados (AES-256-GCM). A chave fica guardada pelo Android Keystore. */
@CapacitorPlugin(name = "Cofre")
public class CofrePlugin extends Plugin {

    @PluginMethod
    public void chave(PluginCall call) {
        try {
            byte[] k = Cofre.chaveDados(getContext());
            JSObject r = new JSObject();
            r.put("chave", Base64.encodeToString(k, Base64.NO_WRAP));
            r.put("keystore", true);
            call.resolve(r);
        } catch (Exception e) {
            call.reject("Cofre indisponível: " + e.getMessage());
        }
    }
}
