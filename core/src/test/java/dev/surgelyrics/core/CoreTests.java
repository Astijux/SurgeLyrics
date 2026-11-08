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
        g.configure(SurgeProfile.BALANCED, true, 100);
        check("near entity kept", !g.cullEntity(30 * 30));
        check("far entity culled", g.cullEntity(100 * 100));
        for (int i = 0; i < 4; i++) g.onSecond(40);        // sustained slow
        check("adaptive tightens", g.scale() < 1.0);
        double tight = g.scale();
        for (int i = 0; i < 30; i++) g.onSecond(200);      // sustained fast
        check("adaptive relaxes", g.scale() > tight);
        for (int i = 0; i < 200; i++) g.onSecond(1);
        check("scale floor 0.5", g.scale() >= 0.5);
        g.configure(SurgeProfile.VANILLA, true, 100);
        check("vanilla never culls", !g.cullEntity(1e9) && g.particleBudget() == 0 && !g.hideClouds());
        g.configure(SurgeProfile.POTATO, false, 100);
        for (int i = 0; i < 50; i++) g.onSecond(1);
        eq("non-adaptive scale fixed", g.scale(), 1.0);
        check("potato hides weather", g.hideWeather());
        check("profile cycle wraps", SurgeProfile.POTATO.next() == SurgeProfile.VANILLA);

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
        eq("font index clamps", FontStyle.of(99), FontStyle.PIXEL);
        Settings ns2 = new Settings();
        ns2.displayMode = 9; ns2.font = -3; ns2.anchor = 40; ns2.offX = 9999; ns2.depthAmount = 0; ns2.lightAngle = -90;
        ns2.glowStrength = 500; ns2.opacity = 0; ns2.gradientMode = 7; ns2.gradientSpeed = 0; ns2.clamp();
        eq("settings clamp batch", ns2.displayMode + "," + ns2.font + "," + ns2.anchor + "," + ns2.offX + "," + ns2.depthAmount + "," + ns2.lightAngle
                + "," + ns2.glowStrength + "," + ns2.opacity + "," + ns2.gradientMode + "," + ns2.gradientSpeed, "4,0,8,300,1,270,100,25,3,1");
        Path nf = dir.resolve("new.json");
        ns2.font = 1; ns2.depthStyle = 1; ns2.window3d = false; ns2.save(nf);
        Settings ns3 = Settings.load(nf);
        eq("new settings roundtrip", ns3.font + "," + ns3.depthStyle + "," + ns3.window3d + "," + ns3.lightAngle, "1,1,false,270");

        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
