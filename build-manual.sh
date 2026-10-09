#!/bin/bash
# 手工编译 APK(不依赖 Gradle/网络): aapt2 -> javac -> d8 -> zipalign -> apksigner
set -e
export JAVA_HOME=/home/hatch/workspace/jdk
export PATH=$JAVA_HOME/bin:$PATH
SDK=/home/hatch/workspace/android-sdk
BT=$SDK/build-tools/34.0.0
PROJ=/home/hatch/workspace/checkin-apk
APP=$PROJ/app/src/main
BUILD=$PROJ/build-manual
rm -rf "$BUILD"
mkdir -p "$BUILD/compiled_res" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"

echo "[1/6] aapt2 compile..."
$BT/aapt2 compile --dir "$APP/res" -o "$BUILD/compiled_res.zip"

echo "[2/6] aapt2 link..."
$BT/aapt2 link -o "$BUILD/base.apk" \
  -I "$SDK/platforms/android-34/android.jar" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --min-sdk-version 24 \
  --target-sdk-version 33 \
  "$BUILD/compiled_res.zip"

echo "[3/6] javac..."
find "$APP/java" -name "*.java" > "$BUILD/sources.txt"
find "$BUILD/gen" -name "*.java" >> "$BUILD/sources.txt"
javac -source 17 -target 17 -nowarn -encoding UTF-8 \
  -cp "$SDK/platforms/android-34/android.jar" \
  -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "[4/6] d8..."
$BT/d8 --lib "$SDK/platforms/android-34/android.jar" \
  --min-api 24 --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name "*.class")

echo "[5/6] add dex + zipalign..."
cp "$BUILD/dex/classes.dex" "$BUILD/"
cd "$BUILD"
zip -q -j base.apk classes.dex
$BT/zipalign -f 4 base.apk aligned.apk

echo "[6/6] sign..."
# 签名密钥固定存放在 signing/(gitignored, 永不入库), 保证各版本签名一致、可覆盖安装
# 口令存放在 signing/.storepass(同样 gitignored, 本地随机生成); 两个文件都不进仓库
KS=$PROJ/signing/debug.keystore
PASSFILE=$PROJ/signing/.storepass
mkdir -p "$PROJ/signing"
if [ ! -f "$PASSFILE" ]; then
  tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32 > "$PASSFILE"
  chmod 600 "$PASSFILE"
fi
KSPASS=$(cat "$PASSFILE")
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias checkin \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass "$KSPASS" -keypass "$KSPASS" \
    -dname "CN=CheckinHelper,O=CheckinHelper" 2>/dev/null
fi
$BT/apksigner sign --ks "$KS" --ks-pass "pass:$KSPASS" \
  --key-pass "pass:$KSPASS" --out signed.apk aligned.apk
$BT/apksigner verify --print-certs signed.apk | head -3
ls -la signed.apk
echo BUILD_OK
