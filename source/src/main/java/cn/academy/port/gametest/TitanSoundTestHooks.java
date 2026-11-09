package cn.academy.port.gametest;
import cn.academy.port.titan.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/** Only compiled into the opt-in game test build. */
@EventBusSubscriber(modid=TitanBlocks.MODID)
public final class TitanSoundTestHooks {
    private record Note(String id,Vec3 position) {}
    private static final List<Note> NOTES=new CopyOnWriteArrayList<>();
    @SubscribeEvent public static void onSound(PlayLevelSoundEvent.AtPosition event) {
        if(!event.getLevel().isClientSide() && event.getSound()!=null) {
            var id=event.getSound().value().getLocation();
            if(id.getNamespace().equals(TitanBlocks.MODID))NOTES.add(new Note(id.getPath(),event.getPosition()));
        }
    }
    public static long count(String id,Vec3 near) {
        return NOTES.stream().filter(n->n.id().equals(id) && n.position().distanceToSqr(near)<4).count();
    }
}
