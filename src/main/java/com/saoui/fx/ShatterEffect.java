package com.saoui.fx;

import com.saoui.render.Projection;
import com.saoui.render.SaoDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** SAO polygon shatter: dead entities burst into glowing glass shards that drift and fade away. */
public final class ShatterEffect {
    private ShatterEffect() {}

    private static final class Shard {
        double x, y, z, px, py, pz;
        double vx, vy, vz;
        final Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f();
        final Vector3f axis = new Vector3f();
        float angle, prevAngle, spin;
        int age, life;
        int color;
    }

    private static final class Flash {
        double x, y, z;
        float radius;
        int age;
    }

    private static final List<Shard> SHARDS = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final Set<Integer> DONE = new HashSet<>();
    private static final int[] COLORS = {0xFFBFF3FF, 0xFF8FDFFF, 0xFFFFFFFF, 0xFF6FC4FF, 0xFFDDF8FF};

    public static void tick(MinecraftClient mc) {
        if (mc.world == null) {
            SHARDS.clear();
            FLASHES.clear();
            DONE.clear();
            return;
        }
        Set<Integer> alive = new HashSet<>();
        for (Entity e : mc.world.getEntities()) {
            alive.add(e.getId());
            if (e instanceof LivingEntity le && !(e instanceof ArmorStandEntity) && le.isDead()) {
                if (DONE.add(e.getId())) spawn(mc, le);
            }
        }
        DONE.retainAll(alive);

        Iterator<Shard> it = SHARDS.iterator();
        while (it.hasNext()) {
            Shard s = it.next();
            s.px = s.x; s.py = s.y; s.pz = s.z;
            s.prevAngle = s.angle;
            s.x += s.vx; s.y += s.vy; s.z += s.vz;
            s.vx *= 0.94; s.vy = s.vy * 0.94 + 0.0025; s.vz *= 0.94; // drift upward like SAO polygons
            s.angle += s.spin;
            if (++s.age >= s.life) it.remove();
        }
        FLASHES.removeIf(f -> ++f.age > 8);
    }

    public static void spawn(MinecraftClient mc, LivingEntity le) {
        Random r = le.getRandom();
        Box box = le.getBoundingBox();
        double cx = (box.minX + box.maxX) / 2, cy = (box.minY + box.maxY) / 2, cz = (box.minZ + box.maxZ) / 2;
        double vol = box.getLengthX() * box.getLengthY() * box.getLengthZ();
        int count = MathHelper.clamp((int) (vol * 90), 24, 260);
        float sizeBase = (float) MathHelper.clamp(Math.cbrt(vol) * 0.18, 0.08, 0.6);
        for (int i = 0; i < count; i++) {
            Shard s = new Shard();
            s.x = s.px = box.minX + r.nextDouble() * box.getLengthX();
            s.y = s.py = box.minY + r.nextDouble() * box.getLengthY();
            s.z = s.pz = box.minZ + r.nextDouble() * box.getLengthZ();
            Vec3d dir = new Vec3d(s.x - cx, s.y - cy + 0.2, s.z - cz).normalize();
            double sp = 0.03 + r.nextDouble() * 0.12;
            s.vx = dir.x * sp + (r.nextDouble() - 0.5) * 0.04;
            s.vy = dir.y * sp + r.nextDouble() * 0.05;
            s.vz = dir.z * sp + (r.nextDouble() - 0.5) * 0.04;
            float size = sizeBase * (0.5f + r.nextFloat());
            s.a.set(randUnit(r)).mul(size);
            s.b.set(randUnit(r)).mul(size * (0.6f + r.nextFloat() * 0.6f));
            s.c.set(randUnit(r)).mul(size * (0.6f + r.nextFloat() * 0.6f));
            s.axis.set(randUnit(r));
            s.spin = (r.nextFloat() - 0.5f) * 0.6f;
            s.life = 22 + r.nextInt(26);
            s.color = COLORS[r.nextInt(COLORS.length)];
            SHARDS.add(s);
        }
        Flash f = new Flash();
        f.x = cx; f.y = cy; f.z = cz;
        f.radius = (float) Math.max(box.getLengthX(), box.getLengthY());
        FLASHES.add(f);

        if (mc.world != null) {
            mc.world.playSound(cx, cy, cz, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.NEUTRAL, 1.0f, 1.5f, false);
            mc.world.playSound(cx, cy, cz, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.NEUTRAL, 1.0f, 1.2f, false);
        }
    }

    private static Vector3f randUnit(Random r) {
        Vector3f v = new Vector3f(r.nextFloat() - 0.5f, r.nextFloat() - 0.5f, r.nextFloat() - 0.5f);
        if (v.lengthSquared() < 1e-4f) v.set(0, 1, 0);
        return v.normalize();
    }

    public static void render(DrawContext ctx, float tickDelta) {
        Vector3f tmp = new Vector3f();
        for (Flash f : FLASHES) {
            float[] p = Projection.project(f.x, f.y, f.z);
            if (p == null) continue;
            float t = (f.age + tickDelta) / 8f;
            float pxPerBlock = ctx.getScaledWindowHeight() / (p[2] * 1.2f);
            float r = f.radius * pxPerBlock * (0.4f + t * 1.2f);
            float a = MathHelper.clamp(1f - t, 0, 1);
            SaoDraw.circle(ctx, p[0], p[1], r * 0.6f, SaoDraw.withAlpha(0xFFFFFFFF, a * 0.35f));
            SaoDraw.ring(ctx, p[0], p[1], r, Math.max(1.5f, r * 0.12f), SaoDraw.withAlpha(0xFFBFF3FF, a * 0.8f));
        }
        for (Shard s : SHARDS) {
            double x = MathHelper.lerp(tickDelta, s.px, s.x);
            double y = MathHelper.lerp(tickDelta, s.py, s.y);
            double z = MathHelper.lerp(tickDelta, s.pz, s.z);
            float ang = MathHelper.lerp(tickDelta, s.prevAngle, s.angle);
            float[] p1 = proj(tmp.set(s.a).rotateAxis(ang, s.axis.x, s.axis.y, s.axis.z), x, y, z);
            float[] p2 = proj(tmp.set(s.b).rotateAxis(ang, s.axis.x, s.axis.y, s.axis.z), x, y, z);
            float[] p3 = proj(tmp.set(s.c).rotateAxis(ang, s.axis.x, s.axis.y, s.axis.z), x, y, z);
            if (p1 == null || p2 == null || p3 == null) continue;
            float t = (s.age + tickDelta) / s.life;
            float alpha = t < 0.6f ? 0.9f : 0.9f * (1f - (t - 0.6f) / 0.4f);
            // twinkle
            alpha *= 0.75f + 0.25f * (float) Math.sin((s.age + tickDelta) * 0.9f + s.color);
            SaoDraw.tri(ctx, p1[0], p1[1], p2[0], p2[1], p3[0], p3[1], SaoDraw.withAlpha(s.color, alpha));
        }
    }

    private static float[] proj(Vector3f off, double x, double y, double z) {
        return Projection.project(x + off.x, y + off.y, z + off.z);
    }
}
