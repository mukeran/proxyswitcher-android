package codes.var.proxyswitcher;

import android.annotation.TargetApi;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@TargetApi(Build.VERSION_CODES.N)
public final class ProxyTileService extends TileService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        executor.execute(() -> {
            ProxyStore store = new ProxyStore(this);
            String active = store.activeIdentifier();
            RootProxyApplier applier = new RootProxyApplier();
            RootProxyApplier.Result result;
            String nextIdentifier;
            if (ProxyStore.DIRECT_IDENTIFIER.equals(active)) {
                nextIdentifier = store.firstOrLastProfileIdentifier();
                result = nextIdentifier == null
                        ? RootProxyApplier.Result.error("Add a proxy profile first.")
                        : applier.applyProfile(store.profileWithIdentifier(nextIdentifier));
            } else {
                nextIdentifier = ProxyStore.DIRECT_IDENTIFIER;
                result = applier.applyDirect();
            }
            if (result.ok) {
                store.setActiveIdentifier(nextIdentifier);
            }
            updateTile();
        });
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        ProxyStore store = new ProxyStore(this);
        String active = store.activeIdentifier();
        boolean direct = ProxyStore.DIRECT_IDENTIFIER.equals(active);
        tile.setState(direct ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.setLabel(direct ? "Direct" : activeProfileName(store, active));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(direct ? "No HTTP proxy" : "HTTP proxy active");
        }
        tile.updateTile();
    }

    private String activeProfileName(ProxyStore store, String identifier) {
        ProxyProfile profile = store.profileWithIdentifier(identifier);
        return profile == null ? "ProxySwitcher" : profile.name;
    }
}
