package codes.var.proxyswitcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

final class ProxyStore {
    static final String DIRECT_IDENTIFIER = "direct";
    static final String ACTION_CHANGED = "codes.var.proxyswitcher.PROFILES_CHANGED";

    private static final String PREFS = "codes.var.proxyswitcher";
    private static final String PROFILES = "profiles";
    private static final String ACTIVE_IDENTIFIER = "ActiveIdentifier";
    private static final String LAST_ACTIVE_PROFILE_IDENTIFIER = "LastActiveProfileIdentifier";

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
        return preferences.getString(ACTIVE_IDENTIFIER, DIRECT_IDENTIFIER);
    }

    String lastActiveProfileIdentifier() {
        return preferences.getString(LAST_ACTIVE_PROFILE_IDENTIFIER, null);
    }

    void setActiveIdentifier(String identifier) {
        SharedPreferences.Editor editor = preferences.edit()
                .putString(ACTIVE_IDENTIFIER, identifier == null ? DIRECT_IDENTIFIER : identifier);
        if (identifier != null && !DIRECT_IDENTIFIER.equals(identifier)) {
            editor.putString(LAST_ACTIVE_PROFILE_IDENTIFIER, identifier);
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

    private JSONArray parseProfiles() {
        try {
            return new JSONArray(preferences.getString(PROFILES, "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private void notifyChanged() {
        Intent intent = new Intent(ACTION_CHANGED).setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
