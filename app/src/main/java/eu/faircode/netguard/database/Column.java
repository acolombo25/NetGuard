package eu.faircode.netguard.database;

public enum Column {
    UID,
    VERSION,
    PROTOCOL,
    ANAME,
    DNAME,
    DADDR,
    DPORT,
    QNAME,
    RADDR,
    RPORT,
    RUID,
    SADDR,
    SPORT,
    TIME,
    BLOCK,
    ALLOWED,
    TTL,
    RESOURCE,

    PACKAGE,
    LABEL,
    SYSTEM,
    INTERNET,
    ENABLED,

    PORT,
    FLAGS,
    CONNECTION,
    INTERACTIVE,
    DATA,

    SENT,
    RECEIVED,
    CONNECTIONS,
    ;

    public String getValue() {
        return name().toLowerCase();
    }

    public enum Type {
        INTEGER, TEXT;

        public String getValue() {
            return name();
        }
    }
}
