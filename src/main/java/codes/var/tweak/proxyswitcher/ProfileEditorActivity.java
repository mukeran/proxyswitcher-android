package codes.var.tweak.proxyswitcher;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

public final class ProfileEditorActivity extends AppCompatActivity {
    private ProxyProfile editingProfile;
    private boolean clearTemporaryOnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_editor);

        ProxyStore store = new ProxyStore(this);
        String profileId = getIntent().getStringExtra("profile_id");
        clearTemporaryOnSave = getIntent().getBooleanExtra("clear_temporary_on_save", false);
        editingProfile = profileId == null ? null : store.profileWithIdentifier(profileId);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_profile_editor);
        toolbar.setTitle(editingProfile == null ? "New Proxy" : "Edit Proxy");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(Menu.NONE, 1, Menu.NONE, "Save")
                .setIcon(R.drawable.ic_save_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> {
            saveProfile();
            return true;
        });

        bindInitialValues();
    }

    private void saveProfile() {
        TextInputLayout hostLayout = findViewById(R.id.layout_host);
        TextInputLayout portLayout = findViewById(R.id.layout_port);
        EditText name = findViewById(R.id.input_name);
        EditText host = findViewById(R.id.input_host);
        EditText port = findViewById(R.id.input_port);
        EditText username = findViewById(R.id.input_username);
        EditText password = findViewById(R.id.input_password);
        EditText noProxy = findViewById(R.id.input_no_proxy);
        hostLayout.setError(null);
        portLayout.setError(null);

        ProxyProfile profile = editingProfile == null ? new ProxyProfile() : editingProfile;
        profile.host = text(host);
        profile.port = parsePort(text(port));
        profile.name = text(name).isEmpty() ? profile.host : text(name);
        profile.username = emptyToNull(text(username));
        profile.password = emptyToNull(text(password));
        profile.noProxy = ProxyProfile.parseNoProxy(text(noProxy));

        boolean invalid = false;
        if (profile.host.isEmpty()) {
            hostLayout.setError("Host is required");
            invalid = true;
        }
        if (profile.port < 1 || profile.port > 65535) {
            portLayout.setError("Port should be 1-65535");
            invalid = true;
        }
        if (invalid) {
            return;
        }
        ProxyStore store = new ProxyStore(this);
        store.saveProfile(profile);
        if (clearTemporaryOnSave) {
            store.clearTemporaryProfile();
        }
        finish();
    }

    private void bindInitialValues() {
        EditText name = findViewById(R.id.input_name);
        EditText host = findViewById(R.id.input_host);
        EditText port = findViewById(R.id.input_port);
        EditText username = findViewById(R.id.input_username);
        EditText password = findViewById(R.id.input_password);
        EditText noProxy = findViewById(R.id.input_no_proxy);

        if (editingProfile != null) {
            name.setText(editingProfile.name);
            host.setText(editingProfile.host);
            port.setText(String.valueOf(editingProfile.port));
            username.setText(editingProfile.username == null ? "" : editingProfile.username);
            password.setText(editingProfile.password == null ? "" : editingProfile.password);
            noProxy.setText(editingProfile.noProxyCsv());
            return;
        }

        String tempName = getIntent().getStringExtra("temp_name");
        String tempHost = getIntent().getStringExtra("temp_host");
        int tempPort = getIntent().getIntExtra("temp_port", ProxyProfile.DEFAULT_PORT);
        String tempUser = getIntent().getStringExtra("temp_username");
        String tempPass = getIntent().getStringExtra("temp_password");
        java.util.ArrayList<String> tempNoProxy = getIntent().getStringArrayListExtra("temp_no_proxy");

        if (tempName != null) name.setText(tempName);
        if (tempHost != null) host.setText(tempHost);
        port.setText(String.valueOf(tempPort));
        if (tempUser != null) username.setText(tempUser);
        if (tempPass != null) password.setText(tempPass);
        if (tempNoProxy != null && !tempNoProxy.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < tempNoProxy.size(); i++) {
                if (i > 0) builder.append(',');
                builder.append(tempNoProxy.get(i));
            }
            noProxy.setText(builder.toString());
        }
    }

    private String text(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private void showError(String message) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("ProxySwitcher")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .create();
        dialog.show();
    }
}
