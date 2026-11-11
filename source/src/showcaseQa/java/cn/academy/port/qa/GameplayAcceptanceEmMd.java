package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.IronSandMedium;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** QA-only real-world witnesses. No start/release/attack/effect receiver is called by this class. */
@EventBusSubscriber(modid="academy", value=Dist.CLIENT)
public final class GameplayAcceptanceEmMd {
    private static final Logger LOG=LogUtils.getLogger();
    private record Spec(int targets, boolean collateral, boolean rain, int materialSpent) {}
    private static final Map<String,Spec> COMBAT=Map.ofEntries(
            Map.entry("arc_gen",new Spec(1,false,false,0)),
            Map.entry("thunder_bolt",new Spec(3,true,false,0)),
            Map.entry("railgun",new Spec(1,false,false,1)),
            Map.entry("thunder_clap",new Spec(3,true,false,0)),
            Map.entry("forked_arc",new Spec(3,true,true,0)),
            Map.entry("iron_sand_lance",new Spec(1,false,false,3)),
            Map.entry("electron_bomb",new Spec(1,false,false,0)),
            Map.entry("meltdowner",new Spec(1,false,false,0)),
            Map.entry("ray_barrage",new Spec(1,false,false,0)),
            Map.entry("dual_point",new Spec(1,false,false,0)),
            Map.entry("scatter_bomb",new Spec(5,false,false,0)),
            Map.entry("electron_missile",new Spec(1,false,false,0)),
            Map.entry("thunder_net",new Spec(3,true,true,0)),
            Map.entry("rail_thunder_array",new Spec(1,false,false,0)),
            Map.entry("disintegration_sweep",new Spec(3,true,false,0)),
            Map.entry("multicore_canopy",new Spec(1,false,false,0)));
    private static final class Fixture {
        final UUID owner; final String skill; final Spec spec; final List<net.minecraft.world.entity.LivingEntity> targets=new ArrayList<>();
        final Map<UUID,BigDecimal> initial=new LinkedHashMap<>();
        final Map<UUID,BigDecimal> initialHp=new LinkedHashMap<>(),initialShield=new LinkedHashMap<>();
        final java.util.Set<UUID> attributedHits=new java.util.HashSet<>();
        double nativeBefore, nativeMinimum, budgetBefore, budgetMinimum; int materialBefore,mechanismPeak; long clockBefore,baselineTick; boolean baseline,passed,sweepGeometryTraced;
        Fixture(UUID owner,String skill,Spec spec){this.owner=owner;this.skill=skill;this.spec=spec;}
    }
    private static Fixture fixture;
    private record Layers(String skill,String hp,String shield,String hpLoss,String shieldLoss,double radius,boolean passed) {}
    private static volatile Layers layers;
    private GameplayAcceptanceEmMd() {}

    /** ShowcaseDriver route: case "gameem" -> { GameplayAcceptanceEmMd.run(mc,arg); wait=6; }. */
    public static void run(Minecraft mc,String action) {
        if(action.startsWith("plan:")){GameplayAcceptanceEmMdPlanning.run(mc,action.substring(5));return;}
        if(action.startsWith("env:")){GameplayAcceptanceEmMdEnvironment.run(mc,action.substring(4));return;}
        if(action.startsWith("modes:")){GameplayAcceptanceEmMdModes.run(mc,action.substring(6));return;}
        if(action.startsWith("utility:")){GameplayAcceptanceEmMdUtility.run(mc,action.substring(8));return;}
        if(action.startsWith("motion:")){GameplayAcceptanceEmMdMotion.run(mc,action.substring(7));return;}
        if(action.startsWith("defense:")){GameplayAcceptanceEmMdDefense.run(mc,action.substring(8));return;}
        if(action.startsWith("construction:")){GameplayAcceptanceEmMdConstruction.run(mc,action.substring(13));return;}
        var server=mc.getSingleplayerServer();require(server!=null&&mc.player!=null,"integrated server and player required");
        UUID owner=mc.player.getUUID();
        if(action.startsWith("setup:")) {
            String id=action.substring(6);require(COMBAT.containsKey(id),"implemented gameplay fixture required: "+id);
            // Existing setup only prepares normal survival learning, finite resources and the active preset.
            LiveInputQa.run(mc,"setup:"+id);
            server.submit(()->{prepare(server.getPlayerList().getPlayer(owner),id);return true;}).join();
            LiveInputQa.assertHealthy();
        } else {
            server.submit(()->{
                var p=server.getPlayerList().getPlayer(owner);
                require(fixture!=null&&fixture.owner.equals(owner),"same gameplay fixture owner");
                if(action.equals("shielded-target")) {
                    require(!fixture.baseline&&fixture.skill.equals("electron_missile"),"prepare real city esper before missile baseline");
                    var previous=fixture.targets.getFirst();var target=cn.academy.port.city.CityNpcContent.NPC.get().create(p.serverLevel());
                    require(target!=null,"real city esper type");var data=new net.minecraft.nbt.CompoundTag();data.putString("Esper","electro");data.putInt("EsperLevel",5);target.readAdditionalSaveData(data);
                    target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);target.setPos(previous.position());
                    require(p.serverLevel().addFreshEntity(target),"real city esper added");target.combat();previous.discard();fixture.targets.set(0,target);
                }
                else if(action.equals("unshielded")) {
                    require(!fixture.baseline,"shield preparation must precede baseline and real input");
                    for(var target:fixture.targets)require(HighPowerCombat.spendShieldFraction(target,BigDecimal.ONE),"finite fixture target shield spent once");
                }
                else if(action.equals("baseline")) baseline(p);
                else if(action.equals("assert")) verify(p);
                else if(action.equals("verify")) require(fixture.passed,"last real mechanism assertion completed");
                else throw new IllegalArgumentException(action);
                return true;
            }).join();
        }
    }
    private static void prepare(ServerPlayer p,String id) {
        require(p!=null&&HighPowerCombat.enabled(p),"real player enrollment exists");
        if(fixture!=null) for(var target:fixture.targets) target.discard();
        fixture=new Fixture(p.getUUID(),id,COMBAT.get(id));
        layers=null;
        var world=p.serverLevel();var feet=p.blockPosition();
        // A legal, clear test lane. No world edits occur after baseline.
        for(int z=-1;z<=24;z++) for(int x=-4;x<=4;x++) {
            world.setBlock(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<=4;y++) world.setBlock(feet.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        world.setWeatherParameters(6000,6000,fixture.spec.rain(),false);
        for(int i=0;i<fixture.spec.targets();i++) {
            var target=EntityType.HUSK.create(world);require(target!=null,"vanilla target creation");
            double x=i==0?0:i%2==1?(i+1)/2.0:-(i/2.0);
            if(!id.equals("scatter_bomb"))x*=2;
            target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);target.addTag("gameem_target");
            target.setPos(p.getX()+x,p.getY(),p.getZ()+((id.equals("scatter_bomb")||id.equals("electron_missile"))?4:6));
            require(world.addFreshEntity(target),"real target added to world");
            require(HighPowerCombat.enroll(target,5),"durable target uses real exact ledger");
            fixture.targets.add(target);
        }
        // Real shallow water makes conductivity biome-independent. No synthetic conductive mark is issued.
        if(fixture.spec.rain())for(int x=-3;x<=3;x++)for(int z=4;z<=8;z++)
            world.setBlock(feet.offset(x,0,z),Blocks.WATER.defaultBlockState(),3);
        LOG.info("QA_GAMEPLAY_PREPARED skill={} targets={} rain={} setupOnly=true realInputRequired=true",id,fixture.targets.size(),fixture.spec.rain());
    }
    private static void baseline(ServerPlayer p) {
        var f=fixture;require(!f.baseline,"no baseline reset after input");
        require(AbilityStorage.get(p).presets.currentSkill(2).equals(f.skill),"normal R preset owns this skill");
        for(var target:f.targets) {
            require(target.isAlive(),"durable target alive at baseline");
            if(f.spec.rain()) require(target.isInWaterOrRain(),"actual environmental conductivity, not a forged mark");
            f.initial.put(target.getUUID(),pool(target));
            f.initialHp.put(target.getUUID(),new BigDecimal(HighPowerCombat.healthText(target)));
            f.initialShield.put(target.getUUID(),HighPowerCombat.shield(target));
        }
        f.nativeBefore=f.nativeMinimum=AbilityStorage.get(p).cp;
        f.budgetBefore=f.budgetMinimum=HighPowerResources.availableCp(p);
        f.clockBefore=paymentClock(p,f.skill);f.baselineTick=p.serverLevel().getGameTime();
        f.materialBefore=material(p,f.skill);f.baseline=true;
        LOG.info("QA_GAMEPLAY_BASELINE skill={} targetPools={} nativeCp={} budgetCp={} material={}",f.skill,f.initial,f.nativeBefore,f.budgetBefore,f.materialBefore);
    }
    /** Observe real debit even when native CP regenerates before a delayed projectile lands. */
    @SubscribeEvent public static void observe(ServerTickEvent.Post event) {
        var f=fixture;if(f==null||!f.baseline)return;
        var p=event.getServer().getPlayerList().getPlayer(f.owner);if(p==null)return;
        if(f.skill.equals("electron_missile")&&!f.targets.isEmpty()){
            var target=f.targets.getFirst();var hp=new BigDecimal(HighPowerCombat.healthText(target));var shield=HighPowerCombat.shield(target);
            layers=new Layers(f.skill,hp.toPlainString(),shield.toPlainString(),f.initialHp.get(target.getUUID()).subtract(hp).toPlainString(),
                    f.initialShield.get(target.getUUID()).subtract(shield).toPlainString(),
                    cn.academy.port.core.SkillEffectParameters.current(f.skill,"reach",AbilityStorage.get(p).exp(f.skill)),f.passed);
        }
        if(f.passed)return;
        f.nativeMinimum=Math.min(f.nativeMinimum,AbilityStorage.get(p).cp);
        f.budgetMinimum=Math.min(f.budgetMinimum,HighPowerResources.availableCp(p));
        int count=switch(f.skill){
            case "scatter_bomb"->cn.academy.port.skill.ScatterBomb.ballCount(p);
            case "electron_missile"->cn.academy.port.skill.ElectronMissile.ballCount(p);
            case "rail_thunder_array"->cn.academy.port.highpower.RailThunderArray.markCount(p);
            case "multicore_canopy"->cn.academy.port.highpower.MulticoreCanopy.cores(p).size();
            default->0;
        };
        f.mechanismPeak=Math.max(f.mechanismPeak,count);
        if(f.skill.equals("disintegration_sweep"))sweepTrace(p);
    }
    /** Native Post is only attribution evidence; success also requires a real persistent pool decrease. */
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent.Post event) {
        var f=fixture;if(f==null||!f.baseline||f.passed||event.getNewDamage()<=0)return;
        if(event.getSource().getEntity() instanceof ServerPlayer p&&p.getUUID().equals(f.owner)
                &&f.initial.containsKey(event.getEntity().getUUID())) {
            // trySpend precedes the real attack. Observe now, before same-tick post-player regeneration.
            observePools(p,f);f.attributedHits.add(event.getEntity().getUUID());
        }
    }
    private static void verify(ServerPlayer p) {
        var f=fixture;require(f.baseline&&!f.passed,"one untouched real-input baseline");
        observePools(p,f);
        if(f.skill.equals("disintegration_sweep"))LOG.info("QA_GAMEPLAY_SWEEP_FINAL pools={} initial={} hits={} cp={} budget={}",f.targets.stream().map(GameplayAcceptanceEmMd::pool).toList(),f.initial,f.attributedHits,f.nativeMinimum,f.budgetMinimum);
        var losses=new LinkedHashMap<UUID,BigDecimal>();int damaged=0;
        for(var target:f.targets) {
            BigDecimal loss=f.initial.get(target.getUUID()).subtract(pool(target));losses.put(target.getUUID(),loss);
            LOG.info("QA_GAMEPLAY_LAYERS skill={} target={} hpBefore={} hpAfter={} shieldBefore={} shieldAfter={} totalLoss={}",f.skill,target.getUUID(),
                    f.initialHp.get(target.getUUID()),HighPowerCombat.healthText(target),f.initialShield.get(target.getUUID()),HighPowerCombat.shield(target),loss);
            if(!f.skill.equals("scatter_bomb"))require(loss.signum()>0,"actual target pool decreased: "+f.skill+" target="+target.getUUID()+" loss="+loss);
            if(loss.signum()>0){damaged++;require(f.attributedHits.contains(target.getUUID()),"normal damage event attributed to casting player: "+target.getUUID());}
        }
        require(damaged>0,"at least one real living target actually damaged");
        if(f.skill.equals("scatter_bomb")||f.skill.equals("electron_missile"))require(f.mechanismPeak>0,"real charge/orb phase preceded actual damage");
        if(f.skill.equals("rail_thunder_array"))require(f.mechanismPeak==3,"three distinct real marked axes preceded the volley");
        if(f.skill.equals("multicore_canopy"))require(f.mechanismPeak>=2,"multiple real deployed cores preceded actual volley damage");
        long clockAfter=paymentClock(p,f.skill);
        boolean closedPayment=clockAfter>f.clockBefore&&clockAfter>f.baselineTick;
        require(HighPowerSkills.contains(f.skill)?f.budgetMinimum<f.budgetBefore||closedPayment:f.nativeMinimum<f.nativeBefore,
                "real debit/reservation dip or closed successful trySpend clock is required, not an active/visual flag");
        if(f.spec.materialSpent()>0) require(f.materialBefore-material(p,f.skill)==f.spec.materialSpent(),"real ammunition debit matches material contract");
        if(f.skill.equals("thunder_bolt")||f.skill.equals("forked_arc")) {
            require(losses.size()==3,"direct target plus two actual secondary targets");
            require(losses.values().iterator().next().signum()>0,"primary target truly hit");
        }
        f.passed=true;
        LOG.info("QA_GAMEPLAY_PASS skill={} witness=actual_world_damage targetLosses={} playerAttributedTargets={} nativeMinimum={} nativeBefore={} budgetMinimum={} budgetBefore={} materialSpent={} mechanismPeak={} paymentClockBefore={} paymentClockAfter={} closedFinitePayment={} noRuntimeStartOrAttackCalled=true",
                f.skill,losses,f.attributedHits.size(),f.nativeMinimum,f.nativeBefore,f.budgetMinimum,f.budgetBefore,f.materialBefore-material(p,f.skill),f.mechanismPeak,f.clockBefore,clockAfter,closedPayment);
    }
    /** QA-only visible ledger, so a shield-only hit cannot be mistaken for a harmless beam. */
    @SubscribeEvent public static void overlay(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event){
        var v=layers;var mc=Minecraft.getInstance();if(v==null||mc.player==null)return;
        var g=event.getGuiGraphics();int x=8,y=45;
        g.fill(x-4,y-4,x+365,y+61,0xc8111824);
        String name=net.minecraft.network.chat.Component.translatable("ac.ability.meltdowner."+v.skill+".name").getString();
        g.drawString(mc.font,name+" | "+(v.passed?"已核验真实伤害":"正在记录真实伤害"),x,y,0xffa7ffcf);
        g.drawString(mc.font,"目标 HP: "+v.hp+"   S: "+v.shield,x,y+14,0xffeef5ff);
        g.drawString(mc.font,"生命损失: "+v.hpLoss+"   护盾损失: "+v.shieldLoss,x,y+28,0xffffdc90);
        g.drawString(mc.font,"HP=生命  S=护盾；护盾吸收时生命可保持不变",x,y+42,0xffb3cff0);
    }
    private static void sweepTrace(ServerPlayer p){
        try{
            var contexts=cn.academy.port.highpower.DisintegrationSweep.class.getDeclaredField("CONTEXTS");contexts.setAccessible(true);Object c=((Map<?,?>)contexts.get(null)).get(p.getUUID());if(c==null)return;
            if(field(c,"plane")!=null&&!fixture.sweepGeometryTraced){
                fixture.sweepGeometryTraced=true;var plane=(cn.academy.port.highpower.core.DisintegrationSweepGeometry.FrozenPlane)field(c,"plane");var hand=cn.academy.port.highpower.DisintegrationSweep.hand(p);var origin=new cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector(hand.x,hand.y,hand.z);
                var nativeTrace=cn.academy.port.highpower.DisintegrationSweep.class.getDeclaredMethod("nativeTrace",ServerPlayer.class,net.minecraft.world.phys.Vec3.class,net.minecraft.world.phys.Vec3.class);nativeTrace.setAccessible(true);
                for(var target:fixture.targets){var bb=target.getBoundingBox();var bounds=new cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds(new cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector(bb.minX,bb.minY,bb.minZ),new cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector(bb.maxX,bb.maxY,bb.maxZ));var contact=cn.academy.port.highpower.core.DisintegrationSweepGeometry.firstContact(plane,origin,bounds,-Math.toRadians(25),Math.toRadians(25));
                    if(contact.isEmpty()){LOG.info("QA_GAMEPLAY_SWEEP_BOUNDARY target={} bounds={} analytic=empty",target.getUUID(),bb);continue;}var end=cn.academy.port.highpower.core.DisintegrationSweepGeometry.rayEndpoint(plane,origin,contact.get().angle());
                    if(end.isEmpty()){LOG.info("QA_GAMEPLAY_SWEEP_BOUNDARY target={} bounds={} analytic={} endpoint=empty",target.getUUID(),bb,contact.get());continue;}
                    Object hit=nativeTrace.invoke(null,p,hand,new net.minecraft.world.phys.Vec3(end.get().x(),end.get().y(),end.get().z()));var point=(net.minecraft.world.phys.Vec3)field(hit,"point");Object actualTarget=field(hit,"target");boolean footprint=cn.academy.port.highpower.core.DisintegrationSweepGeometry.withinFrozenFootprint(plane,new cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector(point.x,point.y,point.z));
                    LOG.info("QA_GAMEPLAY_SWEEP_BOUNDARY target={} bounds={} analytic={} actualTarget={} sameTarget={} actualPoint={} permitted={} footprint={} blocker={} interior={}",target.getUUID(),bb,contact.get(),actualTarget instanceof net.minecraft.world.entity.Entity e?e.getUUID():null,actualTarget==target,point,field(hit,"permitted"),footprint,field(hit,"blocker"),cn.academy.port.highpower.core.DisintegrationSweepGeometry.nativeInteriorCandidate(plane,origin,bounds,contact.get(),Math.toRadians(25),0));
                }
            }
            var slots=(List<?>)field(c,"targets");var details=new java.util.ArrayList<String>();
            for(Object slot:slots)details.add("target="+((net.minecraft.world.entity.Entity)field(slot,"entity")).getUUID()+",angle="+field(slot,"angle")+",attempted="+field(slot,"attempted")+",share="+field(slot,"share"));
            LOG.info("QA_GAMEPLAY_SWEEP_TRACE tick={} released={} plane={} angle={} frozen={} attempts={} successes={} paid={} receipt={}",p.serverLevel().getGameTime(),field(c,"released"),field(c,"plane"),field(c,"angle"),details,field(c,"attempts"),field(c,"successes"),field(c,"paid"),cn.academy.port.highpower.DisintegrationSweep.receipt(p));
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static Object field(Object object,String name)throws ReflectiveOperationException{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void observePools(ServerPlayer p,Fixture f) {
        f.nativeMinimum=Math.min(f.nativeMinimum,AbilityStorage.get(p).cp);f.budgetMinimum=Math.min(f.budgetMinimum,HighPowerResources.availableCp(p));
    }
    private static long paymentClock(ServerPlayer p,String id){return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong(id.equals("forked_arc")?"next_cast":"next_"+id);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beforeRegeneration(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){
        var f=fixture;if(f!=null&&f.baseline&&!f.passed&&event.getEntity() instanceof ServerPlayer p&&p.getUUID().equals(f.owner))observePools(p,f);
    }
    private static BigDecimal pool(net.minecraft.world.entity.LivingEntity target) {return new BigDecimal(HighPowerCombat.healthText(target)).add(HighPowerCombat.shield(target));}
    private static int material(ServerPlayer p,String id) {
        if(id.equals("railgun"))return p.getMainHandItem().getCount();
        if(id.equals("iron_sand_lance"))return p.getOffhandItem().is(IronSandMedium.ITEM.get())?p.getOffhandItem().getCount():0;
        return 0;
    }
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException("QA_GAMEPLAY_FAILED "+message);}
}
