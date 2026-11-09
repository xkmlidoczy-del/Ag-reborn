/* AcademyCraft 1.0.7 VecManip modern transport seam. GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AcademyNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.LinkedHashSet;
import java.util.Set;

final class VectorStarterSupport {
    private VectorStarterSupport() {}
    static boolean serverThread(ServerPlayer p) { return p != null && p.serverLevel().getServer().isSameThread(); }
    static boolean ready(ServerPlayer p) {
        return serverThread(p) && p.isAlive() && !p.isRemoved() && !p.isSpectator() && finite(p.position())
                && Float.isFinite(p.getYRot()) && Float.isFinite(p.getXRot()) && Float.isFinite(p.yRotO) && Float.isFinite(p.xRotO);
    }
    static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    static Set<ServerPlayer> audience(ServerPlayer p) {
        var result = new LinkedHashSet<ServerPlayer>();
        for (var viewer : p.serverLevel().players()) if (viewer.distanceToSqr(p) <= 25 * 25) result.add(viewer);
        result.add(p); return Set.copyOf(result);
    }
    static CompoundTag packet(ServerPlayer p, String kind, long token, long input) {
        var tag = new CompoundTag(); tag.putString("kind", kind); tag.putInt("entity", p.getId());
        tag.putLong("token", token); tag.putLong("input", input); return tag;
    }
    static void position(CompoundTag tag, Vec3 v) { tag.putDouble("x", v.x); tag.putDouble("y", v.y); tag.putDouble("z", v.z); }
    static void send(Set<ServerPlayer> audience, CompoundTag tag) {
        for (var viewer : audience) if (!viewer.hasDisconnected()) PacketDistributor.sendToPlayer(viewer, new AcademyNetwork.ClientData(tag));
    }
}
