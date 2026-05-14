package codes.var.tweak.proxyswitcher;

final class VpnRuntimeState {
    private static final Object LOCK = new Object();
    private static boolean running;
    private static String endpoint;
    private static String lastError;
    private static int generation;

    private VpnRuntimeState() {
    }

    static void markStarting() {
        synchronized (LOCK) {
            running = false;
            endpoint = null;
            lastError = null;
            generation++;
            LOCK.notifyAll();
        }
    }

    static void markRunning(String endpointValue) {
        synchronized (LOCK) {
            running = true;
            endpoint = endpointValue;
            lastError = null;
            generation++;
            LOCK.notifyAll();
        }
    }

    static void markStopped() {
        synchronized (LOCK) {
            running = false;
            endpoint = null;
            lastError = null;
            generation++;
            LOCK.notifyAll();
        }
    }

    static void markError(String message) {
        synchronized (LOCK) {
            running = false;
            endpoint = null;
            lastError = message == null || message.trim().isEmpty() ? "Unable to start VPN proxy." : message.trim();
            generation++;
            LOCK.notifyAll();
        }
    }

    static RootProxyApplier.Result waitForStartResult(int baselineGeneration, long timeoutMs) {
        long end = System.currentTimeMillis() + Math.max(100, timeoutMs);
        synchronized (LOCK) {
            while (System.currentTimeMillis() < end) {
                if (running) {
                    return RootProxyApplier.Result.ok();
                }
                if (lastError != null && !lastError.isEmpty()) {
                    return RootProxyApplier.Result.error(lastError);
                }
                if (generation != baselineGeneration) {
                    if (running) {
                        return RootProxyApplier.Result.ok();
                    }
                    if (lastError != null && !lastError.isEmpty()) {
                        return RootProxyApplier.Result.error(lastError);
                    }
                }
                long waitMs = Math.min(120, Math.max(20, end - System.currentTimeMillis()));
                try {
                    LOCK.wait(waitMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return RootProxyApplier.Result.error("Interrupted while starting VPN.");
                }
            }
            if (running) {
                return RootProxyApplier.Result.ok();
            }
            return RootProxyApplier.Result.error(lastError == null ? "VPN did not start in time." : lastError);
        }
    }

    static int generation() {
        synchronized (LOCK) {
            return generation;
        }
    }

    static boolean isRunning() {
        synchronized (LOCK) {
            return running;
        }
    }

    static String endpoint() {
        synchronized (LOCK) {
            return endpoint;
        }
    }
}
