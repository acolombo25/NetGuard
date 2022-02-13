package eu.faircode.netguard

sealed class Reason(val reason: String) {
    object HostsFileDownload : Reason("hosts file download")
    object Tile : Reason("tile")
    object Widget : Reason("widget")
    object InteractiveStateChanged : Reason("interactive state changed")
    object NetworkAvailable : Reason("network available")
    object LinkPropertiesChanged : Reason("link properties changed")
    object Forwarding : Reason("forwarding")
    object ConnectivityChanged : Reason("connectivity changed")
    class Changed(name: String) : Reason("changed $name")
}
