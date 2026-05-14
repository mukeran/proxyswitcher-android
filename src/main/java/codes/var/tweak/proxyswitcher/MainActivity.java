package codes.var.tweak.proxyswitcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.wifi.WifiManager;
import android.app.StatusBarManager;
import android.graphics.drawable.Icon;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final String ACTION_PROXY_CHANGE = "android.intent.action.PROXY_CHANGE";
    private ProxyStore store;
    private ProfileAdapter adapter;
    private MaterialCardView lsposedBanner;
    private FrameLayout loadingOverlay;
    private FloatingActionButton addFab;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BroadcastReceiver wifiListReceiver;
    private String lastStatusToken;
    private String lastWiFiSnapshotToken;
    private boolean moduleReady;
    private long statusRequestAtMs;
    private boolean operationInProgress;
    private String pendingApplyIdentifier;
    private boolean pendingSwitchToVpn;
    private final Runnable refreshFromSystemEvent = () -> requestWiFiSnapshot(false);

    private final ActivityResultLauncher<Intent> vpnPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (VpnService.prepare(this) == null) {
                    if (pendingSwitchToVpn) {
                        pendingSwitchToVpn = false;
                        store.setRuntimeMode(ProxyStore.MODE_VPN);
                        store.setActiveIdentifier(ProxyStore.DIRECT_IDENTIFIER);
                        new VpnProxyController(this).stopVpn();
                        reloadProfiles();
                        return;
                    }
                    if (pendingApplyIdentifier == null) {
                        reloadProfiles();
                        return;
                    }
                    if (ProxyStore.DIRECT_IDENTIFIER.equals(pendingApplyIdentifier)) {
                        applyDirect();
                    } else {
                        applyProfile(store.profileWithIdentifier(pendingApplyIdentifier));
                    }
                    pendingApplyIdentifier = null;
                    return;
                }
                pendingSwitchToVpn = false;
                pendingApplyIdentifier = null;
                showError("VPN permission denied.");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ProxyStore(this);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("ProxySwitcher");
        toolbar.setNavigationIcon(R.drawable.ic_diagnosis_24);
        toolbar.setNavigationOnClickListener(v ->
                startActivity(new Intent(this, DiagnosticsActivity.class)));
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_mode) {
                showModeDialog();
                return true;
            }
            if (item.getItemId() == R.id.action_add_tile) {
                requestAddTile();
                return true;
            }
            return false;
        });

        ListView listView = findViewById(R.id.list_view);
        adapter = new ProfileAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> onRowClick(adapter.rowAt(position)));
        listView.setOnItemLongClickListener((parent, view, position, id) -> onRowLongClick(adapter.rowAt(position)));

        addFab = findViewById(R.id.fab_add);
        addFab.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu menu = new androidx.appcompat.widget.PopupMenu(this, v);
            menu.getMenu().add(0, 1, 0, "Profile");
            if (!isVpnMode()) {
                menu.getMenu().add(0, 2, 1, "Saved Wi-Fi");
            }
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    startActivity(new Intent(this, ProfileEditorActivity.class));
                    return true;
                }
                if (item.getItemId() == 2) {
                    startActivity(new Intent(this, WiFiPickerActivity.class));
                    return true;
                }
                return false;
            });
            menu.show();
        });

        registerWifiListReceiver();
        requestModuleStatus();
        loadingOverlay = findViewById(R.id.loading_overlay);

        lsposedBanner = findViewById(R.id.card_lsposed_banner);
        MaterialButton helpButton = findViewById(R.id.button_lsposed_help);
        MaterialButton nonRootButton = findViewById(R.id.button_non_root_mode);
        View.OnClickListener openHelp = v ->
                startActivity(new Intent(this, LsposedHelpActivity.class));
        lsposedBanner.setOnClickListener(openHelp);
        helpButton.setOnClickListener(openHelp);
        nonRootButton.setOnClickListener(v -> switchMode(ProxyStore.MODE_VPN));
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestModuleStatus();
        requestWiFiSnapshot(false);
        reloadProfiles();
    }

    @Override
    protected void onDestroy() {
        if (wifiListReceiver != null) {
            unregisterReceiver(wifiListReceiver);
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private void registerWifiListReceiver() {
        wifiListReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }
                String action = intent.getAction();
                if (ProxyActions.ACTION_STATUS_RESULT.equals(action)) {
                    if (isVpnMode()) {
                        return;
                    }
                    String token = intent.getStringExtra(ProxyActions.EXTRA_REQUEST_TOKEN);
                    if (lastStatusToken == null || !lastStatusToken.equals(token)) {
                        return;
                    }
                    moduleReady = intent.getBooleanExtra(ProxyActions.EXTRA_READY, false);
                    if (moduleReady) {
                        requestWiFiSnapshot(false);
                    }
                    reloadProfiles();
                    return;
                }
                if (ProxyActions.ACTION_LIST_WIFI.equals(action)) {
                    String token = intent.getStringExtra(ProxyActions.EXTRA_REQUEST_TOKEN);
                    if (lastWiFiSnapshotToken == null || !lastWiFiSnapshotToken.equals(token)) {
                        return;
                    }
                    ArrayList<String> ssids = intent.getStringArrayListExtra(ProxyActions.EXTRA_WIFI_LIST);
                    ArrayList<String> hints = intent.getStringArrayListExtra(ProxyActions.EXTRA_WIFI_PROXY_LIST);
                    String currentSsid = intent.getStringExtra(ProxyActions.EXTRA_CURRENT_SSID);
                    String currentProxy = intent.getStringExtra(ProxyActions.EXTRA_CURRENT_PROXY);
                    ProxyStateSync.applySnapshot(store, ssids, hints, currentSsid, currentProxy);
                    if (operationInProgress) {
                        setOperationInProgress(false);
                    }
                    reloadProfiles();
                    return;
                }
                if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)
                        || WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)
                        || ACTION_PROXY_CHANGE.equals(action)) {
                    mainHandler.removeCallbacks(refreshFromSystemEvent);
                    mainHandler.postDelayed(refreshFromSystemEvent, 600);
                    return;
                }
                if (ProxyActions.ACTION_VPN_STATE_CHANGED.equals(action)) {
                    boolean running = intent.getBooleanExtra(ProxyActions.EXTRA_VPN_RUNNING, false);
                    String endpoint = intent.getStringExtra(ProxyActions.EXTRA_VPN_ENDPOINT);
                    if (isVpnMode()) {
                        if (running && endpoint != null && !endpoint.isEmpty()) {
                            ProxyStateSync.syncActiveProfileWithSystemProxy(store, endpoint);
                        } else {
                            store.setActiveIdentifier(ProxyStore.DIRECT_IDENTIFIER);
                        }
                    }
                    reloadProfiles();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ProxyActions.ACTION_STATUS_RESULT);
        filter.addAction(ProxyActions.ACTION_LIST_WIFI);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ACTION_PROXY_CHANGE);
        filter.addAction(ProxyActions.ACTION_VPN_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(wifiListReceiver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(wifiListReceiver, filter);
        }
    }

    private void onRowClick(Row row) {
        if (row == null || row.header || row.footer) {
            return;
        }
        if (row.direct) {
            applyDirect();
            return;
        }
        if (row.temporary) {
            applyTemporary();
            return;
        }
        if (row.profile != null) {
            applyProfile(row.profile);
            return;
        }
        if (row.wifiSsid != null) {
            switchWifi(row.wifiSsid);
        }
    }

    private boolean onRowLongClick(Row row) {
        if (row == null || row.header || row.footer) {
            return false;
        }
        if (row.profile != null) {
            showProfileActions(row.profile);
            return true;
        }
        if (row.wifiSsid != null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(row.title)
                    .setItems(new CharSequence[]{"Delete"}, (d, which) -> {
                        store.deleteQuickWiFiSSID(row.wifiSsid);
                        reloadProfiles();
                    })
                    .show();
            return true;
        }
        if (row.temporary) {
            showTemporaryActions();
            return true;
        }
        return false;
    }

    private void reloadProfiles() {
        String active = store.activeIdentifier();
        List<ProxyProfile> profiles = store.profiles();
        boolean vpnMode = isVpnMode();
        adapter.setRows(profiles, active, effectiveTemporary(), store.quickWiFiSSIDs(), store.currentWiFiSSID(), moduleReady, vpnMode);
        if (lsposedBanner != null) {
            lsposedBanner.setVisibility(moduleReady || vpnMode ? View.GONE : View.VISIBLE);
        }
    }

    private ProxyProfile effectiveTemporary() {
        ProxyProfile temporary = store.temporaryProfile();
        return temporary != null ? temporary : store.lastTemporaryProfile();
    }

    private void applyDirect() {
        if (isVpnMode()) {
            runApply(() -> new VpnProxyController(this).applyDirect());
            return;
        }
        if (!ensureModuleReady()) {
            return;
        }
        runApply(() -> new RootProxyApplier(this).applyDirect());
    }

    private void applyTemporary() {
        ProxyProfile temporary = effectiveTemporary();
        if (temporary == null) {
            showError("Temporary proxy not found.");
            return;
        }
        store.setTemporaryProfile(temporary);
        applyProfile(temporary);
    }

    private void applyProfile(ProxyProfile profile) {
        if (profile == null) {
            showError("Proxy profile not found.");
            return;
        }
        if (isVpnMode()) {
            if (!ensureVpnPrepared(profile.identifier)) {
                return;
            }
            runApply(() -> new VpnProxyController(this).applyProfile(profile));
            return;
        }
        if (!ensureModuleReady()) {
            return;
        }
        runApply(() -> new RootProxyApplier(this).applyProfile(profile));
    }

    private void runApply(ApplyTask task) {
        runApply(task, null);
    }

    private void runApply(ApplyTask task, Runnable onSuccess) {
        setOperationInProgress(true);
        executor.execute(() -> {
            RootProxyApplier.Result result = task.run();
            mainHandler.post(() -> {
                if (result.ok) {
                    if (isVpnMode()) {
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                        setOperationInProgress(false);
                        reloadProfiles();
                    } else {
                        ProxyInteractionFlow.onApplyRequested(
                                mainHandler,
                                () -> requestWiFiSnapshot(true),
                                () -> operationInProgress,
                                () -> setOperationInProgress(false)
                        );
                    }
                } else {
                    setOperationInProgress(false);
                    showError(result.message == null || result.message.isEmpty()
                            ? "Unable to update proxy settings."
                            : result.message);
                }
            });
        });
    }

    private void showProfileActions(ProxyProfile profile) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(profile.name)
                .setItems(new CharSequence[]{"Edit", "Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, ProfileEditorActivity.class);
                        intent.putExtra("profile_id", profile.identifier);
                        startActivity(intent);
                    } else {
                        store.deleteProfile(profile.identifier);
                        reloadProfiles();
                    }
                })
                .show();
    }

    private void showTemporaryActions() {
        ProxyProfile temporary = effectiveTemporary();
        if (temporary == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Temporary")
                .setItems(new CharSequence[]{"Save as profile", "Clear temporary"}, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, ProfileEditorActivity.class);
                        intent.putExtra("temp_name", temporary.name);
                        intent.putExtra("temp_host", temporary.host);
                        intent.putExtra("temp_port", temporary.port);
                        intent.putExtra("temp_username", temporary.username);
                        intent.putExtra("temp_password", temporary.password);
                        intent.putStringArrayListExtra("temp_no_proxy", new ArrayList<>(temporary.noProxy));
                        intent.putExtra("clear_temporary_on_save", true);
                        startActivity(intent);
                    } else {
                        store.clearTemporaryProfile();
                        reloadProfiles();
                    }
                })
                .show();
    }

    private void switchWifi(String ssid) {
        if (isVpnMode()) {
            showError("VPN mode does not support switching Wi-Fi.");
            return;
        }
        if (!ensureModuleReady()) {
            return;
        }
        if (ssid == null || ssid.trim().isEmpty()) {
            showError("Invalid SSID.");
            return;
        }
        setOperationInProgress(true);
        executor.execute(() -> {
            Intent intent = new Intent(ProxyActions.ACTION_SWITCH_WIFI)
                    .putExtra(ProxyActions.EXTRA_SSID, ssid.trim());
            sendBroadcast(intent);
            mainHandler.post(() -> {
                ProxyInteractionFlow.onWiFiSwitchRequested(
                        mainHandler,
                        () -> requestWiFiSnapshot(true),
                        () -> requestWiFiSnapshot(false),
                        () -> operationInProgress,
                        () -> setOperationInProgress(false)
                );
            });
        });
    }

    private void showError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("ProxySwitcher")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void requestModuleStatus() {
        if (isVpnMode()) {
            moduleReady = false;
            reloadProfiles();
            return;
        }
        lastStatusToken = String.valueOf(System.currentTimeMillis());
        statusRequestAtMs = System.currentTimeMillis();
        moduleReady = false;
        sendBroadcast(new Intent(ProxyActions.ACTION_STATUS)
                .putExtra(ProxyActions.EXTRA_REQUEST_TOKEN, lastStatusToken)
                .putExtra(ProxyActions.EXTRA_REPLY_PACKAGE, getPackageName()));
        mainHandler.postDelayed(() -> {
            if (!moduleReady && System.currentTimeMillis() - statusRequestAtMs >= 700) {
                reloadProfiles();
            }
        }, 800);
    }

    private void requestWiFiSnapshot(boolean delayed) {
        if (!moduleReady || isVpnMode()) {
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

    private boolean ensureModuleReady() {
        if (moduleReady) {
            return true;
        }
        requestModuleStatus();
        return false;
    }

    private boolean ensureVpnPrepared(String applyIdentifier) {
        VpnProxyController controller = new VpnProxyController(this);
        Intent permissionIntent = controller.preparePermissionIntent();
        if (permissionIntent == null) {
            return true;
        }
        pendingApplyIdentifier = applyIdentifier;
        vpnPermissionLauncher.launch(permissionIntent);
        return false;
    }

    private boolean isVpnMode() {
        return ProxyStore.MODE_VPN.equals(store.runtimeMode());
    }

    private void showModeDialog() {
        final String current = store.runtimeMode();
        final CharSequence[] items = new CharSequence[]{"Root (LSPosed)", "Non-root (VPN)"};
        int checked = ProxyStore.MODE_VPN.equals(current) ? 1 : 0;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Working mode")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    String targetMode = which == 1 ? ProxyStore.MODE_VPN : ProxyStore.MODE_ROOT;
                    if (targetMode.equals(current)) {
                        dialog.dismiss();
                        return;
                    }
                    switchMode(targetMode);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void switchMode(String targetMode) {
        if (ProxyStore.MODE_VPN.equals(targetMode)) {
            VpnProxyController controller = new VpnProxyController(this);
            Intent permissionIntent = controller.preparePermissionIntent();
            if (permissionIntent != null) {
                pendingApplyIdentifier = null;
                pendingSwitchToVpn = true;
                vpnPermissionLauncher.launch(permissionIntent);
                return;
            }
            store.setRuntimeMode(ProxyStore.MODE_VPN);
            store.setActiveIdentifier(ProxyStore.DIRECT_IDENTIFIER);
            controller.stopVpn();
            reloadProfiles();
            return;
        }
        store.setRuntimeMode(ProxyStore.MODE_ROOT);
        new VpnProxyController(this).stopVpn();
        requestModuleStatus();
        requestWiFiSnapshot(false);
        reloadProfiles();
    }

    private void setOperationInProgress(boolean value) {
        operationInProgress = value;
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(value ? View.VISIBLE : View.GONE);
        }
        if (addFab != null) {
            addFab.setEnabled(!value);
            addFab.setAlpha(value ? 0.5f : 1f);
        }
    }

    private void requestAddTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            showError("Request add tile requires Android 13+.");
            return;
        }
        ComponentName componentName = new ComponentName(this, ProxyTileService.class);
        StatusBarManager statusBarManager = getSystemService(StatusBarManager.class);
        if (statusBarManager == null) {
            showError("StatusBarManager unavailable.");
            return;
        }
        statusBarManager.requestAddTileService(
                componentName,
                "ProxySwitcher",
                Icon.createWithResource(this, R.drawable.ic_tiles_24),
                mainHandler::post,
                result -> {
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                        Toast.makeText(this, "Tile added.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        Toast.makeText(this, "Tile already added.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED) {
                        showError("Tile was not added.");
                        return;
                    }
                    showError("Unable to add tile.");
                }
        );
    }

    private interface ApplyTask {
        RootProxyApplier.Result run();
    }

    private final class ProfileAdapter extends BaseAdapter {
        private final ArrayList<Row> rows = new ArrayList<>();
        private final LayoutInflater inflater = LayoutInflater.from(MainActivity.this);

        void setRows(List<ProxyProfile> profiles,
                     String activeIdentifier,
                     ProxyProfile temporaryProfile,
                     List<String> quickWifiSsids,
                     String currentSsid,
                     boolean moduleReady,
                     boolean vpnMode) {
            rows.clear();
            if (vpnMode) {
                rows.add(Row.footer("Mode: Non-root VPN. System-wide HTTP proxy is applied via VPN service."));
            } else if (!moduleReady) {
                rows.add(Row.footer("LSPosed module not active. Tap any action to see setup guide."));
            }
            rows.add(Row.header("Direct"));
            rows.add(Row.direct(activeIdentifier));
            if (temporaryProfile != null) {
                rows.add(Row.temporary(temporaryProfile, activeIdentifier));
            }
            rows.add(Row.footer("Tap Direct or a saved proxy to update active HTTP proxy."));

            rows.add(Row.header("Profiles"));
            if (profiles.isEmpty()) {
                rows.add(Row.footer("Add a proxy profile first. Tile toggles Direct and active profile."));
            } else {
                for (ProxyProfile profile : profiles) {
                    rows.add(Row.profile(profile, activeIdentifier));
                }
            }

            rows.add(Row.header("Wi-Fi"));
            if (vpnMode) {
                rows.add(Row.footer("Wi-Fi switching is only available in Root mode."));
            } else if (quickWifiSsids.isEmpty()) {
                rows.add(Row.footer("Add saved SSIDs here for quick switching."));
            } else {
                for (String ssid : quickWifiSsids) {
                    rows.add(Row.wifi(
                            ssid,
                            ssid.equals(currentSsid),
                            store.wiFiProxyHint(ssid)
                    ));
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
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int position) {
            Row row = rowAt(position);
            if (row == null) {
                return 2;
            }
            if (row.header) {
                return 0;
            }
            if (row.footer) {
                return 1;
            }
            return 2;
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
                TextView header = (TextView) (convertView == null
                        ? inflater.inflate(R.layout.item_row_header, parent, false)
                        : convertView);
                header.setText(row.title);
                return header;
            }
            if (row.footer) {
                TextView footer = (TextView) (convertView == null
                        ? inflater.inflate(R.layout.item_row_footer, parent, false)
                        : convertView);
                footer.setText(row.title);
                return footer;
            }
            View cell = convertView == null
                    ? inflater.inflate(R.layout.item_row_cell, parent, false)
                    : convertView;
            TextView primary = cell.findViewById(R.id.title);
            TextView secondary = cell.findViewById(R.id.subtitle);
            TextView checkmark = cell.findViewById(R.id.checkmark);
            primary.setText(row.title);
            secondary.setText(row.subtitle);
            checkmark.setText(row.checked ? "✓" : "");
            return cell;
        }
    }

    private static final class Row {
        final boolean header;
        final boolean footer;
        final boolean direct;
        final boolean temporary;
        final boolean checked;
        final String title;
        final String subtitle;
        final ProxyProfile profile;
        final String wifiSsid;

        private Row(boolean header,
                    boolean footer,
                    boolean direct,
                    boolean temporary,
                    boolean checked,
                    String title,
                    String subtitle,
                    ProxyProfile profile,
                    String wifiSsid) {
            this.header = header;
            this.footer = footer;
            this.direct = direct;
            this.temporary = temporary;
            this.checked = checked;
            this.title = title;
            this.subtitle = subtitle;
            this.profile = profile;
            this.wifiSsid = wifiSsid;
        }

        static Row header(String title) {
            return new Row(true, false, false, false, false, title, "", null, null);
        }

        static Row footer(String title) {
            return new Row(false, true, false, false, false, title, "", null, null);
        }

        static Row direct(String activeIdentifier) {
            return new Row(false, false, true, false,
                    ProxyStore.DIRECT_IDENTIFIER.equals(activeIdentifier),
                    "Direct", "No HTTP proxy", null, null);
        }

        static Row temporary(ProxyProfile temporary, String activeIdentifier) {
            if (temporary == null) {
                return null;
            }
            return new Row(false, false, false, true,
                    ProxyStore.TEMPORARY_IDENTIFIER.equals(activeIdentifier),
                    temporary.name,
                    temporary.endpoint(),
                    null,
                    null);
        }

        static Row profile(ProxyProfile profile, String activeIdentifier) {
            return new Row(false, false, false, false,
                    profile != null && profile.identifier.equals(activeIdentifier),
                    profile == null ? "Proxy" : profile.name,
                    profile == null ? "" : profile.endpoint(),
                    profile,
                    null);
        }

        static Row wifi(String ssid, boolean current, String proxyHint) {
            String subtitle = (current ? "Current" : "Saved Wi-Fi")
                    + " · Proxy: " + (proxyHint == null || proxyHint.isEmpty() ? "Direct" : proxyHint);
            return new Row(false, false, false, false, current, ssid, subtitle, null, ssid);
        }
    }
}
