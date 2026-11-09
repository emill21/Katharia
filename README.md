# Katharia

*καθαριά: Greek for "clean".* Clear the cache of every app and force-stop them all, in one tap.

Android doesn't allow normal apps to do either of these to other apps, so Katharia uses an
Accessibility service: it opens each selected app's page in Settings and taps **Force stop**
and **Storage → Clear cache** for you. It never taps *Clear storage / Clear data*.

## Install
Grab `Katharia.apk` from the latest [release](../../releases). The welcome tour walks you through
turning it on. Every build is signed with the same key (`keystore/`), so updates install over the
previous version and keep your permissions.

Optional, once: `adb shell pm grant com.katharia.app android.permission.WRITE_SECURE_SETTINGS`
lets Katharia switch its own Accessibility service back on if Android ever turns it off.

## Wear OS
Katharia also runs on Wear OS 3+ watches (`wear/` module). It uses the same sweep engine as the
phone app (the files are shared at build time), taught to scroll the watch's round Settings screens.

1. Download `Katharia-Wear.apk` from the latest release.
2. On the watch: **Settings › Developer options › Wireless debugging** on (tap Build number 7 times
   in **Settings › System › About** to unlock Developer options).
3. From a computer: `adb pair <ip:port>` then `adb connect <ip:port>`, then
   `adb install Katharia-Wear.apk`. (Phone apps such as *Wear Installer 2* or *Bugjaeger* can do the
   same without a computer.)
4. Turn it on. Either **Settings › Accessibility › Katharia** on the watch, or, if your watch doesn't
   list it, `adb shell pm grant com.katharia.app android.permission.WRITE_SECURE_SETTINGS` and open
   Katharia once: it switches itself on.
5. Optional, for cache sizes: `adb shell appops set com.katharia.app GET_USAGE_STATS allow`.

Swipe between **Sweep**, **Apps** and **Settings**; add the **Katharia sweep** tile to sweep from your
tiles. Because the APK is installed with adb, Android's "restricted setting" block doesn't apply.
