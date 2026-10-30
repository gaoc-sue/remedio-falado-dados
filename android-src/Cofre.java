package br.com.remediofalado.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Cofre do aparelho.
 * Uma chave AES-256 fica no Android Keystore: ela é criada dentro do cofre do celular e não pode ser copiada.
 * Essa chave "embrulha" a chave que criptografa os dados do app (AES-256-GCM) e a configuração da conexão com a família.
 */
final class Cofre {
    private static final String ALIAS = "remedio-falado-cofre";
    private static final String PREFS = "remedio_falado_cofre";

    private Cofre() {}

    private static SecretKey chaveDoKeystore() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            KeyStore.Entry e = ks.getEntry(ALIAS, null);
            if (e instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) e).getSecretKey();
            ks.deleteEntry(ALIAS);
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    static String embrulhar(byte[] dado) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, chaveDoKeystore());
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(dado);
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    static byte[] desembrulhar(String b64) throws Exception {
        byte[] all = Base64.decode(b64, Base64.NO_WRAP);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, chaveDoKeystore(), new GCMParameterSpec(128, all, 0, 12));
        return c.doFinal(all, 12, all.length - 12);
    }

    /** Chave dos dados do app. Criada uma vez; guardada embrulhada pelo Keystore. */
    static synchronized byte[] chaveDados(Context ctx) throws Exception {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = p.getString("dados", null);
        if (s != null) {
            try { return desembrulhar(s); } catch (Exception e) { /* chave perdida: cria outra; os dados antigos não abrem mais */ }
        }
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        p.edit().putString("dados", embrulhar(k)).commit();
        return k;
    }

    /** Guarda um texto criptografado pelo Keystore (usado pela conexão com a família). */
    static synchronized void guardar(Context ctx, String nome, String texto) throws Exception {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (texto == null) { p.edit().remove("t_" + nome).commit(); return; }
        p.edit().putString("t_" + nome, embrulhar(texto.getBytes(StandardCharsets.UTF_8))).commit();
    }

    static synchronized String ler(Context ctx, String nome) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = p.getString("t_" + nome, null);
        if (s == null) return null;
        try { return new String(desembrulhar(s), StandardCharsets.UTF_8); } catch (Exception e) { return null; }
    }
}
