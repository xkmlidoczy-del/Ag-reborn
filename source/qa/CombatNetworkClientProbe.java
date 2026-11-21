package qa.combat;

import cn.aca.infinity.InfinityNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "academy_network_probe", value = Dist.CLIENT)
public final class CombatNetworkClientProbe {
  private static boolean hooked, ready;
  private static int capture = -1, warmFrames;
  private static java.util.UUID target;
  private static float seen = -1;

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;
    if (!hooked && InfinityNetwork.clientReceiver != null) {
      var rendering = InfinityNetwork.clientReceiver;
      InfinityNetwork.clientReceiver =
          new InfinityNetwork.ClientReceiver() {
            public void onWearer(InfinityNetwork.WearerSnapshot msg, Player viewer) {
              rendering.onWearer(msg, viewer);
            }

            public void onEntityMark(InfinityNetwork.EntityMarkPayload msg, Player viewer) {
              rendering.onEntityMark(msg, viewer);
            }

            public void onVfxCue(InfinityNetwork.VfxCuePayload msg, Player viewer) {
              rendering.onVfxCue(msg, viewer);
              if (msg.cueId() == InfinityNetwork.CUE_MIND_BEAM) {
                System.out.println(
                    "DUAL_VISUAL "
                        + msg.actor()
                        + " "
                        + msg.cueId()
                        + " "
                        + msg.tick()
                        + " "
                        + msg.from()
                        + " "
                        + msg.to());
                capture = 0;
              }
            }
          };
      hooked = true;
    }
    for (var entity : mc.level.entitiesForRendering()) {
      if (entity instanceof LivingEntity body
          && body.hasCustomName()
          && body.getCustomName().getString().equals("ACA-health")
          && body.getHealth() != seen) {
        seen = body.getHealth();
        target = body.getUUID();
        System.out.println("DUAL_HEALTH " + body.getUUID() + " " + seen);
      }
    }
  }

  @SubscribeEvent
  public static void afterFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null || !hooked || target == null) return;
    if (!ready && seen == 1_000_000F && ++warmFrames >= 40) {
      var mark =
          java.nio.file.Path.of(
              System.getProperty("aca.qa.net.root"),
              mc.player.getGameProfile().getName() + ".ready");
      try {
        java.nio.file.Files.writeString(mark, target.toString());
        ready = true;
      } catch (java.io.IOException failure) {
        throw new java.io.UncheckedIOException(failure);
      }
    }
    if (capture < 0 || capture >= 7) return;
    var name = capture == 3 ? "combat.png" : "combat-" + capture + ".png";
    capture++;
    Screenshot.grab(
        mc.gameDirectory,
        name,
        mc.getMainRenderTarget(),
        message -> System.out.println("DUAL_IMAGE " + message.getString()));
  }
}
