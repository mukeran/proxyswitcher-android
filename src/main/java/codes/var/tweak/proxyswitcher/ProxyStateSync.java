package codes.var.tweak.proxyswitcher;

import java.util.List;

final class ProxyStateSync {
    private ProxyStateSync() {
    }

    static void applySnapshot(ProxyStore store,
                              List<String> ssids,
                              List<String> hints,
                              String currentSsid,
                              String currentProxy) {
        if (store == null) {
            return;
        }
        if (ssids != null && hints != null) {
            store.setWiFiProxyHints(ssids, hints);
        }
        if (currentSsid != null && !currentSsid.isEmpty()) {
            store.setCurrentWiFiSSID(currentSsid);
        }
        syncActiveProfileWithSystemProxy(store, currentProxy);
    }

    static void syncActiveProfileWithSystemProxy(ProxyStore store, String proxyHint) {
        if (store == null) {
            return;
        }
        String normalized = proxyHint == null || proxyHint.isEmpty() ? "Direct" : proxyHint.trim();
        if ("Direct".equalsIgnoreCase(normalized)) {
            store.setActiveIdentifier(ProxyStore.DIRECT_IDENTIFIER);
            return;
        }
        Endpoint endpoint = Endpoint.parse(normalized);
        if (endpoint == null) {
            return;
        }
        List<ProxyProfile> profiles = store.profiles();
        for (ProxyProfile profile : profiles) {
            if (endpoint.host.equalsIgnoreCase(profile.host) && endpoint.port == profile.port) {
                store.setActiveIdentifier(profile.identifier);
                return;
            }
        }
        ProxyProfile temporary = ProxyProfile.temporary(endpoint.host, endpoint.port);
        store.setTemporaryProfile(temporary);
    }

    private static final class Endpoint {
        final String host;
        final int port;

        private Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }

        static Endpoint parse(String value) {
            if (value == null) {
                return null;
            }
            int idx = value.lastIndexOf(':');
            if (idx <= 0 || idx >= value.length() - 1) {
                return null;
            }
            String host = value.substring(0, idx).trim();
            String portString = value.substring(idx + 1).trim();
            if (host.isEmpty() || portString.isEmpty()) {
                return null;
            }
            try {
                int port = Integer.parseInt(portString);
                if (port < 1 || port > 65535) {
                    return null;
                }
                return new Endpoint(host, port);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
