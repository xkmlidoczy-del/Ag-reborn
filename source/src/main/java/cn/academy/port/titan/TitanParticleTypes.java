package cn.academy.port.titan;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TitanParticleTypes {
    private TitanParticleTypes() {}
    public enum Shape { STARBURST, SIGIL, SLASH, WISP, CROWN, MOTIF, OUTLINE }
    public static final DeferredRegister<ParticleType<?>> TYPES=DeferredRegister.create(Registries.PARTICLE_TYPE,TitanBlocks.MODID);
    public static final Map<String,DeferredHolder<ParticleType<?>,SimpleParticleType>> ALL=new LinkedHashMap<>();
    static {
        for (String id:TitanProfiles.all().keySet()) for (Shape shape:Shape.values()) {
            String name=id+"_"+shape.name().toLowerCase(java.util.Locale.ROOT);
            ALL.put(name,TYPES.register(name,()->new SimpleParticleType(true)));
        }
    }
    public static SimpleParticleType get(String species,Shape shape) {
        return ALL.get(species+"_"+shape.name().toLowerCase(java.util.Locale.ROOT)).get();
    }
}
