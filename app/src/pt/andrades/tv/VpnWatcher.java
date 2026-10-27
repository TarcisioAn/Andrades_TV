package pt.andrades.tv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;

/**
 * Sabe se há uma VPN ligada na TV (NordVPN ou qualquer outra que use o VpnService do Android)
 * e avisa no thread principal sempre que isso muda.
 */
final class VpnWatcher {
    interface Listener {
        void onVpnChanged(boolean on);
    }

    private final ConnectivityManager cm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Network> vpns = new HashSet<>();
    private final Listener listener;
    private ConnectivityManager.NetworkCallback callback;

    VpnWatcher(Context c, Listener listener) {
        this.cm = c.getSystemService(ConnectivityManager.class);
        this.listener = listener;
    }

    boolean isOn() {
        synchronized (vpns) {
            return !vpns.isEmpty();
        }
    }

    void start() {
        if (callback != null || cm == null) return;
        NetworkRequest req = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) {
                synchronized (vpns) { vpns.add(n); }
                post();
            }

            @Override public void onLost(Network n) {
                synchronized (vpns) { vpns.remove(n); }
                post();
            }
        };
        try {
            cm.registerNetworkCallback(req, callback);
        } catch (RuntimeException e) {
            callback = null;
        }
        post();
    }

    void stop() {
        if (callback == null) return;
        try {
            cm.unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
        }
        callback = null;
        synchronized (vpns) { vpns.clear(); }
    }

    private void post() {
        main.post(() -> listener.onVpnChanged(isOn()));
    }
}
