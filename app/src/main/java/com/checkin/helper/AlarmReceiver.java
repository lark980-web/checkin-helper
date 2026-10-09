package com.checkin.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 每天闹钟触发: 先约好第二天, 再启动签到。
 *
 * Android 10+ 限制后台直接启动 Activity, 因此走"全屏通知"把 App
 * 带到前台再执行, 这是系统允许的正规路径。
 */
public class AlarmReceiver extends BroadcastReceiver {

    static final String CH = "auto_run";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Scheduler.scheduleDaily(ctx); // 约第二天

        // 路径1: 直接跑(旧版本 Android / 部分机型可用)
        CheckinService s = CheckinService.get();
        if (s != null) {
            s.runCheckin(AppConfig.forLabels(ctx,
                    AppConfig.parseLabels(AppConfig.loadLabels(ctx))));
        }
        // 路径2: 全屏通知拉起前台再执行(最可靠, 防重复由 runCheckin 内部保证)
        postFullScreen(ctx);
    }

    private void postFullScreen(Context ctx) {
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CH, "自动签到", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        Intent it = new Intent(ctx, MainActivity.class);
        it.putExtra("auto_run", true);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 2002, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(ctx, CH)
                : new Notification.Builder(ctx);
        Notification n = b.setContentTitle("签到助手")
                .setContentText("开始今日自动签到")
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
        try {
            nm.notify(2002, n);
        } catch (Exception ignored) {
            // 通知权限被拒时静默跳过, 路径1 仍可能生效
        }
    }
}
