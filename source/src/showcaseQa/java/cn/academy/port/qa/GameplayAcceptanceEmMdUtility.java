package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.skill.ClassicMineScan;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Independent real environment/material witnesses, selected through gameem:utility:*. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceEmMdUtility {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Set<String> IDS=Set.of("charging","mine_detect","body_intensify","mine_ray_basic","mine_ray_expert","mine_ray_luck","beam_etching","point_meltdown","disintegration_sweep");
    private static final class Fixture {
        final UUID owner;final String id;final List<BlockPos> blocks=new ArrayList<>();
        Item drop;double cp,budget,minCp,minBudget,energy;boolean begun,passed;
        Fixture(UUID owner,String id){this.owner=owner;this.id=id;}
    }
    private static Fixture fixture;
    private GameplayAcceptanceEmMdUtility(){}
    public static void run(Minecraft mc,String action){
        var server=mc.getSingleplayerServer();require(server!=null&&mc.player!=null,"integrated player");UUID owner=mc.player.getUUID();
        if(action.startsWith("setup:")){
            String id=action.substring(6);require(IDS.contains(id),"implemented utility fixture "+id);
            LiveInputQa.run(mc,"setup:"+id);
            server.submit(()->{prepare(server.getPlayerList().getPlayer(owner),id);return true;}).join();LiveInputQa.assertHealthy();
        }else{
            server.submit(()->{
                var p=server.getPlayerList().getPlayer(owner);require(fixture!=null&&fixture.owner.equals(owner),"same utility fixture owner");
                if(action.equals("baseline"))baseline(p);
                else if(action.equals("assert"))verify(p);
                else if(action.equals("verify"))require(fixture.passed,"utility actual mechanism passed");
                else throw new IllegalArgumentException(action);
                return true;
            }).join();
            if(action.equals("assert")&&fixture.id.equals("mine_detect"))verifyClientOre(mc);
        }
    }
    private static void prepare(ServerPlayer p,String id){
        fixture=new Fixture(p.getUUID(),id);var f=fixture;var world=p.serverLevel();var feet=p.blockPosition();
        for(int z=-1;z<=18;z++)for(int x=-3;x<=3;x++){
            world.setBlock(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<=4;y++)world.setBlock(feet.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        for(var e:world.getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(25)))e.discard();
        world.setWeatherParameters(6000,0,false,false);
        // Legal setup only; no effect or resource is changed after baseline.
        p.removeAllEffects();
        if(id.equals("charging")){
            p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get()));
            ClassicEnergyItemHelper.setEnergy(p.getMainHandItem(),0);
        }else if(id.equals("mine_detect")){
            BlockPos ore=feet.offset(2,0,5);world.setBlock(ore,Blocks.DIAMOND_ORE.defaultBlockState(),3);f.blocks.add(ore);
        }else if(id.equals("body_intensify")){
            // Forty ticks gives a real guaranteed first JUMP roll; optional regeneration remains random.
        }else{
            int count=id.equals("mine_ray_luck")?8:id.equals("disintegration_sweep")?3:1;
            for(int i=0;i<count;i++){
                BlockPos cell=id.equals("disintegration_sweep")?feet.offset(0,i,5):feet.offset(0,1,5+i);
                var state=switch(id){case "mine_ray_basic"->Blocks.COAL_ORE.defaultBlockState();case "mine_ray_expert","mine_ray_luck"->Blocks.DIAMOND_ORE.defaultBlockState();case "point_meltdown"->Blocks.STONE.defaultBlockState();default->Blocks.DIRT.defaultBlockState();};
                world.setBlock(cell,state,3);f.blocks.add(cell);
            }
            f.drop=switch(id){case "mine_ray_basic"->Items.COAL;case "mine_ray_expert","mine_ray_luck"->Items.DIAMOND;case "point_meltdown"->Items.COBBLESTONE;default->Items.DIRT;};
            if(id.equals("point_meltdown"))p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_PICKAXE));
        }
        p.inventoryMenu.broadcastChanges();AcademyNetwork.sync(p);
        LOG.info("QA_GAMEPLAY_UTILITY_PREPARED skill={} actualBlocks={} expectedDrop={} setupOnly=true",id,f.blocks,f.drop);
    }
    private static void baseline(ServerPlayer p){
        var f=fixture;require(!f.begun,"no utility baseline reset");
        f.cp=f.minCp=AbilityStorage.get(p).cp;f.budget=f.minBudget=HighPowerResources.availableCp(p);
        f.energy=ClassicEnergyItemHelper.getEnergy(p.getMainHandItem());f.begun=true;
        if(f.drop!=null)require(drops(p,f.drop)==0,"empty initial output, not preseeded drops");
        LOG.info("QA_GAMEPLAY_UTILITY_BASELINE skill={} cp={} budget={} itemEnergy={} blocks={}",f.id,f.cp,f.budget,f.energy,f.blocks);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var f=fixture;if(f==null||!f.begun||f.passed)return;var p=event.getServer().getPlayerList().getPlayer(f.owner);if(p==null)return;
        sample(p,f);
    }
    /** Observe only real native harvesting/drop callbacks; never call a harvesting or spending method. */
    @SubscribeEvent public static void breaking(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event){
        var f=fixture;if(f!=null&&f.begun&&!f.passed&&event.getPlayer() instanceof ServerPlayer p
                &&p.getUUID().equals(f.owner)&&f.blocks.contains(event.getPos()))sample(p,f);
    }
    @SubscribeEvent public static void itemJoined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){
        var f=fixture;if(f==null||!f.begun||f.passed||!(event.getEntity() instanceof ItemEntity)||event.getLevel().isClientSide())return;
        var server=event.getLevel().getServer();if(server!=null){var p=server.getPlayerList().getPlayer(f.owner);if(p!=null)sample(p,f);}
    }
    private static void sample(ServerPlayer p,Fixture f){
        f.minCp=Math.min(f.minCp,AbilityStorage.get(p).cp);
        // A native removal owns a short finite reservation before its irreversible settlement.
        // Real removal + real output are separately required by verify; a held preview cannot pass.
        f.minBudget=Math.min(f.minBudget,HighPowerResources.availableCp(p));
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void early(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){
        var f=fixture;if(f!=null&&f.begun&&!f.passed&&event.getEntity() instanceof ServerPlayer p&&p.getUUID().equals(f.owner))sample(p,f);
    }
    private static void verify(ServerPlayer p){
        var f=fixture;require(f.begun&&!f.passed,"utility needs actual input baseline");
        sample(p,f);
        require(f.minCp<f.cp||f.minBudget<f.budget,"real finite CP debit observed, not visual-only");
        if(f.id.equals("charging")){
            double energy=ClassicEnergyItemHelper.getEnergy(p.getMainHandItem());
            require(p.getMainHandItem().is(ClassicEnergyItems.ENERGY_UNIT.get())&&energy>f.energy&&energy<=10000,"same real finite energy item gained native IF");
            LOG.info("QA_GAMEPLAY_PASS skill=charging witness=actual_item_IF before={} after={} nativeCpMinimum={} availableBudgetMinimum={}",f.energy,energy,f.minCp,f.minBudget);
        }else if(f.id.equals("mine_detect")){
            require(p.hasEffect(MobEffects.BLINDNESS),"actual mining perception has real blindness cost");
            require(p.serverLevel().getBlockState(f.blocks.getFirst()).is(Blocks.DIAMOND_ORE),"detection never mines the actual ore");
            // Client actual ore-list evidence is checked synchronously after this server result.
        }else if(f.id.equals("body_intensify")){
            require(p.hasEffect(MobEffects.JUMP)&&p.hasEffect(MobEffects.HUNGER),"real forty-tick body release grants jump and hunger");
            require(!p.hasEffect(MobEffects.MOVEMENT_SPEED)&&!p.hasEffect(MobEffects.DAMAGE_BOOST),"no unsupported speed/strength claim");
            LOG.info("QA_GAMEPLAY_PASS skill=body_intensify witness=real_jump_and_hunger jump={} hunger={} regenerationOptional={}",p.getEffect(MobEffects.JUMP),p.getEffect(MobEffects.HUNGER),p.hasEffect(MobEffects.REGENERATION));
        }else{
            long removed=f.blocks.stream().filter(pos->p.serverLevel().getBlockState(pos).isAir()).count();int output=drops(p,f.drop);
            require(removed>0&&output>0,"real block removal and actual native item output");
            if(f.id.equals("mine_ray_expert"))require(removed==f.blocks.size()&&output>=1,"high-tier diamond ore really harvested");
            if(f.id.equals("mine_ray_luck")){
                require(removed==8&&output>8,"native Fortune batch produced actual extra diamonds; no RNG/loot forced");
            }
            LOG.info("QA_GAMEPLAY_PASS skill={} witness=real_mining_and_native_drop removed={} prepared={} output={} item={} fortuneExtraObserved={} nativeCpMinimum={} availableBudgetMinimum={}",f.id,removed,f.blocks.size(),output,f.drop,f.id.equals("mine_ray_luck")&&output>8,f.minCp,f.minBudget);
        }
        f.passed=true;
    }
    private static int drops(ServerPlayer p,Item item){
        int n=0;for(var e:p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(25)))if(e.getItem().is(item))n+=e.getItem().getCount();
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(item))n+=p.getInventory().getItem(i).getCount();return n;
    }
    @SuppressWarnings("unchecked") private static void verifyClientOre(Minecraft mc){
        try{
            var field=Class.forName("cn.academy.port.client.ClassicMineDetectEffects").getDeclaredField("HANDLERS");field.setAccessible(true);
            var handlers=(List<ClassicMineScan.Handler>)field.get(null);var ore=fixture.blocks.getFirst();
            require(handlers.stream().flatMap(h->h.aliveSims().stream()).anyMatch(e->e.x()==ore.getX()&&e.y()==ore.getY()&&e.z()==ore.getZ()),"actual client ore scan contains the prepared diamond coordinate");
            LOG.info("QA_GAMEPLAY_PASS skill=mine_detect witness=actual_ore_coordinate_plus_blindness ore={} matchingHandlers={} noReceiverCalled=true",ore,handlers.size());
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_GAMEPLAY_UTILITY_FAILED "+why);}
}
