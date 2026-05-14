package codes.var.tweak.proxyswitcher;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@TargetApi(Build.VERSION_CODES.N)
public final class ProxyTileService extends TileService {
    private static final String ACTION_PROXY_CHANGE = "android.intent.action.PROXY_CHANGE";
    private static final String TAG = "ProxySwitcherTile";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshFromSystemEvent = () -> requestWiFiSnapshot(false);
    private AlertDialog currentDialog;
    private DialogAdapter currentAdapter;
    private View dialogLoadingOverlay;
    private BroadcastReceiver stateReceiver;
    private boolean receiverRegistered;
    private boolean operationInProgress;
    private boolean closeDialogOnSuccess;
    private String lastWiFiSnapshotToken;

    @Override
    public void onStartListening() {
        super.onStartListening();
        registerStateReceiver();
        updateTile();
        requestWiFiSnapshot(false);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        Runnable action = this::showProfileDialog;
        if (isLocked()) {
            unlockAndRun(action);
        } else {
            action.run();
        }
    }

    @Override
    public void onDestroy() {
        unregisterStateReceiver();
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        ProxyStore store = new ProxyStore(this);
        String active = store.activeIdentifier();
        boolean direct = ProxyStore.DIRECT_IDENTIFIER.equals(active);
        tile.setState(direct ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.setLabel("ProxySwitcher");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String modeLabel = ProxyStore.MODE_VPN.equals(store.runtimeMode()) ? "VPN" : "Root";
            String profileName = direct ? "Direct" : activeProfileName(store, active);
            tile.setSubtitle(modeLabel + " · " + profileName);
        }
        tile.updateTile();
    }

    private String activeProfileName(ProxyStore store, String identifier) {
        ProxyProfile profile = store.profileWithIdentifier(identifier);
        return profile == null ? "ProxySwitcher" : profile.name;
    }

    private void showProfileDialog() {
        if (currentDialog != null && currentDialog.isShowing()) {
            return;
        }
        if (!isSecure() && isLocked()) {
            return;
        }
        ProxyStore store = new ProxyStore(this);
        requestWiFiSnapshot(false);
        ListView listView = new ListView(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        FrameLayout overlay = new FrameLayout(this);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setBackgroundColor(0x4D000000);
        ProgressBar overlayProgress = new ProgressBar(this);
        overlayProgress.setIndeterminate(true);
        overlay.addView(overlayProgress, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        overlay.setVisibility(operationInProgress ? View.VISIBLE : View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        dialogLoadingOverlay = overlay;
        DialogAdapter adapter = new DialogAdapter();
        adapter.buildRows(store);
        currentAdapter = adapter;
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Row row = adapter.rowAt(position);
            if (row == null || row.header || row.footer) {
                return;
            }
            closeDialogOnSuccess = true;
            if (row.wifiSsid != null) {
                switchWifi(row.wifiSsid);
                return;
            }
            applyIdentifier(row.identifier);
        });
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ProxySwitcher")
                .setView(root)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnDismissListener(d -> {
            currentDialog = null;
            currentAdapter = null;
            dialogLoadingOverlay = null;
            closeDialogOnSuccess = false;
        });
        currentDialog = dialog;
        try {
            showDialog(dialog);
        } catch (WindowManager.BadTokenException badTokenException) {
            Log.w(TAG, "Unable to show tile dialog due to invalid token", badTokenException);
            currentDialog = null;
            currentAdapter = null;
            dialogLoadingOverlay = null;
        } catch (IllegalStateException illegalStateException) {
            Log.w(TAG, "Unable to show tile dialog due to tile state", illegalStateException);
            currentDialog = null;
            currentAdapter = null;
            dialogLoadingOverlay = null;
        }
    }

    private void applyIdentifier(String identifier) {
        if (operationInProgress) {
            return;
        }
        setOperationInProgress(true);
        executor.execute(() -> {
            ProxyStore store = new ProxyStore(this);
            boolean vpnMode = ProxyStore.MODE_VPN.equals(store.runtimeMode());
            RootProxyApplier.Result result;
            if (vpnMode) {
                VpnProxyController controller = new VpnProxyController(this);
                if (ProxyStore.DIRECT_IDENTIFIER.equals(identifier)) {
                    result = controller.applyDirect();
                } else {
                    result = controller.applyProfile(store.profileWithIdentifier(identifier));
                }
            } else {
                RootProxyApplier applier = new RootProxyApplier(this);
                if (ProxyStore.DIRECT_IDENTIFIER.equals(identifier)) {
                    result = applier.applyDirect();
                } else {
                    result = applier.applyProfile(store.profileWithIdentifier(identifier));
                }
            }
            if (result.ok) {
                if (vpnMode) {
                    setOperationInProgress(false);
                    updateTile();
                    refreshDialogRows();
                    dismissDialogForSuccess();
                    return;
                }
                ProxyInteractionFlow.onApplyRequested(
                        mainHandler,
                        () -> requestWiFiSnapshot(true),
                        () -> operationInProgress,
                        () -> setOperationInProgress(false)
                );
                return;
            }
            closeDialogOnSuccess = false;
            setOperationInProgress(false);
        });
    }

    private void switchWifi(String ssid) {
        if (ssid == null || ssid.trim().isEmpty()) {
            return;
        }
        if (operationInProgress) {
            return;
        }
        ProxyStore store = new ProxyStore(this);
        if (ProxyStore.MODE_VPN.equals(store.runtimeMode())) {
            return;
        }
        setOperationInProgress(true);
        executor.execute(() -> {
            sendBroadcast(new Intent(ProxyActions.ACTION_SWITCH_WIFI)
                    .putExtra(ProxyActions.EXTRA_SSID, ssid.trim()));
            ProxyInteractionFlow.onWiFiSwitchRequested(
                    mainHandler,
                    () -> requestWiFiSnapshot(true),
                    () -> requestWiFiSnapshot(false),
                    () -> operationInProgress,
                    () -> setOperationInProgress(false)
            );
        });
    }

    private void setOperationInProgress(boolean value) {
        operationInProgress = value;
        if (dialogLoadingOverlay != null) {
            dialogLoadingOverlay.setVisibility(value ? View.VISIBLE : View.GONE);
        }
        refreshDialogRows();
    }

    private void registerStateReceiver() {
        if (receiverRegistered) {
            return;
        }
        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }
                String action = intent.getAction();
                ProxyStore store = new ProxyStore(ProxyTileService.this);
                boolean vpnMode = ProxyStore.MODE_VPN.equals(store.runtimeMode());
                if (ProxyActions.ACTION_LIST_WIFI.equals(action)) {
                    boolean wasOperationInProgress = operationInProgress;
                    String token = intent.getStringExtra(ProxyActions.EXTRA_REQUEST_TOKEN);
                    boolean tokenMatch = lastWiFiSnapshotToken != null && lastWiFiSnapshotToken.equals(token);
                    boolean dialogShowing = currentDialog != null && currentDialog.isShowing();
                    if (!tokenMatch && !operationInProgress && !dialogShowing) {
                        return;
                    }
                    ArrayList<String> ssids = intent.getStringArrayListExtra(ProxyActions.EXTRA_WIFI_LIST);
                    ArrayList<String> hints = intent.getStringArrayListExtra(ProxyActions.EXTRA_WIFI_PROXY_LIST);
                    String currentSsid = intent.getStringExtra(ProxyActions.EXTRA_CURRENT_SSID);
                    String currentProxy = intent.getStringExtra(ProxyActions.EXTRA_CURRENT_PROXY);
                    ProxyStateSync.applySnapshot(store, ssids, hints, currentSsid, currentProxy);
                    setOperationInProgress(false);
                    updateTile();
                    refreshDialogRows();
                    if (wasOperationInProgress) {
                        dismissDialogForSuccess();
                    }
                    return;
                }
                if (!vpnMode && (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)
                        || WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)
                        || ACTION_PROXY_CHANGE.equals(action))) {
                    if (operationInProgress) {
                        requestWiFiSnapshot(false);
                    }
                    mainHandler.removeCallbacks(refreshFromSystemEvent);
                    mainHandler.postDelayed(refreshFromSystemEvent, 600);
                    return;
                }
                if (ProxyStore.ACTION_CHANGED.equals(action)) {
                    updateTile();
                    refreshDialogRows();
                    return;
                }
                if (ProxyActions.ACTION_VPN_STATE_CHANGED.equals(action)) {
                    boolean running = intent.getBooleanExtra(ProxyActions.EXTRA_VPN_RUNNING, false);
                    String endpoint = intent.getStringExtra(ProxyActions.EXTRA_VPN_ENDPOINT);
                    if (vpnMode) {
                        if (running && endpoint != null && !endpoint.isEmpty()) {
                            ProxyStateSync.syncActiveProfileWithSystemProxy(store, endpoint);
                        } else {
                            store.setActiveIdentifier(ProxyStore.DIRECT_IDENTIFIER);
                        }
                    }
                    updateTile();
                    refreshDialogRows();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ProxyActions.ACTION_LIST_WIFI);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ACTION_PROXY_CHANGE);
        filter.addAction(ProxyStore.ACTION_CHANGED);
        filter.addAction(ProxyActions.ACTION_VPN_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void unregisterStateReceiver() {
        if (!receiverRegistered || stateReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(stateReceiver);
        } catch (Throwable ignored) {
        }
        receiverRegistered = false;
        stateReceiver = null;
    }

    private void requestWiFiSnapshot(boolean delayed) {
        ProxyStore store = new ProxyStore(this);
        if (ProxyStore.MODE_VPN.equals(store.runtimeMode())) {
            return;
        }
        lastWiFiSnapshotToken = String.valueOf(System.currentTimeMillis());
        Runnable task = () -> sendBroadcast(new Intent(ProxyActions.ACTION_LIST_WIFI)
                .putExtra(ProxyActions.EXTRA_REQUEST_TOKEN, lastWiFiSnapshotToken)
                .putExtra(ProxyActions.EXTRA_REPLY_PACKAGE, getPackageName()));
        if (delayed) {
            mainHandler.postDelayed(task, ProxyInteractionFlow.SNAPSHOT_DELAY_MS);
        } else {
            task.run();
        }
    }

    private void refreshDialogRows() {
        mainHandler.post(() -> {
            if (currentDialog == null || !currentDialog.isShowing() || currentAdapter == null) {
                return;
            }
            currentAdapter.buildRows(new ProxyStore(this));
        });
    }

    private void dismissDialogForSuccess() {
        if (!closeDialogOnSuccess) {
            return;
        }
        closeDialogOnSuccess = false;
        mainHandler.post(() -> {
            if (currentDialog != null && currentDialog.isShowing()) {
                currentDialog.dismiss();
            }
        });
    }

    private final class DialogAdapter extends BaseAdapter {
        private final ArrayList<Row> rows = new ArrayList<>();

        void buildRows(ProxyStore store) {
            rows.clear();
            boolean vpnMode = ProxyStore.MODE_VPN.equals(store.runtimeMode());
            String active = store.activeIdentifier();
            rows.add(Row.header("Direct"));
            rows.add(Row.proxy("Direct", "No HTTP proxy", ProxyStore.DIRECT_IDENTIFIER, ProxyStore.DIRECT_IDENTIFIER.equals(active)));

            rows.add(Row.header("Profiles"));
            List<ProxyProfile> profiles = store.profiles();
            if (profiles.isEmpty()) {
                rows.add(Row.footer("No profiles"));
            } else {
                for (ProxyProfile profile : profiles) {
                    rows.add(Row.proxy(profile.name, profile.endpoint(), profile.identifier, profile.identifier.equals(active)));
                }
            }

            rows.add(Row.header("Wi-Fi"));
            if (vpnMode) {
                rows.add(Row.footer("Wi-Fi switching unavailable in VPN mode"));
            } else {
                List<String> ssids = store.quickWiFiSSIDs();
                String currentSsid = store.currentWiFiSSID();
                if (ssids.isEmpty()) {
                    rows.add(Row.footer("No saved Wi-Fi"));
                } else {
                    for (String ssid : ssids) {
                        String proxyHint = store.wiFiProxyHint(ssid);
                        String subtitle = (ssid.equals(currentSsid) ? "Current" : "Saved Wi-Fi")
                                + " · Proxy: " + (proxyHint == null || proxyHint.isEmpty() ? "Direct" : proxyHint);
                        rows.add(Row.wifi(ssid, subtitle, ssid.equals(currentSsid)));
                    }
                }
            }
            notifyDataSetChanged();
        }

        Row rowAt(int position) {
            return position >= 0 && position < rows.size() ? rows.get(position) : null;
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean isEnabled(int position) {
            Row row = rowAt(position);
            return !operationInProgress && row != null && !row.header && !row.footer;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = rows.get(position);
            if (row.header) {
                TextView header = new TextView(ProxyTileService.this);
                header.setText(row.title);
                header.setTextSize(12);
                header.setPadding(dp(18), dp(12), dp(18), dp(6));
                return header;
            }
            if (row.footer) {
                TextView footer = new TextView(ProxyTileService.this);
                footer.setText(row.title);
                footer.setTextSize(12);
                footer.setPadding(dp(18), dp(6), dp(18), dp(12));
                return footer;
            }

            LinearLayout container = new LinearLayout(ProxyTileService.this);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER_VERTICAL);
            container.setPadding(dp(18), dp(10), dp(16), dp(10));

            LinearLayout texts = new LinearLayout(ProxyTileService.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(ProxyTileService.this);
            title.setText(row.title);
            title.setTextSize(16);
            texts.addView(title);
            TextView subtitle = new TextView(ProxyTileService.this);
            subtitle.setText(row.subtitle);
            subtitle.setTextSize(12);
            texts.addView(subtitle);
            container.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView check = new TextView(ProxyTileService.this);
            check.setText(row.checked ? "✓" : "");
            check.setTextSize(20);
            check.setGravity(Gravity.CENTER);
            container.addView(check, new LinearLayout.LayoutParams(dp(24), dp(24)));
            return container;
        }
    }

    private static final class Row {
        final boolean header;
        final boolean footer;
        final boolean checked;
        final String title;
        final String subtitle;
        final String identifier;
        final String wifiSsid;

        private Row(boolean header, boolean footer, boolean checked, String title, String subtitle, String identifier, String wifiSsid) {
            this.header = header;
            this.footer = footer;
            this.checked = checked;
            this.title = title;
            this.subtitle = subtitle;
            this.identifier = identifier;
            this.wifiSsid = wifiSsid;
        }

        static Row header(String title) {
            return new Row(true, false, false, title, "", null, null);
        }

        static Row footer(String title) {
            return new Row(false, true, false, title, "", null, null);
        }

        static Row proxy(String title, String subtitle, String identifier, boolean checked) {
            return new Row(false, false, checked, title, subtitle, identifier, null);
        }

        static Row wifi(String title, String subtitle, boolean checked) {
            return new Row(false, false, checked, title, subtitle, null, title);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
