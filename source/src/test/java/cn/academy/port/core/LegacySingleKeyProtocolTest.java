package cn.academy.port.core;

public final class LegacySingleKeyProtocolTest {
    private static int checks;
    private static void check(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
    private static void invalid(Runnable construction) {
        try { construction.run(); throw new AssertionError("invalid DTO accepted"); }
        catch (IllegalArgumentException expected) { ++checks; }
    }
    public static void main(String[] args) {
        long[] positives = {1, 2, 35, 36, 918273645, Long.MAX_VALUE};
        long[] epochs = {1, 36, Long.MAX_VALUE};
        long[] tokens = {1, 36, Long.MAX_VALUE};
        int[] actors = {0, 123, Integer.MAX_VALUE};
        String[] aliases = {"c", "mm", "ma", "gs", "ds", "tt"};
        var skills = LegacySingleKeyProtocol.Skill.values();
        for (int index = 0; index < skills.length; ++index) {
            var skill = skills[index];
            check(skill.alias().equals(aliases[index]), "fixed compact alias mapping");
            check(LegacySingleKeyProtocol.Skill.find(skill.id()) == skill, "full mapping/callback ID retained");
            check(LegacySingleKeyProtocol.Skill.find(skill.alias()) == null, "wire alias does not broaden mapping IDs");
            for (long input : positives) for (long epoch : epochs) for (int actor : actors) {
                var pending = new LegacySingleKeyProtocol.PendingRequest(skill, input, actor, epoch);
                check(pending.equals(LegacySingleKeyProtocol.parsePending(pending.wire())), "pending exact roundtrip");
                check(pending.wire().length() <= 64, "pending fits existing codec");
                check(LegacySingleKeyProtocol.parseStart(pending.wire()) == null, "pending cannot start");
                check(LegacySingleKeyProtocol.parseAccepted(pending.wire()) == null, "pending cannot claim an accepted token");
                for (int slot = 0; slot < 4; ++slot) {
                    var start = new LegacySingleKeyProtocol.StartRequest(slot, skill, input, actor, epoch);
                    check(start.equals(LegacySingleKeyProtocol.parseStart(start.wire())), "mapped start exact roundtrip");
                    check(start.wire().length() <= 64, "start fits existing codec");
                    check(LegacySingleKeyProtocol.parsePending(start.wire()) == null, "start cannot cancel pending");
                    check(LegacySingleKeyProtocol.parseAccepted(start.wire()) == null, "start cannot terminate accepted");
                }
                for (long token : tokens) {
                    var accepted = new LegacySingleKeyProtocol.AcceptedRequest(skill, input, actor, epoch, token);
                    check(accepted.equals(LegacySingleKeyProtocol.parseAccepted(accepted.wire())), "accepted exact roundtrip");
                    check(accepted.wire().length() <= 64, "accepted fits existing codec including all maxima");
                    check(LegacySingleKeyProtocol.parsePending(accepted.wire()) == null, "accepted cannot masquerade as pending");
                    check(LegacySingleKeyProtocol.parseStart(accepted.wire()) == null, "accepted cannot masquerade as start");
                }
            }
            String a = skill.alias();
            for (String number : new String[]{"", "0", "00", "01", "0a", "A", "Z", "1Y2P0IJ32E8E7", "-1", "+1", " 1", "1 ", "1\n", "1.0", "١", "１", "1_0", "0x1", "9223372036854775807", "1y2p0ij32e8e8", "zzzzzzzzzzzzz", "10000000000000"}) {
                check(LegacySingleKeyProtocol.parseStart("0:" + a + ":" + number + ":0:1") == null, "invalid start input");
                check(LegacySingleKeyProtocol.parseStart("0:" + a + ":1:0:" + number) == null, "invalid start epoch");
                check(LegacySingleKeyProtocol.parsePending(a + ":" + number + ":0:1") == null, "invalid pending input");
                check(LegacySingleKeyProtocol.parsePending(a + ":1:0:" + number) == null, "invalid pending epoch");
                check(LegacySingleKeyProtocol.parseAccepted(a + ":" + number + ":0:1:1") == null, "invalid accepted input");
                check(LegacySingleKeyProtocol.parseAccepted(a + ":1:0:" + number + ":1") == null, "invalid accepted epoch");
                check(LegacySingleKeyProtocol.parseAccepted(a + ":1:0:1:" + number) == null, "invalid accepted token");
            }
            for (String actor : new String[]{"", "00", "01", "-1", "+1", " 1", "1 ", "1\n", "1.0", "2147483648", "99999999999", "١", "a", "A", "0x1"}) {
                check(LegacySingleKeyProtocol.parseStart("0:" + a + ":1:" + actor + ":1") == null, "invalid start actor");
                check(LegacySingleKeyProtocol.parsePending(a + ":1:" + actor + ":1") == null, "invalid pending actor");
                check(LegacySingleKeyProtocol.parseAccepted(a + ":1:" + actor + ":1:1") == null, "invalid accepted actor");
            }
            for (String slot : new String[]{"", "00", "01", "-1", "+1", "4", "99", " 0", "0 ", "a", "١"})
                check(LegacySingleKeyProtocol.parseStart(slot + ":" + a + ":1:0:1") == null, "invalid mapped slot");
            for (String spoof : new String[]{skill.id(), a.toUpperCase(), "unknown", "flashing", "vec_deviation", "", " " + a}) {
                check(LegacySingleKeyProtocol.parseStart("0:" + spoof + ":1:0:1") == null, "unmapped start alias");
                check(LegacySingleKeyProtocol.parsePending(spoof + ":1:0:1") == null, "unmapped pending alias");
                check(LegacySingleKeyProtocol.parseAccepted(spoof + ":1:0:1:1") == null, "unmapped accepted alias");
            }
            check(LegacySingleKeyProtocol.parseStart("0:" + a + ":1:0") == null, "actor-only old start rejected");
            check(LegacySingleKeyProtocol.parseStart("0:" + a + ":1:0:1:") == null, "start trailing field rejected");
            check(LegacySingleKeyProtocol.parsePending(a + ":1:0:1:") == null, "pending trailing field rejected");
            check(LegacySingleKeyProtocol.parseAccepted(a + ":1:0:1:1:") == null, "accepted trailing field rejected");
            for (String bare : new String[]{skill.id() + ":1", a + ":1"}) {
                check(LegacySingleKeyProtocol.parseStart(bare) == null, "bare skill input cannot start");
                check(LegacySingleKeyProtocol.parsePending(bare) == null, "bare skill input cannot cancel pending");
                check(LegacySingleKeyProtocol.parseAccepted(bare) == null, "bare skill input cannot terminate accepted");
            }
        }
        for (String value : new String[]{null, "", ":", ":1", "c:1:0:1:" + "1".repeat(65), "c:1:0:1:1:2"}) {
            check(LegacySingleKeyProtocol.parseStart(value) == null, "bounded start rejects malformed value");
            check(LegacySingleKeyProtocol.parsePending(value) == null, "bounded pending rejects malformed value");
            check(LegacySingleKeyProtocol.parseAccepted(value) == null, "bounded accepted rejects malformed value");
        }
        var skill = LegacySingleKeyProtocol.Skill.CHARGING;
        invalid(() -> new LegacySingleKeyProtocol.StartRequest(-1, skill, 1, 0, 1));
        invalid(() -> new LegacySingleKeyProtocol.StartRequest(4, skill, 1, 0, 1));
        invalid(() -> new LegacySingleKeyProtocol.StartRequest(0, null, 1, 0, 1));
        invalid(() -> new LegacySingleKeyProtocol.PendingRequest(null, 1, 0, 1));
        invalid(() -> new LegacySingleKeyProtocol.AcceptedRequest(null, 1, 0, 1, 1));
        for (long value : new long[]{0, -1, Long.MIN_VALUE}) {
            invalid(() -> new LegacySingleKeyProtocol.StartRequest(0, skill, value, 0, 1));
            invalid(() -> new LegacySingleKeyProtocol.StartRequest(0, skill, 1, 0, value));
            invalid(() -> new LegacySingleKeyProtocol.PendingRequest(skill, value, 0, 1));
            invalid(() -> new LegacySingleKeyProtocol.PendingRequest(skill, 1, 0, value));
            invalid(() -> new LegacySingleKeyProtocol.AcceptedRequest(skill, value, 0, 1, 1));
            invalid(() -> new LegacySingleKeyProtocol.AcceptedRequest(skill, 1, 0, value, 1));
            invalid(() -> new LegacySingleKeyProtocol.AcceptedRequest(skill, 1, 0, 1, value));
        }
        invalid(() -> new LegacySingleKeyProtocol.StartRequest(0, skill, 1, -1, 1));
        invalid(() -> new LegacySingleKeyProtocol.PendingRequest(skill, 1, -1, 1));
        invalid(() -> new LegacySingleKeyProtocol.AcceptedRequest(skill, 1, -1, 1, 1));
        var maximum = new LegacySingleKeyProtocol.AcceptedRequest(LegacySingleKeyProtocol.Skill.THREATENING_TELEPORT, Long.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        check(maximum.wire().equals("tt:1y2p0ij32e8e7:2147483647:1y2p0ij32e8e7:1y2p0ij32e8e7"), "canonical maximum base36 wire");
        check(maximum.wire().length() == 55, "maximum accepted wire occupies 55 of 64 characters");
        check(LegacySingleKeyProtocol.PRESS.equals("single_key_press") && LegacySingleKeyProtocol.RELEASE.equals("single_key_release") && LegacySingleKeyProtocol.ABORT.equals("single_key_abort"), "accepted action names retained");
        check(LegacySingleKeyProtocol.PENDING_RELEASE.equals("single_key_pending_release") && LegacySingleKeyProtocol.PENDING_ABORT.equals("single_key_pending_abort"), "pending phase has distinct actions");
        var ledger = new LegacySingleKeyProtocol.NonceLedger();
        Object owner = new Object(), otherOwner = new Object(), connection = new Object(), otherConnection = new Object();
        check(!ledger.claim(null, connection, 1) && !ledger.claim(owner, null, 1) && !ledger.claim(owner, connection, 0), "invalid nonce claims have no history");
        check(ledger.highest(owner, connection) == 0, "invalid nonce claim no mutation");
        check(ledger.claim(owner, connection, 1), "first nonce");
        check(!ledger.claim(owner, connection, 1) && !ledger.claim(owner, connection, 0), "same nonce replay rejected");
        check(ledger.claim(owner, connection, 3) && !ledger.claim(owner, connection, 2), "older reordered start cannot reopen context");
        check(ledger.highest(owner, connection) == 3, "normal terminal retains nonce history");
        check(ledger.claim(otherOwner, connection, 1), "actual player identity scopes nonce ledger");
        check(ledger.claim(owner, otherConnection, 1) && ledger.highest(owner, connection) == 0, "connection replacement scopes nonce ledger");
        check(ledger.claim(owner, otherConnection, Long.MAX_VALUE) && !ledger.claim(owner, otherConnection, Long.MAX_VALUE), "maximum nonce admitted once");
        ledger.forget(owner);
        check(ledger.highest(owner, otherConnection) == 0 && ledger.highest(otherOwner, connection) == 1, "final removal is single-owner");
        ledger.clear();
        check(ledger.highest(otherOwner, connection) == 0, "server stop clears nonce ledger");
        record EqualOwner(int actor) {}
        var oldOwner = new EqualOwner(42); var successor = new EqualOwner(42);
        check(oldOwner.equals(successor) && oldOwner != successor, "nonce equal-value owner fixture");
        check(ledger.claim(oldOwner, connection, 10) && ledger.claim(successor, connection, 1), "nonce history uses reference identity");
        ledger.forget(oldOwner);
        check(ledger.highest(successor, connection) == 1, "forget old equal-value owner preserves successor nonce");
        System.out.println("PASS LegacySingleKeyProtocol checks=" + checks + "; JDK-only phase/alias/canonical-bound/nonce boundary; maximumWire=" + maximum.wire().length());
    }
}
