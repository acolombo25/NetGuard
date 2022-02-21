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


import java.util.Map;

import eu.faircode.netguard.preference.Preferences;
import eu.faircode.netguard.preference.DefaultPreferences;
import eu.faircode.netguard.preference.Sort;
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
                if (DefaultPreferences.getBoolean(context, Preferences.ENABLED))
                    ServiceSinkhole.start(SimpleReason.Receiver, context);
                else if (DefaultPreferences.getBoolean(context, Preferences.SHOW_STATS))
                    ServiceSinkhole.run(SimpleReason.Receiver, context);

                if (Util.isInteractive(context))
                    ServiceSinkhole.reloadStats(SimpleReason.Receiver, context);
            } catch (Throwable ex) {
                Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }
    }

    public static void upgrade(boolean initialized, Context context) {
        synchronized (context.getApplicationContext()) {
            int oldVersion = DefaultPreferences.getInt(context, Preferences.VERSION);
            int newVersion = Util.getSelfVersionCode(context);
            if (oldVersion == newVersion)
                return;
            Log.i(TAG, "Upgrading from version " + oldVersion + " to " + newVersion);

            SharedPreferences.Editor editor = DefaultPreferences.Editor.get(context);

            if (initialized) {
                if (oldVersion < 38) {
                    Log.i(TAG, "Converting screen wifi/mobile");
                    boolean isUnused = DefaultPreferences.getBoolean(context, Preferences.UNUSED);
                    DefaultPreferences.Editor.putBoolean(editor, Preferences.SCREEN_WIFI, isUnused);
                    DefaultPreferences.Editor.putBoolean(editor, Preferences.SCREEN_OTHER, isUnused);
                    DefaultPreferences.Editor.remove(editor, Preferences.UNUSED);

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
                DefaultPreferences.Editor.resetBoolean(editor, Preferences.FILTER_UDP);
                DefaultPreferences.Editor.putBoolean(editor, Preferences.WHITELIST_WIFI, !Preferences.WHITELIST_WIFI.getDefaultValue());
                DefaultPreferences.Editor.putBoolean(editor, Preferences.WHITELIST_OTHER, !Preferences.WHITELIST_OTHER.getDefaultValue());
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP)
                    DefaultPreferences.Editor.putBoolean(editor, Preferences.FILTER, !Preferences.FILTER.getDefaultValue()); // Optional
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                DefaultPreferences.Editor.putBoolean(editor, Preferences.FILTER, !Preferences.FILTER.getDefaultValue()); // Mandatory

            if (!Util.canFilter(context)) {
                DefaultPreferences.Editor.resetBoolean(editor, Preferences.LOG_APP);
                DefaultPreferences.Editor.resetBoolean(editor, Preferences.FILTER);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                DefaultPreferences.Editor.remove(editor, Preferences.SHOW_TOP);
                if (Sort.Data.getValue().equals(DefaultPreferences.getSort(context, Preferences.SORT)))
                    DefaultPreferences.Editor.remove(editor, Preferences.SORT);
            }

            if (Util.isPlayStoreInstall(context)) {
                DefaultPreferences.Editor.remove(editor, Preferences.UPDATE_CHECK);
                DefaultPreferences.Editor.remove(editor, Preferences.USE_HOSTS);
                DefaultPreferences.Editor.remove(editor, Preferences.HOSTS_URL);
            }

            if (!Util.isDebuggable(context))
                DefaultPreferences.Editor.remove(editor, Preferences.LOG_LEVEL);

            DefaultPreferences.Editor.putInt(editor, Preferences.VERSION, newVersion);
            editor.apply();
        }
    }
}
