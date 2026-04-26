package com.botforge;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Sends a tiny UDP packet every 25 seconds.
 * This keeps the network stack active and prevents Android from
 * putting the process to sleep, giving bots true 24/7 uptime.
 */
public class UdpKeepAlive {

    private static final String TAG = "UdpKeepAlive";

    public static void ping(String host, int port) {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(3000);
                byte[] data    = "ping".getBytes();
                InetAddress addr = InetAddress.getByName(host);
                DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
                socket.send(packet);
                Log.d(TAG, "UDP ping sent → " + host + ":" + port);
            } catch (Exception e) {
                // Silent fail — ping is best-effort
                Log.w(TAG, "UDP ping failed: " + e.getMessage());
            }
        }, "udp-ping").start();
    }
}
