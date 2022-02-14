package eu.faircode.netguard.Serializer;

public enum SerializerType {
    Boolean, Integer, String, Set;

    public String getValue() {
        return this.name().toLowerCase();
    }
}

