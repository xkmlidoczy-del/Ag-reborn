package cn.academy.port.client;

import java.util.*;
import org.joml.Matrix4f;

/** Engine-free screen-space contract. The list is a bounded untrusted proposal, never ownership proof. */
public final class MagneticItemPullSelectionGeometry {
    public static final int MAX_CANDIDATES = 16;
    public static final double RANGE = cn.academy.port.highpower.core.MagneticItemPullRules.RANGE;
    public record Rect(double left, double top, double right, double bottom) {
        public boolean usable() { return right - left >= 3 && bottom - top >= 3; }
        public boolean contains(double x, double y) { return usable() && Double.isFinite(x) && Double.isFinite(y) && x >= left && x <= right && y >= top && y <= bottom; }
    }
    public record Point(UUID id, double x, double y, double distanceSquared) {}
    private MagneticItemPullSelectionGeometry() {}
    /** Crosshair is the first corner. Clamp the dragged corner to the viewport and a finite maximum extent. */
    public static Rect rectangle(int width, int height, double mouseX, double mouseY) {
        if (width <= 0 || height <= 0 || !Double.isFinite(mouseX) || !Double.isFinite(mouseY)) return new Rect(0,0,0,0);
        double x = width / 2.0, y = height / 2.0;
        double dx = Math.min(256, width * .45), dy = Math.min(180, height * .45);
        double endX = Math.clamp(mouseX, x - dx, x + dx), endY = Math.clamp(mouseY, y - dy, y + dy);
        return new Rect(Math.min(x,endX),Math.min(y,endY),Math.max(x,endX),Math.max(y,endY));
    }
    /** NeoForge keeps the camera model-view separate from the entity pose stack at AFTER_PARTICLES. */
    public static Matrix4f viewProjection(Matrix4f projection,Matrix4f modelView,Matrix4f localPose) {
        return new Matrix4f(projection).mul(modelView).mul(localPose);
    }
    /** OpenGL clip-space projection excludes behind-camera, near/far and offscreen candidates. */
    public static Point project(UUID id,double x,double y,double z,double w,double distanceSquared,int width,int height) {
        if (id == null || width <= 0 || height <= 0 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(w) || w <= 0 || !Double.isFinite(distanceSquared) || distanceSquared < 0 || distanceSquared > RANGE * RANGE) return null;
        x /= w; y /= w; z /= w;
        if (x < -1 || x > 1 || y < -1 || y > 1 || z < -1 || z > 1) return null;
        return new Point(id,(x+1)*width/2.0,(1-y)*height/2.0,distanceSquared);
    }
    public static boolean fresh(long now,long projectedAt) { return projectedAt>0&&now>=projectedAt&&now-projectedAt<=250; }
    public static List<UUID> candidates(Rect rect,Collection<Point> projected) {
        if (rect == null || projected == null || !rect.usable()) return List.of();
        return projected.stream().filter(Objects::nonNull).filter(p -> p.id != null && Double.isFinite(p.distanceSquared)
                && p.distanceSquared >= 0 && p.distanceSquared <= RANGE * RANGE && rect.contains(p.x,p.y))
                .sorted(Comparator.comparingDouble(Point::distanceSquared).thenComparing(p -> p.id.toString()))
                .map(Point::id).distinct().limit(MAX_CANDIDATES).toList();
    }
}
