package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeCirculationListeningMotionMixinPlugin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Passive, bounded evidence of actual physical position writes, never a movement writer.
 * Only entities watched by active NEW433 controllers allocate state. Unadmitted position
 * changes invalidate a sample pair, even a tiny teleport or a move-away-and-back pair.
 * Player packet reconciliation that changes the physical move's final position is
 * conservatively refused; an exact no-op absMoveTo leaves the evidence intact. */
@EventBusSubscriber(modid = "academy")
public final class CirculationListeningMotionProof {
    public static final int MAX_WATCHED_ENTITIES = 1024, MAX_TOTAL_REFERENCES = 1024, MAX_OWNERS_PER_ENTITY = 64;
    private static int watchedEntities, references;
    private static long session = 1;
    private CirculationListeningMotionProof() {}
    public record Snapshot(long discontinuity, long physicalWrites) {}

    /** Opaque passive state; no entity/world references and no global entity collection. */
    public static final class State {
        private final long sessionId = session;
        private final Map<Object, Boolean> owners = new IdentityHashMap<>();
        private long discontinuity, physicalWrites;
        private boolean physical, permit, used, invalid;
        private double x, y, z;
        State() {}
        boolean add(Object owner) {
            if (owner == null || sessionId != session || invalid) return false;
            if (owners.containsKey(owner)) return true;
            if (owners.size() >= MAX_OWNERS_PER_ENTITY || references >= MAX_TOTAL_REFERENCES
                    || owners.isEmpty() && watchedEntities >= MAX_WATCHED_ENTITIES) return false;
            if (owners.isEmpty()) watchedEntities++;
            owners.put(owner, Boolean.TRUE); references++; return true;
        }
        boolean remove(Object owner) {
            if (owners.remove(owner) != null && sessionId == session) {
                references--; if (owners.isEmpty()) watchedEntities--;
            }
            return owners.isEmpty();
        }
        void poison() {
            permit = false;
            if (discontinuity == Long.MAX_VALUE) invalid = true; else discontinuity++;
        }
        boolean begin(double toX, double toY, double toZ) {
            if (physical || !Double.isFinite(toX) || !Double.isFinite(toY) || !Double.isFinite(toZ)) {
                poison(); return false;
            }
            physical = permit = true; used = false; x = toX; y = toY; z = toZ; return true;
        }
        void changed(double toX, double toY, double toZ) {
            if (physical && permit && !used && toX == x && toY == y && toZ == z) {
                permit = false; used = true;
                if (physicalWrites == Long.MAX_VALUE) invalid = true; else physicalWrites++;
            } else poison();
        }
        void observe(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
            if (fromX != toX || fromY != toY || fromZ != toZ) changed(toX, toY, toZ);
        }
        void end(boolean completed) {
            if (!completed) poison();
            physical = permit = used = false;
        }
        Snapshot snapshot() { return invalid || sessionId != session || owners.isEmpty() ? null : new Snapshot(discontinuity, physicalWrites); }
    }

    public static boolean available() { return NativeCirculationListeningMotionMixinPlugin.available(); }
    public static boolean watch(Entity entity, Object ownerToken) {
        if (!available() || !thread(entity) || !(entity instanceof NativeCirculationListeningMotionAccess access)) return false;
        State state = access.academy$circulationListeningMotionState();
        if (state == null || state.sessionId != session) state = new State();
        if (!state.add(ownerToken)) return false;
        access.academy$circulationListeningMotionState(state); return true;
    }
    public static void unwatch(Entity entity, Object ownerToken) {
        if (entity instanceof NativeCirculationListeningMotionAccess access) {
            State state = access.academy$circulationListeningMotionState();
            if (state != null && state.remove(ownerToken)) access.academy$circulationListeningMotionState(null);
        }
    }
    public static Snapshot snapshot(Entity entity) {
        if (!available() || !thread(entity)) return null;
        State state = state(entity); return state == null ? null : state.snapshot();
    }
    private static State state(Entity entity) {
        if (!(entity instanceof NativeCirculationListeningMotionAccess access)) return null;
        State state = access.academy$circulationListeningMotionState();
        return state != null && state.sessionId == session && !state.owners.isEmpty() ? state : null;
    }
    private static boolean thread(Entity entity) {
        return entity != null && !entity.level().isClientSide && entity.getServer() != null && entity.getServer().isSameThread();
    }
    /** HEAD observer: no changed coordinates means no history mutation. Never reads blocks/entities. */
    public static void beforeRaw(Entity entity, double x, double y, double z) {
        try {
            State state = state(entity); if (state == null) return;
            if (!thread(entity)) { state.invalid = true; return; }
            state.observe(entity.getX(), entity.getY(), entity.getZ(), x, y, z);
        } catch (RuntimeException | LinkageError failure) { NativeCirculationListeningMotionMixinPlugin.disable(); }
    }
    /** Permit is scoped to one exact native setPos call, with one matching raw write. */
    public static void physicalSet(Entity entity, double x, double y, double z, Operation<Void> original) {
        State state = null; boolean entered = false, completed = false;
        try {
            state = state(entity);
            if (state != null && available() && thread(entity)) entered = state.begin(x, y, z);
        } catch (RuntimeException | LinkageError failure) { NativeCirculationListeningMotionMixinPlugin.disable(); }
        try { original.call(entity, x, y, z); completed = true; }
        finally {
            try { if (state != null && entered) state.end(completed); }
            catch (RuntimeException | LinkageError failure) { NativeCirculationListeningMotionMixinPlugin.disable(); }
        }
    }
    public static int watchedEntityCount() { return watchedEntities; }
    public static int watchReferenceCount() { return references; }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        watchedEntities = references = 0;
        if (session == Long.MAX_VALUE) NativeCirculationListeningMotionMixinPlugin.disable(); else session++;
    }
}
