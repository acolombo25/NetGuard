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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CursorAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.CompoundButtonCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import eu.faircode.netguard.database.Column;
import eu.faircode.netguard.preference.Preferences;
import eu.faircode.netguard.preference.DefaultPreferences;
import eu.faircode.netguard.reason.SimpleReason;

public class AdapterRule extends RecyclerView.Adapter<AdapterRule.ViewHolder> implements Filterable {
    private static final String TAG = "NetGuard.Adapter";

    private final View anchor;
    private final LayoutInflater inflater;
    private RecyclerView rv;
    private final int colorText;
    private final int colorChanged;
    private final int colorOn;
    private final int colorOff;
    private final int colorGrayed;
    private boolean wifiActive = true;
    private boolean otherActive = true;
    private boolean live = true;
    private List<Rule> listAll = new ArrayList<>();
    private List<Rule> listFiltered = new ArrayList<>();

    private final List<String> messaging = Arrays.asList(
            "com.discord",
            "com.facebook.mlite",
            "com.facebook.orca",
            "com.instagram.android",
            "com.Slack",
            "com.skype.raider",
            "com.snapchat.android",
            "com.whatsapp",
            "com.whatsapp.w4b"
    );

    private final List<String> download = Arrays.asList(
            "com.google.android.youtube"
    );

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final View view;

        final LinearLayout llApplication;
        final ImageView ivIcon;
        final ImageView ivExpander;
        final TextView tvName;

        final TextView tvHosts;

        final View llInfo;
        final ImageView ivInfo;
        final ImageView ivLockdown;

        final CheckBox cbWifi;
        final ImageView ivScreenWifi;

        final CheckBox cbOther;
        final ImageView ivScreenOther;
        final TextView tvRoaming;

        final View ivMessaging;
        final View ivDownload;

        final LinearLayout llConfiguration;
        final TextView tvUid;
        final TextView tvPackage;
        final TextView tvVersion;
        final TextView tvInternet;
        final TextView tvDisabled;

        final Button btnRelated;
        final ImageButton ibSettings;
        final ImageButton ibLaunch;
        final ImageButton ibAddToHomeScreen;

        final CheckBox cbApply;

        final LinearLayout llScreenWifi;
        final ImageView ivWifiLegend;
        final CheckBox cbScreenWifi;

        final LinearLayout llScreenOther;
        final ImageView ivOtherLegend;
        final CheckBox cbScreenOther;

        final CheckBox cbRoaming;

        final CheckBox cbLockdown;
        final ImageView ivLockdownLegend;

        final ImageButton btnClear;

        final LinearLayout llFilter;
        final ImageView btnLive;
        final TextView tvLogging;
        final Button btnLogging;
        final ListView lvAccess;
        final ImageButton btnClearAccess;
        final CheckBox cbNotify;

        public ViewHolder(View itemView) {
            super(itemView);
            view = itemView;

            llApplication = itemView.findViewById(R.id.llApplication);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            ivExpander = itemView.findViewById(R.id.ivExpander);
            tvName = itemView.findViewById(R.id.tvName);

            tvHosts = itemView.findViewById(R.id.tvHosts);

            llInfo = itemView.findViewById(R.id.llInfo);
            ivInfo = itemView.findViewById(R.id.ivInfo);
            ivLockdown = itemView.findViewById(R.id.ivLockdown);

            cbWifi = itemView.findViewById(R.id.cbWifi);
            ivScreenWifi = itemView.findViewById(R.id.ivScreenWifi);

            cbOther = itemView.findViewById(R.id.cbOther);
            ivScreenOther = itemView.findViewById(R.id.ivScreenOther);
            tvRoaming = itemView.findViewById(R.id.tvRoaming);

            ivMessaging = itemView.findViewById(R.id.ivMessaging);
            ivDownload = itemView.findViewById(R.id.ivDownload);

            llConfiguration = itemView.findViewById(R.id.llConfiguration);
            tvUid = itemView.findViewById(R.id.tvUid);
            tvPackage = itemView.findViewById(R.id.tvPackage);
            tvVersion = itemView.findViewById(R.id.tvVersion);
            tvInternet = itemView.findViewById(R.id.tvInternet);
            tvDisabled = itemView.findViewById(R.id.tvDisabled);

            btnRelated = itemView.findViewById(R.id.btnRelated);
            ibSettings = itemView.findViewById(R.id.ibSettings);
            ibLaunch = itemView.findViewById(R.id.ibLaunch);
            ibAddToHomeScreen = itemView.findViewById(R.id.ibAddToHomeScreen);

            cbApply = itemView.findViewById(R.id.cbApply);

            llScreenWifi = itemView.findViewById(R.id.llScreenWifi);
            ivWifiLegend = itemView.findViewById(R.id.ivWifiLegend);
            cbScreenWifi = itemView.findViewById(R.id.cbScreenWifi);

            llScreenOther = itemView.findViewById(R.id.llScreenOther);
            ivOtherLegend = itemView.findViewById(R.id.ivOtherLegend);
            cbScreenOther = itemView.findViewById(R.id.cbScreenOther);

            cbRoaming = itemView.findViewById(R.id.cbRoaming);

            cbLockdown = itemView.findViewById(R.id.cbLockdown);
            ivLockdownLegend = itemView.findViewById(R.id.ivLockdownLegend);

            btnClear = itemView.findViewById(R.id.btnClear);

            llFilter = itemView.findViewById(R.id.llFilter);
            btnLive = itemView.findViewById(R.id.btnLive);
            tvLogging = itemView.findViewById(R.id.tvLogging);
            btnLogging = itemView.findViewById(R.id.btnLogging);
            lvAccess = itemView.findViewById(R.id.lvAccess);
            btnClearAccess = itemView.findViewById(R.id.btnClearAccess);
            cbNotify = itemView.findViewById(R.id.cbNotify);

            final View wifiParent = (View) cbWifi.getParent();
            wifiParent.post(new Runnable() {
                public void run() {
                    Rect rect = new Rect();
                    cbWifi.getHitRect(rect);
                    rect.bottom += rect.top;
                    rect.right += rect.left;
                    rect.top = 0;
                    rect.left = 0;
                    wifiParent.setTouchDelegate(new TouchDelegate(rect, cbWifi));
                }
            });

            final View otherParent = (View) cbOther.getParent();
            otherParent.post(new Runnable() {
                public void run() {
                    Rect rect = new Rect();
                    cbOther.getHitRect(rect);
                    rect.bottom += rect.top;
                    rect.right += rect.left;
                    rect.top = 0;
                    rect.left = 0;
                    otherParent.setTouchDelegate(new TouchDelegate(rect, cbOther));
                }
            });
        }
    }

    public AdapterRule(Context context, View anchor) {
        this.anchor = anchor;
        this.inflater = LayoutInflater.from(context);

        if (DefaultPreferences.getBoolean(context, Preferences.DARK))
            colorChanged = Color.argb(128, Color.red(Color.DKGRAY), Color.green(Color.DKGRAY), Color.blue(Color.DKGRAY));
        else
            colorChanged = Color.argb(128, Color.red(Color.LTGRAY), Color.green(Color.LTGRAY), Color.blue(Color.LTGRAY));

        TypedArray ta = context.getTheme().obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        try {
            colorText = ta.getColor(0, 0);
        } finally {
            ta.recycle();
        }

        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorOn, tv, true);
        colorOn = tv.data;
        context.getTheme().resolveAttribute(R.attr.colorOff, tv, true);
        colorOff = tv.data;

        colorGrayed = ContextCompat.getColor(context, R.color.colorGrayed);

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.listPreferredItemHeight, typedValue, true);
        int height = TypedValue.complexToDimensionPixelSize(typedValue.data, context.getResources().getDisplayMetrics());

        setHasStableIds(true);
    }

    public void set(List<Rule> listRule) {
        listAll = listRule;
        listFiltered = new ArrayList<>();
        listFiltered.addAll(listRule);
        notifyDataSetChanged();
    }

    public void setWifiActive() {
        wifiActive = true;
        otherActive = false;
        notifyDataSetChanged();
    }

    public void setMobileActive() {
        wifiActive = false;
        otherActive = true;
        notifyDataSetChanged();
    }

    public void setDisconnected() {
        wifiActive = false;
        otherActive = false;
        notifyDataSetChanged();
    }

    public boolean isLive() {
        return this.live;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        rv = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        rv = null;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final Context context = holder.itemView.getContext();

        final boolean log_app = DefaultPreferences.getBoolean(context, Preferences.LOG_APP);
        final boolean filter = DefaultPreferences.getBoolean(context, Preferences.FILTER);
        final boolean notify_access = DefaultPreferences.getBoolean(context, Preferences.NOTIFY_ACCESS);

        // Get rule
        final Rule rule = listFiltered.get(position);

        // Handle expanding/collapsing
        holder.llApplication.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rule.expanded = !rule.expanded;
                notifyItemChanged(holder.getAdapterPosition());
            }
        });

        // Show if non default rules
        holder.itemView.setBackgroundColor(rule.changed ? colorChanged : Color.TRANSPARENT);

        // Show expand/collapse indicator
        holder.ivExpander.setImageLevel(rule.expanded ? 1 : 0);

        // Show application icon
        holder.ivIcon.setImageDrawable(Util.getAppIconDrawable(context, rule));

        // Show application label
        holder.tvName.setText(rule.name);

        // Show application state
        int color = rule.system ? colorOff : colorText;
        if (!rule.internet || !rule.enabled)
            color = Color.argb(128, Color.red(color), Color.green(color), Color.blue(color));
        holder.tvName.setTextColor(color);

        holder.tvHosts.setVisibility(rule.hosts > 0 ? View.VISIBLE : View.GONE);
        holder.tvHosts.setText(Long.toString(rule.hosts));

        // Lockdown settings
        boolean lockdown = DefaultPreferences.getBoolean(context, Preferences.LOCKDOWN);
        boolean lockdown_wifi = DefaultPreferences.getBoolean(context, Preferences.LOCKDOWN_WIFI);
        boolean lockdown_other = DefaultPreferences.getBoolean(context, Preferences.LOCKDOWN_OTHER);
        if ((otherActive && !lockdown_other) || (wifiActive && !lockdown_wifi))
            lockdown = false;

        holder.ivLockdown.setVisibility(lockdown && !rule.lockdown ? View.VISIBLE : View.GONE);
        holder.ivLockdown.setEnabled(rule.apply);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Drawable wrap = DrawableCompat.wrap(holder.ivLockdown.getDrawable());
            DrawableCompat.setTint(wrap, rule.apply ? colorOff : colorGrayed);
        }

        boolean screen_on = DefaultPreferences.getBoolean(context, Preferences.SCREEN_ON);

        // Wi-Fi settings
        holder.cbWifi.setEnabled(rule.apply);
        holder.cbWifi.setAlpha(wifiActive ? 1 : 0.5f);
        holder.cbWifi.setOnCheckedChangeListener(null);
        holder.cbWifi.setChecked(rule.wifi_blocked);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Drawable wrap = DrawableCompat.wrap(CompoundButtonCompat.getButtonDrawable(holder.cbWifi));
            DrawableCompat.setTint(wrap, rule.apply ? (rule.wifi_blocked ? colorOff : colorOn) : colorGrayed);
        }
        holder.cbWifi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                rule.wifi_blocked = isChecked;
                updateRule(context, rule, true, listAll);
            }
        });

        holder.ivScreenWifi.setEnabled(rule.apply);
        holder.ivScreenWifi.setAlpha(wifiActive ? 1 : 0.5f);
        holder.ivScreenWifi.setVisibility(rule.screen_wifi && rule.wifi_blocked ? View.VISIBLE : View.INVISIBLE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Drawable wrap = DrawableCompat.wrap(holder.ivScreenWifi.getDrawable());
            DrawableCompat.setTint(wrap, rule.apply ? colorOn : colorGrayed);
        }

        // Mobile settings
        holder.cbOther.setEnabled(rule.apply);
        holder.cbOther.setAlpha(otherActive ? 1 : 0.5f);
        holder.cbOther.setOnCheckedChangeListener(null);
        holder.cbOther.setChecked(rule.other_blocked);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Drawable wrap = DrawableCompat.wrap(CompoundButtonCompat.getButtonDrawable(holder.cbOther));
            DrawableCompat.setTint(wrap, rule.apply ? (rule.other_blocked ? colorOff : colorOn) : colorGrayed);
        }
        holder.cbOther.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                rule.other_blocked = isChecked;
                updateRule(context, rule, true, listAll);
            }
        });

        holder.ivScreenOther.setEnabled(rule.apply);
        holder.ivScreenOther.setAlpha(otherActive ? 1 : 0.5f);
        holder.ivScreenOther.setVisibility(rule.screen_other && rule.other_blocked ? View.VISIBLE : View.INVISIBLE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Drawable wrap = DrawableCompat.wrap(holder.ivScreenOther.getDrawable());
            DrawableCompat.setTint(wrap, rule.apply ? colorOn : colorGrayed);
        }

        holder.tvRoaming.setTextColor(rule.apply ? colorOff : colorGrayed);
        holder.tvRoaming.setAlpha(otherActive ? 1 : 0.5f);
        holder.tvRoaming.setVisibility(rule.roaming && (!rule.other_blocked || rule.screen_other) ? View.VISIBLE : View.INVISIBLE);

        boolean hasMessaging = messaging.contains(rule.packageName);
        holder.ivMessaging.setVisibility(hasMessaging ? View.VISIBLE : View.INVISIBLE);
        boolean hasDownload = download.contains(rule.packageName);
        holder.ivDownload.setVisibility(hasDownload ? View.VISIBLE : View.INVISIBLE);
        holder.llInfo.setVisibility(hasMessaging || hasDownload ? View.VISIBLE : View.GONE);
        holder.ivInfo.setOnClickListener(v -> {
            String message = "";
            if (hasMessaging) {
                message += context.getString(R.string.title_messaging);
            }
            if (hasDownload) {
                if (hasMessaging) message += "\n\n";
                message += context.getString(R.string.title_download);
            }
            if (message.isEmpty()) return;
            new AlertDialog.Builder(context)
                    .setMessage(message)
                    .setCancelable(true)
                    .create()
                    .show();
        });

        // Expanded configuration section
        holder.llConfiguration.setVisibility(rule.expanded ? View.VISIBLE : View.GONE);

        // Show application details
        holder.tvUid.setText(Integer.toString(rule.uid));
        holder.tvPackage.setText(rule.packageName);
        holder.tvVersion.setText(rule.version);

        // Show application state
        holder.tvInternet.setVisibility(rule.internet ? View.GONE : View.VISIBLE);
        holder.tvDisabled.setVisibility(rule.enabled ? View.GONE : View.VISIBLE);

        // Show related
        holder.btnRelated.setVisibility(rule.relateduids ? View.VISIBLE : View.GONE);
        holder.btnRelated.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent main = new Intent(context, ActivityMain.class);
                main.putExtra(ActivityMain.EXTRA_SEARCH, Integer.toString(rule.uid));
                main.putExtra(ActivityMain.EXTRA_RELATED, true);
                context.startActivity(main);
            }
        });

        // Launch application settings
        if (rule.expanded) {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + rule.packageName));
            final Intent settings = (intent.resolveActivity(context.getPackageManager()) == null ? null : intent);

            holder.ibSettings.setVisibility(settings == null ? View.GONE : View.VISIBLE);
            holder.ibSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    context.startActivity(settings);
                }
            });
        } else {
            holder.ibSettings.setVisibility(View.GONE);
        }

        // Launch application
        if (rule.expanded) {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(rule.packageName);
            final Intent launch = (intent == null ||
                    intent.resolveActivity(context.getPackageManager()) == null ? null : intent);

            holder.ibLaunch.setVisibility(launch == null ? View.GONE : View.VISIBLE);
            holder.ibLaunch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    context.startActivity(launch);
                }
            });
        } else {
            holder.ibLaunch.setVisibility(View.GONE);
        }

        // Add shortcut
        boolean isSupported = ShortcutManagerCompat.isRequestPinShortcutSupported(context);
        if (rule.expanded && isSupported) {
            Intent check = context.getPackageManager().getLaunchIntentForPackage(rule.packageName);
            final Intent launch = (check == null || check.resolveActivity(context.getPackageManager()) == null ? null : check);

            final Intent shortcut = new Intent(context, ActivityMain.class);
            shortcut.setPackage(context.getPackageName());
            shortcut.setAction(Intent.ACTION_MAIN);
            shortcut.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            shortcut.putExtra(ActivityMain.EXTRA_SHORTCUT_PACKAGE, rule.packageName);

            holder.ibAddToHomeScreen.setVisibility(launch == null ? View.GONE : View.VISIBLE);
            holder.ibAddToHomeScreen.setOnClickListener(view -> {

                Drawable drawable = Util.getAppIconDrawable(context, rule);
                Bitmap bitmap;

                if (drawable != null) {
                    bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    drawable.draw(canvas);
                } else {
                    bitmap = null;
                }

                ShortcutInfoCompat.Builder info = new ShortcutInfoCompat.Builder(context, "netguard-shorcut:" + rule.uid)
                        .setIntent(shortcut)
                        .setShortLabel(rule.name)
                        .setAlwaysBadged();
                if(bitmap != null) info.setIcon(IconCompat.createWithBitmap(bitmap));
                ShortcutManagerCompat.requestPinShortcut(context, info.build(), null);
            });
        } else {
            holder.ibAddToHomeScreen.setVisibility(View.GONE);
        }

        // Apply
        holder.cbApply.setEnabled(rule.pkg && filter);
        holder.cbApply.setOnCheckedChangeListener(null);
        holder.cbApply.setChecked(rule.apply);
        holder.cbApply.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                rule.apply = isChecked;
                updateRule(context, rule, true, listAll);
            }
        });

        // Show Wi-Fi screen on condition
        holder.llScreenWifi.setVisibility(screen_on ? View.VISIBLE : View.GONE);
        holder.cbScreenWifi.setEnabled(rule.wifi_blocked && rule.apply);
        holder.cbScreenWifi.setOnCheckedChangeListener(null);
        holder.cbScreenWifi.setChecked(rule.screen_wifi);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Drawable wrap = DrawableCompat.wrap(holder.ivWifiLegend.getDrawable());
            DrawableCompat.setTint(wrap, colorOn);
        }

        holder.cbScreenWifi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                rule.screen_wifi = isChecked;
                updateRule(context, rule, true, listAll);
            }
        });

        // Show mobile screen on condition
        holder.llScreenOther.setVisibility(screen_on ? View.VISIBLE : View.GONE);
        holder.cbScreenOther.setEnabled(rule.other_blocked && rule.apply);
        holder.cbScreenOther.setOnCheckedChangeListener(null);
        holder.cbScreenOther.setChecked(rule.screen_other);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Drawable wrap = DrawableCompat.wrap(holder.ivOtherLegend.getDrawable());
            DrawableCompat.setTint(wrap, colorOn);
        }

        holder.cbScreenOther.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                rule.screen_other = isChecked;
                updateRule(context, rule, true, listAll);
            }
        });

        // Show roaming condition
        holder.cbRoaming.setEnabled((!rule.other_blocked || rule.screen_other) && rule.apply);
        holder.cbRoaming.setOnCheckedChangeListener(null);
        holder.cbRoaming.setChecked(rule.roaming);
        holder.cbRoaming.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            @TargetApi(Build.VERSION_CODES.M)
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                rule.roaming = isChecked;
                updateRule(context, rule, true, listAll);
            }
        });

        // Show lockdown
        holder.cbLockdown.setEnabled(rule.apply);
        holder.cbLockdown.setOnCheckedChangeListener(null);
        holder.cbLockdown.setChecked(rule.lockdown);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Drawable wrap = DrawableCompat.wrap(holder.ivLockdownLegend.getDrawable());
            DrawableCompat.setTint(wrap, colorOn);
        }

        holder.cbLockdown.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            @TargetApi(Build.VERSION_CODES.M)
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                rule.lockdown = isChecked;
                updateRule(context, rule, true, listAll);
            }
        });

        // Reset rule
        holder.btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Util.areYouSure(view.getContext(), R.string.msg_clear_rules, new Util.DoubtListener() {
                    @Override
                    public void onSure() {
                        holder.cbApply.setChecked(true);
                        holder.cbWifi.setChecked(rule.wifi_default);
                        holder.cbOther.setChecked(rule.other_default);
                        holder.cbScreenWifi.setChecked(rule.screen_wifi_default);
                        holder.cbScreenOther.setChecked(rule.screen_other_default);
                        holder.cbRoaming.setChecked(rule.roaming_default);
                        holder.cbLockdown.setChecked(false);
                    }
                });
            }
        });

        holder.llFilter.setVisibility(Util.canFilter() ? View.VISIBLE : View.GONE);

        // Live
        holder.btnLive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                live = !live;
                holder.btnLive.setImageResource(live ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
                if (live) AdapterRule.this.notifyDataSetChanged();
            }
        });

        // Show logging/filtering is disabled
        holder.tvLogging.setText(log_app && filter ? R.string.title_logging_enabled : R.string.title_logging_disabled);
        holder.btnLogging.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LayoutInflater inflater = LayoutInflater.from(context);
                View view = inflater.inflate(R.layout.enable, null, false);

                final CheckBox cbLogging = view.findViewById(R.id.cbLogging);
                final CheckBox cbFiltering = view.findViewById(R.id.cbFiltering);
                final CheckBox cbNotify = view.findViewById(R.id.cbNotify);
                TextView tvFilter4 = view.findViewById(R.id.tvFilter4);

                cbLogging.setChecked(log_app);
                cbFiltering.setChecked(filter);
                cbFiltering.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
                tvFilter4.setVisibility(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? View.GONE : View.VISIBLE);
                cbNotify.setChecked(notify_access);
                cbNotify.setEnabled(log_app);

                cbLogging.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                        DefaultPreferences.putBoolean(context, Preferences.LOG_APP, checked);
                        cbNotify.setEnabled(checked);
                        if (!checked) {
                            cbNotify.setChecked(false);
                            DefaultPreferences.resetBoolean(context, Preferences.NOTIFY_ACCESS);
                        }
                        ServiceSinkhole.reload(SimpleReason.ChangedNotify, context, false);
                        AdapterRule.this.notifyDataSetChanged();
                    }
                });

                cbFiltering.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                        if (checked)
                            cbLogging.setChecked(true);
                        DefaultPreferences.putBoolean(context, Preferences.FILTER, checked);
                        ServiceSinkhole.reload(SimpleReason.ChangedFilter, context, false);
                        AdapterRule.this.notifyDataSetChanged();
                    }
                });

                cbNotify.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                        DefaultPreferences.putBoolean(context, Preferences.NOTIFY_ACCESS, checked);
                        ServiceSinkhole.reload(SimpleReason.ChangedNotify, context, false);
                        AdapterRule.this.notifyDataSetChanged();
                    }
                });

                AlertDialog dialog = new AlertDialog.Builder(context)
                        .setView(view)
                        .setCancelable(true)
                        .create();
                dialog.show();
            }
        });

        // Show access rules
        if (rule.expanded) {
            // Access the database when expanded only
            final AdapterAccess badapter = new AdapterAccess(context,
                    DatabaseHelper.getInstance(context).getAccess(rule.uid));
            holder.lvAccess.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                @SuppressLint("Range")
                public void onItemClick(AdapterView<?> parent, View view, final int bposition, long bid) {
                    PackageManager pm = context.getPackageManager();
                    Cursor cursor = (Cursor) badapter.getItem(bposition);
                    final long id = cursor.getLong(cursor.getColumnIndex(Column.ID.getValue()));
                    final int version = cursor.getInt(cursor.getColumnIndex(Column.VERSION.getValue()));
                    final int protocol = cursor.getInt(cursor.getColumnIndex(Column.PROTOCOL.getValue()));
                    final String daddr = cursor.getString(cursor.getColumnIndex(Column.DADDR.getValue()));
                    final int dport = cursor.getInt(cursor.getColumnIndex(Column.DPORT.getValue()));
                    long time = cursor.getLong(cursor.getColumnIndex(Column.TIME.getValue()));
                    int block = cursor.getInt(cursor.getColumnIndex(Column.BLOCK.getValue()));

                    PopupMenu popup = new PopupMenu(context, anchor);
                    popup.inflate(R.menu.access);

                    popup.getMenu().findItem(R.id.menu_host).setTitle(
                            Util.getProtocolName(protocol, version, false) + " " +
                                    daddr + (dport > 0 ? "/" + dport : ""));

                    SubMenu sub = popup.getMenu().findItem(R.id.menu_host).getSubMenu();
                    boolean multiple = false;
                    try (Cursor alt = DatabaseHelper.getInstance(context).getAlternateQNames(daddr)) {
                        while (alt.moveToNext()) {
                            multiple = true;
                            sub.add(Menu.NONE, Menu.NONE, 0, alt.getString(0)).setEnabled(false);
                        }
                    }
                    popup.getMenu().findItem(R.id.menu_host).setEnabled(multiple);

                    markPro(context, popup.getMenu().findItem(R.id.menu_allow), ActivityPro.SKU_FILTER);
                    markPro(context, popup.getMenu().findItem(R.id.menu_block), ActivityPro.SKU_FILTER);

                    // Whois
                    final Intent lookupIP = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dnslytics.com/whois-lookup/" + daddr));
                    if (pm.resolveActivity(lookupIP, 0) == null)
                        popup.getMenu().removeItem(R.id.menu_whois);
                    else
                        popup.getMenu().findItem(R.id.menu_whois).setTitle(context.getString(R.string.title_log_whois, daddr));

                    // Lookup port
                    final Intent lookupPort = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.speedguide.net/port.php?port=" + dport));
                    if (dport <= 0 || pm.resolveActivity(lookupPort, 0) == null)
                        popup.getMenu().removeItem(R.id.menu_port);
                    else
                        popup.getMenu().findItem(R.id.menu_port).setTitle(context.getString(R.string.title_log_port, dport));

                    popup.getMenu().findItem(R.id.menu_time).setTitle(
                            SimpleDateFormat.getDateTimeInstance().format(time));

                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {
                            int menu = menuItem.getItemId();
                            boolean result = false;
                            switch (menu) {
                                case R.id.menu_whois:
                                    context.startActivity(lookupIP);
                                    result = true;
                                    break;

                                case R.id.menu_port:
                                    context.startActivity(lookupPort);
                                    result = true;
                                    break;

                                case R.id.menu_allow:
                                    if (IAB.isPurchased(ActivityPro.SKU_FILTER, context)) {
                                        DatabaseHelper.getInstance(context).setAccess(id, 0);
                                        ServiceSinkhole.reload(SimpleReason.AllowHost, context, false);
                                    } else
                                        context.startActivity(new Intent(context, ActivityPro.class));
                                    result = true;
                                    break;

                                case R.id.menu_block:
                                    if (IAB.isPurchased(ActivityPro.SKU_FILTER, context)) {
                                        DatabaseHelper.getInstance(context).setAccess(id, 1);
                                        ServiceSinkhole.reload(SimpleReason.BlockHost, context, false);
                                    } else
                                        context.startActivity(new Intent(context, ActivityPro.class));
                                    result = true;
                                    break;

                                case R.id.menu_reset:
                                    DatabaseHelper.getInstance(context).setAccess(id, -1);
                                    ServiceSinkhole.reload(SimpleReason.ResetHost, context, false);
                                    result = true;
                                    break;

                                case R.id.menu_copy:
                                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                    ClipData clip = ClipData.newPlainText(context.getString(R.string.app_name), daddr);
                                    clipboard.setPrimaryClip(clip);
                                    return true;
                            }

                            if (menu == R.id.menu_allow || menu == R.id.menu_block || menu == R.id.menu_reset)
                                new AsyncTask<Object, Object, Long>() {
                                    @Override
                                    protected Long doInBackground(Object... objects) {
                                        return DatabaseHelper.getInstance(context).getHostCount(rule.uid, false);
                                    }

                                    @Override
                                    protected void onPostExecute(Long hosts) {
                                        rule.hosts = hosts;
                                        notifyDataSetChanged();
                                    }
                                }.execute();

                            return result;
                        }
                    });

                    if (block == 0)
                        popup.getMenu().removeItem(R.id.menu_allow);
                    else if (block == 1)
                        popup.getMenu().removeItem(R.id.menu_block);

                    popup.show();
                }
            });

            holder.lvAccess.setAdapter(badapter);
        } else {
            holder.lvAccess.setAdapter(null);
            holder.lvAccess.setOnItemClickListener(null);
        }

        // Clear access log
        holder.btnClearAccess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Util.areYouSure(view.getContext(), R.string.msg_reset_access, new Util.DoubtListener() {
                    @Override
                    public void onSure() {
                        DatabaseHelper.getInstance(context).clearAccess(rule.uid, true);
                        if (!live)
                            notifyDataSetChanged();
                        if (rv != null)
                            rv.scrollToPosition(holder.getAdapterPosition());
                    }
                });
            }
        });

        // Notify on access
        holder.cbNotify.setEnabled(DefaultPreferences.getBoolean(context, Preferences.NOTIFY_ACCESS) && rule.apply);
        holder.cbNotify.setOnCheckedChangeListener(null);
        holder.cbNotify.setChecked(rule.notify);
        holder.cbNotify.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                rule.notify = isChecked;
                updateRule(context, rule, true, listAll);
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);

        CursorAdapter adapter = (CursorAdapter) holder.lvAccess.getAdapter();
        if (adapter != null) {
            Log.i(TAG, "Closing access cursor");
            adapter.changeCursor(null);
            holder.lvAccess.setAdapter(null);
        }
    }

    private void markPro(Context context, MenuItem menu, @SuppressWarnings("SameParameterValue") String sku) {
        if (sku == null || !IAB.isPurchased(sku, context)) {
            SpannableStringBuilder ssb = new SpannableStringBuilder("  " + menu.getTitle());
            ssb.setSpan(new ImageSpan(context, R.drawable.ic_shopping_cart), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            menu.setTitle(ssb);
        }
    }

    private void updateRule(Context context, Rule rule, boolean root, List<Rule> listAll) {
        SharedPreferences wifi = context.getSharedPreferences(Preferences.WIFI.getKey(), Context.MODE_PRIVATE);
        SharedPreferences other = context.getSharedPreferences(Preferences.OTHER.getKey(), Context.MODE_PRIVATE);
        SharedPreferences apply = context.getSharedPreferences(Preferences.APPLY.getKey(), Context.MODE_PRIVATE);
        SharedPreferences screen_wifi = context.getSharedPreferences(Preferences.SCREEN_WIFI.getKey(), Context.MODE_PRIVATE);
        SharedPreferences screen_other = context.getSharedPreferences(Preferences.SCREEN_OTHER.getKey(), Context.MODE_PRIVATE);
        SharedPreferences roaming = context.getSharedPreferences(Preferences.ROAMING.getKey(), Context.MODE_PRIVATE);
        SharedPreferences lockdown = context.getSharedPreferences(Preferences.LOCKDOWN.getKey(), Context.MODE_PRIVATE);
        SharedPreferences notify = context.getSharedPreferences(Preferences.NOTIFY.getKey(), Context.MODE_PRIVATE);

        if (rule.wifi_blocked == rule.wifi_default)
            wifi.edit().remove(rule.packageName).apply();
        else
            wifi.edit().putBoolean(rule.packageName, rule.wifi_blocked).apply();

        if (rule.other_blocked == rule.other_default)
            other.edit().remove(rule.packageName).apply();
        else
            other.edit().putBoolean(rule.packageName, rule.other_blocked).apply();

        if (rule.apply)
            apply.edit().remove(rule.packageName).apply();
        else
            apply.edit().putBoolean(rule.packageName, rule.apply).apply();

        if (rule.screen_wifi == rule.screen_wifi_default)
            screen_wifi.edit().remove(rule.packageName).apply();
        else
            screen_wifi.edit().putBoolean(rule.packageName, rule.screen_wifi).apply();

        if (rule.screen_other == rule.screen_other_default)
            screen_other.edit().remove(rule.packageName).apply();
        else
            screen_other.edit().putBoolean(rule.packageName, rule.screen_other).apply();

        if (rule.roaming == rule.roaming_default)
            roaming.edit().remove(rule.packageName).apply();
        else
            roaming.edit().putBoolean(rule.packageName, rule.roaming).apply();

        if (rule.lockdown)
            lockdown.edit().putBoolean(rule.packageName, rule.lockdown).apply();
        else
            lockdown.edit().remove(rule.packageName).apply();

        if (rule.notify)
            notify.edit().remove(rule.packageName).apply();
        else
            notify.edit().putBoolean(rule.packageName, rule.notify).apply();

        rule.updateChanged(context);
        Log.i(TAG, "Updated " + rule);

        List<Rule> listModified = new ArrayList<>();
        for (String pkg : rule.related) {
            for (Rule related : listAll)
                if (related.packageName.equals(pkg)) {
                    related.wifi_blocked = rule.wifi_blocked;
                    related.other_blocked = rule.other_blocked;
                    related.apply = rule.apply;
                    related.screen_wifi = rule.screen_wifi;
                    related.screen_other = rule.screen_other;
                    related.roaming = rule.roaming;
                    related.lockdown = rule.lockdown;
                    related.notify = rule.notify;
                    listModified.add(related);
                }
        }

        List<Rule> listSearch = (root ? new ArrayList<>(listAll) : listAll);
        listSearch.remove(rule);
        for (Rule modified : listModified)
            listSearch.remove(modified);
        for (Rule modified : listModified)
            updateRule(context, modified, false, listSearch);

        if (root) {
            notifyDataSetChanged();
            NotificationManagerCompat.from(context).cancel(rule.uid);
            ServiceSinkhole.reload(SimpleReason.RuleChanged, context, false);
        }
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence query) {
                List<Rule> listResult = new ArrayList<>();
                if (query == null)
                    listResult.addAll(listAll);
                else {
                    query = query.toString().toLowerCase().trim();
                    int uid;
                    try {
                        uid = Integer.parseInt(query.toString());
                    } catch (NumberFormatException ignore) {
                        uid = -1;
                    }
                    for (Rule rule : listAll)
                        if (rule.uid == uid ||
                                rule.packageName.toLowerCase().contains(query) ||
                                (rule.name != null && rule.name.toLowerCase().contains(query)))
                            listResult.add(rule);
                }

                FilterResults result = new FilterResults();
                result.values = listResult;
                result.count = listResult.size();
                return result;
            }

            @Override
            protected void publishResults(CharSequence query, FilterResults result) {
                listFiltered.clear();
                if (result == null)
                    listFiltered.addAll(listAll);
                else {
                    listFiltered.addAll((List<Rule>) result.values);
                    if (listFiltered.size() == 1)
                        listFiltered.get(0).expanded = true;
                }
                notifyDataSetChanged();
            }
        };
    }

    @NonNull
    @Override
    public AdapterRule.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.rule, parent, false));
    }

    @Override
    public long getItemId(int position) {
        Rule rule = listFiltered.get(position);
        return (long) rule.packageName.hashCode() * Uid.USER_FACTOR + rule.uid;
    }

    @Override
    public int getItemCount() {
        return listFiltered.size();
    }
}
