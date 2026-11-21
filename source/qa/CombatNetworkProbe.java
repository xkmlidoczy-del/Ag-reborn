package qa.combat;

import cn.aca.infinity.*;
import cn.academy.port.api.NativeCombatDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Dedicated loopback QA. Two independent game clients record a cast and a separate HP probe. */
@Mod("academy_network_probe")
public final class CombatNetworkProbe {
  private int age;
  private boolean cast;
  private Husk subject, puppet;

  public CombatNetworkProbe() {
    NeoForge.EVENT_BUS.addListener(this::tick);
  }

  private void tick(ServerTickEvent.Post event) {
    var server = event.getServer();
    if (!server.isDedicatedServer()) return;
    var host = server.getPlayerList().getPlayerByName("AcaCaster");
    var viewer = server.getPlayerList().getPlayerByName("AcaObserver");
    if (host == null || viewer == null || ++age > 2400) return;
    var level = server.overworld();
    if (age == 5) {
      for (int x = -8; x <= 8; x++)
        for (int z = -4; z <= 16; z++)
          level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState());
      level.setDayTime(18000L);
      host.setGameMode(GameType.SURVIVAL);
      viewer.setGameMode(GameType.SPECTATOR);
      host.teleportTo(level, .5, 100, .5, 0, 0);
      viewer.teleportTo(level, 4.5, 101.5, -.5, 23, 8);
      puppet = mob(level, "ACA-control", .5, 7.5);
      subject = mob(level, "ACA-health", 36.5, 7.5);
      System.out.println(
          "DUAL_READY " + host.getUUID() + " " + viewer.getUUID() + " " + subject.getUUID());
    }
    if (!cast && age >= 100 && clientReady("AcaCaster") && clientReady("AcaObserver")) {
      cast = true;
      var glove = AcaInfinity.wearer(host);
      glove.gauntlet = InfinityGauntletItem.createFullStack();
      glove.updateData(GauntletData.FULL.withSelected(Stone.MIND));
      StoneAbilities.castPrimary(host);
      var result =
          NativeCombatDamage.hurtDetailed(subject, host.damageSources().playerAttack(host), 75000);
      System.out.println(
          "DUAL_COMMIT "
              + result.before()
              + " "
              + result.after()
              + " "
              + result.affected()
              + " owned="
              + host.getUUID().equals(AcaInfinity.status(puppet).mindMaster));
    }
  }

  private static boolean clientReady(String player) {
    return java.nio.file.Files.isRegularFile(
        java.nio.file.Path.of(System.getProperty("aca.qa.net.root"), player + ".ready"));
  }

  private static Husk mob(ServerLevel level, String label, double x, double z) {
    Husk mob = EntityType.HUSK.create(level);
    mob.setNoAi(true);
    mob.setNoGravity(true);
    mob.setPersistenceRequired();
    mob.setCustomName(Component.literal(label));
    mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000_000);
    mob.getAttribute(Attributes.ARMOR).setBaseValue(0);
    mob.setHealth(mob.getMaxHealth());
    mob.moveTo(x, 100, z);
    level.addFreshEntity(mob);
    return mob;
  }
}
