package codes.var.proxyswitcher;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class RootProxyApplier {
    private static final String[] SU_CANDIDATES = {
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/debug_ramdisk/su",
            "/data/adb/magisk/su",
            "su"
    };

    static {
        Shell.enableVerboseLogging = false;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setCommands(findSuCommand())
                .setTimeout(10));
    }

    static final class Result {
        final boolean ok;
        final String message;

        private Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        static Result ok() {
            return new Result(true, "");
        }

        static Result error(String message) {
            return new Result(false, message);
        }
    }

    Result applyDirect() {
        return runAsRoot(wifiProxyScript(null, 0));
    }

    Result applyProfile(ProxyProfile profile) {
        if (profile == null) {
            return Result.error("Proxy profile not found.");
        }
        return runAsRoot(wifiProxyScript(profile.host, profile.port));
    }

    private Result runAsRoot(String script) {
        try {
            Shell shell = Shell.getShell();
            if (!shell.isRoot()) {
                return Result.error("Root permission was not granted. Please allow ProxySwitcher in your root manager.");
            }
            List<String> stdout = new ArrayList<>();
            List<String> stderr = new ArrayList<>();
            Shell.Result result = shell.newJob().add(script).to(stdout, stderr).exec();
            if (result.isSuccess()) {
                return Result.ok();
            }
            return Result.error(errorMessage(result, stdout, stderr));
        } catch (RuntimeException e) {
            return Result.error("Root shell is unavailable: " + e.getMessage());
        }
    }

    private static String errorMessage(Shell.Result result, List<String> stdout, List<String> stderr) {
        String output = join(stderr);
        if (output.isEmpty()) {
            output = join(stdout);
        }
        if (!output.isEmpty()) {
            return output;
        }
        return String.format(Locale.US, "Root command exited with %d.", result.getCode());
    }

    private static String[] findSuCommand() {
        for (String candidate : SU_CANDIDATES) {
            if ("su".equals(candidate) || new File(candidate).canExecute()) {
                return new String[]{candidate};
            }
        }
        return new String[]{"su"};
    }

    private static String wifiProxyScript(String host, int port) {
        boolean direct = host == null || host.isEmpty();
        String mode = direct ? "direct" : "static";
        String hostRaw = direct ? "" : host;
        String hostXml = direct ? "" : xmlEscape(host);
        return ""
                + "set -eu\n"
                + "mode=" + shellQuote(mode) + "\n"
                + "proxy_host_raw=" + shellQuote(hostRaw) + "\n"
                + "proxy_host=" + shellQuote(hostXml) + "\n"
                + "proxy_port=" + port + "\n"
                + "store=''\n"
                + "for p in "
                + "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml "
                + "/data/misc_ce/0/apexdata/com.android.wifi/WifiConfigStore.xml "
                + "/data/misc/wifi/WifiConfigStore.xml "
                + "/data/misc_ce/0/wifi/WifiConfigStore.xml; do\n"
                + "  if [ -f \"$p\" ] && grep -q '<Network>' \"$p\"; then store=\"$p\"; break; fi\n"
                + "done\n"
                + "[ -n \"$store\" ] || { echo 'Wi-Fi config store was not found.' >&2; exit 20; }\n"
                + "ssid=$(cmd wifi status 2>/dev/null | sed -n 's/^Wifi is connected to \"\\(.*\\)\".*$/\\1/p' | head -n 1)\n"
                + "[ -n \"$ssid\" ] || { echo 'No active Wi-Fi network was found.' >&2; exit 21; }\n"
                + "ssid_xml=$(printf '%s' \"$ssid\" | sed -e 's/&/\\&amp;/g' -e 's/\"/\\&quot;/g' -e \"s/'/\\&apos;/g\" -e 's/</\\&lt;/g' -e 's/>/\\&gt;/g')\n"
                + "target='<string name=\"SSID\">&quot;'\"$ssid_xml\"'&quot;</string>'\n"
                + "dir=${store%/*}\n"
                + "tmp=\"$store.proxyswitcher.tmp\"\n"
                + "bak=\"$store.proxyswitcher.bak\"\n"
                + "cp -p \"$store\" \"$bak\"\n"
                + "awk -v target=\"$target\" -v mode=\"$mode\" -v host=\"$proxy_host\" -v port=\"$proxy_port\" '\n"
                + "function emit_ip() {\n"
                + "  print \"<IpConfiguration>\"\n"
                + "  print \"<string name=\\\"IpAssignment\\\">DHCP</string>\"\n"
                + "  if (mode == \"direct\") {\n"
                + "    print \"<string name=\\\"ProxySettings\\\">NONE</string>\"\n"
                + "  } else {\n"
                + "    print \"<string name=\\\"ProxySettings\\\">STATIC</string>\"\n"
                + "    print \"<string name=\\\"ProxyHost\\\">\" host \"</string>\"\n"
                + "    print \"<int name=\\\"ProxyPort\\\" value=\\\"\" port \"\\\" />\"\n"
                + "    print \"<string name=\\\"ProxyExclusionList\\\"></string>\"\n"
                + "  }\n"
                + "  print \"</IpConfiguration>\"\n"
                + "}\n"
                + "function flush_network(   i, skipping, emitted) {\n"
                + "  if (!matched) {\n"
                + "    for (i = 1; i <= n; i++) print buf[i]\n"
                + "    return\n"
                + "  }\n"
                + "  found = 1; skipping = 0; emitted = 0\n"
                + "  for (i = 1; i <= n; i++) {\n"
                + "    if (buf[i] == \"<IpConfiguration>\") { emit_ip(); skipping = 1; emitted = 1; continue }\n"
                + "    if (skipping && buf[i] == \"</IpConfiguration>\") { skipping = 0; continue }\n"
                + "    if (skipping) continue\n"
                + "    if (!emitted && buf[i] == \"</Network>\") { emit_ip(); emitted = 1 }\n"
                + "    print buf[i]\n"
                + "  }\n"
                + "}\n"
                + "BEGIN { innet = 0; n = 0; matched = 0; found = 0 }\n"
                + "$0 == \"<Network>\" { innet = 1; n = 0; matched = 0 }\n"
                + "innet {\n"
                + "  buf[++n] = $0\n"
                + "  if ($0 == target) matched = 1\n"
                + "  if ($0 == \"</Network>\") { flush_network(); innet = 0; n = 0; matched = 0 }\n"
                + "  next\n"
                + "}\n"
                + "{ print }\n"
                + "END { if (found != 1) exit 42 }\n"
                + "' \"$store\" > \"$tmp\" || { rc=$?; rm -f \"$tmp\"; echo \"Unable to update Wi-Fi proxy for $ssid.\" >&2; exit $rc; }\n"
                + "chown system:system \"$tmp\" 2>/dev/null || true\n"
                + "chmod 600 \"$tmp\"\n"
                + "restorecon \"$tmp\" 2>/dev/null || true\n"
                + "mv \"$tmp\" \"$store\"\n"
                + "restorecon \"$store\" 2>/dev/null || true\n"
                + "if [ \"$mode\" = 'direct' ]; then\n"
                + "  settings put global http_proxy :0 >/dev/null 2>&1 || true\n"
                + "  settings delete global global_http_proxy_host >/dev/null 2>&1 || true\n"
                + "  settings delete global global_http_proxy_port >/dev/null 2>&1 || true\n"
                + "else\n"
                + "  settings put global http_proxy \"$proxy_host:$proxy_port\" >/dev/null 2>&1 || true\n"
                + "  settings put global global_http_proxy_host \"$proxy_host\" >/dev/null 2>&1 || true\n"
                + "  settings put global global_http_proxy_port \"$proxy_port\" >/dev/null 2>&1 || true\n"
                + "fi\n"
                + "apk_path=$(pm path codes.var.proxyswitcher 2>/dev/null | sed -n 's/^package://p' | head -n 1)\n"
                + "if [ -n \"$apk_path\" ]; then\n"
                + "  CLASSPATH=\"$apk_path\" app_process /system/bin codes.var.proxyswitcher.RootWifiProxyTool \"$mode\" \"$proxy_host_raw\" \"$proxy_port\" >/dev/null 2>&1 || true\n"
                + "fi\n"
                + "am force-stop com.android.settings >/dev/null 2>&1 || true\n"
                + "am force-stop com.google.android.settings.intelligence >/dev/null 2>&1 || true\n"
                + "echo \"Updated Wi-Fi proxy for $ssid\"\n";
    }

    private static String xmlEscape(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String join(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
