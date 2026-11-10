package dev.surgelyrics.core;

import java.nio.file.*;
import java.util.*;

public final class CoreTests {
    static int pass, fail;
    static void check(String name, boolean ok) { if (ok) pass++; else { fail++; System.out.println("FAIL: " + name); } }
    static void eq(String name, Object a, Object b) { check(name + " (got " + a + ", want " + b + ")", Objects.equals(a, b)); }

    public static void main(String[] args) throws Exception {
        // ---- Json
        Object j = Json.parse("{\"a\":[1,2.5,\"x\\n\\u0041\"],\"b\":{\"c\":true,\"d\":null}}");
        eq("json nested", Json.str(Json.arr(Json.obj(j).get("a")).get(2), ""), "x\nA");
        eq("json bool", Json.bool(Json.obj(Json.obj(j).get("b")).get("c"), false), true);
        String round = Json.write(j);
        eq("json roundtrip", Json.write(Json.parse(round)), round);
        boolean threw = false; try { Json.parse("{\"a\":"); } catch (IllegalArgumentException e) { threw = true; }
        check("json rejects truncated", threw);

        // ---- Lrc
        List<Lrc.Line> l = Lrc.parse("[00:01.5] one\n[00:02.50]two\n[01:00.123]three\n[00:10]\n[00:05][00:20.00] chorus\njunk\n[00:03:25]colon");
        eq("lrc count", l.size(), 6);
        eq("lrc .5 is 500ms", l.get(0).timeMs(), 1500L);
        eq("lrc .50 is 500ms", l.get(1).timeMs(), 2500L);
        eq("lrc colon fraction", l.get(2).timeMs(), 3250L);
        eq("lrc multi-stamp sorted", l.get(3).text(), "chorus");
        eq("lrc 3-digit ms", l.get(5).timeMs(), 60123L);
        eq("lrc blank skipped", l.stream().anyMatch(x -> x.timeMs() == 10000), false);
        eq("indexAt before first", Lrc.indexAt(l, 1000), -1);
        eq("indexAt exact", Lrc.indexAt(l, 1500), 0);
        eq("indexAt mid", Lrc.indexAt(l, 4000), 2);
        eq("indexAt end", Lrc.indexAt(l, 999999), 5);
        eq("indexAt empty", Lrc.indexAt(List.of(), 5), -1);
        eq("lrc null", Lrc.parse(null).size(), 0);

        // ---- Track
        Track t = new Track("id", "T", "A", "", 10_000, 9_000, 0, true);
        eq("track clamps to duration", t.positionMs(5_000_000_000L), 10_000L);
        Track p = new Track("id", "T", "A", "", 10_000, 4_000, 0, false);
        eq("track paused does not advance", p.positionMs(9_000_000_000L), 4_000L);

        // ---- LrcLib with fake transport
        List<String> urls = new ArrayList<>();
        Net.Fetcher fake = url -> {
            urls.add(url);
            if (url.contains("/get?")) return null;                       // exact match misses
            return "[{\"duration\":300,\"syncedLyrics\":\"[00:01.00] far\"},"
                 + "{\"duration\":201,\"syncedLyrics\":\"[00:02.00] near\"},"
                 + "{\"duration\":200,\"syncedLyrics\":\"\"}]";
        };
        LrcLib lib = new LrcLib(fake, "http://x");
        Track song = Track.of("s1", "Hello World", "Me & You", "Alb", 200_000, 0, true);
        List<Lrc.Line> got = lib.request(song).get();
        eq("search picks closest duration w/ lyrics", got.get(0).text(), "near");
        check("url encodes", urls.get(0).contains("Hello+World") && urls.get(0).contains("Me+%26+You"));
        int before = urls.size();
        lib.request(song).get();
        eq("second request hits cache", urls.size(), before);

        Net.Fetcher down = url -> { throw new java.io.IOException("offline"); };
        LrcLib lib2 = new LrcLib(down, "http://x");
        Track s2 = Track.of("s2", "X", "Y", "", 100_000, 0, true);
        eq("failure yields empty", lib2.request(s2).get().size(), 0);
        check("failure is not cached", lib2.cached(s2) == null);

        // ---- Settings
        Path dir = Files.createTempDirectory("sl");
        Path f = dir.resolve("settings.json");
        Files.writeString(f, "{ not json at all");
        Settings s = Settings.load(f);
        eq("corrupt file -> defaults", s.wordsPerLine, 4);
        Files.writeString(f, "{\"wordsPerLine\":99,\"staySeconds\":-4,\"surgeProfile\":\"nope\",\"redirectPort\":80,\"colour\":4286578687}");
        s = Settings.load(f);
        eq("clamps high", s.wordsPerLine, 8);
        eq("clamps low", s.staySeconds, 1f);
        eq("bad profile -> BALANCED", s.surgeProfile, "BALANCED");
        eq("bad port -> default", s.redirectPort, 19542);
        s.spotifyClientId = "  abc  "; s.colour = 0xFF112233; s.save(f);
        Settings s3 = Settings.load(f);
        eq("save/load trims id", s3.spotifyClientId, "abc");
        eq("save/load colour", s3.colour, 0xFF112233);
        check("no tmp file left", !Files.exists(dir.resolve("settings.json.tmp")));

        // ---- TokenStore
        Path tf = dir.resolve("auth.json");
        TokenStore ts = new TokenStore(); ts.refresh = "r"; ts.access = "a"; ts.expiresAtMs = 5; ts.save(tf);
        TokenStore ts2 = TokenStore.load(tf);
        eq("token roundtrip", ts2.refresh + ts2.access + ts2.expiresAtMs, "ra5");
        check("tokens not in settings", !Files.readString(f).contains("\"r\""));

        // ---- Spotify pure helpers (RFC 7636 test vector)
        eq("pkce challenge", Spotify.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
        Map<String, String> q = Spotify.parseQuery("code=a%20b&state=xyz&flag");
        eq("query decode", q.get("code"), "a b");
        eq("query flag", q.get("flag"), "");
        check("random differs", !Spotify.randomB64(32).equals(Spotify.randomB64(32)));

        // ---- SurgeGovernor
        SurgeGovernor g = new SurgeGovernor();
        Settings gs = new Settings();
        g.configure(SurgeProfile.BALANCED, SurgeLimits.of(SurgeProfile.BALANCED, gs), true, 100, true);
        check("near mob kept", !g.cullMob(30 * 30));
        check("far mob culled", g.cullMob(100 * 100));
        check("item range tighter than mob range", g.cullItem(50 * 50) && !g.cullMob(50 * 50));
        for (int i = 0; i < 4; i++) g.onSecond(40);        // sustained slow
        check("adaptive tightens", g.scale() < 1.0);
        double tight = g.scale();
        for (int i = 0; i < 30; i++) g.onSecond(200);      // sustained fast
        check("adaptive relaxes", g.scale() > tight);
        for (int i = 0; i < 200; i++) g.onSecond(1);
        check("scale floor 0.5", g.scale() >= 0.5);
        g.configure(SurgeProfile.BALANCED, SurgeLimits.of(SurgeProfile.BALANCED, gs), true, 100, true);
        for (int i = 0; i < 4; i++) g.onSecond(140, 20);   // fast average but terrible stutter
        check("1% low stutter also tightens", g.scale() < 1.0);
        g.configure(SurgeProfile.VANILLA, SurgeLimits.of(SurgeProfile.VANILLA, gs), true, 100, true);
        check("vanilla never culls", !g.cullMob(1e9) && !g.cullParticle(1e9) && g.particleBudget() == 0 && !g.hideClouds());
        g.configure(SurgeProfile.POTATO, SurgeLimits.of(SurgeProfile.POTATO, gs), false, 100, true);
        for (int i = 0; i < 50; i++) g.onSecond(1);
        eq("non-adaptive scale fixed", g.scale(), 1.0);
        check("potato hides weather", g.hideWeather());
        check("profile cycle wraps", SurgeProfile.CUSTOM.next() == SurgeProfile.VANILLA);

        // lighten UI + counters
        g.configure(SurgeProfile.BALANCED, SurgeLimits.of(SurgeProfile.BALANCED, gs), true, 100, true);
        check("no lighten at full scale", !g.lightenUi());
        for (int i = 0; i < 14; i++) g.onSecond(10);
        check("lighten when easing hard", g.lightenUi());
        g.configure(SurgeProfile.BALANCED, SurgeLimits.of(SurgeProfile.BALANCED, gs), true, 100, false);
        check("lighten respects the setting", !g.lightenUi());
        g.configure(SurgeProfile.BALANCED, SurgeLimits.of(SurgeProfile.BALANCED, gs), false, 100, true);
        g.cullMob(1e6); g.cullMob(1e6); g.cullParticle(1e6); g.cullBlockEntity(1e6); g.cullNametag(1e6);
        g.onSecond(100);
        check("stats line counts", g.statsLine().contains("mobs 2") && g.statsLine().contains("particles 1")
                && g.statsLine().contains("blocks 1") && g.statsLine().contains("names 1"));

        // custom limits
        Settings cs = new Settings();
        cs.cMob = 10; cs.cParticleCap = 0; cs.cClouds = true;
        SurgeLimits cl = SurgeLimits.of(SurgeProfile.CUSTOM, cs);
        eq("custom mob range", cl.mob(), 10.0);
        eq("custom clouds", cl.clouds(), true);
        g.configure(SurgeProfile.CUSTOM, cl, false, 100, true);
        check("custom culls at 15 blocks", g.cullMob(15 * 15) && !g.cullMob(5 * 5));
        eq("custom unlimited particles", g.particleBudget(), 0);
        Settings cp = new Settings();
        SurgeLimits.copyToCustom(SurgeProfile.EXTREME, cp);
        eq("copy preset into custom", cp.cMob + "," + cp.cItem + "," + cp.cParticleCap + "," + cp.cWeather, "16,8,60,true");
        eq("preset ignores custom sliders", SurgeLimits.of(SurgeProfile.POTATO, cs).mob(), 24.0);
        Settings bs = new Settings();
        bs.cMob = 9999; bs.cParticleCap = -5; bs.backgroundFps = 2; bs.clamp();
        eq("custom clamps", bs.cMob + "," + bs.cParticleCap + "," + bs.backgroundFps, "192,0,5");
        bs.backgroundFps = -1; bs.clamp(); eq("background fps off stays off", bs.backgroundFps, 0);
        eq("all profiles named", java.util.Arrays.stream(SurgeProfile.values()).map(x -> x.label).distinct().count(), (long) SurgeProfile.values().length);

        // ---- FrameStats
        FrameStats fs = new FrameStats();
        FrameStats.Snapshot snap = null;
        long tns = 1_000_000_000L;
        for (int i = 0; i < 400 && snap == null; i++) {
            tns += (i == 50) ? 100_000_000L : 10_000_000L;      // 100 fps with one 100ms hitch
            snap = fs.frame(tns);
        }
        check("framestats snapshot after one second", snap != null);
        check("framestats average near 100", snap != null && snap.avg() >= 85 && snap.avg() <= 105);
        check("framestats 1% low catches the hitch", snap != null && snap.low1() <= 12);
        FrameStats fs2 = new FrameStats();
        check("framestats quiet before a second", fs2.frame(5_000_000_000L) == null && fs2.frame(5_016_000_000L) == null);

        // ---- LyricsView
        List<Lrc.Line> v = Lrc.parse("[00:10.00] a b c d e f g h\n[00:14.00] last line\n[00:40.00] after gap");
        eq("view before start empty", LyricsView.compute(v, 5000, 4, 3.5f).text(), "");
        LyricsView.View v1 = LyricsView.compute(v, 10100, 4, 3.5f);
        eq("view first chunk", v1.text(), "a b c d");
        LyricsView.View v2 = LyricsView.compute(v, 12500, 4, 3.5f);
        eq("view second chunk", v2.text(), "e f g h");
        check("view fades in", LyricsView.compute(v, 10000, 4, 3.5f).alpha() < 0.01f && v1.alpha() > 0.3f);
        eq("view next line", v1.next(), "last line");
        check("view fades in long gap", LyricsView.compute(v, 14000 + 3000 + 500, 4, 3.5f).alpha() > 0.9f
                && LyricsView.compute(v, 14000 + 3900, 4, 3.5f).alpha() < 0.01f);
        eq("view 1 word/chunk", LyricsView.compute(v, 10000, 1, 3.5f).text(), "a");
        eq("clock", LyricsView.clock(65_000), "1:05");
        eq("withAlpha", Integer.toHexString(LyricsView.withAlpha(0xFFFFFFFF, 0.5f)), "80ffffff");
        eq("mix mid", LyricsView.mix(0xFF000000, 0xFFFFFFFF, 0.5f) & 0xFF, 127);

        // ---- Motion
        Motion.Spring sp = new Motion.Spring(0, 260f, 24f);
        float peak = 0;
        for (int i = 0; i < 400; i++) { float pv = sp.step(0.016f, 1f); peak = Math.max(peak, pv); }
        eq("spring settles", sp.pos, 1f);
        check("spring overshoots a little", peak > 1.0f && peak < 1.2f);
        Motion.Spring sp2 = new Motion.Spring(0, 260f, 24f);
        float huge = sp2.step(10f, 1f);
        check("spring survives huge dt", !Float.isNaN(huge) && Math.abs(huge) < 5);
        check("spring ignores zero dt", new Motion.Spring(0.3f, 260f, 24f).step(0f, 1f) == 0.3f);

        // ---- Track.merge
        Track m1 = Track.of("a", "T", "A", "", 200000, 5000, true);
        check("merge keeps close snapshot", Track.merge(m1, Track.of("a", "T", "A", "", 200000, 5100, true)) == m1);
        Track far = Track.of("a", "T", "A", "", 200000, 90000, true);
        check("merge takes seek", Track.merge(m1, far) == far);
        Track other = Track.of("b", "T2", "A", "", 200000, 5000, true);
        check("merge takes new song", Track.merge(m1, other) == other);
        check("merge takes pause", Track.merge(m1, Track.of("a", "T", "A", "", 200000, 5000, false)).playing() == false);

        // ---- WindowsMedia line parsing
        Track wl = WindowsMedia.parseLine("{\"app\":\"Spotify.exe\",\"title\":\"Hi \\u00e9\",\"artist\":\"A\",\"album\":\"B\",\"pos\":12345,\"dur\":200000,\"playing\":true}");
        eq("win title", wl.title(), "Hi \u00e9");
        eq("win pos", wl.positionMs() >= 12345 && wl.positionMs() < 13500, true);
        eq("win id", wl.id(), "win:hi \u00e9|a");
        eq("win empty object", WindowsMedia.parseLine("{}"), null);
        eq("win blank title", WindowsMedia.parseLine("{\"title\":\" \"}"), null);
        boolean bad = false; try { WindowsMedia.parseLine("not json"); } catch (RuntimeException e) { bad = true; }
        check("win garbage throws (caught by reader)", bad);

        // ---- NowPlaying selection
        Settings ns = new Settings();
        Spotify nsp = new Spotify(ns, dir.resolve("np-auth.json"));
        WindowsMedia nwm = new WindowsMedia();
        NowPlaying np = new NowPlaying(ns, nsp, nwm);
        eq("np none", np.track(), null);
        Track wt = Track.of("w", "W", "x", "", 1000, 0, true), st = Track.of("s", "S", "y", "", 1000, 0, true);
        nwm.injectTrackForTest(wt); nsp.injectTrackForTest(st);
        ns.source = 0; eq("np auto prefers playing windows", np.track().id(), "w");
        nwm.injectTrackForTest(Track.of("w", "W", "x", "", 1000, 0, false));
        eq("np auto switches to playing spotify", np.track().id(), "s");
        ns.source = 1; eq("np windows only", np.track().id(), "w");
        ns.source = 2; eq("np spotify only", np.track().id(), "s");
        ns.source = 7; ns.clamp(); eq("source clamped", ns.source, 2);

        // ---- display modes
        List<Lrc.Line> dm = Lrc.parse("[00:10.00] one two three four five six\n[00:16.00] second line here\n[00:40.00] third");
        LyricsView.View line = LyricsView.compute(dm, 12000, 1, 4, 3.5f);
        eq("mode line shows whole line", line.text(), "one two three four five six");
        LyricsView.View ty = LyricsView.compute(dm, 12000, 2, 4, 3.5f);
        check("mode typewriter is a prefix", ty.text().length() < 27 && "one two three four five six".startsWith(ty.text()) && !ty.text().isEmpty());
        LyricsView.View tyEnd = LyricsView.compute(dm, 15900, 2, 4, 3.5f);
        eq("typewriter finishes before next line", tyEnd.text(), "one two three four five six");
        LyricsView.View kf = LyricsView.compute(dm, 13000, 3, 4, 3.5f);
        check("mode karaoke fill grows", kf.fill() > 0.2f && kf.fill() < 0.8f && LyricsView.compute(dm, 15900, 3, 4, 3.5f).fill() > 0.9f);
        LyricsView.View sc = LyricsView.compute(dm, 17000, 4, 4, 3.5f);
        eq("mode scroll has before", sc.before(), "one two three four five six");
        eq("mode scroll has next", sc.next(), "third");
        eq("mode 0 equals old chunks", LyricsView.compute(dm, 10100, 0, 4, 3.5f).text(), "one two three four");
        eq("mode before first line", LyricsView.compute(dm, 100, 3, 4, 3.5f).text(), "");

        // ---- Colors
        for (int hx : new int[]{0xFF7DD3FC, 0xFFFB923C, 0xFF808080, 0xFFFFFFFF, 0xFF000000, 0xFF123456}) {
            float[] hsb = Colors.toHsb(hx);
            int back = Colors.fromHsb(hsb[0], hsb[1], hsb[2]);
            boolean near = Math.abs(((back >> 16) & 255) - ((hx >> 16) & 255)) <= 1 && Math.abs(((back >> 8) & 255) - ((hx >> 8) & 255)) <= 1
                    && Math.abs((back & 255) - (hx & 255)) <= 1;
            check("hsb roundtrip " + Integer.toHexString(hx), near);
        }
        eq("hsb pure red", Integer.toHexString(Colors.fromHsb(0f, 1f, 1f)), "ffff0000");
        eq("hsb pure green", Integer.toHexString(Colors.fromHsb(1f / 3f, 1f, 1f)), "ff00ff00");
        eq("hue wraps", Colors.fromHsb(1.0f, 1f, 1f), Colors.fromHsb(0f, 1f, 1f));
        eq("gradient off is c1", Colors.gradient(0, 3, 10, 5f, 0xFF112233, 0xFFAABBCC, 4), 0xFF112233);
        eq("gradient static ends", Colors.gradient(1, 9, 10, 0f, 0xFF000000, 0xFFFFFFFF, 4) & 255, 255);
        eq("gradient static start", Colors.gradient(1, 0, 10, 0f, 0xFF000000, 0xFFFFFFFF, 4) & 255, 0);
        check("gradient flow moves with time", Colors.gradient(2, 2, 10, 0f, 0xFF000000, 0xFFFFFFFF, 4) != Colors.gradient(2, 2, 10, 3f, 0xFF000000, 0xFFFFFFFF, 4));
        check("gradient rainbow varies per char", Colors.gradient(3, 0, 10, 0f, 0, 0, 4) != Colors.gradient(3, 5, 10, 0f, 0, 0, 4));
        eq("gradient single char no div by zero", Colors.gradient(1, 0, 1, 0f, 0xFF102030, 0xFFFFFFFF, 4), 0xFF102030);
        eq("darken keeps alpha", Colors.darken(0x80FFFFFF, 0.5f) >>> 24, 0x80);
        eq("darken halves", Colors.darken(0xFFFFFFFF, 0.5f) & 255, 128);
        check("palettes unique names", Colors.PALETTES.stream().map(Colors.Palette::name).distinct().count() == Colors.PALETTES.size());

        // ---- Layout
        int[] tl = Layout.place(0, 0, 0, 400, 240, 100, 20);
        eq("anchor top-left x", tl[0], 60); eq("anchor top-left y", tl[1], 20);
        int[] c = Layout.place(4, 0, 0, 400, 240, 100, 20);
        eq("anchor center", c[0] + "," + c[1], "200,120");
        int[] br = Layout.place(8, 0, 0, 400, 240, 100, 20);
        eq("anchor bottom-right", br[0] + "," + br[1], "340,220");
        int[] off = Layout.place(4, 1000, -1000, 400, 240, 100, 20);
        eq("offset clamped on screen", off[0] + "," + off[1], "350,10");
        int[] big = Layout.place(0, 0, 0, 100, 50, 300, 90);
        eq("box bigger than screen centres", big[0] + "," + big[1], "50,25");
        eq("bad anchor clamped", Layout.place(99, 0, 0, 400, 240, 100, 20)[0], 340);

        // ---- FontStyle + new settings
        eq("default font has no id", FontStyle.DEFAULT.fontId(), null);
        eq("script font id", FontStyle.SCRIPT.fontId(), "surgelyrics:script");
        eq("font index clamps", FontStyle.of(99), FontStyle.MONOBOLD);
        eq("font index clamps low", FontStyle.of(-5), FontStyle.DEFAULT);
        Settings ns2 = new Settings();
        ns2.displayMode = 9; ns2.font = -3; ns2.anchor = 40; ns2.offX = 9999; ns2.depthAmount = 0; ns2.lightAngle = -90;
        ns2.glowStrength = 500; ns2.opacity = 0; ns2.gradientMode = 7; ns2.gradientSpeed = 0; ns2.clamp();
        eq("settings clamp batch", ns2.displayMode + "," + ns2.font + "," + ns2.anchor + "," + ns2.offX + "," + ns2.depthAmount + "," + ns2.lightAngle
                + "," + ns2.glowStrength + "," + ns2.opacity + "," + ns2.gradientMode + "," + ns2.gradientSpeed, "4,0,8,300,1,270,100,25,6,1");
        Path nf = dir.resolve("new.json");
        ns2.font = 1; ns2.depthStyle = 1; ns2.window3d = false; ns2.save(nf);
        Settings ns3 = Settings.load(nf);
        eq("new settings roundtrip", ns3.font + "," + ns3.depthStyle + "," + ns3.window3d + "," + ns3.lightAngle, "1,1,false,270");

        // ---- fonts
        check("all font keys unique", java.util.Arrays.stream(FontStyle.values()).map(fnt -> fnt.key).distinct().count() == FontStyle.values().length);
        check("37 fonts, 5 pages", FontStyle.values().length == 37 && FontStyle.pages() == 5);
        check("every font key is a legal resource name", java.util.Arrays.stream(FontStyle.values()).filter(fnt -> fnt.key != null).allMatch(fnt -> fnt.key.matches("[a-z0-9_]+")));

        // ---- hex colours
        eq("hex 6", Integer.toHexString(Colors.parseHex("#7dd3fc")), "ff7dd3fc");
        eq("hex no hash upper", Integer.toHexString(Colors.parseHex("FFAA00")), "ffffaa00");
        eq("hex shorthand", Integer.toHexString(Colors.parseHex("#0af")), "ff00aaff");
        eq("hex whitespace", Integer.toHexString(Colors.parseHex("  #112233 ")), "ff112233");
        eq("hex bad digit", Colors.parseHex("#12345g"), null);
        eq("hex bad length", Colors.parseHex("#12345"), null);
        eq("hex empty", Colors.parseHex(""), null);
        eq("hex null", Colors.parseHex(null), null);
        eq("hex format", Colors.toHex(0xFF0A1B2C), "#0A1B2C");
        eq("hex roundtrip", Colors.toHex(Colors.parseHex("#c084fc")), "#C084FC");
        check("health green vs red", (Colors.health(1f) >> 8 & 255) > 200 && (Colors.health(0f) >> 16 & 255) > 200);
        eq("ping good", Colors.pingColor(20), 0xFF4ADE80);
        eq("ping bad", Colors.pingColor(500), 0xFFF87171);

        // ---- Fmt
        eq("duration short", Fmt.duration(83_000), "1:23");
        eq("duration hour", Fmt.duration(3_725_000), "1:02:05");
        eq("duration negative", Fmt.duration(-5), "0:00");
        eq("fixed 2", Fmt.fixed(3.0, 2), "3.00");
        eq("fixed round", Fmt.fixed(3.14159, 2), "3.14");
        eq("fixed 1", Fmt.fixed(2.96, 1), "3.0");
        eq("fixed 3", Fmt.fixed(1.5, 3), "1.500");

        // ---- Reach
        eq("reach in front", Reach.distance(0, 0, 0, 3, -1, -1, 4, 1, 1), 3.0);
        eq("reach inside box is 0", Reach.distance(0.5, 0.5, 0.5, 0, 0, 0, 1, 1, 1), 0.0);
        check("reach diagonal", Math.abs(Reach.distance(0, 0, 0, 3, 4, -1, 5, 6, 1) - 5.0) < 1e-9);

        // ---- ClickCounter
        ClickCounter cc2 = new ClickCounter();
        for (int i = 0; i < 7; i++) cc2.click(10_000 + i * 100);
        eq("cps counts a second", cc2.cps(10_650), 7);
        eq("cps expires", cc2.cps(11_300), 3);
        eq("cps empty", cc2.cps(99_999), 0);

        // ---- CombatState
        CombatState cs2 = new CombatState();
        cs2.setTimeoutMs(3000);
        cs2.hit(1000, 2.9); cs2.hit(1500, 3.1); cs2.hit(2000, 3.4);
        eq("combo builds", cs2.combo(2100), 3);
        eq("reach is last hit", cs2.reach(2100), 3.4);
        eq("combo times out", cs2.combo(6000), 0);
        eq("reach fades", cs2.reach(7000), -1.0);
        cs2.hit(7000, 2.0); cs2.hit(7200, 2.1);
        eq("combo restarts after timeout", cs2.combo(7300), 2);
        cs2.damaged();
        eq("damage ends the combo", cs2.combo(7300), 0);
        eq("best combo remembered", cs2.best(), 3);
        cs2.hit(7400, 2.0); eq("hit after damage starts at 1", cs2.combo(7400), 1);

        // ---- HudWidget + settings
        HudWidget hw = new HudWidget(true, 99, 9999, -9999);
        hw.scale = 1000; hw.color = 0x00112233; hw.clamp();
        eq("widget clamps", hw.anchor + "," + hw.offX + "," + hw.offY + "," + hw.scale + "," + Integer.toHexString(hw.color), "8,300,-300,200,ff112233");
        Settings hs = new Settings();
        hs.armor.on = false; hs.keys.anchor = 2; hs.reach.on = true; hs.combo.scale = 150; hs.ping.color = 0xFFAA00FF;
        hs.armorDur = 3; hs.armorVertical = false; hs.timerMode = 1; hs.reachDecimals = 3; hs.comboSeconds = 7; hs.keysCps = false;
        Path hf = dir.resolve("hud.json"); hs.save(hf);
        Settings hl = Settings.load(hf);
        eq("hud widgets roundtrip", hl.armor.on + "," + hl.keys.anchor + "," + hl.reach.on + "," + hl.combo.scale + "," + Integer.toHexString(hl.ping.color),
                "false,2,true,150,ffaa00ff");
        eq("hud options roundtrip", hl.armorDur + "," + hl.armorVertical + "," + hl.timerMode + "," + hl.reachDecimals + "," + hl.comboSeconds + "," + hl.keysCps,
                "3,false,1,3,7,false");
        Files.writeString(hf, "{\"hud\":{\"armor\":{\"scale\":5000,\"anchor\":-4}},\"armorDur\":42,\"comboSeconds\":0}");
        Settings hb = Settings.load(hf);
        eq("hud loads clamp bad values", hb.armor.scale + "," + hb.armor.anchor + "," + hb.armorDur + "," + hb.comboSeconds, "200,0,3,1");
        eq("hud missing widgets keep defaults", hb.keys.on + "," + hb.timer.anchor, "true,0");
        eq("seven widgets", new Settings().widgets().length, 7);

        // ---- RoundRects: identical pixels to row-by-row drawing, far fewer rectangles
        for (int[] sh : new int[][]{{50, 20, 6}, {120, 68, 8}, {420, 260, 18}, {10, 10, 5}, {7, 30, 0}, {30, 3, 9}, {1, 1, 4}}) {
            int rw = sh[0], rhh = sh[1], rr = sh[2];
            boolean[][] a = new boolean[rhh][rw], b = new boolean[rhh][rw];
            int rrc = Math.max(0, Math.min(rr, Math.min(rw, rhh) / 2));
            for (int dy = 0; dy < rhh; dy++) { int in = RoundRects.inset(rrc, rhh, dy); for (int xx = in; xx < rw - in; xx++) a[dy][xx] = true; }
            List<int[]> rs = RoundRects.rects(rw, rhh, rr);
            boolean overlap = false;
            for (int[] rc : rs) for (int dy = rc[1]; dy < rc[3]; dy++) for (int xx = rc[0]; xx < rc[2]; xx++) { if (b[dy][xx]) overlap = true; b[dy][xx] = true; }
            boolean same = !overlap;
            for (int dy = 0; dy < rhh && same; dy++) for (int xx = 0; xx < rw; xx++) if (a[dy][xx] != b[dy][xx]) { same = false; break; }
            check("roundrect covers same pixels " + rw + "x" + rhh + " r" + rr, same);
        }
        check("roundrect merges rows (420x260 r18 uses few rects)", RoundRects.rects(420, 260, 18).size() <= 40);
        check("roundrect plain rectangle is one rect", RoundRects.rects(100, 40, 0).size() == 1);
        check("roundrect empty", RoundRects.rects(0, 10, 3).isEmpty());

        // ---- Occlusion: a wall at x == 5
        Occlusion.Solid wall = (wx, wy, wz) -> wx == 5;
        check("wall blocks a straight ray", Occlusion.blocked(wall, 0.5, 0.5, 0.5, 10.5, 0.5, 0.5));
        check("no wall before target", !Occlusion.blocked(wall, 0.5, 0.5, 0.5, 3.5, 0.5, 0.5));
        check("target voxel itself is ignored", !Occlusion.blocked(wall, 0.5, 0.5, 0.5, 5.5, 0.5, 0.5));
        check("start voxel is ignored", !Occlusion.blocked((a1, b1, c1) -> a1 == 0, 0.5, 0.5, 0.5, 3.5, 0.5, 0.5));
        check("diagonal ray through the wall", Occlusion.blocked(wall, 0.5, 0.5, 0.5, 9.5, 4.5, 6.5));
        check("ray backwards through the wall", Occlusion.blocked(wall, 10.5, 2.5, 0.5, 0.5, 0.5, 3.5));
        Occlusion.Solid lowWall = (wx, wy, wz) -> wx == 5 && wy <= 2;
        check("ray over a low wall is clear", !Occlusion.blocked(lowWall, 0.5, 6.5, 0.5, 10.5, 6.5, 0.5));
        check("ray under the top is blocked by low wall", Occlusion.blocked(lowWall, 0.5, 1.5, 0.5, 10.5, 1.5, 0.5));
        check("zero length ray", !Occlusion.blocked(wall, 5.2, 5.2, 5.2, 5.4, 5.4, 5.4));
        check("hidden needs every sample blocked", Occlusion.hidden(wall, 0.5, 0.5, 0.5, new double[]{10.5, 0.5, 0.5, 10.5, 1.5, 0.5, 10.5, 0.5, 2.5}));
        check("one clear sample means visible", !Occlusion.hidden(lowWall, 0.5, 0.5, 0.5, new double[]{10.5, 0.5, 0.5, 10.5, 6.5, 0.5}));
        check("no samples means visible", !Occlusion.hidden(wall, 0, 0, 0, new double[0]));
        check("never hides through open air", !Occlusion.blocked((a2, b2, c2) -> false, 0.5, 0.5, 0.5, 200.5, 90.5, -70.5));
        int[] visits = {0};
        Occlusion.blocked((a3, b3, c3) -> { visits[0]++; return false; }, 0.5, 0.5, 0.5, 40.5, 0.5, 0.5);
        check("traversal visits about one voxel per block", visits[0] >= 38 && visits[0] <= 42);

        // ---- AudioReact
        AudioReact ar = new AudioReact();
        for (int i = 0; i < 200; i++) ar.update(0.5f, 0.016f);
        check("audio level follows input", Math.abs(ar.level() - 0.5f) < 0.02f);
        check("steady music is not a beat", ar.beat() < 0.05f);
        for (int i = 0; i < 200; i++) ar.update(0.0f, 0.016f);
        check("silence decays the level", ar.level() < 0.02f);
        ar.update(0.9f, 0.016f);
        check("a spike after quiet is a beat", ar.beat() > 0.9f);
        for (int i = 0; i < 40; i++) ar.update(0.0f, 0.016f);
        check("beat fades out", ar.beat() < 0.05f);
        float lvBefore = ar.level(); ar.update(1f, 0f); ar.update(1f, -1f);
        eq("zero or negative dt changes nothing", ar.level(), lvBefore);
        AudioReact ar2 = new AudioReact(); ar2.update(5f, 10f); ar2.update(Float.NaN, 0.016f);
        check("audio clamps and survives NaN", ar2.level() <= 1.0f && !Float.isNaN(ar2.level()));
        eq("audio parse number", AudioLevel.parse(" 0.250 "), 0.25f);
        eq("audio parse clamps", AudioLevel.parse("7.5"), 1.0f);
        eq("audio parse garbage", AudioLevel.parse("err"), null);
        eq("audio parse null", AudioLevel.parse(null), null);
        eq("audio parse NaN", AudioLevel.parse("NaN"), null);

        // ---- LyricTransitions
        for (int st2 = 0; st2 < LyricTransitions.STYLES.length; st2++) {
            LyricTransitions.Pose in1 = LyricTransitions.in(st2, 1f), out0 = LyricTransitions.out(st2, 0f);
            boolean inEnd = Math.abs(in1.dy()) < 0.01f && Math.abs(in1.scale() - 1f) < 0.01f && in1.alpha() > 0.99f && in1.reveal() > 0.99f;
            check("transition " + st2 + " incoming lands on identity", inEnd);
            check("transition " + st2 + " outgoing starts on identity", Math.abs(out0.dy()) < 0.01f && Math.abs(out0.scale() - 1f) < 0.01f && out0.alpha() > 0.99f);
            check("transition " + st2 + " outgoing ends invisible", LyricTransitions.out(st2, 1f).alpha() < 0.01f);
            float prev = -1; boolean mono = true;
            for (int k = 0; k <= 20; k++) { float al = LyricTransitions.in(st2, k / 20f).alpha(); if (al < prev - 1e-4f) mono = false; prev = al; }
            check("transition " + st2 + " incoming alpha never decreases", mono);
            check("transition " + st2 + " clamps progress", LyricTransitions.in(st2, 9f).alpha() > 0.99f && LyricTransitions.in(st2, -3f).alpha() <= 1f);
        }
        check("fade starts invisible", LyricTransitions.in(0, 0f).alpha() < 0.01f);
        check("slide starts below", LyricTransitions.in(1, 0f).dy() > 5f);
        check("zoom starts smaller", LyricTransitions.in(2, 0f).scale() < 0.9f);
        check("words starts with nothing revealed", LyricTransitions.in(4, 0f).reveal() < 0.01f);
        check("zoom overshoots slightly", LyricTransitions.in(2, 0.7f).scale() > 1f);
        check("bounce drops from above", LyricTransitions.in(3, 0f).dy() < -10f);

        // ---- LyricsView hold/key
        List<Lrc.Line> kv = Lrc.parse("[00:10.00] a b c d e f g h\n[00:14.00] last\n[00:40.00] after gap");
        check("key stable inside a chunk", LyricsView.compute(kv, 10100, 0, 4, 3.5f).key() == LyricsView.compute(kv, 10300, 0, 4, 3.5f).key());
        check("key changes between chunks", LyricsView.compute(kv, 10100, 0, 4, 3.5f).key() != LyricsView.compute(kv, 12500, 0, 4, 3.5f).key());
        check("key changes between lines", LyricsView.compute(kv, 11000, 1, 4, 3.5f).key() != LyricsView.compute(kv, 14500, 1, 4, 3.5f).key());
        eq("key stable while typing", LyricsView.compute(kv, 10300, 2, 4, 3.5f).key(), LyricsView.compute(kv, 12000, 2, 4, 3.5f).key());
        check("hold is 1 during a line", LyricsView.compute(kv, 10200, 1, 4, 3.5f).hold() > 0.99f);
        check("hold fades in a long gap", LyricsView.compute(kv, 14000 + 3900, 1, 4, 3.5f).hold() < 0.1f);
        check("alpha still fades in at the start", LyricsView.compute(kv, 10000, 1, 4, 3.5f).alpha() < 0.01f && LyricsView.compute(kv, 10000, 1, 4, 3.5f).hold() > 0.99f);
        eq("empty view key", LyricsView.EMPTY.key(), -1);

        // ---- new settings
        Settings ms = new Settings();
        eq("new installs default to Aggressive", ms.surgeProfile, "AGGRESSIVE");
        ms.hotbar.on = true; ms.hotbarStyle = 2; ms.hotbarNumbers = false; ms.transition = 3; ms.transitionMs = 450;
        ms.reactive = true; ms.reactStrength = 80; ms.reactBars = false; ms.occludeMobs = false;
        Path mf = dir.resolve("more.json"); ms.save(mf);
        Settings ml = Settings.load(mf);
        eq("hotbar roundtrip", ml.hotbar.on + "," + ml.hotbarStyle + "," + ml.hotbarNumbers, "true,2,false");
        eq("transition + audio roundtrip", ml.transition + "," + ml.transitionMs + "," + ml.reactive + "," + ml.reactStrength + "," + ml.reactBars, "3,450,true,80,false");
        eq("occlusion roundtrip", ml.occludeMobs + "," + ml.occludeBlocks, "false,true");
        ms.transition = 99; ms.transitionMs = 5; ms.hotbarStyle = -4; ms.reactStrength = 900; ms.clamp();
        eq("new settings clamp", ms.transition + "," + ms.transitionMs + "," + ms.hotbarStyle + "," + ms.reactStrength, "4,150,0,100");
        eq("hotbar default position bottom centre", new Settings().hotbar.anchor, 7);
        eq("hotbar off by default", new Settings().hotbar.on, false);
        SurgeGovernor og = new SurgeGovernor();
        og.configure(SurgeProfile.BALANCED, SurgeLimits.of(SurgeProfile.BALANCED, new Settings()), false, 100, true);
        og.countOccluded(); og.countOccluded(); og.onSecond(100);
        check("stats line counts occluded", og.statsLine().contains("behind walls 2"));

        // ---- new gradient modes
        check("seven gradient modes", Colors.GRADIENT_MODES.length == 7);
        int shineHits = 0;
        for (int i = 0; i < 20; i++) if ((Colors.gradient(4, i, 20, 1.0f, 0xFF204080, 0xFF8040C0, 4) & 0xFF) > 0xE0) shineHits++;
        check("shine lights a band, not the whole line", shineHits >= 1 && shineHits <= 8);
        int litA = 0, litB = 0;
        for (int i = 0; i < 20; i++) {
            if ((Colors.gradient(4, i, 20, 0.5f, 0xFF204080, 0xFF8040C0, 4) & 0xFF) > 0xE0) litA += i;
            if ((Colors.gradient(4, i, 20, 1.5f, 0xFF204080, 0xFF8040C0, 4) & 0xFF) > 0xE0) litB += i;
        }
        check("shine band moves over time", litA != litB && litA < litB);
        check("pulse breathes over time", Colors.gradient(5, 3, 10, 0.0f, 0xFFFFFFFF, 0xFFFFFFFF, 4) != Colors.gradient(5, 3, 10, 1.7f, 0xFFFFFFFF, 0xFFFFFFFF, 4));
        eq("pulse is uniform brightness across the line", Colors.gradient(5, 0, 10, 0.5f, 0xFFFFFFFF, 0xFFFFFFFF, 4), Colors.gradient(5, 9, 10, 0.5f, 0xFFFFFFFF, 0xFFFFFFFF, 4));
        int twinkles = 0;
        for (int i = 0; i < 400; i++) if ((Colors.gradient(6, i, 400, 0f, 0xFF000000, 0xFF000000, 4) & 0xFF) > 0xC0) twinkles++;
        check("sparkle twinkles about one letter in seven", twinkles > 30 && twinkles < 90);
        for (int mode = 0; mode < 7; mode++) {
            boolean ok = true;
            for (int i = 0; i < 12; i++) { int gc = Colors.gradient(mode, i, 12, 3.3f, 0xFF7DD3FC, 0xFFC084FC, 5); if ((gc >>> 24) != 0xFF) ok = false; }
            check("gradient mode " + mode + " stays opaque", ok);
        }

        // ---- Flair
        check("hash in range", java.util.stream.IntStream.range(0, 2000).allMatch(k -> { float h = Flair.hash01(k); return h >= 0f && h < 1f; }));
        eq("hash deterministic", Flair.hash01(12345), Flair.hash01(12345));
        check("hash spreads out", Flair.hash01(1) != Flair.hash01(2) && Flair.hash01(2) != Flair.hash01(3));
        boolean glintOk = true;
        for (int gi = 0; gi < 12; gi++) for (int k = 0; k < 200; k++) {
            float[] gl = Flair.glint(gi, k * 0.05f);
            if (gl[0] < -0.5f || gl[0] > 0.5f || gl[1] < -0.6f || gl[1] > 0.6f || gl[2] < 0f || gl[2] > 1f) glintOk = false;
        }
        check("glints stay in their box", glintOk);
        float maxA = 0; for (int k = 0; k < 400; k++) maxA = Math.max(maxA, Flair.glint(3, k * 0.02f)[2]);
        check("glints reach full brightness and fade", maxA > 0.95f);
        check("glints move to a new spot each cycle", Flair.glint(2, 0.1f)[0] != Flair.glint(2, 9.1f)[0] || Flair.glint(2, 0.1f)[1] != Flair.glint(2, 9.1f)[1]);
        check("wave stays within amplitude", java.util.stream.IntStream.range(0, 200).allMatch(k -> Math.abs(Flair.wave(k % 30, k * 0.07f, 3f)) <= 3.0001f));
        eq("wave amplitude zero is flat", Flair.wave(4, 2f, 0f), 0f);
        check("wave travels along the letters", Flair.wave(0, 1f, 3f) != Flair.wave(5, 1f, 3f));

        // ---- Themes
        check("six themes with unique names", Themes.ALL.length == 6 && java.util.Arrays.stream(Themes.ALL).map(Themes.Theme::name).distinct().count() == 6);
        eq("theme index clamps", Themes.of(99), Themes.ALL[5]);
        eq("theme index clamps low", Themes.of(-3), Themes.ALL[0]);
        eq("theme body keeps colour", Themes.body(0, 0x70, 100) & 0xFFFFFF, 0x141A2A);
        eq("theme opacity scales alpha", Themes.body(0, 200, 50) >>> 24, 100);
        eq("theme opacity clamps", Themes.body(0, 200, 500) >>> 24, 200);

        // ---- flair settings
        Settings fl = new Settings();
        fl.backdrop = 9; fl.reflection = 500; fl.waveAmp = -2; fl.uiTheme = 77; fl.glassOpacity = 3; fl.clamp();
        eq("flair clamps", fl.backdrop + "," + fl.reflection + "," + fl.waveAmp + "," + fl.uiTheme + "," + fl.glassOpacity, "3,60,0,5,30");
        fl.backdrop = 2; fl.reflection = 35; fl.waveAmp = 3; fl.sparkles = true; fl.underline = true; fl.edgeGlow = true;
        fl.uiTheme = 3; fl.glassOpacity = 65; fl.windowGlow = false; fl.windowSweep = false;
        Path ff = dir.resolve("flair.json"); fl.save(ff);
        Settings fr = Settings.load(ff);
        eq("flair roundtrip", fr.backdrop + "," + fr.reflection + "," + fr.waveAmp + "," + fr.sparkles + "," + fr.underline + "," + fr.edgeGlow
                + "," + fr.uiTheme + "," + fr.glassOpacity + "," + fr.windowGlow + "," + fr.windowSweep, "2,35,3,true,true,true,3,65,false,false");
        eq("flair defaults are subtle", new Settings().backdrop + "," + new Settings().reflection + "," + new Settings().sparkles, "0,0,false");

        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
