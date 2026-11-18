package cn.aca.omnitrix;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class Abilities {
    public static final int PRIMARY = 0, SECONDARY = 32, AUTOMATED = 64;
    public static final int INPUT_BUFFER_TICKS = 6;
    public static final int STYLE_ALTERNATE = 1, STYLE_FALLBACK = 2;
    private Abilities() {}

    static void register(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, Abilities::reduceDamage);
        bus.addListener(Abilities::breathe);
        TemporalEffects.register(bus);
    }

    private static void reduceDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.hasData(Omnitrix.WRIST)) return;
        var w = Omnitrix.wrist(p);
        if (WristService.absoluteDamage(e.getSource())) return;
        // Invulnerability frames during Celestial transformation council windup
        if (w.stage == Wrist.Stage.ARMING && w.kind() == Kind.CELESTIAL) {
            e.setCanceled(true);
            return;
        }
        if (!w.hasForm()) return;
        if (w.form() == Form.HEATBLAST && e.getSource().is(DamageTypeTags.IS_FIRE)) {
            e.setCanceled(true);
            return;
        }
        // Alien X (X God): 100% Omnipotent Reality Aegis — nullifies all non-absolute damage!
        if (w.form() == Form.ALIEN_X && w.kind() == Kind.CELESTIAL) {
            e.setCanceled(true);
            long now = WristService.time(p);
            if (now - w.lastAegisCueAt >= 6) {
                w.lastAegisCueAt = now;
                WristNetwork.cue(p, WristNetwork.AEGIS_CUE, p.position().add(0, 1.15, 0), p.getEyePosition());
                p.level().playSound(null, p.blockPosition(), Omnitrix.ALIEN_X_AEGIS.get(), SoundSource.PLAYERS, .72f, 1.0f);
            }
            return;
        }
        // Preserve Classic Ripjaws physiological dry-out penalty on land
        if (w.kind() == Kind.CLASSIC && w.form() == Form.RIPJAWS && e.getSource().is(DamageTypes.DRY_OUT)) {
            return;
        }
        if (w.form() == Form.DIAMONDHEAD && w.crystalShellUntil > WristService.time(p)
                && e.getAmount() > 0 && Float.isFinite(e.getAmount())) {
            w.crystalShellUntil = 0;
            e.setCanceled(true);
            WristNetwork.cue(p, WristNetwork.CRYSTAL_SHELL_BREAK_CUE, p.position(), p.getEyePosition());
            WristNetwork.sync(p);
            return;
        }
        // ACA Level 3-5 DNA / Astral / Corrupted Barrier Absorption (cancels incoming hit cleanly for HighPowerCombat compatibility)
        if (w.barrier > 0 && e.getAmount() > 0 && Float.isFinite(e.getAmount())) {
            double deflection = w.kind() == Kind.CELESTIAL ? .35
                    : (w.form() == Form.ALIEN_X ? .18 : w.kind() == Kind.CARNITRIX ? .65 : .75);
            double debit = e.getAmount() * deflection;
            w.barrier = Math.max(0, w.barrier - debit);
            w.lastDamageAt = WristService.time(p);
            e.setCanceled(true);
            WristNetwork.sync(p);
            return;
        }
        if (w.form() == Form.ALIEN_X) {
            e.setAmount(e.getAmount() * .18f);
        }
    }

    private static void breathe(LivingBreatheEvent e) {
        if (e.getEntity() instanceof Player p && p.hasData(Omnitrix.WRIST)) {
            var wrist = Omnitrix.wrist(p);
            if (wrist.hasForm() && (wrist.form() == Form.RIPJAWS
                    || (wrist.form() == Form.ALIEN_X && wrist.kind() == Kind.CELESTIAL))) {
                e.setCanBreathe(true);
                e.setRefillAirAmount(4);
            }
        }
    }

    static void tickBarrier(ServerPlayer p, Wrist w, long now) {
        Kind kind = w.kind();
        if (kind == null || w.form == null) return;
        if (kind == Kind.CELESTIAL && w.form == Form.ALIEN_X) {
            p.clearFire();
            p.resetFallDistance();
            if (p.getHealth() < p.getMaxHealth()) p.setHealth(p.getMaxHealth());
            if (p.getFoodData().getFoodLevel() < 20) p.getFoodData().setFoodLevel(20);
            for (var inst : List.copyOf(p.getActiveEffects())) {
                if (!inst.getEffect().value().isBeneficial()) p.removeEffect(inst.getEffect());
            }
            w.maxBarrier = Form.ALIEN_X.maxBarrier(Kind.CELESTIAL);
            w.barrier = w.maxBarrier;
            if (now % 2 == 0) TemporalEffects.deflectNearbyProjectiles(p, 5.5);
            return;
        }
        if (kind == Kind.CELESTIAL && now % 10 == 0 && p.getHealth() < p.getMaxHealth()) {
            p.heal(8f);
        }
        if (w.maxBarrier <= 0) w.maxBarrier = w.form.maxBarrier(kind);
        long delay = kind == Kind.CELESTIAL ? 40 : 60;
        if (w.barrier < w.maxBarrier && (w.lastDamageAt == Long.MIN_VALUE || now - w.lastDamageAt >= delay)) {
            double rate = kind == Kind.CELESTIAL ? .025 : .015;
            w.barrier = Math.min(w.maxBarrier, w.barrier + w.maxBarrier * rate);
            if (now % 5 == 0 || w.barrier >= w.maxBarrier) WristNetwork.sync(p);
        }
    }

    static void triggerExistentialRewrite(ServerPlayer p, Wrist w, long now) {
        var frozen = TemporalEffects.freezeHostiles(p);
        for (LivingEntity target : frozen) mark(w, target, now);
        Vec3 center = p.position();
        for (LivingEntity enemy : nearby(p, center, 18.0, false)) {
            cosmicAnnihilate(p, enemy, 35_000_000f, enemy.position().subtract(center), 1.8);
        }
        p.level().playSound(null, p.blockPosition(), Omnitrix.ALIEN_X_REWRITE.get(), SoundSource.PLAYERS, 1.05f, 1.0f);
        WristNetwork.cue(p, WristNetwork.REWRITE_CUE, 0,
                WristNetwork.FLAG_HIT_ENTITY | WristNetwork.FLAG_HIT_GROUND, center, p.getEyePosition());
    }

    public static boolean realityFlick(ServerPlayer p, Wrist w, long now) {
        if (w.kind() != Kind.CELESTIAL || w.form != Form.ALIEN_X || now < w.flickAt) return false;
        w.flickAt = now + 8;
        Vec3 eye = p.getEyePosition();
        Vec3 face = p.getLookAngle();
        Vec3 focus = starFocus(p, eye, face, 36.0);
        int flags = 0;
        LivingEntity direct = enemyOnRay(p, eye, face, 36.0);
        if (direct != null) focus = direct.getEyePosition();
        for (LivingEntity enemy : nearby(p, focus, 2.6, false)) {
            float mult = (TemporalEffects.isFrozen(enemy) || linked(w, enemy, now)) ? 2.0f : 1.0f;
            if (cosmicAnnihilate(p, enemy, 18_000_000f * mult, focus.subtract(enemy.position()), 0)) {
                pull(enemy, focus, .45);
                flags |= WristNetwork.FLAG_HIT_ENTITY;
            }
        }
        p.level().playSound(null, BlockPos.containing(focus), Omnitrix.ALIEN_X_FLICK.get(), SoundSource.PLAYERS, .95f, 1.0f);
        WristNetwork.cue(p, WristNetwork.FLICK_CUE, 0, flags, eye.add(face.scale(.65)), focus);
        WristNetwork.sync(p);
        return true;
    }

    private static final int LINK_TICKS = 80;

    static void tickForm(ServerPlayer p, Wrist w, long now) {
        if (w.form == Form.HEATBLAST && p.isOnFire()) p.clearFire();
        if (w.form == Form.XLR8 && w.steerUntil > now) steerDash(p, w);
        if (w.form == Form.GREY_MATTER) {
            boolean holding = w.gripUntil > now && !p.isCrouching() && validGrip(p, w.gripWall);
            if (w.gripWall != null && !holding) {
                w.gripWall = null;
                w.gripUntil = 0;
                w.clearLink();
                WristNetwork.sync(p);
            }
            if (holding) {
                p.setDeltaMovement(Vec3.ZERO);
                p.resetFallDistance();
                if (now % 4 == 0) p.hurtMarked = true;
            } else if (p.horizontalCollision && !p.isCrouching() && !p.onGround()) {
                var speed = p.getDeltaMovement();
                p.setDeltaMovement(speed.x, Math.max(.17, speed.y), speed.z);
                p.resetFallDistance();
                if (now % 4 == 0) p.hurtMarked = true;
            }
        }
        w.landings.entrySet().removeIf(entry -> {
            var landing = entry.getValue();
            if (now >= landing.expires()) return true;
            var entity = p.serverLevel().getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity target) || !validTarget(p, target, false)) return true;
            if (now < landing.earliest() || !target.onGround()) return false;
            slow(p, target, 45);
            if (w.kind() == Kind.CELESTIAL) {
                damage(p, target, 110_000f, Vec3.ZERO, 0, 0, 0);
            }
            return true;
        });
        if (!WristService.eligible(p)) {
            w.bufferedSkill = null;
            if (w.skill != null) finishSkill(p, w);
            return;
        }
        resolveSkill(p, w, now);
        var pending = w.bufferedSkill;
        if (pending == null) return;
        if (pending.expires() < now) {
            w.bufferedSkill = null;
        } else if (readyAt(w, pending.secondary(), pending.shifted()) <= now
                && canStartDuring(w, pending.secondary(), now)) {
            w.bufferedSkill = null;
            beginCast(p, w, pending.secondary(), pending.shifted(), now);
        }
    }

    private static void resolveSkill(ServerPlayer p, Wrist w, long now) {
        var skill = w.skill;
        if (skill == null) return;
        while (skill.due(now)) {
            int beat = skill.nextBeat++;
            skill.lastFlags = 0;
            Vec3 result = skill.motion.action() >= SECONDARY
                    ? secondary(p, w, skill, now)
                    : primary(p, w, skill, beat, now);
            if (result == null) {
                WristService.tell(p, "no_target");
                finishSkill(p, w);
                return;
            }
            Vec3 origin = w.form == Form.FOUR_ARMS && skill.motion.action() < SECONDARY
                    ? p.position().add(0, 1.45, 0) : p.getEyePosition();
            cue(p, skill, beat, origin, result);
            WristNetwork.sync(p);
        }
        if (now >= skill.motion.end()) finishSkill(p, w);
    }

    public static boolean cast(ServerPlayer p, Wrist w, boolean secondary, long now) {
        return cast(p, w, secondary, false, now);
    }

    public static boolean cast(ServerPlayer p, Wrist w, boolean secondary, boolean shifted, long now) {
        if (w.stage != Wrist.Stage.ACTIVE || w.controlled() || !WristService.eligible(p) || w.form() == null
                || now >= w.until) return false;
        boolean alternate = w.form == Form.ALIEN_X && w.kind() == Kind.CELESTIAL
                && (shifted || p.isShiftKeyDown());
        long ready = readyAt(w, secondary, alternate);
        boolean canStart = canStartDuring(w, secondary, now);
        if (ready > now || !canStart) {
            long launch = canStart ? ready : Math.max(ready, w.skill.lastImpact());
            if (launch - now <= INPUT_BUFFER_TICKS) {
                w.bufferedSkill = new Wrist.BufferedSkill(secondary, alternate, now + INPUT_BUFFER_TICKS);
                return true;
            }
            w.skillHintUntil = now + 24;
            WristNetwork.sync(p);
            return WristService.tell(p, ready > now ? "skill_charging" : "busy");
        }
        w.bufferedSkill = null;
        beginCast(p, w, secondary, alternate, now);
        return true;
    }

    private static long readyAt(Wrist w, boolean secondary, boolean alternate) {
        if (alternate) return secondary ? w.shiftSecondaryAt : w.shiftPrimaryAt;
        return secondary ? w.secondaryAt : w.primaryAt;
    }

    private static boolean canStartDuring(Wrist w, boolean secondary, long now) {
        SkillCast current = w.skill;
        if (current == null || current.nextBeat >= current.beats.length) return true;
        return secondary && current.motion.action() == PRIMARY + w.form.ordinal()
                && (w.form == Form.XLR8 && w.brakeUntil >= now
                    || w.form == Form.GREY_MATTER && w.gripUntil > now);
    }

    private static void beginCast(ServerPlayer p, Wrist w, boolean secondary, boolean alternate, long now) {
        Form form = w.form();
        boolean star = w.kind() == Kind.CELESTIAL;

        Vec3 eye = p.getEyePosition();
        Vec3 face = p.getLookAngle();
        Vec3 focus = rayEnd(p, eye, face, 5);
        int style = 0;
        if (form == Form.ALIEN_X) {
            if (alternate) {
                style = STYLE_ALTERNATE;
                focus = secondary ? rayEnd(p, eye, face, 72.0) : starFocus(p, eye, face, 32.0);
            } else {
                focus = secondary ? starFocus(p, eye, face, 64.0) : p.position();
            }
        } else if (form == Form.FOUR_ARMS && secondary) {
            focus = sampleSlamGround(p, face).center();
            if (!p.onGround()) {
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x, Math.min(v.y, -.55), v.z);
                p.hurtMarked = true;
            }
        } else if (form == Form.GREY_MATTER && !secondary) {
            BlockPos wall = gripWall(p, eye, face);
            if (wall == null) {
                style = STYLE_FALLBACK;
                focus = p.position();
            } else {
                focus = Vec3.atCenterOf(wall);
            }
        } else if (form == Form.UPGRADE && !secondary) {
            var block = aimedLever(p, eye, face, star ? 10.0 : 6.0);
            if (block == null) {
                style = STYLE_FALLBACK;
                focus = p.position();
            } else {
                focus = Vec3.atCenterOf(block.getBlockPos());
            }
        } else if (form == Form.GHOSTFREAK && secondary) {
            focus = phaseDestination(p, face);
            if (focus == null) {
                style = STYLE_FALLBACK;
                Vec3 step = shadowStep(p, face);
                focus = step == null ? p.position() : step;
            }
        } else if (form == Form.STINKFLY && secondary && !p.onGround()
                || form == Form.RIPJAWS && p.isInWater()) {
            style = 1;
        }

        int delay = switch (form) {
            case XLR8, GREY_MATTER -> 0;
            case RIPJAWS -> secondary ? 0 : 4;
            case ALIEN_X -> style == 1 ? (secondary ? 6 : 8) : (secondary ? 12 : 4);
            default -> 4;
        };
        int code = (secondary ? SECONDARY : PRIMARY) + form.ordinal();
        int[] beats = form == Form.FOUR_ARMS && !secondary ? new int[]{4, 7, 10} : new int[]{delay};
        int recovery = (form == Form.FOUR_ARMS && secondary) || (form == Form.ALIEN_X && style == 1 && secondary)
                ? 8 : (delay == 0 ? 4 : 6);
        w.skill = new SkillCast(code, style, now, beats, recovery, eye, focus, face, null);
        if (alternate && secondary) {
            w.shiftSecondaryAt = now + 80;
        } else if (alternate) {
            w.shiftPrimaryAt = now + 120;
        } else if (secondary) {
            w.secondaryAt = now + form.secondaryCooldown(w.kind());
        } else {
            w.primaryAt = now + form.primaryCooldown(w.kind());
            w.primaryStarted = now;
            if (form != Form.ALIEN_X) w.clearLink();
        }
        w.skillHintUntil = now + 24;
        w.revision++;
        if (delay == 0) resolveSkill(p, w, now);
        else WristNetwork.sync(p);
    }

    private static void finishSkill(ServerPlayer p, Wrist w) {
        w.skill = null;
        w.revision++;
        WristNetwork.sync(p);
    }

    private static boolean linked(Wrist w, long now) {
        return w.linkUntil > now;
    }

    private static boolean linked(Wrist w, LivingEntity e, long now) {
        return linked(w, now) && e != null && w.markedTargets.contains(e.getUUID());
    }

    private static void mark(Wrist w, LivingEntity e, long now) {
        w.markedTargets.add(e.getUUID());
        w.linkUntil = now + LINK_TICKS;
    }

    static void projectileMarked(ServerPlayer p, long source, LivingEntity e) {
        var w = Omnitrix.wrist(p);
        if (w.stage == Wrist.Stage.ACTIVE && w.primaryStarted == source && w.kind() != Kind.CARNITRIX) {
            mark(w, e, WristService.time(p));
            WristNetwork.sync(p);
        }
    }

    private static Vec3 primary(ServerPlayer p, Wrist w, SkillCast skill, int beat, long now) {
        Vec3 eye = p.getEyePosition();
        Vec3 face = skill.direction;
        boolean star = w.kind() == Kind.CELESTIAL;
        return switch (w.form) {
            case HEATBLAST -> launch(p, Pulse.Variant.FIRE, eye, face, star ? 1.55 : 1.34, 1, w.primaryStarted);
            case WILDMUTT -> {
                double scentRange = star ? 32 : 19;
                int glowTicks = star ? 160 : 95;
                int maxTargets = star ? 8 : 3;
                List<LivingEntity> scented = nearby(p, p.position(), scentRange, false).stream().limit(maxTargets).toList();
                int idx = 1;
                for (LivingEntity entity : scented) {
                    entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, glowTicks, 0, false, false), p);
                    mark(w, entity, now);
                    WristNetwork.cue(p, skill.motion.action(), idx++, WristNetwork.FLAG_HIT_ENTITY,
                            p.position().add(0, .45, 0), entity.getEyePosition());
                }
                if (!scented.isEmpty()) skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                p.serverLevel().sendParticles(star ? ParticleTypes.END_ROD : ParticleTypes.WAX_ON,
                        p.getX(), p.getY() + .25, p.getZ(), 32, 2.1, .15, 2.1, .03);
                yield p.position().add(face.multiply(1, 0, 1).normalize().scale(scentRange));
            }
            case DIAMONDHEAD -> {
                Vec3 built = crystalWall(p, face, w, star ? 200 : 115, now);
                if (built == null) {
                    w.crystalShellUntil = now + 20;
                    w.linkUntil = w.crystalShellUntil;
                    skill.lastFlags |= WristNetwork.FLAG_FALLBACK;
                    yield p.position().add(0, .9, 0);
                }
                if (star) {
                    w.barrier = Math.min(w.maxBarrier, w.barrier + 1_200_000);
                    for (LivingEntity close : nearby(p, built, 3.6, false)) {
                        if (damage(p, close, 90_000f, close.position().subtract(p.position()), .75, 0, 0)) {
                            skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                        }
                    }
                }
                yield built;
            }
            case XLR8 -> {
                w.brakeUntil = now + (star ? 14 : 12);
                w.steerHeading = face.multiply(1, 0, 1).normalize();
                w.steerUntil = now + 8;
                w.linkUntil = w.brakeUntil;
                double dashSpeed = star ? 2.10 : 1.70;
                float dashDmg = star ? 120_000f : 2_600f;
                Vec3 end = dash(p, skill, face, dashSpeed, dashDmg, .17);
                if (star) {
                    // X-XLR8 sub-light slash cuts enemies along dash corridor
                    for (LivingEntity cut : nearby(p, p.position().add(face.scale(2.0)), 2.5, false)) {
                        if (skill.hit.add(cut.getUUID()) && damage(p, cut, dashDmg, face, .45, 0, 0)) {
                            mark(w, cut, now);
                            skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                        }
                    }
                }
                yield end;
            }
            case GREY_MATTER -> {
                BlockPos wall = gripWall(p, eye, face);
                if (wall == null) {
                    w.springUntil = now + 12;
                    w.linkUntil = w.springUntil;
                    skill.lastFlags |= WristNetwork.FLAG_FALLBACK;
                    yield p.position();
                }
                w.gripWall = wall;
                w.gripUntil = now + (star ? 28 : 20);
                w.linkUntil = w.gripUntil;
                if (star) {
                    for (LivingEntity scanned : nearby(p, p.position(), 16.0, false).stream().limit(4).toList()) {
                        scanned.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false), p);
                        mark(w, scanned, now);
                    }
                }
                skill.lastFlags |= WristNetwork.FLAG_HIT_GROUND;
                p.resetFallDistance();
                yield Vec3.atCenterOf(wall);
            }
            case FOUR_ARMS -> fourArmsComboBeat(p, w, skill, beat, star, now);
            case STINKFLY -> launch(p, Pulse.Variant.SLIME, eye, face, star ? 1.48 : 1.28, 1, w.primaryStarted);
            case RIPJAWS -> {
                double biteReach = star ? 4.6 : 3.9;
                var target = enemyOnRay(p, eye, face, biteReach);
                float biteDmg = p.isInWater() ? (star ? 230_000f : 4_600f) : (star ? 140_000f : 2_800f);
                if (target != null && damage(p, target, biteDmg, face, .55, 0, 0)) {
                    mark(w, target, now);
                    skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                }
                yield target == null ? rayEnd(p, eye, face, biteReach) : target.getEyePosition();
            }
            case UPGRADE -> {
                Vec3 point = skill.motion.style() == STYLE_FALLBACK ? null
                        : lever(p, eye, face, BlockPos.containing(skill.motion.focus()), star ? 10.0 : 6.0);
                if (point != null) {
                    w.circuitUntil = 0;
                    w.linkUntil = now + LINK_TICKS;
                    skill.lastFlags |= WristNetwork.FLAG_HIT_GROUND;
                }
                if (point != null) yield point;
                w.circuitUntil = now + LINK_TICKS;
                skill.lastFlags |= WristNetwork.FLAG_FALLBACK;
                yield p.position().add(0, p.getBbHeight() * .58, 0);
            }
            case GHOSTFREAK -> {
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, star ? 160 : 105, 0, false, false), p);
                if (star) {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 75, 1, false, false), p);
                }
                w.linkUntil = now + LINK_TICKS;
                yield eye.add(face);
            }
            case ALIEN_X -> {
                if (skill.motion.style() == 1 && star) {
                    Vec3 core = skill.motion.focus();
                    for (LivingEntity enemy : nearby(p, core, 32.0, false)) {
                        if (!clear(p, core.add(0, .24, 0), enemy.getEyePosition())
                                && !clear(p, eye, enemy.getEyePosition())) continue;
                        // Targets previously received the frozen multiplier on this same beat.
                        if (cosmicAnnihilate(p, enemy, 72_000_000f, Vec3.ZERO, 0)) {
                            if (enemy.isAlive()) {
                                pull(enemy, core.add(0, .6, 0), 1.65);
                                TemporalEffects.freezeSingle(p.serverLevel(), enemy, now + 80);
                            }
                            mark(w, enemy, now);
                            skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                        }
                    }
                    skill.lastFlags |= WristNetwork.FLAG_HIT_GROUND;
                    WristNetwork.cue(p, WristNetwork.SINGULARITY_CUE, 0, skill.lastFlags, p.getEyePosition(), core);
                    p.level().playSound(null, BlockPos.containing(core), Omnitrix.ALIEN_X_SINGULARITY.get(),
                            SoundSource.PLAYERS, 1.1f, 1.0f);
                    yield core;
                }
                var frozen = TemporalEffects.freezeHostiles(p);
                for (LivingEntity target : frozen) mark(w, target, now);
                yield p.position();
            }
        };
    }

    private static Vec3 fourArmsComboBeat(ServerPlayer p, Wrist w, SkillCast skill, int beat, boolean star, long now) {
        double reach = star ? 6.2 : 4.7;
        float dmg = star ? 195_000f : 4_800f;
        Vec3 eye = p.getEyePosition();
        Vec3 chest = p.position().add(0, 1.38, 0);
        Vec3 along = skill.direction.multiply(1, 0, 1);
        if (along.lengthSqr() < 1e-4) along = p.getLookAngle().multiply(1, 0, 1);
        along = along.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : along.normalize();

        Vec3 hitFocus = null;
        double minY = p.getY() - .55;
        double maxY = p.getY() + 2.95;
        var box = AABB.ofSize(chest.add(along.scale(reach * .45)), reach * 2.2, 4.4, reach * 2.2);
        List<LivingEntity> candidates = p.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> validTarget(p, e, false))
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).limit(28).toList();

        for (var enemy : candidates) {
            AABB eb = enemy.getBoundingBox();
            if (eb.maxY < minY || eb.minY > maxY) continue;
            double cx = Math.clamp(chest.x, eb.minX, eb.maxX);
            double cz = Math.clamp(chest.z, eb.minZ, eb.maxZ);
            double dx = cx - chest.x;
            double dz = cz - chest.z;
            double horizSqr = dx * dx + dz * dz;
            if (horizSqr > reach * reach) continue;

            Vec3 centerDelta = enemy.position().add(0, enemy.getBbHeight() * .55, 0).subtract(chest);
            double horizDist = Math.hypot(centerDelta.x, centerDelta.z);
            double angle = horizDist < 1e-3 ? 0 : Math.atan2(centerDelta.x * along.z - centerDelta.z * along.x,
                    centerDelta.x * along.x + centerDelta.z * along.z);
            if (Math.abs(angle) > 1.18 && horizDist > 1.15) continue;
            boolean inBeatSector = horizDist <= 1.25 || switch (beat) {
                case 0 -> angle >= -.22 && angle <= 1.18;
                case 1 -> Math.abs(angle) <= .55;
                default -> angle <= .30 && angle >= -1.18;
            };
            if (!inBeatSector) continue;
            if (!clear(p, eye, enemy.getEyePosition()) && !clear(p, chest, eb.getCenter())) continue;
            if (skill.hit.add(enemy.getUUID()) && damage(p, enemy, dmg, centerDelta, 1.22, 0, 0)) {
                mark(w, enemy, now);
                skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                if (hitFocus == null) hitFocus = eb.getCenter();
            }
        }
        float sweepYaw = beat == 0 ? .36f : beat == 1 ? -.08f : -.34f;
        return hitFocus != null ? hitFocus : chest.add(along.yRot(sweepYaw).scale(reach));
    }

    private static Vec3 secondary(ServerPlayer p, Wrist w, SkillCast skill, long now) {
        Vec3 eye = p.getEyePosition();
        Vec3 face = skill.direction;
        boolean star = w.kind() == Kind.CELESTIAL;
        return switch (w.form) {
            case HEATBLAST -> {
                double coneRange = star ? 10.2 : 6.2;
                float coneDmg = star ? 95_000f : 2_400f;
                LivingEntity seed = null;
                for (var target : coneTargets(p, eye, face, coneRange, star ? .60 : .66, false)) {
                    boolean primed = linked(w, target, now);
                    if (damage(p, target, primed && !star ? coneDmg * 1.5f : coneDmg, face, .65, star ? 110 : 65, 0)) {
                        skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                        if (primed) seed = target;
                    }
                }
                flameParticles(p, eye, face, coneRange, star);
                if (seed != null) {
                    seed.igniteForTicks(Math.max(65, seed.getRemainingFireTicks()) + (star ? 60 : 20));
                    if (star) damage(p, seed, 160_000f, face, .35, 120, 0);
                    skill.lastFlags |= WristNetwork.FLAG_LINKED;
                    if (star) {
                        int idx = 1;
                        final LivingEntity seedRef = seed;
                        for (var other : nearby(p, seedRef.position(), 5.0, false).stream()
                                .filter(e -> e != seedRef && clear(p, seedRef.getEyePosition(), e.getEyePosition())).limit(4).toList()) {
                            if (damage(p, other, 65_000f, other.position().subtract(seedRef.position()), .25, 80, 40)) {
                                WristNetwork.cue(p, skill.motion.action(), idx++,
                                        WristNetwork.FLAG_HIT_ENTITY | WristNetwork.FLAG_LINKED,
                                        seedRef.getEyePosition(), other.getEyePosition());
                            }
                        }
                    }
                    w.clearLink();
                }
                yield rayEnd(p, eye, face, coneRange);
            }
            case WILDMUTT -> {
                double pounceReach = star ? 6.2 : 3.8;
                float pounceDmg = star ? 135_000f : 3_400f;
                Vec3 heading = face.multiply(1, 0, 1).normalize();
                var target = enemyOnRay(p, eye, heading, pounceReach);
                if (linked(w, now) && !linked(w, target, now)) {
                    var tracked = nearby(p, p.position(), pounceReach, false).stream()
                            .filter(e -> linked(w, e, now) && clear(p, eye, e.getEyePosition())
                                    && e.position().subtract(p.position()).multiply(1, 0, 1).normalize().dot(heading) > .95)
                            .findFirst().orElse(null);
                    if (tracked != null) target = tracked;
                }
                Vec3 leap = linked(w, target, now) ? target.position().subtract(p.position()) : heading;
                moveDash(p, leap, star ? 1.18 : .92, .34);
                if (target != null) {
                    boolean isLinked = linked(w, target, now);
                    float totalDmg = isLinked && star ? pounceDmg * 2.0f : pounceDmg;
                    if (damage(p, target, totalDmg, face, .65, 0, 0)) {
                        skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                        if (isLinked) {
                            slow(p, target, star ? 50 : 30);
                            skill.lastFlags |= WristNetwork.FLAG_LINKED;
                            if (star) {
                                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2));
                                w.secondaryAt = Math.max(now, w.secondaryAt - 8);
                            }
                            w.clearLink();
                        }
                    }
                }
                yield target == null ? rayEnd(p, eye, face, pounceReach) : target.getEyePosition();
            }
            case DIAMONDHEAD -> {
                boolean fan = linked(w, now) && (w.crystalShellUntil > now || w.markedCrystals.stream().anyMatch(pos ->
                        p.serverLevel().hasChunkAt(pos) && p.distanceToSqr(Vec3.atCenterOf(pos)) <= 9
                                && p.serverLevel().getBlockState(pos).is(Omnitrix.CRYSTAL.get())));
                int count = fan ? (star ? 5 : 3) : 1;
                float frac = fan ? (star ? .60f : 1f / count) : 1f;
                Vec3 result = null;
                for (int shot = 0; shot < count; shot++) {
                    float stepDeg = star ? 11f : 12f;
                    float angle = fan ? (float) Math.toRadians((shot - count / 2) * stepDeg) : 0;
                    var tip = launch(p, Pulse.Variant.CRYSTAL, eye, face.yRot(angle), star ? 2.45 : 2.1, frac, Long.MIN_VALUE);
                    if (tip != null) result = tip;
                }
                if (fan && result != null) {
                    skill.lastFlags |= WristNetwork.FLAG_LINKED;
                    w.clearLink();
                }
                yield result;
            }
            case XLR8 -> {
                w.steerUntil = 0;
                p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
                p.hurtMarked = true;
                p.setSprinting(false);
                boolean combo = linked(w, now) && w.brakeUntil >= now;
                if (combo) {
                    w.primaryAt = Math.max(now, w.primaryAt - 4);
                    skill.lastFlags |= WristNetwork.FLAG_LINKED;
                    if (star) {
                        for (var close : nearby(p, p.position(), 4.2, false)) {
                            if (clear(p, eye, close.getEyePosition())
                                    && damage(p, close, 95_000f, close.position().subtract(p.position()), .55, 0, 35)) {
                                skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                            }
                        }
                    }
                    w.clearLink();
                } else if (star) {
                    var close = nearby(p, p.position(), 2.6, false).stream()
                            .filter(e -> clear(p, eye, e.getEyePosition())).findFirst().orElse(null);
                    if (close != null && damage(p, close, 48_000f, close.position().subtract(p.position()), .25, 0, 20)) {
                        skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                    }
                }
                if (!combo) {
                    Vec3 side = face.multiply(1, 0, 1).normalize().yRot((float) (Math.PI * .5));
                    Vec3 step = clearStep(p, side, .75);
                    if (step == null) step = clearStep(p, side.scale(-1), .75);
                    if (step != null) {
                        Vec3 shift = step.subtract(p.position());
                        p.setDeltaMovement(shift.x * .70, p.getDeltaMovement().y, shift.z * .70);
                        p.hurtMarked = true;
                        skill.lastFlags |= WristNetwork.FLAG_FALLBACK;
                        w.brakeUntil = 0;
                        yield step;
                    }
                }
                w.brakeUntil = 0;
                yield p.position();
            }
            case GREY_MATTER -> {
                boolean kick = w.gripUntil > now && linked(w, now) && validGrip(p, w.gripWall);
                boolean spring = !kick && w.springUntil > now;
                Vec3 direction = kick ? awayFrom(p, w.gripWall, face) : face;
                w.gripUntil = 0;
                w.gripWall = null;
                w.springUntil = 0;
                double horiz = kick ? (star ? .55 : .44) : (star ? .35 : .28);
                Vec3 result = dash(p, skill, direction, horiz + (spring ? .04 : 0),
                        star ? 85_000f : 1_800f, kick ? .43 : spring ? .38 : .30);
                if (spring) {
                    w.clearLink();
                    skill.lastFlags |= WristNetwork.FLAG_FALLBACK;
                }
                if (star) {
                    // X-Grey Matter Omniscient Neural Orbital Discharge onto analyzed enemy!
                    LivingEntity neural = enemyOnRay(p, eye, face, 16.0);
                    if (neural == null) {
                        neural = nearby(p, p.position(), 12.0, false).stream()
                                .filter(e -> clear(p, eye, e.getEyePosition())).findFirst().orElse(null);
                    }
                    if (neural != null && damage(p, neural, kick ? 150_000f : 95_000f, face, .25, 0, 35)) {
                        skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                        result = neural.getEyePosition();
                    }
                }
                if (kick) {
                    skill.lastFlags |= WristNetwork.FLAG_LINKED;
                    if (star) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40));
                    w.clearLink();
                }
                yield result;
            }
            case FOUR_ARMS -> slam(p, w, skill, star, now);
            case STINKFLY -> {
                boolean dive = skill.motion.style() == 1 && !p.onGround();
                float diveDmg = star ? 125_000f : 2_200f;
                var target = enemyOnRay(p, eye, face, star ? 4.6 : 3.38);
                moveDash(p, face, star ? .92 : .73, dive ? -.45 : .38);
                if (target != null && damage(p, target, diveDmg, face, .55, 0, 0)) {
                    skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                    if (linked(w, target, now)) {
                        var effect = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                        if (effect != null) {
                            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                                    effect.getDuration() + (star ? 40 : 20), effect.getAmplifier()), p);
                            if (star) damage(p, target, 145_000f, face, .35, 0, 0);
                            skill.lastFlags |= WristNetwork.FLAG_LINKED;
                            if (dive && star) {
                                Vec3 delta = p.getDeltaMovement();
                                p.setDeltaMovement(delta.x * .5, .42, delta.z * .5);
                                p.hurtMarked = true;
                            }
                            w.clearLink();
                        }
                    }
                }
                yield target == null ? rayEnd(p, eye, face, star ? 4.6 : 3.38) : target.getEyePosition();
            }
            case RIPJAWS -> {
                boolean water = p.isInWater();
                double force = water ? (star ? 1.35 : 1.15) : .55;
                float tailDmg = star ? 155_000f : 3_400f;
                var target = enemyOnRay(p, eye, water ? face : face.multiply(1, 0, 1).normalize(), 2.85 + force);
                boolean pull = water && linked(w, target, now);
                moveDash(p, face, force, .13);
                if (target != null && damage(p, target, tailDmg, face, pull ? 0 : .55, 0, 0)) {
                    skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                    if (pull) {
                        pull(target, p.position(), .48);
                        skill.lastFlags |= WristNetwork.FLAG_LINKED;
                        if (star) {
                            int idx = 1;
                            for (var other : nearby(p, target.position(), 4.2, false).stream()
                                    .filter(e -> e != target && e.isInWater() && clear(p, target.getEyePosition(), e.getEyePosition()))
                                    .limit(3).toList()) {
                                if (damage(p, other, 85_000f, p.position().subtract(other.position()), 0, 0, 0)) {
                                    other.setDeltaMovement(Vec3.ZERO);
                                    pull(other, p.position(), .42);
                                    WristNetwork.cue(p, skill.motion.action(), idx++,
                                            WristNetwork.FLAG_HIT_ENTITY | WristNetwork.FLAG_LINKED,
                                            other.getEyePosition(), p.position().add(0, .9, 0));
                                }
                            }
                        }
                        w.clearLink();
                    }
                }
                yield target == null ? rayEnd(p, eye, face, 2.85 + force) : target.getEyePosition();
            }
            case UPGRADE -> {
                double beamRange = star ? 36.0 : 20.0;
                float beamDmg = star ? 210_000f : 4_000f;
                boolean charged = w.circuitUntil > now;
                w.circuitUntil = 0;
                if (charged) skill.lastFlags |= WristNetwork.FLAG_FALLBACK;
                var target = enemyOnRay(p, eye, face, beamRange);
                if (target != null) {
                    boolean isLinked = linked(w, now);
                    float total = isLinked && star ? beamDmg + 90_000f : charged ? beamDmg * 1.25f : beamDmg;
                    if (damage(p, target, total, face, .28, 0, 0)) {
                        skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                        if (isLinked) {
                            slow(p, target, 35);
                            skill.lastFlags |= WristNetwork.FLAG_LINKED;
                            if (star) w.secondaryAt = Math.max(now, w.secondaryAt - 10);
                            w.clearLink();
                        }
                    }
                }
                yield target == null ? rayEnd(p, eye, face, beamRange) : target.getEyePosition();
            }
            case GHOSTFREAK -> {
                Vec3 exit = phaseDestination(p, face);
                if (exit == null) {
                    skill.lastFlags |= WristNetwork.FLAG_FALLBACK;
                    exit = shadowStep(p, face);
                    if (exit == null) {
                        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false), p);
                        yield eye;
                    }
                }
                Vec3 entry = p.position().add(0, .95, 0);
                boolean isLinked = linked(w, now) && p.hasEffect(MobEffects.INVISIBILITY);
                if (star) {
                    Vec3 tip = exit.add(0, .95, 0);
                    Vec3 path = tip.subtract(entry);
                    Vec3 mid = entry.lerp(tip, .5);
                    float slashDmg = isLinked ? 280_000f : 175_000f;
                    for (LivingEntity soul : nearby(p, mid, 4.7, false)) {
                        Vec3 point = soul.getBoundingBox().getCenter();
                        double along = Math.clamp(point.subtract(entry).dot(path) / path.lengthSqr(), 0, 1);
                        if (!soul.getBoundingBox().inflate(2.0).contains(entry.add(path.scale(along)))
                                || !clear(p, entry, point) && !clear(p, tip, point)) continue;
                        if (damage(p, soul, slashDmg, path, .35, 0, 30)) {
                            skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                        }
                    }
                }
                p.connection.teleport(exit.x, exit.y, exit.z, p.getYRot(), p.getXRot());
                p.resetFallDistance();
                if (isLinked) {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, star ? 40 : 20, star ? 1 : 0));
                    skill.lastFlags |= WristNetwork.FLAG_LINKED;
                    if (star) w.secondaryAt = Math.max(now, w.secondaryAt - 10);
                    w.clearLink();
                }
                WristNetwork.cue(p, skill.motion.action(), 1, skill.lastFlags, entry, exit.add(0, .95, 0));
                yield exit;
            }
            case ALIEN_X -> {
                if (skill.motion.style() == 1 && star) {
                    // Shift + I: 72-block Galactic Extinction Ray (88,000,000 - 150,000,000 corridor annihilation!)
                    yield galacticExtinctionRay(p, w, skill, eye, face, now);
                }
                Vec3 focus = skill.motion.focus();
                if (eye.distanceToSqr(focus) > 66.0 * 66.0 || !clear(p, eye, focus)
                        || !p.serverLevel().hasChunkAt(BlockPos.containing(focus))) yield null;
                yield supernovaStarburst(p, w, skill, focus, star, now);
            }
        };
    }

    private static Vec3 supernovaStarburst(ServerPlayer p, Wrist w, SkillCast skill, Vec3 center, boolean star, long now) {
        double radius = star ? 16.0 : 9.0;
        float basePower = star ? 65_000_000f : 120_000f;
        boolean anyLinked = false;
        for (LivingEntity enemy : nearby(p, center, radius, WristService.controlled(p))) {
            Vec3 delta = enemy.position().subtract(center);
            double intensity = 1 - .45 * Math.min(1, delta.length() / radius);
            if (!clear(p, center.add(0, .20, 0), enemy.getEyePosition())
                    && !clear(p, p.getEyePosition(), enemy.getEyePosition())) continue;
            boolean judged = star && (TemporalEffects.isFrozen(enemy) || linked(w, enemy, now));
            float amount = (float) (basePower * intensity * (judged ? 2.0 : 1.0));
            boolean hit = star ? cosmicAnnihilate(p, enemy, amount, delta, 2.6 * intensity)
                    : damage(p, enemy, amount, delta, 2.35 * intensity, 0, 0);
            if (hit) {
                skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                if (judged) anyLinked = true;
            }
        }
        if (anyLinked) {
            skill.lastFlags |= WristNetwork.FLAG_LINKED;
            w.clearLink();
        }
        skill.lastFlags |= WristNetwork.FLAG_HIT_GROUND;
        p.level().playSound(null, p.blockPosition(), Omnitrix.ALIEN_X_STARBURST.get(),
                SoundSource.PLAYERS, 1.15f, star ? 1.0f : .88f);
        return center;
    }

    private static Vec3 galacticExtinctionRay(ServerPlayer p, Wrist w, SkillCast skill, Vec3 eye, Vec3 face, long now) {
        Vec3 dir = face.normalize();
        Vec3 end = rayEnd(p, eye, dir, 72.0);
        double len = eye.distanceTo(end);
        AABB corridor = new AABB(eye, end).inflate(3.6);
        boolean anyLinked = false;
        for (LivingEntity enemy : p.serverLevel().getEntitiesOfClass(LivingEntity.class, corridor,
                e -> validTarget(p, e, false))) {
            Vec3 toEnemy = enemy.getBoundingBox().getCenter().subtract(eye);
            double proj = MthClamp(toEnemy.dot(dir), 0, len);
            Vec3 closest = eye.add(dir.scale(proj));
            if (enemy.getBoundingBox().inflate(3.2).contains(closest)) {
                boolean judged = TemporalEffects.isFrozen(enemy) || linked(w, enemy, now);
                float dmg = judged ? 150_000_000f : 88_000_000f;
                if (cosmicAnnihilate(p, enemy, dmg, dir, 1.85)) {
                    skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                    if (judged) anyLinked = true;
                }
            }
        }
        if (anyLinked) {
            skill.lastFlags |= WristNetwork.FLAG_LINKED;
            w.clearLink();
        }
        skill.lastFlags |= WristNetwork.FLAG_HIT_GROUND;
        p.level().playSound(null, p.blockPosition(), Omnitrix.ALIEN_X_EXTINCTION.get(), SoundSource.PLAYERS, 1.15f, 1.0f);
        WristNetwork.cue(p, WristNetwork.EXTINCTION_CUE, 0, skill.lastFlags, eye.add(dir.scale(.8).add(0, -.18, 0)), end);
        return end;
    }

    private static double MthClamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    /**
     * Multi-layer Celestial Annihilation: deals full playerAttack damage (integrating with ACA HighPowerCombat exact ledger
     * and player kill attribution), and if a heavily-armored modded target still survives above 0 HP, follows up with
     * armor-bypassing indirectMagic reality-erasure. Respects event cancellation & protection plugins 100%.
     */
    private static boolean cosmicAnnihilate(ServerPlayer p, LivingEntity enemy, float amount, Vec3 direction, double knock) {
        if (!validTarget(p, enemy, false)) return false;
        int immunity = enemy.invulnerableTime;
        enemy.invulnerableTime = 0;
        boolean hit = enemy.hurt(p.damageSources().playerAttack(p), amount);
        if (!hit) {
            enemy.invulnerableTime = immunity;
            return false;
        }
        if (knock > 0 && direction.lengthSqr() > 1e-6) {
            enemy.knockback(knock, -direction.x, -direction.z);
        }
        if (enemy.isAlive() && enemy.getHealth() > 0) {
            enemy.invulnerableTime = 0;
            enemy.hurt(p.damageSources().indirectMagic(p, p), amount);
        }
        return true;
    }

    private record SlamSample(Vec3 center, BlockPos groundBlock, boolean grounded) {}

    private static SlamSample sampleSlamGround(ServerPlayer p, Vec3 face) {
        Vec3 along = face.multiply(1, 0, 1);
        along = along.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : along.normalize();
        Vec3 chest = p.position().add(0, 1.15, 0);
        HitResult fwd = p.level().clip(new ClipContext(chest, chest.add(along.scale(1.15)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 probeXZ = fwd.getType() == HitResult.Type.BLOCK
                ? fwd.getLocation().subtract(along.scale(.25))
                : p.position().add(along.scale(1.05));
        Vec3 top = new Vec3(probeXZ.x, p.getY() + 1.25, probeXZ.z);
        Vec3 bottom = new Vec3(probeXZ.x, p.getY() - 2.60, probeXZ.z);
        HitResult down = p.level().clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (down instanceof net.minecraft.world.phys.BlockHitResult bhr && down.getType() == HitResult.Type.BLOCK) {
            return new SlamSample(bhr.getLocation(), bhr.getBlockPos(), true);
        }
        return new SlamSample(new Vec3(probeXZ.x, p.getY(), probeXZ.z), null, false);
    }

    private static Vec3 slam(ServerPlayer p, Wrist w, SkillCast skill, boolean star, long now) {
        SlamSample sample = sampleSlamGround(p, skill.direction);
        Vec3 center = sample.center();
        double baseRadius = star ? 7.2 : 4.5;
        double radius = sample.grounded() ? baseRadius : baseRadius * .78;
        float cap = star ? 260_000f : 5_600f;
        boolean consumed = false;

        var slamSfx = star ? Omnitrix.X_FOUR_ARMS_SLAM.get() : Omnitrix.FOUR_ARMS_SLAM.get();
        if (sample.grounded()) {
            skill.lastFlags |= WristNetwork.FLAG_HIT_GROUND;
            var state = p.serverLevel().getBlockState(sample.groundBlock());
            if (!state.isAir()) {
                p.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        center.x, center.y + .12, center.z, star ? 28 : 36, .95, .18, .95, .14);
            }
            p.level().playSound(null, BlockPos.containing(center), slamSfx,
                    SoundSource.PLAYERS, .95f, star ? 1.0f : .95f);
        } else {
            p.level().playSound(null, BlockPos.containing(center), slamSfx,
                    SoundSource.PLAYERS, .76f, star ? 1.06f : 1.02f);
        }

        Vec3 sightOrigin = center.add(0, .35, 0);
        for (var target : nearby(p, center, radius, false)) {
            Vec3 delta = target.position().subtract(center);
            if (!clear(p, sightOrigin, target.getEyePosition())
                    && !clear(p, p.getEyePosition(), target.getEyePosition())) continue;
            double intensity = 1 - .55 * Math.min(1, delta.length() / radius);
            boolean toss = linked(w, target, now);
            if (damage(p, target, (float) (cap * intensity), delta, toss ? 0 : 1.45 * intensity, 0, 0)) {
                skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                if (toss) {
                    target.push(0, .52, 0);
                    target.hurtMarked = true;
                    if (star) w.landings.put(target.getUUID(), new Wrist.Landing(now + 3, now + 100));
                    consumed = true;
                }
            }
        }
        if (consumed) {
            skill.lastFlags |= WristNetwork.FLAG_LINKED;
            w.clearLink();
        }
        return center;
    }

    private static void slow(ServerPlayer p, LivingEntity target, int duration) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 0), p);
    }

    private static LivingEntity nearestOther(ServerPlayer p, LivingEntity seed, double range, boolean water) {
        return nearby(p, seed.position(), range, false).stream().filter(e -> e != seed && (!water || e.isInWater())
                && clear(p, seed.getEyePosition(), e.getEyePosition())).findFirst().orElse(null);
    }

    private static void pull(LivingEntity target, Vec3 center, double strength) {
        Vec3 delta = center.subtract(target.position());
        Vec3 pull = delta.lengthSqr() < 1e-6 ? Vec3.ZERO : delta.normalize().scale(strength);
        target.push(pull.x, pull.y, pull.z);
        target.hurtMarked = true;
    }

    private static Vec3 starFocus(ServerPlayer p, Vec3 eye, Vec3 face, double maxRange) {
        Vec3 end = rayEnd(p, eye, face, maxRange);
        EntityHitResult hit = rayTarget(p, eye, end);
        return hit == null ? end : hit.getLocation();
    }

    private static BlockPos gripWall(ServerPlayer p, Vec3 eye, Vec3 face) {
        var hit = miss(p, eye, face, 1.2);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && block.getDirection().getAxis().isHorizontal() && validGrip(p, block.getBlockPos())) {
            return block.getBlockPos().immutable();
        }
        if (p.horizontalCollision) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                var adjacent = miss(p, p.position().add(0, .23, 0), Vec3.atLowerCornerOf(side.getNormal()), .52);
                if (adjacent instanceof net.minecraft.world.phys.BlockHitResult block && adjacent.getType() == HitResult.Type.BLOCK
                        && validGrip(p, block.getBlockPos())) return block.getBlockPos().immutable();
            }
        }
        return null;
    }

    private static boolean validGrip(ServerPlayer p, BlockPos pos) {
        if (pos == null || !p.serverLevel().hasChunkAt(pos)
                || p.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 2.25) return false;
        return !p.serverLevel().getBlockState(pos).getCollisionShape(p.level(), pos).isEmpty();
    }

    private static Vec3 awayFrom(ServerPlayer p, BlockPos wall, Vec3 face) {
        Vec3 away = p.position().subtract(Vec3.atCenterOf(wall)).multiply(1, 0, 1).normalize();
        return face.dot(away) > 0 ? face : away;
    }

    private static net.minecraft.sounds.SoundEvent carniSound(Form form) {
        if (form == null) return Omnitrix.POWER.get();
        return switch (form) {
            case HEATBLAST -> Omnitrix.CARNI_HEATBLAST.get();
            case WILDMUTT -> Omnitrix.CARNI_WILDMUTT.get();
            case DIAMONDHEAD -> Omnitrix.CARNI_DIAMONDHEAD.get();
            case XLR8 -> Omnitrix.CARNI_XLR8.get();
            case GREY_MATTER -> Omnitrix.CARNI_GREY_MATTER.get();
            case FOUR_ARMS -> Omnitrix.CARNI_FOUR_ARMS.get();
            case STINKFLY -> Omnitrix.CARNI_STINKFLY.get();
            case RIPJAWS -> Omnitrix.CARNI_RIPJAWS.get();
            case UPGRADE -> Omnitrix.CARNI_UPGRADE.get();
            case GHOSTFREAK -> Omnitrix.CARNI_GHOSTFREAK.get();
            case ALIEN_X -> Omnitrix.CARNI_ALIEN_X.get();
        };
    }

    private static net.minecraft.sounds.SoundEvent skillSound(Form form, boolean secondary, boolean star) {
        if (form == null) return Omnitrix.POWER.get();
        if (star) {
            return switch (form) {
                case HEATBLAST -> secondary ? Omnitrix.X_FIRE_CONE.get() : Omnitrix.X_FIRE_LAUNCH.get();
                case WILDMUTT -> secondary ? Omnitrix.X_WILDMUTT_POUNCE.get() : Omnitrix.X_WILDMUTT_SCENT.get();
                case DIAMONDHEAD -> secondary ? Omnitrix.X_CRYSTAL_VOLLEY.get() : Omnitrix.X_CRYSTAL_SURGE.get();
                case XLR8 -> secondary ? Omnitrix.X_XLR8_BRAKE.get() : Omnitrix.X_XLR8_DASH.get();
                case GREY_MATTER -> secondary ? Omnitrix.X_GREY_MATTER_ACTION.get() : Omnitrix.X_GREY_MATTER_GRIP.get();
                case FOUR_ARMS -> secondary ? Omnitrix.X_FOUR_ARMS_SLAM.get() : Omnitrix.X_FOUR_ARMS_PUNCH.get();
                case STINKFLY -> secondary ? Omnitrix.X_STINKFLY_WING.get() : Omnitrix.X_SLIME_SPLAT.get();
                case RIPJAWS -> secondary ? Omnitrix.X_RIPJAWS_SURGE.get() : Omnitrix.X_RIPJAWS_SNAP.get();
                case UPGRADE -> secondary ? Omnitrix.X_UPGRADE_BEAM.get() : Omnitrix.X_UPGRADE_LINK.get();
                case GHOSTFREAK -> secondary ? Omnitrix.X_GHOST_PHASE.get() : Omnitrix.X_GHOST_CLOAK.get();
                case ALIEN_X -> Omnitrix.ALIEN_X_STASIS.get();
            };
        }
        return switch (form) {
            case HEATBLAST -> secondary ? Omnitrix.FIRE_CONE.get() : Omnitrix.FIRE_LAUNCH.get();
            case WILDMUTT -> secondary ? Omnitrix.WILDMUTT_POUNCE.get() : Omnitrix.WILDMUTT_SCENT.get();
            case DIAMONDHEAD -> secondary ? Omnitrix.CRYSTAL_VOLLEY.get() : Omnitrix.CRYSTAL_SURGE.get();
            case XLR8 -> secondary ? Omnitrix.XLR8_BRAKE.get() : Omnitrix.XLR8_DASH.get();
            case GREY_MATTER -> secondary ? Omnitrix.GREY_MATTER_ACTION.get() : Omnitrix.GREY_MATTER_GRIP.get();
            case FOUR_ARMS -> secondary ? Omnitrix.FOUR_ARMS_SLAM.get() : Omnitrix.FOUR_ARMS_PUNCH.get();
            case STINKFLY -> secondary ? Omnitrix.STINKFLY_WING.get() : Omnitrix.SLIME_SPLAT.get();
            case RIPJAWS -> secondary ? Omnitrix.RIPJAWS_SURGE.get() : Omnitrix.RIPJAWS_SNAP.get();
            case UPGRADE -> secondary ? Omnitrix.UPGRADE_BEAM.get() : Omnitrix.UPGRADE_LINK.get();
            case GHOSTFREAK -> secondary ? Omnitrix.GHOST_PHASE.get() : Omnitrix.GHOST_CLOAK.get();
            case ALIEN_X -> Omnitrix.ALIEN_X_STASIS.get();
        };
    }

    private static void cue(ServerPlayer p, SkillCast skill, int beat, Vec3 start, Vec3 result) {
        var wrist = Omnitrix.wrist(p);
        Form form = wrist.form;
        Kind watchKind = wrist.kind();
        int rawAction = skill.motion.action();
        boolean automated = rawAction >= AUTOMATED || watchKind == Kind.CARNITRIX;
        boolean secondary = rawAction >= SECONDARY && rawAction < AUTOMATED;
        boolean star = watchKind == Kind.CELESTIAL;
        boolean style = skill.motion.style() == 1;
        if (!(form == Form.ALIEN_X && style && !automated)) {
            WristNetwork.cue(p, rawAction, beat, skill.lastFlags, start, result);
        }
        boolean hit = (skill.lastFlags & WristNetwork.FLAG_HIT_ENTITY) != 0;
        if (automated) {
            WristService.announce(p, carniSound(form), .94f + .06f * beat + (hit ? .05f : 0));
            if (form == Form.FOUR_ARMS && hit) {
                p.level().playSound(null, BlockPos.containing(result), SoundEvents.PLAYER_ATTACK_STRONG,
                        SoundSource.PLAYERS, .55f, .76f + .08f * beat);
            }
            return;
        }
        if (form == Form.FOUR_ARMS) {
            if (!secondary) {
                WristService.announce(p, star ? Omnitrix.X_FOUR_ARMS_PUNCH.get() : Omnitrix.FOUR_ARMS_PUNCH.get(),
                        .88f + .08f * beat + (hit ? .06f : 0));
            }
            if (hit) {
                p.level().playSound(null, BlockPos.containing(result), SoundEvents.PLAYER_ATTACK_STRONG,
                        SoundSource.PLAYERS, .65f, .78f + .08f * beat);
            } else if (!secondary) {
                p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.PLAYERS, .55f, .82f + .09f * beat);
            }
            return;
        }
        if (form == Form.ALIEN_X && (secondary || style)) return;
        WristService.announce(p, skillSound(form, secondary, star), .96f + .04f * beat + (hit ? .05f : 0));
    }

    static void startAutomatic(ServerPlayer p, Wrist w, PursuitBody body, LivingEntity target, long now) {
        Form form = w.form;
        Vec3 eye = body.getEyePosition();
        Vec3 focus = target.getEyePosition();
        int style = switch (form) {
            case DIAMONDHEAD -> body.distanceToSqr(target) <= 4.2 * 4.2 ? 1 : 0;
            case RIPJAWS -> body.isInWater() ? 1 : 0;
            default -> 0;
        };
        int[] beats = switch (form) {
            case FOUR_ARMS, STINKFLY -> new int[]{6, 11};
            case ALIEN_X -> new int[]{8, 12};
            default -> new int[]{6};
        };
        w.skill = new SkillCast(AUTOMATED + form.ordinal(), style, now, beats, 6, eye, focus,
                focus.subtract(eye).normalize(), target.getUUID());
        w.revision++;
        WristNetwork.sync(p);
    }

    static boolean tickAutomatic(ServerPlayer p, Wrist w, PursuitBody body, LivingEntity target,
                                 boolean visible, double distance, double reach, long now) {
        SkillCast skill = w.skill;
        if (skill == null) return false;
        if (target == null || !target.getUUID().equals(skill.target) || !visible || distance > reach
                || !validTarget(p, target, true) || now >= w.until) {
            finishSkill(p, w);
            return false;
        }
        body.getNavigation().stop();
        if (w.form == Form.STINKFLY && skill.nextBeat == 0 && now > skill.motion.start()) {
            body.setDeltaMovement(body.getDeltaMovement().add(0, .06, 0));
        }
        if (skill.previousDivePosition != null) {
            Vec3 previous = skill.previousDivePosition;
            Vec3 center = body.getBoundingBox().getCenter();
            skill.previousDivePosition = center;
            var contact = target.getBoundingBox().inflate(body.getBbWidth() * .5, body.getBbHeight() * .5,
                    body.getBbWidth() * .5);
            if ((contact.contains(center) || contact.clip(previous, center).isPresent())
                    && skill.hit.add(target.getUUID())
                    && damage(p, target, 6_200f, target.position().subtract(body.position()), .65, 0, 20)) {
                skill.previousDivePosition = null;
                skill.lastFlags = WristNetwork.FLAG_HIT_ENTITY;
                cue(p, skill, 2, center, target.getBoundingBox().getCenter());
            }
        }
        while (skill.due(now)) {
            int beat = skill.nextBeat++;
            skill.lastFlags = 0;
            Vec3 eye = body.getEyePosition();
            Vec3 end = automaticImpact(p, body, target, skill, beat);
            cue(p, skill, beat, eye, end);
        }
        if (now >= skill.motion.end()) finishSkill(p, w);
        return true;
    }

    private static Vec3 automaticImpact(ServerPlayer p, PursuitBody body, LivingEntity target, SkillCast skill, int beat) {
        Form form = body.form();
        Vec3 eye = body.getEyePosition();
        Vec3 aim = target.getEyePosition().subtract(eye).normalize();
        Vec3 end = target.getEyePosition();
        switch (form) {
            case HEATBLAST -> fireCone(p, skill, eye, aim, 8, .71, 6_400f, 75);
            case WILDMUTT -> {
                body.setDeltaMovement(aim.scale(.65).add(0, .27, 0));
                if (body.distanceToSqr(target) <= 21 && damage(p, target, 6_800f, aim, .65, 0, 0)) {
                    skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                }
            }
            case DIAMONDHEAD -> {
                if (skill.motion.style() == 1 && body.distanceToSqr(target) <= 4.2 * 4.2) {
                    cone(p, skill, eye, aim, 4.2, .64, 7_500f, 0, 1.0, 0);
                } else cone(p, skill, eye, aim, 9, .88, 7_500f, 0, .7, 0);
            }
            case XLR8 -> {
                var side = aim.yRot((p.tickCount & 1) == 0 ? .22f : -.22f);
                body.setDeltaMovement(side.multiply(1.42, .22, 1.42));
                if (body.distanceToSqr(target) < 21 && damage(p, target, 8_200f, aim, 1.2, 0, 0)) {
                    skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                }
            }
            case GREY_MATTER -> {
                body.setDeltaMovement(aim.scale(.42).add(0, .28, 0));
                if (damage(p, target, 4_200f, aim, .22, 0, 25)) skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
            }
            case FOUR_ARMS -> {
                Vec3 sweep = aim.multiply(1, 0, 1).normalize().yRot(beat == 0 ? .48f : -.48f).scale(.7);
                Vec3 center = body.position().add(sweep);
                for (LivingEntity e : nearby(p, center, 3.9, true)) {
                    if (!clear(p, center.add(0, .35, 0), e.getEyePosition())
                            && !clear(p, body.getEyePosition(), e.getEyePosition())) continue;
                    Vec3 delta = e.position().subtract(center);
                    double intensity = 1 - .60 * Math.min(1, delta.length() / 3.9);
                    if (damage(p, e, (float) ((beat == 0 ? 8_400 : 11_500) * intensity), delta,
                            (beat == 0 ? .72 : 1.15) * intensity, 0, 0)) {
                        if (beat == 0) {
                            e.push(0, .21, 0);
                            e.hurtMarked = true;
                        }
                        skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                    }
                }
            }
            case STINKFLY -> {
                if (beat == 0) launch(p, Pulse.Variant.SLIME, eye, aim, 1.25);
                else {
                    body.setDeltaMovement(aim.x * .76, -.39, aim.z * .76);
                    skill.previousDivePosition = body.getBoundingBox().getCenter();
                }
            }
            case RIPJAWS -> {
                if (body.isInWater()) body.setDeltaMovement(aim.scale(.82));
                if (damage(p, target, body.isInWater() ? 9_500f : 6_800f, aim, .72, 0, 0)) {
                    skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                }
            }
            case UPGRADE -> direct(p, skill, eye, aim, 12, 7_800f, .34, 0);
            case GHOSTFREAK -> {
                Vec3 before = body.getEyePosition();
                Vec3 side = target.position().add(-aim.z * 1.25, 0, aim.x * 1.25);
                BlockPos feet = BlockPos.containing(side);
                if (side.distanceToSqr(body.position()) < 24 && p.mayInteract(p.level(), feet)
                        && WristService.fits(p, form.dimensions(Kind.CARNITRIX), side)
                        && clear(p, eye, side.add(0, .65, 0))) {
                    body.moveTo(side.x, side.y, side.z, body.getYRot(), 0);
                    WristNetwork.cue(p, skill.motion.action(), 1, 0, before, body.getEyePosition());
                }
                if (clear(p, body.getEyePosition(), end) && damage(p, target, 7_600f, aim, .5, 0, 0)) {
                    skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                }
            }
            case ALIEN_X -> {
                for (LivingEntity other : nearby(p, end, 8.0, true)) {
                    if (!clear(p, end, other.getEyePosition())) continue;
                    if (beat == 0) {
                        pull(other, end.add(0, .32, 0), .46);
                        continue;
                    }
                    float amount = (float) (85_000f * (1 - .50 * Math.min(1, other.distanceToSqr(end) / 64.0)));
                    if (damage(p, other, amount, Vec3.ZERO, 0, 0, 45)) {
                        pull(other, end.add(0, .32, 0), .78);
                        skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
                    }
                }
            }
        }
        return end;
    }

    static boolean validTarget(ServerPlayer p, LivingEntity e, boolean broad) {
        return PursuitBody.permitted(p, e) && (broad || e instanceof Enemy || e instanceof Player);
    }

    static boolean damage(ServerPlayer p, LivingEntity enemy, float amount, Vec3 direction, double knock, int fire, int slow) {
        if (!validTarget(p, enemy, true) || !enemy.hurt(p.damageSources().playerAttack(p), amount)) return false;
        if (fire > 0) enemy.igniteForTicks(fire);
        if (slow > 0) enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 1), p);
        if (knock > 0) enemy.knockback(knock, -direction.x, -direction.z);
        if (WristService.controlled(p)) {
            enemy.addEffect(new MobEffectInstance(Omnitrix.BLEED, 65, 0, false, true), p);
            enemy.addEffect(new MobEffectInstance(MobEffects.WITHER, 65, 0, false, false), p);
        }
        return true;
    }

    private static List<LivingEntity> nearby(ServerPlayer p, Vec3 location, double range, boolean broad) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, AABB.ofSize(location, range * 2, range * 2, range * 2),
                e -> e.distanceToSqr(location) <= range * range && validTarget(p, e, broad))
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(location))).limit(48).toList();
    }

    private static Vec3 cone(ServerPlayer p, SkillCast skill, Vec3 start, Vec3 direction, double range, double spread,
                             float power, int fire, double knock, int slow) {
        for (LivingEntity enemy : coneTargets(p, start, direction, range, spread, WristService.controlled(p))) {
            if (damage(p, enemy, power, enemy.getEyePosition().subtract(start), knock, fire, slow)) {
                skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
            }
        }
        return start.add(direction.scale(range));
    }

    private static List<LivingEntity> coneTargets(ServerPlayer p, Vec3 start, Vec3 direction, double range,
                                                    double spread, boolean broad) {
        Vec3 center = start.add(direction.scale(range * .52));
        return nearby(p, center, range, broad).stream().filter(enemy -> {
            Vec3 delta = enemy.getEyePosition().subtract(start);
            return delta.lengthSqr() <= range * range && delta.normalize().dot(direction) >= spread
                    && clear(p, start, enemy.getEyePosition());
        }).toList();
    }

    private static Vec3 fireCone(ServerPlayer player, SkillCast skill, Vec3 origin, Vec3 aim, double range, double spread, float power, int fire) {
        flameParticles(player, origin, aim, range, false);
        return cone(player, skill, origin, aim, range, spread, power, fire, .60, 0);
    }

    private static void flameParticles(ServerPlayer player, Vec3 origin, Vec3 aim, double range, boolean star) {
        var level = player.serverLevel();
        Vec3 end = rayEnd(player, origin, aim, range);
        double distance = origin.distanceTo(end);
        for (double step = .65; step < distance; step += .65) {
            Vec3 part = origin.add(aim.scale(step));
            var particle = star ? (step < distance * .65 ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME)
                    : (step < distance * .54 ? ParticleTypes.FLAME : step < distance * .83 ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE);
            level.sendParticles(particle, part.x, part.y, part.z, 3, step * .095, step * .087, step * .095, .024);
        }
    }

    private static Vec3 direct(ServerPlayer p, SkillCast skill, Vec3 eye, Vec3 direction, double range, float power, double knock, int slow) {
        Vec3 end = rayEnd(p, eye, direction, range);
        EntityHitResult result = rayTarget(p, eye, end);
        if (result != null && result.getEntity() instanceof LivingEntity target) {
            if (damage(p, target, power, direction, knock, 0, slow)) {
                skill.lastFlags |= WristNetwork.FLAG_HIT_ENTITY;
            }
            return result.getLocation();
        }
        return end;
    }

    private static EntityHitResult rayTarget(ServerPlayer p, Vec3 start, Vec3 end) {
        boolean broad = WristService.controlled(p);
        return ProjectileUtil.getEntityHitResult(p.level(), p, start, end, new AABB(start, end).inflate(1),
                entity -> entity instanceof LivingEntity e && validTarget(p, e, broad), .16f);
    }

    private static LivingEntity enemyOnRay(ServerPlayer p, Vec3 start, Vec3 aim, double range) {
        var hit = rayTarget(p, start, rayEnd(p, start, aim, range));
        return hit != null && hit.getEntity() instanceof LivingEntity e ? e : null;
    }

    private static Vec3 dash(ServerPlayer p, SkillCast skill, Vec3 face, double force, float damage, double lift) {
        Vec3 toward = face.multiply(1, 0, 1).normalize();
        Vec3 from = p.getEyePosition();
        Vec3 to = direct(p, skill, from, toward, 2.65 + force, damage, .55, 0);
        moveDash(p, face, force, lift);
        return to;
    }

    private static void moveDash(ServerPlayer p, Vec3 face, double force, double lift) {
        boolean water = p.isInWater();
        Vec3 toward = water ? face : face.multiply(1, 0, 1).normalize();
        p.setDeltaMovement(toward.x * force, water ? Math.max(-.4, face.y * force) : lift, toward.z * force);
        p.hurtMarked = true;
        p.resetFallDistance();
    }

    private static Vec3 launch(ServerPlayer p, Pulse.Variant variant, Vec3 eye, Vec3 direction, double speed) {
        return launch(p, variant, eye, direction, speed, 1, Long.MIN_VALUE);
    }

    private static Vec3 launch(ServerPlayer p, Pulse.Variant variant, Vec3 eye, Vec3 direction, double speed, float fraction, long mark) {
        Pulse pulse = Omnitrix.PULSE.get().create(p.serverLevel());
        if (pulse == null) return null;
        Vec3 start = eye.add(direction.scale(.46)).add(0, -.12, 0);
        pulse.initialize(p, variant, start, direction.scale(speed), WristService.controlled(p), fraction, mark);
        return p.serverLevel().addFreshEntity(pulse) ? start.add(direction.scale(3)) : null;
    }

    private static HitResult miss(ServerPlayer p, Vec3 start, Vec3 direction, double reach) {
        return p.level().clip(new ClipContext(start, start.add(direction.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
    }

    private static Vec3 rayEnd(ServerPlayer p, Vec3 eye, Vec3 direction, double reach) {
        HitResult hit = miss(p, eye, direction, reach);
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(direction.scale(.13)) : hit.getLocation();
    }

    private static boolean clear(ServerPlayer p, Vec3 from, Vec3 to) {
        return p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }

    private static net.minecraft.world.phys.BlockHitResult aimedLever(ServerPlayer p, Vec3 eye, Vec3 face, double reach) {
        var level = p.serverLevel();
        var block = level.clip(new ClipContext(eye, eye.add(face.scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return block.getType() == HitResult.Type.BLOCK && level.getBlockState(block.getBlockPos()).getBlock() instanceof LeverBlock
                && p.mayInteract(level, block.getBlockPos()) ? block : null;
    }

    private static Vec3 lever(ServerPlayer p, Vec3 eye, Vec3 face, BlockPos expected, double reach) {
        var level = p.serverLevel();
        var block = aimedLever(p, eye, face, reach);
        if (block == null || !expected.equals(block.getBlockPos())) return null;
        var pos = block.getBlockPos();
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof LeverBlock lever && p.mayInteract(level, pos)) {
            var use = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos, block);
            if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(use).isCanceled()) return null;
            lever.pull(state, level, pos, p);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 13, .22, .22, .22, .02);
            return Vec3.atCenterOf(pos);
        }
        return null;
    }

    private static Vec3 crystalWall(ServerPlayer p, Vec3 face, Wrist w, int durationTicks, long now) {
        var level = p.serverLevel();
        Vec3 horizontal = face.multiply(1, 0, 1).normalize();
        if (horizontal.lengthSqr() < .01) return null;
        BlockPos anchor = BlockPos.containing(p.position().add(horizontal.scale(2.25)));
        Direction axis = Direction.getNearest(horizontal.x, 0, horizontal.z).getClockWise();
        int installed = 0;
        for (int side = -1; side <= 1; side++) {
            for (int height = 0; height < (side == 0 ? 2 : 1); height++) {
                BlockPos pos = anchor.relative(axis, side).above(height);
                if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !p.mayInteract(level, pos)
                        || !level.isEmptyBlock(pos) || !level.getEntities(p, new AABB(pos), Entity::isAlive).isEmpty()) continue;
                var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
                if (level.setBlock(pos, Omnitrix.CRYSTAL.get().defaultBlockState(), Block.UPDATE_ALL)) {
                    if (EventHooks.onBlockPlace(p, snapshot, Direction.UP)) snapshot.restore();
                    else {
                        level.scheduleTick(pos, Omnitrix.CRYSTAL.get(), durationTicks);
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos.below())),
                                pos.getX() + .5, pos.getY() + .13, pos.getZ() + .5, 11, .33, .11, .33, .05);
                        level.sendParticles(ParticleTypes.WAX_OFF, pos.getX() + .5, pos.getY() + .62, pos.getZ() + .5, 7, .27, .35, .27, .024);
                        installed++;
                        w.markedCrystals.add(pos.immutable());
                    }
                }
            }
        }
        if (installed == 0) return null;
        w.linkUntil = now + LINK_TICKS;
        level.playSound(null, anchor, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, .93f, 1.15f);
        return Vec3.atCenterOf(anchor);
    }

    private static void steerDash(ServerPlayer p, Wrist w) {
        if (p.horizontalCollision || w.steerHeading.lengthSqr() < .01) return;
        Vec3 move = p.getDeltaMovement();
        double speed = move.horizontalDistance();
        if (speed < .27) return;
        Vec3 aim = p.getLookAngle().multiply(1, 0, 1).normalize();
        if (w.steerHeading.dot(aim) < .25) return;
        Vec3 next = w.steerHeading.lerp(aim, .22).normalize();
        if (next.distanceToSqr(w.steerHeading) < .0005) return;
        w.steerHeading = next;
        p.setDeltaMovement(next.x * speed, move.y, next.z * speed);
        p.hurtMarked = true;
    }

    private static Vec3 shadowStep(ServerPlayer p, Vec3 direction) {
        return clearStep(p, direction.multiply(1, 0, 1).normalize(), 2.5);
    }

    private static Vec3 clearStep(ServerPlayer p, Vec3 heading, double distance) {
        if (heading.lengthSqr() < .01) return null;
        Vec3 start = p.position();
        Vec3 last = null;
        for (double step = .25; step <= distance; step += .25) {
            Vec3 at = start.add(heading.scale(step));
            if (!WristService.humanFits(p, at) || !p.mayInteract(p.level(), BlockPos.containing(at))) break;
            last = at;
        }
        return last;
    }

    static Vec3 phaseDestination(ServerPlayer p, Vec3 direction) {
        Vec3 face = direction.multiply(1, 0, 1).normalize();
        if (face.lengthSqr() < .01) return null;
        Vec3 start = p.position();
        BlockPos previous = null;
        boolean wall = false;
        for (double step = .42; step <= 3.8; step += .18) {
            Vec3 here = start.add(face.scale(step));
            BlockPos feet = BlockPos.containing(here);
            if (!p.level().hasChunkAt(feet) || !p.level().getWorldBorder().isWithinBounds(feet)) return null;
            BlockPos at = BlockPos.containing(here.add(0, .9, 0));
            var state = p.level().getBlockState(at);
            if (!state.getCollisionShape(p.level(), at).isEmpty()) {
                if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.hasBlockEntity() || !p.mayInteract(p.level(), at)) return null;
                if (previous != null && !at.equals(previous)) return null;
                previous = at;
                wall = true;
            }
            if (wall && !at.equals(previous) && WristService.humanFits(p, here) && p.mayInteract(p.level(), feet)
                    && !p.level().getBlockState(feet.below()).getCollisionShape(p.level(), feet.below()).isEmpty()) return here;
        }
        return null;
    }
}
