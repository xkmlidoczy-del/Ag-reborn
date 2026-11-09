/* AcademyCraft 1.0.7 context transport adapter, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AcademyNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.LinkedHashSet;
import java.util.Set;

/** Common-only utilities. Packets never accept client geometry, duration, damage, or mastery. */
final class MeltdownerStarterSupport {
    private MeltdownerStarterSupport() {}
    static boolean serverThread(ServerPlayer player) {
        return player != null && player.serverLevel().getServer().isSameThread();
    }
    static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
    static boolean finiteAim(ServerPlayer player) {
        return Float.isFinite(player.getYRot()) && Float.isFinite(player.getYHeadRot()) && Float.isFinite(player.getXRot());
    }
    static Set<ServerPlayer> audience(ServerPlayer player) {
        var result = new LinkedHashSet<ServerPlayer>();
        for (var viewer : player.serverLevel().players()) if (viewer.distanceToSqr(player) <= 25 * 25) result.add(viewer);
        result.add(player); return Set.copyOf(result);
    }
    static CompoundTag packet(ServerPlayer player, String kind, long token, long input) {
        var tag = new CompoundTag(); tag.putString("kind", kind); tag.putInt("entity", player.getId());
        tag.putLong("token", token); tag.putLong("input", input); return tag;
    }
    static void position(CompoundTag tag, Vec3 point) {
        tag.putDouble("x", point.x); tag.putDouble("y", point.y); tag.putDouble("z", point.z);
    }
    static void send(Set<ServerPlayer> audience, CompoundTag tag) {
        for (var viewer : audience) if (!viewer.hasDisconnected())
            PacketDistributor.sendToPlayer(viewer, new AcademyNetwork.ClientData(tag));
    }
}
