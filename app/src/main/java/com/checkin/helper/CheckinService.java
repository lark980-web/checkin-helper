package com.checkin.helper;

import java.util.List;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/** 无障碍服务: 系统常驻, 引擎通过它执行点击; 任务跑在独立工作线程 */
public class CheckinService extends AccessibilityService {

    private static CheckinService instance;
    private Thread worker;
    private CheckinEngine engine;

    public static CheckinService get() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        LogBus.init(this);
        StatusBus.init(this);
        LogBus.post("无障碍服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 本服务不需要监听事件, 只做主动操作
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    /** 启动一轮签到(已有任务在跑则忽略) */
    public synchronized void runCheckin(List<AppConfig> apps) {
        if (worker != null && worker.isAlive()) {
            LogBus.post("已有签到任务在运行, 请等待完成");
            return;
        }
        engine = new CheckinEngine(this, LogBus::post);
        worker = new Thread(() -> engine.run(apps), "checkin-worker");
        worker.start();
    }

    public synchronized void stopCheckin() {
        if (engine != null) engine.stop();
    }
}
