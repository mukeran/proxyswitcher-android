package codes.var.tweak.proxyswitcher.lsposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.IpConfiguration;
import android.net.LinkProperties;
import android.net.ProxyInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class ProxySwitcherModule implements IXposedHookLoadPackage {
    private static final String ACTION_APPLY = "codes.var.tweak.proxyswitcher.action.APPLY_PROXY";
    private static final String ACTION_SWITCH_WIFI = "codes.var.tweak.proxyswitcher.action.SWITCH_WIFI";
    private static final String ACTION_LIST_WIFI = "codes.var.tweak.proxyswitcher.action.LIST_WIFI";
    private static final String ACTION_STATUS = "codes.var.tweak.proxyswitcher.action.STATUS";
    private static final String ACTION_STATUS_RESULT = "codes.var.tweak.proxyswitcher.action.STATUS_RESULT";
    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_HOST = "host";
    private static final String EXTRA_PORT = "port";
    private static final String EXTRA_NO_PROXY = "no_proxy";
    private static final String EXTRA_SSID = "ssid";
    private static final String EXTRA_WIFI_LIST = "wifi_list";
    private static final String EXTRA_WIFI_PROXY_LIST = "wifi_proxy_list";
    private static final String EXTRA_CURRENT_SSID = "current_ssid";
    private static final String EXTRA_CURRENT_PROXY = "current_proxy";
    private static final String EXTRA_READY = "ready";
    private static final String EXTRA_REQUEST_TOKEN = "request_token";
    private static final String EXTRA_PROCESS = "process";
    private static final String EXTRA_REPLY_PACKAGE = "reply_package";
    private static final String MODE_DIRECT = "direct";
    private static final String MODE_STATIC = "static";
    private static final String WIFI_SERVICE = "com.android.server.wifi.WifiServiceImpl";
    private static final String CONNECTIVITY_SERVICE =
            "android.net.connectivity.com.android.server.ConnectivityService";
    private static final String LEGACY_CONNECTIVITY_SERVICE = "com.android.server.ConnectivityService";
    private static final String SYSTEM_SERVICE_MANAGER = "com.android.server.SystemServiceManager";

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean CLASS_LOAD_HOOKED = new AtomicBoolean(false);
    private static final AtomicBoolean SYSTEM_SERVICE_MANAGER_HOOKED = new AtomicBoolean(false);
    private static final AtomicBoolean WIFI_HOOKED = new AtomicBoolean(false);
    private static final AtomicBoolean CONNECTIVITY_HOOKED = new AtomicBoolean(false);
    private static volatile boolean hasOverride = false;
    private static volatile ProxyInfo currentProxy = null;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log("ProxySwitcher: loaded in android process=" + lpparam.processName);
        hookSystemServiceManager(lpparam.classLoader);
        hookWifiService(lpparam.classLoader);
        hookConnectivityService(lpparam.classLoader);
        hookDeferredSystemServiceClasses();
    }

    private void hookSystemServiceManager(ClassLoader classLoader) {
        Class<?> systemServiceManager = XposedHelpers.findClassIfExists(SYSTEM_SERVICE_MANAGER, classLoader);
        if (systemServiceManager == null || !SYSTEM_SERVICE_MANAGER_HOOKED.compareAndSet(false, true)) {
            return;
        }
        XposedBridge.log("ProxySwitcher: hook SystemServiceManager");
        XposedBridge.hookAllConstructors(systemServiceManager, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Context context = findContext(param.args);
                if (context == null) {
                    context = (Context) getFieldIfExists(param.thisObject, "mContext");
                }
                if (context != null) {
                    registerReceiver(context);
                }
            }
        });
        XposedBridge.hookAllMethods(systemServiceManager, "startService", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                hookSystemServiceResult(param);
            }
        });
        XposedBridge.hookAllMethods(systemServiceManager, "startServiceFromJar", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                hookSystemServiceResult(param);
            }
        });
    }

    private static void hookSystemServiceResult(XC_MethodHook.MethodHookParam param) {
        Object result = param.getResult();
        if (result == null) {
            return;
        }
        ClassLoader classLoader = result.getClass().getClassLoader();
        if (classLoader == null) {
            return;
        }
        hookWifiService(classLoader);
        hookConnectivityService(classLoader);
    }

    private static void hookWifiService(ClassLoader classLoader) {
        Class<?> wifiService = findClassAnywhere(WIFI_SERVICE, classLoader);
        if (wifiService == null) {
            return;
        }
        hookWifiService(wifiService);
    }

    private static void hookWifiService(Class<?> wifiService) {
        if (!WIFI_HOOKED.compareAndSet(false, true)) {
            return;
        }
        XposedBridge.log("ProxySwitcher: hook " + wifiService.getName());
        XposedBridge.hookAllConstructors(wifiService, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Context context = findContext(param.args);
                if (context != null) {
                    registerReceiver(context);
                }
            }
        });
    }

    private static void hookConnectivityService(ClassLoader classLoader) {
        Class<?> connectivityService = findClassAnywhere(CONNECTIVITY_SERVICE, classLoader);
        if (connectivityService == null) {
            connectivityService = findClassAnywhere(LEGACY_CONNECTIVITY_SERVICE, classLoader);
        }
        if (connectivityService == null) {
            return;
        }
        hookConnectivityService(connectivityService);
    }

    private static void hookConnectivityService(Class<?> connectivityService) {
        if (!CONNECTIVITY_HOOKED.compareAndSet(false, true)) {
            return;
        }
        XposedBridge.log("ProxySwitcher: hook " + connectivityService.getName());

        XposedBridge.hookAllConstructors(connectivityService, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Context context = findContext(param.args);
                if (context == null) {
                    context = (Context) getFieldIfExists(param.thisObject, "mContext");
                }
                if (context != null) {
                    registerReceiver(context);
                }
            }
        });

        XposedBridge.hookAllMethods(connectivityService, "getProxyForNetwork", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (hasOverride) {
                    param.setResult(currentProxy);
                }
            }
        });

        XposedBridge.hookAllMethods(connectivityService, "getLinkProperties", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!hasOverride) {
                    return;
                }
                Object result = param.getResult();
                if (result instanceof LinkProperties) {
                    ((LinkProperties) result).setHttpProxy(currentProxy);
                }
            }
        });

        XposedBridge.hookAllMethods(connectivityService, "getGlobalProxy", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (hasOverride) {
                    param.setResult(currentProxy);
                }
            }
        });
    }

    private static void hookDeferredSystemServiceClasses() {
        if (!CLASS_LOAD_HOOKED.compareAndSet(false, true)) {
            return;
        }
        XposedBridge.hookAllMethods(ClassLoader.class, "loadClass", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                hookLoadedClass(param);
            }
        });
        try {
            Class<?> baseDexClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
            XposedBridge.hookAllMethods(baseDexClassLoader, "findClass", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    hookLoadedClass(param);
                }
            });
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
        XposedBridge.log("ProxySwitcher: deferred ClassLoader hook installed");
    }

    private static void hookLoadedClass(XC_MethodHook.MethodHookParam param) {
        Object nameArg = param.args == null || param.args.length == 0 ? null : param.args[0];
        if (!(nameArg instanceof String)) {
            return;
        }
        String name = (String) nameArg;
        if (!WIFI_SERVICE.equals(name)
                && !CONNECTIVITY_SERVICE.equals(name)
                && !LEGACY_CONNECTIVITY_SERVICE.equals(name)) {
            return;
        }
        Object result = param.getResult();
        if (!(result instanceof Class<?>)) {
            return;
        }
        Class<?> loadedClass = (Class<?>) result;
        if (WIFI_SERVICE.equals(name)) {
            hookWifiService(loadedClass);
        } else {
            hookConnectivityService(loadedClass);
        }
    }

    private static Class<?> findClassAnywhere(String name, ClassLoader preferredClassLoader) {
        Class<?> result = XposedHelpers.findClassIfExists(name, preferredClassLoader);
        if (result != null) {
            return result;
        }
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null && contextClassLoader != preferredClassLoader) {
            result = XposedHelpers.findClassIfExists(name, contextClassLoader);
            if (result != null) {
                return result;
            }
        }
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void registerReceiver(Context context) {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_APPLY);
        filter.addAction(ACTION_SWITCH_WIFI);
        filter.addAction(ACTION_LIST_WIFI);
        filter.addAction(ACTION_STATUS);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                if (ACTION_APPLY.equals(action)) {
                    apply(receiverContext, intent);
                    return;
                }
                if (ACTION_SWITCH_WIFI.equals(action)) {
                    switchWifi(receiverContext, intent);
                    return;
                }
                if (ACTION_LIST_WIFI.equals(action)) {
                    sendWifiList(receiverContext, intent);
                    return;
                }
                if (ACTION_STATUS.equals(action)) {
                    sendStatus(receiverContext, intent);
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
        XposedBridge.log("ProxySwitcher: LSPosed receiver registered");
    }

    private static void apply(Context context, Intent intent) {
        String mode = intent.getStringExtra(EXTRA_MODE);
        String host = intent.getStringExtra(EXTRA_HOST);
        int port = intent.getIntExtra(EXTRA_PORT, 0);
        String noProxyCsv = intent.getStringExtra(EXTRA_NO_PROXY);
        List<String> exclusionList = parseNoProxyList(noProxyCsv);
        ProxyInfo proxy = null;
        if (MODE_STATIC.equals(mode)) {
            proxy = ProxyInfo.buildDirectProxy(host, port, exclusionList);
        } else if (!MODE_DIRECT.equals(mode)) {
            return;
        }

        hasOverride = true;
        currentProxy = proxy;
        updateGlobalProxy(context, proxy);
        updateWifiConfiguration(context, proxy);
        forceStopSettings(context);
        XposedBridge.log("ProxySwitcher: proxy apply ok");
    }

    private static void switchWifi(Context context, Intent intent) {
        String targetSsid = normalizeSsid(intent == null ? null : intent.getStringExtra(EXTRA_SSID));
        if (targetSsid == null || targetSsid.isEmpty()) {
            XposedBridge.log("ProxySwitcher: switch wifi skipped, empty ssid");
            return;
        }
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                XposedBridge.log("ProxySwitcher: switch wifi failed, no WifiManager");
                return;
            }
            WifiConfiguration config = currentConfiguration(wifiManager, -1, targetSsid);
            if (config == null) {
                XposedBridge.log("ProxySwitcher: switch wifi target not found: " + targetSsid);
                return;
            }

            boolean switched = connectViaHiddenApis(context, wifiManager, config, targetSsid);
            if (!switched) {
                switched = connectViaLegacyApis(wifiManager, config.networkId);
            }
            XposedBridge.log("ProxySwitcher: switch wifi " + (switched ? "ok" : "failed")
                    + " ssid=" + targetSsid + " netId=" + config.networkId);
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }

    private static void sendWifiList(Context context, Intent request) {
        WifiSnapshot snapshot = listConfiguredSsidsWithProxy(context);
        ArrayList<String> ssids = snapshot.ssids;
        Intent response = new Intent(ACTION_LIST_WIFI);
        String replyPackage = request == null ? null : request.getStringExtra(EXTRA_REPLY_PACKAGE);
        String token = request == null ? null : request.getStringExtra(EXTRA_REQUEST_TOKEN);
        if (replyPackage == null || replyPackage.isEmpty()) {
            replyPackage = context.getPackageName();
        }
        response.setPackage(replyPackage);
        response.putStringArrayListExtra(EXTRA_WIFI_LIST, ssids);
        response.putStringArrayListExtra(EXTRA_WIFI_PROXY_LIST, snapshot.proxyHints);
        response.putExtra(EXTRA_CURRENT_SSID, snapshot.currentSsid);
        response.putExtra(EXTRA_CURRENT_PROXY, snapshot.currentProxy);
        if (token != null && !token.isEmpty()) {
            response.putExtra(EXTRA_REQUEST_TOKEN, token);
        }
        context.sendBroadcast(response);
        XposedBridge.log("ProxySwitcher: wifi list size=" + ssids.size());
    }

    private static void sendStatus(Context context, Intent request) {
        String token = request == null ? null : request.getStringExtra(EXTRA_REQUEST_TOKEN);
        String replyPackage = request == null ? null : request.getStringExtra(EXTRA_REPLY_PACKAGE);
        if (replyPackage == null || replyPackage.isEmpty()) {
            replyPackage = context.getPackageName();
        }
        Intent response = new Intent(ACTION_STATUS_RESULT)
                .setPackage(replyPackage)
                .putExtra(EXTRA_READY, true)
                .putExtra(EXTRA_PROCESS, "android");
        if (token != null && !token.isEmpty()) {
            response.putExtra(EXTRA_REQUEST_TOKEN, token);
        }
        context.sendBroadcast(response);
    }

    private static ArrayList<String> listConfiguredSsids(Context context) {
        return listConfiguredSsidsWithProxy(context).ssids;
    }

    private static final class WifiSnapshot {
        final ArrayList<String> ssids = new ArrayList<>();
        final ArrayList<String> proxyHints = new ArrayList<>();
        String currentSsid = "";
        String currentProxy = "Direct";
    }

    private static WifiSnapshot listConfiguredSsidsWithProxy(Context context) {
        WifiSnapshot result = new WifiSnapshot();
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                return result;
            }
            List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
            if (configs == null) {
                return result;
            }
            Map<String, String> proxyBySsid = new HashMap<>();
            for (WifiConfiguration config : configs) {
                String ssid = normalizeSsid(config == null ? null : config.SSID);
                if (ssid == null || ssid.isEmpty() || result.ssids.contains(ssid)) {
                    continue;
                }
                result.ssids.add(ssid);
                proxyBySsid.put(ssid, proxyHint(config));
            }
            for (String ssid : result.ssids) {
                String hint = proxyBySsid.get(ssid);
                result.proxyHints.add((hint == null || hint.isEmpty() ? "Direct" : hint));
            }
            WifiInfo info = wifiManager.getConnectionInfo();
            String connectedSsid = normalizeSsid(info == null ? null : info.getSSID());
            result.currentSsid = connectedSsid == null ? "" : connectedSsid;
            if (connectedSsid != null && proxyBySsid.containsKey(connectedSsid)) {
                String hint = proxyBySsid.get(connectedSsid);
                result.currentProxy = hint == null || hint.isEmpty() ? "Direct" : hint;
            }
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
        return result;
    }

    private static String proxyHint(WifiConfiguration configuration) {
        if (configuration == null) {
            return "Direct";
        }
        try {
            ProxyInfo proxyInfo = configuration.getHttpProxy();
            if (proxyInfo == null || proxyInfo.getHost() == null || proxyInfo.getHost().isEmpty()) {
                return "Direct";
            }
            return proxyInfo.getHost() + ":" + proxyInfo.getPort();
        } catch (Throwable ignored) {
            return "Direct";
        }
    }

    private static boolean connectViaHiddenApis(Context context,
                                                WifiManager wifiManager,
                                                WifiConfiguration config,
                                                String targetSsid) {
        Object service = getFieldIfExists(wifiManager, "mService");
        if (service == null) {
            return false;
        }
        String opPkg = context.getOpPackageName();
        String attribution = Build.VERSION.SDK_INT >= 30 ? context.getAttributionTag() : null;
        Object callback = null;

        if (invokeIfExists(service, "connect",
                new Class[]{WifiConfiguration.class, int.class, Object.class, String.class, String.class},
                new Object[]{config, -1, callback, opPkg, attribution})) {
            return true;
        }
        if (invokeIfExists(service, "connect",
                new Class[]{WifiConfiguration.class, int.class, Object.class, String.class},
                new Object[]{config, -1, callback, opPkg})) {
            return true;
        }
        if (invokeIfExists(service, "connect",
                new Class[]{int.class, Object.class, String.class, String.class},
                new Object[]{config.networkId, callback, opPkg, attribution})) {
            return true;
        }
        if (invokeIfExists(service, "connect",
                new Class[]{int.class, Object.class, String.class},
                new Object[]{config.networkId, callback, opPkg})) {
            return true;
        }

        return false;
    }

    private static boolean connectViaLegacyApis(WifiManager wifiManager, int networkId) {
        boolean enabled = false;
        boolean reconnected = false;
        try {
            enabled = wifiManager.enableNetwork(networkId, true);
        } catch (Throwable ignored) {
        }
        try {
            reconnected = wifiManager.reconnect();
        } catch (Throwable ignored) {
        }
        return enabled || reconnected;
    }

    private static boolean invokeIfExists(Object target,
                                          String methodName,
                                          Class<?>[] parameterTypes,
                                          Object[] args) {
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            method.invoke(target, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void updateGlobalProxy(Context context, ProxyInfo proxy) {
        if (proxy == null) {
            Settings.Global.putString(context.getContentResolver(), "http_proxy", ":0");
            Settings.Global.putString(context.getContentResolver(), "global_http_proxy_host", null);
            Settings.Global.putString(context.getContentResolver(), "global_http_proxy_port", null);
        } else {
            Settings.Global.putString(context.getContentResolver(), "http_proxy", proxy.getHost() + ":" + proxy.getPort());
            Settings.Global.putString(context.getContentResolver(), "global_http_proxy_host", proxy.getHost());
            Settings.Global.putString(context.getContentResolver(), "global_http_proxy_port", String.valueOf(proxy.getPort()));
        }
    }

    private static void updateWifiConfiguration(Context context, ProxyInfo proxy) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return;
        }
        WifiInfo info = wifiManager.getConnectionInfo();
        int networkId = info == null ? -1 : info.getNetworkId();
        String ssid = info == null ? null : info.getSSID();
        WifiConfiguration configuration = currentConfiguration(wifiManager, networkId, ssid);
        if (configuration == null) {
            return;
        }
        WifiConfiguration updated = new WifiConfiguration(configuration);
        try {
            updated.setIpConfiguration(ipConfiguration(proxy));
            updated.setHttpProxy(proxy);
            wifiManager.updateNetwork(updated);
            try {
                wifiManager.saveConfiguration();
            } catch (RuntimeException ignored) {
            }
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
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
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
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

    private static Context findContext(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Context) {
                return (Context) arg;
            }
        }
        return null;
    }

    private static Object getFieldIfExists(Object target, String name) {
        try {
            return XposedHelpers.getObjectField(target, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void forceStopSettings(Context context) {
        try {
            context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        } catch (Throwable ignored) {
        }
    }

    private static List<String> parseNoProxyList(String csv) {
        ArrayList<String> list = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) {
            return list;
        }
        String[] pieces = csv.split(",");
        for (String piece : pieces) {
            String item = piece == null ? "" : piece.trim();
            if (!item.isEmpty() && !list.contains(item)) {
                list.add(item);
            }
        }
        return list;
    }
}
