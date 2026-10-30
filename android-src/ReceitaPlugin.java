package br.com.remediofalado.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Também recebe a receita pelo "Compartilhar" do Android (PDF, foto ou texto vindos do WhatsApp, e-mail...).
 * Lê o texto de uma foto de receita impressa, no próprio celular (ML Kit, modelo embutido no app).
 * A foto não sai do aparelho e não é guardada: o app só recebe as linhas de texto e onde elas estão.
 * As posições voltam em pixels da imagem recebida. Quem gira a foto deitada é o app (JavaScript),
 * que manda de novo e fica com a leitura que tem mais palavras inteiras.
 */
@CapacitorPlugin(name = "Receita")
public class ReceitaPlugin extends Plugin {

    private TextRecognizer leitor;
    /** receita que chegou pelo "Compartilhar" e ainda não foi aberta pelo app */
    private Intent recebida;

    @Override
    public void load() {
        Activity a = getActivity();
        if (a != null && guardar(a.getIntent())) a.setIntent(new Intent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        if (guardar(intent)) notifyListeners("compartilhado", new JSObject());
    }

    private boolean guardar(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return false;
        recebida = i;
        return true;
    }

    /** Devolve a receita compartilhada (uma vez): {tipo: pdf|imagem|texto, mime, dados (base64) ou texto}. */
    @PluginMethod
    public void compartilhado(final PluginCall call) {
        final Intent i = recebida;
        recebida = null;
        if (i == null) { call.resolve(new JSObject()); return; }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String mime = i.getType() == null ? "" : i.getType();
                    Uri uri = (Uri) i.getParcelableExtra(Intent.EXTRA_STREAM);
                    JSObject r = new JSObject();
                    if (uri == null) {
                        String t = i.getStringExtra(Intent.EXTRA_TEXT);
                        if (t == null) { call.resolve(new JSObject()); return; }
                        r.put("tipo", "texto"); r.put("texto", t.length() > 20000 ? t.substring(0, 20000) : t);
                        call.resolve(r); return;
                    }
                    String m2 = getContext().getContentResolver().getType(uri);
                    if (m2 != null && m2.length() > 0) mime = m2;
                    InputStream in = getContext().getContentResolver().openInputStream(uri);
                    if (in == null) { call.reject("nao-abriu"); return; }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n, total = 0;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > 15 * 1024 * 1024) { in.close(); call.reject("grande"); return; }
                        out.write(buf, 0, n);
                    }
                    in.close();
                    byte[] b = out.toByteArray();
                    boolean pdf = mime.contains("pdf") || (b.length > 4 && b[0] == 0x25 && b[1] == 0x50 && b[2] == 0x44 && b[3] == 0x46);
                    r.put("tipo", pdf ? "pdf" : mime.startsWith("image/") ? "imagem" : "outro");
                    r.put("mime", mime);
                    r.put("dados", Base64.encodeToString(b, Base64.NO_WRAP));
                    call.resolve(r);
                } catch (Exception e) {
                    call.reject("nao-abriu");
                }
            }
        }).start();
    }

    private TextRecognizer leitor() {
        if (leitor == null) leitor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        return leitor;
    }

    @PluginMethod
    public void ler(final PluginCall call) {
        final String b64 = call.getString("imagem");
        if (b64 == null || b64.length() == 0) { call.reject("sem-imagem"); return; }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String limpo = b64.indexOf(',') >= 0 ? b64.substring(b64.indexOf(',') + 1) : b64;
                    byte[] bytes = Base64.decode(limpo, Base64.DEFAULT);
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                    int maior = Math.max(o.outWidth, o.outHeight), amostra = 1;
                    while (maior / amostra > 2600) amostra *= 2;
                    BitmapFactory.Options o2 = new BitmapFactory.Options();
                    o2.inSampleSize = amostra;
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o2);
                    if (bmp == null) { call.reject("imagem-invalida"); return; }

                    Text t = Tasks.await(leitor().process(InputImage.fromBitmap(bmp, 0)));
                    JSONArray linhas = new JSONArray();
                    int pontos = 0;
                    for (Text.TextBlock bloco : t.getTextBlocks()) {
                        for (Text.Line l : bloco.getLines()) {
                            String s = l.getText();
                            Rect r = l.getBoundingBox();
                            JSONObject j = new JSONObject();
                            j.put("t", s);
                            if (r != null) {
                                j.put("x", r.left * amostra); j.put("y", r.top * amostra);
                                j.put("w", r.width() * amostra); j.put("h", r.height() * amostra);
                            }
                            linhas.put(j);
                            pontos += pontosDe(s);
                        }
                    }
                    JSObject r = new JSObject();
                    r.put("linhas", linhas.toString());
                    r.put("pontos", String.valueOf(pontos));
                    bmp.recycle();
                    call.resolve(r);
                } catch (Exception e) {
                    call.reject("nao-leu");
                }
            }
        }).start();
    }

    /** Conta letras de palavras com 3 letras ou mais: leitura de cabeça para baixo dá pouca palavra inteira. */
    private static int pontosDe(String s) {
        int pontos = 0;
        for (String w : s.split("[^A-Za-zÀ-ÿ]+")) if (w.length() >= 3) pontos += w.length();
        return pontos;
    }
}
