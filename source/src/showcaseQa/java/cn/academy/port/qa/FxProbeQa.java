package cn.academy.port.qa;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Real-client skill witness: what one key press actually changed on the integrated server. `slotset:<skill>` maps the skill to
 * preset slot 3 (key R) as the preset editor would; `fxprobe:start` snapshots every creature, the player, nearby blocks and
 * the inventory; `fxprobe:end:<skill>` logs QA_FX with the differences plus the peak creature displacement sampled each tick.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
final class FxProbeQa {
    private static final Logger LOG = LogUtils.getLogger();
    private record Mark(float hp, Vec3 pos, TreeSet<String> fx, Object target) {}
    private static final Map<UUID, Mark> MOBS = new HashMap<>();
    private static Vec3 pos; private static float hp; private static Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blocks = new HashMap<>(); private static Map<String, Integer> inventory = new HashMap<>(); private static final java.util.Set<Integer> ENTS = new java.util.HashSet<>();
    private static double peak, speed, rise, shield, taken; private static boolean watching; private static int arrowHits, arrowsBlocked, fired, tick;
    private static final TreeSet<String> CAST = new TreeSet<>(), FX_SEEN = new TreeSet<>(); private static final java.util.Set<String> REFUSED = new java.util.concurrent.ConcurrentSkipListSet<>(); private static int retargetSeen;
    private static Map<String, Integer> cooldowns = new HashMap<>();
    /** The action-bar reason a refused canon key press reports (CanonCastHints). */
    @net.neoforged.bus.api.SubscribeEvent
    public static void overlay(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System e) {
        if (watching && e.isOverlay()) REFUSED.add(e.getMessage().getString());
    }
    /** Arrow hits that actually reached the caster while watching, and arrows stopped or turned away near the caster. */
    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void hit(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e) {
        if (watching && e.getEntity() instanceof ServerPlayer && e.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow
                && e.getNewDamage() > 0) { arrowHits++; taken += e.getNewDamage(); }   // a hit a shield fully absorbed is not a hit
    }
    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void impact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent e) {
        if (watching && e.isCanceled() && e.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult h && h.getEntity() instanceof ServerPlayer) arrowsBlocked++;
    }
    private static TreeSet<String> selfFx = new TreeSet<>();
    private FxProbeQa() {}

    static void slot(Minecraft mc, String skill) { slot(mc, skill, 2); }
    /** Slot 2 is the R key, slot 3 the F key: two skills pressed back to back without a remap in between. */
    static void slot(Minecraft mc, String skill, int index) {
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        server.execute(() -> { var sp = server.getPlayerList().getPlayer(id); if (sp == null) return;
            var st = cn.academy.port.AbilityStorage.get(sp); boolean ok = st.presets.edit(st.presets.current(), index, skill, st::learned);
            st.setActivateState(true); cn.academy.port.AbilityStorage.save(sp); cn.academy.port.AcademyNetwork.sync(sp);
            LOG.info("QA_SLOT {} mapped={} learned={}", skill, ok, st.learned(skill)); });
    }

    private static TreeSet<String> fx(LivingEntity e) { var out = new TreeSet<String>(); for (var i : e.getActiveEffects()) out.add(i.getEffect().unwrapKey().map(k -> k.location().getPath()).orElse("?")); return out; }
    private static Object target(LivingEntity e) { return e instanceof Mob m && m.getTarget() != null ? m.getTarget().getUUID() : "none"; }
    private static Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blocks(ServerPlayer p) {
        var out = new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>(); BlockPos c = p.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-12, -3, -12), c.offset(12, 6, 12))) out.put(q.immutable(), p.level().getBlockState(q));
        return out;
    }
    private static Map<String, Integer> inventory(ServerPlayer p) { var out = new HashMap<String, Integer>(); for (int i = 0; i < p.getInventory().getContainerSize(); i++) { var s = p.getInventory().getItem(i); if (!s.isEmpty()) out.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum); } return out; }
    private static java.util.List<LivingEntity> around(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), e -> !(e instanceof Player));
    }

    /** Server-side movement state as the skill controllers read it, at the moment of the key press. */
    static void state(Minecraft mc) {
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        server.execute(() -> { var sp = server.getPlayerList().getPlayer(id); if (sp == null) return;
            LOG.info("QA_STATE ground={} delta={} known={} y={} fall={} yaw={} pitch={}", sp.onGround(), sp.getDeltaMovement(), sp.getKnownMovement(),
                    String.format(Locale.ROOT, "%.2f", sp.getY()), sp.fallDistance, sp.getYRot(), sp.getXRot()); });
    }
    private static final Map<Integer, Vec3> ITEMS = new HashMap<>(); private static double itemPeak;
    private static final Map<net.minecraft.world.entity.projectile.Arrow, Vec3> SHOT = new java.util.IdentityHashMap<>(); private static final java.util.Set<net.minecraft.world.entity.projectile.Arrow> TURNED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    static void start(Minecraft mc) {
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        server.execute(() -> { var sp = server.getPlayerList().getPlayer(id); if (sp == null) return;
            MOBS.clear(); ENTS.clear(); peak = 0; speed = 0; rise = 0; arrowHits = 0; arrowsBlocked = 0; fired = 0; tick = 0; retargetSeen = 0;
            CAST.clear(); FX_SEEN.clear(); REFUSED.clear(); cooldowns = new HashMap<>(cn.academy.port.AbilityStorage.get(sp).cooldowns);
            shield = cn.academy.port.highpower.HighPowerCombat.shield(sp).doubleValue();
            for (var e : around(sp)) MOBS.put(e.getUUID(), new Mark(e.getHealth(), e.position(), fx(e), target(e)));
            for (Entity e : sp.serverLevel().getEntities((Entity) null, sp.getBoundingBox().inflate(24), e -> !(e instanceof Player))) ENTS.add(e.getId());
            ITEMS.clear(); itemPeak = 0; SHOT.clear(); TURNED.clear(); taken = 0;
            for (var e : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sp.getBoundingBox().inflate(24))) ITEMS.put(e.getId(), e.position());
            pos = sp.position(); hp = sp.getHealth(); blocks = blocks(sp); inventory = inventory(sp); selfFx = fx(sp); watching = true; });
    }

    /** Called every client tick while watching: peak creature displacement on the server. */
    static void sample(Minecraft mc) {
        if (!watching || mc.getSingleplayerServer() == null || mc.player == null) return;
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        server.execute(() -> { var sp = server.getPlayerList().getPlayer(id); if (sp == null) return;
            tick++; var changed = new java.util.HashSet<UUID>();
            for (var e : around(sp)) { var m = MOBS.get(e.getUUID()); if (m == null) continue; peak = Math.max(peak, e.position().distanceTo(m.pos()));
                for (var f : fx(e)) if (!m.fx().contains(f)) FX_SEEN.add(f);
                if (!target(e).equals(m.target())) changed.add(e.getUUID()); }
            retargetSeen = Math.max(retargetSeen, changed.size());
            for (var e : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sp.getBoundingBox().inflate(24))) {
                var at = ITEMS.get(e.getId()); if (at != null) itemPeak = Math.max(itemPeak, e.position().distanceTo(at)); }
            speed = Math.max(speed, sp.getDeltaMovement().length()); rise = Math.max(rise, sp.getY() - pos.y);
            for (var c : cn.academy.port.AbilityStorage.get(sp).cooldowns.entrySet()) if (c.getValue() > cooldowns.getOrDefault(c.getKey(), 0)) CAST.add(c.getKey());
            cooldowns = new HashMap<>(cn.academy.port.AbilityStorage.get(sp).cooldowns);
            // Deterministic incoming fire for defensive skills: the arena skeleton looses a real arrow at the caster every 15 ticks.
            if (tick % 15 == 5) for (var s : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Skeleton.class, sp.getBoundingBox().inflate(24))) {
                var arrow = new net.minecraft.world.entity.projectile.Arrow(sp.serverLevel(), s, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
                Vec3 from = s.getEyePosition(), to = sp.position().add(0, sp.getBbHeight() * .6, 0), d = to.subtract(from);
                arrow.setPos(from.add(d.normalize().scale(.8))); arrow.shoot(d.x, d.y + d.horizontalDistance() * .06, d.z, 1.8f, 0);
                sp.serverLevel().addFreshEntity(arrow); fired++; SHOT.put(arrow, d.normalize()); }
            // A deflected arrow ends up flying away from its original line (reflected, turned aside or stopped dead in the air).
            for (var a : SHOT.entrySet()) { var v = a.getKey().getDeltaMovement();
                if (!a.getKey().isRemoved() && !a.getKey().onGround() && v.lengthSqr() > 1e-4 && v.normalize().dot(a.getValue()) < .5) TURNED.add(a.getKey()); } });
    }

    static void end(Minecraft mc, String skill) {
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        server.execute(() -> { var sp = server.getPlayerList().getPlayer(id); if (sp == null) return; watching = false;
            var out = new ArrayList<String>(); double dmg = 0; int killed = 0, retarget = 0; var newFx = new TreeSet<String>();
            var alive = new java.util.HashSet<UUID>();
            for (var e : around(sp)) { var m = MOBS.get(e.getUUID()); if (m == null) continue; alive.add(e.getUUID());
                dmg += Math.max(0, m.hp() - e.getHealth()); for (var f : fx(e)) if (!m.fx().contains(f)) newFx.add(f);
                if (!target(e).equals(m.target())) retarget++; }
            for (var k : MOBS.keySet()) if (!alive.contains(k)) killed++;
            if (dmg > 0) out.add(String.format(Locale.ROOT, "dmg=%.0f", dmg)); if (killed > 0) out.add("killed=" + killed);
            if (peak > 1) out.add(String.format(Locale.ROOT, "mob_peak=%.1f", peak)); if (!newFx.isEmpty()) out.add("mob_fx=" + String.join("+", newFx));
            if (retarget > 0) out.add("retarget=" + retarget); else if (retargetSeen > 0) out.add("retarget_seen=" + retargetSeen);
            FX_SEEN.removeAll(newFx); if (!FX_SEEN.isEmpty()) out.add("mob_fx_seen=" + String.join("+", FX_SEEN));
            if (itemPeak > .5) out.add(String.format(Locale.ROOT, "item_move=%.1f", itemPeak));
            if (rise > 1) out.add(String.format(Locale.ROOT, "self_rise=%.1f", rise)); if (speed > .45) out.add(String.format(Locale.ROOT, "self_speed=%.2f", speed));
            double sh = cn.academy.port.highpower.HighPowerCombat.shield(sp).doubleValue(); if (Math.abs(sh - shield) > 1e-6) out.add(String.format(Locale.ROOT, "shield=%+.0f", sh - shield));
            double moved = sp.position().distanceTo(pos); if (moved > .8) out.add(String.format(Locale.ROOT, "self_move=%.1f", moved));
            var sf = fx(sp); sf.removeAll(selfFx); if (!sf.isEmpty()) out.add("self_fx=" + String.join("+", sf));
            if (sp.getAbilities().mayfly && !sp.isCreative()) out.add("flight");
            var nowBlocks = blocks(sp); var changedKinds = new TreeSet<String>(); int changed = 0;
            for (var e : blocks.entrySet()) { var now = nowBlocks.get(e.getKey()); if (now != null && now != e.getValue()) { changed++; changedKinds.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(now.getBlock()).getPath()); } }
            if (changed > 0) out.add("blocks=" + changed + ":" + String.join("+", changedKinds));
            var inv = inventory(sp); var invDelta = new TreeSet<String>();
            for (var k : new java.util.HashSet<String>() {{ addAll(inv.keySet()); addAll(inventory.keySet()); }}) { int d = inv.getOrDefault(k, 0) - inventory.getOrDefault(k, 0); if (d != 0) invDelta.add(k + (d > 0 ? "+" : "") + d); }
            if (!invDelta.isEmpty()) out.add("inv=" + String.join(",", invDelta));
            int spawned = 0; for (Entity e : sp.serverLevel().getEntities((Entity) null, sp.getBoundingBox().inflate(24), e -> !(e instanceof Player))) if (!ENTS.contains(e.getId())) spawned++;
            if (spawned > 0) out.add("spawned=" + spawned);
            if (fired > 0) out.add("arrows=" + arrowHits + "/" + fired + " arrows_blocked=" + arrowsBlocked + " deflected=" + TURNED.size() + String.format(Locale.ROOT, " taken=%.1f", taken));
            out.add("cast=" + (CAST.isEmpty() ? "-" : String.join("+", CAST))); if (!REFUSED.isEmpty()) out.add("refused=\"" + String.join(" / ", REFUSED) + "\"");
            var st = cn.academy.port.AbilityStorage.get(sp);
            LOG.info("QA_FX {} {} effect=[{}] cp={}", st.category, skill, String.join(" ", out), String.format(Locale.ROOT, "%.0f", cn.academy.port.highpower.HighPowerResources.cp(sp))); });
    }
}
