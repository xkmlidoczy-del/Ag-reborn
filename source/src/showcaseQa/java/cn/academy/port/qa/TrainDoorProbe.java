package cn.academy.port.qa;

import cn.academy.port.train.TrainCarEntity;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in animation trace; excluded from the distributable mod. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class TrainDoorProbe {
    private static final boolean ENABLED = Boolean.getBoolean("academy.qa.doorsTrace");
    private static final Map<Integer, Float> LAST = new HashMap<>();
    private TrainDoorProbe() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        for (var car : mc.level.getEntitiesOfClass(TrainCarEntity.class, mc.player.getBoundingBox().inflate(100), c -> c.role() == 1)) {
            float progress = car.doorOpen(1);
            Float prior = LAST.put(car.getId(), progress);
            if (prior == null || Math.abs(progress - prior) > .0001f)
                LogUtils.getLogger().info("QA_DOOR_TRACE airport={} id={} tick={} phase={} amount={}", car.airport(), car.getId(),
                        car.tickCount, car.servicePhase(), progress);
        }
    }
}
