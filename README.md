# Красная панель

Лёгкий нативный лаунчер для телевизора F43G8000C с Android 9. Он не использует WebView, сеть, фоновые службы и аналитику. Управление рассчитано на пульт: стрелки, OK, Назад и Меню.

На этом телевизоре включён системный режим контрастного текста: Android обводит все подписи. Если обводка не нужна, выключите этот режим в настройках доступности телевизора; сам лаунчер его не меняет.

Вкладка «Приложения» открывает Кинопоиск, VK Видео, Яндекс Браузер, YouTube и AmneziaVPN. Вкладка «Выходы» показывает HDMI 1–3 и эфирное ТВ. Шестерёнка открывает настройки телевизора или заводскую оболочку. HDMI открывается через штатный плеер YaOS; при ошибке вызывается штатный выбор источника.

## Сборка

Нужны Android SDK Build Tools 35.0.0 и Android Platform 28 в `/home/joost/.cache/android-sdk-minimal`, а также JBR из PyCharm. Сборка: `bash build.sh`. APK появится в `build/red-panel.apk`; подпись для обновлений лежит в `build/keys/red-panel.jks`. Сохраняйте этот ключ.

Файлы SDK взяты из `https://dl.google.com/android/repository/build-tools_r35_linux.zip` и `https://dl.google.com/android/repository/platform-28_r06.zip`. Их SHA-1: `2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc` и `9a4e52b1d55bd2e24216b150aafae2503d3efba6`.

## Установка и возврат

Установить: `adb install -r build/red-panel.apk`. Выбрать основной домашний экран: `adb shell cmd package set-home-activity com.redpanel.tv/.MainActivity`. Вернуть заводской: `adb shell cmd package set-home-activity com.yandex.tv.home/.HomeActivity`. Заводская оболочка также открывается через шестерёнку в самой панели.
