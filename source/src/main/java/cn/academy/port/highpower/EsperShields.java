package cn.academy.port.highpower;

import cn.academy.port.AbilityDamage;
import cn.academy.port.AbilityStorage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

/**
 * Category shields. Every esper with an active ability category carries the shared base shield of the exact ledger; it now takes
 * ordinary hits before health (see {@code HighPowerCombat.plainSettlement}) and, while anything is left of it, the esper has super
 * armor: knockback is cancelled, so the hit that empties the shield is the first one that moves them. Each category adds its own
 * trait on top:
 * <ul>
 * <li>electromaster, electromagnetic barrier: an absorbed melee hit discharges an arc into the attacker and numbs it; lightning
 * recharges the barrier instead of hurting;</li>
 * <li>meltdowner, plasma shell: an absorbed melee hit burns the attacker; projectiles are vaporised against the shell for a small
 * shield cost;</li>
 * <li>teleporter, phase veil: an absorbed hit may shift the esper aside; when the veil breaks the esper phases away from the
 * attacker with a moment of untouchability;</li>
 * <li>vector, reflection field: projectiles are turned back at their shooter for a small shield cost; melee attackers are thrown
 * back.</li>
 * <li>mental out, mind ward: a melee attacker loses its target for a moment;</li>
 * <li>aero hand, pressure barrier: projectiles are blown aside for a small shield cost; melee attackers are blown back;</li>
 * <li>float dial, buoyancy cushion: fall damage is cushioned for a small shield cost; when the shield breaks the esper floats free;</li>
 * <li>nitrogen armor, nitrogen shell: melee attackers are pressure-shoved; with the Nitrogen Armor skill up each absorbed hit
 * refills a little shield;</li>
 * <li>dark matter, feather veil: feathers catch projectiles for a small shield cost; a breaking veil bursts feathers outward;</li>
 * <li>hydro hand, water film: the esper is put out, and melee attackers are soaked and slowed;</li>
 * <li>paper psychokinesis, paper mail: sheets catch projectiles for a small shield cost; melee attackers get a paper cut;</li>
 * <li>liquid shadow, shadow veil: melee attackers are blinded and slowed.</li>
 * </ul>
 * Traits run after the damage transaction (next server tick), never inside the ledger's own commit.
 */
@EventBusSubscriber(modid = "academy")
public final class EsperShields {
    /** Fraction of the maximum shield a vaporised or reflected projectile costs. */
    public static final BigDecimal PROJECTILE_COST = new BigDecimal("0.04");
    /** Fraction of the maximum shield a lightning strike restores on an electromaster. */
    public static final BigDecimal LIGHTNING_RECHARGE = new BigDecimal("0.25");
    /** Fraction of the maximum shield an absorbed hit refills while the Nitrogen Armor skill is up. */
    public static final BigDecimal SHELL_REFILL = new BigDecimal("0.01");
    private static final double MELEE_REACH = 6;
    private record Hit(ServerPlayer player, ServerLevel level, String category, Entity attacker, Entity direct, boolean broke, DamageSource source, boolean suppressPhase) {}
    private static final List<Hit> PENDING = new ArrayList<>();

    private EsperShields() {}

    public static boolean eligible(LivingEntity entity) {
        return entity instanceof ServerPlayer player && !EsperLegacyBypass.standAside() && EsperProtectionConfig.categoryShields()
                && HighPowerCombat.enabled(player) && HighPowerCategories.active(AbilityStorage.get(player));
    }

    /** True while an eligible esper still has shield left: super armor. */
    public static boolean holding(LivingEntity entity) {
        return eligible(entity) && HighPowerCombat.shield(entity).signum() > 0;
    }

    /** Called by the ledger after the base shield absorbed part of an ordinary hit. */
    static void absorbed(ServerPlayer player, DamageSource source, boolean broke) {
        if (source instanceof AbilityDamage.SkillDamageSource skill && skill.retaliation) return;
        PENDING.add(new Hit(player, player.serverLevel(), AbilityStorage.get(player).category, source == null ? null : source.getEntity(),
                source == null ? null : source.getDirectEntity(), broke, source, false));
    }

    /** Only this exact successful native hit loses its optional random phase step. */
    static void suppressPhaseForEscape(ServerPlayer player, DamageSource source) {
        for(int i=0;i<PENDING.size();i++) {
            Hit h=PENDING.get(i);
            if(h.player()==player&&h.source()==source)
                PENDING.set(i,new Hit(h.player(),h.level(),h.category(),h.attacker(),h.direct(),h.broke(),h.source(),true));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void knockback(LivingKnockBackEvent event) {
        if (!event.isCanceled() && holding(event.getEntity())) event.setCanceled(true);
    }

    /** Electromasters drink lightning into the barrier. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void lightning(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !event.getSource().is(DamageTypes.LIGHTNING_BOLT) || !eligible(event.getEntity())) return;
        var player = (ServerPlayer) event.getEntity();
        if (!HighPowerCategories.isElectromaster(AbilityStorage.get(player).category)) return;
        event.setCanceled(true);
        HighPowerCombat.rechargeShieldFraction(player, LIGHTNING_RECHARGE);
        burst(player, spark(), 24, .8, SoundEvents.BEACON_POWER_SELECT, 1.4F);
    }

    /** Float Dial cushions falls into the shield instead of health. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void fall(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !event.getSource().is(DamageTypes.FALL) || !holding(event.getEntity())) return;
        var player = (ServerPlayer) event.getEntity();
        if (!"float_dial".equals(AbilityStorage.get(player).category) || !HighPowerCombat.spendShieldFraction(player, PROJECTILE_COST)) return;
        event.setCanceled(true);
        burst(player, bubble(), 18, .8, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.3F);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void projectile(ProjectileImpactEvent event) {
        if (event.isCanceled() || !(event.getRayTraceResult() instanceof EntityHitResult hit) || !holding(hit.getEntity() instanceof LivingEntity living ? living : null)) return;
        var player = (ServerPlayer) hit.getEntity();
        Projectile projectile = event.getProjectile();
        if (projectile.getOwner() == player) return;
        String category = AbilityStorage.get(player).category;
        boolean melt = HighPowerCategories.isMeltdowner(category), reflect = HighPowerCategories.isVector(category);
        boolean catchIt = "dark_matter".equals(category) || "paper_psychokinesis".equals(category), blow = "aero_hand".equals(category);
        if (!melt && !reflect && !catchIt && !blow) return;
        // Authenticated, current physical plate contact gets its native hurt transaction first.
        // An active controller, stale evidence, a gap, a broken facet or unsupported projectile is insufficient.
        if (melt && projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
                && (ZeroGapArmorArrowReceipts.pendingContact(player, arrow, event.getRayTraceResult())
                    || DissipationCurtainArrowReceipts.pendingContact(player, arrow, event.getRayTraceResult())
                    || ScatterPlateArrowReceipts.pendingContact(player, arrow, event.getRayTraceResult()))) return;
        if (reflect && projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
                && (MultiAxisReflectionArrowBridge.pendingContact(player, arrow, event.getRayTraceResult())
                    || AutonomousVectorArrowReceipts.pendingCircleContact(player, arrow, event.getRayTraceResult())
                    || AutonomousVectorArrowReceipts.pendingParryContact(player, arrow, event.getRayTraceResult()))) return;
        if (!HighPowerCombat.spendShieldFraction(player, PROJECTILE_COST)) return; // not enough left: the projectile lands normally
        event.setCanceled(true);
        var level = (ServerLevel) player.level();
        Vec3 at = projectile.position();
        if (catchIt) {
            boolean dark = "dark_matter".equals(category);
            level.sendParticles(dark ? white() : paper(), at.x, at.y, at.z, 14, .2, .2, .2, .03);
            if (dark) level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 4, .1, .1, .1, .02);
            level.playSound(null, at.x, at.y, at.z, dark ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, .7F, 1.5F);
            projectile.discard();
        } else if (blow) {
            Vec3 v = projectile.getDeltaMovement(), side = new Vec3(-v.z, 0, v.x);
            if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
            projectile.setDeltaMovement(side.normalize().scale(Math.max(.6, v.length() * .8)).add(0, .25, 0));
            projectile.hasImpulse = true;
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 8, .15, .15, .15, .05);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, .7F, 1.3F);
        } else if (melt) {
            level.sendParticles(green(), at.x, at.y, at.z, 14, .15, .15, .15, .02);
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, .1, .1, .1, .01);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, .6F, 1.6F);
            projectile.discard();
        } else {
            Entity shooter = projectile.getOwner();
            Vec3 back = shooter != null ? shooter.getEyePosition().subtract(at).normalize() : projectile.getDeltaMovement().scale(-1).normalize();
            double speed = Math.max(.8, projectile.getDeltaMovement().length());
            projectile.setOwner(player);
            projectile.setDeltaMovement(back.scale(speed * 1.2));
            projectile.hasImpulse = true;
            level.sendParticles(white(), at.x, at.y, at.z, 10, .12, .12, .12, .05);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, .7F, 1.5F);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        var hits = List.copyOf(PENDING);
        PENDING.clear();
        for (var hit : hits) {
            try { trait(hit); }
            catch (RuntimeException failure) { /* a trait never breaks the server tick */ }
        }
    }

    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { PENDING.clear(); }

    private static void trait(Hit hit) {
        ServerPlayer player = hit.player();
        if (!player.isAlive() || player.isRemoved() || player.hasDisconnected() || player.serverLevel() != hit.level()
                || !eligible(player) || !AbilityStorage.get(player).category.equals(hit.category())) return;
        LivingEntity attacker = hit.attacker() instanceof LivingEntity living && living != player && living.isAlive()
                && living.level() == player.level() && living.distanceTo(player) <= MELEE_REACH && hit.direct() == hit.attacker() ? living : null;
        if (attacker instanceof net.minecraft.world.entity.player.Player other
                && (!cn.academy.port.AcademyConfig.ATTACK_PLAYERS.get() || !player.server.isPvpAllowed() || !player.canHarmPlayer(other))) attacker = null;
        switch (hit.category()) {
            case HighPowerCategories.ELECTROMASTER -> {
                burst(player, spark(), hit.broke() ? 40 : 12, .7, hit.broke() ? SoundEvents.GLASS_BREAK : SoundEvents.AMETHYST_BLOCK_HIT, 1.8F);
                if (attacker != null) {
                    arc(player, attacker);
                    AbilityDamage.shieldCounter(player, "electromaster.arc_gen", attacker, 6);
                    attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), player);
                }
            }
            case HighPowerCategories.MELTDOWNER -> {
                burst(player, green(), hit.broke() ? 40 : 12, .7, hit.broke() ? SoundEvents.GLASS_BREAK : SoundEvents.FIRECHARGE_USE, 1.6F);
                if (attacker != null) {
                    attacker.igniteForSeconds(4);
                    AbilityDamage.shieldCounter(player, "meltdowner.electron_bomb", attacker, 5);
                }
            }
            case HighPowerCategories.TELEPORTER -> {
                burst(player, ParticleTypes.REVERSE_PORTAL, hit.broke() ? 40 : 10, .6, hit.broke() ? SoundEvents.GLASS_BREAK : SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F);
                if (!hit.suppressPhase() && !EmergencyReturn.automaticPending(player) && (hit.broke() || player.getRandom().nextFloat() < .3F)) {
                    if (phaseAside(player, hit.attacker(), hit.broke() ? 4.5 : 2.5) && hit.broke()) player.invulnerableTime = Math.max(player.invulnerableTime, 30);
                }
            }
            case HighPowerCategories.VECMANIP -> {
                burst(player, white(), hit.broke() ? 40 : 12, .7, hit.broke() ? SoundEvents.GLASS_BREAK : SoundEvents.SHIELD_BLOCK, 1.3F);
                if (attacker != null) {
                    Vec3 away = attacker.position().subtract(player.position()).multiply(1, 0, 1).normalize();
                    attacker.knockback(1.6, -away.x, -away.z);
                    attacker.hurtMarked = true;
                    AbilityDamage.shieldCounter(player, "vecmanip.dir_shock", attacker, 4);
                }
            }
            case "mental_out" -> {
                burst(player, pink(), hit.broke() ? 40 : 12, .7, hit.broke() ? SoundEvents.GLASS_BREAK : SoundEvents.AMETHYST_CLUSTER_HIT, 1.7F);
                if (attacker != null) {
                    if (attacker instanceof net.minecraft.world.entity.Mob mob) cn.academy.port.canon.CanonSkillsCombatA.wardDisrupt(player, mob, 40);
                    attacker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0), player);
                    attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), player);
                    AbilityDamage.shieldCounter(player, "mental_out.mental_disrupt", attacker, 3);
                }
            }
            case "aero_hand" -> {
                burst(player, ParticleTypes.CLOUD, hit.broke() ? 30 : 10, .7, hit.broke() ? SoundEvents.GLASS_BREAK : SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4F);
                if (attacker != null) {
                    Vec3 away = attacker.position().subtract(player.position()).multiply(1, 0, 1).normalize();
                    attacker.knockback(1.3, -away.x, -away.z); attacker.hurtMarked = true;
                    AbilityDamage.shieldCounter(player, "aero_hand.aero_push", attacker, 3);
                }
            }
            case "float_dial" -> {
                burst(player, bubble(), hit.broke() ? 40 : 12, .7, hit.broke() ? SoundEvents.GLASS_BREAK : SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.4F);
                if (hit.broke()) {
                    player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 15, 1, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
                    player.invulnerableTime = Math.max(player.invulnerableTime, 20);
                }
            }
            case "nitrogen_armor" -> {
                burst(player, ice(), hit.broke() ? 40 : 12, .75, hit.broke() ? SoundEvents.GLASS_BREAK : SoundEvents.POWDER_SNOW_HIT, 1.2F);
                if (attacker != null) {
                    Vec3 away = attacker.position().subtract(player.position()).multiply(1, 0, 1).normalize();
                    attacker.knockback(1.1, -away.x, -away.z); attacker.hurtMarked = true;
                    AbilityDamage.shieldCounter(player, "nitrogen_armor.nitrogen_shove", attacker, 4);
                }
                if (!hit.broke() && cn.academy.port.canon.CanonSkillsCombatA.ownsNitrogenShell(player)) HighPowerCombat.rechargeShieldFraction(player, SHELL_REFILL);
            }
            case "dark_matter" -> {
                burst(player, white(), hit.broke() ? 40 : 12, .75, hit.broke() ? SoundEvents.GLASS_BREAK : SoundEvents.AMETHYST_BLOCK_HIT, 1.6F);
                if (hit.broke()) {
                    burst(player, ParticleTypes.END_ROD, 30, 1.6, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F);
                    for (var near : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3), e -> e != player && e.isAlive() && e instanceof net.minecraft.world.entity.monster.Enemy)) {
                        Vec3 away = near.position().subtract(player.position()).multiply(1, 0, 1).normalize();
                        near.knockback(1.0, -away.x, -away.z); near.hurtMarked = true;
                    }
                }
            }
            case "hydro_hand" -> {
                burst(player, ParticleTypes.SPLASH, hit.broke() ? 40 : 14, .7, hit.broke() ? SoundEvents.GLASS_BREAK : SoundEvents.PLAYER_SPLASH, 1.3F);
                player.clearFire();
                if (attacker != null) {
                    attacker.clearFire();
                    attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), player);
                    AbilityDamage.shieldCounter(player, "hydro_hand.hydro_whip", attacker, 3);
                }
            }
            case "paper_psychokinesis" -> {
                burst(player, paper(), hit.broke() ? 40 : 12, .7, hit.broke() ? SoundEvents.GLASS_BREAK : SoundEvents.BOOK_PAGE_TURN, 1.3F);
                if (attacker != null) AbilityDamage.shieldCounter(player, "paper_psychokinesis.paper_blade", attacker, 4);
            }
            case "liquid_shadow" -> {
                burst(player, ParticleTypes.SQUID_INK, hit.broke() ? 36 : 12, .7, hit.broke() ? SoundEvents.GLASS_BREAK : SoundEvents.INK_SAC_USE, 1.0F);
                if (attacker != null) {
                    attacker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), player);
                    attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), player);
                    AbilityDamage.shieldCounter(player, "liquid_shadow.liquid_strike", attacker, 3);
                }
            }
            default -> {}
        }
    }

    /** Short safe shift away from the attacker (or backward), onto solid ground, never into blocks. */
    private static boolean phaseAside(ServerPlayer player, Entity attacker, double distance) {
        var level = (ServerLevel) player.level();
        Vec3 from = player.position();
        Vec3 away = attacker != null ? from.subtract(attacker.position()).multiply(1, 0, 1) : player.getLookAngle().multiply(-1, 0, -1);
        if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
        away = away.normalize();
        Vec3 side = new Vec3(-away.z, 0, away.x);
        Vec3[] tries = {away, away.add(side).normalize(), away.subtract(side).normalize(), side, side.scale(-1)};
        for (Vec3 dir : tries) {
            for (double d = distance; d >= 1.5; d -= .5) {
                Vec3 to = from.add(dir.scale(d));
                for (int dy = 1; dy >= -2; dy--) {
                    Vec3 at = new Vec3(to.x, Math.floor(to.y) + dy, to.z);
                    var box = player.getBoundingBox().move(at.subtract(from));
                    var block = net.minecraft.core.BlockPos.containing(at);
                    if (!level.hasChunkAt(block) || !level.getWorldBorder().isWithinBounds(box)
                            || !level.noCollision(player, box) || level.noCollision(player, box.move(0, -.2, 0))
                            || level.containsAnyLiquid(box) || level.getBlockState(block.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                            || level.getBlockState(block.below()).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                            || level.getBlockState(block.below()).is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)) continue;
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 24, .3, .6, .3, .05);
                    player.teleportTo(at.x, at.y, at.z);
                    level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 24, .3, .6, .3, .2);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, .5F, 1.6F);
                    return true;
                }
            }
        }
        return false;
    }

    private static void arc(ServerPlayer player, LivingEntity target) {
        var level = (ServerLevel) player.level();
        Vec3 a = player.position().add(0, player.getBbHeight() * .6, 0), b = target.position().add(0, target.getBbHeight() * .5, 0);
        for (int i = 0; i <= 12; i++) {
            Vec3 p = a.lerp(b, i / 12.0).add((player.getRandom().nextDouble() - .5) * .25, (player.getRandom().nextDouble() - .5) * .25, (player.getRandom().nextDouble() - .5) * .25);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, b.x, b.y, b.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, .35F, 1.9F);
    }

    /** A shell of particles around the esper, the visible shape of the category shield. */
    private static void burst(ServerPlayer player, ParticleOptions particle, int count, double radius, SoundEvent sound, float pitch) {
        var level = (ServerLevel) player.level();
        Vec3 c = player.position().add(0, player.getBbHeight() * .55, 0);
        for (int i = 0; i < count; i++) {
            double u = player.getRandom().nextDouble() * 2 - 1, t = player.getRandom().nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            level.sendParticles(particle, c.x + radius * s * Math.cos(t), c.y + radius * u * 1.3, c.z + radius * s * Math.sin(t), 1, 0, 0, 0, 0);
        }
        level.playSound(null, c.x, c.y, c.z, sound, SoundSource.PLAYERS, .6F, pitch);
    }

    private static ParticleOptions spark() { return new DustParticleOptions(new Vector3f(.55F, .85F, 1F), 1F); }
    private static ParticleOptions green() { return new DustParticleOptions(new Vector3f(.6F, 1F, .65F), 1F); }
    private static ParticleOptions white() { return new DustParticleOptions(new Vector3f(.95F, .95F, 1F), 1F); }
    private static ParticleOptions pink() { return new DustParticleOptions(new Vector3f(1F, .55F, .82F), 1F); }
    private static ParticleOptions bubble() { return new DustParticleOptions(new Vector3f(.68F, 1F, .74F), 1F); }
    private static ParticleOptions ice() { return new DustParticleOptions(new Vector3f(.62F, .82F, 1F), 1F); }
    private static ParticleOptions paper() { return new DustParticleOptions(new Vector3f(1F, .9F, .68F), 1F); }
}
