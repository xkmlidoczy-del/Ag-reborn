package cn.academy.biohum;

import static cn.academy.biohum.Payloads.Cue.*;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/** Named server actions. Shared operations (such as a ray query) do not own the skill's rules. */
public final class CombatSkills {
    private static final String WISH = "acabiohum.new_world_wish";
    private static final String RETURN_POINT = "BioHumReturn";
    private static final String DIVINE_UNTIL = "BioHumDivinePunishmentUntil";
    private static final ResourceKey<Level> NEW_WORLD = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(BioHum.ID, "new_world"));
    private static final Set<String> NETWORK = Set.of("misaka_10032", "last_order", "misaka_worst");

    static Vec3 point(ServerPlayer player, double range) {
        if (range <= 0) return player.position();
        Vec3 from = player.getEyePosition();
        return SkillTrajectory.surface(player.serverLevel(), from, from.add(player.getLookAngle().scale(range))).position();
    }

    static boolean start(HumNpc npc, Skills.Skill skill, LivingEntity target, Vec3 point, ServerPlayer player) {
        Vec3 aim = aim(npc, skill, target, point);
        UUID issuer = player == null ? null : player.getUUID();
        if (!eligible(npc, skill, target, aim, issuer)) return false;
        if (skill.windup() > 0 && !npc.windup(skill.id(), target, skill.windup())) return false;
        npc.beginTechnique(skill.id());
        if(skill.target()!=Skills.Target.SELF)npc.faceCast(aim);
        npc.setReady(skill.id(), npc.level().getGameTime() + skill.cooldown());
        npc.expression(skill.combat() ? 2 : 1);
        if (skill.combat()) npc.getNavigation().stop();
        if (target != null) npc.getLookControl().setLookAt(target, 60, 45);
        if (skill.windup() > 0) {
            npc.aimPending(skill.action() == Skills.Action.CARRY
                    ? point == null ? null : aim(npc, skill, null, point)
                    : aim, player);
            return true;
        }
        return finish(npc, skill, target, aim, issuer);
    }

    private static Vec3 aim(HumNpc npc, Skills.Skill skill, LivingEntity target, Vec3 point) {
        if (target != null) return target.getBoundingBox().getCenter();
        Vec3 from = npc.position();
        Vec3 goal = point == null ? from.add(npc.getLookAngle().scale(skill.range())) : point;
        Vec3 delta = goal.subtract(from);
        return delta.lengthSqr() <= skill.range() * skill.range() ? goal
                : from.add(delta.normalize().scale(skill.range()));
    }

    private static boolean eligible(HumNpc npc, Skills.Skill skill, LivingEntity target, Vec3 aim, UUID issuer) {
        var world = (ServerLevel) npc.level();
        boolean present = target != null && target.isAlive() && target.level() == npc.level()
                && npc.distanceToSqr(target) <= skill.range() * skill.range();
        if (skill.target() == Skills.Target.ENEMY && (!present || !npc.validTarget(target))) return false;
        if (skill.target() == Skills.Target.LIVING && !present) return false;
        if (skill.target() == Skills.Target.ALLY && present && target != npc && !npc.ally(target)) return false;
        if (skill.target() == Skills.Target.POINT && !WorldEffects.inWorld(world, BlockPos.containing(aim))) return false;
        return switch (skill.action()) {
            case SPECIAL -> CharacterArts.allows(npc,skill,target,aim);
            case RAIL -> has(npc, Items.IRON_NUGGET);
            case PHASE_WORLD -> OthinusRewrite.canCast(npc,skill,aim,issuer);
            case GUNGNIR -> OthinusRewrite.spearAvailable(npc);
            case DEVICE -> !skill.id().equals("othinus_mimir_eye") ||
                    npc.phaseId().equals("nt10_fading") && nearby(npc,"touma",9)
                    && world.getFluidState(BlockPos.containing(aim)).isSource()
                    && world.getFluidState(BlockPos.containing(aim)).is(net.minecraft.tags.FluidTags.WATER);
            case SHOT -> has(npc, BioHum.CARTRIDGE.get());
            case RELOAD -> has(npc, BioHum.PISTOL.get()) && cartridgeSource(npc, issuer) != null;
            case DISRUPT -> target instanceof HumNpc other && other.casting().equals("wind_plasma")
                    && networkNear(npc, target, skill.range());
            case TRACK -> (target instanceof HumNpc other && other.profile().esper()
                    || npc.getPersistentData().hasUUID("AimSubject")) && npc.crystals > 0 && npc.getHealth() > 4;
            case DEEP_BLOOD -> target != null && target.getTags().contains("acabiohum.vampire")
                    && !npc.getPersistentData().getBoolean("BioHumCrossSealed");
            case MIND -> target instanceof HumNpc other && other != npc && !other.controlled()
                    && !Set.of("mikoto", "accelerator", "touma", "othinus").contains(other.characterId);
            case DIVINE -> target != null && !divineSuppressed(target)
                    && (target instanceof Mob mob && mob.getTarget() == npc
                    || target == npc.getLastHurtByMob() && npc.tickCount-npc.getLastHurtByMobTimestamp()<=80);
            case SAINT_BREAK -> target instanceof HumNpc saint && saint.characterId.equals("acqua")
                    && nearby(npc, "tatemiya", skill.radius()) && nearby(npc, "kanzaki", skill.radius());
            case BANISH -> target != null && target.getTags().contains(WISH) && !target.isPassenger()
                    && !target.isVehicle() && world.getServer().getLevel(NEW_WORLD) != null && !world.dimension().equals(NEW_WORLD);
            case RETURN -> (target == null ? npc : target).getPersistentData().contains(RETURN_POINT);
            case REFRACT -> skill.id().equals("lotus_wand")
                    ? present&&npc.validTarget(target)&&has(npc, Items.BLAZE_ROD)
                    : target!=null&&npc.hasLineOfSight(target)
                    && (has(npc, Items.OBSIDIAN) && world.canSeeSky(BlockPos.containing(npc.getEyePosition()))
                    && world.getSkyDarken() < 6);
            case COPY -> nearbyEsper(npc, skill.radius()) != null && present;
            case RITUAL -> has(npc, BioHum.RELIC.get()) && nearby(npc, "sasha", skill.radius());
            case ARS_MAGNA -> has(npc, Items.GOLD_NUGGET) && (target == null || npc.hasLineOfSight(target));
            case CROSS_ALIGN -> has(npc,BioHum.RELIC.get())&&world.canSeeSky(BlockPos.containing(aim).above());
            case CROSS -> crossReady(npc,world);
            case IRON_COLOSSUS -> npc.getOffhandItem().is(Items.IRON_NUGGET)&&npc.getOffhandItem().getCount()>=8;
            case SKY_STRIKE -> present&&world.canSeeSky(target.blockPosition().above(2));
            case PRESSURE -> !skill.id().equals("amazing_punch") || npc.hasLineOfSight(target);
            case WIRE, BLADE, STRIKE, FEINT, GRAPPLE, SEAL -> target != null && npc.hasLineOfSight(target);
            default -> true;
        };
    }

    static boolean finish(HumNpc npc, Skills.Skill skill, LivingEntity target, Vec3 point, UUID issuer) {
        var world = (ServerLevel) npc.level();
        Vec3 at = aim(npc, skill, target, point);
        if (!eligible(npc, skill, target, at, issuer) || skill.action() == Skills.Action.ARS_MAGNA && npc.windDisrupted()) {
            Vec3 origin=CastMotion.release(npc,skill,npc.castYaw());
            cue(npc, skill, CANCEL, origin, origin, 1);
            npc.cancelTechnique();
            return false;
        }
        var edit = WorldEffects.Source.of(npc, issuer);
        Vec3 hand = hand(npc, at);
        double radius = skill.radius();
        int duration = skill.duration();
        boolean result = switch (skill.action()) {
            case RAIL -> {
                consume(npc, Items.IRON_NUGGET);
                SkillTrajectory.beam(npc, skill, hand, at, issuer, true, WorldEffects.Change.BREAK, false);
                yield true;
            }
            case RAY -> {
                if (skill.id().equals("meltdowner_sweep")) {
                    Vec3 forward = at.subtract(hand).normalize();
                    for (int i = -2; i <= 2; i++) {
                        Vec3 direction = forward.yRot(i * .095f);
                        SkillTrajectory.beam(npc, skill, hand, hand.add(direction.scale(skill.range())),
                                issuer, true, WorldEffects.Change.SCORCH, true);
                    }
                } else SkillTrajectory.beam(npc, skill, hand, at, issuer, true, WorldEffects.Change.SCORCH, true);
                if(skill.id().equals("shorthand_flare")){
                    WorldEffects.area(world,edit,ground(world,at),2,0,WorldEffects.Change.IGNITE,3,duration);
                }
                yield true;
            }
            case SHOT -> {
                consume(npc, BioHum.CARTRIDGE.get());
                SkillTrajectory.beam(npc, skill, hand, at, issuer, false, null, false);
                yield true;
            }
            case RELOAD -> {
                var source = cartridgeSource(npc, issuer);
                if (source == null) yield false;
                ItemStack from = source.getMainHandItem();
                int moved = Math.min(from.getCount(), BioHum.CARTRIDGE.get().getDefaultMaxStackSize() - npc.getOffhandItem().getCount());
                if (moved <= 0) yield false;
                npc.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(BioHum.CARTRIDGE.get(), npc.getOffhandItem().getCount() + moved));
                if (!source.isCreative()) from.shrink(moved);
                yield true;
            }
            case PLASMA -> {
                var ray = SkillTrajectory.beam(npc, skill, hand, at, issuer, false, WorldEffects.Change.SCORCH, true);
                burst(npc, skill, ray.end(), true, .95);
                WorldEffects.area(world, edit, ray.end(), radius, radius * .45, WorldEffects.Change.SCORCH, skill.power() / 9, duration);
                field(npc, skill, ground(world,ray.end()), radius);
                yield true;
            }
            case DISRUPT -> {
                if(!(target instanceof HumNpc other)||!other.interrupt())yield false;
                Payloads.cue(npc,skill.id(),BEAM,hand,at,.31f,-1,other.getUUID());
                yield true;
            }
            case WIRE -> {
                npc.sevenFlashes(target);
                yield true;
            }
            case BLADE -> {
                var hit = SkillTrajectory.beam(npc, skill, hand, at, issuer, false,
                        skill.id().equals("yuisen") ? WorldEffects.Change.BREAK : null, false);
                yield hit.hits() > 0 || hit.changed() > 0;
            }
            case STRIKE, FEINT -> {
                var damage = skill.action() == Skills.Action.FEINT
                        ? new net.minecraft.world.damagesource.DamageSource(
                            world.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(RosterMove.FEINT), npc)
                        : npc.damageSources().mobAttack(npc);
                boolean hurt = target.hurt(damage, (float) skill.power());
                if (hurt) {
                    shove(target, npc.position(), .3 + skill.power() / 24);
                    impact(npc, skill, at);
                    if(skill.id().equals("claw_disorder")){
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,duration,2),npc);
                        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,duration,1),npc);
                    }
                }
                yield hurt;
            }
            case ELECTRIC -> electric(npc, skill, at, target);
            case NETWORK -> network(npc, skill, target, issuer);
            case BLINK, CARRY -> move(npc, skill, target, point);
            case TRACK -> track(npc, skill, target, issuer);
            case ANALYZE, DEVICE, REPORT -> {
                if(skill.id().equals("blasting_rod")&&npc.suspended())npc.chamberSignal(1,75);
                if(skill.id().equals("othinus_mimir_eye")){
                    field(npc,skill,at,2.0);
                    yield npc.selectPhase("nt10_after");
                }
                yield report(npc, skill, target, at, issuer);
            }
            case ROUTE -> route(npc, skill, at, issuer);
            case SUPPLY -> supply(npc, issuer);
            case HEAL -> heal(npc, skill, target, issuer);
            case SHIELD, FORMATION -> support(npc, skill, target, issuer);
            case GRAPPLE -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 3), npc);
                shove(target, npc.position(), -.52);
                cue(npc, skill, BEAM, hand, at, .15f);
                yield true;
            }
            case FLY -> {
                Vec3 forward=Vec3.directionFromRotation(0,npc.castYaw());
                boolean leap=skill.id().equals("tail_leap");
                boolean extraction=skill.id().equals("flight_gear_step")&&npc.validTarget(npc.getTarget());
                if(extraction)forward= npc.position().subtract(npc.getTarget().position()).multiply(1,0,1).normalize();
                if(skill.id().equals("flight_gear_step")&&!world.noCollision(npc,
                        npc.getBoundingBox().expandTowards(forward.x*1.25,1.7,forward.z*1.25)))yield false;
                if(leap&&!world.noCollision(npc,npc.getBoundingBox().move(forward.scale(.75).add(0,.75,0))))yield false;
                if(skill.id().equals("white_wing_flight")||skill.id().equals("flight_gear_step"))
                    npc.spreadWings(skill.id(),duration);
                npc.push(forward.x*(leap?1.27:.8),leap?.69:.75,forward.z*(leap?1.27:.8));
                npc.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration));
                if(!leap)npc.addEffect(new MobEffectInstance(MobEffects.LEVITATION,Math.max(1,duration/5)));
                field(npc, skill, npc.position(), radius);
                yield true;
            }
            case RESTORE -> {
                if (skill.id().equals("material_body")) npc.spreadWings(skill.id(),duration);
                npc.heal((float) skill.power());
                npc.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration));
                field(npc, skill, npc.position(), radius);
                yield true;
            }
            case DEEP_BLOOD -> target.hurt(npc.damageSources().indirectMagic(npc, npc), (float) skill.power());
            case MIND -> {
                var other = (HumNpc) target;
                int instruction = skill.id().contains("retreat") ? 1 : skill.id().contains("assist") ? 2 : 0;
                if (skill.id().equals("heart_distance")) {
                    other.getPersistentData().putUUID("BioHumAffinity", npc.getUUID());
                    other.getPersistentData().putLong("BioHumAffinityUntil", world.getGameTime() + duration);
                }
                other.control(npc, duration, instruction);
                Payloads.cue(npc,skill.id(),IMPACT,other.getEyePosition(),other.getEyePosition(),.83f,instruction,other.getUUID());
                yield true;
            }
            case SEAL -> {
                boolean stopped = target instanceof HumNpc other && other.interrupt();
                if (target instanceof FlameFamiliar flame) { flame.discard(); stopped = true; }
                if (target instanceof RuneAnchor rune) { rune.discard(); stopped = true; }
                if (stopped) impact(npc, skill, at);
                yield stopped;
            }
            case SUMMON -> summon(npc, skill, at, target, issuer);
            case FORGE -> {
                WorldEffects.area(world, edit, ground(world, at).add(0, .6, 0), radius, 1.0,
                        skill.id().startsWith("carbon_")?WorldEffects.Change.CARBON:WorldEffects.Change.FORM,
                        skill.power(), duration);
                field(npc, skill, at, radius);
                yield true;
            }
            case ARS_MAGNA -> arsMagna(npc, skill, target, at, issuer);
            case DIVINE -> {
                divineSuppress(target, duration);
                field(npc, skill, target.position(), Math.max(1, radius));
                yield true;
            }
            case PRECEDENCE -> {
                Vec3 end = target.getBoundingBox().getCenter();
                var resultLine = SkillTrajectory.beam(npc, skill, hand, end, issuer, false, null, true);
                yield resultLine.hits() > 0;
            }
            case SAINT_BREAK -> saintBreak(npc, skill, (HumNpc) target);
            case MERCY -> {
                npc.getPersistentData().remove("BioHumMercySuppressed");
                npc.removeEffect(MobEffects.WEAKNESS);
                field(npc, skill, npc.position(), radius);
                yield true;
            }
            case HOLY_RIGHT -> holyRight(npc, skill, target, at, issuer);
            case SLICE -> slice(npc, skill, at, issuer);
            case TERRITORY -> {
                WorldEffects.region(world, npc.position(), radius, WorldEffects.RegionKind.TERRITORY, npc.getUUID(), duration);
                field(npc, skill, npc.position(), radius);
                yield true;
            }
            case PRESSURE -> {
                burst(npc, skill, at, true, .45);
                field(npc, skill, at, radius);
                yield true;
            }
            case BANISH -> banish(npc, skill, target);
            case RETURN -> returnFromExile(target == null ? npc : target);
            case UPHEAVE -> {
                Vec3 floor = ground(world, at);
                WorldEffects.area(world, edit, floor.add(0, Math.max(1, radius * .15), 0), radius,
                        Math.max(1, radius * .15), WorldEffects.Change.UPLIFT, skill.power(), duration);
                burst(npc, skill, floor, true, .8);
                field(npc, skill, floor, radius);
                yield true;
            }
            case WONDERLAND -> {
                WorldEffects.region(world, at, radius, WorldEffects.RegionKind.WONDERLAND, npc.getUUID(), duration);
                WorldEffects.area(world, edit, ground(world, at).add(0, 1, 0), radius, .5,
                        WorldEffects.Change.FORM, skill.power(), duration);
                field(npc, skill, at, radius);
                yield true;
            }
            case REWRITE, RITUAL -> {
                if (skill.action() == Skills.Action.RITUAL) consume(npc, BioHum.RELIC.get());
                WorldEffects.sky(world, skill.id().contains("dark") ? 18000 : 13000, true, duration);
                WorldEffects.region(world, at, radius, WorldEffects.RegionKind.WONDERLAND, npc.getUUID(), duration);
                if(skill.id().equals("bethlehem_star")){
                    Vec3 center=ground(world,at).add(0,10,0);
                    WorldEffects.fortress(world,edit,center,radius,skill.power(),duration);
                    field(npc,skill,center,radius);
                }else if(skill.id().equals("adikalika")){
                    Vec3 ground=ground(world,at);
                    WorldEffects.area(world,edit,ground,radius,4,WorldEffects.Change.BREAK,skill.power(),duration);
                    burst(npc,skill,ground,true,.85);
                }else WorldEffects.area(world,edit,ground(world,at).add(0,1,0),radius,.8,
                        WorldEffects.Change.SCORCH,skill.power(),duration);
                if(!skill.id().equals("bethlehem_star"))field(npc, skill, at, radius);
                yield true;
            }
            case REFRACT -> {
                if(skill.id().equals("lotus_wand")){
                    if(!target.hurt(npc.damageSources().indirectMagic(npc,npc),(float)skill.power()))yield false;
                    impact(npc,skill,target.getBoundingBox().getCenter());
                    yield true;
                }
                SkillTrajectory.beam(npc, skill, hand, at, issuer, false,WorldEffects.Change.SCORCH,true);
                yield true;
            }
            case ARMOR -> {
                npc.guardUntil = world.getGameTime() + duration;
                if (target != null && npc.validTarget(target) && npc.distanceTo(target) <= radius) {
                    target.hurt(npc.damageSources().mobAttack(npc), (float) skill.power());
                    shove(target, npc.position(), .7);
                }
                field(npc, skill, npc.position(), radius);
                yield true;
            }
            case COPY -> copy(npc, skill, target, issuer);
            case WINGS -> {
                npc.spreadWings(skill.id(), duration);
                npc.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration));
                if (target != null) {
                    SkillTrajectory.beam(npc, skill, hand, at, issuer, false, null, true);
                    shove(target, npc.position(), .9);
                } else npc.push(0, .8, 0);
                field(npc, skill, npc.position(), radius);
                yield true;
            }
            case DECOY -> {
                if (skill.id().equals("toy_recover")) {
                    var toys = world.getEntitiesOfClass(ToyCharge.class, npc.getBoundingBox().inflate(skill.range()), t -> t.belongsTo(npc));
                    toys.forEach(net.minecraft.world.entity.Entity::discard);
                    yield !toys.isEmpty();
                }
                Vec3 floor = ground(world, at);
                if (!WorldEffects.inWorld(world, BlockPos.containing(floor))) yield false;
                var charge = BioHum.TOY_CHARGE.get().create(world);
                charge.arm(npc, floor, skill, issuer);
                field(npc, skill, floor, radius);
                yield world.addFreshEntity(charge);
            }
            case COLD -> {
                WorldEffects.area(world, edit, at, radius, 3, WorldEffects.Change.FREEZE, skill.power(), duration);
                burst(npc, skill, at, false, 0);
                field(npc, skill, at, radius);
                yield true;
            }
            case TRIPLE -> triple(npc, skill, target);
            case SHADE -> {
                WorldEffects.region(world, at, radius, WorldEffects.RegionKind.SHADOW, npc.getUUID(), duration);
                field(npc, skill, at, radius);
                yield true;
            }
            case AWAKEN -> {
                if (skill.id().endsWith("_wings")) npc.spreadWings(skill.id(), duration);
                npc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 1));
                if (target != null && npc.validTarget(target)) {
                    SkillTrajectory.beam(npc, skill, hand, at, issuer, true, WorldEffects.Change.BREAK, true);
                }
                field(npc, skill, npc.position(), radius);
                yield true;
            }
            case THERMAL -> thermal(npc,skill,world,edit,at);
            case CROSS_ALIGN -> alignCross(npc,skill,world,edit,at);
            case CROSS -> {
                var center=Vec3.atCenterOf(BlockPos.of(npc.getPersistentData().getLong("BioHumCrossPos")));
                WorldEffects.region(world,center,radius,WorldEffects.RegionKind.CROSS,npc.getUUID(),duration);
                field(npc,skill,center.add(0,-.47,0),radius);
                yield true;
            }
            case GUNGNIR -> {
                if(OthinusRewrite.gungnir(npc,skill,target))yield true;
                var ray=SkillTrajectory.beam(npc,skill,hand,at,issuer,false,WorldEffects.Change.BREAK,true);
                var crater=ray.end().add(0,-.5,0);
                WorldEffects.area(world,edit,crater,radius,2.2,WorldEffects.Change.BREAK,skill.power()/7,duration);
                field(npc,skill,crater,radius);
                yield true;
            }
            case PHASE_WORLD -> OthinusRewrite.cast(npc,skill,at,target,issuer);
            case IRON_COLOSSUS -> ironColossus(npc,skill,world,edit,at);
            case MANIFEST -> {
                WorldEffects.region(world,npc.position(),radius,WorldEffects.RegionKind.MANIFEST,npc.getUUID(),duration);
                npc.spreadWings(skill.id(),duration);
                yield report(npc,skill,target,at,issuer);
            }
            case VECTOR_THROW -> vectorThrow(npc,skill,world,edit,at);
            case VOICE -> {
                boolean changed=false;
                for(var other:world.getEntitiesOfClass(HumNpc.class,new AABB(at,at).inflate(radius),
                        victim->victim!=npc&&victim.isAlive()&&npc.distanceTo(victim)<=skill.range()
                                &&victim.distanceToSqr(at)<=radius*radius&&npc.hasLineOfSight(victim))){
                    var active=Skills.get(other.casting());
                    if(active==null||other.getTags().contains("acabiohum.hearing_protected")
                            ||!(active.prop().startsWith("rite")||active.action()==Skills.Action.RITUAL
                                ||active.action()==Skills.Action.VOICE))continue;
                    if(other.interrupt()){
                        Payloads.cue(npc,skill.id(),BEAM,hand,other.getEyePosition(),.23f,-1,other.getUUID());
                        changed=true;
                    }
                }
                if(changed)field(npc,skill,at.add(0,-.5,0),radius);
                yield changed;
            }
            case SKY_STRIKE -> {
                var above=at.add(0,Math.min(23,world.getMaxBuildHeight()-1-at.y),0);
                var strike=SkillTrajectory.beam(npc,skill,above,at,issuer,false,null,true);
                if(strike.hits()==0)yield false;
                var ground=ground(world,at);
                WorldEffects.area(world,edit,ground,radius*.65,0,WorldEffects.Change.IGNITE,4,duration);
                field(npc,skill,ground,radius);
                yield true;
            }
            case SPECIAL -> CharacterArts.apply(npc,skill,target,at,hand,issuer);
            case CLOSE, LEGACY -> throw new IllegalStateException("Skill dispatched outside its combat path");
        };
        if(result)npc.resolved(skill.id());
        Vec3 visualOrigin=skill.action()==Skills.Action.SKY_STRIKE
                ?at.add(0,Math.min(23,world.getMaxBuildHeight()-1-at.y),0):hand;
        cue(npc,skill,result?RELEASE:CANCEL,visualOrigin,at,1);
        return result;
    }

    public static boolean reflects(HumNpc npc) {
        if(npc.level() instanceof ServerLevel level
                && WorldEffects.inside(level,npc.position(),WorldEffects.RegionKind.MANIFEST))return false;
        if (!npc.characterId.equals("accelerator") || npc.controlled()) return false;
        return npc.phaseId().equals(npc.profile().phase()) || npc.guardUntil > npc.level().getGameTime();
    }

    static boolean available(HumNpc npc,Skills.Skill skill,LivingEntity target,Vec3 point) {
        return eligible(npc,skill,target,aim(npc,skill,target,point),null);
    }

    static Vec3 hand(HumNpc npc, Vec3 at) {
        var skill=Skills.get(npc.technique());
        if(skill!=null)return CastMotion.release(npc,skill,npc.castYaw());
        Vec3 shoulder = npc.position().add(0, npc.getBbHeight() * .68, 0);
        Vec3 facing = at.subtract(shoulder).normalize();
        return shoulder.add(facing.scale(.38 * npc.phaseScale()))
                .add(facing.cross(new Vec3(0, 1, 0)).scale(.23 * npc.phaseScale()));
    }

    static void wire(HumNpc npc, LivingEntity target, int wire) {
        var skill = Skills.get("seven_flash");
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 side = center.subtract(npc.position()).cross(new Vec3(0, 1, 0)).normalize();
        long cast=npc.wireStarted;
        float frame=Math.min(skill.length()-1,npc.level().getGameTime()-cast);
        Vec3 grip=CastMotion.world(npc,skill,CastMotion.aimed(npc,skill,frame),npc.castYaw());
        Vec3 from = grip.add(side.scale((wire - 4) * .024)).add(0, .012 * wire, 0);
        var aim=center.add(0,(wire-4)*.09,0);
        var stop=SkillTrajectory.surface((ServerLevel)npc.level(),from,
                from.add(aim.subtract(from).normalize().scale(Math.min(from.distanceTo(aim),skill.range()))));
        Payloads.cue((ServerLevel)npc.level(),npc.getUUID(),skill.id(),cast,BEAM,from,stop.position(),.10f,wire,target.getUUID());
        if(stop.block()!=null){cue(npc,skill,WALL,stop.position(),stop.position(),.35f);return;}
        var contact=target.getBoundingBox().inflate(.08).clip(from,stop.position()).orElse(null);
        if(contact==null)return;
        // The individual strands can hold their target even during normal damage invulnerability.
        if(target.hurt(npc.damageSources().mobAttack(npc),(float)skill.power()))
            Payloads.cue((ServerLevel)npc.level(),npc.getUUID(),skill.id(),cast,IMPACT,contact,contact,.52f,wire,target.getUUID());
        if (target instanceof HumNpc saint && saint.characterId.equals("acqua")) {
            saint.getPersistentData().putLong("BioHumSaintPinned", npc.level().getGameTime() + 50);
            saint.getNavigation().stop();
            saint.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 6), npc);
        }
    }

    private static boolean electric(HumNpc npc, Skills.Skill skill, Vec3 at, LivingEntity target) {
        var world = (ServerLevel) npc.level();
        if (target == null) return false;
        var primary = SkillTrajectory.beam(npc, skill, hand(npc, at), at, null, false, null, true);
        if (primary.hits() <= 0) return false;
        for (var other : world.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(skill.radius()),
                e -> e != target && npc.validTarget(e) && (e.isInWaterOrRain() || target.isInWaterOrRain()
                        || skill.id().equals("worst_discharge")) && target.hasLineOfSight(e))) {
            if (other.hurt(npc.damageSources().indirectMagic(npc, npc), (float) (skill.power() * .5))) {
                cue(npc, skill, BEAM, at, other.getBoundingBox().getCenter(), .16f);
            }
        }
        return true;
    }

    private static boolean network(HumNpc npc, Skills.Skill skill, LivingEntity target, UUID issuer) {
        var world = (ServerLevel) npc.level();
        boolean command=npc.characterId.equals("last_order")&&skill.id().equals("network_coordination");
        int connected = 0;
        for (var member : world.getEntitiesOfClass(HumNpc.class, npc.getBoundingBox().inflate(skill.range()),
                other -> other != npc && NETWORK.contains(other.characterId) && other.isAlive())) {
            connected++;
            member.getPersistentData().putLong("BioHumNetworkAt", world.getGameTime());
            boolean accepted=command&&member.characterId.equals("misaka_10032")&&member.mode()==3&&member.validTarget(target);
            if(accepted)member.setTarget(target);
            Payloads.cue(npc,skill.id(),MOVE,npc.getEyePosition(),member.getEyePosition(),.36f,
                    accepted?1:0,member.getUUID());
        }
        if (target != null) target.addEffect(new MobEffectInstance(MobEffects.GLOWING, skill.duration()), npc);
        var p = audience(npc, issuer);
        if (p != null) npc.tell(p, "御坂网络 · 已联络 " + connected + " 名成员");
        return connected > 0 || p != null || target != null;
    }

    private static boolean networkNear(HumNpc actor, LivingEntity near, double range) {
        return !actor.level().getEntitiesOfClass(HumNpc.class, near.getBoundingBox().inflate(range),
                ally -> ally.isAlive() && (actor.characterId.equals("mikoto")
                        ? ally.characterId.equals("misaka_10032") : ally.characterId.equals("mikoto"))).isEmpty();
    }

    private static boolean move(HumNpc npc, Skills.Skill skill, LivingEntity target, Vec3 point) {
        var level = (ServerLevel) npc.level();
        net.minecraft.world.entity.Entity moved = skill.action() == Skills.Action.BLINK ? npc : target;
        if (moved == null && skill.action() == Skills.Action.CARRY) {
            moved = level.getEntitiesOfClass(ItemEntity.class, npc.getBoundingBox().inflate(skill.radius()),
                    ItemEntity::isAlive).stream().min(Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
        }
        if (moved == null || moved.isPassenger() || moved.isVehicle()) return false;
        if (moved instanceof ServerPlayer player && !npc.ally(player)) return false;
        if (moved != npc && npc.distanceTo(moved) > skill.radius()) return false;
        Vec3 from = moved.position();
        Vec3 goal;
        if (skill.action() == Skills.Action.BLINK && target != null) {
            goal = target.position().add(Vec3.directionFromRotation(0, target.getYRot()).scale(-2.6));
            if(skill.id().equals("almighty_position")){
                goal=advantageousLanding(npc,level,target,skill.range(),goal);
                if(goal==null)return false;
            }
        } else if (point != null) goal = point;
        else goal = npc.position().add(Vec3.directionFromRotation(0, npc.getYRot()).scale(skill.range() * .4));
        Vec3 landing = landing(level, goal);
        if (!Abilities.teleport(moved, landing, false)) return false;
        double halfHeight=moved.getBbHeight()*.5;
        cue(npc, skill, MOVE, from.add(0,halfHeight,0),landing.add(0,halfHeight,0),(float)moved.getBbHeight());
        return true;
    }

    private static Vec3 advantageousLanding(HumNpc npc,ServerLevel level,LivingEntity target,double range,Vec3 behind){
        Vec3 toTarget=target.position().subtract(npc.position()).multiply(1,0,1).normalize();
        Vec3 side=toTarget.yRot((float)(Math.PI/2)).scale(2.6);
        for(Vec3 candidate:List.of(behind,target.position().add(side),target.position().subtract(side))){
            Vec3 stand=landing(level,candidate);
            if(npc.distanceToSqr(stand)<=range*range&&Abilities.safeDestination(npc,stand))return stand;
        }
        return null;
    }

    private static Vec3 landing(ServerLevel level, Vec3 goal) {
        var base=BlockPos.containing(goal);
        for (int y=0; y>=-4; y--) {
            var step=base.above(y);
            if (!WorldEffects.inWorld(level,step)) break;
            var state=level.getBlockState(step);
            if (!state.getCollisionShape(level,step).isEmpty()) break;
            if (level.getBlockState(step.below()).isSolid()) return new Vec3(goal.x,step.getY(),goal.z);
        }
        return goal;
    }

    private static boolean track(HumNpc npc, Skills.Skill skill, LivingEntity target, UUID issuer) {
        var level = (ServerLevel) npc.level();
        var memory = npc.getPersistentData();
        if (target == null && memory.hasUUID("AimSubject") && level.getEntity(memory.getUUID("AimSubject")) instanceof LivingEntity tracked)
            target = tracked;
        if (!(target instanceof HumNpc n) || !n.profile().esper()) return false;
        npc.crystals--;
        npc.hurt(npc.damageSources().generic(), 1);
        memory.putUUID("AimSubject", target.getUUID());
        memory.putLong("BioHumAimTrackedUntil",level.getGameTime()+skill.duration());
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, skill.duration()), npc);
        var p = audience(npc, issuer);
        if (p != null) npc.tell(p, "AIM 记录 · " + target.getName().getString() + " · " + target.blockPosition().toShortString());
        Payloads.cue(npc,skill.id(),MOVE,npc.getEyePosition(),target.getEyePosition(),.58f,-1,target.getUUID());
        return true;
    }

    private static boolean report(HumNpc npc, Skills.Skill skill, LivingEntity target, Vec3 point, UUID issuer) {
        var level = (ServerLevel) npc.level();
        var state = npc.getPersistentData();
        var p = audience(npc, issuer);
        var subject = target == null ? npc : target;
        state.putLong("BioHumObservedAt", level.getGameTime());
        state.putUUID("BioHumObservedSubject", subject.getUUID());
        state.putLong("BioHumObservedBlock", BlockPos.containing(point).asLong());
        if (skill.action() == Skills.Action.DEVICE) state.putString("BioHumDevice", skill.prop());
        if (skill.id().equals("cross_charm")) {
            state.putBoolean("BioHumCrossSealed", !state.getBoolean("BioHumCrossSealed"));
            if (p != null) npc.tell(p, state.getBoolean("BioHumCrossSealed") ? "十字架已封缚吸血杀手。" : "已解除封缚。");
        }
        if (skill.action() == Skills.Action.ANALYZE && target instanceof HumNpc caster) {
            if (p != null) npc.tell(p, skill.label() + " · " + caster.profile().name() + " · "
                    + (caster.casting().isEmpty() ? "当前无咏唱" : Abilities.label(caster.casting())));
        } else if (p != null) npc.tell(p, skill.label() + " · " + subject.getName().getString() + " · "
                + BlockPos.containing(point).toShortString() + " · " + Math.round(subject.getHealth()) + " HP");
        impact(npc, skill, subject.getEyePosition());
        return true;
    }

    private static boolean route(HumNpc npc, Skills.Skill skill, Vec3 at, UUID issuer) {
        var level = (ServerLevel) npc.level();
        Vec3 floor = ground(level, at);
        if (!Abilities.safeDestination(npc, floor)) return false;
        if (!npc.getNavigation().moveTo(floor.x, floor.y, floor.z, 1.05)) return false;
        npc.getPersistentData().putLong("BioHumRoute", BlockPos.containing(floor).asLong());
        var p = audience(npc, issuer);
        if (p != null) npc.tell(p, skill.label() + " · 路点 " + BlockPos.containing(floor).toShortString());
        cue(npc, skill, MOVE, npc.position(), floor, .48f);
        return true;
    }

    private static boolean supply(HumNpc npc, UUID issuer) {
        var p = audience(npc, issuer);
        if (p == null) return false;
        var carried = npc.getOffhandItem();
        if (!carried.isEmpty()) {
            var remaining = carried.copy();
            p.getInventory().add(remaining);
            if (remaining.getCount() == carried.getCount()) return false;
            npc.setItemSlot(EquipmentSlot.OFFHAND, remaining);
            npc.tell(p, "物品已交接。");
            return true;
        }
        var given = p.getMainHandItem();
        if (given.isEmpty()) return false;
        npc.setItemSlot(EquipmentSlot.OFFHAND, given.split(1));
        npc.tell(p, "物品已代管。");
        return true;
    }

    private static boolean thermal(HumNpc npc,Skills.Skill skill,ServerLevel level,WorldEffects.Source source,Vec3 at){
        BlockPos pos=BlockPos.containing(at);
        for(var candidate:List.of(pos,pos.below())){
            if(npc.position().distanceTo(Vec3.atCenterOf(candidate))<=skill.range()
                    &&WorldEffects.preserveTemperature(level,source,candidate,skill.duration())){
                field(npc,skill,Vec3.atCenterOf(candidate).add(0,.52,0),skill.radius());
                return true;
            }
        }
        return false;
    }

    private static boolean alignCross(HumNpc npc,Skills.Skill skill,ServerLevel level,WorldEffects.Source source,Vec3 at){
        var ground=BlockPos.containing(ground(level,at));
        if(!level.canSeeSky(ground)||!WorldEffects.edit(level,source,ground,WorldEffects.Change.RELIC,2))return false;
        consume(npc,BioHum.RELIC.get());
        var data=npc.getPersistentData();
        data.putLong("BioHumCrossPos",ground.asLong());
        data.putLong("BioHumCrossExpires",level.getGameTime()+skill.duration());
        field(npc,skill,Vec3.atBottomCenterOf(ground),2.2);
        return true;
    }

    private static boolean crossReady(HumNpc npc,ServerLevel level){
        var data=npc.getPersistentData();
        if(!data.contains("BioHumCrossPos")||data.getLong("BioHumCrossExpires")<=level.getGameTime())return false;
        var pos=BlockPos.of(data.getLong("BioHumCrossPos"));
        if(!WorldEffects.inWorld(level,pos)||!level.getBlockState(pos).is(Blocks.CHISELED_QUARTZ_BLOCK)
                ||!level.canSeeSky(pos.above())||level.isRaining())return false;
        long time=Math.floorMod(level.getDayTime(),24000L);
        return time>=13100&&time<=22800&&npc.position().distanceTo(Vec3.atCenterOf(pos))<=16
                &&!hasFirework(level, pos);
    }

    private static boolean hasFirework(ServerLevel level,BlockPos pos){
        return !level.getEntitiesOfClass(net.minecraft.world.entity.projectile.FireworkRocketEntity.class,
                new AABB(pos).inflate(26)).isEmpty();
    }

    static void checkCross(HumNpc npc,ServerLevel level){
        if(npc.getPersistentData().contains("BioHumCrossPos")&&!crossReady(npc,level)){
            WorldEffects.endRegion(level,npc.getUUID(),WorldEffects.RegionKind.CROSS);
        }
    }

    private static boolean ironColossus(HumNpc npc,Skills.Skill skill,ServerLevel level,WorldEffects.Source source,Vec3 at){
        Vec3 floor=ground(level,at);
        var figure=BioHum.CONSTRUCT.get().create(level);
        figure.arm(npc,skill.id(),floor,(float)skill.power(),skill.duration());
        if(!Abilities.safeDestination(figure,floor)||!level.addFreshEntity(figure))return false;
        npc.getOffhandItem().shrink(8);
        Vec3 heading=floor.subtract(npc.position()).multiply(1,0,1).normalize();
        if(heading.lengthSqr()==0)heading=npc.getLookAngle().multiply(1,0,1).normalize();
        var berm=floor.add(heading.scale(3));
        WorldEffects.area(level,source,berm,2,0,WorldEffects.Change.IRON,skill.power(),skill.duration());
        var nearest=level.getEntitiesOfClass(LivingEntity.class,figure.getBoundingBox().inflate(16),
                entity->entity!=figure&&entity instanceof net.minecraft.world.entity.monster.Monster&&npc.validTarget(entity))
                .stream().min(Comparator.comparingDouble(figure::distanceToSqr)).orElse(null);
        if(nearest!=null)figure.command(nearest,floor);
        field(npc,skill,floor,skill.radius());
        return true;
    }

    private static boolean vectorThrow(HumNpc npc,Skills.Skill skill,ServerLevel world,WorldEffects.Source source,Vec3 at){
        Vec3 forward=at.subtract(npc.position()).multiply(1,0,1).normalize();
        if(forward.lengthSqr()==0)return false;
        var start=BlockPos.containing(npc.position().add(forward.scale(2.4)));
        var impact=BlockPos.containing(ground(world,at));
        var offset=impact.subtract(start);
        if(offset.getX()*offset.getX()+offset.getZ()*offset.getZ()<20)return false;
        int width=(int)skill.radius();
        int moved=0;
        for(int y=0;y<=width;y++)for(int x=-width;x<=width;x++)for(int z=-1;z<=1;z++){
            if(x*x+z*z>width*width)continue;
            var block=start.offset(x,y,z);
            if(WorldEffects.moveBlock(world,source,block,block.offset(offset),skill.duration()))moved++;
        }
        if(moved==0)return false;
        cue(npc,skill,BEAM,Vec3.atCenterOf(start).add(0,1,0),Vec3.atCenterOf(impact).add(0,1,0),1.45f);
        cue(npc,skill,IMPACT,Vec3.atCenterOf(impact),Vec3.atCenterOf(impact),(float)Math.min(skill.radius(),1.2+moved*.18));
        burst(npc,skill,Vec3.atCenterOf(impact),true,.95);
        return true;
    }

    private static boolean heal(HumNpc npc, Skills.Skill skill, LivingEntity target, UUID issuer) {
        var p = audience(npc, issuer);
        var patient = target != null ? target : p == null ? npc : p;
        if (patient != p && patient != npc && !npc.ally(patient)) return false;
        if (npc.distanceTo(patient) > skill.range()) return false;
        patient.heal((float) skill.power());
        patient.addEffect(new MobEffectInstance(MobEffects.REGENERATION, skill.duration()), npc);
        if (skill.id().contains("triage") || skill.id().contains("rescue")) {
            patient.removeEffect(MobEffects.POISON);
            patient.removeEffect(MobEffects.WEAKNESS);
        }
        impact(npc, skill, patient.getEyePosition());
        return true;
    }

    private static boolean support(HumNpc npc, Skills.Skill skill, LivingEntity target, UUID issuer) {
        var level = (ServerLevel) npc.level();
        var p = audience(npc, issuer);
        var team = level.getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(skill.radius()),
                a -> npc.ally(a) || a == p);
        for (var member : team) {
            member.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, skill.duration()), npc);
            if (skill.action() == Skills.Action.FORMATION && member instanceof HumNpc other && other != npc
                    && other.getTarget() == null && other.distanceTo(npc) > 2) {
                other.getNavigation().moveTo(npc, 1.1);
            }
        }
        if (skill.id().equals("anti_skill_cover")) {
            npc.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            npc.startUsingItem(InteractionHand.OFF_HAND);
        }
        field(npc, skill, npc.position(), skill.radius());
        return true;
    }

    private static boolean summon(HumNpc npc, Skills.Skill skill, Vec3 at, LivingEntity target, UUID issuer) {
        var level = (ServerLevel) npc.level();
        Vec3 floor = ground(level, at);
        if (skill.id().startsWith("ellis_")) {
            for (var ellis : level.getEntitiesOfClass(Construct.class, npc.getBoundingBox().inflate(skill.range()),
                    e -> e.belongsTo(npc))) {
                ellis.command(target, floor);
                return true;
            }
        }
        var entity = BioHum.CONSTRUCT.get().create(level);
        if (!Abilities.safeDestination(entity, floor)) {
            Vec3 free = null;
            for (int r = 1; r <= 2 && free == null; r++) {
                for (int x = -r; x <= r && free == null; x++) for (int z = -r; z <= r; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != r) continue;
                    var candidate = ground(level, floor.add(x, 0, z));
                    if (Abilities.safeDestination(entity, candidate)) { free = candidate; break; }
                }
            }
            if (free == null) return false;
            floor = free;
        }
        entity.arm(npc, skill.id(), floor, (float) skill.power(), skill.duration());
        if (!level.addFreshEntity(entity)) return false;
        entity.command(target, floor);
        field(npc, skill, floor, skill.radius());
        return true;
    }

    private static boolean arsMagna(HumNpc npc, Skills.Skill skill, LivingEntity target, Vec3 at, UUID issuer) {
        var level = (ServerLevel) npc.level();
        consume(npc, Items.GOLD_NUGGET);
        if (skill.id().equals("ars_magna_shape")) {
            WorldEffects.area(level, WorldEffects.Source.of(npc, issuer), ground(level, at).add(0, 1, 0),
                    skill.radius(), .5, WorldEffects.Change.FORM, skill.power(), skill.duration());
            field(npc, skill, at, skill.radius());
            return true;
        }
        if (target == null) return false;
        if (target instanceof HumNpc other && other.characterId.equals("touma") && other.guardUntil > level.getGameTime()) {
            npc.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, skill.duration(), 2));
            cue(other, Skills.get("imagine_breaker"), CANCEL, other.rightHand(), other.rightHand(), 1);
            return false;
        }
        suppress(target, skill.duration());
        field(npc, skill, target.position(), skill.radius());
        return true;
    }

    private static boolean saintBreak(HumNpc npc, Skills.Skill skill, HumNpc saint) {
        Vec3 at = saint.getBoundingBox().getCenter();
        cue(npc, skill, BEAM, hand(npc, at), at, .62f);
        long now = npc.level().getGameTime();
        if (saint.getPersistentData().getLong("BioHumMercySuppressed") <= now) {
            cue(saint, Skills.get("saint_mercy"), CANCEL, at, at, 1.3f);
            return true;
        }
        if (saint.getPersistentData().getLong("BioHumSaintPinned") <= now) {
            cue(npc, skill, CANCEL, hand(npc, at), at, 1.1f);
            return false;
        }
        saint.getPersistentData().remove("BioHumSaintPinned");
        saint.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        saint.hurt(npc.damageSources().indirectMagic(npc, npc), (float) skill.power());
        suppress(saint, skill.duration());
        impact(npc, skill, at);
        return true;
    }

    private static boolean holyRight(HumNpc npc, Skills.Skill skill, LivingEntity target, Vec3 at, UUID issuer) {
        var level = (ServerLevel) npc.level();
        double force = (target.getMaxHealth() + target.getArmorValue() * 2.0) * skill.power();
        Vec3 origin = hand(npc,at);
        if (target.hurt(npc.damageSources().indirectMagic(npc, npc), (float) force)) {
            shove(target, npc.position(), Math.sqrt(force) / 7);
        }
        double radius = skill.radius() + Math.sqrt(force) / 5;
        WorldEffects.area(level, WorldEffects.Source.of(npc, issuer), target.position(), radius,
                1.4, WorldEffects.Change.BREAK, force / 5, skill.duration());
        cue(npc, skill, BEAM, origin, at, (float) (1 + radius * .25));
        impact(npc, skill, at);
        return true;
    }

    private static boolean slice(HumNpc npc, Skills.Skill skill, Vec3 at, UUID issuer) {
        var level = (ServerLevel) npc.level();
        if (!WorldEffects.inside(level, npc.position(), WorldEffects.RegionKind.TERRITORY)) return false;
        Vec3 from = hand(npc, at);
        Vec3 across = at.subtract(from).normalize().cross(new Vec3(0, 1, 0)).scale(skill.radius());
        Vec3 a = at.subtract(across), b = at.add(across);
        WorldEffects.plane(level, WorldEffects.Source.of(npc, issuer), a, b, skill.power(), skill.duration());
        burst(npc, skill, at, true, .5);
        cue(npc, skill, BEAM, a, b, .76f);
        return true;
    }

    private static boolean banish(HumNpc npc, Skills.Skill skill, LivingEntity target) {
        var origin = (ServerLevel) npc.level();
        var destination = origin.getServer().getLevel(NEW_WORLD);
        if (destination == null) return false;
        var saved = new CompoundTag();
        saved.putString("Dimension", origin.dimension().location().toString());
        saved.putDouble("X", target.getX());
        saved.putDouble("Y", target.getY());
        saved.putDouble("Z", target.getZ());
        target.getPersistentData().put(RETURN_POINT, saved);
        Vec3 exit = Vec3.atBottomCenterOf(destination.getSharedSpawnPos());
        if (exit.y < destination.getMinBuildHeight() + 5) exit = new Vec3(.5, destination.getMinBuildHeight() + 5, .5);
        Vec3 old = target.position();
        var moved = target.changeDimension(new DimensionTransition(destination, exit, Vec3.ZERO,
                target.getYRot(), target.getXRot(), DimensionTransition.PLACE_PORTAL_TICKET));
        if (moved == null) {
            target.getPersistentData().remove(RETURN_POINT);
            return false;
        }
        cue(npc, skill, MOVE, npc.rightHand(), old, 1.8f);
        return true;
    }

    public static boolean returnFromExile(LivingEntity target) {
        var origin = (ServerLevel) target.level();
        var saved = target.getPersistentData().getCompound(RETURN_POINT);
        var id = ResourceLocation.tryParse(saved.getString("Dimension"));
        var destination = id == null ? null : origin.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (destination == null) return false;
        var exit = new Vec3(saved.getDouble("X"), saved.getDouble("Y"), saved.getDouble("Z"));
        var pos = BlockPos.containing(exit);
        if (!destination.getWorldBorder().isWithinBounds(pos) || destination.isOutsideBuildHeight(pos)) return false;
        destination.getChunkAt(pos);
        if (!destination.noCollision(target.getBoundingBox().move(exit.subtract(target.position())))) return false;
        var returned = target.changeDimension(new DimensionTransition(destination, exit, Vec3.ZERO,
                target.getYRot(), target.getXRot(), DimensionTransition.PLACE_PORTAL_TICKET));
        if (returned == null) return false;
        returned.getPersistentData().remove(RETURN_POINT);
        return true;
    }

    private static HumNpc nearbyEsper(HumNpc npc, double range) {
        return npc.level().getEntitiesOfClass(HumNpc.class, npc.getBoundingBox().inflate(range),
                other -> other != npc && other.profile().esper() && other.isAlive()
                        && Set.of("mikoto", "misaka_10032", "misaka_worst", "mugino", "accelerator").contains(other.characterId))
                .stream().min(Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
    }

    private static boolean copy(HumNpc npc, Skills.Skill skill, LivingEntity target, UUID issuer) {
        var source = nearbyEsper(npc, skill.radius());
        if (source == null) return false;
        String copied = switch (source.characterId) {
            case "mugino" -> "meltdowner";
            case "accelerator" -> "vector_impact";
            default -> "electric_shock";
        };
        npc.getPersistentData().putString("BioHumCopiedAbility", copied);
        if (copied.equals("vector_impact")) {
            shove(target, npc.position(), 1.3);
            target.hurt(npc.damageSources().mobAttack(npc), (float) skill.power());
            cue(npc, skill, BEAM, npc.rightHand(), target.getEyePosition(), .34f);
        } else {
            var borrowed = Skills.get(copied);
            SkillTrajectory.beam(npc, borrowed, hand(npc, target.getEyePosition()), target.getEyePosition(), issuer,
                    copied.equals("meltdowner"), null, true);
            cue(npc, skill, MOVE, source.getEyePosition(), npc.getEyePosition(), .6f);
        }
        return true;
    }

    private static boolean triple(HumNpc npc, Skills.Skill skill, LivingEntity target) {
        var data = npc.getPersistentData();
        if (skill.id().equals("aradia_aid")) {
            if (target == null || !npc.ally(target) || target.getHealth() >= target.getMaxHealth()) return false;
            target.heal((float) skill.power());
            data.putInt("BioHumGoodDeeds", data.getInt("BioHumGoodDeeds") + 1);
            return true;
        }
        int deeds = data.getInt("BioHumGoodDeeds");
        if (deeds <= 0 || target == null) return false;
        data.remove("BioHumGoodDeeds");
        target.hurt(npc.damageSources().indirectMagic(npc, npc), (float) (skill.power() * deeds * 3));
        impact(npc, skill, target.getEyePosition());
        return true;
    }

    static boolean protect(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof HumNpc npc) || npc.level().isClientSide) return false;
        if (npc.characterId.equals("vento") && event.getSource().getEntity() instanceof LivingEntity hostile
                && npc.validTarget(hostile)) {
            long now = npc.level().getGameTime();
            if (npc.readyAt("divine_punishment") <= now) {
                divineSuppress(hostile, Skills.get("divine_punishment").duration());
                npc.setReady("divine_punishment", now + Skills.get("divine_punishment").cooldown());
                npc.beginTechnique("divine_punishment");
                field(npc, Skills.get("divine_punishment"), hostile.position(), 1);
            }
        }
        if (npc.characterId.equals("acqua") && event.getSource().is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)
                && npc.getPersistentData().getLong("BioHumMercySuppressed") <= npc.level().getGameTime()) {
            event.setCanceled(true);
            cue(npc, Skills.get("saint_mercy"), CANCEL, npc.getEyePosition(), npc.getEyePosition(), 1);
            return true;
        }
        return false;
    }

    static void suppress(LivingEntity target, int duration) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 7));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 4));
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        if (target instanceof HumNpc npc) npc.interrupt();
    }

    public static boolean divineSuppressed(LivingEntity target) {
        return !target.level().isClientSide && target.getPersistentData().getLong(DIVINE_UNTIL)>target.level().getGameTime();
    }

    private static void divineSuppress(LivingEntity target, int duration) {
        target.getPersistentData().putLong(DIVINE_UNTIL,target.level().getGameTime()+duration);
        target.stopUsingItem();
        suppress(target,duration);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,duration,9));
    }

    static void suppressRetarget(LivingChangeTargetEvent event) {
        if(event.getNewAboutToBeSetTarget()!=null&&divineSuppressed(event.getEntity()))event.setCanceled(true);
    }

    static void suppressUse(LivingEntityUseItemEvent.Start event) {
        if(divineSuppressed(event.getEntity()))event.setCanceled(true);
    }

    private static void burst(HumNpc npc, Skills.Skill skill, Vec3 center, boolean harm, double force) {
        var world = (ServerLevel) npc.level();
        for (var e : world.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(skill.radius()), npc::validTarget)) {
            double distance = e.getBoundingBox().getCenter().distanceTo(center);
            if (distance > skill.radius()) continue;
            if (SkillTrajectory.surface(world, center, e.getBoundingBox().getCenter()).block() != null) continue;
            if (harm) {
                double damage = skill.power() * (1 - .55 * distance / Math.max(.01, skill.radius()));
                e.hurt(npc.damageSources().indirectMagic(npc, npc), (float) damage);
                shove(e, center, force);
            } else {
                e.setTicksFrozen(Math.max(e.getTicksFrozen(), skill.duration()));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, skill.duration(), 1), npc);
            }
            impact(npc, skill, e.getBoundingBox().getCenter());
        }
    }

    static void shove(LivingEntity target, Vec3 source, double strength) {
        Vec3 push = target.position().subtract(source).multiply(1, 0, 1).normalize().scale(strength);
        target.push(push.x, Math.abs(strength) * .19, push.z);
    }

    static Vec3 ground(ServerLevel world, Vec3 at) {
        var base = BlockPos.containing(at);
        for (int y = 1; y >= -4; y--) {
            var candidate = base.above(y);
            if (WorldEffects.inWorld(world, candidate) && world.getBlockState(candidate).getCollisionShape(world, candidate).isEmpty()
                    && world.getBlockState(candidate.below()).isSolid()) {
                return new Vec3(at.x, candidate.getY(), at.z);
            }
        }
        return at;
    }

    private static boolean nearby(HumNpc npc, String id, double radius) {
        return !npc.level().getEntitiesOfClass(HumNpc.class, npc.getBoundingBox().inflate(radius),
                other -> other.characterId.equals(id) && other.isAlive()).isEmpty();
    }

    static ServerPlayer audience(HumNpc npc, UUID issuer) {
        if (issuer != null && npc.level() instanceof ServerLevel world) {
            return world.getServer().getPlayerList().getPlayer(issuer);
        }
        return npc.audience();
    }

    private static ServerPlayer cartridgeSource(HumNpc npc, UUID issuer) {
        var player = audience(npc, issuer);
        return player != null && player.getMainHandItem().is(BioHum.CARTRIDGE.get()) ? player : null;
    }

    private static boolean has(HumNpc npc, Item item) {
        return npc.getOffhandItem().is(item) || npc.getMainHandItem().is(item);
    }

    private static void consume(HumNpc npc, Item item) {
        var stack = npc.getOffhandItem().is(item) ? npc.getOffhandItem() : npc.getMainHandItem();
        if (stack.is(item)) stack.shrink(1);
    }

    private static void impact(HumNpc npc, Skills.Skill skill, Vec3 at) { cue(npc, skill, IMPACT, at, at, 1); }
    private static void field(HumNpc npc, Skills.Skill skill, Vec3 at, double radius) {
        cue(npc, skill, FIELD, at, at, (float) radius);
    }
    private static void cue(HumNpc npc, Skills.Skill skill, Payloads.Cue cue, Vec3 from, Vec3 to, float size) {
        Payloads.cue(npc, skill.id(), cue, from, to, size);
    }

    private CombatSkills() {}
}
