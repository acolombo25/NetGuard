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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import androidx.preference.PreferenceManager;

import eu.faircode.netguard.preference.Preferences;

public class WidgetMain extends AppWidgetProvider {
    private static final String TAG = "NetGuard.Widget";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(appWidgetIds, appWidgetManager, context);
    }

    private static void update(int[] appWidgetIds, AppWidgetManager appWidgetManager, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enabled = prefs.getBoolean(Preferences.ENABLED.getKey(), Preferences.ENABLED.getDefaultValue());

        try {
            try {
                Intent intent = new Intent(enabled ? WidgetAdmin.INTENT_OFF : WidgetAdmin.INTENT_ON);
                intent.setPackage(context.getPackageName());
                PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                for (int id : appWidgetIds) {
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
                    views.setOnClickPendingIntent(R.id.ivEnabled, pi);
                    views.setImageViewResource(R.id.ivEnabled, enabled ? R.drawable.ic_twotone_security : R.drawable.ic_security);
                    appWidgetManager.updateAppWidget(id, views);
                }
            } catch (Throwable ex) {
                Util.logException(TAG, ex);
            }
        } catch (Throwable ex) {
            Util.logException(TAG, ex);
        }
    }

    public static void updateWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int appWidgetIds[] = AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, WidgetMain.class));
        update(appWidgetIds, appWidgetManager, context);
    }
}
