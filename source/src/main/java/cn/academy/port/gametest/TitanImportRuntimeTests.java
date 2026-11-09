package cn.academy.port.gametest;
import cn.academy.port.titan.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in dedicated game-test server tests. This class is excluded from normal builds. */
@GameTestHolder("academy_titan_import")
@net.neoforged.fml.common.EventBusSubscriber(modid="academy")
@PrefixGameTestTemplate(false)
public final class TitanImportRuntimeTests {
    @net.neoforged.bus.api.SubscribeEvent public static void template(net.neoforged.neoforge.event.level.LevelEvent.Load event){
        if(!net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel w))return;
        try(var in=TitanImportRuntimeTests.class.getResourceAsStream("/cn/academy/port/gametest/titan_arena.nbt")){
            var tag=net.minecraft.nbt.NbtIo.readCompressed(in,net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            w.getStructureManager().getOrCreate(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy_titan_import","arena")).load(w.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),tag);
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }

    private TitanImportRuntimeTests() {}
    @GameTestGenerator public static Collection<TestFunction> allProfiles() {
        ArrayList<TestFunction> result=new ArrayList<>();
        for(String id:TitanProfiles.all().keySet()) {
            result.add(new TestFunction("profiles","academy_titan_import.profile_"+id,"academy_titan_import:arena",60,0,true,
                helper->profileAndAbilities(helper,id)));
            result.add(new TestFunction("v6_boundaries","academy_titan_import.v6_boundary_"+id,"academy_titan_import:arena",60,0,true,
                helper->enlargedBoundary(helper,id)));
            result.add(new TestFunction("v6_melee_ai","academy_titan_import.v6_melee_ai_"+id,"academy_titan_import:arena",85,0,true,
                helper->aiMelee(helper,id)));
            result.add(new TestFunction("v7_motion","academy_titan_import.v7_motion_"+id,"academy_titan_import:arena",110,0,true,
                helper->motionAttackCycle(helper,id)));
        }
        result.add(new TestFunction("v7_reach","academy_titan_import.v7_tongue_reach","academy_titan_import:arena",60,0,true,
            helper->extendedStrike(helper,"myco_frog",false,26)));
        result.add(new TestFunction("v7_reach","academy_titan_import.v7_tendril_a_reach","academy_titan_import:arena",60,0,true,
            helper->extendedStrike(helper,"ironback",false,22)));
        result.add(new TestFunction("v7_reach","academy_titan_import.v7_tendril_b_reach","academy_titan_import:arena",60,0,true,
            helper->extendedStrike(helper,"ironback",true,22)));
        for(String spec:new String[]{"lava_rex:b:17","frost_bastion:b:13","grove_titan:b:8",
            "tidal_sail:b:20","dune_scorpion:a:22","ironback:a:21","ironback:b:21"}) {
            String[] parts=spec.split(":");String id=parts[0];boolean variantB=parts[1].equals("b");int distance=Integer.parseInt(parts[2]);
            result.add(new TestFunction("v8_limb","academy_titan_import.v8_limb_"+id+"_"+parts[1],"academy_titan_import:arena",80,0,true,
                helper->limbContactAndMiss(helper,id,variantB,distance)));
        }
        return result;
    }
    private static void profileAndAbilities(GameTestHelper helper,String id) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get(id).get(),20,3,40);
        titan.setNoAi(true);titan.setNoGravity(true);
        IronGolem victim=helper.spawn(EntityType.IRON_GOLEM,40,3,40);
        victim.setNoAi(true);victim.setNoGravity(true);
        var profile=TitanProfiles.get(id);
        helper.assertTrue(titan.getMaxHealth()==profile.health(),id+" has wrong health");
        helper.assertTrue(titan.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)==profile.armor(),id+" has wrong armor");
        for(TitanProfiles.Ability ability:new TitanProfiles.Ability[]{profile.first(),profile.second()}) {
            victim.setHealth(victim.getMaxHealth());victim.invulnerableTime=0;
            Vec3 origin=ability.shape()==TitanProfiles.Shape.BEAM?TitanCombat.beamStart(titan):titan.position();
            Vec3 target=ability.shape()==TitanProfiles.Shape.BEAM?victim.position().add(0,victim.getBbHeight()*.5,0):victim.position();
            int hits=TitanCombat.resolve(helper.getLevel(),titan,ability,origin,target);
            helper.assertTrue(hits==1 && victim.getHealth()<victim.getMaxHealth(),id+" skill failed: "+ability.name()+"; hits="+hits);
        }
        helper.assertTrue(titan.hurt(helper.getLevel().damageSources().genericKill(),5000),id+" lethal damage rejected");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class,titan.getBoundingBox().inflate(3)).isEmpty(),id+" loot produced no items");
        titan.discard();victim.discard();helper.succeed();
    }
    @GameTest(template="arena",timeoutTicks=60)
    public static void v9SoundRegistryAndHit(GameTestHelper helper) {
        for(String id:TitanBlocks.IDS)for(String event:TitanSounds.EVENTS) {
            var key=net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(TitanSounds.sound(id,event));
            helper.assertTrue(key!=null && key.getNamespace().equals(TitanBlocks.MODID)
                && key.getPath().equals(id+"."+event),"Unregistered sound "+id+"."+event);
        }
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get("lava_rex").get(),20,20,40);
        titan.setNoAi(true);titan.setNoGravity(true);
        IronGolem victim=helper.spawn(EntityType.IRON_GOLEM,50,20,40);
        victim.setNoAi(true);victim.setNoGravity(true);
        helper.assertTrue(!TitanCombat.melee(helper.getLevel(),titan,victim,5),"out-of-range attack hit");
        victim.teleportTo(titan.getX()+4,titan.getY(),titan.getZ());Vec3 hit=victim.position();
        long before=TitanSoundTestHooks.count("lava_rex.hit_a",hit);
        helper.assertTrue(TitanCombat.melee(helper.getLevel(),titan,victim,8),"close attack did not hit");
        helper.assertTrue(TitanSoundTestHooks.count("lava_rex.hit_a",hit)==before+1,"actual hit sound not emitted at target");
        victim.invulnerableTime=0;
        TitanCombat.melee(helper.getLevel(),titan,victim,8);
        helper.assertTrue(TitanSoundTestHooks.count("lava_rex.hit_a",hit)==before+1,"duplicate hit sound in same swing");
        titan.discard();victim.discard();helper.succeed();
    }
    @GameTest(template="arena",timeoutTicks=170)
    public static void actualCombatLoop(GameTestHelper helper) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get("lava_rex").get(),20,3,40);
        titan.setNoGravity(true);titan.setPersistenceRequired();
        IronGolem victim=helper.spawn(EntityType.IRON_GOLEM,38,3,40);
        victim.setNoAi(true);victim.setNoGravity(true);
        titan.setTarget(victim);
        helper.runAtTickTime(120,()-> {
            helper.assertTrue(victim.getHealth()<victim.getMaxHealth(),"server AI never damaged target");
            helper.assertTrue(victim.isOnFire(),"timed fire skill did not execute");
            titan.discard();victim.discard();helper.succeed();
        });
    }
    // Superseded by ACA city-zone terrain denial tests.
    public static void frostAndGriefRule(GameTestHelper helper) {
        var level=helper.getLevel();
        TitanEntity frost=helper.spawn(TitanBlocks.TYPES.get("frost_bastion").get(),40,3,40);
        frost.setNoAi(true);frost.setNoGravity(true);
        BlockPos center=helper.absolutePos(new BlockPos(40,10,40));
        for(int dx=-12;dx<=12;dx++)for(int dz=-12;dz<=12;dz++)level.setBlock(center.offset(dx,0,dz),Blocks.WATER.defaultBlockState(),3);
        int changed=TitanEnvironment.interact(level,frost,Vec3.atCenterOf(center),12);
        helper.assertTrue(changed>0 && changed<=TitanConfig.TERRAIN_BLOCK_LIMIT.get(),"ice change count not bounded: "+changed);
        var rule=level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean previous=rule.get();
        try {
            rule.set(false,level.getServer());
            helper.assertTrue(TitanEnvironment.interact(level,frost,Vec3.atCenterOf(center),12)==0,"mobGriefing false was ignored");
        } finally {rule.set(previous,level.getServer());}
        frost.discard();helper.succeed();
    }
    private static void enlargedBoundary(GameTestHelper helper,String id) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get(id).get(),20,3,40);
        titan.setNoAi(true);titan.setNoGravity(true);
        IronGolem inside=helper.spawn(EntityType.IRON_GOLEM,40,3,40);
        IronGolem outside=helper.spawn(EntityType.IRON_GOLEM,65,3,40);
        inside.setNoAi(true);inside.setNoGravity(true);outside.setNoAi(true);outside.setNoGravity(true);
        titan.setTarget(inside);
        for(var a:new TitanProfiles.Ability[]{titan.profile().first(),titan.profile().second()}) {
            inside.setHealth(inside.getMaxHealth());outside.setHealth(outside.getMaxHealth());
            inside.invulnerableTime=0;outside.invulnerableTime=0;
            Vec3 base=titan.position(),origin,end;
            if(a.shape()==TitanProfiles.Shape.BEAM) {
                origin=base.add(0,inside.getBbHeight()*.5,0);end=origin.add(38,0,0);
                inside.moveTo(base.x+20,base.y,base.z+a.radius()-2,0,0);
                outside.moveTo(base.x+20,base.y,base.z+a.radius()+4,0,0);
            } else {
                origin=base;end=base;
                inside.moveTo(base.x+a.radius()-2,base.y,base.z,0,0);
                outside.moveTo(base.x+a.radius()+4,base.y,base.z,0,0);
            }
            int hits=TitanCombat.resolve(helper.getLevel(),titan,a,origin,end);
            helper.assertTrue(hits==1 && inside.getHealth()<inside.getMaxHealth(),id+" failed enlarged boundary "+a.name()+" hits="+hits);
            helper.assertTrue(outside.getHealth()==outside.getMaxHealth(),id+" hit outside advertised radius "+a.name());
        }
        titan.discard();inside.discard();outside.discard();helper.succeed();
    }
    private static void environmentSignature(GameTestHelper helper,String id) {
        var level=helper.getLevel();TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get(id).get(),20,3,40);
        titan.setNoAi(true);titan.setNoGravity(true);
        BlockPos center=helper.absolutePos(new BlockPos(40,10,40));
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)level.setBlock(center.offset(x,0,z),Blocks.DIRT.defaultBlockState(),3);
        var expected=switch(titan.profile().environment()) {
            case LAVA->Blocks.MAGMA_BLOCK;case FROST->Blocks.SNOW_BLOCK;case STORM->Blocks.COARSE_DIRT;
            case GROVE->Blocks.MOSS_BLOCK;case TIDAL->Blocks.MUD;case VOID->Blocks.DEEPSLATE;
            case CRYSTAL->Blocks.AMETHYST_BLOCK;case SAND->Blocks.SAND;case FUNGUS->Blocks.MYCELIUM;
            case SOUL->Blocks.SOUL_SOIL;case METAL->Blocks.COPPER_BLOCK;case NIGHT->Blocks.SCULK;
        };
        int changed=TitanEnvironment.interact(level,titan,Vec3.atCenterOf(center.above()),12);
        int matching=0;for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)if(level.getBlockState(center.offset(x,0,z)).is(expected))matching++;
        helper.assertTrue(changed>0 && changed<=TitanConfig.TERRAIN_BLOCK_LIMIT.get() && matching>0,id+" missing themed terrain " + expected + " changed="+changed+" found="+matching);
        titan.discard();helper.succeed();
    }
    private static void aiMelee(GameTestHelper helper,String id) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get(id).get(),20,3,40);
        titan.setNoGravity(true);titan.setPersistenceRequired();
        double reach=Math.max(7,titan.getBbWidth()*.5+3);
        IronGolem target=helper.spawn(EntityType.IRON_GOLEM,(float)(20+reach),3f,40f);
        target.setNoAi(true);target.setNoGravity(true);
        try {
            for(String name:new String[]{"firstCooldown","secondCooldown"}) {
                var field=TitanEntity.class.getDeclaredField(name);field.setAccessible(true);field.setInt(titan,1000);
            }
        } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        titan.setTarget(target);
        helper.runAtTickTime(60,()-> {
            helper.assertTrue(target.getHealth()<target.getMaxHealth(),id+" AI melee failed to damage nearby target");
            titan.discard();target.discard();helper.succeed();
        });
    }
    private static void motionAttackCycle(GameTestHelper helper,String id) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get(id).get(),20,20,40);
        titan.setNoGravity(true);titan.setPersistenceRequired();
        double distance=id.equals("ironback")?22:id.equals("dune_scorpion")?23:Math.max(7,titan.getBbWidth()*.5+3);
        IronGolem target=helper.spawn(EntityType.IRON_GOLEM,(float)(20+distance),20f,40f);
        target.setNoAi(true);target.setNoGravity(true);target.setHealth(target.getMaxHealth());
        try {
            for(String name:new String[]{"firstCooldown","secondCooldown"}) {
                var field=TitanEntity.class.getDeclaredField(name);field.setAccessible(true);field.setInt(titan,1000);
            }
            var cooldown=TitanEntity.class.getDeclaredField("meleeCooldown");cooldown.setAccessible(true);cooldown.setInt(titan,0);
        } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        Vec3 start=titan.position();titan.setTarget(target);
        double secondDistance=switch(id) {
            case "lava_rex" -> 18;
            case "frost_bastion" -> 14;
            case "grove_titan" -> 9;
            case "dune_scorpion" -> 12;
            case "tidal_sail" -> 21;
            default -> distance;
        };
        helper.runAtTickTime(8,()-> {
            var source=target.getLastDamageSource();
            helper.assertTrue(source==null || source.getEntity()!=titan,id+" hit before the windup");
        });
        helper.runAtTickTime(12,()-> {target.setHealth(target.getMaxHealth());target.invulnerableTime=0;});
        helper.runAtTickTime(22,()-> {
            helper.assertTrue(target.getHealth()<target.getMaxHealth()-2,id+" first strike missed: health="+target.getHealth());
            helper.assertTrue(titan.position().distanceToSqr(start)>.09,id+" did not perform the short lunge");
            helper.assertTrue(nextMeleeIsB(titan),id+" did not choose the first attack variant");
        });
        helper.runAtTickTime(52,()-> {
            target.setHealth(target.getMaxHealth());target.invulnerableTime=0;
            target.moveTo(titan.getX()+secondDistance,titan.getY(),titan.getZ(),0,0);target.setDeltaMovement(Vec3.ZERO);
        });
        helper.runAtTickTime(90,()-> {
            helper.assertTrue(target.getHealth()<target.getMaxHealth()-2,id+" second strike missed: health="+target.getHealth());
            helper.assertTrue(!nextMeleeIsB(titan),id+" did not alternate to the second attack variant");
            titan.discard();target.discard();helper.succeed();
        });
    }
    private static boolean nextMeleeIsB(TitanEntity titan) {
        try {
            var field=TitanEntity.class.getDeclaredField("meleeBNext");field.setAccessible(true);return field.getBoolean(titan);
        } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void extendedStrike(GameTestHelper helper,String id,boolean variantB,double distance) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get(id).get(),20,20,40);
        titan.setNoGravity(true);titan.setPersistenceRequired();
        IronGolem target=helper.spawn(EntityType.IRON_GOLEM,(float)(20+distance),20f,40f);
        target.setNoAi(true);target.setNoGravity(true);target.setHealth(target.getMaxHealth());
        try {
            for(String name:new String[]{"firstCooldown","secondCooldown"}) {
                var field=TitanEntity.class.getDeclaredField(name);field.setAccessible(true);field.setInt(titan,1000);
            }
            var cooldown=TitanEntity.class.getDeclaredField("meleeCooldown");cooldown.setAccessible(true);cooldown.setInt(titan,0);
            var next=TitanEntity.class.getDeclaredField("meleeBNext");next.setAccessible(true);next.setBoolean(titan,variantB);
        } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        titan.setTarget(target);
        helper.runAtTickTime(12,()-> {target.setHealth(target.getMaxHealth());target.invulnerableTime=0;});
        helper.runAtTickTime(24,()-> {
            helper.assertTrue(target.getHealth()<target.getMaxHealth()-2,id+" extended strike missed at "+distance+" blocks");
            titan.discard();target.discard();helper.succeed();
        });
    }
    private static void limbContactAndMiss(GameTestHelper helper,String id,boolean variantB,int distance) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get(id).get(),20,20,40);
        titan.setNoAi(true);titan.setNoGravity(true);
        float yaw=TitanBoneRig.turnsTailToTarget(id,variantB)?90:-90;
        titan.setYRot(yaw);titan.yBodyRot=yaw;titan.yHeadRot=yaw;
        IronGolem hit=helper.spawn(EntityType.IRON_GOLEM,20+distance,20,40);
        IronGolem miss=helper.spawn(EntityType.IRON_GOLEM,20+distance,20,48);
        var narrow=helper.spawn(EntityType.ZOMBIE,20+distance,20,40);
        hit.setNoAi(true);hit.setNoGravity(true);miss.setNoAi(true);miss.setNoGravity(true);
        narrow.setNoAi(true);narrow.setNoGravity(true);
        hit.setHealth(hit.getMaxHealth());miss.setHealth(miss.getMaxHealth());
        narrow.setHealth(narrow.getMaxHealth());
        Set<UUID> struck=new HashSet<>();int samples=0;
        for(int age=13;age<=23;age++) {
            var result=TitanLimbCollision.sweep(helper.getLevel(),titan,variantB,(age-2)/20.0,(age-1)/20.0,titan.position(),yaw,struck);
            samples+=result.samples();
            helper.assertTrue(result.maxVertexStep()<=.301,id+" sweep samples too far apart: "+result.maxVertexStep());
            hit.invulnerableTime=0;miss.invulnerableTime=0;narrow.invulnerableTime=0;
        }
        helper.assertTrue(samples>0,id+" collision was never sampled");
        helper.assertTrue(hit.getHealth()<hit.getMaxHealth()-2,id+" actual limb did not hit the on-path target at "+distance);
        helper.assertTrue(narrow.getHealth()<narrow.getMaxHealth()-2,id+" missed a player-width target on path at "+distance);
        helper.assertTrue(miss.getHealth()==miss.getMaxHealth(),id+" hit an off-path target");
        helper.assertTrue(struck.size()==2 && struck.contains(hit.getUUID()) && struck.contains(narrow.getUUID()),id+" failed per-swing deduplication");
        float after=hit.getHealth();hit.invulnerableTime=0;
        TitanLimbCollision.sweep(helper.getLevel(),titan,variantB,.65,.75,titan.position(),yaw,struck);
        helper.assertTrue(hit.getHealth()==after,id+" damaged the same target twice in one swing");
        titan.discard();hit.discard();miss.discard();narrow.discard();helper.succeed();
    }
    @GameTest(template="arena",timeoutTicks=40)
    public static void v8RotatedCuboidRejectsBoundingBoxFalsePositive(GameTestHelper helper) {
        org.joml.Vector3d[] vertices=new org.joml.Vector3d[8];
        var rotation=new org.joml.Matrix4d().rotateY(Math.PI/4);
        for(int i=0;i<8;i++)vertices[i]=rotation.transformPosition(new org.joml.Vector3d((i&4)==0?-1:1,(i&2)==0?-1:1,(i&1)==0?-1:1));
        helper.assertTrue(TitanLimbCollision.intersects(vertices,new net.minecraft.world.phys.AABB(.3,-.5,.3,.6,.5,.6)),"real oriented contact missed");
        helper.assertTrue(!TitanLimbCollision.intersects(vertices,new net.minecraft.world.phys.AABB(1.1,-.5,1.1,1.3,.5,1.3)),"AABB-only false positive was accepted");
        helper.succeed();
    }
    @GameTest(template="arena",timeoutTicks=60)
    public static void v8SolidWallBlocksLimbDamage(GameTestHelper helper) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get("ironback").get(),20,20,40);
        titan.setNoAi(true);titan.setNoGravity(true);titan.setYRot(-90);titan.yBodyRot=-90;
        IronGolem victim=helper.spawn(EntityType.IRON_GOLEM,41,20,40);victim.setNoAi(true);victim.setNoGravity(true);
        victim.setHealth(victim.getMaxHealth());
        var level=helper.getLevel();BlockPos wall=helper.absolutePos(new BlockPos(34,20,36));
        for(int y=0;y<27;y++)for(int z=0;z<9;z++)level.setBlock(wall.offset(0,y,z),Blocks.STONE.defaultBlockState(),2);
        Set<UUID> struck=new HashSet<>();
        for(int age=13;age<=23;age++)TitanLimbCollision.sweep(level,titan,false,(age-2)/20.0,(age-1)/20.0,titan.position(),-90,struck);
        helper.assertTrue(victim.getHealth()==victim.getMaxHealth(),"limb damaged target through a solid wall");
        for(int y=0;y<27;y++)for(int z=0;z<9;z++)level.setBlock(wall.offset(0,y,z),Blocks.AIR.defaultBlockState(),2);
        for(int age=13;age<=23;age++)TitanLimbCollision.sweep(level,titan,false,(age-2)/20.0,(age-1)/20.0,titan.position(),-90,struck);
        helper.assertTrue(victim.getHealth()<victim.getMaxHealth()-2,"same target was not hit after wall removal");
        titan.discard();victim.discard();helper.succeed();
    }
    @GameTest(template="arena",timeoutTicks=40)
    public static void v6MeleeSweepsNeighbors(GameTestHelper helper) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get("clockwork_rex").get(),20,3,40);
        titan.setNoAi(true);titan.setNoGravity(true);
        IronGolem target=helper.spawn(EntityType.IRON_GOLEM,35,3,40);
        IronGolem near=helper.spawn(EntityType.IRON_GOLEM,35,3,44);
        IronGolem far=helper.spawn(EntityType.IRON_GOLEM,35,3,55);
        target.setNoAi(true);near.setNoAi(true);far.setNoAi(true);
        helper.assertTrue(TitanCombat.melee(helper.getLevel(),titan,target,19),"melee did not hit");
        helper.assertTrue(target.getHealth()<target.getMaxHealth() && near.getHealth()<near.getMaxHealth(),"sweep failed to hit both targets");
        helper.assertTrue(far.getHealth()==far.getMaxHealth(),"sweep hit a distant target");
        titan.discard();target.discard();near.discard();far.discard();helper.succeed();
    }
    @GameTest(template="arena",timeoutTicks=80)
    public static void v6TracksMovingTarget(GameTestHelper helper) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get("lava_rex").get(),20,3,40);
        titan.setNoGravity(true);titan.setPersistenceRequired();
        IronGolem target=helper.spawn(EntityType.IRON_GOLEM,36,3,40);
        target.setNoAi(true);target.setNoGravity(true);titan.setTarget(target);
        try {
            var begin=TitanEntity.class.getDeclaredMethod("beginCast",net.minecraft.server.level.ServerLevel.class,TitanProfiles.Ability.class,net.minecraft.world.entity.LivingEntity.class,boolean.class);
            begin.setAccessible(true);begin.invoke(titan,helper.getLevel(),titan.profile().second(),target,true);
        } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        helper.runAtTickTime(5,()->helper.moveTo(target,65,3,40));
        helper.runAtTickTime(50,()-> {
            helper.assertTrue(target.getHealth()<target.getMaxHealth(),"target moving beyond the old locked area escaped tracking");
            titan.discard();target.discard();helper.succeed();
        });
    }
    // Superseded by ACA city-zone terrain denial tests.
    public static void v6BoundedDestructionAndProtection(GameTestHelper helper) {
        var level=helper.getLevel();
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get("lava_rex").get(),20,3,40);
        titan.setNoAi(true);titan.setNoGravity(true);
        BlockPos center=helper.absolutePos(new BlockPos(40,10,40));
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)level.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        BlockPos chest=center.offset(1,0,0),bedrock=center.offset(-1,0,0),obsidian=center.offset(0,0,1);
        level.setBlock(chest,Blocks.CHEST.defaultBlockState(),3);level.setBlock(bedrock,Blocks.BEDROCK.defaultBlockState(),3);
        level.setBlock(obsidian,Blocks.OBSIDIAN.defaultBlockState(),3);
        int changed=TitanEnvironment.interact(level,titan,Vec3.atCenterOf(center.above()),12);
        helper.assertTrue(changed>0 && changed<=TitanConfig.TERRAIN_BLOCK_LIMIT.get(),"terrain limit failed: "+changed);
        int holes=0;for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)if(level.getBlockState(center.offset(x,0,z)).isAir())holes++;
        helper.assertTrue(holes>0,"no blocks were actually destroyed");
        helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST) && level.getBlockEntity(chest)!=null,"container damaged");
        helper.assertTrue(level.getBlockState(bedrock).is(Blocks.BEDROCK) && level.getBlockState(obsidian).is(Blocks.OBSIDIAN),"protected hard block damaged");
        var rule=level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);boolean previous=rule.get();
        try {rule.set(false,level.getServer());helper.assertTrue(TitanEnvironment.interact(level,titan,Vec3.atCenterOf(center),12)==0,"grief rule ignored");}
        finally {rule.set(previous,level.getServer());}
        titan.discard();helper.succeed();
    }
    @GameTest(template="arena",timeoutTicks=110)
    public static void groundPursuit(GameTestHelper helper){motion(helper,"lava_rex",false);}
    @GameTest(template="arena",timeoutTicks=110)
    public static void flyingPursuit(GameTestHelper helper){motion(helper,"storm_drake",false);}
    @GameTest(template="arena",timeoutTicks=110)
    public static void swimmingPursuit(GameTestHelper helper){motion(helper,"tidal_sail",true);}
    private static void motion(GameTestHelper helper,String id,boolean water) {
        var level=helper.getLevel();
        if(water) {
            // Test away from the edge currents; displacement alone could be a
            // current pushing the creature away from its target.
            BlockPos p=helper.absolutePos(new BlockPos(5,11,25));
            for(int dx=0;dx<70;dx++)for(int dz=0;dz<30;dz++)for(int dy=0;dy<4;dy++)
                level.setBlock(p.offset(dx,dy,dz),Blocks.WATER.defaultBlockState(),2);
        }
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get(id).get(),20,12,40);
        titan.setPersistenceRequired();
        if(water)titan.setNoGravity(true);
        IronGolem victim=helper.spawn(EntityType.IRON_GOLEM,60,12,40);
        victim.setNoAi(true);victim.setNoGravity(true);titan.setTarget(victim);
        Vec3 initial=titan.position();
        helper.runAtTickTime(60,()-> {
            double dx=titan.getX()-initial.x,dz=titan.getZ()-initial.z;
            helper.assertTrue(dx*dx+dz*dz>1.0,id+" pursuit did not move horizontally: "+initial+" -> "+titan.position());
            double initialDistance=Math.hypot(victim.getX()-initial.x,victim.getZ()-initial.z);
            double finalDistance=Math.hypot(victim.getX()-titan.getX(),victim.getZ()-titan.getZ());
            helper.assertTrue(initialDistance-finalDistance>1,id+" moved but did not approach its target: "+initialDistance+" -> "+finalDistance);
            titan.discard();victim.discard();helper.succeed();
        });
    }
    @GameTest(template="arena",timeoutTicks=40)
    public static void beamBlockedByWall(GameTestHelper helper) {
        var level=helper.getLevel();
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get("storm_drake").get(),20,12,40);
        titan.setNoAi(true);titan.setNoGravity(true);
        IronGolem victim=helper.spawn(EntityType.IRON_GOLEM,60,12,40);
        victim.setNoAi(true);victim.setNoGravity(true);
        titan.setYRot(-90);titan.yBodyRot=-90;
        BlockPos wall=helper.absolutePos(new BlockPos(45,10,37));
        for(int y=0;y<30;y++)for(int z=0;z<7;z++)level.setBlock(wall.offset(0,y,z),Blocks.STONE.defaultBlockState(),2);
        var ability=titan.profile().first();Vec3 from=TitanCombat.beamStart(titan);
        Vec3 end=TitanCombat.clippedBeamEnd(level,titan,from,victim.position().add(0,victim.getBbHeight()*.5,0),ability.range());
        helper.assertTrue(end.distanceToSqr(from)<victim.position().distanceToSqr(from),"beam passed through wall");
        helper.assertTrue(TitanCombat.resolve(level,titan,ability,from,end)==0,"beam hurt target behind wall");
        titan.discard();victim.discard();helper.succeed();
    }
    @GameTest(template="arena",timeoutTicks=40)
    public static void creativePlayersAreExcluded(GameTestHelper helper) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get("lava_rex").get(),20,12,40);
        helper.assertFalse(TitanCombat.eligible(titan,helper.makeMockPlayer(GameType.CREATIVE)),"creative player eligible for damage");
        helper.assertFalse(TitanCombat.eligible(titan,helper.makeMockPlayer(GameType.SPECTATOR)),"spectator eligible for damage");
        helper.assertTrue(TitanCombat.eligible(titan,helper.makeMockPlayer(GameType.SURVIVAL)),"survival player unexpectedly excluded");
        titan.discard();helper.succeed();
    }
    @GameTest(template="arena",timeoutTicks=40)
    public static void lootActuallyDrops(GameTestHelper helper) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get("lava_rex").get(),20,12,40);
        titan.setNoAi(true);titan.setNoGravity(true);
        helper.assertTrue(titan.hurt(helper.getLevel().damageSources().genericKill(),5000),"lethal damage rejected");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class,titan.getBoundingBox().inflate(3)).isEmpty(),"loot table yielded no items");
        titan.discard();helper.succeed();
    }
    @GameTest(template="arena",timeoutTicks=40)
    public static void enrageAndSave(GameTestHelper helper) {
        TitanEntity titan=helper.spawn(TitanBlocks.TYPES.get("lava_rex").get(),20,12,40);
        titan.setNoGravity(true);titan.setHealth(titan.getMaxHealth()*.4f);
        helper.runAtTickTime(5,()-> {
            helper.assertTrue(titan.enraged(),"half-health phase did not start");
            var tag=new net.minecraft.nbt.CompoundTag();titan.addAdditionalSaveData(tag);
            TitanEntity restored=TitanBlocks.TYPES.get("lava_rex").get().create(helper.getLevel());
            helper.assertTrue(restored!=null,"could not create reload test entity");
            restored.readAdditionalSaveData(tag);
            helper.assertTrue(restored.enraged(),"phase was lost on reload");
            helper.assertTrue(restored.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)>titan.profile().speed(),"phase speed was lost on reload");
            titan.discard();helper.succeed();
        });
    }
}
