package cn.acamech.client;

import cn.acamech.Chassis;
import cn.acamech.MechEntity;
import cn.acamech.MechMod;
import cn.acamech.MechMod.Controls;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = MechMod.ID, dist = Dist.CLIENT)
public final class MechClient {
    private static final KeyMapping AUX = new KeyMapping("key.acamech.aux", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.acamech");
    private static final KeyMapping STRIKE = new KeyMapping("key.acamech.strike", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.acamech");
    private static final KeyMapping FIRE = new KeyMapping("key.acamech.fire", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.acamech");
    private static int previousMount = -1;
    private static long firstTick;
    private static boolean hasStruck;

    public MechClient(IEventBus modBus) {
        modBus.addListener(MechRenderTypes::registerShaders);
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(AUX);
            event.register(STRIKE);
            event.register(FIRE);
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            for (Chassis model : Chassis.values())
                event.registerEntityRenderer(MechMod.entity(model), context -> new MechRenderer(context, model));
            event.registerEntityRenderer(MechMod.PULSE_BOLT.get(), PulseRenderer::new);
        });
        modBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAbove(VanillaGuiLayers.HOTBAR, MechMod.id("cockpit"), (out, tick) -> {
                    var game = Minecraft.getInstance();
                    if (game.player == null || game.options.hideGui || !(game.player.getVehicle() instanceof MechEntity mech)) return;
                    boolean hints = mech.tickCount - firstTick < (hasStruck ? 170 : 240);
                    CockpitHud.render(out, tick, game, mech, AUX, STRIKE, FIRE, hints);
                }));
        var game = NeoForge.EVENT_BUS;
        game.addListener(MechClient::keys);
        game.addListener(MechClient::cameraImpulse);
        game.addListener(RangedEffects::render);
        game.addListener((CalculateDetachedCameraDistanceEvent event) -> {
            if (event.getCamera().getEntity() != null
                    && event.getCamera().getEntity().getVehicle() instanceof MechEntity mech)
                event.setDistance(mech.chassis().camera);
        });
        game.addListener((RenderPlayerEvent.Pre event) -> {
            if (event.getEntity().getVehicle() instanceof MechEntity) event.setCanceled(true);
        });
        game.addListener((RenderHandEvent event) -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.getVehicle() instanceof MechEntity) event.setCanceled(true);
        });
        game.addListener((RenderGuiLayerEvent.Pre event) -> {
            var player = Minecraft.getInstance().player;
            if (event.getName().equals(VanillaGuiLayers.CROSSHAIR) && player != null && player.getVehicle() instanceof MechEntity) {
                event.setCanceled(true);
            }
        });
        game.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.getVehicle() instanceof MechEntity) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        });
    }

    private static void cameraImpulse(ViewportEvent.ComputeCameraAngles event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || event.getCamera().getEntity() != mc.player || !mc.options.getCameraType().isFirstPerson()
                || !mc.options.bobView().get() || !(mc.player.getVehicle() instanceof MechEntity mech)) return;
        float partial = (float) event.getPartialTick();
        float contact = impulse(mech.strikeImpactAge(partial), 6f) * .74f;
        if (mech.rangedHitMarker()) contact += impulse(mech.rangedImpactAge(partial), 5f) * .32f;
        float shot = mech.rangedAge() >= mech.chassis().ranged.windup && mech.rangedResult() != cn.acamech.RangedWeapon.ABORTED
                ? impulse(mech.rangedAge() - mech.chassis().ranged.windup + partial, 6f) * .86f : 0;
        float mass = switch (mech.chassis()) { case ATLAS -> 1; case AETHER -> .56f; case SERAPH -> .76f; };
        float landing = mech.landing(partial);
        event.setPitch(event.getPitch() + Mth.clamp((landing * 1.08f + contact * .65f - shot) * mass, -1.2f, 1.2f));
        event.setRoll(event.getRoll() + (contact - shot) * .43f * mass);
    }

    private static float impulse(float age, float length) {
        if (age < 0 || age > length) return 0;
        return (1 - AttackPose.ease(age / length)) * Mth.cos(age * .78f);
    }

    private static void keys(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof MechEntity mech)) {
            previousMount = -1;
            hasStruck = false;
            while (STRIKE.consumeClick()) {}
            while (FIRE.consumeClick()) {}
            return;
        }
        if (mech.getId() != previousMount) {
            previousMount = mech.getId();
            firstTick = mech.tickCount;
            while (STRIKE.consumeClick()) {}
            while (FIRE.consumeClick()) {}
            hasStruck = false;
        }
        int input = 0;
        if (mc.screen == null) {
            if (mc.options.keyUp.isDown()) input |= Controls.FORWARD;
            if (mc.options.keyDown.isDown()) input |= Controls.BACK;
            if (mc.options.keyLeft.isDown()) input |= Controls.LEFT;
            if (mc.options.keyRight.isDown()) input |= Controls.RIGHT;
            if (AUX.isDown()) input |= Controls.AUX;
            if (mc.options.keySprint.isDown()) input |= Controls.RUN;
            if (STRIKE.consumeClick()) {
                hasStruck = true;
                input |= Controls.STRIKE;
            }
            if (FIRE.consumeClick()) input |= Controls.FIRE;
        }
        PacketDistributor.sendToServer(new Controls((byte) input, mech.getId()));
    }

}
