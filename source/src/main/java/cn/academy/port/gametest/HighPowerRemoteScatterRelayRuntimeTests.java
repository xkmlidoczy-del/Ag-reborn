package cn.academy.port.gametest;

import cn.academy.port.highpower.core.RemoteScatterRelayRules;
import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW253 native contracts. Reflection arranges complex geometry only; every emission enters
 * through authenticated press/release, the closed permit, and Minecraft's real damage pipeline.
 * Genuine finite learning and input/client transport have separate dedicated suites. */
@GameTestHolder("academy_remote_scatter_relay_runtime")
@PrefixGameTestTemplate(false)
public final class HighPowerRemoteScatterRelayRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="remote_scatter_relay_native";
    private static final String ID=RemoteScatterRelay.ID, SKILL="meltdowner.remote_scatter_relay";
    private HighPowerRemoteScatterRelayRuntimeTests() {}
    private static BigDecimal hp(LivingEntity e){return HighPowerCombat.enabled(e)?new BigDecimal(HighPowerCombat.healthText(e)):BigDecimal.valueOf(e.getHealth());}
    private static void exact(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+": "+actual+" != "+expected);}
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-6,why+": "+actual+" != "+expected);}
    private static RemoteScatterRelayGeometry.Vector vector(Vec3 v){return new RemoteScatterRelayGeometry.Vector(v.x,v.y,v.z);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void field(Object owner,String name,Object value){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Object context(ServerPlayer p){try{Field f=RemoteScatterRelay.class.getDeclaredField("CONTEXTS");f.setAccessible(true);return ((Map<?,?>)f.get(null)).get(p.getUUID());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void visible_node_then_visible_terminal_remains_fixed_after_walking_around_cover(GameTestHelper h){
        Fixture f=new Fixture(h);Villager target=f.mob(6,0,8,true);BigDecimal before=hp(target);double cp=f.cp();
        f.face(f.p.getEyePosition().add(0,0,8));BlockPos stop=BlockPos.containing(f.p.getEyePosition().add(0,0,8));f.block(stop);
        f.p.setShiftKeyDown(true);String place=f.input();h.assertTrue(RemoteScatterRelay.start(f.p,place)&&RemoteScatterRelay.release(f.p,place),"actual crouch short press creates a directly visible finite node");
        h.assertTrue(RemoteScatterRelay.plates(f.p).size()==1,"one paid plate is really present");near(h,cp-f.cp(),.01,"LV5 deployment pays once");
        var plates=RemoteScatterRelay.plates(f.p);long placed=RemoteScatterRelay.placedAt(f.p);f.air(stop);f.p.setShiftKeyDown(false);f.pump();
        h.runAtTickTime(2,()->{f.tickPlayer();f.face(target.getBoundingBox().getCenter());double prior=f.cp();h.assertTrue(f.tap(),"first normal short press selects the actual visible endpoint");near(h,prior-f.cp(),0,"terminal selection is free");exact(h,hp(target),before,"selection itself causes no attack");});
        h.runAtTickTime(4,()->{
            Object c=context(f.p);Vec3 terminal=(Vec3)field(c,"terminal");f.p.setPos(f.p.position().add(-2,0,0));f.p.connection.resetPosition();f.face(f.p.getEyePosition().add(-4,0,0));
            Vec3 cover=f.p.getEyePosition().lerp(target.getBoundingBox().getCenter(),.5);f.block(BlockPos.containing(cover));double prior=f.cp();
            h.assertTrue(f.tap(),"next normal short press fires the existing around-cover route after movement");
            exact(h,before.subtract(hp(target)),RemoteScatterRelayRules.damage(HighPowerGrowth.values(f.p).damage()),"one final hit owns all 2.4D");near(h,prior-f.cp(),.2,"exactly one LV5 shot payment");
            h.assertTrue(field(c,"terminal").equals(terminal)&&RemoteScatterRelay.plates(f.p).equals(plates)&&RemoteScatterRelay.placedAt(f.p)==placed,"walking and firing do not move endpoint, nodes or expiry");
            f.receipt(1);h.assertTrue(h.getLevel().getBlockState(BlockPos.containing(cover)).is(Blocks.STONE),"cover is never destroyed");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void one_to_four_ordered_nodes_deliver_one_total_budget_and_no_forgeable_source(GameTestHelper h){
        for(int count=1;count<=4;count++)try(Fixture f=new Fixture(h)){
            Villager target=f.mob(1.5,0,4,true);List<Vec3> centers=new ArrayList<>();
            for(int i=0;i<count;i++)centers.add(f.p.position().add(i%2==0?0:3,1.2,7+i*3));
            f.route(centers,target.getBoundingBox().getCenter());Observation hit=f.observe(target);BigDecimal before=hp(target);double cp=f.cp();
            h.assertTrue(f.fire(),"genuine authenticated route with "+count+" nodes commits");
            exact(h,before.subtract(hp(target)),RemoteScatterRelayRules.damage(HighPowerGrowth.values(f.p).damage()),"node count cannot multiply the total 2.4D");near(h,cp-f.cp(),.2,"one total shot payment");
            var receipt=f.receipt(count);h.assertTrue(hit.posts==1,"only terminal body invokes one native damage completion");assertSource(h,hit.source,f.p,receipt.path().getLast().start());
            h.assertFalse(HighPowerCombat.attack(f.p,SKILL,target,BigDecimal.ONE),"generic skill string cannot forge private relay source");
            h.assertFalse(HighPowerCombat.attackRemoteScatterRelay(null),"null permit never authorizes a beam");
            for(var constructor:RemoteScatterRelay.EmissionPermit.class.getDeclaredConstructors())h.assertTrue(Modifier.isPrivate(constructor.getModifiers()),"permit construction stays private");
            BigDecimal replayHp=hp(target),replayShield=HighPowerCombat.shield(target);double replayCp=f.cp();boolean[] canceledAtIncoming={false};
            f.hook(new Object(){@SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void incoming(LivingIncomingDamageEvent event){if(event.getEntity()==target&&event.getSource()==hit.source)canceledAtIncoming[0]=event.isCanceled();}});
            // Declared timer fixture: reproduce the exact successful source and native amount
            // with no hurt-cooldown refusal available to conceal missing source ownership.
            target.invulnerableTime=0;target.hurtTime=0;
            h.assertFalse(target.hurt(hit.source,hit.incoming),"captured successful native source cannot be reused outside its closed emission");
            h.assertTrue(canceledAtIncoming[0],"closed source ownership cancels the real incoming event independently of iframes");
            exact(h,hp(target),replayHp,"successful source replay cannot debit HP again");exact(h,HighPowerCombat.shield(target),replayShield,"successful source replay cannot debit S");near(h,f.cp(),replayCp,"successful source replay cannot debit CP");h.assertTrue(hit.posts==1,"source replay never reaches a second native completion");
        }ordinaryLethalOutcomes(h);h.succeed();
    }

    /** Both ordinary lethal outcomes use real hurt/death dispatch. A canceled death retains the
     * pinned native outcome, established with a paired ordinary PLAYER_ATTACK control. */
    private static void ordinaryLethalOutcomes(GameTestHelper h){
        for(boolean cancelDeath:List.of(false,true))try(Fixture f=new Fixture(h)){
            Villager target=f.basic(false),control=f.mob(10,0,4,false);target.setHealth(1);control.setHealth(1);f.discount(target);Observation hit=f.observe(target);int[] deaths={0,0};
            f.hook(new Object(){@SubscribeEvent public void death(LivingDeathEvent event){if(event.getEntity()==target){deaths[0]++;if(cancelDeath)event.setCanceled(true);}else if(event.getEntity()==control){deaths[1]++;if(cancelDeath)event.setCanceled(true);}}});
            Vec3 node=RemoteScatterRelay.plates(f.p).getLast().center();DamageSource nativeSource=new DamageSource(f.p.damageSources().playerAttack(f.p).typeHolder(),f.p,f.p,node.add(control.position().subtract(target.position())));
            h.assertTrue(control.hurt(nativeSource,5),"paired native low-HP control reaches its actual death decision");double cp=f.cp();String wire=f.pending;
            h.assertTrue(f.fire(),"real low-HP target outcome does not revoke an already committed native hit");near(h,cp-f.cp(),.2,"ordinary lethal or death-canceled hit pays once without refund");
            h.assertTrue(deaths[0]==1&&deaths[1]==1&&hit.posts==1,"one actual native damage completion and one death event per target");
            h.assertTrue(target.getHealth()==control.getHealth()&&target.isAlive()==control.isAlive()&&target.isRemoved()==control.isRemoved(),"death cancellation preserves the matching pinned native outcome instead of assuming resurrection");
            var receipt=f.receipt(1);h.assertTrue(receipt.status()==RemoteScatterRelay.DAMAGED&&target.getUUID().equals(receipt.victim()),"successful ordinary lethal damage is confirmed as DAMAGED with the actual victim");
            if(!cancelDeath)h.assertTrue(!target.isAlive()&&target.getHealth()==0,"ordinary unvetoed native lethal damage really kills the target");
            float health=target.getHealth();BigDecimal shield=HighPowerCombat.shield(target);h.assertFalse(RemoteScatterRelay.release(f.p,wire)||RemoteScatterRelay.start(f.p,wire),"retired physical input cannot replay either native death outcome");
            near(h,cp-f.cp(),.2,"death outcome never refunds or repeats committed payment");h.assertTrue(target.getHealth()==health&&deaths[0]==1&&hit.posts==1,"replay neither repeats native damage nor emits another death event");exact(h,HighPowerCombat.shield(target),shield,"death outcome replay cannot touch S");
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void five_native_legs_can_exceed_thirty_two_total_without_exceeding_any_leg(GameTestHelper h){
        try(Fixture f=new Fixture(h)){
            Vec3 start=RemoteScatterRelay.hand(f.p);List<Vec3> centers=List.of(start.add(0,0,30),start.add(30,0,30),start.add(30,30,30),start.add(30,30,0));
            Vec3 terminal=start.add(0,30,0);Villager target=f.mobAt(terminal.add(0,-.9,0),true);f.route(centers,terminal);double cp=f.cp();
            h.assertTrue(f.fire(),"real native five-leg route reaches a remote terminal body");var receipt=f.receipt(4);
            double length=receipt.path().stream().mapToDouble(s->s.start().distanceTo(s.end())).sum();
            h.assertTrue(length>140&&length<=160&&receipt.path().stream().allMatch(s->s.start().distanceTo(s.end())<=32),"each native leg is bounded separately and total remains within 160");near(h,cp-f.cp(),.2,"long route has one shot cost");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void an_overrange_first_middle_or_final_leg_is_free_and_never_shortened(GameTestHelper h){
        for(int leg=0;leg<3;leg++)try(Fixture f=new Fixture(h)){
            Villager target=f.mob(1.5,0,4,true);Vec3 hand=RemoteScatterRelay.hand(f.p),a=hand.add(0,0,5),b=hand.add(5,0,5),end=target.getBoundingBox().getCenter();
            if(leg==0)a=hand.add(0,0,32.001);if(leg==1)b=a.add(32.001,0,0);if(leg==2)end=b.add(0,0,32.001);
            f.route(List.of(a,b),end);BigDecimal before=hp(target);double cp=f.cp();
            h.assertFalse(f.fire(),"overrange leg "+leg+" refuses before damage");near(h,cp-f.cp(),0,"invalid geometry spends no CP");exact(h,hp(target),before,"controller cannot truncate route to acquire target");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void intermediate_living_or_unsupported_bodies_stop_without_damage_or_payment(GameTestHelper h){
        for(boolean unsupported:List.of(false,true))try(Fixture f=new Fixture(h)){
            Villager target=f.basic(true);Vec3 middle=RemoteScatterRelay.hand(f.p).lerp(RemoteScatterRelay.plates(f.p).getFirst().center(),.5);
            Entity blocker;
            if(unsupported){var stand=EntityType.ARMOR_STAND.create(h.getLevel());stand.setNoGravity(true);stand.setPos(middle.add(0,-1,0));h.getLevel().addFreshEntity(stand);f.entities.add(stand);blocker=stand;h.assertFalse(HighPowerCombat.supported(stand),"actual unsupported native body");}
            else blocker=f.mobAt(middle.add(0,-1,0),true);
            BigDecimal before=hp(target),body=hp((LivingEntity)blocker);double cp=f.cp();
            h.assertFalse(f.fire(),"a body on an intermediate leg blocks the whole beam");exact(h,hp(target),before,"terminal target is not acquired through body");exact(h,hp((LivingEntity)blocker),body,"intermediate body is never a victim");near(h,cp-f.cp(),0,"blocked opportunity remains free");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_solid_wins_exact_terminal_body_tie(GameTestHelper h){
        try(Fixture f=new Fixture(h)){
            Vec3 hand=RemoteScatterRelay.hand(f.p),node=hand.add(0,0,5);BlockPos at=BlockPos.containing(node.add(0,0,5));
            Villager target=f.mobAt(Vec3.atBottomCenterOf(at),true);target.setBoundingBox(new AABB(at));f.block(at);f.route(List.of(node),Vec3.atCenterOf(at));double cp=f.cp();BigDecimal before=hp(target);
            h.assertFalse(f.fire(),"coincident inflated solid/body boundaries give the solid priority");exact(h,hp(target),before,"no damage through tied solid");near(h,cp-f.cp(),0,"solid/body tie remains free");h.assertTrue(h.getLevel().getBlockState(at).is(Blocks.STONE),"trace does not break tied solid");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void protected_target_and_unloaded_native_prefix_refuse_without_forcing_chunks(GameTestHelper h){
        try(Fixture f=new Fixture(h)){
            Villager target=f.basic(true);target.getPersistentData().putBoolean("academy:protected",true);double cp=f.cp();BigDecimal before=hp(target);int loaded=h.getLevel().getChunkSource().getLoadedChunksCount();
            h.assertFalse(f.fire(),"protected final target is not eligible");exact(h,hp(target),before,"protected target has no damage");near(h,cp-f.cp(),0,"permission failure is free");
            // Moving an added native player itself loads its center chunk. Keep the owner
            // registered in place and exercise the production read-only prefix guard directly.
            Vec3 absent=new Vec3(1_000_000,80,1_000_000);
            h.assertFalse(h.getLevel().hasChunk(62500,62500),"native prefix starts genuinely unloaded");
            try{var trace=RemoteScatterRelay.class.getDeclaredMethod("nativeTrace",ServerPlayer.class,Vec3.class,Vec3.class);trace.setAccessible(true);Object hit=trace.invoke(null,f.p,absent,absent.add(0,0,8));
                h.assertTrue(!(Boolean)field(hit,"permitted")&&!(Boolean)field(hit,"contact")&&field(hit,"point").equals(absent),"first missing native prefix refuses before any block or entity collision lookup");
            }catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}
            h.assertFalse(h.getLevel().hasChunk(62500,62500),"denied native prefix remains unloaded");
            h.assertTrue(h.getLevel().getChunkSource().getLoadedChunksCount()==loaded,"neither refusal forces a chunk");near(h,cp-f.cp(),0,"missing native prefix cannot spend CP");exact(h,hp(target),before,"missing native prefix cannot damage the protected body");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_border",timeoutTicks=10)
    public static void each_remote_leg_obeys_native_world_border(GameTestHelper h){
        try(Fixture f=new Fixture(h)){
            Villager target=f.basic(true);var border=h.getLevel().getWorldBorder();double x=border.getCenterX(),z=border.getCenterZ(),size=border.getSize(),cp=f.cp();BigDecimal before=hp(target);
            try{border.setCenter(f.p.getX(),f.p.getZ());border.setSize(12);h.assertFalse(f.fire(),"remote node outside native border invalidates route");near(h,cp-f.cp(),0,"border refusal is free");exact(h,hp(target),before,"no outside-border damage");}
            finally{border.setCenter(x,z);border.setSize(size);}
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void callback_mutations_at_every_native_stage_cancel_before_any_shot_payment(GameTestHelper h){
        for(int stage=0;stage<4;stage++)for(int mutation=0;mutation<8;mutation++)try(Fixture f=new Fixture(h)){
            Villager target=f.basic(mutation%2==0);f.discount(target);Mutation gate=new Mutation(f,target,stage,mutation);f.hook(gate);BigDecimal before=hp(target);double cp=f.cp();
            h.assertFalse(f.fire(),"native callback veto is free at stage "+stage+", mutation "+mutation);h.assertTrue(gate.called,"requested real callback executed");
            exact(h,hp(target),before,"frozen route veto prevents exact and ordinary HP loss");near(h,cp-f.cp(),mutation==2?.01:0,"only an intentional callback CP write remains, never a shot debit");
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(ID,0)==0&&AbilityStorage.get(f.p).exp(ID)==0,"canceled opportunity creates no cooldown or productive mastery");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void skill_incoming_pre_and_late_absorption_zeroes_are_free(GameTestHelper h){
        for(boolean enrolled:List.of(false,true))for(int mode=0;mode<5;mode++)try(Fixture f=new Fixture(h)){
            Villager target=f.basic(enrolled);f.discount(target);Zero gate=new Zero(target,mode);f.hook(gate);double cp=f.cp();BigDecimal before=hp(target);
            h.assertFalse(f.fire(),"zero/cancel at native stage "+mode+" cannot commit payment");h.assertTrue(gate.called,"native zero hook is reached");near(h,cp-f.cp(),0,"zero or canceled attempt is free");exact(h,hp(target),before,"zero has no exact or ordinary damage");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void synthetic_early_post_cannot_commit_cp_hp_or_authorize_a_later_canceled_ray(GameTestHelper h){
        for(boolean enrolled:List.of(false,true))for(boolean cancel:List.of(false,true))try(Fixture f=new Fixture(h)){
            Villager target=f.basic(enrolled);f.discount(target);double cp=f.cp();BigDecimal before=hp(target);boolean[] called={false};
            f.hook(new Object(){@SubscribeEvent(priority=EventPriority.LOWEST)public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()!=target)return;e.addReductionModifier(DamageContainer.Reduction.ABSORPTION,(container,reduction)->{
                called[0]=true;NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(target,container));
                exact(h,hp(target),before,"same-source synthetic Post cannot debit HP before final native guard");near(h,cp-f.cp(),0,"same-source synthetic Post cannot prepay CP");
                if(cancel)RemoteScatterRelay.abort(f.p);return reduction;});}});
            h.assertTrue(f.fire()!=cancel&&called[0],"only valid genuine final native commit may pay");near(h,cp-f.cp(),cancel?0:.2,"one debit only after real final seam");exact(h,hp(target),cancel?before:before.subtract(BigDecimal.valueOf(5)),"early notification neither duplicates nor rescues terminal damage");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void throwing_native_callbacks_retire_unknown_transactions_without_refund_or_permit_replay(GameTestHelper h){
        for(boolean enrolled:List.of(false,true))for(boolean afterPayment:List.of(false,true))try(Fixture f=new Fixture(h)){
            Villager target=f.basic(enrolled);f.discount(target);Object capturedContext=context(f.p);String originalInput=f.pending;BigDecimal before=hp(target);double cp=f.cp();int[] calls={0};
            RemoteScatterRelay.EmissionPermit[] capturedPermit={null};
            f.hook(new Object(){
                private void fail(){
                    calls[0]++;capturedPermit[0]=(RemoteScatterRelay.EmissionPermit)field(capturedContext,"emitting");
                    h.assertTrue(capturedPermit[0]!=null,"throwing callback belongs to an actual live closed emission");
                    near(h,cp-f.cp(),afterPayment?.2:0,"callback observes the correct native side of deferred payment");
                    throw new IllegalStateException("Declared NEW253 native callback exception fixture");
                }
                @SubscribeEvent(priority=EventPriority.LOWEST)public void skill(SkillAttackEvent event){if(!afterPayment&&event.player==f.p&&event.target==target&&event.skill.equals(SKILL))fail();}
                @SubscribeEvent(priority=EventPriority.LOWEST)public void post(LivingDamageEvent.Post event){if(afterPayment&&event.getEntity()==target&&event.getSource().getEntity()==f.p)fail();}
            });
            h.assertFalse(f.fire(),"exception reports an unconfirmed transaction instead of claiming successful fire");h.assertTrue(calls[0]==1,"one real attack callback throws exactly once");
            near(h,cp-f.cp(),afterPayment?.2:0,"pre-payment exception is free; post-payment exception never refunds committed CP");
            BigDecimal after=hp(target),loss=before.subtract(after);h.assertTrue(loss.signum()>=0&&loss.compareTo(BigDecimal.valueOf(5))<=0,"uncertain native outcome never exceeds the single five-point test allocation");
            if(!afterPayment)exact(h,after,before,"exception before native damage leaves HP untouched");
            h.assertFalse(RemoteScatterRelay.active(f.p)||RemoteScatterRelay.holding(f.p),"unknown outcome retires the entire emitting context");
            var receipt=(RemoteScatterRelay.Receipt)field(capturedContext,"receipt");h.assertTrue(receipt!=null&&receipt.status()==RemoteScatterRelay.REFUSED&&receipt.victim()==null,"retired transaction records refusal without claiming an unknown damage result");
            h.assertFalse(RemoteScatterRelay.start(f.p,originalInput)||RemoteScatterRelay.release(f.p,originalInput),"original authenticated input cannot replay the retired transaction");
            h.assertFalse(HighPowerCombat.attackRemoteScatterRelay(capturedPermit[0]),"actual consumed permit cannot replay after exception retirement");
            near(h,cp-f.cp(),afterPayment?.2:0,"input and permit replay attempts cannot debit again");exact(h,hp(target),after,"replay attempts cannot repeat an uncertain native damage result");h.assertTrue(calls[0]==1,"retired permit never reenters native callbacks");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void reentry_is_rejected_without_poisoning_the_next_authenticated_input(GameTestHelper h){
        Fixture f=new Fixture(h);Villager target=f.basic(true);f.discount(target);boolean[] called={false},accepted={true};String reentry=f.input();
        f.hook(new Object(){@SubscribeEvent public void attack(SkillAttackEvent e){if(e.player==f.p&&e.target==target&&e.skill.equals(SKILL)){called[0]=true;accepted[0]=RemoteScatterRelay.start(f.p,reentry);}}});
        double cp=f.cp();h.assertTrue(f.fire()&&called[0]&&!accepted[0],"recursive authentic start cannot replace the emitting context");near(h,cp-f.cp(),.2,"reentry cannot duplicate payment");f.pump();
        h.runAtTickTime(2,()->{f.tickPlayer();h.assertTrue(RemoteScatterRelay.start(f.p,reentry),"rejected recursion did not retire the next real input");RemoteScatterRelay.abort(f.p,reentry);h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void full_native_absorption_pays_once_while_equal_hit_iframe_rejection_is_free(GameTestHelper h){
        Fixture f=new Fixture(h);Villager target=f.basic(true);f.discount(target);target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,2));target.setAbsorptionAmount(10);BigDecimal before=hp(target);double cp=f.cp();Observation hit=f.observe(target);
        h.assertTrue(f.fire(),"positive native hit entirely absorbed still commits one real attempt");near(h,cp-f.cp(),.2,"full absorption pays once");exact(h,hp(target),before,"full absorption preserves exact HP");near(h,target.getAbsorptionAmount(),5,"native absorption is spent once");h.assertTrue(hit.posts==1,"one real native completion");f.pump();
        h.runAtTickTime(2,()->{f.tickPlayer();double next=f.cp();h.assertFalse(f.fire(),"native equal-hit iframe refuses second opportunity before payment");near(h,next-f.cp(),0,"iframe rejection costs no CP");near(h,target.getAbsorptionAmount(),5,"iframe refusal cannot spend absorption");h.assertTrue(hit.posts==1,"iframe refusal has no second native completion");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void held_shield_uses_last_node_side_and_full_block_is_free(GameTestHelper h){
        List<Fixture> fixtures=new ArrayList<>();List<Villager> targets=new ArrayList<>();List<Observation> hits=new ArrayList<>();
        for(int i=0;i<4;i++){
            Fixture f=new Fixture(h,i);fixtures.add(f);Villager target=f.basic(i<2);targets.add(target);f.discount(target);hits.add(f.observe(target));
            face(target,i%2==0?0:180);target.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));target.startUsingItem(InteractionHand.OFF_HAND);
        }
        h.runAtTickTime(7,()->{
            for(int i=0;i<4;i++){
                Fixture f=fixtures.get(i);Villager target=targets.get(i);face(target,i%2==0?0:180);h.assertTrue(target.isBlocking(),"real held-shield raising delay elapsed");double cp=f.cp();BigDecimal before=hp(target);boolean blocks=i%2==0;
                h.assertTrue(f.fire()!=blocks,"final node hemisphere controls native held shield");near(h,cp-f.cp(),blocks?0:.2,"full native held shield is free, open side pays once");exact(h,hp(target),blocks?before:before.subtract(BigDecimal.valueOf(5)),"native side decision applies to exact and ordinary bodies");
                h.assertTrue(hits.get(i).blocked==blocks,"real native shield event confirms side");assertSource(h,hits.get(i).source,f.p,RemoteScatterRelay.plates(f.p).getLast().center());
            }h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_armor_resistance_enchantment_and_absorption_match_ordinary_control(GameTestHelper h){
        for(boolean enrolled:List.of(false,true))try(Fixture f=new Fixture(h)){
            Villager target=f.basic(enrolled),control=f.mob(10,0,4,false);defenses(h,target);defenses(h,control);f.discount(target);Observation hit=f.observe(target),nativeHit=f.observe(control);BigDecimal before=hp(target);
            Vec3 origin=RemoteScatterRelay.plates(f.p).getLast().center();DamageSource source=new DamageSource(f.p.damageSources().playerAttack(f.p).typeHolder(),f.p,f.p,origin.add(control.position().subtract(target.position())));
            h.assertTrue(control.hurt(source,5)&&f.fire(),"paired ordinary and closed relay hits use actual native damage");
            h.assertTrue(hit.posts==1&&nativeHit.posts==1&&hit.armor==nativeHit.armor&&hit.effects==nativeHit.effects&&hit.enchantments==nativeHit.enchantments&&hit.absorption==nativeHit.absorption,"all native reducers agree with matching control");
            if(enrolled){BigDecimal loss=DamageMitigation.mitigate(BigDecimal.valueOf(5),new DefenseProfile(BigDecimal.valueOf(target.getArmorValue()),BigDecimal.valueOf(target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)),1,4)).subtract(new BigDecimal(".5"));exact(h,before.subtract(hp(target)),loss,"exact ledger retains final native residual");}
            else h.assertTrue(target.getHealth()==control.getHealth(),"ordinary body retains native float damage");
            near(h,target.getAbsorptionAmount(),0,"one native absorption debit");assertSource(h,hit.source,f.p,origin);
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=515)
    public static void measure_hold_preserves_fixed_array_and_first_placement_deadline(GameTestHelper h){
        Fixture f=new Fixture(h);f.basic(true);Object c=context(f.p);field(c,"hold",null);f.pending=null;var plates=RemoteScatterRelay.plates(f.p);Vec3 terminal=(Vec3)field(c,"terminal");long first=RemoteScatterRelay.placedAt(f.p);f.pump();
        h.runAtTickTime(2,()->{f.p.setShiftKeyDown(false);f.pending=f.input();h.assertTrue(RemoteScatterRelay.start(f.p,f.pending),"normal measurement hold begins");f.p.setShiftKeyDown(true);});
        h.runAtTickTime(15,()->{double cp=f.cp();h.assertTrue(RemoteScatterRelay.release(f.p,f.pending),"normal twelve-tick hold only measures despite later crouching");f.pending=null;near(h,cp-f.cp(),0,"measurement is free");h.assertTrue(RemoteScatterRelay.plates(f.p).equals(plates)&&field(c,"terminal").equals(terminal)&&RemoteScatterRelay.placedAt(f.p)==first,"measure preserves every fixed point and original deadline");});
        h.runAtTickTime(RemoteScatterRelayRules.LIFETIME-2,()->h.assertTrue(RemoteScatterRelay.active(f.p),"fixed route is alive before its immutable deadline"));
        h.runAtTickTime(RemoteScatterRelayRules.LIFETIME+5,()->{h.assertFalse(RemoteScatterRelay.active(f.p),"actual server ticks expire the original route lifetime");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void crouch_hold_clears_without_refund_and_session_replay_cannot_restore_it(GameTestHelper h){
        Fixture f=new Fixture(h);f.basic(true);field(context(f.p),"hold",null);f.pending=null;f.pump();
        h.runAtTickTime(2,()->{f.p.setShiftKeyDown(true);f.pending=f.input();h.assertTrue(RemoteScatterRelay.start(f.p,f.pending),"crouch clear hold begins");f.p.setShiftKeyDown(false);});
        h.runAtTickTime(15,()->{String old=f.pending;double cp=f.cp();h.assertTrue(RemoteScatterRelay.release(f.p,old)&&!RemoteScatterRelay.active(f.p),"captured crouch hold clears without firing");near(h,cp-f.cp(),0,"clear neither pays nor refunds");h.assertFalse(RemoteScatterRelay.start(f.p,old),"released input cannot recreate geometry");String stale=f.input();LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();h.assertFalse(RemoteScatterRelay.start(f.p,stale),"retired owner epoch never revives saved array");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void preset_interference_logout_death_and_relog_retire_ephemeral_geometry(GameTestHelper h){
        for(int mode=0;mode<5;mode++)try(Fixture f=new Fixture(h)){
            f.basic(true);double cp=f.cp();
            switch(mode){case 0->AbilityStorage.get(f.p).presets.edit(0,0,"",id->true);case 1->AbilityStorage.get(f.p).interfering=true;case 2->RemoteScatterRelay.logout(new PlayerEvent.PlayerLoggedOutEvent(f.p));case 3->f.p.setHealth(0);default->{AbilityStorage.save(f.p);var saved=f.p.saveWithoutId(new net.minecraft.nbt.CompoundTag());RemoteScatterRelay.remove(f.p);AbilityStorage.remove(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);f.p.load(saved);f.p.connection.resetPosition();f.epoch();}}
            RemoteScatterRelay.serverTick(new ServerTickEvent.Post(()->true,h.getLevel().getServer()));h.assertFalse(RemoteScatterRelay.active(f.p),"native lifecycle retires transient route "+mode);near(h,cp-f.cp(),0,"lifecycle never refunds or refills CP");
        }h.succeed();
    }

    private static void face(LivingEntity e,float yaw){e.setYRot(yaw);e.setYHeadRot(yaw);e.yBodyRot=yaw;e.setXRot(0);}
    private static void defenses(GameTestHelper h,LivingEntity e){
        ItemStack helmet=new ItemStack(Items.DIAMOND_HELMET);helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),4);helmet.set(DataComponents.UNBREAKABLE,new Unbreakable(true));e.setItemSlot(EquipmentSlot.HEAD,helmet);e.getAttribute(Attributes.ARMOR).setBaseValue(5);e.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0));e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,0));e.setAbsorptionAmount(.5F);
    }
    private static void assertSource(GameTestHelper h,DamageSource source,ServerPlayer caster,Vec3 origin){
        h.assertTrue(source!=null&&source.is(DamageTypes.PLAYER_ATTACK)&&source.getEntity()==caster&&source.getDirectEntity()==caster&&origin.equals(source.getSourcePosition())&&origin.equals(source.sourcePositionRaw()),"native source attributes caster and actual final node origin");
        h.assertFalse(source.is(DamageTypeTags.IS_PROJECTILE)||source.is(DamageTypeTags.BYPASSES_ARMOR)||source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)||source.is(DamageTypeTags.BYPASSES_EFFECTS)||source.is(DamageTypeTags.BYPASSES_RESISTANCE)||source.is(DamageTypeTags.BYPASSES_SHIELD)||source.is(DamageTypeTags.BYPASSES_COOLDOWN)||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY),"relay adds no native defense bypass");
    }
    public static final class Observation {
        final LivingEntity target;DamageSource source;int posts;boolean blocked;float incoming,armor,effects,enchantments,absorption;
        Observation(LivingEntity target){this.target=target;}
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target){source=e.getSource();incoming=e.getAmount();}}
        @SubscribeEvent public void shield(LivingShieldBlockEvent e){if(e.getEntity()==target)blocked=e.getBlocked();}
        @SubscribeEvent public void post(LivingDamageEvent.Post e){if(e.getEntity()!=target)return;posts++;armor=e.getReduction(DamageContainer.Reduction.ARMOR);effects=e.getReduction(DamageContainer.Reduction.MOB_EFFECTS);enchantments=e.getReduction(DamageContainer.Reduction.ENCHANTMENTS);absorption=e.getReduction(DamageContainer.Reduction.ABSORPTION);}
    }
    public static final class Mutation {
        final Fixture f;final LivingEntity target;final int stage,mutation;boolean called;
        Mutation(Fixture f,LivingEntity target,int stage,int mutation){this.f=f;this.target=target;this.stage=stage;this.mutation=mutation;}
        void change(){if(called)return;called=true;switch(mutation){case 0->target.setPos(target.position().add(0,4,0));case 1->f.p.setPos(f.p.position().add(0,4,0));case 2->f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp",f.cp()-.01);case 3->field(context(f.p),"terminal",((Vec3)field(context(f.p),"terminal")).add(1,0,0));case 4->f.block(BlockPos.containing(RemoteScatterRelay.hand(f.p).lerp(RemoteScatterRelay.plates(f.p).getFirst().center(),.5)));case 5->RemoteScatterRelay.abort(f.p);case 6->target.getPersistentData().putBoolean("academy:protected",true);default->target.setInvulnerable(true);}}
        @SubscribeEvent public void skill(SkillAttackEvent e){if(stage==0&&e.target==target&&e.skill.equals(SKILL))change();}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()!=target)return;if(stage==1)change();if(stage==3)e.addReductionModifier(DamageContainer.Reduction.ABSORPTION,(container,reduction)->{change();return reduction;});}
        @SubscribeEvent public void pre(LivingDamageEvent.Pre e){if(stage==2&&e.getEntity()==target)change();}
    }
    public static final class Zero {
        final LivingEntity target;final int mode;boolean called;Zero(LivingEntity target,int mode){this.target=target;this.mode=mode;}
        @SubscribeEvent public void skill(SkillAttackEvent e){if(mode==0&&e.target==target&&e.skill.equals(SKILL)){called=true;e.amount=0;}}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()!=target)return;if(mode==1){called=true;e.setCanceled(true);}if(mode==2){called=true;e.setAmount(0);}if(mode==4)e.addReductionModifier(DamageContainer.Reduction.ABSORPTION,(container,reduction)->{called=true;container.setNewDamage(0);return 0;});}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void pre(LivingDamageEvent.Pre e){if(mode==3&&e.getEntity()==target){called=true;e.setNewDamage(0);}}
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final List<BlockPos> blocks=new ArrayList<>();
        long serial;String pending;boolean closed,pumping;long lastPlayerTick=-1;
        Fixture(GameTestHelper h){this(h,0);}
        Fixture(GameTestHelper h,int offset){
            this.h=h;var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"RS"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);Vec3 at=h.absoluteVec(new Vec3(5.5+offset*7,18,5.5));p.moveTo(at.x,at.y,at.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();
            h.assertTrue(HighPowerCombat.enroll(p,5),"real authenticated LV5 caster");var state=AbilityStorage.get(p);state.selectCategory("meltdowner");state.setLevel(5);state.learn(DualPoint.ID);state.learn(FoldedMirrorArray.ID);state.learn(ID);state.activated=true;state.overloadFine=true;h.assertTrue(state.presets.edit(0,0,ID,id->true),"bind native NEW253");HighPowerResources.initializeBudget(p);epoch();
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Villager mob(double x,double y,double z,boolean enrolled){return mobAt(p.position().add(x,y,z),enrolled);}
        Villager mobAt(Vec3 at,boolean enrolled){var target=new Villager(EntityType.VILLAGER,h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setPos(at);h.getLevel().addFreshEntity(target);entities.add(target);if(enrolled)h.assertTrue(HighPowerCombat.enroll(target,5),"native enrolled exact target");return target;}
        Villager basic(boolean enrolled){Villager target=mob(1.5,0,4,enrolled);route(List.of(p.position().add(0,1.2,8)),target.getBoundingBox().getCenter());return target;}
        void route(List<Vec3> centers,Vec3 endpoint){
            p.setShiftKeyDown(false);pending=input();h.assertTrue(RemoteScatterRelay.start(p,pending),"authentic native press owns route fixture");Object c=context(p);List<RemoteScatterRelayGeometry.Node> nodes=new ArrayList<>();for(int i=0;i<centers.size();i++)nodes.add(new RemoteScatterRelayGeometry.Node(i,vector(centers.get(i)),new RemoteScatterRelayGeometry.Vector(0,0,-1)));field(c,"plates",List.copyOf(nodes));field(c,"terminal",endpoint);field(c,"placedAt",p.server.overworld().getGameTime());
        }
        boolean fire(){if(pending==null)return tap();String wire=pending;pending=null;return RemoteScatterRelay.release(p,wire);}
        boolean tap(){p.setShiftKeyDown(false);String wire=input();return RemoteScatterRelay.start(p,wire)&&RemoteScatterRelay.release(p,wire);}
        void face(Vec3 point){Vec3 d=point.subtract(p.getEyePosition());p.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String input(){return new HighPowerInput.Input(0,++serial,p.getId(),epoch()).wire();}
        double cp(){return HighPowerResources.cp(p);}
        void block(BlockPos at){h.getLevel().setBlockAndUpdate(at,Blocks.STONE.defaultBlockState());blocks.add(at);}
        void air(BlockPos at){h.getLevel().setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());}
        void tickPlayer(){long tick=p.server.overworld().getGameTime();if(!closed&&lastPlayerTick!=tick){lastPlayerTick=tick;p.doTick();p.setDeltaMovement(Vec3.ZERO);}}
        void pump(){if(pumping)return;pumping=true;h.onEachTick(this::tickPlayer);}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void discount(LivingEntity target){hook(new Object(){@SubscribeEvent(priority=EventPriority.HIGHEST)public void attack(SkillAttackEvent e){if(e.target==target&&e.skill.equals(SKILL))e.amount=5;}});}
        Observation observe(LivingEntity target){Observation hit=new Observation(target);hook(hit);return hit;}
        RemoteScatterRelay.Receipt receipt(int nodes){var r=RemoteScatterRelay.receipt(p);h.assertTrue(r!=null&&r.crossings()==nodes&&r.path().size()==nodes+1,"receipt contains all ordered nodes and exactly one more leg");for(int i=1;i<r.path().size();i++)h.assertTrue(r.path().get(i-1).end().equals(r.path().get(i).start()),"native path remains continuous");h.assertTrue(r.path().stream().mapToDouble(s->s.start().distanceTo(s.end())).sum()<=160,"native receipt stays within whole-path budget");return r;}
        public void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);blocks.forEach(this::air);RemoteScatterRelay.remove(p);AbilityStorage.remove(p);LegacySingleKeyOwnerEpoch.forget(p);p.server.getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}
