package eu.faircode.netguard.format;

import android.content.Context;

import java.io.File;
import java.util.Date;

import eu.faircode.netguard.R;

public class Files {

    public enum Format {
        Pcap,
        Xml,
        ;

        public String getValue() {
            return "." + name().toLowerCase();
        }
    }

    public final static String FILE_TEXT_XML = "*/*"; // text/xml

    public static final String FILE_HOSTS = "hosts.txt";
    public static final String FILE_HOSTS_TMP = "hosts.tmp";

    public static final String URL_HOSTS = "https://www.netguard.me/hosts"; /// id

    public static File getPcapFile(Context context) {
        return new File(context.getDir("data", Context.MODE_PRIVATE), context.getString(R.string.app_name) + Format.Pcap.getValue());
    }

    public static String getFileName(Context context, Format format) {
        return context.getString(R.string.app_name) + "_" +
                DateFormats.STANDARD.format(new Date()) +
                format.getValue();
    }
}
