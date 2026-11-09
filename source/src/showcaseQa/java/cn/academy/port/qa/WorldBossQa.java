package cn.academy.port.qa;

import cn.academy.port.worldboss.Clips;
import cn.academy.port.worldboss.WorldBossContent;
import cn.academy.port.worldboss.client.BossRenderer;
import cn.academy.port.worldboss.client.Cinematic;
import cn.academy.port.worldboss.client.EnvClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** QA only. Real spawn egg, normal animation clock, native Husk damage; never calls combat or FX receivers. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class WorldBossQa {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("academy.qa.showcase");
    private static final float[] CAPS={0,0,0,4,10,12,12,10,10,10,12,10,12,10,10,12,12,14,16};
    private static final double[][] STATIONS={{0,2,-32},{0,4,-20},{0,3,-18},{4.5,4,-15},{-4.5,4,-15},
        {0,2,24},{0,0,26},{5.1,0,0},{0,0,0},{0,0,-12},{0,4,-45}};
    private static final Set<Integer> VERIFIED=new HashSet<>(),SUPPLEMENT_VERIFIED=new HashSet<>();
    private static final Map<String,AtomicInteger> RENDERS=new ConcurrentHashMap<>();
    private static volatile Scene scene;
    private static WorldBossContent.Boss boss;
    private static Vec3 inputBaseline,lockedBaseline;
    private static int originalFov;
    private static String awaited="";private static int polls;
    private static final Set<Integer> INTRO_SHOTS=new HashSet<>();
    private static final int[] PEAKS={98,28,21,50,36,54,40,37,42,52,50,47,77,68,72,68,78,59,102};
    private static final String[] KEYS={"00_spawn","01_idle","02_walk","03_roar","a01_crushing_bite","a02_rending_combo","a03_skull_ram","a04_left_rake","a05_right_uppercut","a06_tail_sweep","a07_tail_slam","a08_seismic_stomp","a09_twin_quake","a10_onslaught","a11_furnace_breath","a12_solar_lance","a13_meteor_volley","a14_dorsal_nova","a15_worldbreaker"};
    private static volatile boolean arenaPrepared;
    private static int preparedFrames,frameSerial;
    private static volatile RenderWitness witness;
    private record RenderWitness(UUID boss,int clip,int start,float frame,int serial) {}
    private static final class Scene {
        final int clip;final long start;final UUID boss;final List<Husk> targets=new ArrayList<>();
        final Map<UUID,Vec3> anchors=new LinkedHashMap<>();final Map<UUID,Integer> hits=new LinkedHashMap<>();
        final Map<UUID,Float> postDamage=new LinkedHashMap<>(),peakSpent=new LinkedHashMap<>(),expectedHealth=new LinkedHashMap<>(),allPost=new LinkedHashMap<>();
        final List<String> ledgerErrors=new ArrayList<>();
        final int renderBefore;boolean passed;volatile boolean peakCaptured;volatile long observedAge;boolean naturalIntro,supplement,farLance;int desiredPeak;volatile double observedFarBeamLength;
        Scene(int clip,long start,UUID boss){this.clip=clip;this.start=start;this.boss=boss;desiredPeak=PEAKS[clip];renderBefore=renders(boss,clip);}
    }
    private static String key(UUID boss,int clip){return boss+":"+clip;}
    private static int renders(UUID boss,int clip){return RENDERS.getOrDefault(key(boss,clip),new AtomicInteger()).get();}
    private static void require(boolean condition,String detail){if(!condition)throw new IllegalStateException("QA_WORLDBOSS_FAILED "+detail);}

    /** Polling driver route; every wait advances normal client/server time. No synthetic tick acceleration. */
    public static boolean step(Minecraft mc,String action) {
        if(action.equals("prepared")) {
            if(!awaited.equals(action)){awaited=action;polls=0;}require(++polls<3000,"arena real-frame prewarm timeout");
            if(!arenaPrepared||preparedFrames<100)return false;
            for(int[] point:new int[][]{{0,0},{48,0},{-48,0},{0,48},{0,-48},{32,32},{-32,-32}}) {
                BlockPos at=new BlockPos(256+point[0],80,256+point[1]);
                if(!mc.level.hasChunkAt(at)||!mc.level.getBlockState(at).is(Blocks.GRASS_BLOCK))return false;
            }
            LOG.info("QA_WORLDBOSS_ARENA_WARM_PASS actualRenderedFrames={} sevenNativeGroundChunks=true noBossSpawned=true",preparedFrames);return true;
        }
        if(action.equals("intro-first")) {
            if(!awaited.equals(action)){awaited=action;polls=0;}require(++polls<1000,"first actual cinematic frame timeout");
            if(INTRO_SHOTS.contains(0))return true;
            require(!Cinematic.finished,"first cinematic segment must actually be captured");return false;
        }
        if(action.equals("peak-await")) {
            var current=scene;require(current!=null&&!current.naturalIntro,"controlled clip exists");
            require(current.peakCaptured||current.observedAge<=Clips.get(current.clip).ticks()+20,"actual peak render was missed, no late replacement permitted");
            return current.peakCaptured;
        }
        if(action.equals("finish-await")) {
            var current=scene;require(current!=null&&current.peakCaptured,"actual peak required before end wait");
            return current.observedAge>=Clips.get(current.clip).ticks()+5;
        }
        if(action.equals("intro-end")) {
            if(!awaited.equals(action)){awaited=action;polls=0;}require(++polls<1000,"natural cinematic end timeout");
            if(Cinematic.active||!Cinematic.finished)return false;
            require(INTRO_SHOTS.size()==5,"five actual original camera shots");require(mc.getCameraEntity()==mc.player,"natural camera restoration");
            require(!mc.options.hideGui,"original HUD user setting restored without mutation");
            require(mc.options.fov().get()==originalFov,"original FOV user setting unchanged");
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();inputBaseline=server.submit(()->server.getPlayerList().getPlayer(id).position()).join();
            LOG.info("QA_WORLDBOSS_CINEMATIC_END_PASS cameraRestored=true fiveShots=5 fovOptionUnmutated=true playerBaseline={}",inputBaseline);return true;
        }
        run(mc,action);return true;
    }
    /** ShowcaseDriver: case "worldboss" -> { if(!WorldBossQa.step(mc,arg))index--; wait=0; }. */
    public static void run(Minecraft mc,String action) {
        var server=mc.getSingleplayerServer();require(server!=null&&mc.player!=null,"integrated server/player");
        UUID owner=mc.player.getUUID();
        if(action.equals("prepare")) {
            server.submit(()->{prepareArena(server.getPlayerList().getPlayer(owner));return true;}).join();
        } else if(action.equals("spawn")) {
            require(arenaPrepared&&preparedFrames>=100,"arena rendered before egg spawn");
            originalFov=mc.options.fov().get();
            server.submit(()->{setup(server.getPlayerList().getPlayer(owner),0,true);return true;}).join();
        } else if(action.equals("intro-input-baseline")) {
            require(Cinematic.active&&mc.getCameraEntity()!=mc.player,"production camera active before real input lock probe");
            lockedBaseline=server.submit(()->server.getPlayerList().getPlayer(owner).position()).join();
        } else if(action.equals("intro-input-locked")) {
            require(Cinematic.active&&lockedBaseline!=null,"cinematic remains active for input lock proof");
            Vec3 current=server.submit(()->server.getPlayerList().getPlayer(owner).position()).join();
            require(current.distanceTo(lockedBaseline)<.01,"real W key is blocked during original cinematic");
            LOG.info("QA_WORLDBOSS_CINEMATIC_INPUT_LOCK_PASS before={} after={} distance={}",lockedBaseline,current,lockedBaseline.distanceTo(current));
        } else if(action.equals("intro-input-restored")) {
            require(inputBaseline!=null&&!Cinematic.active&&mc.getCameraEntity()==mc.player,"input lock and camera released");
            Vec3 position=server.submit(()->server.getPlayerList().getPlayer(owner).position()).join();
            require(position.distanceTo(inputBaseline)>.05,"real normal W input moved player after cinematic");
            LOG.info("QA_WORLDBOSS_CINEMATIC_INPUT_RESTORED before={} after={} distance={}",inputBaseline,position,inputBaseline.distanceTo(position));
        } else if(action.equals("far-lance")) {
            server.submit(()->{
                var player=server.getPlayerList().getPlayer(owner);setup(player,Clips.LANCE,false,true);
                scene.supplement=true;scene.farLance=true;scene.desiredPeak=PEAKS[Clips.LANCE]+4;
                Vec3 camera=boss.position().add(60,20,57),target=boss.position().add(0,17,57);
                Vec3 delta=target.subtract(camera.add(0,player.getEyeHeight(),0));
                player.teleportTo(player.serverLevel(),camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
                LOG.info("QA_WORLDBOSS_FAR_LANCE_BASELINE target={} targetCenter={} camera={} desiredPeak={} originalHardEnd={} actualNativeHusk=true",scene.targets.getFirst().getUUID(),scene.targets.getFirst().getBoundingBox().getCenter(),camera,scene.desiredPeak,PEAKS[Clips.LANCE]+10);
                return true;
            }).join();
        } else if(action.startsWith("supplement:")) {
            int clip=Integer.parseInt(action.substring(11));require(clip>=Clips.ROAR&&clip<19,"supplement clip index");
            server.submit(()->{
                var player=server.getPlayerList().getPlayer(owner);setup(player,clip,false);
                scene.supplement=true;scene.desiredPeak=PEAKS[clip]+4;
                Vec3 cameraOffset,lookOffset;
                if(clip==Clips.TSWEEP||clip==Clips.TSLAM){cameraOffset=new Vec3(-40,14,-40);lookOffset=new Vec3(0,10,-12);}
                else if(clip==Clips.LANCE){cameraOffset=new Vec3(48,18,25);lookOffset=new Vec3(0,10,25);}
                else if(clip==Clips.NOVA){cameraOffset=new Vec3(45,24,15);lookOffset=new Vec3(0,20,0);}
                else {cameraOffset=new Vec3(12,10,38);lookOffset=new Vec3(0,12,20);}
                Vec3 camera=boss.position().add(cameraOffset),target=boss.position().add(lookOffset);
                Vec3 delta=target.subtract(camera.add(0,player.getEyeHeight(),0));
                player.teleportTo(player.serverLevel(),camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
                LOG.info("QA_WORLDBOSS_SUPPLEMENT_BASELINE clip={} desiredPeak={} hardEnd={} camera={} lookAt={} realPlay=true originalWeather=true",clip,scene.desiredPeak,PEAKS[scene.clip]+10,camera,target);
                return true;
            }).join();
        } else if(action.startsWith("verify-supplement:")) {
            var expected=new HashSet<Integer>();for(var value:action.substring(18).split(","))expected.add(Integer.parseInt(value));
            require(!expected.isEmpty()&&SUPPLEMENT_VERIFIED.equals(expected),"all requested close visual supplements independently passed: "+SUPPLEMENT_VERIFIED);
            require(mc.player.getMainHandItem().is(WorldBossContent.SPAWN_EGG.get())&&mc.player.getMainHandItem().getCount()==1,"supplement retains real native held egg");
            LOG.info("QA_WORLDBOSS_SUPPLEMENT_ALL_PASS clips={} realPositiveDamage=true budgets=true originalWeather=true",expected);
        } else if(action.startsWith("setup:")) {
            int clip=Integer.parseInt(action.substring(6));require(clip>=0&&clip<19,"clip index");
            server.submit(()->{setup(server.getPlayerList().getPlayer(owner),clip,false);return true;}).join();
        } else if(action.equals("assert")) {
            server.submit(()->{verify();return true;}).join();
        } else if(action.equals("verify")) {
            require(VERIFIED.size()==19,"nineteen individual clips verified: "+VERIFIED);
            require(mc.player.getMainHandItem().is(WorldBossContent.SPAWN_EGG.get())&&mc.player.getMainHandItem().getCount()==1,"real client main hand displays one original spawn egg");
            LOG.info("QA_WORLDBOSS_ALL_PASS clips=19 attacks=15 roar=1 realSpawnEgg=true vanillaHp=60 vanillaAttack=10 qaRendererWrapper=true");
        } else throw new IllegalArgumentException(action);
    }

    private static void prepareArena(ServerPlayer player) {
        require(player!=null,"arena owner");var level=player.serverLevel();var floor=new BlockPos(256,80,256);
        // Fixture construction is completed before the real spawn starts any production clock or camera.
        for(var entity:level.getAllEntities())if(entity instanceof WorldBossContent.Boss old&&old.position().distanceTo(new Vec3(256.5,81,256.5))<128)old.discard();
        for(int x=-60;x<=60;x++)for(int z=-60;z<=60;z++)if(x*x+z*z<=3600) {
            for(int y=-22;y<0;y++)level.setBlock(floor.offset(x,y,z),Blocks.STONE.defaultBlockState(),2|16);
            level.setBlock(floor.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2|16);
        }
        player.setGameMode(GameType.CREATIVE);
        Vec3 origin=new Vec3(256.5,81,256.5),camera=origin.add(52,24,65),target=origin.add(0,15,0);
        Vec3 delta=target.subtract(camera.add(0,player.getEyeHeight(),0));
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        player.teleportTo(level,camera.x,camera.y,camera.z,yaw,pitch);
        player.getAbilities().flying=true;player.getAbilities().mayfly=true;player.onUpdateAbilities();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(WorldBossContent.SPAWN_EGG.get()));
        preparedFrames=0;arenaPrepared=true;
        LOG.info("QA_WORLDBOSS_ARENA_PREPARED floor={} stoneDepth=22 radius=60 camera={} noBossYet=true",floor,camera);
    }

    private static void setup(ServerPlayer player,int clip,boolean naturalIntro) {setup(player,clip,naturalIntro,false);}
    private static void setup(ServerPlayer player,int clip,boolean naturalIntro,boolean farLance) {
        require(player!=null,"server player");
        if(scene!=null)for(var target:scene.targets)target.discard();
        if(boss==null||boss.isRemoved()) {
            var level=player.serverLevel();var floor=new BlockPos(256,80,256);
            var stack=new ItemStack(WorldBossContent.SPAWN_EGG.get());player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var before=new HashSet<UUID>();for(var entity:level.getAllEntities())before.add(entity.getUUID());
            var result=player.gameMode.useItemOn(player,level,stack,InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(256.5,81,256.5),Direction.UP,floor,false));
            require(result.consumesAction(),"registered spawn egg native GameMode useItemOn consumes action");
            require(player.getMainHandItem().is(WorldBossContent.SPAWN_EGG.get())&&player.getMainHandItem().getCount()==1,"creative native GameMode restores the one held spawn egg");
            for(var entity:level.getAllEntities())if(entity instanceof WorldBossContent.Boss found&&!before.contains(found.getUUID()))boss=found;
            require(boss!=null,"native egg created registered boss");
            require(boss.getHealth()==60&&boss.getMaxHealth()==60&&boss.getAttributeValue(Attributes.ATTACK_DAMAGE)==10,"native egg attributes 60/10");
            boss.setNoAi(true);boss.setNoGravity(true);boss.setYRot(0);boss.yRotO=0;boss.yBodyRot=0;boss.setYHeadRot(0);
            var camera=player.position();
            LOG.info("QA_WORLDBOSS_EGG_PASS uuid={} result={} health={} maxHealth={} attack={} position={} playerCamera={} nativeGameMode=true heldEggCount={}",boss.getUUID(),result,boss.getHealth(),boss.getMaxHealth(),boss.getAttributeValue(Attributes.ATTACK_DAMAGE),boss.position(),camera,player.getMainHandItem().getCount());
        }
        require(boss.isAlive()&&boss.getHealth()==60,"same undamaged sixty-health live boss");
        scene=new Scene(clip,boss.level().getGameTime(),boss.getUUID());scene.naturalIntro=naturalIntro;
        if(!naturalIntro)for(var station:STATIONS) {
            var target=new Husk(EntityType.HUSK,boss.level());target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(200);
            Vec3 point=farLance&&scene.targets.isEmpty()?boss.position().add(0,16,92):boss.local(station[0],station[1],station[2]);target.setPos(point);target.addTag("aca_worldboss_qa_target");
            require(boss.level().addFreshEntity(target),"real Husk target admitted");scene.targets.add(target);scene.anchors.put(target.getUUID(),point);scene.expectedHealth.put(target.getUUID(),200F);
        }
        if(!naturalIntro){boss.setTarget(scene.targets.getFirst());boss.play(clip);}
        if(naturalIntro)require(boss.clip()==Clips.SPAWN,"egg naturally starts original emergence without QA play trigger");
        LOG.info("QA_WORLDBOSS_BASELINE clip={} key={} duration={} start={} boss={} targets={} damageCap={} nativeOnly=true",clip,Clips.get(clip).key(),Clips.get(clip).ticks(),scene.start,scene.boss,scene.targets.stream().map(t->t.getUUID()+"@"+t.position()+"=200").toList(),CAPS[clip]);
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void before(ServerTickEvent.Pre event) {
        var current=scene;if(!ENABLED||current==null||boss==null||boss.level().getServer()!=event.getServer())return;
        // Stationary fixtures, exactly as the native suite. No health/iframe resets or accelerated ticks.
        for(var target:current.targets)if(target.isAlive()){target.setPos(current.anchors.get(target.getUUID()));target.setDeltaMovement(Vec3.ZERO);}
        boss.setDeltaMovement(Vec3.ZERO);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void after(ServerTickEvent.Post event) {
        var current=scene;if(!ENABLED||current==null||boss==null||boss.level().getServer()!=event.getServer())return;
        for(var target:current.targets)current.peakSpent.merge(target.getUUID(),boss.damageSpent(target.getUUID()),Math::max);
        current.observedAge=boss.level().getGameTime()-current.start;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void damage(LivingDamageEvent.Post event) {
        var current=scene;if(!ENABLED||current==null||!current.anchors.containsKey(event.getEntity().getUUID()))return;
        var id=event.getEntity().getUUID();float before=current.expectedHealth.getOrDefault(id,200F);
        float expected=Math.max(0F,Math.min(event.getEntity().getMaxHealth(),before-event.getNewDamage()));
        current.expectedHealth.put(id,expected);current.allPost.merge(id,event.getNewDamage(),Float::sum);
        if(Float.floatToIntBits(expected)!=Float.floatToIntBits(event.getEntity().getHealth()))current.ledgerErrors.add("target="+id+" source="+event.getSource().getMsgId()+" expected="+expected+" actual="+event.getEntity().getHealth());
        if(event.getSource().getEntity()!=boss){LOG.info("QA_WORLDBOSS_ENV_NATIVE_DAMAGE clip={} uuid={} source={} attacker={} amount={} sequentialExpectedHp={}",current.clip,id,event.getSource().getMsgId(),event.getSource().getEntity(),event.getNewDamage(),expected);return;}
        current.hits.merge(id,1,Integer::sum);current.postDamage.merge(id,event.getNewDamage(),Float::sum);
    }
    private static void verify() {
        var current=scene;require(current!=null&&!current.passed,"one pending clip");
        require(current.peakCaptured,"actual framebuffer peak captured");require(current.ledgerErrors.isEmpty(),"all-source native sequential float ledger: "+current.ledgerErrors);
        long age=boss.level().getGameTime()-current.start;require(age>=Clips.get(current.clip).ticks()+5,"full native duration elapsed");
        int renders=renders(current.boss,current.clip)-current.renderBefore;require(renders>0,"production render returned successfully for clip "+current.clip);
        if(Clips.get(current.clip).loops())require(boss.clip()==current.clip,"loop remains correct clip");
        else require(boss.clip()==Clips.IDLE,"production naturally returned one-shot to IDLE");
        int productive=0;
        for(var target:current.targets) {
            UUID id=target.getUUID();float loss=200-target.getHealth(),post=current.postDamage.getOrDefault(id,0F),spent=current.peakSpent.getOrDefault(id,0F);
            require(Float.floatToIntBits(current.expectedHealth.getOrDefault(id,200F))==Float.floatToIntBits(target.getHealth()),"all native sources account for exact sequential float HP");require(spent<=CAPS[current.clip]+.001,"whole-cast native budget respected");
            if(post>0&&current.hits.getOrDefault(id,0)>0)productive++;
            LOG.info("QA_WORLDBOSS_TARGET clip={} uuid={} loss={} nativePost={} nativeEvents={} peakSpent={} cap={} allNativePost={} sequentialExpectedHp={} burning={}",current.clip,id,loss,post,current.hits.getOrDefault(id,0),spent,CAPS[current.clip],current.allPost.getOrDefault(id,0F),current.expectedHealth.getOrDefault(id,200F),target.isOnFire());
        }
        if(current.clip>=Clips.ROAR)require(productive>0,"real native positive damage required, not animation-only");
        else require(productive==0,"intro/idle/walk cause no direct boss-owned damage; native environment is recorded separately");
        require(boss.pendingMeteorCount()==0,"no outstanding native meteor work");
        if(current.farLance){
            require(current.postDamage.getOrDefault(current.targets.getFirst().getUUID(),0F)>0,"far native Husk actually receives boss-owned damage");
            require(current.observedFarBeamLength>=65,"actual visible block-clipped beam spans at least sixty-five meters");
            LOG.info("QA_WORLDBOSS_FAR_LANCE_PASS target={} bossNativePost={} renderedBeamLength={} cap={}",current.targets.getFirst().getUUID(),current.postDamage.get(current.targets.getFirst().getUUID()),current.observedFarBeamLength,CAPS[current.clip]);
        }
        current.passed=true;VERIFIED.add(current.clip);if(current.supplement)SUPPLEMENT_VERIFIED.add(current.clip);
        LOG.info("QA_WORLDBOSS_CLIP_PASS clip={} key={} realAge={} renders={} productive={} naturalEnd={} qaRendererWrapper=true",current.clip,Clips.get(current.clip).key(),age,renders,productive,boss.clip());
    }

    @SubscribeEvent
    public static void framebuffer(RenderFrameEvent.Post event) {
        int serial=++frameSerial;if(!ENABLED)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.screen!=null)return;
        if(arenaPrepared&&boss==null)preparedFrames++;
        var rendered=witness;var current=scene;
        if(rendered==null||current==null||rendered.serial()!=serial||!rendered.boss().equals(current.boss))return;
        if(current.naturalIntro&&Cinematic.active&&rendered.clip()==Clips.SPAWN) {
            float[] starts={.6F,2.55F,3.55F,4.95F,7.25F},ends={2.4F,3.35F,4.45F,6.8F,8.6F};
            for(int shot=0;shot<5;shot++)if(Cinematic.time>=starts[shot]&&Cinematic.time<ends[shot]&&!INTRO_SHOTS.contains(shot)) {
                require(mc.getCameraEntity()!=mc.player,"native detached camera for real framebuffer shot");
                require(EnvClient.storm>0&&EnvClient.glow>0,"actual sky/storm for native frame");
                capture(mc,"worldboss_natural_intro_shot_"+shot);
                INTRO_SHOTS.add(shot);
                LOG.info("QA_WORLDBOSS_CINEMATIC_SHOT_PASS shot={} time={} fov={} storm={} glow={} actualFrameSerial={} camera={} nativeFramebuffer=true",shot,Cinematic.time,Cinematic.fovNow,EnvClient.storm,EnvClient.glow,serial,mc.gameRenderer.getMainCamera().getPosition());
            }
        }
        if(!current.naturalIntro&&!current.peakCaptured&&rendered.clip()==current.clip&&rendered.start()==(int)current.start&&rendered.frame()>=current.desiredPeak) {
            require(rendered.frame()<=PEAKS[current.clip]+10F,"actual render missed the half-second peak window; no late replacement");
            int fx=actualFx();if(current.clip>=Clips.BITE)require(fx>0,"actual attack FX in rendered peak frame");
            if(current.farLance){
                WorldBossContent.Boss visible=null;for(var entity:mc.level.entitiesForRendering())if(entity instanceof WorldBossContent.Boss found&&found.getUUID().equals(current.boss))visible=found;
                require(visible!=null,"far lance client boss exists");
                Vec3 mouth=cn.academy.port.worldboss.client.ClientHooks.anchor(visible,cn.academy.port.worldboss.client.ClientHooks.MOUTH,mc.getTimer().getGameTimeDeltaPartialTick(false));
                Vec3 end=mouth.add(visible.attackAim().subtract(mouth).normalize().scale(90));
                var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(mouth,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,visible));
                if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)end=hit.getLocation();
                current.observedFarBeamLength=mouth.distanceTo(end);require(current.observedFarBeamLength>=65,"far real rendered beam length");
                LOG.info("QA_WORLDBOSS_FAR_LANCE_RENDER mouth={} aim={} end={} blockClippedLength={}",mouth,visible.attackAim(),end,current.observedFarBeamLength);
            }
            capture(mc,current.farLance?"worldboss_far_lance_15":String.format(java.util.Locale.ROOT,current.supplement?"worldboss_supp_%02d_%s":"worldboss_%02d_%s",current.clip,KEYS[current.clip]));
            current.peakCaptured=true;
            LOG.info("QA_WORLDBOSS_PEAK clip={} clientFrame={} desiredFrame={} renders={} actualFx={} boss={} actualFrameSerial={} camera={} nativeFramebuffer=true",current.clip,rendered.frame(),current.desiredPeak,renders(current.boss,current.clip)-current.renderBefore,fx,current.boss,serial,mc.gameRenderer.getMainCamera().getPosition());
        }
    }
    private static int actualFx() {
        try{var type=Class.forName("cn.academy.port.worldboss.client.ModernFx");var field=type.getDeclaredField("fx");field.setAccessible(true);return ((List<?>)field.get(null)).size();}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    private static void capture(Minecraft mc,String name) {
        LOG.info("QA_SHOT {} nativeFramebuffer=true",name);
        Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),message->LOG.info("QA_SHOT_SAVED {}",message.getString()));
    }

    /** A QA observer wraps and delegates to the exact production renderer; no production counters are added. */
    public static final class CountingRenderer extends BossRenderer {
        CountingRenderer(EntityRendererProvider.Context context){super(context);}
        @Override public void render(WorldBossContent.Boss entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
            super.render(entity,yaw,partial,pose,buffers,light);
            RENDERS.computeIfAbsent(key(entity.getUUID(),entity.clip()),ignored->new AtomicInteger()).incrementAndGet();
            witness=new RenderWitness(entity.getUUID(),entity.clip(),entity.start(),entity.frame(partial),frameSerial+1);
        }
    }
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class RendererObserver {
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public static void register(EntityRenderersEvent.RegisterRenderers event) {
            if(ENABLED)event.registerEntityRenderer(WorldBossContent.BOSS.get(),CountingRenderer::new);
        }
    }
    private WorldBossQa() {}
}
