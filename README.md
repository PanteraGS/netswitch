# Виджет «Сеть 3G/4G»

Виджет на рабочий стол (2×2) с кнопками **3G** и **4G**, плитка для шторки
и приложение для настройки. Для Android 12–16, рассчитан на ColorOS 16 (OPPO Find X9 Pro).
Язык интерфейса — русский или английский, переключается в приложении.

**Скачать APK:** [NetSwitch.apk](../../releases/latest/download/NetSwitch.apk)

## Почему нужен Shizuku
Android не даёт обычным приложениям менять тип сети: для этого нужно системное
разрешение `MODIFY_PHONE_STATE`. Shizuku даёт приложению права ADB (shell) без root,
и через них виджет вызывает системный метод `setAllowedNetworkTypesForReason`.

## Настройка на телефоне (один раз)
1. Установить **Shizuku** (Google Play или https://shizuku.rikka.app/download/).
2. Включить режим разработчика: Настройки → Об устройстве → Версия → 7 раз нажать
   на «Номер версии». Потом Настройки → Дополнительные → Для разработчиков →
   включить **Беспроводную отладку**.
3. В Shizuku: «Запуск через беспроводную отладку» → «Сопряжение» → ввести код из
   уведомления → «Запустить».
4. Установить APK, открыть приложение «Сеть 3G/4G» → **Выдать разрешение**.
5. Добавить виджет: долгое нажатие на рабочем столе → Виджеты → «Сеть 3G/4G».

После перезагрузки телефона Shizuku нужно снова запустить (п. 3, без сопряжения).
На ColorOS стоит разрешить Shizuku автозапуск и работу в фоне.

---

## English

A home screen widget (2×2) with **3G** and **4G** buttons, a Quick Settings tile
and a setup app. Android 12–16.

Android does not allow regular apps to change the network type, so the app needs
[Shizuku](https://shizuku.rikka.app/download/), which grants ADB (shell) rights without root.

1. Install Shizuku and enable Developer options → **Wireless debugging**.
2. In Shizuku: pair via wireless debugging, then tap **Start**.
3. Install the APK, open “Network 3G/4G” → **Grant permission**.
4. Long-press the home screen → Widgets → “Network 3G/4G”.

After a reboot, start Shizuku again. The language can be switched in the app.
