package codes.var.proxyswitcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private ProxyStore store;
    private ProfileAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ProxyStore(this);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xFFFAFAFA);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFAFAFA);
        root.setPadding(0, statusBarHeight(), 0, 0);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(20), dp(12), dp(20), dp(8));

        TextView title = new TextView(this);
        title.setText("ProxySwitcher");
        title.setTextColor(0xFF111111);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView listView = new ListView(this);
        listView.setDividerHeight(1);
        listView.setBackgroundColor(0xFFFAFAFA);
        adapter = new ProfileAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Row row = adapter.rowAt(position);
            if (row == null || row.header || row.footer) {
                return;
            }
            if (row.direct) {
                applyDirect();
            } else if (row.profile != null) {
                applyProfile(row.profile);
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            Row row = adapter.rowAt(position);
            if (row == null || row.profile == null) {
                return false;
            }
            showProfileActions(row.profile);
            return true;
        });
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView addButton = new TextView(this);
        addButton.setText("+");
        addButton.setTextColor(Color.WHITE);
        addButton.setTextSize(30);
        addButton.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0xFF00897B);
        addButton.setBackground(background);
        addButton.setElevation(dp(6));
        addButton.setContentDescription("Add proxy");
        addButton.setOnClickListener(view -> showProfileEditor(null));
        int fabSize = dp(56);
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(fabSize, fabSize, Gravity.BOTTOM | Gravity.END);
        fabParams.setMargins(0, 0, dp(20), dp(20) + navigationBarHeight());
        frame.addView(addButton, fabParams);

        setContentView(frame);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadProfiles();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void reloadProfiles() {
        adapter.setProfiles(store.profiles(), store.activeIdentifier());
    }

    private void applyDirect() {
        runApply(() -> new RootProxyApplier().applyDirect(), ProxyStore.DIRECT_IDENTIFIER);
    }

    private void applyProfile(ProxyProfile profile) {
        runApply(() -> new RootProxyApplier().applyProfile(profile), profile.identifier);
    }

    private void runApply(ApplyTask task, String activeIdentifier) {
        executor.execute(() -> {
            RootProxyApplier.Result result = task.run();
            mainHandler.post(() -> {
                if (result.ok) {
                    store.setActiveIdentifier(activeIdentifier);
                    reloadProfiles();
                } else {
                    showError(result.message == null || result.message.isEmpty()
                            ? "Unable to update proxy settings."
                            : result.message);
                }
            });
        });
    }

    private void showProfileActions(ProxyProfile profile) {
        new AlertDialog.Builder(this)
                .setTitle(profile.name)
                .setItems(new CharSequence[]{"Edit", "Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        showProfileEditor(profile);
                    } else {
                        store.deleteProfile(profile.identifier);
                        reloadProfiles();
                    }
                })
                .show();
    }

    private void showProfileEditor(ProxyProfile profile) {
        boolean editing = profile != null;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int side = dp(20);
        form.setPadding(side, dp(8), side, 0);

        EditText name = field("Name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(editing ? profile.name : "");
        form.addView(name);

        EditText host = field("Host", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        host.setSingleLine(true);
        host.setText(editing ? profile.host : "");
        form.addView(host);

        EditText port = field("Port", InputType.TYPE_CLASS_NUMBER);
        port.setSingleLine(true);
        port.setText(editing ? String.valueOf(profile.port) : String.valueOf(ProxyProfile.DEFAULT_PORT));
        form.addView(port);

        EditText username = field("Username (optional)", InputType.TYPE_CLASS_TEXT);
        username.setSingleLine(true);
        username.setText(editing && profile.username != null ? profile.username : "");
        form.addView(username);

        EditText password = field("Password (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSingleLine(true);
        password.setText(editing && profile.password != null ? profile.password : "");
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);
        form.addView(password);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "Edit Proxy" : "New Proxy")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            ProxyProfile updated = editing ? profile : new ProxyProfile();
            updated.host = host.getText().toString().trim();
            updated.port = parsePort(port.getText().toString());
            updated.name = name.getText().toString().trim().isEmpty()
                    ? updated.host
                    : name.getText().toString().trim();
            updated.username = emptyToNull(username.getText().toString().trim());
            updated.password = emptyToNull(password.getText().toString());
            if (updated.host.isEmpty() || updated.port < 1 || updated.port > 65535) {
                showError("Host or port is invalid.");
                return;
            }
            store.saveProfile(updated);
            reloadProfiles();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private EditText field(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        editText.setSelectAllOnFocus(false);
        return editText;
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("ProxySwitcher")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int statusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private int navigationBarHeight() {
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private interface ApplyTask {
        RootProxyApplier.Result run();
    }

    private final class ProfileAdapter extends BaseAdapter {
        private final ArrayList<Row> rows = new ArrayList<>();

        void setProfiles(List<ProxyProfile> profiles, String activeIdentifier) {
            rows.clear();
            rows.add(Row.header("Direct"));
            rows.add(Row.direct(activeIdentifier));
            rows.add(Row.footer("Tap Direct or a saved proxy to immediately update the active Wi-Fi HTTP proxy."));
            rows.add(Row.header("Profiles"));
            if (profiles.isEmpty()) {
                rows.add(Row.footer("Add a proxy profile first. The Quick Settings tile switches between Direct and the last active profile."));
            } else {
                for (ProxyProfile profile : profiles) {
                    rows.add(Row.profile(profile, activeIdentifier));
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
            return row != null && !row.header && !row.footer;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = rows.get(position);
            if (row.header) {
                TextView header = new TextView(MainActivity.this);
                header.setText(row.title);
                header.setTextColor(0xFF6A6A6A);
                header.setTextSize(13);
                header.setGravity(Gravity.BOTTOM | Gravity.START);
                header.setPadding(dp(20), dp(18), dp(20), dp(6));
                return header;
            }
            if (row.footer) {
                TextView footer = new TextView(MainActivity.this);
                footer.setText(row.title);
                footer.setTextColor(0xFF6A6A6A);
                footer.setTextSize(13);
                footer.setPadding(dp(20), dp(8), dp(20), dp(18));
                return footer;
            }

            LinearLayout cell = new LinearLayout(MainActivity.this);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setPadding(dp(20), dp(10), dp(18), dp(10));
            cell.setMinimumHeight(dp(64));

            LinearLayout labels = new LinearLayout(MainActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);

            TextView primary = new TextView(MainActivity.this);
            primary.setText(row.title);
            primary.setTextColor(0xFF111111);
            primary.setTextSize(17);
            labels.addView(primary);

            TextView secondary = new TextView(MainActivity.this);
            secondary.setText(row.subtitle);
            secondary.setTextColor(0xFF6A6A6A);
            secondary.setTextSize(14);
            labels.addView(secondary);
            cell.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView checkmark = new TextView(MainActivity.this);
            checkmark.setText(row.checked ? "✓" : "");
            checkmark.setTextColor(0xFF00897B);
            checkmark.setTextSize(24);
            checkmark.setGravity(Gravity.CENTER);
            cell.addView(checkmark, new LinearLayout.LayoutParams(dp(32), dp(40)));
            return cell;
        }
    }

    private static final class Row {
        final boolean header;
        final boolean footer;
        final boolean direct;
        final boolean checked;
        final String title;
        final String subtitle;
        final ProxyProfile profile;

        private Row(boolean header, boolean footer, boolean direct, boolean checked, String title, String subtitle, ProxyProfile profile) {
            this.header = header;
            this.footer = footer;
            this.direct = direct;
            this.checked = checked;
            this.title = title;
            this.subtitle = subtitle;
            this.profile = profile;
        }

        static Row header(String title) {
            return new Row(true, false, false, false, title, "", null);
        }

        static Row footer(String title) {
            return new Row(false, true, false, false, title, "", null);
        }

        static Row direct(String activeIdentifier) {
            return new Row(false, false, true, ProxyStore.DIRECT_IDENTIFIER.equals(activeIdentifier), "Direct", "No HTTP proxy", null);
        }

        static Row profile(ProxyProfile profile, String activeIdentifier) {
            return new Row(false, false, false, profile.identifier.equals(activeIdentifier), profile.name, profile.endpoint(), profile);
        }
    }
}
