package eu.faircode.netguard

sealed class Reason(val reason: String) {
    // Start
    object UI: Reason("UI")
    object Prepared : Reason("prepared")
    object Receiver : Reason("receiver")
    object CallState : Reason("call state")

    object Foreground : Reason("foreground")
    object Background : Reason("background")
    // Reload
    object HostsFileDownload : Reason("hosts file download")

    object Tile : Reason("tile")
    object Widget : Reason("widget")
    object Notification : Reason("notification")
    object Lockdown : Reason("lockdown")
    object Pull : Reason("pull")
    object Forwarding : Reason("forwarding")

    object InteractiveStateChanged : Reason("interactive state changed")
    object LinkPropertiesChanged : Reason("link properties changed")
    object DataConnectionStateChanged : Reason("data connection state changed")
    object ConnectivityChanged : Reason("connectivity changed")
    object ConnectedStateChanged : Reason("Connected state changed")
    object UnmeteredStateChanged : Reason("Unmetered state changed")
    object GenerationChanged : Reason("Generation changed")
    object IdleStateChanged : Reason("idle state changed")

    object NetworkAvailable : Reason("network available")
    object NetworkLost : Reason("network lost")
    object PackageAdded : Reason("package added")
    object PackageDeleted : Reason("package deleted")
    object PermissionGranted : Reason("permission granted")
    object HostsImport : Reason("hosts import")
    object ChangedNotify : Reason("changed notify")
    object ChangedFilter : Reason("changed filter")
    object AllowHost : Reason("allow host")
    object BlockHost : Reason("block host")
    object ResetHost : Reason("reset host")
    object RuleChanged : Reason("rule changed")
    object DNSCleanup : Reason("DNS cleanup")
    object DNSClear : Reason("DNS changcleared")

    class Changed(name: String) : Reason("changed $name")
    // Stop
    object Import : Reason("import")
    object SwitchOff : Reason("switch off")
}
