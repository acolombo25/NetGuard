package eu.faircode.netguard;

public enum NotificationChannels {
    Foreground,
    Notify,
    Access,
    ;

    public String getValue() {
        return this.name().toLowerCase();
    }
}
