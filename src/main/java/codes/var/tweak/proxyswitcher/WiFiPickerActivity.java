package codes.var.tweak.proxyswitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public final class WiFiPickerActivity extends AppCompatActivity {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> availableSsids = new ArrayList<>();
    private final ArrayList<String> availableHints = new ArrayList<>();
    private final ArrayList<String> addedSsids = new ArrayList<>();
    private final ArrayList<String> addedHints = new ArrayList<>();
    private final ArrayList<Row> rows = new ArrayList<>();
    private BroadcastReceiver receiver;
    private String token;
    private boolean resolved;
    private RowAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_picker);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_wifi_picker);
        toolbar.setTitle("Add Saved Wi-Fi");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.list_wifi_picker);
        adapter = new RowAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((p, v, position, id) -> {
            Row row = adapter.rowAt(position);
            if (row == null || row.header || !row.clickable) {
                return;
            }
            String ssid = row.ssid;
            new ProxyStore(this).addQuickWiFiSSID(ssid);
            finish();
        });

        registerReceiverInternal();
        requestList();
    }

    @Override
    protected void onDestroy() {
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        super.onDestroy();
    }

    private void registerReceiverInternal() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !ProxyActions.ACTION_LIST_WIFI.equals(intent.getAction())) {
                    return;
                }
                String responseToken = intent.getStringExtra(ProxyActions.EXTRA_REQUEST_TOKEN);
                if (token == null || !token.equals(responseToken)) {
                    return;
                }
                resolved = true;
                ArrayList<String> ssids = intent.getStringArrayListExtra(ProxyActions.EXTRA_WIFI_LIST);
                ArrayList<String> proxyHints = intent.getStringArrayListExtra(ProxyActions.EXTRA_WIFI_PROXY_LIST);
                availableSsids.clear();
                availableHints.clear();
                addedSsids.clear();
                addedHints.clear();
                rows.clear();
                ProxyStore proxyStore = new ProxyStore(WiFiPickerActivity.this);
                if (ssids != null) {
                    proxyStore.setWiFiProxyHints(ssids, proxyHints == null ? new ArrayList<>() : proxyHints);
                    List<String> existing = proxyStore.quickWiFiSSIDs();
                    for (int i = 0; i < ssids.size(); i++) {
                        String ssid = ssids.get(i);
                        if (ssid == null || ssid.isEmpty()) {
                            continue;
                        }
                        String hint = (proxyHints != null && i >= 0 && i < proxyHints.size())
                                ? proxyHints.get(i)
                                : "Direct";
                        String normalizedHint = hint == null || hint.isEmpty() ? "Direct" : hint;
                        if (!existing.contains(ssid)) {
                            availableSsids.add(ssid);
                            availableHints.add(normalizedHint);
                        } else {
                            addedSsids.add(ssid);
                            addedHints.add(normalizedHint);
                        }
                    }
                }
                buildRows();
                adapter.notifyDataSetChanged();
            }
        };
        IntentFilter filter = new IntentFilter(ProxyActions.ACTION_LIST_WIFI);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    private void requestList() {
        token = String.valueOf(System.currentTimeMillis());
        resolved = false;
        sendBroadcast(new Intent(ProxyActions.ACTION_LIST_WIFI)
                .putExtra(ProxyActions.EXTRA_REQUEST_TOKEN, token)
                .putExtra(ProxyActions.EXTRA_REPLY_PACKAGE, getPackageName()));
        mainHandler.postDelayed(() -> {
            if (!resolved) {
                showInfo("Saved Wi-Fi list unavailable. Check LSPosed scope on android process.");
            }
        }, 1000);
    }

    private void showInfo(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("ProxySwitcher")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void buildRows() {
        rows.clear();
        rows.add(Row.header("Available"));
        if (availableSsids.isEmpty()) {
            rows.add(Row.disabled("No additional saved Wi-Fi", ""));
        } else {
            for (int i = 0; i < availableSsids.size(); i++) {
                rows.add(Row.available(
                        availableSsids.get(i),
                        "Saved Proxy: " + availableHints.get(i)
                ));
            }
        }
        rows.add(Row.header("Already Added"));
        if (addedSsids.isEmpty()) {
            rows.add(Row.disabled("None", ""));
        } else {
            for (int i = 0; i < addedSsids.size(); i++) {
                rows.add(Row.disabled(
                        addedSsids.get(i),
                        "Saved Proxy: " + addedHints.get(i)
                ));
            }
        }
    }

    private final class RowAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(WiFiPickerActivity.this);

        Row rowAt(int position) {
            return position >= 0 && position < rows.size() ? rows.get(position) : null;
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            Row row = rowAt(position);
            return row != null && row.header ? 0 : 1;
        }

        @Override
        public int getViewTypeCount() {
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
            return row != null && !row.header && row.clickable;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = rowAt(position);
            if (row == null) {
                return new View(WiFiPickerActivity.this);
            }
            if (row.header) {
                TextView header = (TextView) (convertView == null
                        ? inflater.inflate(R.layout.item_row_header, parent, false)
                        : convertView);
                header.setText(row.title);
                return header;
            }
            View item = convertView == null
                    ? inflater.inflate(R.layout.item_row_cell, parent, false)
                    : convertView;
            TextView title = item.findViewById(R.id.title);
            TextView subtitle = item.findViewById(R.id.subtitle);
            TextView checkmark = item.findViewById(R.id.checkmark);
            title.setText(row.title);
            subtitle.setText(row.subtitle);
            checkmark.setText(row.clickable ? "+" : "");
            item.setAlpha(row.clickable ? 1f : 0.58f);
            return item;
        }
    }

    private static final class Row {
        final boolean header;
        final boolean clickable;
        final String title;
        final String subtitle;
        final String ssid;

        private Row(boolean header, boolean clickable, String title, String subtitle, String ssid) {
            this.header = header;
            this.clickable = clickable;
            this.title = title;
            this.subtitle = subtitle;
            this.ssid = ssid;
        }

        static Row header(String title) {
            return new Row(true, false, title, "", null);
        }

        static Row available(String title, String subtitle) {
            return new Row(false, true, title, subtitle, title);
        }

        static Row disabled(String title, String subtitle) {
            return new Row(false, false, title, subtitle, null);
        }
    }
}
