package codes.var.tweak.proxyswitcher;

final class ProxyActions {
    static final String ACTION_APPLY = "codes.var.tweak.proxyswitcher.action.APPLY_PROXY";
    static final String ACTION_SWITCH_WIFI = "codes.var.tweak.proxyswitcher.action.SWITCH_WIFI";
    static final String ACTION_LIST_WIFI = "codes.var.tweak.proxyswitcher.action.LIST_WIFI";
    static final String ACTION_STATUS = "codes.var.tweak.proxyswitcher.action.STATUS";
    static final String ACTION_STATUS_RESULT = "codes.var.tweak.proxyswitcher.action.STATUS_RESULT";
    static final String ACTION_VPN_STATE_CHANGED = "codes.var.tweak.proxyswitcher.action.VPN_STATE_CHANGED";
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_HOST = "host";
    static final String EXTRA_PORT = "port";
    static final String EXTRA_NO_PROXY = "no_proxy";
    static final String EXTRA_SSID = "ssid";
    static final String EXTRA_WIFI_LIST = "wifi_list";
    static final String EXTRA_WIFI_PROXY_LIST = "wifi_proxy_list";
    static final String EXTRA_CURRENT_SSID = "current_ssid";
    static final String EXTRA_CURRENT_PROXY = "current_proxy";
    static final String EXTRA_READY = "ready";
    static final String EXTRA_REQUEST_TOKEN = "request_token";
    static final String EXTRA_PROCESS = "process";
    static final String EXTRA_REPLY_PACKAGE = "reply_package";
    static final String EXTRA_VPN_RUNNING = "vpn_running";
    static final String EXTRA_VPN_ENDPOINT = "vpn_endpoint";
    static final String MODE_DIRECT = "direct";
    static final String MODE_STATIC = "static";

    private ProxyActions() {
    }
}
