package dev.surgelyrics.core;

import java.util.*;
import java.util.regex.*;

/** Synced-lyrics (LRC) parsing and lookup. */
public final class Lrc {
    private Lrc() {}

    public record Line(long timeMs, String text) {}

    // [mm:ss], [mm:ss.x], [mm:ss.xx], [mm:ss.xxx] (also ':' as fraction separator)
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

    public static List<Line> parse(String lrc) {
        if (lrc == null || lrc.isBlank()) return List.of();
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = STAMP.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {          // only leading stamps count
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                long ms = 0;
                if (m.group(3) != null) {
                    String f = m.group(3);
                    ms = Math.round(Long.parseLong(f) * Math.pow(10, 3 - f.length()));
                }
                times.add(min * 60_000 + sec * 1000 + ms);
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(end).strip();
            if (text.isEmpty()) continue;
            for (long t : times) out.add(new Line(t, text));
        }
        out.sort(Comparator.comparingLong(Line::timeMs));
        return List.copyOf(out);
    }

    /** Index of the last line whose time is <= posMs, or -1 if the song hasn't reached the first line. */
    public static int indexAt(List<Line> lines, long posMs) {
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs() <= posMs) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }
}
