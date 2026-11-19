package cn.aca.omnitrix;

import cn.aca.omnitrix.Wrist.Stage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class WristService {
    private static final ResourceLocation MODIFIER = Omnitrix.id("form");
    private static final Holder<Attribute> FLIGHT = NeoForgeMod.CREATIVE_FLIGHT;
    private static final int REVERT_TICKS = 10;
    private static final int RESIST_COOLDOWN_TICKS = 200;
    public static final int CLASSIC_WARNING_TICKS = 200;
    public static final int RESIST_PRESSES = 6;

    private WristService() {}

    static void register(IEventBus bus) {
        bus.addListener(WristService::tick);
        bus.addListener(WristService::size);
        bus.addListener(WristService::login);
        bus.addListener(WristService::logout);
        bus.addListener(WristService::track);
        bus.addListener(WristService::changedDimension);
        bus.addListener(WristService::beginDimensionTravel);
        bus.addListener(WristService::cloned);
        bus.addListener(WristService::respawn);
        bus.addListener(EventPriority.LOWEST, WristService::drops);
        bus.addListener(EventPriority.LOWEST, WristService::failsafe);
        bus.addListener(WristService::recordDamage);
        bus.addListener(WristService::mounting);
        bus.addListener(WristService::stop);
    }

    public static long time(ServerPlayer player) { return player.server.overworld().getGameTime(); }

    public static boolean controlled(ServerPlayer player) {
        return player.hasData(Omnitrix.WRIST) && Omnitrix.wrist(player).controlled();
    }

    public static boolean equip(ServerPlayer player, ItemStack stack) {
        var wrist = Omnitrix.wrist(player);
        if (!(stack.getItem() instanceof WatchItem watch) || !eligible(player)) return false;
        if (wrist.kind() != null) return tell(player, "full");
        long now = time(player);
        if (now < wrist.equipReadyAt) return tell(player, "charging");
        wrist.stack = stack.split(1);
        wrist.carniLocked = watch.kind() == Kind.CARNITRIX;
        wrist.lastHumanClear = null;
        wrist.enter(Stage.BINDING, now, now + 16);
        announce(player, Omnitrix.LATCH.get(), .82f + watch.kind().ordinal() * .12f);
        WristNetwork.sync(player);
        return true;
    }

    public static boolean unequip(ServerPlayer player, boolean station) {
        var wrist = Omnitrix.wrist(player);
        if (wrist.kind() == null) return tell(player, "empty");
        if (wrist.stage != Stage.IDLE && wrist.stage != Stage.COOLING) return tell(player, "busy");
        if (wrist.carniLocked && !(station && wrist.stage == Stage.IDLE)) return tell(player, "quarantine");
        if (!eligible(player)) return false;
        ItemStack remainder = wrist.stack.copy();
        if (!player.getInventory().add(remainder) || !remainder.isEmpty()) return tell(player, "inventory_full");
        wrist.stack = ItemStack.EMPTY;
        wrist.carniLocked = false;
        wrist.targetId = -1;
        wrist.form = null;
        wrist.enter(Stage.IDLE, time(player), 0);
        announce(player, Omnitrix.LATCH.get(), .78f);
        WristNetwork.sync(player);
        return true;
    }

    public static boolean eligible(ServerPlayer p) {
        return mobile(p) && p.containerMenu == p.inventoryMenu;
    }

    private static boolean mobile(ServerPlayer p) {
        return p.isAlive() && !p.isRemoved() && !p.isSpectator() && !p.isPassenger() && !p.isSleeping() && !p.isFallFlying();
    }

    private static boolean assimilatedRide(ServerPlayer p) {
        return p.getVehicle() instanceof net.minecraft.world.entity.vehicle.AbstractMinecart cart
                && SurvivalForms.mayRideMachine(p, cart);
    }

    static void request(ServerPlayer player, WristNetwork.Request packet) {
        var wrist = Omnitrix.wrist(player);
        long now = time(player);
        if (!player.isAlive() || player.isSpectator() || wrist.kind() == null) return;
        if (packet.action() == WristNetwork.CLOSE) {
            if (wrist.panelUp) {
                wrist.panelUp = false;
                wrist.revision++;
                WristNetwork.sync(player);
            }
            return;
        }
        if (wrist.controlled()) return;
        boolean skillRequest = packet.action() == WristNetwork.PRIMARY || packet.action() == WristNetwork.SECONDARY
                || packet.action() == WristNetwork.FLICK;
        if (!skillRequest) {
            if (wrist.lastRequest == now) return;
            wrist.lastRequest = now;
        }
        if (wrist.stage == Stage.LOCKING) {
            if (packet.action() == WristNetwork.RESIST) resist(player, wrist, now);
            return;
        }
        switch (packet.action()) {
            case WristNetwork.OPEN -> {
                if (!eligible(player) || wrist.stage == Stage.BINDING || wrist.hasForm() || wrist.stage == Stage.ARMING) return;
                if (wrist.lastPanel != Long.MIN_VALUE && now - wrist.lastPanel < 3) return;
                wrist.lastPanel = now;
                wrist.hurriedAtOpen = player.isSprinting() || player.fallDistance > 2;
                wrist.panelUp = true;
                wrist.revision++;
                WristNetwork.sync(player);
            }
            case WristNetwork.TRANSFORM -> {
                if (wrist.panelUp && !select(player, wrist, Form.at(packet.value()), now)) close(player, wrist);
            }
            case WristNetwork.UNEQUIP -> unequip(player, false);
            case WristNetwork.REVERT -> {
                if (wrist.stage == Stage.ACTIVE) revert(player, now);
            }
            case WristNetwork.PRIMARY, WristNetwork.SECONDARY -> {
                if (wrist.stage == Stage.ACTIVE && eligible(player)) {
                    Abilities.cast(player, wrist, packet.action() == WristNetwork.SECONDARY, (packet.value() & 1) != 0, now);
                }
            }
            case WristNetwork.FLICK -> {
                if (wrist.stage == Stage.ACTIVE && eligible(player)) {
                    Abilities.realityFlick(player, wrist, now);
                }
            }
            default -> {}
        }
    }

    private static boolean select(ServerPlayer player, Wrist wrist, Form requested, long now) {
        if (requested == null || !requested.canUse(wrist.kind()) || wrist.stage != Stage.IDLE || !eligible(player)) return false;
        Form selected = requested;
        boolean panicked = player.isSprinting() || player.fallDistance > 2 || now - 14 <= wrist.lastDamageAt
                || wrist.hurriedAtOpen && now - 12 <= wrist.lastPanel;
        if (wrist.kind() == Kind.CLASSIC && panicked && player.getRandom().nextFloat() < .08f) {
            var alternatives = Form.CLASSIC.stream().filter(form -> form != requested)
                    .filter(form -> fits(player, form.dimensions(Kind.CLASSIC), player.position())).toList();
            if (!alternatives.isEmpty()) selected = alternatives.get(player.getRandom().nextInt(alternatives.size()));
        }
        if (!start(player, selected, -1)) return false;
        if (selected != requested) {
            wrist.misfire = true;
            wrist.revision++;
            announce(player, Omnitrix.MISFIRE.get(), 1f);
            WristNetwork.sync(player);
        }
        return true;
    }

    private static void recordDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && event.getEntity() instanceof ServerPlayer player && player.hasData(Omnitrix.WRIST)) {
            Omnitrix.wrist(player).lastDamageAt = time(player);
        }
    }

    public static boolean start(ServerPlayer player, Form form, int targetId) {
        var wrist = Omnitrix.wrist(player);
        Kind kind = wrist.kind();
        long now = time(player);
        if (kind == null || !(kind == Kind.CARNITRIX ? mobile(player) : eligible(player))
                || wrist.stage != Stage.IDLE || now < wrist.equipReadyAt) {
            return tell(player, "charging");
        }
        if (form == null || !form.canUse(kind) || (kind == Kind.CARNITRIX && targetId < 0)) return false;
        if (!fits(player, form.dimensions(kind), player.position())) return tell(player, "no_space");
        prepare(player, wrist, form, targetId);
        AscalonService.onOmnitrixTransformStart(player);
        wrist.enter(Stage.ARMING, now, now + kind.windup);
        if (kind == Kind.CARNITRIX) {
            player.stopUsingItem();
            player.setSprinting(false);
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
            player.hurtMarked = true;
            if (player.containerMenu != player.inventoryMenu) player.closeContainer();
            AcademyInterop.releaseHeldAbility(player);
        }
        announce(player, switch (kind) {
            case CLASSIC -> Omnitrix.CLASSIC_SHIFT.get();
            case CELESTIAL -> Omnitrix.X_SHIFT.get();
            case CARNITRIX -> Omnitrix.CARNI_SHIFT.get();
        }, 1f);
        WristNetwork.sync(player);
        return true;
    }

    private static void prepare(ServerPlayer player, Wrist wrist, Form form, int targetId) {
        wrist.form = form;
        wrist.targetId = targetId;
        wrist.primaryAt = 0;
        wrist.secondaryAt = 0;
        wrist.misfire = false;
        wrist.warningSecond = -1;
        wrist.dryTicks = 0;
        wrist.lastHumanClear = humanFits(player, player.position()) ? player.position() : null;
    }

    private static void failsafe(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(Omnitrix.WRIST)
                || absoluteDamage(event.getSource()) || player.isSpectator() || player.isPassenger()) return;
        Wrist wrist = Omnitrix.wrist(player);
        long now = time(player);

        // 1. Alien X (X God) Existential Death-Denial Rewrite: refuses any non-absolute death while active!
        if (wrist.hasForm() && wrist.kind() == Kind.CELESTIAL && wrist.form() == Form.ALIEN_X) {
            event.setCanceled(true);
            player.resetFallDistance();
            player.clearFire();
            player.removeAllEffects();
            reviveAtFullHealth(player);
            wrist.maxBarrier = Form.ALIEN_X.maxBarrier(Kind.CELESTIAL);
            wrist.barrier = wrist.maxBarrier;
            Abilities.triggerExistentialRewrite(player, wrist, now);
            announce(player, Omnitrix.X_SHIFT.get(), 1.15f);
            WristNetwork.sync(player);
            return;
        }

        // 2. X-Watch (Celestial) Emergency Failsafe from human IDLE -> instant zero-windup Alien X!
        if (wrist.kind() == Kind.CELESTIAL && wrist.stage == Stage.IDLE && now >= wrist.equipReadyAt
                && fits(player, Form.ALIEN_X.dimensions(Kind.CELESTIAL), player.position())) {
            event.setCanceled(true);
            player.resetFallDistance();
            player.clearFire();
            player.stopUsingItem();
            if (player.containerMenu != player.inventoryMenu) player.closeContainer();
            prepare(player, wrist, Form.ALIEN_X, -1);
            activate(player, now);
            reviveAtFullHealth(player);
            announce(player, Omnitrix.ALIEN_X_REWRITE.get(), 1.0f);
            WristNetwork.cue(player, WristNetwork.REWRITE_CUE, player.position(), player.getEyePosition());
            return;
        }

        // 3. Classic Omnitrix Emergency Failsafe -> Diamondhead / Four Arms at 1 HP + full DNA Barrier
        if (wrist.kind() != Kind.CLASSIC || wrist.stage != Stage.IDLE || now < wrist.equipReadyAt) return;
        Form form = null;
        for (Form candidate : new Form[]{Form.DIAMONDHEAD, Form.FOUR_ARMS}) {
            if (fits(player, candidate.dimensions(Kind.CLASSIC), player.position())) {
                form = candidate;
                break;
            }
        }
        if (form == null) return;
        event.setCanceled(true);
        player.setHealth(1f);
        player.resetFallDistance();
        player.clearFire();
        player.stopUsingItem();
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        prepare(player, wrist, form, -1);
        activate(player, now);
        announce(player, Omnitrix.FAILSAFE.get(), 1f);
        WristNetwork.cue(player, WristNetwork.FAILSAFE_CUE, player.position(), player.getEyePosition());
    }

    static void beginResistance(ServerPlayer player, Wrist wrist, int targetId, long now) {
        wrist.resistance = 0;
        wrist.lastResist = Long.MIN_VALUE;
        wrist.targetId = targetId;
        wrist.enter(Stage.LOCKING, now, now + 30);
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        announce(player, Omnitrix.HEARTBEAT.get(), .83f);
        WristNetwork.sync(player);
    }

    private static void resist(ServerPlayer player, Wrist wrist, long now) {
        if (now >= wrist.until || now - 2 < wrist.lastResist) return;
        wrist.lastResist = now;
        if (++wrist.resistance < RESIST_PRESSES) {
            wrist.revision++;
            WristNetwork.sync(player);
            return;
        }
        var food = player.getFoodData();
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - 8));
        food.setSaturation(Math.min(food.getSaturationLevel(), food.getFoodLevel()));
        wrist.resistance = 0;
        wrist.targetId = -1;
        wrist.enter(Stage.COOLING, now, now + RESIST_COOLDOWN_TICKS);
        announce(player, Omnitrix.RESTORE.get(), .74f);
        WristNetwork.sync(player);
        WristNetwork.cue(player, WristNetwork.RESISTED_CUE, player.position(), player.position());
        tell(player, "resisted");
    }

    static void reviveAtFullHealth(Player player) {
        // A death-cancelled entity must be alive before vanilla healing can run.
        if (player.getHealth() <= 0) player.setHealth(1);
        float missing = player.getMaxHealth() - player.getHealth();
        if (missing > 0) player.heal(missing);
    }

    static boolean humanFits(ServerPlayer player, Vec3 pos) {
        return fits(player, player.getType().getDimensions(), pos);
    }

    static boolean fits(ServerPlayer player, EntityDimensions dimensions, Vec3 pos) {
        var bounds = dimensions.makeBoundingBox(pos).deflate(1.0e-5);
        var level = player.serverLevel();
        return pos.y >= level.getMinBuildHeight() && bounds.maxY < level.getMaxBuildHeight()
                && level.hasChunksAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))
                && level.getWorldBorder().isWithinBounds(bounds) && level.noBlockCollision(player, bounds);
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(Omnitrix.WRIST)) return;
        var wrist = Omnitrix.wrist(player);
        if (wrist.kind() == null || !player.isAlive()) return;
        long now = time(player);
        if (player.isSpectator() || player.isSleeping() || (player.isPassenger() && !(player.getVehicle() instanceof PursuitBody)
                && !assimilatedRide(player))) {
            if (wrist.hasForm() || wrist.stage == Stage.ARMING) finish(player, now);
            if (wrist.stage == Stage.LOCKING) {
                wrist.resistance = 0;
                wrist.targetId = -1;
                wrist.enter(Stage.IDLE, now, 0);
                WristNetwork.sync(player);
            }
            if (wrist.panelUp) close(player, wrist);
            return;
        }
        if (wrist.panelUp && !eligible(player)) close(player, wrist);
        switch (wrist.stage) {
            case IDLE -> {
                if (wrist.kind() == Kind.CARNITRIX && now % 5 == 0 && mobile(player)) PursuitBody.detect(player, wrist);
            }
            case LOCKING -> {
                if (now >= wrist.until || now % 5 == 0) PursuitBody.updateResistance(player, wrist, now);
            }
            case BINDING, COOLING -> {
                if (now >= wrist.until) {
                    wrist.enter(Stage.IDLE, now, 0);
                    if (wrist.kind() != Kind.CARNITRIX) announce(player, SoundEvents.NOTE_BLOCK_PLING.value(), 1.7f);
                    WristNetwork.sync(player);
                }
            }
            case ARMING -> {
                if (now >= wrist.until) activate(player, now);
            }
            case ACTIVE -> {
                if (humanFits(player, player.position())) wrist.lastHumanClear = player.position();
                if (now >= wrist.until) {
                    revert(player, now);
                } else if (wrist.kind() == Kind.CARNITRIX) {
                    Abilities.tickBarrier(player, wrist, now);
                    PursuitBody.ensure(player, wrist);
                } else {
                    Abilities.tickBarrier(player, wrist, now);
                    Abilities.tickForm(player, wrist, now);
                    if (wrist.kind() == Kind.CLASSIC) {
                        SurvivalForms.tick(player, wrist, now);
                        warn(player, wrist, now);
                    }
                }
            }
            case REVERTING -> {
                if (now >= wrist.until) finish(player, now);
            }
        }
    }

    private static void warn(ServerPlayer player, Wrist wrist, long now) {
        long left = wrist.until - now;
        if (left <= 0 || left > CLASSIC_WARNING_TICKS) return;
        int second = (int)((left + 19) / 20);
        if (second == wrist.warningSecond) return;
        wrist.warningSecond = second;
        announce(player, Omnitrix.TIMEOUT.get(), second <= 3 ? 1.24f : 1f);
    }

    private static void close(ServerPlayer p, Wrist wrist) {
        wrist.panelUp = false;
        wrist.revision++;
        WristNetwork.sync(p);
    }

    private static void activate(ServerPlayer player, long now) {
        var wrist = Omnitrix.wrist(player);
        Kind kind = wrist.kind();
        if (!fits(player, wrist.form.dimensions(kind), player.position())) {
            tell(player, "no_space");
            finish(player, now);
            return;
        }
        wrist.enter(Stage.ACTIVE, now, now + kind.duration);
        AscalonService.onOmnitrixTransformStart(player);
        wrist.attackCueAt = now + 6;
        wrist.maxBarrier = wrist.form.maxBarrier(kind);
        wrist.barrier = wrist.maxBarrier;
        if (kind == Kind.CARNITRIX) PursuitBody.ensure(player, wrist);
        applyForm(player, wrist);
        WristNetwork.sync(player);
    }

    private static void revert(ServerPlayer player, long now) {
        var wrist = Omnitrix.wrist(player);
        wrist.enter(Stage.REVERTING, now, now + REVERT_TICKS);
        announce(player, Omnitrix.RESTORE.get(), 1f);
        if (wrist.kind() == Kind.CLASSIC) {
            var at = player.position();
            player.serverLevel().sendParticles(new net.minecraft.core.particles.DustColorTransitionOptions(
                    new org.joml.Vector3f(.50f,1,.23f),new org.joml.Vector3f(.86f,1,.66f),1.26f),
                    at.x, at.y + player.getBbHeight()*.58, at.z, 24, .34, .44, .34, .012);
        }
        WristNetwork.sync(player);
    }

    private static void finish(ServerPlayer player, long now) {
        var wrist = Omnitrix.wrist(player);
        Kind kind = wrist.kind();
        if (kind == null) return;
        boolean needsSpace = wrist.hasForm() && !humanFits(player, player.position());
        boolean toll = kind == Kind.CARNITRIX && wrist.hasForm() && player.isAlive();
        var body = player.getVehicle() instanceof PursuitBody pursuit ? pursuit : null;
        wrist.form = null;
        wrist.targetId = -1;
        wrist.barrier = 0;
        wrist.maxBarrier = 0;
        wrist.civilianOverride = false;
        wrist.misfire = false;
        wrist.resistance = 0;
        wrist.enter(Stage.COOLING, now, now + kind.recharge);
        wrist.equipReadyAt = wrist.until;
        if (body != null) {
            player.stopRiding();
            body.discard();
        }
        if (needsSpace && player.isAlive()) relocate(player, wrist.lastHumanClear);
        applyForm(player, wrist);
        if (toll) {
            player.removeEffect(Omnitrix.NERVE_EXHAUSTION);
            player.addEffect(new MobEffectInstance(Omnitrix.NERVE_EXHAUSTION, 100));
            announce(player, Omnitrix.EXHAUSTED.get(), 1f);
        }
        wrist.lastHumanClear = null;
        WristNetwork.sync(player);
    }

    private static void relocate(ServerPlayer player, Vec3 saved) {
        Vec3 start = player.position();
        Vec3 target = null;
        if (saved != null && saved.distanceToSqr(start) < 64 * 64 && humanFits(player, saved)) target = saved;
        for (BlockPos pos : BlockPos.withinManhattan(BlockPos.containing(start), 6, 4, 6)) {
            if (target != null) break;
            Vec3 here = Vec3.atBottomCenterOf(pos);
            if (humanFits(player, here) && player.serverLevel().getBlockState(pos.below()).isCollisionShapeFullBlock(player.level(), pos.below())) target = here;
        }
        if (target != null) {
            player.connection.teleport(target.x, target.y, target.z, player.getYRot(), player.getXRot());
            player.resetFallDistance();
        } else {
            ServerLevel level = player.server.overworld();
            var spawn = level.getSharedSpawnPos();
            level.getChunk(spawn);
            BlockPos floor = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
            player.teleportTo(level, floor.getX() + .5, floor.getY(), floor.getZ() + .5, java.util.Set.of(), player.getYRot(), player.getXRot());
        }
    }

    private static void size(EntityEvent.Size event) {
        if (event.getEntity() instanceof ServerPlayer p && p.hasData(Omnitrix.WRIST)) {
            var wrist = Omnitrix.wrist(p);
            if (wrist.hasForm()) event.setNewSize(wrist.form.dimensions(wrist.kind()).scale(p.getScale()));
        }
    }

    private static void applyForm(ServerPlayer p, Wrist wrist) {
        Form form = wrist.hasForm() ? wrist.form : null;
        Kind kind = wrist.kind() == null ? Kind.CLASSIC : wrist.kind();
        modify(p, Attributes.MAX_HEALTH, form == null ? 0 : form.maxHealthBonus(kind), AttributeModifier.Operation.ADD_VALUE);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        modify(p, Attributes.MOVEMENT_SPEED, form == null ? 0 : form.speed(kind) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modify(p, Attributes.ARMOR, form == null ? 0 : form.armor(kind), AttributeModifier.Operation.ADD_VALUE);
        modify(p, Attributes.ARMOR_TOUGHNESS, form == null ? 0 : form.toughness(kind), AttributeModifier.Operation.ADD_VALUE);
        modify(p, Attributes.ATTACK_DAMAGE, form == null ? 0 : form.attack(kind), AttributeModifier.Operation.ADD_VALUE);
        modify(p, Attributes.KNOCKBACK_RESISTANCE, form == null ? 0 : form.knockbackResistance(kind), AttributeModifier.Operation.ADD_VALUE);
        modify(p, Attributes.WATER_MOVEMENT_EFFICIENCY, form == Form.RIPJAWS ? 1 : 0, AttributeModifier.Operation.ADD_VALUE);
        modify(p, Attributes.STEP_HEIGHT, form == Form.XLR8 || (form == Form.ALIEN_X && kind == Kind.CELESTIAL) ? .55 : 0, AttributeModifier.Operation.ADD_VALUE);
        modify(p, FLIGHT, form != null && form.flies() && kind != Kind.CARNITRIX ? 1 : 0, AttributeModifier.Operation.ADD_VALUE);
        p.getAbilities().setFlyingSpeed(form == Form.ALIEN_X && kind == Kind.CELESTIAL ? .095f : .05f);
        if (!p.mayFly() && p.getAbilities().flying) {
            p.getAbilities().flying = false;
        }
        p.onUpdateAbilities();
        if (form != null && wrist.maxBarrier <= 0) {
            wrist.maxBarrier = form.maxBarrier(kind);
            wrist.barrier = wrist.maxBarrier;
        }
        p.refreshDimensions();
    }

    private static void modify(ServerPlayer player, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(MODIFIER);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(MODIFIER, amount, operation));
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.hasData(Omnitrix.WRIST)) return;
        var w = Omnitrix.wrist(p);
        long now = time(p);
        w.clearSkills();
        boolean inTransform = w.hasForm() || w.stage == Stage.ARMING;
        if (inTransform && w.kind() != Kind.CARNITRIX) finish(p, now);
        else if (inTransform && now >= w.until) {
            if (w.stage == Stage.ARMING && now < w.until + Kind.CARNITRIX.duration && !p.isSpectator()) {
                long ready = w.until;
                activate(p, now);
                if (w.stage == Stage.ACTIVE) w.until = ready + Kind.CARNITRIX.duration;
            } else {
                long spentAt = w.stage == Stage.ARMING ? w.until + Kind.CARNITRIX.duration : w.until;
                finish(p, spentAt);
            }
        } else applyForm(p, w);
        if (w.stage == Stage.COOLING && now >= w.until) w.enter(Stage.IDLE, now, 0);
        if (w.controlled()) AcademyInterop.releaseHeldAbility(p);
        WristNetwork.sync(p);
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.hasData(Omnitrix.WRIST)) leave(p);
    }

    private static void stop(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.hasData(Omnitrix.WRIST)) leave(player);
        }
    }

    private static void leave(ServerPlayer p) {
        var w = Omnitrix.wrist(p);
        w.panelUp = false;
        w.clearSkills();
        if (w.stage == Stage.LOCKING) w.enter(Stage.IDLE, time(p), 0);
        if (w.kind() != Kind.CARNITRIX && (w.hasForm() || w.stage == Stage.ARMING)) finish(p, time(p));
        if (p.getVehicle() instanceof PursuitBody body) body.release();
    }

    private static void track(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer actor
                && actor.hasData(Omnitrix.WRIST)) WristNetwork.tracking(viewer, actor);
    }

    private static void beginDimensionTravel(net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getVehicle() instanceof PursuitBody body) body.release();
    }

    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.hasData(Omnitrix.WRIST)) return;
        var w = Omnitrix.wrist(p);
        w.clearSkills();
        w.lastHumanClear = null;
        w.targetId = -1;
        if (w.stage == Stage.LOCKING) w.enter(Stage.IDLE, time(p), 0);
        w.revision++;
        if (w.kind() != Kind.CARNITRIX && (w.hasForm() || w.stage == Stage.ARMING)) finish(p, time(p));
        else WristNetwork.sync(p);
    }

    private static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.hasData(Omnitrix.WRIST)) return;
        var w = Omnitrix.wrist(p);
        w.clearSkills();
        if (p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        if (w.stack.isEmpty()) return;
        var drop = new ItemEntity(p.level(), p.getX(), p.getY() + .3, p.getZ(), w.stack);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
        w.stack = ItemStack.EMPTY;
    }

    private static void cloned(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getOriginal().hasData(Omnitrix.WRIST)) return;
        var prior = Omnitrix.wrist(event.getOriginal());
        var next = Omnitrix.wrist(p);
        next.equipReadyAt = prior.equipReadyAt;
        if (!event.isWasDeath() || p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            next.stack = prior.stack.copy();
            next.carniLocked = prior.carniLocked;
            if (prior.hasForm() || prior.stage == Stage.ARMING) {
                next.equipReadyAt = Math.max(next.equipReadyAt, time(p) + prior.kind().recharge);
            }
            if (!next.stack.isEmpty() && time(p) < next.equipReadyAt) next.enter(Stage.COOLING, time(p), next.equipReadyAt);
        }
        next.revision = prior.revision + 1;
    }

    private static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && p.hasData(Omnitrix.WRIST)) WristNetwork.sync(p);
    }

    private static void mounting(EntityMountEvent e) {
        if (!(e.getEntityMounting() instanceof ServerPlayer p) || !p.hasData(Omnitrix.WRIST)) return;
        var w = Omnitrix.wrist(p);
        if (e.getEntityBeingMounted() instanceof net.minecraft.world.entity.vehicle.AbstractMinecart cart
                && SurvivalForms.mayRideMachine(p, cart)) return;
        if (e.isMounting() && !(e.getEntityBeingMounted() instanceof PursuitBody) && (w.controlled() || w.hasForm())) {
            e.setCanceled(true);
        }
    }

    static boolean absoluteDamage(net.minecraft.world.damagesource.DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    public static boolean tell(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("watch.aca_omnitrix." + key), true);
        return false;
    }

    static void announce(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, .8f, pitch);
    }

    public static void forceHumanForAscalon(ServerPlayer player) {
        if (!player.hasData(Omnitrix.WRIST)) return;
        var wrist = Omnitrix.wrist(player);
        long now = time(player);
        if (wrist.panelUp) close(player, wrist);
        if (wrist.stage == Stage.LOCKING) {
            wrist.resistance = 0;
            wrist.targetId = -1;
            wrist.enter(Stage.IDLE, now, 0);
            WristNetwork.sync(player);
        } else if (wrist.hasForm() || wrist.stage == Stage.ARMING) {
            finish(player, now);
        }
    }
}
