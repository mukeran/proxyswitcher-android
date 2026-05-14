package codes.var.tweak.proxyswitcher;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public final class DiagnosticsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_diag);
        toolbar.setTitle("Diagnostics");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView text = findViewById(R.id.text_diagnostics);
        text.setText(new ProxyStore(this).diagnosticsSummary());
    }
}
