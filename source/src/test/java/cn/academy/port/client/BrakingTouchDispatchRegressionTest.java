package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Executes actual compiled Post, gate and request callbacks. Only native environment/transport is substituted. */
public final class BrakingTouchDispatchRegressionTest {
    private static final String CLIENT="cn/academy/port/client/AcademyClient", GENERATED="cn/academy/port/client/generated/BrakingTouchDispatchClient";
    private static int checks;
    private static final List<String> packets=new ArrayList<>();
    private static final List<String> retiredVisuals=new ArrayList<>();
    private static BrakingTouchState visualState=new BrakingTouchState();
    private static Class<?> client;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        inspectRealDispatchSites(); client=load(); completedReplacement();
        for(boolean crouch:new boolean[]{false,true})for(boolean released:new boolean[]{false,true}){
            reset();queue();if(released)input().release(input().current());
            check(packets.isEmpty(),"fresh press/release never sends before native movement");
            nativeMovement(crouch);post();
            var expected=new ArrayList<>(List.of("native_sneak:"+crouch,"native_move","brake_touch_start:0:1:19:71"));
            if(released)expected.add("brake_touch_end:0:1:19:71");
            check(packets.equals(expected),"actual Post sends identity-only start/end after native sneak/move");
            check(input().permits(1,71)==(!crouch||!released),"actual Post crouch sample distinguishes released tap from canceled arm");
            post();check(packets.equals(expected),"actual second Post is inert");
        }
        reset();queue();nativeMovement(true);post();input().accepted(1,71,BrakingTouchState.ARMED);
        progress().cooldowns.put("braking_touch",10);progress().consuming=true;tick();post();
        check(input().permits(1,71)&&packets.size()==3,"actual own cooldown/payment leaves armed input intact");
        check(!input().eligible(gates()),"actual new-edge gate still rejects own cooldown/payment");
        StubMinecraft.INSTANCE.physical=false;tick();post();
        check(!input().permits(1,71)&&input().current()==null&&packets.getLast().equals("brake_touch_end:0:1:19:71"),"actual physical release fences delayed landing and sends end");
        reset();queue();nativeMovement(true);post();input().accepted(1,71,BrakingTouchState.LANDED);
        StubMinecraft.INSTANCE.physical=false;tick();post();
        check(input().permits(1,71),"actual release keeps confirmed landing cosmetic lease");
        List<Consumer<StubMinecraft>> losses=List.of(m->m.screen=new Object(),m->m.focused=false,m->m.camera=new StubPlayer(),
                m->m.player.alive=false,m->m.player.removed=true,m->m.player.spectator=true,m->m.player.passenger=true,
                m->m.player.vehicle=true,m->m.player.sleeping=true,m->m.player.abilities.instabuild=true,
                m->m.player.abilities.invulnerable=true,m->m.player.abilities.flying=true,m->m.player.noPhysics=true,
                m->m.player.gliding=true,m->m.player.water=true,m->m.player.lava=true,m->m.player.climbing=true,
                m->m.player.containerMenu=new Object(),m->m.level=new StubLevel(),m->m.player=new StubPlayer(),m->m.connection=new Object());
        for(var loss:losses)for(boolean sent:new boolean[]{false,true}){
            reset();queue();if(sent){nativeMovement(true);post();}loss.accept(StubMinecraft.INSTANCE);
            if(sent)tick();else{nativeMovement(false);post();}
            check(input().current()==null&&!input().permits(1,71),"actual native source/environment loss fences input");
            check(packets.stream().filter(p->p.startsWith("brake_touch_start:")).count()==(sent?1:0),"actual invalidation cannot dispatch or repeat start");
        }
        for(var loss:List.<Consumer<StubProgress>>of(p->p.activated=false,p->p.overloadFine=false,p->p.interfering=true,
                p->p.category="electromaster",p->p.level=0,p->p.level=6,p->p.learned=false,p->p.mapped=false,
                p->p.presets.preset++,p->p.presets.revision++,p->p.presets.skill="other",p->p.cooldowns.put("braking_touch",1),p->p.consuming=true)){
            reset();queue();loss.accept(progress());nativeMovement(false);post();
            check(input().current()==null&&packets.stream().noneMatch(p->p.startsWith("brake_touch_start:")),"actual fresh Post rechecks ability/mapping/payment/cooldown gates");
        }
        reset();queue();StubMinecraft.INSTANCE.paused=true;nativeMovement(false);post();
        check(input().current()==null&&packets.stream().noneMatch(p->p.startsWith("brake_touch_start:")),"actual pause between Pre/Post discards start");
        reset();queue();field("serverSingleEpoch").setLong(null,72);nativeMovement(false);post();
        check(input().current()==null&&packets.size()==2,"owner epoch change sends no cross-owner abort");
        reset();queue();field("singleInputEpoch").setLong(null,2);nativeMovement(false);post();
        check(input().current()==null&&packets.stream().noneMatch(p->p.startsWith("brake_touch_start:")),"session epoch change fences queued start");
        reset();queue();keys()[0].key.value=30;nativeMovement(false);post();
        check(input().current()==null&&packets.stream().noneMatch(p->p.startsWith("brake_touch_start:")),"physical remap invalidates original identity");
        reset();queue();StubCatalog.enabled=false;nativeMovement(false);post();
        check(input().current()==null&&packets.stream().noneMatch(p->p.startsWith("brake_touch_start:")),"configuration disabling rechecked at dispatch");
        reset();queue();call("flushBrakingTouchStart");nativeMovement(true);
        check(packets.getFirst().startsWith("brake_touch_start:"),"negative mutation demonstrates Pre dispatch sees stale native sneak state");
        System.out.println("BrakingTouchDispatchRegressionTest: "+checks+" compiled dispatch/gate/transport checks passed");
    }
    private static void completedReplacement()throws Exception{
        var owner=new java.util.UUID(413,1);var zero=new cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point(0,0,0);
        var incoming=new cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point(0,-1,0);
        for(int phase:new int[]{BrakingTouchState.BRAKED,BrakingTouchState.LANDED}){
            reset();queue();nativeMovement(phase==BrakingTouchState.LANDED);post();
            var old=new BrakingTouchState.Frame(new BrakingTouchState.Identity(19,owner,71,1,10),1,phase,zero,incoming,zero,112);
            check(visualState.accept(old,true,100),"actual press fixture retains a12tick completed receipt");
            input().accepted(1,71,phase);
            check(!queue(2)&&retiredVisuals.isEmpty(),"actual helper protects a completed input while physically held");
            StubMinecraft.INSTANCE.physical=false;tick();post();
            for(int age=1;age<4;age++){
                progress().cooldowns.put("braking_touch",4-age);tick();post();
                visualState.tick(false,100+age,c->inputPermits(c.id().input(),c.id().epoch()));
                check(!queue(2)&&visualState.size()==1&&retiredVisuals.isEmpty(),"actual cooldown rejection preserves previous success visual");
            }
            progress().cooldowns.put("braking_touch",0);
            check(visualState.current(owner).age()<12&&input().permits(1,71),"previous cosmetic remains active at actual cooldown readiness");
            StubMinecraft.INSTANCE.physical=true;
            check(queue(2),"actual helper admits fresh press at4tick readiness during12tick visual");
            check(retiredVisuals.equals(List.of("1:71"))&&visualState.size()==0,"actual helper fences old visual synchronously before effect validation can cancel new input");
            post();check(input().permits(2,71)&&!input().permits(1,71),"newly dispatched identity owns only its own receipts");
            input().ended(1,71);input().accepted(1,71,phase);
            check(input().permits(2,71)&&input().pendingCount()==1,"late old end/completion cannot end or acknowledge replacement");
            var late=new BrakingTouchState.Frame(old.id(),2,phase,zero,incoming,zero,114);
            check(!visualState.accept(late,true,102),"old late success cannot replay after immediate visual fence");
            var newer=new BrakingTouchState.Frame(new BrakingTouchState.Identity(19,owner,71,2,11),1,BrakingTouchState.BRAKED,zero,incoming,zero,114);
            check(visualState.accept(newer,true,102),"newer server token admits its own visual");
            var lateEnd=new BrakingTouchState.Frame(old.id(),3,BrakingTouchState.ENDED,zero,zero,zero,102);
            check(!visualState.accept(lateEnd,true,102)&&visualState.current(owner).id().token()==11,"late old end cannot erase newer visual token");
            check(packets.stream().noneMatch(p->p.startsWith("brake_touch_abort:")),"cosmetic replacement adds no gameplay abort or cooldown request");
        }
        reset();queue();input().release(input().current());nativeMovement(false);post();
        check(!queue(2)&&retiredVisuals.isEmpty(),"actual helper cannot replace an unknown released first reply");
    }
    private static boolean inputPermits(long input,long epoch){try{return input().permits(input,epoch);}catch(Exception e){throw new AssertionError(e);}}
    private static void inspectRealDispatchSites()throws Exception{
        ClassNode source=read();int postCalls=0;boolean annotation=false;
        for(var m:source.methods){
            for(var op:m.instructions)if(op instanceof MethodInsnNode c&&c.owner.equals(CLIENT)&&c.name.equals("flushBrakingTouchStart")){
                check(m.name.equals("finishContextDisposals")&&m.desc.contains("ClientTickEvent$Post"),"only actual ClientTickEvent.Post may dispatch NEW413");postCalls++;
            }
            if(m.name.equals("finishContextDisposals")&&m.visibleAnnotations!=null)
                annotation=m.visibleAnnotations.stream().anyMatch(a->a.desc.equals("Lnet/neoforged/bus/api/SubscribeEvent;"));
        }
        check(postCalls==1&&annotation,"single subscribed production Post dispatch site");
        try(var stream=BrakingTouchDispatchRegressionTest.class.getClassLoader().getResourceAsStream(CLIENT+"$1.class")){
            var host=new ClassNode();new ClassReader(stream).accept(host,0);int calls=0;
            for(var method:host.methods)if(method.name.equals("eligible"))for(var instruction:method.instructions)
                if(instruction instanceof MethodInsnNode call&&call.owner.equals("cn/academy/port/client/BrakingTouchInputState")
                        && call.getPrevious() instanceof MethodInsnNode gate && gate.owner.equals(CLIENT) && gate.name.equals("brakingTouchGates")){
                    check(call.name.equals("delegateEligible"),"actual AcademyClient binding must retain admitted receipt ownership through its own cooldown");calls++;
                }
            check(calls==1,"exactly one compiled host call uses NEW413 delegate eligibility");
        }
    }
    private static Class<?> load()throws Exception{
        var map=new HashMap<String,String>();map.put(CLIENT,GENERATED);
        map.put("net/minecraft/client/Minecraft",Type.getInternalName(StubMinecraft.class));
        map.put("net/minecraft/client/multiplayer/ClientLevel",Type.getInternalName(StubLevel.class));
        map.put("net/minecraft/client/multiplayer/ClientPacketListener","java/lang/Object");
        map.put("net/minecraft/client/gui/screens/Screen","java/lang/Object");
        for(var name:List.of("net/minecraft/world/entity/Entity","net/minecraft/world/entity/player/Player","net/minecraft/client/player/LocalPlayer"))map.put(name,Type.getInternalName(StubPlayer.class));
        map.put("net/minecraft/world/inventory/AbstractContainerMenu","java/lang/Object");
        map.put("net/minecraft/world/inventory/InventoryMenu","java/lang/Object");
        map.put("net/minecraft/world/entity/player/Abilities",Type.getInternalName(StubAbilities.class));
        map.put("net/minecraft/client/KeyMapping",Type.getInternalName(StubKey.class));
        map.put("com/mojang/blaze3d/platform/InputConstants$Key",Type.getInternalName(StubKeyValue.class));
        map.put("com/mojang/blaze3d/platform/Window",Type.getInternalName(StubWindow.class));
        map.put("cn/academy/port/core/AbilityProgress",Type.getInternalName(StubProgress.class));
        map.put("cn/academy/port/core/SkillPresets",Type.getInternalName(StubPresets.class));
        map.put("cn/academy/port/SkillCatalog",Type.getInternalName(StubCatalog.class));
        map.put("cn/academy/port/preset/PresetSkills",Type.getInternalName(StubPresetSkills.class));
        map.put("org/lwjgl/glfw/GLFW",Type.getInternalName(StubGLFW.class));
        map.put("cn/academy/port/AcademyNetwork$Request",Type.getInternalName(StubRequest.class));
        map.put("net/neoforged/neoforge/network/PacketDistributor",Type.getInternalName(StubPackets.class));
        map.put("net/minecraft/network/protocol/common/custom/CustomPacketPayload","java/lang/Object");
        map.put("net/neoforged/neoforge/client/event/ClientTickEvent$Post",Type.getInternalName(StubPost.class));
        map.put("cn/academy/port/client/BrakingTouchEffects",Type.getInternalName(StubEffects.class));
        var node=read();
        Set<String> kept=Set.of("finishContextDisposals","flushBrakingTouchStart","flushBrakingTouchRelease","brakingTouchGates",
                "sendBrakingTouchCancellation","inputSessionEpoch","singleOwnerEpochMatches","request","mappings",
                "brakingTouchCancelInput","brakingTouchControlValid","tickBrakingTouchInput","pressBrakingTouchInput");
        node.methods.removeIf(m->!kept.contains(m.name)&&!m.name.startsWith("lambda$flushBrakingTouch"));
        node.fields.removeIf(f->!Set.of("brakingTouchInputs","state","singleInputEpoch","serverSingleEpoch","SLOTS","binding").contains(f.name));
        for(var f:node.fields)f.access&=~Opcodes.ACC_FINAL;
        node.innerClasses.clear();node.nestMembers.clear();node.visibleAnnotations=null;node.invisibleAnnotations=null;
        for(var m:node.methods)for(var op:m.instructions)if(op instanceof MethodInsnNode c&&c.owner.equals(CLIENT)){
            if(c.name.equals("newSkillCancelAll")){check(c.desc.equals("()V"),"unrelated new-skill cleanup has no arguments");c.owner=Type.getInternalName(StubNative.class);c.name="noop";}
            else if(c.name.equals("synchronizeSession")||c.name.equals("sourceKey")||c.name.equals("physicalDown"))c.owner=Type.getInternalName(StubNative.class);
            else if(m.name.equals("finishContextDisposals")&&!c.name.equals("flushBrakingTouchStart")&&!c.name.equals("flushBrakingTouchRelease")){
                check(c.desc.equals("()V"),"unrelated legacy callback has no arguments");c.owner=Type.getInternalName(StubNative.class);c.name="noop";
            }
        }
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(new ClassRemapper(writer,new SimpleRemapper(map)));
        byte[] bytes=writer.toByteArray();
        var loader=new ClassLoader(BrakingTouchDispatchRegressionTest.class.getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                if(!name.equals(GENERATED.replace('/','.')))throw new ClassNotFoundException(name);
                return defineClass(name,bytes,0,bytes.length);
            }
        };
        return Class.forName(GENERATED.replace('/','.'),true,loader);
    }
    private static ClassNode read()throws Exception{try(var in=BrakingTouchDispatchRegressionTest.class.getClassLoader().getResourceAsStream(CLIENT+".class")){var n=new ClassNode();new ClassReader(in).accept(n,0);return n;}}
    private static Field field(String name)throws Exception{var f=client.getDeclaredField(name);f.setAccessible(true);return f;}
    private static BrakingTouchInputState input()throws Exception{return (BrakingTouchInputState)field("brakingTouchInputs").get(null);}
    private static StubProgress progress()throws Exception{return (StubProgress)field("state").get(null);}
    private static StubKey[] keys()throws Exception{return (StubKey[])field("SLOTS").get(null);}
    private static BrakingTouchInputState.Gates gates()throws Exception{var m=client.getDeclaredMethod("brakingTouchGates",StubMinecraft.class,int.class,int.class);m.setAccessible(true);return (BrakingTouchInputState.Gates)m.invoke(null,StubMinecraft.INSTANCE,0,keys()[0].key.value);}
    private static void call(String name)throws Exception{var m=client.getDeclaredMethod(name);m.setAccessible(true);m.invoke(null);}
    private static boolean queue(long value)throws Exception{
        var method=client.getDeclaredMethod("pressBrakingTouchInput",HighPowerInput.Input.class,BrakingTouchInputState.Gates.class);
        method.setAccessible(true);return (boolean)method.invoke(null,new HighPowerInput.Input(0,value,19,71),gates());
    }
    private static void queue()throws Exception{check(queue(1),"production press helper/gates admit fresh input");}
    private static void tick()throws Exception{var m=client.getDeclaredMethod("tickBrakingTouchInput",StubMinecraft.class);m.setAccessible(true);m.invoke(null,StubMinecraft.INSTANCE);}
    private static void post()throws Exception{client.getMethod("finishContextDisposals",StubPost.class).invoke(null,new StubPost());}
    private static void nativeMovement(boolean crouch){StubMinecraft.INSTANCE.player.crouching=crouch;packets.add("native_sneak:"+crouch);packets.add("native_move");}
    private static void reset()throws Exception{
        StubMinecraft.INSTANCE=new StubMinecraft();StubCatalog.enabled=true;packets.clear();retiredVisuals.clear();visualState=new BrakingTouchState();
        field("brakingTouchInputs").set(null,new BrakingTouchInputState());field("state").set(null,new StubProgress());
        field("singleInputEpoch").setLong(null,1);field("serverSingleEpoch").setLong(null,71);
        field("SLOTS").set(null,new StubKey[]{new StubKey(-100),new StubKey(-99),new StubKey(19),new StubKey(33)});
    }
    public static final class StubMinecraft{
        static StubMinecraft INSTANCE=new StubMinecraft();public StubLevel level=new StubLevel();public StubPlayer player=new StubPlayer();
        public Object screen;Object connection=new Object();boolean focused=true,physical=true,paused;StubPlayer camera=player;
        public static StubMinecraft getInstance(){return INSTANCE;}public Object getConnection(){return connection;}
        public boolean isPaused(){return paused;}public StubPlayer getCameraEntity(){return camera;}public StubWindow getWindow(){return new StubWindow();}
    }
    public static final class StubLevel{}
    public static final class StubPlayer{
        public boolean noPhysics;boolean alive=true,removed,spectator,passenger,vehicle,sleeping,grounded=true,gliding,swimming,water,lava,climbing,crouching;
        public Object inventoryMenu=new Object(),containerMenu=inventoryMenu;
        final StubAbilities abilities=new StubAbilities();public int getId(){return 19;}public boolean isAlive(){return alive;}
        public boolean isRemoved(){return removed;}public boolean isSpectator(){return spectator;}public boolean isPassenger(){return passenger;}
        public boolean isVehicle(){return vehicle;}public boolean onClimbable(){return climbing;}public boolean isShiftKeyDown(){return crouching;}
        public boolean isInWaterOrBubble(){return water;}public boolean isSleeping(){return sleeping;}public boolean onGround(){return grounded;}public boolean isFallFlying(){return gliding;}
        public boolean isSwimming(){return swimming;}public boolean isInWater(){return water;}public boolean isInLava(){return lava;}
        public StubAbilities getAbilities(){return abilities;}
    }
    public static final class StubAbilities{public boolean instabuild,invulnerable,flying;}
    public static final class StubKey{final StubKeyValue key;StubKey(int v){key=new StubKeyValue(v);}public StubKeyValue getKey(){return key;}}
    public static final class StubKeyValue{int value;StubKeyValue(int v){value=v;}}
    public static final class StubWindow{public long getWindow(){return 1;}}
    public static final class StubGLFW{public static int glfwGetWindowAttrib(long w,int attribute){return StubMinecraft.INSTANCE.focused?1:0;}}
    public static final class StubProgress{
        public boolean activated=true,overloadFine=true,interfering,consuming,learned=true,mapped=true;public String category="vecmanip";public int level=1;
        public StubPresets presets=new StubPresets();public final Map<String,Integer> cooldowns=new HashMap<>();
        public boolean isActivated(){return activated;}public boolean learned(String id){return learned;}public boolean consumptionInProgress(){return consuming;}
    }
    public static final class StubPresets{int preset;long revision=1;String skill="braking_touch";public int current(){return preset;}public long revision(){return revision;}public String currentSkill(int slot){return slot==0?skill:"";}}
    public static final class StubCatalog{static boolean enabled=true;public static boolean enabled(String category,String skill){return enabled;}}
    public static final class StubPresetSkills{public static boolean mappedUsable(StubProgress state,String skill){return state.mapped;}}
    public static final class StubNative{public static void synchronizeSession(StubMinecraft mc){}public static int sourceKey(StubKeyValue key){return key.value;}public static boolean physicalDown(int key){return StubMinecraft.INSTANCE.physical;}public static void noop(){}}
    public static final class StubEffects{public static void abortLocal(long input,long epoch){retiredVisuals.add(input+":"+epoch);visualState.cancel(epoch,input);}}
    public record StubRequest(String action,String value){}
    public static final class StubPackets{public static void sendToServer(Object payload,Object...rest){var p=(StubRequest)payload;packets.add(p.action+":"+p.value);}}
    public static final class StubPost{}
}
