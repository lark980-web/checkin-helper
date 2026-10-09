package com.checkin.helper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机后恢复每天定时任务 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Scheduler.scheduleDaily(ctx);
        }
    }
}
