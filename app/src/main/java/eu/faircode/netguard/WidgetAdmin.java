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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import java.util.Date;

import eu.faircode.netguard.preference.Preferences;
import eu.faircode.netguard.preference.DefaultPreferences;
import eu.faircode.netguard.reason.SimpleReason;

public class WidgetAdmin extends ReceiverAutostart {
    private static final String TAG = "NetGuard.Widget";

    public static final String INTENT_ON = "eu.faircode.netguard.ON";
    public static final String INTENT_OFF = "eu.faircode.netguard.OFF";

    public static final String INTENT_LOCKDOWN_ON = "eu.faircode.netguard.LOCKDOWN_ON";
    public static final String INTENT_LOCKDOWN_OFF = "eu.faircode.netguard.LOCKDOWN_OFF";

    private static final int MILLIS_VIBRATION = 50;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        Log.i(TAG, "Received " + intent);
        Util.logExtras(intent);

        // Cancel set alarm
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(INTENT_ON);
        i.setPackage(context.getPackageName());
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        if (INTENT_ON.equals(intent.getAction()) || INTENT_OFF.equals(intent.getAction()))
            am.cancel(pi);

        // Vibrate
        Vibrator vs = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vs.hasVibrator())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vs.vibrate(VibrationEffect.createOneShot(MILLIS_VIBRATION, VibrationEffect.DEFAULT_AMPLITUDE));
            else
                vs.vibrate(MILLIS_VIBRATION);

        try {
            if (INTENT_ON.equals(intent.getAction()) || INTENT_OFF.equals(intent.getAction())) {
                boolean enabled = INTENT_ON.equals(intent.getAction());
                DefaultPreferences.putBoolean(context, Preferences.ENABLED, enabled);
                if (enabled)
                    ServiceSinkhole.start(SimpleReason.Widget, context);
                else
                    ServiceSinkhole.stop(SimpleReason.Widget, context, false);

                // Auto enable
                int auto = DefaultPreferences.getBoxedInt(context, Preferences.AUTO_ENABLE);
                if (!enabled && auto > 0) {
                    Log.i(TAG, "Scheduling enabled after minutes=" + auto);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                        am.set(AlarmManager.RTC_WAKEUP, new Date().getTime() + auto * 60 * 1000L, pi);
                    else
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, new Date().getTime() + auto * 60 * 1000L, pi);
                }

            } else if (INTENT_LOCKDOWN_ON.equals(intent.getAction()) || INTENT_LOCKDOWN_OFF.equals(intent.getAction())) {
                boolean lockdown = INTENT_LOCKDOWN_ON.equals(intent.getAction());
                DefaultPreferences.putBoolean(context, Preferences.LOCKDOWN, lockdown);
                ServiceSinkhole.reload(SimpleReason.Widget, context, false);
                WidgetLockdown.updateWidgets(context);
            }
        } catch (Throwable ex) {
            Util.logException(TAG, ex);
        }
    }
}
