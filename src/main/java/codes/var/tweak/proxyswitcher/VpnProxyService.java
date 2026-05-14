package codes.var.tweak.proxyswitcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.List;

public final class VpnProxyService extends VpnService {
    private static final String TAG = "ProxySwitcherVPN";
    static final String ACTION_START = "codes.var.tweak.proxyswitcher.action.VPN_START";
    static final String ACTION_STOP = "codes.var.tweak.proxyswitcher.action.VPN_STOP";
    static final String EXTRA_HOST = "host";
    static final String EXTRA_PORT = "port";
    static final String EXTRA_NO_PROXY = "no_proxy";

    private static final int NOTIFICATION_ID = 12011;
    private static final String CHANNEL_ID = "proxy_switcher_vpn";
    private static final int DEFAULT_MTU = 1500;

    private ParcelFileDescriptor tunnel;

    static {
        System.loadLibrary("tun2http");
    }

    private native void nativeInit();

    private native void nativeStart(int tunFd, boolean fwd53, int rcode, String proxyIp, int proxyPort);

    private native void nativeStop(int tunFd);

    private native int nativeGetMtu();

    private native void nativeDone();

    @Override
    public void onCreate() {
        super.onCreate();
        nativeInit();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopVpn();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            VpnRuntimeState.markStopped();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) {
            return START_NOT_STICKY;
        }

        String host = intent.getStringExtra(EXTRA_HOST);
        int port = intent.getIntExtra(EXTRA_PORT, 0);
        if (host == null || host.trim().isEmpty() || port < 1 || port > 65535) {
            VpnRuntimeState.markError("Invalid proxy endpoint.");
            stopSelf();
            return START_NOT_STICKY;
        }

        List<String> noProxy = ProxyProfile.parseNoProxy(intent.getStringExtra(EXTRA_NO_PROXY));
        VpnRuntimeState.markStarting();
        if (!startVpn(host.trim(), port, noProxy)) {
            VpnRuntimeState.markError("Failed to establish VPN tunnel.");
            stopSelf();
            return START_NOT_STICKY;
        }
        VpnRuntimeState.markRunning(host.trim() + ":" + port);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn();
        nativeDone();
        VpnRuntimeState.markStopped();
        super.onDestroy();
    }

    private boolean startVpn(String host, int port, List<String> noProxy) {
        try {
            stopVpn();
            ensureNotificationChannel();
            Builder builder = new Builder()
                    .setSession("ProxySwitcher")
                    .addAddress("10.99.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addRoute("::", 0)
                    .setMtu(resolveMtu());

            tunnel = builder.establish();
            if (tunnel == null) {
                Log.e(TAG, "builder.establish returned null");
                return false;
            }

            startForeground(NOTIFICATION_ID, buildNotification(host, port));
            nativeStart(tunnel.getFd(), false, 3, host, port);
            broadcastVpnState(true, host + ":" + port);
            Log.i(TAG, "vpn established: " + host + ":" + port);
            return true;
        } catch (Throwable throwable) {
            Log.e(TAG, "startVpn failed", throwable);
            stopVpn();
            return false;
        }
    }

    private void stopVpn() {
        if (tunnel != null) {
            try {
                nativeStop(tunnel.getFd());
            } catch (Throwable throwable) {
                Log.w(TAG, "native stop failed", throwable);
            }
            try {
                tunnel.close();
            } catch (IOException ignored) {
            }
            tunnel = null;
        }
        broadcastVpnState(false, null);
    }

    private void broadcastVpnState(boolean running, String endpoint) {
        Intent intent = new Intent(ProxyActions.ACTION_VPN_STATE_CHANGED)
                .setPackage(getPackageName())
                .putExtra(ProxyActions.EXTRA_VPN_RUNNING, running);
        if (endpoint != null && !endpoint.isEmpty()) {
            intent.putExtra(ProxyActions.EXTRA_VPN_ENDPOINT, endpoint);
        }
        sendBroadcast(intent);
    }

    private int resolveMtu() {
        try {
            int mtu = nativeGetMtu();
            if (mtu >= 1200 && mtu <= 10000) {
                return mtu;
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "nativeGetMtu failed", throwable);
        }
        return DEFAULT_MTU;
    }

    private Notification buildNotification(String host, int port) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                1,
                openIntent,
                pendingFlags()
        );

        Intent stopIntent = new Intent(this, VpnProxyService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this,
                2,
                stopIntent,
                pendingFlags()
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tiles_24)
                .setContentTitle("ProxySwitcher VPN")
                .setContentText("HTTP proxy: " + host + ":" + port)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .addAction(0, "Stop", stopPending)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "ProxySwitcher VPN",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Foreground service for non-root proxy mode");
        manager.createNotificationChannel(channel);
    }
}
