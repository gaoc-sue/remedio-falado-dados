package br.com.remediofalado.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Ajuda o alarme a tocar em celulares que fecham apps em segundo plano
 * (Xiaomi, Redmi, Huawei, Oppo, Vivo, Samsung e outros).
 * Só abre as telas de configuração; quem liga a opção é a pessoa.
 */
@CapacitorPlugin(name = "Energia")
public class EnergiaPlugin extends Plugin {

    /** Telas de "início automático" conhecidas, por fabricante. */
    private static final String[][] AUTO_INICIO = {
        {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
        {"com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"},
        {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
        {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
        {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
        {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
        {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
        {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
        {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
        {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
        {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"},
        {"com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"},
        {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
        {"com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"},
        {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"},
        {"com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"}
    };

    @PluginMethod
    public void status(PluginCall call) {
        Context ctx = getContext();
        boolean semEconomia = true;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            semEconomia = pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        }
        String fab = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        JSObject r = new JSObject();
        r.put("semEconomia", semEconomia);
        r.put("fabricante", fab);
        r.put("android", String.valueOf(Build.VERSION.SDK_INT));
        call.resolve(r);
    }

    @PluginMethod
    public void liberarBateria(PluginCall call) {
        Context ctx = getContext();
        boolean ok = false;
        if (Build.VERSION.SDK_INT >= 23) {
            ok = abrir(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.getPackageName())));
            if (!ok) ok = abrir(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
        if (!ok) ok = abrirDetalhes();
        responder(call, ok);
    }

    @PluginMethod
    public void abrirAutoInicio(PluginCall call) {
        boolean ok = false;
        for (String[] c : AUTO_INICIO) {
            Intent i = new Intent();
            i.setComponent(new ComponentName(c[0], c[1]));
            if (abrir(i)) { ok = true; break; }
        }
        if (!ok) ok = abrirDetalhes();
        responder(call, ok);
    }

    @PluginMethod
    public void abrirApp(PluginCall call) {
        responder(call, abrirDetalhes());
    }

    private boolean abrirDetalhes() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
        return abrir(i);
    }

    private boolean abrir(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void responder(PluginCall call, boolean ok) {
        JSObject r = new JSObject();
        r.put("aberto", ok);
        call.resolve(r);
    }
}
