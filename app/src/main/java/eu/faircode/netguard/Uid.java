package eu.faircode.netguard;

public enum Uid {
    Root(0, "root", "root"),
    Media(1013, "android.media", "mediaserver"),
    Multicast(1020, "android.multicast", "multicast"),
    Gps(1021, "android.gps", "gps"),
    Dns(1051, "android.dns", "dns"),
    Nobody(9999, "nobody", "nobody"),
    ;

    public static final int USER_FACTOR = 100000;

    private final int code;
    private final String packageName;
    private final String id;

    Uid(int code, String packageName, String id) {
        this.code = code;
        this.packageName = packageName;
        this.id = id;
    }

    public int getCode() {
        return code;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getId() {
        return id;
    }
}
