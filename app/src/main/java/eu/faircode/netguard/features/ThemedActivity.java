package eu.faircode.netguard.features;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import eu.faircode.netguard.Util;

public class ThemedActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Util.setTheme(this);
        super.onCreate(savedInstanceState);
    }
}
