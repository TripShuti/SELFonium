# Збірка

## Інструменти

| Компонент | Версія |
|---|---|
| JDK | 17 або 21; Java source level 17 |
| Android SDK | Platform 35, Build Tools 35.0.0 |
| Мінімальний Android | 8.0 / API 26 |
| Android Gradle Plugin | 8.9.2 |
| Gradle | 8.11.1 |
| Python | 3.11+ |

Bash, curl, unzip та SHA-256 tooling потрібні для Linux-скриптів. Для fish запускай скрипти через `bash`.

## Наявний SDK

Встанови компоненти через Android Studio SDK Manager або `sdkmanager`:

```sh
sdkmanager --licenses
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
bash scripts/build.sh
```

Укажи `JAVA_HOME` для потрібного JDK і `ANDROID_HOME` для SDK, якщо вони не визначаються автоматично. Скрипт також перевіряє `ANDROID_SDK_ROOT`, локальну `.tooling/android-sdk`, стандартний Linux SDK path та `/opt/android-sdk`.

## Локальне встановлення на Linux

```sh
bash scripts/setup-android.sh
```

SDK і Gradle зберігаються в ігнорованій Git директорії `.tooling`. Скрипт отримує command-line tools 12.0 з офіційного репозиторію Google та перевіряє їх за metadata/checksum. Ліцензії SDK переглядаються інтерактивно. Системні пакети не встановлюються.

`gradlew` — Bash launcher із закріпленою версією та SHA-256 дистрибутива Gradle. Він підтримує звичайні Gradle arguments, але не є стандартним wrapper JAR. Для Android Studio вибери Gradle 8.11.1 / JDK 21; можна використати локальний дистрибутив `.tooling/gradle-8.11.1`.

## Результати

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Lint: `app/build/reports/lint-results-debug.html`.
- Установлення: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Оновлення поверх установленого застосунку потребує такого самого application ID і signing key. CI створює тимчасову debug-збірку; її підпис може відрізнятися від локального. Збереження релізного ключа й release signing поки налаштовуються окремо.

## Швидкі перевірки

```sh
bash scripts/test-core.sh
```

Після однієї повної збірки можна використати кеші для окремої перевірки Java, Android resources та Lint без Gradle daemon:

```sh
python3 scripts/check-android-cached.py
```

Цей скрипт не пакує інсталяційний APK і не запускає тести на телефоні. Звіти пишуться у Git-ігноровану директорію `build/reports`.
