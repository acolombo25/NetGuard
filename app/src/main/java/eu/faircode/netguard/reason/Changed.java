package eu.faircode.netguard.reason;

public class Changed implements Reason {

    private final String change;

    public Changed(String change) {
        this.change = change;
    }

    @Override
    public String getReason() {
        return "changed" + change;
    }
}
