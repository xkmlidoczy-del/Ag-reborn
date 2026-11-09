package cn.academy.port.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cosmetic receipts for already authenticated ARMED cues; never grants gameplay/input ownership. */
public final class NewSkillWindowVisualLease {
    public record Identity(int kind, UUID owner, long token, long input, long epoch) {}
    private record Lease(Identity identity, long sequence, long until) {}
    private final Map<Integer, Lease> leases = new HashMap<>();

    public void arm(Identity identity, long sequence, long now, int lifetime) {
        if (identity == null || identity.owner() == null || identity.kind() < 0 || identity.kind() > 7
                || identity.token() <= 0 || identity.input() <= 0 || identity.epoch() <= 0
                || sequence <= 0 || lifetime <= 0 || lifetime > 120) return;
        leases.put(identity.kind(), new Lease(identity, sequence, now + lifetime));
    }

    /** Only ordered DIVERT/END packets may use a receipt after the ordinary input ack has retired. */
    public boolean accept(Identity identity, long sequence, long now, boolean divert, boolean end) {
        if (identity == null || divert == end) return false;
        Lease lease = leases.get(identity.kind());
        if (lease == null) return false;
        if (now >= lease.until()) { leases.remove(identity.kind()); return false; }
        if (!lease.identity().equals(identity) || sequence <= lease.sequence()) return false;
        if (end) leases.remove(identity.kind());
        else leases.put(identity.kind(), new Lease(identity, sequence, lease.until()));
        return true;
    }

    public void expire(long now) { leases.values().removeIf(lease -> now >= lease.until()); }
    public void clear() { leases.clear(); }
}
