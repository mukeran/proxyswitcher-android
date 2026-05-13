package codes.var.proxyswitcher;

import android.content.Context;
import android.net.IpConfiguration;
import android.net.ProxyInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Looper;

import java.lang.reflect.Method;
import java.util.List;

public final class RootWifiProxyTool {
    private RootWifiProxyTool() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "direct";
        String host = args.length > 1 ? args[1] : "";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 0;

        Context context = systemContext();
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            throw new IllegalStateException("WifiManager is unavailable.");
        }

        WifiInfo info = wifiManager.getConnectionInfo();
        int networkId = info == null ? -1 : info.getNetworkId();
        String ssid = info == null ? null : info.getSSID();
        WifiConfiguration configuration = currentConfiguration(wifiManager, networkId, ssid);
        if (configuration == null) {
            throw new IllegalStateException("Current Wi-Fi configuration was not found.");
        }

        WifiConfiguration updated = new WifiConfiguration(configuration);
        if ("direct".equals(mode)) {
            updated.setIpConfiguration(ipConfiguration(null));
            updated.setHttpProxy(null);
        } else {
            ProxyInfo proxyInfo = ProxyInfo.buildDirectProxy(host, port);
            updated.setIpConfiguration(ipConfiguration(proxyInfo));
            updated.setHttpProxy(proxyInfo);
        }

        int result = wifiManager.updateNetwork(updated);
        if (result < 0) {
            throw new IllegalStateException("WifiManager.updateNetwork failed.");
        }
        try {
            wifiManager.saveConfiguration();
        } catch (RuntimeException ignored) {
        }
        try {
            wifiManager.disconnect();
            Thread.sleep(300);
            wifiManager.reconnect();
        } catch (RuntimeException ignored) {
        }
    }

    private static Context systemContext() throws Exception {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Method systemMain = activityThreadClass.getDeclaredMethod("systemMain");
        Object activityThread = systemMain.invoke(null);
        Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
        return (Context) getSystemContext.invoke(activityThread);
    }

    private static WifiConfiguration currentConfiguration(WifiManager wifiManager, int networkId, String ssid) {
        List<WifiConfiguration> configurations = wifiManager.getConfiguredNetworks();
        if (configurations == null) {
            return null;
        }
        String normalizedSsid = normalizeSsid(ssid);
        for (WifiConfiguration configuration : configurations) {
            if (configuration.networkId == networkId) {
                return configuration;
            }
        }
        for (WifiConfiguration configuration : configurations) {
            if (normalizedSsid != null && normalizedSsid.equals(normalizeSsid(configuration.SSID))) {
                return configuration;
            }
        }
        return null;
    }

    private static String normalizeSsid(String value) {
        if (value == null || "<unknown ssid>".equals(value)) {
            return null;
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static IpConfiguration ipConfiguration(ProxyInfo proxyInfo) throws Exception {
        Class<?> ipConfigurationClass = Class.forName("android.net.IpConfiguration");
        Object ipConfiguration = ipConfigurationClass.getDeclaredConstructor().newInstance();
        setEnumField(ipConfiguration, "ipAssignment", "android.net.IpConfiguration$IpAssignment", "DHCP");
        setEnumField(ipConfiguration, "proxySettings", "android.net.IpConfiguration$ProxySettings",
                proxyInfo == null ? "NONE" : "STATIC");
        setField(ipConfiguration, "httpProxy", proxyInfo);
        return (IpConfiguration) ipConfiguration;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnumField(Object target, String fieldName, String enumClassName, String enumValue) throws Exception {
        Class enumClass = Class.forName(enumClassName);
        setField(target, fieldName, Enum.valueOf(enumClass, enumValue));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
