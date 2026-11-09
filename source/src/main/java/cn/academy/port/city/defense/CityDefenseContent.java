package cn.academy.port.city.defense;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.*;

/** Ten officers is a gameplay invariant; the limit bounds simultaneous responses, not team size. */
public final class CityDefenseContent {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,"academy");
    public static final DeferredHolder<EntityType<?>,EntityType<GuardianEntity>> GUARD=TYPES.register("city_guardian",
        ()->EntityType.Builder.<GuardianEntity>of(GuardianEntity::new,MobCategory.MISC).sized(.6f,1.8f)
            .clientTrackingRange(12).updateInterval(2).build("academy:city_guardian"));
    public static final DeferredHolder<EntityType<?>,EntityType<O5OperativeEntity>> O5=TYPES.register("o5_operative",
        ()->EntityType.Builder.<O5OperativeEntity>of(O5OperativeEntity::new,MobCategory.MISC).sized(.6f,1.8f)
            .clientTrackingRange(16).updateInterval(1).fireImmune().build("academy:o5_operative"));
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue MAX_TEAMS,LEVEL,LIFETIME,O5_LOSSES;
    public static final ModConfigSpec.DoubleValue O5_LOW_HEALTH;
    static {
        var b=new ModConfigSpec.Builder();
        ENABLED=b.comment("Respond to actual civilian damage in academy:story_city_map.").define("enabled",true);
        MAX_TEAMS=b.comment("Maximum concurrent ten-officer responses per city dimension.").defineInRange("maxTeams",2,1,4);
        LEVEL=b.comment("Guardian uses the existing player HP/shield curve at this level. No player stats are changed.").defineInRange("guardianLevel",3,1,5);
        LIFETIME=b.comment("Maximum duty time; the team never forces chunks to stay loaded.").defineInRange("dutyTicks",3600,600,12000);
        O5_LOSSES=b.comment("Confirmed officer deaths required for one five-member O5 reinforcement. Unloaded officers are not casualties.").defineInRange("o5Casualties",3,1,10);
        O5_LOW_HEALTH=b.comment("O5 also responds if every surviving regular officer is at or below this fraction. All survivors must be loaded.").defineInRange("o5LowHealthFraction",.30,.05,.90);
        SPEC=b.build();
    }
    public static void register(IEventBus bus,ModContainer container){
        TYPES.register(bus);cn.academy.port.city.defense.armor.DefenseArmor.register(bus);bus.addListener(CityDefenseContent::attributes);
        container.registerConfig(ModConfig.Type.SERVER,SPEC,"academy-city-defense-server.toml");
    }
    private static void attributes(EntityAttributeCreationEvent e){e.put(GUARD.get(),Mob.createMobAttributes()
        .add(Attributes.MOVEMENT_SPEED,.30).add(Attributes.FOLLOW_RANGE,64).add(Attributes.ATTACK_DAMAGE,3).build());
        e.put(O5.get(),Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED,.34).add(Attributes.FOLLOW_RANGE,96).add(Attributes.ATTACK_DAMAGE,1).add(Attributes.SCALE,2).add(Attributes.KNOCKBACK_RESISTANCE,1).build());}
}
