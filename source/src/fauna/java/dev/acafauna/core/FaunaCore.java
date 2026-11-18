package dev.acafauna.core;

import java.util.*;
import dev.acafauna.entity.FaunaAnimal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.*;

/** No AcademyCraft references. A future host calls register once with its mod bus. */
public final class FaunaCore {
    public static final String ID="aca_fauna";
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,ID);
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,ID);
    public static final Map<String,DeferredHolder<EntityType<?>,EntityType<FaunaAnimal>>> TYPES=new LinkedHashMap<>();
    public static final DeferredHolder<Item,Item> INSECT_FEED=ITEMS.register("insect_feed",()->new Item(new Item.Properties()));
    static {
        for(var p:Species.all()) {
            var type=ENTITIES.register(p.id(),()->EntityType.Builder.<FaunaAnimal>of(
                (t,l)->FaunaAnimal.create(t,l,p),p.aquatic()?MobCategory.WATER_CREATURE:MobCategory.CREATURE)
                .sized(p.width(),p.height()).clientTrackingRange(p.width()>1.5?12:8).updateInterval(3)
                .build(ID+":"+p.id()));
            TYPES.put(p.id(),type);
            ITEMS.register(p.id()+"_spawn_egg",()->new DeferredSpawnEggItem(type,p.color(),p.color()^0x536247,new Item.Properties()));
        }
    }
    public static void register(IEventBus bus) {
        ENTITIES.register(bus);ITEMS.register(bus);FaunaSounds.REGISTER.register(bus);
        bus.addListener(FaunaCore::attributes);bus.addListener(FaunaCore::creative);bus.addListener(FaunaCore::spawnRules);
    }
    private static void attributes(EntityAttributeCreationEvent e) {
        for(var p:Species.all())e.put(TYPES.get(p.id()).get(),Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH,p.hp()).add(Attributes.MOVEMENT_SPEED,p.speed())
            .add(Attributes.FLYING_SPEED,p.speed()*1.5).add(Attributes.FOLLOW_RANGE,24)
            .add(Attributes.ATTACK_DAMAGE,p.predator()?Math.max(2,p.damage()):p.damage()).build());
    }
    private static void creative(BuildCreativeModeTabContentsEvent e) {
        if(e.getTabKey()==CreativeModeTabs.SPAWN_EGGS)
            TYPES.keySet().forEach(id->e.accept(ITEMS.getEntries().stream().filter(x->x.getId().getPath().equals(id+"_spawn_egg")).findFirst().orElseThrow().get()));
        if(e.getTabKey()==CreativeModeTabs.INGREDIENTS)e.accept(INSECT_FEED.get());
    }
    private static void spawnRules(RegisterSpawnPlacementsEvent e) {
        for (var p : Species.all()) {
            var habitat = TagKey.create(Registries.BIOME, p.resource(p.id()));
            e.register(TYPES.get(p.id()).get(),p.aquatic()?SpawnPlacementTypes.IN_WATER:SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(type,world,reason,pos,random)-> {
                if(reason!=MobSpawnType.NATURAL&&reason!=MobSpawnType.CHUNK_GENERATION)return true;
                var level = world.getLevel();
                if(!FaunaConfig.NATURAL.get() || !Level.OVERWORLD.equals(level.dimension())
                        || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                        || !world.getBiome(pos).is(habitat)) return false;
                float clearance=Math.max(p.width(),p.clearance());
                var box=new AABB(pos.getX()+.5-clearance*.5,pos.getY(),pos.getZ()+.5-clearance*.5,
                    pos.getX()+.5+clearance*.5,pos.getY()+p.height(),pos.getZ()+.5+clearance*.5);
                if(box.minY<=world.getMinBuildHeight()||box.maxY>=world.getMaxBuildHeight())return false;
                int half=(int)Math.ceil(clearance*.5);
                for(int x=-half;x<=half;x++)for(int z=-half;z<=half;z++)
                    if(!world.hasChunkAt(pos.offset(x,0,z)))return false;
                if(!world.getWorldBorder().isWithinBounds(box)||!world.noCollision(box))return false;
                if(reason==MobSpawnType.NATURAL && level.getEntitiesOfClass(FaunaAnimal.class,
                        new AABB(pos).inflate(32)).size()>=FaunaConfig.LOCAL_CAP.get()) return false;
                if(p.aquatic()) {
                    int depth=Math.max((int)Math.ceil(p.height()),p.id().equals("orca")?4:2);
                    for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX-.0001);x++)
                        for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ-.0001);z++)
                            for(int y=0;y<depth;y++)if(!world.getFluidState(new BlockPos(x,pos.getY()+y,z)).is(FluidTags.WATER))return false;
                    return world.getFluidState(pos.below()).is(FluidTags.WATER);
                }
                return world.getRawBrightness(pos,0)>7&&world.getBlockState(pos.below()).isSolid()
                    &&world.getFluidState(pos).isEmpty();
            },RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
    private FaunaCore(){}
}
