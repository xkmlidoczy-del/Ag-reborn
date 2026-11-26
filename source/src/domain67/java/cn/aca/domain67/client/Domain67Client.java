package cn.aca.domain67.client;

import cn.aca.domain67.AcaDomain67;
import cn.aca.domain67.CircusMegaphoneItem;
import cn.aca.domain67.Domain67Network;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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

@Mod(value = AcaDomain67.ID, dist = Dist.CLIENT)
public final class Domain67Client {
    private static final String CATEGORY = "key.categories.aca_domain67";

    public static final IKeyConflictContext DOMAIN67_CONTEXT = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            if (!KeyConflictContext.IN_GAME.isActive()) return false;
            var mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            if (isAcademyGun(mc.player.getMainHandItem()) || isAcademyGun(mc.player.getOffhandItem())) return false;
            return CircusMegaphoneItem.hasMegaphone(mc.player);
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyMapping KEY_DOMAIN_EXPAND = key("domain_expand", GLFW.GLFW_KEY_LEFT_BRACKET);
    public static final KeyMapping KEY_CIRCUS_BROADCAST = key("circus_broadcast", GLFW.GLFW_KEY_RIGHT_BRACKET);
    public static final KeyMapping KEY_LOG_FINISHER = key("log_finisher", GLFW.GLFW_KEY_SEMICOLON);
    public static final KeyMapping KEY_BENTO_CAMEO = key("bento_cameo", GLFW.GLFW_KEY_APOSTROPHE);

    private static boolean isAcademyGun(ItemStack stack) {
        return !stack.isEmpty() && "cn.academy.port.fleet.GunItem".equals(stack.getItem().getClass().getName());
    }

    private static KeyMapping key(String name, int glfwKey) {
        return new KeyMapping("key.aca_domain67." + name, DOMAIN67_CONTEXT,
                InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    public Domain67Client(IEventBus modBus) {
        Domain67Network.clientReceiver = new Domain67Network.ClientReceiver() {
            @Override
            public void onDomainSync(Domain67Network.DomainSyncPayload payload) {
                Domain67ClientCache.handleDomainSync(payload);
            }

            @Override
            public void onVfxCue(Domain67Network.VfxCuePayload cue) {
                var mc = Minecraft.getInstance();
                if (mc.player == null || mc.level == null) return;
                Domain67PostShader.onCue(cue, mc.player, Domain67ClientCache.ticks());
                Domain67ClientCache.handleVfxCue(cue, mc.player.getUUID());
            }
        };

        modBus.addListener(Domain67Client::registerKeys);
        modBus.addListener(Domain67Client::registerRenderers);
        modBus.addListener(Domain67Client::registerItemExtensions);
        modBus.addListener(Domain67Client::registerGui);
        modBus.addListener(Domain67Client::registerReload);
        modBus.addListener(Domain67CoreShader::register);

        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(Domain67Client::onClientTick);
        gameBus.addListener(Domain67Client::onLogout);
        gameBus.addListener(Domain67WorldRenderer::onRenderLevel);
        gameBus.addListener(Domain67WorldRenderer::onRenderHand);
        gameBus.addListener(Domain67WorldRenderer::onCameraShake);
        gameBus.addListener(Domain67PostShader::onRenderLevel);
        gameBus.addListener(Domain67PostShader::onComputeFov);
        gameBus.addListener(Domain67PostShader::onComputeFogColor);
        gameBus.addListener(Domain67PostShader::onRenderFog);
    }

    private static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(KEY_DOMAIN_EXPAND);
        e.register(KEY_CIRCUS_BROADCAST);
        e.register(KEY_LOG_FINISHER);
        e.register(KEY_BENTO_CAMEO);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(AcaDomain67.CURSED_LOG.get(), Domain67WorldRenderer.CursedLogRenderer::new);
    }

    private static void registerItemExtensions(RegisterClientExtensionsEvent e) {
        IClientItemExtensions item3dExt = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return Domain67ItemRenderer.get();
            }
        };
        e.registerItem(item3dExt,
                AcaDomain67.CIRCUS_MEGAPHONE_67.get(),
                AcaDomain67.CHOCO_SAUSAGE_NOODLES.get(),
                AcaDomain67.NO_HEARTBURN_BENTO.get());
    }

    private static void registerGui(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, AcaDomain67.id("hud"), Domain67SpatialUi::renderOverlay);
    }

    private static void registerReload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((ResourceManagerReloadListener) rm -> Domain67PostShader.reload());
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        Domain67ClientCache.clearStateOnly();
        Domain67PostShader.reset();
    }

    private static void onClientTick(ClientTickEvent.Post e) {
        Domain67ClientCache.tick();
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            Domain67ClientCache.updateCameraSpring(true, mc.player.getYRot(), mc.player.getXRot(),
                    mc.player.getDeltaMovement().horizontalDistance());
        } else {
            Domain67ClientCache.updateCameraSpring(false, 0f, 0f, 0.0);
        }

        if (mc.player == null || mc.screen != null || !DOMAIN67_CONTEXT.isActive()) {
            while (KEY_DOMAIN_EXPAND.consumeClick()) {}
            while (KEY_CIRCUS_BROADCAST.consumeClick()) {}
            while (KEY_LOG_FINISHER.consumeClick()) {}
            while (KEY_BENTO_CAMEO.consumeClick()) {}
            return;
        }
        if (KEY_DOMAIN_EXPAND.consumeClick()) {
            while (KEY_DOMAIN_EXPAND.consumeClick()) {}
            Domain67Network.sendAction(Domain67Network.ACT_EXPAND_DOMAIN, 0);
        }
        if (KEY_CIRCUS_BROADCAST.consumeClick()) {
            while (KEY_CIRCUS_BROADCAST.consumeClick()) {}
            Domain67Network.sendAction(Domain67Network.ACT_CIRCUS_BROADCAST, 0);
        }
        if (KEY_LOG_FINISHER.consumeClick()) {
            while (KEY_LOG_FINISHER.consumeClick()) {}
            var dom = Domain67ClientCache.primaryDomain(mc.player.position());
            if (dom == null || dom.isCollapsing()) {
                Domain67ClientCache.pushThrottledToast("need_dom", keyLabel(KEY_DOMAIN_EXPAND),
                        "ui.aca_domain67.toast.need_domain.title",
                        "ui.aca_domain67.toast.need_domain.sub", 0, 0xFFE5B85C, 28);
                return;
            }
            Domain67Network.sendAction(Domain67Network.ACT_LOG_FINISHER, 0);
        }
        if (KEY_BENTO_CAMEO.consumeClick()) {
            while (KEY_BENTO_CAMEO.consumeClick()) {}
            var dom = Domain67ClientCache.primaryDomain(mc.player.position());
            if (dom == null || dom.isCollapsing()) {
                Domain67ClientCache.pushThrottledToast("need_dom", keyLabel(KEY_DOMAIN_EXPAND),
                        "ui.aca_domain67.toast.need_domain.title",
                        "ui.aca_domain67.toast.need_domain.sub", 0, 0xFFE5B85C, 28);
                return;
            }
            Domain67Network.sendAction(Domain67Network.ACT_BENTO_CAMEO, 0);
        }
    }

    static String keyLabel(KeyMapping binding) {
        if (binding.getKeyModifier() == net.neoforged.neoforge.client.settings.KeyModifier.NONE
                && binding.getKey().getType() == InputConstants.Type.KEYSYM) {
            String printable = GLFW.glfwGetKeyName(binding.getKey().getValue(), 0);
            if (printable != null) return printable.toUpperCase(java.util.Locale.ROOT);
        }
        return binding.getTranslatedKeyMessage().getString();
    }
}
