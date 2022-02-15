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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import eu.faircode.netguard.serializer.Serializer;
import eu.faircode.netguard.database.Column;
import eu.faircode.netguard.database.Table;
import eu.faircode.netguard.format.Files;
import eu.faircode.netguard.reason.SimpleReason;

public class ActivityDns extends AppCompatActivity {
    private static final String TAG = "NetGuard.DNS";

    private static final int REQUEST_EXPORT = 1;

    private boolean running;
    private AdapterDns adapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Util.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.resolving);

        getSupportActionBar().setTitle(R.string.setting_show_resolved);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ListView lvDns = findViewById(R.id.lvDns);
        adapter = new AdapterDns(this, DatabaseHelper.getInstance(this).getDns());
        lvDns.setAdapter(adapter);

        running = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.dns, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        PackageManager pm = getPackageManager();
        menu.findItem(R.id.menu_export).setEnabled(getIntentExport().resolveActivity(pm) != null);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                refresh();
                return true;

            case R.id.menu_cleanup:
                cleanup();
                return true;

            case R.id.menu_clear:
                Util.areYouSure(this, R.string.menu_clear, new Util.DoubtListener() {
                    @Override
                    public void onSure() {
                        clear();
                    }
                });
                return true;

            case R.id.menu_export:
                export();
                return true;
        }
        return false;
    }

    private void refresh() {
        updateAdapter();
    }

    private void cleanup() {
        new AsyncTask<Object, Object, Object>() {
            @Override
            protected Long doInBackground(Object... objects) {
                Log.i(TAG, "Cleanup DNS");
                DatabaseHelper.getInstance(ActivityDns.this).cleanupDns();
                return null;
            }

            @Override
            protected void onPostExecute(Object result) {
                ServiceSinkhole.reload(SimpleReason.DNSCleanup, ActivityDns.this, false);
                updateAdapter();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void clear() {
        new AsyncTask<Object, Object, Object>() {
            @Override
            protected Long doInBackground(Object... objects) {
                Log.i(TAG, "Clear DNS");
                DatabaseHelper.getInstance(ActivityDns.this).clearDns();
                return null;
            }

            @Override
            protected void onPostExecute(Object result) {
                ServiceSinkhole.reload(SimpleReason.DNSClear, ActivityDns.this, false);
                updateAdapter();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void export() {
        startActivityForResult(getIntentExport(), REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "onActivityResult request=" + requestCode + " result=" + requestCode + " ok=" + (resultCode == RESULT_OK));
        if (requestCode == REQUEST_EXPORT) {
            if (resultCode == RESULT_OK && data != null)
                handleExport(data);
        }
    }

    private Intent getIntentExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(Files.FILE_TEXT_XML); // text/xml
        intent.putExtra(Intent.EXTRA_TITLE, Files.getFileName(this, Files.Format.Xml));
        return intent;
    }

    private void handleExport(final Intent data) {
        new AsyncTask<Object, Object, Throwable>() {
            @Override
            protected Throwable doInBackground(Object... objects) {
                OutputStream out = null;
                try {
                    Uri target = data.getData();
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
                        Toast.makeText(ActivityDns.this, R.string.msg_completed, Toast.LENGTH_LONG).show();
                    else
                        Toast.makeText(ActivityDns.this, ex.toString(), Toast.LENGTH_LONG).show();
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

        DateFormat df = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss Z", Locale.US); // RFC 822

        try (Cursor cursor = DatabaseHelper.getInstance(this).getDns()) {
            int colTime = cursor.getColumnIndex(Column.TIME.getValue());
            int colQName = cursor.getColumnIndex(Column.QNAME.getValue());
            int colAName = cursor.getColumnIndex(Column.ANAME.getValue());
            int colResource = cursor.getColumnIndex(Column.RESOURCE.getValue());
            int colTTL = cursor.getColumnIndex(Column.TTL.getValue());
            while (cursor.moveToNext()) {
                long time = cursor.getLong(colTime);
                String qname = cursor.getString(colQName);
                String aname = cursor.getString(colAName);
                String resource = cursor.getString(colResource);
                int ttl = cursor.getInt(colTTL);

                serializer.startTag(null, Table.DNS.getValue());
                serializer.attribute(null, Column.TIME.name(), df.format(time));
                serializer.attribute(null, Column.QNAME.getValue(), qname);
                serializer.attribute(null, Column.ANAME.getValue(), aname);
                serializer.attribute(null, Column.RESOURCE.getValue(), resource);
                serializer.attribute(null, Column.TTL.getValue(), Integer.toString(ttl));
                serializer.endTag(null, Table.DNS.getValue());
            }
        }

        serializer.endTag(null, appName);
        serializer.endDocument();
        serializer.flush();
    }

    private void updateAdapter() {
        if (adapter != null)
            adapter.changeCursor(DatabaseHelper.getInstance(this).getDns());
    }

    @Override
    protected void onDestroy() {
        running = false;
        adapter = null;
        super.onDestroy();
    }
}
