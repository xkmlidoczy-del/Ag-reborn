/* AcademyCraft1.0.7 DispatcherAch/manual hooks, GPLv3. See NOTICE. */
package cn.academy.port.achievements;

import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.fusion.MatterUnitHarvestEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Awards only at actual server event/success boundaries; inventory and saved marker scans never award. */
@EventBusSubscriber(modid="academy")
public final class ClassicAchievements {
    private ClassicAchievements(){}
    public static boolean earned(ServerPlayer player,String id){
        var e=ClassicAchievementCatalog.get(id);if(e==null)return false;
        var holder=player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("academy",e.advancementPath()));
        return holder!=null&&player.getAdvancements().getOrStartProgress(holder).isDone();
    }
    /** Vanilla advancements don't enforce parent gates, so retain 1.7.10 StatFileWriter's gate here. */
    public static boolean trigger(ServerPlayer player,String id){
        if(player==null||!player.server.isSameThread())return false;
        var e=ClassicAchievementCatalog.get(id);
        if(!ClassicAchievementCatalog.canAward(e,key->earned(player,key)))return false;
        var holder=player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("academy",e.advancementPath()));
        if(holder==null||!player.getAdvancements().award(holder,e.criterion()))return false;
        sync(player,id);return true;
    }
    public static void bind(ServerPlayer player,AbilityProgress state){
        cn.academy.port.api.AbilityCategoryLifecycle.bind(player,state);
        cn.academy.port.api.AbilityProgressLifecycle.bind(player,state);
    }
    @SubscribeEvent public static void learned(cn.academy.port.api.SkillLearnEvent e){
        if(e.getEntity() instanceof ServerPlayer p)for(var entry:ClassicAchievementCatalog.matching(ClassicAchievementCatalog.Kind.LEARN,e.skill,0))if(entry.page().equals(e.category))trigger(p,entry.id());
    }
    @SubscribeEvent public static void level(cn.academy.port.api.LevelChangeEvent e){if(e.getEntity() instanceof ServerPlayer p&&e.state.hasCategory())event(p,ClassicAchievementCatalog.Kind.LEVEL,e.state.category,e.state.level);}
    private static void event(ServerPlayer player,ClassicAchievementCatalog.Kind kind,String key,int value){
        for(var entry:ClassicAchievementCatalog.matching(kind,key,value))trigger(player,entry.id());
    }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent e){
        if(e.getEntity() instanceof ServerPlayer p&&!e.getCrafting().isEmpty())event(p,ClassicAchievementCatalog.Kind.CRAFT,ClassicAchievementCatalog.craftKey(BuiltInRegistries.ITEM.getKey(e.getCrafting().getItem()).toString()),0);
    }
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Post e){
        if(e.getPlayer() instanceof ServerPlayer p&&!e.getOriginalStack().isEmpty()){
            var stack=e.getOriginalStack();String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            // Source matches the low-crystal item's exact metadata zero, but does not inspect NBT/count.
            if(id.equals("academy:crystal_low")&&stack.getDamageValue()==0)event(p,ClassicAchievementCatalog.Kind.PICKUP,"crystal_low",0);
        }
    }
    @SubscribeEvent public static void matter(MatterUnitHarvestEvent e){if(e.player instanceof ServerPlayer p)event(p,ClassicAchievementCatalog.Kind.MATTER,e.material,0);}
    /** Keep the original pages accurate if an administrator grants/revokes a native backing criterion. */
    @SubscribeEvent public static void progress(net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementProgressEvent e){
        if(e.getEntity() instanceof ServerPlayer p&&e.getAdvancement().id().getNamespace().equals("academy")&&ClassicAchievementCatalog.ALL.stream().anyMatch(entry->entry.advancementPath().equals(e.getAdvancement().id().getPath())))sync(p,"");
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p,"");}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p,"");}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p,"");}
    /** Advancement awards are vanilla UUID-backed; preserve the original teleport counter across player clone. */
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){
        if(e.getOriginal() instanceof ServerPlayer old&&e.getEntity() instanceof ServerPlayer p){
            var from=old.getPersistentData();var to=p.getPersistentData();
            if(from.contains("ac_tpcount"))to.putInt("ac_tpcount",from.getInt("ac_tpcount"));
            for(String key:java.util.List.of("ac_teleporter_ignore_barrier","ac_teleporter_mastery","ac_teleporter_flashing","ac_teleporter_critical_attack"))if(from.contains(key))to.putBoolean(key,from.getBoolean(key));
        }
    }
    public static CompoundTag snapshot(ServerPlayer p,String awarded){
        var tag=new CompoundTag();tag.putString("kind","classic_achievements");tag.putString("award",awarded);
        var earned=new CompoundTag();for(var e:ClassicAchievementCatalog.ALL)if(earned(p,e.id()))earned.putBoolean(e.id(),true);
        tag.put("earned",earned);return tag;
    }
    private static void sync(ServerPlayer player,String awarded){if(!player.hasDisconnected())PacketDistributor.sendToPlayer(player,new AcademyNetwork.ClientData(snapshot(player,awarded)));}
}
