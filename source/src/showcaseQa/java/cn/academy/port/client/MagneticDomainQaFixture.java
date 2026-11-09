package cn.academy.port.client;

import cn.academy.port.highpower.MagneticDomain;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * QA-only (-PshowcaseQA): feeds the production receiver with heartbeat frames for a non-local armor stand so the
 * magnetic domain renders without learned skills. Enabled by -Dacademy.qa.magneticDomain=true; never in the JAR.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MagneticDomainQaFixture {
    private static final boolean ENABLED = Boolean.getBoolean("academy.qa.magneticDomain");
    private static long sequence;

    private MagneticDomainQaFixture() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.tickCount % 4 != 0) return;
        Entity stand = null;
        for (Entity e : mc.level.entitiesForRendering()) if (e.getType() == EntityType.ARMOR_STAND) { stand = e; break; }
        if (stand == null) return;
        Vec3 center = stand.position().add(0, 1, 0);
        MagneticDomainEffects.receive(new MagneticDomain.Visual(stand.getId(), stand.getUUID(), 1, 1, 1, ++sequence, 0, center, new Vec3(0, 0, 1), center, false, 0));
    }
}
