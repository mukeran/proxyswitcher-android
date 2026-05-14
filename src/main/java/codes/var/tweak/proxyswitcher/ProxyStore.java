package codes.var.tweak.proxyswitcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ProxyStore {
    static final String DIRECT_IDENTIFIER = "direct";
    static final String TEMPORARY_IDENTIFIER = "temporary";
    static final String ACTION_CHANGED = "codes.var.tweak.proxyswitcher.PROFILES_CHANGED";

    private static final String PREFS = "codes.var.tweak.proxyswitcher";
    private static final String PROFILES = "profiles";
    private static final String ACTIVE_IDENTIFIER = "ActiveIdentifier";
    private static final String LAST_ACTIVE_PROFILE_IDENTIFIER = "LastActiveProfileIdentifier";
    private static final String TEMPORARY_PROFILE = "TemporaryProfile";
    private static final String LAST_TEMPORARY_PROFILE = "LastTemporaryProfile";
    private static final String QUICK_WIFI_SSIDS = "QuickWiFiSSIDs";
    private static final String WIFI_PROXY_HINTS = "WiFiProxyHints";
    private static final String CURRENT_WIFI_SSID = "CurrentWiFiSSID";

    private final Context context;
    private final SharedPreferences preferences;

    ProxyStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<ProxyProfile> profiles() {
        ArrayList<ProxyProfile> result = new ArrayList<>();
        JSONArray array = parseProfiles();
        for (int index = 0; index < array.length(); index++) {
            ProxyProfile profile = ProxyProfile.fromJson(array.optJSONObject(index));
            if (profile != null) {
                result.add(profile);
            }
        }
        return result;
    }

    ProxyProfile profileWithIdentifier(String identifier) {
        if (TEMPORARY_IDENTIFIER.equals(identifier)) {
            return temporaryProfile();
        }
        for (ProxyProfile profile : profiles()) {
            if (profile.identifier.equals(identifier)) {
                return profile;
            }
        }
        return null;
    }

    void saveProfile(ProxyProfile profile) {
        JSONArray existing = parseProfiles();
        JSONArray updated = new JSONArray();
        boolean replaced = false;
        for (int index = 0; index < existing.length(); index++) {
            ProxyProfile current = ProxyProfile.fromJson(existing.optJSONObject(index));
            if (current == null) {
                continue;
            }
            try {
                updated.put(current.identifier.equals(profile.identifier) ? profile.toJson() : current.toJson());
                replaced = replaced || current.identifier.equals(profile.identifier);
            } catch (JSONException ignored) {
            }
        }
        if (!replaced) {
            try {
                updated.put(profile.toJson());
            } catch (JSONException ignored) {
            }
        }
        preferences.edit().putString(PROFILES, updated.toString()).apply();
        notifyChanged();
    }

    void deleteProfile(String identifier) {
        JSONArray existing = parseProfiles();
        JSONArray updated = new JSONArray();
        for (int index = 0; index < existing.length(); index++) {
            ProxyProfile profile = ProxyProfile.fromJson(existing.optJSONObject(index));
            if (profile == null || profile.identifier.equals(identifier)) {
                continue;
            }
            try {
                updated.put(profile.toJson());
            } catch (JSONException ignored) {
            }
        }
        SharedPreferences.Editor editor = preferences.edit().putString(PROFILES, updated.toString());
        if (identifier.equals(activeIdentifier())) {
            editor.putString(ACTIVE_IDENTIFIER, DIRECT_IDENTIFIER);
        }
        editor.apply();
        notifyChanged();
    }

    String activeIdentifier() {
        String value = preferences.getString(ACTIVE_IDENTIFIER, DIRECT_IDENTIFIER);
        return value == null ? DIRECT_IDENTIFIER : value;
    }

    String lastActiveProfileIdentifier() {
        return preferences.getString(LAST_ACTIVE_PROFILE_IDENTIFIER, null);
    }

    void setActiveIdentifier(String identifier) {
        SharedPreferences.Editor editor = preferences.edit()
                .putString(ACTIVE_IDENTIFIER, identifier == null ? DIRECT_IDENTIFIER : identifier);
        if (identifier != null
                && !DIRECT_IDENTIFIER.equals(identifier)
                && !TEMPORARY_IDENTIFIER.equals(identifier)) {
            editor.putString(LAST_ACTIVE_PROFILE_IDENTIFIER, identifier);
        }
        if (identifier == null || !TEMPORARY_IDENTIFIER.equals(identifier)) {
            editor.remove(TEMPORARY_PROFILE);
        }
        editor.apply();
        notifyChanged();
    }

    String firstOrLastProfileIdentifier() {
        String last = lastActiveProfileIdentifier();
        if (last != null && profileWithIdentifier(last) != null) {
            return last;
        }
        List<ProxyProfile> profiles = profiles();
        return profiles.isEmpty() ? null : profiles.get(0).identifier;
    }

    ProxyProfile temporaryProfile() {
        return ProxyProfile.fromJson(parseObject(TEMPORARY_PROFILE));
    }

    ProxyProfile lastTemporaryProfile() {
        return ProxyProfile.fromJson(parseObject(LAST_TEMPORARY_PROFILE));
    }

    void setTemporaryProfile(ProxyProfile profile) {
        SharedPreferences.Editor editor = preferences.edit();
        if (profile == null) {
            editor.remove(TEMPORARY_PROFILE);
        } else {
            try {
                profile.name = "Temporary";
                editor.putString(TEMPORARY_PROFILE, profile.toJson().toString());
                editor.putString(LAST_TEMPORARY_PROFILE, profile.toJson().toString());
                editor.putString(ACTIVE_IDENTIFIER, TEMPORARY_IDENTIFIER);
            } catch (JSONException ignored) {
            }
        }
        editor.apply();
        notifyChanged();
    }

    void clearTemporaryProfile() {
        SharedPreferences.Editor editor = preferences.edit()
                .remove(TEMPORARY_PROFILE)
                .remove(LAST_TEMPORARY_PROFILE);
        if (TEMPORARY_IDENTIFIER.equals(activeIdentifier())) {
            editor.putString(ACTIVE_IDENTIFIER, DIRECT_IDENTIFIER);
        }
        editor.apply();
        notifyChanged();
    }

    String currentWiFiSSID() {
        return preferences.getString(CURRENT_WIFI_SSID, "");
    }

    void setCurrentWiFiSSID(String ssid) {
        preferences.edit().putString(CURRENT_WIFI_SSID, ssid == null ? "" : ssid).apply();
        notifyChanged();
    }

    List<String> quickWiFiSSIDs() {
        String raw = preferences.getString(QUICK_WIFI_SSIDS, "[]");
        ArrayList<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String ssid = array.optString(i, "");
                if (!ssid.isEmpty() && !result.contains(ssid)) {
                    result.add(ssid);
                }
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    void addQuickWiFiSSID(String ssid) {
        if (ssid == null || ssid.trim().isEmpty()) {
            return;
        }
        String normalized = ssid.trim();
        ArrayList<String> items = new ArrayList<>(quickWiFiSSIDs());
        if (!items.contains(normalized)) {
            items.add(normalized);
            saveQuickWiFiSSIDs(items);
        }
    }

    void deleteQuickWiFiSSID(String ssid) {
        if (ssid == null || ssid.trim().isEmpty()) {
            return;
        }
        String normalized = ssid.trim();
        ArrayList<String> items = new ArrayList<>(quickWiFiSSIDs());
        if (items.remove(normalized)) {
            saveQuickWiFiSSIDs(items);
        }
    }

    void setWiFiProxyHints(List<String> ssids, List<String> hints) {
        if (ssids == null || hints == null) {
            return;
        }
        JSONObject object = parseObject(WIFI_PROXY_HINTS);
        if (object == null) {
            object = new JSONObject();
        }
        for (int i = 0; i < ssids.size(); i++) {
            String ssid = ssids.get(i);
            if (ssid == null || ssid.trim().isEmpty()) {
                continue;
            }
            String hint = i < hints.size() ? hints.get(i) : "Direct";
            try {
                object.put(ssid.trim(), hint == null || hint.isEmpty() ? "Direct" : hint);
            } catch (JSONException ignored) {
            }
        }
        preferences.edit().putString(WIFI_PROXY_HINTS, object.toString()).apply();
        notifyChanged();
    }

    String wiFiProxyHint(String ssid) {
        if (ssid == null || ssid.trim().isEmpty()) {
            return "Direct";
        }
        JSONObject object = parseObject(WIFI_PROXY_HINTS);
        if (object == null) {
            return "Direct";
        }
        String value = object.optString(ssid.trim(), "Direct");
        return value == null || value.isEmpty() ? "Direct" : value;
    }

    String nextIdentifierAfterActive() {
        List<ProxyProfile> profiles = profiles();
        if (profiles.isEmpty()) {
            return DIRECT_IDENTIFIER;
        }
        String active = activeIdentifier();
        if (DIRECT_IDENTIFIER.equals(active)) {
            return profiles.get(0).identifier;
        }
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).identifier.equals(active)) {
                return i + 1 < profiles.size() ? profiles.get(i + 1).identifier : DIRECT_IDENTIFIER;
            }
        }
        return profiles.get(0).identifier;
    }

    String diagnosticsSummary() {
        return "SSID=" + currentWiFiSSID()
                + ", active=" + activeIdentifier()
                + ", profiles=" + profiles().size()
                + ", quickWiFi=" + quickWiFiSSIDs().size();
    }

    private JSONArray parseProfiles() {
        try {
            return new JSONArray(preferences.getString(PROFILES, "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private JSONObject parseObject(String key) {
        String raw = preferences.getString(key, null);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(raw);
        } catch (JSONException ignored) {
            return null;
        }
    }

    private void saveQuickWiFiSSIDs(List<String> items) {
        JSONArray array = new JSONArray();
        for (String item : items) {
            if (item != null && !item.isEmpty()) {
                array.put(item);
            }
        }
        preferences.edit().putString(QUICK_WIFI_SSIDS, array.toString()).apply();
        notifyChanged();
    }

    private void notifyChanged() {
        Intent intent = new Intent(ACTION_CHANGED).setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
