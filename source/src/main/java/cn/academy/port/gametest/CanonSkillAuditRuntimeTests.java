package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.canon.CanonAuxItems;
import cn.academy.port.canon.CanonCastHints;
import cn.academy.port.canon.CanonLiquidEntities;
import cn.academy.port.canon.CanonSkills;
import cn.academy.port.canon.CanonVfx;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;

/**
 * Player-path audit of every canon skill. A fresh player gets only category, level, learned skills and the situation
 * that CanonCastHints says the skill needs (shell up, airborne, water, thrown item, ally, paper in hand...), then presses
 * the preset key. Ledger enrollment is left to the production path. Logs acceptance, CP, damage with the hit distance
 * measured at the moment of the hit, and every emitted visual's extent, so range claims can be compared with visuals.
 */
@GameTestHolder("academy_canon_audit")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class CanonSkillAuditRuntimeTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<UUID, List<CanonVfx.Visual>> VISUALS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<String>> HITS = new ConcurrentHashMap<>();
    private static final Map<UUID, ServerPlayer> CASTERS = new ConcurrentHashMap<>();
    private static final Vec3 START = new Vec3(8.5, 2, 3.5);
    /**
     * Skills this generic arena cannot stage faithfully (precise aim at a fresh construct or fire block, a pursued ally, a
     * full shield). Their own runtime tests cover them; every other skill must cast here through the player path.
     */
    /**
     * Accepted casts whose result this arena cannot observe: mock players never integrate their own velocity into position
     * (self and ally float), the liquid body's own steering and senses act on the body, transfer needs a second target pick,
     * fall guard only acts on landing, and the staged mobs already carry the slowness that descent/grapple would add.
     * Each of these has a dedicated runtime test of its real effect.
     */
    private static final java.util.Set<String> EFFECT_BLIND = java.util.Set.of("float_descent", "float_hover", "float_air_brake", "float_ally_ascent",
            "float_ally_hover", "float_ally_descent", "float_hostile_descent", "hydro_transfer", "liquid_dismiss", "liquid_recall", "liquid_grapple",
            "liquid_listen", "liquid_path", "liquid_pause", "liquid_retreat", "liquid_turn", "liquid_follow", "liquid_route", "liquid_survey",
            "liquid_dual_sensor", "nitrogen_fall_guard");
    private static final java.util.Set<String> NOT_STAGED = java.util.Set.of("hydro_extinguish", "hydro_undertow", "liquid_switch",
            "mental_crowd_detach", "nitrogen_ally_guard", "nitrogen_shell_reinforce", "paper_emergency_stairs", "paper_gate", "paper_ladder",
            "paper_plate", "paper_retract", "paper_switch");
    private CanonSkillAuditRuntimeTests() {}

    @SubscribeEvent public static void template(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag(); var size = new ListTag(); var position = new ListTag();
        for (int value : new int[]{16, 12, 28}) size.add(IntTag.valueOf(value));
        for (int i = 0; i < 3; i++) position.add(IntTag.valueOf(0));
        var air = new CompoundTag(); air.putString("Name", "minecraft:air");
        var cell = new CompoundTag(); cell.put("pos", position); cell.putInt("state", 0);
        var blocks = new ListTag(); blocks.add(cell); var palette = new ListTag(); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        for (String namespace : new String[]{"academy_canon_audit", "academy_classic_audit"})   // the classic audit shares the arena
            level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath(namespace, "arena"))
                    .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    /** Damage dealt by an audited caster, with the victim's distance at that instant. */
    @SubscribeEvent public static void damage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !CASTERS.containsKey(p.getUUID())) return;
        var list = HITS.get(p.getUUID());
        if (list != null) list.add(String.format(Locale.ROOT, "%.1f@%.1f", event.getNewDamage(), event.getEntity().position().distanceTo(p.position())));
    }

    /** Everything a skill can visibly do to the arena, so an accepted cast that changes nothing is caught. */
    record Probe(java.util.Map<LivingEntity, float[]> mobs, java.util.Map<LivingEntity, Vec3> mobPos, java.util.Map<LivingEntity, java.util.Set<String>> mobFx,
                         java.util.Map<LivingEntity, Object> mobTarget, Vec3 pos, float hp, java.util.Set<String> fx, int blocks, java.util.Set<Integer> entities, float allyHp, java.util.Set<String> allyFx, Vec3 allyPos) {}
    static java.util.Set<String> fx(LivingEntity e) { var out = new java.util.TreeSet<String>(); for (var i : e.getActiveEffects()) out.add(i.getEffect().unwrapKey().map(k -> k.location().getPath()).orElse("?")); return out; }
    static int invHash(ServerPlayer p) { int x = 0; for (int i = 0; i < p.getInventory().getContainerSize(); i++) { var st = p.getInventory().getItem(i); x = x * 31 + st.getCount() * 7 + st.getItem().hashCode(); } return x; }
    static int blockHash(GameTestHelper h) { int hash = 0; for (int x = 0; x < 16; x++) for (int y = 1; y < 8; y++) for (int z = 0; z < 28; z++) hash = hash * 31 + h.getBlockState(new BlockPos(x, y, z)).hashCode(); return hash; }
    static Probe probe(GameTestHelper h, ServerPlayer p, ServerPlayer ally, List<LivingEntity> mobs) {
        var hp = new java.util.LinkedHashMap<LivingEntity, float[]>(); var pos = new java.util.LinkedHashMap<LivingEntity, Vec3>(); var mfx = new java.util.LinkedHashMap<LivingEntity, java.util.Set<String>>();
        var tgt = new java.util.LinkedHashMap<LivingEntity, Object>();
        for (var m : mobs) { hp.put(m, new float[]{m.getHealth()}); pos.put(m, m.position()); mfx.put(m, fx(m)); tgt.put(m, m instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null ? mob.getTarget().getUUID() : "none"); }
        var ents = new java.util.HashSet<Integer>(); for (var e : h.getLevel().getEntities((net.minecraft.world.entity.Entity) null, p.getBoundingBox().inflate(32), e -> !(e instanceof net.minecraft.world.entity.player.Player))) ents.add(e.getId());
        return new Probe(hp, pos, mfx, tgt, p.position(), p.getHealth(), fx(p), blockHash(h), ents, ally == null ? 0 : ally.getHealth(), ally == null ? java.util.Set.of() : fx(ally), ally == null ? Vec3.ZERO : ally.position());
    }
    static String diff(GameTestHelper h, Probe a, ServerPlayer p, ServerPlayer ally, List<LivingEntity> mobs) {
        var out = new java.util.ArrayList<String>(); double dmg = 0, push = 0; int killed = 0, retarget = 0; var newFx = new java.util.TreeSet<String>();
        for (var m : mobs) {
            if (!m.isAlive() || m.isRemoved()) killed++;
            dmg += Math.max(0, a.mobs().get(m)[0] - m.getHealth());
            push = Math.max(push, m.position().distanceTo(a.mobPos().get(m)));
            for (var f : fx(m)) if (!a.mobFx().get(m).contains(f)) newFx.add(f);
            Object t = m instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null ? mob.getTarget().getUUID() : "none"; if (!t.equals(a.mobTarget().get(m))) retarget++;
        }
        if (dmg > 0) out.add(String.format(Locale.ROOT, "mob_dmg=%.0f", dmg)); if (killed > 0) out.add("killed=" + killed);
        if (push > .4) out.add(String.format(Locale.ROOT, "mob_move=%.1f", push)); if (!newFx.isEmpty()) out.add("mob_fx=" + String.join("+", newFx)); if (retarget > 0) out.add("retarget=" + retarget);
        double moved = p.position().distanceTo(a.pos()); if (moved > .6) out.add(String.format(Locale.ROOT, "self_move=%.1f", moved));
        var pf = new java.util.TreeSet<String>(); for (var f : fx(p)) if (!a.fx().contains(f)) pf.add(f); if (!pf.isEmpty()) out.add("self_fx=" + String.join("+", pf));
        if (p.getHealth() > a.hp() + .5) out.add("self_heal");
        if (blockHash(h) != a.blocks()) out.add("blocks");
        int spawned = 0; for (var e : h.getLevel().getEntities((net.minecraft.world.entity.Entity) null, p.getBoundingBox().inflate(32), e -> !(e instanceof net.minecraft.world.entity.player.Player))) if (!a.entities().contains(e.getId())) spawned++;
        if (spawned > 0) out.add("spawned=" + spawned);
        if (ally != null) { var af = new java.util.TreeSet<String>(); for (var f : fx(ally)) if (!a.allyFx().contains(f)) af.add(f); if (!af.isEmpty()) out.add("ally_fx=" + String.join("+", af));
            if (ally.position().distanceTo(a.allyPos()) > .6) out.add(String.format(Locale.ROOT, "ally_move=%.1f", ally.position().distanceTo(a.allyPos()))); }
        return out.isEmpty() ? "NONE" : String.join(" ", out);
    }

    /** Instant reactions sampled a few ticks after the press: velocity, flight, aggro, items and arrows (mock players never integrate velocity into position). */
    record Early(Vec3 vel, boolean fly, boolean noGrav, java.util.Map<Integer, Vec3> items, java.util.Map<Integer, Vec3> arrows, java.util.Map<LivingEntity, Object> targets, java.util.Map<LivingEntity, Vec3> mobVel) {}
    static Early early(GameTestHelper h, ServerPlayer p, List<LivingEntity> mobs) {
        var items = new java.util.HashMap<Integer, Vec3>(); var arrows = new java.util.HashMap<Integer, Vec3>();
        for (var e : h.getLevel().getEntities((net.minecraft.world.entity.Entity) null, p.getBoundingBox().inflate(32), e -> true)) {
            if (!(e instanceof net.minecraft.world.entity.player.Player) && !(e instanceof net.minecraft.world.entity.projectile.AbstractArrow) && !mobs.contains(e)) items.put(e.getId(), e.position().add(e.getDeltaMovement().scale(10)));
            if (e instanceof net.minecraft.world.entity.projectile.AbstractArrow) arrows.put(e.getId(), e.getDeltaMovement());
        }
        var t = new java.util.HashMap<LivingEntity, Object>(); var mv = new java.util.HashMap<LivingEntity, Vec3>();
        for (var m : mobs) { t.put(m, m instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null ? mob.getTarget().getUUID() : "none"); mv.put(m, m.getDeltaMovement()); }
        return new Early(p.getDeltaMovement(), p.getAbilities().mayfly, p.isNoGravity(), items, arrows, t, mv);
    }
    static String earlyDiff(Early a, Early b) {
        var out = new java.util.ArrayList<String>();
        if (b.vel().subtract(a.vel()).length() > .15) out.add(String.format(Locale.ROOT, "self_vel=%.2f", b.vel().subtract(a.vel()).length()));
        if (b.fly() && !a.fly()) out.add("flight"); if (b.noGrav() != a.noGrav()) out.add("gravity");
        int items = 0; for (var e : b.items().entrySet()) { var was = a.items().get(e.getKey()); if (was == null || was.distanceTo(e.getValue()) > .5) items++; } if (items > 0) out.add("ents=" + items);
        int arrows = 0; for (var e : a.arrows().entrySet()) { var now = b.arrows().get(e.getKey()); if (now == null || now.subtract(e.getValue()).length() > .2) arrows++; } if (arrows > 0) out.add("arrows=" + arrows);
        int re = 0; double kick = 0; for (var m : a.targets().keySet()) { if (!a.targets().get(m).equals(b.targets().get(m))) re++; if (b.mobVel().get(m) != null) kick = Math.max(kick, b.mobVel().get(m).subtract(a.mobVel().get(m)).length()); }
        if (re > 0) out.add("early_retarget=" + re); if (kick > .15) out.add(String.format(Locale.ROOT, "mob_vel=%.2f", kick));
        return out.isEmpty() ? "" : String.join(" ", out);
    }

    @GameTestGenerator public static Collection<TestFunction> audit() {
        var tests = new ArrayList<TestFunction>();
        var only = java.util.Set.of(System.getProperty("academy.audit.only", "").split(","));
        for (var skill : CanonSkills.ALL) {
            String id = skill.id(), category = skill.category();
            if (!only.contains("") && !only.contains(id)) continue;
            tests.add(new TestFunction("audit_" + id, "academy_canon_audit.audit_" + id, "academy_canon_audit:arena", Rotation.NONE, 160, 0, true, false, 1, 1, false,
                    h -> run(h, category, id)));
        }
        return tests;
    }

    private static String prerequisite(List<String> needs) {
        if (needs.contains("shell")) return "nitrogen_armor";
        if (needs.contains("wings")) return "dark_wings";
        if (needs.contains("liquid_body")) return "liquid_scout";
        if (needs.contains("construct")) return "paper_patch";
        if (needs.contains("floating_item")) return "float_item_lift";
        return null;
    }

    /** Canon category shields: an enrolled canon esper hit in melee by a zombie triggers that category's shield trait. */
    @GameTestGenerator public static Collection<TestFunction> shields() {
        var tests = new ArrayList<TestFunction>();
        for (String category : CanonSkills.CATEGORIES)
            tests.add(new TestFunction("shield_" + category, "academy_canon_audit.shield_" + category, "academy_canon_audit:arena", Rotation.NONE, 140, 0, true, false, 1, 1, false,
                    h -> shield(h, category)));
        return tests;
    }
    private static void shield(GameTestHelper h, String category) {
        var level = h.getLevel();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 28; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        var player = join(h, "SH"); player.moveTo(h.absoluteVec(START), 0, 0);
        var state = AbilityStorage.get(player); state.changeCategoryClassic(category); state.setLevel(5);
        state.learn(CanonSkills.root(category)); state.setActivateState(true);
        level.setDayTime(18000); // no sun burn: a burning zombie's hurt cooldown would swallow the counter hit
        var zombie = h.spawn(EntityType.ZOMBIE, START.add(0, 0, 1.5)); zombie.setTarget(player);
        // GameTests run with the legacy shield bypass on; the classic shield tests force shields back on the same way.
        cn.academy.port.highpower.EsperLegacyBypass.force(true);
        try {
            h.assertTrue(cn.academy.port.highpower.HighPowerProgression.certifyCanon(player), "canon esper enrolled");
            h.assertTrue(HighPowerCombat.shield(player).signum() > 0 && cn.academy.port.highpower.EsperShields.holding(player), category + " esper holds a category shield");
            float[] zombieHp = new float[1];
            // A freshly joined player is spawn-invulnerable for 60 ticks; hit after that.
            h.runAfterDelay(65, () -> {
                var before = HighPowerCombat.shield(player);
                // The zombie may already have swung and been countered; clear its hurt cooldown so this hit's counter lands.
                zombie.invulnerableTime = 0; player.setRemainingFireTicks(100); zombieHp[0] = zombie.getHealth();
                player.hurt(level.damageSources().mobAttack(zombie), 4);
                h.assertTrue(HighPowerCombat.shield(player).compareTo(before) < 0, "the hit went into the shield");
            });
            h.runAfterDelay(69, () -> {
                try {
                    switch (category) {
                        case "mental_out" -> h.assertTrue(zombie.getTarget() != player && zombie.hasEffect(MobEffects.CONFUSION), "mind ward drops the attacker's target");
                        case "liquid_shadow" -> h.assertTrue(zombie.hasEffect(MobEffects.BLINDNESS), "shadow veil blinds the attacker");
                        case "hydro_hand" -> h.assertTrue(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && !player.isOnFire(), "water film soaks the attacker and puts the esper out");
                        case "aero_hand", "nitrogen_armor" -> h.assertTrue(zombie.position().distanceTo(player.position()) > 1.6 || zombie.getDeltaMovement().horizontalDistance() > 0.1, "barrier shoves the attacker back");
                        case "paper_psychokinesis" -> h.assertTrue(zombie.getHealth() < zombieHp[0], "paper mail cuts the attacker");
                        default -> { }
                    }
                    if (category.equals("float_dial")) {
                        var shieldBefore = HighPowerCombat.shield(player); float hp = player.getHealth();
                        player.hurt(level.damageSources().fall(), 6);
                        h.assertTrue(HighPowerCombat.shield(player).compareTo(shieldBefore) < 0, "buoyancy cushion takes the fall into the shield");
                    }
                    if (category.equals("dark_matter") || category.equals("paper_psychokinesis") || category.equals("aero_hand")) {
                        var archer = h.spawn(EntityType.SKELETON, START.add(0, 0, 8)); archer.setNoAi(true);
                        var arrow = new Arrow(level, archer, new ItemStack(Items.ARROW), null);
                        arrow.setPos(player.getEyePosition().add(0, -.4, .6)); arrow.setDeltaMovement(0, 0, -1);
                        var event = new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(arrow, new net.minecraft.world.phys.EntityHitResult(player));
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
                        h.assertTrue(event.isCanceled(), category + " shield stops a real arrow");
                        archer.discard(); arrow.discard();
                    }
                    h.succeed();
                } finally { zombie.discard(); player.server.getPlayerList().remove(player); cn.academy.port.highpower.EsperLegacyBypass.force(false); }
            });
        } catch (RuntimeException failure) {
            zombie.discard(); player.server.getPlayerList().remove(player); cn.academy.port.highpower.EsperLegacyBypass.force(false); throw failure;
        }
    }

    /** /aim edits skip the developer: a level-1 enrollment must be rebaselined so level-5 canon skills open up. */
    @net.minecraft.gametest.framework.GameTest(template = "arena", batch = "audit_aim_level", timeoutTicks = 60)
    public static void aim_level_edit_rebaselines_canon_ledger(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 28; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        var player = join(h, "AL"); player.moveTo(h.absoluteVec(START), 0, 0);
        try {
            var state = AbilityStorage.get(player); state.changeCategoryClassic("dark_matter"); state.setLevel(1);
            for (var s : CanonSkills.ALL) if (s.category().equals("dark_matter")) state.learn(s.id());
            state.setActivateState(true); state.presets.edit(0, 0, "dark_construct", state::learned);
            h.assertTrue(cn.academy.port.highpower.HighPowerProgression.certifyCanon(player), "level-1 canon player enrolled");
            h.assertTrue(HighPowerCombat.earnedLevel(player) == 1, "enrolled at the current level");
            var source = player.createCommandSourceStack().withPermission(4);
            player.server.getCommands().performPrefixedCommand(source, "aim level 5");
            h.assertTrue(state.level == 5 && HighPowerCombat.earnedLevel(player) == 5, "/aim level rebaselines the earned level");
            h.assertTrue(AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0")), "level-5 Dark Construct casts after /aim");
            h.succeed();
        } finally { player.server.getPlayerList().remove(player); }
    }

    /** Point the caster's crosshair at a world position, as a real player would before pressing the key. */
    static void aimAt(ServerPlayer p, Vec3 target) {
        Vec3 d = target.subtract(p.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.setYRot(yaw); p.setYHeadRot(yaw); p.setXRot(pitch); p.yRotO = yaw; p.xRotO = pitch;
    }

    static ServerPlayer join(GameTestHelper h, String prefix) {
        var level = h.getLevel(); UUID uuid = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(uuid, prefix + uuid.toString().substring(0, 8)), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection); level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL); player.getAbilities().instabuild = false;
        return player;
    }

    private static void run(GameTestHelper h, String category, String id) {
        var level = h.getLevel(); var needs = CanonCastHints.needs(id);
        h.assertTrue(CanonCastHints.covers(id), "every canon skill lists its cast requirements: " + id);
        level.setDayTime(18000);
        boolean wet = needs.contains("in_water") || needs.contains("target_in_water");
        for (int x = 0; x < 16; x++) for (int z = 0; z < 28; z++) {
            h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            if (wet && x >= 4 && x <= 12 && z <= 20) for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
        }
        if (needs.contains("water_near")) h.setBlock(new BlockPos(6, 2, 5), Blocks.WATER);
        if (needs.contains("ledge")) for (int x = 5; x <= 11; x++) for (int z = 5; z <= 14; z++) { h.setBlock(new BlockPos(x, 1, z), Blocks.AIR); h.setBlock(new BlockPos(x, 0, z), Blocks.AIR); }
        var player = join(h, "AU"); UUID uuid = player.getUUID(); CASTERS.put(uuid, player);
        boolean airborne = needs.contains("airborne") || needs.contains("falling");
        boolean wantsMobs = needs.stream().anyMatch(n -> n.contains("hostile") || n.equals("body_contact") || n.equals("aim_mob") || n.equals("navigating")
                || n.equals("pursuing") || n.equals("target_in_water") || n.equals("not_on_you") || n.equals("shield") && !needs.contains("ally_sneak")) || needs.isEmpty();
        // Airborne strikes have 3-6 block radii, so the caster hovers just above the lane instead of high up.
        Vec3 start = START.add(0, airborne ? (wantsMobs ? 1.0 : 6) : wet ? 1.2 : 0, 0);
        boolean aimDown = needs.contains("block_face") || needs.contains("construct") || needs.contains("fire_block") || needs.contains("farmland")
                || needs.contains("water_aim");
        float pitch = needs.contains("fire_block") ? 20 : needs.contains("ledge") ? 55 : aimDown ? 40 : airborne ? (wantsMobs ? 30 : 25) : 6;
        player.moveTo(h.absoluteVec(start), 0, pitch); player.setYHeadRot(0);
        if (needs.contains("falling")) { player.setDeltaMovement(0, -0.7, 0); player.fallDistance = 4; }
        else if (needs.contains("moving")) player.setDeltaMovement(airborne ? 0.6 : 0, airborne ? -0.05 : 0, airborne ? 0 : 0.6);
        else if (airborne) player.setDeltaMovement(0, -0.05, 0);
        if (needs.contains("on_fire")) player.setRemainingFireTicks(200);
        for (String need : needs) if (need.startsWith("paper") || need.equals("construct")) player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER, 64));
        if (needs.contains("medium") || needs.contains("liquid_body")) player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get(), 16));
        if (needs.contains("crystal")) { player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 1)); player.getInventory().setItem(8, new ItemStack(CanonAuxItems.DARK_MATTER_CRYSTAL.get(), 4)); }
        if (needs.contains("fire_block")) {
            if (id.equals("hydro_campfire_out")) h.setBlock(new BlockPos(8, 2, 6), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
            else if (id.equals("hydro_candle_out")) h.setBlock(new BlockPos(8, 2, 6), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true));
            else h.setBlock(new BlockPos(8, 2, 6), Blocks.FIRE);
        }
        if (needs.contains("farmland")) for (int z = 4; z <= 8; z++) h.setBlock(new BlockPos(8, 1, z), Blocks.FARMLAND);
        if (needs.contains("water_aim")) for (int z = 4; z <= 7; z++) h.setBlock(new BlockPos(8, 1, z), Blocks.WATER);
        if (needs.contains("body_lever")) h.setBlock(new BlockPos(8, 2, 6), Blocks.LEVER);
        var state = AbilityStorage.get(player); state.changeCategoryClassic(category); state.setLevel(5);
        for (var s : CanonSkills.ALL) if (s.category().equals(category)) state.learn(s.id());
        state.setActivateState(true); state.presets.edit(0, 0, id, state::learned);
        String before = prerequisite(needs);
        if (before != null) state.presets.edit(0, 1, before, state::learned);
        boolean mobile = needs.contains("navigating") || needs.contains("pursuing");
        boolean body = needs.stream().anyMatch(n -> n.startsWith("body_hostile") || n.equals("body_contact"));
        double[] lane = body ? new double[]{2.8, 3.5, 4.4} : new double[]{1.8, 3, 5, 8, 12};
        var mobs = new ArrayList<LivingEntity>();
        if (wantsMobs) {
            Vec3 floor = START.add(0, wet ? 0 : 0, 0);
            for (double d : lane) mobs.add(h.spawn(EntityType.ZOMBIE, floor.add(0, 0, d)));
            mobs.add(h.spawn(EntityType.ZOMBIE, floor.add(4, 0, 3)));
            for (var m : mobs) {
                ((Zombie) m).setPersistenceRequired();
                if (!mobile) m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 20, false, false));
            }
        }
        ServerPlayer ally = null;
        if (needs.contains("ally_sneak")) {
            ally = join(h, "AL");
            var board = level.getScoreboard(); var team = board.getPlayerTeam("audit") != null ? board.getPlayerTeam("audit") : board.addPlayerTeam("audit");
            board.addPlayerToTeam(player.getScoreboardName(), team); board.addPlayerToTeam(ally.getScoreboardName(), team);
            ally.moveTo(h.absoluteVec(start.add(0, 0, 3)), 180, 0); ally.setShiftKeyDown(true); ally.setPose(Pose.CROUCHING);
            if (airborne) ally.setDeltaMovement(0, -0.6, 0);
            if (id.equals("hydro_douse_ally")) ally.setRemainingFireTicks(200);
            player.setXRot(airborne ? 10 : 12);
        }
        if (id.equals("hydro_douse_hostile")) for (var m : mobs) m.setRemainingFireTicks(200);
        var floatingItems = new ArrayList<ItemEntity>();
        if (needs.contains("thrown_item") || needs.contains("floating_item")) {
            // A real Q-key toss: only ItemTossEvent stamps the server-side ownership proof these skills require.
            int count = id.contains("field") || id.contains("vortex") || id.contains("orbit") ? 3 : 1;
            for (int k = 0; k < count; k++) {
                var item = net.neoforged.neoforge.common.CommonHooks.onPlayerTossEvent(player, new ItemStack(Items.COBBLESTONE), false);
                if (item == null) continue;
                item.setPickUpDelay(400);
                if (needs.contains("floating_item")) {
                    // Keep the actual toss provenance, but give the audit a reproducible clear ray.
                    item.setPos(player.getEyePosition().add((k - (count - 1) * .5) * .65, -.1, 3));
                    item.setDeltaMovement(Vec3.ZERO);
                    floatingItems.add(item);
                }
            }
        }
        var visuals = new ArrayList<CanonVfx.Visual>(); VISUALS.put(uuid, visuals);
        var hits = new ArrayList<String>(); HITS.put(uuid, hits);
        CanonVfx.serverObserver = v -> { var list = VISUALS.get(v.owner()); if (list != null) list.add(v); };
        double[] cp = new double[2]; boolean[] accepted = new boolean[2]; Probe[] snap = new Probe[1]; double[] peak = {0}; String[] late = {""}; int[] inv = {0}; Early[] pre = new Early[1]; String[] quick = {""}; Vec3[] origin = new Vec3[1]; String[] aim = {"-"};
        final ServerPlayer friend = ally;
        boolean slowStart = needs.contains("navigating") || needs.contains("pursuing") || needs.contains("two_pursuing");
        int press = slowStart ? 25 : 5;
        h.runAfterDelay(3, () -> {
            if (wet) { player.baseTick(); if (friend != null) friend.baseTick(); }
            if (!needs.contains("not_on_you")) for (var m : mobs) if (m instanceof Zombie z) z.setTarget(player);
            player.setOnGround(!airborne && !wet);
            if (!floatingItems.isEmpty()) aimAt(player, floatingItems.get(floatingItems.size() / 2).getBoundingBox().getCenter());
            if (before != null) accepted[1] = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "1"));
        });
        h.runAfterDelay(press, () -> {
            if (wet) { player.baseTick(); if (friend != null) friend.baseTick(); }
            // Crosshair on whatever the skill acts on.
            boolean aims = needs.stream().anyMatch(n -> n.startsWith("aim_") || n.equals("not_on_you") || n.equals("second_hostile") || n.equals("two_pursuing"));
            if (friend != null) aimAt(player, friend.getBoundingBox().getCenter());
            else if (aims && !mobs.isEmpty()) mobs.stream().filter(LivingEntity::isAlive).min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(player)))
                    .ifPresent(m -> aimAt(player, m.getBoundingBox().getCenter()));
            if (!floatingItems.isEmpty()) aimAt(player, floatingItems.get(floatingItems.size() / 2).getBoundingBox().getCenter());
            else if (needs.contains("thrown_item")) level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).stream()
                    .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player))).ifPresent(e -> aimAt(player, e.position()));
            if (needs.contains("fire_block")) aimAt(player, h.absoluteVec(new Vec3(8.5, 2.3, 6.5)));
            if (needs.contains("construct")) {
                BlockPos base = player.blockPosition();
                for (BlockPos q : BlockPos.betweenClosed(base.offset(-4, 0, -4), base.offset(4, 2, 4)))
                    if (!level.getBlockState(q).isAir() && level.getBlockState(q).getBlock() != Blocks.STONE && q.getY() >= base.getY()) {
                        aimAt(player, id.equals("paper_plate") ? Vec3.atCenterOf(q).add(0, .5, 0) : Vec3.atCenterOf(q).add(0, 0, -.5)); break;
                    }
            }
            if (needs.contains("body_arrow")) {
                var archer = h.spawn(EntityType.SKELETON, start.add(0, 0, 12)); archer.setNoAi(true);
                var arrow = new Arrow(level, archer, new ItemStack(Items.ARROW), null);
                arrow.setPos(h.absoluteVec(start.add(0, 0.6, 3.8))); arrow.setDeltaMovement(0, 0, -0.5); level.addFreshEntity(arrow);
            }
            if (needs.contains("incoming_arrow")) {
                var archer = h.spawn(EntityType.SKELETON, start.add(0, 0, 12)); archer.setNoAi(true);
                var arrow = new Arrow(level, archer, new ItemStack(Items.ARROW), null);
                arrow.setPos(h.absoluteVec(start.add(0, 1.5, 4.5))); arrow.setDeltaMovement(0, 0, -0.9); level.addFreshEntity(arrow);
            }
            player.setOnGround(!airborne && !wet);
            if (needs.contains("falling")) player.setDeltaMovement(0, -0.7, 0);
            else if (needs.contains("moving")) player.setDeltaMovement(airborne ? 0.6 : 0, airborne ? -0.05 : 0, airborne ? 0 : 0.6);
            player.setKnownMovement(player.getDeltaMovement());
            if (needs.contains("not_on_you")) for (var m : mobs) if (m instanceof Zombie z) z.setTarget(null);
            cp[0] = HighPowerResources.cp(player); origin[0] = player.position(); snap[0] = probe(h, player, friend, mobs); pre[0] = early(h, player, mobs); inv[0] = invHash(player);
            var pick = player.pick(6, 0, false);
            aim[0] = pick instanceof net.minecraft.world.phys.BlockHitResult b ? level.getBlockState(b.getBlockPos()).getBlock().getDescriptionId().replace("block.minecraft.", "").replace("block.academy.", "")
                    + "@" + b.getDirection().getName() : "none";
            accepted[0] = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0"));
        });
        // Mock players never run their own tick; post the per-player tick events so player-driven skills (constructs, wings, bodies) act as in play.
        for (int k = press + 1; k < 88; k++) h.runAfterDelay(k, () -> { if (!player.isRemoved()) { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player));
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player)); } });
        for (int k = press + 2; k < 88; k += 3) h.runAfterDelay(k, () -> { if (snap[0] != null) for (var m : mobs) if (m.isAlive()) peak[0] = Math.max(peak[0], m.position().distanceTo(snap[0].mobPos().get(m))); });
        if (needs.contains("shield")) h.runAfterDelay(press + 8, () -> {
            // a real arrow at the caster after the ward is up: blocked, deflected or it lands
            var archer = h.spawn(EntityType.SKELETON, start.add(0, 0, 10)); archer.setNoAi(true);
            var arrow = new Arrow(level, archer, new ItemStack(Items.ARROW), null);
            arrow.setPos(player.position().add(3.5, 1.3, .6)); arrow.setDeltaMovement(-1.2, 0, 0); level.addFreshEntity(arrow);   // from the side: no zombie in the lane
            float hp = player.getHealth(); var shieldBefore = HighPowerCombat.shield(player);
            h.runAfterDelay(press + 16, () -> late[0] = (arrow.isRemoved() || arrow.getDeltaMovement().x > -0.5 ? "arrow_stopped" : "arrow_through")
                    + (player.getHealth() < hp ? " self_hurt" : "") + (HighPowerCombat.shield(player).compareTo(shieldBefore) < 0 ? " shield_paid" : ""));
        });
        h.runAfterDelay(press + 3, () -> { if (pre[0] != null) quick[0] = earlyDiff(pre[0], early(h, player, mobs)); });
        h.runAfterDelay(90, () -> {
            try {
                cp[1] = HighPowerResources.cp(player);
                var vfx = new StringBuilder();
                for (var v : visuals) if (v.skill().equals(id)) vfx.append(String.format(Locale.ROOT, "%s:%.1f/%.1f ", v.phase().name().toLowerCase(Locale.ROOT),
                        v.origin().distanceTo(v.endpoint()), v.endpoint().distanceTo(origin[0])));
                String effect = snap[0] == null ? "" : (diff(h, snap[0], player, friend, mobs) + " " + quick[0] + (peak[0] > 1.2 ? String.format(Locale.ROOT, " mob_peak=%.1f", peak[0]) : "")
                        + (invHash(player) != inv[0] ? " inventory" : "") + " " + late[0]).replace("NONE", "").replaceAll("\\bspawned=1\\b", "").trim();
                LOG.info("CANON_EFFECT {} {} accepted={} effect=[{}]", category, id, accepted[0], effect);
                if (accepted[0] && !NOT_STAGED.contains(id) && !EFFECT_BLIND.contains(id))
                    h.assertTrue(!effect.isEmpty(), id + " was accepted but changed nothing in the world (no damage, control, movement, block, item or ward)");
                LOG.info("CANON_AUDIT {} {} accepted={} pre={} enrolled={} cp={} hits=[{}] vfx=[{}] needs={} aim={}", category, id, accepted[0],
                        before == null ? "-" : before + ":" + accepted[1], HighPowerCombat.enabled(player),
                        String.format(Locale.ROOT, "%.1f", cp[0] - cp[1]), String.join(" ", hits), vfx.toString().trim(), needs, aim[0]);
                if (!NOT_STAGED.contains(id)) {
                    h.assertTrue(accepted[0], id + " casts for a player whose ledger was never enrolled by a fixture");
                    h.assertTrue(HighPowerCombat.enabled(player), id + " enrolls the canon player's ledger");
                }
            } finally {
                VISUALS.remove(uuid); HITS.remove(uuid); CASTERS.remove(uuid);
                for (var m : mobs) m.discard();
                player.server.getPlayerList().remove(player);
                if (friend != null) friend.server.getPlayerList().remove(friend);
            }
            h.succeed();
        });
    }
}
