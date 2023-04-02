package com.netbyte.vtunnel.service;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.netbyte.vtunnel.activity.MainActivity;
import com.netbyte.vtunnel.R;
import com.netbyte.vtunnel.model.Config;
import com.netbyte.vtunnel.model.Global;
import com.netbyte.vtunnel.thread.VpnThread;
import com.netbyte.vtunnel.model.Const;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MyVpnService extends VpnService {
    private Config config;
    private PendingIntent pendingIntent;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private IpService ipService;
    private final IntentFilter filter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    private final BroadcastReceiver airplaneModeOnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                if (Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0) {
                    stopVpn();
                }
            }
        }
    };

    public MyVpnService() {
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    @Override
    public void onCreate() {
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        registerReceiver(airplaneModeOnReceiver, filter);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        switch (intent.getAction()) {
            case Const.BTN_ACTION_CONNECT:
                // init config
                initConfig();
                // create notification
                createNotification();
                // start VPN
                startVpn();
                return START_STICKY;
            case Const.BTN_ACTION_DISCONNECT:
                // stop VPN
                stopVpn();
                return START_NOT_STICKY;
            default:
                return START_NOT_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        super.unregisterReceiver(airplaneModeOnReceiver);
    }

    @Override
    public void onRevoke() {
        stopVpn();
    }

    private void initConfig() {
        SharedPreferences preferences = this.getSharedPreferences(Const.APP_NAME, Activity.MODE_PRIVATE);
        String server = preferences.getString("server", Const.DEFAULT_SERVER_ADDRESS);
        String path = Const.DEFAULT_PATH;
        String dns = preferences.getString("dns", Const.DEFAULT_DNS);
        String key = preferences.getString("key", Const.DEFAULT_KEY);
        String bypassApps = preferences.getString("bypass_apps", "");
        String obfs = preferences.getString("obfuscation", "off");
        String proto = preferences.getString("proto", "wss");
        String serverAddress;
        int serverPort;
        if (server.contains(":")) {
            serverAddress = server.split(":")[0];
            serverPort = Integer.parseInt(server.split(":")[1]);
        } else {
            serverAddress = server;
            serverPort = Const.DEFAULT_SERVER_PORT;
        }
        this.config = new Config(serverAddress, serverPort, path, dns, key,  obfs, proto,bypassApps);
        this.ipService = new IpService(config.getServerAddress(), config.getServerPort(), config.getKey(), Objects.equals(proto,"wss"));
        Log.i(Const.DEFAULT_TAG, config.toString());
    }

    private void createNotification() {
        NotificationChannel channel = new NotificationChannel(Const.NOTIFICATION_CHANNEL_ID, Const.NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_NONE);
        notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
        notificationBuilder = new NotificationCompat.Builder(this, channel.getId());
        notificationBuilder.setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true);
    }

    public void startVpn() {
        try {
            if (Global.RUNNING) {
                stopVpn();
                TimeUnit.SECONDS.sleep(3);
            }
            Global.RUNNING = true;
            VpnThread vpnThread = new VpnThread(config, this, ipService, notificationManager, notificationBuilder);
            vpnThread.start();
            Log.i(Const.DEFAULT_TAG, "VPN started");
        } catch (Exception e) {
            Log.e(Const.DEFAULT_TAG, "error on startVPN:" + e.toString());
        }
    }

    public void stopVpn() {
        this.resetGlobalVar();
        this.stopSelf();
        Log.i(Const.DEFAULT_TAG, "VPN stopped");
    }

    private void resetGlobalVar() {
        Global.IS_CONNECTED = false;
        Global.RUNNING = false;
    }

}