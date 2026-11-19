package cn.aca.infinity.client;

import cn.aca.infinity.AcaInfinity;
import cn.aca.infinity.InfinityNetwork;
import cn.aca.infinity.SanctuaryShipEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = AcaInfinity.ID, dist = Dist.CLIENT)
public final class InfinityClient {
    private static final String CATEGORY = "key.categories.aca_infinity";
    private static final int INPUT_BUFFER_WINDOW_TICKS = 6;

    /**
     * Context-isolated Gauntlet keybinds: active ONLY when in-game, on foot (not piloting Sanctuary II),
     * and not holding an Academy firearm (`cn.academy.port.fleet.GunItem`), ensuring ZERO keybind collision
     * across Academy (`V/C/N/R/F/B/LeftAlt`), LabClient guns (`G/H/J/K`), and Omnitrix+Ascalon (`Y/U/I/O/M`)!
     */
    public static final IKeyConflictContext GAUNTLET_CONTEXT = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            if (!KeyConflictContext.IN_GAME.isActive()) return false;
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.getVehicle() instanceof SanctuaryShipEntity) return false;
            return !isAcademyGun(mc.player.getMainHandItem()) && !isAcademyGun(mc.player.getOffhandItem());
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    /** Context-isolated Sanctuary II bridge controls: active ONLY while piloting Sanctuary II! */
    public static final IKeyConflictContext SHIP_PILOT_CONTEXT = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            if (!KeyConflictContext.IN_GAME.isActive()) return false;
            var mc = Minecraft.getInstance();
            return mc.player != null && mc.player.getVehicle() instanceof SanctuaryShipEntity;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyMapping KEY_WHEEL = key("wheel", GAUNTLET_CONTEXT, GLFW.GLFW_KEY_H);
    public static final KeyMapping KEY_PRIMARY = key("primary", GAUNTLET_CONTEXT, GLFW.GLFW_KEY_J);
    public static final KeyMapping KEY_SECONDARY = key("secondary", GAUNTLET_CONTEXT, GLFW.GLFW_KEY_K);
    public static final KeyMapping KEY_SNAP = key("snap", GAUNTLET_CONTEXT, GLFW.GLFW_KEY_Z);
    public static final KeyMapping KEY_TOGGLE_GAUNTLET = key("toggle_gauntlet", GAUNTLET_CONTEXT, GLFW.GLFW_KEY_COMMA);

    public static final KeyMapping KEY_SHIP_DESCEND = key("ship_descend", SHIP_PILOT_CONTEXT, GLFW.GLFW_KEY_C);
    public static final KeyMapping KEY_SHIP_SHIELD = key("ship_shield", SHIP_PILOT_CONTEXT, GLFW.GLFW_KEY_R);
    public static final KeyMapping KEY_SHIP_RAIN_FIRE = key("ship_rain_fire", SHIP_PILOT_CONTEXT, GLFW.GLFW_KEY_V);
    public static final KeyMapping KEY_SHIP_ESCORT = key("ship_escort", SHIP_PILOT_CONTEXT, GLFW.GLFW_KEY_B);
    public static final KeyMapping KEY_SHIP_WARP = key("ship_warp", SHIP_PILOT_CONTEXT, GLFW.GLFW_KEY_X);
    public static final KeyMapping KEY_SHIP_CAMERA = key("ship_camera", SHIP_PILOT_CONTEXT, GLFW.GLFW_KEY_G);

    private static int bufferedActionId = -1;
    private static int bufferedArg0 = 0;
    private static long bufferedExpireTick = -1L;

    private static boolean isAcademyGun(ItemStack stack) {
        return !stack.isEmpty() && "cn.academy.port.fleet.GunItem".equals(stack.getItem().getClass().getName());
    }

    private static boolean canUseGauntletHotkeys(Player player) {
        return player != null
                && !(player.getVehicle() instanceof SanctuaryShipEntity)
                && !isAcademyGun(player.getMainHandItem())
                && !isAcademyGun(player.getOffhandItem());
    }

    private static KeyMapping key(String name, IKeyConflictContext ctx, int glfwKey) {
        return new KeyMapping("key.aca_infinity." + name, ctx,
                InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    public static int bufferedAction() {
        return bufferedActionId;
    }

    public InfinityClient(IEventBus modBus) {
        InfinityNetwork.clientReceiver = new ClientCache();
        modBus.addListener(InfinityClient::registerKeys);
        modBus.addListener(InfinityClient::registerRenderers);
        modBus.addListener(InfinityClient::registerLayers);
        modBus.addListener(InfinityClient::registerGui);
        modBus.addListener(InfinityClient::registerItemExtensions);
        modBus.addListener(InfinityClient::registerReload);
        modBus.addListener(InfinityCosmicShader::register);

        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(InfinityClient::onClientTick);
        gameBus.addListener(InfinityClient::onLogout);
        gameBus.addListener(InfinityClient::onAttackOrUse);
        gameBus.addListener(GauntletRender::onRenderHand);
        gameBus.addListener(CosmicVfxRenderer::onRenderLevel);
        gameBus.addListener(CosmicVfxRenderer::onRenderNameplate);
        gameBus.addListener(CosmicVfxRenderer::onCameraShake);
        gameBus.addListener(InfinityPostShader::onRenderLevel);
        gameBus.addListener(InfinityPostShader::onComputeFov);
        gameBus.addListener(ShipClientRender::onCameraDistance);
        gameBus.addListener(ShipClientRender::onRenderGuiLayerPre);
        gameBus.addListener(ShipClientRender::onCaptureMatrices);
    }

    private static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(KEY_WHEEL);
        e.register(KEY_PRIMARY);
        e.register(KEY_SECONDARY);
        e.register(KEY_SNAP);
        e.register(KEY_TOGGLE_GAUNTLET);
        e.register(KEY_SHIP_DESCEND);
        e.register(KEY_SHIP_SHIELD);
        e.register(KEY_SHIP_RAIN_FIRE);
        e.register(KEY_SHIP_ESCORT);
        e.register(KEY_SHIP_WARP);
        e.register(KEY_SHIP_CAMERA);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(AcaInfinity.SANCTUARY_SHIP.get(), ShipClientRender.ShipRenderer::new);
        e.registerEntityRenderer(AcaInfinity.SHIP_ORDNANCE.get(), ShipClientRender.OrdnanceRenderer::new);
    }

    private static void registerLayers(EntityRenderersEvent.AddLayers e) {
        for (var skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof PlayerRenderer pr) {
                pr.addLayer(new GauntletRender.WearerLayer(pr));
            }
        }
    }

    private static void registerGui(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, AcaInfinity.id("hud"), InfinityHud::render);
        e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, AcaInfinity.id("snap_flash"), CosmicVfxRenderer::renderScreenOverlay);
    }

    private static void registerItemExtensions(RegisterClientExtensionsEvent e) {
        IClientItemExtensions showcaseExt = new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return GauntletRender.customItemRenderer();
            }
        };
        e.registerItem(showcaseExt,
                AcaInfinity.INFINITY_GAUNTLET.get(),
                AcaInfinity.stoneItem(cn.aca.infinity.Stone.SPACE),
                AcaInfinity.stoneItem(cn.aca.infinity.Stone.REALITY),
                AcaInfinity.stoneItem(cn.aca.infinity.Stone.POWER),
                AcaInfinity.stoneItem(cn.aca.infinity.Stone.MIND),
                AcaInfinity.stoneItem(cn.aca.infinity.Stone.TIME),
                AcaInfinity.stoneItem(cn.aca.infinity.Stone.SOUL),
                AcaInfinity.SANCTUARY_BEACON.get(),
                AcaInfinity.HOSTILE_SANCTUARY_BEACON.get());
    }

    private static void registerReload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((ResourceManagerReloadListener) rm -> {
            InfinityRig.clearCache();
            InfinityPostShader.reload();
        });
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientCache.clear();
        bufferedActionId = -1;
        bufferedArg0 = 0;
        bufferedExpireTick = -1L;
    }

    private static void onAttackOrUse(InputEvent.InteractionKeyMappingTriggered e) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof SanctuaryShipEntity) {
            if (e.isAttack()) {
                ShipClientRender.queueWeaponTrigger(true, false);
                e.setSwingHand(false);
                e.setCanceled(true);
            } else if (e.isUseItem()) {
                ShipClientRender.queueWeaponTrigger(false, true);
                e.setSwingHand(false);
                e.setCanceled(true);
            }
        }
    }

    private static void queueOrDispatchSkill(Minecraft mc, ClientCache.WearerView self, int action) {
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        long est = self.estimatedServerNow(now);
        int sel = self.data().selectedOrdinal();
        int shiftFlag = (Screen.hasShiftDown() || mc.options.keyShift.isDown() || mc.player.isShiftKeyDown()) ? 1 : 0;
        long readyAt = switch (action) {
            case InfinityNetwork.ACT_CAST_PRIMARY -> self.snapshot().primaryReady()[sel];
            case InfinityNetwork.ACT_CAST_SECONDARY -> self.snapshot().secondaryReady()[sel];
            case InfinityNetwork.ACT_CAST_SNAP -> self.snapshot().snapReadyAt();
            default -> 0L;
        };
        long rem = readyAt - est;
        if (!mc.player.getAbilities().instabuild && rem > 0L && rem <= INPUT_BUFFER_WINDOW_TICKS) {
            bufferedActionId = action;
            bufferedArg0 = shiftFlag;
            bufferedExpireTick = now + INPUT_BUFFER_WINDOW_TICKS;
            return;
        }
        bufferedActionId = -1;
        InfinityNetwork.sendToServer(action, shiftFlag, 0);
    }

    private static void flushInputBuffer(Minecraft mc, ClientCache.WearerView self) {
        if (bufferedActionId < 0 || mc.level == null) return;
        long now = mc.level.getGameTime();
        if (now > bufferedExpireTick) {
            bufferedActionId = -1;
            return;
        }
        long est = self.estimatedServerNow(now);
        int sel = self.data().selectedOrdinal();
        long readyAt = switch (bufferedActionId) {
            case InfinityNetwork.ACT_CAST_PRIMARY -> self.snapshot().primaryReady()[sel];
            case InfinityNetwork.ACT_CAST_SECONDARY -> self.snapshot().secondaryReady()[sel];
            case InfinityNetwork.ACT_CAST_SNAP -> self.snapshot().snapReadyAt();
            default -> 0L;
        };
        if (mc.player.getAbilities().instabuild || est >= readyAt) {
            int act = bufferedActionId;
            int arg0 = bufferedArg0;
            bufferedActionId = -1;
            bufferedArg0 = 0;
            InfinityNetwork.sendToServer(act, arg0, 0);
        }
    }

    private static void onClientTick(ClientTickEvent.Post e) {
        ClientCache.tick();
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (mc.player.getVehicle() instanceof SanctuaryShipEntity ship && mc.screen == null) {
            ShipClientRender.tickPilotControls(mc, ship);
            drainGauntletKeys();
            return;
        }

        if (mc.screen != null || !canUseGauntletHotkeys(mc.player)) {
            drainKeys();
            return;
        }

        while (KEY_TOGGLE_GAUNTLET.consumeClick()) {
            InfinityNetwork.sendToServer(InfinityNetwork.ACT_TOGGLE_GAUNTLET, 0, 0);
        }

        var self = ClientCache.self();
        boolean worn = self != null && self.snapshot().equipped();
        if (worn) {
            flushInputBuffer(mc, self);
        } else {
            bufferedActionId = -1;
        }

        while (KEY_WHEEL.consumeClick()) {
            if (worn) {
                boolean sockMode = Screen.hasShiftDown() || mc.options.keyShift.isDown();
                InfinityNetwork.sendToServer(InfinityNetwork.ACT_OPEN_WHEEL, sockMode ? 1 : 0, 0);
                mc.setScreen(new InfinityWheelScreen(sockMode));
            }
        }
        while (KEY_PRIMARY.consumeClick()) {
            if (worn) queueOrDispatchSkill(mc, self, InfinityNetwork.ACT_CAST_PRIMARY);
        }
        while (KEY_SECONDARY.consumeClick()) {
            if (worn) queueOrDispatchSkill(mc, self, InfinityNetwork.ACT_CAST_SECONDARY);
        }
        while (KEY_SNAP.consumeClick()) {
            if (worn) queueOrDispatchSkill(mc, self, InfinityNetwork.ACT_CAST_SNAP);
        }
    }

    private static void drainGauntletKeys() {
        while (KEY_WHEEL.consumeClick()) {}
        while (KEY_PRIMARY.consumeClick()) {}
        while (KEY_SECONDARY.consumeClick()) {}
        while (KEY_SNAP.consumeClick()) {}
        while (KEY_TOGGLE_GAUNTLET.consumeClick()) {}
    }

    private static void drainKeys() {
        drainGauntletKeys();
        while (KEY_SHIP_DESCEND.consumeClick()) {}
        while (KEY_SHIP_SHIELD.consumeClick()) {}
        while (KEY_SHIP_RAIN_FIRE.consumeClick()) {}
        while (KEY_SHIP_ESCORT.consumeClick()) {}
        while (KEY_SHIP_WARP.consumeClick()) {}
        while (KEY_SHIP_CAMERA.consumeClick()) {}
    }
}
