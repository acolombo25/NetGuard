package eu.faircode.netguard.features.shortcut;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import eu.faircode.netguard.R;
import eu.faircode.netguard.Rule;
import eu.faircode.netguard.features.ThemedActivity;

public class ShortcutConfigurationActivity extends ThemedActivity implements ShortcutSelectionAdapter.AppSelectedListener {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shortcut_configuration);

        //FIXME add filtering, remove apps that cannot be launched
        RecyclerView recyclerView = findViewById(R.id.rvShortcuts);
        recyclerView.setAdapter(new ShortcutSelectionAdapter(Rule.getRules(false, this), this));
    }

    @Override
    public void onSelection(Rule rule) {
        setResult(RESULT_OK, ShortcutManager.getShortcutResultIntent(this, rule));
        finish();
    }

}
