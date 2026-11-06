# Changelog

## 3.3 · Cache clearing on Pixel (2026-11-06)

- Fixed Clear cache not being found on Pixel phones running Android 16 and 17
- Katharia now picks the exact "Storage & cache" row first, taps it again if the page was still loading, and recognises differently worded Clear cache buttons
- If something still can't be found, the sweep log now lists what was on screen so it can be fixed quickly
- New: this changelog. You'll see what's new after each update, and the full history is in the palette menu

## 3.2 · Cache clearing on more phones (2026-11-06)

- Recognises more Storage row names ("Storage usage", "Storage used", "App storage")
- Xiaomi, Redmi and POCO: clears cache through the Clear data menu
- Confirms "Clear cache?" pop-ups on phones that ask
- Checks that the cache really cleared, and the sweep log says why if it didn't

## 3.1 · Close what's running (2026-11-06)

- New Running mode: only force-stops apps you've used since the last stop-sweep
- Pure black option for OLED screens, just like Bataria
- Fixed apps not being cleaned on Settings screens built with Jetpack Compose (Android 14 and later)

## 3.0 · Hello, Katharia (2026-11-06)

- Sweepr is now called Katharia, Greek for "clean"
- New welcome tour that walks you through setup
- Updates now install over the previous version and keep your permissions
- Optional auto-restore: with a one-time computer command, Katharia can switch itself back on

## 2.2 · Faster start (2026-11-06)

- Sweeps start instantly instead of measuring every app first
- App info pages swap in place, with no flash of the app between apps
- New sweep log with per-app timings

## 2.1 · Much faster sweeps (2026-11-06)

- Apps that are already stopped or have almost no cache are skipped instantly
- Reacts the moment Settings updates, with no fixed pauses
- Shows how long each sweep took

## 2.0 · The big upgrade (2026-11-06)

- Animated cache orb, radar progress ring, live ticker and a celebration when you're done
- Quick Settings tile and a Sweep now home-screen shortcut
- Auto-protects your keyboard, launcher, phone and SMS apps
- Sorting, all-time stats and the Bataria appearance options

## 1.1 · Looks like Bataria (2026-11-06)

- Same theme, aurora background, cards, floating button and icon style as Bataria

## 1.0 · First release (2026-11-06)

- Clear cache and force stop every app you pick, using Accessibility
