package eu.faircode.netguard.database;

public enum Table {
    LOG,
    ACCESS,
    DNS,
    FORWARD,
    APP,
    ;

    public String getValue() {
        return name().toLowerCase();
    }
}
