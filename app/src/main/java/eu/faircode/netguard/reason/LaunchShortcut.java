package eu.faircode.netguard.reason;

public class LaunchShortcut implements Reason {

    private final String packageName;

    public LaunchShortcut(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public String getReason() {
        return "shortcut launch" + packageName;
    }

    public String getPackageName() {
        return packageName;
    }
}
