# Sweepr

Clear the cache of every app and force-stop them all, in one tap.

Android doesn't allow normal apps to do either of these to other apps, so Sweepr uses an
Accessibility service: it opens each selected app's page in Settings and taps **Force stop**
and **Storage → Clear cache** for you, with a progress banner and a Stop button.
It never taps *Clear storage / Clear data*, and does nothing unless you start a sweep.

## Install
Grab `Sweepr.apk` from the latest [release](../../releases), open it, then:
1. Android 13+: App info → ⋮ → **Allow restricted settings** (needed for sideloaded apps).
2. Settings → Accessibility → Installed apps → **Sweepr** → On.
3. Optional: allow **Usage access** to see cache sizes and how much was freed.

## Build
Every push builds a signed (debug-key) APK with GitHub Actions; pushes to `main` publish a release.
