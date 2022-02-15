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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.Map;

import eu.faircode.netguard.database.Column;
import eu.faircode.netguard.preference.Preferences;
import eu.faircode.netguard.preference.Sort;
import eu.faircode.netguard.reason.Reason;
import eu.faircode.netguard.reason.SimpleReason;

public class ReceiverAutostart extends BroadcastReceiver {
    private static final String TAG = "NetGuard.Receiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.i(TAG, "Received " + intent);
        Util.logExtras(intent);

        String action = (intent == null ? null : intent.getAction());
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))
            try {
                // Upgrade settings
                upgrade(true, context);

                // Start service
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                if (prefs.getBoolean(Preferences.ENABLED.getKey(), Preferences.ENABLED.getDefaultValue()))
                    ServiceSinkhole.start(SimpleReason.Receiver, context);
                else if (prefs.getBoolean(Preferences.SHOW_STATS.getKey(), false))
                    ServiceSinkhole.run(SimpleReason.Receiver, context);

                if (Util.isInteractive(context))
                    ServiceSinkhole.reloadStats(SimpleReason.Receiver, context);
            } catch (Throwable ex) {
                Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }
    }

    public static void upgrade(boolean initialized, Context context) {
        synchronized (context.getApplicationContext()) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            int oldVersion = prefs.getInt("version", -1);
            int newVersion = Util.getSelfVersionCode(context);
            if (oldVersion == newVersion)
                return;
            Log.i(TAG, "Upgrading from version " + oldVersion + " to " + newVersion);

            SharedPreferences.Editor editor = prefs.edit();

            if (initialized) {
                if (oldVersion < 38) {
                    Log.i(TAG, "Converting screen wifi/mobile");
                    editor.putBoolean(Preferences.SCREEN_WIFI.getKey(), prefs.getBoolean(Preferences.UNUSED.getKey(), Preferences.UNUSED.getDefaultValue()));
                    editor.putBoolean(Preferences.SCREEN_WIFI.getKey(), prefs.getBoolean(Preferences.UNUSED.getKey(), prefs.getBoolean(Preferences.UNUSED.getKey(), Preferences.UNUSED.getDefaultValue())));
                    editor.remove(Preferences.UNUSED.getKey());

                    SharedPreferences unused = context.getSharedPreferences(Preferences.UNUSED.getKey(), Context.MODE_PRIVATE);
                    SharedPreferences screen_wifi = context.getSharedPreferences(Preferences.SCREEN_WIFI.getKey(), Context.MODE_PRIVATE);
                    SharedPreferences screen_other = context.getSharedPreferences(Preferences.SCREEN_OTHER.getKey(), Context.MODE_PRIVATE);

                    Map<String, ?> punused = unused.getAll();
                    SharedPreferences.Editor edit_screen_wifi = screen_wifi.edit();
                    SharedPreferences.Editor edit_screen_other = screen_other.edit();
                    for (String key : punused.keySet()) {
                        edit_screen_wifi.putBoolean(key, (Boolean) punused.get(key));
                        edit_screen_other.putBoolean(key, (Boolean) punused.get(key));
                    }
                    edit_screen_wifi.apply();
                    edit_screen_other.apply();

                } else if (oldVersion <= 2017032112)
                    editor.remove(Preferences.IP6.getKey());

            } else {
                Log.i(TAG, "Initializing sdk=" + Build.VERSION.SDK_INT);
                editor.putBoolean(Preferences.FILTER_UDP.getKey(), Preferences.FILTER_UDP.getDefaultValue());
                editor.putBoolean(Preferences.WHITELIST_WIFI.getKey(), !Preferences.WHITELIST_WIFI.getDefaultValue());
                editor.putBoolean(Preferences.WHITELIST_OTHER.getKey(), !Preferences.WHITELIST_OTHER.getDefaultValue());
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP)
                    editor.putBoolean(Preferences.FILTER.getKey(), !Preferences.FILTER.getDefaultValue()); // Optional
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                editor.putBoolean(Preferences.FILTER.getKey(), !Preferences.FILTER.getDefaultValue()); // Mandatory

            if (!Util.canFilter(context)) {
                editor.putBoolean(Preferences.LOG_APP.getKey(), Preferences.LOG_APP.getDefaultValue());
                editor.putBoolean(Preferences.FILTER.getKey(), Preferences.FILTER.getDefaultValue());
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                editor.remove(Preferences.SHOW_TOP.getKey());
                if (Sort.Data.getValue().equals(prefs.getString(Preferences.SORT.getKey(), Preferences.SORT.getDefaultValue().getValue())))
                    editor.remove(Preferences.SORT.getKey());
            }

            if (Util.isPlayStoreInstall(context)) {
                editor.remove(Preferences.UPDATE_CHECK.getKey());
                editor.remove(Preferences.USE_HOSTS.getKey());
                editor.remove(Preferences.HOSTS_URL.getKey());
            }

            if (!Util.isDebuggable(context))
                editor.remove(Preferences.LOG_LEVEL.getKey());

            editor.putInt(Column.VERSION.getValue(), newVersion);
            editor.apply();
        }
    }
}
