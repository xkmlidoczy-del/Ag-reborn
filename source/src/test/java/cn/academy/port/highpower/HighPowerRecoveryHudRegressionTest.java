package cn.academy.port.highpower;

import cn.academy.port.highpower.client.RecoveryHudMixinPlugin;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Headless production-model tests; runtime screenshot/absorption checks remain a separate acceptance stage. */
public final class HighPowerRecoveryHudRegressionTest {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void denied(Runnable action, String message) { boolean failed = false; try { action.run(); } catch (IllegalArgumentException | NullPointerException expected) { failed = true; } check(failed, message); }
    private static HighPowerRecoveryHudModel.Values value(String hp, String max, String status, String rate, int interval, int next, int pause) {
        return new HighPowerRecoveryHudModel.Values(hp, max, "5", "10", status, rate, interval, next, pause, false);
    }
    private static HighPowerRecoveryHudModel.Identity id(UUID owner, int entity, String dimension, long epoch, long sequence) {
        return new HighPowerRecoveryHudModel.Identity(owner, entity, dimension, epoch, sequence);
    }
    public static void main(String[] args) throws Exception {
        checks += ZeroGapRecoveryHudRegressionTest.runChecks();
        checks += CompactRecoveryHudRegressionTest.runChecks();
        verifyZeroGapCodec();
        var seen = new java.util.HashSet<Integer>();
        for (String category : new String[] {"electromaster", "meltdowner", "teleporter", "vecmanip"}) {
            var accent = HighPowerRecoveryHudModel.accent(category);
            check(seen.add(accent.primary()) && seen.add(accent.pulse()), category + " has its own accent, shared with no other category");
            for (int colour : new int[] {accent.primary(), accent.idle(), accent.edge(), accent.pulse()}) check((colour >>> 24) == 0xff, category + " accent is opaque");
            int luminance = ((accent.primary() >> 16 & 255) * 299 + (accent.primary() >> 8 & 255) * 587 + (accent.primary() & 255) * 114) / 1000;
            check(luminance >= 150, category + " primary stays readable on the dark panel");
        }
        boolean unknown = false; try { HighPowerRecoveryHudModel.accent("classic"); } catch (IllegalStateException expected) { unknown = true; }
        check(unknown, "an unknown category has no accent");
        // Growth-style hint line: pure presentation of the server's style state, one distinct line per category.
        var lines = new java.util.HashSet<String>();
        for (String category : new String[] {"electromaster", "meltdowner", "teleporter", "vecmanip"}) {
            var idle = new HighPowerRecoveryHudModel.GrowthHint(category, 0, 0);
            var live = new HighPowerRecoveryHudModel.GrowthHint(category, 30, 3);
            check(!idle.engaged(), category + " idle hint is not engaged");
            check(live.engaged() && !idle.key().equals(live.key()), category + " has separate idle and engaged lines");
            check(lines.add(idle.text()) && lines.add(live.text()), category + " hint text is its own: " + live.text());
            check(live.text().contains("30%") && !live.text().contains("%s"), category + " engaged line shows the bonus without leftover placeholders: " + live.text());
            check(live.level() > idle.level() && live.level() <= 1 && idle.level() == 0, category + " meter rises with the style");
            check(live.arguments().size() == live.fallback().split("%s", -1).length - 1, category + " argument count matches placeholders");
        }
        check(new HighPowerRecoveryHudModel.GrowthHint("electromaster", 30, 3).text().equals("Resonance x3: next cast +30%"), "EM line reads as resonance stacks and bonus");
        check(new HighPowerRecoveryHudModel.GrowthHint("teleporter", 50, 2).text().equals("Variety 2 in 30 s: new skill +50%"), "TP line reads as skills in the window");
        check(!new HighPowerRecoveryHudModel.GrowthHint("teleporter", 50, 0).engaged() && new HighPowerRecoveryHudModel.GrowthHint("teleporter", 50, 0).text().endsWith("+50%"), "TP idle still advertises the flat +50% for a new skill");
        check(new HighPowerRecoveryHudModel.GrowthHint("teleporter", 50, 4).level() == 1, "TP meter fills at four skills");
        for (int[] bad : new int[][] {{-1, 0}, {61, 0}, {0, -1}, {0, 257}}) {
            boolean rejected = false; try { new HighPowerRecoveryHudModel.GrowthHint("meltdowner", bad[0], bad[1]); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "out-of-range growth hint is rejected: " + bad[0] + "/" + bad[1]);
        }
        boolean foreign = false; try { new HighPowerRecoveryHudModel.GrowthHint("classic", 0, 0); } catch (IllegalArgumentException expected) { foreign = true; }
        check(foreign, "an unknown category cannot carry a growth hint");
        // The growth line may enter the native plan only as its own accent-tinted role, and fits like any other line.
        var hintPlan = HighPowerRecoveryHudModel.nativePlan(java.util.List.of(new HighPowerRecoveryHudModel.NativeSection("ACA", null, 3),
                new HighPowerRecoveryHudModel.NativeSection(new HighPowerRecoveryHudModel.GrowthHint("vecmanip", 25, 0).text(), null, 6)), 320, 9,
                (text, width) -> java.util.List.of(text), String::length);
        check(hintPlan != null && hintPlan.text().get(1).ink() == 2 && hintPlan.meters().isEmpty(), "growth role is tinted text without a meter");
        check(HighPowerRecoveryHudModel.nativePlan(java.util.List.of(new HighPowerRecoveryHudModel.NativeSection("x", null, 7)), 320, 9,
                (text, width) -> java.util.List.of(text), String::length) == null, "roles beyond the growth line stay rejected");
        verifyCompactHudAndSummary();
        var curtain = new HighPowerRecoveryHudModel.Values("90","100","10","20","full","0",0,0,0,true,"0","0",false,"meltdowner","9.999999999999999999","10",true);
        check(curtain.curtainVisible() && !curtain.domainVisible(), "MD advertises only its fixed lower half-reserve");
        check(!HighPowerRecoveryHudModel.pool(curtain.curtainReserve(),curtain.curtainCapacity(),HighPowerRecoveryHudModel.numberLocale("zh_cn")).text().equals("10 / 10"), "curtain deficit never rounds to full");
        denied(() -> new HighPowerRecoveryHudModel.Values("90","100","10","20","full","0",0,0,0,true,"0","0",false,"electromaster","1","10",true),"EM cannot advertise MD curtain reserve");
        denied(() -> new HighPowerRecoveryHudModel.Values("90","100","10","20","full","0",0,0,0,true,"0","0",false,"meltdowner","11","11",true),"curtain capacity cannot exceed half of base S");

        var large = value("99999999999999999999", "100000000000000000000", "saturated", "10000000000000000000", 10, 7, 0);
        check(large.health().equals("99999999999999999999"), "the exact balance never loses the one-point deficit");
        check(large.maxHealth().equals("100000000000000000000"), "1e20 maximum is represented exactly");
        check(Double.isFinite(large.healthRatio()) && large.healthRatio() >= 0 && large.healthRatio() <= 1, "only the drawn ratio uses bounded floating point");
        check(large.shieldRatio() == .5 && large.pulseRatio() == .3, "separate shield and server pulse progress");
        check(HighPowerRecoveryHudModel.compact("100000000000000000000").equals("100 quintillion"), "English high quantities use readable full-word units");
        check(HighPowerRecoveryHudModel.compact("99999999999999999999").equals("≈99.9999 quintillion"), "rounding is visibly approximate and never rounds a deficit up to full");
        check(HighPowerRecoveryHudModel.compact("0.00000000000000000001").equals("<0.0001"), "small nonzero quantities remain visibly nonzero without exponent notation");
        check(HighPowerRecoveryHudModel.compact("12.5").equals("12.5"), "short exact decimals remain verbatim");
        check(HighPowerRecoveryHudModel.compact("0").equals("0"), "zero has no invented exponent");
        check(HighPowerRecoveryHudModel.ratio("0", "0") == 0, "zero shield capacity is finite");
        check(HighPowerRecoveryHudModel.rate("200000000000000000000").equals(HighPowerRecoveryHudModel.MAX_SCHEDULED_RATE.stripTrailingZeros()), "display rate supports two full pools per second at native max HP one");
        check(HighPowerRecoveryHudModel.compactRate("200000000000000000000").equals("200 quintillion"), "2CAP scheduled rate formats without clipping");
        check(value("1", "100000000000000000000", "saturated", "200000000000000000000", 10, 1, 0).exactHpPerSecond().equals("200000000000000000000"), "payload retains exact rate above ledger CAP");
        denied(() -> HighPowerRecoveryHudModel.decimal("200000000000000000000"), "rate exception does not expand HP/shield ledger bounds");
        denied(() -> HighPowerRecoveryHudModel.rate("200000000000000000001"), "rate ceiling is independently bounded");
        denied(() -> value("1", "20", "saturated", "41", 10, 1, 0), "rate cannot exceed two of this player's smaller pools per second");
        for (String invalidRate : List.of("-1", "1e20", "NaN", "1E999999999", "1.0", "1".repeat(513)))
            denied(() -> HighPowerRecoveryHudModel.rate(invalidRate), "rate uses the same canonical length/scale/precision boundaries");
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) check(HighPowerRecoveryHudModel.clamp(invalid) == 0, "invalid ratio fails closed");
        check(HighPowerRecoveryHudModel.clamp(2) == 1, "drawing ratio cannot exceed panel");
        for (String malformed : List.of("", "NaN", "Infinity", "-1", "+1", "01", "1.0", "1e20", "1E99999999", "100000000000000000001", "0." + "0".repeat(256) + "1", "1".repeat(513)))
            denied(() -> HighPowerRecoveryHudModel.decimal(malformed), "bounded canonical decimal rejects " + malformed.substring(0, Math.min(24, malformed.length())));
        denied(() -> value("21", "20", "full", "0", 0, 0, 0), "HP cannot exceed max");
        denied(() -> value("0", "0", "full", "0", 0, 0, 0), "max HP is positive");
        denied(() -> value("1", "20", "made_up", "0", 0, 0, 0), "status is a bounded enum");
        denied(() -> value("1", "20", "paused", "1", 0, 0, 40), "blocked recovery must show rate zero");
        for (int interval : new int[]{-1, 1, 20, 81, Integer.MAX_VALUE}) denied(() -> value("1", "20", "slow", "1", interval, 0, 0), "only genuine FoodData cadences");
        denied(() -> value("1", "20", "slow", "1", 80, 81, 0), "next pulse cannot exceed period");
        denied(() -> value("1", "20", "slow", "1", 80, -1, 0), "negative next pulse rejected");
        denied(() -> value("1", "20", "paused", "0", 0, 0, 12001), "combat pause wire bound");
        for (String state : HighPowerRecoveryHudModel.STATUSES) {
            var v = value("1", "20", state, "0", 0, 0, state.equals("paused") ? 40 : 0);
            check(Double.isFinite(v.pulseRatio()) && v.pulseRatio() == 0, "blocked/no-timer ratio finite for " + state);
        }
        for (int next = 1; next <= 80; next++) {
            var v = value("1", "20", "slow", "0.25", 80, next, 0);
            check(v.pulseRatio() >= 0 && v.pulseRatio() < 1, "all eighty server timer phases are bounded");
        }
        for (int nativeHp = 0; nativeHp <= 40; nativeHp++) for (int absorption = 0; absorption <= 10; absorption++)
            check((absorption <= HighPowerRecoveryHudModel.absorptionJitterThreshold(nativeHp)) == (nativeHp + absorption <= 4),
                    "absorption retains the exact native low-health jitter predicate");
        Object connection = new Object(), world = new Object(), player = new Object(); UUID owner = UUID.randomUUID();
        var session = new HighPowerRecoveryHudModel.Session(); session.synchronize(connection, world, player);
        check(session.accept(id(owner, 2, "minecraft:overworld", 1, 1), large, owner, 2, "minecraft:overworld"), "first own-player packet accepted");
        check(!session.accept(id(owner, 2, "minecraft:overworld", 1, 1), large, owner, 2, "minecraft:overworld"), "duplicate sequence rejected");
        check(!session.accept(id(UUID.randomUUID(), 2, "minecraft:overworld", 1, 2), large, owner, 2, "minecraft:overworld"), "foreign UUID rejected");
        check(!session.accept(id(owner, 3, "minecraft:overworld", 1, 2), large, owner, 2, "minecraft:overworld"), "foreign entity instance rejected");
        check(!session.accept(id(owner, 2, "minecraft:the_nether", 1, 2), large, owner, 2, "minecraft:overworld"), "foreign dimension rejected");
        session.tick(true); check(session.age() == 0, "local pause does not stale an integrated paused snapshot");
        for (int i = 0; i < HighPowerRecoveryHudModel.STALE_TICKS - 1; i++) session.tick(false);
        check(session.current() == large, "snapshot remains visible within the bounded lease");
        session.tick(false); check(session.current() == null, "quiet stale packet restores native HUD");
        check(session.accept(id(owner, 2, "minecraft:overworld", 1, 2), large, owner, 2, "minecraft:overworld"), "new keepalive restores valid state");
        session.synchronize(connection, new Object(), player); check(session.current() == null, "world transition immediately clears numbers");
        check(!session.accept(id(owner, 2, "minecraft:overworld", 1, 1000), large, owner, 2, "minecraft:overworld"), "retired epoch cannot return after world transition");
        check(session.accept(id(owner, 2, "minecraft:overworld", 2, 1), large, owner, 2, "minecraft:overworld"), "new world epoch admitted");
        session.synchronize(connection, world, new Object());
        check(!session.accept(id(owner, 2, "minecraft:overworld", 2, 1001), large, owner, 2, "minecraft:overworld"), "respawn fences the old player even with reused UUID/id");
        check(session.accept(id(owner, 2, "minecraft:overworld", 3, 1), large, owner, 2, "minecraft:overworld"), "new player epoch admitted");
        check(session.accept(id(owner, 2, "minecraft:overworld", 3, 2), null, owner, 2, "minecraft:overworld") && session.current() == null, "explicit enrollment removal clears HUD");
        check(!session.accept(id(owner, 2, "minecraft:overworld", 3, 1), large, owner, 2, "minecraft:overworld"), "old visible packet cannot undo clear");
        session.synchronize(null, null, null);
        check(!session.accept(id(owner, 2, "minecraft:overworld", 4, 1), large, owner, 2, "minecraft:overworld"), "logout rejects queued delivery");
        session.synchronize(new Object(), world, player);
        check(session.accept(id(owner, 2, "minecraft:overworld", 1, 1), large, owner, 2, "minecraft:overworld"), "a different server connection has its own epoch namespace");
        for (String dimension : List.of("", "a", "minecraft:bad space", "minecraft:" + "a".repeat(129))) denied(() -> id(owner, 2, dimension, 1, 1), "bounded dimension identity");
        denied(() -> id(owner, -1, "minecraft:overworld", 1, 1), "negative entity rejected");
        denied(() -> id(owner, 2, "minecraft:overworld", 0, 1), "zero epoch rejected");
        denied(() -> id(owner, 2, "minecraft:overworld", 1, 0), "zero sequence rejected");
        for (int width : new int[]{320, 427, 640, 960, 1280}) for (int height : new int[]{240, 360, 540}) {
            var cp = new HighPowerRecoveryHudModel.Box(width - 205, 12, 194, 40);
            var keys = new HighPowerRecoveryHudModel.Box(width - 188, (int)((height - 48.3) / 2 + 30), 185, 88);
            var notification = new HighPowerRecoveryHudModel.Box(0, 15, 130, 43);
            var chat = new HighPowerRecoveryHudModel.Box(0, height - 130, Math.min(width, 336), 102);
            var box = HighPowerRecoveryHudModel.layout(width, height, cp, keys, notification, chat);
            check(box != null && box.within(width, height) && !box.intersects(cp) && !box.intersects(keys) && !box.intersects(chat) && !box.intersects(notification), "GUI-scaled default anchors remain disjoint at " + width + "x" + height);
        }
        var huge = new HighPowerRecoveryHudModel.Box(0, 0, 1000, 1000);
        check(HighPowerRecoveryHudModel.layout(320, 240, huge, huge, huge, huge) == null, "crowded custom HUD restores native hearts rather than overlapping");
        check(HighPowerRecoveryHudModel.layout(100, 100, huge, huge, huge, huge) == null, "tiny GUI does not produce negative/clipped bars");
        ClassNode gui = new ClassNode();
        try (InputStream stream = HighPowerRecoveryHudRegressionTest.class.getResourceAsStream("/net/minecraft/client/gui/Gui.class")) {
            check(stream != null, "real pinned NeoForge Gui bytecode is available"); new ClassReader(stream).accept(gui, 0);
        }
        check(RecoveryHudMixinPlugin.supported(gui), "real 1.21.1 Gui callsite passes narrow guard");
        var overlay=gui.methods.stream().filter(m->m.name.equals("renderOverlayMessage")).findFirst().orElseThrow();
        String overlayDesc=overlay.desc; overlay.desc="()V";
        check(!RecoveryHudMixinPlugin.supported(gui),"changed overlay signature refuses transformation"); overlay.desc=overlayDesc;
        var extraRead=new org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETFIELD,RecoveryHudMixinPlugin.GUI,"overlayMessageString","Lnet/minecraft/network/chat/Component;");
        overlay.instructions.add(extraRead);
        check(!RecoveryHudMixinPlugin.supported(gui),"extra overlay read refuses ambiguous transformation"); overlay.instructions.remove(extraRead);
        var firstRead=java.util.Arrays.stream(overlay.instructions.toArray()).filter(i->i instanceof org.objectweb.asm.tree.FieldInsnNode f && f.name.equals("overlayMessageString")).findFirst().orElseThrow();
        var read=(org.objectweb.asm.tree.FieldInsnNode)firstRead;String fieldName=read.name;read.name="other";
        check(!RecoveryHudMixinPlugin.supported(gui),"missing overlay read refuses transformation"); read.name=fieldName;

        var heartsMethod = gui.methods.stream().filter(m -> m.name.equals("renderHearts")).findFirst().orElseThrow();
        org.objectweb.asm.tree.VarInsnNode absorptionOperand = null;
        for (var instruction : heartsMethod.instructions) if (instruction.getOpcode() == Opcodes.ICONST_4) {
            var previous = instruction.getPrevious(); while (previous != null && !(previous instanceof org.objectweb.asm.tree.VarInsnNode)) previous = previous.getPrevious();
            absorptionOperand = (org.objectweb.asm.tree.VarInsnNode)previous;
        }
        int absorptionSlot = absorptionOperand.var; absorptionOperand.var = 9;
        check(!RecoveryHudMixinPlugin.supported(gui), "changed jitter operand refuses transformation despite identical signature/constant"); absorptionOperand.var = absorptionSlot;
        var extraThreshold = new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_4); heartsMethod.instructions.add(extraThreshold);
        check(!RecoveryHudMixinPlugin.supported(gui), "ambiguous absorption-jitter seam refuses transformation"); heartsMethod.instructions.remove(extraThreshold);
        var method = gui.methods.stream().filter(m -> m.name.equals("renderHealthLevel")).findFirst().orElseThrow();
        MethodInsnNode hearts = null;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("renderHearts")) hearts = call;
        String descriptor = hearts.desc; hearts.desc = "()V";
        check(!RecoveryHudMixinPlugin.supported(gui), "changed heart signature refuses transformation"); hearts.desc = descriptor;
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, RecoveryHudMixinPlugin.GUI, "renderHearts", descriptor, false));
        check(!RecoveryHudMixinPlugin.supported(gui), "duplicate ambiguous heart callsite refuses transformation");
        String payload = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerRecoveryHudPayload.java"));
        check(payload.contains("playToClient(") && !payload.contains("playToServer(") && !payload.contains("playBidirectional("), "dedicated payload has no client-to-server mutation route");
        check(payload.contains("MAX_ENCODED_LENGTH") && payload.contains("SEND_INTERVAL") && payload.contains("KEEPALIVE_TICKS"), "bounded string decoding and bounded changed/keepalive publishing");
        String mixin = Files.readString(Path.of("src/main/java/cn/academy/port/client/highpower/mixin/RecoveryHudHeartMixin.java"));
        check(!mixin.contains("args.set(9") && mixin.contains("replacesNativeHearts") && mixin.contains("args.set(6, 0F)"), "native absorption argument is never overwritten");
        String client = Files.readString(Path.of("src/main/java/cn/academy/port/client/HighPowerRecoveryHud.java"));
        check(client.contains("mc.options.hideGui") && client.contains("mc.screen == null") && client.contains("isSpectator()") && client.contains("mc.getCameraEntity() == mc.player"), "visibility excludes F1, screens, spectators and other cameras");
        check(!client.contains("RenderGuiLayerEvent") && !client.contains("setHealth") && !client.contains("playToServer"), "client cannot suppress broad GUI or mutate health");
        check(client.contains("detailPages <= 1") && client.contains("!detailHeld(mc)") && client.contains("rowsPerPage"), "long exact detail strings remain readable through bounded ALT-wheel pages");
        verifyReadableNumbers();
        verifyMixinPackageBoundaries();
        System.out.println("PASS " + checks + " recovery HUD exact-decimal, bounded-rate, session, layout and native-heart guard checks");
    }

    /** The production v6 codec must preserve exact totals and validate every decoded reserve relationship. */
    private static void verifyZeroGapCodec() {
        var identity = id(UUID.randomUUID(), 17, "minecraft:overworld", 2, 7);
        var learned = new HighPowerRecoveryHudModel.Values("90", "100", "7", "20", "full", "0", 0, 0, 0, false,
                "0", "0", false, "meltdowner", "5", "10", false, "13.00000000000000000001", "20", false);
        var active = new HighPowerRecoveryHudModel.Values("90", "100", "49999999999999999999", "50000000000000000000", "full", "0", 0, 0, 0, true,
                "0", "0", false, "meltdowner", "25000000000000000000", "25000000000000000000", false,
                "50000000000000000000", "50000000000000000000", true);
        var curtain = new HighPowerRecoveryHudModel.Values("90", "100", "7", "20", "full", "0", 0, 0, 0, true,
                "0", "0", false, "meltdowner", "5", "10", true);
        var domain = new HighPowerRecoveryHudModel.Values("90", "100", "7", "20", "full", "0", 0, 0, 0, true, "13", "20", true);
        check(HighPowerRecoveryHudPayload.Snapshot.TYPE.id().getPath().equals("hp_recovery_hud_v6"), "new wire fields use a distinct optional v6 channel");
        for (var values : new HighPowerRecoveryHudModel.Values[]{learned, active, curtain, domain, null}) {
            var packet = new HighPowerRecoveryHudPayload.Snapshot(identity, values);
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
            try {
                HighPowerRecoveryHudPayload.Snapshot.CODEC.encode(buffer, packet);
                var decoded = HighPowerRecoveryHudPayload.Snapshot.CODEC.decode(buffer);
                check(packet.equals(decoded) && !buffer.isReadable(), "v6 exactly roundtrips NEW252 active/stored, legacy reserves and clear snapshots");
            } finally { buffer.release(); }
        }
        // Complete packets ensure a malformed amount cannot pass merely because the decoder ran out of bytes.
        for (String[] fields : List.of(
                new String[]{"meltdowner", "20", "5", "10", "4", "20"},
                new String[]{"meltdowner", "20", "5", "10", "16", "20"},
                new String[]{"meltdowner", "20", "5", "10", "13", "19"},
                new String[]{"meltdowner", "20", "5", "9", "13", "20"},
                new String[]{"electromaster", "20", "0", "0", "13", "20"},
                new String[]{"meltdowner", "50000000000000000001", "0", "25000000000000000000.5", "0", "50000000000000000001"},
                new String[]{"meltdowner", "20", "0", "10", "1e1", "20"},
                new String[]{"meltdowner", "20", "0", "10", "1".repeat(513), "20"})) {
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
            try {
                buffer.writeUUID(identity.owner()); buffer.writeInt(identity.entity()); buffer.writeUtf(identity.dimension());
                buffer.writeLong(identity.epoch()); buffer.writeLong(identity.sequence()); buffer.writeBoolean(true);
                buffer.writeUtf("90"); buffer.writeUtf("100"); buffer.writeUtf("0"); buffer.writeUtf(fields[1]);
                buffer.writeUtf("full"); buffer.writeUtf("0"); buffer.writeVarInt(0); buffer.writeVarInt(0); buffer.writeVarInt(0); buffer.writeBoolean(false);
                buffer.writeUtf("0"); buffer.writeUtf("0"); buffer.writeBoolean(false); buffer.writeUtf(fields[0]);
                buffer.writeUtf(fields[2]); buffer.writeUtf(fields[3]); buffer.writeBoolean(false);
                buffer.writeUtf(fields[4]); buffer.writeUtf(fields[5]); buffer.writeBoolean(false);
                boolean rejected = false;
                try { HighPowerRecoveryHudPayload.Snapshot.CODEC.decode(buffer); }
                catch (RuntimeException expected) { rejected = true; }
                check(rejected, "v6 decoder rejects inconsistent, wrong-category, oversized and noncanonical aggregate reserves");
            } finally { buffer.release(); }
        }
    }

    private static void verifyCompactHudAndSummary() {
        var en = HighPowerRecoveryHudModel.hudNumberLocale("en_us");
        var zh = HighPowerRecoveryHudModel.hudNumberLocale("zh_cn");
        check(HighPowerRecoveryHudModel.compact("100000000000000000000",en).equals("100Qi"), "HUD uses readable short English unit");
        check(HighPowerRecoveryHudModel.compact("200000000000",zh).equals("2000亿"), "Chinese familiar units retained");
        String cap="100000000000000000000";
        check(HighPowerRecoveryHudModel.pool("199999999999999.9999",cap,en).current().equals("≈199.9999T"),"small fraction of maximum uses informative own unit");
        for (var locale : List.of(en,zh)) {
            for (String current : List.of("0","0.00000001","1","99999999999999999999","99999999999999999999.99999999",cap)) {
                var pool=HighPowerRecoveryHudModel.pool(current,cap,locale);
                check(pool.full()==current.equals(cap), "compact pool never hides deficit");
                check(current.equals("0") || !pool.current().equals("0"), "positive balance remains visible");
                check(!pool.text().matches(".*[0-9][eE][+-]?[0-9].*"), "short unit is not raw exponent notation");
            }
            check(!HighPowerRecoveryHudModel.pool("12345.6788","12345.6789",locale).full(), "rounded maximum preserves shortfall");
        }
        for (var locale : List.of(en,zh)) for (int panelWidth : new int[]{150,174,202}) {
            for (String current : List.of("0","0.00000001","1","199999999999999.9999","99999999999999999999","99999999999999999999.99999999",cap)) {
                String display=HighPowerRecoveryHudModel.pool(current,cap,locale).text();
                // Conservative built-in font upper bound: ASCII/digits <=6, CJK <=9 GUI pixels.
                int measured=display.codePoints().map(c->c>=0x2e80?9:6).sum();
                var row=HighPowerRecoveryHudModel.numberRow(panelWidth,66,measured);
                check(row.valueX()>=0 && row.valueX()+row.valueWidth()<=panelWidth-5,"numeric row stays in usable panel");
                check(row.valueScale()*measured<=row.valueWidth()+.001,"entire compact value fits without clipping");
                check(row.valueScale()>=.5f,"representative cap/deficit values avoid microscopic scaling");
            }
        }
        check(HighPowerRecoveryHudModel.pool("99999999999999999999",cap,en).text().equals("100Qi−1 / 100Qi"), "one-point English deficit exact");
        check(HighPowerRecoveryHudModel.pool("99999999999999999999.99999999",cap,en).shortfall().equals("−<0.0001"), "subquantum shortfall stays explicit");
        var original=net.minecraft.network.chat.Component.translatableWithFallback("hud.academy.highpower.summary.v1", "%s %s %s %s %s",4,"25.5","100Qi","50Qi","100K").withStyle(net.minecraft.ChatFormatting.AQUA);
        var shortened=HighPowerStatusSummary.select(original,true,300,c->100);
        check(shortened!=original && shortened.getStyle().equals(original.getStyle()), "scoped summary preserves style");
        check(shortened.getString().isEmpty(), "fresh rendered HP/shield/spendable-CP panel removes duplicate periodic actionbar");
        check(HighPowerStatusSummary.select(original,false,300,c->100)==original,"unavailable/stale actual panel restores full original immediately");
        check(HighPowerStatusSummary.select(original,true,50,c->100).getString().isEmpty(),"duplicate summary is quiet even on a narrow screen");
        check(HighPowerStatusSummary.select(original,true,0,c->100)==original,"invalid viewport never suppresses original");
        for (String status : HighPowerRecoveryHudModel.STATUSES) {
            var sample = value("100", "100", status, "0", 0, 0, 0);
            check(HighPowerRecoveryHudModel.ordinaryStatusVisible(sample) == !status.equals("full"), "only redundant full-health status is quiet: " + status);
        }
        var activeDomain = new HighPowerRecoveryHudModel.Values("100", "100", "8", "20", "full", "0", 0, 0, 0, true, "4", "10", true);
        check(HighPowerRecoveryHudModel.ordinaryStatusVisible(activeDomain), "full health does not hide an active domain benefit");
        try { String hud = Files.readString(Path.of("src/main/java/cn/academy/port/client/HighPowerRecoveryHud.java"));
        check(hud.contains("!presetUsesBudget() || budget(SESSION.current()) != null") && hud.contains("layout(mc, mc.getWindow().getGuiScaledWidth()"), "only a fresh visible spendable-CP panel suppresses the duplicate actionbar");
        check(hud.contains("if (hint != null) fields.add(growthText(hint))"), "growth hints remain available in exact ALT details"); } catch (java.io.IOException error) { throw new AssertionError(error); }
        check(HighPowerStatusSummary.select(null,true,300,c->100)==null,"native null guard preserved");
        for (var other : List.of(net.minecraft.network.chat.Component.literal("EM LV4"), net.minecraft.network.chat.Component.translatable("other",4,"0","1","2","3"), net.minecraft.network.chat.Component.translatable("hud.academy.highpower.summary.v1","4","0","1","2","3"), original.copy().append("external")))
            check(HighPowerStatusSummary.select(other,true,300,c->100)==other,"unrelated/malformed/sibling message retains identity");
    }

    private static void verifyReadableNumbers() {
        var zh = HighPowerRecoveryHudModel.numberLocale("zh_cn");
        var tw = HighPowerRecoveryHudModel.numberLocale("zh_tw");
        var ja = HighPowerRecoveryHudModel.numberLocale("ja_jp");
        var en = HighPowerRecoveryHudModel.numberLocale("en_us");
        check(HighPowerRecoveryHudModel.compact("200000000000", zh).equals("2000亿"), "requested 2e11 is visibly 2000亿");
        check(HighPowerRecoveryHudModel.compact("200000000000", tw).equals("2000億"), "traditional Chinese unit");
        check(HighPowerRecoveryHudModel.compact("200000000000", ja).equals("2000億"), "Japanese myriad unit");
        check(HighPowerRecoveryHudModel.compact("200000000000", en).equals("200 billion"), "English billion is a word, not encoded notation");
        check(HighPowerRecoveryHudModel.compact("10000", HighPowerRecoveryHudModel.numberLocale("ZH_TW")).equals("1萬"), "language matching is case independent");
        check(HighPowerRecoveryHudModel.compact("1000000000", HighPowerRecoveryHudModel.numberLocale("unknown")).equals("1 billion"), "unsupported locale has a readable fallback");
        var translated = HighPowerRecoveryHudModel.numberLocale("zh_cn", (key, fallback) -> key.equals("number.unit.8") ? "亿(译)" : fallback);
        check(HighPowerRecoveryHudModel.compact("200000000000", translated).equals("2000亿(译)"), "resource-pack labels do not alter numeric scaling");
        var malformedLabels = HighPowerRecoveryHudModel.numberLocale("zh_cn", (key, fallback) -> " ");
        check(HighPowerRecoveryHudModel.compact("200000000000", malformedLabels).equals("2000亿"), "missing unit labels cannot remove dimensional meaning");
        check(HighPowerRecoveryHudModel.compact("9999.999", zh).equals("9999.999"), "9999.999 never promotes by rounding");
        check(HighPowerRecoveryHudModel.compact("9999.99999", zh).equals("约9999.9999"), "the final pre-promotion fraction rounds down explicitly");
        check(HighPowerRecoveryHudModel.compact("0.0001", zh).equals("0.0001"), "smallest direct fractional display remains exact");
        check(HighPowerRecoveryHudModel.compact("0.00001", zh).equals("<0.0001"), "positive below display quantum never becomes zero");
        check(HighPowerRecoveryHudModel.compact("0", zh).equals("0"), "true zero remains unambiguous");
        check(HighPowerRecoveryHudModel.compactRate("200000000000000000000", zh).equals("2垓"), "rate can reach two ledger caps without exponent notation");
        int[] powers = {4, 8, 12, 16, 20}; String[] units = {"万", "亿", "万亿", "京", "垓"};
        for (int index = 0; index < powers.length; index++) {
            var threshold = java.math.BigDecimal.ONE.scaleByPowerOfTen(powers[index]);
            String exact = threshold.toPlainString();
            check(HighPowerRecoveryHudModel.compact(exact, zh).equals("1" + units[index]), "exact promotion at 10^" + powers[index]);
            String below = threshold.subtract(new java.math.BigDecimal("0.001")).stripTrailingZeros().toPlainString();
            check(!HighPowerRecoveryHudModel.compact(below, zh).endsWith(units[index]), "no early unit promotion just below 10^" + powers[index]);
            if (index < powers.length - 1) check(HighPowerRecoveryHudModel.compact(threshold.multiply(java.math.BigDecimal.TWO).toPlainString(), zh).equals("2" + units[index]), "post-promotion coefficient stays exact");
            for (var locale : List.of(zh, tw, ja, en)) {
                String display = HighPowerRecoveryHudModel.compact(exact, locale);
                check(!display.matches(".*[0-9][eE][+-]?[0-9].*"), "no scientific/engineering notation for constant HUD values");
                check(!HighPowerRecoveryHudModel.compact(exact, zh).contains("兆"), "Chinese scale has no ambiguous 兆 unit");
            }
        }
        String cap = "100000000000000000000", oneShort = "99999999999999999999";
        var missingOne = HighPowerRecoveryHudModel.pool(oneShort, cap, zh);
        check(missingOne.text().equals("1垓−1 / 1垓") && !missingOne.full(), "one-point deficit remains exact and visible at the cap");
        check(HighPowerRecoveryHudModel.pool(cap, cap, zh).text().equals("1垓 / 1垓"), "only exact equality displays the full pair");
        check(HighPowerRecoveryHudModel.pool("100000000000", "200000000000", zh).text().equals("1000亿 / 2000亿"), "pool values use the same readable scale");
        check(HighPowerRecoveryHudModel.pool("1", cap, zh).current().equals("1"), "critical low HP remains one rather than a tiny fraction of 垓");
        check(HighPowerRecoveryHudModel.pool("0", cap, zh).current().equals("0"), "dead/empty pools never inherit near-full text");
        var subQuantumDeficit = HighPowerRecoveryHudModel.pool("99999999999999999999.99999999", cap, zh);
        check(subQuantumDeficit.current().equals("<1垓") && subQuantumDeficit.shortfall().equals("差<0.0001") && !subQuantumDeficit.full(), "sub-display deficit is explicitly present, never falsely full");
        var roundedMaximum = HighPowerRecoveryHudModel.pool("12345.6788", "12345.6789", zh);
        check(roundedMaximum.current().equals("未满") && roundedMaximum.shortfall().equals("差0.0001"), "matching rounded coefficients cannot pretend equal balances");
        check(HighPowerRecoveryHudModel.pool("0.00000001", "0.00000002", zh).current().equals("未满"), "tiny nonzero pools remain distinguishable from zero/full");
        check(HighPowerRecoveryHudModel.pool("0", "0", zh).text().equals("0 / 0"), "zero-capacity shield stays finite");
        for (int width : new int[]{2, 16, 150, 164, 192, 202}) {
            check(HighPowerRecoveryHudModel.barPixels(oneShort, cap, width) == width - 1, "tiny deficit has a visible bar gap at width " + width);
            check(HighPowerRecoveryHudModel.barPixels("1", cap, width) == 1, "live tiny health has a visible fill at width " + width);
            check(HighPowerRecoveryHudModel.barPixels(cap, cap, width) == width, "truly full endpoint at width " + width);
            check(HighPowerRecoveryHudModel.barPixels("0", cap, width) == 0, "truly empty endpoint at width " + width);
            check(HighPowerRecoveryHudModel.barPixels("0", "0", width) == 0, "zero maximum bar is safe at width " + width);
        }
        check(HighPowerRecoveryHudModel.barPixels(oneShort, cap, 1) == 0, "one-pixel test canvas cannot claim an incomplete pool is full");
        for (int width : new int[]{0, -1}) check(HighPowerRecoveryHudModel.barPixels("1", cap, width) == 0, "unavailable bar canvas is empty");
        for (String malformed : List.of("-1", "-0", "+1", "1e20", "NaN", "100000000000000000001"))
            denied(() -> HighPowerRecoveryHudModel.compact(malformed, zh), "localized formatter retains exact amount validation");
        denied(() -> HighPowerRecoveryHudModel.pool("21", "20", zh), "formatted pool cannot exceed its maximum");
        denied(() -> HighPowerRecoveryHudModel.barPixels("21", "20", 100), "pixel rendering cannot disguise an invalid pool");
        for (int available : new int[]{100, 130, 164, 192}) for (int measured : new int[]{20, 150, 260, 360}) {
            float scale = HighPowerRecoveryHudModel.fittedScale(available, measured, .75f);
            check(Float.isFinite(scale) && scale > 0 && scale <= .75f && measured * scale <= available + .001f, "complete numeric text fits the reserved row without clipping");
        }
        for (var locale : List.of(zh, tw, ja, en)) for (int step = 1; step <= 200; step++) {
            var amount = new java.math.BigDecimal(step).movePointLeft(step % 12);
            String canonical = amount.stripTrailingZeros().toPlainString();
            check(!HighPowerRecoveryHudModel.compact(canonical, locale).equals("0"), "every positive fractional amount remains visibly positive");
        }
        try {
            String renderer = Files.readString(Path.of("src/main/java/cn/academy/port/client/HighPowerRecoveryHud.java"));
            String numericDraw = renderer.substring(renderer.indexOf("private static void fittedText("), renderer.indexOf("private static void text("));
            check(!numericDraw.contains("plainSubstrByWidth") && numericDraw.contains("drawString(mc.font, value"), "numeric rows are fitted whole, never substring-truncated");
            String detail = renderer.substring(renderer.indexOf("private static void detail("), renderer.indexOf("private static String tr("));
            check(!detail.contains("compact(") && !detail.contains("compactRate(") && detail.contains("values.health()"), "ALT detail remains the full exact ledger strings");
            String prototype = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/ElectromasterPrototype.java"));
            String fallback = prototype.substring(prototype.indexOf("if(now%10==0)"), prototype.indexOf("private static boolean eligibleArrow"));
            check(!fallback.contains("toEngineeringString") && !fallback.matches("(?s).*\\+\\s*HighPowerCombat\\.healthText.*"), "constant fallback never concatenates engineering notation or raw exact HP");
            check(fallback.contains("hudNumberLocale(p.clientInformation().language())") && fallback.contains("String hp=HighPowerRecoveryHudModel.compact(")
                    && fallback.contains("String shield=HighPowerRecoveryHudModel.compact(") && fallback.contains("String cp=HighPowerRecoveryHudModel.compact("),
                    "HP, shield and prototype CP fallback share the player-localized decimal formatter");
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }

    /** Mixin owns its entire configured package recursively: ordinary marker/helper classes must live outside it. */
    private static void verifyMixinPackageBoundaries() throws Exception {
        var prefixes = new java.util.ArrayList<String>();
        var declaredMixins = new java.util.HashSet<String>();
        var plugins = new java.util.HashSet<String>();
        try (var configs = Files.walk(Path.of("src/main/resources"))) {
            for (Path path : configs.filter(path -> path.toString().endsWith(".mixins.json")).toList()) {
                var json = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                String prefix = json.get("package").getAsString() + "."; prefixes.add(prefix);
                if (json.has("plugin")) plugins.add(json.get("plugin").getAsString());
                for (String side : List.of("mixins", "client", "server")) if (json.has(side))
                    for (var entry : json.getAsJsonArray(side)) declaredMixins.add(prefix + entry.getAsString());
            }
        }
        check(prefixes.size() >= 3, "all current mixin configurations participate in package-boundary validation");
        Path root = Path.of("build/classes/java/main");
        var foundMixins = new java.util.HashSet<String>();
        try (var classes = Files.walk(root)) {
            for (Path path : classes.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = root.relativize(path).toString().replace(java.io.File.separatorChar, '.');
                name = name.substring(0, name.length() - 6);
                final String typeName = name;
                ClassNode node = new ClassNode(); new ClassReader(Files.readAllBytes(path)).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                boolean mixin = java.util.stream.Stream.concat(node.visibleAnnotations == null ? java.util.stream.Stream.empty() : node.visibleAnnotations.stream(),
                        node.invisibleAnnotations == null ? java.util.stream.Stream.empty() : node.invisibleAnnotations.stream())
                        .anyMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"));
                if (declaredMixins.contains(name)) { check(mixin, "each declared mixin is an actual @Mixin, never an ordinary marker: " + name); foundMixins.add(name); }
                boolean owned = prefixes.stream().anyMatch(name::startsWith);
                if (owned) check(declaredMixins.stream().anyMatch(registered -> typeName.equals(registered) || typeName.startsWith(registered + "$"))
                                || plugins.stream().anyMatch(plugin -> typeName.equals(plugin) || typeName.startsWith(plugin + "$")),
                        "mixin-owned package contains no directly loadable ordinary helper/marker: " + name);
                if (!mixin && (node.access & Opcodes.ACC_INTERFACE) != 0)
                    check(!owned, "ordinary marker/interface stays outside every configured mixin prefix: " + name);
            }
        }
        check(foundMixins.containsAll(declaredMixins), "every configured mixin has a compiled class");
        for (String marker : List.of("cn.academy.port.client.highpower.RecoveryHudHeartsAccess", "cn.academy.port.highpower.NativeCooldownAccess", "cn.academy.port.highpower.NativeFoodRecoveryAccess"))
            check(prefixes.stream().noneMatch(marker::startsWith), "directly referenced adapter marker outside all reserved mixin packages: " + marker);
        check("cn.academy.port.client.highpower.RecoveryHudHeartsAccess".startsWith("cn.academy.port.client.highpower."),
                "regression witness: former broad HUD prefix would own and prohibit the directly referenced marker");
    }
}
