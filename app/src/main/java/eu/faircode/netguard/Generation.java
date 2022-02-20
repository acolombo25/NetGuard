package eu.faircode.netguard;

public enum Generation {
    Gen_2G("2G"),
    Gen_3G("3G"),
    Gen_4G("4G"),
    Gen_Unknown("?G"),
    ;

    private final String generation;

    Generation(String generation) {
        this.generation = generation;
    }

    public String getValue() {
        return generation;
    }

}
