package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityCalculation;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.skill.RadiationMarks;
import cn.academy.port.skill.ThreateningTeleport;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/** Opt-in passive-learning and real native modifier witnesses. Never learns the tested record or calls a skill. */
public final class CanonPassiveAcceptanceQa {
    private static CompletableFuture<JsonObject> pending;
    private static String operation;
    private static Snapshot before;
    private record Snapshot(UUID owner,String category,String skill,Object world,AbilityProgress state,long tick,
                            double cp,double overload,double recovery,double critical0,double critical1,double critical2) {}
    private CanonPassiveAcceptanceQa() {}
    public static boolean step(Minecraft mc,String op){
        require(Boolean.getBoolean("academy.qa.showcase")&&Boolean.getBoolean("academy.qa.fx4rollout"),"opt-in complete acceptance only");
        require(mc.player!=null&&mc.level!=null&&mc.getSingleplayerServer()!=null,"integrated isolated player/world");
        if(pending==null){operation=op;pending=new CompletableFuture<>();var id=mc.player.getUUID();var server=mc.getSingleplayerServer();
            server.execute(()->{try{var p=server.getPlayerList().getPlayer(id);require(p!=null,"same actual player");pending.complete(run(p,op));}catch(Throwable e){pending.completeExceptionally(e);}});}
        require(op.equals(operation),"same asynchronous operation");if(!pending.isDone())return false;
        JsonObject result=pending.join();pending=null;operation=null;write(result,mc);return true;
    }
    private static JsonObject run(ServerPlayer p,String op) throws ReflectiveOperationException {
        if(op.equals("passive-radiation-hit")){
            var state=AbilityStorage.get(p);require(state.category.equals("meltdowner")&&state.learned("rad_intensify"),"actual learned radiation passive");
            var targets=p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(16),m->m.getTags().contains("passive_radiation_target"));
            require(targets.size()==1,"same real ordinary target");var target=targets.getFirst();var data=target.getPersistentData();
            int ticks=data.getInt("academy:md_mark_ticks");double rate=data.getFloat("academy:md_mark_rate"),expected=RadiationMarks.rate(state.exp("rad_intensify"));
            require(ticks>0&&ticks<=60&&Math.abs(rate-expected)<1e-6,"actual ElectronBomb hit wrote native target mark/rate; no mark setter in QA");
            var row=identity(p,"rad_intensify","radiation-hit");row.addProperty("target",target.getUUID().toString());row.addProperty("nativeMarkTicks",ticks);row.addProperty("nativeMarkRate",rate);row.addProperty("expectedRate",expected);row.addProperty("noQaMarkWrite",true);row.addProperty("pass",true);return row;
        }
        int split=op.indexOf(':');require(split>0,"operation:category/id");String action=op.substring(0,split),selector=op.substring(split+1);int slash=selector.indexOf('/');
        require(slash>0,"category-qualified passive record");String category=selector.substring(0,slash),id=selector.substring(slash+1);
        var def=SkillCatalog.REGISTERED.stream().filter(s->s.category().equals(category)&&s.id().equals(id)).findFirst().orElseThrow();require(!def.controllable(),"genuine non-controllable passive");
        var s=AbilityStorage.get(p);
        if(action.equals("passive-reset")){
            // Fresh precondition only. Actual tested learning still requires native GUI confirmation and finite IF debit.
            s.selectCategory(category);s.setLevel(5);s.activated=true;s.overloadFine=true;s.interfering=false;
            AbilityStorage.save(p);AcademyNetwork.sync(p);before=null;
            var row=identity(p,id,"reset-precondition");row.addProperty("testedRecordLearned",s.learned(id));row.addProperty("pass",!s.learned(id));return row;
        }
        require(s.category.equals(category),"same passive category");
        if(action.equals("passive-before")){
            require(!s.learned(id),"tested passive unlearned after prerequisite-only native preparation");
            before=snapshot(p,id);var row=identity(p,id,"before-learning");row.add("nativeModifiers",json(before));row.addProperty("testedRecordLearned",false);return row;
        }
        require(action.equals("passive-after"),"known passive operation");
        require(before!=null&&before.owner.equals(p.getUUID())&&before.category.equals(category)&&before.skill.equals(id)&&before.world==p.serverLevel()&&before.state==s,"same original passive witness");
        require(s.learned(id),"native GUI must have actually learned the tested passive");Snapshot after=snapshot(p,id);var row=identity(p,id,"after-learning");
        row.add("beforeNativeModifiers",json(before));row.add("afterNativeModifiers",json(after));row.addProperty("testedRecordLearned",true);
        row.add("finiteDeveloperIf",finiteIf(p,category,id));
        switch(id){
            case "brain_course" -> close(after.cp-before.cp,1000,"real cached maxCP increases1000");
            case "brain_course_advanced" -> {close(after.cp-before.cp,1500,"real cached maxCP increases1500");close(after.overload-before.overload,100,"real cached overload increases100");}
            case "mind_course" -> close(after.recovery/before.recovery,1.2,"real native CP_RECOVERY calculation multiplies1.2");
            case "dim_folding_theorem" -> {close(after.critical0-before.critical0,.1,"native critical tier0 probability increases.1 at actual newly learned mastery0");close(after.critical1,before.critical1,"higher tier1 unchanged");close(after.critical2,before.critical2,"higher tier2 unchanged");}
            case "space_fluct" -> {close(after.critical0-before.critical0,.18,"native tier0 increases.18");close(after.critical1-before.critical1,.1,"native tier1 increases.1");close(after.critical2-before.critical2,.01,"native tier2 increases.01");}
            case "rad_intensify" -> {double mastery=s.exp(id),expected=RadiationMarks.displayedMastery(s.maxCp(),s.initCp(5));close(mastery,expected,"displayed actual maxCP-dependent mastery");row.addProperty("radiationMastery",mastery);row.addProperty("radiationRate",RadiationMarks.rate(mastery));row.addProperty("additionalActualHitRequired",true);}
            default -> throw new IllegalStateException("Unspecified passive behavior oracle: "+id);
        }
        row.addProperty("pass",true);row.addProperty("observedScope","actual native learning/developer debit and player modifier APIs; random critical proc pixel playback is separate");before=null;return row;
    }
    private static Snapshot snapshot(ServerPlayer p,String skill) throws ReflectiveOperationException{
        var s=AbilityStorage.get(p);Method calculate=AbilityProgress.class.getDeclaredMethod("calculate",AbilityCalculation.Kind.class,double.class);calculate.setAccessible(true);
        double recovery=((Number)calculate.invoke(s,AbilityCalculation.Kind.CP_RECOVERY,1.0)).doubleValue();
        return new Snapshot(p.getUUID(),s.category,skill,p.serverLevel(),s,p.serverLevel().getGameTime(),s.baseCp(),s.baseOverload(),recovery,
                ThreateningTeleport.criticalProbability(s,0),ThreateningTeleport.criticalProbability(s,1),ThreateningTeleport.criticalProbability(s,2));
    }
    private static JsonObject finiteIf(ServerPlayer p,String category,String skill) throws ReflectiveOperationException{
        Field f=LiveInputQa.class.getDeclaredField("learningProbe");f.setAccessible(true);Object probe=f.get(null);require(probe!=null,"real native learning probe");
        require(p.getUUID().equals(field(probe,"owner"))&&skill.equals(field(probe,"id"))&&category.equals(field(probe,"category")),"same actual finite-IF learning identity");
        double start=((Number)field(probe,"before")).doubleValue(),expected=((Number)field(probe,"expected")).doubleValue();
        var type=(DeveloperType)field(probe,"type");Object machine=field(probe,"machine");ItemStack stack=(ItemStack)field(probe,"stack");double end;
        if(machine instanceof MachineDeveloperBlockEntity m){require(m.available()&&m.developerType()==type,"same real complete developer");end=m.battery().getEnergy();}
        else {require(p.getMainHandItem()==stack,"same real portable developer");end=new DeveloperItemEnergy(stack,type).energy();}
        require(Math.abs(start-end-expected)<1e-6,"actual native developer IF debit");require(Double.isFinite(start)&&Double.isFinite(end)&&expected>0&&end>=0,"finite positive debit");
        var row=new JsonObject();row.addProperty("type",type.name());row.addProperty("before",start);row.addProperty("after",end);row.addProperty("expectedNativeDebit",expected);row.addProperty("actualDebit",start-end);return row;
    }
    private static Object field(Object object,String name)throws ReflectiveOperationException{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static JsonObject identity(ServerPlayer p,String id,String stage){var j=new JsonObject();j.addProperty("stage",stage);j.addProperty("category",AbilityStorage.get(p).category);j.addProperty("skill",id);j.addProperty("owner",p.getUUID().toString());j.addProperty("tick",p.serverLevel().getGameTime());return j;}
    private static JsonObject json(Snapshot s){var j=new JsonObject();j.addProperty("tick",s.tick);j.addProperty("cachedMaxCp",s.cp);j.addProperty("cachedMaxOverload",s.overload);j.addProperty("nativeRecoveryMultiplier",s.recovery);j.addProperty("criticalTier0",s.critical0);j.addProperty("criticalTier1",s.critical1);j.addProperty("criticalTier2",s.critical2);return j;}
    private static void close(double value,double expected,String why){require(Double.isFinite(value)&&Math.abs(value-expected)<=1e-5*Math.max(1,Math.abs(expected)),why+" got="+value+" expected="+expected);}
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("QA_PASSIVE_ACCEPTANCE "+why);}
    private static void write(JsonObject data,Minecraft mc){
        Path root=Path.of(System.getProperty("academy.qa.fx4.root",".")).toAbsolutePath().normalize();String run=System.getProperty("academy.qa.fx4.run","");
        require(run.matches("[A-Za-z0-9_-]{8,64}")&&mc.gameDirectory.toPath().toAbsolutePath().normalize().startsWith(root),"isolated current-run output");
        var j=new JsonObject();j.addProperty("run",run);j.addProperty("event","passive-acceptance");j.addProperty("time",System.currentTimeMillis());j.add("data",data);
        try{Files.writeString(root.resolve("passive-receipts.jsonl"),j+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(java.io.IOException e){throw new IllegalStateException(e);}
        LogUtils.getLogger().info("QA_PASSIVE_ACCEPTANCE {}",j);
    }
}
