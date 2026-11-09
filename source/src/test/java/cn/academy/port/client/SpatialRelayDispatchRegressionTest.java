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
public final class SpatialRelayDispatchRegressionTest {
    private static final String CLIENT="cn/academy/port/client/AcademyClient", GENERATED="cn/academy/port/client/generated/SpatialRelayDispatchClient";
    private static int checks;
    private static final List<String> packets=new ArrayList<>();
    private static Class<?> client;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        inspectRealDispatchSites(); client=load();
        for(boolean released:new boolean[]{false,true}) {
            reset();queue();if(released)input().release(input().current());
            check(packets.isEmpty(),"genuine Pre down/up makes no request");nativeMovement(true);post();
            var expected=new ArrayList<>(List.of("native_sneak:true","native_move","hp_spatial_relay_start:0:1:19:71"));
            if(released)expected.add("hp_spatial_relay_release:0:1:19:71");
            check(packets.equals(expected),"compiled Post sends both edges strictly after native movement");
            post();check(packets.equals(expected),"no repeated packet while held or after release");
        }
        reset();queue();nativeMovement(false);post();
        for(int i=0;i<6;i++)post();
        check(packets.size()==3&&input().holding(),"heldclear never sends an action before physical release");
        input().ended(1,71);input().release(input().current());nativeMovement(false);post();
        check(packets.stream().noneMatch(p->p.startsWith("hp_spatial_relay_release")),"clear receipt prevents late release action");
        reset();queue();nativeMovement(false);post();input().release(input().current());
        check(packets.size()==3,"native up is queued, not sent immediately");nativeMovement(true);post();
        check(packets.subList(3,6).equals(List.of("native_sneak:true","native_move","hp_spatial_relay_release:0:1:19:71")),"release samples latest server-native crouch before action");
        List<Consumer<StubMinecraft>> losses=List.of(m->m.screen=new Object(),m->m.focused=false,m->m.camera=new StubPlayer(),
                m->m.player.alive=false,m->m.player.removed=true,m->m.player.spectator=true,
                m->m.level=new StubLevel(),m->m.player=new StubPlayer(),m->m.connection=new Object());
        for(var loss:losses) {
            reset();queue();nativeMovement(false);post();input().release(input().current());loss.accept(StubMinecraft.INSTANCE);nativeMovement(true);post();
            check(packets.stream().noneMatch(p->p.startsWith("hp_spatial_relay_release")),"ownership loss between native edges prevents release action");
            check(input().current()==null,"ownership cancellation clears all queued edges");
        }
        for(var loss:List.<Consumer<StubProgress>>of(p->p.activated=false,p->p.overloadFine=false,p->p.interfering=true,
                p->p.consuming=true,p->p.category="electromaster",p->p.level=3,p->p.learned=false,p->p.mapped=false,
                p->p.presets.preset++,p->p.presets.revision++,p->p.presets.skill="other")) {
            reset();queue();nativeMovement(false);post();input().release(input().current());loss.accept(progress());post();
            check(packets.stream().noneMatch(p->p.startsWith("hp_spatial_relay_release"))&&input().current()==null,
                    "compiled category/preset/learning gates cancel release before commit");
        }
        reset();queue();nativeMovement(false);post();input().release(input().current());keys()[0].key.value=30;post();
        check(input().current()==null&&packets.stream().noneMatch(p->p.startsWith("hp_spatial_relay_release")),"physical remap cannot transfer release");
        reset();queue();nativeMovement(false);post();input().release(input().current());field("serverSingleEpoch").setLong(null,72);post();
        check(packets.size()==3&&input().current()==null,"owner epoch mismatch sends neither release nor cross-owner abort");
        reset();queue();StubMinecraft.INSTANCE.paused=true;post();
        check(packets.isEmpty()&&input().queuedStart()!=null,"pause with valid ownership defers start");
        StubMinecraft.INSTANCE.paused=false;nativeMovement(false);post();input().release(input().current());post();input().accepted(1,71,400,false);
        StubMinecraft.INSTANCE.paused=true;for(int i=0;i<80;i++)tick();
        check(input().acceptedRemainingTicks()==400,"actual paused tick freezes mark lifetime");
        StubMinecraft.INSTANCE.screen=new Object();tick();StubMinecraft.INSTANCE.screen=null;StubMinecraft.INSTANCE.paused=false;post();
        check(input().current()==null&&packets.stream().filter(p->p.startsWith("hp_spatial_relay_release")).count()==1,
                "pause-menu cancellation cannot execute a new relay on resume");
        reset();queue();nativeMovement(false);post();input().release(input().current());post();input().accepted(1,71,400,false);
        StubMinecraft.INSTANCE.player.grounded=false;progress().cooldowns.put("spatial_relay",40);
        for(int i=0;i<70;i++)tick();
        check(input().permits(1,71)&&input().acceptedRemainingTicks()==330,"relay marks survives walking and own cooldown");
        check(input().press(new HighPowerInput.Input(0,2,19,71),gates()),"actual cooldown/airborne gates retain free holdclear path");
        nativeMovement(true);post();check(packets.getLast().equals("hp_spatial_relay_start:0:2:19:71"),"freehold begins with identity-only Post start");
        // Mutation witness: moving the real compiled flush to Pre exposes the native crouch race.
        reset();queue();input().release(input().current());call("flushSpatialRelayStart");call("flushSpatialRelayRelease");nativeMovement(true);
        check(packets.getFirst().startsWith("hp_spatial_relay_start")&&packets.get(1).startsWith("hp_spatial_relay_release"),
                "Pre-dispatch mutation reproduces stale movement ordering");
        System.out.println("SpatialRelayDispatchRegressionTest: "+checks+" exact compiled Post/gate/transport checks passed");
    }
    private static void inspectRealDispatchSites()throws Exception{
        ClassNode source=read();int postCalls=0;boolean annotation=false;
        for(var m:source.methods){
            for(var op:m.instructions)if(op instanceof MethodInsnNode c&&c.owner.equals(CLIENT)&&(c.name.equals("flushSpatialRelayStart")||c.name.equals("flushSpatialRelayRelease"))){
                check(m.name.equals("finishContextDisposals")&&m.desc.contains("ClientTickEvent$Post"),"only actual ClientTickEvent.Post may dispatch NEW343");postCalls++;
            }
            if(m.name.equals("finishContextDisposals")&&m.visibleAnnotations!=null)
                annotation=m.visibleAnnotations.stream().anyMatch(a->a.desc.equals("Lnet/neoforged/bus/api/SubscribeEvent;"));
        }
        check(postCalls==2&&annotation,"both production edges have exactly one subscribed Post dispatch site");
        for(String method:List.of("cancelPersistentHighPowerInputs","synchronizeSession")) {
            var body=source.methods.stream().filter(m->m.name.equals(method)).findFirst().orElseThrow();
            boolean wired=false;
            for(var op:body.instructions)if(op instanceof MethodInsnNode c&&c.owner.equals(CLIENT)&&c.name.equals("spatialRelayCancelInput"))wired=true;
            check(wired,"explicit lifecycle cancellation includes relay before discard: "+method);
        }

    }
    private static Class<?> load()throws Exception{
        var map=new HashMap<String,String>();map.put(CLIENT,GENERATED);
        map.put("net/minecraft/client/Minecraft",Type.getInternalName(StubMinecraft.class));
        map.put("net/minecraft/client/multiplayer/ClientLevel",Type.getInternalName(StubLevel.class));
        map.put("net/minecraft/client/multiplayer/ClientPacketListener","java/lang/Object");
        map.put("net/minecraft/client/gui/screens/Screen","java/lang/Object");
        for(var name:List.of("net/minecraft/world/entity/Entity","net/minecraft/world/entity/player/Player","net/minecraft/client/player/LocalPlayer"))map.put(name,Type.getInternalName(StubPlayer.class));
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
        map.put("cn/academy/port/client/SpatialRelayEffects",Type.getInternalName(StubEffects.class));
        var node=read();
        Set<String> kept=Set.of("finishContextDisposals","flushSpatialRelayStart","flushSpatialRelayRelease","spatialRelayGates","sendSpatialRelayStart","sendSpatialRelayRelease",
                "sendSpatialRelayCancellation","inputSessionEpoch","singleOwnerEpochMatches","request","mappings",
                "spatialRelayCancelInput","spatialRelayControlValid","tickSpatialRelayInput");
        node.methods.removeIf(m->!kept.contains(m.name));
        node.fields.removeIf(f->!Set.of("spatialRelayInputs","state","singleInputEpoch","serverSingleEpoch","SLOTS","binding").contains(f.name));
        for(var f:node.fields)f.access&=~Opcodes.ACC_FINAL;
        node.innerClasses.clear();node.nestMembers.clear();node.visibleAnnotations=null;node.invisibleAnnotations=null;
        for(var m:node.methods)for(var op:m.instructions)if(op instanceof MethodInsnNode c&&c.owner.equals(CLIENT)){
            if(c.name.equals("synchronizeSession")||c.name.equals("sourceKey")||c.name.equals("physicalDown"))c.owner=Type.getInternalName(StubNative.class);
            else if(m.name.equals("finishContextDisposals")&&!c.name.equals("flushSpatialRelayStart")&&!c.name.equals("flushSpatialRelayRelease")){
                check(c.desc.equals("()V"),"unrelated legacy callback has no arguments");c.owner=Type.getInternalName(StubNative.class);c.name="noop";
            }
        }
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(new ClassRemapper(writer,new SimpleRemapper(map)));
        byte[] bytes=writer.toByteArray();
        var loader=new ClassLoader(SpatialRelayDispatchRegressionTest.class.getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                if(!name.equals(GENERATED.replace('/','.')))throw new ClassNotFoundException(name);
                return defineClass(name,bytes,0,bytes.length);
            }
        };
        return Class.forName(GENERATED.replace('/','.'),true,loader);
    }
    private static ClassNode read()throws Exception{try(var in=SpatialRelayDispatchRegressionTest.class.getClassLoader().getResourceAsStream(CLIENT+".class")){var n=new ClassNode();new ClassReader(in).accept(n,0);return n;}}
    private static Field field(String name)throws Exception{var f=client.getDeclaredField(name);f.setAccessible(true);return f;}
    private static SpatialRelayInputState input()throws Exception{return (SpatialRelayInputState)field("spatialRelayInputs").get(null);}
    private static StubProgress progress()throws Exception{return (StubProgress)field("state").get(null);}
    private static StubKey[] keys()throws Exception{return (StubKey[])field("SLOTS").get(null);}
    private static SpatialRelayInputState.Gates gates()throws Exception{var m=client.getDeclaredMethod("spatialRelayGates",StubMinecraft.class,int.class,int.class);m.setAccessible(true);return (SpatialRelayInputState.Gates)m.invoke(null,StubMinecraft.INSTANCE,0,keys()[0].key.value);}
    private static void call(String name)throws Exception{var m=client.getDeclaredMethod(name);m.setAccessible(true);m.invoke(null);}
    private static void queue()throws Exception{check(input().press(new HighPowerInput.Input(0,1,19,71),gates()),"production gates admit fresh input");}
    private static void tick()throws Exception{var m=client.getDeclaredMethod("tickSpatialRelayInput",StubMinecraft.class);m.setAccessible(true);m.invoke(null,StubMinecraft.INSTANCE);}
    private static void post()throws Exception{client.getMethod("finishContextDisposals",StubPost.class).invoke(null,new StubPost());}
    private static void nativeMovement(boolean crouch){packets.add("native_sneak:"+crouch);packets.add("native_move");}
    private static void reset()throws Exception{
        StubMinecraft.INSTANCE=new StubMinecraft();StubCatalog.enabled=true;packets.clear();
        field("spatialRelayInputs").set(null,new SpatialRelayInputState());field("state").set(null,new StubProgress());
        field("singleInputEpoch").setLong(null,1);field("serverSingleEpoch").setLong(null,71);
        field("SLOTS").set(null,new StubKey[]{new StubKey(-100),new StubKey(-99),new StubKey(19),new StubKey(33)});
    }
    public static final class StubMinecraft{
        static StubMinecraft INSTANCE=new StubMinecraft();public StubLevel level=new StubLevel();public StubPlayer player=new StubPlayer();
        public Object screen;Object connection=new Object();boolean focused=true,paused;StubPlayer camera=player;
        public static StubMinecraft getInstance(){return INSTANCE;}public Object getConnection(){return connection;}
        public boolean isPaused(){return paused;}public StubPlayer getCameraEntity(){return camera;}public StubWindow getWindow(){return new StubWindow();}
    }
    public static final class StubLevel{}
    public static final class StubPlayer{
        public boolean noPhysics;boolean alive=true,removed,spectator,passenger,sleeping,grounded=true,gliding,swimming,water,lava,sneaking;
        final StubAbilities abilities=new StubAbilities();public int getId(){return 19;}public boolean isAlive(){return alive;}
        public boolean isRemoved(){return removed;}public boolean isSpectator(){return spectator;}public boolean isPassenger(){return passenger;}
        public boolean isSleeping(){return sleeping;}public boolean onGround(){return grounded;}public boolean isFallFlying(){return gliding;}
        public boolean isSwimming(){return swimming;}public boolean isInWater(){return water;}public boolean isInLava(){return lava;}
        public boolean isShiftKeyDown(){return sneaking;}public StubAbilities getAbilities(){return abilities;}
    }
    public static final class StubAbilities{public boolean instabuild,invulnerable;}
    public static final class StubKey{final StubKeyValue key;StubKey(int v){key=new StubKeyValue(v);}public StubKeyValue getKey(){return key;}}
    public static final class StubKeyValue{int value;StubKeyValue(int v){value=v;}}
    public static final class StubWindow{public long getWindow(){return 1;}}
    public static final class StubGLFW{public static int glfwGetWindowAttrib(long w,int attribute){return StubMinecraft.INSTANCE.focused?1:0;}}
    public static final class StubProgress{
        public boolean activated=true,overloadFine=true,interfering,consuming,learned=true,mapped=true;public String category="teleporter";public int level=4;
        public StubPresets presets=new StubPresets();public final Map<String,Integer> cooldowns=new HashMap<>();
        public boolean isActivated(){return activated;}public boolean learned(String id){return learned;}public boolean consumptionInProgress(){return consuming;}
    }
    public static final class StubPresets{int preset;long revision=1;String skill="spatial_relay";public int current(){return preset;}public long revision(){return revision;}public String currentSkill(int slot){return slot==0?skill:"";}}
    public static final class StubCatalog{static boolean enabled=true;public static boolean enabled(String category,String skill){return enabled;}}
    public static final class StubPresetSkills{public static boolean mappedUsable(StubProgress state,String skill){return state.mapped;}}
    public static final class StubNative{public static void synchronizeSession(StubMinecraft mc){}public static int sourceKey(StubKeyValue key){return key.value;}public static boolean physicalDown(int key){return false;}public static void noop(){}}
    public static final class StubEffects{public static void abortLocal(long input,long epoch){}}
    public record StubRequest(String action,String value){}
    public static final class StubPackets{public static void sendToServer(Object payload,Object...rest){var p=(StubRequest)payload;packets.add(p.action+":"+p.value);}}
    public static final class StubPost{}
}
