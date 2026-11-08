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

## Look and feel (all in the K menu)
- **Display modes:** Chunk (a few words at a time), Line, Type (typewriter), Sing (karaoke fill), Scroll (previous / current / next).
- **Fonts:** 23 of them (Script cursive, italic and classic serifs, display, heavy, narrow, mono, techno, pixel, ...), paged, each shown in itself.
  To add one: drop `name.ttf` + `name.json` into `assets/surgelyrics/font/` and add a line to `FontStyle`.
- **Layout:** 3x3 anchor grid with a mini-screen, plus horizontal/vertical offsets, and the bar at top or bottom-left.
- **Colour:** 12 presets, a hex box (#RRGGBB or #RGB), hue/saturation/brightness sliders with gradient tracks, gradient Off / Static / Flow / Rainbow.
- **Effects:** flat, 3D extruded (thickness + light angle) or outline text; glow strength; motion; a 3D settings window that leans with the mouse.

## HUD widgets (HUD tab)
Armor status (icons, durability as bar / number / percent, vertical or horizontal, optional held items), keystrokes (WASD, space,
mouse buttons with CPS), ping, session timer (since launch or since joining the world), reach of your last hit, combo counter.
Each has its own position grid, offsets, scale, colour (hex), and optional glass background.

## Surge (FPS booster)
- **Profiles:** Vanilla, Balanced, Aggressive, Potato, Extreme, PvP (long mob sight, cheap everything else) and Custom.
- **Culling tab:** separate distances for mobs, dropped items, item frames/armor stands, chests/signs, name tags and particles,
  a particles-per-tick cap, and hide clouds / weather. Moving a slider switches to Custom; "Copy current profile" seeds it.
- **Never hidden:** you, other players, your pets, your mount, bosses, glowing mobs.
- **Hide behind walls:** mobs, chests and signs that are completely hidden by solid blocks are not drawn (throttled line-of-sight
  checks, cached ~0.12 s; anything uncertain, close, large or behind glass/leaves stays visible).
- **Cheap HUD:** panels are drawn as a handful of merged rectangles and gradient text is cached, so the mod's own HUD costs little.
- **Adaptive easing** watches the average FPS *and* the 1% low (stutter), tightens every range when the game struggles and relaxes when it recovers.
- **Background FPS cap** while the window isn't focused (default 20).
- **Lighten lyrics when slow:** glow and 3D text layers are skipped while Surge is easing.
- **FPS overlay** shows average, 1% low, easing level and what was culled per second.
- It never edits your video options, so a crash can't leave your settings changed.

## Audio reactive HUD (Audio tab, optional, Windows)
Reads your system output level (the volume-mixer peak meter) and drives: lyric scale/glow pulses on beats, five dancing bars in the
now-playing bar, and a glass glow on the HUD widgets. Strength slider and per-effect toggles. Off by default; the meter only runs while on.

## Lyric transitions (Effects tab)
Fade, Slide, Zoom, Bounce or Words (word-by-word reveal); the old line animates out as the new one animates in. Duration 150-800 ms.
The preview strip shows the chosen transition live.

## Custom hotbar (HUD tab -> Hotbar)
Glass / Dark / Minimal styles, a highlight that glides between slots, the selected item pops up, slot numbers, optional offhand,
any anchor and offset, scale. Off by default (the vanilla hotbar stays until you enable it).

## Audio sources
Settings (K) -> **Source**: Auto, Windows, or Spotify.
- **Windows** reads the system media session (the same card as the volume flyout), so Spotify, Apple Music, browsers, VLC and
  anything else that reports to Windows work, with no login. It runs one hidden PowerShell process that exits when Minecraft does.
- **Spotify** uses the Spotify Web API (needs a Client ID, below).
- **Auto** uses whichever is actually playing, preferring Windows.

## Spotify setup (only needed for the Spotify source)
Create an app at developer.spotify.com, add Redirect URI `http://127.0.0.1:19542/callback`, paste the Client ID
in settings (K), click Connect. Tokens are stored in `config/surgelyrics-auth.json`, separate from settings.
