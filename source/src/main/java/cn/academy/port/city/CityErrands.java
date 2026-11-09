package cn.academy.port.city;

import java.util.List;
import cn.academy.port.city.CityServicePlan.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Per-player work orders; repeated contracts keep their own completion count and game-time cooldown. */
final class CityErrands {
    static final String DATA="academy:city_errands";
    record Errand(String id,Role from,Role to,int reward,BlockPos survey,int wheat,int cooldown,int rideFrom,int rideTo,String item) {
        Errand(String id,Role from,Role to,int reward,BlockPos survey,int wheat){this(id,from,to,reward,survey,wheat,0,-1,-1,"minecraft:wheat");}
        Component name() {return Component.translatable("academy.city.quest."+id+".name");}
        boolean repeatable(){return cooldown>0;}
        ItemStack requiredItem(){return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(item)));}
    }
    static final List<Errand> ALL=List.of(
        new Errand("course",Role.MENTOR,Role.LIBRARIAN,5,null,0),
        new Errand("stock",Role.EXCHANGE,Role.WEST,7,null,0),
        new Errand("clinic",Role.CAMPUS_MEDIC,Role.FIELD_MEDIC,8,null,0),
        new Errand("health",Role.FIELD_MEDIC,Role.LIBRARIAN,8,null,0),
        new Errand("wind",Role.EXCHANGE,Role.ENGINEER,12,null,0),
        new Errand("calibration",Role.ENGINEER,Role.RESEARCH_CONTACT,10,null,0),
        new Errand("exhibit",Role.RESEARCH_CONTACT,Role.CURATOR,9,null,0),
        new Errand("sports",Role.CURATOR,Role.COACH,8,null,0),
        new Errand("rations",Role.COACH,Role.FOOD_STREET,10,null,8),
        new Errand("cargo",Role.LOGISTICS,Role.AIRPORT,12,null,0),
        new Errand("water",Role.RESERVOIR,Role.RESERVOIR,10,new BlockPos(-1320,9,-700),0),
        new Errand("inspection",Role.ENGINEER,Role.ENGINEER,8,new BlockPos(-800,0,-1430),0),
        new Errand("repeat_archive",Role.LIBRARIAN,Role.CAMPUS_DESK,4,null,4,2400,-1,-1,"minecraft:paper"),
        new Errand("repeat_central",Role.CAMPUS_DESK,Role.CENTRAL_DESK,8,null,2,6000,1,2,"minecraft:paper"),
        new Errand("repeat_office",Role.CENTRAL_DESK,Role.OFFICE_DESK,10,null,2,6000,2,3,"minecraft:paper"),
        new Errand("repeat_return",Role.OFFICE_DESK,Role.CAMPUS_DESK,10,null,0,6000,3,1,"minecraft:paper"),
        new Errand("repeat_cafe",Role.WEST,Role.EAST,5,null,1,2400,-1,-1,"minecraft:cocoa_beans"),
        new Errand("repeat_wind",Role.ENGINEER,Role.ENGINEER,8,new BlockPos(-800,0,-1430),0,6000,-1,-1,"minecraft:paper")
    );
    private CityErrands() {}
    static CompoundTag data(ServerPlayer player) {return player.getPersistentData().getCompound(DATA);}
    static int state(ServerPlayer player,Errand quest) {
        int value=data(player).getInt(quest.id());
        return quest.repeatable()&&value==2&&remaining(player,quest)==0?0:value;
    }
    static long remaining(ServerPlayer p,Errand q){return Math.max(0,data(p).getLong(q.id()+"_ready_at")-p.serverLevel().getGameTime());}
    static Component destination(Errand q) {
        var site=CityServicePlan.site(q.to());var pos=site.home();
        return Component.translatable("academy.city.quest.destination",Component.translatable("academy.city.npc."+q.to().name().toLowerCase(java.util.Locale.ROOT)),
                CityPlan.district(pos.getX(),pos.getZ()),pos.getX(),pos.getY(),pos.getZ());
    }
    static Component description(Errand q) {
        var text=Component.translatable("academy.city.quest."+q.id()+".brief").append("\n").append(destination(q));
        if(q.survey()!=null) text.append("\n").append(Component.translatable("academy.city.quest.survey",q.survey().getX(),q.survey().getY(),q.survey().getZ()));
        if(q.wheat()>0) text.append("\n").append(Component.translatable("academy.city.quest.item_supply",q.requiredItem().getHoverName(),q.wheat()));
        if(q.rideFrom()>=0)text.append("\n").append(Component.translatable("academy.city.quest.ride_required",Component.translatable(cn.academy.port.train.NorthSouthLine.STATIONS.get(q.rideFrom()).translationKey()),Component.translatable(cn.academy.port.train.NorthSouthLine.STATIONS.get(q.rideTo()).translationKey())));
        if(q.repeatable())text.append("\n").append(Component.translatable("academy.city.quest.repeat_interval",q.cooldown()/1200));
        return text.append("\n").append(Component.translatable("academy.city.quest.reward",q.reward()));
    }
    static boolean ready(ServerPlayer player,Errand q) {
        return state(player,q)==1 && (q.survey()==null || data(player).getBoolean(q.id()+"_visited"))
                && (q.wheat()==0 || count(player,q.requiredItem())>=q.wheat())
                && (q.rideFrom()<0 || data(player).getBoolean(q.id()+"_rode"));
    }
    static Component accept(ServerPlayer player,Errand q) {
        var data=data(player); if(state(player,q)!=0) return Component.translatable("academy.city.quest.changed");
        data.putInt(q.id(),1);data.remove(q.id()+"_visited");data.remove(q.id()+"_rode");player.getPersistentData().put(DATA,data);
        return Component.translatable("academy.city.quest.accepted",q.name()).append("\n\n").append(description(q));
    }
    static Component complete(ServerPlayer player,Errand q) {
        if(!ready(player,q)) return Component.translatable("academy.city.quest.not_ready").append("\n").append(description(q));
        if(q.wheat()>0) remove(player,q.requiredItem(),q.wheat());
        var data=data(player); data.putInt(q.id(),2);
        if(q.repeatable()){data.putLong(q.id()+"_ready_at",player.serverLevel().getGameTime()+q.cooldown());data.putInt(q.id()+"_count",data.getInt(q.id()+"_count")+1);}
        player.getPersistentData().put(DATA,data);
        var coins=new ItemStack(CityGoods.CITY_COIN.get(),q.reward());
        if(!player.getInventory().add(coins)) player.drop(coins,false);
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
        return Component.translatable("academy.city.quest.completed",q.name(),q.reward());
    }
    static void tick(ServerPlayer player) {
        if(!player.serverLevel().dimension().equals(cn.academy.port.train.TrainManager.CITY) || player.tickCount%20!=0) return;
        var data=data(player); boolean changed=false;
        for(var q:ALL) if(q.survey()!=null && data.getInt(q.id())==1 && !data.getBoolean(q.id()+"_visited")
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(q.survey()))<64) {
            data.putBoolean(q.id()+"_visited",true);changed=true;
            player.sendSystemMessage(Component.translatable("academy.city.quest.visited",q.name()));
        }
        if(changed) player.getPersistentData().put(DATA,data);
    }
    /** Called only after a real passenger dismounts the north-south service at another stop. */
    public static void rode(ServerPlayer player,int from,int to){
        var tag=data(player);boolean changed=false;
        for(var q:ALL)if(q.rideFrom()==from&&q.rideTo()==to&&state(player,q)==1){tag.putBoolean(q.id()+"_rode",true);changed=true;}
        if(changed){player.getPersistentData().put(DATA,tag);player.sendSystemMessage(Component.translatable("academy.city.quest.ride_recorded"));}
    }
    static int coins(ServerPlayer player) {return count(player,new ItemStack(CityGoods.CITY_COIN.get()));}
    static boolean pay(ServerPlayer player,int amount) {
        if(coins(player)<amount) return false;
        remove(player,new ItemStack(CityGoods.CITY_COIN.get()),amount);return true;
    }
    private static int count(ServerPlayer player,ItemStack kind) {
        int total=0;var inventory=player.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++) if(inventory.getItem(i).is(kind.getItem())) total+=inventory.getItem(i).getCount();
        return total;
    }
    private static void remove(ServerPlayer player,ItemStack kind,int count) {
        var inventory=player.getInventory();
        for(int i=0;i<inventory.getContainerSize() && count>0;i++) if(inventory.getItem(i).is(kind.getItem())) {
            int take=Math.min(count,inventory.getItem(i).getCount());inventory.getItem(i).shrink(take);count-=take;
        }
        inventory.setChanged();player.containerMenu.broadcastChanges();
    }
}
