package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Abilities;
import cn.aca.omnitrix.Form;
import cn.aca.omnitrix.Kind;
import cn.aca.omnitrix.Omnitrix;
import cn.aca.omnitrix.PursuitBody;
import cn.aca.omnitrix.Wrist.Stage;
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

    private WatchRender() {}

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
        double now = view.now(partial);
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
            float elapsed = (float) (now - performance.start());
            // Immediate skills have their contact pose at tick zero.
            float weight = performance.impact() == performance.start() ? 1 : Math.min(1f, .50f + elapsed * .50f);
            pose = rig.animate(pose, clip, elapsed, weight);
        }
        if (form == Form.ALIEN_X && !carni) {
            var flick = ClientState.effect(player);
            if (flick != null && flick.cue().action() == cn.aca.omnitrix.WristNetwork.FLICK_CUE) {
                float age = player.level().getGameTime() - flick.tick() + partial;
                if (age >= 0 && age < 7) pose = rig.animate(pose, "flick", age, performance == null ? 1 : .28f);
            }
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
        fore.xRot = Mth.lerp(lift, fore.xRot, -1.17f);
        fore.yRot = Mth.lerp(lift, fore.yRot, sign * .51f);
        fore.zRot = Mth.lerp(lift, fore.zRot, sign * .11f);
        if (phase == Stage.ARMING && t < 18 && view.s().watch() != Kind.CARNITRIX) {
            float press = Motion.part(t, 1, 7) * (1 - Motion.part(t, 10, 17));
            free.xRot = Mth.lerp(press, free.xRot, -1.14f);
            free.yRot = Mth.lerp(press, free.yRot, -sign * .89f);
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

    private static void renderHeld(net.minecraft.world.entity.player.Player player, Rig rig, Rig.Motion motion, PoseStack pose,
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
            float bearing = automatic && (phase == Stage.ARMING || phase == Stage.LOCKING) ? ClientState.bearing(player, view, partial) : age * 6;
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
        var phase = view.s().phase();
        boolean focus = WristScreen.isOpen() || phase == Stage.BINDING || phase == Stage.ARMING || phase == Stage.LOCKING;
        boolean left = player.getMainArm() == HumanoidArm.RIGHT;
        int sign = left ? -1 : 1;
        float time = player.tickCount + partial;
        float age = view.age(partial);
        if (phase == Stage.REVERTING) {
            float glow = (float) Math.sin(Math.PI * Motion.part(age, 0, 10));
            if (glow > .12f) {
                pose.pushPose();
                pose.translate(sign * .31, -.25, -.66);
                Rig.rotate(pose, 38, sign * -23, sign * 11);
                Rig.get(view.s().watch().id).draw(pose, buffers, Motion.watch(view.s().watch(), glow * .6f, 1, 0, time), .68f,
                        DialDraw.alpha(-1, glow * .81f), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        if (focus) {
            float lift = phase == Stage.BINDING ? Motion.part(age, 0, 6) : WristScreen.lift(partial);
            if (phase == Stage.ARMING) lift = 1;
            if (phase == Stage.LOCKING) lift = Motion.part(age, 0, 4);
            Kind kind = view.s().watch();
            boolean automatic = kind == Kind.CARNITRIX;
            float press = phase == Stage.ARMING && !automatic ? Motion.part(age, 2, 6) * (1 - Motion.part(age, 9, 16)) : 0;
            float closure = phase == Stage.BINDING ? Motion.part(age, 2, 14)
                    : phase == Stage.ARMING && automatic ? .75f + .25f * Motion.part(age, 4, 19) : 1;
            float bearing = automatic && (phase == Stage.ARMING || phase == Stage.LOCKING)
                    ? ClientState.bearing(player, view, partial) : time * 6;
            if (phase == Stage.LOCKING && !ClientState.reduced()) bearing += Mth.sin(age * 1.8f) * 24;
            float breath = (float) Math.sin(time * .13) * .005f;
            float recoil = ClientState.reduced() ? 0 : WristPost.impact(view, partial);
            float dialYaw = WristScreen.isOpen() ? WristScreen.dialYaw(partial) : 0f;
            float dialTap = WristScreen.switchImpulse(partial);
            float lockShake = phase == Stage.LOCKING && !ClientState.reduced() ? Mth.sin(age * 2.7f) * .014f : 0f;
            pose.pushPose();
            pose.translate(sign * (.56 - .16 * lift) + lockShake, -.60 + .27 * lift + breath - .038 * recoil + .018 * dialTap, -1.02 - .10 * lift);
            Rig.rotate(pose, 24 + lift * 30 - dialTap * 4f, sign * (-28 + lift * 13), sign * 10);
            humanArm(player, pose, buffers, light, left, .95f);
            pose.translate(.0, .018, -.023);
            Rig.rotate(pose, 5, 0, 0);
            Rig.Motion motion = Motion.watch(kind, lift * .83f, closure, press, time, bearing, dialYaw);
            if (view.s().misfire() && phase == Stage.ARMING && age < 13) motion = redCore(motion);
            Rig.get(kind.id).draw(pose, buffers, motion, .73f,
                    -1, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            if (phase == Stage.ARMING && !automatic && age < 19) {
                pose.pushPose();
                float raise = Motion.part(age, 0, 2.5f) * (1 - Motion.part(age, 2.5f, 5f));
                float fall = Motion.part(age, 2, 6);
                float withdraw = Motion.part(age, 9, 17);
                pose.translate(-sign * (.52 - .74 * press), .52 + .22 * raise - .79 * fall - .58 * withdraw, -.86 - .24 * press);
                Rig.rotate(pose, 93 + raise * 18 - press * 42, -sign * (16 + 41 * press), -sign * (12 + 7 * press));
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
            var rider = player.getVehicle() instanceof PursuitBody pursuit ? pursuit : null;
            var gait = rider == null ? player.walkAnimation : rider.walkAnimation;
            boolean swim = form == Form.RIPJAWS && (rider == null ? player.isInWater() : rider.isInWater());
            Rig.Motion skillPose = animatedActor(player, view, rig, partial, gait.position(partial), gait.speed(partial),
                    0, 0, rider == null ? !player.onGround() : !rider.onGround(), swim, 1);
            Rig.Motion hands = bone -> {
                var j = skillPose.at(bone);
                float pitchGain = bone.startsWith("elbow_") ? .48f : .38f;
                return new Rig.Joint(j.x() * .45f, j.y() * .45f, j.z() * .45f,
                        Mth.clamp(j.pitch() * pitchGain, -24, 46), j.yaw() * .52f, j.roll() * .52f, j.scale(), j.tint());
            };
            boolean activeSkill = view.performance(partial) != null;
            for (HumanoidArm arm : HumanoidArm.values()) {
                boolean sideLeft = arm == HumanoidArm.LEFT;
                int side = sideLeft ? -1 : 1;
                String rigSide = sideLeft ? "l" : "r";
                float span = rig.armSpan(rigSide);
                float armScale = Mth.clamp(11.8f / span, .62f, 1.38f) * (form == Form.FOUR_ARMS ? .74f : .80f);
                float armPunch = Mth.clamp((skillPose.at("arm_" + rigSide).pitch() - 20) / 85f, 0, 1);
                pose.pushPose();
                if (form == Form.FOUR_ARMS) {
                    pose.translate(side * (.52 - .12 * armPunch), -.33 + Mth.sin(time * .35f) * .011 + .05 * armPunch,
                            -.42 - .24 * armPunch);
                    Rig.rotate(pose, 74 + attack * 22, side * (14 - attack * 18), side * 11);
                } else {
                    float pullY = span < 8f ? -.31f : -.35f;
                    float pullZ = span < 8f ? -.44f : -.50f;
                    pose.translate(side * .47, pullY + Mth.sin(time * .35f) * .011 + .04f * armPunch, pullZ - .16 * armPunch);
                    Rig.rotate(pose, 72 + attack * 22, side * (15 - attack * 18), side * 12);
                }
                rig.arm(pose, buffers, rigSide, hands, armScale, -1, light);
                var item = arm == player.getMainArm() ? player.getMainHandItem() : player.getOffhandItem();
                if (!carni && !item.isEmpty()) {
                    if (!rig.attachArmGrip(pose, rigSide, hands, armScale)) pose.translate(0, -.40, -.04);
                    Rig.rotate(pose, -63, side * 18, 0);
                    renderer.renderItem(player, item, sideLeft ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                            sideLeft, pose, buffers, light);
                }
                pose.popPose();
                if (form == Form.FOUR_ARMS && (activeSkill || attack > .02f)) {
                    float extraPunch = Mth.clamp((skillPose.at("extra_" + rigSide).pitch() - 18) / 78f, 0, 1);
                    pose.pushPose();
                    pose.translate(side * (.36 - .10 * extraPunch), -.56 + .09 * extraPunch, -.46 - .26 * extraPunch);
                    Rig.rotate(pose, 78 + attack * 20, side * 18, side * 8);
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
        boolean left = player.getMainArm() == HumanoidArm.RIGHT;
        var hand = player.getOffhandItem();
        if (!hand.isEmpty()) return;
        int sign = left ? -1 : 1;
        pose.pushPose();
        pose.translate(sign * .64, -.66, -.82);
        Rig.rotate(pose, 34, sign * -27, sign * 13);
        humanArm(player, pose, buffers, light, left, .87f);
        pose.translate(0, .013, -.035);
        Rig.get(view.s().watch().id).draw(pose, buffers, Motion.watch(view.s().watch(), 0, 1, 0, player.tickCount + partial),
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
