package cn.academy.biohum;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class SignatureEffects {
    private SignatureEffects() {}

    static void dust(ServerLevel level, Vec3 at, int rgb, float size) {
        var color = new Vector3f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f);
        point(level, at, new DustParticleOptions(color, size));
    }

    private static void point(ServerLevel level, Vec3 at, ParticleOptions particle) {
        level.sendParticles(particle, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    static void line(ServerLevel level, Vec3 from, Vec3 to, int color, float size, int segments) {
        for (int i = 0; i <= segments; i++) dust(level, from.lerp(to, (double) i / segments), color, size);
    }

    static void arc(ServerLevel level, Vec3 from, Vec3 to, int color, double bend) {
        int segments = Math.min(36, Math.max(10, (int) (from.distanceTo(to) * 4)));
        Vec3 side = to.subtract(from).normalize().cross(new Vec3(0, 1, 0));
        for (int i = 0; i <= segments; i++) {
            double phase = (double) i / segments;
            double taper = Math.sin(phase * Math.PI);
            Vec3 at = from.lerp(to, phase).add(side.scale(Math.sin(i * 1.82) * bend * taper))
                    .add(0, Math.sin(i * 1.17) * bend * taper * .70, 0);
            dust(level, at, color, .95f);
            if (i % 4 == 0) point(level, at, ParticleTypes.ELECTRIC_SPARK);
        }
    }

    static void ring(ServerLevel level, Vec3 center, double radius, int color, int points) {
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            dust(level, center.add(radius * Math.cos(angle), 0, radius * Math.sin(angle)), color, 1.2f);
        }
    }

    static void corners(ServerLevel level, Vec3 center, int color) {
        for (int side = 0; side < 4; side++) {
            double angle = Math.PI * (side + .5) / 2;
            Vec3 corner = center.add(.66 * Math.cos(angle), .06, .66 * Math.sin(angle));
            line(level, corner, corner.add(0, 1.18, 0), color, 1.05f, 6);
        }
    }

    static void beam(ServerLevel level, Vec3 from, Vec3 to, int color) {
        int segments = Math.max(8, Math.min(54, (int) (from.distanceTo(to) * 5)));
        line(level, from, to, color, 1.55f, segments);
        line(level, from, to, 0xf9ffec, .62f, segments);
    }

    static void wings(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 side = to.subtract(from).normalize().cross(new Vec3(0, 1, 0));
        for (int i = 0; i <= 22; i++) {
            double t = i / 22.0;
            Vec3 at = from.lerp(to, t).add(0, Math.sin(t * Math.PI) * .50, 0);
            for (int sign : new int[]{-1, 1}) {
                Vec3 feather = at.add(side.scale(sign * Math.sin(t * Math.PI) * 1.10));
                dust(level, feather, i % 3 == 0 ? 0xcdcaf4 : 0xfffbef, 1.08f);
            }
        }
    }
}
