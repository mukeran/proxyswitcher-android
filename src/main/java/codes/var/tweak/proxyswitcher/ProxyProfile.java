package codes.var.tweak.proxyswitcher;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ProxyProfile {
    static final int DEFAULT_PORT = 8080;
    static final String TEMPORARY_IDENTIFIER = "temporary";

    final String identifier;
    String name;
    String host;
    int port;
    String username;
    String password;
    List<String> noProxy;

    ProxyProfile() {
        identifier = UUID.randomUUID().toString();
        name = "Proxy";
        host = "";
        port = DEFAULT_PORT;
        noProxy = new ArrayList<>();
    }

    private ProxyProfile(String identifier) {
        this.identifier = identifier == null || identifier.isEmpty()
                ? UUID.randomUUID().toString()
                : identifier;
    }

    String endpoint() {
        return host + ":" + port;
    }

    static ProxyProfile temporary(String host, int port) {
        ProxyProfile profile = new ProxyProfile(TEMPORARY_IDENTIFIER);
        profile.name = "Temporary";
        profile.host = host == null ? "" : host;
        profile.port = port;
        profile.username = null;
        profile.password = null;
        profile.noProxy = new ArrayList<>();
        return profile;
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
        if (noProxy != null && !noProxy.isEmpty()) {
            JSONArray array = new JSONArray();
            for (String item : noProxy) {
                if (item != null && !item.isEmpty()) {
                    array.put(item);
                }
            }
            if (array.length() > 0) {
                object.put("noProxy", array);
            }
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
        profile.noProxy = normalizeNoProxy(object.optJSONArray("noProxy"));
        if (profile.host.isEmpty() || profile.port < 1 || profile.port > 65535) {
            return null;
        }
        return profile;
    }

    String noProxyCsv() {
        if (noProxy == null || noProxy.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < noProxy.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(noProxy.get(index));
        }
        return builder.toString();
    }

    static List<String> parseNoProxy(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String[] pieces = value.split(",");
        ArrayList<String> result = new ArrayList<>();
        for (String piece : pieces) {
            String trimmed = piece == null ? "" : piece.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static List<String> normalizeNoProxy(JSONArray array) {
        if (array == null || array.length() == 0) {
            return new ArrayList<>();
        }
        ArrayList<String> result = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            String item = emptyToNull(array.optString(index, null));
            if (item != null && !result.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
