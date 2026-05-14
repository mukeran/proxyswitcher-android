package codes.var.tweak.proxyswitcher;

import android.content.Context;
import android.content.Intent;

final class RootProxyApplier {
    private final Context context;

    RootProxyApplier(Context context) {
        this.context = context.getApplicationContext();
    }

    static final class Result {
        final boolean ok;
        final String message;

        private Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        static Result ok() {
            return new Result(true, "");
        }

        static Result error(String message) {
            return new Result(false, message);
        }
    }

    Result applyDirect() {
        Intent intent = new Intent(ProxyActions.ACTION_APPLY)
                .putExtra(ProxyActions.EXTRA_MODE, ProxyActions.MODE_DIRECT);
        context.sendBroadcast(intent);
        return Result.ok();
    }

    Result applyProfile(ProxyProfile profile) {
        if (profile == null) {
            return Result.error("Proxy profile not found.");
        }
        Intent intent = new Intent(ProxyActions.ACTION_APPLY)
                .putExtra(ProxyActions.EXTRA_MODE, ProxyActions.MODE_STATIC)
                .putExtra(ProxyActions.EXTRA_HOST, profile.host)
                .putExtra(ProxyActions.EXTRA_PORT, profile.port)
                .putExtra(ProxyActions.EXTRA_NO_PROXY, profile.noProxyCsv());
        context.sendBroadcast(intent);
        return Result.ok();
    }
}
