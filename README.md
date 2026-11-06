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
