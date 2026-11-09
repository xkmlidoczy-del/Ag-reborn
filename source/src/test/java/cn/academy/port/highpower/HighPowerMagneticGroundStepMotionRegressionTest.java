package cn.academy.port.highpower;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

/** Actual compiled helper, actual native input/friction/clamp bytecode, stub world/player boundaries.
 * This is not a live-client acceptance test; rendering, real packets and third-party physics remain pending. */
public final class HighPowerMagneticGroundStepMotionRegressionTest {
    private static final String CLIENT = "cn/academy/port/client/MagneticGroundStepClientMotion";
    private static final String HARNESS = "cn/academy/port/highpower/generated/GroundStepMotionHarness";
    private static final String NATIVE = HARNESS + "Native";
    private static final String STUB = Type.getInternalName(StubPlayer.class);
    private static int checks;
    public static void main(String[] args) throws Exception {
        auditedVanillaSurfaceInheritance();
        Harness h = harness();
        h.reset();
        check(h.canStart(), "ordinary flat-ground sprint passes compiled pre-payment gate");
        for(String surface:List.of("grass","podzol","log")){
            h.reset();mc().level.surface=surface;
            check(h.canStart(),"audited ordinary vanilla surface is usable before payment: "+surface);
            h.receive(packet(1,1));h.move();
            check(mc().player.writes==1&&h.active(),"compiled assistance works on a fully supported audited surface: "+surface);
        }
        h.reset();mc().level.surface="custom_grass";
        check(!h.canStart(),"unknown subclass of an admitted vanilla grass class is not silently trusted");
        h.receive(packet(1,1));h.move();check(mc().player.writes==0,"unknown surface subclass receives no motion write");
        h.reset();
        h.receive(packet(1, 1));
        StubPlayer p = mc().player; p.velocity = new Vec3(.025, -.0784000015258789, .11);
        Vec3 before = p.velocity; h.move();
        check(bits(p.velocity.y) == bits(before.y), "addition copies CURRENT negative Y bit exactly");
        check(bits(p.velocity.x) == bits(before.x) && close(p.velocity.z - before.z, .08), "lateral external XZ is preserved; only locked forward addition");
        Vec3 applied = p.velocity; h.move();
        check(p.velocity.equals(applied) && p.writes == 1, "one grant is single-use within the local tick");
        h.receive(packet(1, 2)); h.move();
        check(p.writes == 1, "a newer grant in the same tick cannot batch additions");
        Vec3 nativeStep = h.nativeStep();
        check(nativeStep.horizontalDistance() <= .4 && bits(nativeStep.y) == bits(0), "actual native input/clamp step stays under cap");
        h.tick(); h.receive(packet(1, 3)); h.move();
        check(p.writes == 3 && h.active(), "successive server grants survive prior consumption and tick cleanup"); // threshold writes once too

        for (double y : new double[]{-0.0, -.002999, -.0784, -1}) {
            h.reset(); p = mc().player; p.velocity = new Vec3(.07, y, .1); h.receive(packet(1, 1)); h.move();
            check(bits(p.velocity.y) == bits(y), "raw vertical bits are preserved including negative zero and threshold-adjacent Y");
        }
        h.reset(); h.receive(packet(1, 1)); mc().player.velocity = new Vec3(.07, .09, .1); before = mc().player.velocity; h.move();
        check(mc().player.velocity.equals(before) && !h.active(), "new positive external Y is never overwritten or helped");
        h.reset(); h.receive(packet(1, 1)); mc().player.velocity = new Vec3(.07, -.08, -.000000000001); before = mc().player.velocity; h.move();
        check(mc().player.velocity.equals(before) && !h.active(), "any opposing raw dot refuses assistance even when native input would mask it");

        for (double component : new double[]{-.002999, .002999, -.003, .003}) {
            h.reset(); p = mc().player; p.yaw = -45; p.input.leftImpulse = .5F;
            Vec3 direction = new Vec3(1, 0, 1).normalize(); p.velocity = new Vec3(component, -.08, .09);
            h.receive(custom(1, 1, 1, 1, 8, 40, origin(), direction, false)); h.move();
            Vec3 expected = h.prediction(p.velocity, p.input, p.yaw, p.speed, .6F);
            nativeStep = h.nativeStep();
            check(expected.equals(nativeStep) && nativeStep.horizontalDistance() <= .4,
                    "post-add .003 component clamp matches actual native bytecode at signed boundary " + component);
        }
        Random random = new Random(132);
        for (int i = 0; i < 300; i++) {
            h.reset(); p = mc().player; p.yaw = random.nextFloat() * 360 - 180;
            p.input.leftImpulse = random.nextFloat() * 2 - 1; p.input.forwardImpulse = .1F + random.nextFloat() * .9F;
            p.speed = .01F + random.nextFloat() * .2F;
            Vec3 dir = new Vec3(-Mth.sin(p.yaw * ((float)Math.PI / 180F)), 0, Mth.cos(p.yaw * ((float)Math.PI / 180F))).normalize();
            p.velocity = dir.scale(random.nextDouble() * .2).add(0, -.08, 0);
            h.receive(custom(1, 1, 1, 1, 8, 40, origin(), dir, false)); h.move();
            if (p.writes > 0) {
                Vec3 predicted = h.prediction(p.velocity, p.input, p.yaw, p.speed, .6F);
                nativeStep = h.nativeStep();
                check(predicted.equals(nativeStep), "full diagonal normalization and float-yaw native prediction agree");
                check(nativeStep.horizontalDistance() <= .4, "actual ensuing native step obeys horizontal speed ceiling");
            }
        }
        h.reset(); p = mc().player; p.velocity = new Vec3(0, -.08, .195); p.input.leftImpulse = 1;
        h.receive(packet(1, 1)); h.move(); nativeStep = h.nativeStep();
        check(nativeStep.horizontalDistance() <= .4, "diagonal sprint native acceleration included in cap");
        for (float bad : new float[]{0, -1, Float.NaN, .401F}) {
            h.reset(); p = mc().player; p.speed = bad; h.receive(packet(1, 1)); before = p.velocity; h.move();
            check(p.velocity.equals(before), "invalid/excessive native movement speed fails closed");
        }
        for (float bad : new float[]{0, -1, Float.NaN, .98F}) {
            h.reset(); mc().level.friction = bad; h.receive(packet(1, 1)); h.move();
            check(mc().player.writes == 0, "nonordinary or invalid friction fails closed");
        }

        h.reset(); p = mc().player; p.position = new Vec3(.5, 64, 8.25); // 7.75m from fixed origin; a real server grant then has only .25m left
        h.receive(custom(1, 1, 1, 1, .25, 40, origin(), new Vec3(0,0,1), false)); h.move(); nativeStep = h.nativeStep();
        check(nativeStep.horizontalDistance() <= .25 && p.position.z <= 8.5 + 1e-12,
                "first delayed grant accounts fixed-origin distance before next native step");
        h.reset(); mc().player.velocity = new Vec3(0, -.08, 0); h.receive(custom(1, 1, 1, 1, .15, 40, origin(), new Vec3(0,0,1), false)); h.move(); nativeStep = h.nativeStep();
        check(nativeStep.horizontalDistance() <= .15, "server remaining distance directly limits full next step");
        h.reset(); h.receive(packet(1, 1));
        double distance = 0; int assists = 0;
        for (int i = 0; i < 40; i++) {
            if (!h.active()) break;
            int writes = mc().player.writes; h.move();
            if (mc().player.writes == writes) break;
            assists++; nativeStep = h.nativeStep(); distance += nativeStep.horizontalDistance();
            check(nativeStep.horizontalDistance() <= .4 && distance <= 8 + 1e-12, "native-loop assisted trajectory never exceeds 8m");
            h.tick(); h.receive(custom(1, 1, 1, i + 2, 8, 40, origin(), new Vec3(0,0,1), false));
        }
        check(assists > 2 && assists < 40 && distance <= 8 + 1e-12, "cumulative real native path exhausts assistance before 40 ticks");
        before = mc().player.velocity; h.abort();
        check(mc().player.velocity.equals(before), "retirement preserves all native coast rather than braking at eight metres");
        h.reset(); h.receive(packet(1, 1)); h.move(); h.tick();
        mc().player.position = origin().add(.1, 0, .1); h.move(); // consumed permission still accounts this path
        double spent = h.spent(); mc().player.position = origin();
        h.receive(packet(1, 2)); h.move();
        check(h.spent() >= spent + Math.sqrt(.02) - 1e-12, "no-grant tick plus backtracking count path, never only a multi-tick chord");
        h.reset(); h.receive(packet(1, 1)); mc().player.position = origin().add(.801, 0, 0); h.move();
        check(!h.active() && mc().player.writes == 0, "teleport/large observed displacement retires without motion writes");
        h.reset(); h.receive(packet(1, 1)); mc().player.position = origin().add(0, .026, 0); h.move();
        check(!h.active(), "observed vertical displacement invalidates sloped trajectory");

        for (String geometry : List.of("wall", "gap", "center_hole", "slope", "fluid", "special", "unloaded", "border", "height", "suffocation")) {
            h.reset(); mc().level.geometry(geometry); h.receive(packet(1, 1)); h.move();
            check(mc().player.writes == 0 && !h.active(), "full swept-body/support/native-physics rejection: " + geometry);
            check(!h.canStart(), "pre-payment gate rejects unsupported geometry: " + geometry);
        }
        for (String state : List.of("jump", "sneak", "air", "water", "lava", "fly", "mayfly", "climb", "passenger", "sleep", "noclip", "slow", "item", "effect", "camera", "hungry", "spin", "vertical_input", "not_sprint", "backwards_input")) {
            h.reset(); h.receive(packet(1, 1)); mc().player.state(state); before = mc().player.velocity; h.move();
            check(mc().player.velocity.equals(before) && !h.active(), "unsupported native state never alters velocity: " + state);
        }
        h.reset(); mc().options.auto = true;
        check(h.blocker().endsWith("autojump"), "pre-payment gate explains autojump restriction"); h.move();
        mc().options.auto = false; check(h.blocker().endsWith("settling"), "recently disabled autojump cannot bypass queued native jump");
        h.move(); check(!h.canStart(), "one off-input pass insufficient to guarantee private autoJumpTime drain");
        h.move(); check(!h.canStart(), "duplicate input event cannot manufacture an elapsed native input tick");
        h.tick(); h.move(); check(h.canStart(), "two distinct off-input passes permit a later cast");
        h.receive(packet(1, 1)); h.move(); check(mc().player.writes == 1, "cast after drained autojump can apply once");

        h.reset(); h.receive(packet(1, 3)); h.receive(packet(1, 2)); h.receive(packet(1, 3));
        check(h.sequence() == 3, "duplicate and out-of-order grant cannot overwrite newest permission");
        h.move(); check(mc().player.writes == 1, "packet burst only applies newest permission once");
        h.reset(); h.receive(packet(1, 1)); h.tick(); check(h.active(), "unused grant survives one local tick");
        h.tick();h.move();check(h.active()&&mc().player.writes==0,"after two local ticks the expired grant gives zero additions while the bounded cast waits");
        h.receive(packet(1,1));h.move();check(mc().player.writes==0,"reissued current-clock duplicate sequence cannot refresh an expired permission");
        StubAssist staleHigher=packet(1,99);mc().level.time+=3;h.receive(staleHigher);h.move();
        check(mc().player.writes==0&&h.sequence()==1,"stale higher sequence cannot authorize movement or advance accepted sequence");
        h.receive(packet(1,99));h.move();check(h.active()&&mc().player.writes==1,"only a new independently fresh higher sequence supplies its own one addition after a packet gap");
        h.move();check(mc().player.writes==1,"the recovered permission is still single-use in that local tick");
        h.reset();h.receive(packet(1,1));
        for(int gap=0;gap<4;gap++){h.tick();mc().player.position=mc().player.position.add(0,0,.2);h.move();}
        check(h.active()&&Math.abs(h.spent()-.8)<1e-12&&mc().player.writes==1,"ordinary movement during a stale gap keeps charging the full cast path without reusing its consumed grant");
        h.receive(custom(1,1,1,2,7.2,36,origin(),new Vec3(0,0,1),false));h.move();
        check(h.active()&&h.spent()>=.8-1e-12&&mc().player.writes==2,"fresh recovery keeps its already consumed distance and original deadline");
        h.reset();h.receive(packet(1,1));h.tick();h.tick();h.abort(1,1);h.receive(packet(1,99));h.move();
        check(!h.active()&&mc().player.writes==0,"explicit cancellation while waiting permanently fences all later same-cast grants");
        h.reset();h.receive(packet(1,1));h.tick();h.tick();h.receive(custom(1,1,1,2,0,0,origin(),new Vec3(0,0,1),true));h.receive(packet(1,99));h.move();
        check(!h.active()&&mc().player.writes==0,"server terminal while waiting permanently fences later positive replays");
        h.reset(); h.abort(1,1); h.receive(packet(1,1)); check(!h.active(), "cancel-before-first fences delayed grant");
        h.receive(packet(2,1)); h.abort(1,1); check(h.active(), "old cancellation leaves replacement input intact");
        h.receive(custom(1,1,1,99,0,0,origin(),new Vec3(0,0,1),true)); check(h.active(), "old terminal cannot end newer token");
        h.receive(custom(3,2,1,1,8,40,origin(),new Vec3(0,0,1),false)); check(h.input()==2, "new input cannot reuse accepted server token");
        h.receive(custom(2,9,1,2,8,40,origin(),new Vec3(0,0,1),false)); check(h.token()==2, "same input cannot change token");
        h.reset(); h.receive(packet(1,1)); h.clear(); h.receive(packet(1,2)); check(!h.active(), "same-connection clear retains replay fences");
        h.reset(); h.receive(packet(1,1)); mc().paused=true; h.tick(); mc().paused=false; h.receive(packet(1,2));
        check(!h.active() && mc().player.writes==0, "pause retires unspent grant with no resume impulse");
        for (String transition : List.of("world","player","connection","disconnect","clear","pause","gui","epoch")) {
            h.reset(); mc().defer = true; h.receive(packet(1,1)); transition(h,transition); mc().flush();
            check(!h.active(), "queued grant cannot cross lifecycle boundary: " + transition);
        }
        h.reset(); StubAssist expired=packet(1,1); mc().level.time+=3; h.receive(expired);
        check(!h.active(), "arbitrarily delayed first permission is rejected by server world-time expiry");
        h.reset(); h.receive(packet(1,1)); mc().level.time+=3; h.move(); check(mc().player.writes==0, "freshness rechecked at movement application");
        h.reset(); h.receive(packet(1,1));
        for(int i=0;i<39;i++){h.tick();h.receive(packet(1,i+2));}
        check(h.active(), "refresh cannot reset but may maintain original bounded cast lifetime");
        h.tick();h.receive(packet(1,99));check(!h.active(), "forty local ticks end assistance despite continuous grants");
        h.reset(); h.receive(packet(1,1)); mc().level.time--; h.receive(custom(1,1,1,2,8,40,origin().add(.01,0,0),new Vec3(0,0,1),false));
        check(!h.active(), "same token cannot replace fixed cast origin");
        h.reset(); h.receive(packet(1,1)); h.receive(custom(1,1,1,2,7,39,origin(),new Vec3(0,0,1),false));
        h.receive(custom(1,1,1,3,8,40,origin(),new Vec3(0,0,1),false));check(!h.active(), "server budget/lifetime increases fail closed");
        for (int i = 0; i < invalidPackets().size(); i++) { h.reset(); h.receive(invalidPackets().get(i)); check(!h.active(), "malformed assist rejected with otherwise matching current owner"); }
        h.reset();h.receive(null);check(!h.active(),"null assist harmless");
        verifyNativeOrdering();
        System.out.println("PASS " + checks + " actual compiled NEW-132 helper + native clamp/input/friction, lifecycle and swept geometry checks; live client/gameplay acceptance pending");
    }
    private static StubMinecraft mc(){return StubMinecraft.INSTANCE;}
    private static Vec3 origin(){return new Vec3(.5,64,.5);}
    private static long bits(double d){return Double.doubleToRawLongBits(d);}
    private static boolean close(double a,double b){return Math.abs(a-b)<1e-12;}
    private static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
    private static StubAssist packet(long input,long seq){return custom(input,input,1,seq,8,40,origin(),new Vec3(0,0,1),false);}
    private static StubAssist custom(long input,long token,long epoch,long seq,double remaining,int ticks,Vec3 origin,Vec3 direction,boolean end){
        return new StubAssist(mc().player.id,mc().player.uuid,input,token,epoch,seq,origin,direction,remaining,ticks,mc().level.time,mc().level.time+2,end);
    }
    private static List<StubAssist> invalidPackets(){
        UUID owner=mc().player.uuid; Vec3 o=origin(),d=new Vec3(0,0,1);long t=mc().level.time;
        return List.of(new StubAssist(-1,owner,1,1,1,1,o,d,8,40,t,t+2,false),new StubAssist(1,null,1,1,1,1,o,d,8,40,t,t+2,false),
                new StubAssist(1,owner,0,1,1,1,o,d,8,40,t,t+2,false),new StubAssist(1,owner,1,0,1,1,o,d,8,40,t,t+2,false),
                new StubAssist(1,owner,1,1,0,1,o,d,8,40,t,t+2,false),new StubAssist(1,owner,1,1,1,0,o,d,8,40,t,t+2,false),
                new StubAssist(1,owner,1,1,1,1,o,new Vec3(0,.01,1),8,40,t,t+2,false),new StubAssist(1,owner,1,1,1,1,o,Vec3.ZERO,8,40,t,t+2,false),
                new StubAssist(1,owner,1,1,1,1,o,d,16.01,40,t,t+2,false),new StubAssist(1,owner,1,1,1,1,o,d,Double.NaN,40,t,t+2,false),
                new StubAssist(1,owner,1,1,1,1,o,d,8,41,t,t+2,false),new StubAssist(1,owner,1,1,1,1,o,d,8,40,t,t+3,false),
                new StubAssist(1,owner,1,1,1,1,o,d,8,40,t,t+2,true),new StubAssist(2,owner,1,1,1,1,o,d,8,40,t,t+2,false),
                new StubAssist(1,UUID.randomUUID(),1,1,1,1,o,d,8,40,t,t+2,false),
                new StubAssist(1,owner,1,1,1,1,new Vec3(Double.NaN,64,0),d,8,40,t,t+2,false),
                new StubAssist(1,owner,1,1,1,1,null,d,8,40,t,t+2,false),
                new StubAssist(1,owner,1,1,1,1,o,null,8,40,t,t+2,false),
                new StubAssist(1,owner,1,1,1,1,o,d,-.01,40,t,t+2,false),
                new StubAssist(1,owner,1,1,1,1,o,d,8,-1,t,t+2,false),
                new StubAssist(1,owner,1,1,1,1,o,d,8,40,t+2,t+4,false));
    }
    private static void transition(Harness h,String kind)throws Exception{
        switch(kind){case"world"->mc().level=new StubLevel();case"player"->mc().player=new StubPlayer();case"connection"->mc().connection=new Object();
            case"disconnect"->{mc().player=null;mc().level=null;mc().connection=null;}case"clear"->h.clear();case"pause"->mc().paused=true;
            case"gui"->mc().screen=new Object();case"epoch"->StubAcademy.epoch=2;}
    }
    private static Harness harness() throws Exception {
        Map<String,String> map=new HashMap<>();
        for(String suffix:List.of("","$Stamp","$Session"))map.put(CLIENT+suffix,HARNESS+suffix);
        mapping(map,"net/minecraft/client/Minecraft",StubMinecraft.class);mapping(map,"net/minecraft/client/multiplayer/ClientLevel",StubLevel.class);
        for(String name:List.of("net/minecraft/client/player/LocalPlayer","net/minecraft/world/entity/Entity","net/minecraft/world/entity/player/Player","net/minecraft/world/entity/LivingEntity"))mapping(map,name,StubPlayer.class);
        mapping(map,"net/minecraft/client/Options",StubOptions.class);mapping(map,"net/minecraft/client/OptionInstance",StubOption.class);
        map.put("net/minecraft/client/multiplayer/ClientPacketListener","java/lang/Object");map.put("net/minecraft/client/gui/screens/Screen","java/lang/Object");
        mapping(map,"net/minecraft/client/player/Input",StubInput.class);mapping(map,"net/minecraft/world/entity/player/Abilities",StubAbilities.class);
        mapping(map,"net/minecraft/world/food/FoodData",StubFood.class);mapping(map,"net/neoforged/neoforge/fluids/FluidType",StubFluidType.class);
        mapping(map,"net/minecraft/world/level/block/Block",StubBlock.class);mapping(map,"net/minecraft/world/level/block/state/BlockState",StubBlockState.class);
        mapping(map,"net/minecraft/world/level/block/GrassBlock",StubGrassBlock.class);mapping(map,"net/minecraft/world/level/block/SnowyDirtBlock",StubSnowyDirtBlock.class);
        mapping(map,"net/minecraft/world/level/block/RotatedPillarBlock",StubPillarBlock.class);
        mapping(map,"net/minecraft/world/level/LevelReader",StubLevelReader.class);mapping(map,"net/minecraft/world/level/material/FluidState",StubFluidState.class);
        mapping(map,"net/minecraft/world/level/border/WorldBorder",StubBorder.class);mapping(map,"cn/academy/port/highpower/MagneticGroundStep$Assist",StubAssist.class);
        mapping(map,"cn/academy/port/client/AcademyClient",StubAcademy.class);mapping(map,"cn/academy/port/highpower/MagneticGroundStep",StubDiagnostic.class);mapping(map,"cn/academy/port/core/AbilityProgress",StubProgress.class);
        mapping(map,"cn/academy/port/core/SkillPresets",StubPresets.class);mapping(map,"net/neoforged/neoforge/client/event/ClientTickEvent$Post",StubPost.class);
        mapping(map,"net/neoforged/neoforge/client/event/MovementInputUpdateEvent",StubEvent.class);
        Map<String,byte[]> classes=new HashMap<>();
        for(String suffix:List.of("","$Stamp","$Session")){
            ClassNode node=read(CLIENT+suffix);node.visibleAnnotations=null;node.invisibleAnnotations=null;
            if(node.nestMembers!=null)node.nestMembers.removeIf(n->n.endsWith("$Setup"));node.innerClasses.removeIf(n->n.name.endsWith("$Setup"));
            classes.put((HARNESS+suffix).replace('/','.'),remapped(node,map));
        }
        classes.put(NATIVE.replace('/','.'),nativeClass());
        ClassLoader loader=new ClassLoader(HighPowerMagneticGroundStepMotionRegressionTest.class.getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{byte[]b=classes.get(name);if(b==null)throw new ClassNotFoundException(name);return defineClass(name,b,0,b.length);}
        };
        return new Harness(Class.forName(HARNESS.replace('/','.'),true,loader),Class.forName(NATIVE.replace('/','.'),true,loader));
    }
    private static void mapping(Map<String,String> map,String name,Class<?> replacement){map.put(name,Type.getInternalName(replacement));}
    private static ClassNode read(String name)throws Exception{try(var stream=HighPowerMagneticGroundStepMotionRegressionTest.class.getClassLoader().getResourceAsStream(name+".class")){
        if(stream==null)throw new IllegalStateException("Missing compiled production/native class: "+name);ClassNode n=new ClassNode();new ClassReader(stream).accept(n,0);return n;}}
    private static byte[] remapped(ClassNode n,Map<String,String>map){ClassWriter w=new ClassWriter(0);n.accept(new ClassRemapper(w,new SimpleRemapper(map)));return w.toByteArray();}
    private static MethodNode method(ClassNode n,String name){return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static void auditedVanillaSurfaceInheritance()throws Exception{
        var parents=Map.of("GrassBlock","SpreadingSnowyDirtBlock","SpreadingSnowyDirtBlock","SnowyDirtBlock","SnowyDirtBlock","Block","RotatedPillarBlock","Block");
        var motion=Set.of("entityInside","stepOn","fallOn","updateEntityAfterFallOn","getFriction","getSpeedFactor","getJumpFactor","getCollisionShape");
        for(var entry:parents.entrySet()){
            var node=read("net/minecraft/world/level/block/"+entry.getKey());
            check(node.superName.equals("net/minecraft/world/level/block/"+entry.getValue()),"audited vanilla surface retains its exact reviewed parent: "+entry.getKey());
            check(node.methods.stream().noneMatch(m->motion.contains(m.name)),"audited surface inheritance introduces no movement callback override: "+entry.getKey());
        }
    }
    /** Run the actual native input vector and ground friction methods, plus the exact aiStep threshold slice. */
    private static byte[] nativeClass()throws Exception{
        ClassNode n=new ClassNode();n.version=Opcodes.V21;n.access=Opcodes.ACC_PUBLIC;n.name=NATIVE;n.superName="java/lang/Object";
        MethodNode input=method(read("net/minecraft/world/entity/Entity"),"getInputVector");input.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC;n.methods.add(input);
        ClassNode living=read("net/minecraft/world/entity/LivingEntity");MethodNode friction=method(living,"getFrictionInfluencedSpeed");
        friction.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC;friction.desc="(L"+STUB+";F)F";friction.localVariables=null;
        for(AbstractInsnNode x:friction.instructions)if(x instanceof FrameNode)friction.instructions.remove(x);
        n.methods.add(friction);
        MethodNode ai=method(living,"aiStep"),clamp=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"clamp","(L"+STUB+";)V",null,null);
        AbstractInsnNode start=null,end=null;
        for(AbstractInsnNode x:ai.instructions)if(x instanceof MethodInsnNode call&&call.name.equals("getDeltaMovement")){
            AbstractInsnNode next=nextCode(x);if(next instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ASTORE){start=x.getPrevious();break;}}
        if(start==null)throw new AssertionError("native threshold start not found");
        for(AbstractInsnNode x=start;x!=null;x=x.getNext())if(x instanceof MethodInsnNode call&&call.name.equals("setDeltaMovement")&&call.desc.equals("(DDD)V")){end=x;break;}
        if(end==null)throw new AssertionError("native threshold end not found");
        Map<LabelNode,LabelNode>labels=new HashMap<>();for(AbstractInsnNode x:ai.instructions)if(x instanceof LabelNode l)labels.put(l,new LabelNode());
        for(AbstractInsnNode x=start;;x=x.getNext()){if(!(x instanceof FrameNode)&&!(x instanceof LineNumberNode))clamp.instructions.add(x.clone(labels));if(x==end)break;}
        clamp.instructions.add(new InsnNode(Opcodes.RETURN));clamp.maxStack=8;clamp.maxLocals=ai.maxLocals;n.methods.add(clamp);
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        n.accept(new ClassRemapper(w,new SimpleRemapper(Map.of("net/minecraft/world/entity/LivingEntity",STUB))));return w.toByteArray();
    }
    private static AbstractInsnNode nextCode(AbstractInsnNode n){do{n=n.getNext();}while(n!=null&&n.getOpcode()<0);return n;}
    private static void verifyNativeOrdering()throws Exception{
        MethodNode local=method(read("net/minecraft/client/player/LocalPlayer"),"aiStep");int index=0,event=-1,jump=-1,push=-1,parent=-1;
        for(AbstractInsnNode n:local.instructions){if(n instanceof MethodInsnNode call){if(call.name.equals("onMovementInputUpdate"))event=index;
            if(call.name.equals("moveTowardsClosestSpace"))push=index;if(call.name.equals("aiStep"))parent=index;}
            if(event>=0&&n instanceof FieldInsnNode f&&f.name.equals("autoJumpTime")&&jump<0)jump=index;index++;}
        check(event>=0&&event<jump&&jump<push&&push<parent,"compiled native hook precedes autojump/push/super travel, requiring strict guards");
        MethodNode player=method(read("net/minecraft/world/entity/player/Player"),"aiStep");index=0;int superAt=-1,speedAt=-1;
        for(AbstractInsnNode n:player.instructions){if(n instanceof MethodInsnNode c){if(c.name.equals("aiStep"))superAt=index;if(c.name.equals("setSpeed"))speedAt=index;}index++;}
        check(superAt>=0&&speedAt>superAt,"native current getSpeed used before Player updates scalar after travel");
        MethodNode helper=method(read(CLIENT),"movement");long writes=0;
        for(AbstractInsnNode n:helper.instructions)if(n instanceof MethodInsnNode c){if(c.name.equals("setDeltaMovement"))writes++;
            check(!Set.of("setPos","moveTo","teleportTo","resetFallDistance","setOnGround","absoluteSetEntityMotion").contains(c.name),"helper contains no position/Y-reset/absolute motion write");}
        check(writes==1,"only one additive motion setter exists on guarded success path");
    }
    private record Harness(Class<?> type,Class<?> nativeType){
        void reset()throws Exception{StubMinecraft.INSTANCE=new StubMinecraft();StubAcademy.epoch=1;StubAcademy.state=new StubProgress();clear();tick();move();tick();move();tick();mc().player.writes=0;}
        void receive(StubAssist p)throws Exception{type.getMethod("receive",StubAssist.class).invoke(null,p);}
        void tick()throws Exception{if(mc().level!=null&&!mc().paused)mc().level.time++;type.getMethod("tick",StubPost.class).invoke(null,new StubPost());}
        void move()throws Exception{type.getMethod("movement",StubEvent.class).invoke(null,new StubEvent(mc().player));}
        void clear()throws Exception{type.getMethod("clear").invoke(null);}
        void abort()throws Exception{type.getMethod("abortLocal").invoke(null);}
        void abort(long input,long epoch)throws Exception{type.getMethod("abortLocal",long.class,long.class).invoke(null,input,epoch);}
        boolean canStart()throws Exception{return(boolean)type.getMethod("canStartLocal").invoke(null);}
        String blocker()throws Exception{return(String)type.getMethod("startBlocker").invoke(null);}
        Object field(Object owner,String name)throws Exception{Field f=(owner==null?type:owner.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
        boolean active()throws Exception{return field(null,"session")!=null;}
        double spent()throws Exception{return(double)field(field(null,"session"),"spent");}
        long sequence()throws Exception{return(long)field(null,"sequence");}
        long input()throws Exception{return((StubAssist)field(field(null,"session"),"permission")).input();}
        long token()throws Exception{return((StubAssist)field(field(null,"session"),"permission")).token();}
        Vec3 prediction(Vec3 changed,StubInput input,float yaw,float speed,float friction)throws Exception{
            var method=type.getDeclaredMethod("nativeInput",StubInput.class,float.class,float.class,float.class);method.setAccessible(true);
            Vec3 nativeInput=(Vec3)method.invoke(null,input,yaw,speed,friction);
            method=type.getDeclaredMethod("predicted",Vec3.class,Vec3.class,Vec3.class,double.class);method.setAccessible(true);
            return(Vec3)method.invoke(null,changed,nativeInput,new Vec3(0,0,1),0D);
        }
        Vec3 nativeStep()throws Exception{
            StubPlayer p=mc().player;nativeType.getMethod("clamp",StubPlayer.class).invoke(null,p);
            float acceleration=(float)nativeType.getMethod("getFrictionInfluencedSpeed",StubPlayer.class,float.class).invoke(null,p,mc().level.friction);
            Vec3 input=new Vec3((double)(p.input.leftImpulse*.98F),0,(double)(p.input.forwardImpulse*.98F));
            Vec3 nativeInput=(Vec3)nativeType.getMethod("getInputVector",Vec3.class,float.class,float.class).invoke(null,input,acceleration,p.yaw);
            Vec3 step=new Vec3(p.velocity.x+nativeInput.x,0,p.velocity.z+nativeInput.z);
            p.position=p.position.add(step);float friction=mc().level.friction*.91F;
            p.velocity=new Vec3(step.x*(double)friction,-.0784000015258789,step.z*(double)friction);return step;
        }
    }
    public record StubAssist(int entity,UUID owner,long input,long token,long epoch,long sequence,Vec3 origin,Vec3 direction,double remaining,int remainingTicks,long issuedAt,long expiresAt,boolean terminal){}
    public static final class StubDiagnostic{public static void diagnostic(long token,String stage,String detail){}}
    public static final class StubPost{}
    public static final class StubEvent{final StubPlayer player;StubEvent(StubPlayer p){player=p;}public StubPlayer getEntity(){return player;}public StubInput getInput(){return player.input;}}
    public static final class StubInput{public float forwardImpulse=1,leftImpulse;public boolean jumping,shiftKeyDown;}
    public static final class StubOption{public Object get(){return mc().options.auto;}}
    public static final class StubOptions{boolean auto;public StubOption autoJump(){return new StubOption();}}
    public static final class StubAcademy{static long epoch=1;public static StubProgress state=new StubProgress();public static boolean singleOwnerEpochMatches(long e){return e==epoch&&e>0;}}
    public static final class StubProgress{public boolean activated=true,overloadFine=true,interfering;public String category="electromaster";public int level=3;public StubPresets presets=new StubPresets();public boolean consumptionInProgress(){return false;}public boolean learned(String id){return true;}}
    public static final class StubPresets{public boolean currentContains(String id){return true;}}
    public static final class StubMinecraft{
        static StubMinecraft INSTANCE=new StubMinecraft();public StubLevel level=new StubLevel();public StubPlayer player=new StubPlayer();public StubOptions options=new StubOptions();
        public Object screen;Object connection=new Object();boolean paused,defer,focus=true;StubPlayer camera;List<Runnable>queue=new ArrayList<>();
        StubMinecraft(){level.player=player;camera=player;}public static StubMinecraft getInstance(){return INSTANCE;}public Object getConnection(){return connection;}
        public StubPlayer getCameraEntity(){return camera;}public boolean isWindowActive(){return focus;}public boolean isPaused(){return paused;}
        public void execute(Runnable task){if(defer)queue.add(task);else task.run();}void flush(){for(Runnable r:List.copyOf(queue))r.run();queue.clear();}
    }
    public static final class StubAbilities{public boolean flying,mayfly;}
    public static final class StubFood{int food=20;public int getFoodLevel(){return food;}}
    public static final class StubFluidType{boolean air=true;public boolean isAir(){return air;}}
    public static final class StubPlayer{
        final int id=1;final UUID uuid=UUID.randomUUID();Vec3 position=origin(),velocity=new Vec3(0,-.08,.1);float yaw,speed=.13F;int writes;
        public StubInput input=new StubInput();public boolean noPhysics,horizontalCollision,isInPowderSnow;public float yya;
        boolean ground=true,sprint=true,alive=true,removed,water,lava,climb,passenger,sleep,slow,item,effect,spin;
        StubAbilities abilities=new StubAbilities();StubFood food=new StubFood();StubFluidType fluid=new StubFluidType();
        public int getId(){return id;}public UUID getUUID(){return uuid;}public boolean isAlive(){return alive;}public boolean isRemoved(){return removed;}
        public Vec3 position(){return position;}public Vec3 getDeltaMovement(){return velocity;}public void setDeltaMovement(Vec3 v){velocity=v;writes++;}public void setDeltaMovement(double x,double y,double z){setDeltaMovement(new Vec3(x,y,z));}
        public double getX(){return position.x;}public double getY(){return position.y;}public double getZ(){return position.z;}public float getYRot(){return yaw;}public float getXRot(){return 0;}
        public float getSpeed(){return speed;}public float getFlyingSpeed(){return .02F;}public float getBbWidth(){return .6F;}public Vec3 getLookAngle(){float a=yaw*((float)Math.PI/180F);return new Vec3(-Mth.sin(a),0,Mth.cos(a));}
        public BlockPos getBlockPosBelowThatAffectsMyMovement(){return BlockPos.containing(getX(),getY()-.500001,getZ());}
        public AABB getBoundingBox(){return new AABB(getX()-.3,getY(),getZ()-.3,getX()+.3,getY()+1.8,getZ()+.3);}
        public boolean onGround(){return ground;}public boolean isSprinting(){return sprint;}public boolean isEffectiveAi(){return true;}public Pose getPose(){return Pose.STANDING;}
        public boolean isMovingSlowly(){return slow;}public boolean isPassenger(){return passenger;}public boolean isVehicle(){return false;}public boolean isSleeping(){return sleep;}
        public boolean isFallFlying(){return false;}public boolean isAutoSpinAttack(){return spin;}public StubAbilities getAbilities(){return abilities;}public boolean isInWaterOrBubble(){return water;}
        public boolean isInLava(){return lava;}public StubFluidType getMaxHeightFluidType(){return fluid;}public boolean onClimbable(){return climb;}public boolean isShiftKeyDown(){return input.shiftKeyDown;}
        public boolean isUsingItem(){return item;}public boolean isSwimming(){return false;}public Collection<Object>getActiveEffects(){return effect?List.of(new Object()):List.of();}public StubFood getFoodData(){return food;}
        void state(String s){switch(s){case"jump"->input.jumping=true;case"sneak"->input.shiftKeyDown=true;case"air"->ground=false;case"water"->water=true;case"lava"->lava=true;
            case"fly"->abilities.flying=true;case"mayfly"->abilities.mayfly=true;case"climb"->climb=true;case"passenger"->passenger=true;case"sleep"->sleep=true;case"noclip"->noPhysics=true;
            case"slow"->slow=true;case"item"->item=true;case"effect"->effect=true;case"camera"->mc().camera=new StubPlayer();case"hungry"->food.food=6;case"spin"->spin=true;
            case"vertical_input"->yya=.2F;case"not_sprint"->sprint=false;case"backwards_input"->input.forwardImpulse=-1;}}
    }
    public interface StubLevelReader{}
    public static class StubBlock{public float getSpeedFactor(){return 1;}public float getJumpFactor(){return 1;}}
    public static class StubGrassBlock extends StubBlock{}
    public static final class StubSnowyDirtBlock extends StubBlock{}
    public static final class StubPillarBlock extends StubBlock{}
    public static final class UnknownGrassBlock extends StubGrassBlock{}
    public static final class SpecialBlock extends StubBlock{}
    public static final class StubBlockState{final boolean air,special;StubBlockState(boolean air,boolean special){this.air=air;this.special=special;}public boolean isAir(){return air;}
        public StubBlock getBlock(){if(special)return new SpecialBlock();return switch(mc().level.surface){case"grass"->new StubGrassBlock();case"podzol"->new StubSnowyDirtBlock();case"log"->new StubPillarBlock();case"custom_grass"->new UnknownGrassBlock();default->new StubBlock();};}public float getFriction(StubLevelReader world,BlockPos pos,StubPlayer p){return((StubLevel)world).friction;}}
    public static final class StubFluidState{final boolean empty;StubFluidState(boolean e){empty=e;}public boolean isEmpty(){return empty;}}
    public static final class StubBorder{boolean inside=true;public boolean isWithinBounds(AABB a){return inside;}}
    public static final class StubLevel implements StubLevelReader{
        StubPlayer player;long time=100;float friction=.6F;String surface="block";boolean loaded=true,liquid,special,suffocation;int min=-64,max=320;StubBorder border=new StubBorder();
        List<AABB>floor=new ArrayList<>(List.of(new AABB(-128,63,-128,128,64,128))),obstacles=new ArrayList<>();
        public StubPlayer getEntity(int id){return player!=null&&player.id==id?player:null;}public long getGameTime(){return time;}public int getMinBuildHeight(){return min;}public int getMaxBuildHeight(){return max;}
        public boolean hasChunk(int x,int z){return loaded;}public StubBorder getWorldBorder(){return border;}public StubBlockState getBlockState(BlockPos p){return new StubBlockState(p.getY()>=64,special);}
        public StubFluidState getFluidState(BlockPos p){return new StubFluidState(!liquid);}public boolean noCollision(StubPlayer p,AABB box){return obstacles.stream().noneMatch(box::intersects)&&floor.stream().noneMatch(box::intersects);}
        public boolean collidesWithSuffocatingBlock(StubPlayer p,AABB column){return suffocation;}
        public Iterable<VoxelShape>getBlockCollisions(StubPlayer p,AABB box){return floor.stream().filter(box::intersects).map(Shapes::create).toList();}
        void geometry(String s){switch(s){case"wall"->obstacles.add(new AABB(.2,64,.84,.8,66,1));case"gap"->floor=List.of(new AABB(-128,63,-128,128,64,.85));
            case"center_hole"->floor=List.of(new AABB(-128,63,-128,.49,64,128),new AABB(.51,63,-128,128,64,128),new AABB(.49,63,-128,.51,64,.6),new AABB(.49,63,.62,.51,64,128));
            case"slope"->floor=List.of(new AABB(-128,63,-128,128,64,.85),new AABB(-128,63,.85,128,64.5,128));case"fluid"->liquid=true;case"special"->special=true;
            case"unloaded"->loaded=false;case"border"->border.inside=false;case"height"->max=65;case"suffocation"->suffocation=true;}}
    }
}
