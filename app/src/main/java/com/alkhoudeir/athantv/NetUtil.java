package com.alkhoudeir.athantv;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/** Finds the TV's address on the home network (no permission needed). */
final class NetUtil {

    private NetUtil() {}

    static String localIpv4() {
        String fallback = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    String ip = a.getHostAddress();
                    String name = ni.getName();
                    if (a.isSiteLocalAddress() && (name.startsWith("wlan") || name.startsWith("eth"))) {
                        return ip;
                    }
                    if (fallback == null && a.isSiteLocalAddress()) fallback = ip;
                }
            }
        } catch (Exception ignored) {
            // No network.
        }
        return fallback;
    }
}
