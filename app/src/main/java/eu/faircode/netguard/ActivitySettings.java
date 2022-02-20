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

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.core.util.PatternsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import eu.faircode.netguard.Serializer.Serializer;
import eu.faircode.netguard.Serializer.SerializerType;
import eu.faircode.netguard.database.Column;
import eu.faircode.netguard.format.Files;
import eu.faircode.netguard.preference.Preferences;
import eu.faircode.netguard.reason.Changed;
import eu.faircode.netguard.reason.SimpleReason;

public class ActivitySettings extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "NetGuard.Settings";

    private boolean running = false;

    private static final int REQUEST_EXPORT = 1;
    private static final int REQUEST_IMPORT = 2;
    private static final int REQUEST_HOSTS = 3;
    private static final int REQUEST_HOSTS_APPEND = 4;
    private static final int REQUEST_CALL = 5;

    private static final String TAG_APPLICATION = "application";
    private static final String TAG_RULE = "rule";
    private static final String TAG_PKG = "pkg";

    private static final String ERROR_BAD_ADDRESS = "Bad address";

    private AlertDialog dialogFilter = null;

    private static final Intent INTENT_VPN_SETTINGS = new Intent("android.net.vpn.SETTINGS");

    protected void onCreate(Bundle savedInstanceState) {
        Util.setTheme(this);
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new FragmentSettings()).commit();
        getSupportActionBar().setTitle(R.string.menu_settings);
        running = true;
    }

    private PreferenceScreen getPreferenceScreen() {
        return ((PreferenceFragment) getFragmentManager().findFragmentById(android.R.id.content)).getPreferenceScreen();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        final PreferenceScreen screen = getPreferenceScreen();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        PreferenceGroup cat_options = (PreferenceGroup) ((PreferenceGroup) screen.findPreference("screen_options")).findPreference("category_options");
        PreferenceGroup cat_network = (PreferenceGroup) ((PreferenceGroup) screen.findPreference("screen_network_options")).findPreference("category_network_options");
        PreferenceGroup cat_advanced = (PreferenceGroup) ((PreferenceGroup) screen.findPreference("screen_advanced_options")).findPreference("category_advanced_options");
        PreferenceGroup cat_stats = (PreferenceGroup) ((PreferenceGroup) screen.findPreference("screen_stats")).findPreference("category_stats");
        PreferenceGroup cat_backup = (PreferenceGroup) ((PreferenceGroup) screen.findPreference("screen_backup")).findPreference("category_backup");

        // Handle auto enable
        Preference pref_auto_enable = screen.findPreference(Preferences.AUTO_ENABLE.getKey());
        pref_auto_enable.setTitle(getString(R.string.setting_auto, prefs.getInt(Preferences.AUTO_ENABLE.getKey(), Preferences.AUTO_ENABLE.getDefaultValue())));

        // Handle screen delay
        Preference pref_screen_delay = screen.findPreference(Preferences.SCREEN_DELAY.getKey());
        pref_screen_delay.setTitle(getString(R.string.setting_delay, prefs.getInt(Preferences.SCREEN_DELAY.getKey(), Preferences.SCREEN_DELAY.getDefaultValue())));

        // Handle theme
        Preference pref_screen_theme = screen.findPreference(Preferences.THEME.getKey());
        String theme = prefs.getString(Preferences.THEME.getKey(), Preferences.THEME.getDefaultValue().getValue());
        String[] themeNames = getResources().getStringArray(R.array.themeNames);
        String[] themeValues = getResources().getStringArray(R.array.themeValues);
        for (int i = 0; i < themeNames.length; i++)
            if (theme.equals(themeValues[i])) {
                pref_screen_theme.setTitle(getString(R.string.setting_theme, themeNames[i]));
                break;
            }

        // Wi-Fi home
        MultiSelectListPreference pref_wifi_homes = (MultiSelectListPreference) screen.findPreference(Preferences.WIFI_HOMES.getKey());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
            cat_network.removePreference(pref_wifi_homes);
        else {
            Set<String> ssids = prefs.getStringSet(Preferences.WIFI_HOMES.getKey(), Preferences.WIFI_HOMES.getDefaultValue());
            if (ssids.size() > 0)
                pref_wifi_homes.setTitle(getString(R.string.setting_wifi_home, TextUtils.join(", ", ssids)));
            else
                pref_wifi_homes.setTitle(getString(R.string.setting_wifi_home, "-"));

            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            List<CharSequence> listSSID = new ArrayList<>();
            List<WifiConfiguration> configs = wm.getConfiguredNetworks();
            if (configs != null)
                for (WifiConfiguration config : configs)
                    listSSID.add(config.SSID == null ? "NULL" : config.SSID);
            for (String ssid : ssids)
                if (!listSSID.contains(ssid))
                    listSSID.add(ssid);
            pref_wifi_homes.setEntries(listSSID.toArray(new CharSequence[0]));
            pref_wifi_homes.setEntryValues(listSSID.toArray(new CharSequence[0]));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            TwoStatePreference pref_handover =
                    (TwoStatePreference) screen.findPreference("handover");
            cat_advanced.removePreference(pref_handover);
        }

        Preference pref_reset_usage = screen.findPreference("reset_usage");
        pref_reset_usage.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Util.areYouSure(ActivitySettings.this, R.string.setting_reset_usage, new Util.DoubtListener() {
                    @Override
                    public void onSure() {
                        new AsyncTask<Object, Object, Throwable>() {
                            @Override
                            protected Throwable doInBackground(Object... objects) {
                                try {
                                    DatabaseHelper.getInstance(ActivitySettings.this).resetUsage(-1);
                                    return null;
                                } catch (Throwable ex) {
                                    return ex;
                                }
                            }

                            @Override
                            protected void onPostExecute(Throwable ex) {
                                if (ex == null)
                                    Toast.makeText(ActivitySettings.this, R.string.msg_completed, Toast.LENGTH_LONG).show();
                                else
                                    Toast.makeText(ActivitySettings.this, ex.toString(), Toast.LENGTH_LONG).show();
                            }
                        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }
                });
                return false;
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TwoStatePreference pref_reload_onconnectivity = (TwoStatePreference) screen.findPreference(Preferences.RELOAD_CONNECTIVITY.getKey());
            pref_reload_onconnectivity.setChecked(true);
            pref_reload_onconnectivity.setEnabled(false);
        }

        // Handle port forwarding
        Preference pref_forwarding = screen.findPreference(Preferences.FORWARDING.getKey());
        pref_forwarding.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(ActivitySettings.this, ActivityForwarding.class));
                return true;
            }
        });

        boolean can = Util.canFilter(this);
        TwoStatePreference pref_log_app = (TwoStatePreference) screen.findPreference(Preferences.LOG_APP.getKey());
        TwoStatePreference pref_filter = (TwoStatePreference) screen.findPreference(Preferences.FILTER.getKey());
        pref_log_app.setEnabled(can);
        pref_filter.setEnabled(can);
        if (!can) {
            pref_log_app.setSummary(R.string.msg_unavailable);
            pref_filter.setSummary(R.string.msg_unavailable);
        }

        // VPN parameters
        screen.findPreference(Preferences.VPN4.getKey()).setTitle(getString(R.string.setting_vpn4, prefs.getString(Preferences.VPN4.getKey(), Preferences.VPN4.getDefaultValue())));
        screen.findPreference(Preferences.VPN6.getKey()).setTitle(getString(R.string.setting_vpn6, prefs.getString(Preferences.VPN6.getKey(), Preferences.VPN6.getDefaultValue())));
        EditTextPreference pref_dns1 = (EditTextPreference) screen.findPreference(Preferences.DNS1.getKey());
        EditTextPreference pref_dns2 = (EditTextPreference) screen.findPreference(Preferences.DNS2.getKey());
        EditTextPreference pref_validate = (EditTextPreference) screen.findPreference(Preferences.VALIDATE.getKey());
        EditTextPreference pref_ttl = (EditTextPreference) screen.findPreference(Preferences.TTL.getKey());
        pref_dns1.setTitle(getString(R.string.setting_dns, prefs.getString(Preferences.DNS1.getKey(), Preferences.DNS1.getDefaultValue())));
        pref_dns2.setTitle(getString(R.string.setting_dns, prefs.getString(Preferences.DNS2.getKey(), Preferences.DNS2.getDefaultValue())));
        pref_validate.setTitle(getString(R.string.setting_validate, prefs.getString(Preferences.VALIDATE.getKey(), Preferences.VALIDATE.getDefaultValue())));
        pref_ttl.setTitle(getString(R.string.setting_ttl, prefs.getInt(Preferences.TTL.getKey(), Preferences.TTL.getDefaultValue())));

        // SOCKS5 parameters
        screen.findPreference(Preferences.SOCKS_5_ADDR.getKey()).setTitle(getString(R.string.setting_socks5_addr, prefs.getString(Preferences.SOCKS_5_ADDR.getKey(), Preferences.SOCKS_5_ADDR.getDefaultValue())));
        screen.findPreference(Preferences.SOCKS_5_PORT.getKey()).setTitle(getString(R.string.setting_socks5_port, prefs.getString(Preferences.SOCKS_5_PORT.getKey(), Preferences.SOCKS_5_PORT.getDefaultValue())));
        screen.findPreference(Preferences.SOCKS_5_USERNAME.getKey()).setTitle(getString(R.string.setting_socks5_username, prefs.getString(Preferences.SOCKS_5_USERNAME.getKey(), Preferences.SOCKS_5_USERNAME.getDefaultValue())));
        screen.findPreference(Preferences.SOCKS_5_PASSWORD.getKey()).setTitle(getString(R.string.setting_socks5_password, TextUtils.isEmpty(prefs.getString(Preferences.SOCKS_5_PASSWORD.getKey(), Preferences.SOCKS_5_PASSWORD.getDefaultValue())) ? "-" : "*****"));

        // PCAP parameters
        screen.findPreference(Preferences.PCAP_RECORD_SIZE.getKey()).setTitle(getString(R.string.setting_pcap_record_size, prefs.getLong(Preferences.PCAP_RECORD_SIZE.getKey(), Preferences.PCAP_RECORD_SIZE.getDefaultValue())));
        screen.findPreference(Preferences.PCAP_FILE_SIZE.getKey()).setTitle(getString(R.string.setting_pcap_file_size, prefs.getLong(Preferences.PCAP_FILE_SIZE.getKey(), Preferences.PCAP_FILE_SIZE.getDefaultValue())));

        // Watchdog
        screen.findPreference(Preferences.WATCHDOG.getKey()).setTitle(getString(R.string.setting_watchdog, prefs.getLong(Preferences.WATCHDOG.getKey(), Preferences.WATCHDOG.getDefaultValue())));

        // Show resolved
        Preference pref_show_resolved = screen.findPreference(Preferences.SHOW_RESOLVED.getKey());
        if (Util.isPlayStoreInstall(this))
            cat_advanced.removePreference(pref_show_resolved);
        else
            pref_show_resolved.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(ActivitySettings.this, ActivityDns.class));
                    return true;
                }
            });

        // Handle stats
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            cat_stats.removePreference(screen.findPreference(Preferences.SHOW_TOP.getKey()));
        EditTextPreference pref_stats_frequency = (EditTextPreference) screen.findPreference(Preferences.STATS_FREQUENCY.getKey());
        EditTextPreference pref_stats_samples = (EditTextPreference) screen.findPreference(Preferences.STATS_SAMPLES.getKey());
        pref_stats_frequency.setTitle(getString(R.string.setting_stats_frequency, prefs.getLong(Preferences.STATS_FREQUENCY.getKey(), Preferences.STATS_FREQUENCY.getDefaultValue())));
        pref_stats_samples.setTitle(getString(R.string.setting_stats_samples, prefs.getLong(Preferences.STATS_SAMPLES.getKey(), Preferences.STATS_SAMPLES.getDefaultValue())));

        // Handle export
        Preference pref_export = screen.findPreference(Preferences.EXPORT.getKey());
        pref_export.setEnabled(getIntentCreateExport().resolveActivity(getPackageManager()) != null);
        pref_export.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivityForResult(getIntentCreateExport(), ActivitySettings.REQUEST_EXPORT);
                return true;
            }
        });

        // Handle import
        Preference pref_import = screen.findPreference(Preferences.IMPORT.getKey());
        pref_import.setEnabled(getIntentOpenExport().resolveActivity(getPackageManager()) != null);
        pref_import.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivityForResult(getIntentOpenExport(), ActivitySettings.REQUEST_IMPORT);
                return true;
            }
        });

        // Hosts file settings
        Preference pref_block_domains = screen.findPreference(Preferences.USE_HOSTS.getKey());
        EditTextPreference pref_rcode = (EditTextPreference) screen.findPreference(Preferences.R_CODE.getKey());
        Preference pref_hosts_import = screen.findPreference(Preferences.HOSTS_IMPORT.getKey());
        Preference pref_hosts_import_append = screen.findPreference(Preferences.HOSTS_IMPORT_APPEND.getKey());
        EditTextPreference pref_hosts_url = (EditTextPreference) screen.findPreference(Preferences.HOSTS_URL.getKey());
        final Preference pref_hosts_download = screen.findPreference(Preferences.HOSTS_DOWNLOAD.getKey());

        pref_rcode.setTitle(getString(R.string.setting_rcode, prefs.getInt(Preferences.R_CODE.getKey(), Preferences.R_CODE.getDefaultValue())));

        if (Util.isPlayStoreInstall(this) || !Util.hasValidFingerprint(this))
            cat_options.removePreference(screen.findPreference(Preferences.UPDATE_CHECK.getKey()));

        if (Util.isPlayStoreInstall(this)) {
            Log.i(TAG, "Play store install");
            cat_advanced.removePreference(pref_block_domains);
            cat_advanced.removePreference(pref_rcode);
            cat_advanced.removePreference(pref_forwarding);
            cat_backup.removePreference(pref_hosts_import);
            cat_backup.removePreference(pref_hosts_import_append);
            cat_backup.removePreference(pref_hosts_url);
            cat_backup.removePreference(pref_hosts_download);

        } else {
            String last_import = prefs.getString(Preferences.HOSTS_LAST_IMPORT.getKey(), Preferences.HOSTS_LAST_DOWNLOAD.getDefaultValue());
            String last_download = prefs.getString(Preferences.HOSTS_LAST_DOWNLOAD.getKey(), Preferences.HOSTS_LAST_DOWNLOAD.getDefaultValue());
            if (last_import != null)
                pref_hosts_import.setSummary(getString(R.string.msg_import_last, last_import));
            if (last_download != null)
                pref_hosts_download.setSummary(getString(R.string.msg_download_last, last_download));

            // Handle hosts import
            // https://github.com/Free-Software-for-Android/AdAway/wiki/HostsSources
            pref_hosts_import.setEnabled(getIntentOpenHosts().resolveActivity(getPackageManager()) != null);
            pref_hosts_import.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivityForResult(getIntentOpenHosts(), ActivitySettings.REQUEST_HOSTS);
                    return true;
                }
            });
            pref_hosts_import_append.setEnabled(pref_hosts_import.isEnabled());
            pref_hosts_import_append.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivityForResult(getIntentOpenHosts(), ActivitySettings.REQUEST_HOSTS_APPEND);
                    return true;
                }
            });

            // Handle hosts file download
            pref_hosts_url.setSummary(pref_hosts_url.getText());
            pref_hosts_download.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    final File tmp = new File(getFilesDir(), Files.FILE_HOSTS_TMP);
                    final File hosts = new File(getFilesDir(), Files.FILE_HOSTS);

                    EditTextPreference pref_hosts_url = (EditTextPreference) screen.findPreference(Preferences.HOSTS_URL.getKey());
                    String hosts_url = pref_hosts_url.getText();
                    if (Files.URL_HOSTS.equals(hosts_url))
                        hosts_url = BuildConfig.HOSTS_FILE_URI;

                    try {
                        new DownloadTask(ActivitySettings.this, new URL(hosts_url), tmp, new DownloadTask.Listener() {
                            @Override
                            public void onCompleted() {
                                if (hosts.exists())
                                    hosts.delete();
                                tmp.renameTo(hosts);

                                String last = SimpleDateFormat.getDateTimeInstance().format(new Date());
                                prefs.edit().putString(Preferences.HOSTS_LAST_DOWNLOAD.getKey(), last).apply();

                                if (running) {
                                    pref_hosts_download.setSummary(getString(R.string.msg_download_last, last));
                                    Toast.makeText(ActivitySettings.this, R.string.msg_downloaded, Toast.LENGTH_LONG).show();
                                }

                                ServiceSinkhole.reload(SimpleReason.HostsFileDownload, ActivitySettings.this, false);
                            }

                            @Override
                            public void onCancelled() {
                                if (tmp.exists())
                                    tmp.delete();
                            }

                            @Override
                            public void onException(Throwable ex) {
                                if (tmp.exists())
                                    tmp.delete();

                                if (running)
                                    Toast.makeText(ActivitySettings.this, ex.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    } catch (MalformedURLException ex) {
                        Toast.makeText(ActivitySettings.this, ex.toString(), Toast.LENGTH_LONG).show();
                    }
                    return true;
                }
            });
        }

        // Development
        if (!Util.isDebuggable(this))
            screen.removePreference(screen.findPreference(Preferences.SCREEN_DEVELOPMENT.getKey()));

        // Handle technical info
        Preference.OnPreferenceClickListener listener = new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                updateTechnicalInfo();
                return true;
            }
        };

        // Technical info
        Preference pref_technical_info = screen.findPreference(Preferences.TECHNICAL_INFO.getKey());
        Preference pref_technical_network = screen.findPreference(Preferences.TECHNICAL_NETWORK.getKey());
        pref_technical_info.setEnabled(INTENT_VPN_SETTINGS.resolveActivity(this.getPackageManager()) != null);
        pref_technical_info.setIntent(INTENT_VPN_SETTINGS);
        pref_technical_info.setOnPreferenceClickListener(listener);
        pref_technical_network.setOnPreferenceClickListener(listener);
        updateTechnicalInfo();

        markPro(screen.findPreference(Preferences.THEME.getKey()), ActivityPro.SKU_THEME);
        markPro(screen.findPreference(Preferences.INSTALL.getKey()), ActivityPro.SKU_NOTIFY);
        markPro(screen.findPreference(Preferences.SHOW_STATS.getKey()), ActivityPro.SKU_SPEED);
    }

    @Override
    protected void onResume() {
        super.onResume();

        checkPermissions(null);

        // Listen for preference changes
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Listen for interactive state changes
        IntentFilter ifInteractive = new IntentFilter();
        ifInteractive.addAction(Intent.ACTION_SCREEN_ON);
        ifInteractive.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(interactiveStateReceiver, ifInteractive);

        // Listen for connectivity updates
        IntentFilter ifConnectivity = new IntentFilter();
        ifConnectivity.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityChangedReceiver, ifConnectivity);
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);

        unregisterReceiver(interactiveStateReceiver);
        unregisterReceiver(connectivityChangedReceiver);
    }

    @Override
    protected void onDestroy() {
        running = false;
        if (dialogFilter != null) {
            dialogFilter.dismiss();
            dialogFilter = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Log.i(TAG, "Up");
                NavUtils.navigateUpFromSameTask(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onSharedPreferenceChanged(SharedPreferences prefs, String name) {
        // Pro features
        if (Preferences.THEME.getKey().equals(name)) {
            if (!Preferences.THEME.getDefaultValue().getValue().equals(prefs.getString(name, Preferences.THEME.getDefaultValue().getValue())) && !IAB.isPurchased(ActivityPro.SKU_THEME, this)) {
                prefs.edit().putString(name, Preferences.THEME.getDefaultValue().getValue()).apply();
                ((ListPreference) getPreferenceScreen().findPreference(name)).setValue(Preferences.THEME.getDefaultValue().getValue());
                startActivity(new Intent(this, ActivityPro.class));
                return;
            }
        } else if (Preferences.INSTALL.getKey().equals(name)) {
            if (prefs.getBoolean(name, Preferences.INSTALL.getDefaultValue()) && !IAB.isPurchased(ActivityPro.SKU_NOTIFY, this)) {
                prefs.edit().putBoolean(name, Preferences.INSTALL.getDefaultValue()).apply();
                ((TwoStatePreference) getPreferenceScreen().findPreference(name)).setChecked(Preferences.INSTALL.getDefaultValue());
                startActivity(new Intent(this, ActivityPro.class));
                return;
            }
        } else if (Preferences.SHOW_STATS.getKey().equals(name)) {
            if (prefs.getBoolean(name, Preferences.SHOW_STATS.getDefaultValue()) && !IAB.isPurchased(ActivityPro.SKU_SPEED, this)) {
                prefs.edit().putBoolean(name, Preferences.SHOW_STATS.getDefaultValue()).apply();
                startActivity(new Intent(this, ActivityPro.class));
                return;
            }
            ((TwoStatePreference) getPreferenceScreen().findPreference(name)).setChecked(prefs.getBoolean(name, false));
        }

        Object value = prefs.getAll().get(name);
        if (value instanceof String && "".equals(value))
            prefs.edit().remove(name).apply();

        // Dependencies
        if (Preferences.SCREEN_ON.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.WHITELIST_WIFI.getKey().equals(name) ||
                Preferences.SCREEN_WIFI.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.WHITELIST_OTHER.getKey().equals(name) ||
                Preferences.SCREEN_OTHER.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.WHITELIST_ROAMING.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.AUTO_ENABLE.getKey().equals(name))
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_auto, prefs.getInt(name, Preferences.AUTO_ENABLE.getDefaultValue())));

        else if (Preferences.SCREEN_DELAY.getKey().equals(name))
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_delay, prefs.getInt(name, Preferences.SCREEN_DELAY.getDefaultValue())));

        else if (Preferences.THEME.getKey().equals(name) || Preferences.DARK.getKey().equals(name))
            recreate();

        else if (Preferences.SUBNET.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.TETHERING.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.LAN.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.IP6.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.WIFI_HOMES.getKey().equals(name)) {
            MultiSelectListPreference pref_wifi_homes = (MultiSelectListPreference) getPreferenceScreen().findPreference(name);
            Set<String> ssid = prefs.getStringSet(name, new HashSet<String>());
            if (ssid.size() > 0)
                pref_wifi_homes.setTitle(getString(R.string.setting_wifi_home, TextUtils.join(", ", ssid)));
            else
                pref_wifi_homes.setTitle(getString(R.string.setting_wifi_home, "-"));
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.USE_METERED.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.UNMETERED_2G.getKey().equals(name) ||
                Preferences.UNMETERED_3G.getKey().equals(name) ||
                Preferences.UNMETERED_4G.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.NATIONAL.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.EU.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.DISABLE_ON_CALL.getKey().equals(name)) {
            if (prefs.getBoolean(name, false)) {
                if (checkPermissions(name))
                    ServiceSinkhole.reload(new Changed(name), this, false);
            } else
                ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.LOCKDOWN_WIFI.getKey().equals(name) || Preferences.LOCKDOWN_OTHER.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.MANAGE_SYSTEM.getKey().equals(name)) {
            boolean manage = prefs.getBoolean(name, false);
            if (!manage) prefs.edit().putBoolean(Preferences.SHOW_USER.getKey(), Preferences.SHOW_USER.getDefaultValue()).apply();
            prefs.edit().putBoolean(Preferences.SHOW_SYSTEM.getKey(), manage).apply();
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.LOG_APP.getKey().equals(name)) {
            Intent ruleset = new Intent(ActivityMain.ACTION_RULES_CHANGED);
            LocalBroadcastManager.getInstance(this).sendBroadcast(ruleset);
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.NOTIFY_ACCESS.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.FILTER.getKey().equals(name)) {
            // Show dialog
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && prefs.getBoolean(name, false)) {
                LayoutInflater inflater = LayoutInflater.from(ActivitySettings.this);
                View view = inflater.inflate(R.layout.filter, null, false);
                dialogFilter = new AlertDialog.Builder(ActivitySettings.this)
                        .setView(view)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Do nothing
                            }
                        })
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialogInterface) {
                                dialogFilter = null;
                            }
                        })
                        .create();
                dialogFilter.show();
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && !prefs.getBoolean(name, false)) {
                prefs.edit().putBoolean(name, true).apply();
                Toast.makeText(ActivitySettings.this, R.string.msg_filter4, Toast.LENGTH_SHORT).show();
            }

            ((TwoStatePreference) getPreferenceScreen().findPreference(name)).setChecked(prefs.getBoolean(name, false));

            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.USE_HOSTS.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.VPN4.getKey().equals(name)) {
            String vpn4 = prefs.getString(name, null);
            try {
                checkAddress(vpn4, false);
                prefs.edit().putString(name, vpn4.trim()).apply();
            } catch (Throwable ex) {
                prefs.edit().remove(name).apply();
                ((EditTextPreference) getPreferenceScreen().findPreference(name)).setText(null);
                if (!TextUtils.isEmpty(vpn4))
                    Toast.makeText(ActivitySettings.this, ex.toString(), Toast.LENGTH_LONG).show();
            }
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_vpn4, prefs.getString(name, Preferences.VPN4.getDefaultValue())));
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.VPN6.getKey().equals(name)) {
            String vpn6 = prefs.getString(name, null);
            try {
                checkAddress(vpn6, false);
                prefs.edit().putString(name, vpn6.trim()).apply();
            } catch (Throwable ex) {
                prefs.edit().remove(name).apply();
                ((EditTextPreference) getPreferenceScreen().findPreference(name)).setText(null);
                if (!TextUtils.isEmpty(vpn6))
                    Toast.makeText(ActivitySettings.this, ex.toString(), Toast.LENGTH_LONG).show();
            }
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_vpn6, prefs.getString(name, Preferences.VPN6.getDefaultValue())));
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.DNS1.getKey().equals(name) || Preferences.DNS2.getKey().equals(name)) {
            String dns = prefs.getString(name, null);
            try {
                checkAddress(dns, true);
                prefs.edit().putString(name, dns.trim()).apply();
            } catch (Throwable ex) {
                prefs.edit().remove(name).apply();
                ((EditTextPreference) getPreferenceScreen().findPreference(name)).setText(null);
                if (!TextUtils.isEmpty(dns))
                    Toast.makeText(ActivitySettings.this, ex.toString(), Toast.LENGTH_LONG).show();
            }
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_dns, prefs.getString(name, Preferences.DNS1.getDefaultValue())));
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.VALIDATE.getDefaultValue().equals(name)) {
            String host = prefs.getString(name, Preferences.VALIDATE.getDefaultValue());
            try {
                checkDomain(host);
                prefs.edit().putString(name, host.trim()).apply();
            } catch (Throwable ex) {
                prefs.edit().remove(name).apply();
                ((EditTextPreference) getPreferenceScreen().findPreference(name)).setText(null);
                if (!TextUtils.isEmpty(host))
                    Toast.makeText(ActivitySettings.this, ex.toString(), Toast.LENGTH_LONG).show();
            }
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_validate, prefs.getString(name, Preferences.VALIDATE.getDefaultValue())));
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.TTL.getKey().equals(name))
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_ttl, prefs.getInt(name, Preferences.TTL.getDefaultValue())));

        else if (Preferences.R_CODE.getKey().equals(name)) {
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_rcode, prefs.getInt(name, Preferences.R_CODE.getDefaultValue())));
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.SOCKS_5_ENABLED.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);

        else if (Preferences.SOCKS_5_ADDR.getKey().equals(name)) {
            String socks5_addr = prefs.getString(name, null);
            try {
                if (!TextUtils.isEmpty(socks5_addr) && !Util.isNumericAddress(socks5_addr))
                    throw new IllegalArgumentException(ERROR_BAD_ADDRESS);
            } catch (Throwable ex) {
                prefs.edit().remove(name).apply();
                ((EditTextPreference) getPreferenceScreen().findPreference(name)).setText(null);
                if (!TextUtils.isEmpty(socks5_addr))
                    Toast.makeText(ActivitySettings.this, ex.toString(), Toast.LENGTH_LONG).show();
            }
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_socks5_addr, prefs.getString(name, Preferences.SOCKS_5_ADDR.getDefaultValue())));
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.SOCKS_5_PORT.getKey().equals(name)) {
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_socks5_port, prefs.getString(name, Preferences.SOCKS_5_PORT.getDefaultValue())));
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.SOCKS_5_USERNAME.getKey().equals(name)) {
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_socks5_username, prefs.getString(name, Preferences.SOCKS_5_USERNAME.getDefaultValue())));
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.SOCKS_5_PASSWORD.getKey().equals(name)) {
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_socks5_password, TextUtils.isEmpty(Preferences.SOCKS_5_PASSWORD.getDefaultValue()) ? "-" : "*****"));
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.PCAP_RECORD_SIZE.getKey().equals(name) || Preferences.PCAP_FILE_SIZE.getKey().equals(name)) {
            if (Preferences.PCAP_RECORD_SIZE.getKey().equals(name))
                getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_pcap_record_size, prefs.getLong(name, Preferences.PCAP_RECORD_SIZE.getDefaultValue())));
            else if (Preferences.PCAP_FILE_SIZE.getKey().equals(name))
                getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_pcap_file_size, prefs.getLong(name, Preferences.PCAP_FILE_SIZE.getDefaultValue())));

            ServiceSinkhole.setPcap(false, this);

            File pcap_file = Files.getPcapFile(this);
            if (pcap_file.exists() && !pcap_file.delete())
                Log.w(TAG, "Delete PCAP failed");

            if (prefs.getBoolean(Preferences.PCAP.getKey(), Preferences.PCAP.getDefaultValue()))
                ServiceSinkhole.setPcap(true, this);

        } else if (Preferences.WATCHDOG.getKey().equals(name)) {
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_watchdog, prefs.getLong(name, Preferences.WATCHDOG.getDefaultValue())));
            ServiceSinkhole.reload(new Changed(name), this, false);

        } else if (Preferences.SHOW_STATS.getKey().equals(name))
            ServiceSinkhole.reloadStats(new Changed(name), this);

        else if (Preferences.STATS_FREQUENCY.getKey().equals(name))
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_stats_frequency, prefs.getLong(name, Preferences.STATS_FREQUENCY.getDefaultValue())));

        else if (Preferences.STATS_SAMPLES.getKey().equals(name))
            getPreferenceScreen().findPreference(name).setTitle(getString(R.string.setting_stats_samples, prefs.getLong(name, Preferences.STATS_SAMPLES.getDefaultValue())));

        else if (Preferences.HOSTS_URL.getKey().equals(name))
            getPreferenceScreen().findPreference(name).setSummary(prefs.getString(name, BuildConfig.HOSTS_FILE_URI));

        else if (Preferences.LOG_LEVEL.getKey().equals(name))
            ServiceSinkhole.reload(new Changed(name), this, false);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean checkPermissions(String name) {
        PreferenceScreen screen = getPreferenceScreen();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Check if permission was revoked
        if ((name == null || Preferences.DISABLE_ON_CALL.getKey().equals(name)) && prefs.getBoolean(Preferences.DISABLE_ON_CALL.getKey(), Preferences.DISABLE_ON_CALL.getDefaultValue()))
            if (!Util.hasPhoneStatePermission(this)) {
                prefs.edit().putBoolean(Preferences.DISABLE_ON_CALL.getKey(), Preferences.DISABLE_ON_CALL.getDefaultValue()).apply();
                ((TwoStatePreference) screen.findPreference(Preferences.DISABLE_ON_CALL.getKey())).setChecked(false);

                requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_CALL);

                return name==null;
            }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        PreferenceScreen screen = getPreferenceScreen();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean granted = (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);

        if (requestCode == REQUEST_CALL) {
            prefs.edit().putBoolean(Preferences.DISABLE_ON_CALL.getKey(), granted).apply();
            ((TwoStatePreference) screen.findPreference(Preferences.DISABLE_ON_CALL.getKey())).setChecked(granted);
        }

        if (granted)
            ServiceSinkhole.reload(SimpleReason.PermissionGranted, this, false);
    }

    private void checkAddress(String address, boolean allow_local) throws IllegalArgumentException, UnknownHostException {
        if (address != null)
            address = address.trim();
        if (TextUtils.isEmpty(address))
            throw new IllegalArgumentException(ERROR_BAD_ADDRESS);
        if (!Util.isNumericAddress(address))
            throw new IllegalArgumentException(ERROR_BAD_ADDRESS);
        if (!allow_local) {
            InetAddress iaddr = InetAddress.getByName(address);
            if (iaddr.isLoopbackAddress() || iaddr.isAnyLocalAddress())
                throw new IllegalArgumentException(ERROR_BAD_ADDRESS);
        }
    }

    private void checkDomain(String address) throws IllegalArgumentException, UnknownHostException {
        if (address != null)
            address = address.trim();
        if (TextUtils.isEmpty(address))
            throw new IllegalArgumentException(ERROR_BAD_ADDRESS);
        if (Util.isNumericAddress(address))
            throw new IllegalArgumentException(ERROR_BAD_ADDRESS);
        if (!PatternsCompat.DOMAIN_NAME.matcher(address).matches())
            throw new IllegalArgumentException(ERROR_BAD_ADDRESS);
    }

    private BroadcastReceiver interactiveStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Util.logExtras(intent);
            updateTechnicalInfo();
        }
    };

    private BroadcastReceiver connectivityChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Util.logExtras(intent);
            updateTechnicalInfo();
        }
    };

    private void markPro(Preference pref, String sku) {
        if (sku == null || !IAB.isPurchased(sku, this)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean dark = prefs.getBoolean(Preferences.DARK.getKey(), Preferences.DARK.getDefaultValue());
            SpannableStringBuilder ssb = new SpannableStringBuilder("  " + pref.getTitle());
            ssb.setSpan(new ImageSpan(this, dark ? R.drawable.ic_shopping_cart_white_24dp : R.drawable.ic_shopping_cart_black_24dp), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            pref.setTitle(ssb);
        }
    }

    private void updateTechnicalInfo() {
        PreferenceScreen screen = getPreferenceScreen();
        Preference pref_technical_info = screen.findPreference(Preferences.TECHNICAL_INFO.getKey());
        Preference pref_technical_network = screen.findPreference(Preferences.TECHNICAL_NETWORK.getKey());

        pref_technical_info.setSummary(Util.getGeneralInfo(this));
        pref_technical_network.setSummary(Util.getNetworkInfo(this));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        Log.i(TAG, "onActivityResult request=" + requestCode + " result=" + requestCode + " ok=" + (resultCode == RESULT_OK));
        if (requestCode == REQUEST_EXPORT) {
            if (resultCode == RESULT_OK && data != null)
                handleExport(data);

        } else if (requestCode == REQUEST_IMPORT) {
            if (resultCode == RESULT_OK && data != null)
                handleImport(data);

        } else if (requestCode == REQUEST_HOSTS) {
            if (resultCode == RESULT_OK && data != null)
                handleHosts(data, false);

        } else if (requestCode == REQUEST_HOSTS_APPEND) {
            if (resultCode == RESULT_OK && data != null)
                handleHosts(data, true);

        } else {
            Log.w(TAG, "Unknown activity result request=" + requestCode);
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private Intent getIntentCreateExport() {
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
            intent.setType(Files.FILE_TEXT_XML); // text/xml
            intent.putExtra(Intent.EXTRA_TITLE, Files.getFileName(this, Files.Format.Xml));
        }
        return intent;
    }

    private Intent getIntentOpenExport() {
        Intent intent;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            intent = new Intent(Intent.ACTION_GET_CONTENT);
        else
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(Files.FILE_TEXT_XML); // text/xml
        return intent;
    }

    private Intent getIntentOpenHosts() {
        Intent intent;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            intent = new Intent(Intent.ACTION_GET_CONTENT);
        else
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(Files.FILE_TEXT_XML); // text/plain
        return intent;
    }

    private void handleExport(final Intent data) {
        new AsyncTask<Object, Object, Throwable>() {
            @Override
            protected Throwable doInBackground(Object... objects) {
                OutputStream out = null;
                try {
                    Uri target = data.getData();
                    if (data.hasExtra("org.openintents.extra.DIR_PATH"))
                        target = Uri.parse(target + "/"+ Files.getFileName(ActivitySettings.this, Files.Format.Xml));
                    Log.i(TAG, "Writing URI=" + target);
                    out = getContentResolver().openOutputStream(target);
                    xmlExport(out);
                    return null;
                } catch (Throwable ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    return ex;
                } finally {
                    if (out != null)
                        try {
                            out.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                }
            }

            @Override
            protected void onPostExecute(Throwable ex) {
                if (running) {
                    if (ex == null)
                        Toast.makeText(ActivitySettings.this, R.string.msg_completed, Toast.LENGTH_LONG).show();
                    else
                        Toast.makeText(ActivitySettings.this, ex.toString(), Toast.LENGTH_LONG).show();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void handleHosts(final Intent data, final boolean append) {
        new AsyncTask<Object, Object, Throwable>() {
            @Override
            protected Throwable doInBackground(Object... objects) {
                File hosts = new File(getFilesDir(), Files.FILE_HOSTS);

                FileOutputStream out = null;
                InputStream in = null;
                try {
                    Log.i(TAG, "Reading URI=" + data.getData());
                    ContentResolver resolver = getContentResolver();
                    String[] streamTypes = resolver.getStreamTypes(data.getData(), Files.FILE_TEXT_XML);
                    String streamType = (streamTypes == null || streamTypes.length == 0 ? Files.FILE_TEXT_XML : streamTypes[0]);
                    AssetFileDescriptor descriptor = resolver.openTypedAssetFileDescriptor(data.getData(), streamType, null);
                    in = descriptor.createInputStream();
                    out = new FileOutputStream(hosts, append);

                    int len;
                    long total = 0;
                    byte[] buf = new byte[4096];
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                        total += len;
                    }
                    Log.i(TAG, "Copied bytes=" + total);

                    return null;
                } catch (Throwable ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    return ex;
                } finally {
                    if (out != null)
                        try {
                            out.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                    if (in != null)
                        try {
                            in.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                }
            }

            @Override
            protected void onPostExecute(Throwable ex) {
                if (running) {
                    if (ex == null) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ActivitySettings.this);
                        String last = SimpleDateFormat.getDateTimeInstance().format(new Date());
                        prefs.edit().putString(Preferences.HOSTS_LAST_IMPORT.getKey(), last).apply();

                        if (running) {
                            getPreferenceScreen().findPreference(Preferences.HOSTS_IMPORT.getKey()).setSummary(getString(R.string.msg_import_last, last));
                            Toast.makeText(ActivitySettings.this, R.string.msg_completed, Toast.LENGTH_LONG).show();
                        }

                        ServiceSinkhole.reload(SimpleReason.HostsImport, ActivitySettings.this, false);
                    } else
                        Toast.makeText(ActivitySettings.this, ex.toString(), Toast.LENGTH_LONG).show();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void handleImport(final Intent data) {
        new AsyncTask<Object, Object, Throwable>() {
            @Override
            protected Throwable doInBackground(Object... objects) {
                InputStream in = null;
                try {
                    Log.i(TAG, "Reading URI=" + data.getData());
                    ContentResolver resolver = getContentResolver();
                    String[] streamTypes = resolver.getStreamTypes(data.getData(), Files.FILE_TEXT_XML);
                    String streamType = (streamTypes == null || streamTypes.length == 0 ? Files.FILE_TEXT_XML : streamTypes[0]);
                    AssetFileDescriptor descriptor = resolver.openTypedAssetFileDescriptor(data.getData(), streamType, null);
                    in = descriptor.createInputStream();
                    xmlImport(in);
                    return null;
                } catch (Throwable ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    return ex;
                } finally {
                    if (in != null)
                        try {
                            in.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                }
            }

            @Override
            protected void onPostExecute(Throwable ex) {
                if (running) {
                    if (ex == null) {
                        Toast.makeText(ActivitySettings.this, R.string.msg_completed, Toast.LENGTH_LONG).show();
                        ServiceSinkhole.reloadStats(SimpleReason.Import, ActivitySettings.this);
                        // Update theme, request permissions
                        recreate();
                    } else
                        Toast.makeText(ActivitySettings.this, ex.toString(), Toast.LENGTH_LONG).show();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void xmlExport(OutputStream out) throws IOException {
        XmlSerializer serializer = Xml.newSerializer();
        serializer.setOutput(out, Serializer.OUTPUT);
        serializer.startDocument(null, true);
        serializer.setFeature(Serializer.FEATURE, true);
        String appName = getString(R.string.app_name);
        serializer.startTag(null, appName);

        serializer.startTag(null, TAG_APPLICATION);
        xmlExport(PreferenceManager.getDefaultSharedPreferences(this), serializer);
        serializer.endTag(null, TAG_APPLICATION);

        String keyWifi = Preferences.WIFI.getKey();
        serializer.startTag(null, keyWifi);
        xmlExport(getSharedPreferences(keyWifi, Context.MODE_PRIVATE), serializer);
        serializer.endTag(null, keyWifi);

        String keyMobile = Preferences.MOBILE.getKey();
        String keyOther = Preferences.OTHER.getKey();
        serializer.startTag(null, keyMobile);
        xmlExport(getSharedPreferences(keyOther, Context.MODE_PRIVATE), serializer);
        serializer.endTag(null, keyMobile);

        String keyScreenWifi = Preferences.SCREEN_WIFI.getKey();
        serializer.startTag(null, keyScreenWifi);
        xmlExport(getSharedPreferences(keyScreenWifi, Context.MODE_PRIVATE), serializer);
        serializer.endTag(null, keyScreenWifi);

        String keyScreenOther = Preferences.SCREEN_OTHER.getKey();
        serializer.startTag(null, keyScreenOther);
        xmlExport(getSharedPreferences(keyScreenOther, Context.MODE_PRIVATE), serializer);
        serializer.endTag(null, keyScreenOther);

        String keyRoaming = Preferences.ROAMING.getKey();
        serializer.startTag(null, keyRoaming);
        xmlExport(getSharedPreferences(keyRoaming, Context.MODE_PRIVATE), serializer);
        serializer.endTag(null, keyRoaming);

        String keyLockDown = Preferences.LOCKDOWN.getKey();
        serializer.startTag(null, keyLockDown);
        xmlExport(getSharedPreferences(keyLockDown, Context.MODE_PRIVATE), serializer);
        serializer.endTag(null, keyLockDown);

        String keyApply = Preferences.APPLY.getKey();
        serializer.startTag(null, keyApply);
        xmlExport(getSharedPreferences(keyApply, Context.MODE_PRIVATE), serializer);
        serializer.endTag(null, keyApply);

        String keyNotify = Preferences.NOTIFY.getKey();
        serializer.startTag(null, keyNotify);
        xmlExport(getSharedPreferences(keyNotify, Context.MODE_PRIVATE), serializer);
        serializer.endTag(null, keyNotify);

        String keyFilter = Preferences.FILTER.getKey();
        serializer.startTag(null, keyFilter);
        filterExport(serializer);
        serializer.endTag(null, keyFilter);

        String keyForward = Preferences.FORWARD.getKey();
        serializer.startTag(null, keyForward);
        forwardExport(serializer);
        serializer.endTag(null, keyForward);

        serializer.endTag(null, appName);
        serializer.endDocument();
        serializer.flush();
    }

    private void xmlExport(SharedPreferences prefs, XmlSerializer serializer) throws IOException {
        Map<String, ?> settings = prefs.getAll();
        for (String key : settings.keySet()) {
            Object value = settings.get(key);

            if (Preferences.IMPORTED.getKey().equals(key))
                continue;

            SerializerType type;
            String serializedValue;
            if (value instanceof Boolean) {
                type = SerializerType.Boolean;
                serializedValue = value.toString();
            } else if (value instanceof Integer) {
                type = SerializerType.Integer;
                serializedValue = value.toString();
            } else if (value instanceof String) {
                type = SerializerType.String;
                serializedValue = value.toString();
            } else if (value instanceof Set) {
                type = SerializerType.Set;
                serializedValue = TextUtils.join("\n", (Set<String>) value);
            } else {
                Log.e(TAG, "Unknown key=" + key);
                continue;
            }
            serializer.startTag(null, Serializer.Tag.SETTING);
            serializer.attribute(null, Serializer.Attribute.KEY, key);
            serializer.attribute(null, Serializer.Attribute.TYPE, type.getValue());
            serializer.attribute(null, Serializer.Attribute.VALUE, serializedValue);
            serializer.endTag(null, Serializer.Tag.SETTING);
        }
    }

    private void filterExport(XmlSerializer serializer) throws IOException {
        try (Cursor cursor = DatabaseHelper.getInstance(this).getAccess()) {
            int colUid = cursor.getColumnIndex(Column.UID.getValue());
            int colVersion = cursor.getColumnIndex(Column.VERSION.getValue());
            int colProtocol = cursor.getColumnIndex(Column.PROTOCOL.getValue());
            int colDAddr = cursor.getColumnIndex(Column.DADDR.getValue());
            int colDPort = cursor.getColumnIndex(Column.DPORT.getValue());
            int colTime = cursor.getColumnIndex(Column.TIME.getValue());
            int colBlock = cursor.getColumnIndex(Column.BLOCK.getValue());
            while (cursor.moveToNext())
                for (String pkg : getPackages(cursor.getInt(colUid))) {
                    String tag = TAG_RULE;
                    serializer.startTag(null, tag);
                    serializer.attribute(null, TAG_PKG, pkg);
                    serializer.attribute(null, Column.VERSION.getValue(), Integer.toString(cursor.getInt(colVersion)));
                    serializer.attribute(null, Column.PROTOCOL.getValue(), Integer.toString(cursor.getInt(colProtocol)));
                    serializer.attribute(null, Column.DADDR.getValue(), cursor.getString(colDAddr));
                    serializer.attribute(null, Column.DPORT.getValue(), Integer.toString(cursor.getInt(colDPort)));
                    serializer.attribute(null, Column.TIME.getValue(), Long.toString(cursor.getLong(colTime)));
                    serializer.attribute(null, Column.BLOCK.getValue(), Integer.toString(cursor.getInt(colBlock)));
                    serializer.endTag(null, tag);
                }
        }
    }

    private void forwardExport(XmlSerializer serializer) throws IOException {
        try (Cursor cursor = DatabaseHelper.getInstance(this).getForwarding()) {
            int colProtocol = cursor.getColumnIndex(Column.PROTOCOL.getValue());
            int colDPort = cursor.getColumnIndex(Column.DPORT.getValue());
            int colRAddr = cursor.getColumnIndex(Column.RADDR.getValue());
            int colRPort = cursor.getColumnIndex(Column.RPORT.getValue());
            int colRUid = cursor.getColumnIndex(Column.RUID.getValue());
            while (cursor.moveToNext())
                for (String pkg : getPackages(cursor.getInt(colRUid))) {
                    String tag = Column.PORT.getValue();
                    serializer.startTag(null, tag);
                    serializer.attribute(null, TAG_PKG, pkg);
                    serializer.attribute(null, Column.PROTOCOL.getValue(), Integer.toString(cursor.getInt(colProtocol)));
                    serializer.attribute(null, Column.DPORT.getValue(), Integer.toString(cursor.getInt(colDPort)));
                    serializer.attribute(null, Column.RADDR.getValue(), cursor.getString(colRAddr));
                    serializer.attribute(null, Column.RPORT.getValue(), Integer.toString(cursor.getInt(colRPort)));
                    serializer.endTag(null, tag);
                }
        }
    }

    private String[] getPackages(int uid) {
        if (uid == Uid.Root.getCode())
            return new String[]{Uid.Root.getId()};
        else if (uid == Uid.Media.getCode())
            return new String[]{Uid.Media.getId()};
        else if (uid == Uid.Nobody.getCode())
            return new String[]{Uid.Nobody.getId()};
        else {
            String pkgs[] = getPackageManager().getPackagesForUid(uid);
            if (pkgs == null)
                return new String[0];
            else
                return pkgs;
        }
    }

    private void xmlImport(InputStream in) throws IOException, SAXException, ParserConfigurationException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        prefs.edit().putBoolean(Preferences.ENABLED.getKey(), Preferences.ENABLED.getDefaultValue()).apply();
        ServiceSinkhole.stop(SimpleReason.Import, this, false);

        XMLReader reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        XmlImportHandler handler = new XmlImportHandler(this);
        reader.setContentHandler(handler);
        reader.parse(new InputSource(in));

        xmlImport(handler.application, prefs);
        xmlImport(handler.wifi, getSharedPreferences(Preferences.OTHER.getKey(), Context.MODE_PRIVATE));
        xmlImport(handler.mobile, getSharedPreferences(Preferences.WIFI.getKey(), Context.MODE_PRIVATE));
        xmlImport(handler.screen_wifi, getSharedPreferences(Preferences.SCREEN_WIFI.getKey(), Context.MODE_PRIVATE));
        xmlImport(handler.screen_other, getSharedPreferences(Preferences.SCREEN_OTHER.getKey(), Context.MODE_PRIVATE));
        xmlImport(handler.roaming, getSharedPreferences(Preferences.ROAMING.getKey(), Context.MODE_PRIVATE));
        xmlImport(handler.lockdown, getSharedPreferences(Preferences.LOCKDOWN.getKey(), Context.MODE_PRIVATE));
        xmlImport(handler.apply, getSharedPreferences(Preferences.APPLY.getKey(), Context.MODE_PRIVATE));
        xmlImport(handler.notify, getSharedPreferences(Preferences.NOTIFY.getKey(), Context.MODE_PRIVATE));

        // Upgrade imported settings
        ReceiverAutostart.upgrade(true, this);

        DatabaseHelper.clearCache();

        // Refresh UI
        prefs.edit().putBoolean(Preferences.IMPORTED.getKey(), Preferences.IMPORTED.getDefaultValue()).apply();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    private void xmlImport(Map<String, Object> settings, SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();

        // Clear existing setting
        for (String key : prefs.getAll().keySet())
            if (!Preferences.ENABLED.getKey().equals(key))
                editor.remove(key);

        // Apply new settings
        for (String key : settings.keySet()) {
            Object value = settings.get(key);
            if (value instanceof Boolean)
                editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer)
                editor.putInt(key, (Integer) value);
            else if (value instanceof String)
                editor.putString(key, (String) value);
            else if (value instanceof Set)
                editor.putStringSet(key, (Set<String>) value);
            else
                Log.e(TAG, "Unknown type=" + value.getClass());
        }

        editor.apply();
    }

    private class XmlImportHandler extends DefaultHandler {
        private Context context;
        public boolean enabled = false;
        public Map<String, Object> application = new HashMap<>();
        public Map<String, Object> wifi = new HashMap<>();
        public Map<String, Object> mobile = new HashMap<>();
        public Map<String, Object> screen_wifi = new HashMap<>();
        public Map<String, Object> screen_other = new HashMap<>();
        public Map<String, Object> roaming = new HashMap<>();
        public Map<String, Object> lockdown = new HashMap<>();
        public Map<String, Object> apply = new HashMap<>();
        public Map<String, Object> notify = new HashMap<>();
        private Map<String, Object> current = null;

        public XmlImportHandler(Context context) {
            this.context = context;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (qName.equals(getString(R.string.app_name)))
                ; // Ignore

            else if (qName.equals(TAG_APPLICATION))
                current = application;

            else if (qName.equals(Preferences.WIFI.getKey()))
                current = wifi;

            else if (qName.equals(Preferences.MOBILE.getKey()))
                current = mobile;

            else if (qName.equals(Preferences.SCREEN_WIFI.getKey()))
                current = screen_wifi;

            else if (qName.equals(Preferences.SCREEN_OTHER.getKey()))
                current = screen_other;

            else if (qName.equals(Preferences.ROAMING.getKey()))
                current = roaming;

            else if (qName.equals(Preferences.LOCKDOWN.getKey()))
                current = lockdown;

            else if (qName.equals(Preferences.APPLY.getKey()))
                current = apply;

            else if (qName.equals(Preferences.NOTIFY.getKey()))
                current = notify;

            else if (qName.equals(Preferences.FILTER.getKey())) {
                current = null;
                Log.i(TAG, "Clearing filters");
                DatabaseHelper.getInstance(context).clearAccess();

            } else if (qName.equals(Preferences.FORWARD.getKey())) {
                current = null;
                Log.i(TAG, "Clearing forwards");
                DatabaseHelper.getInstance(context).deleteForward();

            } else if (qName.equals(Serializer.Tag.SETTING)) {
                String key = attributes.getValue(Serializer.Attribute.KEY);
                String type = attributes.getValue(Serializer.Attribute.TYPE);
                String value = attributes.getValue(Serializer.Attribute.VALUE);

                if (current == null)
                    Log.e(TAG, "No current key=" + key);
                else {
                    if (Preferences.ENABLED.getKey().equals(key))
                        enabled = Boolean.parseBoolean(value);
                    else {
                        if (current == application) {
                            // Pro features
                            if (Preferences.LOG.getKey().equals(key)) {
                                if (!IAB.isPurchased(ActivityPro.SKU_LOG, context))
                                    return;
                            } else if (Preferences.THEME.getKey().equals(key)) {
                                if (!IAB.isPurchased(ActivityPro.SKU_THEME, context))
                                    return;
                            } else if (Preferences.SHOW_STATS.getKey().equals(key)) {
                                if (!IAB.isPurchased(ActivityPro.SKU_SPEED, context))
                                    return;
                            }

                            if (Preferences.HOSTS_LAST_IMPORT.getKey().equals(key) || Preferences.HOSTS_LAST_DOWNLOAD.getKey().equals(key))
                                return;
                        }

                        if ("boolean".equals(type))
                            current.put(key, Boolean.parseBoolean(value));
                        else if ("integer".equals(type))
                            current.put(key, Integer.parseInt(value));
                        else if ("string".equals(type))
                            current.put(key, value);
                        else if ("set".equals(type)) {
                            Set<String> set = new HashSet<>();
                            if (!TextUtils.isEmpty(value))
                                for (String s : value.split("\n"))
                                    set.add(s);
                            current.put(key, set);
                        } else
                            Log.e(TAG, "Unknown type key=" + key);
                    }
                }

            } else if (qName.equals(TAG_RULE)) {
                String pkg = attributes.getValue(TAG_PKG);

                String version = attributes.getValue(Column.VERSION.getValue());
                String protocol = attributes.getValue(Column.PROTOCOL.getValue());

                Packet packet = new Packet();
                packet.version = (version == null ? 4 : Integer.parseInt(version));
                packet.protocol = (protocol == null ? 6 /* TCP */ : Integer.parseInt(protocol));
                packet.daddr = attributes.getValue(Column.DADDR.getValue());
                packet.dport = Integer.parseInt(attributes.getValue(Column.DPORT.getValue()));
                packet.time = Long.parseLong(attributes.getValue(Column.TIME.getValue()));

                int block = Integer.parseInt(attributes.getValue(Column.BLOCK.getValue()));

                try {
                    packet.uid = getUid(pkg);
                    DatabaseHelper.getInstance(context).updateAccess(packet, null, block);
                } catch (PackageManager.NameNotFoundException ex) {
                    Log.w(TAG, "Package not found pkg=" + pkg);
                }

            } else if (qName.equals(Column.PORT.getValue())) {
                String pkg = attributes.getValue(TAG_PKG);
                int protocol = Integer.parseInt(attributes.getValue(Column.PROTOCOL.getValue()));
                int dport = Integer.parseInt(attributes.getValue(Column.DPORT.getValue()));
                String raddr = attributes.getValue(Column.RADDR.getValue());
                int rport = Integer.parseInt(attributes.getValue(Column.RPORT.getValue()));

                try {
                    int uid = getUid(pkg);
                    DatabaseHelper.getInstance(context).addForward(protocol, dport, raddr, rport, uid);
                } catch (PackageManager.NameNotFoundException ex) {
                    Log.w(TAG, "Package not found pkg=" + pkg);
                }

            } else
                Log.e(TAG, "Unknown element qname=" + qName);
        }

        private int getUid(String pkg) throws PackageManager.NameNotFoundException {
            if (Uid.Root.getPackageName().equals(pkg))
                return Uid.Root.getCode();
            else if (Uid.Media.getPackageName().equals(pkg))
                return Uid.Media.getCode();
            else if (Uid.Multicast.getPackageName().equals(pkg))
                return Uid.Multicast.getCode();
            else if (Uid.Gps.getPackageName().equals(pkg))
                return Uid.Gps.getCode();
            else if (Uid.Dns.getPackageName().equals(pkg))
                return Uid.Dns.getCode();
            else if (Uid.Nobody.getPackageName().equals(pkg))
                return Uid.Nobody.getCode();
            else
                return getPackageManager().getApplicationInfo(pkg, 0).uid;
        }
    }
}
