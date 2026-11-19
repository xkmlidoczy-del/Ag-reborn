package cn.aca.omnitrix.client;

import cn.aca.omnitrix.AscalonService;
import cn.aca.omnitrix.AscalonSlash;
import cn.aca.omnitrix.Omnitrix;
import cn.aca.omnitrix.WristNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

public final class AscalonRenderer {
    private static final float TAU = (float) (Math.PI * 2.0);
    // Style A Palette: Galvanic Star-Forged Paladin (cool platinum-ivory, brushed silver, royal slate-cobalt, muted gold)
    private static final int PLATINUM_IVORY = 0xfff7faff;
    private static final int PLATINUM_SILVER = 0xffdce8f8;
    private static final int SLATE_COBALT = 0xff7e9cc8;
    private static final int CHAMPAGNE_GOLD = 0xffebd39c;

    private static final int COPPER = 0xffc38a4f;
    private static final int INDIGO = 0xff51486e;
    private record ActiveWaypoint(Vec3 origin, Vec3 dest, long startTick) {}
    private record ActiveFissure(Vec3 from, Vec3 to, boolean iaido, long startTick) {}
    private record ActiveSonicRing(Vec3 center, Vec3 axis, int tier, long startTick) {}
    private record BannerToast(String titleKey, String subKey, boolean golden, long startTick, int duration) {}
    private record FlightRibbonSample(Vec3 left, Vec3 right, Vec3 capeTip, Vec3 wingAxis, float machRatio) {}

    private static final class CapeSpring {
        float pitch;
        float sway;
    }

    /** Per-player Viltrumite Flight Kinematics & Sub-Stepped Aerodynamic Ribbon State. */
    private static final class FlightPoseState {
        float prevHover, hover;
        float prevLean, lean;
        float prevPitchDeg, pitchDeg;
        float prevBankDeg, bankDeg;
        float prevMult = 1f, mult = 1f;
        float lastYaw;
        boolean initialized;
        boolean ribbonAttached;
        Vec3 smoothedDir = new Vec3(0, 0, 1);
        Vec3 lastSampleBase;
        final ArrayDeque<FlightRibbonSample> ribbon = new ArrayDeque<>();

        float hover(float p) { return Mth.lerp(p, prevHover, hover); }
        float lean(float p) { return Mth.lerp(p, prevLean, lean); }
        float pitch(float p) { return Mth.lerp(p, prevPitchDeg, pitchDeg); }
        float bank(float p) { return Mth.lerp(p, prevBankDeg, bankDeg); }
        float mult(float p) { return Mth.lerp(p, prevMult, mult); }
    }

    private static final Map<UUID, ArrayDeque<Vec3>> BLADE_TRAILS = new HashMap<>();
    private static final Map<UUID, CapeSpring> CAPE_SPRINGS = new HashMap<>();
    private static final Map<UUID, FlightPoseState> FLIGHT_STATES = new HashMap<>();
    private static final List<ActiveWaypoint> WAYPOINTS = new ArrayList<>();
    private static final List<ActiveFissure> FISSURES = new ArrayList<>();
    private static final List<ActiveSonicRing> SONIC_RINGS = new ArrayList<>();
    private static BannerToast activeBanner;
    private static BlockEntityWithoutLevelRenderer itemRendererInstance;

    private AscalonRenderer() {}

    static BlockEntityWithoutLevelRenderer itemRenderer() {
        if (itemRendererInstance == null) {
            var mc = Minecraft.getInstance();
            itemRendererInstance = new BlockEntityWithoutLevelRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels()) {
                @Override
                public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                                         MultiBufferSource buffers, int light, int overlay) {
                    renderSwordItem(stack, ctx, pose, buffers, light, overlay);
                }
            };
        }
        return itemRendererInstance;
    }

    static void clear() {
        BLADE_TRAILS.clear();
        CAPE_SPRINGS.clear();
        FLIGHT_STATES.clear();
        WAYPOINTS.clear();
        FISSURES.clear();
        SONIC_RINGS.clear();
        activeBanner = null;
    }

    public static boolean isMach9BannerActive() {
        return activeBanner != null && "ascalon.aca_omnitrix.banner_mach_9".equals(activeBanner.titleKey);
    }

    public static int activeFlightRibbonNodes(Player player) {
        var st = FLIGHT_STATES.get(player.getUUID());
        return st == null ? 0 : st.ribbon.size();
    }

    static float cameraBankRoll(Player player, float partial) {
        var st = FLIGHT_STATES.get(player.getUUID());
        return st == null ? 0f : st.bank(partial) * st.lean(partial);
    }

    static float playerFlightMultiplier(Player player, float partial) {
        var st = FLIGHT_STATES.get(player.getUUID());
        return st == null ? 1f : st.mult(partial);
    }

    private static void pushBanner(String titleKey, String subKey, boolean golden, long now, int duration) {
        activeBanner = new BannerToast(titleKey, subKey, golden, now, duration);
    }

    static void onCue(WristNetwork.Cue cue, long now) {
        var mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && cue.actor().equals(mc.player.getUUID());

        switch (cue.action()) {
            case WristNetwork.ASCALON_ARMOR_CUE -> {
                if (isSelf && cue.beat() == 1) {
                    pushBanner("ascalon.aca_omnitrix.banner_armor_on", "ascalon.aca_omnitrix.banner_armor_sub", false, now, 42);
                }
            }
            case WristNetwork.ASCALON_PARRY_CUE -> {
                if (isSelf && cue.beat() == 2) {
                    pushBanner("ascalon.aca_omnitrix.banner_parry", "ascalon.aca_omnitrix.banner_parry_sub", true, now, 34);
                }
            }
            case WristNetwork.ASCALON_SWING_CUE -> {
                if (isSelf) {
                    if (cue.beat() == 4) {
                        pushBanner("ascalon.aca_omnitrix.banner_counter", "ascalon.aca_omnitrix.banner_counter_sub", true, now, 38);
                    } else if (cue.beat() == 5) {
                        pushBanner("ascalon.aca_omnitrix.banner_iaido", "ascalon.aca_omnitrix.banner_iaido_sub", true, now, 42);
                    } else if (cue.projectile() && cue.beat() == 3) {
                        pushBanner("ascalon.aca_omnitrix.banner_crescent", "ascalon.aca_omnitrix.banner_crescent_sub", false, now, 34);
                    }
                }
            }
            case WristNetwork.ASCALON_RIFT_CUE -> {
                if (cue.beat() == 0) {
                    WAYPOINTS.add(new ActiveWaypoint(cue.from(), cue.to(), now));
                    while (WAYPOINTS.size() > 6) WAYPOINTS.removeFirst();
                    if (isSelf) pushBanner("ascalon.aca_omnitrix.banner_rift_1", "ascalon.aca_omnitrix.banner_rift_1_sub", false, now, 36);
                } else if (cue.beat() == 1) {
                    WAYPOINTS.clear();
                    if (isSelf) pushBanner("ascalon.aca_omnitrix.banner_rift_2", "ascalon.aca_omnitrix.banner_rift_2_sub", true, now, 40);
                }
            }
            case WristNetwork.ASCALON_TEMPEST_CUE -> {
                if (cue.beat() == 0) {
                    if (isSelf) pushBanner("ascalon.aca_omnitrix.banner_array_1", "ascalon.aca_omnitrix.banner_array_1_sub", false, now, 38);
                } else if (cue.beat() == 1) {
                    if (isSelf) pushBanner("ascalon.aca_omnitrix.banner_array_2", "ascalon.aca_omnitrix.banner_array_2_sub", true, now, 42);
                }
            }
            case WristNetwork.ASCALON_SYZYGY_CUE -> {
                if (isSelf) {
                    pushBanner("ascalon.aca_omnitrix.banner_syzygy", "ascalon.aca_omnitrix.banner_syzygy_sub", true, now, 56);
                }
            }
            case WristNetwork.ASCALON_MACH_CUE -> {
                Vec3 axis = cue.to().subtract(cue.from());
                axis = axis.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : axis.normalize();
                SONIC_RINGS.add(new ActiveSonicRing(cue.from(), axis, Math.max(1, cue.beat()), now));
                while (SONIC_RINGS.size() > 14) SONIC_RINGS.removeFirst();
                if (isSelf && cue.beat() >= 3) {
                    pushBanner("ascalon.aca_omnitrix.banner_mach_9", "ascalon.aca_omnitrix.banner_mach_9_sub", true, now, 46);
                }
            }
            case WristNetwork.ASCALON_FISSURE_CUE -> {
                FISSURES.add(new ActiveFissure(cue.from(), cue.to(), cue.linked(), now));
                while (FISSURES.size() > 54) FISSURES.removeFirst();
            }
            default -> {}
        }
    }

    /**
     * Whole-Body Viltrumite Flight Transform (called from PlayerFlightPose Mixin in PlayerRenderer.setupRotations):
     * - Stationary Hover: Calm vertical smart-atom levitation wave + subtle -5° heroic chest poise.
     * - Ballistic Flight: Aligns yaw with true 3D flight trajectory, pitches spine head-first (-88° - flightPitch) & rolls on turns!
     */
    public static void applyViltrumBodyTransform(AbstractClientPlayer player, PoseStack pose,
                                                 float bob, float yBodyRot, float partialTick) {
        var wristView = ClientState.get(player);
        if (wristView != null && wristView.s().morphed()) return;
        var fs = FLIGHT_STATES.get(player.getUUID());
        if (fs == null) return;
        float hover = fs.hover(partialTick);
        if (hover <= .01f) return;

        float lean = fs.lean(partialTick);
        float pitch = fs.pitch(partialTick);
        float bank = fs.bank(partialTick) * lean;
        float hoverWave = ClientState.reduced() ? 0f : (1f - lean * .82f) * hover * Mth.sin(bob * .085f) * .058f;

        if (lean > .01f && fs.smoothedDir.horizontalDistanceSqr() > 1e-4) {
            float flightYaw = (float) Math.toDegrees(Math.atan2(-fs.smoothedDir.x, fs.smoothedDir.z));
            float yawFix = Mth.wrapDegrees(yBodyRot - flightYaw) * lean * hover;
            pose.mulPose(Axis.YP.rotationDegrees(yawFix));
        }
        pose.translate(0f, .92f + hoverWave, 0f);
        float bodyPitchDeg = hover * (-5.2f * (1f - lean) + lean * (-88.0f - pitch));
        pose.mulPose(Axis.XP.rotationDegrees(bodyPitchDeg));
        if (Math.abs(bank) > .05f) {
            pose.mulPose(Axis.YP.rotationDegrees(bank));
        }
        pose.translate(0f, -.92f, 0f);
    }

    /**
     * Limb & Head Viltrumite Kinematics:
     * - Hover (lean -> 0): Asymmetric single-knee Viltrumite levitation stance.
     * - Cruise/Mach-9 (lean -> 1): Visor looking straight ahead, arms swept sleekly back along flanks, legs locked in spearline!
     */
    static void poseViltrumModel(PlayerModel<?> model, AbstractClientPlayer player) {
        var fs = FLIGHT_STATES.get(player.getUUID());
        if (fs == null) return;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float hover = fs.hover(partial);
        if (hover <= .01f) return;
        float lean = fs.lean(partial);

        model.head.xRot = Mth.lerp(hover * lean, model.head.xRot, -1.26f);
        model.head.yRot = Mth.lerp(hover * lean * .85f, model.head.yRot, 0f);
        model.hat.copyFrom(model.head);

        float targetLeftLegX = Mth.lerp(lean, -.42f, .04f);
        float targetLeftLegY = Mth.lerp(lean, 11.15f, 12.0f);
        float targetLeftLegZ = Mth.lerp(lean, -1.45f, 0.0f);
        float targetLeftLegRoll = Mth.lerp(lean, -.04f, -.022f);

        float targetRightLegX = Mth.lerp(lean, .11f, .04f);
        float targetRightLegY = 12.0f;
        float targetRightLegZ = Mth.lerp(lean, .38f, 0.0f);
        float targetRightLegRoll = Mth.lerp(lean, .04f, .022f);

        model.leftLeg.xRot = Mth.lerp(hover, model.leftLeg.xRot, targetLeftLegX);
        model.leftLeg.y = Mth.lerp(hover, model.leftLeg.y, targetLeftLegY);
        model.leftLeg.z = Mth.lerp(hover, model.leftLeg.z, targetLeftLegZ);
        model.leftLeg.zRot = Mth.lerp(hover, model.leftLeg.zRot, targetLeftLegRoll);
        model.leftLeg.yRot = Mth.lerp(hover, model.leftLeg.yRot, 0f);

        model.rightLeg.xRot = Mth.lerp(hover, model.rightLeg.xRot, targetRightLegX);
        model.rightLeg.y = Mth.lerp(hover, model.rightLeg.y, targetRightLegY);
        model.rightLeg.z = Mth.lerp(hover, model.rightLeg.z, targetRightLegZ);
        model.rightLeg.zRot = Mth.lerp(hover, model.rightLeg.zRot, targetRightLegRoll);
        model.rightLeg.yRot = Mth.lerp(hover, model.rightLeg.yRot, 0f);

        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);

        var asc = ClientState.ascalon(player);
        float iaido = asc == null ? 0f : asc.iaidoChargeProgress(partial);
        float swing = player.getAttackAnim(partial);
        boolean busyArms = swing > .02f || player.isUsingItem() || iaido > .05f;
        float armWeight = hover * (busyArms ? .22f : 1f);
        if (armWeight > .01f) {
            float targetRightArmX = Mth.lerp(lean, -.20f, .32f);
            float targetRightArmZ = Mth.lerp(lean, .13f, .15f);
            float targetLeftArmX = Mth.lerp(lean, -.16f, .32f);
            float targetLeftArmZ = Mth.lerp(lean, -.13f, -.15f);

            model.rightArm.xRot = Mth.lerp(armWeight, model.rightArm.xRot, targetRightArmX);
            model.rightArm.yRot = Mth.lerp(armWeight, model.rightArm.yRot, .04f * lean);
            model.rightArm.zRot = Mth.lerp(armWeight, model.rightArm.zRot, targetRightArmZ);

            model.leftArm.xRot = Mth.lerp(armWeight, model.leftArm.xRot, targetLeftArmX);
            model.leftArm.yRot = Mth.lerp(armWeight, model.leftArm.yRot, -.04f * lean);
            model.leftArm.zRot = Mth.lerp(armWeight, model.leftArm.zRot, targetLeftArmZ);

            model.leftSleeve.copyFrom(model.leftArm);
            model.rightSleeve.copyFrom(model.rightArm);
        }
    }

    static void tick(long now) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        WAYPOINTS.removeIf(w -> now - w.startTick >= 80);
        FISSURES.removeIf(f -> now - f.startTick >= (f.iaido ? 44 : 32));
        SONIC_RINGS.removeIf(s -> now - s.startTick >= 28);
        if (activeBanner != null && now - activeBanner.startTick > activeBanner.duration) {
            activeBanner = null;
        }

        for (Player p : mc.level.players()) {
            boolean holding = AscalonService.holdingAscalon(p);
            var asc = ClientState.ascalon(p);
            var wristView = ClientState.get(p);
            boolean morphed = wristView != null && wristView.s().morphed();
            boolean armored = asc != null && asc.s().armored() && !morphed;

            var fs = FLIGHT_STATES.computeIfAbsent(p.getUUID(), k -> new FlightPoseState());
            fs.prevHover = fs.hover;
            fs.prevLean = fs.lean;
            fs.prevPitchDeg = fs.pitchDeg;
            fs.prevBankDeg = fs.bankDeg;
            fs.prevMult = fs.mult;

            boolean flying = armored && p.getAbilities().flying && !p.onGround() && !p.isFallFlying() && !p.isSpectator();
            Vec3 rawVel = p == mc.player ? p.getDeltaMovement()
                    : new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
            double speed = rawVel.length();

            float targetHover = flying ? 1f : 0f;
            float targetLean = (flying && speed > .10) ? Mth.clamp((float) ((speed - .08) / .38), 0f, 1f) : 0f;
            fs.hover = Mth.lerp(.30f, fs.hover, targetHover);
            // Snap upright quickly on instant smart-atom hover brake!
            fs.lean = Mth.lerp(targetLean < fs.lean ? .42f : .26f, fs.lean, targetLean);

            float curYaw = p.getYRot();
            float dYaw = fs.initialized ? Mth.wrapDegrees(curYaw - fs.lastYaw) : 0f;
            fs.lastYaw = curYaw;
            fs.initialized = true;

            if (flying && speed > .06) {
                Vec3 dir = rawVel.normalize();
                fs.smoothedDir = fs.smoothedDir.lerp(dir, .48).normalize();
                double horiz = Math.hypot(fs.smoothedDir.x, fs.smoothedDir.z);
                float velPitch = (float) -Math.toDegrees(Math.atan2(fs.smoothedDir.y, Math.max(1e-4, horiz)));
                fs.pitchDeg = Mth.lerp(.36f, fs.pitchDeg, Mth.clamp(velPitch, -86f, 86f));
                float targetBank = Mth.clamp(-dYaw * 2.25f, -30f, 30f) * fs.lean;
                fs.bankDeg = Mth.lerp(.26f, fs.bankDeg, targetBank);
            } else {
                double lookYawRad = Math.toRadians(curYaw);
                fs.smoothedDir = new Vec3(-Math.sin(lookYawRad), 0, Math.cos(lookYawRad));
                fs.pitchDeg = Mth.lerp(.34f, fs.pitchDeg, 0f);
                fs.bankDeg = Mth.lerp(.34f, fs.bankDeg, 0f);
            }

            float rawMult = 1f;
            if (flying && speed > .05) {
                if (p == mc.player) {
                    rawMult = AscalonService.localFlightMultiplier(p, 1f);
                } else {
                    float snapMult = asc == null ? 1f : AscalonService.flightMultiplierFromBoost(asc.s().flightBoost());
                    float speedMult = (float) Mth.clamp(speed / AscalonService.BASE_FLIGHT_BLOCKS_PER_TICK,
                            1.0, AscalonService.MAX_FLIGHT_MULTIPLIER);
                    rawMult = Math.max(snapMult, speedMult);
                }
            }
            // Instant HUD & VFX reset when player executes smart-atom hover brake!
            if (p == mc.player && speed < 0.02) {
                fs.mult = 1.0f;
                fs.prevMult = 1.0f;
            } else {
                fs.mult = Mth.lerp(.42f, fs.mult, rawMult);
            }
            float machRatio = Mth.clamp((fs.mult - 1.0f) / 8.0f, 0f, 1f);

            fs.ribbonAttached = !ClientState.reduced() && flying && speed > .12 && !p.isInvisible()
                    && (mc.player == null || p.distanceToSqr(mc.player) < 120 * 120);
            if (fs.ribbonAttached) {
                Vec3 curBase = p.position();
                Vec3 prevBase = fs.lastSampleBase != null && fs.lastSampleBase.distanceToSqr(curBase) < 36.0
                        ? fs.lastSampleBase : curBase;
                double dist = prevBase.distanceTo(curBase);
                int subSteps = Math.max(1, Math.min(4, (int) Math.ceil(dist / .85)));
                for (int s = 1; s <= subSteps; s++) {
                    float frac = (float) s / subSteps;
                    Vec3 basePt = prevBase.lerp(curBase, frac);
                    fs.ribbon.addLast(computeRibbonAnchors(basePt, fs.smoothedDir, fs.lean, fs.bankDeg, machRatio,
                            now + frac, p.getId()));
                }
                int maxNodes = 14 + Math.round(machRatio * 18f);
                while (fs.ribbon.size() > maxNodes) fs.ribbon.removeFirst();
                fs.lastSampleBase = curBase;
            } else {
                fs.lastSampleBase = null;
                for (int k = 0; k < 4 && !fs.ribbon.isEmpty(); k++) {
                    fs.ribbon.removeFirst();
                }
            }

            if (armored) {
                var cape = CAPE_SPRINGS.computeIfAbsent(p.getUUID(), k -> new CapeSpring());
                float horizSpeed = (float) Math.hypot(rawVel.x, rawVel.z);
                float fallLift = (float) Mth.clamp(-rawVel.y * 38.0, -8.0, 42.0);
                float groundTarget = 9.5f + Math.min(48f, horizSpeed * 92f + p.walkAnimation.speed() * 28f) + fallLift;
                if (p.isCrouching() && !flying) groundTarget += 18f;
                float targetPitch = Mth.lerp(fs.lean, groundTarget, 6.8f + machRatio * 3.0f);
                cape.pitch = Mth.lerp(.30f, cape.pitch, targetPitch);
                cape.sway = Mth.lerp(.24f, cape.sway,
                        Mth.sin(now * (.14f + machRatio * .28f) + p.getId()) * (2.5f + horizSpeed * 6f * (1f - fs.lean * .65f)));
            }

            if (ClientState.reduced() || (!holding && !armored) || p.isInvisible()
                    || (mc.player != null && p.distanceToSqr(mc.player) > 48 * 48)) {
                var old = BLADE_TRAILS.get(p.getUUID());
                if (old != null && !old.isEmpty()) old.removeFirst();
                continue;
            }

            if (holding && fs.lean < .35f && (asc == null || asc.iaidoChargeProgress(0f) < .15f)) {
                float yawRad = (float) Math.toRadians(p.yBodyRot);
                boolean leftHand = (p.getMainArm() == HumanoidArm.LEFT) == p.getMainHandItem().is(Omnitrix.ASCALON.get());
                double sideSign = leftHand ? 1.0 : -1.0;
                Vec3 side = new Vec3(Math.cos(yawRad) * .48 * sideSign, 0, Math.sin(yawRad) * .48 * sideSign);
                Vec3 fwd = p.getLookAngle().normalize();
                double swingArc = Math.sin(p.getAttackAnim(1f) * Math.PI) * 1.15;
                Vec3 tip = p.position().add(0, 1.32 - swingArc * .35, 0).add(side).add(fwd.scale(1.28 + swingArc));
                var trail = BLADE_TRAILS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
                if (trail.isEmpty() || trail.getLast().distanceToSqr(tip) > .014) {
                    trail.addLast(tip);
                    while (trail.size() > 12) trail.removeFirst();
                } else {
                    trail.removeFirst();
                }
            } else {
                var trail = BLADE_TRAILS.get(p.getUUID());
                if (trail != null && !trail.isEmpty()) trail.removeFirst();
            }
        }
        BLADE_TRAILS.values().removeIf(ArrayDeque::isEmpty);
        FLIGHT_STATES.entrySet().removeIf(e -> mc.level.getPlayerByUUID(e.getKey()) == null);
    }

    /**
     * Wide falcon-flank aerodynamic ribbon anchors so ribbons frame the Paladin's silhouette cleanly
     * without passing straight through the central THIRD_PERSON_BACK camera ray!
     */
    private static FlightRibbonSample computeRibbonAnchors(Vec3 basePos, Vec3 fwdDir, float lean, float bankDeg,
                                                           float machRatio, float time, int entitySeed) {
        Vec3 fwd = fwdDir.lengthSqr() < 1e-5 ? new Vec3(0, 0, 1) : fwdDir.normalize();
        Vec3 worldUp = Math.abs(fwd.y) > .93 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = fwd.cross(worldUp).normalize();
        Vec3 up = right.cross(fwd).normalize();
        double bankRad = Math.toRadians(bankDeg * lean);
        Vec3 bankedRight = right.scale(Math.cos(bankRad)).add(up.scale(Math.sin(bankRad)));
        Vec3 bankedUp = bankedRight.cross(fwd).normalize();

        Vec3 com = basePos.add(0, .92, 0);
        Vec3 shoulderCenter = com
                .add(0, .42 * (1.0 - lean), 0)
                .add(fwd.scale(.26 * lean - .18 * (1.0 - lean)))
                .add(bankedUp.scale(.16 * lean));

        // Asymmetric phase flutter between left & right swallowtail wings
        double waveL = Math.sin(time * (.28 + machRatio * .24) + entitySeed) * (.055 + machRatio * .045);
        double waveR = Math.cos(time * (.26 + machRatio * .22) + entitySeed * 1.3) * (.055 + machRatio * .045);
        double span = .56 + machRatio * .20;
        Vec3 leftPt = shoulderCenter.subtract(bankedRight.scale(span)).add(bankedUp.scale(waveL));
        Vec3 rightPt = shoulderCenter.add(bankedRight.scale(span * 1.06)).add(bankedUp.scale(waveR + .03));
        Vec3 capeTip = com.subtract(fwd.scale(.48 * lean)).add(bankedUp.scale(.18 * lean));
        return new FlightRibbonSample(leftPt, rightPt, capeTip, bankedRight, machRatio);
    }

    /** 3D Polygonal Ascalon Sword Renderer + Iaido Sheathing + Zero-Drag Swept Wing-Blade Alignment (-110°)! */
    private static void renderSwordItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                                        MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        CosmicVfxShader.clock(partial);
        AscalonSurfaceShader.clock(partial);
        float time = (mc.level == null ? (System.currentTimeMillis() % 100000L) / 50f : mc.level.getGameTime() + partial);
        var selfAsc = ClientState.selfAscalon();
        float iaido = selfAsc == null ? 0f : selfAsc.iaidoChargeProgress(partial);
        float selfFlightLean = mc.player == null ? 0f
                : FLIGHT_STATES.getOrDefault(mc.player.getUUID(), new FlightPoseState()).lean(partial);

        pose.pushPose();
        pose.translate(.5f, .5f, .5f);
        float rigScale = .88f;
        boolean gui = ctx == ItemDisplayContext.GUI;

        switch (ctx) {
            case GUI -> {
                float hover = ClientState.reduced() ? 0f : Mth.sin(time * .09f) * .018f;
                pose.translate(-.18f, -.20f + hover, 0f);
                pose.mulPose(Axis.ZP.rotationDegrees(-45f));
                pose.mulPose(Axis.YP.rotationDegrees(ClientState.reduced() ? 18f : 18f + Mth.sin(time * .05f) * 6f));
                rigScale = .54f;
            }
            case GROUND -> {
                pose.translate(0f, -.08f + Mth.sin(time * .12f) * .035f, 0f);
                pose.mulPose(Axis.ZP.rotationDegrees(-35f));
                rigScale = .60f;
            }
            case FIXED -> {
                pose.translate(.16f, -.18f, 0f);
                pose.mulPose(Axis.ZP.rotationDegrees(45f));
                pose.mulPose(Axis.YP.rotationDegrees(180f));
                rigScale = .66f;
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                if (iaido > .15f) {
                    pose.scale(.001f, .001f, .001f);
                } else {
                    pose.translate(0f, .10f, .04f);
                    // -109° compensates arm xRot (+18.3°) so the broadsword locks razor-parallel along the swept forearm!
                    pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(selfFlightLean, -78f, -109f)));
                    pose.mulPose(Axis.YP.rotationDegrees(ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? -90f : 90f));
                    rigScale = .90f;
                }
            }
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
                int sign = left ? -1 : 1;
                boolean guarding = mc.player != null && mc.player.isUsingItem() && mc.player.getUseItem() == stack;
                if (iaido > .02f) {
                    float tremor = iaido >= .85f && !ClientState.reduced() ? Mth.sin(time * 2.4f) * .006f : 0f;
                    pose.translate(sign * (-.48f * iaido + .05f * (1f - iaido)) + tremor,
                            -.32f * iaido - .10f * (1f - iaido),
                            -.18f * iaido - .08f * (1f - iaido));
                    pose.mulPose(Axis.ZP.rotationDegrees(sign * 72f * iaido));
                    pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(iaido, -68f, -12f)));
                    pose.mulPose(Axis.YP.rotationDegrees(sign * Mth.lerp(iaido, 86f, 24f)));
                } else if (guarding) {
                    pose.translate(sign * -.16f, -.05f, -.14f);
                    pose.mulPose(Axis.ZP.rotationDegrees(sign * 24f));
                    pose.mulPose(Axis.XP.rotationDegrees(-16f));
                    pose.mulPose(Axis.YP.rotationDegrees(sign * 66f));
                } else {
                    pose.translate(sign * (.05f + .06f * selfFlightLean), -.10f - .08f * selfFlightLean, -.08f - .06f * selfFlightLean);
                    pose.mulPose(Axis.ZP.rotationDegrees(sign * -10f * selfFlightLean));
                    pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(selfFlightLean, -68f, -84f)));
                    pose.mulPose(Axis.YP.rotationDegrees(sign * 86f));
                }
                rigScale = .86f;
            }
            default -> rigScale = .70f;
        }

        Rig sword = Rig.get("ascalon");
        float pulse = .84f + .16f * Mth.sin(time * .16f);
        int pulseTint = DialDraw.alpha(PLATINUM_SILVER, pulse);
        Rig.Motion motion = bone -> new Rig.Joint(0, 0, 0, 0, 0, 0, 1f, pulseTint);
        sword.drawAscalonSurface(pose, buffers, motion, rigScale, LightTexture.FULL_BRIGHT, overlay);

        if ((!ClientState.reduced() || gui) && !(iaido > .15f && (ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND))) {
            float unit = rigScale / 16f;
            Vec3 hilt = new Vec3(0, 3.0 * unit, 0);
            Vec3 tip = new Vec3(0, 33.4 * unit, 0);
            Vec3 localCam = new Vec3(0, 15 * unit, 2.4);
            VfxPrimitives.lusterRibbonEroded(pose, buffers, hilt, tip, localCam,
                    .68 * unit, .14 * unit, DialDraw.alpha(PLATINUM_IVORY, gui ? .42f : .50f), 1.8f, 0.22f, false);
        }
        pose.popPose();
    }

    static void renderFirstPersonOverlay(LocalPlayer player, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                         int light, float partial) {
        if (Minecraft.getInstance().getCameraEntity() != player) return;
        var asc = ClientState.ascalon(player);
        boolean armored = asc != null && asc.s().armored();
        boolean holding = AscalonService.holdingAscalon(player);
        float iaido = asc == null ? 0f : asc.iaidoChargeProgress(partial);
        boolean parryReady = asc != null && asc.parryCounterReady(partial);
        if (!armored && !(holding && player.isUsingItem()) && iaido <= .02f) return;

        CosmicVfxShader.clock(partial);
        float time = player.tickCount + partial;
        boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
        float flightLean = FLIGHT_STATES.getOrDefault(player.getUUID(), new FlightPoseState()).lean(partial);

        if (armored && !WristScreen.isOpen()) {
            Rig armorRig = Rig.get("ascalon_armor");
            float swing = player.getAttackAnim(partial);
            int sign = rightMain ? 1 : -1;
            pose.pushPose();
            pose.translate(sign * (.54f - swing * .18f - iaido * .36f + flightLean * .06f),
                    -.54f + Mth.sin(swing * (float) Math.PI) * .16f - iaido * .12f - flightLean * .08f,
                    -.72f - swing * .14f + iaido * .10f);
            Rig.rotate(pose, 68f + swing * 24f - iaido * 22f - flightLean * 12f,
                    sign * (-18f + iaido * 34f), sign * 12f);
            armorRig.limb(pose, buffers, rightMain ? "arm_r" : "arm_l", Rig.STILL, .82f, -1, LightTexture.FULL_BRIGHT, false);
            pose.popPose();
        }

        if (holding && player.isUsingItem()) {
            pose.pushPose();
            pose.translate(0f, -.04f, -.94f);
            float spin = ClientState.reduced() ? 0f : time * .06f;
            int plateCol = DialDraw.alpha(parryReady ? CHAMPAGNE_GOLD : SLATE_COBALT, .44f);
            int trimCol = DialDraw.alpha(parryReady ? CHAMPAGNE_GOLD : PLATINUM_IVORY, .92f);
            VfxPrimitives.heraldicHexShield(pose, buffers, .44, spin, plateCol, trimCol);
            pose.popPose();
            buffers.endBatch(CosmicVfxShader.voidSheath());
            buffers.endBatch(CosmicVfxShader.glow());
        } else if (iaido > .05f) {
            pose.pushPose();
            pose.translate(-.28f * (rightMain ? 1 : -1), -.28f, -.86f);
            pose.mulPose(Axis.XP.rotationDegrees(74f));
            float spin = ClientState.reduced() ? 0f : time * .18f;
            VfxPrimitives.astrolabeRing(pose, buffers, Vec3.ZERO, .16 + iaido * .22, .07,
                    spin, spin + TAU, 32, DialDraw.alpha(iaido >= .8f ? CHAMPAGNE_GOLD : PLATINUM_IVORY, .88f * iaido));
            pose.popPose();
            buffers.endBatch(CosmicVfxShader.voidSheath());
            buffers.endBatch(CosmicVfxShader.glow());
        }
    }

    static final class ArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        ArmorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                           float walk, float speed, float partial, float age, float headYaw, float headPitch) {
            if (player.isInvisible()) return;
            var wristView = ClientState.get(player);
            if (wristView != null && wristView.s().morphed()) return;

            var asc = ClientState.ascalon(player);
            float factor = asc == null ? 0f : asc.transitionFactor(partial);
            boolean phantomActive = asc != null && asc.phantomArrayActive(partial);
            if (factor <= .01f && !phantomActive) return;

            CosmicVfxShader.clock(partial);
            AscalonSurfaceShader.clock(partial);
            PlayerModel<AbstractClientPlayer> model = getParentModel();
            var fs = FLIGHT_STATES.get(player.getUUID());
            float flightHover = fs == null ? 0f : fs.hover(partial);
            float flightLean = fs == null ? 0f : fs.lean(partial);
            float machRatio = fs == null ? 0f : Mth.clamp((fs.mult(partial) - 1f) / 8f, 0f, 1f);

            if (factor > .01f) {
                Rig armor = Rig.get("ascalon_armor");
                boolean slim = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
                int color = factor >= .99f ? -1 : DialDraw.alpha(PLATINUM_SILVER, factor);
                int armorLight = Math.max(light, LightTexture.pack(13, 15));
                float s = .97f + .03f * factor;

                boolean combatVisorClosed = AscalonService.holdingAscalon(player) || phantomActive
                        || flightHover > .1f || (asc != null && asc.skySyzygyActive(partial));
                float visorPitch = (1f - factor) * -52f + (combatVisorClosed ? 0f : -42f * factor);
                float iaido = asc == null ? 0f : asc.iaidoChargeProgress(partial);
                var cape = CAPE_SPRINGS.get(player.getUUID());
                float baseCapePitch = cape == null ? (10f + speed * 32f) : cape.pitch;
                float capeSway = cape == null ? 0f : cape.sway;
                float strideKick = Math.abs(Mth.sin(walk * .6662f)) * speed * 12f * (1f - flightHover);

                float waveFreq = .13f + machRatio * .42f;
                float waveAmp = 1f + machRatio * .55f;
                float wave1 = ClientState.reduced() ? 0f : Mth.sin(age * waveFreq) * 2.8f * waveAmp;
                float wave2 = ClientState.reduced() ? 0f : Mth.sin(age * waveFreq - .85f) * 4.2f * waveAmp;
                float wave3 = ClientState.reduced() ? 0f : Mth.sin(age * waveFreq - 1.70f) * 5.6f * waveAmp;
                float wave4 = ClientState.reduced() ? 0f : Mth.sin(age * waveFreq - 2.55f) * 7.2f * waveAmp;

                Rig.Motion motion = boneId -> switch (boneId) {
                    case "visor" -> Rig.Joint.bend(visorPitch, 0f, 0f);
                    case "cape_1" -> Rig.Joint.bend(-(baseCapePitch * .42f + wave1), 0f, capeSway * .35f);
                    case "cape_2" -> Rig.Joint.bend(-(baseCapePitch * .24f + wave2), 0f, capeSway * .30f);
                    case "cape_3" -> Rig.Joint.bend(-(baseCapePitch * .18f + wave3), 0f, capeSway * .25f);
                    case "cape_4" -> Rig.Joint.bend(-(baseCapePitch * .14f + wave4), 0f, capeSway * .25f);
                    case "tasset_r" -> Rig.Joint.bend(0f, 0f, -(strideKick + flightLean * 4f));
                    case "tasset_l" -> Rig.Joint.bend(0f, 0f, strideKick + flightLean * 4f);
                    case "sheathed_hilt" -> new Rig.Joint(0, 0, 0, 0, 0, 0, iaido > .14f ? 1f : 0f);
                    default -> Rig.Joint.ZERO;
                };

                renderPart(pose, buffer, model.head, armor, "helmet", motion, 0f, s, color, armorLight);
                renderPart(pose, buffer, model.body, armor, "chest", motion, 0f, s, color, armorLight);
                renderPart(pose, buffer, model.rightArm, armor, "arm_r", motion, slim ? -.5f / 16f : -1f / 16f, s, color, armorLight);
                renderPart(pose, buffer, model.leftArm, armor, "arm_l", motion, slim ? .5f / 16f : 1f / 16f, s, color, armorLight);
                renderPart(pose, buffer, model.rightLeg, armor, "leg_r", motion, 0f, s, color, armorLight);
                renderPart(pose, buffer, model.leftLeg, armor, "leg_l", motion, 0f, s, color, armorLight);
            }

            pose.pushPose();
            model.body.translateAndRotate(pose);
            pose.mulPose(Axis.ZP.rotationDegrees(180f));

            if (player.isUsingItem() && AscalonService.holdingAscalon(player)) {
                boolean parryReady = asc != null && asc.parryCounterReady(partial);
                pose.pushPose();
                pose.translate(0.0, -.30, -.68);
                float spin = ClientState.reduced() ? 0f : age * .06f;
                VfxPrimitives.heraldicHexShield(pose, buffer, .78, spin,
                        DialDraw.alpha(parryReady ? CHAMPAGNE_GOLD : SLATE_COBALT, .48f),
                        DialDraw.alpha(parryReady ? CHAMPAGNE_GOLD : PLATINUM_IVORY, .94f));
                pose.popPose();
            }

            if (phantomActive) {
                Rig swordRig = Rig.get("ascalon");
                pose.pushPose();
                pose.translate(0.0, .28, .44);
                pose.mulPose(Axis.XP.rotationDegrees(90f));
                float hSpin = ClientState.reduced() ? 0f : age * .045f;
                VfxPrimitives.astrolabeRing(pose, buffer, Vec3.ZERO, 1.08, .16, hSpin, hSpin + TAU, 36,
                        DialDraw.alpha(PLATINUM_SILVER, .68f));
                pose.popPose();

                float[] fanDeg = {-64f, -40f, -16f, 16f, 40f, 64f};
                for (int i = 0; i < 6; i++) {
                    float fAngle = fanDeg[i];
                    float rad = (float) Math.toRadians(fAngle);
                    float bob = ClientState.reduced() ? 0f : Mth.sin(age * .14f + i * .95f) * .06f;
                    double px = Math.sin(rad) * 1.18;
                    double py = .22 + Math.cos(rad) * .48 + bob;
                    double pz = .40 + Math.abs(i - 2.5) * .05;
                    pose.pushPose();
                    pose.translate(px, py, pz);
                    pose.mulPose(Axis.ZP.rotationDegrees(-fAngle * .74f));
                    pose.mulPose(Axis.XP.rotationDegrees(-10f));
                    swordRig.draw(pose, buffer, Rig.STILL, .68f,
                            DialDraw.alpha(PLATINUM_IVORY, .92f), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
                    pose.popPose();
                }
            }
            pose.popPose();
        }

        private static void renderPart(PoseStack pose, MultiBufferSource buffer, ModelPart part,
                                       Rig armor, String boneId, Rig.Motion motion, float offsetX,
                                       float scale, int color, int light) {
            if (!part.visible) return;
            pose.pushPose();
            part.translateAndRotate(pose);
            if (offsetX != 0f) pose.translate(offsetX, 0f, 0f);
            pose.mulPose(Axis.ZP.rotationDegrees(180f));
            armor.limb(pose, buffer, boneId, motion, scale, color, light, false);
            pose.popPose();
        }
    }

    /** EntityRenderer for AscalonSlash: VFX 2.0 Abyssal Sheath + Razor Leading Edge + Eroded Wake! */
    static final class SlashEntityRenderer extends EntityRenderer<AscalonSlash> {
        private static final ResourceLocation TEX = Omnitrix.id("textures/rig/ascalon.png");

        SlashEntityRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(AscalonSlash entity, float entityYaw, float partial, PoseStack pose,
                           MultiBufferSource buffers, int packedLight) {
            CosmicVfxShader.clock(partial);
            Vec3 vel = entity.getDeltaMovement();
            Vec3 fwd = vel.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : vel.normalize();
            float scale = entity.spanScale();
            float roll = entity.rollDeg();
            boolean iaido = entity.variant() == 1;
            double radius = (iaido ? 3.55 : 2.95) * scale;
            float ageErode = Mth.clamp((entity.tickCount + partial) / 34f, 0.08f, 0.78f);

            pose.pushPose();
            pose.translate(0, .55, 0);
            Vec3 center = Vec3.ZERO;

            VfxPrimitives.lusterCrescentEroded(pose, buffers, center, fwd, radius, (iaido ? 1.75 : 1.28) * scale,
                    roll, iaido ? -1.38f : -1.22f, iaido ? 2.76f : 2.44f,
                    DialDraw.alpha(iaido ? CHAMPAGNE_GOLD : PLATINUM_SILVER, .94f), ageErode);
            if (iaido) {
                VfxPrimitives.orientedAstrolabeRing(pose, buffers, center, fwd, radius * .62, .22 * scale,
                        (entity.tickCount + partial) * .20, 40, DialDraw.alpha(CHAMPAGNE_GOLD, .68f));
            }
            pose.popPose();
        }

        @Override
        public ResourceLocation getTextureLocation(AscalonSlash entity) {
            return TEX;
        }
    }

    static void renderHud(GuiGraphics g, DeltaTracker tracker) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator() || WristScreen.isOpen()) return;
        var wristView = ClientState.self();
        if (wristView != null && wristView.s().morphed()) return;

        var asc = ClientState.selfAscalon();
        boolean armored = asc != null && asc.s().armored();
        boolean holding = AscalonService.holdingAscalon(mc.player);
        if (!armored && !holding) return;

        float partial = tracker.getGameTimeDeltaPartialTick(false);
        long gameTime = mc.level == null ? 0 : mc.level.getGameTime();
        double now = asc == null ? gameTime : asc.now(partial);
        float charge = asc == null ? 100f : asc.s().charge();
        boolean syzygyReady = charge >= 99.5f || mc.player.isCreative();

        if (activeBanner != null) {
            float age = (gameTime - activeBanner.startTick) + partial;
            if (age >= 0f && age <= activeBanner.duration) {
                renderAstralBanner(g, mc, activeBanner, age);
            }
        }

        float iaidoProg = asc == null ? 0f : asc.iaidoChargeProgress(partial);
        boolean parryPrimed = asc != null && asc.parryCounterReady(partial);
        boolean riftStage2 = asc != null && asc.riftRecallReady(partial);
        boolean arrayStage2 = asc != null && asc.phantomArrayActive(partial);

        int baseRightX = g.guiWidth() - 16;
        int baseY = g.guiHeight() - 22;

        drawSwordResonanceGauge(g, baseRightX, baseY - 12, Mth.clamp(charge / 100f, 0f, 1f), syzygyReady, gameTime + partial);

        double cdU = asc == null ? 0 : Math.max(0, asc.s().slashAt() - now);
        double cdI = asc == null ? 0 : Math.max(0, asc.s().riftAt() - now);
        double cdSU = asc == null ? 0 : Math.max(0, asc.s().tempestAt() - now);
        double cdO = asc == null ? 0 : Math.max(0, asc.s().syzygyAt() - now);

        String uKey = shortKey(OmnitrixClient.PRIMARY.getTranslatedKeyMessage().getString());
        String iKey = shortKey(OmnitrixClient.SECONDARY.getTranslatedKeyMessage().getString());
        String oKey = shortKey(OmnitrixClient.FLICK.getTranslatedKeyMessage().getString());

        int slotStart = baseRightX - 22;
        int spacing = 24;
        drawDiamondSlot(g, mc, slotStart - spacing * 3, baseY, 9, 0, cdU / 22.0, iaidoProg > .05f, uKey);
        drawDiamondSlot(g, mc, slotStart - spacing * 2, baseY, 9, 1, cdI / 24.0, riftStage2, iKey);
        drawDiamondSlot(g, mc, slotStart - spacing, baseY, 9, 2, cdSU / 45.0, arrayStage2, "S+" + uKey);
        drawDiamondSlot(g, mc, slotStart, baseY, 9, 3, cdO / 70.0, syzygyReady, oKey);

        var fs = FLIGHT_STATES.get(mc.player.getUUID());
        if (armored && fs != null && fs.hover(partial) > .25f) {
            float mult = fs.mult(partial);
            float machFrac = Mth.clamp((mult - 1f) / 8f, 0f, 1f);
            boolean maxMach = mult >= 8.85f;
            int barRight = slotStart + 8;
            int barLeft = slotStart - spacing * 3 - 8;
            int barY = baseY - 17;
            g.fill(barLeft, barY, barRight, barY + 1, 0x88152034);
            int fillW = Math.round((barRight - barLeft) * machFrac);
            if (fillW > 0) {
                g.fill(barLeft, barY, barLeft + fillW, barY + 1, maxMach ? CHAMPAGNE_GOLD : PLATINUM_IVORY);
            }
            drawDiamond(g, barLeft - 2, barY, 1, maxMach ? CHAMPAGNE_GOLD : PLATINUM_SILVER);
            String label = String.format(Locale.ROOT, "✦ %.1f×", mult);
            var pose = g.pose();
            pose.pushPose();
            pose.translate(barRight, barY - 6.5f, 0f);
            pose.scale(.64f, .64f, 1f);
            int lw = mc.font.width(label);
            g.drawString(mc.font, label, -lw, 0, maxMach ? CHAMPAGNE_GOLD : PLATINUM_IVORY, true);
            pose.popPose();
        } else if (parryPrimed) {
            int px = slotStart - spacing * 3 / 2;
            int py = baseY - 18;
            drawDiamond(g, px, py, 4, DialDraw.alpha(CHAMPAGNE_GOLD, .92f));
            drawDiamondOutline(g, px, py, 6, DialDraw.alpha(PLATINUM_IVORY, .78f));
        }
    }

    private static String shortKey(String raw) {
        return raw.length() > 3 ? raw.substring(0, 1).toUpperCase() : raw.toUpperCase();
    }

    private static void renderAstralBanner(GuiGraphics g, Minecraft mc, BannerToast b, float age) {
        float enter = Mth.clamp(age / 4.2f, 0f, 1f);
        enter = 1f - (1f - enter) * (1f - enter) * (1f - enter) * (1f - enter);
        float exit = Mth.clamp((b.duration - age) / 8.5f, 0f, 1f);
        float alpha = enter * exit;
        if (alpha <= .02f) return;

        Component mainComp = Component.translatable(b.titleKey);
        Component subComp = Component.translatable(b.subKey);
        int mainW = mc.font.width(mainComp);
        int subW = Math.round(mc.font.width(subComp) * .65f);
        int halfSpan = Math.round(Math.max(88, Math.max(mainW, subW) / 2 + 28) * (.35f + .65f * enter));

        int cx = g.guiWidth() / 2;
        int cy = 36;
        int lineCol = DialDraw.alpha(b.golden ? CHAMPAGNE_GOLD : PLATINUM_SILVER, .88f * alpha);
        int subLineCol = DialDraw.alpha(SLATE_COBALT, .44f * alpha);

        int bands = 12;
        for (int i = -bands; i < bands; i++) {
            float f = 1f - Math.abs(i + .5f) / bands;
            int x0 = cx + i * halfSpan / bands;
            int x1 = cx + (i + 1) * halfSpan / bands;
            int bgA = Math.round(182 * f * f * alpha);
            g.fill(x0, cy - 11, x1, cy + 12, (bgA << 24) | 0x09101e);
        }

        g.fill(cx - halfSpan, cy - 11, cx + halfSpan, cy - 10, lineCol);
        g.fill(cx - halfSpan + 14, cy - 13, cx + halfSpan - 14, cy - 12, subLineCol);
        g.fill(cx - halfSpan, cy + 11, cx + halfSpan, cy + 12, lineCol);
        g.fill(cx - halfSpan + 14, cy + 13, cx + halfSpan - 14, cy + 14, subLineCol);

        if (!ClientState.reduced() && age < 16f) {
            int glintX = cx - halfSpan + Math.round((age / 16f) * halfSpan * 2);
            g.fill(Math.max(cx - halfSpan, glintX - 18), cy - 11, Math.min(cx + halfSpan, glintX + 18), cy - 10,
                    DialDraw.alpha(PLATINUM_IVORY, alpha));
        }

        drawDiamond(g, cx - halfSpan - 3, cy, 3, lineCol);
        drawDiamond(g, cx + halfSpan + 3, cy, 3, lineCol);
        drawDiamond(g, cx, cy - 12, 2, DialDraw.alpha(PLATINUM_IVORY, .92f * alpha));

        int titleCol = DialDraw.alpha(b.golden ? 0xfffff4dc : PLATINUM_IVORY, Math.max(.08f, alpha));
        g.drawString(mc.font, mainComp, cx - mainW / 2, cy - 7, titleCol, true);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy + 4.5f, 0f);
        pose.scale(.65f, .65f, 1f);
        int rawSubW = mc.font.width(subComp);
        g.drawString(mc.font, subComp, -rawSubW / 2, 0, DialDraw.alpha(PLATINUM_SILVER, Math.max(.08f, .84f * alpha)), false);
        pose.popPose();
    }

    private static void drawDiamondSlot(GuiGraphics g, Minecraft mc, int cx, int cy, int r,
                                        int glyphId, double cdFrac, boolean highlighted, String keyLabel) {
        double clampedCd = Mth.clamp(cdFrac, 0.0, 1.0);
        boolean ready = clampedCd <= 0.001;

        int splitDy = (int) Math.round(r - (1.0 - clampedCd) * 2 * r);
        for (int dy = -r; dy <= r; dy++) {
            int span = r - Math.abs(dy);
            boolean filled = dy >= splitDy;
            int col = !filled ? 0x1e0b101a : (highlighted ? 0x52342c1c : 0x34132238);
            g.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, col);
        }

        int borderCol = highlighted ? CHAMPAGNE_GOLD
                : ready ? DialDraw.alpha(PLATINUM_SILVER, .88f)
                : 0x883b4e6b;
        drawDiamondOutline(g, cx, cy, r, borderCol);
        if (highlighted) {
            drawDiamondOutline(g, cx, cy, r + 2, DialDraw.alpha(CHAMPAGNE_GOLD, .42f));
        }

        int gCol = highlighted ? CHAMPAGNE_GOLD : ready ? PLATINUM_IVORY : 0x776880a0;
        switch (glyphId) {
            case 0 -> {
                g.fill(cx - 2, cy - 4, cx + 1, cy - 3, gCol);
                g.fill(cx + 1, cy - 3, cx + 3, cy + 3, gCol);
                g.fill(cx - 2, cy + 3, cx + 1, cy + 4, gCol);
                g.fill(cx, cy - 2, cx + 2, cy + 2, gCol);
            }
            case 1 -> {
                g.fill(cx, cy - 5, cx + 1, cy + 5, gCol);
                g.fill(cx - 3, cy - 1, cx + 4, cy, gCol);
                g.fill(cx - 1, cy - 3, cx + 2, cy + 3, DialDraw.alpha(gCol, .65f));
            }
            case 2 -> {
                g.fill(cx, cy - 5, cx + 1, cy + 4, gCol);
                g.fill(cx - 3, cy - 3, cx - 2, cy + 3, gCol);
                g.fill(cx + 3, cy - 3, cx + 4, cy + 3, gCol);
                g.fill(cx - 2, cy + 1, cx + 3, cy + 2, gCol);
            }
            default -> {
                drawDiamond(g, cx, cy, 3, gCol);
                g.fill(cx - 5, cy, cx + 6, cy + 1, gCol);
                g.fill(cx, cy - 5, cx + 1, cy + 6, gCol);
            }
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy + r + 2.5f, 0f);
        pose.scale(.58f, .58f, 1f);
        int kw = mc.font.width(keyLabel);
        g.drawString(mc.font, keyLabel, -kw / 2, 0, ready ? 0xbccadcec : 0x777084a0, false);
        pose.popPose();
    }

    private static void drawSwordResonanceGauge(GuiGraphics g, int cx, int bottomY, float chargeFrac,
                                                boolean full, float time) {
        int bladeH = 24;
        int topY = bottomY - bladeH;
        g.fill(cx - 1, bottomY + 5, cx + 2, bottomY + 7, CHAMPAGNE_GOLD);
        g.fill(cx, bottomY + 1, cx + 1, bottomY + 5, 0xff2b3850);
        g.fill(cx - 4, bottomY, cx + 5, bottomY + 1, full ? CHAMPAGNE_GOLD : PLATINUM_SILVER);
        g.fill(cx - 5, bottomY - 1, cx - 3, bottomY, PLATINUM_SILVER);
        g.fill(cx + 4, bottomY - 1, cx + 6, bottomY, PLATINUM_SILVER);

        g.fill(cx - 1, topY + 3, cx + 2, bottomY, 0xc0121a28);
        g.fill(cx, topY, cx + 1, topY + 3, 0xc0121a28);
        int litH = Math.round(bladeH * chargeFrac);
        if (litH > 0) {
            int fillTop = bottomY - litH;
            int fillCol = full ? CHAMPAGNE_GOLD : PLATINUM_IVORY;
            g.fill(cx - 1, Math.max(topY + 3, fillTop), cx + 2, bottomY, DialDraw.alpha(SLATE_COBALT, .85f));
            g.fill(cx, fillTop, cx + 1, bottomY, fillCol);
        }
        if (full && !ClientState.reduced()) {
            float pulse = .55f + .45f * Mth.sin(time * .20f);
            drawDiamond(g, cx, topY - 2, 2, DialDraw.alpha(CHAMPAGNE_GOLD, pulse));
        }
    }

    private static void drawDiamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int span = r - Math.abs(dy);
            g.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }

    private static void drawDiamondOutline(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int span = r - Math.abs(dy);
            g.fill(cx - span, cy + dy, cx - span + 1, cy + dy + 1, color);
            if (span > 0) {
                g.fill(cx + span, cy + dy, cx + span + 1, cy + dy + 1, color);
            }
        }
    }

    /** World-Space VFX 2.0: High-Contrast Eroded Trails, Aerodynamic Flight Streams, Sakuga Bursts & Syzygy! */
    static void renderWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, long now, float partial) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float time = now + partial;

        // 1. High-Contrast Eroded Sword-Tip Trail
        for (var entry : BLADE_TRAILS.entrySet()) {
            if (entry.getValue().size() >= 2) {
                var pts = new ArrayList<>(entry.getValue());
                VfxPrimitives.lusterPolyline(pose, buffers, pts, camera, .16, DialDraw.alpha(PLATINUM_IVORY, .92f));
            }
        }

        // 1b. VFX 2.0 Aerodynamic Viltrumite Flight Streams + Sky Sonic-Astrolabe Breakthrough Rings!
        if (!ClientState.reduced()) {
            renderViltrumFlightStreams(pose, buffers, camera, time, partial);
        }
        for (ActiveSonicRing ring : SONIC_RINGS) {
            if (ring.center.distanceToSqr(camera) > 140 * 140) continue;
            float age = (now - ring.startTick) + partial;
            float p = age / 28f;
            if (p < 0f || p >= 1f) continue;
            // Sakuga Ease-Out Quint snap expansion + progressive FBM erosion!
            double ease = 1.0 - Math.pow(1.0 - p, 4.5);
            float erode = (float) Math.pow(p, 1.15) * 0.92f;
            float fade = (1f - p * .55f);
            double maxR = 4.6 + ring.tier * 2.6;
            double rad = .65 + (maxR - .65) * ease;
            int ringCol = ring.tier >= 3 ? CHAMPAGNE_GOLD : ring.tier == 2 ? PLATINUM_IVORY : PLATINUM_SILVER;
            if (ring.tier == 1) {
                Vec3 lateral = ring.axis.cross(new Vec3(0, 1, 0)).normalize();
                if (lateral.lengthSqr() < .01) lateral = new Vec3(1, 0, 0);
                VfxPrimitives.bowShockWave(pose, buffers, ring.center, ring.axis, lateral, camera,
                        rad * .63, .37, DialDraw.alpha(0xff709fba, fade * .78f));
            } else {
                VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, ring.center, ring.axis, camera, rad,
                        .32 + ring.tier * .10, age * (.07 + ring.tier * .025), 48,
                        DialDraw.alpha(ringCol, .84f * fade), erode);
                VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, ring.center.subtract(ring.axis.scale(.55)),
                        ring.axis, camera, rad * .62, .20, -age * .12, 36,
                        DialDraw.alpha(PLATINUM_SILVER, .76f * fade), erode * 1.05f);
            }
        }

        // 2. Precision Broken-Armillary Foot Astrolabe + Sakuga Pre-Implosion Iaido Convergence Lines!
        float maxSkySyzygy = 0f;
        Vec3 skyAnchor = camera;
        int skyVariant = 0;
        for (Player p : mc.level.players()) {
            var asc = ClientState.ascalon(p);
            if (asc == null) continue;
            float factor = asc.transitionFactor(partial);
            float iaido = asc.iaidoChargeProgress(partial);
            var fs = FLIGHT_STATES.get(p.getUUID());
            float flightLean = fs == null ? 0f : fs.lean(partial);

            float groundAstrolabeFade = 1f - flightLean * .92f;
            if ((factor > .05f || iaido > .05f) && groundAstrolabeFade > .08f
                    && p.distanceToSqr(camera) < 76 * 76 && !p.isInvisible()) {
                Vec3 feet = p.getPosition(partial);
                double spin = ClientState.reduced() ? 0 : time * .036;
                float ringAlpha = Math.max(factor * .48f, iaido * .92f) * groundAstrolabeFade;
                // Iaido implosion: ring contracts inward toward scabbard as charge approaches 100%!
                double outerR = 1.28 * Math.max(.4f, factor) + iaido * (0.85 - 0.55 * iaido);
                VfxPrimitives.astrolabeRingEroded(pose, buffers, feet.add(0, .03, 0), outerR, .20,
                        spin, spin + TAU, 40,
                        DialDraw.alpha(iaido >= .8f ? CHAMPAGNE_GOLD : PLATINUM_SILVER, ringAlpha), 0.08f);

                if (iaido > .10f && !ClientState.reduced()) {
                    // Sakuga Vacuum Implosion: 8 starlight streaks sucked inward into left-hip scabbard!
                    Vec3 scabbard = feet.add(0, 0.92, 0);
                    for (int i = 0; i < 8; i++) {
                        double phase = ((time * 0.14 + i * 0.37) % 1.0);
                        double dist = (1.0 - phase) * (2.6 + (i % 2) * 0.9);
                        double ang = TAU * i / 8.0 + time * 0.05;
                        Vec3 outerPt = scabbard.add(Math.cos(ang) * dist, (i % 3 - 1) * 0.35 * (1.0 - phase), Math.sin(ang) * dist);
                        Vec3 innerPt = scabbard.lerp(outerPt, Math.max(0.05, 1.0 - 0.38));
                        VfxPrimitives.speedStreak(pose, buffers, outerPt, innerPt, camera,
                                .035 * iaido, DialDraw.alpha(iaido >= .8f ? CHAMPAGNE_GOLD : PLATINUM_IVORY, .86f * iaido), 0.15f);
                    }
                }
            }
        }
        for (var active : ClientState.scenes()) {
            var scene = active.scene();
            if (scene.effect() != WristNetwork.SYZYGY_SCENE || active.until() <= now
                    || scene.center().distanceToSqr(camera) > 128 * 128) continue;
            float age = now - active.began() + partial;
            float skyInt = Motion.part(age, 0, 14) * Math.min(1, (active.until() - now) / 30f);
            if (skyInt > maxSkySyzygy) {
                maxSkySyzygy = skyInt;
                skyAnchor = scene.center();
                skyVariant = scene.variant();
            }
        }

        if (maxSkySyzygy > .02f) {
            boolean skyVisible = mc.level.dimensionType().hasSkyLight() && mc.level.canSeeSky(BlockPos.containing(skyAnchor).above());
            if (skyVisible && skyVariant == 1 && !ClientState.reduced()) {
                renderSkySyzygy(pose, buffers, camera, skyAnchor, time, maxSkySyzygy);
            } else {
                VfxPrimitives.eclipseCanopy(pose, buffers, skyAnchor, 8.0, skyVisible ? 12.0 : 4.3,
                        0, DialDraw.alpha(INDIGO, maxSkySyzygy * .56f));
            }
            VfxPrimitives.astrolabeRingEroded(pose, buffers, skyAnchor.add(0, .06, 0), 52, .36,
                    0, TAU, 60, DialDraw.alpha(COPPER, maxSkySyzygy * .65f), .26f);
        }

        // 3. Stage-1 Spatial Rift Phantom Sword Waypoints
        Rig swordRig = Rig.get("ascalon");
        for (ActiveWaypoint wp : WAYPOINTS) {
            if (wp.dest.distanceToSqr(camera) > 100 * 100) continue;
            float age = (now - wp.startTick) + partial;
            float erode = Mth.clamp((age - 56f) / 24f, 0f, .95f);
            float fade = 1f - erode * .65f;
            for (Vec3 pt : new Vec3[]{wp.origin, wp.dest}) {
                VfxPrimitives.astrolabeRingEroded(pose, buffers, pt.add(0, -.92, 0), .88, .18,
                        age * .07, age * .07 + TAU, 32, DialDraw.alpha(PLATINUM_SILVER, .78f * fade), erode);
                pose.pushPose();
                pose.translate(pt.x, pt.y + .95 + Mth.sin(age * .15f) * .12, pt.z);
                pose.mulPose(Axis.YP.rotationDegrees(age * 4.5f));
                pose.mulPose(Axis.ZP.rotationDegrees(180f));
                swordRig.draw(pose, buffers, Rig.STILL, .72f,
                        DialDraw.alpha(PLATINUM_IVORY, .88f * (1f - erode)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
                pose.popPose();
            }
        }

        // 4. Stage-2 Heavenly Lance Hexagonal Seals (Exponential Sky-Drop + Eroded Dissolution)
        for (var active : ClientState.scenes()) {
            var seal = active.scene();
            if (seal.effect() != WristNetwork.SEAL_SCENE || active.until() <= now
                    || seal.center().distanceToSqr(camera) > 110 * 110) continue;
            float age = now - active.began() + partial;
            float endProg = Mth.clamp((age - (AscalonService.STORM_DOME_TICKS - 24f)) / 24f, 0f, 1f);
            float fade = 1f - endProg * .75f;
            double r = AscalonService.STORM_DOME_RADIUS;

            VfxPrimitives.astrolabeRingEroded(pose, buffers, seal.center().add(0, .035, 0), r, .52,
                    age * .026, age * .026 + TAU, 48, DialDraw.alpha(PLATINUM_SILVER, .86f * fade), endProg);
            VfxPrimitives.astrolabeRingEroded(pose, buffers, seal.center().add(0, .055, 0), r * .56, .32,
                    -age * .042, -age * .042 + TAU, 36, DialDraw.alpha(CHAMPAGNE_GOLD, .80f * fade), endProg);

            float drop = Mth.clamp(age / 4.2f, 0f, 1f);
            double dropOffset = Math.pow(1.0 - drop, 3.5) * 18.0;
            Vec3[] vertices = new Vec3[6];
            for (int i = 0; i < 6; i++) {
                double ang = TAU * i / 6.0 + .26;
                Vec3 vPos = seal.center().add(Math.cos(ang) * r * .92, 0, Math.sin(ang) * r * .92);
                vertices[i] = vPos;

                pose.pushPose();
                pose.translate(vPos.x, vPos.y + 3.45 + dropOffset, vPos.z);
                pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(-ang) + 90f));
                pose.mulPose(Axis.ZP.rotationDegrees(180f));
                swordRig.draw(pose, buffers, Rig.STILL, 1.65f,
                        DialDraw.alpha(PLATINUM_IVORY, .94f * (1f - endProg)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
                pose.popPose();

                if (age < 18f) {
                    float p = age / 18f;
                    VfxPrimitives.lusterRibbonEroded(pose, buffers, vPos, vPos.add(0, 18.0 * (1f - p * .25f), 0), camera,
                            .38 * (1f - p * .4f), .08, DialDraw.alpha(PLATINUM_IVORY, .88f * (1f - p * .5f)), 3.0f, p * .88f, true);
                }
            }
            for (int i = 0; i < 6; i++) {
                Vec3 a = vertices[i].add(0, 1.35 + dropOffset, 0);
                Vec3 b = vertices[(i + 1) % 6].add(0, 1.35 + dropOffset, 0);
                VfxPrimitives.lusterRibbonEroded(pose, buffers, a, b, camera,
                        .08 * fade, .08 * fade, DialDraw.alpha(PLATINUM_SILVER, .76f * fade), 1.5f, endProg * .9f, false);
            }
        }

        // 5. High-Contrast Ground Abyssal Fissures & Eroded Starlight Scars
        for (ActiveFissure fis : FISSURES) {
            if (fis.from.distanceToSqr(camera) > 96 * 96) continue;
            float maxAge = fis.iaido ? 44f : 32f;
            float age = (now - fis.startTick) + partial;
            float p = age / maxAge;
            if (p < 0f || p >= 1f) continue;
            double w = fis.iaido ? .38 : .22;
            VfxPrimitives.lusterRibbonEroded(pose, buffers, fis.from.add(0, .03, 0), fis.to.add(0, .03, 0), camera,
                    w, w * .65,
                    DialDraw.alpha(fis.iaido ? CHAMPAGNE_GOLD : PLATINUM_IVORY, .94f * (1f - p * .35f)),
                    1.4f, p * .92f, true);
        }
    }

    /**
     * VFX 2.0 Aerodynamic Viltrumite Flight Architecture (70 / 25 / 5 Hierarchy):
     * - 5% Razor Focus: Supersonic Bow-Shock Leading Crescent ahead of chest/helmet + Broken Armillary Vernier Ring
     * - 25% Main Flow: Wide Falcon-Flank Dual-Pass Overflowing Star-Silk Ribbons (Abyssal Dark Sheath + FBM Eroded Strands)
     * - 70% Breathable Space: Peripheral Hollow-Cylinder Anamorphic Speed Streaks (>= 3.6x speed, zero center clutter!)
     */
    private static void renderViltrumFlightStreams(PoseStack pose, MultiBufferSource buffers, Vec3 camera,
                                                   float time, float partial) {
        var mc = Minecraft.getInstance();
        boolean firstPersonSelf = mc.options.getCameraType().isFirstPerson();

        for (Player p : mc.level.players()) {
            var fs = FLIGHT_STATES.get(p.getUUID());
            if (fs == null || p.isInvisible() || p.distanceToSqr(camera) > 140 * 140) continue;
            boolean isFpSelf = firstPersonSelf && p == mc.player && mc.getCameraEntity() == mc.player;

            float lean = fs.lean(partial);
            float mult = fs.mult(partial);
            float machRatio = Mth.clamp((mult - 1.0f) / 8.0f, 0f, 1f);
            int savedCount = fs.ribbon.size();
            if (savedCount < 2 && lean < .15f) continue;

            Vec3 liveBase = p.getPosition(partial);
            FlightRibbonSample liveHead = computeRibbonAnchors(liveBase, fs.smoothedDir, lean,
                    fs.bank(partial), machRatio, time, p.getId());

            if (savedCount >= 1) {
                var leftPts = new ArrayList<Vec3>(savedCount + 1);
                var rightPts = new ArrayList<Vec3>(savedCount + 1);
                var wingAxes = new ArrayList<Vec3>(savedCount + 1);

                for (FlightRibbonSample s : fs.ribbon) {
                    leftPts.add(s.left);
                    rightPts.add(s.right);
                    wingAxes.add(s.wingAxis);
                }
                if (!isFpSelf && fs.ribbonAttached) {
                    leftPts.add(liveHead.left);
                    rightPts.add(liveHead.right);
                    wingAxes.add(liveHead.wingAxis);
                }

                // Sleek calligraphic proportions (never bloated bars!)
                double ribbonWidth = .095 + machRatio * .145;
                float uScroll = time * (.12f + machRatio * .32f);
                int veilCol = DialDraw.alpha(machRatio > .65f ? PLATINUM_SILVER : SLATE_COBALT,
                        (.56f + machRatio * .28f) * (isFpSelf ? .35f : 1f));
                int spineCol = DialDraw.alpha(machRatio > .72f ? CHAMPAGNE_GOLD : PLATINUM_IVORY,
                        (.90f + machRatio * .08f) * (isFpSelf ? .45f : 1f));

                VfxPrimitives.overflowingSilkRibbon(pose, buffers, leftPts, wingAxes, camera,
                        ribbonWidth, uScroll, veilCol, spineCol);
                VfxPrimitives.overflowingSilkRibbon(pose, buffers, rightPts, wingAxes, camera,
                        ribbonWidth * 1.08, uScroll + .41f, veilCol, spineCol);
            }

            Vec3 fwd = fs.smoothedDir.lengthSqr() < 1e-4 ? p.getLookAngle().normalize() : fs.smoothedDir;
            Vec3 wRight = liveHead.wingAxis;
            Vec3 wUp = wRight.cross(fwd);
            wUp = wUp.lengthSqr() < 1e-5 ? new Vec3(0, 1, 0) : wUp.normalize();

            // 5% Focal Bow-Shock Crescent & Broken Armillary Halo ahead of chest (>= 2.6x speed, never blocking rear camera!)
            if (!isFpSelf && fs.hover(partial) > .4f && mult >= 2.6f && lean > .35f) {
                float machAlpha = Mth.clamp((mult - 2.6f) / 4.2f, 0f, 1f);
                Vec3 bowApex = liveBase.add(0, .92, 0).add(fwd.scale(.92));
                int bowCol = DialDraw.alpha(mult >= 8.4f ? CHAMPAGNE_GOLD : PLATINUM_IVORY, .88f * machAlpha);
                VfxPrimitives.bowShockWave(pose, buffers, bowApex, fwd, wRight, camera,
                        .82 + machRatio * .58, .38 + machRatio * .32, bowCol);

                VfxPrimitives.orientedAstrolabeRingEroded(pose, buffers, bowApex.subtract(fwd.scale(.65)),
                        fwd, camera, .95 + machRatio * .48, .18 + machRatio * .05,
                        time * (.07 + machRatio * .12), 40,
                        DialDraw.alpha(mult >= 8.5f ? CHAMPAGNE_GOLD : PLATINUM_SILVER, .76f * machAlpha), 0.12f);
            }

            // Peripheral Hollow-Cylinder Anamorphic Speed Streaks (>= 3.6x speed, wide 2.3m..4.6m radius for 70% clean center!)
            if (mult >= 3.6f && lean > .4f) {
                float streakAlpha = Mth.clamp((mult - 3.6f) / 4.2f, 0f, 1f);
                int count = 8 + Math.round(machRatio * 6f);
                Vec3 centerAnchor = liveBase.add(0, .92, 0);
                for (int i = 0; i < count; i++) {
                    double goldenAng = i * 2.39996 + (p.getId() & 15) * .4;
                    double rad = 2.35 + (i % 4) * 0.62;
                    double cycle = ((time * (0.22 + machRatio * 0.34) + i * 0.19) % 1.0);
                    double axialOffset = 4.5 - cycle * 11.0;
                    double len = 1.35 + machRatio * 2.4;
                    Vec3 radial = wRight.scale(Math.cos(goldenAng) * rad).add(wUp.scale(Math.sin(goldenAng) * rad * .72));
                    Vec3 head = centerAnchor.add(radial).add(fwd.scale(axialOffset));
                    Vec3 tail = head.subtract(fwd.scale(len));
                    float env = (float) Math.sin(cycle * Math.PI);
                    int sCol = DialDraw.alpha((i % 3 == 0 && mult >= 7.5f) ? CHAMPAGNE_GOLD : PLATINUM_IVORY,
                            .74f * streakAlpha * env);
                    VfxPrimitives.speedStreak(pose, buffers, tail, head, camera, .026 + machRatio * .014, sCol, 0.15f);
                }
            }
        }
    }

    /** Deep Cosmic Eclipse Canopy + Azmuth's 6 Eclipse-Lit Celestial Planets & Converging Beams! */
    private static void renderSkySyzygy(PoseStack pose, MultiBufferSource buffers, Vec3 camera, Vec3 anchor,
                                        float time, float intensity) {
        double skyY = anchor.y + 66;
        double slowYaw = .48 + (ClientState.reduced() ? 0 : time * .0022);
        Vec3 axis = new Vec3(Math.cos(slowYaw), .12, Math.sin(slowYaw)).normalize();
        Vec3 skyCenter = new Vec3(anchor.x, skyY, anchor.z);

        if (intensity > .18f) {
            float eclipseAlpha = Mth.clamp((intensity - .15f) / .75f, 0f, 1f) * .90f;
            VfxPrimitives.eclipseCanopy(pose, buffers, anchor.add(0, 18.0, 0),
                    142.0, 66.0, time * .0015f, DialDraw.alpha(0xff393057, eclipseAlpha));
        }

        Vec3 lineStart = skyCenter.subtract(axis.scale(62.0));
        Vec3 lineEnd = skyCenter.add(axis.scale(62.0));

        VfxPrimitives.materialRibbon(pose, buffers, lineStart, lineEnd, camera,
                1.2 * intensity, .70 * intensity, DialDraw.alpha(0xff906396, .66f * intensity), VfxPrimitives.STAR, .14f);
        VfxPrimitives.astrolabeRingEroded(pose, buffers, skyCenter.add(0, -1.5, 0), 22.0 * intensity, 2.0,
                time * .007, time * .007 + TAU, 64, DialDraw.alpha(COPPER, .63f * intensity), 0.12f);

        double[] offsets = {-42.0, -25.0, -9.0, 8.0, 26.0, 45.0};
        double[] radii = {2.4, 3.7, 2.7, 5.2, 3.3, 2.6};
        int[] cols = {0xff627db9, 0xffdcab6b, 0xffa46ac2, 0xffc48d5c, 0xff5d9eac, 0xff8270b8};
        double[] heights = {0, -4, 1.4, -2.6, 3.0, .6};
        Vec3 transverse = axis.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 swordTip = anchor.add(0, 2.35, 0);
        for (int i = 0; i < 6; i++) {
            Vec3 pCenter = skyCenter.add(axis.scale(offsets[i])).add(0, heights[i], 0)
                    .add(transverse.scale((i % 3 - 1) * 7.4));
            double r = radii[i] * (.65 + .35 * intensity);
            VfxPrimitives.celestialPlanet(pose, buffers, pCenter, r, time * (.018f + i * .004f),
                    DialDraw.alpha(cols[i], .92f * intensity),
                    DialDraw.alpha(CHAMPAGNE_GOLD, .82f * intensity), i == 1 || i == 3);
            if (intensity > .64f) {
                float beamA = Mth.clamp((intensity - .64f) / .36f, 0f, 1f);
                Vec3 rim = pCenter.add(swordTip.subtract(pCenter).normalize().scale(r * .92));
                VfxPrimitives.materialRibbon(pose, buffers, rim, swordTip, camera,
                        .77 * beamA, .14 * beamA, DialDraw.alpha(cols[i], .88f * beamA), VfxPrimitives.STAR, .16f);
                VfxPrimitives.flowRibbon(pose, buffers, rim, swordTip, camera,
                        .083 * beamA, .022 * beamA, DialDraw.alpha(CHAMPAGNE_GOLD, .44f * beamA), false, true, 4f);
            }
        }
    }

    /** VFX 2.0 Sakuga Timing Cues: Exponential Ease-Out Snap (`1 - (1-p)^4.2`) + Progressive FBM Erosion! */
    static boolean renderCue(PoseStack pose, MultiBufferSource buffers, WristNetwork.Cue cue, float age, Vec3 camera) {
        int action = cue.action();
        if (action < WristNetwork.ASCALON_ARMOR_CUE || action > WristNetwork.ASCALON_MACH_CUE) return false;

        switch (action) {
            case WristNetwork.ASCALON_MACH_CUE -> {
                if (age > 16f) return true;
                float p = age / 16f;
                VfxPrimitives.lusterImpactBurst(pose, buffers, cue.from(), camera, 1.8 + cue.beat() * .75, p,
                        DialDraw.alpha(cue.beat() >= 3 ? CHAMPAGNE_GOLD : PLATINUM_IVORY, (1f - p * .45f) * .92f));
                return true;
            }
            case WristNetwork.ASCALON_ARMOR_CUE -> {
                if (age > 24f) return true;
                float p = age / 24f;
                double ease = 1.0 - Math.pow(1.0 - p, 4.2);
                float erode = (float) Math.pow(p, 1.15) * 0.92f;
                boolean donned = cue.beat() == 1;
                Vec3 base = cue.from();
                if (donned) {
                    VfxPrimitives.lusterRibbonEroded(pose, buffers, base, base.add(0, 7.2 * (1f - p * .2f), 0), camera,
                            .76 * (1f - p * .45f), .18, DialDraw.alpha(PLATINUM_IVORY, .92f * (1f - p * .35f)), 2.2f, erode, true);
                    VfxPrimitives.astrolabeRingEroded(pose, buffers, base.add(0, .06, 0), .6 + ease * 6.0, .52,
                            age * .12, age * .12 + TAU, 44, DialDraw.alpha(PLATINUM_SILVER, .92f * (1f - p * .4f)), erode);
                    VfxPrimitives.astrolabeRingEroded(pose, buffers, base.add(0, 1.15, 0), .4 + ease * 3.8, .32,
                            -age * .16, -age * .16 + TAU, 36, DialDraw.alpha(CHAMPAGNE_GOLD, .86f * (1f - p * .4f)), erode);
                } else {
                    double r = Math.max(.2, 2.6 * (1.0 - ease));
                    VfxPrimitives.astrolabeRingEroded(pose, buffers, base.add(0, .9, 0), r, .26,
                            -age * .20, -age * .20 + TAU, 32, DialDraw.alpha(PLATINUM_SILVER, 1f - p), erode);
                }
            }
            case WristNetwork.ASCALON_SWING_CUE -> {
                float maxAge = cue.beat() >= 4 ? 19f : 14f;
                if (age > maxAge) return true;
                float p = age / maxAge;
                double snap = 1.0 - Math.pow(1.0 - p, 4.4);
                float erode = (float) Math.pow(p, 1.12) * 0.90f;
                float alphaHold = 1f - p * .38f;
                Vec3 from = cue.from();
                Vec3 dir = cue.to().subtract(from);
                int beat = cue.beat();

                if (beat == 4) {
                    VfxPrimitives.lusterRibbonEroded(pose, buffers, from, cue.to(), camera,
                            .44 * (1f - p * .35f), .12, DialDraw.alpha(CHAMPAGNE_GOLD, .96f * alphaHold), 3.2f, erode, true);
                    VfxPrimitives.lusterSlashArcEroded(pose, buffers, cue.to().subtract(dir.normalize().scale(2.5)), dir,
                            5.8 + snap * 2.8, 1.55 * (1f - p * .45f) + .25, 2f, -1.48f, 2.96f,
                            DialDraw.alpha(CHAMPAGNE_GOLD, .96f * alphaHold), erode);
                    return true;
                }
                if (beat == 5) {
                    VfxPrimitives.astrolabeRingEroded(pose, buffers, from.add(0, -1.12, 0), 1.0 + snap * 8.2, .58,
                            age * .14, age * .14 + TAU, 48, DialDraw.alpha(CHAMPAGNE_GOLD, .92f * alphaHold), erode);
                    VfxPrimitives.lusterSlashArcEroded(pose, buffers, from, dir, 5.6 + snap * 3.0, 1.65 * (1f - p * .45f) + .28,
                            4f, -1.45f, 2.90f, DialDraw.alpha(PLATINUM_IVORY, .98f * alphaHold), erode);
                    return true;
                }

                float tilt = switch (beat) {
                    case 0 -> -28f;
                    case 1 -> 26f;
                    case 2 -> 4f;
                    default -> 82f;
                };
                float startA = beat == 2 ? -2.35f : -1.30f;
                float sweepA = beat == 2 ? 4.70f : 2.60f;
                double rad = (beat == 2 ? 4.4 : 3.9) + snap * 1.65;
                VfxPrimitives.lusterSlashArcEroded(pose, buffers, from, dir, rad, 1.22 * (1f - p * .42f) + .22,
                        tilt, startA, sweepA, DialDraw.alpha(PLATINUM_SILVER, .96f * alphaHold), erode);
                if (beat == 3) {
                    VfxPrimitives.lusterSlashArcEroded(pose, buffers, from, dir, rad * .96, .88 * (1f - p * .42f) + .18,
                            -24f, -1.22f, 2.44f, DialDraw.alpha(CHAMPAGNE_GOLD, .92f * alphaHold), erode);
                }
            }
            case WristNetwork.ASCALON_PARRY_CUE -> {
                if (age > 15f) return true;
                float p = age / 15f;
                double snap = 1.0 - Math.pow(1.0 - p, 4.2);
                float erode = p * .88f;
                VfxPrimitives.astrolabeRingEroded(pose, buffers, cue.to(), .28 + snap * 2.1, .26,
                        age * .22, age * .22 + TAU, 32, DialDraw.alpha(CHAMPAGNE_GOLD, .94f * (1f - p * .4f)), erode);
                VfxPrimitives.lusterImpactBurst(pose, buffers, cue.to(), camera, 2.15, p, DialDraw.alpha(PLATINUM_IVORY, 1f - p * .3f));
                if (cue.beat() >= 1 && cue.from().distanceToSqr(cue.to()) > .5) {
                    VfxPrimitives.lusterRibbonEroded(pose, buffers, cue.from(), cue.to(), camera,
                            .16 * (1f - p * .4f), .05, DialDraw.alpha(PLATINUM_SILVER, .90f * (1f - p * .4f)), 1.6f, erode, true);
                }
            }
            case WristNetwork.ASCALON_CLEAVE_HIT_CUE -> {
                if (age > 15f) return true;
                float p = age / 15f;
                VfxPrimitives.lusterImpactBurst(pose, buffers, cue.to(), camera, 2.1, p, DialDraw.alpha(CHAMPAGNE_GOLD, 1f - p * .35f));
            }
            case WristNetwork.ASCALON_BEAM_END_CUE -> {
                if (age > 22f) return true;
                float p = age / 22f;
                double snap = 1.0 - Math.pow(1.0 - p, 4.0);
                Vec3 at = cue.to();
                VfxPrimitives.lusterRibbonEroded(pose, buffers, at.add(0, -1.0, 0), at.add(0, 10.5 * (1f - p * .2f), 0), camera,
                        .98 * (1f - p * .45f), .22, DialDraw.alpha(PLATINUM_SILVER, .90f * (1f - p * .4f)), 2.6f, p * .90f, true);
                VfxPrimitives.astrolabeRingEroded(pose, buffers, at, .6 + snap * 6.8, .52,
                        age * .14, age * .14 + TAU, 40, DialDraw.alpha(PLATINUM_IVORY, .94f * (1f - p * .4f)), p * .88f);
            }
            case WristNetwork.ASCALON_RIFT_CUE -> {
                if (age > 26f) return true;
                float p = age / 26f;
                double snap = 1.0 - Math.pow(1.0 - p, 4.2);
                float erode = (float) Math.pow(p, 1.15) * 0.90f;
                Vec3 entry = cue.from();
                Vec3 exit = cue.to();

                if (cue.beat() == 1) {
                    Vec3 mid = entry.lerp(exit, .5);
                    Rig swordRig = Rig.get("ascalon");
                    float conv = Mth.clamp(age / 8.5f, 0f, 1f);
                    conv = 1f - (1f - conv) * (1f - conv) * (1f - conv);
                    for (int i = 0; i < 8; i++) {
                        double ang = TAU * i / 8.0;
                        Vec3 startPt = mid.add(Math.cos(ang) * 7.5, 2.2 + (i % 2) * 1.1, Math.sin(ang) * 7.5);
                        Vec3 curPt = startPt.lerp(mid, conv);
                        VfxPrimitives.lusterRibbonEroded(pose, buffers, startPt, curPt, camera,
                                .15 * (1f - p * .4f), .04, DialDraw.alpha(PLATINUM_IVORY, .90f * (1f - p * .35f)), 1.8f, erode, true);
                        Vec3 toMid = mid.subtract(startPt).normalize();
                        float yaw = (float) Math.toDegrees(Math.atan2(toMid.x, toMid.z));
                        pose.pushPose();
                        pose.translate(curPt.x, curPt.y, curPt.z);
                        pose.mulPose(Axis.YP.rotationDegrees(yaw));
                        pose.mulPose(Axis.XP.rotationDegrees(108f));
                        swordRig.draw(pose, buffers, Rig.STILL, .78f,
                                DialDraw.alpha(PLATINUM_IVORY, (1f - p) * .94f), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
                        pose.popPose();
                    }
                    VfxPrimitives.astrolabeRingEroded(pose, buffers, mid.add(0, -.8, 0), .6 + snap * 6.8, .50,
                            -age * .18, -age * .18 + TAU, 40, DialDraw.alpha(CHAMPAGNE_GOLD, .94f * (1f - p * .4f)), erode);
                    return true;
                }

                VfxPrimitives.lusterRibbonEroded(pose, buffers, entry.add(0, -1.25, 0), entry.add(0, 1.95, 0), camera,
                        .52 * (1f - p * .4f), .12, DialDraw.alpha(SLATE_COBALT, .94f * (1f - p * .35f)), 1.6f, erode, true);
                VfxPrimitives.lusterRibbonEroded(pose, buffers, exit.add(0, -1.25, 0), exit.add(0, 2.15, 0), camera,
                        .62 * (1f - p * .4f), .14, DialDraw.alpha(PLATINUM_IVORY, .96f * (1f - p * .35f)), 1.6f, erode, true);
                VfxPrimitives.lusterRibbonEroded(pose, buffers, entry, exit, camera,
                        .28 * (1f - p * .4f), .14, DialDraw.alpha(PLATINUM_SILVER, .88f * (1f - p * .35f)), 4.0f, erode, true);
                VfxPrimitives.astrolabeRingEroded(pose, buffers, exit.add(0, -.88, 0), .5 + snap * 5.0, .38,
                        age * .18, age * .18 + TAU, 36, DialDraw.alpha(PLATINUM_SILVER, .92f * (1f - p * .4f)), erode);
            }
            case WristNetwork.ASCALON_TEMPEST_CUE -> {
                if (cue.beat() == 2) {
                    if (age > 11f) return true;
                    float p = age / 11f;
                    Vec3 cur = cue.from().lerp(cue.to(), Math.min(1f, p * 1.55f));
                    VfxPrimitives.lusterRibbonEroded(pose, buffers, cue.from(), cur, camera,
                            .15 * (1f - p * .3f), .035, DialDraw.alpha(PLATINUM_IVORY, .92f * (1f - p * .35f)), 2.0f, p * .85f, true);
                    return true;
                }
                if (age > 24f) return true;
                float p = age / 24f;
                double snap = 1.0 - Math.pow(1.0 - p, 4.2);
                float erode = (float) Math.pow(p, 1.15) * 0.90f;
                Vec3 c = cue.beat() == 1 ? cue.to() : cue.from();
                VfxPrimitives.astrolabeRingEroded(pose, buffers, c.add(0, .10, 0), 1.2 + snap * 9.2, .62,
                        age * .15, age * .15 + TAU, 48,
                        DialDraw.alpha(cue.beat() == 1 ? CHAMPAGNE_GOLD : PLATINUM_SILVER, .92f * (1f - p * .4f)), erode);
                VfxPrimitives.lusterRingEroded(pose, buffers, c.add(0, .25, 0), 1.0 + snap * 6.8, .42, .36 * (1f - p),
                        -age * .18, -age * .18 + TAU, 40, DialDraw.alpha(PLATINUM_IVORY, .86f * (1f - p * .4f)), erode);
            }
            case WristNetwork.ASCALON_DOME_ARC_CUE -> {
                if (age > 10f) return true;
                float p = age / 10f;
                VfxPrimitives.lusterRibbonEroded(pose, buffers, cue.from(), cue.to(), camera,
                        .14 * (1f - p * .4f), .04, DialDraw.alpha(PLATINUM_IVORY, .94f * (1f - p * .3f)), 1.4f, p * .85f, true);
            }
            case WristNetwork.ASCALON_SYZYGY_CUE -> {
                if (age > 44f) return true;
                float bp = age / 44f;
                double snap = 1.0 - Math.pow(1.0 - bp, 4.0);
                float erode = (float) Math.pow(bp, 1.2) * 0.90f;
                Vec3 c = cue.from();
                double r = 1.2 + 50.8 * snap;
                VfxPrimitives.materialRibbon(pose, buffers, c, c.add(0, 4.2, 0), camera,
                        1.25 * (1f - bp * .55f), .18,
                        DialDraw.alpha(COPPER, .94f * (1f - bp * .4f)), VfxPrimitives.STAR, erode);
                VfxPrimitives.lusterRingEroded(pose, buffers, c.add(0, .12, 0), r, 1.35, 1.05 * (1f - bp),
                        age * .09, age * .09 + TAU, 72, DialDraw.alpha(COPPER, .86f * (1f - bp * .4f)), erode);
                VfxPrimitives.astrolabeRingEroded(pose, buffers, c.add(0, .28, 0), r * .72, 1.15,
                        -age * .12, -age * .12 + TAU, 60, DialDraw.alpha(CHAMPAGNE_GOLD, .92f * (1f - bp * .4f)), erode);
            }
            default -> {}
        }
        return true;
    }
}
