package eu.faircode.netguard.preference;

public class Preference<T> {
    private final String key;
    private final T defaultValue;

    public Preference(String key, T defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public T getDefaultValue() {
        return defaultValue;
    }
}
