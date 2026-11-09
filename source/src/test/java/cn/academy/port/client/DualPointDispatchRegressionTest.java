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

/** Actual compiled AcademyClient gates + host wiring, not a copied eligibility oracle. */
public final class DualPointDispatchRegressionTest {
    private static final String CLIENT="cn/academy/port/client/AcademyClient",GENERATED="cn/academy/port/client/generated/DualPointDispatchClient";
    private static int checks;private static Class<?> client;private static final List<String> packets=new ArrayList<>();
    private static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        inspectHost();client=load();reset();
        var id=new HighPowerInput.Input(0,1,19,71);
        check(input().tap(id,gates()),"actual compiled gates admit fresh dual tap");input().accepted(1,71);
        progress().cooldowns.put("dual_point",4);
        check(delegate(),"actual compiled delegate retains acknowledged input through own CD");
        check(!input().eligible(gates())&&!input().tap(new HighPowerInput.Input(0,2,19,71),gates()),"fresh tap cannot bypass own CD");
        input().release(id);check(delegate(),"ordinary release does not abort paid second slot");
        input().ended(1,71);check(!delegate(),"finished receipt no longer exempts cooldown");
        List<Consumer<StubMinecraft>> losses=List.of(m->m.screen=new Object(),m->m.focused=false,m->m.camera=new StubPlayer(),
          m->m.player.alive=false,m->m.player.removed=true,m->m.player.spectator=true,m->m.player.passenger=true,
          m->m.player.abilities.instabuild=true,m->m.player.abilities.invulnerable=true,m->m.player.noPhysics=true,
          m->m.player.sleeping=true,m->m.level=new StubLevel(),m->m.player=new StubPlayer(),m->m.connection=new Object());
        for(var loss:losses){reset();input().tap(id,gates());input().accepted(1,71);progress().cooldowns.put("dual_point",4);loss.accept(StubMinecraft.INSTANCE);check(!delegate(),"real control/environment loss rejects own receipt");}
        for(var loss:List.<Consumer<StubProgress>>of(v->v.activated=false,v->v.overloadFine=false,v->v.interfering=true,v->v.consuming=true,
          v->v.category="electromaster",v->v.level=0,v->v.level=6,v->v.learned=false,v->v.mapped=false,
          v->v.presets.preset++,v->v.presets.revision++,v->v.presets.skill="other")){
          reset();input().tap(id,gates());input().accepted(1,71);progress().cooldowns.put("dual_point",4);loss.accept(progress());check(!delegate(),"category/progress/preset changes still refuse receipt");}
        reset();input().tap(id,gates());StubCatalog.enabled=false;check(!delegate(),"disabled skill still cancels");
        reset();input().tap(id,gates());field("serverSingleEpoch").setLong(null,72);check(!delegate(),"owner epoch replacement still cancels");
        reset();input().tap(id,gates());field("singleInputEpoch").setLong(null,2);check(!delegate(),"session replacement still cancels");
        reset();input().tap(id,gates());keys()[0].key.value++;check(!delegate(),"key rebinding still cancels");
        reset();progress().cooldowns.put("dual_point",4);check(!delegate(),"CD without owned receipt is never eligible");
        System.out.println("DualPointDispatchRegressionTest: "+checks+" actual compiled gate/host checks passed");
    }
    private static boolean delegate()throws Exception{var m=client.getDeclaredMethod("dualPointDelegateEligible",StubMinecraft.class,int.class,int.class);m.setAccessible(true);return (boolean)m.invoke(null,StubMinecraft.INSTANCE,0,keys()[0].key.value);}
    private static void inspectHost()throws Exception{
        var actual=read();int gatedTap=0,freshGuard=0;
        for(var method:actual.methods)for(var op:method.instructions)if(op instanceof MethodInsnNode call&&call.owner.equals("cn/academy/port/client/DualPointInputState")){
            if(call.name.equals("tap")){check(call.desc.contains("DualPointInputState$Gates"),"actual compiled press captures validated control gates");gatedTap++;}
            if(call.name.equals("eligible"))freshGuard++;
        }
        check(gatedTap==1&&freshGuard==1,"actual compiled press has one fresh-CD guard and one gated tap");
        try(var in=DualPointDispatchRegressionTest.class.getClassLoader().getResourceAsStream(CLIENT+"$1.class")){
          var n=new ClassNode();new ClassReader(in).accept(n,0);int calls=0;
          for(var m:n.methods)if(m.name.equals("eligible"))for(var op:m.instructions)if(op instanceof MethodInsnNode c&&c.owner.equals(CLIENT)&&c.name.equals("dualPointDelegateEligible"))calls++;
          check(calls==1,"actual compiled Host.eligible routes dual to dedicated receipt gate exactly once");
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
        map.put("cn/academy/port/client/DualPointEffects",Type.getInternalName(StubEffects.class));
        var node=read();
        Set<String> kept=Set.of("dualPointGates","dualPointDelegateEligible","inputSessionEpoch","mappings");
        node.methods.removeIf(m->!kept.contains(m.name));
        node.fields.removeIf(f->!Set.of("dualPointInputs","state","singleInputEpoch","serverSingleEpoch","SLOTS").contains(f.name));
        for(var f:node.fields)f.access&=~Opcodes.ACC_FINAL;
        node.innerClasses.clear();node.nestMembers.clear();node.visibleAnnotations=null;node.invisibleAnnotations=null;
        for(var m:node.methods)for(var op:m.instructions)if(op instanceof MethodInsnNode c&&c.owner.equals(CLIENT)){
            if(c.name.equals("synchronizeSession")||c.name.equals("sourceKey")||c.name.equals("physicalDown"))c.owner=Type.getInternalName(StubNative.class);
        }
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(new ClassRemapper(writer,new SimpleRemapper(map)));
        byte[] bytes=writer.toByteArray();
        var loader=new ClassLoader(DualPointDispatchRegressionTest.class.getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                if(!name.equals(GENERATED.replace('/','.')))throw new ClassNotFoundException(name);
                return defineClass(name,bytes,0,bytes.length);
            }
        };
        return Class.forName(GENERATED.replace('/','.'),true,loader);
    }
    private static ClassNode read()throws Exception{try(var in=DualPointDispatchRegressionTest.class.getClassLoader().getResourceAsStream(CLIENT+".class")){var n=new ClassNode();new ClassReader(in).accept(n,0);return n;}}
    private static Field field(String name)throws Exception{var f=client.getDeclaredField(name);f.setAccessible(true);return f;}
    private static DualPointInputState input()throws Exception{return (DualPointInputState)field("dualPointInputs").get(null);}
    private static StubProgress progress()throws Exception{return (StubProgress)field("state").get(null);}
    private static StubKey[] keys()throws Exception{return (StubKey[])field("SLOTS").get(null);}
    private static DualPointInputState.Gates gates()throws Exception{var m=client.getDeclaredMethod("dualPointGates",StubMinecraft.class,int.class,int.class);m.setAccessible(true);return (DualPointInputState.Gates)m.invoke(null,StubMinecraft.INSTANCE,0,keys()[0].key.value);}
    private static void reset()throws Exception{
        StubMinecraft.INSTANCE=new StubMinecraft();StubCatalog.enabled=true;packets.clear();
        field("dualPointInputs").set(null,new DualPointInputState());field("state").set(null,new StubProgress());
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
        public boolean noPhysics;boolean crouch;public boolean isShiftKeyDown(){return crouch;}boolean alive=true,removed,spectator,passenger,sleeping,grounded=true,gliding,swimming,water,lava;
        final StubAbilities abilities=new StubAbilities();public int getId(){return 19;}public boolean isAlive(){return alive;}
        public boolean isRemoved(){return removed;}public boolean isSpectator(){return spectator;}public boolean isPassenger(){return passenger;}
        public boolean isSleeping(){return sleeping;}public boolean onGround(){return grounded;}public boolean isFallFlying(){return gliding;}
        public boolean isSwimming(){return swimming;}public boolean isInWater(){return water;}public boolean isInLava(){return lava;}
        public StubAbilities getAbilities(){return abilities;}
    }
    public static final class StubAbilities{public boolean instabuild,invulnerable;}
    public static final class StubKey{final StubKeyValue key;StubKey(int v){key=new StubKeyValue(v);}public StubKeyValue getKey(){return key;}}
    public static final class StubKeyValue{int value;StubKeyValue(int v){value=v;}}
    public static final class StubWindow{public long getWindow(){return 1;}}
    public static final class StubGLFW{public static int glfwGetWindowAttrib(long w,int attribute){return StubMinecraft.INSTANCE.focused?1:0;}}
    public static final class StubProgress{
        public boolean activated=true,overloadFine=true,interfering,consuming,learned=true,mapped=true;public String category="meltdowner";public int level=1;
        public StubPresets presets=new StubPresets();public final Map<String,Integer> cooldowns=new HashMap<>();
        public boolean isActivated(){return activated;}public boolean learned(String id){return learned;}public boolean consumptionInProgress(){return consuming;}
    }
    public static final class StubPresets{int preset;long revision=1;String skill="dual_point";public int current(){return preset;}public long revision(){return revision;}public String currentSkill(int slot){return slot==0?skill:"";}}
    public static final class StubCatalog{static boolean enabled=true;public static boolean enabled(String category,String skill){return enabled;}}
    public static final class StubPresetSkills{public static boolean mappedUsable(StubProgress state,String skill){return state.mapped;}}
    public static final class StubNative{public static void synchronizeSession(StubMinecraft mc){}public static int sourceKey(StubKeyValue key){return key.value;}public static boolean physicalDown(int key){return false;}public static void noop(){}}
    public static final class StubEffects{public static void abortLocal(long input,long epoch){}}
    public record StubRequest(String action,String value){}
    public static final class StubPackets{public static void sendToServer(Object payload,Object...rest){var p=(StubRequest)payload;packets.add(p.action+":"+p.value);}}
    public static final class StubPost{}
}
