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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;


import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import eu.faircode.netguard.database.Column;
import eu.faircode.netguard.database.Index;
import eu.faircode.netguard.database.Table;
import eu.faircode.netguard.preference.Preferences;
import eu.faircode.netguard.preference.DefaultPreferences;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "NetGuard.Database";

    private static final String DB_NAME = "Netguard";
    private static final int DB_VERSION = 21;

    private static boolean once = true;
    private static final List<LogChangedListener> logChangedListeners = new ArrayList<>();
    private static final List<AccessChangedListener> accessChangedListeners = new ArrayList<>();
    private static final List<ForwardChangedListener> forwardChangedListeners = new ArrayList<>();

    private static final HandlerThread hthread;
    private static final Handler handler;

    private static final Map<Integer, Long> mapUidHosts = new HashMap<>();

    private final static int MSG_LOG = 1;
    private final static int MSG_ACCESS = 2;
    private final static int MSG_FORWARD = 3;

    private final SharedPreferences prefs;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    static {
        hthread = new HandlerThread("DatabaseHelper");
        hthread.start();
        handler = new Handler(hthread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                handleChangedNotification(msg);
            }
        };
    }

    private static DatabaseHelper dh = null;

    public static DatabaseHelper getInstance(Context context) {
        if (dh == null)
            dh = new DatabaseHelper(context.getApplicationContext());
        return dh;
    }

    public static void clearCache() {
        synchronized (mapUidHosts) {
            mapUidHosts.clear();
        }
    }

    @Override
    public void close() {
        Log.w(TAG, "Database is being closed");
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        prefs = DefaultPreferences.getPreferences(context);

        if (!once) {
            once = true;

            File dbfile = context.getDatabasePath(DB_NAME);
            if (dbfile.exists()) {
                Log.w(TAG, "Deleting " + dbfile);
                dbfile.delete();
            }

            File dbjournal = context.getDatabasePath(DB_NAME + "-journal");
            if (dbjournal.exists()) {
                Log.w(TAG, "Deleting " + dbjournal);
                dbjournal.delete();
            }
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating database " + DB_NAME + " version " + DB_VERSION);
        createTableLog(db);
        createTableAccess(db);
        createTableDns(db);
        createTableForward(db);
        createTableApp(db);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.enableWriteAheadLogging();
        super.onConfigure(db);
    }

    private void createTableLog(SQLiteDatabase db) {
        Log.i(TAG, "Creating log table");
        db.execSQL("CREATE TABLE "+Table.LOG.getValue()+" (" +
                " ID INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", "+Column.TIME.getValue()+" INTEGER NOT NULL" +
                ", "+Column.VERSION.getValue()+" INTEGER" +
                ", "+Column.PROTOCOL.getValue()+" INTEGER" +
                ", "+Column.FLAGS.getValue()+" TEXT" +
                ", "+Column.SADDR.getValue()+" TEXT" +
                ", "+Column.SPORT.getValue()+" INTEGER" +
                ", "+Column.DADDR.getValue()+" TEXT" +
                ", "+Column.DPORT.getValue()+" INTEGER" +
                ", "+Column.DNAME.getValue()+" TEXT" +
                ", "+Column.UID.getValue()+" INTEGER" +
                ", "+Column.DATA.getValue()+" TEXT" +
                ", "+Column.ALLOWED.getValue()+" INTEGER" +
                ", "+Column.CONNECTION.getValue()+" INTEGER" +
                ", "+Column.INTERACTIVE.getValue()+" INTEGER" +
                ");");
        createIndex(db, Index.IDX_LOG_TIME, Table.LOG, Column.TIME);
        createIndex(db, Index.IDX_LOG_DEST, Table.LOG, Column.DADDR);
        createIndex(db, Index.IDX_LOG_DNAME, Table.LOG, Column.DNAME);
        createIndex(db, Index.IDX_LOG_DPORT, Table.LOG, Column.DPORT);
        createIndex(db, Index.IDX_LOG_UID, Table.LOG, Column.UID);
    }

    private void createTableAccess(SQLiteDatabase db) {
        Log.i(TAG, "Creating access table");
        db.execSQL("CREATE TABLE "+Table.ACCESS.getValue()+" (" +
                " ID INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", "+Column.UID.getValue()+" INTEGER NOT NULL" +
                ", "+Column.VERSION.getValue()+" INTEGER NOT NULL" +
                ", "+Column.PROTOCOL.getValue()+" INTEGER NOT NULL" +
                ", "+Column.DADDR.getValue()+" TEXT NOT NULL" +
                ", "+Column.DPORT.getValue()+" INTEGER NOT NULL" +
                ", "+Column.TIME.getValue()+" INTEGER NOT NULL" +
                ", "+Column.ALLOWED.getValue()+" INTEGER" +
                ", "+Column.BLOCK.getValue()+" INTEGER NOT NULL" +
                ", "+Column.SENT.getValue()+" INTEGER" +
                ", "+Column.RECEIVED.getValue()+" INTEGER" +
                ", "+Column.CONNECTION.getValue()+" INTEGER" +
                ");");
        createUniqueIndex(db, Index.IDX_ACCESS, Table.ACCESS, Column.UID, Column.VERSION, Column.PROTOCOL, Column.DADDR, Column.DPORT);
        createIndex(db, Index.IDX_ACCESS_DADDR, Table.ACCESS, Column.DADDR);
        createIndex(db, Index.IDX_ACCESS_BLOCK, Table.ACCESS, Column.BLOCK);
    }

    private void createTableDns(SQLiteDatabase db) {
        Log.i(TAG, "Creating dns table");
        db.execSQL("CREATE TABLE "+Table.DNS.getValue()+" (" +
                " ID INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", "+Column.TIME.getValue()+" INTEGER NOT NULL" +
                ", "+Column.QNAME.getValue()+" TEXT NOT NULL" +
                ", "+Column.ANAME.getValue()+" TEXT NOT NULL" +
                ", "+Column.RESOURCE.getValue()+" TEXT NOT NULL" +
                ", "+Column.TTL.getValue()+" INTEGER" +
                ");");
        createUniqueIndex(db, Index.IDX_DNS, Table.DNS, Column.QNAME, Column.ANAME, Column.RESOURCE);
        createIndex(db, Index.IDX_DNS_RESOURCE, Table.DNS, Column.RESOURCE);
    }

    private void createTableForward(SQLiteDatabase db) {
        Log.i(TAG, "Creating forward table");
        db.execSQL("CREATE TABLE "+Table.FORWARD.getValue()+" (" +
                " ID INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", "+Column.PROTOCOL.getValue()+" INTEGER NOT NULL" +
                ", "+Column.DPORT.getValue()+" INTEGER NOT NULL" +
                ", "+Column.RADDR +" TEXT NOT NULL" +
                ", "+Column.RPORT +" INTEGER NOT NULL" +
                ", "+Column.RUID +" INTEGER NOT NULL" +
                ");");
        createUniqueIndex(db, Index.IDX_FORWARD, Table.FORWARD, Column.PROTOCOL, Column.DPORT);
    }

    private void createTableApp(SQLiteDatabase db) {
        Log.i(TAG, "Creating app table");
        db.execSQL("CREATE TABLE "+Table.APP.getValue()+" (" +
                " ID INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", "+Column.PACKAGE.getValue()+" TEXT" +
                ", "+Column.LABEL.getValue()+" TEXT" +
                ", "+Column.SYSTEM.getValue()+" INTEGER  NOT NULL" +
                ", "+Column.INTERNET.getValue()+" INTEGER NOT NULL" +
                ", "+Column.ENABLED.getValue()+" INTEGER NOT NULL" +
                ");");
        createUniqueIndex(db, Index.IDX_PACKAGE, Table.APP, Column.PACKAGE);
    }

    private boolean isColumnMissing(SQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + table + " LIMIT 0", null)) {
            return (cursor.getColumnIndex(column) < 0);
        } catch (Throwable ex) {
            Util.logException(TAG, ex);
            return true;
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, DB_NAME + " upgrading from version " + oldVersion + " to " + newVersion);

        db.beginTransaction();
        try {
            if (oldVersion < 2) {
                addColumn(db, Table.LOG, Column.VERSION, Column.Type.INTEGER);
                addColumn(db, Table.LOG, Column.PROTOCOL, Column.Type.INTEGER);
                addColumn(db, Table.LOG, Column.UID, Column.Type.INTEGER);
                oldVersion = 2;
            }
            if (oldVersion < 3) {
                addColumn(db, Table.LOG, Column.PORT, Column.Type.INTEGER);
                addColumn(db, Table.LOG, Column.FLAGS, Column.Type.TEXT);
                oldVersion = 3;
            }
            if (oldVersion < 4) {
                addColumn(db, Table.LOG, Column.CONNECTION, Column.Type.INTEGER);
                oldVersion = 4;
            }
            if (oldVersion < 5) {
                addColumn(db, Table.LOG, Column.INTERACTIVE, Column.Type.INTEGER);
                oldVersion = 5;
            }
            if (oldVersion < 6) {
                addColumn(db, Table.LOG, Column.ALLOWED, Column.Type.INTEGER);
                oldVersion = 6;
            }
            if (oldVersion < 7) {
                dropTable(db, Table.LOG);
                createTableLog(db);
                oldVersion = 8;
            }
            if (oldVersion < 8) {
                addColumn(db, Table.LOG, Column.DATA, Column.Type.TEXT);
                dropIndex(db, Index.IDX_LOG_SOURCE);
                dropIndex(db, Index.IDX_LOG_DEST);
                createIndex(db, Index.IDX_LOG_SOURCE, Table.LOG, Column.SADDR);
                createIndex(db, Index.IDX_LOG_DEST, Table.LOG, Column.DADDR);
                createIndexIfNotExists(db, Index.IDX_LOG_UID, Table.LOG, Column.UID);
                oldVersion = 8;
            }
            if (oldVersion < 9) {
                createTableAccess(db);
                oldVersion = 9;
            }
            if (oldVersion < 10) {
                dropTable(db, Table.LOG);
                dropTable(db, Table.ACCESS);
                createTableLog(db);
                createTableAccess(db);
                oldVersion = 10;
            }
            if (oldVersion < 12) {
                dropTable(db, Table.ACCESS);
                createTableAccess(db);
                oldVersion = 12;
            }
            if (oldVersion < 13) {
                createIndexIfNotExists(db, Index.IDX_LOG_DPORT, Table.LOG, Column.DPORT);
                createIndexIfNotExists(db, Index.IDX_LOG_DNAME, Table.LOG, Column.DNAME);
                oldVersion = 13;
            }
            if (oldVersion < 14) {
                createTableDns(db);
                oldVersion = 14;
            }
            if (oldVersion < 15) {
                dropTable(db, Table.ACCESS);
                createTableAccess(db);
                oldVersion = 15;
            }
            if (oldVersion < 16) {
                createTableForward(db);
                oldVersion = 16;
            }
            if (oldVersion < 17) {
                addColumn(db, Table.ACCESS, Column.SENT, Column.Type.INTEGER);
                addColumn(db, Table.ACCESS, Column.RECEIVED, Column.Type.INTEGER);
                oldVersion = 17;
            }
            if (oldVersion < 18) {
                createIndexIfNotExists(db, Index.IDX_ACCESS_BLOCK, Table.ACCESS, Column.BLOCK);
                dropIndex(db, Index.IDX_DNS);
                createUniqueIndexIfNotExists(db, Index.IDX_DNS, Table.DNS, Column.QNAME, Column.ANAME, Column.RESOURCE);
                createIndexIfNotExists(db, Index.IDX_DNS_RESOURCE, Table.DNS, Column.RESOURCE);
                oldVersion = 18;
            }
            if (oldVersion < 19) {
                addColumn(db, Table.ACCESS, Column.CONNECTIONS, Column.Type.INTEGER);
                oldVersion = 19;
            }
            if (oldVersion < 20) {
                createIndexIfNotExists(db, Index.IDX_ACCESS_DADDR, Table.ACCESS, Column.DADDR);
                oldVersion = 20;
            }
            if (oldVersion < 21) {
                createTableApp(db);
                oldVersion = 21;
            }

            if (oldVersion == DB_VERSION) {
                db.setVersion(oldVersion);
                db.setTransactionSuccessful();
                Log.i(TAG, DB_NAME + " upgraded to " + DB_VERSION);
            } else
                throw new IllegalArgumentException(DB_NAME + " upgraded to " + oldVersion + " but required " + DB_VERSION);

        } catch (Throwable ex) {
            Util.logException(TAG, ex);
        } finally {
            db.endTransaction();
        }
    }

    // region Operations
    private void dropTable(SQLiteDatabase db, Table table) {
        db.execSQL("DROP TABLE "+table);
    }

    private void addColumn(SQLiteDatabase db, Table table, Column column, Column.Type type) {
        if (isColumnMissing(db, table.getValue(), column.getValue()))
            db.execSQL("ALTER TABLE "+table.getValue()+" ADD COLUMN "+column.getValue()+" "+type.getValue());
    }

    private void dropIndex(SQLiteDatabase db, Index index) {
        db.execSQL("DROP INDEX "+index.getValue());
    }

    private void createIndex(SQLiteDatabase db, Index index, Table table, Column column) {
        db.execSQL(
            "CREATE INDEX "+index.getValue()+" ON "+table.getValue()+"("+column.getValue()+")"
        );
    }
    private void createIndexIfNotExists(SQLiteDatabase db, Index index, Table table, Column column) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS "+index.getValue()+ " ON "+table.getValue()+"("+column.getValue()+")"
        );
    }
    private void createUniqueIndex(SQLiteDatabase db, Index index, Table table, Column... column) {
        db.execSQL(
            "CREATE UNIQUE INDEX "+index.getValue()+ " ON "+table.getValue()+"("+ getColumnList(column) +")"
        );
    }
    private void createUniqueIndexIfNotExists(SQLiteDatabase db, Index index, Table table, Column... column) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS "+index.getValue()+ " ON "+table.getValue()+"("+ getColumnList(column) +")"
        );
    }
    private String getColumnList(Column... column) {
        String indexes = Arrays.toString(column);
        return indexes.substring(1, indexes.length()-1);
    }
    // endregion

    // Log
    public void insertLog(Packet packet, String dname, int connection, boolean interactive) {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                ContentValues cv = new ContentValues();
                cv.put(Column.TIME.getValue(), packet.time);
                cv.put(Column.VERSION.getValue(), packet.version);

                if (packet.protocol < 0)
                    cv.putNull(Column.PROTOCOL.getValue());
                else
                    cv.put(Column.PROTOCOL.getValue(), packet.protocol);

                cv.put(Column.FLAGS.getValue(), packet.flags);

                cv.put(Column.SADDR.getValue(), packet.saddr);
                if (packet.sport < 0)
                    cv.putNull(Column.SPORT.getValue());
                else
                    cv.put(Column.SPORT.getValue(), packet.sport);

                cv.put(Column.DADDR.getValue(), packet.daddr);
                if (packet.dport < 0)
                    cv.putNull(Column.DPORT.getValue());
                else
                    cv.put(Column.DPORT.getValue(), packet.dport);

                if (dname == null)
                    cv.putNull(Column.DNAME.getValue());
                else
                    cv.put(Column.DNAME.getValue(), dname);

                cv.put(Column.DATA.getValue(), packet.data);

                if (packet.uid < 0)
                    cv.putNull(Column.UID.getValue());
                else
                    cv.put(Column.UID.getValue(), packet.uid);

                cv.put(Column.ALLOWED.getValue(), packet.allowed ? 1 : 0);

                cv.put(Column.CONNECTION.getValue(), connection);
                cv.put(Column.INTERACTIVE.getValue(), interactive ? 1 : 0);

                if (db.insert(Table.LOG.getValue(), null, cv) == -1)
                    Log.e(TAG, "Insert log failed");

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }

        notifyLogChanged();
    }

    public void clearLog(int uid) {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                if (uid < 0)
                    db.delete(Table.LOG.getValue(), null, new String[]{});
                else
                    db.delete(Table.LOG.getValue(), Column.UID.getValue()+" = ?", new String[]{Integer.toString(uid)});

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            db.execSQL("VACUUM");
        } finally {
            lock.writeLock().unlock();
        }

        notifyLogChanged();
    }

    public void cleanupLog(long time) {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                // There an index on time
                int rows = db.delete(Table.LOG.getValue(), Column.TIME.getValue()+" < ?", new String[]{Long.toString(time)});
                Log.i(TAG, "Cleanup log" +
                        " before=" + SimpleDateFormat.getDateTimeInstance().format(new Date(time)) +
                        " rows=" + rows);

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Cursor getLog(boolean udp, boolean tcp, boolean other, boolean allowed, boolean blocked) {
        lock.readLock().lock();
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            // There is an index on time
            // There is no index on protocol/allowed for write performance
            String query = "SELECT ID AS _id, *"+
                    " FROM " + Table.LOG.getValue() +
                    " WHERE (0 = 1";
            if (udp)
                query += " OR protocol = 17";
            if (tcp)
                query += " OR protocol = 6";
            if (other)
                query += " OR (protocol <> 6 AND protocol <> 17)";
            query += ") AND (0 = 1";
            if (allowed)
                query += " OR allowed = 1";
            if (blocked)
                query += " OR allowed = 0";
            query += ")";
            query += " ORDER BY "+Column.TIME+" DESC";
            return db.rawQuery(query, new String[]{});
        } finally {
            lock.readLock().unlock();
        }
    }

    public Cursor searchLog(String find) {
        lock.readLock().lock();
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            // There is an index on daddr, dname, dport and uid
            String query = "SELECT ID AS _id, *"+
            " FROM "+Table.LOG.getValue()+
            " WHERE "+Column.DADDR.getValue()+" LIKE ? OR "+Column.DNAME.getValue()+" LIKE ? OR "+Column.DPORT.getValue()+" = ? OR "+Column.UID.getValue()+" = ?"+
            " ORDER BY "+Column.TIME.getValue()+" DESC";
            return db.rawQuery(query, new String[]{"%" + find + "%", "%" + find + "%", find, find});
        } finally {
            lock.readLock().unlock();
        }
    }

    // Access

    public boolean updateAccess(Packet packet, String dname, int block) {
        int rows;

        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                ContentValues cv = new ContentValues();
                cv.put(Column.TIME.getValue(), packet.time);
                cv.put(Column.ALLOWED.getValue(), packet.allowed ? 1 : 0);
                if (block >= 0)
                    cv.put(Column.BLOCK.getValue(), block);

                // There is a segmented index on uid, version, protocol, daddr and dport
                rows = db.update(Table.ACCESS.getValue(), cv, Column.UID.getValue()+" = ? AND "+Column.VERSION.getValue()+" = ? AND "+Column.PROTOCOL.getValue()+" = ? AND "+Column.DADDR.getValue()+" = ? AND "+Column.DPORT.getValue()+" = ?",
                        new String[]{
                                Integer.toString(packet.uid),
                                Integer.toString(packet.version),
                                Integer.toString(packet.protocol),
                                dname == null ? packet.daddr : dname,
                                Integer.toString(packet.dport)});

                if (rows == 0) {
                    cv.put(Column.UID.getValue(), packet.uid);
                    cv.put(Column.VERSION.getValue(), packet.version);
                    cv.put(Column.PROTOCOL.getValue(), packet.protocol);
                    cv.put(Column.DADDR.getValue(), dname == null ? packet.daddr : dname);
                    cv.put(Column.DPORT.getValue(), packet.dport);
                    if (block < 0)
                        cv.put(Column.BLOCK.getValue(), block);

                    if (db.insert(Table.ACCESS.getValue(), null, cv) == -1)
                        Log.e(TAG, "Insert access failed");
                } else if (rows != 1)
                    Log.e(TAG, "Update access failed rows=" + rows);

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }

        notifyAccessChanged();
        return (rows == 0);
    }

    public void updateUsage(Usage usage, String dname) {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                // There is a segmented index on uid, version, protocol, daddr and dport
                String selection = Column.UID.getValue() + " = ? AND "
                        + Column.VERSION.getValue() + " = ?  AND "
                        + Column.PROTOCOL.getValue() + " = ? AND "
                        + Column.DADDR.getValue() + " = ?  AND "
                        + Column.DPORT.getValue() + " = ?";
                String[] selectionArgs = new String[]{
                        Integer.toString(usage.Uid),
                        Integer.toString(usage.Version),
                        Integer.toString(usage.Protocol),
                        dname == null ? usage.DAddr : dname,
                        Integer.toString(usage.DPort)
                };

                try (Cursor cursor = db.query(Table.ACCESS.getValue(), new String[]{Column.SENT.getValue(), Column.RECEIVED.getValue(), Column.CONNECTIONS.getValue()}, selection, selectionArgs, null, null, null)) {
                    long sent = 0;
                    long received = 0;
                    int connections = 0;
                    int colSent = cursor.getColumnIndex(Column.SENT.getValue());
                    int colReceived = cursor.getColumnIndex(Column.RECEIVED.getValue());
                    int colConnections = cursor.getColumnIndex(Column.CONNECTIONS.getValue());
                    if (cursor.moveToNext()) {
                        sent = cursor.isNull(colSent) ? 0 : cursor.getLong(colSent);
                        received = cursor.isNull(colReceived) ? 0 : cursor.getLong(colReceived);
                        connections = cursor.isNull(colConnections) ? 0 : cursor.getInt(colConnections);
                    }

                    ContentValues cv = new ContentValues();
                    cv.put(Column.SENT.getValue(), sent + usage.Sent);
                    cv.put(Column.RECEIVED.getValue(), received + usage.Received);
                    cv.put(Column.CONNECTIONS.getValue(), connections + 1);

                    int rows = db.update(Table.ACCESS.getValue(), cv, selection, selectionArgs);
                    if (rows != 1)
                        Log.e(TAG, "Update usage failed rows=" + rows);
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }

        notifyAccessChanged();
    }

    public void setAccess(long id, int block) {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                ContentValues cv = new ContentValues();
                cv.put(Column.BLOCK.getValue(), block);
                cv.put(Column.ALLOWED.getValue(), -1);

                if (db.update(Table.ACCESS.getValue(), cv, "ID = ?", new String[]{Long.toString(id)}) != 1)
                    Log.e(TAG, "Set access failed");

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }

        notifyAccessChanged();
    }

    public void clearAccess() {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                db.delete(Table.ACCESS.getValue(), null, null);

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }

        notifyAccessChanged();
    }

    public void clearAccess(int uid, boolean keeprules) {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                // There is a segmented index on uid
                // There is an index on block
                if (keeprules)
                    db.delete(Table.ACCESS.getValue(), Column.UID.getValue() + " = ? AND " + Column.BLOCK.getValue() + " < 0", new String[]{Integer.toString(uid)});
                else
                    db.delete(Table.ACCESS.getValue(), Column.UID.getValue() + " = ?", new String[]{Integer.toString(uid)});

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }

        notifyAccessChanged();
    }

    public void resetUsage(int uid) {
        lock.writeLock().lock();
        try {
            // There is a segmented index on uid
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                ContentValues cv = new ContentValues();
                cv.putNull(Column.SENT.getValue());
                cv.putNull(Column.RECEIVED.getValue());
                cv.putNull(Column.CONNECTIONS.getValue());
                db.update(Table.ACCESS.getValue(), cv,
                        (uid < 0 ? null : Column.UID.getValue() + " = ?"),
                        (uid < 0 ? null : new String[]{Integer.toString(uid)}));

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }

        notifyAccessChanged();
    }

    public Cursor getAccess(int uid) {
        lock.readLock().lock();
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            // There is a segmented index on uid
            // There is no index on time for write performance
            String query = "SELECT a.ID AS _id, a.*"+
            ", (SELECT COUNT(DISTINCT d."+Column.QNAME.getValue()+") FROM "+Table.DNS.getValue()+" d WHERE d."+Column.RESOURCE.getValue()+" IN (SELECT d1."+Column.RESOURCE.getValue()+" FROM "+Table.DNS.getValue()+" d1 WHERE d1."+Column.QNAME.getValue()+" = a."+Column.DADDR.getValue()+")) count"+
            " FROM "+Table.ACCESS.getValue()+" a"+
            " WHERE a."+Column.UID.getValue()+" = ?"+
            " ORDER BY a."+Column.TIME.getValue()+" DESC"+
            " LIMIT 250";
            return db.rawQuery(query, new String[]{Integer.toString(uid)});
        } finally {
            lock.readLock().unlock();
        }
    }

    public Cursor getAccess() {
        lock.readLock().lock();
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            // There is a segmented index on uid
            // There is an index on block
            return db.query(Table.ACCESS.getValue(), null, Column.BLOCK.getValue() + " >= 0", null, null, null, Column.UID.getValue());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Cursor getAccessUnset(int uid, int limit, long since) {
        lock.readLock().lock();
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            // There is a segmented index on uid, block and daddr
            // There is no index on allowed and time for write performance
            String query = "SELECT MAX("+Column.TIME.getValue()+") AS "+Column.TIME.getValue()+", "+Column.DADDR.getValue()+", "+Column.ALLOWED.getValue()+
            " FROM "+Table.ACCESS.getValue()+
            " WHERE "+Column.UID.getValue()+" = ?"+
            " AND "+Column.BLOCK.getValue()+" < 0"+
            " AND "+Column.TIME.getValue()+" >= ?"+
            " GROUP BY "+Column.DADDR.getValue()+", "+Column.ALLOWED.getValue()+
            " ORDER BY "+Column.TIME.getValue()+" DESC";
            if (limit > 0)
                query += " LIMIT " + limit;
            return db.rawQuery(query, new String[]{Integer.toString(uid), Long.toString(since)});
        } finally {
            lock.readLock().unlock();
        }
    }

    public long getHostCount(int uid, boolean usecache) {
        if (usecache)
            synchronized (mapUidHosts) {
                if (mapUidHosts.containsKey(uid))
                    return mapUidHosts.get(uid);
            }

        lock.readLock().lock();
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            // There is a segmented index on uid
            // There is an index on block
            long hosts = db.compileStatement(
                    "SELECT COUNT(*)" +
                        " FROM "+Table.ACCESS.getValue()+
                        " WHERE "+Column.BLOCK.getValue()+" >= 0" +
                        " AND "+Column.UID.getValue()+" =" + uid
            ).simpleQueryForLong();
            synchronized (mapUidHosts) {
                mapUidHosts.put(uid, hosts);
            }
            return hosts;
        } finally {
            lock.readLock().unlock();
        }
    }

    // DNS

    public boolean insertDns(ResourceRecord rr) {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                int ttl = rr.TTL;

                int min = DefaultPreferences.getBoxedInt(prefs, Preferences.TTL);
                if (ttl < min)
                    ttl = min;

                ContentValues cv = new ContentValues();
                cv.put(Column.TIME.getValue(), rr.Time);
                cv.put(Column.TTL.getValue(), ttl * 1000L);

                int rows = db.update(
                        Table.DNS.getValue(),
                        cv,
                        Column.QNAME.getValue() + " = ? AND "
                                +Column.ANAME.getValue()+" = ? AND "
                                +Column.RESOURCE.getValue()+" = ?",
                        new String[]{rr.QName, rr.AName, rr.Resource}
                );

                if (rows == 0) {
                    cv.put(Column.QNAME.getValue(), rr.QName);
                    cv.put(Column.ANAME.getValue(), rr.AName);
                    cv.put(Column.RESOURCE.getValue(), rr.Resource);

                    if (db.insert(Table.DNS.getValue(), null, cv) == -1)
                        Log.e(TAG, "Insert dns failed");
                    else
                        rows = 1;
                } else if (rows != 1)
                    Log.e(TAG, "Update dns failed rows=" + rows);

                db.setTransactionSuccessful();

                return (rows > 0);
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void cleanupDns() {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                // There is no index on time for write performance
                long now = new Date().getTime();
                db.delete(Table.DNS.getValue(), Column.TIME.getValue() + " + " + Column.TTL + " < " + now, null);
                Log.i(TAG, "Cleanup DNS");

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearDns() {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                db.delete(Table.DNS.getValue(), null, new String[]{});

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getQName(int uid, String ip) {
        lock.readLock().lock();
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            // There is a segmented index on resource
            String query = "SELECT d."+Column.QNAME.getValue()+
            " FROM "+Table.DNS.getValue()+" AS d"+
            " WHERE d."+Column.RESOURCE.getValue()+" = '" + ip.replace("'", "''") + "'"+
            " ORDER BY d."+Column.QNAME.getValue()+
            " LIMIT 1";
            // There is no way to known for sure which domain name an app used, so just pick the first one
            return db.compileStatement(query).simpleQueryForString();
        } catch (SQLiteDoneException ignored) {
            // Not found
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Cursor getAlternateQNames(String qname) {
        lock.readLock().lock();
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            String query = "SELECT DISTINCT d2."+Column.QNAME.getValue()+
            " FROM "+Table.DNS.getValue()+" d1"+
            " JOIN "+Table.DNS.getValue()+" d2"+
            " ON d2."+Column.RESOURCE.getValue()+" = d1."+Column.RESOURCE.getValue()+" AND d2.id <> d1.id"+
            " WHERE d1."+Column.QNAME.getValue()+" = ?"+
            " ORDER BY d2."+Column.QNAME.getValue();
            return db.rawQuery(query, new String[]{qname});
        } finally {
            lock.readLock().unlock();
        }
    }

    public Cursor getDns() {
        lock.readLock().lock();
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            // There is an index on resource
            // There is a segmented index on qname
            String query = "SELECT ID AS _id, *"+
            " FROM "+Table.DNS.getValue()+
            " ORDER BY "+Column.RESOURCE.getValue()+", "+Column.QNAME.getValue();
            return db.rawQuery(query, new String[]{});
        } finally {
            lock.readLock().unlock();
        }
    }

    public Cursor getAccessDns(String dname) {
        long now = new Date().getTime();
        lock.readLock().lock();
        try {
            SQLiteDatabase db = this.getReadableDatabase();

            // There is a segmented index on dns.qname
            // There is an index on access.daddr and access.block
            String query = "SELECT a."+Column.UID.getValue()+", a."+Column.VERSION.getValue()+", a."+Column.PROTOCOL.getValue()+", a."+Column.DADDR.getValue()+", d."+Column.RESOURCE.getValue()+", a."+Column.DPORT.getValue()+", a."+Column.BLOCK.getValue()+", d."+Column.TIME.getValue()+", d."+Column.TTL+
            " FROM "+Table.ACCESS.getValue()+" AS a"+
            " LEFT JOIN "+Table.DNS.getValue()+" AS d"+
            " ON d."+Column.QNAME.getValue()+" = a."+Column.DADDR.getValue()+
            " WHERE a."+Column.BLOCK.getValue()+" >= 0"+
            " AND (d."+Column.TIME.getValue()+" IS NULL OR d."+Column.TIME.getValue()+" + d."+Column.TTL+" >= " + now + ")";
            if (dname != null) query += " AND a."+Column.DADDR.getValue()+" = ?";

            return db.rawQuery(query, dname == null ? new String[]{} : new String[]{dname});
        } finally {
            lock.readLock().unlock();
        }
    }

    // Forward

    public void addForward(int protocol, int dport, String raddr, int rport, int ruid) {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                ContentValues cv = new ContentValues();
                cv.put(Column.PROTOCOL.getValue(), protocol);
                cv.put(Column.DPORT.getValue(), dport);
                cv.put(Column.RADDR.getValue(), raddr);
                cv.put(Column.RPORT.getValue(), rport);
                cv.put(Column.RUID.getValue(), ruid);

                if (db.insert(Table.FORWARD.getValue(), null, cv) < 0)
                    Log.e(TAG, "Insert forward failed");

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }

        notifyForwardChanged();
    }

    public void deleteForward() {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                db.delete(Table.FORWARD.getValue(), null, null);

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }

        notifyForwardChanged();
    }

    public void deleteForward(int protocol, int dport) {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                db.delete(Table.FORWARD.getValue(), Column.PROTOCOL.getValue() + " = ? AND " + Column.DPORT.getValue() + " = ?", new String[]{Integer.toString(protocol), Integer.toString(dport)});

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }

        notifyForwardChanged();
    }

    public Cursor getForwarding() {
        lock.readLock().lock();
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            String query = "SELECT ID AS _id, *"+
            " FROM "+Table.FORWARD.getValue()+
            " ORDER BY "+Column.DPORT.getValue();
            return db.rawQuery(query, new String[]{});
        } finally {
            lock.readLock().unlock();
        }
    }

    public void addApp(String packageName, String label, boolean system, boolean internet, boolean enabled) {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                ContentValues cv = new ContentValues();
                cv.put(Column.PACKAGE.getValue(), packageName);
                if (label == null)
                    cv.putNull(Column.LABEL.getValue());
                else
                    cv.put(Column.LABEL.getValue(), label);
                    cv.put(Column.SYSTEM.getValue(), system ? 1 : 0);
                    cv.put(Column.INTERNET.getValue(), internet ? 1 : 0);
                    cv.put(Column.ENABLED.getValue(), enabled ? 1 : 0);

                if (db.insert(Table.APP.getValue(), null, cv) < 0)
                    Log.e(TAG, "Insert app failed");

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Cursor getApp(String packageName) {
        lock.readLock().lock();
        try {
            SQLiteDatabase db = this.getReadableDatabase();

            // There is an index on package
            String query = "SELECT * FROM "+Table.APP.getValue()+
                    " WHERE "+Column.PACKAGE.getValue()+" = ?";

            return db.rawQuery(query, new String[]{packageName});
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearApps() {
        lock.writeLock().lock();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.beginTransactionNonExclusive();
            try {
                db.delete(Table.APP.getValue(), null, null);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addLogChangedListener(LogChangedListener listener) {
        logChangedListeners.add(listener);
    }

    public void removeLogChangedListener(LogChangedListener listener) {
        logChangedListeners.remove(listener);
    }

    public void addAccessChangedListener(AccessChangedListener listener) {
        accessChangedListeners.add(listener);
    }

    public void removeAccessChangedListener(AccessChangedListener listener) {
        accessChangedListeners.remove(listener);
    }

    public void addForwardChangedListener(ForwardChangedListener listener) {
        forwardChangedListeners.add(listener);
    }

    public void removeForwardChangedListener(ForwardChangedListener listener) {
        forwardChangedListeners.remove(listener);
    }

    private void notifyLogChanged() {
        Message msg = handler.obtainMessage();
        msg.what = MSG_LOG;
        handler.sendMessage(msg);
    }

    private void notifyAccessChanged() {
        Message msg = handler.obtainMessage();
        msg.what = MSG_ACCESS;
        handler.sendMessage(msg);
    }

    private void notifyForwardChanged() {
        Message msg = handler.obtainMessage();
        msg.what = MSG_FORWARD;
        handler.sendMessage(msg);
    }

    private static void handleChangedNotification(Message msg) {
        // Batch notifications
        try {
            Thread.sleep(1000);
            if (handler.hasMessages(msg.what))
                handler.removeMessages(msg.what);
        } catch (InterruptedException ignored) {
        }

        // Notify listeners
        if (msg.what == MSG_LOG) {
            for (LogChangedListener listener : logChangedListeners)
                try {
                    listener.onChanged();
                } catch (Throwable ex) {
                    Util.logException(TAG, ex);
                }

        } else if (msg.what == MSG_ACCESS) {
            for (AccessChangedListener listener : accessChangedListeners)
                try {
                    listener.onChanged();
                } catch (Throwable ex) {
                    Util.logException(TAG, ex);
                }

        } else if (msg.what == MSG_FORWARD) {
            for (ForwardChangedListener listener : forwardChangedListeners)
                try {
                    listener.onChanged();
                } catch (Throwable ex) {
                    Util.logException(TAG, ex);
                }
        }
    }

    public interface LogChangedListener {
        void onChanged();
    }

    public interface AccessChangedListener {
        void onChanged();
    }

    public interface ForwardChangedListener {
        void onChanged();
    }
}
