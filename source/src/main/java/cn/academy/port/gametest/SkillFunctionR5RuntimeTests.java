package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.canon.*;
import cn.academy.port.core.*;
import cn.academy.port.highpower.*;
import cn.academy.port.macro.*;
import cn.academy.port.skill.ElectronMissile;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Functional assertions, not cast receipts: actual shields/HP, exact spatial exclusions and native payloads. */
@GameTestHolder("academy_function_r5")
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid="academy")
public final class SkillFunctionR5RuntimeTests {
    private static final String TEMPLATE="academy_function_r5:room";
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private SkillFunctionR5RuntimeTests() {}
    @net.neoforged.bus.api.SubscribeEvent public static void template(net.neoforged.neoforge.event.level.LevelEvent.Load event){
        if(!GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof ServerLevel world))return;
        var tag=new net.minecraft.nbt.CompoundTag();var size=new net.minecraft.nbt.ListTag();for(int n:new int[]{160,24,160})size.add(net.minecraft.nbt.IntTag.valueOf(n));
        var pos=new net.minecraft.nbt.ListTag();for(int n=0;n<3;n++)pos.add(net.minecraft.nbt.IntTag.valueOf(0));var cell=new net.minecraft.nbt.CompoundTag();cell.put("pos",pos);cell.putInt("state",0);
        var blocks=new net.minecraft.nbt.ListTag();blocks.add(cell);var air=new net.minecraft.nbt.CompoundTag();air.putString("Name","minecraft:air");var palette=new net.minecraft.nbt.ListTag();palette.add(air);
        tag.put("size",size);tag.put("blocks",blocks);tag.put("palette",palette);tag.put("entities",new net.minecraft.nbt.ListTag());
        world.getStructureManager().getOrCreate(net.minecraft.resources.ResourceLocation.parse(TEMPLATE)).load(world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),tag);
    }
    @GameTestGenerator public static Collection<TestFunction> contracts(){
        var tests=new ArrayList<TestFunction>();
        for(double mastery:new double[]{0,.5,1}) for(boolean shielded:new boolean[]{true,false}){
            String name="missile_"+(int)(mastery*100)+(shielded?"_shield":"_hp");
            tests.add(new TestFunction(name,"academy_function_r5."+name,TEMPLATE,Rotation.NONE,150,0,true,false,1,1,false,h->missile(h,mastery,shielded)));
        }
        for(String id:List.of("aero_squall","dark_shard_ring"))for(double mastery:new double[]{0,.5,1}){
            String name=id+"_"+(int)(mastery*100);
            tests.add(new TestFunction(name,"academy_function_r5."+name+"_boundary_and_mastery_snapshot",TEMPLATE,Rotation.NONE,120,0,true,false,1,1,false,h->area(h,id,mastery)));
        }
        tests.add(new TestFunction("curtain","academy_function_r5.curtain_boxes_match_actual_outer_and_inner_intercepts",TEMPLATE,Rotation.NONE,100,0,true,false,1,1,false,SkillFunctionR5RuntimeTests::curtain));
        tests.add(new TestFunction("float_field","academy_function_r5.float_field_keeps_real_control_beyond_old_32_block_limit",TEMPLATE,Rotation.NONE,150,0,true,false,1,1,false,SkillFunctionR5RuntimeTests::floatField));
        tests.add(new TestFunction("catalog","academy_function_r5.all_321_registered_descriptions_resolve_in_four_locales",TEMPLATE,Rotation.NONE,30,0,true,false,1,1,false,SkillFunctionR5RuntimeTests::catalog));
        return tests;
    }
    private record Peer(ServerPlayer player,EmbeddedChannel channel) {}
    static final class Fixture {
        final GameTestHelper h;final ServerLevel world;final Peer owner;final ServerPlayer p;final Vec3 at;
        final List<Peer> peers=new ArrayList<>();final List<Entity> entities=new ArrayList<>();
        final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> saved=new LinkedHashMap<>();boolean closed;
        final Set<Long> ownedTickets=new HashSet<>();
        Fixture(GameTestHelper h,String category,String skill,double mastery){
            EsperLegacyBypass.force(true);
            this.h=h;world=h.getLevel();at=h.absoluteVec(new Vec3(40,8,32)).add(-8192,0,-8192);owner=join(at);p=owner.player;
            var s=AbilityStorage.get(p);s.changeCategoryClassic(category);s.setLevel(5);
            for(var def:SkillCatalog.REGISTERED)if(def.category().equals(category))s.learn(def.id());
            s.setSkillExp(skill,(float)mastery);s.activated=true;s.overloadFine=true;
            h.assertTrue(s.presets.edit(0,0,skill,s::learned),"actual selected preset");
            h.assertTrue(HighPowerCombat.enroll(p,5),"finite level-five caster");HighPowerResources.initializeBudget(p);
            s.cp=s.maxCp();s.overload=0;s.overloadFine=true;
            // Only this isolated fixture, outside the framework's barrier walls,
            // needs temporary tickets so real entities keep ticking during waits.
            for(int x=-96;x<=96;x+=16)for(int z=-96;z<=96;z+=16){
                var pos=new net.minecraft.world.level.ChunkPos(BlockPos.containing(at.add(x,0,z)));
                if(!world.getForcedChunks().contains(pos.toLong())){world.setChunkForced(pos.x,pos.z,true);ownedTickets.add(pos.toLong());}
                world.getChunk(pos.x,pos.z);
            }
            h.testInfo.addListener(new GameTestListener(){
                public void testStructureLoaded(GameTestInfo i){}
                public void testPassed(GameTestInfo i,GameTestRunner r){close();}
                public void testFailed(GameTestInfo i,GameTestRunner r){close();}
                public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}
            });
        }
        Peer join(Vec3 where){
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"R5"+UUID.randomUUID().toString().substring(0,8)),false);
            var player=new ServerPlayer(world.getServer(),world,cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);
            world.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);player.moveTo(where,0,0);player.setYHeadRot(0);
            var peer=new Peer(player,channel);peers.add(peer);return peer;
        }
        Husk target(double x,double z,boolean shielded){
            var target=EntityType.HUSK.create(world);h.assertTrue(target!=null,"real vanilla target");
            target.setNoAi(true);target.setNoGravity(true);target.setPos(at.add(x,0,z));target.getAttribute(Attributes.ARMOR).setBaseValue(0);
            h.assertTrue(world.addFreshEntity(target)&&HighPowerCombat.enroll(target,5),"real finite target ledger");
            if(!shielded)h.assertTrue(HighPowerCombat.spendShieldFraction(target,BigDecimal.ONE),"fixture spends target shield once");
            entities.add(target);return target;
        }
        LivingEntity esperTarget(double x,double z){
            var target=cn.academy.port.city.CityNpcContent.NPC.get().create(world);h.assertTrue(target!=null,"real city esper");
            var tag=new net.minecraft.nbt.CompoundTag();tag.putString("Esper","electro");tag.putInt("EsperLevel",5);target.readAdditionalSaveData(tag);
            target.setNoAi(true);target.setNoGravity(true);target.setPos(at.add(x,0,z));target.getAttribute(Attributes.ARMOR).setBaseValue(0);
            h.assertTrue(world.addFreshEntity(target),"city esper added");target.combat();entities.add(target);
            h.assertTrue(HighPowerCombat.enabled(target)&&HighPowerCombat.shield(target).signum()>0,"real category shield, not an inactive generic-mob ledger");return target;
        }
        void block(Vec3 pos,net.minecraft.world.level.block.Block type){var at=BlockPos.containing(pos);saved.putIfAbsent(at,world.getBlockState(at));world.setBlock(at,type.defaultBlockState(),2);}
        void aim(Vec3 point){CanonSkillAuditRuntimeTests.aimAt(p,point);}
        void close(){if(closed)return;closed=true;for(var entity:entities)entity.discard();for(var entry:saved.entrySet())world.setBlock(entry.getKey(),entry.getValue(),2);
            try{for(var peer:peers){ElectronMissile.remove(peer.player);world.getServer().getPlayerList().remove(peer.player);peer.channel.finishAndReleaseAll();}}
            finally{for(long key:ownedTickets){var pos=new net.minecraft.world.level.ChunkPos(key);world.setChunkForced(pos.x,pos.z,false);}EsperLegacyBypass.force(false);}}
    }
    private record Packets(List<MacroVfx.Event> macro,List<CanonVfx.Visual> canon,List<String> nativeKinds) {}
    private static Packets drain(Peer peer){
        peer.channel.runPendingTasks();peer.channel.flushOutbound();peer.channel.runPendingTasks();
        var macro=new ArrayList<MacroVfx.Event>();var canon=new ArrayList<CanonVfx.Visual>();var kinds=new ArrayList<String>();Object value;
        while((value=peer.channel.readOutbound())!=null){try{if(value instanceof ClientboundCustomPayloadPacket packet){
            if(packet.payload() instanceof MacroVfx.Event v)macro.add(v);
            if(packet.payload() instanceof CanonVfx.Visual v)canon.add(v);
            if(packet.payload() instanceof AcademyNetwork.ClientData v)kinds.add(v.data().getString("kind"));
        }}finally{ReferenceCountUtil.release(value);}}
        return new Packets(macro,canon,kinds);
    }
    private static BigDecimal health(LivingEntity e){return new BigDecimal(HighPowerCombat.healthText(e));}
    private static BigDecimal pool(LivingEntity e){return health(e).add(HighPowerCombat.shield(e));}
    private static void near(GameTestHelper h,BigDecimal actual,BigDecimal expected,String what){h.assertTrue(actual.subtract(expected).abs().compareTo(new BigDecimal("0.05"))<0,what+": actual="+actual+" expected="+expected);}
    private static void missile(GameTestHelper h,double mastery,boolean shielded){
        var f=new Fixture(h,"meltdowner",ElectronMissile.ID,mastery);double radius=32+16*mastery;
        var inside=f.esperTarget(0,radius-.01);var outside=f.esperTarget(0,-radius-.01);
        // Real wall: the original skill deliberately acquires through it.
        for(int y=0;y<4;y++)f.block(f.at.add(0,y,2),Blocks.BEDROCK);
        var watcher=f.join(f.at.add(radius+50,0,0));
        var beforeH=health(inside);BigDecimal[] beforeS={BigDecimal.ZERO},beforeOutside={BigDecimal.ZERO},expected={BigDecimal.ZERO};
        // Let the native entity sections become ticking before a multi-chunk query.
        h.runAfterDelay(65,()->{
            if(!shielded){h.assertTrue(HighPowerCombat.spendShieldFraction(inside,BigDecimal.ONE)&&HighPowerCombat.spendShieldFraction(outside,BigDecimal.ONE),"prepare finite zero-shield targets immediately before input");}
            beforeS[0]=HighPowerCombat.shield(inside);beforeOutside[0]=pool(outside);
            expected[0]=HighPowerGrowth.values(f.p).damage().multiply(BigDecimal.valueOf(10+8*mastery)).divide(BigDecimal.valueOf(EsperBalanceConfig.divisor()));
            h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("slot_press_token","0:1")),"normal authenticated preset starts missile");
        });
        for(int t=66;t<=95;t++)h.runAfterDelay(t,()->ElectronMissile.tick(f.p));
        h.runAfterDelay(75,()->{
            BigDecimal hLoss=beforeH.subtract(health(inside)),sLoss=beforeS[0].subtract(HighPowerCombat.shield(inside));
            var state=AbilityStorage.get(f.p);
            LOG.info("FUNCTION_R5_MISSILE_STATE mastery={} ticks={} balls={} active={} cp={}/{} overload={} fine={} targetAlive={} loaded={} present={} caster={} target={}",mastery,ElectronMissile.heldTicks(f.p),ElectronMissile.ballCount(f.p),ElectronMissile.active(f.p),state.cp,state.maxCp(),state.overload,state.overloadFine,inside.isAlive(),f.world.hasChunkAt(inside.blockPosition()),f.world.getEntity(inside.getUUID())==inside,f.p.position(),inside.position());
            near(h,hLoss.add(sLoss),expected[0],"one unarmoured real beam follows original raw * category D / divisor");
            h.assertTrue(shielded?hLoss.signum()==0&&sLoss.signum()>0:sLoss.signum()==0&&hLoss.signum()>0,"shield absorption and HP damage are distinct");
            near(h,pool(outside),beforeOutside[0],"outside target is untouched");
            var packets=drain(watcher);
            h.assertTrue(packets.nativeKinds.containsAll(List.of("electron_missile_start","electron_missile_ball","electron_missile_ray")),"distant watcher receives real orb and shot, not just ambient VFX");
            var ranges=packets.macro.stream().filter(v->v.skill().equals(ElectronMissile.ID)&&v.actualRadius()>0).toList();
            h.assertTrue(!ranges.isEmpty()&&ranges.stream().allMatch(v->v.actualRadius()==radius&&v.visualRadius()==radius&&v.to().distanceTo(f.p.position())<.01),"wire radius and centre match the actual captured search sphere");
            LOG.info("FUNCTION_R5_MISSILE mastery={} radius={} targetDistance={} hpBefore={} hpAfter={} shieldBefore={} shieldAfter={} expected={} observerDistance={}",mastery,radius,inside.distanceTo(f.p),beforeH,health(inside),beforeS[0],HighPowerCombat.shield(inside),expected[0],watcher.player.distanceTo(f.p));
            inside.discard();
        });
        h.runAfterDelay(84,()->{
            near(h,pool(outside),beforeOutside[0],"nearest outside target alone is still excluded after mastery XP changes");
            h.assertTrue(ElectronMissile.release(f.p,1),"matching release ends the real hold");
        });
        h.runAfterDelay(97,()->{
            near(h,pool(outside),beforeOutside[0],"release/residue cannot deal damage");
            h.assertTrue(!ElectronMissile.active(f.p)&&ElectronMissile.ballCount(f.p)==0,"no lingering server attack or orbs");h.succeed();
        });
    }
    private static void floatField(GameTestHelper h){
        var f=new Fixture(h,"float_dial","float_field",1);var target=f.target(40,0,true);target.setNoGravity(false);target.setNoAi(false);
        var outside=f.target(48.01,0,true);outside.setNoGravity(false);outside.setNoAi(true);
        for(int x:new int[]{40,48})f.block(f.at.add(x,-1,0),Blocks.STONE);
        double height=f.at.y;
        h.runAfterDelay(65,()->h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("slot_press","0")),"actual mastered float field"));
        h.runAfterDelay(80,()->{
            LOG.info("FUNCTION_R5_FLOAT_STATE target={} velocity={} distance={} alive={} loaded={} present={} gravity={} casterExp={} casterActive={} casterOverloadFine={}",target.position(),target.getDeltaMovement(),target.distanceTo(f.p),target.isAlive(),f.world.hasChunkAt(target.blockPosition()),f.world.getEntity(target.getUUID())==target,target.isNoGravity(),AbilityStorage.get(f.p).exp("float_field"),AbilityStorage.get(f.p).activated,AbilityStorage.get(f.p).overloadFine);
            h.assertTrue(target.getY()>height+.5&&target.getDeltaMovement().y>0,"real controlled target at 40m continues rising after several ticks");
            h.assertTrue(outside.getY()<height+.1,"48.01m target stays outside the field");
            var ranges=drain(f.owner).macro.stream().filter(v->v.skill().equals("float_field")&&v.actualRadius()>0).toList();
            h.assertTrue(!ranges.isEmpty()&&ranges.stream().allMatch(v->v.actualRadius()==48&&v.visualRadius()==48),"same 48m actual and drawn field");
            LOG.info("FUNCTION_R5_FLOAT_FIELD realDistance={} heightGain={} outsideHeightGain={}",target.distanceTo(f.p),target.getY()-height,outside.getY()-height);h.succeed();
        });
    }
    private static void area(GameTestHelper h,String id,double mastery){
        String category=id.startsWith("aero_")?"aero_hand":"dark_matter";var f=new Fixture(h,category,id,mastery);
        double radius=SkillEffectParameters.current(id,"radius",mastery);
        var inside=f.target(radius-.01,0,true);var outside=f.target(radius+.01,0,true);var beforeIn=pool(inside);var beforeOut=pool(outside);
        h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("slot_press","0")),"real area cast with novice skill mastery");
        h.runAfterDelay(8,()->{
            h.assertTrue(pool(inside).compareTo(beforeIn)<0,"inside target takes real area damage");near(h,pool(outside),beforeOut,"outside target excluded");
            var events=drain(f.owner).macro.stream().filter(v->v.skill().equals(id)&&v.actualRadius()>0).toList();
            h.assertTrue(!events.isEmpty()&&events.stream().allMatch(v->v.actualRadius()==radius&&v.visualRadius()==radius&&v.to().distanceTo(f.p.getEyePosition())<.01),"cast reward cannot retroactively enlarge the boundary, whose actual centre is the eye");
            LOG.info("FUNCTION_R5_AREA skill={} mastery={} insideDistance={} outsideDistance={} realRadius={} displayedRadius={}",id,mastery,inside.distanceTo(f.p),outside.distanceTo(f.p),radius,events.getFirst().visualRadius());h.succeed();
        });
    }
    private static void curtain(GameTestHelper h){
        var f=new Fixture(h,"aero_hand","aero_air_curtain",0);var eye=f.p.getEyePosition();
        h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("slot_press","0")),"actual curtain cast");
        var initial=drain(f.owner);var outer=CanonExpansionCombatA.barrierBox(eye,"aero_air_curtain");var inner=CanonExpansionCombatA.barrierInnerBox(eye,"aero_air_curtain");
        h.assertTrue(Math.abs(outer.getXsize()-96)<1e-6&&Math.abs(inner.getXsize()-5.2)<1e-6,"actual outer and inner box widths");
        h.assertTrue(initial.canon.stream().anyMatch(v->v.skill().equals("aero_air_curtain")&&v.radius()==48&&v.areaCentre().distanceTo(eye)<.01),"wire uses actual box half-width and eye centre, independent of mastery");
        var shooter=EntityType.SKELETON.create(f.world);shooter.setNoAi(true);shooter.setPos(f.at.add(20,0,0));f.world.addFreshEntity(shooter);f.entities.add(shooter);
        var arrows=new ArrayList<net.minecraft.world.entity.projectile.Arrow>();
        for(double x:new double[]{48.6,3.1}){
            var arrow=new net.minecraft.world.entity.projectile.Arrow(f.world,shooter,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),null);
            arrow.setPos(eye.add(x,0,0));arrow.setDeltaMovement(-1,0,0);arrow.setNoGravity(true);f.world.addFreshEntity(arrow);f.entities.add(arrow);arrows.add(arrow);
        }
        var shield=HighPowerCombat.shield(f.p);CanonExpansionCombatA.tickBarrier(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(f.p));
        h.assertTrue(arrows.stream().allMatch(a->a.getDeltaMovement().x>0),"real outside and already-inside arrows both intercepted at their corresponding surfaces");
        near(h,shield.subtract(HighPowerCombat.shield(f.p)),new BigDecimal(HighPowerCombat.maxShieldText(f.p)).multiply(new BigDecimal(".02")),"two unchanged one-percent shield debits");h.succeed();
    }
    private static void catalog(GameTestHelper h){
        var keys=new HashSet<String>();int active=0,passive=0;
        for(String locale:List.of("zh_cn","zh_tw","en_us","ja_jp")){
            var path="/assets/academy/lang/"+locale+".json";
            try(var reader=new InputStreamReader(Objects.requireNonNull(SkillCatalog.class.getResourceAsStream(path)),StandardCharsets.UTF_8)){
                var lang=JsonParser.parseReader(reader).getAsJsonObject();
                for(var skill:SkillCatalog.REGISTERED){
                    String base="ac.ability."+skill.category()+"."+skill.id();
                    if(!lang.has(base+".desc"))base="ac.ability.generic."+skill.id();
                    h.assertTrue(lang.has(base+".desc")&&lang.has(base+".name"),"localized registered name and description: "+locale+"/"+skill.category()+"/"+skill.id());
                    String prose=SkillTuning.format(skill.id(),lang.get(base+".desc").getAsString());
                    h.assertTrue(!prose.contains("{{")&&!prose.contains("2147483647")&&!prose.contains("∞"),"resolved readable description: "+locale+"/"+skill.id());
                    if(locale.equals("zh_cn")){
                        h.assertTrue(keys.add(skill.category()+"/"+skill.id()),"unique registry key");if(skill.controllable())active++;else passive++;
                        var fields=new TreeMap<>(SkillTuning.fields(skill.id()));
                        LOG.info("FUNCTION_R5_CATALOG category={} skill={} level={} active={} name={} params={} description={}",skill.category(),skill.id(),skill.level(),skill.controllable(),lang.get(base+".name").getAsString(),fields,prose);
                    }
                }
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
        }
        h.assertTrue(active==306&&passive==15&&keys.size()==321,"authoritative 306 active plus 15 passive/training registry");h.succeed();
    }
}
