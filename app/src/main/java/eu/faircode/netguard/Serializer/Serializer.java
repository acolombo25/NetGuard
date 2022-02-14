package eu.faircode.netguard.Serializer;

public interface Serializer {
    interface Tag {
        String SETTING = "setting";
    }

    interface Attribute {
        String KEY = "key";
        String TYPE = "type";
        String VALUE = "value";
    }
}
