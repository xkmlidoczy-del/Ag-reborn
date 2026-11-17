package dev.acafauna.core;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.*;
public final class FaunaSounds {
    public static final String[] EVENTS={"ambient","alert","hurt","death","step","attack","eat","special"};
    public static final DeferredRegister<SoundEvent> REGISTER=DeferredRegister.create(Registries.SOUND_EVENT,FaunaCore.ID);
    private static final Map<String,DeferredHolder<SoundEvent,SoundEvent>> SOUNDS=new LinkedHashMap<>();
    static {
        for(var p:Species.all())for(var event:EVENTS) {
            String key=p.id()+"."+event;
            SOUNDS.put(key,REGISTER.register(key,()->SoundEvent.createVariableRangeEvent(p.resource(key))));
        }
    }
    public static SoundEvent get(Species p,String event){return SOUNDS.get(p.id()+"."+event).get();}
    private FaunaSounds(){}
}
