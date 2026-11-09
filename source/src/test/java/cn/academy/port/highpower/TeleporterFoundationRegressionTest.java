package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.ScatterPlateShares;
import cn.academy.port.highpower.core.SharedShieldReserveShares;
import cn.academy.port.highpower.core.ZeroGapArmorShares;
import cn.academy.port.preset.PresetSkills;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Headless NEW313 registration, classic migration, retained shield parents and v6 wire contracts. */
public final class TeleporterFoundationRegressionTest {
    private static int checks;
    private static final String ID = HighPowerSkills.SAFE_LANDING_PROBE;
    private static void check(boolean condition, String why) { checks++; if (!condition) throw new AssertionError(why); }
    private static void denied(Runnable action, String why) {
        boolean denied = false;
        try { action.run(); } catch (IllegalArgumentException expected) { denied = true; }
        check(denied, why);
    }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static AbilityProgress ability(String category, int level) {
        var state = new AbilityProgress(); state.selectCategory(category); state.setLevel(level); state.setActivateState(true); return state;
    }
    private static HighPowerRecoveryHudModel.Values tp(String status) {
        return new HighPowerRecoveryHudModel.Values("199999999999.99999999999999999999", "200000000000", "0", "0",
                status, status.equals("slow") ? "2500000000" : "0", status.equals("slow") ? 80 : 0,
                status.equals("slow") ? 70 : 0, status.equals("paused") ? 40 : 0,
                false, "0", "0", false, HighPowerCategories.TELEPORTER, "0", "0", false, "0", "0", false);
    }
    private static final class Battery implements DevelopmentProcess.Developer {
        double energy;
        Battery(double energy) { this.energy = energy; }
        public DeveloperType type() { return DeveloperType.PORTABLE; }
        public double energy() { return energy; }
        public double maxEnergy() { return 10000; }
        public boolean tryPullEnergy(double amount) {
            if (energy >= amount) { energy -= amount; return true; }
            energy = 0; return false;
        }
    }

    public static void main(String[] args) throws Exception {
        CurrentHighPowerInventory.verify();
        registrationAndPaidLearning();
        categoryAndSaveMigration();
        neutralHudCannotConsumeOrExposeParents();
        wireAndSessionFences();
        serverBoundaryAudit();
        System.out.println("PASS " + checks + " NEW313 foundation, category/save migration, retained parents and neutral v6 HUD checks");
    }

    private static void registrationAndPaidLearning() {
        var root = SkillCatalog.find(HighPowerCategories.TELEPORTER, ID).orElseThrow();
        check(HighPowerSkills.SAFE_LANDING_PROBE_PROPOSAL == 313 && ID.equals("safe_landing_probe"), "canonical proposal and persistent ID");
        check(root.level() == 1 && root.controllable() && root.requirements().isEmpty() && root.anyLearnedSkillLevel() == 0, "TP has one LV1 controllable root without prerequisites");
        check(HighPowerSkills.ALL.get(24).equals(root) && HighPowerSkills.IDS.contains(ID), "NEW313 retains its appended ordinal in explicit registries");
        check(SkillCatalog.ALL.size() == 50 && SkillCatalog.REGISTERED.size() == (111 + cn.academy.port.canon.CanonSkills.ALL.size()) && cn.academy.port.SkillCatalog.REGISTERED.get(100).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(30).id().equals(HighPowerSkills.CORNER_EXCHANGE) && HighPowerSkills.ALL.get(31).id().equals(HighPowerSkills.SHORT_INTERCEPTION)
                && HighPowerSkills.ALL.get(32).id().equals(HighPowerSkills.SPATIAL_RELAY)
                && HighPowerSkills.ALL.get(33).id().equals(HighPowerSkills.EMERGENCY_RETURN)
                && HighPowerSkills.ALL.get(34).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION)
                && SkillCatalog.REGISTERED.get(84).equals(HighPowerSkills.ALL.get(34))
                && HighPowerSkills.ALL.stream().filter(skill -> skill.category().equals("teleporter")).count() == 15
                && HighPowerSkills.ALL.get(35).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.REGISTERED.get(85).equals(HighPowerSkills.ALL.get(35))
                && HighPowerSkills.ALL.get(36).id().equals(HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY)
                && SkillCatalog.REGISTERED.get(86).equals(HighPowerSkills.ALL.get(36)),
                "classic fifty and all retained TP ordinals are immutable; NEW353 stays eleventh and NEW341 stays twelfth and NEW351 appends as the thirteenth TP addition");
        check(HighPowerSkills.ALL.subList(0, 30).stream().filter(s -> s.category().equals("teleporter")).map(SkillCatalog.Skill::id).toList().equals(List.of(ID, HighPowerSkills.PHASE_SIDESTEP, HighPowerSkills.DISPLACED_PROJECTION, HighPowerSkills.RETURN_MARK, HighPowerSkills.RECOVERY_MARK, HighPowerSkills.CROSS_SUBSTITUTION)), "only approved NEW313, NEW312, NEW311 and appended NEW322/NEW323/NEW321 enter TP in stable order");
        for (String category : List.of("electromaster", "meltdowner", "teleporter")) {
            check(HighPowerCategories.enabled(category), "explicit admission " + category);
            for (int level = 0; level <= 6; level++) {
                var state = new AbilityProgress(); state.category = category; state.level = level;
                check(HighPowerCategories.active(state) == (level >= 1 && level <= 5), "real integer level admission " + category + "/" + level);
            }
        }
        for (String category : List.of("unsupported_test", "future", "")) check(!HighPowerCategories.enabled(category), "foreign category remains closed " + category);
        check(!HighPowerCategories.enabled(null) && !HighPowerCategories.active(null), "missing category/state remains closed");
        check(HighPowerCategories.recoveryRoot("teleporter").equals(ID), "food-only rest extension requires learned NEW313");
        check(HighPowerCategories.recoveryRoot("unsupported_test") == null, "foreign category has no recovery root");

        var state = ability("teleporter", 1);
        int denominator = SkillCatalog.levelSkillCount(state);
        check(denominator == SkillCatalog.ALL.stream().filter(s -> s.category().equals("teleporter") && s.level() == 1 && s.controllable()).count(), "NEW313 does not enter classic XP denominator");
        check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.edit(state, 0, 0, ID), "unlearned root cannot cast or bind");
        check(DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, root) && SkillCatalog.canLearn(state, root), "actual LV1 Portable purchase is eligible");
        var process = new DevelopmentProcess(); var battery = new Battery(2340);
        state.levelExperience = .125; var action = DevelopmentActions.skill(state, root);
        check(action.stimulations() == 3 && DeveloperType.PORTABLE.actualConsumption(3) == 2340, "existing finite Portable cost is 78 ticks / 2340 IF");
        process.start(battery, action);
        for (int tick = 0; tick < 77; tick++) process.tick();
        check(!state.learned(ID) && battery.energy == 30, "root waits for the final paid tick");
        process.tick();
        check(state.learned(ID) && state.exp(ID) == 0 && state.levelExperience == .125 && battery.energy == 0, "paid completion grants only learned bit without XP");
        check(SkillCatalog.levelSkillCount(state) == denominator && HighPowerSkills.eligible(state, ID), "learning changes eligibility without denominator inflation");
        check(PresetSkills.edit(state, 0, 0, ID) && state.presets.currentSkill(0).equals(ID), "ordinary preset registration includes learned root");
        for (String category : List.of("electromaster", "meltdowner", "vecmanip")) {
            var wrong = ability(category, 5); wrong.experience.put(ID, 0.0); // Deliberately stale foreign learned bit.
            check(!HighPowerSkills.eligible(wrong, ID) && !DevelopmentActions.canLearn(wrong, DeveloperType.ADVANCED, root)
                    && !PresetSkills.selectable(wrong, ID), "stale root cannot cross category " + category);
        }
        var saved = AbilityStorage.encodeSaved(state);
        try {
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ID), new ClassicSkillConfiguration.Flags(false, false)));
            check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID), "live disabled flag gates cast and binding");
            var fresh = ability("teleporter", 1);
            check(!SkillCatalog.canLearn(fresh, root) && !DevelopmentActions.canLearn(fresh, DeveloperType.PORTABLE, root), "disabled root refuses both actual learning validators");
            check(AbilityStorage.encodeSaved(state).equals(saved), "disabled root retains learned/bound state and native saved resources");
            var config = SkillCatalog.configurationSnapshot(); SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(config) && !SkillCatalog.enabled("teleporter", ID), "existing bounded config transport includes TP root");
        } finally { SkillCatalog.resetConfiguration(); }
    }

    private static void categoryAndSaveMigration() {
        var old = ability("teleporter", 4);
        old.learn(SkillCatalog.ALL.stream().filter(s -> s.category().equals("teleporter")).findFirst().orElseThrow().id());
        old.levelExperience = .37; old.extraCp = 3.5;
        old.cp = 31.25; old.overload = 7.5; old.cpDelay = 11; old.overloadDelay = 13;
        var tag = AbilityStorage.encodeSaved(old); var restored = AbilityStorage.decodeSaved(tag, old.cpDataConfig());
        check(!restored.learned(ID) && AbilityStorage.encodeSaved(restored).equals(tag), "pre-NEW313 TP save gains no learned root, refill or schema change");
        restored.learn(ID); restored.setActivateState(true); check(PresetSkills.edit(restored, 2, 1, ID), "canonical binding is saveable");
        restored.cp = 27.5; restored.overload = 4.25;
        tag = AbilityStorage.encodeSaved(restored); var relog = AbilityStorage.decodeSaved(tag, restored.cpDataConfig());
        check(relog.learned(ID) && relog.presets.skill(2, 1).equals(ID) && AbilityStorage.encodeSaved(relog).equals(tag), "learned TP root/binding and depleted native ledgers roundtrip schema3");
        for (String from : List.of("electromaster", "meltdowner")) {
            var state = ability(from, 4); state.levelExperience = .37; state.extraCp = 3.5;
            state.learn(HighPowerCategories.recoveryRoot(from)); state.presets.edit(0, 0, HighPowerCategories.recoveryRoot(from), state::learned);
            state.setCooldown(HighPowerCategories.recoveryRoot(from), 9);
            state.changeCategoryClassic("teleporter");
            check(state.level == 4 && state.levelExperience == .37 && state.extraCp == 3.5, "genuine switch keeps native level, progress and training " + from);
            check(state.experience.isEmpty() && state.presets.currentSkill(0).isEmpty() && state.cooldowns.isEmpty(), "genuine switch retains native learned/preset/cooldown clearing " + from);
            state.learn(ID); state.setActivateState(true);
            check(HighPowerSkills.eligible(state, ID) && state.levelExperience == .37, "relearning TP grants no retained XP reinterpretation " + from);
            state.selectCategory("teleporter");
            check(state.level == 0 && state.levelExperience == 0 && state.extraCp == 0 && state.experience.isEmpty()
                    && !state.activated, "admin category selection remains its separate native reset path " + from);
        }
    }

    private static void neutralHudCannotConsumeOrExposeParents() {
        var base = new ExactCombatState(n("71.125"), n("100"), n("28"), n("60"), false);
        var scatter = ScatterPlateShares.restore(base.shield(), base.maxShield(), List.of(n("10"), n("10"), n("8")));
        var reserves = new SharedShieldReserveShares(n("13"), n("16"));
        var armor = ZeroGapArmorShares.restore(base, scatter, reserves,
                List.of(n("0"), n("10"), n("3"), n("7"), n("8"), n("0")),
                List.of(n("0"), n("10"), n("5"), n("0"), n("8"), n("6")));
        var children = armor.toDecimalMap();
        var neutral = tp("slow");
        check(neutral.categoryLabel().equals("TP") && neutral.teleporter() && !neutral.electromaster() && !neutral.meltdowner(), "TP label is explicit rather than default EM/MD");
        check(!neutral.shieldVisible() && !neutral.shieldActive() && !neutral.domainVisible()
                && !neutral.curtainVisible() && !neutral.zeroGapVisible(), "neutral TP shows no base shield or reserve benefit");
        check(neutral.health().equals("199999999999.99999999999999999999") && neutral.healing(), "TP retains exact HP and food-only rate");
        var damage = base.withShieldEnabled(false).applyMitigatedDamage(n("0.00000000000000000001"));
        check(damage.shieldAbsorbed().signum() == 0 && damage.state().health().compareTo(base.health()) < 0
                && damage.state().shield().equals(base.shield()), "an explicitly disabled pure shield state spends HP and retains stored shield");
        var after = armor.reconcileParents(damage.state(), scatter, reserves).after();
        check(after.toDecimalMap().equals(children) && after.reserves().equals(reserves), "ordinary HP damage retains all unequal twelve children and lower/upper parents");
        check(ZeroGapArmorShares.fromDecimalMap(damage.state(), scatter, reserves, children).toDecimalMap().equals(children), "retained children restore without repartition or refill");
        for (String language : List.of("zh_cn", "zh_tw", "ja_jp", "en_us")) {
            var pool = HighPowerRecoveryHudModel.pool(neutral.health(), neutral.maxHealth(), HighPowerRecoveryHudModel.numberLocale(language));
            check(!pool.full() && HighPowerRecoveryHudModel.barPixels(neutral.health(), neutral.maxHealth(), 180) == 179, "tiny TP HP deficit remains visible " + language);
        }
        check(HighPowerRecoveryHudModel.compact("200000000000", HighPowerRecoveryHudModel.numberLocale("zh_cn")).equals("2000亿"), "Chinese formatter remains unchanged");
        var phaseShield = new HighPowerRecoveryHudModel.Values("1", "20", "1", "10", "full", "0", 0, 0, 0, true, "0", "0", false, "teleporter");
        check(phaseShield.shieldVisible() && phaseShield.shieldHolding() && !phaseShield.domainVisible()
                && !phaseShield.curtainVisible() && !phaseShield.zeroGapVisible(), "TP publishes its real base shield and super armor, without EM or MD reserve benefits");
        var brokenPhaseShield = new HighPowerRecoveryHudModel.Values("1", "20", "0", "10", "full", "0", 0, 0, 0, true, "0", "0", false, "teleporter");
        check(brokenPhaseShield.shieldVisible() && !brokenPhaseShield.shieldHolding(), "broken TP shield remains visible at zero and cannot advertise super armor");
        denied(() -> new HighPowerRecoveryHudModel.Values("1", "20", "0", "0", "full", "0", 0, 0, 0, false, "0", "0", true, "teleporter"), "TP rejects EM domain status");
        denied(() -> new HighPowerRecoveryHudModel.Values("1", "20", "0", "0", "full", "0", 0, 0, 0, false, "0", "0", false, "teleporter", "0", "0", true), "TP rejects MD curtain status");
        denied(() -> new HighPowerRecoveryHudModel.Values("1", "20", "0", "0", "full", "0", 0, 0, 0, false, "0", "0", false, "unsupported_test"), "unknown HUD category cannot default to TP/EM/MD");
    }

    private static void wireAndSessionFences() {
        var owner = new UUID(3, 13); var identity = new HighPowerRecoveryHudModel.Identity(owner, 4, "minecraft:overworld", 1, 1);
        check(HighPowerRecoveryHudPayload.Snapshot.TYPE.id().getPath().equals("hp_recovery_hud_v6"), "new TP enum negotiates its own optional channel");
        for (String status : HighPowerRecoveryHudModel.STATUSES) {
            var packet = new HighPowerRecoveryHudPayload.Snapshot(identity, tp(status));
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
            try {
                HighPowerRecoveryHudPayload.Snapshot.CODEC.encode(buffer, packet);
                check(packet.equals(HighPowerRecoveryHudPayload.Snapshot.CODEC.decode(buffer)) && !buffer.isReadable(), "v6 exact TP status roundtrip " + status);
            } finally { buffer.release(); }
        }
        var phasePacket = new HighPowerRecoveryHudPayload.Snapshot(identity,
                new HighPowerRecoveryHudModel.Values("19", "20", "8", "10", "full", "0", 0, 0, 0, true, "0", "0", false, "teleporter"));
        var phaseBuffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try {
            HighPowerRecoveryHudPayload.Snapshot.CODEC.encode(phaseBuffer, phasePacket);
            var decoded = HighPowerRecoveryHudPayload.Snapshot.CODEC.decode(phaseBuffer);
            check(phasePacket.equals(decoded) && !phaseBuffer.isReadable() && decoded.values().shieldHolding()
                    && !decoded.values().domainVisible() && !decoded.values().curtainVisible(), "v6 TP base shield roundtrips without leaking category-specific reserves");
        } finally { phaseBuffer.release(); }
        // Complete malicious packets prove category and bound validation, rather than buffer underflow.
        for (String[] fields : List.of(new String[]{"teleporter", "11", "10", "0", "0", "0", "0", "0", "0"},
                new String[]{"teleporter", "0", "0", "1", "10", "0", "0", "0", "0"},
                new String[]{"teleporter", "0", "0", "0", "0", "1", "10", "0", "0"},
                new String[]{"teleporter", "0", "0", "0", "0", "0", "0", "1", "10"},
                new String[]{"unsupported_test", "0", "0", "0", "0", "0", "0", "0", "0"})) {
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
            try {
                buffer.writeUUID(owner); buffer.writeInt(4); buffer.writeUtf("minecraft:overworld"); buffer.writeLong(1); buffer.writeLong(1); buffer.writeBoolean(true);
                buffer.writeUtf("1"); buffer.writeUtf("20"); buffer.writeUtf(fields[1]); buffer.writeUtf(fields[2]);
                buffer.writeUtf("full"); buffer.writeUtf("0"); buffer.writeVarInt(0); buffer.writeVarInt(0); buffer.writeVarInt(0); buffer.writeBoolean(false);
                buffer.writeUtf(fields[3]); buffer.writeUtf(fields[4]); buffer.writeBoolean(false); buffer.writeUtf(fields[0]);
                buffer.writeUtf(fields[5]); buffer.writeUtf(fields[6]); buffer.writeBoolean(false);
                buffer.writeUtf(fields[7]); buffer.writeUtf(fields[8]); buffer.writeBoolean(false);
                boolean rejected = false;
                try { HighPowerRecoveryHudPayload.Snapshot.CODEC.decode(buffer); } catch (RuntimeException expected) { rejected = true; }
                check(rejected, "complete v6 packet rejects over-cap base shield, cross-category reserves or foreign enum");
            } finally { buffer.release(); }
        }
        var em = new HighPowerRecoveryHudModel.Values("1", "20", "7", "10", "full", "0", 0, 0, 0, true);
        var md = new HighPowerRecoveryHudModel.Values("1", "20", "7", "10", "full", "0", 0, 0, 0, false, "0", "0", false, "meltdowner");
        var session = new HighPowerRecoveryHudModel.Session(); var connection = new Object(); var world = new Object(); var player = new Object();
        session.synchronize(connection, world, player);
        long sequence = 0;
        for (var value : List.of(em, md, tp("unlearned"), tp("slow"), em))
            check(session.accept(new HighPowerRecoveryHudModel.Identity(owner, 4, "minecraft:overworld", 1, ++sequence), value, owner, 4, "minecraft:overworld"), "ordered EM/MD/TP/relearn/return snapshot accepted");
        check(!session.accept(identity, tp("slow"), owner, 4, "minecraft:overworld") && session.current() == em, "late TP packet cannot replace returned EM category");
        session.synchronize(new Object(), world, player);
        check(session.current() == null && session.accept(identity, tp("slow"), owner, 4, "minecraft:overworld"), "relog begins fresh snapshot namespace without inherited category");
    }

    /** Static contracts supplement headless behavior; actual server events require the separate runtime batch. */
    private static void serverBoundaryAudit() throws Exception {
        String progression = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerProgression.java"));
        check(progression.contains("boolean fresh = !HighPowerCombat.enabled(player);") && progression.contains("if (fresh) {")
                && progression.contains("if (fresh) HighPowerCombat.synchronizeEarnedProgress(player);"), "learning's existing ledger opt-in and initial witness remain fresh-only");
        String payload = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerRecoveryHudPayload.java"));
        check(payload.contains("event.registrar(\"6\").optional()") && payload.contains("NetworkRegistry.hasChannel"), "optional version negotiation checks connection before sending");
        check(!payload.contains("!electromaster") && payload.contains("shieldCategory ? s.shield() : \"0\"")
                && payload.contains("EsperShields.eligible(player)") && payload.contains("meltdowner && DissipationCurtain.active(player)"), "TP base shield is eligible while MD curtain and EM reserves stay category-gated");
        String renderer = Files.readString(Path.of("src/main/java/cn/academy/port/client/HighPowerRecoveryHud.java"));
        check(renderer.contains("values.shieldVisible()") && renderer.contains("scheduled_rest_rate") && renderer.contains("status.unlearned_tp"), "TP renderer names inactive shield, extension and correct unlearned root");
        String recovery = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerNaturalRecovery.java"));
        check(recovery.contains("HighPowerCategories.recoveryRoot(ability.category)") && recovery.contains("food.getFoodLevel() < 18")
                && recovery.contains("GameRules.RULE_NATURAL_REGENERATION") && recovery.contains("pauseTicksRemaining(server) > 0"), "TP extension keeps category-root, hunger, gamerule and combat-pause guards");
    }
}
