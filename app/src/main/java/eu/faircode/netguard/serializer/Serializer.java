package eu.faircode.netguard.serializer;

public interface Serializer {
    String OUTPUT = "UTF-8";
    String FEATURE = "http://xmlpull.org/v1/doc/features.html#indent-output";

    interface Tag {
        String SETTING = "setting";
    }

    interface Attribute {
        String KEY = "key";
        String TYPE = "type";
        String VALUE = "value";
    }
}
