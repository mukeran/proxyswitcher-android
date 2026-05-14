package codes.var.tweak.proxyswitcher;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public final class LsposedHelpActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lsposed_help);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_lsposed_help);
        toolbar.setTitle("LSPosed Setup Help");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView text = findViewById(R.id.text_lsposed_help);
        text.setText(
                "When ProxySwitcher shows 'not active', please verify:\n\n" +
                "1. Open LSPosed.\n" +
                "2. Enable module: ProxySwitcher.\n" +
                "3. Scope: select android/system process.\n" +
                "4. Reboot device.\n" +
                "5. Open ProxySwitcher again.\n\n" +
                "If still not active, make sure LSPosed itself is enabled and Zygisk/Riru environment is healthy."
        );
    }
}
