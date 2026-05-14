package codes.var.tweak.proxyswitcher;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

final class VpnProxyController {
    private final Context context;

    VpnProxyController(Context context) {
        this.context = context.getApplicationContext();
    }

    Intent preparePermissionIntent() {
        return VpnService.prepare(context);
    }

    RootProxyApplier.Result applyDirect() {
        stopVpn();
        return RootProxyApplier.Result.ok();
    }

    RootProxyApplier.Result applyProfile(ProxyProfile profile) {
        if (profile == null) {
            return RootProxyApplier.Result.error("Proxy profile not found.");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return RootProxyApplier.Result.error("VPN proxy mode requires Android 10+");
        }
        Intent prepare = VpnService.prepare(context);
        if (prepare != null) {
            return RootProxyApplier.Result.error("VPN permission is required.");
        }

        int baseline = VpnRuntimeState.generation();
        Intent intent = new Intent(context, VpnProxyService.class)
                .setAction(VpnProxyService.ACTION_START)
                .putExtra(VpnProxyService.EXTRA_HOST, profile.host)
                .putExtra(VpnProxyService.EXTRA_PORT, profile.port)
                .putExtra(VpnProxyService.EXTRA_NO_PROXY, profile.noProxyCsv());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        return VpnRuntimeState.waitForStartResult(baseline, 2500);
    }

    void stopVpn() {
        VpnRuntimeState.markStopped();
        Intent stopIntent = new Intent(context, VpnProxyService.class).setAction(VpnProxyService.ACTION_STOP);
        context.startService(stopIntent);
    }
}
