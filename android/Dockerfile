# Build do APK sem Android Studio.
# Uso:
#   docker build -t setup-mdm-build .
#   docker run --rm -v "$PWD":/proj -w /proj setup-mdm-build ./gradlew :app:assembleDebug
# O APK sai em app/build/outputs/apk/debug/app-debug.apk
FROM eclipse-temurin:17-jdk

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools

RUN apt-get update && apt-get install -y --no-install-recommends unzip curl && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /opt/android-sdk/cmdline-tools && \
    curl -sSL -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q /tmp/clt.zip -d /tmp/clt && \
    mv /tmp/clt/cmdline-tools /opt/android-sdk/cmdline-tools/latest && \
    rm -rf /tmp/clt.zip /tmp/clt && \
    yes | sdkmanager --sdk_root=$ANDROID_SDK_ROOT --licenses >/dev/null 2>&1 || true && \
    sdkmanager --sdk_root=$ANDROID_SDK_ROOT "platform-tools" "platforms;android-34" "build-tools;34.0.0"

WORKDIR /proj
