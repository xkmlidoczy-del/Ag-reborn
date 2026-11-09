package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.client.CastPoseClient;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.core.CastPose;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Two separate native clients. The observer only reads received state and the actual rendered remote player. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class DualPoseQa {
    private static final boolean OBSERVER="observer".equals(System.getProperty("academy.qa.dual"));
    /** Host-side recording of the peer's poses, so the reverse direction (observer casts, host sees) is checkable. */
    private static final boolean RECORD=Boolean.getBoolean("academy.qa.dualRecord");
    /** Let the observer run its own script after joining, instead of being driven only by the observer state machine. */
    private static final boolean SCRIPTED=Boolean.getBoolean("academy.qa.dualScript");
    private static final Set<String> SEEN=new LinkedHashSet<>();
    private static final Path REPORT=Path.of(System.getProperty("academy.qa.dualReport","dual-observer.txt"));
    private static final Path HOST_REPORT=Path.of(System.getProperty("academy.qa.dualReportHost","dual-host.txt"));
    private static final int PORT=Integer.getInteger("academy.qa.dualPort",25589);
    private static boolean connecting,joined,positioned;
    private static int ticks,frames;
    private static long lastTrace;
    private static final java.util.Queue<String> SHOTS=new java.util.ArrayDeque<>();
    private DualPoseQa() {}

    static boolean observerTick(Minecraft mc) {
        if(!OBSERVER) return false;
        if(++ticks>9000) throw new IllegalStateException("dual observer timed out: "+SEEN);
        if(mc.level==null || mc.player==null) {
            if(joined) {
                try {
                    var f=CastPoseClient.class.getDeclaredField("CASTS"); f.setAccessible(true);
                    if(!((Map<?,?>)f.get(null)).isEmpty()) return true;
                    for(String field:new String[]{"CUES","POSES"}) {
                        var construct=Class.forName("cn.academy.port.client.ConstructClient").getDeclaredField(field); construct.setAccessible(true);
                        if(!((Map<?,?>)construct.get(null)).isEmpty()) return true;
                    }
                    SEEN.add("disconnect-clean"); write();
                    LogUtils.getLogger().info("QA_DUAL_DONE remoteFrames={} states={}",frames,SEEN); mc.stop();
                } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
            } else if(mc.screen instanceof TitleScreen && ticks>40 && !connecting) {
                connecting=true;
                ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString("127.0.0.1:"+PORT),new ServerData("ACA QA","127.0.0.1:"+PORT,ServerData.Type.OTHER),false,null);
            }
            return true;
        }
        joined=true;
        String shot=SHOTS.poll();
        if(shot!=null) net.minecraft.client.Screenshot.grab(mc.gameDirectory,"remote_"+shot.toLowerCase()+".png",mc.getMainRenderTarget(),msg->{});
        mc.options.pauseOnLostFocus=false;
        var remote=mc.level.players().stream().filter(p->p!=mc.player).findFirst().orElse(null);
        if(remote!=null) {
            var d=remote.getEyePosition().subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
        }
        // A scripted observer casts after joining; the driver then runs its script while this state machine still
        // handles the connection and the auto-aim above. The diagnostic below is read-only and shows whether a
        // local press produced a client-side cast for the granted skill.
        if(SCRIPTED && ticks%20==0) observerDiagnostic(mc);
        return !SCRIPTED;
    }

    private static void observerDiagnostic(Minecraft mc) {
        try {
            var f=CastPoseClient.class.getDeclaredField("CASTS"); f.setAccessible(true);
            int casts=((Map<?,?>)f.get(null)).size();
            var st=cn.academy.port.client.AcademyClient.state;
            String slot2=st.presets.currentSkill(2);
            LogUtils.getLogger().info("QA_DUAL_OBSERVER_STATE casts={} slot2={} canUse={} activated={} category={} key82Down={}",
                casts,slot2,st.canUse(slot2),st.isActivated(),st.category,
                cn.academy.port.client.AcademyClient.SLOTS[2].isDown());
        } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    static boolean host(Minecraft mc,String action) {
        if(action.startsWith("cast-ready:")){
            String skill=action.substring(11);var entry=SkillCatalog.REGISTERED.stream().filter(e->e.id().equals(skill)).findFirst().orElseThrow();
            var state=cn.academy.port.client.AcademyClient.state;
            boolean ready=mc.screen==null&&mc.player!=null&&mc.level!=null&&mc.level.players().size()>=2
                    &&state.category.equals(entry.category())&&state.level>=entry.level()&&state.canUse(skill)
                    &&skill.equals(state.presets.currentSkill(2))&&cn.academy.port.client.AcademyClient.serverSingleEpoch()>0
                    &&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable;
            if(skill.equals(cn.academy.port.highpower.IronSandLance.ID))ready&=mc.player!=null
                    &&mc.player.getOffhandItem().is(cn.academy.port.highpower.IronSandMedium.ITEM.get())
                    &&mc.player.getOffhandItem().getCount()>=cn.academy.port.highpower.IronSandMedium.UNITS_PER_CAST;
            if(ready)LogUtils.getLogger().info("QA_DUAL_CAST_READY skill={} category={} level={} epoch={} material={} survival=true",skill,state.category,state.level,
                    cn.academy.port.client.AcademyClient.serverSingleEpoch(),mc.player.getOffhandItem().getCount());
            return ready;
        }
        if(action.equals("await-disconnect")){
            if(!OBSERVER)throw new IllegalStateException("Only the observer waits for actual host disconnect");
            return false; // observerTick alone verifies cleared actual client state after the host leaves.
        }
        if(action.equals("await-peer")){
            if(!RECORD)throw new IllegalStateException("Reverse host recording required");
            if(!Files.isRegularFile(HOST_REPORT))return false;
            try{
                var states=Set.copyOf(Files.readAllLines(HOST_REPORT));
                for(String expected:System.getProperty("academy.qa.dualExpectedHost","").split(","))
                    if(!expected.isBlank()&&!states.contains(expected))return false;
                return true;
            }catch(java.io.IOException error){throw new IllegalStateException(error);}
        }
        var server=mc.getSingleplayerServer();
        if(action.equals("publish")) {
            server.setUsesAuthentication(false);
            if(!server.publishServer(GameType.SURVIVAL,false,PORT)) throw new IllegalStateException("LAN publish failed");
            LogUtils.getLogger().info("QA_DUAL_PUBLISHED port={}",PORT); return true;
        }
        if(action.equals("ready")) {
            if(mc.level.players().size()<2) return false;
            if(!positioned) {
                positioned=true;
                var id=mc.player.getUUID();
                server.execute(()->{ for(var p:server.getPlayerList().getPlayers()) if(!p.getUUID().equals(id)) {
                    p.teleportTo(5.5,-60,-3.5); p.setGameMode(GameType.CREATIVE);
                    p.getAbilities().flying=true; p.onUpdateAbilities();
                }});
            }
            return true;
        }
        if(action.startsWith("grant:")) {
            String skill=action.substring(6); var hostId=mc.player.getUUID();
            server.execute(()->{ for(var p:server.getPlayerList().getPlayers()) if(!p.getUUID().equals(hostId)) {
                try { grant(skill,p); } catch(RuntimeException e) { LogUtils.getLogger().error("QA_DUAL_GRANT_FAILED {}",e.toString()); }
            }});
            return true;
        }
        if(action.equals("verify")) {
            try {
                // Forward direction (host casts, observer renders). Skipped when the host only verifies the reverse
                // direction, because a reverse-only observer never writes the forward report.
                if(!RECORD) {
                    String report=Files.readString(REPORT);
                    for(String expected:System.getProperty("academy.qa.dualExpected","").split(","))
                        if(!expected.isBlank() && !report.contains("\n"+expected+"\n")) throw new IllegalStateException("missing observer state: "+expected+" report="+report);
                    LogUtils.getLogger().info("QA_DUAL_HOST_PASS {}",report.replace('\n',' '));
                }
                if(RECORD) {
                    String host=Files.readString(HOST_REPORT);
                    for(String expected:System.getProperty("academy.qa.dualExpectedHost","").split(","))
                        if(!expected.isBlank() && !host.contains("\n"+expected+"\n")) throw new IllegalStateException("host never saw peer state: "+expected+" report="+host);
                    LogUtils.getLogger().info("QA_DUAL_HOST_REVERSE_PASS {}",host.replace('\n',' '));
                }
            } catch(java.io.IOException e) { throw new IllegalStateException(e); }
            return true;
        }
        throw new IllegalArgumentException(action);
    }

    /** Server-side equivalent of LiveInputQa.setup for the remote player: LV5 enrolment, category, skill in slot 2. */
    private static void grant(String skillId,ServerPlayer p) {
        var entry=SkillCatalog.REGISTERED.stream().filter(e->e.id().equals(skillId)).findFirst().orElseThrow();
        p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.onUpdateAbilities();
        if(skillId.equals(cn.academy.port.highpower.IronSandLance.ID)){
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,new net.minecraft.world.item.ItemStack(cn.academy.port.highpower.IronSandMedium.ITEM.get(),32));
            p.inventoryMenu.broadcastChanges();
        }
        var s=AbilityStorage.get(p); s.selectCategory(entry.category()); s.setLevel(5);
        p.getPersistentData().remove(HighPowerCombat.DATA_KEY);
        if(!HighPowerCombat.enroll(p,5)) throw new IllegalStateException("peer enrollment refused");
        for(var e:SkillCatalog.REGISTERED) if(e.category().equals(entry.category())) s.learn(e.id());
        s.activated=true; s.overloadFine=true; s.cooldowns.clear();
        s.presets.switchTo(0);
        for(int slot=0;slot<4;slot++) s.presets.edit(0,slot,slot==2?entry.id():"",s::learned);
        HighPowerResources.initializeBudget(p); AbilityStorage.save(p); AcademyNetwork.sync(p);
        LogUtils.getLogger().info("QA_DUAL_GRANTED skill={} player={}",skillId,p.getName().getString());
    }

    @SubscribeEvent public static void rendered(RenderPlayerEvent.Post event) {
        if(!OBSERVER && !RECORD) return;
        var mc=Minecraft.getInstance(); var p=event.getEntity();
        if(p==mc.player || mc.level==null) return;
        double time=mc.level.getGameTime()+mc.getTimer().getGameTimeDeltaPartialTick(false);
        var motion=CastPoseClient.motion(p,time); if(motion.strength()<.95) return;
        var m=event.getRenderer().getModel(); var a=motion.arms();
        // CastPoseClient.apply adds the torso twist to both arm yRot (see its apply(), "arms stay attached to the
        // turning body"), so the rendered expectation is arms + twist for a non-swinging player. Comparing against the
        // untwisted arms made every pose miss by the twist amount (~0.1 rad); the 0.08 tolerance itself is unchanged.
        double twist = m.attackTime > 0 ? 0 : motion.body().twist();
        if(Math.abs(m.rightArm.xRot-a.rx())>.08 || Math.abs(m.leftArm.xRot-a.lx())>.08
                || Math.abs(m.rightArm.yRot-(a.ry()+twist))>.08 || Math.abs(m.leftArm.yRot-(a.ly()+twist))>.08
                || Math.abs(m.rightArm.zRot-a.rz())>.08 || Math.abs(m.leftArm.zRot-a.lz())>.08) {
            // QA diagnostic: name the axis that keeps a remote pose from matching its own model, so a missed state can be
            // attributed to a specific joint instead of guessed. Throttled, read-only, showcase-only source set.
            if(mc.level.getGameTime()-lastTrace>=10) {
                lastTrace=mc.level.getGameTime();
                var prof=CastPoseClient.profile(p);
                LogUtils.getLogger().info("QA_DUAL_POSE_TRACE state={} strength={} rx={} lx={} ry={} ly={} rz={} lz={}",
                        prof==null?null:prof.name(), motion.strength(),
                        Math.abs(m.rightArm.xRot-a.rx()), Math.abs(m.leftArm.xRot-a.lx()),
                        Math.abs(m.rightArm.yRot-a.ry()), Math.abs(m.leftArm.yRot-a.ly()),
                        Math.abs(m.rightArm.zRot-a.rz()), Math.abs(m.leftArm.zRot-a.lz()));
            }
            return;
        }
        frames++;
        // v3 metadata observed on the actual rendered remote entity, not locally synthesized from its gesture.
        for (var cast : CastPoseClient.signatures(mc.getTimer().getGameTimeDeltaPartialTick(false))) {
            if (cast.entity()!=p.getId() || cast.local() || cast.skill().isEmpty() || cast.category().isEmpty()) continue;
            var entry=SkillCatalog.find(cast.category(),cast.skill()).orElseThrow(()->new IllegalStateException("unknown remote signature identity"));
            if (cast.level()!=entry.level() || CastPose.of(cast.skill())!=cast.archetype())
                throw new IllegalStateException("remote signature category/level/action mismatch: "+cast);
            record(mc,"signature-"+cast.category());
            record(mc,"signature-skill-"+cast.skill());
        }
        var profile=CastPoseClient.profile(p);
        if(profile!=null) record(mc,profile.name());
        try {
            var cls=Class.forName("cn.academy.port.client.ConstructClient"); var f=cls.getDeclaredField("POSES"); f.setAccessible(true);
            Object timeline=((Map<?,?>)f.get(null)).get(p.getUUID());
            if(timeline!=null) {
                int phase=(int)timeline.getClass().getMethod("phase").invoke(timeline);
                // Constructs use their own authoritative Visual channel, not CastPoseNet v3 category/level fields.
                var cues=cls.getDeclaredField("CUES");cues.setAccessible(true);
                Object cue=((Map<?,?>)cues.get(null)).get(p.getUUID());
                if(cue==null)throw new IllegalStateException("rendered construct timeline has no received native cue");
                var visualMethod=cue.getClass().getDeclaredMethod("visual");visualMethod.setAccessible(true);
                var visual=(cn.academy.port.highpower.ConstructRuntime.Visual)visualMethod.invoke(cue);
                var timelineToken=timeline.getClass().getDeclaredField("token");timelineToken.setAccessible(true);
                if(!visual.owner().equals(p.getUUID())||visual.entityId()!=p.getId()||visual.token()<=0
                        ||visual.token()!=timelineToken.getLong(timeline)||visual.phase()!=phase
                        ||!visual.dimension().equals(mc.level.dimension().location()))
                    throw new IllegalStateException("construct cue owner/entity/token/phase/dimension does not own the rendered timeline");
                var expectedAction=switch(phase){
                    case cn.academy.port.highpower.ConstructRuntime.PREVIEW->CastPose.Archetype.CONSTRUCT_LOCK;
                    case cn.academy.port.highpower.ConstructRuntime.DASH->CastPose.Archetype.CONSTRUCT_PULL;
                    case cn.academy.port.highpower.ConstructRuntime.HOLD->CastPose.Archetype.CONSTRUCT_STEER;
                    case cn.academy.port.highpower.ConstructRuntime.THROWN->CastPose.Archetype.CONSTRUCT_THROW;
                    case cn.academy.port.highpower.ConstructRuntime.LANDED->CastPose.Archetype.CONSTRUCT_LAND;
                    default->null;
                };
                if(expectedAction!=null){
                    var cast=CastPoseClient.signatures(mc.getTimer().getGameTimeDeltaPartialTick(false)).stream()
                            .filter(s->s.entity()==p.getId()&&!s.local()&&s.skill().equals("vector_construct")).findFirst()
                            .orElse(null);
                    // A network cue can arrive between the client tick and render. Wait for the real derived cast,
                    // without recording this phase; persistent disagreement still fails the required final states.
                    if(cast==null||cast.archetype()!=expectedAction)return;
                    String category=cn.academy.port.client.CastSignatureDecor.category(cast,"");
                    var entry=SkillCatalog.find(category,cast.skill()).orElseThrow();
                    if(!entry.category().equals("vecmanip")||entry.level()!=4
                            ||cn.academy.port.client.CastSignatureDecor.level(cast,category)!=entry.level())
                        throw new IllegalStateException("construct native phase/catalog identity disagrees with the rendered cast");
                    if(!SEEN.contains("construct-"+phase))LogUtils.getLogger().info("QA_DUAL_CONSTRUCT_CHANNEL owner={} entity={} token={} phase={} skill=vector_construct category={} catalogLevel={} source=construct_visual_not_v3_metadata",
                            visual.owner(),visual.entityId(),visual.token(),phase,category,entry.level());
                    record(mc,"construct-channel-vector_construct");
                }
                record(mc,"construct-"+phase);
                if(phase==2 && p.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT) record(mc,"construct-left");
                if(phase==2) {
                    var unturned=((cn.academy.port.highpower.core.ConstructPose.Timeline)timeline).sample(time,0,p.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT);
                    if(Math.abs(motion.hand().rotZ()-unturned.hand().rotZ())>6) record(mc,"construct-steer");
                }
            }
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static void record(Minecraft mc,String state) {
        if(!SEEN.add(state)) return;
        write();
        LogUtils.getLogger().info("QA_DUAL_REMOTE_RENDER state={} frames={}",state,frames);
        SHOTS.add(state);
    }
    private static void write() {
        try { Path target=out(); Files.createDirectories(target.toAbsolutePath().getParent()); Files.writeString(target,"\n"+String.join("\n",SEEN)+"\n"); }
        catch(java.io.IOException error) { throw new IllegalStateException(error); }
    }
    private static Path out(){ return OBSERVER?REPORT:HOST_REPORT; }
}
