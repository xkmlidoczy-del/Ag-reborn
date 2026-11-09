package cn.academy.port.core;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public final class LegacySingleKeyOwnerEpochTest {
    private static int checks;
    private static void check(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
    private record Player(UUID uuid, int actor) {}
    private record EqualReference(String value) {}
    private static Field field(String name) throws Exception {
        Field field = LegacySingleKeyOwnerEpoch.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    public static void main(String[] args) throws Exception {
        LegacySingleKeyOwnerEpoch.clear();
        var history = field("lastIssuedEpoch");
        var cache = field("owners");
        Object connection = new EqualReference("connection"), world = new EqualReference("world"), state = new EqualReference("state");
        var owner = new Player(UUID.fromString("11111111-2222-3333-4444-555555555555"), 42);
        var successor = new Player(owner.uuid(), owner.actor());
        check(owner.equals(successor) && owner != successor, "respawn fixture reuses UUID and actor while replacing actual player");
        long before = history.getLong(null);
        check(LegacySingleKeyOwnerEpoch.peek(owner, connection, world, state) == 0, "peek does not issue an epoch");
        check(!LegacySingleKeyOwnerEpoch.matches(owner, connection, world, state, before + 1), "matches cannot allocate guessed epoch");
        check(((Map<?, ?>) cache.get(null)).isEmpty() && history.getLong(null) == before, "unissued read paths do not mutate cache/history");
        long original = LegacySingleKeyOwnerEpoch.current(owner, connection, world, state);
        check(original > before && original > 0, "complete owner tuple receives unique positive epoch");
        check(LegacySingleKeyOwnerEpoch.current(owner, connection, world, state) == original, "stable tuple retains epoch");
        check(LegacySingleKeyOwnerEpoch.peek(owner, connection, world, state) == original, "exact peek observes existing epoch");
        check(LegacySingleKeyOwnerEpoch.matches(owner, connection, world, state, original), "exact tuple matches existing epoch");
        check(!LegacySingleKeyOwnerEpoch.matches(owner, connection, world, state, 0) && !LegacySingleKeyOwnerEpoch.matches(owner, connection, world, state, -1), "zero and negative epochs fail closed");
        long successorEpoch = LegacySingleKeyOwnerEpoch.current(successor, connection, world, state);
        check(successorEpoch > original, "new actual player gets a new epoch with same UUID/id/connection/world/state");
        check(!LegacySingleKeyOwnerEpoch.matches(successor, connection, world, state, original), "old owner epoch cannot match respawn successor");
        check(LegacySingleKeyOwnerEpoch.matches(owner, connection, world, state, original), "separate actual owners retain separately captured entries");
        LegacySingleKeyOwnerEpoch.forget(owner);
        check(LegacySingleKeyOwnerEpoch.peek(owner, connection, world, state) == 0, "forget removes exact old owner");
        check(LegacySingleKeyOwnerEpoch.matches(successor, connection, world, state, successorEpoch), "forget old equal-value owner does not remove successor");
        long afterForget = LegacySingleKeyOwnerEpoch.current(owner, connection, world, state);
        check(afterForget > successorEpoch && afterForget != original, "forget never reuses epoch");
        Object otherConnection = new EqualReference("connection"), otherWorld = new EqualReference("world"), otherState = new EqualReference("state");
        check(connection.equals(otherConnection) && connection != otherConnection && world.equals(otherWorld) && state.equals(otherState), "equal-value tuple fixture uses different actual references");
        int size = ((Map<?, ?>) cache.get(null)).size();
        long last = history.getLong(null);
        check(LegacySingleKeyOwnerEpoch.peek(owner, otherConnection, world, state) == 0, "mismatched peek returns zero");
        check(!LegacySingleKeyOwnerEpoch.matches(owner, otherConnection, world, state, afterForget), "mismatched connection read fails");
        check(!LegacySingleKeyOwnerEpoch.matches(owner, connection, otherWorld, state, afterForget), "mismatched world read fails");
        check(!LegacySingleKeyOwnerEpoch.matches(owner, connection, world, otherState, afterForget), "mismatched state read fails");
        check(((Map<?, ?>) cache.get(null)).size() == size && history.getLong(null) == last && LegacySingleKeyOwnerEpoch.peek(owner, connection, world, state) == afterForget, "failed peek/matches do not replace owner entry or issue epoch");
        long reconnected = LegacySingleKeyOwnerEpoch.current(owner, otherConnection, world, state);
        check(reconnected > afterForget && !LegacySingleKeyOwnerEpoch.matches(owner, connection, world, state, afterForget), "connection replacement invalidates prior owner epoch");
        long dimensionChanged = LegacySingleKeyOwnerEpoch.current(owner, otherConnection, otherWorld, state);
        check(dimensionChanged > reconnected && !LegacySingleKeyOwnerEpoch.matches(owner, otherConnection, world, state, reconnected), "world replacement invalidates prior owner epoch");
        long stateChanged = LegacySingleKeyOwnerEpoch.current(owner, otherConnection, otherWorld, otherState);
        check(stateChanged > dimensionChanged && !LegacySingleKeyOwnerEpoch.matches(owner, otherConnection, otherWorld, state, dimensionChanged), "ability-state replacement invalidates prior owner epoch");
        long restoredTuple = LegacySingleKeyOwnerEpoch.current(owner, connection, world, state);
        check(restoredTuple > stateChanged && restoredTuple != original && restoredTuple != afterForget, "returning to old references cannot revive an old epoch");
        Object[] complete = {owner, connection, world, state};
        for (int missing = 0; missing < 4; ++missing) {
            Object[] tuple = complete.clone(); tuple[missing] = null;
            last = history.getLong(null); size = ((Map<?, ?>) cache.get(null)).size();
            check(LegacySingleKeyOwnerEpoch.current(tuple[0], tuple[1], tuple[2], tuple[3]) == 0, "incomplete tuple cannot issue");
            check(LegacySingleKeyOwnerEpoch.peek(tuple[0], tuple[1], tuple[2], tuple[3]) == 0, "incomplete tuple cannot peek");
            check(!LegacySingleKeyOwnerEpoch.matches(tuple[0], tuple[1], tuple[2], tuple[3], restoredTuple), "incomplete tuple cannot match");
            check(history.getLong(null) == last && ((Map<?, ?>) cache.get(null)).size() == size && LegacySingleKeyOwnerEpoch.peek(owner, connection, world, state) == restoredTuple, "invalid references do not mutate complete owner");
        }
        LegacySingleKeyOwnerEpoch.forget(null);
        check(LegacySingleKeyOwnerEpoch.peek(owner, connection, world, state) == restoredTuple, "forget null does not mutate actual owners");
        LegacySingleKeyOwnerEpoch.clear();
        check(!LegacySingleKeyOwnerEpoch.matches(owner, connection, world, state, restoredTuple) && LegacySingleKeyOwnerEpoch.peek(successor, connection, world, state) == 0, "clear forgets cached owners");
        long afterClear = LegacySingleKeyOwnerEpoch.current(successor, connection, world, state);
        check(afterClear > restoredTuple && !LegacySingleKeyOwnerEpoch.matches(successor, connection, world, state, successorEpoch), "clear never resets epoch issuance or revives old epoch");
        // Pending can arrive before the server start in a deliberately reordered modern host.
        // Storage stays bounded by the fixed family set rather than one tombstone per input.
        Object cancelOwner = new Object(), otherOwner = new Object();
        long cancelEpoch = LegacySingleKeyOwnerEpoch.current(cancelOwner, connection, world, state);
        long otherEpoch = LegacySingleKeyOwnerEpoch.current(otherOwner, connection, world, state);
        last = history.getLong(null); size = ((Map<?, ?>) cache.get(null)).size();
        for (LegacySingleKeyProtocol.Skill skill : LegacySingleKeyProtocol.Skill.values()) {
            check(!LegacySingleKeyOwnerEpoch.pendingRetired(cancelOwner, connection, world, state, cancelEpoch, skill, 1), "unretired family input remains usable");
            check(LegacySingleKeyOwnerEpoch.retirePending(cancelOwner, connection, world, state, cancelEpoch, skill, 1), "pending before start retires exact issued family input");
            check(LegacySingleKeyOwnerEpoch.pendingRetired(cancelOwner, connection, world, state, cancelEpoch, skill, 1), "equal late start remains retired");
            check(!LegacySingleKeyOwnerEpoch.pendingRetired(cancelOwner, connection, world, state, cancelEpoch, skill, 2), "input+1 remains usable");
            check(!LegacySingleKeyOwnerEpoch.pendingRetired(otherOwner, connection, world, state, otherEpoch, skill, 1), "other actual owner is not poisoned");
            check(!LegacySingleKeyOwnerEpoch.retirePending(otherOwner, connection, world, state, cancelEpoch, skill, Long.MAX_VALUE), "old owner epoch cannot poison new owner with large input");
            check(!LegacySingleKeyOwnerEpoch.retirePending(cancelOwner, otherConnection, world, state, cancelEpoch, skill, Long.MAX_VALUE), "wrong connection cannot mutate retirement");
            check(!LegacySingleKeyOwnerEpoch.retirePending(cancelOwner, connection, otherWorld, state, cancelEpoch, skill, Long.MAX_VALUE), "wrong world cannot mutate retirement");
            check(!LegacySingleKeyOwnerEpoch.retirePending(cancelOwner, connection, world, otherState, cancelEpoch, skill, Long.MAX_VALUE), "wrong state cannot mutate retirement");
            check(!LegacySingleKeyOwnerEpoch.retirePending(cancelOwner, connection, world, state, 0, skill, Long.MAX_VALUE), "zero epoch cannot mutate retirement");
            check(!LegacySingleKeyOwnerEpoch.retirePending(cancelOwner, connection, world, state, cancelEpoch, skill, 0), "zero input cannot mutate retirement");
            check(!LegacySingleKeyOwnerEpoch.pendingRetired(cancelOwner, connection, world, state, cancelEpoch, skill, 2), "all rejected retirements preserve input+1");
        }
        check(!LegacySingleKeyOwnerEpoch.retirePending(new Object(), connection, world, state, cancelEpoch, LegacySingleKeyProtocol.Skill.CHARGING, Long.MAX_VALUE), "unissued owner cannot allocate through retirement");
        check(!LegacySingleKeyOwnerEpoch.retirePending(cancelOwner, connection, world, state, cancelEpoch, null, 2), "unknown family cannot allocate retirement");
        check(history.getLong(null) == last && ((Map<?, ?>) cache.get(null)).size() == size, "pending reads/rejections never issue or replace owner identity");
        check(LegacySingleKeyOwnerEpoch.retirePending(cancelOwner, connection, world, state, cancelEpoch, LegacySingleKeyProtocol.Skill.CHARGING, 100), "one family accepts higher watermark");
        check(LegacySingleKeyOwnerEpoch.retirePending(cancelOwner, connection, world, state, cancelEpoch, LegacySingleKeyProtocol.Skill.CHARGING, 3), "lower duplicate retirement remains harmless");
        check(LegacySingleKeyOwnerEpoch.pendingRetired(cancelOwner, connection, world, state, cancelEpoch, LegacySingleKeyProtocol.Skill.CHARGING, 99), "lower late input cannot revive family");
        check(!LegacySingleKeyOwnerEpoch.pendingRetired(cancelOwner, connection, world, state, cancelEpoch, LegacySingleKeyProtocol.Skill.CHARGING, 101), "higher watermark preserves input+1");
        check(!LegacySingleKeyOwnerEpoch.pendingRetired(cancelOwner, connection, world, state, cancelEpoch, LegacySingleKeyProtocol.Skill.MAG_MOVEMENT, 2), "global client counter does not imply cross-family cancellation");
        check(LegacySingleKeyOwnerEpoch.retirePending(cancelOwner, connection, world, state, cancelEpoch, LegacySingleKeyProtocol.Skill.CHARGING, Long.MAX_VALUE), "maximum input retirement does not overflow");
        check(!LegacySingleKeyOwnerEpoch.pendingRetired(otherOwner, connection, world, state, otherEpoch, LegacySingleKeyProtocol.Skill.CHARGING, 1), "maximum input cannot affect another owner");
        Object entry = ((Map<?, ?>) cache.get(null)).get(cancelOwner);
        var watermarks = entry.getClass().getDeclaredMethod("retiredPending"); watermarks.setAccessible(true);
        check(((long[]) watermarks.invoke(entry)).length == 6, "exactly six watermark slots bound per-owner storage");
        long changedCancelEpoch = LegacySingleKeyOwnerEpoch.current(cancelOwner, connection, otherWorld, state);
        check(changedCancelEpoch > cancelEpoch && !LegacySingleKeyOwnerEpoch.pendingRetired(cancelOwner, connection, otherWorld, state, changedCancelEpoch, LegacySingleKeyProtocol.Skill.CHARGING, 1), "new issued incarnation has fresh retirement watermarks");
        check(!LegacySingleKeyOwnerEpoch.retirePending(cancelOwner, connection, otherWorld, state, cancelEpoch, LegacySingleKeyProtocol.Skill.CHARGING, Long.MAX_VALUE), "old epoch cannot poison replacement incarnation");
        int count = 16;
        long[] issued = new long[count];
        Object[] concurrentOwners = new Object[count];
        Thread[] threads = new Thread[count];
        CountDownLatch ready = new CountDownLatch(count), start = new CountDownLatch(1);
        for (int i = 0; i < count; ++i) {
            final int index = i; concurrentOwners[index] = new Object();
            threads[index] = new Thread(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                issued[index] = LegacySingleKeyOwnerEpoch.current(concurrentOwners[index], connection, world, state);
            });
            threads[index].start();
        }
        ready.await(); start.countDown();
        for (Thread thread : threads) thread.join();
        var unique = new HashSet<Long>();
        for (int i = 0; i < count; ++i) {
            check(issued[i] > afterClear && unique.add(issued[i]), "concurrent new owners receive distinct positive epochs");
            check(LegacySingleKeyOwnerEpoch.matches(concurrentOwners[i], connection, world, state, issued[i]), "concurrent issuance cached exact owner");
        }
        Object sharedOwner = new Object();
        for (int i = 0; i < count; ++i) {
            final int index = i;
            threads[index] = new Thread(() -> issued[index] = LegacySingleKeyOwnerEpoch.current(sharedOwner, connection, world, state));
            threads[index].start();
        }
        for (Thread thread : threads) thread.join();
        for (long epoch : issued) check(epoch == issued[0] && epoch > 0, "concurrent exact tuple issues once");
        // Test-only exhaustion injection in this standalone JVM; production has no counter-reset API.
        history.setLong(null, Long.MAX_VALUE - 1);
        Object finalOwner = new Object();
        long finalEpoch = LegacySingleKeyOwnerEpoch.current(finalOwner, connection, world, state);
        check(finalEpoch == Long.MAX_VALUE, "last representable positive epoch can be issued");
        check(LegacySingleKeyOwnerEpoch.current(finalOwner, connection, world, state) == Long.MAX_VALUE, "exhaustion retains exact issued tuple");
        size = ((Map<?, ?>) cache.get(null)).size();
        check(LegacySingleKeyOwnerEpoch.current(new Object(), connection, world, state) == 0, "new owner after exhaustion fails closed");
        check(LegacySingleKeyOwnerEpoch.current(finalOwner, otherConnection, world, state) == 0, "changed tuple after exhaustion fails closed");
        check(history.getLong(null) == Long.MAX_VALUE && ((Map<?, ?>) cache.get(null)).size() == size && LegacySingleKeyOwnerEpoch.matches(finalOwner, connection, world, state, finalEpoch), "overflow neither wraps nor mutates an old valid entry");
        LegacySingleKeyOwnerEpoch.forget(finalOwner);
        check(LegacySingleKeyOwnerEpoch.current(finalOwner, connection, world, state) == 0, "forget cannot recover capacity by reusing final epoch");
        LegacySingleKeyOwnerEpoch.clear();
        check(LegacySingleKeyOwnerEpoch.current(new Object(), connection, world, state) == 0 && history.getLong(null) == Long.MAX_VALUE && ((Map<?, ?>) cache.get(null)).isEmpty(), "clear retains exhausted issuance history and fails closed");
        System.out.println("PASS LegacySingleKeyOwnerEpoch checks=" + checks + "; JDK-only reference tuple/respawn/peek/forget/clear/concurrency/overflow boundary");
    }
}
