package eu.faircode.netguard.format;

import android.annotation.SuppressLint;

import java.text.SimpleDateFormat;

@SuppressLint("SimpleDateFormat")
public class DateFormats {
    public static final SimpleDateFormat DAY_TIME = new SimpleDateFormat("dd HH:mm");
    public static final SimpleDateFormat STANDARD = new SimpleDateFormat("yyyyMMdd");
}
