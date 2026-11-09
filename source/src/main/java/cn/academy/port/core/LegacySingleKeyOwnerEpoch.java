package cn.academy.port.core;

import java.util.IdentityHashMap;
import java.util.Map;

/** Server-issued incarnations of actual owner, connection, world and ability-state references. */
public final class LegacySingleKeyOwnerEpoch {
    private record Entry(Object connection, Object world, Object state, long epoch, long[] retiredPending) {
        boolean same(Object connection, Object world, Object state) {
            return this.connection == connection && this.world == world && this.state == state;
        }
    }
    private static final Map<Object, Entry> owners = new IdentityHashMap<>();
    private static long lastIssuedEpoch;

    /** Issues only for a complete tuple, retaining the epoch while all actual references match. */
    public static synchronized long current(Object owner, Object connection, Object world, Object state) {
        if (!complete(owner, connection, world, state)) return 0;
        Entry previous = owners.get(owner);
        if (previous != null && previous.same(connection, world, state)) return previous.epoch;
        if (lastIssuedEpoch == Long.MAX_VALUE) return 0;
        long epoch = ++lastIssuedEpoch;
        owners.put(owner, new Entry(connection, world, state, epoch, new long[LegacySingleKeyProtocol.Skill.values().length]));
        return epoch;
    }
    /** Validates only existing state; never issues, replaces or allocates a cached entry. */
    public static synchronized boolean matches(Object owner, Object connection, Object world, Object state, long epoch) {
        if (epoch <= 0 || !complete(owner, connection, world, state)) return false;
        Entry existing = owners.get(owner);
        return existing != null && existing.epoch == epoch && existing.same(connection, world, state);
    }
    /** Reads an existing exact tuple, returning zero without allocating or changing cache state. */
    public static synchronized long peek(Object owner, Object connection, Object world, Object state) {
        if (!complete(owner, connection, world, state)) return 0;
        Entry existing = owners.get(owner);
        return existing != null && existing.same(connection, world, state) ? existing.epoch : 0;
    }
    /**
     * Retires an authenticated pending input even when its server start has not run yet.
     * Six fixed family watermarks bound storage; this never issues or replaces an owner entry.
     * The client shares one monotonically increasing input sequence across the six families,
     * while cancellation retires only this family in this exact issued owner incarnation.
     */
    public static synchronized boolean retirePending(Object owner, Object connection, Object world, Object state,
            long epoch, LegacySingleKeyProtocol.Skill skill, long input) {
        if (skill == null || input <= 0 || !matches(owner, connection, world, state, epoch)) return false;
        Entry existing = owners.get(owner);
        int index = skill.ordinal();
        existing.retiredPending[index] = Math.max(existing.retiredPending[index], input);
        return true;
    }
    /** Nonallocating start preflight; an equal or older canceled input cannot revive this family. */
    public static synchronized boolean pendingRetired(Object owner, Object connection, Object world, Object state,
            long epoch, LegacySingleKeyProtocol.Skill skill, long input) {
        return skill != null && input > 0 && matches(owner, connection, world, state, epoch)
            && input <= owners.get(owner).retiredPending[skill.ordinal()];
    }
    public static synchronized void forget(Object owner) { owners.remove(owner); }
    /** Drops owner references without resetting the process-wide issuance history. */
    public static synchronized void clear() { owners.clear(); }
    private static boolean complete(Object owner, Object connection, Object world, Object state) {
        return owner != null && connection != null && world != null && state != null;
    }
    private LegacySingleKeyOwnerEpoch() {}
}
