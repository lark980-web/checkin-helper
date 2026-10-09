package com.checkin.helper;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import android.content.Context;

/** 简单的日志总线: 引擎 -> 界面, 同时按天写入文件 */
public class LogBus {
    public interface Listener {
        void onLog(String s);
    }

    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static File logDir;

    /** 在 MainActivity 和 CheckinService 启动时各调一次 */
    public static void init(Context ctx) {
        try {
            logDir = ctx.getExternalFilesDir(null);
        } catch (Exception ignored) {}
    }

    public static String logDirPath() {
        return logDir == null ? "" : logDir.getAbsolutePath();
    }

    public static void add(Listener l) {
        listeners.add(l);
    }

    public static void post(String s) {
        writeFile(s);
        for (Listener l : listeners) {
            try {
                l.onLog(s);
            } catch (Exception ignored) {}
        }
    }

    private static synchronized void writeFile(String s) {
        if (logDir == null) return;
        try {
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            File f = new File(logDir, "签到日志-" + day + ".txt");
            FileWriter w = new FileWriter(f, true);
            w.write("[" + time + "] " + s + "\n");
            w.close();
        } catch (Exception ignored) {}
    }
}
