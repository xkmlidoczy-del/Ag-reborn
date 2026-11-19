package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Abilities;
import cn.aca.omnitrix.Form;
import cn.aca.omnitrix.Kind;
import cn.aca.omnitrix.Omnitrix;
import cn.aca.omnitrix.PursuitBody;
import cn.aca.omnitrix.Wrist.Stage;
import cn.aca.omnitrix.WristNetwork;
import cn.aca.omnitrix.client.ClientState.View;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Vector3f;

public final class WatchRender {
    private static final java.util.Set<Direction> SIDES = java.util.EnumSet.allOf(Direction.class);
    private static final ModelPart RIGHT_ARM = skinArm(4, false, false);
    private static final ModelPart LEFT_ARM = skinArm(4, true, false);
    private static final ModelPart RIGHT_SLIM = skinArm(3, false, false);
    private static final ModelPart LEFT_SLIM = skinArm(3, true, false);
    private static final ModelPart RIGHT_SLEEVE = skinArm(4, false, true);
    private static final ModelPart LEFT_SLEEVE = skinArm(4, true, true);
    private static final ModelPart RIGHT_SLIM_SLEEVE = skinArm(3, false, true);
    private static final ModelPart LEFT_SLIM_SLEEVE = skinArm(3, true, true);
    private static final java.util.Map<Player, TravelPose> TRAVEL = new java.util.WeakHashMap<>();
    private static final FpKinematics FP = new FpKinematics();
    private static long dialSlamClientTick = Long.MIN_VALUE;

    private WatchRender() {}

    static void notifyDialSlam() {
        var level = Minecraft.getInstance().level;
        if (level != null) dialSlamClientTick = level.getGameTime();
    }

    /**
     * Framerate-independent first-person kinematics & context-sensitive hand visibility controller.
     * Eliminates stiff, permanently visible dual hands in both human watch-wearing mode and alien mode,
     * while adding camera turn lag sway, figure-8 stride bob, landing spring squash, and single/dual cast choreography.
     */
    private static final class FpKinematics {
        Player owner;
        double lastSampleTime = Double.NaN;
        float prevYaw, prevPitch;
        float swayX, swayY, swayRoll;
        float airLagY, landImpulse;
        boolean wasOnGround = true;

        // Human watch glance envelope (0 = stowed cleanly below bezel, 1 = peripheral wrist peek)
        Stage lastHumanPhase;
        long humanPeekUntil = Long.MIN_VALUE;
        float humanPeekFactor;

        // Alien combat / skill engagement & off-hand stow state
        float mainEngageFactor = .35f;
        float offhandVisibleFactor = 0f;
        long combatActiveUntil = Long.MIN_VALUE;
        long dualHandUntil = Long.MIN_VALUE;
        boolean prevSwinging;
        int comboHandToggle;

        void step(LocalPlayer player, View view, float partial) {
            double frameNow = player.tickCount + partial;
            if (owner != player || Double.isNaN(lastSampleTime) || Math.abs(frameNow - lastSampleTime) > 20.0) {
                owner = player;
                lastSampleTime = frameNow;
                prevYaw = player.getViewYRot(partial);
                prevPitch = player.getViewXRot(partial);
                swayX = swayY = swayRoll = airLagY = landImpulse = 0f;
                wasOnGround = player.onGround();
                lastHumanPhase = view == null ? null : view.s().phase();
                humanPeekFactor = 0f;
                mainEngageFactor = .25f;
                offhandVisibleFactor = 0f;
                return;
            }
            float dt = (float) Math.clamp(frameNow - lastSampleTime, 0.0, 1.25);
            if (dt <= 1e-4f) return;
            lastSampleTime = frameNow;

            float yaw = player.getViewYRot(partial);
            float pitch = player.getViewXRot(partial);
            float dYaw = Mth.wrapDegrees(yaw - prevYaw);
            float dPitch = pitch - prevPitch;
            prevYaw = yaw;
            prevPitch = pitch;

            if (ClientState.reduced()) {
                swayX = swayY = swayRoll = airLagY = landImpulse = 0f;
            } else {
                float damp = 1f - (float) Math.pow(0.72, dt * 3.0);
                swayX = Mth.clamp(swayX - dYaw * .0024f, -.085f, .085f);
                swayY = Mth.clamp(swayY + dPitch * .0021f, -.075f, .075f);
                swayRoll = Mth.clamp(swayRoll - dYaw * .18f, -8.5f, 8.5f);
                swayX = Mth.lerp(damp, swayX, 0f);
                swayY = Mth.lerp(damp, swayY, 0f);
                swayRoll = Mth.lerp(damp, swayRoll, 0f);

                boolean grounded = player.onGround();
                float vy = (float) player.getDeltaMovement().y;
                if (!wasOnGround && grounded) {
                    landImpulse = Math.min(.085f, landImpulse + .048f);
                }
                wasOnGround = grounded;
                float targetAir = grounded ? 0f : Mth.clamp(-vy * .065f, -.055f, .065f);
                airLagY = Mth.lerp(damp, airLagY, targetAir);
                landImpulse = Mth.lerp(1f - (float) Math.pow(0.62, dt * 3.0), landImpulse, 0f);
            }

            if (view == null) return;
            long gameTick = player.level().getGameTime();
            Stage phase = view.s().phase();

            // 1. Human mode peripheral watch glance logic: stow cleanly when peaceful!
            if (phase != lastHumanPhase) {
                if (phase == Stage.BINDING || phase == Stage.REVERTING
                        || (lastHumanPhase == Stage.COOLING && phase == Stage.IDLE)
                        || (lastHumanPhase == Stage.REVERTING && (phase == Stage.COOLING || phase == Stage.IDLE))) {
                    humanPeekUntil = gameTick + 55;
                }
                lastHumanPhase = phase;
            }
            boolean lookAtWrist = pitch > 50f && !player.isSwimming();
            boolean lowHpAlert = view.s().watch() != Kind.CARNITRIX && player.getHealth() <= 8.0f && phase == Stage.IDLE;
            float targetPeek = (gameTick < humanPeekUntil || lookAtWrist || lowHpAlert) ? 1f : 0f;
            float peekRate = 1f - (float) Math.pow(targetPeek > humanPeekFactor ? 0.52 : 0.78, dt * 2.2);
            humanPeekFactor = Mth.lerp(peekRate, humanPeekFactor, targetPeek);

            // 2. Alien mode combat / skill / stow choreography
            if (view.s().morphed()) {
                float attack = player.getAttackAnim(partial);
                boolean swingingNow = attack > .02f && attack < .35f;
                if (swingingNow && !prevSwinging) {
                    comboHandToggle ^= 1;
                }
                prevSwinging = attack > .02f;

                var perf = view.performance(partial);
                Form form = view.s().chosen();
                boolean offhandHeld = !player.getOffhandItem().isEmpty() && view.s().watch() != Kind.CARNITRIX;
                boolean hurtNow = player.hurtTime > 0 || timeout(view);

                if (attack > .01f) {
                    combatActiveUntil = Math.max(combatActiveUntil, gameTick + 52);
                    if (form == Form.FOUR_ARMS && comboHandToggle == 1) {
                        dualHandUntil = Math.max(dualHandUntil, gameTick + 18);
                    }
                }
                if (hurtNow) {
                    combatActiveUntil = Math.max(combatActiveUntil, gameTick + 36);
                }
                if (perf != null) {
                    combatActiveUntil = Math.max(combatActiveUntil, gameTick + 56);
                    if (requiresDualHands(form, perf.action())) {
                        dualHandUntil = Math.max(dualHandUntil, gameTick + 24);
                    }
                }
                var cue = ClientState.effect(player);
                if (cue != null && gameTick - cue.tick() < 12) {
                    combatActiveUntil = Math.max(combatActiveUntil, gameTick + 44);
                }

                boolean inCombat = gameTick < combatActiveUntil;
                boolean needOffhand = offhandHeld || gameTick < dualHandUntil
                        || (perf != null && requiresDualHands(form, perf.action()));

                float targetMain = (attack > .01f || perf != null) ? 1.0f : inCombat ? .72f : .0f;
                float targetOff = needOffhand ? 1.0f : 0.0f;

                float mainRate = 1f - (float) Math.pow(targetMain > mainEngageFactor ? 0.35 : 0.82, dt * 2.4);
                float offRate = 1f - (float) Math.pow(targetOff > offhandVisibleFactor ? 0.30 : 0.78, dt * 2.4);
                mainEngageFactor = Mth.lerp(mainRate, mainEngageFactor, targetMain);
                offhandVisibleFactor = Mth.lerp(offRate, offhandVisibleFactor, targetOff);
            }
        }

        private static boolean requiresDualHands(Form form, int action) {
            if (form == null) return false;
            boolean secondary = action >= Abilities.SECONDARY && action < Abilities.AUTOMATED;
            return switch (form) {
                case FOUR_ARMS, DIAMONDHEAD, RIPJAWS -> true;
                case HEATBLAST -> secondary; // U = 1-hand fireball hurl, I = 2-hand pyrokinetic fire cone!
                case WILDMUTT -> secondary;  // U = sensory pulse, I = twin-claw pounce!
                case GHOSTFREAK -> secondary; // I = phase rift tear
                case ALIEN_X -> action != WristNetwork.FLICK_CUE; // U/I = cosmic decree, O = 1-hand reality flick!
                default -> false;
            };
        }
    }

    private static final class TravelPose {
        final long formAt;
        boolean wet, air;
        float waterFrom;
        double waterAt = Double.NEGATIVE_INFINITY, airAt = Double.NEGATIVE_INFINITY;

        TravelPose(long formAt, boolean wet, boolean air) {
            this.formAt = formAt;
            this.wet = wet;
            this.air = air;
            waterFrom = wet ? 1 : 0;
        }

        float water(double now) {
            return Mth.lerp(Motion.part((float) (now - waterAt), 0, 6), waterFrom, wet ? 1 : 0);
        }

        void update(boolean swimming, boolean airborne, double now) {
            if (wet != swimming) {
                waterFrom = water(now);
                wet = swimming;
                waterAt = now;
            }
            if (air != airborne) {
                air = airborne;
                airAt = now;
            }
        }
    }

    private static Rig.Motion animatedActor(Player player, View view, Rig rig, float partial,
                                            float gait, float speed, float lookX, float lookY, boolean air,
                                            boolean swimming, float materialize) {
        Form form = view.s().chosen();
        boolean carni = view.s().watch() == Kind.CARNITRIX;
        double rawNow = view.now(partial);

        // Client-side visual micro hit-stop (~1.55 render ticks / ~75ms pose lock on heavy confirmed melee hits, 0 input freeze!)
        float hitTremor = 0f;
        if (!ClientState.reduced() && player.level() != null) {
            var latest = ClientState.effect(player);
            if (latest != null && (latest.cue().hitEntity() || latest.cue().hitGround()) && !latest.cue().projectile()) {
                float hitAge = (player.level().getGameTime() - latest.tick()) + partial;
                float stopWindow = (form == Form.FOUR_ARMS || form == Form.ALIEN_X) ? 1.85f : 1.35f;
                if (hitAge >= 0f && hitAge < stopWindow) {
                    rawNow -= hitAge * .84;
                    hitTremor = Mth.sin(hitAge * 24f) * (1f - hitAge / stopWindow) * 1.6f;
                }
            }
        }
        final double now = rawNow;
        final float tremor = hitTremor;

        var travel = TRAVEL.get(player);
        if (travel == null || travel.formAt != view.s().began()) {
            travel = new TravelPose(view.s().began(), swimming, air);
            TRAVEL.put(player, travel);
        }
        travel.update(swimming, air, now);
        float wet = travel.water(now);
        float clock = (float) (now % 31680);
        var performance = view.performance(partial);
        float swing = performance == null ? player.getAttackAnim(partial) : 0;
        Rig.Motion pose = Motion.actor(form, carni, clock, gait, speed, lookX, lookY, air, wet, swing, materialize);
        pose = rig.animate(pose, "idle", clock, Math.max(0, 1 - speed * 1.65f) * (performance == null ? 1 : .25f));
        Rig.Motion strides = rig.animate(pose, "locomotion", gait * 4, Math.min(1, speed * 1.35f) * (1 - wet));
        if (performance == null) {
            pose = strides;
        } else {
            Rig.Motion steady = Motion.actor(form, carni, clock, gait, speed * .22f,
                    lookX, lookY, air, wet, 0, materialize);
            pose = bone -> supportsWeight(bone) ? strides.at(bone) : steady.at(bone);
        }
        if (air) pose = rig.animate(pose, "air", clock, .67f * (1 - wet));
        if (Double.isFinite(travel.airAt)) {
            pose = rig.animate(pose, air ? "takeoff" : "land", (float) (now - travel.airAt), 1 - wet);
        }
        pose = rig.animate(pose, "swim", clock, wet);
        if (Double.isFinite(travel.waterAt)) {
            pose = rig.animate(pose, travel.wet ? "enter_water" : "leave_water",
                    (float) (now - travel.waterAt), 1f);
        }
        if (performance != null) {
            String clip = performance.action() >= Abilities.AUTOMATED ? "auto"
                    : performance.action() >= Abilities.SECONDARY ? "i" : "u";
            if (performance.style() == Abilities.STYLE_ALTERNATE && rig.hasClip(clip + "_alt")) clip += "_alt";
            if (performance.style() == Abilities.STYLE_FALLBACK && rig.hasClip(clip + "_fallback")) clip += "_fallback";
            float elapsed = (float) Math.max(0.0, now - performance.start());
            float weight = performance.impact() == performance.start() ? 1 : Math.min(1f, .54f + elapsed * .52f);
            pose = rig.animate(pose, clip, elapsed, weight);
        }
        if (form == Form.ALIEN_X && !carni) {
            var flick = ClientState.effect(player);
            if (flick != null && flick.cue().action() == WristNetwork.FLICK_CUE) {
                float age = player.level().getGameTime() - flick.tick() + partial;
                if (age >= 0 && age < 7) pose = rig.animate(pose, "flick", age, performance == null ? 1 : .28f);
            }
        }
        if (Math.abs(tremor) > .02f) {
            Rig.Motion base = pose;
            pose = bone -> {
                var j = base.at(bone);
                if (bone.startsWith("arm_") || bone.startsWith("elbow_") || bone.startsWith("extra_")) {
                    return new Rig.Joint(j.x(), j.y(), j.z(), j.pitch() + tremor, j.yaw(), j.roll(), j.scale(), j.tint());
                }
                return j;
            };
        }
        return pose;
    }

    private static boolean supportsWeight(String bone) {
        return bone.startsWith("leg_") || bone.startsWith("shin_") || bone.startsWith("hock_")
                || bone.startsWith("wheel_") || bone.startsWith("foot_");
    }

    private static ModelPart skinArm(int width, boolean left, boolean sleeve) {
        int u = left ? (sleeve ? 48 : 32) : 40;
        int v = left ? 48 : (sleeve ? 32 : 16);
        float over = sleeve ? .24f : 0;
        return new ModelPart(java.util.List.of(new ModelPart.Cube(u, v, -width / 2f, -7, -2, width, 12, 4,
                over, over, over, false, 64, 64, SIDES)), java.util.Map.of());
    }

    public static void poseHuman(PlayerModel<?> model, net.minecraft.world.entity.LivingEntity carrier, float time) {
        if (!(carrier instanceof AbstractClientPlayer player)) return;
        View view = ClientState.get(player);
        if (view != null && view.s().morphed()) return;
        AscalonRenderer.poseViltrumModel(model, player);
        if (view == null) return;
        var phase = view.s().phase();
        if (!view.s().lifted() && phase != Stage.BINDING && phase != Stage.ARMING) return;
        boolean left = player.getMainArm() == HumanoidArm.RIGHT;
        float sign = left ? 1 : -1;
        var fore = left ? model.leftArm : model.rightArm;
        var free = left ? model.rightArm : model.leftArm;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float t = view.age(partial);
        float lift = phase == Stage.BINDING ? Motion.part(t, 0, 7) : 1;
        fore.xRot = Mth.lerp(lift, fore.xRot, -1.19f);
        fore.yRot = Mth.lerp(lift, fore.yRot, sign * .53f);
        fore.zRot = Mth.lerp(lift, fore.zRot, sign * .12f);
        if (view.s().lifted() && phase == Stage.IDLE && view.s().watch() != Kind.CARNITRIX) {
            // Third-person right hand hovers over watch bezel while browsing dial!
            float hoverPulse = Mth.sin((player.tickCount + partial) * .25f) * .04f;
            free.xRot = Mth.lerp(.85f, free.xRot, -1.06f + hoverPulse);
            free.yRot = Mth.lerp(.85f, free.yRot, -sign * .78f);
        } else if (phase == Stage.ARMING && t < 18 && view.s().watch() != Kind.CARNITRIX) {
            float windup = Motion.part(t, 0, 2.5f) * (1 - Motion.part(t, 2.5f, 5.5f));
            float press = Motion.part(t, 2, 6) * (1 - Motion.part(t, 10, 17));
            free.xRot = Mth.lerp(Math.max(windup, press), free.xRot, -1.55f * windup - 1.14f * press);
            free.yRot = Mth.lerp(Math.max(windup, press), free.yRot, -sign * (.35f * windup + .92f * press));
        }
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
    }

    static void morph(RenderPlayerEvent.Pre event) {
        View view = ClientState.get(event.getEntity());
        if (view == null || !view.s().morphed()) return;
        event.setCanceled(true);
        var player = event.getEntity();
        if (player.isInvisibleTo(Minecraft.getInstance().player)) return;
        float partial = event.getPartialTick();
        boolean carni = view.s().watch() == Kind.CARNITRIX;
        Form form = view.s().chosen();
        float age = view.age(partial);
        float morph = view.s().phase() == Stage.REVERTING ? 1 - Motion.part(age, 4, 10) : Motion.part(age, 0, 5);
        float roll = player.deathTime > 0 ? Math.min(1f, (player.deathTime + partial) / 15) * 83 : 0;
        var pose = event.getPoseStack();
        var frame = sampleActor(player, view, partial, morph);
        var rig = frame.rig;
        float scale = frame.scale;
        Rig.Motion anim = frame.motion;
        if (timeout(view) && (ClientState.reduced() || (cn.aca.omnitrix.WristService.CLASSIC_WARNING_TICKS - view.ticksLeft()) / 5 % 2 == 0)) {
            Rig.Motion base = anim;
            anim = bone -> base.at(bone).tint(0xffff3831);
        }
        if (rig.starfield) StarfieldShader.clock(partial);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - frame.bodyYaw));
        if (roll > 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        int color = view.s().phase() == Stage.REVERTING && morph < .5f ? 0xb9e4ffd5 : -1;
        rig.draw(pose, event.getMultiBufferSource(), anim, scale, color, event.getPackedLight(), LivingEntityRenderer.getOverlayCoords(player, 0));
        if (!carni) renderHeld(player, rig, anim, pose, event.getMultiBufferSource(), event.getPackedLight(), scale);
        if (age < 7 || view.s().phase() == Stage.REVERTING) {
            watchRune(pose, event.getMultiBufferSource(), form.dimensions(view.s().watch()).height() * .68f, view.s().watch(), age,
                    view.s().phase() == Stage.REVERTING ? morph : 1 - morph);
        }
        pose.popPose();
    }

    private record ActorPose(Rig rig, Rig.Motion motion, float scale, float bodyYaw) {}

    private static ActorPose sampleActor(Player player, View view, float partial, float morph) {
        Form form = view.s().chosen();
        var watch = view.s().watch();
        var carrier = player.getVehicle() instanceof PursuitBody pursuit ? pursuit : player;
        float yaw = Mth.rotLerp(partial, carrier.yBodyRotO, carrier.yBodyRot);
        float pitch = Mth.lerp(partial, carrier.xRotO, carrier.getXRot());
        float look = Mth.wrapDegrees(Mth.rotLerp(partial, carrier.yHeadRotO, carrier.yHeadRot) - yaw);
        var walk = carrier.walkAnimation;
        boolean swim = form == Form.RIPJAWS && carrier.isInWater() || carrier.isSwimming();
        Rig rig = Rig.get(form.asset(watch));
        Rig.Motion motion = animatedActor(player, view, rig, partial, walk.position(partial), walk.speed(partial),
                pitch, look, !carrier.onGround(), swim, morph);
        return new ActorPose(rig, motion, form.dimensions(watch).height() * 16 / rig.height * player.getScale(), yaw);
    }

    static Vec3 skillAnchor(Player player, View view, String bone, float partial, Vec3 fallback) {
        if (player == null || view == null || !view.s().morphed() || view.s().chosen() == null) return fallback;
        var actor = sampleActor(player, view, partial, Motion.part(view.age(partial), 0, 5));
        var pose = new PoseStack();
        pose.mulPose(Axis.YP.rotationDegrees(180 - actor.bodyYaw));
        if (!actor.rig.attach(pose, actor.motion, bone, actor.scale)) return fallback;
        Vector3f offset = pose.last().pose().transformPosition(new Vector3f());
        return player.getPosition(partial).add(offset.x(), offset.y(), offset.z());
    }

    static boolean timeout(View view) {
        return view.s().watch() == Kind.CLASSIC && view.s().phase() == Stage.ACTIVE
                && view.ticksLeft() > 0 && view.ticksLeft() <= cn.aca.omnitrix.WristService.CLASSIC_WARNING_TICKS;
    }

    private static void watchRune(PoseStack pose, MultiBufferSource buffers, float height, Kind kind, float time, float alpha) {
        if (alpha <= .02) return;
        pose.pushPose();
        pose.translate(0, height, -.29);
        pose.mulPose(Axis.XP.rotationDegrees(-75));
        Rig.get(kind.id).draw(pose, buffers, Motion.watch(kind, 1, 1, 0, time), .48f,
                ((int) (alpha * 255) << 24) | 0xffffff, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static void renderHeld(Player player, Rig rig, Rig.Motion motion, PoseStack pose,
                                   MultiBufferSource sources, int light, float scale) {
        ItemRenderer items = Minecraft.getInstance().getItemRenderer();
        for (HumanoidArm side : HumanoidArm.values()) {
            boolean left = side == HumanoidArm.LEFT;
            var item = side == player.getMainArm() ? player.getMainHandItem() : player.getOffhandItem();
            if (item.isEmpty()) continue;
            pose.pushPose();
            if (rig.attach(pose, motion, left ? "grip_l" : "grip_r", scale)) {
                pose.translate(0, -.015 * scale, -.023 * scale);
                Rig.rotate(pose, 25, 180, left ? -12 : 12);
                pose.scale(.78f, .78f, .78f);
                items.renderStatic(player, item, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        left, pose, sources, player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
            }
            pose.popPose();
        }
    }

    static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                           float walk, float speed, float partial, float age, float headYaw, float headPitch) {
            var view = ClientState.get(player);
            if (view == null || player.isInvisible() || view.s().morphed()) return;
            boolean left = player.getMainArm() == HumanoidArm.RIGHT;
            var arm = left ? getParentModel().leftArm : getParentModel().rightArm;
            float t = view.age(partial);
            var phase = view.s().phase();
            pose.pushPose();
            arm.translateAndRotate(pose);
            boolean slim = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
            pose.translate((left ? 1 : -1) * (slim ? .025 : .037), .45, -.161);
            Rig.rotate(pose, -91, 180, 0);
            float binding = phase == Stage.BINDING ? Motion.part(t, 1, 14) : 1;
            float open = view.s().lifted() || phase == Stage.ARMING || phase == Stage.LOCKING ? 1 : 0;
            Kind kind = view.s().watch();
            boolean automatic = kind == Kind.CARNITRIX;
            float press = phase == Stage.ARMING && !automatic ? Motion.part(t, 0, 6) : 0;
            float bearing = automatic && (phase == Stage.ARMING || phase == Stage.LOCKING)
                    ? ClientState.bearing(player, view, partial) : age * 6;
            Rig.Motion motion = Motion.watch(kind, open, binding, press, age, bearing);
            if (view.s().misfire() && phase == Stage.ARMING && t < 13) motion = redCore(motion);
            Rig.get(kind.id).draw(pose, buffer, motion,
                    slim ? .78f : .9f, -1, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    public static boolean firstPerson(ItemInHandRenderer renderer, LocalPlayer player, PoseStack pose,
                                      MultiBufferSource.BufferSource buffers, int light, float partial) {
        View view = ClientState.get(player);
        if (view == null || player.isSpectator() || (player.isPassenger() && !(player.getVehicle() instanceof PursuitBody))) return false;
        FP.step(player, view, partial);

        var phase = view.s().phase();
        boolean slamBridge = dialSlamClientTick != Long.MIN_VALUE
                && player.level().getGameTime() - dialSlamClientTick <= 2 && phase == Stage.IDLE;
        boolean focus = WristScreen.isOpen() || slamBridge || phase == Stage.BINDING
                || phase == Stage.ARMING || phase == Stage.LOCKING;
        boolean left = player.getMainArm() == HumanoidArm.RIGHT;
        int sign = left ? -1 : 1;
        float time = player.tickCount + partial;
        float age = view.age(partial);

        if (phase == Stage.REVERTING) {
            float glow = (float) Math.sin(Math.PI * Motion.part(age, 0, 10));
            if (glow > .12f) {
                pose.pushPose();
                pose.translate(sign * .31 + FP.swayX, -.25 + FP.swayY, -.66);
                Rig.rotate(pose, 38, sign * -23, sign * 11 + FP.swayRoll * .4f);
                Rig.get(view.s().watch().id).draw(pose, buffers, Motion.watch(view.s().watch(), glow * .6f, 1, 0, time), .68f,
                        DialDraw.alpha(-1, glow * .81f), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }

        if (focus) {
            float lift = phase == Stage.BINDING ? Motion.part(age, 0, 6)
                    : slamBridge || phase == Stage.ARMING ? 1f
                    : phase == Stage.LOCKING ? Motion.part(age, 0, 4)
                    : WristScreen.lift(partial);
            Kind kind = view.s().watch();
            boolean automatic = kind == Kind.CARNITRIX;
            float press = phase == Stage.ARMING && !automatic
                    ? Motion.part(age, 2, 5.8f) * (1 - Motion.part(age, 9, 16)) : 0f;
            float closure = phase == Stage.BINDING ? Motion.part(age, 2, 14)
                    : phase == Stage.ARMING && automatic ? .75f + .25f * Motion.part(age, 4, 19) : 1f;
            float bearing = automatic && (phase == Stage.ARMING || phase == Stage.LOCKING)
                    ? ClientState.bearing(player, view, partial) : time * 6;
            if (phase == Stage.LOCKING && !ClientState.reduced()) bearing += Mth.sin(age * 1.8f) * 24;
            float breath = (float) Math.sin(time * .13) * .005f;
            float recoil = ClientState.reduced() ? 0 : WristPost.impact(view, partial);
            float dialYaw = WristScreen.isOpen() ? WristScreen.dialYaw(partial) : 0f;
            float dialTap = WristScreen.switchImpulse(partial);
            int tapDir = WristScreen.switchSign();
            float lockShake = phase == Stage.LOCKING && !ClientState.reduced() ? Mth.sin(age * 2.7f) * .014f : 0f;

            // Left / watch-bearing arm: rises along an arc with spring overshoot + impact depression on slam
            pose.pushPose();
            pose.translate(
                    sign * (.55 - .16 * lift) + lockShake + FP.swayX * .55f,
                    -.62 + .29 * lift + breath - .046 * recoil - .026 * press + .012 * dialTap + FP.swayY * .55f - FP.landImpulse * .5f,
                    -1.02 - .10 * lift);
            Rig.rotate(pose, 24 + lift * 31 - dialTap * 3.5f + press * 7f,
                    sign * (-28 + lift * 14), sign * 10 + FP.swayRoll * .35f);
            humanArm(player, pose, buffers, light, left, .95f);
            pose.translate(.0, .018, -.023);
            Rig.rotate(pose, 5, 0, 0);
            Rig.Motion motion = Motion.watch(kind, lift * .88f, closure, press, time, bearing, dialYaw);
            if (view.s().misfire() && phase == Stage.ARMING && age < 13) motion = redCore(motion);
            Rig.get(kind.id).draw(pose, buffers, motion, .75f,
                    -1, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();

            // Right / free hand: hovers at bezel & flicks dial during WristScreen, then raises & SLAMS core on confirm!
            if (WristScreen.showsBezelHand()) {
                float reach = Mth.clamp((lift - .15f) / .85f, 0f, 1f);
                if (reach > .02f) {
                    pose.pushPose();
                    float flickTwist = dialTap * tapDir;
                    pose.translate(
                            -sign * (.48 - .28 * reach + .032 * flickTwist) + FP.swayX * .45f,
                            -.64 + .30 * reach + breath * .8f + .022 * dialTap + FP.swayY * .45f,
                            -.96 - .06 * reach);
                    Rig.rotate(pose,
                            56 + reach * 16 + dialTap * 11f,
                            -sign * (20 + 24 * reach + flickTwist * 13f),
                            -sign * (14 - flickTwist * 9f));
                    humanArm(player, pose, buffers, light, !left, .90f);
                    pose.popPose();
                }
            } else if ((slamBridge || phase == Stage.ARMING) && !automatic && (slamBridge || age < 19)) {
                pose.pushPose();
                float effAge = slamBridge ? 0.8f : age;
                // Anticipation high palm lift (0..2.6t) -> thunderous downward SLAM onto watch core (2.2..5.8t) -> follow-through!
                float raise = Motion.part(effAge, 0, 2.4f) * (1 - Motion.part(effAge, 2.4f, 5.2f));
                float fall = Motion.part(effAge, 2.0f, 5.6f);
                float withdraw = Motion.part(effAge, 9.5f, 17.5f);
                pose.translate(
                        -sign * (.42 - .64 * press),
                        .46 + .28 * raise - .82 * fall - .58 * withdraw,
                        -.88 - .22 * press);
                Rig.rotate(pose,
                        98 + raise * 24 - press * 48,
                        -sign * (18 + 42 * press),
                        -sign * (11 + 9 * press));
                humanArm(player, pose, buffers, light, !left, .92f);
                pose.popPose();
            }
            buffers.endBatch();
            return true;
        }

        if (view.s().morphed()) {
            Form form = view.s().chosen();
            boolean carni = view.s().watch() == Kind.CARNITRIX;
            var rig = Rig.get(form.asset(view.s().watch()));
            if (rig.starfield) StarfieldShader.clock(partial);
            float attack = player.getAttackAnim(partial);
            float strikeSnap = Motion.impactCurve(attack);
            var rider = player.getVehicle() instanceof PursuitBody pursuit ? pursuit : null;
            var gait = rider == null ? player.walkAnimation : rider.walkAnimation;
            float gaitPos = gait.position(partial);
            float gaitSpeed = gait.speed(partial);
            boolean airborne = rider == null ? !player.onGround() : !rider.onGround();
            boolean swim = form == Form.RIPJAWS && (rider == null ? player.isInWater() : rider.isInWater());

            Rig.Motion skillPose = animatedActor(player, view, rig, partial, gaitPos, gaitSpeed,
                    0, 0, airborne, swim, 1);
            Rig.Motion hands = bone -> {
                var j = skillPose.at(bone);
                float pitchGain = bone.startsWith("elbow_") ? .52f : .40f;
                return new Rig.Joint(j.x() * .45f, j.y() * .45f, j.z() * .45f,
                        Mth.clamp(j.pitch() * pitchGain, -26, 48), j.yaw() * .54f, j.roll() * .54f, j.scale(), j.tint());
            };

            var perf = view.performance(partial);
            boolean activeSkill = perf != null;
            float sprintTuck = player.isSprinting() && !activeSkill && attack <= .01f ? 1f : 0f;
            // Sleek forms tuck even lower out of combat so forward speed/flight vision stays wide open!
            float formRestDrop = switch (form) {
                case XLR8, STINKFLY, GHOSTFREAK, GREY_MATTER -> .24f;
                case ALIEN_X, UPGRADE -> .21f;
                default -> .17f;
            };

            for (HumanoidArm arm : HumanoidArm.values()) {
                boolean isMain = arm == player.getMainArm();
                boolean sideLeft = arm == HumanoidArm.LEFT;
                int side = sideLeft ? -1 : 1;
                String rigSide = sideLeft ? "l" : "r";
                var item = isMain ? player.getMainHandItem() : player.getOffhandItem();
                boolean holdingItem = !carni && !item.isEmpty();

                // Off-hand stows completely off-screen when peaceful & empty — NO MORE STIFF DUAL HANDS!
                float presence = isMain ? 1f : (holdingItem ? 1f : FP.offhandVisibleFactor);
                if (presence <= .015f) continue;
                float stowDrop = (1f - Motion.ease(presence)) * .52f;
                float stowOut = (1f - Motion.ease(presence)) * .20f;

                // Main hand relaxes into low peripheral breathing stance out of combat, surging up on engage!
                float engage = holdingItem ? Math.max(.55f, FP.mainEngageFactor) : (isMain ? FP.mainEngageFactor : presence);
                float relax = 1f - Motion.ease(engage);

                // Route left-click punch primarily to the active striking hand (alternating on Four Arms!)
                boolean swingsThisArm = form == Form.FOUR_ARMS
                        ? ((FP.comboHandToggle == 0) == isMain || attack <= .01f)
                        : isMain;
                float localStrike = swingsThisArm ? strikeSnap : strikeSnap * .15f;

                // Figure-8 locomotion stride bob + breathing + camera inertia
                float stridePhase = gaitPos * .6662f + (sideLeft ? (float) Math.PI : 0f);
                float bobX = ClientState.reduced() ? 0f : Mth.cos(stridePhase) * gaitSpeed * .028f;
                float bobY = ClientState.reduced() ? 0f : -Math.abs(Mth.sin(stridePhase)) * gaitSpeed * .034f;
                float breathY = Mth.sin(time * .11f + (sideLeft ? .9f : 0f)) * .008f;
                float breathX = Mth.cos(time * .055f) * .004f * side;

                float span = rig.armSpan(rigSide);
                float armScale = Mth.clamp(11.8f / span, .62f, 1.38f) * (form == Form.FOUR_ARMS ? .72f : .78f);
                float armPunch = Mth.clamp((skillPose.at("arm_" + rigSide).pitch() - 20) / 82f, 0, 1);

                pose.pushPose();
                float baseX = (form == Form.FOUR_ARMS ? .53f : .48f) + relax * .08f + stowOut;
                float baseY = (form == Form.FOUR_ARMS ? -.34f : (span < 8f ? -.32f : -.36f))
                        - relax * formRestDrop - sprintTuck * .07f - stowDrop;
                float baseZ = (form == Form.FOUR_ARMS ? -.43f : (span < 8f ? -.45f : -.51f)) - relax * .04f;

                pose.translate(
                        side * (baseX - .13f * armPunch - .11f * localStrike) + bobX + breathX + FP.swayX,
                        baseY + breathY + bobY + .055f * armPunch + .045f * localStrike + FP.swayY + FP.airLagY - FP.landImpulse,
                        baseZ - .22f * armPunch - .25f * localStrike);
                Rig.rotate(pose,
                        71 - relax * 16f - sprintTuck * 12f + localStrike * 26f - stowDrop * 38f,
                        side * (15 + relax * 7f - localStrike * 22f),
                        side * (12 + relax * 6f) + FP.swayRoll);

                rig.arm(pose, buffers, rigSide, hands, armScale, -1, light);
                if (holdingItem) {
                    if (!rig.attachArmGrip(pose, rigSide, hands, armScale)) pose.translate(0, -.40, -.04);
                    Rig.rotate(pose, -63, side * 18, 0);
                    renderer.renderItem(player, item,
                            sideLeft ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                            sideLeft, pose, buffers, light);
                }
                pose.popPose();

                // Four Arms lower pair surges into view ONLY on active combo / slam / punch!
                if (form == Form.FOUR_ARMS && (activeSkill || (swingsThisArm && attack > .02f))) {
                    float extraPunch = Mth.clamp((skillPose.at("extra_" + rigSide).pitch() - 16) / 76f, 0, 1);
                    pose.pushPose();
                    pose.translate(
                            side * (.37 - .11 * extraPunch - .08 * localStrike) + FP.swayX * .8f,
                            -.56 + .10 * extraPunch + .06 * localStrike + FP.swayY * .8f - FP.landImpulse,
                            -.46 - .28 * extraPunch - .20 * localStrike);
                    Rig.rotate(pose, 78 + localStrike * 24, side * (18 - localStrike * 14), side * 8 + FP.swayRoll * .7f);
                    rig.limb(pose, buffers, "extra_" + rigSide, hands, .68f, -1, light, rig.starfield);
                    pose.popPose();
                }
            }
            buffers.endBatch();
            return true;
        }
        return false;
    }

    private static Rig.Motion redCore(Rig.Motion motion) { return name -> motion.at(name).tint(0xffff4038); }

    public static void idleWrist(LocalPlayer player, PoseStack pose, MultiBufferSource.BufferSource buffers, int light, float partial) {
        if (!player.isInvisible() && !player.isSpectator()) {
            AscalonRenderer.renderFirstPersonOverlay(player, pose, buffers, light, partial);
        }
        View view = ClientState.get(player);
        if (view == null || player.isInvisible() || player.isPassenger() || player.isUsingItem()) return;
        FP.step(player, view, partial);

        // Stow the human watch arm cleanly off-screen during ordinary gameplay so 2 stiff hands never clutter the screen!
        float peek = FP.humanPeekFactor;
        if (peek <= .015f) return;

        boolean left = player.getMainArm() == HumanoidArm.RIGHT;
        var hand = player.getOffhandItem();
        if (!hand.isEmpty()) return;
        int sign = left ? -1 : 1;
        float lift = Motion.ease(peek);
        float time = player.tickCount + partial;
        var walk = player.walkAnimation;
        float gaitPos = walk.position(partial) * .6662f;
        float gaitSpd = walk.speed(partial);
        float bobX = ClientState.reduced() ? 0f : Mth.cos(gaitPos) * gaitSpd * .022f;
        float bobY = ClientState.reduced() ? 0f : -Math.abs(Mth.sin(gaitPos)) * gaitSpd * .026f;
        float breath = Mth.sin(time * .11f) * .006f;

        pose.pushPose();
        pose.translate(
                sign * (.68 - .06 * lift) + bobX + FP.swayX * .75f,
                -.98 + .32 * lift + breath + bobY + FP.swayY * .75f + FP.airLagY - FP.landImpulse,
                -.84);
        Rig.rotate(pose,
                16 + 19 * lift,
                sign * (-34 + 8 * lift),
                sign * 13 + FP.swayRoll * .5f);
        humanArm(player, pose, buffers, light, left, .87f);
        pose.translate(0, .013, -.035);
        Rig.get(view.s().watch().id).draw(pose, buffers, Motion.watch(view.s().watch(), peek * .18f, 1, 0, time),
                .9f, -1, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        buffers.endBatch();
    }

    private static void humanArm(AbstractClientPlayer player, PoseStack pose, MultiBufferSource buffer, int light, boolean left, float scale) {
        boolean slim = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        ModelPart arm = left ? (slim ? LEFT_SLIM : LEFT_ARM) : (slim ? RIGHT_SLIM : RIGHT_ARM);
        ModelPart sleeve = left ? (slim ? LEFT_SLIM_SLEEVE : LEFT_SLEEVE) : (slim ? RIGHT_SLIM_SLEEVE : RIGHT_SLEEVE);
        var skin = player.getSkin().texture();
        pose.pushPose();
        Rig.rotate(pose, -90, 0, 0);
        pose.scale(scale, scale, scale);
        arm.render(pose, buffer.getBuffer(net.minecraft.client.renderer.RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
        if (player.isModelPartShown(left ? net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE : net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE)) {
            sleeve.render(pose, buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
    }

    static final class Invisible<T extends net.minecraft.world.entity.Entity> extends EntityRenderer<T> {
        Invisible(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(T entity) { return Omnitrix.id("textures/rig/omnitrix.png"); }
    }
}
