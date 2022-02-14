package eu.faircode.netguard.preference;

public enum Sort {
    Name, Uid, Data;

    public String getValue() {
        return this.name().toLowerCase();
    }
}
