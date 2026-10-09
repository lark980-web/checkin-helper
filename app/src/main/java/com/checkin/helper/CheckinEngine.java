package com.checkin.helper;

import java.util.List;
import java.util.Random;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 签到引擎: 在工作线程中运行, 通过无障碍服务逐个打开 App 并点击签到。
 * 按"文字语义"找控件, 不依赖固定坐标。
 */
public class CheckinEngine {

    public interface Logger {
        void log(String s);
    }

    private final AccessibilityService svc;
    private final Logger logger;
    private final Random rnd = new Random();
    private volatile boolean stopped = false;

    public CheckinEngine(AccessibilityService svc, Logger logger) {
        this.svc = svc;
        this.logger = logger;
    }

    public void stop() {
        stopped = true;
    }

    private static final int R_OK = 1, R_PENDING = 2, R_FAIL = 0;

    /** 当前前台应用的包名, 取不到返回 "" */
    private String foregroundPkg() {
        try {
            AccessibilityNodeInfo r = svc.getRootInActiveWindow();
            if (r == null) return "";
            CharSequence p = r.getPackageName();
            return p == null ? "" : p.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 等待目标 App 到前台, 超时返回 false */
    private boolean waitForApp(String pkg, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end && !stopped) {
            if (pkg.equals(foregroundPkg())) return true;
            sleep(500);
        }
        return pkg.equals(foregroundPkg());
    }

    public void run(List<AppConfig> apps) {
        PowerManager pm = (PowerManager) svc.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "checkin:run");
        try {
            wl.acquire(2 * 3600 * 1000L);
            tryUnlock(); // 亮屏后上滑, 解开非密码锁屏
            logger.log("===== 本轮签到开始, 共 " + apps.size() + " 个 App =====");
            int ok = 0, pending = 0;
            StringBuilder fail = new StringBuilder();
            for (AppConfig app : apps) {
                if (stopped) {
                    logger.log("已手动停止");
                    break;
                }
                int r = checkinOne(app);
                if (r == R_OK) ok++;
                else if (r == R_PENDING) pending++;
                else {
                    if (fail.length() > 0) fail.append("、");
                    fail.append(app.label);
                }
                sleep(2000 + rnd.nextInt(2000));
            }
            String summary = "本轮结束: 成功 " + ok + "/" + apps.size()
                    + (pending > 0 ? ", 待确认 " + pending + " 个" : "")
                    + (fail.length() > 0 ? ", 失败: " + fail : "");
            logger.log("===== " + summary + " =====");
        } finally {
            if (wl.isHeld()) wl.release();
            goHome();
        }
    }

    // ---------------- 单个 App ----------------

    private int checkinOne(AppConfig app) {
        logger.log("—— 开始: " + app.label + " ——");
        StatusBus.post(app.label, StatusBus.RUNNING, "");
        try {
            String pkg = pkgForApp(app);
            if (pkg == null) {
                logger.log(app.label + ": 手机里没找到这个应用, 跳过");
                StatusBus.post(app.label, StatusBus.SKIPPED, "未安装此应用");
                return R_FAIL;
            }
            Intent it = svc.getPackageManager().getLaunchIntentForPackage(pkg);
            if (it == null) {
                logger.log(app.label + ": 无法启动");
                StatusBus.post(app.label, StatusBus.FAILED, "无法启动");
                return R_FAIL;
            }
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            svc.startActivity(it);
            // 关键防线: 确认目标 App 真的到了前台, 否则后面一切点击都是误操作。
            // 若系统拦截了启动(如未允许"后台弹出界面"), 在此大声失败, 绝不假成功。
            if (!waitForApp(pkg, 8000)) {
                // 重试一次: 有些机型首次启动慢或任务栈状态异常, 换 flag 再拉一次
                logger.log(app.label + ": 首次启动超时(当前前台: "
                        + foregroundPkg() + "), 重试");
                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                svc.startActivity(it);
            }
            if (!waitForApp(pkg, 8000)) {
                logger.log(app.label + ": 未能打开应用(当前前台: "
                        + foregroundPkg() + ", 可能被系统拦截启动), 跳过");
                StatusBus.post(app.label, StatusBus.FAILED, "未能打开应用");
                goHome();
                return R_FAIL;
            }
            sleep(2000 + rnd.nextInt(2000));
            dismissPopups();

            // 1. 找签到入口(先直接找, 找不到就上滑几次再找, 防入口在首屏下方)
            boolean entered = findEntry(app);
            if (!entered) { // 兜底
                for (String k : new String[]{"签到", "领金币"}) {
                    if (clickText(k)) {
                        entered = true;
                        break;
                    }
                }
            }
            if (!entered) {
                logger.log(app.label + ": 未找到签到入口, 跳过");
                dumpVisibleTexts(app.label); // 导出页面文字, 便于精准适配
                StatusBus.post(app.label, StatusBus.SKIPPED, "未找到签到入口");
                goHome();
                return R_FAIL;
            }
            if (!pkg.equals(foregroundPkg())) {
                logger.log(app.label + ": 点击入口后应用不在前台, 判定失败");
                StatusBus.post(app.label, StatusBus.FAILED, "应用被切走");
                goHome();
                return R_FAIL;
            }
            sleep(3000 + rnd.nextInt(2000));
            dismissPopups();

            // 2. 是否已签到
            if (hasAnyText(app.done) || hasAnyText(AppConfig.EXTRA_DONE)) {
                logger.log(app.label + ": 今日已签到 ✓");
                StatusBus.post(app.label, StatusBus.ALREADY, "");
                goHome();
                return R_OK;
            }

            // 3. 点签到按钮, 点完再确认一次是否到账
            if (!pkg.equals(foregroundPkg())) {
                logger.log(app.label + ": 点签到前应用不在前台, 判定失败");
                StatusBus.post(app.label, StatusBus.FAILED, "应用被切走");
                goHome();
                return R_FAIL;
            }
            boolean s = false;
            for (String k : app.sign) {
                if (clickText(k)) {
                    s = true;
                    break;
                }
            }
            sleep(2500);
            dismissPopups();
            if (s) {
                sleep(1500);
                if (hasAnyText(app.done) || hasAnyText(AppConfig.EXTRA_DONE)) {
                    logger.log(app.label + ": 签到完成 ✓");
                    StatusBus.post(app.label, StatusBus.DONE, "");
                    goHome();
                    return R_OK;
                } else {
                    logger.log(app.label + ": 已点击签到, 未检测到到账标识, 请手动检查");
                    StatusBus.post(app.label, StatusBus.PENDING, "待确认");
                    goHome();
                    return R_PENDING;
                }
            } else {
                logger.log(app.label + ": 未找到签到按钮(可能已签或入口变化)");
                StatusBus.post(app.label, StatusBus.FAILED, "未找到签到按钮");
            }
            goHome();
            return R_FAIL;
        } catch (Exception e) {
            logger.log(app.label + ": 出错 " + e.getMessage());
            StatusBus.post(app.label, StatusBus.FAILED, "出错");
            goHome();
            return R_FAIL;
        }
    }

    // ---------------- 控件操作 ----------------

    /** 上滑手势解开非密码锁屏(滑动解锁); 有密码/指纹锁屏则解不开, 需手动设为滑动解锁 */
    private void tryUnlock() {
        try {
            android.util.DisplayMetrics dm = svc.getResources().getDisplayMetrics();
            int w = dm.widthPixels, h = dm.heightPixels;
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(w / 2f, h * 0.85f);
            p.lineTo(w / 2f, h * 0.25f);
            android.accessibilityservice.GestureDescription.StrokeDescription stroke =
                    new android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 500);
            android.accessibilityservice.GestureDescription gd =
                    new android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build();
            svc.dispatchGesture(gd, null, null);
            sleep(1800);
            dismissPopups();
        } catch (Exception ignored) {}
    }

    /** 按包名(或桌面显示名)解析出包名, 未安装返回 null */
    private String pkgForLabel(String label) {
        // AppConfig.resolvePkg 已处理包名优先逻辑, 这里保留按 label 查找作兜底
        PackageManager pm = svc.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        for (ResolveInfo ri : list) {
            try {
                String l = ri.loadLabel(pm).toString();
                if (label.equals(l)) return ri.activityInfo.packageName;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String pkgForApp(AppConfig app) {
        String p = AppConfig.resolvePkg(svc, app);
        return p != null ? p : pkgForLabel(app.label);
    }

    private AccessibilityNodeInfo findVisible(String text) {
        AccessibilityNodeInfo root;
        try {
            root = svc.getRootInActiveWindow();
        } catch (Exception e) {
            return null;
        }
        if (root == null) return null;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo n : list) {
            if (n.isVisibleToUser()) return n;
            n.recycle();
        }
        return null;
    }

    private boolean hasAnyText(String[] keys) {
        for (String k : keys) {
            AccessibilityNodeInfo n = findVisible(k);
            if (n != null) {
                n.recycle();
                return true;
            }
            if (stopped) break;
        }
        return false;
    }

    /** 点击屏幕上第一个包含该文字的可见控件 */
    private boolean clickText(String text) {
        AccessibilityNodeInfo n = findVisible(text);
        if (n == null) return false;
        try {
            AccessibilityNodeInfo cur = n;
            while (cur != null) {
                if (cur.isClickable()) {
                    cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    // 轻微随机等待, 模拟真人节奏
                    sleep(800 + rnd.nextInt(1700));
                    return true;
                }
                cur = cur.getParent();
            }
            return false;
        } finally {
            n.recycle();
        }
    }

    private void dismissPopups() {
        for (int i = 0; i < 4 && !stopped; i++) {
            boolean hit = false;
            for (String k : AppConfig.POPUP_KEYS) {
                if (clickText(k)) {
                    logger.log("关闭弹窗: " + k);
                    sleep(1200);
                    hit = true;
                    break;
                }
            }
            if (!hit) break;
        }
    }

    /** 找签到入口: 先直接找, 找不到就上滑 3 次、每次再找(防入口在首屏下方);
     *  再找不到就点"我的"进个人页找一轮(部分 App 入口在 tab 页里) */
    private boolean findEntry(AppConfig app) {
        for (String k : app.entry) {
            if (clickText(k)) {
                logger.log("进入: " + k);
                return true;
            }
        }
        for (int i = 0; i < 3 && !stopped; i++) {
            swipeUp();
            sleep(1500);
            dismissPopups();
            for (String k : app.entry) {
                if (clickText(k)) {
                    logger.log("上滑后进入: " + k);
                    return true;
                }
            }
        }
        for (String tab : new String[]{"我的"}) {
            if (stopped) break;
            if (clickText(tab)) {
                sleep(2000);
                dismissPopups();
                for (String k : app.entry) {
                    if (clickText(k)) {
                        logger.log("我的页进入: " + k);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 从屏幕中部向上滑一段(找首屏下方的入口用) */
    private void swipeUp() {
        try {
            android.util.DisplayMetrics dm = svc.getResources().getDisplayMetrics();
            int w = dm.widthPixels, h = dm.heightPixels;
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(w / 2f, h * 0.8f);
            p.lineTo(w / 2f, h * 0.3f);
            android.accessibilityservice.GestureDescription.StrokeDescription stroke =
                    new android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 400);
            android.accessibilityservice.GestureDescription gd =
                    new android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build();
            svc.dispatchGesture(gd, null, null);
        } catch (Exception ignored) {}
    }

    /**
     * 找不到入口时, 把当前屏幕可见文字导出到文件。
     * 用户把文件发给开发者, 即可按实际文字精准补充关键词, 不用猜。
     */
    private void dumpVisibleTexts(String label) {
        try {
            AccessibilityNodeInfo root = svc.getRootInActiveWindow();
            if (root == null) return;
            StringBuilder sb = new StringBuilder();
            collectTexts(root, sb, 0);
            java.io.File dir = svc.getExternalFilesDir(null);
            if (dir == null) return;
            String ts = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
                    java.util.Locale.US).format(new java.util.Date());
            String name = "页面文字_" + label + "_" + ts + ".txt";
            java.io.FileWriter w = new java.io.FileWriter(new java.io.File(dir, name));
            w.write(sb.toString());
            w.close();
            logger.log("已导出页面文字: " + name + " ——点主界面「分享运行文件」发给我, 可精准适配");
        } catch (Exception e) {
            logger.log("导出页面文字失败: " + e.getMessage());
        }
    }

    /** 只收叶子/可点击节点的文字, 避免父容器重复文本刷屏 */
    private void collectTexts(AccessibilityNodeInfo n, StringBuilder sb, int depth) {
        if (n == null || depth > 12) return;
        try {
            if (n.isVisibleToUser() && (n.getChildCount() == 0 || n.isClickable())) {
                CharSequence t = n.getText();
                if (t == null || t.length() == 0) t = n.getContentDescription();
                if (t != null && t.length() > 0 && t.length() < 40) {
                    sb.append(t).append('\n');
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                collectTexts(n.getChild(i), sb, depth + 1);
            }
        } catch (Exception ignored) {}
    }

    private void goHome() {
        try {
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        } catch (Exception ignored) {}
        sleep(1500);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}
