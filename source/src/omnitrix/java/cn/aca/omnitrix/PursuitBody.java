package cn.aca.omnitrix;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class PursuitBody extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SHAPE = SynchedEntityData.defineId(PursuitBody.class, EntityDataSerializers.INT);
    public static final TagKey<EntityType<?>> PROTECTED = TagKey.create(Registries.ENTITY_TYPE, Omnitrix.id("protected"));
    private static final int ACQUIRE_RANGE = 16;
    private static final int CHASE_RANGE = 24;
    private UUID owner;
    private boolean releasing;
    private long strikeAt;
    private BlockPos lastTrail;

    public PursuitBody(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setSilent(true);
        setPersistenceRequired();
    }

    static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, .27).add(Attributes.FOLLOW_RANGE, CHASE_RANGE)
                .add(Attributes.FLYING_SPEED, .33).add(Attributes.ATTACK_DAMAGE, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHAPE, Form.HEATBLAST.ordinal());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> field) {
        super.onSyncedDataUpdated(field);
        if (SHAPE.equals(field)) refreshDimensions();
    }

    public Form form() { return Form.at(entityData.get(SHAPE)); }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return form().dimensions(Kind.CARNITRIX);
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource source, float damage) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override protected boolean canAddPassenger(Entity candidate) { return candidate.getUUID().equals(owner) && !isVehicle(); }
    @Override protected void positionRider(Entity passenger, Entity.MoveFunction move) { move.accept(passenger, getX(), getY(), getZ()); }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity rider) { return position(); }

    private void initialize(ServerPlayer player, Form shape, long now) {
        owner = player.getUUID();
        entityData.set(SHAPE, shape.ordinal());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(shape == Form.XLR8 ? .40 : .27);
        getAttribute(Attributes.STEP_HEIGHT).setBaseValue(shape == Form.XLR8 ? 1.0 : .66);
        if (shape.flies()) {
            navigation = new FlyingPathNavigation(this, level());
            moveControl = new FlyingMoveControl(this, 20, true);
            setNoGravity(true);
        } else {
            navigation = new GroundPathNavigation(this, level());
            moveControl = new MoveControl(this);
        }
        moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        strikeAt = now + 8;
    }

    static void ensure(ServerPlayer p, Wrist wrist) {
        if (p.getVehicle() instanceof PursuitBody) return;
        PursuitBody body = Omnitrix.PURSUER.get().create(p.serverLevel());
        if (body == null) return;
        body.initialize(p, wrist.form, WristService.time(p));
        if (!p.serverLevel().addFreshEntity(body) || !p.startRiding(body, true)) body.discard();
    }

    void release() {
        releasing = true;
        ejectPassengers();
        discard();
    }

    public static boolean isAutomatedStrike(ServerPlayer player) {
        return player.getVehicle() instanceof PursuitBody body && body.attacking;
    }

    private boolean attacking;

    static void detect(ServerPlayer player, Wrist wrist) {
        var nearby = candidates(player, ACQUIRE_RANGE, true);
        if (nearby.isEmpty()) return;
        if (nearby.stream().allMatch(target -> civilian(player, target))) {
            WristService.beginResistance(player, wrist, nearby.getFirst().getId(), WristService.time(player));
            return;
        }
        engage(player, wrist, nearby.stream().filter(target -> permitted(player, target, false)).toList(), false);
    }

    static void updateResistance(ServerPlayer player, Wrist wrist, long now) {
        var nearby = candidates(player, ACQUIRE_RANGE, true);
        if (nearby.isEmpty() || now >= wrist.until || nearby.stream().anyMatch(target -> !civilian(player, target))) {
            wrist.resistance = 0;
            wrist.targetId = -1;
            wrist.enter(Wrist.Stage.IDLE, now, 0);
            boolean civiliansOnly = !nearby.isEmpty() && nearby.stream().allMatch(target -> civilian(player, target));
            engage(player, wrist, civiliansOnly ? nearby : candidates(player, ACQUIRE_RANGE), civiliansOnly);
            if (wrist.stage == Wrist.Stage.IDLE) WristNetwork.sync(player);
        } else if (nearby.getFirst().getId() != wrist.targetId) {
            wrist.targetId = nearby.getFirst().getId();
            wrist.revision++;
            WristNetwork.sync(player);
        }
    }

    private static void engage(ServerPlayer player, Wrist wrist, List<LivingEntity> crowd, boolean civilians) {
        if (crowd.isEmpty()) return;
        LivingEntity target = crowd.getFirst();
        Form shape;
        if (crowd.size() >= 5 || crowd.stream().anyMatch(e -> e.getMaxHealth() >= 60)) shape = Form.ALIEN_X;
        else if (player.isInWater() || target.isInWater()) shape = Form.RIPJAWS;
        else if (target.getY() - player.getY() > 3) shape = Form.STINKFLY;
        else if (target.distanceToSqr(player) > 121) shape = Form.XLR8;
        else {
            shape = Form.CLASSIC.get(wrist.carniCursor);
            wrist.carniCursor = (wrist.carniCursor + 1) % Form.CLASSIC.size();
        }
        if (!WristService.fits(player, shape.dimensions(Kind.CARNITRIX), player.position())) {
            shape = Form.GREY_MATTER;
        }
        if (WristService.fits(player, shape.dimensions(Kind.CARNITRIX), player.position())) {
            wrist.civilianOverride = civilians;
            if (!WristService.start(player, shape, target.getId())) wrist.civilianOverride = false;
        }
    }

    static boolean permitted(ServerPlayer attacker, LivingEntity target) {
        return permitted(attacker, target, attacker.hasData(Omnitrix.WRIST) && Omnitrix.wrist(attacker).civilianOverride);
    }

    private static boolean civilian(ServerPlayer attacker, LivingEntity target) {
        return target instanceof AbstractVillager || target instanceof TamableAnimal pet && attacker.getUUID().equals(pet.getOwnerUUID());
    }

    private static boolean permitted(ServerPlayer attacker, LivingEntity target, boolean civilians) {
        if (target == attacker || target instanceof PursuitBody || target.getType().is(PROTECTED) || target.isRemoved()
                || !target.isAlive() || target.isInvulnerable() || target.isSpectator()) return false;
        boolean ownPet = target instanceof TamableAnimal pet && attacker.getUUID().equals(pet.getOwnerUUID());
        if (ownPet && !civilians) return false;
        if (attacker.isAlliedTo(target)) return false;
        if (target instanceof Player player && (player.isCreative() || !attacker.canHarmPlayer(player))) return false;
        return !target.isInvulnerableTo(attacker.damageSources().playerAttack(attacker));
    }

    private static List<LivingEntity> candidates(ServerPlayer player, int radius) {
        return candidates(player, radius, player.hasData(Omnitrix.WRIST) && Omnitrix.wrist(player).civilianOverride);
    }

    private static List<LivingEntity> candidates(ServerPlayer player, int radius, boolean civilians) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                target -> target.distanceToSqr(player) <= radius * radius && permitted(player, target, civilians) && player.hasLineOfSight(target))
                .stream().sorted(Comparator.<LivingEntity>comparingDouble(player::distanceToSqr).thenComparingInt(Entity::getId)).toList();
    }

    @Override
    protected void customServerAiStep() {
        if (!(getFirstPassenger() instanceof ServerPlayer player) || !player.isAlive()) {
            release();
            return;
        }
        var wrist = Omnitrix.wrist(player);
        if (!wrist.controlled()) { release(); return; }
        if (wrist.stage() != Wrist.Stage.ACTIVE) { getNavigation().stop(); return; }
        long now = WristService.time(player);
        LivingEntity target = level().getEntity(wrist.targetId) instanceof LivingEntity living
                && living.distanceToSqr(this) <= CHASE_RANGE * CHASE_RANGE && permitted(player, living) ? living : null;
        if (now % 8 == 0 && (target == null || (!getSensing().hasLineOfSight(target) && getNavigation().isDone()))) {
            var nearby = candidates(player, CHASE_RANGE);
            if (!nearby.isEmpty()) target = nearby.getFirst();
            else target = null;
            int id = target == null ? -1 : target.getId();
            if (id != wrist.targetId) {
                wrist.targetId = id;
                wrist.revision++;
                WristNetwork.sync(player);
            }
        }
        if (target == null) {
            if (wrist.skill != null) Abilities.tickAutomatic(player, wrist, this, null, false, 0, 0, now);
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(.65, form().flies() ? .65 : 1, .65));
            return;
        }
        Form form = form();
        if (form == Form.DIAMONDHEAD && onGround()) {
            BlockPos location = blockPosition();
            if (lastTrail != null && !lastTrail.equals(location) && lastTrail.distManhattan(location) <= 2) {
                SurvivalForms.leaveSpike(player, lastTrail);
            }
            lastTrail = location.immutable();
        }
        getLookControl().setLookAt(target, 35, 25);
        Vec3 delta = target.position().subtract(position());
        double distance = delta.length();
        boolean visible = getSensing().hasLineOfSight(target);
        double attackRange = switch (form) {
            case ALIEN_X -> 12;
            case DIAMONDHEAD, UPGRADE, HEATBLAST, STINKFLY -> 8;
            case XLR8, WILDMUTT, GHOSTFREAK -> 5;
            case RIPJAWS, FOUR_ARMS -> 3.4;
            case GREY_MATTER -> 2.25;
        };
        if (wrist.skill != null) {
            attacking = true;
            try {
                if (Abilities.tickAutomatic(player, wrist, this, target, visible, distance, attackRange, now)) return;
            } finally {
                attacking = false;
            }
        }
        if (now % 5 == 0) {
            if (form == Form.UPGRADE && distance < 3.5 && visible) {
                Vec3 away = position().subtract(delta.normalize().scale(4));
                getNavigation().moveTo(away.x, away.y, away.z, 1.15);
            } else if (form == Form.RIPJAWS && isInWater()) {
                Vec3 heading = delta.normalize().scale(.18);
                setDeltaMovement(getDeltaMovement().scale(.45).add(heading));
            } else if (form == Form.STINKFLY || form == Form.ALIEN_X) {
                double height = form == Form.STINKFLY ? 1.9 : .7;
                getNavigation().moveTo(target.getX(), target.getY() + height, target.getZ(), 1.16);
            } else if (distance > Math.min(attackRange * .72, 4) || !visible) {
                getNavigation().moveTo(target, form == Form.XLR8 ? 1.35 : 1.12);
                if (form == Form.GREY_MATTER && horizontalCollision && getNavigation().isDone()) {
                    setDeltaMovement(getDeltaMovement().add(0, .19, 0));
                }
                if (form == Form.GHOSTFREAK && !visible && getNavigation().isDone() && distance < 5) {
                    Vec3 gap = Abilities.phaseDestination(player, delta.normalize());
                    if (gap != null) moveTo(gap.x, gap.y, gap.z, getYRot(), 0);
                }
            } else getNavigation().stop();
        }
        if (visible && distance <= attackRange && now >= strikeAt) {
            Abilities.startAutomatic(player, wrist, this, target, now);
            strikeAt = now + switch (form) {
                case ALIEN_X -> 72;
                case XLR8, GREY_MATTER -> 22;
                case FOUR_ARMS, WILDMUTT, RIPJAWS -> 25;
                default -> 37;
            };
        }
    }

    static void register(IEventBus bus) {
        bus.addListener(PursuitBody::preventDismount);
        bus.addListener(EventPriority.HIGHEST, PursuitBody::preventAttack);
        bus.addListener(EventPriority.HIGHEST, PursuitBody::preventInteract);
        bus.addListener(EventPriority.HIGHEST, PursuitBody::preventUse);
        bus.addListener(EventPriority.HIGHEST, PursuitBody::preventBlock);
        bus.addListener(EventPriority.HIGHEST, PursuitBody::preventDig);
        bus.addListener(EventPriority.HIGHEST, PursuitBody::preventBreak);
        bus.addListener(EventPriority.HIGHEST, PursuitBody::preventChangeBlock);
    }

    private static boolean locked(Player p) {
        return p instanceof ServerPlayer server && WristService.controlled(server);
    }

    private static void preventDismount(EntityMountEvent e) {
        if (e.isDismounting() && e.getEntityBeingMounted() instanceof PursuitBody body && !body.releasing
                && e.getEntityMounting() instanceof ServerPlayer p && p.isAlive() && locked(p)) e.setCanceled(true);
    }

    private static void preventAttack(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && locked(p) && !isAutomatedStrike(p)) e.setCanceled(true);
    }

    private static void preventInteract(PlayerInteractEvent.EntityInteract e) {
        if (locked(e.getEntity())) { e.setCancellationResult(InteractionResult.FAIL); e.setCanceled(true); }
    }

    private static void preventUse(PlayerInteractEvent.RightClickItem e) {
        if (locked(e.getEntity())) { e.setCancellationResult(InteractionResult.FAIL); e.setCanceled(true); }
    }

    private static void preventBlock(PlayerInteractEvent.RightClickBlock e) {
        if (locked(e.getEntity())) { e.setCancellationResult(InteractionResult.FAIL); e.setCanceled(true); }
    }

    private static void preventDig(PlayerInteractEvent.LeftClickBlock e) {
        if (locked(e.getEntity())) e.setCanceled(true);
    }

    private static void preventBreak(BlockEvent.BreakEvent e) {
        if (locked(e.getPlayer())) e.setCanceled(true);
    }

    private static void preventChangeBlock(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open e) {
        if (e.getEntity() instanceof ServerPlayer player && locked(player)) player.closeContainer();
    }
}
