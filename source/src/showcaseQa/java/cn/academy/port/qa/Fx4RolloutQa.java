package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.CastPoseClient;
import cn.academy.port.highpower.HighPowerResources;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.lwjgl.glfw.GLFW;

/** Opt-in recording witnesses only: no cast, resource debit, packet or model is manufactured here. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class Fx4RolloutQa {
    private static final boolean ENABLED=Boolean.getBoolean("academy.qa.showcase")&&Boolean.getBoolean("academy.qa.fx4rollout");
    private static final String RUN=System.getProperty("academy.qa.fx4.run","");
    private static final Path ROOT=Path.of(System.getProperty("academy.qa.fx4.root",".")).toAbsolutePath().normalize();
    private static final Set<String> NEW=Set.of("mental_out","dark_matter","nitrogen_armor","aero_hand","hydro_hand","float_dial","liquid_shadow","paper_psychokinesis");
    private static final Set<String> CHARGED_FIRE=Set.of("meltdowner","plasma_cannon","railgun","thunder_clap");
    private static Consumer<CanonVfx.Visual> receiver;
    private static volatile Session active;
    private static int serial;
    private Fx4RolloutQa() {}

    private static final class Session {
        final String skill,mode,category;final int level,serial;final UUID owner;final Object world,server;
        volatile Sample before;volatile CompletableFuture<Sample> pending;volatile RuntimeException error;
        volatile double minCp=Double.POSITIVE_INFINITY,minNativeCp=Double.POSITIVE_INFINITY;
        volatile long maxClock;volatile int maxCooldown,serverSamples;
        boolean ready,ending,rPressed;int keyDown,keyUp,visuals,visualEnds,releasedFrames,heldFrames;
        Session(Minecraft mc,String skill,String mode){
            this.skill=skill;this.mode=mode;this.serial=++Fx4RolloutQa.serial;owner=mc.player.getUUID();world=mc.level;server=mc.getSingleplayerServer();
            var entry=SkillCatalog.REGISTERED.stream().filter(s->s.id().equals(skill)).findFirst().orElseThrow();
            category=entry.category();level=entry.level();
        }
    }
    private record Sample(long tick,double cp,double nativeCp,long clock,int cooldown,int playerLevel,
                          boolean casterInWater,int nearbyMobs,int mobsInWater,int ownedMechanisms,String position,String dimension) {
        JsonObject json(){var j=new JsonObject();j.addProperty("tick",tick);j.addProperty("cp",cp);j.addProperty("nativeCp",nativeCp);
            j.addProperty("skillClock",clock);j.addProperty("cooldown",cooldown);j.addProperty("playerLevel",playerLevel);
            j.addProperty("casterInWater",casterInWater);j.addProperty("nearbyMobs",nearbyMobs);j.addProperty("mobsInWater",mobsInWater);
            j.addProperty("ownedMechanisms",ownedMechanisms);
            j.addProperty("position",position);j.addProperty("dimension",dimension);return j;}
    }
    private static Sample sample(ServerPlayer p,String skill){
        var state=AbilityStorage.get(p);var ledger=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        var mobs=p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(32));
        long clock=Math.max(ledger.getLong("next_"+skill),skill.equals("forked_arc")?ledger.getLong("next_cast"):0);
        return new Sample(p.serverLevel().getGameTime(),HighPowerResources.cp(p),state.cp,clock,
            state.cooldowns.getOrDefault(skill,0),state.level,p.isInWaterOrBubble(),mobs.size(),
            (int)mobs.stream().filter(Mob::isInWaterOrBubble).count(),ownedMechanisms(p,skill),p.position().toString(),p.serverLevel().dimension().location().toString());
    }
    /** Read the real owner-scoped lifecycle pool; never create or remove its entries. */
    private static int ownedMechanisms(ServerPlayer p,String skill){
        String type,field;
        if(skill.equals("liquid_dismiss")){type="CanonLiquidSkills";field="ACTIVE";}
        else if(skill.equals("float_item_release")||skill.equals("float_release_field")){type="CanonFloatItems";field="ACTIVE";}
        else if(skill.equals("paper_retract")){type="CanonPaperContent";field="LEASES";}
        else return 0;
        try{var f=Class.forName("cn.academy.port.canon."+type).getDeclaredField(field);f.setAccessible(true);
            int n=0;for(Object lease:((java.util.Map<?,?>)f.get(null)).values()){
                var owner=lease.getClass().getDeclaredField("owner");owner.setAccessible(true);Object value=owner.get(lease);
                if(value==p||p.getUUID().equals(value))n++;
            }return n;
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Lifecycle evidence bridge",e);}
    }
    private static void request(Minecraft mc,Session s){
        var server=mc.getSingleplayerServer();require(server!=null,"integrated server required");
        s.pending=new CompletableFuture<>();server.execute(()->{
            try{var p=server.getPlayerList().getPlayer(s.owner);require(p!=null,"same server player present");s.pending.complete(sample(p,s.skill));}
            catch(RuntimeException e){s.pending.completeExceptionally(e);}
        });
    }
    /** begin:skill:real|preview and end; asynchronous server reads repeat without blocking the render thread. */
    public static boolean step(Minecraft mc,String op){
        require(ENABLED,"requires academy.qa.fx4rollout=true");require(mc.level!=null&&mc.player!=null,"world/player required");
        require(RUN.matches("[A-Za-z0-9_-]{8,64}")&&mc.gameDirectory.toPath().toAbsolutePath().normalize().startsWith(ROOT),"isolated root/run required");
        require(System.getProperty("academy.qa.canon.role","").isEmpty(),"do not combine competing CanonFormal receiver wrappers");
        if(op.startsWith("fixture:"))return CanonCompleteFixturesQa.step(mc,op.substring(8));
        install();
        if(op.startsWith("begin:")){
            String[] bits=op.split(":");require(bits.length==3&&Set.of("real","preview").contains(bits[2]),"begin:skill:real|preview");
            Session s=active;
            if(s==null){s=new Session(mc,bits[1],bits[2]);active=s;request(mc,s);}
            require(s.skill.equals(bits[1])&&s.mode.equals(bits[2])&&!s.ready&&!s.ending,"previous clip must end before next begin");
            if(!s.pending.isDone())return false;
            s.before=s.pending.join();s.minCp=s.before.cp();s.minNativeCp=s.before.nativeCp();s.maxClock=s.before.clock();s.maxCooldown=s.before.cooldown();s.ready=true;
            var data=base(s);data.add("server",s.before.json());write("begin",data);return true;
        }
        require(op.equals("end")&&active!=null,"known operation and active clip required");Session s=active;
        require(s.ready&&s.world==mc.level&&s.owner.equals(mc.player.getUUID()),"same client world/player required");
        if(s.error!=null)throw s.error;
        if(!s.ending){s.ending=true;request(mc,s);}
        if(!s.pending.isDone())return false;
        Sample after=s.pending.join();track(s,after);
        boolean paid=s.minCp<s.before.cp()-1e-6||s.minNativeCp<s.before.nativeCp()-1e-6||s.maxClock>s.before.clock()||s.maxCooldown>s.before.cooldown();
        boolean committedCooldown=s.maxClock>s.before.clock()||s.maxCooldown>s.before.cooldown();
        // Charged OWN skills may spend upkeep before an early release aborts.
        // Their ordinary fire cooldown is required in addition to the native release.
        boolean classic=paid&&s.releasedFrames>0&&(!CHARGED_FIRE.contains(s.skill)||committedCooldown);
        boolean lifecycleEnd=s.visuals==0&&s.visualEnds>0&&s.before.ownedMechanisms()>0&&after.ownedMechanisms()<s.before.ownedMechanisms();
        boolean accepted=s.mode.equals("real")&&s.keyDown>0&&s.keyUp>0&&(NEW.contains(s.category)?paid&&(s.visuals>0||lifecycleEnd):classic);
        var data=base(s);data.add("serverBefore",s.before.json());data.add("serverAfter",after.json());
        data.addProperty("minimumCp",s.minCp);data.addProperty("minimumNativeCp",s.minNativeCp);data.addProperty("maximumSkillClock",s.maxClock);
        data.addProperty("maximumCooldown",s.maxCooldown);data.addProperty("serverSamples",s.serverSamples);data.addProperty("paymentEvidence",paid);
        data.addProperty("committedCooldownEvidence",committedCooldown);data.addProperty("chargedFireRequiresCooldown",CHARGED_FIRE.contains(s.skill));
        data.addProperty("physicalRDown",s.keyDown);data.addProperty("physicalRUp",s.keyUp);data.addProperty("serverVisuals",s.visuals);data.addProperty("serverVisualEnds",s.visualEnds);
        data.addProperty("nativeHeldSignatureFrames",s.heldFrames);data.addProperty("nativeReleasedSignatureFrames",s.releasedFrames);
        data.addProperty("lifecycleEndOnly",NEW.contains(s.category)&&lifecycleEnd);
        data.addProperty("confirmedRealCast",accepted);data.addProperty("status",s.mode.equals("preview")?"visual_preview_only":accepted?"confirmed_real_cast":"rejected_or_unconfirmed");
        data.addProperty("scope","read-only server ledger and received presentation/release witnesses; pixel review and gameplay acceptance remain separate");
        write("end",data);active=null;return true;
    }
    private static JsonObject base(Session s){var j=new JsonObject();j.addProperty("clip",s.serial);j.addProperty("skill",s.skill);j.addProperty("mode",s.mode);j.addProperty("category",s.category);j.addProperty("skillLevel",s.level);return j;}
    private static void track(Session s,Sample sample){s.minCp=Math.min(s.minCp,sample.cp());s.minNativeCp=Math.min(s.minNativeCp,sample.nativeCp());s.maxClock=Math.max(s.maxClock,sample.clock());s.maxCooldown=Math.max(s.maxCooldown,sample.cooldown());s.serverSamples++;}
    private static void install(){
        if(CanonVfx.clientReceiver==receiver)return;
        var downstream=CanonVfx.clientReceiver;
        receiver=v->{downstream.accept(v);Session s=active;var mc=Minecraft.getInstance();
            if(s==null||!s.ready||v==null||mc.level!=s.world||!s.owner.equals(v.owner())||!s.skill.equals(v.skill()))return;
            if(v.phase()==CanonVfx.Phase.END)s.visualEnds++;else s.visuals++;
            var d=base(s);d.addProperty("phase",v.phase().name());d.addProperty("owner",v.owner().toString());d.addProperty("entity",v.entity());
            d.addProperty("origin",v.origin().toString());d.addProperty("endpoint",v.endpoint().toString());d.addProperty("duration",v.duration());
            d.addProperty("source","original CanonVfx.clientReceiver after downstream; preview command bypasses this network receiver");write("server-visual",d);
        };CanonVfx.clientReceiver=receiver;
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post e){if(ENABLED&&Minecraft.getInstance().level!=null)install();}
    @SubscribeEvent public static void server(ServerTickEvent.Post e){Session s=active;if(!ENABLED||s==null||!s.ready||s.ending)return;
        try{var p=e.getServer().getPlayerList().getPlayer(s.owner);if(p!=null)track(s,sample(p,s.skill));}catch(RuntimeException ex){s.error=ex;}
    }
    /** These selected native runtimes can commit after the ordinary server sampler and
     * recover before its next call. Observe their permanent wallets at both real
     * tick boundaries; a reservation is never accepted as a debit. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void afterNativePayments(ServerTickEvent.Post e){
        Session s=active;if(!ENABLED||s==null||s.server!=e.getServer())return;
        var p=e.getServer().getPlayerList().getPlayer(s.owner);
        if(p!=null)permanentTickLedger(p,"native_server_tick_post_lowest");
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void beforeNativeRecovery(PlayerTickEvent.Pre e){
        if(e.getEntity() instanceof ServerPlayer p)permanentTickLedger(p,"native_player_tick_pre_highest");
    }
    private static void permanentTickLedger(ServerPlayer p,String source){
        Session s=active;
        if(!ENABLED||s==null||!s.ready||s.ending||!Set.of("spatial_relay","multidimensional_landing_array","scatter_locator_plate").contains(s.skill)
                ||p.server!=s.server||!p.server.isSameThread()||!s.owner.equals(p.getUUID())
                ||p.server.getPlayerList().getPlayer(s.owner)!=p
                ||!p.serverLevel().dimension().location().toString().equals(s.before.dimension())
                ||HighPowerResources.hasReservation(p))return;
        try{
            Sample observed=sample(p,s.skill);
            boolean changed=observed.cp()<s.minCp-1e-6||observed.nativeCp()<s.minNativeCp-1e-6
                    ||observed.clock()>s.maxClock||observed.cooldown()>s.maxCooldown;
            track(s,observed);
            if(!changed)return;
            var data=base(s);data.addProperty("samplingSource",source);
            data.addProperty("actualServerThread",true);data.addProperty("harvestReservationActive",false);
            data.addProperty("ledgerRead","HighPowerResources.cp; full permanent ledger, not availableCp");
            data.add("server",observed.json());write("native-ledger-checkpoint",data);
        }catch(RuntimeException ex){s.error=ex;}
    }
    /** Native destroyBlock settles its real removal before playerDestroy spawns loot.
     * Sample full CP at that irreversible native checkpoint, before same-tick recovery.
     * A live reservation is deliberately excluded: a transient claim is not payment. */
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void nativeLoot(EntityJoinLevelEvent e){
        Session s=active;
        if(!ENABLED||s==null||!s.ready||s.ending||e.isCanceled()||e.getLevel().isClientSide()
                ||!(e.getEntity() instanceof ItemEntity item)||!Set.of("beam_etching","disintegration_sweep","point_meltdown").contains(s.skill))return;
        var server=e.getLevel().getServer();if(server==null||!server.isSameThread())return;
        var p=server.getPlayerList().getPlayer(s.owner);
        if(p==null||p.serverLevel()!=e.getLevel()||HighPowerResources.hasReservation(p))return;
        try{
            Sample observed=sample(p,s.skill);track(s,observed);
            var data=base(s);data.addProperty("samplingSource","native_item_join_after_permanent_removal_debit");
            data.addProperty("actualServerThread",true);data.addProperty("harvestReservationActive",false);
            data.addProperty("ledgerRead","HighPowerResources.cp; full permanent ledger, not availableCp");
            data.addProperty("nativeItem",item.getUUID().toString());data.addProperty("nativeItemPosition",item.position().toString());
            data.add("server",observed.json());write("native-ledger-checkpoint",data);
        }catch(RuntimeException ex){s.error=ex;}
    }
    @SubscribeEvent public static void input(InputEvent.Key e){
        var screen=Minecraft.getInstance().screen;
        observeKey(e.getKey(),e.getAction(),"world_input_event",screen==null?null:screen.getClass().getName());
    }
    /** A production selection screen may consume release while committing its real selection.
     * Observe the native Pre callback without consuming it or closing that screen. */
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void screenReleased(ScreenEvent.KeyReleased.Pre e){
        observeKey(e.getKeyCode(),GLFW.GLFW_RELEASE,"native_screen_key_released_pre",e.getScreen().getClass().getName());
    }
    private static void observeKey(int key,int action,String entry,String screen){
        Session s=active;
        if(!ENABLED||s==null||!s.ready||key!=GLFW.GLFW_KEY_R||action!=GLFW.GLFW_PRESS&&action!=GLFW.GLFW_RELEASE)return;
        boolean counted=false;
        if(action==GLFW.GLFW_PRESS){s.keyDown++;s.rPressed=true;counted=true;}
        else if(s.rPressed){s.keyUp++;s.rPressed=false;counted=true;}
        // Both GUI and world callbacks may observe the same release. A held-state
        // edge deduplicates those entries without losing multiple true pairs in a tick.
        var data=base(s);data.addProperty("entry",entry);data.addProperty("nativeScreenClass",screen);
        data.addProperty("key",key);data.addProperty("action",action);data.addProperty("counted",counted);
        data.addProperty("physicalRDownAfter",s.keyDown);data.addProperty("physicalRUpAfter",s.keyUp);
        try{write("physical-key-observed",data);}catch(RuntimeException ex){s.error=ex;}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){Session s=active;if(!ENABLED||s==null||!s.ready||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level!=s.world)return;
        for(var cast:CastPoseClient.signatures(0))if(cast.local()&&cast.entity()==mc.player.getId()&&s.skill.equals(cast.skill())){
            if(cast.sinceRelease()>=0)s.releasedFrames++;else s.heldFrames++;
        }
    }
    private static synchronized void write(String event,JsonObject data){
        var row=new JsonObject();row.addProperty("run",RUN);row.addProperty("event",event);row.addProperty("time",System.currentTimeMillis());
        var mc=Minecraft.getInstance();if(mc.level!=null)row.addProperty("clientTick",mc.level.getGameTime());row.add("data",data);
        try{Files.createDirectories(ROOT);Files.writeString(ROOT.resolve("fx4-receipts.jsonl"),row+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(java.io.IOException e){throw new IllegalStateException(e);}LogUtils.getLogger().info("QA_FX4_ROLLOUT {}",row);
    }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_FX4_ROLLOUT "+why);}
}
