package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Armor status, keystrokes, ping, session timer, reach and combo. Each widget has its own anchor, offsets,
 * scale, colour and optional glass background. All logic (formatting, reach maths, combo, CPS) lives in core.
 */
final class HudWidgets {
    private static final int PAD = 5;

    private long lastNanos = System.nanoTime();
    private final float[] press = new float[7];                 // W A S D space LMB RMB
    private final ClickCounter lmb = new ClickCounter(), rmb = new ClickCounter();
    private boolean lmbWas, rmbWas;
    private Object lastWorld;
    private long worldJoinMs = System.currentTimeMillis();
    private int lastHurt;

    void render(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int sw, int sh) {
        var player = mc.player;
        if (player == null) return;
        long nowN = System.nanoTime();
        float dt = Math.min(0.1f, (nowN - lastNanos) / 1e9f);
        lastNanos = nowN;
        long now = System.currentTimeMillis();
        Settings s = Runtime.SETTINGS;

        if (mc.world != lastWorld) { lastWorld = mc.world; worldJoinMs = now; }
        Runtime.COMBAT.setTimeoutMs(s.comboSeconds * 1000L);
        int hurt = player.hurtTime;                              // VERIFY: public hurtTime field on LivingEntity
        if (hurt > lastHurt) Runtime.COMBAT.damaged();
        lastHurt = hurt;

        if (s.armor.on) armor(ctx, mc, tr, s, sw, sh);
        if (s.keys.on) keys(ctx, mc, tr, s, sw, sh, dt, now);
        if (s.timer.on) {
            String v = Fmt.duration(now - (s.timerMode == 0 ? Runtime.LAUNCH_MS : worldJoinMs));
            text(ctx, tr, s.timer, s.timerMode == 0 ? "Session" : "World", v, s.timer.color, sw, sh, 1f);
        }
        if (s.ping.on) {
            int ms = -1;
            var nh = mc.getNetworkHandler();
            if (nh != null) { var e = nh.getPlayerListEntry(player.getUuid()); if (e != null) ms = e.getLatency(); }
            String v = mc.isInSingleplayer() ? "local" : ms <= 0 ? "--" : ms + " ms";
            text(ctx, tr, s.ping, "Ping", v, s.pingColorize && ms > 0 ? Colors.pingColor(ms) : s.ping.color, sw, sh, 1f);
        }
        if (s.reach.on) {
            double r = Runtime.COMBAT.reach(now);
            text(ctx, tr, s.reach, "Reach", r < 0 ? "--" : Fmt.fixed(r, s.reachDecimals), s.reach.color, sw, sh, 1f);
        }
        if (s.combo.on) {
            int n = Runtime.COMBAT.combo(now);
            float age = (now - Runtime.COMBAT.lastHitMs()) / 250f;
            float pop = n > 0 ? 1f + 0.25f * Math.max(0f, 1f - age) : 1f;       // small pop when the combo grows
            text(ctx, tr, s.combo, "Combo", String.valueOf(n), s.combo.color, sw, sh, pop);
        }
    }

    // ------------------------------------------------------------------ placement helpers

    private float[] place(HudWidget c, int w, int h, int sw, int sh, float extra) {
        float sc = c.scale / 100f * extra;
        int bw = Math.round(w * sc), bh = Math.round(h * sc);
        int[] p = Layout.place(c.anchor, c.offX, c.offY, sw, sh, bw, bh);
        return new float[]{p[0] - bw / 2f, p[1] - bh / 2f, sc};
    }

    private void begin(DrawContext ctx, float[] pl) {
        var m = ctx.getMatrices();
        m.pushMatrix();                                   // VERIFY: Matrix3x2fStack API (1.21.6+)
        m.translate(pl[0], pl[1]);
        m.scale(pl[2], pl[2]);
    }

    private void end(DrawContext ctx) { ctx.getMatrices().popMatrix(); }

    private void background(DrawContext ctx, HudWidget c, int w, int h) {
        if (!c.bg) return;
        Settings s = Runtime.SETTINGS;
        float pulse = s.reactive && s.reactWidgets ? LyricsHud.pulse(s) : 0f;       // audio reaction tints the glass
        int fill = pulse > 0.02f ? LyricsView.mix(0x5A141A2A, (0x90 << 24) | (s.colour & 0xFFFFFF), pulse * 0.6f) : 0x5A141A2A;
        Glass.panel(ctx, 0, 0, w, h, 8, fill, 0.6f + 0.35f * pulse, 0.12f);
    }

    // ------------------------------------------------------------------ simple "Label  value" widgets

    private void text(DrawContext ctx, TextRenderer tr, HudWidget c, String label, String value, int valueColor,
                      int sw, int sh, float extra) {
        int lw = label.isEmpty() ? 0 : tr.getWidth(label) + 4;
        int w = lw + tr.getWidth(value) + 2 * PAD, h = 9 + 2 * PAD - 2;
        float[] pl = place(c, w, h, sw, sh, extra);
        begin(ctx, pl);
        background(ctx, c, w, h);
        if (lw > 0) Glass.text(ctx, tr, label, PAD, (h - 8) / 2, 0xFFA4ADBF);
        Glass.text(ctx, tr, value, PAD + lw, (h - 8) / 2, valueColor);
        end(ctx);
    }

    // ------------------------------------------------------------------ armor status

    private void armor(DrawContext ctx, MinecraftClient mc, TextRenderer tr, Settings s, int sw, int sh) {
        EquipmentSlot[] slots = s.armorHands
                ? new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}
                : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        List<ItemStack> stacks = new ArrayList<>();
        for (EquipmentSlot slot : slots) {
            ItemStack st = mc.player.getEquippedStack(slot);
            if (!st.isEmpty()) stacks.add(st);
        }
        if (stacks.isEmpty()) return;

        int n = stacks.size(), mode = s.armorDur;
        float[] frac = new float[n];
        String[] label = new String[n];
        int textMax = 0;
        for (int i = 0; i < n; i++) {
            ItemStack st = stacks.get(i);
            if (st.isDamageable() && st.getMaxDamage() > 0) {
                int left = st.getMaxDamage() - st.getDamage();
                frac[i] = left / (float) st.getMaxDamage();
                label[i] = mode == 2 ? String.valueOf(left) : mode == 3 ? Math.round(frac[i] * 100) + "%" : "";
            } else {
                frac[i] = -1f;
                label[i] = st.getCount() > 1 ? "x" + st.getCount() : "";
            }
            textMax = Math.max(textMax, tr.getWidth(label[i]));
        }

        boolean vert = s.armorVertical;
        int w, h;
        if (vert) {
            int extra = mode == 1 ? 4 + 22 : (textMax > 0 ? 4 + textMax : 0);
            w = 2 * PAD + 16 + extra;
            h = 2 * PAD + 18 * n - 2;
        } else {
            int cell = Math.max(18, textMax + 2);
            w = 2 * PAD + cell * n - (cell - 16);
            h = 2 * PAD + 16 + (mode == 1 ? 4 : (textMax > 0 ? 10 : 0));
        }
        float[] pl = place(s.armor, w, h, sw, sh, 1f);
        begin(ctx, pl);
        background(ctx, s.armor, w, h);
        int cell = Math.max(18, textMax + 2);
        for (int i = 0; i < n; i++) {
            ItemStack st = stacks.get(i);
            int col = frac[i] >= 0 ? Colors.health(frac[i]) : 0xFFFFFFFF;
            if (vert) {
                int x = PAD, y = PAD + i * 18;
                ctx.drawItem(st, x, y);
                if (mode == 1 && frac[i] >= 0) {
                    Glass.rrect(ctx, x + 20, y + 7, 22, 3, 1, 0x66000000);
                    Glass.rrect(ctx, x + 20, y + 7, Math.max(1, Math.round(22 * frac[i])), 3, 1, col);
                } else if (!label[i].isEmpty()) {
                    Glass.text(ctx, tr, label[i], x + 20, y + 4, col);
                }
            } else {
                int x = PAD + i * cell + (cell - 16) / 2, y = PAD;
                ctx.drawItem(st, x, y);
                if (mode == 1 && frac[i] >= 0) {
                    Glass.rrect(ctx, x, y + 17, 16, 2, 1, 0x66000000);
                    Glass.rrect(ctx, x, y + 17, Math.max(1, Math.round(16 * frac[i])), 2, 1, col);
                } else if (!label[i].isEmpty()) {
                    Glass.textCentered(ctx, tr, label[i], x + 8, y + 17, col);
                }
            }
        }
        end(ctx);
    }

    // ------------------------------------------------------------------ keystrokes

    private void keys(DrawContext ctx, MinecraftClient mc, TextRenderer tr, Settings s, int sw, int sh, float dt, long now) {
        var o = mc.options;
        boolean noScreen = mc.currentScreen == null;
        long win = mc.getWindow().getHandle();
        boolean l = noScreen && GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = noScreen && GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (l && !lmbWas) lmb.click(now);
        if (r && !rmbWas) rmb.click(now);
        lmbWas = l; rmbWas = r;

        boolean[] down = {o.forwardKey.isPressed(), o.leftKey.isPressed(), o.backKey.isPressed(), o.rightKey.isPressed(),
                o.jumpKey.isPressed(), l, r};
        float k = Math.min(1f, dt * 20f);
        for (int i = 0; i < press.length; i++) press[i] += ((down[i] ? 1f : 0f) - press[i]) * k;

        final int cell = 18, gap = 2, row = cell + gap, inner = 3 * cell + 2 * gap;
        int w = 2 * PAD + inner;
        int h = 2 * PAD + 2 * cell + gap + (s.keysSpace ? gap + 12 : 0) + (s.keysMouse ? gap + cell : 0);
        float[] pl = place(s.keys, w, h, sw, sh, 1f);
        begin(ctx, pl);
        background(ctx, s.keys, w, h);
        int c = s.keys.color;
        key(ctx, tr, PAD + row, PAD, cell, cell, "W", press[0], c, null);
        key(ctx, tr, PAD, PAD + row, cell, cell, "A", press[1], c, null);
        key(ctx, tr, PAD + row, PAD + row, cell, cell, "S", press[2], c, null);
        key(ctx, tr, PAD + 2 * row, PAD + row, cell, cell, "D", press[3], c, null);
        int y = PAD + 2 * row;
        if (s.keysSpace) {
            key(ctx, tr, PAD, y, inner, 12, "", press[4], c, null);
            Glass.rrect(ctx, PAD + inner / 2 - 8, y + 5, 16, 2, 1, LyricsView.mix(0xFFFFFFFF, 0xFF10131C, press[4] * 0.85f));
            y += 12 + gap;
        }
        if (s.keysMouse) {
            int mw = (inner - gap) / 2;
            key(ctx, tr, PAD, y, mw, cell, "LMB", press[5], c, s.keysCps ? String.valueOf(lmb.cps(now)) : null);
            key(ctx, tr, PAD + mw + gap, y, mw, cell, "RMB", press[6], c, s.keysCps ? String.valueOf(rmb.cps(now)) : null);
        }
        end(ctx);
    }

    private void key(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, float p, int color, String sub) {
        p = LyricsView.clamp01(p);
        Glass.panel(ctx, x, y, w, h, 5, LyricsView.mix(0x34FFFFFF, (0xC0 << 24) | (color & 0xFFFFFF), p), 0.5f + 0.4f * p, 0.2f);
        int tc = LyricsView.mix(0xFFFFFFFF, 0xFF10131C, p * 0.85f);
        if (label.isEmpty()) return;
        if (sub == null) { Glass.textCentered(ctx, tr, label, x + w / 2, y + (h - 8) / 2, tc); return; }
        Glass.textCentered(ctx, tr, label, x + w / 2, y + 2, tc);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x + w / 2f, y + h - 8);
        m.scale(0.75f, 0.75f);
        Glass.textCentered(ctx, tr, sub + " cps", 0, 0, tc);
        m.popMatrix();
    }
}
