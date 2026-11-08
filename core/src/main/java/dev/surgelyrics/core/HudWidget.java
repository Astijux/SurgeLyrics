package dev.surgelyrics.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Position, size and look shared by every HUD widget (armor, keystrokes, ping, ...). */
public final class HudWidget {
    public boolean on, bg = true;
    public int anchor, offX, offY, scale = 100;     // anchor 0..8 (see Layout), scale 50..200 percent
    public int color = 0xFF7DD3FC;

    public HudWidget(boolean on, int anchor, int offX, int offY) {
        this.on = on; this.anchor = anchor; this.offX = offX; this.offY = offY;
    }

    public void clamp() {
        anchor = Math.max(0, Math.min(8, anchor));
        offX = Math.max(-300, Math.min(300, offX));
        offY = Math.max(-300, Math.min(300, offY));
        scale = Math.max(50, Math.min(200, scale));
        color = 0xFF000000 | color;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("on", on); m.put("bg", bg); m.put("anchor", anchor); m.put("offX", offX); m.put("offY", offY);
        m.put("scale", scale); m.put("color", (long) color & 0xFFFFFFFFL);
        return m;
    }

    public void load(Map<String, Object> m) {
        on = Json.bool(m.get("on"), on); bg = Json.bool(m.get("bg"), bg);
        anchor = (int) Json.num(m.get("anchor"), anchor); offX = (int) Json.num(m.get("offX"), offX);
        offY = (int) Json.num(m.get("offY"), offY); scale = (int) Json.num(m.get("scale"), scale);
        color = (int) (long) Json.num(m.get("color"), color);
        clamp();
    }
}
