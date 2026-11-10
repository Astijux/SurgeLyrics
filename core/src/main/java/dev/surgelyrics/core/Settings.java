package dev.surgelyrics.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** All user settings. Loading never throws: bad or missing values fall back to defaults and are clamped. */
public final class Settings {
    // lyrics
    public boolean lyricsEnabled = true;
    public boolean showBar = true;
    public int displayMode = 0;        // 0 chunks, 1 full line, 2 typewriter, 3 karaoke fill, 4 scroll
    public int font = 0;               // index into FontStyle
    public int anchor = 4;             // 0..8 grid, see Layout
    public int offX = 0, offY = 30;    // pixels from the anchor (GUI scale)
    public boolean barBottom = false;
    public int depthStyle = 0;         // 0 flat, 1 3D extrude, 2 outline
    public int depthAmount = 4;        // 1..8 layers
    public int lightAngle = 45;        // 0..359 degrees, direction the 3D extrusion points
    public int gradientMode = 1;       // 0 off, 1 static, 2 flow, 3 rainbow
    public int gradientSpeed = 4;      // 1..10
    public int glowStrength = 50;      // 0..100 (0 = no glow)
    public int opacity = 100;          // 25..100
    public boolean window3d = true;    // 3D floating settings window
    public int intensity = 1;          // 0 calm, 1 normal, 2 lively
    public int wordsPerLine = 4;       // 1..8
    public float staySeconds = 3.5f;   // 1..10
    public int sizePercent = 100;      // 50..200
    public int syncOffsetMs = 0;       // -3000..3000
    public int colour = 0xFF7DD3FC;
    public int colour2 = 0xFFC084FC;
    public int source = 0;             // 0 auto, 1 Windows media, 2 Spotify API
    public int redirectPort = 19542;
    public String spotifyClientId = "";
    // HUD widgets
    public final HudWidget armor = new HudWidget(true, 5, 0, 0), keys = new HudWidget(true, 6, 6, -52),
            ping = new HudWidget(false, 2, 0, 0), timer = new HudWidget(true, 0, 0, 34),
            reach = new HudWidget(false, 3, 0, -30), combo = new HudWidget(false, 3, 0, 0),
            hotbar = new HudWidget(false, 7, 0, 10);
    public boolean armorVertical = true, armorHands = false;
    public int armorDur = 1;                 // 0 none, 1 bar, 2 number, 3 percent
    public boolean keysMouse = true, keysSpace = true, keysCps = true;
    public boolean pingColorize = true;
    public int timerMode = 0;                // 0 since game launch, 1 since joining this world/server
    public int reachDecimals = 2;            // 1..3
    public int comboSeconds = 3;             // 1..10
    public int hotbarStyle = 0;              // 0 glass, 1 dark, 2 minimal
    public boolean hotbarNumbers = true, hotbarOffhand = true, hotbarAnimate = true;
    // flair
    public int backdrop = 0;                 // 0 none, 1 glass, 2 dark, 3 bar
    public int reflection = 0;               // 0..60 percent
    public int waveAmp = 0;                  // 0..6 pixels (0 = off); letters drawn one by one
    public boolean sparkles = false, underline = false, edgeGlow = false;
    // theme
    public int uiTheme = 0;                  // index into Themes.ALL
    public int glassOpacity = 100;           // 30..100 percent, scales every glass surface
    public boolean windowGlow = true, windowSweep = true;
    // lyric transitions
    public int transition = 1;               // 0 fade, 1 slide, 2 zoom, 3 bounce, 4 words
    public int transitionMs = 320;           // 150..800
    // audio reactive (Windows)
    public boolean reactive = false, reactLyrics = true, reactBars = true, reactWidgets = true;
    public int reactStrength = 60;           // 0..100
    // occlusion culling
    public boolean occludeMobs = true, occludeBlocks = true;
    // surge
    public String surgeProfile = "AGGRESSIVE";
    public boolean surgeAdaptive = true;
    public boolean surgeOverlay = false;
    public int targetFps = 90;         // 30..240
    public int backgroundFps = 20;     // 0 = off, else 5..60: cap while the window isn't focused
    public boolean lightenLyrics = true;   // drop glow / 3D layers while Surge is easing
    // custom culling limits (used when surgeProfile = CUSTOM); distances in blocks, 0 = no limit
    public int cMob = 64, cItem = 40, cDeco = 40, cBlockEntity = 48, cNametag = 48, cParticleDist = 48, cParticleCap = 600;
    public boolean cClouds = false, cWeather = false;

    private static int cl(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    public HudWidget[] widgets() { return new HudWidget[]{armor, keys, ping, timer, reach, combo, hotbar}; }
    public static final String[] WIDGET_NAMES = {"armor", "keys", "ping", "timer", "reach", "combo", "hotbar"};

    public void clamp() {
        for (HudWidget w : widgets()) w.clamp();
        armorDur = cl(armorDur, 0, 3); timerMode = cl(timerMode, 0, 1);
        reachDecimals = cl(reachDecimals, 1, 3); comboSeconds = cl(comboSeconds, 1, 10);
        backdrop = cl(backdrop, 0, 3); reflection = cl(reflection, 0, 60); waveAmp = cl(waveAmp, 0, 6);
        uiTheme = cl(uiTheme, 0, Themes.ALL.length - 1); glassOpacity = cl(glassOpacity, 30, 100);
        hotbarStyle = cl(hotbarStyle, 0, 2); transition = cl(transition, 0, 4); transitionMs = cl(transitionMs, 150, 800);
        reactStrength = cl(reactStrength, 0, 100);
        intensity = Math.max(0, Math.min(2, intensity));
        wordsPerLine = Math.max(1, Math.min(8, wordsPerLine));
        staySeconds = Math.max(1f, Math.min(10f, staySeconds));
        sizePercent = Math.max(50, Math.min(200, sizePercent));
        syncOffsetMs = Math.max(-3000, Math.min(3000, syncOffsetMs));
        targetFps = Math.max(30, Math.min(240, targetFps));
        source = Math.max(0, Math.min(2, source));
        backgroundFps = backgroundFps <= 0 ? 0 : Math.max(5, Math.min(60, backgroundFps));
        cMob = cl(cMob, 0, 192); cItem = cl(cItem, 0, 192); cDeco = cl(cDeco, 0, 192); cBlockEntity = cl(cBlockEntity, 0, 192);
        cNametag = cl(cNametag, 0, 192); cParticleDist = cl(cParticleDist, 0, 192); cParticleCap = cl(cParticleCap, 0, 2000);
        displayMode = Math.max(0, Math.min(4, displayMode));
        font = Math.max(0, Math.min(FontStyle.values().length - 1, font));
        anchor = Math.max(0, Math.min(8, anchor));
        offX = Math.max(-300, Math.min(300, offX));
        offY = Math.max(-300, Math.min(300, offY));
        depthStyle = Math.max(0, Math.min(2, depthStyle));
        depthAmount = Math.max(1, Math.min(8, depthAmount));
        lightAngle = ((lightAngle % 360) + 360) % 360;
        gradientMode = Math.max(0, Math.min(Colors.GRADIENT_MODES.length - 1, gradientMode));
        gradientSpeed = Math.max(1, Math.min(10, gradientSpeed));
        glowStrength = Math.max(0, Math.min(100, glowStrength));
        opacity = Math.max(25, Math.min(100, opacity));
        redirectPort = (redirectPort < 1024 || redirectPort > 65535) ? 19542 : redirectPort;
        if (spotifyClientId == null) spotifyClientId = "";
        spotifyClientId = spotifyClientId.strip();
        if (SurgeProfile.byName(surgeProfile) == null) surgeProfile = "BALANCED";
    }

    public static Settings load(Path file) {
        Settings s = new Settings();
        try {
            if (Files.exists(file)) {
                Map<String, Object> m = Json.obj(Json.parse(Files.readString(file)));
                s.lyricsEnabled = Json.bool(m.get("lyricsEnabled"), s.lyricsEnabled);
                s.showBar = Json.bool(m.get("showBar"), s.showBar);
                s.displayMode = (int) Json.num(m.get("displayMode"), s.displayMode);
                s.font = (int) Json.num(m.get("font"), s.font);
                s.anchor = (int) Json.num(m.get("anchor"), s.anchor);
                s.offX = (int) Json.num(m.get("offX"), s.offX);
                s.offY = (int) Json.num(m.get("offY"), s.offY);
                s.barBottom = Json.bool(m.get("barBottom"), s.barBottom);
                s.depthStyle = (int) Json.num(m.get("depthStyle"), s.depthStyle);
                s.depthAmount = (int) Json.num(m.get("depthAmount"), s.depthAmount);
                s.lightAngle = (int) Json.num(m.get("lightAngle"), s.lightAngle);
                s.gradientMode = (int) Json.num(m.get("gradientMode"), s.gradientMode);
                s.gradientSpeed = (int) Json.num(m.get("gradientSpeed"), s.gradientSpeed);
                s.glowStrength = (int) Json.num(m.get("glowStrength"), s.glowStrength);
                s.opacity = (int) Json.num(m.get("opacity"), s.opacity);
                s.window3d = Json.bool(m.get("window3d"), s.window3d);
                s.intensity = (int) Json.num(m.get("intensity"), s.intensity);
                s.wordsPerLine = (int) Json.num(m.get("wordsPerLine"), s.wordsPerLine);
                s.staySeconds = (float) Json.num(m.get("staySeconds"), s.staySeconds);
                s.sizePercent = (int) Json.num(m.get("sizePercent"), s.sizePercent);
                s.syncOffsetMs = (int) Json.num(m.get("syncOffsetMs"), s.syncOffsetMs);
                s.colour = (int) (long) Json.num(m.get("colour"), s.colour);
                s.colour2 = (int) (long) Json.num(m.get("colour2"), s.colour2);
                s.redirectPort = (int) Json.num(m.get("redirectPort"), s.redirectPort);
                s.source = (int) Json.num(m.get("source"), s.source);
                Map<String, Object> hud = Json.obj(m.get("hud"));
                HudWidget[] ws = s.widgets();
                for (int i = 0; i < ws.length; i++) ws[i].load(Json.obj(hud.get(WIDGET_NAMES[i])));
                s.armorVertical = Json.bool(m.get("armorVertical"), s.armorVertical); s.armorHands = Json.bool(m.get("armorHands"), s.armorHands);
                s.armorDur = (int) Json.num(m.get("armorDur"), s.armorDur);
                s.keysMouse = Json.bool(m.get("keysMouse"), s.keysMouse); s.keysSpace = Json.bool(m.get("keysSpace"), s.keysSpace);
                s.keysCps = Json.bool(m.get("keysCps"), s.keysCps); s.pingColorize = Json.bool(m.get("pingColorize"), s.pingColorize);
                s.timerMode = (int) Json.num(m.get("timerMode"), s.timerMode); s.reachDecimals = (int) Json.num(m.get("reachDecimals"), s.reachDecimals);
                s.comboSeconds = (int) Json.num(m.get("comboSeconds"), s.comboSeconds);
                s.backdrop = (int) Json.num(m.get("backdrop"), s.backdrop); s.reflection = (int) Json.num(m.get("reflection"), s.reflection);
                s.waveAmp = (int) Json.num(m.get("waveAmp"), s.waveAmp); s.sparkles = Json.bool(m.get("sparkles"), s.sparkles);
                s.underline = Json.bool(m.get("underline"), s.underline); s.edgeGlow = Json.bool(m.get("edgeGlow"), s.edgeGlow);
                s.uiTheme = (int) Json.num(m.get("uiTheme"), s.uiTheme); s.glassOpacity = (int) Json.num(m.get("glassOpacity"), s.glassOpacity);
                s.windowGlow = Json.bool(m.get("windowGlow"), s.windowGlow); s.windowSweep = Json.bool(m.get("windowSweep"), s.windowSweep);
                s.hotbarStyle = (int) Json.num(m.get("hotbarStyle"), s.hotbarStyle);
                s.hotbarNumbers = Json.bool(m.get("hotbarNumbers"), s.hotbarNumbers); s.hotbarOffhand = Json.bool(m.get("hotbarOffhand"), s.hotbarOffhand);
                s.hotbarAnimate = Json.bool(m.get("hotbarAnimate"), s.hotbarAnimate);
                s.transition = (int) Json.num(m.get("transition"), s.transition); s.transitionMs = (int) Json.num(m.get("transitionMs"), s.transitionMs);
                s.reactive = Json.bool(m.get("reactive"), s.reactive); s.reactLyrics = Json.bool(m.get("reactLyrics"), s.reactLyrics);
                s.reactBars = Json.bool(m.get("reactBars"), s.reactBars); s.reactWidgets = Json.bool(m.get("reactWidgets"), s.reactWidgets);
                s.reactStrength = (int) Json.num(m.get("reactStrength"), s.reactStrength);
                s.occludeMobs = Json.bool(m.get("occludeMobs"), s.occludeMobs); s.occludeBlocks = Json.bool(m.get("occludeBlocks"), s.occludeBlocks);
                s.spotifyClientId = Json.str(m.get("spotifyClientId"), "");
                s.surgeProfile = Json.str(m.get("surgeProfile"), s.surgeProfile);
                s.surgeAdaptive = Json.bool(m.get("surgeAdaptive"), s.surgeAdaptive);
                s.surgeOverlay = Json.bool(m.get("surgeOverlay"), s.surgeOverlay);
                s.targetFps = (int) Json.num(m.get("targetFps"), s.targetFps);
                s.backgroundFps = (int) Json.num(m.get("backgroundFps"), s.backgroundFps);
                s.lightenLyrics = Json.bool(m.get("lightenLyrics"), s.lightenLyrics);
                s.cMob = (int) Json.num(m.get("cMob"), s.cMob); s.cItem = (int) Json.num(m.get("cItem"), s.cItem);
                s.cDeco = (int) Json.num(m.get("cDeco"), s.cDeco); s.cBlockEntity = (int) Json.num(m.get("cBlockEntity"), s.cBlockEntity);
                s.cNametag = (int) Json.num(m.get("cNametag"), s.cNametag); s.cParticleDist = (int) Json.num(m.get("cParticleDist"), s.cParticleDist);
                s.cParticleCap = (int) Json.num(m.get("cParticleCap"), s.cParticleCap);
                s.cClouds = Json.bool(m.get("cClouds"), s.cClouds); s.cWeather = Json.bool(m.get("cWeather"), s.cWeather);
            }
        } catch (Exception ignored) {
            // corrupt file: keep defaults; the next save() rewrites it cleanly
        }
        s.clamp();
        return s;
    }

    /** Atomic save: write a temp file then move it over the real one. */
    public void save(Path file) {
        clamp();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lyricsEnabled", lyricsEnabled); m.put("showBar", showBar); m.put("intensity", intensity); m.put("wordsPerLine", wordsPerLine);
        m.put("staySeconds", (double) staySeconds); m.put("sizePercent", sizePercent);
        m.put("syncOffsetMs", syncOffsetMs); m.put("colour", (long) colour & 0xFFFFFFFFL);
        m.put("colour2", (long) colour2 & 0xFFFFFFFFL); m.put("redirectPort", redirectPort); m.put("source", source);
        Map<String, Object> hud = new LinkedHashMap<>();
        HudWidget[] ws = widgets();
        for (int i = 0; i < ws.length; i++) hud.put(WIDGET_NAMES[i], ws[i].toMap());
        m.put("hud", hud);
        m.put("armorVertical", armorVertical); m.put("armorHands", armorHands); m.put("armorDur", armorDur);
        m.put("keysMouse", keysMouse); m.put("keysSpace", keysSpace); m.put("keysCps", keysCps);
        m.put("pingColorize", pingColorize); m.put("timerMode", timerMode); m.put("reachDecimals", reachDecimals);
        m.put("comboSeconds", comboSeconds);
        m.put("backdrop", backdrop); m.put("reflection", reflection); m.put("waveAmp", waveAmp); m.put("sparkles", sparkles);
        m.put("underline", underline); m.put("edgeGlow", edgeGlow); m.put("uiTheme", uiTheme); m.put("glassOpacity", glassOpacity);
        m.put("windowGlow", windowGlow); m.put("windowSweep", windowSweep);
        m.put("hotbarStyle", hotbarStyle); m.put("hotbarNumbers", hotbarNumbers); m.put("hotbarOffhand", hotbarOffhand);
        m.put("hotbarAnimate", hotbarAnimate); m.put("transition", transition); m.put("transitionMs", transitionMs);
        m.put("reactive", reactive); m.put("reactLyrics", reactLyrics); m.put("reactBars", reactBars);
        m.put("reactWidgets", reactWidgets); m.put("reactStrength", reactStrength);
        m.put("occludeMobs", occludeMobs); m.put("occludeBlocks", occludeBlocks);
        m.put("spotifyClientId", spotifyClientId); m.put("surgeProfile", surgeProfile);
        m.put("surgeAdaptive", surgeAdaptive); m.put("surgeOverlay", surgeOverlay);
        m.put("targetFps", targetFps); m.put("backgroundFps", backgroundFps); m.put("lightenLyrics", lightenLyrics);
        m.put("cMob", cMob); m.put("cItem", cItem); m.put("cDeco", cDeco); m.put("cBlockEntity", cBlockEntity);
        m.put("cNametag", cNametag); m.put("cParticleDist", cParticleDist); m.put("cParticleCap", cParticleCap);
        m.put("cClouds", cClouds); m.put("cWeather", cWeather);
        m.put("displayMode", displayMode); m.put("font", font); m.put("anchor", anchor); m.put("offX", offX);
        m.put("offY", offY); m.put("barBottom", barBottom); m.put("depthStyle", depthStyle);
        m.put("depthAmount", depthAmount); m.put("lightAngle", lightAngle); m.put("gradientMode", gradientMode);
        m.put("gradientSpeed", gradientSpeed); m.put("glowStrength", glowStrength); m.put("opacity", opacity);
        m.put("window3d", window3d);
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(tmp, Json.write(m));
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ignored) { }
    }
}
