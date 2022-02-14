package eu.faircode.netguard.database;

public enum Index {
    IDX_ACCESS, IDX_ACCESS_BLOCK, IDX_ACCESS_DADDR,

    IDX_LOG, IDX_LOG_SOURCE, IDX_LOG_TIME, IDX_LOG_DEST, IDX_LOG_DNAME, IDX_LOG_DPORT, IDX_LOG_UID,

    IDX_DNS, IDX_DNS_RESOURCE,

    IDX_FORWARD,

    IDX_PACKAGE,
    ;

    public String getValue() {
        return name().toLowerCase();
    }

}
