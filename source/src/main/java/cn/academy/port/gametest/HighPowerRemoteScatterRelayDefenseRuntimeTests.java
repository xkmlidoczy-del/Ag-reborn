package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static cn.academy.port.gametest.ZeroGapArmorNativeFixture.*;

/** NEW253's explicit finite MD defense allowlists, through the closed emission and real hurt.
 * Reuses existing connected-player/login/startup/resource fixtures; reflection fixes relay nodes
 * only and never fabricates a permit, native contact, absorption, or damage completion. */
@GameTestHolder("academy_remote_scatter_relay_runtime")
@PrefixGameTestTemplate(false)
public final class HighPowerRemoteScatterRelayDefenseRuntimeTests {
    private static final String TEMPLATE="highpower_empty",BATCH="remote_scatter_relay_defenses";
    private static final BigDecimal FIVE=BigDecimal.valueOf(5);
    private HighPowerRemoteScatterRelayDefenseRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void last_leg_crosses_only_one_new212_plate_and_gap_or_rear_cannot_borrow_any_share(GameTestHelper h){
        List<Pair> pairs=new ArrayList<>();for(int i=0;i<3;i++)pairs.add(new Pair(h,i*28));
        readyDefenses(h,pairs,212,()->{
            for(int i=0;i<3;i++){
                Pair pair=pairs.get(i);var f=pair.defender;f.seed(copies("1"),copies("0"));face(f.p,i==0?0:i==1?21:180);
                BigDecimal health=f.hp(),shield=f.shield();var before=ScatterPlateStorage.read(f.p).orElseThrow().toDecimalMap();double cp=pair.caster.cp();
                Vec3 node=f.p.position().add(0,1,8);h.assertTrue(pair.cast(node),"closed NEW253 reaches actual NEW212 body case "+i);var leg=pair.last();
                var pose=ScatterPlateGeometry.pose(scatter(f.p.position().add(0,1,0)),scatter(f.p.getLookAngle()));var contact=ScatterPlateGeometry.crossing(pose,scatter(leg.start()),scatter(leg.end()),7);
                if(i==0){
                    h.assertTrue(contact.isPresent(),"last node-to-body segment really crosses the finite live front plate");int hit=contact.orElseThrow().plateIndex();var shares=ScatterPlateStorage.read(f.p).orElseThrow();
                    eq(h,f.shield(),new BigDecimal("4"),"only contacted two-point parent pays");eq(h,f.hp(),health.subtract(new BigDecimal("3")),"exhausted parent passes only its unpaid three-point residual");
                    for(int plate=0;plate<3;plate++)eq(h,shares.plate(plate).current(),plate==hit?BigDecimal.ZERO:new BigDecimal("2"),"uncontacted siblings cannot subsidize parent "+plate);
                }else{
                    h.assertTrue(contact.isEmpty(),"actual last leg is a physical "+(i==1?"gap":"rear")+" miss");eq(h,f.hp(),health.subtract(FIVE),"miss leaves native body damage unchanged");eq(h,f.shield(),shield,"miss spends no base shield");h.assertTrue(ScatterPlateStorage.read(f.p).orElseThrow().toDecimalMap().equals(before),"miss changes no share");
                }
                near(h,cp-pair.caster.cp(),.2,"one native final hit has one relay payment");pair.assertSource(node);
            }h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void last_leg_obeys_new242_facets_gaps_rear_inside_and_finite_lower_reserve_exhaustion(GameTestHelper h){
        List<Pair> pairs=new ArrayList<>();for(int i=0;i<5;i++)pairs.add(new Pair(h,i*28));
        readyDefenses(h,pairs,242,()->{
            for(int i=0;i<5;i++){
                Pair pair=pairs.get(i);var f=pair.defender;face(f.p,0);
                // Keep every inactive252 child positive: exhausting a child during setup would
                // deliberately open252 global break debt and suppress the curtain under test.
                if(i==1)f.seed(amounts(".2",".2",".2",".2",".1",".1"),amounts("1","0","0","0","0","7"));
                else f.seed(copies("100"),copies("100"));
                h.assertTrue(DissipationCurtain.active(f.p)&&!ZeroGapArmor.blocksOtherDefense(f.p),"finite fixture keeps the real curtain active without unrelated252 break debt");
                BigDecimal health=f.hp(),shield=f.shield(),lower=f.saved().reserves().lower(),upper=f.saved().reserves().upper();
                double angle=i==2?20:i==3?180:0,radius=i==4?3:8;Vec3 radial=new Vec3(Math.sin(Math.toRadians(angle)),0,Math.cos(Math.toRadians(angle)));Vec3 node=f.p.position().add(0,1,0).add(radial.scale(radius));
                h.assertTrue(pair.cast(node),"closed NEW253 reaches actual NEW242 case "+i);var leg=pair.last();
                var pose=DissipationCurtainGeometry.pose(curtain(f.p.position().add(0,1,0)),curtain(f.p.getLookAngle()));var contact=DissipationCurtainGeometry.crossing(pose,curtain(leg.start()),curtain(leg.end()),7);
                if(i<2){
                    h.assertTrue(contact.isPresent(),"final node is outside and ray crosses actual finite curtain facet");
                    if(i==0){eq(h,f.shield(),shield.subtract(FIVE),"reviewed relay is a 1:1 beam, with no arrow discount");eq(h,f.hp(),health,"funded physical contact absorbs final residual");eq(h,f.saved().reserves().lower(),lower,"base is spent before lower reserve");}
                    else{eq(h,f.shield(),BigDecimal.ZERO,"finite base is exhausted");eq(h,f.saved().reserves().lower(),BigDecimal.ZERO,"only eligible lower reserve is exhausted");eq(h,f.hp(),health.subtract(new BigDecimal("3")),"only two funded points are absorbed, remaining three reach HP");}
                }else{
                    h.assertTrue(contact.isEmpty(),"gap/rear/inside route never invents an outside-to-inside facet crossing");eq(h,f.hp(),health.subtract(FIVE),"physical curtain miss keeps all native body damage");eq(h,f.shield(),shield,"miss spends no shield");eq(h,f.saved().reserves().lower(),lower,"miss spends no lower reserve");
                }
                eq(h,f.saved().reserves().upper(),upper,"upper reserve is ineligible for NEW242 in every case");pair.assertSource(node);
            }h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void current_rotating_new252_child_owns_one_contact_and_exhaustion_never_spills_to_siblings(GameTestHelper h){
        List<Pair> pairs=new ArrayList<>();for(int i=0;i<4;i++)pairs.add(new Pair(h,i*28));
        readyDefenses(h,pairs,252,()->{
            for(int i=0;i<4;i++){
                Pair pair=pairs.get(i);var f=pair.defender;f.seed(copies(i==1?"2":"100"),copies(i==1?"1":"100"));var pose=f.pose();var front=f.front();
                double yaw=ZeroGapArmorGeometry.plateYawDegrees(f.timing().phaseTick(),front.id())+(i==2?30:i==3?180:0);Vec3 direction=new Vec3(-Math.sin(Math.toRadians(yaw)),0,Math.cos(Math.toRadians(yaw)));Vec3 node=f.center().add(direction.scale(8));
                var before=f.armor();BigDecimal health=f.hp();h.assertTrue(pair.cast(node),"closed relay reaches native rotating armor case "+i);var leg=pair.last();var contact=ZeroGapArmorGeometry.nearestContact(pose,vector(leg.start()),vector(leg.end()));
                if(i<2){
                    h.assertTrue(contact.isPresent(),"actual final leg intersects a currently active finite numbered child");int child=contact.orElseThrow().plateId();
                    eq(h,before.total().subtract(f.armor().total()),i==1?new BigDecimal("3"):FIVE,"only contacted child pays final native amount at 1:1");eq(h,f.hp(),i==1?health.subtract(new BigDecimal("2")):health,"partial child exhaustion goes directly to HP");
                    for(int id=0;id<6;id++)if(id!=child)h.assertTrue(before.plate(id).equals(f.armor().plate(id)),"every uncontacted sibling keeps its exact base and reserve "+id);
                    if(i==1){eq(h,f.armor().plate(child).current(),BigDecimal.ZERO,"contacted finite child is exhausted");h.assertTrue(f.timing().breakTicks()==4,"real exhaustion opens the existing global break debt");}
                }else{
                    h.assertTrue(contact.isEmpty(),"actual final leg misses rotating armor through "+(i==2?"gap":"rear"));eq(h,f.hp(),health.subtract(FIVE),"physical miss leaves normal body damage");h.assertTrue(f.armor().toDecimalMap().equals(before.toDecimalMap()),"no replacement child or all-round forcefield is admitted");
                }
                pair.assertSource(node);
            }h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void early_same_source_post_followed_by_late_abort_cannot_spend_any_new212_242_or_252_pool(GameTestHelper h){
        List<Pair> pairs=new ArrayList<>();for(int i=0;i<3;i++)pairs.add(new Pair(h,i*28));
        h.runAfterDelay(61,()->{
            for(int i=0;i<3;i++)arm(h,pairs.get(i).defender,i==0?212:i==1?242:252);
            h.runAfterDelay(6,()->{
                for(int i=0;i<3;i++){
                    Pair pair=pairs.get(i);var f=pair.defender;f.seed(copies("100"),copies("100"));Vec3 node=i==2?f.center().add(vec(f.front().normal()).scale(8)):f.p.position().add(0,1,8);
                    var before=f.armor().toDecimalMap();BigDecimal health=f.hp();double cp=pair.caster.cp();boolean[] called={false};
                    f.hook(new Object(){@SubscribeEvent(priority=EventPriority.LOWEST)public void incoming(LivingIncomingDamageEvent event){if(event.getEntity()!=f.p)return;event.addReductionModifier(DamageContainer.Reduction.ABSORPTION,(container,reduction)->{
                        called[0]=true;NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(f.p,container));h.assertTrue(f.armor().toDecimalMap().equals(before),"synthetic pre-commit Post cannot debit any shared defensive child or reserve");eq(h,f.hp(),health,"synthetic Post cannot debit exact HP");near(h,cp-pair.caster.cp(),0,"synthetic Post cannot prepay attack CP");RemoteScatterRelay.abort(pair.caster.p);return reduction;});}});
                    h.assertFalse(pair.cast(node),"late native abort cancels relay before deferred payment");h.assertTrue(called[0],"real late absorption modifier executed");h.assertTrue(f.armor().toDecimalMap().equals(before),"canceled closed source consumes no defensive resources");eq(h,f.hp(),health,"canceled closed source consumes no HP");near(h,cp-pair.caster.cp(),0,"canceled closed source spends no shot CP");
                }h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch="category_shield_residual_contacts",timeoutTicks=100)
    public static void category_residual_252_keeps_original_contact_award_and_all_final_break_debt(GameTestHelper h){
        Pair pair=new Pair(h,0);
        readyDefenses(h,List.of(pair),252,()->{
            var f=pair.defender;f.seed(copies("2"),copies("1"));
            var front=f.front();Vec3 node=f.center().add(vec(front.normal()).scale(8));
            var before=f.armor();BigDecimal health=f.hp();double mastery=f.mastery();
            var stale=ZeroGapArmor.captureBeam(f.p,pair.caster.p,node,f.p.getBoundingBox().getCenter(),f.p);
            h.assertTrue(stale!=null&&ZeroGapArmor.matches(f.p,stale),"real rotating physical crossing produces one opaque current capture");
            EsperLegacyBypass.force(true);
            try{
                h.assertTrue(pair.cast(node),"native253 final leg settles through252 plus the category base shield");
                eq(h,f.hp(),health,"funded category residual protects HP after local child exhaustion");
                eq(h,before.total().subtract(f.armor().total()),FIVE,"original contact plus fallback spend exactly one five-point budget");
                h.assertTrue(f.mastery()>mastery,"final authorized shares preserve the original252 contact mastery award");
                h.assertTrue(f.timing().breakTicks()==4,"final authentic depletion persists global break debt");
                pair.last();
                // Current break debt can hide the depleted geometry, so use the original opaque physical index.
                eq(h,f.armor().plate(stale.plateIndex()).current(),BigDecimal.ZERO,"original finite contacted child is actually exhausted");
                h.assertFalse(ZeroGapArmor.matches(f.p,stale),"original pre-break capture cannot authorize a second settlement");
                pair.assertSource(node);
            }finally{EsperLegacyBypass.force(false);}
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch="category_shield_residual_contacts",timeoutTicks=100)
    public static void category_residual_212_invalidates_exhausted_sibling_receipts_before_refill(GameTestHelper h){
        Pair pair=new Pair(h,0);
        readyDefenses(h,List.of(pair),212,()->{
            var f=pair.defender;f.seed(copies("1"),copies("0"));face(f.p,0);
            var pose=ScatterPlateGeometry.pose(scatter(f.p.position().add(0,1,0)),scatter(f.p.getLookAngle()));
            var captures=new ScatterPlates.Capture[3];
            for(int id=0;id<3;id++){
                var center=ScatterPlateGeometry.plateCenter(pose,id);
                captures[id]=ScatterPlates.captureBeam(f.p,pair.caster.p,vec212(center.add(pose.axis().scale(4))),vec212(center.subtract(pose.axis())),f.p);
                h.assertTrue(captures[id]!=null&&captures[id].plateIndex()==id&&ScatterPlates.matches(f.p,captures[id]),"real funded sibling crossing owns current opaque capture "+id);
            }
            BigDecimal health=f.hp();double mastery=AbilityStorage.get(f.p).exp(ScatterPlates.ID);Vec3 node=f.p.position().add(0,1,8);
            EsperLegacyBypass.force(true);
            try{
                h.assertTrue(pair.cast(node),"native253 passes residual from one212 plate into category base");
                eq(h,f.hp(),health,"funded212 category fallback protects HP");eq(h,f.shield(),BigDecimal.ONE,"two contact points and three fallback points spend one five-point hit");
                h.assertTrue(AbilityStorage.get(f.p).exp(ScatterPlates.ID)>mastery,"original authenticated212 contact still awards mastery once");
            }finally{EsperLegacyBypass.force(false);}
            var after=ScatterPlateStorage.read(f.p).orElseThrow();
            var exhausted=new ArrayList<Integer>();for(int id=0;id<3;id++)if(after.plate(id).current().signum()==0)exhausted.add(id);
            h.assertTrue(exhausted.size()==2,"actual settlement exhausts the contacted plate and one fallback sibling");
            f.seed(copies("1"),copies("0")); // Explicit real parent refill; never edits capture/generation fields.
            for(int id:exhausted)h.assertFalse(ScatterPlates.matches(f.p,captures[id]),"refill cannot revive pre-break sibling receipt "+id);
            h.succeed();
        });
    }
    private static Vec3 vec212(ScatterPlateGeometry.Vector v){return new Vec3(v.x(),v.y(),v.z());}

    private static void readyDefenses(GameTestHelper h,List<Pair> pairs,int defense,Runnable action){h.runAfterDelay(61,()->{for(Pair pair:pairs)arm(h,pair.defender,defense);h.runAfterDelay(6,()->{for(Pair pair:pairs)h.assertTrue(defense==212?ScatterPlates.active(pair.defender.p):defense==242?DissipationCurtain.active(pair.defender.p):ZeroGapArmor.active(pair.defender.p),"real login invulnerability and native defensive startup elapsed");action.run();});});}
    private static void arm(GameTestHelper h,ZeroGapArmorNativeFixture f,int defense){face(f.p,0);if(defense==212)h.assertTrue(ScatterPlates.toggle(f.p,f.wire(1,1)),"actual sender-owned NEW212 activation");else if(defense==242)h.assertTrue(DissipationCurtain.start(f.p,f.wire(2,1)),"actual sender-owned NEW242 startup");else f.start();}
    private static void face(ServerPlayer p,float yaw){p.setYRot(yaw);p.setYHeadRot(yaw);p.yBodyRot=yaw;p.setXRot(0);}
    private static ScatterPlateGeometry.Vector scatter(Vec3 v){return new ScatterPlateGeometry.Vector(v.x,v.y,v.z);}
    private static DissipationCurtainGeometry.Vector curtain(Vec3 v){return new DissipationCurtainGeometry.Vector(v.x,v.y,v.z);}
    private static RemoteScatterRelayGeometry.Vector relay(Vec3 v){return new RemoteScatterRelayGeometry.Vector(v.x,v.y,v.z);}
    private static void set(Object owner,String name,Object value){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Object context(ServerPlayer p){try{Field f=RemoteScatterRelay.class.getDeclaredField("CONTEXTS");f.setAccessible(true);return ((Map<?,?>)f.get(null)).get(p.getUUID());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static final class Pair {
        final GameTestHelper h;final ZeroGapArmorNativeFixture defender,caster;DamageSource source;int posts;long input;
        Pair(GameTestHelper h,double offset){
            this.h=h;defender=new ZeroGapArmorNativeFixture(h,offset);caster=new ZeroGapArmorNativeFixture(h,offset+7);defender.shooter.discard();caster.shooter.discard();var state=AbilityStorage.get(caster.p);state.learn(FoldedMirrorArray.ID);state.learn(RemoteScatterRelay.ID);h.assertTrue(state.presets.edit(0,0,RemoteScatterRelay.ID,state::learned),"bind native closed NEW253 emitter");
            caster.hook(new Object(){@SubscribeEvent(priority=EventPriority.HIGHEST)public void skill(SkillAttackEvent event){if(event.player==caster.p&&event.target==defender.p&&event.skill.equals("meltdowner.remote_scatter_relay"))event.amount=5;}});
            defender.hook(new Object(){@SubscribeEvent public void incoming(LivingIncomingDamageEvent event){if(event.getEntity()==defender.p)source=event.getSource();}@SubscribeEvent public void post(LivingDamageEvent.Post event){if(event.getEntity()==defender.p)posts++;}});
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){RemoteScatterRelay.remove(caster.p);}public void testFailed(GameTestInfo i,GameTestRunner r){RemoteScatterRelay.remove(caster.p);}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){RemoteScatterRelay.remove(caster.p);}});
        }
        boolean cast(Vec3 node){
            Vec3 direction=node.subtract(defender.p.position().add(0,1,0)).normalize(),side=new Vec3(direction.z,0,-direction.x);Vec3 at=defender.p.position().add(side.scale(6)).subtract(direction.scale(4));caster.p.setPos(at);caster.p.setDeltaMovement(Vec3.ZERO);caster.p.connection.resetPosition();caster.p.setShiftKeyDown(false);face(caster.p,0);
            String wire=caster.wire(0,++input);boolean pvp=caster.p.server.isPvpAllowed();caster.p.server.setPvpAllowed(true);
            try{
                h.assertTrue(RemoteScatterRelay.start(caster.p,wire),"authentic native press owns complex fixed route fixture");Object c=context(caster.p);set(c,"plates",List.of(new RemoteScatterRelayGeometry.Node(0,relay(node),new RemoteScatterRelayGeometry.Vector(0,0,-1))));set(c,"terminal",defender.p.getBoundingBox().getCenter());set(c,"placedAt",caster.p.server.overworld().getGameTime());return RemoteScatterRelay.release(caster.p,wire);
            }finally{caster.p.server.setPvpAllowed(pvp);}
        }
        RemoteScatterRelay.SegmentVisual last(){var receipt=RemoteScatterRelay.receipt(caster.p);h.assertTrue(receipt!=null&&receipt.crossings()==1&&receipt.path().size()==2,"real receipt traverses fixed node and one final body segment");return receipt.path().getLast();}
        void assertSource(Vec3 node){h.assertTrue(posts==1&&source!=null&&source.is(DamageTypes.PLAYER_ATTACK)&&source.getEntity()==caster.p&&source.getDirectEntity()==caster.p&&node.equals(source.getSourcePosition()),"one real native hit locates defense at fixed last node while retaining caster attribution");}
    }
}
