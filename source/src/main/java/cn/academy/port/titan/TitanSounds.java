package cn.academy.port.titan;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Species-specific positional events. Files and two variations are in sounds.json. */
public final class TitanSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,TitanBlocks.MODID);
    public static final String[] EVENTS={"swing_a","swing_b","hit_a","hit_b","charge_a","charge_b",
        "skill_a","skill_b","roar","hurt","death","step"};
    private static final Map<String,DeferredHolder<SoundEvent,SoundEvent>> VALUES=new LinkedHashMap<>();
    static {
        for(String id:TitanBlocks.IDS)for(String event:EVENTS) {
            String key=id+"."+event;
            VALUES.put(key,SOUNDS.register(key,()->SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(TitanBlocks.MODID,key))));
        }
    }
    private TitanSounds() {}
    public static SoundEvent sound(String id,String event) {
        var value=VALUES.get(id+"."+event);
        if(value==null)throw new IllegalArgumentException("Unknown titan sound "+id+"."+event);
        return value.get();
    }
    public static void play(TitanEntity source,String event,Vec3 position,float volume) {
        if(source.isSilent() || !(source.level() instanceof ServerLevel level))return;
        float pitch=.96f+source.getRandom().nextFloat()*.08f;
        level.playSound(null,position.x,position.y,position.z,sound(source.species(),event),
            SoundSource.HOSTILE,volume,pitch);
    }
}
