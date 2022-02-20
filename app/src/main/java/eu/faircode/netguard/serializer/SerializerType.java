package eu.faircode.netguard.serializer;

public enum SerializerType {
    Boolean, Integer, String, Set;

    public String getValue() {
        return this.name().toLowerCase();
    }
}

