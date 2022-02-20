package eu.faircode.netguard;

/*
    This file is part of NetGuard.

    NetGuard is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NetGuard is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NetGuard.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2015-2019 by Marcel Bokhorst (M66B)
*/


import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import eu.faircode.netguard.preference.Preferences;
import eu.faircode.netguard.reason.SimpleReason;

@TargetApi(Build.VERSION_CODES.N)
public class ServiceTileFilter extends TileService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "NetGuard.TileFilter";

    public void onStartListening() {
        Log.i(TAG, "Start listening");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        update();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (Preferences.FILTER.getKey().equals(key))
            update();
    }

    private void update() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean filter = prefs.getBoolean(Preferences.FILTER.getKey(), Preferences.FILTER.getDefaultValue());
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(filter ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    public void onStopListening() {
        Log.i(TAG, "Stop listening");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    public void onClick() {
        Log.i(TAG, "Click");

        if (Util.canFilter()) {
            if (IAB.isPurchased(ActivityPro.SKU_FILTER, this)) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit().putBoolean(Preferences.FILTER.getKey(), !prefs.getBoolean(Preferences.FILTER.getKey(), Preferences.FILTER.getDefaultValue())).apply();
                ServiceSinkhole.reload(SimpleReason.Tile, this, false);
            } else
                Toast.makeText(this, R.string.title_pro_feature, Toast.LENGTH_SHORT).show();
        } else
            Toast.makeText(this, R.string.msg_unavailable, Toast.LENGTH_SHORT).show();
    }
}
