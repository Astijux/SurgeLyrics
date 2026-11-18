package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PvP information widgets: target health, potion timers, item counter, coordinates, CPS, own health, speed, stance,
 * and the low-health and no-totem warnings.
 * Everything shown is information the client already has. Nothing here changes reach, hitboxes or combat.
 */
final class PvpWidgets {
    private static final int PAD = 5;
    private ItemStack iconTotem, iconGapple, iconPearl, iconCrystal, iconXp, iconArrow;

    void render(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int sw, int sh) {
        var player = mc.player;
        if (player == null) return;
        Settings s = Runtime.SETTINGS;
        long now = System.currentTimeMillis();
        float time = LyricsHud.time();

        if (s.lowHealthWarn) Safe.gui("low-health", ctx, () -> lowHealth(ctx, player.getHealth(), s, sw, sh, time));
        if (s.target.on) Safe.gui("target-hud", ctx, () -> target(ctx, mc, tr, s, sw, sh, now));
        if (s.effects.on) Safe.gui("effects-hud", ctx, () -> effects(ctx, mc, tr, s, sw, sh, time));
        if (s.items.on) Safe.gui("item-counter", ctx, () -> items(ctx, mc, tr, s, sw, sh));
        if (s.coords.on) Safe.gui("coords", ctx, () -> coords(ctx, mc, tr, s, sw, sh));
        if (s.health.on) Safe.gui("health-hud", ctx, () -> health(ctx, mc, tr, s, sw, sh));
        if (s.speed.on) Safe.gui("speed-hud", ctx, () -> speed(ctx, mc, tr, s, sw, sh));
        if (s.stance.on) Safe.gui("stance-hud", ctx, () -> stance(ctx, mc, tr, s, sw, sh));
        if (s.lowTotemWarn) Safe.gui("totem-warn", ctx, () -> totemWarn(ctx, mc, tr, sw, sh, time));
        if (s.cps.on) Safe.gui("cps", ctx, () -> {
            int l = Clicks.L.cps(now), r = Clicks.R.cps(now);
            text(ctx, tr, s.cps, "CPS", s.cpsRight ? l + "  |  " + r : String.valueOf(l), s.cps.color, sw, sh);
        });
    }

    // ------------------------------------------------------------------ low health: red edges that pulse

    private void lowHealth(DrawContext ctx, float hp, Settings s, int sw, int sh, float time) {
        float k = Warn.health(hp, s.lowHealthHp, time);
        if (k < 0.02f) return;
        for (int i = 0; i < 8; i++) {
            int a = Math.round(255 * 0.34f * k * (1f - i / 8f));
            if (a < 2) break;
            int c = (a << 24) | 0xE53935;
            ctx.fill(0, i * 2, sw, i * 2 + 2, c);
            ctx.fill(0, sh - i * 2 - 2, sw, sh - i * 2, c);
            ctx.fill(0, i * 2, i * 2 + 2, sh, c);
            ctx.fill(sw - i * 2 - 2, i * 2, sw - i * 2, sh, c);
        }
    }

    // ------------------------------------------------------------------ target health

    private void target(DrawContext ctx, MinecraftClient mc, TextRenderer tr, Settings s, int sw, int sh, long now) {
        LivingEntity le = null;
        if (s.targetSource != 2 && Runtime.lastTargetId >= 0 && now - Runtime.lastTargetMs < s.targetHoldSec * 1000L && mc.world != null) {
            Entity last = mc.world.getEntityById(Runtime.lastTargetId);
            if (last instanceof LivingEntity l && l.isAlive()) le = l;
        }
        if (le == null && s.targetSource != 1 && mc.targetedEntity instanceof LivingEntity l2) le = l2;     // VERIFY: MinecraftClient.targetedEntity
        if (le == null) return;

        String name = le.getDisplayName().getString();
        float hp = le.getHealth(), max = Math.max(1f, le.getMaxHealth()), abs = le.getAbsorptionAmount();
        float frac = LyricsView.clamp01(hp / max);
        String hpText = Fmt.fixed(hp, 1) + " / " + Fmt.fixed(max, 0) + (abs > 0 ? "  +" + Fmt.fixed(abs, 0) : "");
        String extra = "";
        if (s.targetArmor) extra += "Armor " + le.getArmor();
        if (s.targetDistance) extra += (extra.isEmpty() ? "" : "   ") + Fmt.fixed(mc.player.distanceTo(le), 1) + "m";

        int w = Math.max(120, Math.max(tr.getWidth(name) + tr.getWidth(hpText) + 20, tr.getWidth(extra) + 2 * PAD));
        int h = 2 * PAD + 9 + 4 + 7 + (extra.isEmpty() ? 0 : 12);
        float[] pl = place(s.target, w, h, sw, sh);
        begin(ctx, pl);
        background(ctx, s.target, w, h);
        Glass.text(ctx, tr, Glass.fit(tr, name, w - 2 * PAD - tr.getWidth(hpText) - 6), PAD, PAD, 0xFFF2F5FA);
        Glass.textRight(ctx, tr, hpText, w - PAD, PAD, Colors.health(frac));
        int by = PAD + 13, bw = w - 2 * PAD;
        Glass.rrect(ctx, PAD, by, bw, 7, 3, 0x66000000);
        if (frac > 0.01f) Glass.rrect(ctx, PAD, by, Math.max(2, Math.round(bw * frac)), 7, 3, Colors.health(frac));
        if (abs > 0) Glass.rrect(ctx, PAD, by + 5, Math.max(2, Math.round(bw * LyricsView.clamp01(abs / max))), 2, 1, 0xFFFACC15);   // absorption
        if (!extra.isEmpty()) Glass.text(ctx, tr, extra, PAD, by + 10, 0xFFA4ADBF);
        end(ctx);
    }

    // ------------------------------------------------------------------ potion effects with timers

    private void effects(DrawContext ctx, MinecraftClient mc, TextRenderer tr, Settings s, int sw, int sh, float time) {
        List<StatusEffectInstance> list = new ArrayList<>(mc.player.getStatusEffects());
        if (list.isEmpty()) return;
        list.sort(Comparator.comparingInt(e -> e.isInfinite() ? Integer.MAX_VALUE : e.getDuration()));        // VERIFY: StatusEffectInstance.isInfinite()
        int rows = Math.min(8, list.size());
        String[] names = new String[rows], times = new String[rows];
        boolean[] bad = new boolean[rows], warn = new boolean[rows];
        int w = 0;
        for (int i = 0; i < rows; i++) {
            StatusEffectInstance e = list.get(i);
            var type = e.getEffectType().value();                                                              // VERIFY: RegistryEntry<StatusEffect>.value()
            String nm = type.getName().getString();
            if (s.effectsLevel && e.getAmplifier() > 0) nm += " " + Fmt.roman(e.getAmplifier() + 1);
            names[i] = nm;
            times[i] = e.isInfinite() ? "inf" : Fmt.duration(e.getDuration() * 50L);
            bad[i] = !type.isBeneficial();                                                                     // VERIFY: StatusEffect.isBeneficial()
            warn[i] = s.effectsWarnSec > 0 && !e.isInfinite() && e.getDuration() <= s.effectsWarnSec * 20;
            w = Math.max(w, tr.getWidth(nm) + 14 + tr.getWidth(times[i]));
        }
        w += 2 * PAD;
        int h = 2 * PAD - 2 + rows * 11;
        float[] pl = place(s.effects, w, h, sw, sh);
        begin(ctx, pl);
        background(ctx, s.effects, w, h);
        for (int i = 0; i < rows; i++) {
            int col = bad[i] ? 0xFFF87171 : s.effects.color;
            if (warn[i]) col = LyricsView.mix(col, 0xFFFFFFFF, 0.5f + 0.5f * (float) Math.sin(time * 8f));        // about to run out: blink
            Glass.text(ctx, tr, names[i], PAD, PAD - 1 + i * 11, col);
            Glass.textRight(ctx, tr, times[i], w - PAD, PAD - 1 + i * 11, warn[i] ? col : 0xFFA4ADBF);
        }
        end(ctx);
    }

    // ------------------------------------------------------------------ item counter

    private void items(DrawContext ctx, MinecraftClient mc, TextRenderer tr, Settings s, int sw, int sh) {
        if (iconTotem == null) {
            iconTotem = new ItemStack(Items.TOTEM_OF_UNDYING); iconGapple = new ItemStack(Items.GOLDEN_APPLE);
            iconPearl = new ItemStack(Items.ENDER_PEARL); iconCrystal = new ItemStack(Items.END_CRYSTAL);
            iconXp = new ItemStack(Items.EXPERIENCE_BOTTLE); iconArrow = new ItemStack(Items.ARROW);
        }
        int totem = 0, gapple = 0, pearl = 0, crystal = 0, xp = 0, arrow = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {                                    // hotbar, main, armor and offhand slots
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            int c = st.getCount();
            if (st.isOf(Items.TOTEM_OF_UNDYING)) totem += c;
            else if (st.isOf(Items.GOLDEN_APPLE) || st.isOf(Items.ENCHANTED_GOLDEN_APPLE)) gapple += c;
            else if (st.isOf(Items.ENDER_PEARL)) pearl += c;
            else if (st.isOf(Items.END_CRYSTAL)) crystal += c;
            else if (st.isOf(Items.EXPERIENCE_BOTTLE)) xp += c;
            else if (st.isOf(Items.ARROW)) arrow += c;
        }
        List<ItemStack> icons = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        if (s.itemTotem) add(icons, counts, iconTotem, totem, s);
        if (s.itemGapple) add(icons, counts, iconGapple, gapple, s);
        if (s.itemPearl) add(icons, counts, iconPearl, pearl, s);
        if (s.itemCrystal) add(icons, counts, iconCrystal, crystal, s);
        if (s.itemXp) add(icons, counts, iconXp, xp, s);
        if (s.itemArrow) add(icons, counts, iconArrow, arrow, s);
        if (icons.isEmpty()) return;

        int n = icons.size();
        String[] txt = new String[n];
        int[] tw = new int[n];
        for (int i = 0; i < n; i++) { txt[i] = String.valueOf(counts.get(i)); tw[i] = tr.getWidth(txt[i]); }
        int w, h;
        if (s.itemsVertical) {
            int tmax = 0;
            for (int v : tw) tmax = Math.max(tmax, v);
            w = 2 * PAD + 20 + tmax;
            h = 2 * PAD + 18 * n - 2;
        } else {
            w = 2 * PAD - 6;
            for (int v : tw) w += 20 + v + 8;
            h = 2 * PAD + 16;
        }
        float[] pl = place(s.items, w, h, sw, sh);
        begin(ctx, pl);
        background(ctx, s.items, w, h);
        int x = PAD, y = PAD;
        for (int i = 0; i < n; i++) {
            int col = counts.get(i) == 0 ? 0xFFF87171 : s.items.color;
            ctx.drawItem(icons.get(i), x, y);
            Glass.text(ctx, tr, txt[i], x + 18, y + 4, col);
            if (s.itemsVertical) y += 18; else x += 20 + tw[i] + 8;
        }
        end(ctx);
    }

    private void add(List<ItemStack> icons, List<Integer> counts, ItemStack icon, int count, Settings s) {
        if (count == 0 && s.itemsHideZero) return;
        icons.add(icon);
        counts.add(count);
    }

    // ------------------------------------------------------------------ coordinates

    private void coords(DrawContext ctx, MinecraftClient mc, TextRenderer tr, Settings s, int sw, int sh) {
        var p = mc.player;
        int d = s.coordsDecimals;
        String l1 = "XYZ  " + Fmt.fixed(p.getX(), d) + "  " + Fmt.fixed(p.getY(), d) + "  " + Fmt.fixed(p.getZ(), d);
        if (s.coordsFacing) l1 += "   " + Fmt.facing(p.getYaw());
        String l2 = null;
        if (s.coordsNether && mc.world != null) {
            boolean inNether = mc.world.getRegistryKey() == net.minecraft.world.World.NETHER;                  // VERIFY: World.NETHER
            l2 = inNether ? "Overworld  " + Fmt.fixed(p.getX() * 8, d) + "  " + Fmt.fixed(p.getZ() * 8, d)
                          : "Nether  " + Fmt.fixed(p.getX() / 8, d) + "  " + Fmt.fixed(p.getZ() / 8, d);
        }
        int w = Math.max(tr.getWidth(l1), l2 == null ? 0 : tr.getWidth(l2)) + 2 * PAD;
        int h = 2 * PAD - 2 + 9 + (l2 == null ? 0 : 11);
        float[] pl = place(s.coords, w, h, sw, sh);
        begin(ctx, pl);
        background(ctx, s.coords, w, h);
        Glass.text(ctx, tr, l1, PAD, PAD - 1, s.coords.color);
        if (l2 != null) Glass.text(ctx, tr, l2, PAD, PAD + 10, 0xFFA4ADBF);
        end(ctx);
    }

    // ------------------------------------------------------------------ own health, speed, stance, totem warning

    private void health(DrawContext ctx, MinecraftClient mc, TextRenderer tr, Settings s, int sw, int sh) {
        var p = mc.player;
        float hp = p.getHealth(), max = Math.max(1f, p.getMaxHealth()), abs = p.getAbsorptionAmount();
        String v = Fmt.fixed(hp, 1) + (s.healthAbsorb && abs > 0 ? "  +" + Fmt.fixed(abs, 0) : "");
        text(ctx, tr, s.health, "HP", v, s.healthColorize ? Colors.health(hp / max) : s.health.color, sw, sh);
    }

    private double spX, spZ, spVal;
    private long spNs;

    /** Horizontal speed in blocks per second, sampled ten times a second and smoothed so it doesn't flicker. */
    private void speed(DrawContext ctx, MinecraftClient mc, TextRenderer tr, Settings s, int sw, int sh) {
        var p = mc.player;
        long now = System.nanoTime();
        if (spNs == 0 || now - spNs > 1_000_000_000L) {                       // first frame, or we were off for a while
            spX = p.getX(); spZ = p.getZ(); spNs = now; spVal = 0;
        } else if (now - spNs >= 100_000_000L) {
            double dt = (now - spNs) / 1e9, dx = p.getX() - spX, dz = p.getZ() - spZ;
            spVal = spVal * 0.5 + (Math.sqrt(dx * dx + dz * dz) / dt) * 0.5;
            spX = p.getX(); spZ = p.getZ(); spNs = now;
        }
        text(ctx, tr, s.speed, "Speed", Fmt.fixed(spVal, s.speedDecimals) + " b/s", s.speed.color, sw, sh);
    }

    private void stance(DrawContext ctx, MinecraftClient mc, TextRenderer tr, Settings s, int sw, int sh) {
        var p = mc.player;
        boolean sneak = p.isSneaking(), sprint = p.isSprinting();
        if (!sneak && !sprint && !s.stanceAlways) return;
        String v = sneak ? "Sneaking" : sprint ? "Sprinting" : "Walking";
        int col = sneak ? 0xFFFACC15 : sprint ? s.stance.color : 0xFFA4ADBF;
        text(ctx, tr, s.stance, "", v, col, sw, sh);
    }

    /** Pulsing "NO TOTEM" under the crosshair while you carry none. */
    private void totemWarn(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int sw, int sh, float time) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isEmpty() && st.isOf(Items.TOTEM_OF_UNDYING)) return;
        }
        int a = Math.round(120 + 135 * (0.5f + 0.5f * (float) Math.sin(time * 6f)));
        Glass.textCentered(ctx, tr, "NO TOTEM", sw / 2, sh / 2 + 34, (a << 24) | 0xF87171);
    }

    // ------------------------------------------------------------------ shared drawing helpers

    private float[] place(HudWidget c, int w, int h, int sw, int sh) {
        float sc = c.scale / 100f;
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
        float pulse = s.reactive && s.reactWidgets ? LyricsHud.pulse(s) : 0f;
        int fill = pulse > 0.02f ? LyricsView.mix(Glass.hudFill(0x5A), (0x90 << 24) | (s.colour & 0xFFFFFF), pulse * 0.6f) : Glass.hudFill(0x5A);
        Glass.panel(ctx, 0, 0, w, h, 8, fill, 0.6f + 0.35f * pulse, 0.12f);
    }

    private void text(DrawContext ctx, TextRenderer tr, HudWidget c, String label, String value, int valueColor, int sw, int sh) {
        int lw = label.isEmpty() ? 0 : tr.getWidth(label) + 4;
        int w = lw + tr.getWidth(value) + 2 * PAD, h = 9 + 2 * PAD - 2;
        float[] pl = place(c, w, h, sw, sh);
        begin(ctx, pl);
        background(ctx, c, w, h);
        if (lw > 0) Glass.text(ctx, tr, label, PAD, (h - 8) / 2, 0xFFA4ADBF);
        Glass.text(ctx, tr, value, PAD + lw, (h - 8) / 2, valueColor);
        end(ctx);
    }
}
