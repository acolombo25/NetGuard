package eu.faircode.netguard.features.shortcut;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import eu.faircode.netguard.ActivityMain;
import eu.faircode.netguard.BuildConfig;
import eu.faircode.netguard.Rule;
import eu.faircode.netguard.Util;

public class ShortcutManager {

    private static ShortcutInfoCompat getInfo(Context context, Rule rule) {

        final Intent shortcut = new Intent(context, ActivityMain.class);
        shortcut.setPackage(context.getPackageName());
        shortcut.setAction(Intent.ACTION_MAIN);
        shortcut.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shortcut.putExtra(ActivityMain.EXTRA_SHORTCUT_PACKAGE, rule.packageName);

        Drawable drawable = Util.getAppIconDrawable(context, rule);
        Bitmap bitmap = null;

        if (drawable!=null) {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }

        String shortcutId = BuildConfig.APPLICATION_ID + ".shortcut." + rule.uid;
        ShortcutInfoCompat.Builder info = new ShortcutInfoCompat.Builder(context, shortcutId)
                .setIntent(shortcut)
                .setShortLabel(rule.name)
                .setAlwaysBadged();

        if (bitmap!=null) info.setIcon(IconCompat.createWithBitmap(bitmap));

        return info.build();
    }

    public static boolean canRequestPinShortcut(Context context) {
        return ShortcutManagerCompat.isRequestPinShortcutSupported(context);
    }

    public static boolean requestPinShortcut(Context context, Rule rule) {
        return ShortcutManagerCompat.requestPinShortcut(context, getInfo(context, rule), null);
    }

    public static Intent getShortcutResultIntent(Context context, Rule rule) {
        return ShortcutManagerCompat.createShortcutResultIntent(context, getInfo(context, rule));
    }

}

