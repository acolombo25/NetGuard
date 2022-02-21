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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.Arrays;
import java.util.List;

import eu.faircode.netguard.preference.DefaultPreferences;
import eu.faircode.netguard.preference.Preferences;
import eu.faircode.netguard.preference.Sort;
import eu.faircode.netguard.reason.SimpleReason;

public class ActivityMain extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "NetGuard.Main";

    private boolean running = false;
    private ImageView ivIcon;
    private View pbQueue;
    private SwitchCompat swEnabled;
    private ImageView ivMetered;
    private SwipeRefreshLayout swipeRefresh;
    private AdapterRule adapter = null;
    private MenuItem menuSearch = null;
    private AlertDialog dialogFirst = null;
    private AlertDialog dialogVpn = null;
    private AlertDialog dialogDoze = null;
    private AlertDialog dialogLegend = null;
    private AlertDialog dialogAbout = null;

    private IAB iab = null;

    private static final int REQUEST_VPN = 1;
    private static final int REQUEST_INVITE = 2;
    private static final int REQUEST_LOGCAT = 3;
    public static final int REQUEST_ROAMING = 4;

    private static final int MIN_SDK = Build.VERSION_CODES.LOLLIPOP_MR1;

    public static final String ACTION_RULES_CHANGED = "eu.faircode.netguard.ACTION_RULES_CHANGED";
    public static final String ACTION_QUEUE_CHANGED = "eu.faircode.netguard.ACTION_QUEUE_CHANGED";
    public static final String EXTRA_REFRESH = "Refresh";
    public static final String EXTRA_SEARCH = "Search";
    public static final String EXTRA_RELATED = "Related";
    public static final String EXTRA_APPROVE = "Approve";
    public static final String EXTRA_LOGCAT = "Logcat";
    public static final String EXTRA_CONNECTED = "Connected";
    public static final String EXTRA_METERED = "Metered";
    public static final String EXTRA_SIZE = "Size";
    public static final String EXTRA_SHORTCUT_PACKAGE = "Shortcut_Package";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Create version=" + Util.getSelfVersionName(this) + "/" + Util.getSelfVersionCode(this));
        Util.logExtras(getIntent());

        // Check minimum Android version
        if (Build.VERSION.SDK_INT < MIN_SDK) {
            Log.i(TAG, "SDK=" + Build.VERSION.SDK_INT);
            super.onCreate(savedInstanceState);
            setContentView(R.layout.android);
            return;
        }

        // Check for Xposed
        if (Util.hasXposed(this)) {
            Log.i(TAG, "Xposed running");
            super.onCreate(savedInstanceState);
            setContentView(R.layout.xposed);
            return;
        }

        checkExtrasAndLaunchShortcut(getIntent());

        Util.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        running = true;

        boolean enabled = DefaultPreferences.getBoolean(this, Preferences.ENABLED);
        boolean initialized = DefaultPreferences.getBoolean(this, Preferences.INITIALIZED);

        // Upgrade
        ReceiverAutostart.upgrade(initialized, this);

        if (!getIntent().hasExtra(EXTRA_APPROVE)) {
            if (enabled)
                ServiceSinkhole.start(SimpleReason.UI, this);
            else
                ServiceSinkhole.stop(SimpleReason.UI, this, false);
        }

        // Action bar
        final View actionView = getLayoutInflater().inflate(R.layout.actionmain, null, false);
        ivIcon = actionView.findViewById(R.id.ivIcon);
        pbQueue = actionView.findViewById(R.id.pbQueue);
        swEnabled = actionView.findViewById(R.id.swEnabled);
        ivMetered = actionView.findViewById(R.id.ivMetered);

        // Icon
        ivIcon.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                menu_about();
                return true;
            }
        });

        // Title
        getSupportActionBar().setTitle(null);

        // On/off switch
        swEnabled.setChecked(enabled);
        swEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.i(TAG, "Switch=" + isChecked);
            DefaultPreferences.putBoolean(ActivityMain.this, Preferences.ENABLED, isChecked);

            if (isChecked) {
                start();
            } else
                ServiceSinkhole.stop(SimpleReason.SwitchOff, ActivityMain.this, false);
            }
        );
        if (enabled)
            checkDoze();

        // Network is metered
        ivMetered.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                int location[] = new int[2];
                actionView.getLocationOnScreen(location);
                Toast toast = Toast.makeText(ActivityMain.this, R.string.msg_metered, Toast.LENGTH_LONG);
                toast.setGravity(
                        Gravity.TOP | Gravity.LEFT,
                        location[0] + ivMetered.getLeft(),
                        Math.round(location[1] + ivMetered.getBottom() - toast.getView().getPaddingTop()));
                toast.show();
                return true;
            }
        });

        getSupportActionBar().setDisplayShowCustomEnabled(true);
        getSupportActionBar().setCustomView(actionView);

        // Disabled warning
        TextView tvDisabled = findViewById(R.id.tvDisabled);
        tvDisabled.setVisibility(enabled ? View.GONE : View.VISIBLE);

        // Application list
        RecyclerView rvApplication = findViewById(R.id.rvApplication);
        rvApplication.setHasFixedSize(false);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setAutoMeasureEnabled(true);
        rvApplication.setLayoutManager(llm);
        adapter = new AdapterRule(this, findViewById(R.id.vwPopupAnchor));
        rvApplication.setAdapter(adapter);

        // Swipe to refresh
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimary, tv, true);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeColors(Color.WHITE, Color.WHITE, Color.WHITE);
        swipeRefresh.setProgressBackgroundColorSchemeColor(tv.data);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Rule.clearCache(ActivityMain.this);
                ServiceSinkhole.reload(SimpleReason.Pull, ActivityMain.this, false);
                updateApplicationList(null);
            }
        });

        // Hint usage
        final LinearLayout llUsage = findViewById(R.id.llUsage);
        Button btnUsage = findViewById(R.id.btnUsage);
        boolean hintUsage = DefaultPreferences.getBoolean(this, Preferences.HINT_USAGE);
        llUsage.setVisibility(hintUsage ? View.VISIBLE : View.GONE);
        btnUsage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DefaultPreferences.toggleBoolean(ActivityMain.this, Preferences.HINT_USAGE);
                llUsage.setVisibility(View.GONE);
                showHints();
            }
        });

        final LinearLayout llFairEmail = findViewById(R.id.llFairEmail);
        TextView tvFairEmail = findViewById(R.id.tvFairEmail);
        tvFairEmail.setMovementMethod(LinkMovementMethod.getInstance());
        Button btnFairEmail = findViewById(R.id.btnFairEmail);
        boolean hintFairEmail = DefaultPreferences.getBoolean(this, Preferences.HINT_FAIR_EMAIL);
        llFairEmail.setVisibility(hintFairEmail && Util.hasValidFingerprint(this) ? View.VISIBLE : View.GONE);
        btnFairEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DefaultPreferences.toggleBoolean(ActivityMain.this, Preferences.HINT_FAIR_EMAIL);
                llFairEmail.setVisibility(View.GONE);
            }
        });

        showHints();

        // Listen for preference changes
        DefaultPreferences.registerListener(this, this);

        // Listen for rule set changes
        IntentFilter ifr = new IntentFilter(ACTION_RULES_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(onRulesChanged, ifr);

        // Listen for queue changes
        IntentFilter ifq = new IntentFilter(ACTION_QUEUE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(onQueueChanged, ifq);

        // Listen for added/removed applications
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        registerReceiver(packageChangedReceiver, intentFilter);

        // First use
        if (!initialized) {
            // Create view
            LayoutInflater inflater = LayoutInflater.from(this);
            View view = inflater.inflate(R.layout.first, null, false);

            TextView tvFirst = view.findViewById(R.id.tvFirst);
            TextView tvEula = view.findViewById(R.id.tvEula);
            TextView tvPrivacy = view.findViewById(R.id.tvPrivacy);
            tvFirst.setMovementMethod(LinkMovementMethod.getInstance());
            tvEula.setMovementMethod(LinkMovementMethod.getInstance());
            tvPrivacy.setMovementMethod(LinkMovementMethod.getInstance());

            // Show dialog
            dialogFirst = new AlertDialog.Builder(this)
                    .setView(view)
                    .setCancelable(false)
                    .setPositiveButton(R.string.app_agree, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (running) {
                                DefaultPreferences.putBoolean(ActivityMain.this, Preferences.INITIALIZED, !Preferences.INITIALIZED.getDefaultValue());
                            }
                        }
                    })
                    .setNegativeButton(R.string.app_disagree, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (running)
                                finish();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            dialogFirst = null;
                        }
                    })
                    .create();
            dialogFirst.show();
        }

        // Fill application list
        updateApplicationList(getIntent().getStringExtra(EXTRA_SEARCH));

        // Update IAB SKUs
        try {
            iab = new IAB(new IAB.Delegate() {
                @Override
                public void onReady(IAB iab) {
                    try {
                        iab.updatePurchases();

                        if (!IAB.isPurchased(ActivityPro.SKU_LOG, ActivityMain.this))
                            DefaultPreferences.resetBoolean(ActivityMain.this, Preferences.LOG);
                        if (!IAB.isPurchased(ActivityPro.SKU_THEME, ActivityMain.this))
                            DefaultPreferences.resetTheme(ActivityMain.this, Preferences.THEME);
                        if (!IAB.isPurchased(ActivityPro.SKU_NOTIFY, ActivityMain.this))
                            DefaultPreferences.resetBoolean(ActivityMain.this, Preferences.INSTALL);
                        if (!IAB.isPurchased(ActivityPro.SKU_SPEED, ActivityMain.this))
                            DefaultPreferences.resetBoolean(ActivityMain.this, Preferences.SHOW_STATS);
                    } catch (Throwable ex) {
                        Util.logException(TAG, ex);
                    } finally {
                        iab.unbind();
                    }
                }
            }, this);
            iab.bind();
        } catch (Throwable ex) {
            Util.logException(TAG, ex);
        }

        // Support
        TextView tvSupport = findViewById(R.id.tvSupport);

        SpannableString content = new SpannableString(getString(R.string.app_support));
        content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
        tvSupport.setText(content);

        tvSupport.setOnClickListener(view -> startActivity(getIntentPro(ActivityMain.this)));

        // Handle intent
        checkExtras(getIntent());
    }

    private boolean start() {
        try {
            String alwaysOn = Settings.Secure.getString(getContentResolver(), "always_on_vpn_app");
            Log.i(TAG, "Always-on=" + alwaysOn);
            if (!TextUtils.isEmpty(alwaysOn))
                if (getPackageName().equals(alwaysOn)) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            DefaultPreferences.getBoolean(ActivityMain.this, Preferences.FILTER)) {
                        int lockdown = Settings.Secure.getInt(getContentResolver(), "always_on_vpn_lockdown", 0);
                        Log.i(TAG, "Lockdown=" + lockdown);
                        if (lockdown != 0) {
                            swEnabled.setChecked(false);
                            Toast.makeText(ActivityMain.this, R.string.msg_always_on_lockdown, Toast.LENGTH_LONG).show();
                            return false;
                        }
                    }
                } else {
                    swEnabled.setChecked(false);
                    Toast.makeText(ActivityMain.this, R.string.msg_always_on, Toast.LENGTH_LONG).show();
                    return false;
                }
        } catch (Throwable ex) {
            Util.logException(TAG, ex);
        }

        boolean filter = DefaultPreferences.getBoolean(ActivityMain.this, Preferences.FILTER);
        if (filter && Util.isPrivateDns(ActivityMain.this))
            Toast.makeText(ActivityMain.this, R.string.msg_private_dns, Toast.LENGTH_LONG).show();

        try {
            final Intent prepare = VpnService.prepare(ActivityMain.this);
            if (prepare == null) {
                Log.i(TAG, "Prepare done");
                onActivityResult(REQUEST_VPN, RESULT_OK, null);
                return true;
            } else {
                // Show dialog
                LayoutInflater inflater = LayoutInflater.from(ActivityMain.this);
                View view = inflater.inflate(R.layout.vpn, null, false);
                dialogVpn = new AlertDialog.Builder(ActivityMain.this)
                        .setView(view)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            if (running) {
                                Log.i(TAG, "Start intent=" + prepare);
                                try {
                                    // com.android.vpndialogs.ConfirmDialog required
                                    startActivityForResult(prepare, REQUEST_VPN);
                                } catch (Throwable ex) {
                                    Util.logException(TAG, ex);
                                    onActivityResult(REQUEST_VPN, RESULT_CANCELED, null);
                                    DefaultPreferences.resetBoolean(this, Preferences.ENABLED);
                                }
                            }
                        })
                        .setOnDismissListener(dialogInterface -> dialogVpn = null)
                        .create();
                dialogVpn.show();
                return false;
            }
        } catch (Throwable ex) {
            // Prepare failed
            Util.logException(TAG, ex);
            DefaultPreferences.resetBoolean(this, Preferences.ENABLED);
            return false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.i(TAG, "New intent");
        Util.logExtras(intent);
        super.onNewIntent(intent);

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this))
            return;

        setIntent(intent);

        if (intent.hasExtra(EXTRA_REFRESH))
            updateApplicationList(intent.getStringExtra(EXTRA_SEARCH));
        else
            updateSearch(intent.getStringExtra(EXTRA_SEARCH));

        checkExtrasAndLaunchShortcut(intent);
        checkExtras(intent);
    }

    private void checkExtrasAndLaunchShortcut(Intent intent) {
        if (intent.hasExtra(EXTRA_SHORTCUT_PACKAGE)) {
            String extraPackage = getIntent().getStringExtra(EXTRA_SHORTCUT_PACKAGE);
            if (!extraPackage.isEmpty()) {
                Intent launch = getPackageManager().getLaunchIntentForPackage(extraPackage);
                boolean canLaunch = launch != null && launch.resolveActivity(getPackageManager()) != null;
                if (canLaunch && start()) startActivity(launch);
            }
        }
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "Resume");

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this)) {
            super.onResume();
            return;
        }

        DatabaseHelper.getInstance(this).addAccessChangedListener(accessChangedListener);
        if (adapter != null)
            adapter.notifyDataSetChanged();

        PackageManager pm = getPackageManager();
        View tvSupport = findViewById(R.id.tvSupport);
        tvSupport.setVisibility(
                IAB.isPurchasedAny(this) || getIntentPro(this).resolveActivity(pm) == null
                        ? View.GONE : View.VISIBLE);

        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "Pause");
        super.onPause();

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this))
            return;

        DatabaseHelper.getInstance(this).removeAccessChangedListener(accessChangedListener);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Log.i(TAG, "Config");
        super.onConfigurationChanged(newConfig);

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this))
            return;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Destroy");

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this)) {
            super.onDestroy();
            return;
        }

        running = false;
        adapter = null;

        DefaultPreferences.unregisterListener(this, this);

        LocalBroadcastManager.getInstance(this).unregisterReceiver(onRulesChanged);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onQueueChanged);
        unregisterReceiver(packageChangedReceiver);

        if (dialogFirst != null) {
            dialogFirst.dismiss();
            dialogFirst = null;
        }
        if (dialogVpn != null) {
            dialogVpn.dismiss();
            dialogVpn = null;
        }
        if (dialogDoze != null) {
            dialogDoze.dismiss();
            dialogDoze = null;
        }
        if (dialogLegend != null) {
            dialogLegend.dismiss();
            dialogLegend = null;
        }
        if (dialogAbout != null) {
            dialogAbout.dismiss();
            dialogAbout = null;
        }

        if (iab != null) {
            iab.unbind();
            iab = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        Log.i(TAG, "onActivityResult request=" + requestCode + " result=" + requestCode + " ok=" + (resultCode == RESULT_OK));
        Util.logExtras(data);

        if (requestCode == REQUEST_VPN) {
            // Handle VPN approval
            DefaultPreferences.putBoolean(this, Preferences.ENABLED, resultCode == RESULT_OK);
            if (resultCode == RESULT_OK) {
                ServiceSinkhole.start(SimpleReason.Prepared, this);
                checkExtrasAndLaunchShortcut(getIntent());

                Toast on = Toast.makeText(ActivityMain.this, R.string.msg_on, Toast.LENGTH_LONG);
                on.setGravity(Gravity.CENTER, 0, 0);
                on.show();

                checkDoze();
            } else if (resultCode == RESULT_CANCELED)
                Toast.makeText(this, R.string.msg_vpn_cancelled, Toast.LENGTH_LONG).show();

        } else if (requestCode == REQUEST_INVITE) {
            // Do nothing

        } else if (requestCode == REQUEST_LOGCAT) {
            // Send logcat by e-mail
            if (resultCode == RESULT_OK) {
                Uri target = data.getData();
                if (data.hasExtra("org.openintents.extra.DIR_PATH"))
                    target = Uri.parse(target + "/logcat.txt");
                Log.i(TAG, "Export URI=" + target);
                Util.sendLogcat(target, this);
            }

        } else {
            Log.w(TAG, "Unknown activity result request=" + requestCode);
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_ROAMING && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ServiceSinkhole.reload(SimpleReason.PermissionGranted, this, false);
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String name) {
        Log.i(TAG, "Preference " + name + "=" + prefs.getAll().get(name));

        if (Preferences.ENABLED.getKey().equals(name)) {
            // Get enabled
            boolean enabled = DefaultPreferences.getBoolean(this, Preferences.ENABLED);

            // Display disabled warning
            TextView tvDisabled = findViewById(R.id.tvDisabled);
            tvDisabled.setVisibility(enabled ? View.GONE : View.VISIBLE);

            // Check switch state
            SwitchCompat swEnabled = getSupportActionBar().getCustomView().findViewById(R.id.swEnabled);
            if (swEnabled.isChecked() != enabled)
                swEnabled.setChecked(enabled);

        } else if (Preferences.WHITELIST_WIFI.getKey().equals(name) ||
                Preferences.SCREEN_ON.getKey().equals(name) ||
                Preferences.SCREEN_WIFI.getKey().equals(name) ||
                Preferences.WHITELIST_OTHER.getKey().equals(name) ||
                Preferences.SCREEN_OTHER.getKey().equals(name) ||
                Preferences.WHITELIST_ROAMING.getKey().equals(name) ||
                Preferences.SHOW_USER.getKey().equals(name) ||
                Preferences.SHOW_SYSTEM.getKey().equals(name) ||
                Preferences.SHOW_NO_INTERNET.getKey().equals(name) ||
                Preferences.SHOW_DISABLED.getKey().equals(name) ||
                Preferences.SORT.getKey().equals(name) ||
                Preferences.IMPORTED.getKey().equals(name)) {
            updateApplicationList(null);

            final LinearLayout llWhitelist = findViewById(R.id.llWhitelist);
            boolean screen_on = DefaultPreferences.getBoolean(this, Preferences.SCREEN_ON);
            boolean whitelist_wifi = DefaultPreferences.getBooleanNotDefault(this, Preferences.WHITELIST_WIFI);
            boolean whitelist_other = DefaultPreferences.getBooleanNotDefault(this, Preferences.WHITELIST_OTHER);
            boolean hintWhitelist = DefaultPreferences.getBoolean(this, Preferences.HINT_WHITELIST);
            llWhitelist.setVisibility(!(whitelist_wifi || whitelist_other) && screen_on && hintWhitelist ? View.VISIBLE : View.GONE);

        } else if (Preferences.MANAGE_SYSTEM.getKey().equals(name)) {
            invalidateOptionsMenu();
            updateApplicationList(null);

            LinearLayout llSystem = findViewById(R.id.llSystem);
            boolean system = DefaultPreferences.getBoolean(this, Preferences.MANAGE_SYSTEM);
            boolean hint = DefaultPreferences.getBoolean(this, Preferences.HINT_SYSTEM);
            llSystem.setVisibility(!system && hint ? View.VISIBLE : View.GONE);

        } else if (Preferences.DEBUG_IAB.getKey().equals(name) || Preferences.THEME.getKey().equals(name) || Preferences.DARK.getKey().equals(name))
            recreate();
    }

    private final DatabaseHelper.AccessChangedListener accessChangedListener = new DatabaseHelper.AccessChangedListener() {
        @Override
        public void onChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (adapter != null && adapter.isLive())
                        adapter.notifyDataSetChanged();
                }
            });
        }
    };

    private final BroadcastReceiver onRulesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            Util.logExtras(intent);

            if (adapter != null)
                if (intent.hasExtra(EXTRA_CONNECTED) && intent.hasExtra(EXTRA_METERED)) {
                    ivIcon.setAlpha(Util.isNetworkActive(ActivityMain.this) ? 1.0f : 0.6f);
                    if (intent.getBooleanExtra(EXTRA_CONNECTED, false)) {
                        if (intent.getBooleanExtra(EXTRA_METERED, false))
                            adapter.setMobileActive();
                        else
                            adapter.setWifiActive();
                        ivMetered.setVisibility(Util.isMeteredNetwork(ActivityMain.this) ? View.VISIBLE : View.INVISIBLE);
                    } else {
                        adapter.setDisconnected();
                        ivMetered.setVisibility(View.INVISIBLE);
                    }
                } else
                    updateApplicationList(null);
        }
    };

    private final BroadcastReceiver onQueueChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            Util.logExtras(intent);
            int size = intent.getIntExtra(EXTRA_SIZE, -1);
            ivIcon.setVisibility(size == 0 ? View.VISIBLE : View.GONE);
            pbQueue.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        }
    };

    private final BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            Util.logExtras(intent);
            updateApplicationList(null);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (Build.VERSION.SDK_INT < MIN_SDK)
            return false;

        PackageManager pm = getPackageManager();

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);

        // Search
        menuSearch = menu.findItem(R.id.menu_search);
        menuSearch.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (getIntent().hasExtra(EXTRA_SEARCH) && !getIntent().getBooleanExtra(EXTRA_RELATED, false))
                    finish();
                return true;
            }
        });

        final SearchView searchView = (SearchView) menuSearch.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (adapter != null)
                    adapter.getFilter().filter(query);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (adapter != null)
                    adapter.getFilter().filter(newText);
                return true;
            }
        });
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                Intent intent = getIntent();
                intent.removeExtra(EXTRA_SEARCH);

                if (adapter != null)
                    adapter.getFilter().filter(null);
                return true;
            }
        });
        String search = getIntent().getStringExtra(EXTRA_SEARCH);
        if (search != null) {
            menuSearch.expandActionView();
            searchView.setQuery(search, true);
        }

        markPro(menu.findItem(R.id.menu_log), ActivityPro.SKU_LOG);
        if (!IAB.isPurchasedAny(this))
            markPro(menu.findItem(R.id.menu_pro), null);

        if (!Util.isPurchasable(this))
            menu.removeItem(R.id.menu_pro);

        if (!Util.hasValidFingerprint(this) || getIntentInvite(this).resolveActivity(pm) == null)
            menu.removeItem(R.id.menu_invite);

        if (!Util.hasValidFingerprint(this) || getIntentSupport().resolveActivity(getPackageManager()) == null)
            menu.removeItem(R.id.menu_support);

        if (!Util.hasValidFingerprint(this) || getIntentApps().resolveActivity(pm) != null && Util.isPlayStoreInstall(this))
            menu.removeItem(R.id.menu_apps);

        return true;
    }

    private void markPro(MenuItem menu, String sku) {
        if (sku == null || !IAB.isPurchased(sku, this)) {
            SpannableStringBuilder ssb = new SpannableStringBuilder("  " + menu.getTitle());
            ssb.setSpan(new ImageSpan(this, R.drawable.ic_shopping_cart), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            menu.setTitle(ssb);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (DefaultPreferences.getBoolean(this, Preferences.MANAGE_SYSTEM)) {
            menu.findItem(R.id.menu_app_user).setChecked(DefaultPreferences.getBoolean(this, Preferences.SHOW_USER));
            menu.findItem(R.id.menu_app_system).setChecked(DefaultPreferences.getBoolean(this, Preferences.SHOW_SYSTEM));
        } else {
            Menu submenu = menu.findItem(R.id.menu_filter).getSubMenu();
            submenu.removeItem(R.id.menu_app_user);
            submenu.removeItem(R.id.menu_app_system);
        }

        menu.findItem(R.id.menu_app_nointernet).setChecked(DefaultPreferences.getBoolean(this, Preferences.SHOW_NO_INTERNET));
        menu.findItem(R.id.menu_app_disabled).setChecked(DefaultPreferences.getBoolean(this, Preferences.SHOW_DISABLED));

        String sort = DefaultPreferences.getSort(this, Preferences.SORT);
        if (Sort.Uid.getValue().equals(sort))
            menu.findItem(R.id.menu_sort_uid).setChecked(true);
        else if (Sort.Name.getValue().equals(sort))
            menu.findItem(R.id.menu_sort_name).setChecked(true);

        menu.findItem(R.id.menu_lockdown).setChecked(DefaultPreferences.getBoolean(this, Preferences.LOCKDOWN));

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "Menu=" + item.getTitle());

        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_app_user:
                item.setChecked(!item.isChecked());
                DefaultPreferences.putBoolean(this, Preferences.SHOW_USER, item.isChecked());
                return true;

            case R.id.menu_app_system:
                item.setChecked(!item.isChecked());
                DefaultPreferences.putBoolean(this, Preferences.SHOW_SYSTEM, item.isChecked());
                return true;

            case R.id.menu_app_nointernet:
                item.setChecked(!item.isChecked());
                DefaultPreferences.putBoolean(this, Preferences.SHOW_NO_INTERNET, item.isChecked());
                return true;

            case R.id.menu_app_disabled:
                item.setChecked(!item.isChecked());
                DefaultPreferences.putBoolean(this, Preferences.SHOW_DISABLED, item.isChecked());
                return true;

            case R.id.menu_sort_name:
                item.setChecked(true);
                DefaultPreferences.putSort(this, Preferences.SORT, Sort.Name);
                return true;

            case R.id.menu_sort_uid:
                item.setChecked(true);
                DefaultPreferences.putSort(this, Preferences.SORT, Sort.Uid);
                return true;

            case R.id.menu_lockdown:
                menu_lockdown(item);
                return true;

            case R.id.menu_log:
                if (Util.canFilter())
                    if (IAB.isPurchased(ActivityPro.SKU_LOG, this))
                        startActivity(new Intent(this, ActivityLog.class));
                    else
                        startActivity(new Intent(this, ActivityPro.class));
                else
                    Toast.makeText(this, R.string.msg_unavailable, Toast.LENGTH_SHORT).show();
                return true;

            case R.id.menu_settings:
                startActivity(new Intent(this, ActivitySettings.class));
                return true;

            case R.id.menu_pro:
                startActivity(new Intent(ActivityMain.this, ActivityPro.class));
                return true;

            case R.id.menu_invite:
                startActivityForResult(getIntentInvite(this), REQUEST_INVITE);
                return true;

            case R.id.menu_legend:
                menu_legend();
                return true;

            case R.id.menu_support:
                startActivity(getIntentSupport());
                return true;

            case R.id.menu_about:
                menu_about();
                return true;

            case R.id.menu_apps:
                menu_apps();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showHints() {
        boolean hintUsage = DefaultPreferences.getBoolean(this, Preferences.HINT_USAGE);

        // Hint white listing
        final LinearLayout llWhitelist = findViewById(R.id.llWhitelist);
        Button btnWhitelist = findViewById(R.id.btnWhitelist);
        boolean whitelist_wifi = DefaultPreferences.getBoolean(this, Preferences.WHITELIST_WIFI);
        boolean whitelist_other = DefaultPreferences.getBoolean(this, Preferences.WHITELIST_OTHER);
        boolean hintWhitelist = DefaultPreferences.getBoolean(this, Preferences.HINT_WHITELIST);
        llWhitelist.setVisibility(!(whitelist_wifi || whitelist_other) && hintWhitelist && !hintUsage ? View.VISIBLE : View.GONE);
        btnWhitelist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DefaultPreferences.toggleBoolean(ActivityMain.this, Preferences.HINT_WHITELIST);
                llWhitelist.setVisibility(View.GONE);
            }
        });

        // Hint push messages
        final LinearLayout llPush = findViewById(R.id.llPush);
        Button btnPush = findViewById(R.id.btnPush);
        boolean hintPush = DefaultPreferences.getBoolean(this, Preferences.HINT_PUSH);
        llPush.setVisibility(hintPush && !hintUsage ? View.VISIBLE : View.GONE);
        btnPush.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DefaultPreferences.toggleBoolean(ActivityMain.this, Preferences.HINT_PUSH);
                llPush.setVisibility(View.GONE);
            }
        });

        // Hint system applications
        final LinearLayout llSystem = findViewById(R.id.llSystem);
        Button btnSystem = findViewById(R.id.btnSystem);
        boolean system = DefaultPreferences.getBoolean(this, Preferences.MANAGE_SYSTEM);
        boolean hintSystem = DefaultPreferences.getBoolean(this, Preferences.HINT_SYSTEM);
        llSystem.setVisibility(!system && hintSystem ? View.VISIBLE : View.GONE);
        btnSystem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DefaultPreferences.toggleBoolean(ActivityMain.this, Preferences.HINT_SYSTEM);
                llSystem.setVisibility(View.GONE);
            }
        });
    }

    private void checkExtras(Intent intent) {
        // Approve request
        if (intent.hasExtra(EXTRA_APPROVE)) {
            Log.i(TAG, "Requesting VPN approval");
            swEnabled.toggle();
        }

        if (intent.hasExtra(EXTRA_LOGCAT)) {
            Log.i(TAG, "Requesting logcat");
            Intent logcat = getIntentLogcat();
            if (logcat.resolveActivity(getPackageManager()) != null)
                startActivityForResult(logcat, REQUEST_LOGCAT);
        }
    }

    private void updateApplicationList(final String search) {
        Log.i(TAG, "Update search=" + search);

        new AsyncTask<Object, Object, List<Rule>>() {
            private boolean refreshing = true;

            @Override
            protected void onPreExecute() {
                swipeRefresh.post(new Runnable() {
                    @Override
                    public void run() {
                        if (refreshing)
                            swipeRefresh.setRefreshing(true);
                    }
                });
            }

            @Override
            protected List<Rule> doInBackground(Object... arg) {
                return Rule.getRules(false, ActivityMain.this);
            }

            @Override
            protected void onPostExecute(List<Rule> result) {
                if (running) {
                    if (adapter != null) {
                        adapter.set(result);
                        updateSearch(search);
                    }

                    if (swipeRefresh != null) {
                        refreshing = false;
                        swipeRefresh.setRefreshing(false);
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void updateSearch(String search) {
        if (menuSearch != null) {
            SearchView searchView = (SearchView) menuSearch.getActionView();
            if (search == null) {
                if (menuSearch.isActionViewExpanded())
                    adapter.getFilter().filter(searchView.getQuery().toString());
            } else {
                menuSearch.expandActionView();
                searchView.setQuery(search, true);
            }
        }
    }

    private void checkDoze() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final Intent doze = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            if (Util.batteryOptimizing(this) && getPackageManager().resolveActivity(doze, 0) != null) {
                if (!DefaultPreferences.getBoolean(this, Preferences.NO_DOZE)) {
                    LayoutInflater inflater = LayoutInflater.from(this);
                    View view = inflater.inflate(R.layout.doze, null, false);
                    final CheckBox cbDontAsk = view.findViewById(R.id.cbDontAsk);
                    dialogDoze = new AlertDialog.Builder(this)
                            .setView(view)
                            .setCancelable(true)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    DefaultPreferences.putBoolean(ActivityMain.this, Preferences.NO_DOZE, cbDontAsk.isChecked());
                                    startActivity(doze);
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    DefaultPreferences.putBoolean(ActivityMain.this, Preferences.NO_DOZE, cbDontAsk.isChecked());
                                }
                            })
                            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialogInterface) {
                                    dialogDoze = null;
                                    checkDataSaving();
                                }
                            })
                            .create();
                    dialogDoze.show();
                } else
                    checkDataSaving();
            } else
                checkDataSaving();
        }
    }

    private void checkDataSaving() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final Intent settings = new Intent(
                    Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            if (Util.dataSaving(this) && getPackageManager().resolveActivity(settings, 0) != null)
                try {
                    if (!DefaultPreferences.getBoolean(this, Preferences.NO_DATA)) {
                        LayoutInflater inflater = LayoutInflater.from(this);
                        View view = inflater.inflate(R.layout.datasaving, null, false);
                        final CheckBox cbDontAsk = view.findViewById(R.id.cbDontAsk);
                        dialogDoze = new AlertDialog.Builder(this)
                                .setView(view)
                                .setCancelable(true)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        DefaultPreferences.putBoolean(ActivityMain.this, Preferences.NO_DATA, cbDontAsk.isChecked());
                                        startActivity(settings);
                                    }
                                })
                                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        DefaultPreferences.putBoolean(ActivityMain.this, Preferences.NO_DATA, cbDontAsk.isChecked());
                                    }
                                })
                                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                    @Override
                                    public void onDismiss(DialogInterface dialogInterface) {
                                        dialogDoze = null;
                                    }
                                })
                                .create();
                        dialogDoze.show();
                    }
                } catch (Throwable ex) {
                    Log.e(TAG, ex + "\n" + Arrays.toString(ex.getStackTrace()));
                }
        }
    }

    private void menu_legend() {
        dialogLegend = new AlertDialog.Builder(this)
                .setView(LayoutInflater.from(this).inflate(R.layout.legend, null, false))
                .setCancelable(true)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        dialogLegend = null;
                    }
                })
                .create();
        dialogLegend.show();
    }

    private void menu_lockdown(MenuItem item) {
        item.setChecked(!item.isChecked());
        DefaultPreferences.putBoolean(this, Preferences.LOCKDOWN, item.isChecked());
        ServiceSinkhole.reload(SimpleReason.Lockdown, this, false);
        WidgetLockdown.updateWidgets(this);
    }

    private void menu_about() {
        // Create view
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.about, null, false);
        TextView tvVersionName = view.findViewById(R.id.tvVersionName);
        TextView tvVersionCode = view.findViewById(R.id.tvVersionCode);
        Button btnRate = view.findViewById(R.id.btnRate);
        TextView tvCopyright = view.findViewById(R.id.tvCopyright);
        TextView tvEula = view.findViewById(R.id.tvEula);
        TextView tvPrivacy = view.findViewById(R.id.tvPrivacy);

        // Show version
        tvVersionName.setText(Util.getSelfVersionName(this));
        if (!Util.hasValidFingerprint(this)) {
            tvVersionName.setTextColor(Color.GRAY);
        }
        tvCopyright.setVisibility(!Util.hasValidFingerprint(this) ? View.GONE : View.VISIBLE);
        tvVersionCode.setText(Integer.toString(Util.getSelfVersionCode(this)));

        // Handle license
        tvEula.setMovementMethod(LinkMovementMethod.getInstance());
        tvPrivacy.setMovementMethod(LinkMovementMethod.getInstance());

        // Handle logcat
        view.setOnClickListener(new View.OnClickListener() {
            private short tap = 0;
            private final Toast toast = Toast.makeText(ActivityMain.this, "", Toast.LENGTH_SHORT);
            private static final int TAPS_NEEDED = 7;

            @Override
            public void onClick(View view) {
                tap++;
                if (tap == TAPS_NEEDED) {
                    tap = 0;
                    toast.cancel();

                    Intent intent = getIntentLogcat();
                    if (intent.resolveActivity(getPackageManager()) != null)
                        startActivityForResult(intent, REQUEST_LOGCAT);

                } else if (tap > TAPS_NEEDED/2) {
                    toast.setText(Integer.toString(TAPS_NEEDED - tap));
                    toast.show();
                }
            }
        });

        // Handle rate
        btnRate.setVisibility(getIntentRate(this).resolveActivity(getPackageManager()) == null ? View.GONE : View.VISIBLE);
        btnRate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(getIntentRate(ActivityMain.this));
            }
        });

        // Show dialog
        dialogAbout = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        dialogAbout = null;
                    }
                })
                .create();
        dialogAbout.show();
    }

    private void menu_apps() {
        startActivity(getIntentApps());
    }

    private static Intent getIntentPro(Context context) {
        if (Util.isPlayStoreInstall(context))
            return new Intent(context, ActivityPro.class);
        else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://contact.faircode.eu/?product=netguardstandalone"));
            return intent;
        }
    }

    private static Intent getIntentInvite(Context context) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name));
        intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.msg_try) + "\n\nhttps://www.netguard.me/\n\n");
        return intent;
    }

    private static Intent getIntentApps() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/dev?id=8420080860664580239"));
    }

    private static Intent getIntentRate(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName()));
        if (intent.resolveActivity(context.getPackageManager()) == null)
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + context.getPackageName()));
        return intent;
    }

    private static Intent getIntentSupport() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://github.com/M66B/NetGuard/blob/master/FAQ.md"));
        return intent;
    }

    private Intent getIntentLogcat() {
        Intent intent;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (Util.isPackageInstalled("org.openintents.filemanager", this)) {
                intent = new Intent("org.openintents.action.PICK_DIRECTORY");
            } else {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=org.openintents.filemanager"));
            }
        } else {
            intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "logcat.txt");
        }
        return intent;
    }
}
