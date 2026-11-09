package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.*;
import cn.academy.port.skill.CoinTosses;
import cn.academy.port.skill.MeltdownerLateEntities;
import cn.academy.port.skill.SilbarnEntity;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

/** Additional player-operated modes. Only legal setup, native mouse input, ordinary threats and read-only witnesses. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceEmMdModes {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Map<String,String> IDS=Map.of("coin","railgun","semiconductor","ray_barrage","dual-secondary","dual_point","net-hold","thunder_net","lance-orbit","iron_sand_lance","arrow-separation","magnetic_item_pull","rail-hold","rail_thunder_array","canopy-held","multicore_canopy","pulse-detect","pulse_shield");
    private static final class Fixture {
        final UUID owner;final String mode,id;final List<Husk> targets=new ArrayList<>();final Map<UUID,BigDecimal> pools=new LinkedHashMap<>();final Set<UUID> hits=new HashSet<>(),restrained=new HashSet<>();
        Husk shooter;Arrow arrow;Vec3 arrowVelocity,detectPoint;double cp,minCp,budget,minBudget,coinProgress;int material,pulses,orbitLances,deflected,separated;long tick,clock;boolean begun,passed,tossSeen,silbarnSeen,silbarnHit,markSeen,heldModeSeen,detectSeen,separationSeen;
        Fixture(UUID owner,String mode){this.owner=owner;this.mode=mode;id=IDS.get(mode);}
    }
    private static Fixture fixture;
    private GameplayAcceptanceEmMdModes(){}
    public static void run(Minecraft mc,String action){
        var server=mc.getSingleplayerServer();require(server!=null&&mc.player!=null,"integrated player");var owner=mc.player.getUUID();
        if(action.startsWith("mouse:")){
            require(mc.screen==null,"native item-use requires closed GUI");
            int state=switch(action.substring(6)){case "press"->1;case "release"->0;case "click"->2;default->throw new IllegalArgumentException(action);};
            try{var method=mc.mouseHandler.getClass().getDeclaredMethod("onPress",long.class,int.class,int.class,int.class);method.setAccessible(true);if(state==2){method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),1,1,0);method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),1,0,0);}else method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),1,state,0);}catch(ReflectiveOperationException e){throw new IllegalStateException("native mouse input unavailable",e);}return;
        }
        if(action.startsWith("setup:")){
            String mode=action.substring(6);require(IDS.containsKey(mode),"implemented mode "+mode);LiveInputQa.run(mc,"setup:"+IDS.get(mode));
            server.submit(()->{prepare(server.getPlayerList().getPlayer(owner),mode);return true;}).join();LiveInputQa.assertHealthy();
        }else server.submit(()->{
            var p=server.getPlayerList().getPlayer(owner);require(fixture!=null&&fixture.owner.equals(owner),"same mode owner");
            switch(action){case "baseline"->baseline(p);case "arrow"->arrow(p);case "assert"->verify(p);case "verify"->require(fixture.passed,"mode genuine effect passed");default->throw new IllegalArgumentException(action);}return true;
        }).join();
    }
    private static void prepare(ServerPlayer p,String mode){
        if(fixture!=null){fixture.targets.forEach(Husk::discard);if(fixture.shooter!=null)fixture.shooter.discard();if(fixture.arrow!=null)fixture.arrow.discard();}
        var world=p.serverLevel();var feet=p.blockPosition();
        for(var old:world.getEntitiesOfClass(Husk.class,p.getBoundingBox().inflate(36)))if(old.isNoAi())old.discard();
        for(int z=-3;z<=22;z++)for(int x=-5;x<=5;x++){world.setBlock(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<6;y++)world.setBlock(feet.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        world.setWeatherParameters(6000,0,false,false);fixture=new Fixture(p.getUUID(),mode);var f=fixture;
        p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        if(mode.equals("coin"))p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(AcademyCraft.COIN.get(),2));
        if(mode.equals("semiconductor"))p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(MeltdownerLateEntities.SILBARN.get(),2));
        if(mode.equals("lance-orbit"))p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(IronSandMedium.ITEM.get(),32));
        if(Set.of("coin","rail-hold","canopy-held","pulse-detect").contains(mode))f.targets.add(mob(p,new Vec3(0,0,6)));
        if(mode.equals("semiconductor")||mode.equals("net-hold"))for(int x:new int[]{0,-2,2})f.targets.add(mob(p,new Vec3(x,0,6)));
        if(mode.equals("dual-secondary"))for(int x:new int[]{-2,2})f.targets.add(mob(p,new Vec3(x,0,6)));
        if(mode.equals("net-hold"))for(int x=-3;x<=3;x++)for(int z=4;z<=8;z++)world.setBlock(feet.offset(x,0,z),Blocks.WATER.defaultBlockState(),3);
        if(mode.equals("lance-orbit")||mode.equals("arrow-separation"))f.shooter=mob(p,new Vec3(4,0,10));
        if(mode.equals("pulse-detect")){f.targets.getFirst().getPersistentData().putBoolean(NewSkillRuntime.CIRCUIT_FLAG,true);f.targets.getFirst().setTarget(p);f.detectPoint=f.targets.getFirst().position().add(0,f.targets.getFirst().getBbHeight()/2,0);}
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();
        LOG.info("QA_GAMEPLAY_MODE_PREPARED mode={} skill={} targets={} realInputRequired=true",mode,f.id,f.targets.size());
    }
    private static Husk mob(ServerPlayer p,Vec3 offset){var h=EntityType.HUSK.create(p.serverLevel());require(h!=null,"native target");h.setNoAi(true);h.setNoGravity(true);h.setSilent(true);h.setPos(p.position().add(offset));require(p.serverLevel().addFreshEntity(h)&&HighPowerCombat.enroll(h,5),"durable real target");return h;}
    private static int material(ServerPlayer p){return switch(fixture.mode){case "coin","semiconductor"->p.getMainHandItem().getCount();case "lance-orbit"->p.getOffhandItem().getCount();default->0;};}
    private static void baseline(ServerPlayer p){var f=fixture;require(!f.begun,"one baseline");for(var h:f.targets){f.pools.put(h.getUUID(),pool(h));if(f.mode.equals("net-hold"))require(h.isInWaterOrRain(),"real wet conductive targets");}
        f.cp=f.minCp=AbilityStorage.get(p).cp;f.budget=f.minBudget=HighPowerResources.availableCp(p);f.material=material(p);f.clock=clock(p);f.tick=p.serverLevel().getGameTime();f.begun=true;
        LOG.info("QA_GAMEPLAY_MODE_BASELINE mode={} pools={} cp={} budget={} material={}",f.mode,f.pools,f.cp,f.budget,f.material);
    }
    /** Normal hostile Arrow flight. Orbit test starts at a physically observed lance capsule, not an injected contact event. */
    private static void arrow(ServerPlayer p){var f=fixture;require(f.begun&&f.arrow==null&&f.shooter!=null,"one genuine projectile after mode activation");Vec3 at=p.position().add(0,1.2,9),velocity=new Vec3(0,0,-.25);
        if(f.mode.equals("lance-orbit")){var poses=IronSandLance.serverPose(p);require(poses.size()==3&&IronSandLance.paid(p),"three actual paid orbit lances");var pose=poses.stream().filter(v->v.status()==IronSandLance.FLYING).findFirst().orElseThrow();var midpoint=pose.tail().add(pose.head()).scale(.5);var radial=midpoint.subtract(p.position().add(0,1,0)).normalize();at=midpoint.add(radial.scale(.2));velocity=radial.scale(-.1);}
        f.arrow=new Arrow(p.serverLevel(),f.shooter,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));f.arrow.setPos(at);f.arrow.setDeltaMovement(velocity);f.arrow.setNoGravity(false);f.arrowVelocity=velocity;require(p.serverLevel().addFreshEntity(f.arrow),"native projectile spawned");
        LOG.info("QA_GAMEPLAY_MODE_ARROW mode={} arrow={} origin={} velocity={}",f.mode,f.arrow.getUUID(),at,velocity);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void early(PlayerTickEvent.Post e){if(e.getEntity() instanceof ServerPlayer p)sample(p);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){var f=fixture;if(f!=null){var p=e.getServer().getPlayerList().getPlayer(f.owner);if(p!=null)sample(p);}}
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void hit(LivingDamageEvent.Post e){var f=fixture;if(f==null||!f.begun||f.passed)return;if(e.getSource().getEntity() instanceof ServerPlayer p&&p.getUUID().equals(f.owner)&&f.pools.containsKey(e.getEntity().getUUID())){f.hits.add(e.getEntity().getUUID());sample(p);}}
    @SubscribeEvent public static void client(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        var f=fixture;if(f==null||!f.begun||f.passed||!f.mode.equals("pulse-detect"))return;
        // Inspect the actual accepted server DETECT message. Never call its receiver or synthesize points.
        var mc=Minecraft.getInstance();if(mc.player==null||!mc.player.getUUID().equals(f.owner))return;
        try{var field=Class.forName("cn.academy.port.client.NewSkillEffects").getDeclaredField("LIVE");field.setAccessible(true);var live=(List<?>)field.get(null);synchronized(live){for(Object l:live){var v=(NewSkillRuntime.Visual)field(l,"visual");if(v.owner().equals(f.owner)&&v.phase()==NewSkillRuntime.DETECT&&NewSkillRuntime.Kind.values()[v.kind()].id.equals("pulse_shield")&&f.detectPoint!=null&&v.points().stream().anyMatch(point->point.distanceToSqr(f.detectPoint)<.0001))f.detectSeen=true;}}}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void sample(ServerPlayer p){var f=fixture;if(f==null||!f.begun||f.passed||!p.getUUID().equals(f.owner))return;
        f.minCp=Math.min(f.minCp,AbilityStorage.get(p).cp);f.minBudget=Math.min(f.minBudget,HighPowerResources.availableCp(p));
        if(f.mode.equals("coin")){Object toss=contexts(CoinTosses.class,"TOSSES").get(p.getUUID());if(toss!=null){f.tossSeen=true;var path=(CoinTosses.Trajectory)field(toss,"trajectory");f.coinProgress=Math.max(f.coinProgress,path.progress());}}
        if(f.mode.equals("semiconductor"))for(var s:p.serverLevel().getEntitiesOfClass(SilbarnEntity.class,p.getBoundingBox().inflate(24))){f.silbarnSeen=true;f.silbarnHit|=s.isHit();}
        if(f.mode.equals("dual-secondary"))f.markSeen|=!f.targets.isEmpty()&&f.targets.getFirst().getUUID().equals(DualPoint.markedTarget(p));
        if(f.mode.equals("net-hold")){f.pulses=Math.max(f.pulses,ThunderNet.pulseCount(p));for(var h:f.targets){var a=h.getAttribute(Attributes.MOVEMENT_SPEED);if(a!=null&&a.getModifier(ThunderNetRestraint.MODIFIER_ID)!=null&&a.getValue()<a.getBaseValue())f.restrained.add(h.getUUID());}}
        if(f.mode.equals("lance-orbit")){f.orbitLances=Math.max(f.orbitLances,IronSandLance.emittedCount(p));f.deflected=Math.max(f.deflected,IronSandLance.deflectedCount(p));}
        if(f.mode.equals("rail-hold")){f.pulses=Math.max(f.pulses,RailThunderArray.pulseCount(p));f.heldModeSeen|=RailThunderArray.phase(p)==RailThunderArray.HOLD;}
        if(f.mode.equals("canopy-held")){var receipt=MulticoreCanopy.receipt(p);if(receipt!=null&&receipt.held()&&receipt.paid()&&receipt.successes()>0)f.heldModeSeen=true;}
        if(f.mode.equals("arrow-separation")){
            f.separated=Math.max(f.separated,MagneticArrowSeparation.affectedCount(p));
            if(!f.separationSeen&&f.separated>0&&f.arrow!=null&&MagneticArrowSeparation.leased(f.arrow)&&f.arrow.getDeltaMovement().distanceTo(f.arrowVelocity)>.01){
                f.separationSeen=true;LOG.info("QA_GAMEPLAY_SEPARATION_NATIVE_WITNESS tick={} affected={} arrow={} before={} actual={} lease={} paid={}",p.serverLevel().getGameTime(),f.separated,f.arrow.getUUID(),f.arrowVelocity,f.arrow.getDeltaMovement(),f.arrow.getPersistentData().get(MagneticArrowSeparation.LEASE_KEY),MagneticArrowSeparation.paid(p));
            }
        }
    }
    private static void verify(ServerPlayer p){var f=fixture;require(f.begun&&!f.passed,"one mode result");sample(p);
        boolean nativeMode=f.mode.equals("coin")||f.mode.equals("semiconductor");boolean paid=nativeMode?f.minCp<f.cp:f.minBudget<f.budget||clock(p)>f.clock&&clock(p)>f.tick;
        if(!f.mode.equals("pulse-detect"))require(paid,"genuine finite payment witnessed");for(var h:f.targets)if(!f.mode.equals("pulse-detect"))require(pool(h).compareTo(f.pools.get(h.getUUID()))<0&&f.hits.contains(h.getUUID()),"real attributed damage for each mode target "+h.getUUID());
        switch(f.mode){
            case "coin"->{require(f.tossSeen&&f.coinProgress>.7,"real native toss reached late QTE");require(material(p)==f.material-1&&!p.getPersistentData().contains("academy:coin_escrow"),"exactly one normal coin consumed and escrow closed, not iron path");}
            case "semiconductor"->{require(f.silbarnSeen&&f.silbarnHit,"native thrown semiconductor actually triggered scatter");require(material(p)==f.material-1,"exactly one normal semiconductor consumed");}
            case "dual-secondary"->require(f.markSeen,"real first target was marked before second-target shot");
            case "net-hold"->{require(f.pulses>=2&&f.restrained.size()==f.targets.size(),"multiple paid pulses and real movement-speed reduction on all targets");require(!ThunderNet.active(p),"normal release ended hold");for(var h:f.targets)require(h.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThunderNetRestraint.MODIFIER_ID)==null,"normal release removed only own restraint");}
            case "lance-orbit"->{require(f.orbitLances==3&&f.deflected>0&&f.arrow!=null&&IronSandLance.leased(f.arrow)&&f.arrow.getDeltaMovement().distanceTo(f.arrowVelocity)>.01,"actual paid three-lance orbit turned hostile native arrow");require(material(p)==f.material-3,"exact three-sand orbit material cost");}
            case "rail-hold"->{require(f.heldModeSeen&&f.pulses>=2,"actual held grid made repeated paid damaging pulses");require(!RailThunderArray.active(p),"normal release terminated rail grid");}
            case "canopy-held"->{require(f.heldModeSeen,"actual paid tracking receipt successfully hit target");require(!MulticoreCanopy.holding(p),"normal release stopped tracking emission");}
            case "pulse-detect"->{require(f.detectSeen,"actual server detection returned prepared circuit target point");require(f.minBudget>=f.budget&&f.minCp>=f.cp,"detect mode spent neither wallet");for(var h:f.targets)require(pool(h).compareTo(f.pools.get(h.getUUID()))==0&&h.getTarget()==p&&h.getPersistentData().getLong(NewSkillRuntime.PULSE_UNTIL)<=f.tick,"detect mode did not damage, interfere or clear target AI");}
            case "arrow-separation"->require(f.separated>0&&f.arrow!=null&&f.separationSeen,"actual GUI-confirmed corridor changed native arrow velocity while its native lease was live");
            default->throw new IllegalStateException(f.mode);
        }
        f.passed=true;LOG.info("QA_GAMEPLAY_MODE_PASS mode={} skill={} targetPoolsBefore={} targetPoolsAfter={} attributed={} minCp={} minBudget={} clockBefore={} clockAfter={} materialBefore={} materialAfter={} tossProgress={} restraintTargets={} paidPulses={} lances={} deflections={} separated={}",f.mode,f.id,f.pools,f.targets.stream().map(GameplayAcceptanceEmMdModes::pool).toList(),f.hits,f.minCp,f.minBudget,f.clock,clock(p),f.material,material(p),f.coinProgress,f.restrained,f.pulses,f.orbitLances,f.deflected,f.separated);
    }
    private static BigDecimal pool(Husk h){return new BigDecimal(HighPowerCombat.healthText(h)).add(HighPowerCombat.shield(h));}
    private static long clock(ServerPlayer p){return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+fixture.id);}
    private static Map<?,?> contexts(Class<?> type,String name){try{var f=type.getDeclaredField(name);f.setAccessible(true);return(Map<?,?>)f.get(null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException("QA_GAMEPLAY_MODE_FAILED "+why);}
}
