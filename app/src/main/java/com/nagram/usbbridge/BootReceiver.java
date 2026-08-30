package com.nagram.usbbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences p = context.getSharedPreferences("bridge", Context.MODE_PRIVATE);
        if (!p.getBoolean("auto_start", false)) return;
        Intent s = new Intent(context, BridgeService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s); else context.startService(s);
        } catch (Throwable ignored) {
            // On newer Android versions background launch may be restricted. The journal remains safe.
        }
    }
}
