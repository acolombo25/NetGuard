package eu.faircode.netguard.reason;

public enum SimpleReason implements Reason {
    // Start
    UI("UI"),
    Prepared("prepared"),
    Receiver("receiver"),
    CallState("call state"),
    Foreground("foreground"),
    Background("background"),
    // Reload
    HostsFileDownload("hosts file download"),
    Tile("tile"),
    Widget("widget"),
    Notification("notification"),
    Lockdown("lockdown"),
    Pull("pull"),
    Forwarding("forwarding"),
    InteractiveStateChanged("interactive state changed"),
    LinkPropertiesChanged("link properties changed"),
    DataConnectionStateChanged("data connection state changed"),
    ConnectivityChanged("connectivity changed"),
    ConnectedStateChanged("Connected state changed"),
    UnmeteredStateChanged("Unmetered state changed"),
    GenerationChanged("Generation changed"),
    IdleStateChanged("idle state changed"),
    NetworkAvailable("network available"),
    NetworkLost("network lost"),
    PackageAdded("package added"),
    PackageDeleted("package deleted"),
    PermissionGranted("permission granted"),
    HostsImport("hosts import"),
    ChangedNotify("changed notify"),
    ChangedFilter("changed filter"),
    AllowHost("allow host"),
    BlockHost("block host"),
    ResetHost("reset host"),
    RuleChanged("rule changed"),
    DNSCleanup("DNS cleanup"),
    DNSClear("DNS changcleared"),
    // Stop,
    Import("import"),
    SwitchOff("switch off"),
    ;

    private final String reason;

    SimpleReason(String reason) {
        this.reason = reason;
    }
    
    @Override
    public String getReason() {
        return reason;
    }
}
