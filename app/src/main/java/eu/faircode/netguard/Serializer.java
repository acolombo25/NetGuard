package eu.faircode.netguard;

public interface Serializer {
    interface  Tag {
        public String SETTING = "setting";
    }
    interface Attribute {
        public String KEY = "key";
        public String TYPE = "type";
        public String VALUE = "value";
    }
}

public enum SerializerType {
    Boolean, Integer, String, Set;
    public String getValue() {
        return this.name().toLowerCase();
    }
}