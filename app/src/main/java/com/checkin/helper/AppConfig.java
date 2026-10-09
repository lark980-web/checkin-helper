package com.checkin.helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/** 单个 App 的签到配置: 显示名 + 包名 + 入口/签到/已签到关键词 */
public class AppConfig {
    public String label;
    public String pkg; // 包名(可为空, 为空时按桌面显示名匹配)
    public String[] entry;
    public String[] sign;
    public String[] done;

    public AppConfig(String label, String pkg, String[] entry, String[] sign, String[] done) {
        this.label = label;
        this.pkg = pkg;
        this.entry = entry;
        this.sign = sign;
        this.done = done;
    }

    /** 通用弹窗关键词 */
    public static final String[] POPUP_KEYS = {
            "跳过", "关闭", "以后再说", "我知道了", "取消",
            "暂不升级", "稍后再说", "下次再说", "残忍拒绝", "暂不开启"
    };

    /** 通用"已签到"补充判断(各 App 实际文案不一) */
    public static final String[] EXTRA_DONE = {
            "已领取", "明天再来", "明日再来"
    };

    private static List<AppConfig> defaults() {
        // v1.8: 只保留经核实有每日签到功能的 App(2026-10 核实);
        // 抖音/快手/今日头条主 App 无每日金币签到证据(签到为极速版专属), 已移出默认名单,
        // 如需可手动在列表里加回(会走通用关键词)。
        List<AppConfig> list = new ArrayList<>();
        list.add(new AppConfig("抖音极速版", "com.ss.android.ugc.aweme.lite",
                new String[]{"赚钱", "金币任务", "来赚钱"},
                new String[]{"签到"}, new String[]{"已签到", "签到成功"}));
        list.add(new AppConfig("快手极速版", "com.kuaishou.nebula",
                new String[]{"去赚钱", "红包"}, new String[]{"签到"}, new String[]{"已签到", "签到成功"}));
        list.add(new AppConfig("今日头条极速版", "com.ss.android.article.lite",
                new String[]{"任务"}, new String[]{"签到"}, new String[]{"已签到"}));
        list.add(new AppConfig("番茄免费小说", "com.dragon.read",
                new String[]{"福利"}, new String[]{"签到"}, new String[]{"已签到"}));
        list.add(new AppConfig("番茄畅听", "com.xs.fm",
                new String[]{"福利", "金币"}, new String[]{"签到"}, new String[]{"已签到"}));
        list.add(new AppConfig("西瓜视频", "com.ss.android.article.video",
                new String[]{"签到", "金币"}, new String[]{"签到"}, new String[]{"已签到"}));
        list.add(new AppConfig("汽水音乐", "com.luna.music",
                new String[]{"福利", "签到", "金币"}, new String[]{"签到"}, new String[]{"已签到"}));
        list.add(new AppConfig("红果免费短剧", "com.phoenix.read",
                new String[]{"签到", "福利"}, new String[]{"签到"}, new String[]{"已签到", "签到成功"}));
        list.add(new AppConfig("抖音火山版", "com.ss.android.ugc.live",
                new String[]{"签到", "金币"}, new String[]{"签到"}, new String[]{"已签到"}));
        return list;
    }

    private static final String PREFS = "cfg";
    private static final String KEY_LABELS = "labels";
    private static final String KEY_VER = "cfg_ver";
    /** 名单版本号: 默认名单变化时 +1, 已保存的旧名单自动刷新为新默认 */
    private static final int CFG_VER = 2;

    public static String defaultLabelsText() {
        StringBuilder sb = new StringBuilder();
        for (AppConfig c : defaults()) sb.append(c.label).append("\n");
        return sb.toString().trim();
    }

    public static void saveLabels(Context ctx, String text) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_LABELS, text).putInt(KEY_VER, CFG_VER).apply();
    }

    public static String loadLabels(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (sp.getInt(KEY_VER, 1) != CFG_VER) return defaultLabelsText();
        String s = sp.getString(KEY_LABELS, null);
        return s == null ? defaultLabelsText() : s;
    }

    /** 按用户填写的应用名生成配置; 未知应用给一套通用关键词 */
    public static List<AppConfig> forLabels(Context ctx, List<String> labels) {
        Map<String, AppConfig> map = new HashMap<>();
        for (AppConfig c : defaults()) map.put(c.label, c);
        List<AppConfig> out = new ArrayList<>();
        for (String raw : labels) {
            String label = raw == null ? "" : raw.trim();
            if (label.isEmpty()) continue;
            AppConfig c = map.get(label);
            if (c == null) {
                c = new AppConfig(label, "",
                        new String[]{"签到", "领金币", "福利"},
                        new String[]{"签到"},
                        new String[]{"已签到"});
            }
            out.add(c);
        }
        return out;
    }

    /** 该应用是否已安装: 优先按包名查, 包名为空则按桌面显示名匹配 */
    public static boolean isInstalled(Context ctx, AppConfig c) {
        PackageManager pm = ctx.getPackageManager();
        if (c.pkg != null && !c.pkg.isEmpty()) {
            try {
                pm.getPackageInfo(c.pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        for (ResolveInfo ri : list) {
            try {
                if (c.label.equals(ri.loadLabel(pm).toString())) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /** 按包名(或显示名)解析出包名, 未安装返回 null */
    public static String resolvePkg(Context ctx, AppConfig c) {
        PackageManager pm = ctx.getPackageManager();
        if (c.pkg != null && !c.pkg.isEmpty()) {
            try {
                pm.getPackageInfo(c.pkg, 0);
                return c.pkg;
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        for (ResolveInfo ri : list) {
            try {
                if (c.label.equals(ri.loadLabel(pm).toString()))
                    return ri.activityInfo.packageName;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static List<String> parseLabels(String text) {
        return new ArrayList<>(Arrays.asList(text.split("\n")));
    }
}
