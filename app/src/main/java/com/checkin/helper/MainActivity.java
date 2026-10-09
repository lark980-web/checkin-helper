package com.checkin.helper;

import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 主界面 v1.4: 卡片式布局 + 今日进度条 + 应用图标 + 状态列表 */
public class MainActivity extends Activity {

    private TextView statusTv;
    private TextView logTv;
    private ScrollView logScroll;
    private EditText appsEt;
    private LinearLayout statusList;
    private TextView progressTv;
    private ProgressBar progressBar;
    private java.util.Map<String, TextView> statusViews = new java.util.HashMap<>();
    private java.util.Map<String, TextView> lastViews = new java.util.HashMap<>();
    private java.util.Map<String, Integer> rowStates = new java.util.HashMap<>();
    private java.util.Map<String, Drawable> iconCache = new java.util.HashMap<>();
    private int totalApps = 0;
    private boolean wizardMode = false;
    private LinearLayout wizardSteps;
    private static final String PREF_SETUP = "setup";

    private static final int BG = 0xFFF2F4F7;
    private static final int CARD = 0xFFFFFFFF;
    private static final int PRIMARY = 0xFF1E88E5;
    private static final int GREEN = 0xFF2E9E5B;
    private static final int RED = 0xFFD32F2F;
    private static final int GRAY = 0xFF8A94A6;
    private static final int TEXT = 0xFF1F2A37;
    private static final int SUB = 0xFF8A94A6;
    private static final int DIV = 0xFFE8ECF1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        // ---- 顶部: 今日进度卡片 ----
        LinearLayout header = card();
        TextView title = new TextView(this);
        title.setText("签到助手");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        header.addView(title);
        statusTv = new TextView(this);
        statusTv.setTextSize(13);
        statusTv.setTextColor(SUB);
        statusTv.setPadding(0, dp(4), 0, dp(8));
        header.addView(statusTv);
        LinearLayout prow = new LinearLayout(this);
        prow.setOrientation(LinearLayout.HORIZONTAL);
        prow.setGravity(Gravity.CENTER_VERTICAL);
        progressTv = new TextView(this);
        progressTv.setTextSize(14);
        progressTv.setTextColor(TEXT);
        progressTv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.rightMargin = dp(12);
        progressTv.setLayoutParams(plp);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        progressBar.setLayoutParams(blp);
        if (Build.VERSION.SDK_INT >= 21) {
            progressBar.setProgressTintList(ColorStateList.valueOf(PRIMARY));
        }
        prow.addView(progressTv);
        prow.addView(progressBar);
        header.addView(prow);
        root.addView(header);

        // ---- 应用列表卡片 ----
        LinearLayout appCard = card();
        appCard.addView(sectionTitle("签到应用(每行一个, 须与桌面显示名一致)"));
        TextView loginTip = new TextView(this);
        loginTip.setText("提示: 每个 App 请先手动登录一次账号, 否则无法签到");
        loginTip.setTextSize(12);
        loginTip.setTextColor(0xFF9E9E9E);
        loginTip.setPadding(0, 0, 0, 6);
        appCard.addView(loginTip);
        appsEt = new EditText(this);
        appsEt.setMinLines(5);
        appsEt.setTextSize(13);
        appsEt.setTextColor(TEXT);
        appsEt.setText(AppConfig.loadLabels(this));
        appCard.addView(appsEt);
        root.addView(appCard);

        // ---- 按钮组 ----
        root.addView(btn("开启无障碍服务", 0xFFE8EEF5, PRIMARY, v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        root.addView(btn("忽略电池优化(防后台被杀)", 0xFFE8EEF5, PRIMARY, v -> {
            try {
                Intent it = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(it);
            } catch (Exception e) {
                toast("请到系统设置中手动关闭电池优化");
            }
        }));
        root.addView(btn("开始签到", PRIMARY, 0xFFFFFFFF, v -> startRun()));
        root.addView(btn("停止当前任务", 0xFFE8ECF1, TEXT, v -> {
            CheckinService s = CheckinService.get();
            if (s != null) {
                s.stopCheckin();
                LogBus.post("已发送停止信号");
            }
        }));
        root.addView(btn("设置每天 8:00 自动运行", 0xFFE8EEF5, PRIMARY, v -> {
            AppConfig.saveLabels(this, appsEt.getText().toString());
            Scheduler.scheduleDaily(this);
            toast("已设置每天 8:00 自动签到");
            LogBus.post("已设置每天 8:00 自动运行(开机后自动恢复)");
        }));

        // ---- 签到状态卡片 ----
        LinearLayout statusCard = card();
        statusCard.addView(sectionTitle("签到状态"));
        statusList = new LinearLayout(this);
        statusList.setOrientation(LinearLayout.VERTICAL);
        ScrollView statusScroll = new ScrollView(this);
        statusScroll.addView(statusList);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320));
        statusScroll.setLayoutParams(slp);
        statusCard.addView(statusScroll);
        root.addView(statusCard);

        // ---- 日志卡片 ----
        LinearLayout logCard = card();
        logCard.addView(sectionTitle("运行日志"));
        logTv = new TextView(this);
        logTv.setTextSize(12);
        logTv.setTextColor(TEXT);
        logScroll = new ScrollView(this);
        logScroll.addView(logTv);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(200));
        logScroll.setLayoutParams(llp);
        logCard.addView(logScroll);
        root.addView(logCard);
        root.addView(btn("分享运行文件(日志+页面文字, 发给开发者适配)", 0xFFE8EEF5, PRIMARY,
                v -> shareFiles()));

        ScrollView outer = new ScrollView(this);
        outer.addView(root);

        if (!isSetupDone()) {
            // 首次使用: 傻瓜式 3 步向导
            wizardMode = true;
            setContentView(buildWizard());
            return;
        }
        setContentView(outer);

        LogBus.init(this);
        StatusBus.init(this);
        LogBus.add(s -> runOnUiThread(() -> {
            logTv.append(s + "\n");
            logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
        }));
        StatusBus.add((label, state, detail) -> runOnUiThread(() ->
                setRowStatus(label, state, detail)));
        rebuildStatusRows(AppConfig.forLabels(this,
                AppConfig.parseLabels(appsEt.getText().toString())));
        LogBus.post("签到助手就绪。");
        LogBus.post("日志文件目录: " + LogBus.logDirPath());

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 3001);
            }
        }
        if (getIntent().getBooleanExtra("auto_run", false)) {
            LogBus.post("收到自动签到触发, 3 秒后开始");
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(this::startRun, 3000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wizardMode) {
            if (allStepsDone()) {
                setSetupDone(true);
                wizardMode = false;
                toast("设置完成!");
                recreate();
            } else {
                refreshWizard();
            }
            return;
        }
        boolean ok = isServiceOn();
        statusTv.setText(ok ? "● 无障碍服务已开启" : "● 无障碍服务未开启, 点下方按钮去开启");
        statusTv.setTextColor(ok ? GREEN : RED);
    }

    // ---------- 傻瓜式设置向导 ----------

    private boolean isSetupDone() {
        return getSharedPreferences(PREF_SETUP, MODE_PRIVATE).getBoolean("done", false);
    }

    private void setSetupDone(boolean v) {
        getSharedPreferences(PREF_SETUP, MODE_PRIVATE).edit().putBoolean("done", v).apply();
    }

    private boolean isBatteryOk() {
        try {
            android.os.PowerManager pm =
                    (android.os.PowerManager) getSystemService(POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isNotifOk() {
        if (Build.VERSION.SDK_INT < 33) return true;
        return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean allStepsDone() {
        return isServiceOn() && isBatteryOk() && isNotifOk();
    }

    private android.view.View buildWizard() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(40), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("3 步完成设置");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("跟着点就行, 全程不用懂技术");
        sub.setTextSize(14);
        sub.setTextColor(SUB);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, dp(16));
        root.addView(sub);

        wizardSteps = new LinearLayout(this);
        wizardSteps.setOrientation(LinearLayout.VERTICAL);
        root.addView(wizardSteps);
        refreshWizard();

        TextView skip = new TextView(this);
        skip.setText("我是老手, 跳过引导");
        skip.setTextSize(13);
        skip.setTextColor(SUB);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(0, dp(20), 0, 0);
        skip.setOnClickListener(v -> {
            setSetupDone(true);
            recreate();
        });
        root.addView(skip);

        ScrollView outer = new ScrollView(this);
        outer.addView(root);
        return outer;
    }

    private void refreshWizard() {
        if (wizardSteps == null) return;
        wizardSteps.removeAllViews();
        addStep(1, "开启无障碍服务", "点按钮跳到系统设置, 在列表中找到「签到助手」并打开开关。\n(安卓规定这一步必须亲手开, 任何 App 都不能代劳)",
                "去开启", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
                isServiceOn());
        addStep(2, "允许后台运行", "在弹出的系统对话框里点「允许」, 防止手机杀后台导致早上 8 点没跑起来。",
                "一键设置", v -> {
                    try {
                        startActivity(new Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        toast("请到系统设置中手动关闭电池优化");
                    }
                }, isBatteryOk());
        if (Build.VERSION.SDK_INT >= 33) {
            addStep(3, "允许发送通知", "每天 8 点靠这条通知把 App 唤到前台再签到, 点「允许」即可。",
                    "去允许", v -> requestPermissions(
                            new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 3001),
                    isNotifOk());
        }
    }

    private void addStep(int n, String title, String desc, String btnText,
                         android.view.View.OnClickListener l, boolean done) {
        LinearLayout c = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView num = new TextView(this);
        num.setText(done ? "✓" : String.valueOf(n));
        num.setTextSize(16);
        num.setTypeface(Typeface.DEFAULT_BOLD);
        num.setTextColor(0xFFFFFFFF);
        num.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(done ? GREEN : PRIMARY);
        num.setBackground(circle);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(38), dp(38));
        nlp.rightMargin = dp(12);
        num.setLayoutParams(nlp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(clp);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(TEXT);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(13);
        d.setTextColor(SUB);
        d.setPadding(0, dp(4), 0, 0);
        col.addView(t);
        col.addView(d);

        row.addView(num);
        row.addView(col);
        c.addView(row);

        if (done) {
            TextView ok = new TextView(this);
            ok.setText("已完成 ✓");
            ok.setTextSize(14);
            ok.setTextColor(GREEN);
            ok.setGravity(Gravity.CENTER);
            ok.setPadding(0, dp(10), 0, 0);
            c.addView(ok);
        } else {
            Button b = btn(btnText, PRIMARY, 0xFFFFFFFF, l);
            c.addView(b);
        }
        wizardSteps.addView(c);
    }

    private boolean isServiceOn() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName() + "/.CheckinService");
    }

    private void startRun() {
        AppConfig.saveLabels(this, appsEt.getText().toString());
        CheckinService s = CheckinService.get();
        if (s == null) {
            toast("请先开启无障碍服务");
            return;
        }
        List<AppConfig> apps = AppConfig.forLabels(this,
                AppConfig.parseLabels(appsEt.getText().toString()));
        // 只跑已安装的: 任务目标 = 可签到库 ∩ 本机已安装
        List<AppConfig> targets = new java.util.ArrayList<>();
        for (AppConfig a : apps) {
            if (AppConfig.isInstalled(this, a)) targets.add(a);
        }
        if (targets.isEmpty()) {
            toast("没有检测到已安装的可签到应用");
            return;
        }
        rebuildStatusRows(apps);
        LogBus.post("手动启动, 本机已安装 " + targets.size() + " 个可签到应用");
        s.runCheckin(targets);
    }

    // ---------- 状态列表 ----------

    private void rebuildStatusRows(List<AppConfig> apps) {
        statusList.removeAllViews();
        statusViews.clear();
        lastViews.clear();
        rowStates.clear();
        // 动态任务目标: 只把"已安装"的应用列为任务, 未安装的折叠为一行小字
        List<AppConfig> installed = new java.util.ArrayList<>();
        List<String> missing = new java.util.ArrayList<>();
        for (AppConfig a : apps) {
            if (AppConfig.isInstalled(this, a)) installed.add(a);
            else missing.add(a.label);
        }
        totalApps = installed.size();
        for (AppConfig a : installed) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            ImageView iv = new ImageView(this);
            Drawable d = appIcon(a.label);
            if (d != null) iv.setImageDrawable(d);
            else iv.setImageResource(android.R.drawable.sym_def_app_icon);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(42), dp(42));
            ip.rightMargin = dp(12);
            iv.setLayoutParams(ip);

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            mid.setLayoutParams(mp);
            TextView name = new TextView(this);
            name.setText(a.label);
            name.setTextSize(15);
            name.setTextColor(TEXT);
            TextView last = new TextView(this);
            last.setTextSize(11);
            last.setTextColor(SUB);
            String li = StatusBus.lastInfo(a.label);
            if (!li.isEmpty()) last.setText(li);
            mid.addView(name);
            mid.addView(last);

            TextView st = new TextView(this);
            st.setText("等待");
            st.setTextSize(13);
            st.setTextColor(GRAY);

            row.addView(iv);
            row.addView(mid);
            row.addView(st);
            statusList.addView(row);

            android.view.View div = new android.view.View(this);
            div.setBackgroundColor(DIV);
            div.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            statusList.addView(div);

            statusViews.put(a.label, st);
            lastViews.put(a.label, last);
            rowStates.put(a.label, StatusBus.WAITING);
        }
        if (!missing.isEmpty()) {
            TextView note = new TextView(this);
            note.setText("未安装 " + missing.size() + " 个: "
                    + android.text.TextUtils.join("、", missing));
            note.setTextSize(11);
            note.setTextColor(SUB);
            note.setPadding(0, dp(8), 0, 0);
            statusList.addView(note);
        }
        if (installed.isEmpty()) {
            TextView note = new TextView(this);
            note.setText("本机没有已安装的可签到应用, 先去应用市场装几个再回来");
            note.setTextSize(12);
            note.setTextColor(RED);
            note.setPadding(0, dp(8), 0, 0);
            statusList.addView(note);
        }
        updateProgress();
    }

    private void setRowStatus(String label, int state, String detail) {
        TextView st = statusViews.get(label);
        if (st == null) return;
        rowStates.put(label, state);
        String t = StatusBus.text(state);
        if (detail != null && !detail.isEmpty()) t += "(" + detail + ")";
        st.setText(t);
        int color;
        switch (state) {
            case StatusBus.RUNNING: color = PRIMARY; break;
            case StatusBus.DONE:
            case StatusBus.ALREADY: color = GREEN; break;
            case StatusBus.PENDING: color = 0xFFE65100; break;
            case StatusBus.FAILED: color = RED; break;
            default: color = GRAY;
        }
        st.setTextColor(color);
        TextView last = lastViews.get(label);
        if (last != null) last.setText(StatusBus.lastInfo(label));
        updateProgress();
    }

    private void updateProgress() {
        int done = 0, pending = 0;
        for (int s : rowStates.values()) {
            if (s == StatusBus.DONE || s == StatusBus.ALREADY) done++;
            else if (s == StatusBus.PENDING) pending++;
        }
        progressTv.setText("今日 " + done + "/" + totalApps + " 已完成"
                + (pending > 0 ? "（" + pending + " 待确认）" : ""));
        progressBar.setMax(Math.max(totalApps, 1));
        progressBar.setProgress(done);
    }

    /** 按桌面显示名取应用图标 */
    private Drawable appIcon(String label) {
        if (iconCache.containsKey(label)) return iconCache.get(label);
        Drawable d = null;
        try {
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                if (label.equals(ri.loadLabel(pm).toString())) {
                    d = pm.getApplicationIcon(ri.activityInfo.packageName);
                    break;
                }
            }
        } catch (Exception ignored) {}
        iconCache.put(label, d);
        return d;
    }

    // ---------- 样式小件 ----------

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(CARD);
        gd.setCornerRadius(dp(16));
        c.setBackground(gd);
        int p = dp(16);
        c.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(2));
        return c;
    }

    private TextView sectionTitle(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(13);
        tv.setTextColor(SUB);
        tv.setPadding(0, 0, 0, dp(8));
        return tv;
    }

    private Button btn(String t, int bg, int fg, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextColor(fg);
        b.setTextSize(15);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(dp(12));
        b.setBackground(gd);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(dp(1));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(8);
        b.setLayoutParams(p);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /** 把日志+页面文字合并成一个 txt, 用单文件分享(兼容性最强, 部分 App 接不好多文件分享) */
    private void shareFiles() {
        try {
            java.io.File dir = getExternalFilesDir(null);
            java.io.File[] fs = dir == null ? null : dir.listFiles(
                    (d, n) -> n.endsWith(".txt") && !n.startsWith("运行文件_合集"));
            if (fs == null || fs.length == 0) {
                toast("还没有可分享的文件, 先跑一次签到");
                return;
            }
            java.util.Arrays.sort(fs, (a, b) ->
                    Long.compare(b.lastModified(), a.lastModified()));
            String ts = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm",
                    java.util.Locale.US).format(new java.util.Date());
            java.io.File out = new java.io.File(getCacheDir(),
                    "运行文件_合集_" + ts + ".txt");
            java.io.FileWriter w = new java.io.FileWriter(out);
            for (java.io.File f : fs) {
                w.write("\n========== " + f.getName() + " ==========\n");
                java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.FileReader(f));
                String line;
                while ((line = r.readLine()) != null) {
                    w.write(line);
                    w.write("\n");
                }
                r.close();
            }
            w.close();
            Uri u = FileShareProvider.uriFor(this, out);
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("text/plain");
            it.putExtra(Intent.EXTRA_STREAM, u);
            it.setClipData(android.content.ClipData.newRawUri("files", u));
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, "分享运行文件"));
        } catch (Exception e) {
            toast("分享失败: " + e.getMessage());
        }
    }
}
