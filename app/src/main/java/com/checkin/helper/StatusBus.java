package com.checkin.helper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import android.content.Context;
import android.content.SharedPreferences;

/** 各 App 签到状态总线: 引擎 -> 状态列表, 同时持久化上次结果 */
public class StatusBus {
    public static final int WAITING = 0;
    public static final int RUNNING = 1;
    public static final int DONE = 2;
    public static final int ALREADY = 3;
    public static final int FAILED = 4;
    public static final int SKIPPED = 5;
    public static final int PENDING = 6; // 点了签到但未检测到到账标识, 需人工确认

    public interface Listener {
        void onStatus(String label, int state, String detail);
    }

    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static SharedPreferences prefs;

    public static void init(Context ctx) {
        try {
            prefs = ctx.getSharedPreferences("status", Context.MODE_PRIVATE);
        } catch (Exception ignored) {}
    }

    public static void add(Listener l) {
        listeners.add(l);
    }

    public static String text(int state) {
        switch (state) {
            case RUNNING: return "签到中…";
            case DONE: return "✓ 签到完成";
            case ALREADY: return "✓ 今日已签到";
            case FAILED: return "✗ 失败";
            case SKIPPED: return "— 跳过";
            case PENDING: return "◷ 待确认";
            default: return "等待";
        }
    }

    public static void post(String label, int state, String detail) {
        try {
            if (prefs != null) {
                String t = new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date());
                prefs.edit().putString("s_" + label,
                        state + "|" + t + "|" + (detail == null ? "" : detail)).apply();
            }
        } catch (Exception ignored) {}
        for (Listener l : listeners) {
            try {
                l.onStatus(label, state, detail);
            } catch (Exception ignored) {}
        }
    }

    /** 上次结果描述, 如 "上次 10-05 08:02 ✓ 签到完成" */
    public static String lastInfo(String label) {
        try {
            if (prefs == null) return "";
            String v = prefs.getString("s_" + label, null);
            if (v == null) return "";
            String[] p = v.split("\\|", 3);
            int st = Integer.parseInt(p[0]);
            String info = "上次 " + p[1] + " " + text(st);
            if (p.length > 2 && !p[2].isEmpty()) info += "(" + p[2] + ")";
            return info;
        } catch (Exception e) {
            return "";
        }
    }
}
