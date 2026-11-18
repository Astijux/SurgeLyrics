# Surge Lyrics

Synced Spotify lyrics on your HUD + Surge-style FPS culling, one mod.

## Layout
```
core/         version-independent Java 21, zero dependencies  (400+ unit checks, all pass)
mc1_21_11/    Fabric adapter for Minecraft 1.21.11            (focus build; NOT yet compiled)
```
Everything that is logic (LRC parsing, lyric chunking/fading, Spotify PKCE login, LRCLIB cache, settings, Surge
culling decisions and adaptive easing) lives in `core` and is shared. A Minecraft version is just a thin adapter:
mixins, a HUD element, a settings screen, a keybind. To add a version, copy `mc1_21_11`, change `gradle.properties`
and mappings, port those 5 small classes, add it to `settings.gradle`.

## Get the jar with zero setup (GitHub) - the one-file way
GitHub's web uploader silently drops anything past 100 files per upload, and this project has ~145 files (mostly fonts). So:
1. Do **not** extract `surgelyrics.zip`. In your repo choose **Add file -> Upload files** and upload the zip itself (one file). Commit.
2. Open `.github/workflows/main.yml`, click the pencil, replace everything with the `main.yml` that came with the download, commit.
3. **Actions** tab -> wait for the green check -> open the run -> **Artifacts** -> download `surgelyrics-1.21.11`.
4. Unzip it and put the `.jar` in your instance's `mods` folder. Needs Fabric Loader 0.18+ and Fabric API for 1.21.11.
The build checks that all 34 font files are present and fails loudly if not. The old extract-and-upload way still works if you upload in batches of under 100.

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
- **Fonts:** 35 of them (Script handwriting, italics, classic and display serifs, heavy, narrow, several monos, techno, bold and light weights ...), paged, each shown in itself. Add your own by dropping `name.ttf` + `name.json` into `assets/surgelyrics/font/` and one line in `FontStyle`.
  To add one: drop `name.ttf` + `name.json` into `assets/surgelyrics/font/` and add a line to `FontStyle`.
- **Layout:** 3x3 anchor grid with a mini-screen, plus horizontal/vertical offsets, and the bar at top or bottom-left.
- **Colour:** 12 presets, a hex box (#RRGGBB or #RGB), hue/saturation/brightness sliders with gradient tracks, gradient Off / Static / Flow / Rainbow / Shine (a bright band sweeps the line) / Pulse (the line breathes) / Sparkle (letters twinkle).
- **Effects:** flat, 3D extruded (thickness + light angle) or outline text; glow strength; motion; a 3D settings window that leans with the mouse.

## Flair and Packs tabs
- **Flair:** backdrop card behind the lyrics (glass / dark / bar), reflection under the line, wave letters (each letter rides a wave),
  twinkling sparkles, a progress underline, and a screen-edge glow on beats (needs Audio reactive).
- **Packs / Window:** twelve glass tints (Midnight, Ocean, Plum, Forest, Rose, Slate, Crimson, Amber, Teal, Indigo, Mocha, Graphite), glass opacity, drifting colour clouds and a light sweep on the
  settings window, the 3D window toggle and lyric bob motion. Theme and opacity apply to the HUD panels, widgets and hotbar too.

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

## PvP helpers (PvP tab) - information only, no cheats
Target health (name, hearts, armor, distance; last hit or crosshair), potion timers that blink when about to run out, item counter
(totems, gapples, pearls, crystals, XP, arrows), coordinates with facing and a Nether/Overworld line, standalone CPS, a low-health
screen pulse, a low-armor flash, a no-totem alert, your own health, speed (blocks per second) and sprint/sneak stance, and
hold-to-zoom (key C). Nothing changes reach, hitboxes, aim or sends anything to servers.

## Packs (Packs tab)
18 one-click packs. **Looks** (Clean, Neon, Cinema, Retro, Sunset, Ocean, Midnight, Sakura, Gold, Hacker, Vapor, Minimal) set font,
colours, effects and the window theme. **PvP loadouts** (PvP, PvP Lite, PvP Crystal, PvP Sword, PvP Archer, PvP Full) switch the right
PvP widgets and alerts on and keep the lyrics small. Packs never touch your accounts. The Window page has 12 glass themes plus opacity and motion.

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

## If the game crashes or something misbehaves
- **Crash shield:** every feature runs inside a guard. If one throws, it switches itself off for that session, the game keeps going,
  and the reason is written to `config/surgelyrics-errors.log` (the settings window also lists what was switched off).
- **Crash report:** vanilla launcher `.minecraft/crash-reports/`; Prism/MultiMC: instance -> Minecraft folder -> `crash-reports/` and `logs/latest.log`.
- **Safe config:** edit `config/surgelyrics.json` with the game closed: set `"surgeProfile": "VANILLA"`, `"occludeMobs": false`,
  `"occludeBlocks": false`, `"reactive": false`, and `"on": false` inside `hud` -> `hotbar`, then relaunch.
