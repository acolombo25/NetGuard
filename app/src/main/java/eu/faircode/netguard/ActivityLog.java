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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NavUtils;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;

import eu.faircode.netguard.database.Column;
import eu.faircode.netguard.format.Files;
import eu.faircode.netguard.preference.Preferences;
import eu.faircode.netguard.reason.Changed;
import eu.faircode.netguard.reason.SimpleReason;

public class ActivityLog extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "NetGuard.Log";

    private boolean running = false;
    private AdapterLog adapter;
    private MenuItem menuSearch = null;

    private boolean live;
    private InetAddress vpn4 = null;
    private InetAddress vpn6 = null;

    private static final int REQUEST_PCAP = 1;

    private final DatabaseHelper.LogChangedListener listener = new DatabaseHelper.LogChangedListener() {
        @Override
        public void onChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateAdapter();
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!IAB.isPurchased(ActivityPro.SKU_LOG, this)) {
            startActivity(new Intent(this, ActivityPro.class));
            finish();
        }

        Util.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.logging);
        running = true;

        // Action bar
        View actionView = getLayoutInflater().inflate(R.layout.actionlog, null, false);
        SwitchCompat swEnabled = actionView.findViewById(R.id.swEnabled);

        getSupportActionBar().setDisplayShowCustomEnabled(true);
        getSupportActionBar().setCustomView(actionView);

        getSupportActionBar().setTitle(R.string.menu_log);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Get settings
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean resolve = prefs.getBoolean(Preferences.RESOLVE.getKey(), Preferences.RESOLVE.getDefaultValue());
        boolean organization = prefs.getBoolean(Preferences.ORGANIZATION.getKey(), Preferences.ORGANIZATION.getDefaultValue());
        boolean log = prefs.getBoolean(Preferences.LOG.getKey(), Preferences.LOG.getDefaultValue());

        // Show disabled message
        TextView tvDisabled = findViewById(R.id.tvDisabled);
        tvDisabled.setVisibility(log ? View.GONE : View.VISIBLE);

        // Set enabled switch
        swEnabled.setChecked(log);
        swEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(Preferences.LOG.getKey(), isChecked).apply();
            }
        });

        // Listen for preference changes
        prefs.registerOnSharedPreferenceChangeListener(this);

        ListView lvLog = findViewById(R.id.lvLog);

        boolean udp = prefs.getBoolean(Preferences.PROTO_UDP.getKey(), Preferences.PROTO_UDP.getDefaultValue());
        boolean tcp = prefs.getBoolean(Preferences.PROTO_TCP.getKey(), Preferences.PROTO_TCP.getDefaultValue());
        boolean other = prefs.getBoolean(Preferences.PROTO_OTHER.getKey(), Preferences.PROTO_OTHER.getDefaultValue());
        boolean allowed = prefs.getBoolean(Preferences.TRAFFIC_ALLOWED.getKey(), Preferences.TRAFFIC_ALLOWED.getDefaultValue());
        boolean blocked = prefs.getBoolean(Preferences.TRAFFIC_BLOCKED.getKey(), Preferences.TRAFFIC_BLOCKED.getDefaultValue());

        adapter = new AdapterLog(this, DatabaseHelper.getInstance(this).getLog(udp, tcp, other, allowed, blocked), resolve, organization);
        adapter.setFilterQueryProvider(new FilterQueryProvider() {
            public Cursor runQuery(CharSequence constraint) {
                return DatabaseHelper.getInstance(ActivityLog.this).searchLog(constraint.toString());
            }
        });

        lvLog.setAdapter(adapter);

        try {
            vpn4 = InetAddress.getByName(prefs.getString(Preferences.VPN4.getKey(), Preferences.VPN4.getDefaultValue()));
            vpn6 = InetAddress.getByName(prefs.getString(Preferences.VPN6.getKey(), Preferences.VPN6.getDefaultValue()));
        } catch (UnknownHostException ex) {
            Util.logException(TAG, ex);
        }

        lvLog.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            @SuppressLint("Range")
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                PackageManager pm = getPackageManager();
                Cursor cursor = (Cursor) adapter.getItem(position);
                long time = cursor.getLong(cursor.getColumnIndex(Column.TIME.getValue()));
                int version = cursor.getInt(cursor.getColumnIndex(Column.VERSION.getValue()));
                int protocol = cursor.getInt(cursor.getColumnIndex(Column.PROTOCOL.getValue()));
                final String saddr = cursor.getString(cursor.getColumnIndex(Column.SADDR.getValue()));
                final int sport = (cursor.isNull(cursor.getColumnIndex(Column.SPORT.getValue())) ? -1 : cursor.getInt(cursor.getColumnIndex(Column.SPORT.getValue())));
                final String daddr = cursor.getString(cursor.getColumnIndex(Column.DADDR.getValue()));
                final int dport = (cursor.isNull(cursor.getColumnIndex(Column.DPORT.getValue())) ? -1 : cursor.getInt(cursor.getColumnIndex(Column.DPORT.getValue())));
                final String dname = cursor.getString(cursor.getColumnIndex(Column.DNAME.getValue()));
                final int uid = (cursor.isNull(cursor.getColumnIndex(Column.UID.getValue())) ? -1 : cursor.getInt(cursor.getColumnIndex(Column.UID.getValue())));
                int allowed = (cursor.isNull(cursor.getColumnIndex(Column.ALLOWED.getValue())) ? -1 : cursor.getInt(cursor.getColumnIndex(Column.ALLOWED.getValue())));

                // Get external address
                InetAddress addr = null;
                try {
                    addr = InetAddress.getByName(daddr);
                } catch (UnknownHostException ex) {
                    Util.logException(TAG, ex);
                }

                String ip;
                int port;
                if (addr.equals(vpn4) || addr.equals(vpn6)) {
                    ip = saddr;
                    port = sport;
                } else {
                    ip = daddr;
                    port = dport;
                }

                // Build popup menu
                PopupMenu popup = new PopupMenu(ActivityLog.this, findViewById(R.id.vwPopupAnchor));
                popup.inflate(R.menu.log);

                // Application name
                if (uid >= 0)
                    popup.getMenu().findItem(R.id.menu_application).setTitle(TextUtils.join(", ", Util.getApplicationNames(uid, ActivityLog.this)));
                else
                    popup.getMenu().removeItem(R.id.menu_application);

                // Destination IP
                popup.getMenu().findItem(R.id.menu_protocol).setTitle(Util.getProtocolName(protocol, version, false));

                // Whois
                final Intent lookupIP = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dnslytics.com/whois-lookup/" + ip));
                if (pm.resolveActivity(lookupIP, 0) == null)
                    popup.getMenu().removeItem(R.id.menu_whois);
                else
                    popup.getMenu().findItem(R.id.menu_whois).setTitle(getString(R.string.title_log_whois, ip));

                // Lookup port
                final Intent lookupPort = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.speedguide.net/port.php?port=" + port));
                if (port <= 0 || pm.resolveActivity(lookupPort, 0) == null)
                    popup.getMenu().removeItem(R.id.menu_port);
                else
                    popup.getMenu().findItem(R.id.menu_port).setTitle(getString(R.string.title_log_port, port));

                if (prefs.getBoolean(Preferences.FILTER.getKey(), Preferences.FILTER.getDefaultValue())) {
                    if (uid <= 0) {
                        popup.getMenu().removeItem(R.id.menu_allow);
                        popup.getMenu().removeItem(R.id.menu_block);
                    }
                } else {
                    popup.getMenu().removeItem(R.id.menu_allow);
                    popup.getMenu().removeItem(R.id.menu_block);
                }

                final Packet packet = new Packet();
                packet.version = version;
                packet.protocol = protocol;
                packet.daddr = daddr;
                packet.dport = dport;
                packet.time = time;
                packet.uid = uid;
                packet.allowed = (allowed > 0);

                // Time
                popup.getMenu().findItem(R.id.menu_time).setTitle(SimpleDateFormat.getDateTimeInstance().format(time));

                // Handle click
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.menu_application: {
                                Intent main = new Intent(ActivityLog.this, ActivityMain.class);
                                main.putExtra(ActivityMain.EXTRA_SEARCH, Integer.toString(uid));
                                startActivity(main);
                                return true;
                            }

                            case R.id.menu_whois:
                                startActivity(lookupIP);
                                return true;

                            case R.id.menu_port:
                                startActivity(lookupPort);
                                return true;

                            case R.id.menu_allow:
                                if (IAB.isPurchased(ActivityPro.SKU_FILTER, ActivityLog.this)) {
                                    DatabaseHelper.getInstance(ActivityLog.this).updateAccess(packet, dname, 0);
                                    ServiceSinkhole.reload(SimpleReason.AllowHost, ActivityLog.this, false);
                                    Intent main = new Intent(ActivityLog.this, ActivityMain.class);
                                    main.putExtra(ActivityMain.EXTRA_SEARCH, Integer.toString(uid));
                                    startActivity(main);
                                } else
                                    startActivity(new Intent(ActivityLog.this, ActivityPro.class));
                                return true;

                            case R.id.menu_block:
                                if (IAB.isPurchased(ActivityPro.SKU_FILTER, ActivityLog.this)) {
                                    DatabaseHelper.getInstance(ActivityLog.this).updateAccess(packet, dname, 1);
                                    ServiceSinkhole.reload(SimpleReason.BlockHost, ActivityLog.this, false);
                                    Intent main = new Intent(ActivityLog.this, ActivityMain.class);
                                    main.putExtra(ActivityMain.EXTRA_SEARCH, Integer.toString(uid));
                                    startActivity(main);
                                } else
                                    startActivity(new Intent(ActivityLog.this, ActivityPro.class));
                                return true;

                            case R.id.menu_copy:
                                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText(getString(R.string.app_name), dname == null ? daddr : dname);
                                clipboard.setPrimaryClip(clip);
                                return true;

                            default:
                                return false;
                        }
                    }
                });

                // Show
                popup.show();
            }
        });

        live = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (live) {
            DatabaseHelper.getInstance(this).addLogChangedListener(listener);
            updateAdapter();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (live)
            DatabaseHelper.getInstance(this).removeLogChangedListener(listener);
    }

    @Override
    protected void onDestroy() {
        running = false;
        adapter = null;
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String name) {
        Log.i(TAG, "Preference " + name + "=" + prefs.getAll().get(name));
        if (Preferences.LOG.getKey().equals(name)) {
            // Get enabled
            boolean log = prefs.getBoolean(name, false);

            // Display disabled warning
            TextView tvDisabled = findViewById(R.id.tvDisabled);
            tvDisabled.setVisibility(log ? View.GONE : View.VISIBLE);

            // Check switch state
            SwitchCompat swEnabled = getSupportActionBar().getCustomView().findViewById(R.id.swEnabled);
            if (swEnabled.isChecked() != log)
                swEnabled.setChecked(log);

            ServiceSinkhole.reload(new Changed(name), ActivityLog.this, false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.logging, menu);

        menuSearch = menu.findItem(R.id.menu_search);
        SearchView searchView = (SearchView) menuSearch.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (adapter != null)
                    adapter.getFilter().filter(getUidForName(query));
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (adapter != null)
                    adapter.getFilter().filter(getUidForName(newText));
                return true;
            }
        });
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                if (adapter != null)
                    adapter.getFilter().filter(null);
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // https://gist.github.com/granoeste/5574148
        File pcap_file = Files.getPcapFile(this);

        boolean export = (getPackageManager().resolveActivity(getIntentPCAPDocument(), 0) != null);

        menu.findItem(R.id.menu_protocol_udp).setChecked(prefs.getBoolean(Preferences.PROTO_UDP.getKey(), Preferences.PROTO_UDP.getDefaultValue()));
        menu.findItem(R.id.menu_protocol_tcp).setChecked(prefs.getBoolean(Preferences.PROTO_TCP.getKey(), Preferences.PROTO_TCP.getDefaultValue()));
        menu.findItem(R.id.menu_protocol_other).setChecked(prefs.getBoolean(Preferences.PROTO_OTHER.getKey(), Preferences.PROTO_OTHER.getDefaultValue()));
        menu.findItem(R.id.menu_traffic_allowed).setEnabled(prefs.getBoolean(Preferences.FILTER.getKey(), Preferences.FILTER.getDefaultValue()));
        menu.findItem(R.id.menu_traffic_allowed).setChecked(prefs.getBoolean(Preferences.TRAFFIC_ALLOWED.getKey(), Preferences.TRAFFIC_ALLOWED.getDefaultValue()));
        menu.findItem(R.id.menu_traffic_blocked).setChecked(prefs.getBoolean(Preferences.TRAFFIC_BLOCKED.getKey(), Preferences.TRAFFIC_BLOCKED.getDefaultValue()));

        menu.findItem(R.id.menu_refresh).setEnabled(!menu.findItem(R.id.menu_log_live).isChecked());
        menu.findItem(R.id.menu_log_resolve).setChecked(prefs.getBoolean(Preferences.RESOLVE.getKey(), Preferences.RESOLVE.getDefaultValue()));
        menu.findItem(R.id.menu_log_organization).setChecked(prefs.getBoolean(Preferences.ORGANIZATION.getKey(), Preferences.ORGANIZATION.getDefaultValue()));
        menu.findItem(R.id.menu_pcap_enabled).setChecked(prefs.getBoolean(Preferences.PCAP.getKey(), Preferences.PCAP.getDefaultValue()));
        menu.findItem(R.id.menu_pcap_export).setEnabled(pcap_file.exists() && export);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final File pcap_file = Files.getPcapFile(this);

        switch (item.getItemId()) {
            case android.R.id.home:
                Log.i(TAG, "Up");
                NavUtils.navigateUpFromSameTask(this);
                return true;

            case R.id.menu_protocol_udp:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean(Preferences.PROTO_UDP.getKey(), item.isChecked()).apply();
                updateAdapter();
                return true;

            case R.id.menu_protocol_tcp:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean(Preferences.PROTO_TCP.getKey(), item.isChecked()).apply();
                updateAdapter();
                return true;

            case R.id.menu_protocol_other:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean(Preferences.PROTO_OTHER.getKey(), item.isChecked()).apply();
                updateAdapter();
                return true;

            case R.id.menu_traffic_allowed:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean(Preferences.TRAFFIC_ALLOWED.getKey(), item.isChecked()).apply();
                updateAdapter();
                return true;

            case R.id.menu_traffic_blocked:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean(Preferences.TRAFFIC_BLOCKED.getKey(), item.isChecked()).apply();
                updateAdapter();
                return true;

            case R.id.menu_log_live:
                item.setChecked(!item.isChecked());
                live = item.isChecked();
                if (live) {
                    DatabaseHelper.getInstance(this).addLogChangedListener(listener);
                    updateAdapter();
                } else
                    DatabaseHelper.getInstance(this).removeLogChangedListener(listener);
                return true;

            case R.id.menu_refresh:
                updateAdapter();
                return true;

            case R.id.menu_log_resolve:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean(Preferences.RESOLVE.getKey(), item.isChecked()).apply();
                adapter.setResolve(item.isChecked());
                adapter.notifyDataSetChanged();
                return true;

            case R.id.menu_log_organization:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean(Preferences.ORGANIZATION.getKey(), item.isChecked()).apply();
                adapter.setOrganization(item.isChecked());
                adapter.notifyDataSetChanged();
                return true;

            case R.id.menu_pcap_enabled:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean(Preferences.PCAP.getKey(), item.isChecked()).apply();
                ServiceSinkhole.setPcap(item.isChecked(), ActivityLog.this);
                return true;

            case R.id.menu_pcap_export:
                startActivityForResult(getIntentPCAPDocument(), REQUEST_PCAP);
                return true;

            case R.id.menu_log_clear:
                new AsyncTask<Object, Object, Object>() {
                    @Override
                    protected Object doInBackground(Object... objects) {
                        DatabaseHelper.getInstance(ActivityLog.this).clearLog(-1);
                        if (prefs.getBoolean(Preferences.PCAP.getKey(), Preferences.PCAP.getDefaultValue())) {
                            ServiceSinkhole.setPcap(false, ActivityLog.this);
                            if (pcap_file.exists() && !pcap_file.delete())
                                Log.w(TAG, "Delete PCAP failed");
                            ServiceSinkhole.setPcap(true, ActivityLog.this);
                        } else {
                            if (pcap_file.exists() && !pcap_file.delete())
                                Log.w(TAG, "Delete PCAP failed");
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Object result) {
                        if (running)
                            updateAdapter();
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                return true;

            case R.id.menu_log_support:
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://github.com/M66B/NetGuard/blob/master/FAQ.md#user-content-faq27"));
                if (getPackageManager().resolveActivity(intent, 0) != null)
                    startActivity(intent);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateAdapter() {
        if (adapter != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean udp = prefs.getBoolean(Preferences.PROTO_UDP.getKey(), Preferences.PROTO_UDP.getDefaultValue());
            boolean tcp = prefs.getBoolean(Preferences.PROTO_TCP.getKey(), Preferences.PROTO_TCP.getDefaultValue());
            boolean other = prefs.getBoolean(Preferences.PROTO_OTHER.getKey(), Preferences.PROTO_OTHER.getDefaultValue());
            boolean allowed = prefs.getBoolean(Preferences.TRAFFIC_ALLOWED.getKey(), Preferences.TRAFFIC_ALLOWED.getDefaultValue());
            boolean blocked = prefs.getBoolean(Preferences.TRAFFIC_BLOCKED.getKey(), Preferences.TRAFFIC_BLOCKED.getDefaultValue());
            adapter.changeCursor(DatabaseHelper.getInstance(this).getLog(udp, tcp, other, allowed, blocked));
            if (menuSearch != null && menuSearch.isActionViewExpanded()) {
                SearchView searchView = (SearchView) menuSearch.getActionView();
                adapter.getFilter().filter(getUidForName(searchView.getQuery().toString()));
            }
        }
    }

    private String getUidForName(String query) {
        if (query != null && query.length() > 0) {
            for (Rule rule : Rule.getRules(true, ActivityLog.this))
                if (rule.name != null && rule.name.toLowerCase().contains(query.toLowerCase())) {
                    String newQuery = Integer.toString(rule.uid);
                    Log.i(TAG, "Search " + query + " found " + rule.name + " new " + newQuery);
                    return newQuery;
                }
            Log.i(TAG, "Search " + query + " not found");
        }
        return query;
    }

    private Intent getIntentPCAPDocument() {
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
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE, Files.getFileName(this, Files.Format.Pcap));
        }
        return intent;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        Log.i(TAG, "onActivityResult request=" + requestCode + " result=" + requestCode + " ok=" + (resultCode == RESULT_OK));

        if (requestCode == REQUEST_PCAP) {
            if (resultCode == RESULT_OK && data != null)
                handleExportPCAP(data);

        } else {
            Log.w(TAG, "Unknown activity result request=" + requestCode);
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void handleExportPCAP(final Intent data) {
        new AsyncTask<Object, Object, Throwable>() {
            @Override
            protected Throwable doInBackground(Object... objects) {
                OutputStream out = null;
                FileInputStream in = null;
                try {
                    // Stop capture
                    ServiceSinkhole.setPcap(false, ActivityLog.this);

                    Uri target = data.getData();
                    if (data.hasExtra("org.openintents.extra.DIR_PATH"))
                        target = Uri.parse(target + "/netguard.pcap");
                    Log.i(TAG, "Export PCAP URI=" + target);
                    out = getContentResolver().openOutputStream(target);

                    File pcap = Files.getPcapFile(ActivityLog.this);
                    in = new FileInputStream(pcap);

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
                    Util.logException(TAG, ex);
                    return ex;
                } finally {
                    if (out != null)
                        try {
                            out.close();
                        } catch (IOException ex) {
                            Util.logException(TAG, ex);
                        }
                    if (in != null)
                        try {
                            in.close();
                        } catch (IOException ex) {
                            Util.logException(TAG, ex);
                        }

                    // Resume capture
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ActivityLog.this);
                    if (prefs.getBoolean(Preferences.PCAP.getKey(), Preferences.PCAP.getDefaultValue()))
                        ServiceSinkhole.setPcap(true, ActivityLog.this);
                }
            }

            @Override
            protected void onPostExecute(Throwable ex) {
                if (ex == null)
                    Toast.makeText(ActivityLog.this, R.string.msg_completed, Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(ActivityLog.this, ex.toString(), Toast.LENGTH_LONG).show();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
