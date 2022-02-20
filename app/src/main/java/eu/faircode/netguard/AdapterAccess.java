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

import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;

import java.net.InetAddress;
import java.net.UnknownHostException;

import eu.faircode.netguard.database.Column;
import eu.faircode.netguard.format.DateFormats;

public class AdapterAccess extends CursorAdapter {
    private final int colVersion;
    private final int colProtocol;
    private final int colDaddr;
    private final int colDPort;
    private final int colTime;
    private final int colAllowed;
    private final int colBlock;
    private final int colCount;
    private final int colSent;
    private final int colReceived;
    private final int colConnections;

    private final int colorText;
    private final int colorOn;
    private final int colorOff;

    public AdapterAccess(Context context, Cursor cursor) {
        super(context, cursor, 0);
        colVersion = cursor.getColumnIndex(Column.VERSION.getValue());
        colProtocol = cursor.getColumnIndex(Column.PROTOCOL.getValue());
        colDaddr = cursor.getColumnIndex(Column.DADDR.getValue());
        colDPort = cursor.getColumnIndex(Column.DPORT.getValue());
        colTime = cursor.getColumnIndex(Column.TIME.getValue());
        colAllowed = cursor.getColumnIndex(Column.ALLOWED.getValue());
        colBlock = cursor.getColumnIndex(Column.BLOCK.getValue());
        colCount = cursor.getColumnIndex(Column.COUNT.getValue());
        colSent = cursor.getColumnIndex(Column.SENT.getValue());
        colReceived = cursor.getColumnIndex(Column.RECEIVED.getValue());
        colConnections = cursor.getColumnIndex(Column.CONNECTIONS.getValue());

        TypedArray ta = context.getTheme().obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
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
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.access, parent, false);
    }

    @Override
    public void bindView(final View view, final Context context, final Cursor cursor) {
        // Get values
        final int version = cursor.getInt(colVersion);
        final int protocol = cursor.getInt(colProtocol);
        final String daddr = cursor.getString(colDaddr);
        final int dport = cursor.getInt(colDPort);
        long time = cursor.getLong(colTime);
        int allowed = cursor.getInt(colAllowed);
        int block = cursor.getInt(colBlock);
        int count = cursor.getInt(colCount);
        long sent = cursor.isNull(colSent) ? -1 : cursor.getLong(colSent);
        long received = cursor.isNull(colReceived) ? -1 : cursor.getLong(colReceived);
        int connections = cursor.isNull(colConnections) ? -1 : cursor.getInt(colConnections);

        // Get views
        TextView tvTime = view.findViewById(R.id.tvTime);
        ImageView ivBlock = view.findViewById(R.id.ivBlock);
        final TextView tvDest = view.findViewById(R.id.tvDest);
        LinearLayout llTraffic = view.findViewById(R.id.llTraffic);
        TextView tvConnections = view.findViewById(R.id.tvConnections);
        TextView tvTraffic = view.findViewById(R.id.tvTraffic);

        // Set values
        tvTime.setText(DateFormats.DAY_TIME.format(time));
        if (block < 0)
            ivBlock.setImageDrawable(null);
        else {
            ivBlock.setImageResource(block > 0 ? R.drawable.ic_close : R.drawable.ic_check);
        }

        String dest = Util.getProtocolName(protocol, version, true) +
                " " + daddr + (dport > 0 ? "/" + dport : "") + (count > 1 ? " ?" + count : "");
        SpannableString span = new SpannableString(dest);
        span.setSpan(new UnderlineSpan(), 0, dest.length(), 0);
        tvDest.setText(span);

        if (Util.isNumericAddress(daddr))
            new AsyncTask<String, Object, String>() {
                @Override
                protected void onPreExecute() {
                    ViewCompat.setHasTransientState(tvDest, true);
                }

                @Override
                protected String doInBackground(String... args) {
                    try {
                        return InetAddress.getByName(args[0]).getHostName();
                    } catch (UnknownHostException ignored) {
                        return args[0];
                    }
                }

                @Override
                protected void onPostExecute(String addr) {
                    tvDest.setText(
                            Util.getProtocolName(protocol, version, true) +
                                    " >" + addr + (dport > 0 ? "/" + dport : ""));
                    ViewCompat.setHasTransientState(tvDest, false);
                }
            }.execute(daddr);

        if (allowed < 0)
            tvDest.setTextColor(colorText);
        else if (allowed > 0)
            tvDest.setTextColor(colorOn);
        else
            tvDest.setTextColor(colorOff);

        llTraffic.setVisibility(connections > 0 || sent > 0 || received > 0 ? View.VISIBLE : View.GONE);
        if (connections > 0)
            tvConnections.setText(context.getString(R.string.msg_count, connections));

        if (sent > 1024 * 1204 * 1024L || received > 1024 * 1024 * 1024L)
            tvTraffic.setText(context.getString(R.string.msg_gb,
                    (sent > 0 ? sent / (1024 * 1024 * 1024f) : 0),
                    (received > 0 ? received / (1024 * 1024 * 1024f) : 0)));
        else if (sent > 1204 * 1024L || received > 1024 * 1024L)
            tvTraffic.setText(context.getString(R.string.msg_mb,
                    (sent > 0 ? sent / (1024 * 1024f) : 0),
                    (received > 0 ? received / (1024 * 1024f) : 0)));
        else
            tvTraffic.setText(context.getString(R.string.msg_kb,
                    (sent > 0 ? sent / 1024f : 0),
                    (received > 0 ? received / 1024f : 0)));
    }
}
