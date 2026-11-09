package cn.academy.port.fleet.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import cn.academy.port.fleet.GunItem;
import cn.academy.port.fleet.InteractionLab;
import cn.academy.port.fleet.LabNetwork;
import cn.academy.port.fleet.LabVehicle;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * ACA six-gun/six-vehicle controls, HUD and authored model animation for NeoForge 1.21.1.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class LabClient {
    private static final KeyMapping RELOAD = new KeyMapping("key.aclab.reload", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.aclab");
    private static final KeyMapping FIRE = new KeyMapping("key.aclab.fire", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.aclab");
    private static final KeyMapping AIM_TOGGLE = new KeyMapping("key.aclab.aim_toggle", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.aclab");
    private static final KeyMapping SIREN = new KeyMapping("key.aclab.siren", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.aclab");
    public static float aimAmount(){return aim;}
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("academy", "textures/ui/white.png");

    private static float aim, previousAim, recoil, previousRecoil, recoilVelocity;
    private static float sprint, previousSprint, driveRoll, previousDriveRoll;
    private static int lastAmmo = -1, lastVariant = -1, lastSlot = -1;
    private static int cachedAmmo, cachedReserve, cachedCapacity;
    private static long cachedReloadStart, cachedReloadEnd, nextFireTick, nextReloadTick;
    private static long lastShotTick = Long.MIN_VALUE, lastGameTick = Long.MIN_VALUE;
    private static Object observedPlayer, observedLevel;
    private static boolean aimToggle, fireWasHeld, suppressTriggerUntilRelease;
    private static String reloadKey = "", aimKey = "", forwardKey = "", backKey = "", leftKey = "", rightKey = "", exitKey = "";

    private LabClient() {}

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(RELOAD);
            e.register(FIRE);
            e.register(AIM_TOGGLE);e.register(SIREN);
        }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(InteractionLab.CAR.get(), LabVehicleRenderer::new);
        }

        @SubscribeEvent
        public static void shaders(net.neoforged.neoforge.client.event.RegisterShadersEvent e) throws java.io.IOException {
            e.registerShader(new net.minecraft.client.renderer.ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("academy","fleet_vehicle"),com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY),value->VehicleGpu.shader=value);
        }

        @SubscribeEvent
        public static void driverLayers(EntityRenderersEvent.AddLayers e){
            for(var skin:e.getSkins()){
                net.minecraft.client.renderer.entity.player.PlayerRenderer renderer=e.getSkin(skin);
                renderer.addLayer(new DriverLimbLayer(renderer));
            }
        }

        @SubscribeEvent
        public static void reloadListeners(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((ResourceManagerReloadListener) resources -> {
                LabMesh.clearCache();
                GunAnchors.clearCache();
                G03Renderer.clear();
                VehicleModel.clear();DriverHands.clear();
                LabHud.clearResourceCache();FleetAudio.reset();
            });
        }
    }

    private static boolean playerView(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.player.isAlive()
                && !mc.player.isSpectator() && mc.getCameraEntity() == mc.player;
    }

    private static boolean gun(Minecraft mc) {
        if (!playerView(mc) || mc.player.getVehicle() instanceof LabVehicle) return false;
        if (!(mc.player.getMainHandItem().getItem() instanceof GunItem g)) return false;
        return g.variant >= 0 && g.variant < InteractionLab.GUN_IDS.length;
    }

    private static boolean controls(Minecraft mc) {
        return playerView(mc) && mc.screen == null && mc.isWindowActive() && !mc.isPaused();
    }

    private static boolean currentPose(Minecraft mc, GunItem held) {
        return held.variant == lastVariant && mc.player.getInventory().selected == lastSlot;
    }

    private static float steering(LabVehicle vehicle) {
        float value = vehicle.steering();
        return Float.isFinite(value) ? Mth.clamp(value, -1, 1) : 0;
    }

    private static void send(String action) {
        PacketDistributor.sendToServer(new LabNetwork.Request(action, 0, 0));
    }

    private static int clicks(KeyMapping mapping) {
        int count = 0;
        while (mapping.consumeClick()) count++;
        return count;
    }

    private static void resetWeapon() {
        G03Renderer.resetFirstPerson();
        aim = previousAim = recoil = previousRecoil = recoilVelocity = sprint = previousSprint = 0;
        lastAmmo = lastVariant = lastSlot = -1;
        cachedAmmo = cachedReserve = cachedCapacity = 0;
        cachedReloadStart = cachedReloadEnd = nextFireTick = nextReloadTick = 0;
        lastShotTick = Long.MIN_VALUE;
        aimToggle = fireWasHeld = false;
    }

    private static void resetAll() {
        resetWeapon();
        driveRoll = previousDriveRoll = 0;
        observedPlayer = observedLevel = null;
        lastGameTick = Long.MIN_VALUE;
        suppressTriggerUntilRelease = true;
    }

    private static void updateKeyLabels(Minecraft mc) {
        reloadKey = RELOAD.getTranslatedKeyMessage().getString();
        aimKey = mc.options.keyUse.getTranslatedKeyMessage().getString();
        forwardKey = mc.options.keyUp.getTranslatedKeyMessage().getString();
        backKey = mc.options.keyDown.getTranslatedKeyMessage().getString();
        leftKey = mc.options.keyLeft.getTranslatedKeyMessage().getString();
        rightKey = mc.options.keyRight.getTranslatedKeyMessage().getString();
        exitKey = mc.options.keyShift.getTranslatedKeyMessage().getString();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        // Always drain mappings, including menus and disconnects, to prevent queued actions on return.
        boolean reloadPressed = clicks(RELOAD) > 0;
        boolean sirenPressed = clicks(SIREN) > 0;
        boolean togglePressed = (clicks(AIM_TOGGLE) & 1) != 0;
        clicks(FIRE);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            resetAll();
            return;
        }
        if (observedPlayer != mc.player || observedLevel != mc.level) {
            resetAll();
            observedPlayer = mc.player;
            observedLevel = mc.level;
        }
        long now = mc.level.getGameTime();
        if (now < lastGameTick) resetWeapon();
        lastGameTick = now;
        previousAim = aim;
        previousRecoil = recoil;
        previousSprint = sprint;
        previousDriveRoll = driveRoll;
        updateKeyLabels(mc);

        boolean allowed = controls(mc);
        if (!allowed) {
            aimToggle = false;
            suppressTriggerUntilRelease = true;
        }
        if (mc.player.getVehicle() instanceof LabVehicle vehicle) {
            if(allowed&&togglePressed)send("horn");
            if(allowed&&sirenPressed)send("siren");
            float forward = allowed ? (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0) : 0;
            float steer = allowed ? (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0) : 0;
            // Keep the original per-tick driver protocol: the vehicle timeout is not in the handoff.
            PacketDistributor.sendToServer(new LabNetwork.Request("drive", forward, steer));
            float speed = Float.isFinite(vehicle.speed()) ? vehicle.speed() : 0;
            float rollTarget = -steering(vehicle) * Math.min(1, Math.abs(speed) * 4) * 1.5f;
            driveRoll += (rollTarget - driveRoll) * 0.25f;
        } else {
            driveRoll *= 0.7f;
        }
        if (!gun(mc)) {
            resetWeapon();
            return;
        }

        ItemStack stack = mc.player.getMainHandItem();
        GunItem held = (GunItem) stack.getItem();
        int slot = mc.player.getInventory().selected;
        // Do not compare ItemStack identity: a normal network ammo update may replace that object.
        // Selected-slot tracking also resets when swapping two guns of the same variant.
        if (lastVariant != held.variant || lastSlot != slot) resetWeapon();
        int ammo = Math.max(0, GunItem.ammo(stack));
        if (held.variant != 2 && lastAmmo >= 0 && ammo < lastAmmo) {
            recoilVelocity = Math.min(1.4f, recoilVelocity + 0.85f + (held.heavy() ? 0.25f : 0));
            lastShotTick = now;
        }
        lastVariant = held.variant;
        lastSlot = slot;
        lastAmmo = cachedAmmo = ammo;
        cachedCapacity = held.capacity();
        cachedReloadStart = GunItem.reloadStart(stack);
        cachedReloadEnd = GunItem.reloadEnd(stack);
        cachedReserve = 0;
        for (ItemStack inventoryStack : mc.player.getInventory().items) {
            if (inventoryStack.is(held.heavy() ? InteractionLab.HEAVY_CELL.get() : InteractionLab.LIGHT_CELL.get())) {
                cachedReserve += inventoryStack.getCount();
            }
        }
        if(held.variant>=0)cachedReserve=cn.academy.port.fleet.CoilReloads.reserve(mc.player);
        boolean reloading = ReloadTimeline.isActive(cachedReloadStart, cachedReloadEnd, now);
        if (allowed && togglePressed) aimToggle = !aimToggle;
        boolean reloadRequested = false;
        if (allowed && reloadPressed && (held.variant>=0||!reloading) && (reloading||ammo < held.capacity()) && (reloading||cachedReserve > 0) && now >= nextReloadTick) {
            send("reload");
            nextReloadTick = now + Math.max(4, Math.min(10, held.reloadTicks()));
            reloadRequested = true;
        }

        boolean rawTrigger = mc.options.keyAttack.isDown() || FIRE.isDown();
        if (allowed && !rawTrigger) suppressTriggerUntilRelease = false;
        boolean trigger = allowed && !suppressTriggerUntilRelease && rawTrigger;
        if (trigger && !reloading && !reloadRequested) {
            if (ammo > 0 && now >= nextFireTick && !mc.player.getCooldowns().isOnCooldown(held)) {
                send("fire");
                nextFireTick = now + Math.max(1, InteractionLab.COOLDOWN[held.variant]);
            } else if (ammo == 0 && !fireWasHeld && now >= nextFireTick) {
                // One dry click per press, not a sound packet every client tick.
                send("fire");
                nextFireTick = now + 6;
            }
        }
        fireWasHeld = trigger;
        boolean aiming = allowed && !reloading && !reloadRequested && !mc.player.isSprinting()
                && (mc.options.keyUse.isDown() || aimToggle);
        boolean running = allowed && mc.player.isSprinting() && !reloading && !aiming;
        aim += ((aiming ? 1f : 0f) - aim) * 0.32f;
        sprint += ((running ? 1f : 0f) - sprint) * 0.25f;
        recoilVelocity += -0.40f * recoil - 0.45f * recoilVelocity;
        recoil = Mth.clamp(recoil + recoilVelocity, -0.12f, 1.3f);
    }

    @SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (controls(mc) && gun(mc) && (e.isAttack() || e.isUseItem())) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    private static float partial(Minecraft mc) {
        return Mth.clamp(mc.getTimer().getGameTimeDeltaPartialTick(false), 0, 1);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.usedConfiguredFov() && gun(mc) && mc.screen == null && mc.options.getCameraType().isFirstPerson()) {
            GunItem held = (GunItem) mc.player.getMainHandItem().getItem();
            float renderAim = currentPose(mc, held) ? Mth.lerp(partial(mc), previousAim, aim) : 0;
            e.setFOV(e.getFOV() * (1 - 0.16 * renderAim));
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (playerView(mc) && mc.player.getVehicle() instanceof LabVehicle && mc.options.getCameraType().isFirstPerson()) {
            e.setRoll(e.getRoll() + Mth.lerp(partial(mc), previousDriveRoll, driveRoll));
        }
    }

    private static float reload(ItemStack stack, Minecraft mc) {
        if (mc.level == null) return 0;
        return ReloadTimeline.progress(GunItem.reloadStart(stack), GunItem.reloadEnd(stack), (double)mc.level.getGameTime() + partial(mc));
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!playerView(mc) || !mc.options.getCameraType().isFirstPerson()) return;
        if (mc.player.getVehicle() instanceof LabVehicle vehicle) {
            e.setCanceled(true);
            if (e.getHand() == InteractionHand.MAIN_HAND) DriverHands.first(e, vehicle);
            return;
        }
        if (!gun(mc)) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = mc.player.getMainHandItem();
        GunItem held = (GunItem) stack.getItem();
        GunAnchors.Profile profile = GunAnchors.forVariant(held.variant);
        float r = reload(stack, mc);
        boolean empty = GunItem.ammo(stack) <= 0;
        var motion = ReloadTimeline.sample(r, empty);
        boolean poseCurrent = currentPose(mc, held);
        float renderAim = poseCurrent ? Mth.lerp(e.getPartialTick(), previousAim, aim) : 0;
        float renderRecoil = poseCurrent ? Mth.lerp(e.getPartialTick(), previousRecoil, recoil) : 0;
        float renderSprint = poseCurrent ? Mth.lerp(e.getPartialTick(), previousSprint, sprint) : 0;
        float side = mc.player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        double time = (double)mc.level.getGameTime() + e.getPartialTick();
        if (held.variant >= 0) { G03Renderer.first(e, stack, renderAim, renderSprint, time); return; }
        float breathe = (float) Math.sin(time * 0.08) * 0.0012f * (1 - renderAim);
        float reloadSway = r > 0 ? (float) Math.sin(Math.PI * r) : 0;
        PoseStack ps = e.getPoseStack();
        org.joml.Matrix4f basePose = new org.joml.Matrix4f(ps.last().pose());
        ps.pushPose();
        // Runtime model +Z points toward the muzzle. The 180 degree yaw turns it into camera -Z.
        // Therefore the sight's local +X needs a +X camera translation, and Y needs -sight.y.
        ps.translate(side * (Mth.lerp(renderAim, profile.hip().x(), profile.sight().x()) - 0.06f * reloadSway),
                Mth.lerp(renderAim, profile.hip().y(), -profile.sight().y()) - motion.rootLower()
                        - 0.055f * renderSprint - 0.24f * e.getEquipProgress() + breathe,
                Mth.lerp(renderAim, profile.hip().z(), -profile.adsDistance()) + 0.06f * renderRecoil + 0.035f * reloadSway);
        ps.scale(side, 1, 1);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        var grip = profile.grip();
        ps.translate(grip.x(), grip.y(), grip.z());
        ps.mulPose(Axis.XP.rotationDegrees(-6.5f * renderRecoil + motion.rootPitchDeg()
                + 15 * renderSprint + 12 * e.getEquipProgress()));
        ps.mulPose(Axis.ZP.rotationDegrees(motion.rootRollDeg() - 8 * renderSprint));
        ps.translate(-grip.x(), -grip.y(), -grip.z());
        LabMesh.draw(InteractionLab.GUN_IDS[held.variant], ps, e.getMultiBufferSource(), e.getPackedLight(),
                0, 0, r, renderRecoil, false, empty);
        if (poseCurrent && lastShotTick != Long.MIN_VALUE && time - lastShotTick >= 0 && time - lastShotTick < 0.8 && r == 0) {
            flash(e, held.variant, profile);
        }
        GunRig.draw(ps, e.getMultiBufferSource(), e.getPackedLight(), basePose, held.variant, r, empty);
        ps.popPose();
    }

    private static void flash(RenderHandEvent e, int variant, GunAnchors.Profile profile) {
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        var muzzle = profile.muzzle();
        ps.translate(muzzle.x(), muzzle.y(), muzzle.z() + 0.025f);
        float radius = variant >= 4 ? 0.095f : 0.055f;
        var out = e.getMultiBufferSource().getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        var pose = ps.last();
        int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        vertex(out, pose, -radius, -radius * 0.35f, 113, 240, 255, light);
        vertex(out, pose, radius, -radius * 0.35f, 113, 240, 255, light);
        vertex(out, pose, radius, radius * 0.35f, 113, 240, 255, light);
        vertex(out, pose, -radius, radius * 0.35f, 113, 240, 255, light);
        vertex(out, pose, -radius * 0.35f, -radius, 215, 255, 255, light);
        vertex(out, pose, radius * 0.35f, -radius, 215, 255, 255, light);
        vertex(out, pose, radius * 0.35f, radius, 215, 255, 255, light);
        vertex(out, pose, -radius * 0.35f, radius, 215, 255, 255, light);
        ps.popPose();
    }

    private static void wheel(RenderHandEvent e,LabVehicle vehicle){
        PoseStack p=e.getPoseStack();org.joml.Matrix4f basePose=new org.joml.Matrix4f(p.last().pose());
        if(vehicle.variant()==2){handlebars(e,vehicle,basePose);return;}
        if(vehicle.variant()==3){
            // Pilot view from the model's cockpit, drawn relative to a calibrated eye point.
            p.pushPose();p.mulPose(Axis.YP.rotationDegrees(180));p.translate(-.44,-1.24,-.44);
            LabMesh.draw(InteractionLab.CAR_IDS[3],p,e.getMultiBufferSource(),e.getPackedLight(),0,steering(vehicle),0,0,true);
            p.popPose();
        } else CockpitRig.draw(p,e.getMultiBufferSource(),e.getPackedLight());
        p.pushPose();p.translate(0,vehicle.variant()==3?-0.24:-0.35,vehicle.variant()==3?-0.53:-0.85);p.mulPose(Axis.ZP.rotationDegrees(-steering(vehicle)*48));
        var out=e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(WHITE));
        var pose=p.last();
        for(int i=0;i<32;i++){
            double a=Math.PI*2*i/32,b=Math.PI*2*(i+1)/32;
            vertex(out,pose,(float)Math.cos(a)*.23f,(float)Math.sin(a)*.23f,42,58,65,e.getPackedLight());
            vertex(out,pose,(float)Math.cos(b)*.23f,(float)Math.sin(b)*.23f,42,58,65,e.getPackedLight());
            vertex(out,pose,(float)Math.cos(b)*.18f,(float)Math.sin(b)*.18f,96,119,126,e.getPackedLight());
            vertex(out,pose,(float)Math.cos(a)*.18f,(float)Math.sin(a)*.18f,96,119,126,e.getPackedLight());
        }
        for(int side:new int[]{-1,1}){
            vertex(out,pose,0,-.04f,62,81,89,e.getPackedLight());vertex(out,pose,side*.20f,-.02f,62,81,89,e.getPackedLight());
            vertex(out,pose,side*.20f,.025f,62,81,89,e.getPackedLight());vertex(out,pose,0,.04f,62,81,89,e.getPackedLight());
        }
        GunRig.drawWheelHands(p,e.getMultiBufferSource(),e.getPackedLight(),basePose);
        p.popPose();
    }
    private static void handlebars(RenderHandEvent e,LabVehicle v,org.joml.Matrix4f basePose){
        PoseStack p=e.getPoseStack();p.pushPose();p.translate(0,-.35,-.85);p.mulPose(Axis.YP.rotationDegrees(-steering(v)*23));
        var o=e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(WHITE));var pose=p.last();int l=e.getPackedLight();
        vertex(o,pose,-.39f,-.025f,33,41,45,l);vertex(o,pose,.39f,-.025f,33,41,45,l);vertex(o,pose,.39f,.025f,83,98,102,l);vertex(o,pose,-.39f,.025f,83,98,102,l);
        vertex(o,pose,-.035f,-.22f,35,48,52,l);vertex(o,pose,.035f,-.22f,35,48,52,l);vertex(o,pose,.035f,.03f,72,92,98,l);vertex(o,pose,-.035f,.03f,72,92,98,l);
        vertex(o,pose,-.11f,.02f,24,34,40,l);vertex(o,pose,.11f,.02f,24,34,40,l);vertex(o,pose,.11f,.09f,47,129,140,l);vertex(o,pose,-.11f,.09f,47,129,140,l);
        GunRig.drawHandlebarHands(p,e.getMultiBufferSource(),l,basePose);p.popPose();
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack.Pose pose,float x,float y,int r,int g,int b,int light){
        out.addVertex(pose,x,y,0).setColor(r,g,b,255).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
    }

    @SubscribeEvent
    public static void crosshairLayer(RenderGuiLayerEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getName().equals(VanillaGuiLayers.CROSSHAIR) && playerView(mc) && mc.screen == null
                && (gun(mc) || mc.player.getVehicle() instanceof LabVehicle)) {
            // Drawing a custom crosshair without cancelling this layer causes a double reticle in ADS.
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!playerView(mc) || mc.options.hideGui || mc.screen != null) return;
        if (mc.player.getVehicle() instanceof LabVehicle vehicle) {
            LabHud.vehicle(e.getGuiGraphics(), mc, new LabHud.DriveState(vehicle.speed(), vehicle.steering(),
                    forwardKey, backKey, leftKey, rightKey, exitKey));
            return;
        }
        if (!gun(mc)) return;
        GunItem held = (GunItem) mc.player.getMainHandItem().getItem();
        if (held.variant != lastVariant || mc.player.getInventory().selected != lastSlot) return;
        double time = (double)mc.level.getGameTime() + partial(mc);
        boolean active = ReloadTimeline.isActive(cachedReloadStart, cachedReloadEnd, time);
        float progress = ReloadTimeline.progress(cachedReloadStart, cachedReloadEnd, time);
        float renderAim = mc.options.getCameraType().isFirstPerson() ? Mth.lerp(partial(mc), previousAim, aim) : 0;
        float renderRecoil = Mth.lerp(partial(mc), previousRecoil, recoil);
        LabHud.weapon(e.getGuiGraphics(), mc, new LabHud.GunState(held.variant, cachedAmmo, cachedReserve,
                cachedCapacity, held.heavy(), active, progress, reloadKey, aimKey), renderAim, renderRecoil);
    }
}
