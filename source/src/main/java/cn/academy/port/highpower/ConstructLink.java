package cn.academy.port.highpower;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * Engine-neutral seam between V-ADV-01 and an optional rigid-body backend. ACA never references the backend directly: the
 * Sable implementation is loaded by name only when the {@code sable} mod is present, so ACA runs unchanged without it.
 */
public interface ConstructLink {
    /** Result of a bounded connected-block search. {@code reason} is a lang suffix when {@code ok} is false. */
    record Selection(boolean ok, String reason, BlockPos origin, int blocks, int span, double[] min, double[] max, double[] center) {
        public static Selection refuse(String reason) { return new Selection(false, reason, null, 0, 0, null, null, null); }
    }
    /** Live state of a held body: world centre of mass, velocity, world bounds, mass, tilt from vertical and yaw (radians). */
    record Body(double[] center, double[] velocity, double[] min, double[] max, double mass, double tilt, double yaw) {}
    record Grab(boolean ok, String reason, UUID body, int blocks, int span, double mass) {
        public static Grab refuse(String reason) { return new Grab(false, reason, null, 0, 0, 0); }
    }

    Selection select(ServerPlayer player, BlockPos origin, int maxBlocks, int maxSpan);
    Grab grab(ServerPlayer player, Selection selection, int maxBlocks, int maxSpan);
    /** Owner check for re-grabbing an existing ACA-made body that contains {@code pos}; null when none. */
    UUID ownedBodyAt(ServerPlayer player, BlockPos pos);
    Body body(ServerLevel level, UUID body);
    /** Hold target applied on every physics step until {@link #release}. */
    void control(ServerLevel level, UUID body, double[] target, double yaw, double maxAccel);
    double effort(UUID body);
    void release(UUID body);
    /** World-space view-ray aim, excluding the held body so the caster can aim through it. */
    net.minecraft.world.phys.Vec3 throwAim(ServerPlayer player, UUID ignoredBody, double range);
    boolean launch(ServerLevel level, UUID body, double[] look, double speed);
    /** Writes the body back to the block grid at the nearest quarter turn; returns null on success or a lang suffix. */
    String land(ServerPlayer player, UUID body);
    List<LivingEntity> touching(ServerLevel level, UUID body, double inflate);
    /** Up to {@code max} block states sampled from the body's own blocks, for debris and for the blocks that lock a target. */
    default List<net.minecraft.world.level.block.state.BlockState> palette(ServerLevel level, UUID body, int max) { return List.of(); }
    /** Destroys the body and the blocks it carries without writing them back to the grid; true when something was destroyed. */
    default boolean shatter(ServerLevel level, UUID body) { return false; }

    static ConstructLink get() { return Holder.INSTANCE; }

    final class Holder {
        private Holder() {}
        static final ConstructLink INSTANCE = load();
        private static ConstructLink load() {
            try {
                if (!ModList.get().isLoaded("sable")) return null;
                return (ConstructLink) Class.forName("cn.academy.port.compat.sable.SableConstructBridge").getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                return null;
            }
        }
    }
}
