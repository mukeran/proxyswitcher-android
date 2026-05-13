package codes.var.proxyswitcher;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

final class ProxyProfile {
    static final int DEFAULT_PORT = 8080;

    final String identifier;
    String name;
    String host;
    int port;
    String username;
    String password;

    ProxyProfile() {
        identifier = UUID.randomUUID().toString();
        name = "Proxy";
        host = "";
        port = DEFAULT_PORT;
    }

    private ProxyProfile(String identifier) {
        this.identifier = identifier == null || identifier.isEmpty()
                ? UUID.randomUUID().toString()
                : identifier;
    }

    String endpoint() {
        return host + ":" + port;
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("identifier", identifier);
        object.put("name", name == null || name.isEmpty() ? "Proxy" : name);
        object.put("host", host == null ? "" : host);
        object.put("port", port);
        if (username != null && !username.isEmpty()) {
            object.put("username", username);
        }
        if (password != null && !password.isEmpty()) {
            object.put("password", password);
        }
        return object;
    }

    static ProxyProfile fromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        ProxyProfile profile = new ProxyProfile(object.optString("identifier", null));
        profile.name = object.optString("name", "Proxy");
        profile.host = object.optString("host", "");
        profile.port = object.optInt("port", DEFAULT_PORT);
        profile.username = emptyToNull(object.optString("username", null));
        profile.password = emptyToNull(object.optString("password", null));
        if (profile.host.isEmpty() || profile.port < 1 || profile.port > 65535) {
            return null;
        }
        return profile;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
