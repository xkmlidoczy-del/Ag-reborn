package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.WindPressureLift;
import cn.academy.port.highpower.core.WindPressureLiftGeometry;
import cn.academy.port.highpower.core.WindPressureLiftRules;
import cn.academy.port.highpower.client.WindPressureLiftTravelMixinPlugin;
import cn.academy.port.client.highpower.mixin.WindPressureLiftAutoJumpAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** NEW443: current paid snapshot plus a certified native travel permits one bounded current-velocity ADD. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class WindPressureLiftClientMotion {
    private static final WindPressureLiftReceiptState RECEIPTS = new WindPressureLiftReceiptState();
    private static ClientLevel world;
    private static LocalPlayer player;
    private static Object connection;
    private static volatile long generation;
    private static long tick, session, presetRevision, input, epoch;
    private static int preset;
    private static MinecraftServer ownerServer;
    private static java.util.UUID ownerUuid;
    private static ResourceLocation ownerDimension;
    private static int ownerEntity=-1;
    private static boolean wasAirborne;
    private static Vec3 previousPosition;
    private static WindPressureLift.Grant permission;
    private static String qaSweepReason = "not_queried";
    private WindPressureLiftClientMotion() {}
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { WindPressureLift.motionReceiver = WindPressureLiftClientMotion::receive; }
    }
    public static boolean canStartLocal() { return startBlocker().isEmpty(); }
    public static String startBlocker() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        String prefix = "academy.highpower.wind_pressure_lift.";
        if (!integrated(mc)) return prefix + "unsupported";
        if (!WindPressureLiftNativeTravel.sableStateReason(player).isEmpty()) return prefix + "dynamic_structure";
        if (!WindPressureLiftNativeTravel.ready(mc)) return prefix + "native_compat";
        if (!autoJumpReadable(player)) return prefix + "unavailable";
        if (autoJumpTime(player) > 0) return prefix + "autojump";
        if (!eligible(mc) || !normalMovement(mc, player == null ? null : player.input)) return prefix + "unavailable";
        if (!player.onGround() || player.getDeltaMovement().y > 0) return prefix + "ground";
        var before=boundary(mc);
        AABB body = standingBody();
        AABB column = new AABB(player.getX()-3,player.getY(),player.getZ()-3,player.getX()+3,player.getY()+6,player.getZ()+3);
        if (!loaded(column) || !world.getWorldBorder().isWithinBounds(column) || !world.noCollision(player, body.deflate(1e-7))) return prefix + "blocked";
        if (!WindPressureLiftNativeTravel.ready(mc)) return prefix + "native_compat";
        if (!eligible(mc) || !normalMovement(mc,player.input)
                || !WindPressureLiftNativeSweep.unchanged(before,boundary(mc))) return prefix + "unavailable";
        return "";
    }
    /** Capture ownership only; no position or velocity is changed and no proof is manufactured. */
    public static boolean captureInput(long nextInput, long nextEpoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        WindPressureLiftTrace.start(nextInput,nextEpoch);
        if (!canStartLocal() || !AcademyClient.singleOwnerEpochMatches(nextEpoch)
                || !RECEIPTS.begin(nextInput, nextEpoch)) {
            if(WindPressureLiftTrace.ENABLED) WindPressureLiftTrace.record(nextInput,nextEpoch,"capture_refused",snapshot(mc));
            return false;
        }
        input = nextInput; epoch = nextEpoch; session = AcademyClient.inputSessionEpoch();
        ownerServer=mc.getSingleplayerServer();ownerUuid=player.getUUID();ownerEntity=player.getId();ownerDimension=world.dimension().location();
        preset = AcademyClient.state.presets.current(); presetRevision = AcademyClient.state.presets.revision();
        previousPosition = player.position(); permission = null; wasAirborne = false;
        trace("capture","accepted "+snapshotIfEnabled(mc));return true;
    }
    public static void receive(WindPressureLift.Grant packet) {
        if (!valid(packet)) {trace("receive_reject","malformed");return;}
        long queuedAt = WindPressureLiftTrace.ENABLED ? System.nanoTime() : 0;
        Minecraft mc = Minecraft.getInstance();var capturedDelivery=delivery(mc);
        mc.execute(() -> {
            if (!WindPressureLiftReceiptState.sameDelivery(capturedDelivery,delivery(mc))) {
                trace("receive_reject","queued_owner_or_generation");return;
            }
            synchronize(mc);
            if (!identity(packet)) {traceGrant("receive_reject",packet,"identity",queuedAt);return;}
            // A free off edge already retired motion locally. Its matching END still closes pending input.
            if (packet.terminal()) {
                if(!RECEIPTS.terminal(packet.input(),packet.epoch(),packet.token())) {traceGrant("receive_reject",packet,"terminal_identity_or_token",queuedAt);return;}
                traceGrant("receive_terminal",packet,"server_end",queuedAt);
                abortLocal(packet.input(),packet.epoch()); AcademyClient.windPressureLiftEndedInput(packet.input(),packet.epoch()); return;
            }
            if (!RECEIPTS.matches(packet.input(), packet.epoch())) {traceGrant("receive_reject",packet,"local_closed",queuedAt);return;}
            if (!context(mc) || mc.isPaused()) { stop("receive_context"); return; }
            var snapshot=WindPressureLift.latestSnapshot(mc.getSingleplayerServer(),player.getId(),player.getUUID(),
                    packet.input(),packet.epoch(),world.dimension().location(),packet.token());
            boolean current=snapshot!=null && snapshot.grant().equals(packet);
            traceGrant("receive",packet,"snapshot_current="+current,queuedAt);
            if (!current) return;
            if (!fixedProof(packet)) { stop("receive_geometry_changed"); return; }
            if (!RECEIPTS.receive(packet.input(), packet.epoch(), packet.token(), packet.sequence(), packet.terminal())) {traceGrant("receive_reject",packet,"receipt_fence",queuedAt);return;}
            if(!RECEIPTS.bindGeometry(packet.token(),geometry(packet))) {stop("receive_geometry_binding");return;}
            permission = packet;
            AcademyClient.windPressureLiftAcceptedInput(packet.input(), packet.epoch());
        });
    }
    private static boolean valid(WindPressureLift.Grant p) {
        return p != null && p.entity() >= 0 && p.owner() != null && p.dimension() != null && p.input() > 0
                && p.token() > 0 && p.epoch() > 0 && p.sequence() > 0 && finite(p.anchor()) && finite(p.accepted())
                && (p.pose() == Pose.STANDING || p.pose() == Pose.CROUCHING)
                && p.mode() != null && finite(p.direction()) && Math.abs(p.direction().y) <= 1e-8
                && Math.abs(p.direction().lengthSqr()-1) <= 1e-6 && p.issuedAt() >= 0 && p.issuedAt() < Long.MAX_VALUE
                && p.expiresAt() == p.issuedAt() + 1;
    }
    private static boolean identity(WindPressureLift.Grant p) {
        return player != null && world != null && p.entity() == player.getId() && p.owner().equals(player.getUUID())
                && world.getEntity(p.entity()) == player && p.dimension().equals(world.dimension().location())
                && AcademyClient.singleOwnerEpochMatches(p.epoch()) && AcademyClient.windPressureLiftInputMatches(p.input(), p.epoch());
    }
    private static boolean fixedProof(WindPressureLift.Grant p) {
        return RECEIPTS.geometryMatches(geometry(p));
    }
    private static WindPressureLiftReceiptState.Geometry geometry(WindPressureLift.Grant p) {
        return new WindPressureLiftReceiptState.Geometry(p.anchor().x,p.anchor().y,p.anchor().z,p.mode().ordinal(),
                p.direction().x,p.direction().y,p.direction().z);
    }
    private static WindPressureLiftReceiptState.Delivery delivery(Minecraft mc) {
        return new WindPressureLiftReceiptState.Delivery(mc.level,mc.player,mc.getConnection(),generation);
    }
    private static boolean integrated(Minecraft mc) {
        return mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null && !mc.getSingleplayerServer().isPublished();
    }
    private static boolean eligible(Minecraft mc) {
        var s = AcademyClient.state;
        return integrated(mc) && world != null && player != null && connection != null && mc.screen == null
                && mc.isWindowActive() && !mc.isPaused() && mc.getCameraEntity() == player && world.getEntity(player.getId()) == player
                && world.tickRateManager().tickrate()==20 && !world.tickRateManager().isFrozen() && world.tickRateManager().runsNormally()
                && player.isAlive() && !player.isRemoved() && !player.isSpectator() && s != null && s.activated && s.overloadFine
                && !s.interfering && !s.consumptionInProgress() && "vecmanip".equals(s.category) && s.level >= 4 && s.level <= 5
                && s.learned(WindPressureLift.ID) && s.learned("multi_axis_reflection") && s.presets.currentContains(WindPressureLift.ID)
                && SkillCatalog.enabled("vecmanip", WindPressureLift.ID) && !ClassicVectorStarterEffects.anyActive()
                && !ClassicVectorCombatEffects.anyActive() && !ClassicVectorFinalEffects.anyActive()
                && !ClassicTeleporterFinalEffects.anyActive() && !ClassicMeltdownerLateEffects.delegateActive("jet_engine");
    }
    private static boolean context(Minecraft mc) {
        return eligible(mc) && AcademyClient.sameInputSessionEpoch(session) && AcademyClient.singleOwnerEpochMatches(epoch)
                && AcademyClient.windPressureLiftInputMatches(input, epoch) && AcademyClient.windPressureLiftControlValid()
                && AcademyClient.state.presets.current() == preset && AcademyClient.state.presets.revision() == presetRevision;
    }
    private static boolean normalMovement(Minecraft mc, Input i) {
        var p = player;
        return p != null && i != null && !i.jumping && !mc.options.keyJump.isDown() && autoJumpTime(p) == 0
                && p.isEffectiveAi() && (p.getPose() == Pose.STANDING || p.getPose() == Pose.CROUCHING)
                && !p.noPhysics && !p.horizontalCollision && !p.isPassenger() && !p.isVehicle() && !p.isSleeping()
                && !p.isFallFlying() && !p.isAutoSpinAttack() && !p.getAbilities().flying && !p.getAbilities().mayfly
                && !p.isInWaterOrBubble() && !p.isInLava() && p.getMaxHeightFluidType().isAir() && !p.onClimbable()
                && !p.isInPowderSnow && !p.isSwimming() && p.getActiveEffects().isEmpty() && !p.isNoGravity()
                && p.getGravity() == .08 && p.hurtTime == 0 && !p.isUsingItem()
                && Float.isFinite(i.forwardImpulse) && Math.abs(i.forwardImpulse) <= 1
                && Float.isFinite(i.leftImpulse) && Math.abs(i.leftImpulse) <= 1 && Float.isFinite(p.getYRot())
                && finite(p.position()) && finite(p.getDeltaMovement()) && p.getDeltaMovement().y <= .160000001;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); tick++;
        observeInterruption(mc);
        if (input != 0 && RECEIPTS.matches(input, epoch) && (!context(mc) || !normalMovement(mc, player.input))) stop("client_post_context_or_native_state");
        if(WindPressureLiftTrace.ENABLED && input>0)trace("native_post","afterMoveTick="+(tick-1)+" "+snapshot(mc));
    }
    /** Observe real interruptions even between native ticks and when only historical quota debt remains. */
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event) {
        Minecraft mc=Minecraft.getInstance();synchronize(mc);observeInterruption(mc);
    }
    private static void observeInterruption(Minecraft mc) {
        if(mc.screen==null && !mc.isPaused() && mc.isWindowActive())return;
        if(integrated(mc) && mc.player!=null)
            WindPressureLift.pauseIntegratedOwner(mc.getSingleplayerServer(),mc.player.getUUID());
        stop("menu_pause_or_focus");
    }
    /** Input events can only cancel; they never mint or consume native travel authority. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void movement(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (player == null || event.getEntity() != player || event.getInput() != player.input) return;
        if (mc.isPaused()) { stop("movement_paused"); return; }
        if (input == 0 || !RECEIPTS.matches(input, epoch)) return;
        if (!context(mc) || !normalMovement(mc, event.getInput())) { stop("movement_context_or_native_state"); return; }
    }
    /** Called solely by the exact LocalPlayer travel wrapper after native input/jump/clamp processing. */
    static void nativeTravel(WindPressureLiftTravelState.Travel travel,Vec3 travelVector) {
        Minecraft mc=Minecraft.getInstance();synchronize(mc);
        if(input==0 || !RECEIPTS.matches(input,epoch) || !WindPressureLiftNativeTravel.owns(travel,mc))return;
        if(!context(mc) || !normalMovement(mc,player.input) || !finite(travelVector)) {stop("travel_context_or_native_state");return;}
        Vec3 position = player.position();
        if (!player.onGround()) wasAirborne = true;
        if (previousPosition == null || previousPosition.distanceToSqr(position) > WindPressureLiftRules.MAX_ACCEPTED_MOVE * WindPressureLiftRules.MAX_ACCEPTED_MOVE) { stop("position_discontinuity"); return; }
        previousPosition = position;
        // The snapshot can cover bounded clock skew; it never carries an unused historical impulse.
        var snapshot = WindPressureLift.latestSnapshot(mc.getSingleplayerServer(),player.getId(),player.getUUID(),
                input,epoch,world.dimension().location(),RECEIPTS.expectedToken());
        if(snapshot==null) {permission=null;trace("travel_skip","no_current_paid_snapshot");return;}
        WindPressureLift.Grant p=snapshot.grant();
        if(!valid(p) || !identity(p)) {traceGrant("movement_skip",p,"lookup_identity",0);return;}
        if(!fixedProof(p)) {stop("lookup_geometry_changed");return;}
        if(!RECEIPTS.observeSnapshot(p.input(),p.epoch(),p.token(),p.sequence())
                || !RECEIPTS.retireTravel(p.input(),p.epoch(),p.token(),p.sequence(),travel.serial())) {
            traceGrant("movement_skip",p,"lookup_receipt_fence",0);return;
        }
        if(!RECEIPTS.bindGeometry(p.token(),geometry(p))) {stop("lookup_geometry_binding");return;}
        permission=p;
        AcademyClient.windPressureLiftAcceptedInput(p.input(),p.epoch());
        permission = null;
        if (!identity(p) || p.pose() != player.getPose()) {traceGrant("movement_skip",p,"identity_or_pose",0);return;}
        if (position.distanceToSqr(p.accepted()) > WindPressureLiftRules.MAX_ACCEPTED_MOVE * WindPressureLiftRules.MAX_ACCEPTED_MOVE) { stop("accepted_position_drift"); return; }
        Vec3 current = player.getDeltaMovement();
        var before = boundary(mc);
        var result = WindPressureLiftRules.assist(new WindPressureLiftRules.Velocity(current.x,current.y,current.z), p.mode(),
                new WindPressureLiftRules.Horizontal(p.direction().x,p.direction().z));
        if (result == null) {traceGrant("movement_skip",p,"malformed_proposal",0);return;}
        Vec3 changed = new Vec3(result.x(),result.y(),result.z());
        if (changed.equals(current)) {traceGrant("movement_skip",p,"no_addition",0);return;}
        if (!loaded(standingBody().inflate(1))) { stop("standing_query_unloaded"); return; }
        Vec3 predicted = predictedNative(changed, travelVector);
        if (!finite(predicted)) {stop("native_prediction_invalid");return;}
        if (!safeSweep(p, predicted)) { stop("unsafe_sweep:"+qaSweepReason); return; }
        // Block/entity collision hooks are extension callbacks: they must not make this proposal overwrite
        // new knockback, a teleport, an input/pose change, or replacement ownership during the safety query.
        if (!context(mc) || !normalMovement(mc,player.input) || !identity(p)
                || !WindPressureLiftNativeSweep.unchanged(before,boundary(mc))
                || !current.equals(player.getDeltaMovement()) || !position.equals(player.position())
                || !RECEIPTS.ownsTravel(p.input(),p.epoch(),p.token(),p.sequence(),travel.serial())
                || !fixedProof(p) || !WindPressureLiftNativeTravel.owns(travel,mc)) { stop("callback_boundary_changed"); return; }
        var actor=player;var localWorld=world;var localConnection=connection;var server=mc.getSingleplayerServer();
        // No world/collision/payment callback is permitted under this short common lock. Native plain
        // velocity get/set and position getters have independent bytecode pins. END cannot interleave
        // the final atomic quota claim and this single setter.
        boolean committed;
        WindPressureLift.StepOutcome claim;
        synchronized(WindPressureLift.integratedCommitLock()) {
            if(mc.player!=actor || mc.level!=localWorld || mc.getConnection()!=localConnection
                    || !current.equals(actor.getDeltaMovement()) || !position.equals(actor.position())
                    || !RECEIPTS.ownsTravel(p.input(),p.epoch(),p.token(),p.sequence(),travel.serial())
                    || !fixedProof(p) || !WindPressureLiftNativeTravel.owns(travel,mc))return;
            var step=travel.step();var run=step.run();
            claim=WindPressureLift.consumeIntegratedStepOutcome(snapshot,server,travel.serial(),run.serial(),step.index(),run.plannedCount());
            committed=claim==WindPressureLift.StepOutcome.ACCEPTED;
            if(committed)actor.setDeltaMovement(changed);
        }
        if(!committed) {
            traceGrant("travel_skip",p,"atomic_claim_"+claim.name(),0);
            if(claim==WindPressureLift.StepOutcome.CLOCK_SKEW_CLOSED)
                actor.displayClientMessage(Component.translatable("academy.highpower.wind_pressure_lift.clock_skew_stop"),true);
            return;
        }
        if(WindPressureLiftTrace.ENABLED)traceGrant("applied",p,"beforeV="+current+" afterV="+player.getDeltaMovement()
                +" pos="+position+" ground="+player.onGround()+" predicted="+predicted,0);
    }
    private static Vec3 predictedNative(Vec3 velocity, Vec3 travelVector) {
        float speed = player.isSprinting() ? .026F : .02F;
        if (player.onGround()) {
            BlockPos at = player.getBlockPosBelowThatAffectsMyMovement();
            float friction = world.getBlockState(at).getFriction(world,at,player);
            if (!Float.isFinite(friction) || friction <= 0) return null;
            speed = player.getSpeed() * (.21600002F / (friction * friction * friction));
        }
        if (!Float.isFinite(speed) || speed < 0 || speed > .5F) return null;
        // LivingEntity.aiStep already clamped current velocity and multiplied movement input by .98.
        // Use its actual argument unchanged; scaling either twice would predict a different move.
        float sin = Mth.sin(player.getYRot() * (float)(Math.PI/180.0)), cos = Mth.cos(player.getYRot() * (float)(Math.PI/180.0));
        var prediction=WindPressureLiftNativeSweep.project(new WindPressureLiftRules.Velocity(velocity.x,velocity.y,velocity.z),
                new WindPressureLiftRules.Velocity(travelVector.x,travelVector.y,travelVector.z),speed,sin,cos);
        return prediction==null?null:new Vec3(prediction.x(),prediction.y(),prediction.z());
    }
    private static WindPressureLiftNativeSweep.Boundary boundary(Minecraft mc) {
        LocalPlayer p=mc.player;if(p==null || p.input==null) return null;
        Vec3 at=p.position(),v=p.getDeltaMovement();Input i=p.input;
        return new WindPressureLiftNativeSweep.Boundary(mc.level,p,mc.getConnection(),generation,i,p.getPose(),
                new WindPressureLiftGeometry.Point(at.x,at.y,at.z),new WindPressureLiftRules.Velocity(v.x,v.y,v.z),
                box(p.getBoundingBox()),box(p.getDimensions(Pose.STANDING).makeBoundingBox(at)),p.getYRot(),p.getSpeed(),
                p.onGround(),i.forwardImpulse,i.leftImpulse,i.jumping,i.shiftKeyDown);
    }
    private static WindPressureLiftGeometry.Box box(AABB b) {return new WindPressureLiftGeometry.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static AABB standingBody() { return player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()); }
    private static boolean safeSweep(WindPressureLift.Grant grant, Vec3 delta) {
        AABB body = standingBody(), rawSweep = body.expandTowards(delta);
        // Native block collision iteration includes a one-block halo around the requested box.
        if (!loaded(rawSweep.inflate(1))) return unsafe("raw_query_unloaded");
        // Read native collision results without moving the player. Only downward floor clipping is supported;
        // wall/step-up or ceiling clipping ends this controller, so native step-up cannot broaden its footprint.
        Vec3 nativeCollision = Entity.collideBoundingBox(player, delta, body, world, world.getEntityCollisions(player, rawSweep));
        if (!finite(nativeCollision)) return unsafe("native_collision_nonfinite");
        var allowed = WindPressureLiftNativeSweep.accepted(new WindPressureLiftRules.Velocity(delta.x,delta.y,delta.z),
                new WindPressureLiftRules.Velocity(nativeCollision.x,nativeCollision.y,nativeCollision.z),wasAirborne);
        if (allowed == null) return unsafe("native_collision_refused requested="+delta+" accepted="+nativeCollision+" airborneSeen="+wasAirborne);
        Vec3 accepted = new Vec3(allowed.x(),allowed.y(),allowed.z());
        // Retain the existing conservative clearance margin. Actual sprint/input state is now final
        // for this travel, and no later movement-event prediction is substituted for its real argument.
        double nativeMargin = player.onGround() ? .16 : .007;
        AABB swept = body.expandTowards(accepted).inflate(nativeMargin,0,nativeMargin), column = new AABB(grant.anchor().x-3,grant.anchor().y,
                grant.anchor().z-3,grant.anchor().x+3,grant.anchor().y+6,grant.anchor().z+3);
        var frame = new WindPressureLiftGeometry.Frame(new WindPressureLiftGeometry.Point(grant.anchor().x,grant.anchor().y,grant.anchor().z));
        var box = new WindPressureLiftGeometry.Box(body.minX,body.minY,body.minZ,body.maxX,body.maxY,body.maxZ);
        if (!WindPressureLiftGeometry.containsSweep(frame,box,new WindPressureLiftRules.Velocity(accepted.x,accepted.y,accepted.z)))return unsafe("fixed_column_sweep");
        if (!WindPressureLiftGeometry.contains(frame,new WindPressureLiftGeometry.Box(swept.minX,swept.minY,swept.minZ,swept.maxX,swept.maxY,swept.maxZ)))return unsafe("native_margin_column");
        if (!loaded(column))return unsafe("column_unloaded");
        if (!loaded(swept.inflate(1)))return unsafe("sweep_halo_unloaded");
        if (!world.getWorldBorder().isWithinBounds(column))return unsafe("world_border");
        if (!world.noCollision(player,body.deflate(1e-7)))return unsafe("body_collision");
        if (!world.noCollision(player,swept.deflate(1e-7)))return unsafe("sweep_collision");
        for (BlockPos at : BlockPos.betweenClosed(BlockPos.containing(swept.minX,swept.minY,swept.minZ),
                BlockPos.containing(swept.maxX,swept.maxY,swept.maxZ)))
            if (!world.getFluidState(at).isEmpty()) return unsafe("sweep_fluid");
        // Keep conservative clearance for the whole block columns used by LocalPlayer push-out checks.
        for (int x:new int[]{-1,1}) for (int z:new int[]{-1,1}) {
            BlockPos at=BlockPos.containing(player.getX()+x*player.getBbWidth()*.35,player.getY(),player.getZ()+z*player.getBbWidth()*.35);
            AABB pushColumn=new AABB(at.getX(),body.minY,at.getZ(),at.getX()+1,body.maxY,at.getZ()+1).deflate(1e-7);
            if(!loaded(pushColumn.inflate(1)))return unsafe("push_out_unloaded");
            if(world.collidesWithSuffocatingBlock(player,pushColumn))return unsafe("push_out_collision");
        }
        return true;
    }
    private static boolean loaded(AABB box) {
        if (world == null || !finite(new Vec3(box.minX,box.minY,box.minZ)) || !finite(new Vec3(box.maxX,box.maxY,box.maxZ))
                || Math.abs(box.minX)>30_000_000 || Math.abs(box.maxX)>30_000_000 || Math.abs(box.minZ)>30_000_000 || Math.abs(box.maxZ)>30_000_000
                || box.getXsize()>8 || box.getYsize()>8 || box.getZsize()>8 || box.minY<world.getMinBuildHeight() || box.maxY>=world.getMaxBuildHeight()) return false;
        for (int x=Mth.floor(box.minX)>>4;x<=Mth.floor(box.maxX)>>4;x++)
            for (int z=Mth.floor(box.minZ)>>4;z<=Mth.floor(box.maxZ)>>4;z++) if (!world.hasChunk(x,z)) return false;
        return true;
    }
    private static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static boolean autoJumpReadable(LocalPlayer p) {
        return p!=null && WindPressureLiftTravelMixinPlugin.available() && p instanceof WindPressureLiftAutoJumpAccessor;
    }
    private static int autoJumpTime(LocalPlayer p) {
        return autoJumpReadable(p)?((WindPressureLiftAutoJumpAccessor)p).academy$windPressureLiftAutoJumpTime():-1;
    }
    private static void stop(String reason) {
        long oldInput=input,oldEpoch=epoch;
        if (oldInput<=0 || !RECEIPTS.matches(oldInput,oldEpoch)) return;
        if(WindPressureLiftTrace.ENABLED)trace("stop",reason+" "+snapshot(Minecraft.getInstance()));
        abortLocal(oldInput,oldEpoch); WindPressureLiftEffects.cancelLocal(oldInput,oldEpoch);
        AcademyClient.windPressureLiftLocalVisualLost(oldInput,oldEpoch);
    }
    static void nativeBoundaryRefused(String reason) {
        Minecraft mc=Minecraft.getInstance();
        boolean active=input>0 && RECEIPTS.matches(input,epoch);
        stop(reason);
        if(active && mc.player!=null)
            mc.player.displayClientMessage(Component.translatable(
                    "native_catchup_over_two".equals(reason)
                            ? "academy.highpower.wind_pressure_lift.catchup_stop"
                            : "academy.highpower.wind_pressure_lift.timing_stop"),true);
    }
    public static void abortLocal(long input,long epoch) {
        if(WindPressureLiftTrace.ENABLED)WindPressureLiftTrace.record(input,epoch,"abort_local","clientTick="+tick);
        if(WindPressureLiftClientMotion.input==input && WindPressureLiftClientMotion.epoch==epoch)revokeIntegrated();
        RECEIPTS.cancel(input,epoch); permission=null;WindPressureLiftNativeTravel.cancelCurrent();
    }
    private static void revokeIntegrated() {
        if(ownerServer!=null && ownerUuid!=null && ownerDimension!=null && ownerEntity>=0 && input>0 && epoch>0)
            WindPressureLift.stopIntegrated(ownerServer,ownerEntity,ownerUuid,input,epoch,ownerDimension,RECEIPTS.expectedToken());
    }
    public static void clear() {
        trace("clear","generation="+generation);revokeIntegrated();RECEIPTS.clear();permission=null;input=epoch=0;
        ownerServer=null;ownerUuid=null;ownerDimension=null;ownerEntity=-1;
        previousPosition=null;generation++;WindPressureLiftNativeTravel.clear();
    }
    private static void trace(String stage,String detail) {
        if(WindPressureLiftTrace.ENABLED)WindPressureLiftTrace.record(input,epoch,stage,"clientTick="+tick+" "+detail);
    }
    private static void traceGrant(String stage,WindPressureLift.Grant p,String detail,long queuedAt) {
        if(!WindPressureLiftTrace.ENABLED)return;
        WindPressureLiftTrace.record(p.input(),p.epoch(),stage,"clientTick="+tick+" token="+p.token()+" seq="+p.sequence()
                +" issued="+p.issuedAt()+" expires="+p.expiresAt()+" mode="+p.mode()+" pose="+p.pose()
                +" queueUs="+(queuedAt==0?-1:Math.max(0,(System.nanoTime()-queuedAt)/1000))+" "+detail);
    }
    private static String snapshotIfEnabled(Minecraft mc) {return WindPressureLiftTrace.ENABLED?snapshot(mc):"";}
    private static String snapshot(Minecraft mc) {
        if(mc.player==null)return "player=absent";
        var p=mc.player;var s=AcademyClient.state;
        return "pos="+p.position()+" velocity="+p.getDeltaMovement()+" ground="+p.onGround()+" airborneSeen="+wasAirborne
                +" localOpen="+RECEIPTS.matches(input,epoch)+" permissionSeq="+(permission==null?-1:permission.sequence())
                +" pose="+p.getPose()+" jump="+(p.input!=null&&p.input.jumping)+" autoJump="+autoJumpTime(p)
                +" horizontalCollision="+p.horizontalCollision+" verticalCollision="+p.verticalCollision+" fallDistance="+p.fallDistance
                +" paused="+mc.isPaused()+" screen="+(mc.screen!=null)+" focused="+mc.isWindowActive()
                +" hurt="+p.hurtTime+" effects="+p.getActiveEffects().size()+" category="+(s==null?"none":s.category)
                +" active="+(s!=null&&s.activated)+" overloadFine="+(s!=null&&s.overloadFine)
                +" interfering="+(s!=null&&s.interfering)+" busy="+(s!=null&&s.consumptionInProgress());
    }
    private static boolean unsafe(String reason) {if(WindPressureLiftTrace.ENABLED)qaSweepReason=reason;return false;}
    private static void synchronize(Minecraft mc) {
        if (world==mc.level && player==mc.player && connection==mc.getConnection()) return;
        clear(); if (connection!=mc.getConnection()) RECEIPTS.resetConnection();
        world=mc.level;player=mc.player;connection=mc.getConnection();tick=0;
    }
}
