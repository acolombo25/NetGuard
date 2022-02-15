package eu.faircode.netguard.format;

public enum Format {
    Pcap,
    Xml,
    ;

    public String getValue() {
        return "." + name().toLowerCase();
    }
}
