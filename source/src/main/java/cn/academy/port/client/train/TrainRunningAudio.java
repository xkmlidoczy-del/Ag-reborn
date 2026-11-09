package cn.academy.port.client.train;

import cn.academy.port.train.TrainCarEntity;
import cn.academy.port.train.TrainLine;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** One positional rolling loop for the middle car, active only while its real service is moving. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class TrainRunningAudio {
    private static final Map<UUID, Rolling> ACTIVE = new HashMap<>();
    private static ClientLevel world;

    private TrainRunningAudio() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (world != mc.level) {
            ACTIVE.values().forEach(sound -> { sound.finish(); mc.getSoundManager().stop(sound); });
            ACTIVE.clear(); world = mc.level;
        }
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        var near = mc.level.getEntitiesOfClass(TrainCarEntity.class, mc.player.getBoundingBox().inflate(96),
                car -> car.role() == 1 && moving(car.servicePhase()));
        var iter = ACTIVE.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            if (entry.getValue().car.isRemoved() || !near.contains(entry.getValue().car)) {
                entry.getValue().finish(); mc.getSoundManager().stop(entry.getValue()); iter.remove();
            }
        }
        for (var car : near) if (!ACTIVE.containsKey(car.getUUID())) {
            var sound = new Rolling(car, mc.level);
            ACTIVE.put(car.getUUID(), sound);
            mc.getSoundManager().play(sound);
        }
    }

    private static boolean moving(TrainLine.Phase phase) {
        return phase == TrainLine.Phase.DEPARTING || phase == TrainLine.Phase.CRUISE || phase == TrainLine.Phase.APPROACHING;
    }

    private static final class Rolling extends AbstractTickableSoundInstance {
        final TrainCarEntity car;
        final ClientLevel level;
        double lastX, lastZ;
        float speed;

        Rolling(TrainCarEntity car, ClientLevel level) {
            super(SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, RandomSource.create());
            this.car = car; this.level = level;
            looping = true; delay = 0; volume = .02f; pitch = .7f;
            x = lastX = car.getX(); y = car.getY(); z = lastZ = car.getZ();
        }

        @Override public void tick() {
            if (car.isRemoved() || Minecraft.getInstance().level != level || !moving(car.servicePhase())) { stop(); return; }
            double dx = car.getX() - lastX, dz = car.getZ() - lastZ;
            float target = (float) Math.min(1, Math.hypot(dx, dz) / (TrainLine.VMAX / 20));
            speed += (target - speed) * .18f;
            volume = .04f + .28f * speed;
            pitch = .68f + .34f * speed;
            x = lastX = car.getX(); y = car.getY(); z = lastZ = car.getZ();
        }

        void finish() { stop(); }
    }
}
