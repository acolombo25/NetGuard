package eu.faircode.netguard;

import android.util.Log;

import java.util.HashSet;
import java.util.Set;

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

public interface Preferences {

    Preference<Boolean> ENABLED = new Preference<>("enabled", false); ///
    Preference<Boolean> INITIALIZED = new Preference<>("initialized", false); ///
    Preference<Boolean> LOCKDOWN = new Preference<>("lockdown", false); ///

    Preference<Boolean> LOCKDOWN_WIFI = new Preference<>("lockdown_wifi", true); /// id
    Preference<Boolean> LOCKDOWN_OTHER = new Preference<>("lockdown_other", true); /// id

    Preference<Boolean> HINT_USAGE = new Preference<>("hint_usage", true); ///
    Preference<Boolean> HINT_FAIR_EMAIL = new Preference<>("hint_fairemail", true); ///
    Preference<Boolean> HINT_WHITELIST = new Preference<>("hint_whitelist", true); ///
    Preference<Boolean> HINT_SYSTEM = new Preference<>("hint_system", true); ///
    Preference<Boolean> HINT_PUSH = new Preference<>("hint_push", true); ///

    Preference<Boolean> DARK = new Preference<>("dark_theme", false); /// id
    Preference<Theme> THEME = new Preference<>("theme", Theme.Teal); /// id
    Preference<Boolean> INSTALL = new Preference<>("install", false); /// mmmm
    Preference<Boolean> SHOW_STATS = new Preference<>("show_stats", false); ///
    Preference<Boolean> LOG = new Preference<>("log", false); ///
    Preference<Boolean> LOG_APP = new Preference<>("log_app", false); ///
    Preference<Boolean> TRACK_USAGE = new Preference<>("track_usage", false); ///
    Preference<Integer> SCREEN_DELAY = new Preference<>("screen_delay", 0); ///
    Preference<Integer> AUTO_ENABLE = new Preference<>("auto_enable", 0); ///
    Preference<Set<String>> WIFI_HOMES = new Preference<>("wifi_homes", new HashSet<>()); ///

    Preference<Boolean> WHITELIST_WIFI = new Preference<>("whitelist_wifi", true); ///
    Preference<Boolean> SCREEN_ON = new Preference<>("screen_on", true); ///
    Preference<Boolean> SCREEN_WIFI = new Preference<>("screen_wifi", false); ///
    Preference<Boolean> WHITELIST_OTHER = new Preference<>("whitelist_other", true); ///
    Preference<Boolean> SCREEN_OTHER = new Preference<>("screen_other", false); ///
    Preference<Boolean> WHITELIST_ROAMING = new Preference<>("whitelist_roaming", true); ///
    Preference<Boolean> NOTIFY_ACCESS = new Preference<>("notify_access", false); ///
    Preference<Boolean> MANAGE_SYSTEM = new Preference<>("manage_system", false); ///
    Preference<Boolean> FILTER_UDP = new Preference<>("filter_udp", true); ///

    Preference<Boolean> SUBNET = new Preference<>("subnet", false); ///
    Preference<Boolean> TETHERING = new Preference<>("tethering", false); ///
    Preference<Boolean> LAN = new Preference<>("lan", false); ///
    Preference<Boolean> IP6 = new Preference<>("ip6", true); ///

    Preference<String> DNS1 = new Preference<>("dns", "-"); ///
    Preference<String> DNS2 = new Preference<>("dns2", "-"); ///
    Preference<String> VPN4 = new Preference<>("vpn4", "10.1.10.1"); ///
    Preference<String> VPN6 = new Preference<>("vpn6", "fd00:1:fd00:1:fd00:1:fd00:1"); ///
    Preference<String> VALIDATE = new Preference<>("validate", "www.google.com"); ///
    Preference<String> TTL = new Preference<>("ttl", "259200"); ///
    Preference<Integer> R_CODE = new Preference<>("rcode", 3); /// id
    Preference<Integer> LOG_LEVEL = new Preference<>("loglevel", Log.WARN); /// id

    Preference<Boolean> SHOW_TOP = new Preference<>("show_top", false); /// id
    Preference<Boolean> SOCKS_5_ENABLED = new Preference<>("socks5_enabled", false); /// id
    Preference<String> SOCKS_5_ADDR = new Preference<>("socks5_addr", "-"); /// id
    Preference<String> SOCKS_5_PORT = new Preference<>("socks5_port", "-"); /// id
    Preference<String> SOCKS_5_USERNAME = new Preference<>("socks5_username", "-"); /// id
    Preference<String> SOCKS_5_PASSWORD = new Preference<>("socks5_password", ""); /// id

    Preference<Boolean> USE_HOSTS = new Preference<>("use_hosts", false); /// id
    Preference<Boolean> UPDATE_CHECK = new Preference<>("update_check", true); /// id
    Preference<String> HOSTS_URL = new Preference<>("hosts_url", null); /// id

    Preference<Boolean> USE_METERED = new Preference<>("use_metered", false); /// id
    Preference<Boolean> UNMETERED_2G = new Preference<>("unmetered_2g", false); /// id
    Preference<Boolean> UNMETERED_3G = new Preference<>("unmetered_3g", false); /// id
    Preference<Boolean> UNMETERED_4G = new Preference<>("unmetered_4g", false); /// id
    Preference<Boolean> NATIONAL = new Preference<>("national_roaming", false); /// id
    Preference<Boolean> EU = new Preference<>("eu_roaming", false); /// id

    Preference<Boolean> DISABLE_ON_CALL = new Preference<>("disable_on_call", false); /// id

    Preference<Boolean> SHOW_USER = new Preference<>("show_user", true); ///
    Preference<Boolean> SHOW_SYSTEM = new Preference<>("show_system", false); ///
    Preference<Boolean> SHOW_NO_INTERNET = new Preference<>("show_nointernet", true); ///
    Preference<Boolean> SHOW_DISABLED = new Preference<>("show_disabled", true); ///

    Preference<Sort> SORT = new Preference<>("sort", Sort.Name); /// "data" ???
    Preference<Boolean> IMPORTED = new Preference<>("imported", true); ///

    Preference<Boolean> FILTER = new Preference<>("filter", false); /// id

    Preference<Boolean> NOTIFY = new Preference<>("notify", false); ///
    Preference<Boolean> ROAMING = new Preference<>("roaming", false); //
    Preference<Boolean> WIFI = new Preference<>("wifi", false); ///
    Preference<Boolean> MOBILE = new Preference<>("mobile", false); ///
    Preference<Boolean> OTHER = new Preference<>("other", false); ///
    Preference<Boolean> APPLY = new Preference<>("apply", false); ///

    Preference<Boolean> NO_DOZE = new Preference<>("nodoze", false); ///
    Preference<Boolean> NO_DATA = new Preference<>("nodata", false); ///

    Preference<Boolean> UNUSED = new Preference<>("unused", false); ///

    Preference<Boolean> PROTO_UDP = new Preference<>("proto_udp", true); ///
    Preference<Boolean> PROTO_TCP = new Preference<>("proto_tcp", true); ///
    Preference<Boolean> PROTO_OTHER = new Preference<>("proto_other", true); ///
    Preference<Boolean> TRAFFIC_ALLOWED = new Preference<>("traffic_allowed", true); ///
    Preference<Boolean> TRAFFIC_BLOCKED = new Preference<>("traffic_blocked", true); ///

    Preference<Boolean> RESOLVE = new Preference<>("resolve", false); ///
    Preference<Boolean> ORGANIZATION = new Preference<>("organization", false); ///
    Preference<Boolean> PCAP = new Preference<>("pcap", false); ///

    Preference<String> HOSTS_LAST_IMPORT = new Preference<>("hosts_last_import", null);
    Preference<String> HOSTS_LAST_DOWNLOAD = new Preference<>("hosts_last_download", null);

    Preference<Boolean> RELOAD_CONNECTIVITY = new Preference<>("reload_onconnectivity", false);
    Preference<Boolean> FORWARDING = new Preference<>("forwarding", false); /// id
}

public enum Sort {
    Name, Uid, Data;
    public String getValue() {
        return this.name().toLowerCase();
    }
}
