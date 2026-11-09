package cn.academy.port.client;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.highpower.HighPowerInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Real input retirement and bounded cosmetic ARMED -> DIVERT -> END ownership. No game launch. */
public final class NewSkillWindowVisualRegressionTest {
    private static int checks;
    private static void check(boolean pass, String why) { checks++; if (!pass) throw new AssertionError(why); }
    private static NewSkillWindowVisualLease.Identity id(long token, long input, long epoch) {
        return new NewSkillWindowVisualLease.Identity(6, new UUID(1, 2), token, input, epoch);
    }
    public static void main(String[] args) throws Exception {
        var state = new BrakingTouchInputState();
        var context = new BrakingTouchInputState.DispatchContext(new Object(), new Object(), new Object(), 5, 9, 41, 0, 1, "split");
        var gates = new BrakingTouchInputState.Gates(context, 0, 82, true, true, true, true, true, true, true, true, true, true, true, true);
        var first = new HighPowerInput.Input(0, 1, 9, 41);
        check(state.press(first, gates), "first real direction input");
        state.flushStartPost(gates, false, ignored -> {}, ignored -> {});
        state.accepted(1, 41, BrakingTouchState.BRAKED);
        state.release(first); state.flushReleasePost(gates, ignored -> {}, ignored -> {});
        var second = new HighPowerInput.Input(0, 2, 9, 41);
        check(state.press(second, gates), "second direction replaces its completed first tap");
        state.flushStartPost(gates, false, ignored -> {}, ignored -> {});
        state.accepted(2, 41, BrakingTouchState.BRAKED);
        state.release(second); state.flushReleasePost(gates, ignored -> {}, ignored -> {});
        check(state.permits(2, 41) && !state.permits(1, 1), "hardcoded legacy input would reject owner DIVERT");
        var lease = new NewSkillWindowVisualLease(); var key = id(73, 2, 41);
        lease.arm(key, 1, 0, 120);
        for (int i = 0; i < BrakingTouchInputState.ACK_TIMEOUT_TICKS; i++) state.tick(gates, false, ignored -> {});
        check(state.current() == null, "ordinary input timeout remains 60 ticks");
        check(!lease.accept(id(74, 2, 41), 2, 70, true, false), "wrong token refused");
        check(!lease.accept(id(73, 1, 41), 2, 70, true, false), "wrong input refused");
        check(!lease.accept(id(73, 2, 42), 2, 70, true, false), "wrong epoch refused");
        check(!lease.accept(new NewSkillWindowVisualLease.Identity(6, new UUID(9, 9), 73, 2, 41), 2, 70, true, false), "wrong owner refused");
        check(!lease.accept(key, 1, 70, true, false), "replayed ARMED sequence refused");
        check(!lease.accept(key, 2, 70, false, false), "lease cannot admit a start/gameplay phase");
        check(lease.accept(key, 2, 70, true, false), "real later-window DIVERT remains visible after input timeout");
        check(!lease.accept(key, 2, 70, true, false), "repeated DIVERT refused");
        check(lease.accept(key, 3, 70, false, true), "ordered END closes cosmetic receipt");
        check(!lease.accept(key, 4, 71, true, false), "closed receipt cannot admit another diversion");
        lease.arm(key, 1, 100, 120);
        check(!lease.accept(key, 2, 220, true, false), "cosmetic lease expires independently");
        lease.arm(key, 1, 300, 120); lease.clear();
        check(!lease.accept(key, 2, 301, true, false), "world cleanup drops receipts");
        lease.arm(key, 1, 300, 121);
        check(!lease.accept(key, 2, 301, true, false), "unbounded lease refused");
        lease.arm(key, 1, 300, 120); lease.arm(id(80, 3, 41), 1, 301, 120);
        check(!lease.accept(key, 2, 302, true, false), "new arm replaces the old receipt for that skill");

        Path root = Path.of(args.length == 0 ? "." : args[0]);
        String runtime = Files.readString(root.resolve("src/main/java/cn/academy/port/highpower/NewSkillRuntime.java"));
        String effects = Files.readString(root.resolve("src/main/java/cn/academy/port/client/NewSkillEffects.java"));
        check(runtime.contains("kind = context.kind; input = context.input; token = context.identity.token();"), "armed metadata captured from actual context");
        check(runtime.contains("armed.input.input(), armed.input.epoch()") && !runtime.contains("1_000_000 + now(p)"), "real identity on wire");
        check(runtime.contains("++armed.sequence"), "ordered window sequence");
        String consume = runtime.substring(runtime.indexOf("private static void tickWindow"), runtime.indexOf("private static boolean bypassProjectile"));
        check(consume.contains("finally {") && consume.contains("sendWindow(p, armed, END"), "consumed candidate always closes visual input");
        check(consume.contains("if (WINDOWS.remove(p.getUUID(), armed)) sendWindow(p, armed, END"), "expiry closes exactly the removed window");
        check(runtime.contains("SPLIT_WINDOW = SkillTuning.integer(\"impulse_split\", \"duration_ticks\")"), "mechanical window reads the configured duration");
        String end = effects.substring(effects.indexOf("if (v.phase() == NewSkillRuntime.END)"), effects.indexOf("if (local) AcademyClient.newSkillAcceptedInput"));
        check(end.contains("newSkillEndedInput") && !end.contains("LIVE") && !end.contains("clear("), "END leaves one-shot DIVERT lifetime intact");
        check(effects.contains("l -> ++l.age >= NewSkillFx.LIFE"), "DIVERT uses normal short effect lifetime");
        check(effects.contains("world != mc.level || player != mc.player || connection != mc.getConnection()"), "visual owner identity changes clear even within one ClientLevel");
        check(effects.contains("if (local && (!mc.player.isAlive() || mc.player.isSpectator())) return;"), "dead or spectator owner cannot rebuild its stale window");
        check(effects.contains("LIVE.removeIf(l -> l.visual.owner().equals(mc.player.getUUID()))"), "death cleanup removes only local effects, retaining remote spectators' views");
        check(effects.contains("if (!sync(mc)) return;") && effects.contains("if (!sync(mc) || !mc.level.dimension()"), "both receive and frame paths fence owner replacement");
        System.out.println("PASS " + checks + " armed-window visual identity, late receipt, END and natural lifetime contracts");
    }
}
