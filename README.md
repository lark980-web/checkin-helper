# 签到助手 (Checkin Helper)

一款安卓 App, 通过系统无障碍服务, 每天自动帮你完成各 App 的每日签到, 解放双手。

## 功能

- 预置 10 个 App: 抖音极速版、快手极速版、今日头条极速版、番茄免费小说、番茄畅听、抖音、快手、今日头条、西瓜视频、汽水音乐
- 每天早上 8:00 自动运行一轮签到(可修改时间, 见下)
- 首次打开 3 步傻瓜式设置向导: 无障碍服务 → 电池优化 → 通知权限
- 主界面「签到状态」列表: 每个 App 实时显示 等待 / 签到中 / ✓ 完成 / ✗ 失败, 并显示上次结果
- 日志按天写入文件, 方便对账
- 按文字语义查找控件点击, 不依赖固定坐标, 抗 App 小改版
- 找不到入口时自动上滑 3 次再找(防入口在首屏下方); 仍找不到则导出当前页面文字到文件, 发给开发者即可精准补充关键词
- 随机操作间隔, 模拟真人节奏

## 使用(直接装 APK)

1. 安装 APK(会提示未知来源, 允许即可)
2. 按 App 内「3 步完成设置」向导走完:
   - 开启无障碍服务(找到「签到助手」打开开关; **安卓规定这一步必须亲手开, 任何 App 都不能代劳**)
   - 允许后台运行(防手机杀后台)
   - 允许发送通知(每天 8 点靠通知把 App 唤到前台)
3. 备用机建议: 锁屏设为滑动解锁(不要设密码)、保持联网、关闭省电模式
4. 每个 App 请先手动登录一次账号, 否则签到助手进不去签到页

## 从源码构建

需要 JDK 17 + Android SDK(platform android-34, build-tools 34.0.0):

```bash
./build-manual.sh
# 产物: build-manual/signed.apk
```

仓库里同时保留了 Gradle 配置(`settings.gradle` / `build.gradle`), 但在受限网络环境下
Gradle 可能无法下载 Android Gradle Plugin, 此时请用上面的手工编译脚本。

## 自定义

- 增减 App / 改签到入口关键词: `app/src/main/java/com/checkin/helper/AppConfig.java`
- 改每天自动运行时间: `app/src/main/java/com/checkin/helper/Scheduler.java`
  (默认 8:00)

## 项目结构

```
app/src/main/
├── AndroidManifest.xml
├── java/com/checkin/helper/
│   ├── MainActivity.java      # 主界面: 设置向导 / 状态列表 / 日志
│   ├── CheckinService.java    # 无障碍服务(常驻)
│   ├── CheckinEngine.java     # 签到引擎: 逐个打开 App 并点击签到
│   ├── AppConfig.java         # App 列表与入口关键词配置
│   ├── StatusBus.java         # 各 App 签到状态总线(含持久化)
│   ├── LogBus.java            # 日志总线(按天写文件)
│   ├── Scheduler.java         # 每天定时
│   ├── AlarmReceiver.java     # 闹钟触发(含全屏通知拉起)
│   └── BootReceiver.java      # 开机恢复定时
└── res/
    ├── values/strings.xml
    └── xml/accessibility_service_config.xml
```

## 注意事项

- 请使用小号运行, 各 App 的用户协议通常禁止自动化操作, 有封号 / 金币清零风险
- 单机收益为零花钱量级; 本项目仅为个人学习与效率工具

## License

MIT
