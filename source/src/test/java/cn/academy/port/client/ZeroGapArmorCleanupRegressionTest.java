package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry;
import java.lang.reflect.Field;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

/** Actual compiled Effects receive/Post -> AcademyClient cancellation/transport, with engine boundaries substituted. */
public final class ZeroGapArmorCleanupRegressionTest {
    private static final String EFFECTS="cn/academy/port/client/ZeroGapArmorEffects", CLIENT="cn/academy/port/client/AcademyClient";
    private static final String HE="cn/academy/port/client/generated/ZeroGapCleanupEffects", HC="cn/academy/port/client/generated/ZeroGapCleanupClient";
    private static int checks;
    private static Harness current;
    private static final List<StubRequest> sent=new ArrayList<>();
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var h=harness(false);current=h;
        for(boolean released:new boolean[]{false,true}){
            h.reset();h.start(1);if(released)h.input().release(h.input().current());
            // Minecraft opens ChatScreen inside handleKeybinds, after Pre and before Effects Post.
            check(h.input().tick(h.gates(),false)==null,"Pre still sees an eligible owner");
            StubMinecraft.INSTANCE.screen=new Object();h.tick();
            check(sent.size()==1&&sent.getFirst().value().equals("0:1:19:71"),"Post chat loss sends matching abort after release="+released);
            check(h.input().current()==null,"Post clears input only after dispatch");
            h.tick();check(sent.size()==1,"next tick cannot send duplicate chat abort");
        }
        h.reset();h.start(1);
        for(int tick=0;tick<ZeroGapArmorState.QUIET_TICKS;tick++)h.tick();
        check(sent.size()==1,"quiet lease expiry sends one abort");
        for(int tick=0;tick<10;tick++)h.tick();
        check(sent.size()==1,"cosmetic fade removal does not send another abort");

        h.reset();h.start(1);h.receive(1,2,ZeroGapArmorState.ENDED);
        check(h.input().current()==null&&sent.isEmpty(),"true received server ENDED is a no-wire terminal");
        for(int tick=0;tick<10;tick++)h.tick();
        h.receive(1,2,ZeroGapArmorState.ENDED);h.loss(1,71);
        check(sent.isEmpty(),"duplicate server ENDED and delayed cosmetic loss stay no-wire");

        h.reset();h.start(1);h.loss(1,72);h.loss(99,71);
        check(sent.isEmpty()&&h.input().current()!=null,"wrong identity or epoch cannot cancel current owner");
        h.receive(1,2,ZeroGapArmorState.ENDED);h.start(2);h.loss(1,71);
        check(sent.isEmpty()&&h.input().current().input()==2,"stale visual cannot cancel a newer activation");
        h.loss(2,71);check(sent.size()==1&&sent.getFirst().value().equals("0:2:19:71"),"matching new visual closes only its owner");

        h.reset();h.start(1);
        check(h.input().press(new HighPowerInput.Input(0,2,19,71),h.gates()).dispatch(),"fresh off request captured");
        h.loss(1,71);
        check(sent.size()==1&&sent.getFirst().value().equals("0:2:19:71"),"original accepted visual loss aborts the latest pending-off identity");
        h.loss(1,71);h.loss(2,71);check(sent.size()==1,"old/new duplicate local losses send only once");

        h.reset();h.start(1);StubPackets.failOnce=true;
        try{h.loss(1,71);throw new AssertionError("dispatch failure was not injected");}
        catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof IllegalStateException,"actual callback surfaces transport failure");}
        check(h.input().current()!=null&&sent.isEmpty(),"failed dispatch retains its abort identity");
        h.loss(1,71);check(sent.size()==1&&h.input().current()==null,"later retry sends once and releases ownership");

        h.reset();h.start(1);StubMinecraft.INSTANCE.player.alive=false;
        h.menuFence().press(0,true);
        h.deathCancel();h.discard();
        check(sent.size()==1&&h.epoch()==0&&h.input().current()==null,"death abort precedes loss of the server epoch");
        check(!h.menuFence().blocks(-100),"actual discard also clears the retained production menu-mouse fence");
        h.deathCancel();check(sent.size()==1,"repeated death cleanup is idempotent");
        verifyDeathOrder();

        // Reintroduce precisely the old callback at the production Effects Post callsite.
        var broken=harness(true);current=broken;broken.reset();broken.start(1);
        StubMinecraft.INSTANCE.screen=new Object();broken.tick();
        check(sent.isEmpty()&&broken.input().current()==null,"old ended-callback mutation reproduces orphaned server ownership");
        check(broken.input().tick(broken.gates(),false)==null,"next Pre cannot recover the lost abort identity");
        var missing=harness(false,true);current=missing;missing.reset();
        try{missing.discard();throw new AssertionError("missing menu-fence closure was not detected");}
        catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof NoSuchFieldError&&expected.getCause().getMessage().contains("menuMouseFence"),"old missing-field fixture mutation still fails at actual discardBinding");}
        System.out.println("PASS ZeroGapArmorCleanupRegressionTest: "+checks+" compiled callback-order and mutation checks");
    }
    private static void verifyDeathOrder()throws Exception{
        var source=read(CLIENT);boolean found=false;
        for(var method:source.methods)if(method.name.equals("tick")){
            List<String> calls=new ArrayList<>();
            for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(CLIENT))calls.add(call.name);
            int discard=calls.indexOf("discardBinding");
            found=discard>0&&calls.get(discard-1).equals("cancelZeroGapArmorBeforeDiscard")&&calls.get(discard+1).equals("clearEffects");
        }
        check(found,"actual AcademyClient death branch cancels before discardBinding and clearEffects");
    }
    private static Harness harness(boolean oldCallback)throws Exception{return harness(oldCallback,false);}
    private static Harness harness(boolean oldCallback,boolean missingMenuFence)throws Exception{
        Map<String,String> mappings=new HashMap<>();mappings.put(EFFECTS,HE);mappings.put(EFFECTS+"$Marker",HE+"$Marker");mappings.put(CLIENT,HC);
        for(String name:List.of("net/minecraft/world/entity/Entity","net/minecraft/world/entity/player/Player","net/minecraft/client/player/LocalPlayer"))mappings.put(name,Type.getInternalName(StubPlayer.class));
        mappings.put("net/minecraft/client/Minecraft",Type.getInternalName(StubMinecraft.class));
        mappings.put("net/minecraft/client/multiplayer/ClientLevel",Type.getInternalName(StubLevel.class));
        mappings.put("net/minecraft/client/multiplayer/ClientPacketListener","java/lang/Object");
        mappings.put("net/minecraft/client/gui/screens/Screen","java/lang/Object");
        mappings.put("net/minecraft/world/entity/player/Abilities",Type.getInternalName(StubAbilities.class));
        mappings.put("net/minecraft/world/phys/Vec3",Type.getInternalName(StubVector.class));
        mappings.put("cn/academy/port/highpower/ZeroGapArmor$Visual",Type.getInternalName(StubVisual.class));
        mappings.put("cn/academy/port/core/AbilityProgress",Type.getInternalName(StubProgress.class));
        mappings.put("cn/academy/port/core/SkillPresets",Type.getInternalName(StubPresets.class));
        mappings.put("cn/academy/port/SkillCatalog",Type.getInternalName(StubCatalog.class));
        mappings.put("cn/academy/port/AcademyNetwork$Request",Type.getInternalName(StubRequest.class));
        mappings.put("net/neoforged/neoforge/network/PacketDistributor",Type.getInternalName(StubPackets.class));
        mappings.put("net/minecraft/network/protocol/common/custom/CustomPacketPayload","java/lang/Object");
        mappings.put("net/neoforged/neoforge/client/event/ClientTickEvent$Post",Type.getInternalName(StubPost.class));
        var effects=read(EFFECTS);var marker=read(EFFECTS+"$Marker");var client=read(CLIENT);
        effects.methods.removeIf(m->!Set.of("receive","frame","point","abortLocal","clear","synchronize","localEligible","validOwner","tick","lambda$receive$0").contains(m.name));
        effects.fields.removeIf(f->!Set.of("STATE","MARKERS","world","localPlayer","connection","generation").contains(f.name));
        effects.innerClasses.removeIf(c->!c.name.equals(EFFECTS+"$Marker"));effects.nestMembers.removeIf(n->!n.equals(EFFECTS+"$Marker"));
        effects.visibleAnnotations=null;effects.invisibleAnnotations=null;
        var ei=new MethodNode(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);
        initialize(ei,EFFECTS,"STATE","cn/academy/port/client/ZeroGapArmorState","Lcn/academy/port/client/ZeroGapArmorState;");
        initialize(ei,EFFECTS,"MARKERS","java/util/LinkedHashMap","Ljava/util/Map;");finishInit(effects,ei);
        for(var method:effects.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(CLIENT)){
            if(Set.of("inputSessionEpoch","sameInputSessionEpoch").contains(call.name))call.owner=Type.getInternalName(StubSession.class);
            if(oldCallback&&method.name.equals("tick")&&call.name.equals("zeroGapArmorLocalVisualLost"))call.name="zeroGapArmorEndedInput";
        }
        client.methods.removeIf(m->!Set.of("zeroGapArmorInputMatches","zeroGapArmorAcceptedInput","zeroGapArmorEndedInput","zeroGapArmorLocalVisualLost",
                "sendZeroGapArmorCancellation","cancelZeroGapArmorBeforeDiscard","cancelZeroGapArmorLocally","sendPersistentCancellation",
                "singleOwnerEpochMatches","request","discardBinding").contains(m.name));
        client.fields.removeIf(f->!Set.of("zeroGapArmorInputs","serverSingleEpoch","singleInputEpoch","state","binding","overrideKeys","menuMouseFence").contains(f.name));
        if(missingMenuFence)client.fields.removeIf(f->f.name.equals("menuMouseFence"));
        client.innerClasses.clear();client.nestMembers.clear();client.visibleAnnotations=null;client.invisibleAnnotations=null;
        var ci=new MethodNode(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);
        initialize(ci,CLIENT,"zeroGapArmorInputs","cn/academy/port/client/ZeroGapArmorInputState","Lcn/academy/port/client/ZeroGapArmorInputState;");
        if(!missingMenuFence)initialize(ci,CLIENT,"menuMouseFence","cn/academy/port/client/MenuMouseInputFence","Lcn/academy/port/client/MenuMouseInputFence;");
        initialize(ci,CLIENT,"state","cn/academy/port/core/AbilityProgress","Lcn/academy/port/core/AbilityProgress;");finishInit(client,ci);
        Map<String,byte[]> classes=Map.of(HE.replace('/','.'),remap(effects,mappings),(HE+"$Marker").replace('/','.'),remap(marker,mappings),HC.replace('/','.'),remap(client,mappings));
        ClassLoader loader=new ClassLoader(ZeroGapArmorCleanupRegressionTest.class.getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{byte[] bytes=classes.get(name);if(bytes==null)throw new ClassNotFoundException(name);return defineClass(name,bytes,0,bytes.length);}
        };
        return new Harness(Class.forName(HE.replace('/','.'),true,loader),Class.forName(HC.replace('/','.'),true,loader));
    }
    private static void initialize(MethodNode init,String owner,String field,String type,String descriptor){
        init.instructions.add(new TypeInsnNode(Opcodes.NEW,type));init.instructions.add(new InsnNode(Opcodes.DUP));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,type,"<init>","()V",false));init.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC,owner,field,descriptor));
    }
    private static void finishInit(ClassNode owner,MethodNode init){init.instructions.add(new InsnNode(Opcodes.RETURN));init.maxStack=2;owner.methods.add(init);}
    private static ClassNode read(String name)throws Exception{
        try(var stream=ZeroGapArmorCleanupRegressionTest.class.getClassLoader().getResourceAsStream(name+".class")){
            if(stream==null)throw new IllegalStateException("Missing production class "+name);var node=new ClassNode();new ClassReader(stream).accept(node,0);return node;
        }
    }
    private static byte[] remap(ClassNode node,Map<String,String> mappings){var writer=new ClassWriter(0);node.accept(new ClassRemapper(writer,new SimpleRemapper(mappings)));return writer.toByteArray();}
    private record Harness(Class<?> effects,Class<?> client){
        void reset()throws Exception{StubMinecraft.INSTANCE=new StubMinecraft();input().reset();field("serverSingleEpoch").setLong(null,71);effects.getMethod("clear").invoke(null);sent.clear();StubPackets.failOnce=false;}
        Field field(String name)throws Exception{var field=client.getDeclaredField(name);field.setAccessible(true);return field;}
        ZeroGapArmorInputState input()throws Exception{return (ZeroGapArmorInputState)field("zeroGapArmorInputs").get(null);}
        MenuMouseInputFence menuFence()throws Exception{return (MenuMouseInputFence)field("menuMouseFence").get(null);}
        long epoch()throws Exception{return field("serverSingleEpoch").getLong(null);}
        ZeroGapArmorInputState.Gates gates(){var mc=StubMinecraft.INSTANCE;return new ZeroGapArmorInputState.Gates(new ZeroGapArmorInputState.DispatchContext(mc.level,mc.player,mc.connection,1,19,71,0,1,"same"),0,-100,mc.player.alive,true,true,true,true,true,mc.screen==null,true,true,true);}
        void start(long input)throws Exception{check(input().press(new HighPowerInput.Input(0,input,19,71),gates()).dispatch(),"captured start");receive(input,1,ZeroGapArmorState.ACTIVE);check(input().acceptedIdentity()!=null,"actual receive accepts matching input");}
        void receive(long input,long sequence,int phase)throws Exception{
            int mask=ZeroGapArmorGeometry.capture(new Vector(0,64,0),0,0).activeMask();var mc=StubMinecraft.INSTANCE;
            var packet=new StubVisual(19,mc.player.uuid,71,input,input,sequence,phase,new StubVector(0,64,0),0,0,mask,phase==ZeroGapArmorState.ENDED?0:mask,0,-1,new StubVector(0,64,0));
            effects.getMethod("receive",StubVisual.class).invoke(null,packet);
        }
        void tick()throws Exception{effects.getMethod("tick",StubPost.class).invoke(null,new StubPost());}
        void loss(long input,long epoch)throws Exception{client.getMethod("zeroGapArmorLocalVisualLost",long.class,long.class).invoke(null,input,epoch);}
        void privateCall(String name)throws Exception{var method=client.getDeclaredMethod(name);method.setAccessible(true);method.invoke(null);}
        void deathCancel()throws Exception{privateCall("cancelZeroGapArmorBeforeDiscard");}
        void discard()throws Exception{privateCall("discardBinding");}
    }
    public static final class StubMinecraft{
        static StubMinecraft INSTANCE=new StubMinecraft();public StubLevel level=new StubLevel();public StubPlayer player=new StubPlayer();
        public Object screen;Object connection=new Object();boolean paused;
        StubMinecraft(){level.player=player;}public static StubMinecraft getInstance(){return INSTANCE;}public Object getConnection(){return connection;}
        public void execute(Runnable work){work.run();}public boolean isPaused(){return paused;}public boolean isWindowActive(){return true;}public StubPlayer getCameraEntity(){return player;}
    }
    public static final class StubLevel{StubPlayer player;public StubPlayer getEntity(int id){return id==19?player:null;}}
    public static final class StubPlayer{
        final UUID uuid=UUID.randomUUID();boolean alive=true;public boolean noPhysics;final StubAbilities abilities=new StubAbilities();
        public int getId(){return 19;}public UUID getUUID(){return uuid;}public boolean isAlive(){return alive;}public boolean isRemoved(){return false;}
        public boolean isSpectator(){return false;}public boolean isPassenger(){return false;}public boolean isSleeping(){return false;}public StubAbilities getAbilities(){return abilities;}
    }
    public static final class StubAbilities{public boolean instabuild,invulnerable;}
    public static final class StubProgress{public boolean activated=true,overloadFine=true,interfering;public String category="meltdowner";public int level=5;public StubPresets presets=new StubPresets();public boolean learned(String ignored){return true;}}
    public static final class StubPresets{public boolean currentContains(String ignored){return true;}}
    public static final class StubCatalog{public static boolean enabled(String category,String skill){return true;}}
    public static final class StubSession{public static long inputSessionEpoch(){return 1;}public static boolean sameInputSessionEpoch(long epoch){return epoch==1;}}
    public static final class StubVector{public final double x,y,z;StubVector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}}
    public record StubVisual(int entity,UUID owner,long epoch,long input,long token,long sequence,int phase,StubVector feet,double yaw,int phaseTick,int selectedMask,int liveMask,int breakTicks,int plate,StubVector point){}
    public record StubRequest(String action,String value){}
    public static final class StubPackets{
        static boolean failOnce;
        public static void sendToServer(Object payload,Object... rest){
            try{
                var request=(StubRequest)payload;check(request.action().equals("hp_zero_gap_armor_abort"),"real transport action is the scoped abort");
                check(current.input().current()!=null&&current.epoch()==71,"transport executes before input/epoch cleanup");
                if(failOnce){failOnce=false;throw new IllegalStateException("injected dispatch failure");}sent.add(request);
            }catch(IllegalStateException expected){throw expected;
            }catch(Exception error){throw new AssertionError(error);}
        }
    }
    public static final class StubPost{}
}
