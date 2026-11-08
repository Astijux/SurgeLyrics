package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/**
 * Replacement hotbar: glass / dark / minimal styles, a selection highlight that glides between slots, the
 * selected item popping up slightly, slot numbers, optional offhand, free placement and scale.
 */
final class CustomHotbar {
    private static final int SLOT = 20, PAD = 4;
    private final Motion.Spring sel = new Motion.Spring(0f, 300f, 26f);
    private long last = System.nanoTime();

    void render(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int sw, int sh) {
        var player = mc.player;
        if (player == null) return;
        Settings s = Runtime.SETTINGS;
        HudWidget c = s.hotbar;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;

        var inv = player.getInventory();
        int selected = inv.getSelectedSlot();                      // VERIFY: getSelectedSlot() in yarn 1.21.11
        float pos = s.hotbarAnimate ? sel.step(dt, selected) : (sel.pos = selected);
        ItemStack off = player.getOffHandStack();
        boolean hasOff = s.hotbarOffhand && !off.isEmpty();

        int inner = 9 * SLOT, w = inner + 2 * PAD + (hasOff ? SLOT + 8 : 0), h = SLOT + 2 * PAD;
        float sc = c.scale / 100f;
        int bw = Math.round(w * sc), bh = Math.round(h * sc);
        int[] pl = Layout.place(c.anchor, c.offX, c.offY, sw, sh, bw, bh);

        var m = ctx.getMatrices();
        m.pushMatrix();                                            // VERIFY: Matrix3x2fStack API (1.21.6+)
        m.translate(pl[0] - bw / 2f, pl[1] - bh / 2f);
        m.scale(sc, sc);

        int accent = c.color & 0xFFFFFF;
        if (c.bg) {
            if (s.hotbarStyle == 0) Glass.panel(ctx, 0, 0, w, h, 10, 0x66141A2A, 0.8f, 0.14f);
            else if (s.hotbarStyle == 1) Glass.rrect(ctx, 0, 0, w, h, 5, 0xCC0D1017);
        }
        float hx = PAD + pos * SLOT;
        if (s.hotbarStyle == 0) Glass.panel(ctx, Math.round(hx), PAD, SLOT, SLOT, 7, (0x78 << 24) | accent, 1f, 0.3f);
        else if (s.hotbarStyle == 1) Glass.rrect(ctx, Math.round(hx), PAD, SLOT, SLOT, 3, (0xC0 << 24) | accent);
        else Glass.panel(ctx, Math.round(hx), PAD, SLOT, SLOT, 7, (0x2A << 24) | accent, 1f, 0.2f);

        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            int x = PAD + i * SLOT + 2, y = PAD + 2;
            if (!st.isEmpty()) {
                float near = Math.max(0f, 1f - Math.abs(pos - i));        // 1 under the highlight, 0 away from it
                float k = 1f + 0.14f * near;
                m.pushMatrix();
                m.translate(x + 8, y + 8 - 1.5f * near);
                m.scale(k, k);
                m.translate(-8, -8);
                ctx.drawItem(st, 0, 0);
                ctx.drawStackOverlay(tr, st, 0, 0);
                m.popMatrix();
            }
            if (s.hotbarNumbers) {
                m.pushMatrix();
                m.translate(PAD + i * SLOT + 2, PAD + 1);
                m.scale(0.6f, 0.6f);
                Glass.text(ctx, tr, String.valueOf(i + 1), 0, 0, i == selected ? 0xFFFFFFFF : 0xFF8E97AB);
                m.popMatrix();
            }
        }
        if (hasOff) {
            int x0 = PAD + inner + 4;
            Glass.rrect(ctx, x0, PAD + 3, 1, SLOT - 6, 0, 0x33FFFFFF);
            ctx.drawItem(off, x0 + 5, PAD + 2);
            ctx.drawStackOverlay(tr, off, x0 + 5, PAD + 2);
        }
        m.popMatrix();
    }
}
