package cn.academy.port.gametest;

import cn.academy.port.highpower.core.ConductiveCircuitRules;
import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.ConductiveCircuit;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.core.ConductiveCircuitRules.Phase;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.math.BigDecimal;
import java.util.*;

/** Native NEW-121 geometry, authenticated multi-tap lifecycle and real exact hurt acceptance. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerConductiveCircuitRuntimeTests {
    private static final String TEMPLATE="highpower_empty",BATCH="highpower_conductive_circuit";
    private HighPowerConductiveCircuitRuntimeTests(){}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void circuit_visible_near_bottom_ground_survives_full_ray_height_overshoot(GameTestHelper h){
        Fixture f=new Fixture(h);Vec3 from=f.p.getEyePosition(),full=from.add(f.p.getLookAngle().scale(24));
        h.assertTrue(from.y>=h.getLevel().getMinBuildHeight()&&full.y<h.getLevel().getMinBuildHeight(),"fixture actually reproduces ray below world min: "+f.diagnostic());
        h.assertTrue(f.tap(1),"valid earlier native UP hit is permitted by vertically bounded ray: "+f.diagnostic());h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void circuit_two_placements_then_paid_pulse_and_replay_guards(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp();
        h.assertTrue(f.tap(1),"first explicit ground anchor; "+f.diagnostic());h.assertTrue(ConductiveCircuit.phase(f.p)==Phase.FIRST_ANCHOR,"single anchor is not an active line");
        near(h,f.cp(),cp,"first placement is free");h.assertFalse(f.tap(1),"replay refused");
        h.assertFalse(f.tap(2),"fresh same-tick second placement refused");
        h.runAfterDelay(1,()->{
            f.aim(12.5);h.assertFalse(f.tap(2),"previous refused nonce remains consumed");h.assertTrue(f.tap(3),"second explicit ground anchor");
            h.assertTrue(ConductiveCircuit.phase(f.p)==Phase.RESTING,"two anchors form resting line");
            h.runAfterDelay(1,()->{
                double before=f.cp();h.assertTrue(f.tap(4),"third press energizes");near(h,f.cp(),before-5,"one LV2 shared CP payment");
                h.assertTrue(ConductiveCircuit.phase(f.p)==Phase.ENERGIZED,"authoritative active phase");
                h.assertFalse(f.tap(4),"pulse replay denied");near(h,f.cp(),before-5,"no repeat debit");
                h.runAfterDelay(1,()->{
                    double charged=f.cp();h.assertFalse(f.tap(5),"active fresh tap cannot extend pulse");near(h,f.cp(),charged,"active tap is free refusal");
                    f.p.setShiftKeyDown(true);h.runAfterDelay(1,()->{h.assertTrue(f.tap(6),"crouch recalls");h.assertFalse(ConductiveCircuit.active(f.p),"recall retires pair");h.succeed();});
                });
            });
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void circuit_real_crossing_debits_point_six_D_once_not_standing_overlap(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie crossed=f.zombie(6,7),standing=f.zombie(6.5,10);
        f.pairAndPulse(()->{
            String before=HighPowerCombat.healthText(crossed),standingBefore=HighPowerCombat.healthText(standing);
            h.runAfterDelay(1,()->{
                f.move(crossed,1,0,0);ConductiveCircuit.tick(f.p);
                h.assertTrue(new BigDecimal(HighPowerCombat.healthText(crossed)).compareTo(new BigDecimal(before).subtract(cn.academy.port.highpower.core.HighPowerLevel.LV2.damage().multiply(new BigDecimal(".6"))))==0,"actual crossing uses exact0.6D through native armorless damage");
                h.assertTrue(HighPowerCombat.healthText(standing).equals(standingBefore),"energizing under a stationary entity causes no hit");
                h.assertTrue(ConductiveCircuit.attemptedCount(f.p)==1,"one attempt admitted");String after=HighPowerCombat.healthText(crossed);
                ConductiveCircuit.tick(f.p);h.assertTrue(HighPowerCombat.healthText(crossed).equals(after),"double server callback cannot repeat native hurt");
                h.runAfterDelay(1,()->{
                    f.move(crossed,-1,0,0);ConductiveCircuit.tick(f.p);
                    h.assertTrue(ConductiveCircuit.attemptedCount(f.p)==1,"reverse crossing inside global10tick fence causes no new attempt");h.succeed();
                });
            });
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void circuit_pulse_cap_six_and_passive_non_enemies_excluded(GameTestHelper h){
        Fixture f=new Fixture(h);List<Zombie> enemies=new ArrayList<>();for(int i=0;i<8;i++)enemies.add(f.zombie(6,4+i));
        var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setNoGravity(true);pig.setPos(h.absoluteVec(new Vec3(6,3,11.8)));h.getLevel().addFreshEntity(pig);
        CircuitDamageTrace trace=new CircuitDamageTrace(f.p,enemies);f.hooks.add(trace);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(trace);
        awaitCircuitRoofSkylight(h,enemies,20,()->f.pairAndPulse(()->h.runAfterDelay(1,()->{
            Map<UUID,BigDecimal> before=new HashMap<>();
            for(Zombie e:enemies){
                before.put(e.getUUID(),new BigDecimal(HighPowerCombat.healthText(e)));
                h.assertFalse(h.getLevel().canSeeSky(BlockPos.containing(e.getEyePosition()))||e.isOnFire()||e.invulnerableTime>0,
                        "real roof preserves clean native enemies without rewriting HP or cooldown; "+trace.describe(before));
            }
            for(Zombie e:enemies)f.move(e,1,0,0);pig.move(MoverType.SELF,new Vec3(1,0,0));ConductiveCircuit.tick(f.p);
            String diagnostic=trace.describe(before);
            h.assertTrue(ConductiveCircuit.attemptedCount(f.p)==6,"entire pulse admits exactly six native hurt attempts; "+diagnostic);
            h.assertTrue(trace.incoming.size()==6&&trace.positivePosts.size()==6,"six distinct attempts reach native positive Post; "+diagnostic);
            int changed=0;for(Zombie e:enemies){
                BigDecimal delta=before.get(e.getUUID()).subtract(new BigDecimal(HighPowerCombat.healthText(e)));
                if(delta.signum()!=0){changed++;h.assertTrue(delta.compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV2.damage().multiply(new BigDecimal(".6")))==0,"each accepted crossing debits exactly0.6D; "+diagnostic);}
            }
            h.assertTrue(changed==6,"exactly six real ledgers change from immediate pre-crossing snapshot; "+diagnostic);
            near(h,pig.getHealth(),pig.getMaxHealth(),"passive pig is not an enemy");pig.discard();h.succeed();
        })));
    }
    private static void awaitCircuitRoofSkylight(GameTestHelper h,List<Zombie> enemies,int ticksRemaining,Runnable ready){
        for(int x=0;x<15;x++)for(int z=0;z<15;z++){
            BlockPos roof=new BlockPos(x,6,z);
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(roof)).is(Blocks.STONE),"physical fixture roof must exist before waiting for skylight at "+roof);
        }
        boolean shaded=true;for(Zombie e:enemies)if(h.getLevel().canSeeSky(BlockPos.containing(e.getEyePosition())))shaded=false;
        if(shaded){ready.run();return;}
        // Let native asynchronous skylight settle before any anchor; pulse and crossing delays stay unchanged.
        h.assertTrue(ticksRemaining>0,"native roof skylight must settle within20 setup ticks");
        h.runAfterDelay(1,()->awaitCircuitRoofSkylight(h,enemies,ticksRemaining-1,ready));
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void circuit_gap_wall_slab_and_support_loss_fail_closed(GameTestHelper h){
        Fixture f=new Fixture(h);h.assertTrue(f.tap(1),"firstanchor; "+f.diagnostic());
        h.runAfterDelay(1,()->{
            f.aim(12.5);h.setBlock(new BlockPos(6,2,8),Blocks.AIR);h.assertFalse(f.tap(2),"gap between grounded endpoints is refused");
            h.runAfterDelay(1,()->{
                h.setBlock(new BlockPos(6,2,8),Blocks.STONE);h.setBlock(new BlockPos(6,3,8),Blocks.STONE);
                h.assertFalse(f.tap(3),"intervening wall cannot become a circuit");
                h.runAfterDelay(1,()->{
                    h.setBlock(new BlockPos(6,3,8),Blocks.AIR);h.assertTrue(f.tap(4),"flat continuous ground accepted");
                    h.setBlock(new BlockPos(6,2,8),Blocks.STONE_SLAB);ConductiveCircuit.tick(f.p);
                    h.assertFalse(ConductiveCircuit.active(f.p),"support replacement by slab invalidates whole circuit");h.succeed();
                });
            });
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void circuit_teleport_and_fast_displacement_do_not_invent_crossings(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie teleport=f.zombie(6,7),fast=f.zombie(5,9),air=f.zombie(6,11);air.setPos(air.position().add(0,.2,0));
        f.pairAndPulse(()->h.runAfterDelay(1,()->{
            ConductiveCircuit.teleport(new EntityTeleportEvent.TeleportCommand(teleport,teleport.getX()+1,teleport.getY(),teleport.getZ()));
            f.move(teleport,1,0,0);f.move(fast,3,0,0);f.move(air,1,0,0);ConductiveCircuit.tick(f.p);
            h.assertTrue(ConductiveCircuit.attemptedCount(f.p)==0,"teleport reset,unreviewed speed and jumping over wire are excluded");h.succeed();
        }));
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=60)
    public static void circuit_pulse_expires_to_rest_and_no_refresh_from_extra_taps(GameTestHelper h){
        Fixture f=new Fixture(h);f.pairAndPulse(()->{
            h.runAfterDelay(ConductiveCircuitRules.PULSE_TICKS-1,()->{ConductiveCircuit.tick(f.p);h.assertTrue(ConductiveCircuit.phase(f.p)==Phase.ENERGIZED,"pulse remains active on its final eligible tick");
                h.runAfterDelay(1,()->{ConductiveCircuit.tick(f.p);h.assertTrue(ConductiveCircuit.phase(f.p)==Phase.RESTING,"next tick returns to dashed rest");h.succeed();});});
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void circuit_authenticated_mapping_and_cancel_bounds(GameTestHelper h){
        Fixture f=new Fixture(h);long epoch=f.epoch();
        h.assertFalse(ConductiveCircuit.tap(f.p,new HighPowerInput.Input(0,1,f.p.getId()+1,epoch).wire()),"forgedactor");
        h.assertFalse(ConductiveCircuit.tap(f.p,new HighPowerInput.Input(0,1,f.p.getId(),epoch+1).wire()),"forgedepoch");
        String first=f.wire(1);h.assertFalse(ConductiveCircuit.abort(f.p,first),"earlyabort fences delayedstart");h.assertFalse(f.tap(1),"delayedstart retired");
        h.assertTrue(f.tap(2),"fresh firstanchor");h.assertFalse(ConductiveCircuit.abort(f.p,first),"staleabort cannot remove newerpair");
        AbilityStorage.get(f.p).presets.switchTo(1);ConductiveCircuit.tick(f.p);h.assertFalse(ConductiveCircuit.active(f.p),"actual preset change invalidatespair");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void circuit_canceled_native_hurt_consumes_attempt_and_global_persisted_fence(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.zombie(6,7);CircuitDamageGate gate=new CircuitDamageGate(f.p,target);
        f.hooks.add(gate);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(gate);
        f.pairAndPulse(()->h.runAfterDelay(1,()->{
            String before=HighPowerCombat.healthText(target);f.move(target,1,0,0);ConductiveCircuit.tick(f.p);
            h.assertTrue(gate.calls==1&&ConductiveCircuit.attemptedCount(f.p)==1,"one canceled native hurt still reserves exactly one attempt; nested callback cannot reenter");
            h.assertTrue(HighPowerCombat.healthText(target).equals(before),"native cancellation preserves exact HP");
            long reserved=target.getPersistentData().getLong("academy:conductive_circuit_attempt");
            h.assertTrue(reserved==h.getLevel().getServer().overworld().getGameTime(),"target-level global interval reserved before native event");
            Zombie reload=EntityType.ZOMBIE.create(h.getLevel());reload.load(target.saveWithoutId(new CompoundTag()));
            h.assertTrue(reload.getPersistentData().contains("academy:conductive_circuit_attempt")&&reload.getPersistentData().getLong("academy:conductive_circuit_attempt")==reserved,"native entity NBT roundtrip retains global interval");
            ConductiveCircuit.abort(f.p);h.assertTrue(target.getPersistentData().getLong("academy:conductive_circuit_attempt")==reserved,"recall cannot clear target fence");h.succeed();
        }));
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void circuit_world_border_and_unloaded_ground_are_refused_without_loading(GameTestHelper h){
        Fixture f=new Fixture(h);var border=h.getLevel().getWorldBorder();double x=border.getCenterX(),z=border.getCenterZ(),size=border.getSize();
        try{border.setCenter(f.p.getX()+100,f.p.getZ()+100);border.setSize(2);h.assertFalse(f.tap(1),"out-of-border ground denied before lookup or payment");}
        finally{border.setCenter(x,z);border.setSize(size);}
        // Probe loaded-area preflight directly with a confirmed absent distant chunk: it may not request it.
        try{
            int chunkX=1_000_000,chunkZ=1_000_000;h.assertFalse(h.getLevel().hasChunk(chunkX,chunkZ),"unloaded fixture");
            var method=ConductiveCircuit.class.getDeclaredMethod("loadedArea",net.minecraft.server.level.ServerLevel.class,net.minecraft.world.phys.AABB.class);method.setAccessible(true);
            var box=new net.minecraft.world.phys.AABB(chunkX*16.0,64,chunkZ*16.0,chunkX*16.0+2,65,chunkZ*16.0+2);
            h.assertFalse((boolean)method.invoke(null,h.getLevel(),box),"missing chunk refuses scan");h.assertFalse(h.getLevel().hasChunk(chunkX,chunkZ),"read-only validation never loads chunk");
            var permitted=ConductiveCircuit.class.getDeclaredMethod("permitted",ServerPlayer.class,BlockPos.class);permitted.setAccessible(true);
            h.assertFalse((boolean)permitted.invoke(null,f.p,new BlockPos(0,h.getLevel().getMaxBuildHeight(),0)),"outside-height ground denied");
        }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
        h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void circuit_target_interval_refuses_malformed_native_save_data(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.zombie(6,7);String key="academy:conductive_circuit_attempt";long now=h.getLevel().getServer().overworld().getGameTime();
        try{
            var ready=ConductiveCircuit.class.getDeclaredMethod("targetIntervalReady",net.minecraft.world.entity.LivingEntity.class,long.class);ready.setAccessible(true);
            h.assertTrue((boolean)ready.invoke(null,target,now),"absent interval permits first real attempt");
            target.getPersistentData().putString(key,"0");Zombie malformed=EntityType.ZOMBIE.create(h.getLevel());malformed.load(target.saveWithoutId(new CompoundTag()));
            h.assertFalse((boolean)ready.invoke(null,malformed,now),"saved string cannot decode as missing/default zero interval");
            target.getPersistentData().putLong(key,-1);malformed.load(target.saveWithoutId(new CompoundTag()));
            h.assertFalse((boolean)ready.invoke(null,malformed,now),"saved negative interval fails closed");
            target.getPersistentData().putLong(key,now);malformed.load(target.saveWithoutId(new CompoundTag()));
            h.assertFalse((boolean)ready.invoke(null,malformed,now),"valid saved same-tick interval remains reserved");
            h.assertTrue((boolean)ready.invoke(null,malformed,now+10),"valid saved interval matures after ten ticks");
        }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
        h.succeed();
    }
    public static final class CircuitDamageTrace {
        final ServerPlayer owner;final List<Zombie> targets;final Set<UUID> incoming=new HashSet<>(),positivePosts=new HashSet<>();final List<String> events=new ArrayList<>();
        CircuitDamageTrace(ServerPlayer owner,List<Zombie> targets){this.owner=owner;this.targets=targets;}
        @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST,receiveCanceled=true)
        public void incoming(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event){
            if(!targets.contains(event.getEntity()))return;
            events.add("incoming:"+event.getEntity().getUUID()+":"+event.getSource().getMsgId()+":cancel="+event.isCanceled()+":iframe="+event.getEntity().invulnerableTime);
            if(event.getSource().getEntity()==owner)incoming.add(event.getEntity().getUUID());
        }
        @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
        public void post(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event){
            if(!targets.contains(event.getEntity()))return;
            events.add("post:"+event.getEntity().getUUID()+":"+event.getSource().getMsgId()+":damage="+event.getNewDamage());
            if(event.getSource().getEntity()==owner&&event.getNewDamage()>0)positivePosts.add(event.getEntity().getUUID());
        }
        String describe(Map<UUID,BigDecimal> before){
            var result=new StringBuilder("attempts=").append(ConductiveCircuit.attemptedCount(owner)).append(", events=").append(events);
            for(Zombie e:targets)result.append("; id=").append(e.getId()).append('/').append(e.getUUID()).append(" pos=").append(e.position())
                    .append(" before=").append(before.get(e.getUUID())).append(" now=").append(HighPowerCombat.healthText(e)).append(" fire=").append(e.getRemainingFireTicks())
                    .append(" invul=").append(e.invulnerableTime).append(" hurt=").append(e.hurtTime).append(" sky=").append(e.level().canSeeSky(BlockPos.containing(e.getEyePosition())));
            return result.toString();
        }
    }
    public static final class CircuitDamageGate {
        final ServerPlayer owner;final Zombie target;int calls;
        CircuitDamageGate(ServerPlayer owner,Zombie target){this.owner=owner;this.target=target;}
        @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)
        public void incoming(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event){
            if(event.getEntity()!=target)return;calls++;ConductiveCircuit.tick(owner);event.setCanceled(true);
        }
    }
    private static void near(GameTestHelper h,double actual,double expected,String message){h.assertTrue(Math.abs(actual-expected)<1e-8,message+": "+actual+" != "+expected);}
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final List<Zombie> zombies=new ArrayList<>();final List<Object> hooks=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){
            this.h=h;for(int x=0;x<15;x++)for(int z=0;z<15;z++){
                h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
                // NoAI does not disable vanilla random sunlight ignition. A real roof controls that unrelated damage.
                h.setBlock(new BlockPos(x,6,z),Blocks.STONE);
            }
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPCircuitProbe"),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(GameType.SURVIVAL);
            Vec3 at=h.absoluteVec(new Vec3(6.5,3,1.5));p.moveTo(at.x,at.y,at.z,0,0);p.setNoGravity(true);p.getAbilities().instabuild=false;
            h.assertTrue(HighPowerCombat.enroll(p,2),"approvedLV2ledger");var state=AbilityStorage.get(p);state.selectCategory("electromaster");state.setLevel(2);state.learn("forked_arc");state.learn(ConductiveCircuit.ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,ConductiveCircuit.ID,id->id.equals(ConductiveCircuit.ID)),"real learned slot");cp(120);epoch();aim(3.5);
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Zombie zombie(double x,double z){Zombie e=EntityType.ZOMBIE.create(h.getLevel());e.setNoAi(true);e.setNoGravity(true);e.setSilent(true);e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);e.setPos(h.absoluteVec(new Vec3(x,3,z)));
            // Native roof skylight propagation is asynchronous. Ordinary non-armor headgear prevents
            // that unrelated ignition before the first tick, without resetting fire, HP or invulnerability.
            e.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN));
            h.getLevel().addFreshEntity(e);h.assertTrue(HighPowerCombat.enroll(e,2),"reviewed enemy ledger");zombies.add(e);return e;}
        void move(Zombie e,double x,double y,double z){e.setDeltaMovement(Vec3.ZERO);e.move(MoverType.SELF,new Vec3(x,y,z));}
        void pairAndPulse(Runnable active){h.assertTrue(tap(1),"firstanchor; "+diagnostic());h.runAfterDelay(1,()->{aim(12.5);h.assertTrue(tap(2),"secondanchor");h.runAfterDelay(1,()->{h.assertTrue(tap(3),"energize");active.run();});});}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long n){return new HighPowerInput.Input(0,n,p.getId(),epoch()).wire();}boolean tap(long n){return ConductiveCircuit.tap(p,wire(n));}
        void aim(double z){Vec3 target=h.absoluteVec(new Vec3(6.5,3,z)),d=target.subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setYHeadRot(p.getYRot());p.setXRot((float)Math.toDegrees(Math.atan2(-d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
        double cp(){return p.getPersistentData().getCompound("academy:electromaster_prototype").getDouble("cp");}
        void cp(double amount){CompoundTag tag=p.getPersistentData().getCompound("academy:electromaster_prototype");tag.putDouble("cp",amount);p.getPersistentData().put("academy:electromaster_prototype",tag);}
        String diagnostic(){
            var state=AbilityStorage.get(p);Vec3 from=p.getEyePosition(),end=from.add(p.getLookAngle().scale(24));
            var hit=p.serverLevel().clip(new net.minecraft.world.level.ClipContext(from,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            var pos=hit.getBlockPos();var floor=p.serverLevel().getBlockState(pos);var above=p.serverLevel().getBlockState(pos.above());
            boolean rawLoaded=false;String looking="unknown";
            try{
                var load=ConductiveCircuit.class.getDeclaredMethod("loadedArea",net.minecraft.server.level.ServerLevel.class,net.minecraft.world.phys.AABB.class);load.setAccessible(true);rawLoaded=(boolean)load.invoke(null,p.serverLevel(),new net.minecraft.world.phys.AABB(from,end));
                var ground=ConductiveCircuit.class.getDeclaredMethod("lookingGround",ServerPlayer.class);ground.setAccessible(true);looking=String.valueOf(ground.invoke(null,p));
            }catch(ReflectiveOperationException failure){looking=failure.toString();}
            return "eye="+from+", end="+end+", height="+p.serverLevel().getMinBuildHeight()+".."+p.serverLevel().getMaxBuildHeight()+", rawLoaded="+rawLoaded
                    +", hit="+hit.getType()+"/"+hit.getDirection()+"/"+hit.getLocation()+", floor="+floor+", full="+net.minecraft.world.level.block.Block.isShapeFullBlock(floor.getCollisionShape(p.serverLevel(),pos))
                    +", above="+above+", mayInteract="+p.serverLevel().mayInteract(p,pos)+", ground="+looking+", ready="+cn.academy.port.highpower.ElectromasterPrototype.ready(p)
                    +", effectiveLevel="+cn.academy.port.highpower.ElectromasterPrototype.effectiveLevel(p)+", canUse="+state.canUse(ConductiveCircuit.ID)+", activated="+state.activated+", slot="+state.presets.currentSkill(0)+", unsupported="+HighPowerCombat.unsupportedReason(p);
        }
        void close(){if(closed)return;closed=true;hooks.forEach(net.neoforged.neoforge.common.NeoForge.EVENT_BUS::unregister);ConductiveCircuit.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);channel.finishAndReleaseAll();for(Zombie e:zombies)e.discard();}
    }
}
