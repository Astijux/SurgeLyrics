# Surge Lyrics

Synced Spotify lyrics on your HUD + Surge-style FPS culling, one mod.

## Layout
```
core/         version-independent Java 21, zero dependencies  (57 unit tests, all pass)
mc1_21_11/    Fabric adapter for Minecraft 1.21.11            (focus build; NOT yet compiled)
```
Everything that is logic (LRC parsing, lyric chunking/fading, Spotify PKCE login, LRCLIB cache, settings, Surge
culling decisions and adaptive easing) lives in `core` and is shared. A Minecraft version is just a thin adapter:
mixins, a HUD element, a settings screen, a keybind. To add a version, copy `mc1_21_11`, change `gradle.properties`
and mappings, port those 5 small classes, add it to `settings.gradle`.

## Get the jar with zero setup (GitHub)
1. Create a new GitHub repository and upload the contents of this folder (including `.github`).
2. Open the **Actions** tab, wait about 3 minutes for "Build mod jar", open the run, download **surgelyrics-1.21.11**.
3. Unzip it and drop `surgelyrics-1.0.0+1.21.11.jar` into your launcher instance's `mods` folder
   (Prism / MultiMC / CurseForge / Modrinth app / vanilla launcher all work).
   The instance needs **Fabric Loader 0.18+** and **Fabric API** for 1.21.11.

If the build fails, the log names the exact line to fix; send it to me.

## Build locally
```
gradle :mc1_21_11:build        # jar in mc1_21_11/build/libs
java -cp core/out dev.surgelyrics.core.CoreTests   # or run CoreTests from your IDE
```

## First-build checklist (written offline, so these were not checkable)
1. `mc1_21_11/gradle.properties`: confirm `yarn_mappings` and `fabric_api_version` at https://fabricmc.net/develop.
2. Every line marked `VERIFY` in the adapter (mixin method names, `KeyBinding.Category.create`,
   `HudElementRegistry.addLast`, `Matrix3x2fStack` calls). Compile errors will point at them.
3. All mixin injectors use `require = 0`, so a wrong method name means that one optimization silently does nothing
   instead of crashing the game. In `latest.log`, search for "surgelyrics" mixin warnings after first launch.

## Audio sources
Settings (K) -> **Source**: Auto, Windows, or Spotify.
- **Windows** reads the system media session (the same card as the volume flyout), so Spotify, Apple Music, browsers, VLC and
  anything else that reports to Windows work, with no login. It runs one hidden PowerShell process that exits when Minecraft does.
- **Spotify** uses the Spotify Web API (needs a Client ID, below).
- **Auto** uses whichever is actually playing, preferring Windows.

## Spotify setup (only needed for the Spotify source)
Create an app at developer.spotify.com, add Redirect URI `http://127.0.0.1:19542/callback`, paste the Client ID
in settings (K), click Connect. Tokens are stored in `config/surgelyrics-auth.json`, separate from settings.
