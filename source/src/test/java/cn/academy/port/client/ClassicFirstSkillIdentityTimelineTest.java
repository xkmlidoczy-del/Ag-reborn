package cn.academy.port.client;

import java.util.UUID;

/** Executes the live receiver's pure packet-order helper; no Minecraft instance or render bootstrap. */
public final class ClassicFirstSkillIdentityTimelineTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        UUID caster = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID replacement = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var history = new ClassicFirstSkillEffects.SingleKeyTimeline();
        check(history.acceptStart(caster, 1, 10, 0), "press1 accepted once");
        check(!history.acceptStart(caster, 1, 10, 0), "duplicate prepare cannot restart its age");
        check(!history.acceptTerminal(caster, 2, 10, 7), "same token with wrong physical input cannot end press1");
        check(!history.acceptTerminal(caster, 1, 10, -1), "negative terminal tick rejected");
        check(history.acceptStart(caster, 2, 11, 0), "press2 can be acknowledged ahead of end1");
        check(history.acceptTerminal(caster, 1, 10, 7), "valid old result remains independently accepted");
        check(!history.acceptStart(caster, 1, 10, 0), "old prepare cannot revive after its result");
        check(!history.acceptTerminal(caster, 1, 10, 7), "duplicate old sound/result suppressed");
        check(!history.acceptStart(caster, 3, 10, 0), "input substitution cannot replay an old server token");
        check(history.acceptTerminal(caster, 2, 11, 0), "same-tick terminal is legal");
        check(!history.acceptStart(caster, 2, 11, 0), "perform before delayed prepare is terminal");
        check(history.acceptTerminal(caster, 3, 12, 0), "abort before any accepted prepare creates a tombstone");
        check(!history.acceptStart(caster, 3, 12, 0), "late aborted start refused");
        check(!history.acceptTerminal(caster, 3, 12, 1), "abort duplicate suppressed");
        check(history.acceptStart(caster, 4, 13, 4), "new identity after terminal accepted");
        check(!history.acceptTerminal(caster, 4, 13, 3), "terminal cannot regress captured tick");
        check(history.acceptTerminal(caster, 4, 13, 4), "equal terminal tick accepted");
        check(history.acceptStart(replacement, 1, 10, 0), "UUID histories are independent despite reused entity IDs");
        check(history.acceptTerminal(replacement, 1, 10, 7), "replacement owner ends only its own identity");
        check(history.acceptStart(caster, 0, 14, 0), "trusted input0 stays representable for conservative binding");
        check(history.acceptTerminal(caster, 0, 14, 1), "trusted input0 terminal is still token exact");
        check(!history.acceptStart(caster, -1, 15, 0), "negative input refused");
        check(!history.acceptStart(caster, 1, 0, 0), "nonpositive token refused");
        check(!history.acceptTerminal(caster, 1, -1, 0), "negative token refused");
        check(!history.acceptStart(null, 1, 15, 0), "missing UUID refused");
        check(!history.acceptTerminal(null, 1, 15, 0), "missing terminal UUID refused");
        check(history.acceptTerminal(caster, 1, 15, 7), "observer first sees a valid independent result");
        history.clear();
        check(history.historySize() == 0, "session reset clears history");
        check(history.acceptStart(caster, 1, 10, 0), "new session may reuse a remote history envelope");
        history.clear();
        for (int token = 1; token <= 1100; token++) {
            check(history.acceptStart(caster, token, token, 0), "monotonically increasing start " + token);
            check(history.acceptTerminal(caster, token, token, 7), "monotonically increasing terminal " + token);
        }
        check(history.historySize() == 1024, "history stays bounded");
        check(!history.acceptTerminal(caster, 1, 1, 7), "retired token cannot replay once its detailed tombstone is evicted");
        check(!history.acceptStart(caster, 1, 1, 0), "retired start cannot replay");
        check(history.acceptTerminal(caster, 9999, 1101, 0), "terminal-only pending envelope still accepted after history bound");
        check(!history.acceptStart(caster, 9999, 1101, 0), "terminal-only pending envelope cannot start late");
        System.out.println("ClassicFirstSkillIdentityTimelineTest: " + assertions + " assertions");
    }
}
