package cn.academy.port.qa;

import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** QA-only observation of chime requests before the client resolves sound assets. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class TrainCueProbe {
    private static final AtomicInteger COUNT=new AtomicInteger();
    private TrainCueProbe() {}
    @SubscribeEvent public static void sound(PlaySoundEvent event) {
        var sound=event.getSound();
        if(sound!=null && sound.getLocation().toString().equals("minecraft:block.note_block.chime")) COUNT.incrementAndGet();
    }
    static void reset() { COUNT.set(0); }
    static int count() { return COUNT.get(); }
}
