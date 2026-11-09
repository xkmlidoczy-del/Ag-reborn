package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.highpower.*;
import cn.academy.port.wireless.*;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Environmental branches of the actual player descriptions; normal keys only after legal setup. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceEmMdEnvironment {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Map<String,String> IDS=Map.ofEntries(Map.entry("charging-block","charging"),Map.entry("arc-fire","arc_gen"),Map.entry("arc-creeper","arc_gen"),Map.entry("mag-entity","mag_movement"),Map.entry("fork-single","forked_arc"),Map.entry("light-touch","light_shield"),Map.entry("melt-terrain","meltdowner"),Map.entry("melt-overcharge","meltdowner"),Map.entry("jet-collision","jet_engine"),Map.entry("etch-batch","beam_etching"),Map.entry("etch-hard-refusal","beam_etching"),Map.entry("point-no-tool","point_meltdown"),Map.entry("point-hard-refusal","point_meltdown"),Map.entry("laminate-low","particle_laminate"));
    private static final class Fixture {
        final UUID owner;final String mode,id;final List<LivingEntity> targets=new ArrayList<>();final List<BlockPos> blocks=new ArrayList<>();final Map<UUID,BigDecimal> pools=new LinkedHashMap<>();final Set<UUID> hits=new HashSet<>();
        Vec3 start;double cp,minCp,budget,minBudget,overload,maxOverload,maxMove,energy;long clock,tick;int peak;boolean begun,passed,fire,charged,low,triggered;
        Fixture(UUID owner,String mode){this.owner=owner;this.mode=mode;id=IDS.get(mode);}
    }
    private static Fixture fixture;
    private GameplayAcceptanceEmMdEnvironment(){}
    public static void run(Minecraft mc,String action){var server=mc.getSingleplayerServer();require(server!=null&&mc.player!=null,"integrated player");var owner=mc.player.getUUID();
        if(action.startsWith("setup:")){String mode=action.substring(6);require(IDS.containsKey(mode),"known environmental fixture");LiveInputQa.run(mc,"setup:"+IDS.get(mode));server.submit(()->{prepare(server.getPlayerList().getPlayer(owner),mode);return true;}).join();LiveInputQa.assertHealthy();}
        else server.submit(()->{var p=server.getPlayerList().getPlayer(owner);require(fixture!=null&&fixture.owner.equals(owner),"same fixture owner");switch(action){case "baseline"->baseline(p);case "assert"->verify(p);case "verify"->require(fixture.passed,"environment genuine result passed");default->throw new IllegalArgumentException(action);}return true;}).join();
    }
    private static void prepare(ServerPlayer p,String mode){if(fixture!=null)fixture.targets.forEach(Entity::discard);var w=p.serverLevel();var feet=p.blockPosition();
        for(var old:w.getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(40)))if(old.isNoAi())old.discard();
        for(var old:w.getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(25)))old.discard();
        for(int x=-6;x<=6;x++)for(int z=-2;z<=24;z++){w.setBlock(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<7;y++)w.setBlock(feet.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        fixture=new Fixture(p.getUUID(),mode);var f=fixture;w.setWeatherParameters(6000,0,false,false);p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        switch(mode){
            case "charging-block"->{var pos=feet.offset(0,1,4);f.blocks.add(pos);w.setBlock(pos,ClassicWirelessDevices.BASIC.get().defaultBlockState(),3);require(w.getBlockEntity(pos) instanceof ClassicWirelessNodeBlockEntity,"real native energy node");((ClassicWirelessNodeBlockEntity)w.getBlockEntity(pos)).setEnergy(0);}
            case "arc-fire"->{var pos=feet.offset(0,0,5);f.blocks.add(pos);w.setBlock(pos,Blocks.NETHERRACK.defaultBlockState(),3);AbilityStorage.get(p).setSkillExp("arc_gen",1f);}
            case "arc-creeper"->{var e=EntityType.CREEPER.create(w);require(e!=null,"native creeper");add(p,e,0,5);AbilityStorage.get(p).setSkillExp("arc_gen",1f);}
            case "mag-entity"->{var e=EntityType.IRON_GOLEM.create(w);require(e!=null,"native metal entity");add(p,e,0,8);}
            case "fork-single"->{for(int x:new int[]{0,-2,2}){var e=EntityType.HUSK.create(w);require(e!=null,"native wet target");add(p,e,x,6);}for(int x=-3;x<=3;x++)for(int z=4;z<=8;z++)w.setBlock(feet.offset(x,0,z),Blocks.WATER.defaultBlockState(),3);}
            case "light-touch"->{var e=EntityType.HUSK.create(w);require(e!=null,"native close target");add(p,e,0,2);}
            case "jet-collision"->{var e=EntityType.HUSK.create(w);require(e!=null,"native collision target");add(p,e,0,5);}
            case "melt-terrain"->{for(int y=0;y<4;y++){var at=feet.offset(0,y,4);f.blocks.add(at);w.setBlock(at,Blocks.STONE.defaultBlockState(),3);}var e=EntityType.HUSK.create(w);require(e!=null,"native target behind wall");add(p,e,0,8);}
            case "melt-overcharge"->{var e=EntityType.HUSK.create(w);require(e!=null,"native unhit cancellation target");add(p,e,0,6);}
            case "etch-batch"->{for(int x=-1;x<=2;x++)for(int y=0;y<4;y++){var at=feet.offset(x,y,5);f.blocks.add(at);w.setBlock(at,(y%2==0?Blocks.DIRT:Blocks.GRASS_BLOCK).defaultBlockState(),3);}}
            case "etch-hard-refusal","point-no-tool","point-hard-refusal"->{var at=feet.offset(0,1,5);f.blocks.add(at);w.setBlock(at,(mode.equals("point-hard-refusal")?Blocks.BEDROCK:Blocks.STONE).defaultBlockState(),3);if(mode.equals("point-hard-refusal"))p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_PICKAXE));}
            case "laminate-low"->{for(int x:new int[]{-4,4})w.setBlock(feet.offset(x,1,6),Blocks.STONE.defaultBlockState(),3);var e=EntityType.HUSK.create(w);require(e!=null,"real low-output corridor target");add(p,e,0,5);}
            default->throw new IllegalArgumentException(mode);
        }
        p.inventoryMenu.broadcastChanges();AbilityStorage.save(p);AcademyNetwork.sync(p);LOG.info("QA_GAMEPLAY_ENV_PREPARED mode={} skill={} targets={} blocks={} trainedArcFixture={}",mode,f.id,f.targets.size(),f.blocks,mode.startsWith("arc-"));
    }
    private static void add(ServerPlayer p,Mob e,int x,int z){e.setNoAi(true);e.setNoGravity(true);e.setSilent(true);e.setPos(p.position().add(x,0,z));require(p.serverLevel().addFreshEntity(e),"real entity in world");if(!fixture.mode.equals("mag-entity"))require(HighPowerCombat.enroll(e,5),"durable real target enrollment");fixture.targets.add(e);}
    private static void baseline(ServerPlayer p){var f=fixture;require(!f.begun&&AbilityStorage.get(p).canUse(f.id),"one baseline with usable skill");f.cp=f.minCp=AbilityStorage.get(p).cp;f.budget=f.minBudget=HighPowerResources.availableCp(p);f.overload=f.maxOverload=AbilityStorage.get(p).overload;f.clock=clock(p);f.tick=p.serverLevel().getGameTime();f.start=p.position();for(var e:f.targets){f.pools.put(e.getUUID(),pool(e));if(f.mode.equals("fork-single"))require(e.isInWaterOrRain(),"real conductive side targets");}if(f.mode.equals("charging-block"))f.energy=node(p).getEnergy();f.begun=true;LOG.info("QA_GAMEPLAY_ENV_BASELINE mode={} cp={} budget={} overload={} pools={} blocks={}",f.mode,f.cp,f.budget,f.overload,f.pools,f.blocks);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void early(PlayerTickEvent.Post e){if(e.getEntity() instanceof ServerPlayer p)sample(p);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){var f=fixture;if(f!=null){var p=e.getServer().getPlayerList().getPlayer(f.owner);if(p!=null)sample(p);}}
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void hit(LivingDamageEvent.Post e){var f=fixture;if(f!=null&&f.begun&&!f.passed&&e.getSource().getEntity() instanceof ServerPlayer p&&p.getUUID().equals(f.owner)){if(f.pools.containsKey(e.getEntity().getUUID()))f.hits.add(e.getEntity().getUUID());sample(p);}}
    private static void sample(ServerPlayer p){var f=fixture;if(f==null||!f.begun||f.passed||!f.owner.equals(p.getUUID()))return;f.minCp=Math.min(f.minCp,AbilityStorage.get(p).cp);f.minBudget=Math.min(f.minBudget,HighPowerResources.availableCp(p));f.maxOverload=Math.max(f.maxOverload,AbilityStorage.get(p).overload);f.maxMove=Math.max(f.maxMove,p.position().distanceTo(f.start));
        if(f.mode.equals("arc-fire"))f.fire|=p.serverLevel().getBlockState(f.blocks.getFirst().above()).is(Blocks.FIRE);
        if(f.mode.equals("arc-creeper"))f.charged|=((Creeper)f.targets.getFirst()).isPowered();
        if(f.id.equals("beam_etching"))f.peak=Math.max(f.peak,BeamEtching.marks(p).size());
        if(f.mode.equals("jet-collision"))f.triggered|=cn.academy.port.skill.JetEngine.triggering(p);
        if(f.mode.equals("laminate-low")){var r=ParticleLaminate.receipt(p);f.low|=r!=null&&r.lowMode()&&r.paid()&&r.successes()>0;}
    }
    private static void verify(ServerPlayer p){var f=fixture;require(f.begun&&!f.passed,"one environmental result");sample(p);boolean refusal=f.mode.endsWith("refusal")||f.mode.equals("point-no-tool");boolean paid=f.minCp<f.cp||f.minBudget<f.budget||clock(p)>f.clock&&clock(p)>f.tick;
        if(refusal){require(!paid,"unsupported material/tool produced no successful payment");for(var at:f.blocks)require(!p.serverLevel().getBlockState(at).isAir(),"refused actual block remains");require(drops(p)==0,"refusal produced no phantom loot");}
        else {require(paid,"actual finite energy/resource payment");switch(f.mode){
            case "charging-block"->require(node(p).getEnergy()>f.energy&&node(p).getEnergy()<=node(p).getMaxEnergy(),"actual finite native block IF increased");
            case "arc-fire"->require(f.fire,"normal trained arcs actually created a world fire block, RNG unmodified");
            case "arc-creeper"->{require(f.charged,"normal arc actually powered native creeper, RNG unmodified");damage(f,f.targets.getFirst());}
            case "mag-entity"->require(f.maxMove>2&&f.targets.getFirst().isAlive(),"actual player moved toward a live magnetic entity anchor");
            case "fork-single"->{damage(f,f.targets.getFirst());for(var e:f.targets.subList(1,f.targets.size()))require(pool(e).compareTo(f.pools.get(e.getUUID()))==0&&!f.hits.contains(e.getUUID()),"crouch suppressed conductive secondary damage");}
            case "light-touch"->{damage(f,f.targets.getFirst());require(!cn.academy.port.skill.LightShield.active(p),"normal release ended close-contact shield");}
            case "melt-terrain"->{require(f.blocks.stream().anyMatch(at->p.serverLevel().getBlockState(at).isAir()),"actual beam removed obstructing stone");damage(f,f.targets.getFirst());require(f.maxOverload>f.overload,"actual beam imposed overload risk");}
            case "melt-overcharge"->{require(f.maxOverload>f.overload&&!cn.academy.port.skill.Meltdowner.active(p),"actual excessive held charge imposed overload and ended");for(var e:f.targets)require(pool(e).compareTo(f.pools.get(e.getUUID()))==0&&!f.hits.contains(e.getUUID()),"overlong charge canceled without a fake shot");}
            case "jet-collision"->{require(f.triggered&&f.maxMove>2,"actual release trajectory moved player");damage(f,f.targets.getFirst());}
            case "etch-batch"->{require(f.peak==16&&f.blocks.stream().allMatch(at->p.serverLevel().getBlockState(at).isAir()),"actual sixteen visible soft-earth selections harvested");require(drops(p)>=16,"actual sixteen native soft-earth drops exist");}
            case "laminate-low"->{require(f.low,"actual low-output successful paid receipt");damage(f,f.targets.getFirst());}
            default->throw new IllegalArgumentException(f.mode);
        }}f.passed=true;LOG.info("QA_GAMEPLAY_ENV_PASS mode={} skill={} poolsBefore={} poolsAfter={} hits={} cpMinimum={} budgetMinimum={} maxOverload={} maxMove={} fire={} creeperPowered={} markedPeak={} low={} realDrops={} clockBefore={} clockAfter={}",f.mode,f.id,f.pools,f.targets.stream().map(GameplayAcceptanceEmMdEnvironment::pool).toList(),f.hits,f.minCp,f.minBudget,f.maxOverload,f.maxMove,f.fire,f.charged,f.peak,f.low,drops(p),f.clock,clock(p));
    }
    private static void damage(Fixture f,LivingEntity e){require(pool(e).compareTo(f.pools.get(e.getUUID()))<0&&f.hits.contains(e.getUUID()),"real attributed target damage "+e.getUUID());}
    private static int drops(ServerPlayer p){int n=0;for(var e:p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(24)))if(e.getItem().is(Items.DIRT)||e.getItem().is(Items.COBBLESTONE))n+=e.getItem().getCount();n+=p.getInventory().countItem(Items.DIRT)+p.getInventory().countItem(Items.COBBLESTONE);return n;}
    private static BigDecimal pool(LivingEntity e){return HighPowerCombat.enabled(e)?new BigDecimal(HighPowerCombat.healthText(e)).add(HighPowerCombat.shield(e)):BigDecimal.valueOf(e.getHealth()+e.getAbsorptionAmount());}
    private static ClassicWirelessNodeBlockEntity node(ServerPlayer p){return(ClassicWirelessNodeBlockEntity)p.serverLevel().getBlockEntity(fixture.blocks.getFirst());}
    private static long clock(ServerPlayer p){return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+fixture.id);}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException("QA_GAMEPLAY_ENV_FAILED "+why);}
}
