package eu.faircode.netguard;

public enum Theme {
    Teal, Blue, Purple, Amber, Orange, Green;
    public String getValue() {
        return this.name().toLowerCase();
    }
}
