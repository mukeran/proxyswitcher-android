package codes.var.tweak.proxyswitcher;

import android.os.Handler;

final class ProxyInteractionFlow {
    static final long SNAPSHOT_DELAY_MS = 500L;

    private static final long APPLY_TIMEOUT_MS = 2500L;
    private static final long WIFI_TIMEOUT_MS = 3000L;
    private static final long WIFI_RETRY_1_MS = 1200L;
    private static final long WIFI_RETRY_2_MS = 2200L;

    interface OperationState {
        boolean inProgress();
    }

    private ProxyInteractionFlow() {
    }

    static void onApplyRequested(Handler handler,
                                 Runnable requestSnapshotDelayed,
                                 OperationState state,
                                 Runnable clearProgress) {
        requestSnapshotDelayed.run();
        handler.postDelayed(() -> {
            if (state.inProgress()) {
                clearProgress.run();
            }
        }, APPLY_TIMEOUT_MS);
    }

    static void onWiFiSwitchRequested(Handler handler,
                                      Runnable requestSnapshotDelayed,
                                      Runnable requestSnapshotNow,
                                      OperationState state,
                                      Runnable clearProgress) {
        requestSnapshotDelayed.run();
        handler.postDelayed(() -> {
            if (state.inProgress()) {
                requestSnapshotNow.run();
            }
        }, WIFI_RETRY_1_MS);
        handler.postDelayed(() -> {
            if (state.inProgress()) {
                requestSnapshotNow.run();
            }
        }, WIFI_RETRY_2_MS);
        handler.postDelayed(() -> {
            if (state.inProgress()) {
                clearProgress.run();
            }
        }, WIFI_TIMEOUT_MS);
    }
}
