#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="TConnectPrinter"
PKG_PATH="com/niko/tconnectprinter"

cd "$ROOT"

rm -rf build apk
mkdir -p build/classes apk

if [ ! -f tools/debug.keystore ]; then
  keytool \
    -genkeypair \
    -v \
    -keystore tools/debug.keystore \
    -storepass android \
    -keypass android \
    -alias debug \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Debug,O=Local,C=US"
fi

javac \
  -source 1.8 \
  -target 1.8 \
  -classpath tools/android-4.1.1.4.jar \
  -d build/classes \
  "src/${PKG_PATH}/MainActivity.java"

java -cp tools/r8.jar com.android.tools.r8.D8 \
  --min-api 23 \
  --output apk \
  build/classes/com/niko/tconnectprinter/MainActivity*.class

cp AndroidManifest.xml apk/AndroidManifest.xml
cp apktool.yml apk/apktool.yml

apktool b apk -o "build/${APP_NAME}-unsigned.apk"
cp "build/${APP_NAME}-unsigned.apk" "build/${APP_NAME}.apk"

jarsigner \
  -keystore tools/debug.keystore \
  -storepass android \
  -keypass android \
  "build/${APP_NAME}.apk" \
  debug

echo "Built build/${APP_NAME}.apk"
