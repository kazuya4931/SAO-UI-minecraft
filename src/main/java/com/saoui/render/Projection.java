package com.saoui.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Captures the world camera matrices each frame so HUD code can project world points to screen. */
public final class Projection {
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3d camPos = Vec3d.ZERO;
    private static boolean valid;

    private Projection() {}

    public static void capture(Matrix4f view, Matrix4f proj, Vec3d cam) {
        VIEW.set(view);
        PROJ.set(proj);
        camPos = cam;
        valid = true;
    }

    public static Vec3d cameraPos() {
        return camPos;
    }

    /** @return {screenX, screenY, depth(w)} in scaled GUI pixels, or null when behind the camera. */
    public static float[] project(double x, double y, double z) {
        if (!valid) return null;
        Vector4f v = new Vector4f((float) (x - camPos.x), (float) (y - camPos.y), (float) (z - camPos.z), 1f);
        VIEW.transform(v);
        PROJ.transform(v);
        if (v.w <= 0.05f) return null;
        float nx = v.x / v.w, ny = v.y / v.w;
        MinecraftClient mc = MinecraftClient.getInstance();
        float sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        return new float[]{(nx * 0.5f + 0.5f) * sw, (1f - (ny * 0.5f + 0.5f)) * sh, v.w};
    }
}
