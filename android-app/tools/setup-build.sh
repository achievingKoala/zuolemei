#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .tools
cd .tools
if [ ! -d jdk ]; then
  curl --fail --location --retry 3 'https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jdk/hotspot/normal/eclipse' -o jdk.tar.gz
  mkdir -p jdk-unpack
  tar -xzf jdk.tar.gz -C jdk-unpack
  mv jdk-unpack/*/Contents/Home jdk
fi
export JAVA_HOME="$PWD/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$PWD/sdk"
export GRADLE_USER_HOME="$PWD/gradle-home"
if [ ! -d gradle-8.11.1 ]; then
  curl --fail --location --retry 3 https://services.gradle.org/distributions/gradle-8.11.1-bin.zip -o gradle.zip
  unzip -q -o gradle.zip
fi
if [ ! -f sdk/cmdline-tools/latest/bin/sdkmanager ]; then
  curl --fail --location --retry 3 https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip -o sdk.zip
  mkdir -p sdk/cmdline-tools sdk-unpack
  unzip -q -o sdk.zip -d sdk-unpack
  mv sdk-unpack/cmdline-tools sdk/cmdline-tools/latest
fi
set +o pipefail
yes | sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null
set -o pipefail
sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
cd ..
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
.tools/gradle-8.11.1/bin/gradle --no-daemon :app:assembleDebug :app:lintDebug
