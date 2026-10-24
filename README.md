# Красная панель

Лёгкий нативный лаунчер для телевизора F43G8000C с Android 9. Он не использует WebView, сеть, фоновые службы и аналитику. Управление рассчитано на пульт: стрелки, OK, Назад и Меню.

На этом телевизоре включён системный режим контрастного текста: Android обводит все подписи. Если обводка не нужна, выключите этот режим в настройках доступности телевизора; сам лаунчер его не меняет.

Вкладка «Приложения» открывает Кинопоиск, VK Видео, Яндекс Браузер, YouTube и AmneziaVPN. Вкладка «Выходы» показывает HDMI 1–3 и эфирное ТВ. Шестерёнка открывает настройки телевизора или заводскую оболочку. HDMI открывается через штатный плеер YaOS; при ошибке вызывается штатный выбор источника.

## Что нужно сохранить для восстановления

Исходники и инструкция находятся в этом репозитории: `https://github.com/Offensive94/vibetelevising.git`. APK и ключ подписи находятся в `build/`, который исключён из Git. **Сохраните `build/keys/red-panel.jks` отдельно от компьютера и репозитория.** Для обновления уже установленного приложения нужен тот же ключ. Если его потерять, можно собрать новый APK, но установить его поверх старого не получится: придётся сначала вернуть заводской домашний экран, удалить старый лаунчер и установить новый.

Текущая сборка рассчитана на F43G8000C, Android 9 (SDK 28), разрешение 1280×720. Пакет лаунчера: `com.redpanel.tv`. Стандартный домашний экран: `com.yandex.tv.home/.HomeActivity`. Список приложений и обработка HDMI находятся в `src/com/redpanel/tv/MainActivity.java`; манифест — в `AndroidManifest.xml`. Нативный интерфейс не требует сети или WebView.

## Сборка на этом компьютере

```bash
cd /home/joost/red-panel
bash build.sh
```

APK появится в `build/red-panel.apk`. Скрипт использует локальные Android SDK Build Tools 35.0.0 и Platform 28 из `/home/joost/.cache/android-sdk-minimal`, а также JBR 21 из `/opt/pycharm-2025.2/jbr`. Если ключ уже есть в `build/keys/`, он используется повторно.

## Сборка после клонирования на другом компьютере

1. Установите JDK 21, Python 3 и Android SDK с пакетами **Build Tools 35.0.0** и **Platform 28**. Их можно выбрать в [Android Studio SDK Manager](https://developer.android.com/tools). Для работы с телевизором нужен `adb` (Android SDK Platform Tools).
2. Клонируйте репозиторий и верните сохранённый ключ в `build/keys/red-panel.jks`.
3. Укажите пути к Android SDK и JDK и запустите сборку:

```bash
git clone https://github.com/Offensive94/vibetelevising.git
cd vibetelevising
mkdir -p build/keys
cp /путь/к/резервной-копии/red-panel.jks build/keys/red-panel.jks
export ANDROID_SDK_ROOT=/путь/к/android-sdk
export JAVA_HOME=/путь/к/jdk-21
bash build.sh
```

Скрипт ожидает стандартные каталоги SDK `build-tools/35.0.0` и `platforms/android-28`. Если SDK разложен иначе, можно указать `RP_BUILD_TOOLS`, `RP_PLATFORM` (полный путь к `android.jar`) и `RP_JAVA_BIN` (каталог с `java`, `javac` и `keytool`). Для исходной локальной раскладки использовались официальные архивы `https://dl.google.com/android/repository/build-tools_r35_linux.zip` и `https://dl.google.com/android/repository/platform-28_r06.zip`; их SHA-1: `2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc` и `9a4e52b1d55bd2e24216b150aafae2503d3efba6`.

## Установка, проверка и возврат

Подключите ADB и убедитесь, что телевизор виден в `adb devices`. Адрес телевизора на момент настройки был `192.168.1.104:5555`; DHCP может его изменить.

```bash
adb connect 192.168.1.104:5555
adb devices
adb install -r build/red-panel.apk
adb shell cmd package set-home-activity com.redpanel.tv/.MainActivity
```

Проверить выбор домашнего экрана: `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`. Вернуть заводской: `adb shell cmd package set-home-activity com.yandex.tv.home/.HomeActivity`. Заводская оболочка также открывается через шестерёнку в панели.

Если подпись старого APK отличается, **сначала** переключите домашний экран на заводской, затем выполните `adb uninstall com.redpanel.tv`, установите новый APK и выберите его домашним экраном. Удаление приложения сбрасывает его локальные данные.

HDMI 1–3 соответствуют входам MediaTek `HW6`, `HW7`, `HW8`; они открываются через системный `com.yandex.tv.live`. Эфирное ТВ открывается через `com.mediatek.wwtv.tvcenter`. Если над изображением HDMI появляется ошибка примерно через 20 секунд, она ранее воспроизводилась и при автоматическом переключении PS4 через HDMI-CEC без участия лаунчера; в системном журнале MediaTek сообщал о тайм-ауте выбора устройства. Это отдельная проблема телевизора/CEC, а не ошибка сборки APK.

Дополнительные настройки телевизора, сделанные через ADB ранее, не входят в APK: были отключены `com.yandex.android.advid`, `com.yandex.tv.bugreportsender`, `com.ctv.autotest.helper`, `com.ctv.newfactorytest`; масштабы анимации выставлены в `0.5`. Локальный скрипт возврата этих настроек: `/home/joost/Загрузки/tv-adb-rollback.sh` (он не входит в репозиторий). Не применяйте эти изменения повторно вслепую после сброса телевизора: сначала проверьте состояние и поведение устройства.
