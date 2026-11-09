package cn.academy.port.worldboss.client;

import cn.academy.port.worldboss.Clips;
import cn.academy.port.worldboss.FxPacket;
import cn.academy.port.worldboss.WorldBossContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The sole client entry point. Dedicated servers never resolve renderers, cameras, or Minecraft. */
@EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WorldBossClient {
    private static boolean initialized;
    private static ClientLevel observedLevel;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(WorldBossClient::init);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WorldBossContent.BOSS.get(),BossRenderer::new);
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        FxPacket.CLIENT_HANDLER = ClientHooks::fx;
        NeoForge.EVENT_BUS.addListener(WorldBossClient::tick);
        NeoForge.EVENT_BUS.addListener(ModernFx::render);
        NeoForge.EVENT_BUS.addListener(Shake::angles);
        NeoForge.EVENT_BUS.addListener(Cinematic::tick);
        NeoForge.EVENT_BUS.addListener(Cinematic::frame);
        NeoForge.EVENT_BUS.addListener(Cinematic::fov);
        NeoForge.EVENT_BUS.addListener(Shake::fov);
        NeoForge.EVENT_BUS.addListener(Cinematic::hand);
        NeoForge.EVENT_BUS.addListener(Cinematic::layers);
        NeoForge.EVENT_BUS.addListener(Cinematic::move);
        NeoForge.EVENT_BUS.addListener(Cinematic::interact);
        NeoForge.EVENT_BUS.addListener(EnvClient::tick);
        NeoForge.EVENT_BUS.addListener(EnvClient::fogColor);
        NeoForge.EVENT_BUS.addListener(EnvClient::fog);
        NeoForge.EVENT_BUS.addListener(EnvClient::overlay);
        NeoForge.EVENT_BUS.addListener(Cinematic::overlay);
    }

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level != observedLevel) {
            observedLevel = mc.level;
            Cinematic.clear();
            EnvClient.clear();
            ModernFx.clear();
            Shake.clear();
        }
        if (mc.level == null || mc.isPaused()) return;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof WorldBossContent.Boss boss) || boss.isRemoved() || !boss.isAlive()) continue;
            // START also changes for consecutive instances of the same clip (including idle and walk loops).
            if (boss.seenClip != boss.clip() || boss.seenStart != boss.start()) ClientHooks.clipChanged(boss);
            int age = Math.max(0, (int) (mc.level.getGameTime() - boss.start()));
            ClientHooks.bossTick(boss, age, Clips.get(boss.clip()));
        }
    }

    private WorldBossClient() {}
}
