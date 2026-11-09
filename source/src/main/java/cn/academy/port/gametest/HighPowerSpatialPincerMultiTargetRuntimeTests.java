package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.DisplacedNeedleLedger;
import cn.academy.port.highpower.core.SpatialPincerCorridorRules;
import cn.academy.port.gametest.HighPowerSpatialPincerCorridorRuntimeTests.Fixture;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Authenticated five-release casts; all accepted hits come from unredirected native projectile ticks.
 * Resident mocked-network players, learned flags, loaded floors and stationary enrolled husks are fixtures.
 * The 32x8x80 template fits x4..18/z3..64 and keeps neighboring entities outside the 26.7m-wide/64m-long selection volume.
 */
@GameTestHolder("academy_highpower_spatial_corridor")
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid="academy")
public final class HighPowerSpatialPincerMultiTargetRuntimeTests {
    private static final String TEMPLATE="highpower_corridor_volley", BATCH="highpower_corridor_multi_target";
    private static final String ID=SpatialPincerCorridor.ID, SKILL="teleporter.spatial_pincer_corridor";
    private static final double[] OFFSETS={0,1.1,-1.1,2.2,-2.2,3.3,-3.3,4.4};
    private HighPowerSpatialPincerMultiTargetRuntimeTests() {}
    private static BigDecimal hp(LivingEntity e){return new BigDecimal(HighPowerCombat.healthText(e));}
    private static void eq(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+": "+actual+" != "+expected);}
    private static BigDecimal total(Fixture f){return HighPowerGrowth.values(f.p).damage().multiply(new BigDecimal("2.8"));}
    private static BigDecimal share(BigDecimal total,int slot,int count){BigDecimal each=total.divide(BigDecimal.valueOf(count),CombatAmounts.MAX_ABSOLUTE_SCALE,RoundingMode.DOWN);return slot==count-1?total.subtract(each.multiply(BigDecimal.valueOf(count-1))):each;}

    @SubscribeEvent public static void installTemplate(net.neoforged.neoforge.event.level.LevelEvent.Load event){
        if(!net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return;
        CompoundTag root=new CompoundTag(),cell=new CompoundTag(),air=new CompoundTag();ListTag size=new ListTag(),position=new ListTag(),blocks=new ListTag(),palette=new ListTag();
        for(int dimension:new int[]{32,8,80})size.add(IntTag.valueOf(dimension));for(int axis=0;axis<3;axis++)position.add(IntTag.valueOf(0));
        cell.put("pos",position);cell.putInt("state",0);blocks.add(cell);air.putString("Name","minecraft:air");palette.add(air);
        root.put("size",size);root.put("blocks",blocks);root.put("palette",palette);root.put("entities",new ListTag());
        level.getStructureManager().getOrCreate(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy_highpower_spatial_corridor",TEMPLATE)).load(level.registryAccess().lookupOrThrow(Registries.BLOCK),root);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85) public static void one_frozen_target_uses_three_real_originals(GameTestHelper h){countCase(h,1,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85) public static void two_frozen_targets_share_three_real_originals(GameTestHelper h){countCase(h,2,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85) public static void three_frozen_targets_share_one_whole_budget(GameTestHelper h){countCase(h,3,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85) public static void four_frozen_targets_cycle_three_sources(GameTestHelper h){countCase(h,4,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85) public static void five_frozen_targets_keep_five_fixed_shares(GameTestHelper h){countCase(h,5,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85) public static void six_frozen_targets_keep_six_fixed_shares(GameTestHelper h){countCase(h,6,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85) public static void seven_targets_receive_the_exact_final_decimal_residual(GameTestHelper h){countCase(h,7,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85) public static void eight_actual_native_contacts_conserve_one_whole_budget(GameTestHelper h){countCase(h,8,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85) public static void ninth_eligible_hostile_is_excluded_from_the_frozen_eight(GameTestHelper h){countCase(h,8,true);}
    private static void countCase(GameTestHelper h,int count,boolean ninth){
        Fixture f=fixture(h,count);List<Zombie> targets=targets(f,count);Zombie excluded=ninth?f.mob(f.target.position().add(-4.55,0,0),true):null;
        BigDecimal excludedHp=excluded==null?BigDecimal.ZERO:hp(excluded),whole=total(f);Map<UUID,BigDecimal> before=health(targets);
        List<SpatialPincerCorridor.TargetVisual> frozen=new ArrayList<>();Map<UUID,HighPowerSpatialPincerCorridorRuntimeTests.Observation> hits=new HashMap<>();
        for(Zombie target:targets)hits.put(target.getUUID(),f.observe(target));
        f.begin(()->{
            frozen.addAll(SpatialPincerCorridor.frozenTargets(f.p));assertPreview(f,targets,frozen);
            h.assertValueEqual(SpatialPincerCorridor.plannedNeedles(f.p),Math.max(3,count),"preview freezes N=max(3,T)");
            if(excluded!=null)h.assertTrue(frozen.stream().noneMatch(v->v.uuid().equals(excluded.getUUID())),"strictly wider-angle ninth candidate is not selected");
        },()->{
            h.assertTrue(SpatialPincerCorridor.frozenTargets(f.p).equals(frozen),"confirmation preserves every preview identity and endpoint");
            for(int slot=1;slot<f.volleyNeedles;slot++){final int i=slot;
                f.post(4*i-1,()->h.assertValueEqual(f.spawns.media.size(),i,"emission "+i+" is not early"));
                f.post(4*i,()->h.assertValueEqual(f.spawns.media.size(),i+1,"emission "+i+" publishes on its fourth-tick boundary"));
            }
            f.post(4*(f.volleyNeedles-1)+12,()->{
                h.assertValueEqual(f.spawns.media.size(),f.volleyNeedles,"exactly the frozen number of originals were published");
                h.assertTrue(f.spawns.media.stream().map(Entity::getUUID).distinct().count()==f.volleyNeedles,"every emission owns a distinct actual native entity");
                List<Vec3> origins=List.of(f.originA,f.originB,f.originC);BigDecimal allocated=BigDecimal.ZERO,applied=BigDecimal.ZERO;
                for(int i=0;i<f.volleyNeedles;i++){
                    h.assertValueEqual(f.spawns.slots.get(i),i,"physical custody index is the frozen emission index");
                    h.assertValueEqual(f.spawns.ticks.get(i),f.started+4*i,"actual native join follows 4i cadence");
                    h.assertTrue(f.spawns.origins.get(i).equals(origins.get(i%3)),"emission "+i+" cycles source i%3");
                    Vec3 expected=frozen.get(i%count).endpoint().subtract(origins.get(i%3)).normalize().scale(1.6);
                    h.assertTrue(f.spawns.initialMotions.get(i).equals(expected),"emission "+i+" aims at frozen target i%T");
                    allocated=allocated.add(share(whole,i,f.volleyNeedles));
                }
                eq(h,allocated,whole,"all independent fixed shares including final residual sum to precisely 2.8D");
                for(int i=0;i<count;i++){
                    var target=frozen.get(i);Zombie entity=targets.stream().filter(t->t.getUUID().equals(target.uuid())).findFirst().orElseThrow();
                    BigDecimal expected=share(whole,i,f.volleyNeedles);eq(h,hp(entity),before.get(target.uuid()).subtract(expected),"target "+i+" receives its allocated native first hit, with ordinary iframes for repeats");
                    applied=applied.add(before.get(target.uuid()).subtract(hp(entity)));
                    h.assertValueEqual(hits.get(target.uuid()).posts,1,"one accepted native damage callback for this target");
                }
                if(count>=3)eq(h,applied,whole,"actual distinct-target HP debits consume one whole 2.8D budget");
                var receipt=SpatialPincerCorridor.receipt(f.p);
                h.assertTrue(receipt.paidNeedles()==f.volleyNeedles&&receipt.published()==f.volleyNeedles&&receipt.contacts()==f.volleyNeedles&&receipt.hurts()==count,"actual receipt retains publications, first contacts and native iframe decisions: "+receipt);
                h.assertTrue(f.spawns.exactPayments&&f.budget.debitCount==f.volleyNeedles,"each native publication pays its own represented CP share including the final residual");
                f.budget.assertConserved("whole-volley CP debits plus actual native recovery conserve the exact represented ledger");
                h.assertTrue(f.needles()==0&&!SpatialPincerCorridor.active(f.p)&&AbilityStorage.get(f.p).exp(ID)==0,"completion consumes N originals, grants no mastery and closes authority");
                if(excluded!=null)eq(h,hp(excluded),excludedHp,"excluded ninth target receives no reassigned or extra share");
            });
        });f.finish(4*(f.volleyNeedles-1)+25);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void preview_and_confirmation_replays_never_reacquire_or_publish_twice(GameTestHelper h){
        Fixture f=fixture(h,3);List<Zombie> original=targets(f,3);List<SpatialPincerCorridor.TargetVisual> frozen=new ArrayList<>();
        f.begin(()->{frozen.addAll(SpatialPincerCorridor.frozenTargets(f.p));String replay=f.wire(f.inputs);
            h.assertFalse(SpatialPincerCorridor.release(f.p,replay)||SpatialPincerCorridor.press(f.p,replay),"fourth input cannot also become the fifth confirmation");
            h.assertTrue(f.spawns.media.isEmpty()&&SpatialPincerCorridor.frozenTargets(f.p).equals(frozen),"replay keeps the free preview unchanged");
            f.aim(f.source.add(10,0,0));
        },()->{String replay=f.wire(f.inputs);double cp=f.cp();
            h.assertFalse(SpatialPincerCorridor.release(f.p,replay)||SpatialPincerCorridor.press(f.p,replay),"fifth input cannot publish a second original");
            h.assertTrue(f.spawns.media.size()==1&&f.cp()==cp&&SpatialPincerCorridor.frozenTargets(f.p).equals(frozen),"confirmation uses the original preview even after a different aim");
            assertPreview(f,original,frozen);SpatialPincerCorridor.cancelActive(f.p);
        });f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void losing_one_of_eight_stock_after_preview_refuses_the_whole_confirmation(GameTestHelper h){
        Fixture f=fixture(h,8);targets(f,8);f.preview(()->{var frozen=SpatialPincerCorridor.frozenTargets(f.p);double cp=f.cp();
            f.p.getOffhandItem().shrink(1);f.post(4,()->{
                h.assertFalse(f.tap(),"seven physical originals cannot confirm an eight-original preview");
                h.assertTrue(frozen.size()==8&&f.spawns.media.isEmpty()&&f.cp()==cp&&f.needles()==7,"stock change causes no partial escrow, payment or smaller reacquisition");
                h.assertTrue(SpatialPincerCorridor.receipt(f.p).reason().equals("needles_required")&&!SpatialPincerCorridor.active(f.p),"stock refusal closes the frozen plan");
            });});f.finish(17);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void invisible_secondary_is_excluded_from_frozen_target_visuals(GameTestHelper h){
        Fixture f=fixture(h,3);List<Zombie> targets=targets(f,3);Zombie hidden=targets.get(1);makeInvisible(f,hidden);
        f.preview(()->{var frozen=SpatialPincerCorridor.frozenTargets(f.p);
            h.assertTrue(frozen.size()==2&&frozen.stream().noneMatch(target->target.uuid().equals(hidden.getUUID())),"native invisible hostile has no frozen target record or endpoint");
            h.assertTrue(frozen.getFirst().uuid().equals(f.target.getUUID())&&frozen.stream().anyMatch(target->target.uuid().equals(targets.get(2).getUUID())),"visible primary and other visible hostile remain selected");
            h.assertTrue(SpatialPincerCorridor.plannedNeedles(f.p)==3&&f.spawns.media.isEmpty(),"two visible targets preserve three-original planning without exposing the invisible secondary");
            SpatialPincerCorridor.cancelActive(f.p);
        });f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void target_becoming_invisible_after_preview_refuses_whole_confirmation(GameTestHelper h){
        Fixture f=fixture(h,3);targets(f,3);f.preview(()->{makeInvisible(f,entity(f,SpatialPincerCorridor.frozenTargets(f.p).get(1)));double cp=f.cp();
            f.post(4,()->{h.assertFalse(f.tap(),"fifth tap cannot confirm a target that became natively invisible after preview");
                h.assertTrue(f.spawns.media.isEmpty()&&f.needles()==3&&f.cp()==cp&&!SpatialPincerCorridor.active(f.p),"invisibility invalidates the whole frozen preview before escrow or CP, without shrinking or reacquisition");});
        });f.finish(17);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void later_C_shape_callback_cannot_invalidate_earlier_target_then_admit_whole_plan(GameTestHelper h){
        Fixture f=fixture(h,3);targets(f,3);f.preview(()->f.post(4,()->{
            var frozen=SpatialPincerCorridor.frozenTargets(f.p);Zombie primary=entity(f,frozen.getFirst());double cp=f.cp();
            BlockPos pos=BlockPos.containing(f.originC.add(1,0,0));var original=h.getLevel().getBlockState(pos);boolean[] called={false};
            h.assertTrue(pos.getX()>Math.floor(f.originA.x+.125+1)&&pos.getX()>Math.floor(f.originB.x+.125+1)
                    &&pos.getX()<=Math.floor(f.originC.x+.125+1),"declared shape belongs only to C's native origin-neighbor reads, after A and B route validation");
            h.assertTrue(NativeFailureBlock.block!=null,"GameTest-only dynamic shape exists before the native registry freezes");NativeFailureBlock.armed=false;NativeFailureBlock.onRead=null;
            try{
                f.block(pos,NativeFailureBlock.block.defaultBlockState());
                NativeFailureBlock.onRead=()->{called[0]=true;makeInvisible(f,primary);};
                h.assertFalse(f.tap(),"late C validation cannot admit a plan whose already-checked primary became invisible");
                h.assertTrue(called[0]&&primary.isInvisibleTo(f.p),"actual later native shape callback changed earlier frozen target eligibility");
                h.assertTrue(f.spawns.media.isEmpty()&&f.cp()==cp&&f.needles()==3&&!SpatialPincerCorridor.active(f.p),"final all-target state check refuses the whole fifth confirmation before any escrow or payment");
            }finally{NativeFailureBlock.onRead=null;NativeFailureBlock.armed=false;h.getLevel().setBlockAndUpdate(pos,original);}
        }));f.finish(17);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=45)
    public static void post_admission_invisibility_retires_own_flight_and_future_repeat_only(GameTestHelper h){
        Fixture f=fixture(h,2);List<Zombie> targets=targets(f,2);Map<UUID,BigDecimal> before=health(targets);BigDecimal whole=total(f);
        f.begin(()->{h.assertTrue(f.spawns.slots.equals(List.of(0)),"primary has one actual paid and admitted native original before becoming invisible");makeInvisible(f,f.target);
            f.post(15,()->{
                h.assertTrue(f.spawns.slots.equals(List.of(0,1))&&f.spawns.ticks.equals(List.of(f.started,f.started+4)),"invisibility retires primary flight0, skips its future repeated slot2, and keeps unrelated slot1");
                eq(h,hp(f.target),before.get(f.target.getUUID()),"invisible primary receives no new native damage");
                eq(h,hp(targets.get(1)),before.get(targets.get(1).getUUID()).subtract(share(whole,1,3)),"unrelated visible target receives only its own original fixed share");
                var receipt=SpatialPincerCorridor.receipt(f.p);h.assertTrue(receipt.paidNeedles()==2&&receipt.contacts()==1&&receipt.hurts()==1&&f.needles()==1,"only already admitted flight0 and unrelated flight1 cost originals/CP; skipped repeat remains unspent: "+receipt);
                h.assertTrue(!f.first().isContactEnabled()&&!f.first().isRemoved()&&CorridorNeedleRegistry.authentic(f.p,f.first()),"retired invisible-target original remains authentic harmless physical custody");
                f.budget.assertConserved("invisibility neither refunds the already paid shot nor pools its lost damage/CP allocation");
            });
        });f.finish(28);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void dead_frozen_target_before_confirmation_refuses_every_share(GameTestHelper h){beforeConfirmation(h,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void hidden_frozen_target_before_confirmation_refuses_every_share(GameTestHelper h){beforeConfirmation(h,true);}
    private static void beforeConfirmation(GameTestHelper h,boolean hidden){
        Fixture f=fixture(h,3);targets(f,3);f.preview(()->{var frozen=SpatialPincerCorridor.frozenTargets(f.p);Zombie selected=entity(f,frozen.get(1));double cp=f.cp();
            if(hidden)hideFromOwner(f,selected);else selected.discard();
            f.post(4,()->{h.assertFalse(f.tap(),"one invalid frozen target prevents the whole fifth confirmation");
                h.assertTrue(f.spawns.media.isEmpty()&&f.needles()==3&&f.cp()==cp&&!SpatialPincerCorridor.active(f.p),"no target shrink, refund or first emission after invalid preview");
                h.assertFalse(SpatialPincerCorridor.receipt(f.p).paid(),"invalid target refusal precedes every payment");
            });});f.finish(17);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=55)
    public static void dead_second_target_after_admission_skips_only_its_frozen_share(GameTestHelper h){afterAdmission(h,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=55)
    public static void hidden_second_target_after_admission_skips_only_its_frozen_share(GameTestHelper h){afterAdmission(h,true);}
    private static void afterAdmission(GameTestHelper h,boolean hidden){
        Fixture f=fixture(h,3);List<Zombie> targets=targets(f,3);Map<UUID,BigDecimal> before=health(targets);BigDecimal whole=total(f);
        f.begin(()->{var frozen=SpatialPincerCorridor.frozenTargets(f.p);Zombie lost=entity(f,frozen.get(1));
            if(hidden)hideFromOwner(f,lost);else lost.discard();
            f.post(21,()->{h.assertTrue(f.spawns.slots.equals(List.of(0,2)),"loss skips exactly slot one, preserves unrelated slot zero and publishes slot two");
                h.assertTrue(f.spawns.ticks.equals(List.of(f.started,f.started+8)),"skipping preserves fixed time instead of moving later shots earlier");
                for(int i:new int[]{0,2}){Zombie target=entity(f,frozen.get(i));eq(h,hp(target),before.get(target.getUUID()).subtract(share(whole,i,3)),"unrelated target keeps only its original allocation");}
                if(hidden)eq(h,hp(lost),before.get(lost.getUUID()),"hidden target loses attack eligibility without receiving another target's share");
                var receipt=SpatialPincerCorridor.receipt(f.p);h.assertTrue(receipt.paidNeedles()==2&&receipt.contacts()==2&&receipt.hurts()==2&&f.needles()==1,"only two actual originals and two CP shares are spent: "+receipt);
                f.budget.assertConserved("lost target cannot pool, refund or reassign CP");
            });});f.finish(34);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=55)
    public static void moved_target_and_new_hostile_never_replace_frozen_endpoint_or_identity(GameTestHelper h){
        Fixture f=fixture(h,3);List<Zombie> targets=targets(f,3);Map<UUID,BigDecimal> before=health(targets);
        f.begin(()->{var frozen=SpatialPincerCorridor.frozenTargets(f.p);Zombie moved=entity(f,frozen.get(1));Vec3 original=moved.position();
            moved.setPos(original.add(0,4,0));Zombie replacement=f.mob(original,true);BigDecimal replacementHp=hp(replacement);
            f.post(21,()->{eq(h,hp(moved),before.get(moved.getUUID()),"frozen endpoint never follows the moved target");eq(h,hp(replacement),replacementHp,"new hostile never receives the lost assigned share");
                h.assertTrue(f.spawns.slots.equals(List.of(0,2)),"retained replacement body makes exactly the old endpoint's assigned slot skip before publication");
                h.assertTrue(f.needles()==1&&f.budget.debitCount==2&&SpatialPincerCorridor.receipt(f.p).hurts()==2,"unrelated targets survive, while the blocked assigned original and CP share remain unspent");
                f.budget.assertConserved("moved target never redistributes the frozen budget");
            });});f.finish(34);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void exactly_sixty_four_total_candidates_include_owner_and_ineligible_entities(GameTestHelper h){
        Fixture f=fixture(h,1);f.ready(()->{
            Vec3 at=f.target.position().add(8,0,6);
            for(int i=0;i<62;i++){ItemEntity item=new ItemEntity(h.getLevel(),at.x,at.y+.2,at.z,new ItemStack(Items.STICK));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);item.setUnlimitedLifetime();
                h.assertTrue(h.getLevel().addFreshEntity(item),"declared ineligible candidate "+i+" is resident");f.entities.add(item);}
            f.aim(f.target.getBoundingBox().getCenter());double cp=f.cp();
            h.assertTrue(f.tap(),"owner + primary +62 ineligible candidates fit the exact total limit of64: "+SpatialPincerCorridor.receipt(f.p));
            h.assertTrue(SpatialPincerCorridor.frozenTargets(f.p).size()==1&&f.cp()==cp&&f.spawns.media.isEmpty(),"ineligible candidates count but cannot become extra frozen targets");
            SpatialPincerCorridor.cancelActive(f.p);
        });f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void third_assigned_route_obstruction_refuses_before_any_first_emission(GameTestHelper h){
        Fixture f=fixture(h,3);targets(f,3);f.preview(()->{
            var frozen=SpatialPincerCorridor.frozenTargets(f.p);Vec3 obstruction=f.originC.lerp(frozen.get(2).endpoint(),.15);
            f.block(BlockPos.containing(obstruction),Blocks.STONE.defaultBlockState());double cp=f.cp();
            for(int i=0;i<2;i++){Vec3 origin=i==0?f.originA:f.originB;
                h.assertTrue(h.getLevel().clip(new ClipContext(origin,frozen.get(i).endpoint(),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,f.p)).getType()==HitResult.Type.MISS,"earlier assigned route "+i+" remains independently clear");}
            h.assertTrue(h.getLevel().clip(new ClipContext(f.originC,frozen.get(2).endpoint(),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,f.p)).getType()==HitResult.Type.BLOCK,"only later C route contains the declared native first solid");
            f.post(4,()->{h.assertFalse(f.tap(),"fifth confirmation validates the entire frozen route plan before publishing A");
                h.assertTrue(f.spawns.media.isEmpty()&&f.cp()==cp&&f.needles()==3,"blocked later assignment causes no partial CP or original consumption");});
        });f.finish(17);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void sixty_five_total_candidates_including_unpickable_items_refuse_preview(GameTestHelper h){overflow(h,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void unseen_candidates_behind_near_wall_still_count_toward_total_scan_bound(GameTestHelper h){overflow(h,true);}
    private static void overflow(GameTestHelper h,boolean unseen){
        Fixture f=fixture(h,1);f.ready(()->{
            Vec3 at=f.target.position().add(8,0,6);
            if(unseen)for(int y=0;y<3;y++)for(int x=-1;x<=1;x++)f.block(BlockPos.containing(at.add(x,y,-2)),Blocks.STONE.defaultBlockState());
            for(int i=0;i<63;i++){ItemEntity item=new ItemEntity(h.getLevel(),at.x,at.y+.2,at.z,new ItemStack(Items.STICK));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);item.setUnlimitedLifetime();
                h.assertTrue(h.getLevel().addFreshEntity(item),"declared unpickable native candidate is resident");f.entities.add(item);h.assertFalse(item.isPickable(),"ineligible item still occupies the bounded candidate query");}
            if(unseen)h.assertTrue(h.getLevel().clip(new ClipContext(f.p.getEyePosition(),at.add(0,.2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,f.p)).getType()==HitResult.Type.BLOCK,"native wall hides the overflow candidates from owner LOS");
            double cp=f.cp();f.aim(f.target.getBoundingBox().getCenter());h.assertFalse(f.tap(),"total candidate overflow cannot be hidden by eligibility or occlusion filtering");
            h.assertTrue(SpatialPincerCorridor.receipt(f.p).reason().equals("entity_scan_budget")&&f.spawns.media.isEmpty()&&f.needles()==3&&f.cp()==cp,"overflow refuses before any preview authority, physical escrow or CP payment");
        });f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=28)
    public static void equal_distance_competing_body_blocks_primary_regardless_of_uuid_order(GameTestHelper h){
        Fixture f=fixture(h,1);f.ready(()->{Zombie overlap=f.mob(f.target.position(),false);
            h.assertTrue(overlap.getBoundingBox().equals(f.target.getBoundingBox()),"two actual native bodies have identical first-entry geometry");
            double cp=f.cp();f.aim(f.target.getBoundingBox().getCenter());h.assertFalse(f.tap(),"ambiguous simultaneous first body cannot select either hostile");
            h.assertTrue(f.spawns.media.isEmpty()&&f.cp()==cp&&f.needles()==3,"equal-distance body tie remains entirely free");
        });f.finish(13);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=60)
    public static void early_actual_pickup_does_not_reuse_slot_zero_for_later_publications(GameTestHelper h){
        Fixture f=fixture(h,8);targets(f,8);f.begin(()->{
            f.post(7,()->{DisplacedNeedleEntity first=f.first();h.assertFalse(first.isContactEnabled(),"first original has completed its one native contact");
                f.block(BlockPos.containing(first.position()).below(),Blocks.STONE.defaultBlockState());first.setDeltaMovement(0,-1,0);for(int i=0;i<12;i++)first.tick();
                h.assertTrue(first.inGround&&first.shakeTime==0,"actual native gravity and block impact make first original pickup-ready");
                int handBefore=f.p.getOffhandItem().getCount();first.playerTouch(f.p);h.assertTrue(first.isRemoved(),"real owner pickup returns the original before slot two publishes");
                f.initialStock+=f.p.getOffhandItem().getCount()-handBefore;
                var spent=CorridorNeedleRegistry.get(f.p.serverLevel()).snapshots(f.p.getUUID()).getFirst();
                h.assertTrue(spent.state()==DisplacedNeedleLedger.State.SPENT,"picked-up physical slot is spent");
                f.post(26,()->{var snapshots=CorridorNeedleRegistry.get(f.p.serverLevel()).snapshots(f.p.getUUID());
                    h.assertTrue(snapshots.getFirst().equals(spent)&&f.spawns.slots.equals(List.of(0,1,2,3,4,5,6,7)),"later emissions retain exact slot indices and never reuse the earlier picked-up slot");
                    h.assertValueEqual(f.needles(),1,"all eight original publications leave precisely the one genuinely recovered original");
                    h.assertValueEqual(f.budget.debitCount,8,"pickup cannot refund or create an extra publication CP debit");f.budget.assertConserved("actual early pickup changes item custody only");
                });
            });
        });f.finish(48);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=55)
    public static void first_contact_with_unassigned_nonhostile_consumes_only_that_shot(GameTestHelper h){
        Fixture f=fixture(h,3);List<Zombie> targets=targets(f,3);Map<UUID,BigDecimal> before=health(targets);
        f.begin(()->{Vec3 at=f.originA.lerp(f.target.getBoundingBox().getCenter(),.15);Sheep sheep=sheep(f,at.add(0,-.7,0));float health=sheep.getHealth();
            var frozen=SpatialPincerCorridor.frozenTargets(f.p);List<Vec3> origins=List.of(f.originA,f.originB,f.originC);
            for(int i=1;i<3;i++)h.assertFalse(sheep.getBoundingBox().inflate((double).3F).clip(origins.get(i),frozen.get(i).endpoint()).isPresent()
                    ||sheep.getBoundingBox().clip(f.p.getEyePosition(),frozen.get(i).endpoint()).isPresent(),"first-contact blocker leaves unrelated assignment "+i+" and its owner visibility independently clear");
            f.post(21,()->{eq(h,hp(f.target),before.get(f.target.getUUID()),"unassigned native first body blocks only the primary's shot");
                h.assertTrue(sheep.getHealth()==health&&!f.first().isContactEnabled(),"unassigned nonhostile is unharmed and consumes first-contact authority");
                h.assertTrue(SpatialPincerCorridor.receipt(f.p).hurts()==2&&f.spawns.media.size()==3,"both unrelated target assignments still execute without sharing the miss");
                f.budget.assertConserved("missed first contact never refunds or reallocates a paid share");
            });});f.finish(34);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=55)
    public static void throwing_native_visibility_shape_retires_only_the_affected_flight(GameTestHelper h){
        Fixture f=fixture(h,3);List<Zombie> targets=targets(f,3);Map<UUID,BigDecimal> before=health(targets);
        f.begin(()->{var frozen=SpatialPincerCorridor.frozenTargets(f.p);
            f.post(4,()->{
                h.assertTrue(f.spawns.slots.equals(List.of(0,1)),"second assigned native original is already admitted before the failed read");
                DisplacedNeedleEntity medium=f.spawns.media.get(1);Zombie affected=entity(f,frozen.get(1));
                Vec3 sight=f.p.getEyePosition().lerp(affected.getBoundingBox().getCenter(),.6);BlockPos pos=BlockPos.containing(sight);
                var original=h.getLevel().getBlockState(pos);NativeFailureBlock.armed=false;NativeFailureBlock.reads=0;NativeFailureBlock.onRead=null;
                h.assertTrue(NativeFailureBlock.block!=null,"declared GameTest-only dynamic block was registered before the native registry froze");
                // The registered test-only shape is armed and restored within this callback, before any world tick/save.
                try{
                    f.block(pos,NativeFailureBlock.block.defaultBlockState());NativeFailureBlock.armed=true;
                    for(int i=0;i<12&&medium.isContactEnabled();i++)medium.tick();
                    h.assertTrue(NativeFailureBlock.reads>0&&!medium.isContactEnabled(),"actual native contact queries the throwing visibility shape and retires safely without escaping the tick");
                    eq(h,hp(affected),before.get(affected.getUUID()),"failed current-visibility read grants no target HP debit");
                    h.assertTrue(!medium.isRemoved()&&CorridorNeedleRegistry.authentic(f.p,medium),"the already paid original remains in exact harmless physical custody");
                }finally{NativeFailureBlock.armed=false;h.getLevel().setBlockAndUpdate(pos,original);}
            });
            f.post(22,()->{
                h.assertTrue(f.spawns.slots.equals(List.of(0,1,2))&&SpatialPincerCorridor.receipt(f.p).hurts()==2,"unrelated already flying and future assigned originals both complete after one failed visibility callback");
                for(int index:new int[]{0,2}){Zombie target=entity(f,frozen.get(index));eq(h,hp(target),before.get(target.getUUID()).subtract(share(total(f),index,3)),"unrelated target retains exactly its own fixed share");}
                h.assertValueEqual(f.budget.debitCount,3,"failed read never refunds the admitted original or creates an additional payment");f.budget.assertConserved("failed visibility callback preserves native CP accounting");
            });
        });f.finish(35);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=45)
    public static void exception_in_actual_contact_callback_closes_future_queue_without_refund(GameTestHelper h){
        Fixture f=new Fixture(h,true);boolean[] called={false};f.hook(new Object(){@SubscribeEvent public void skill(SkillAttackEvent event){if(event.target==f.target&&event.skill.equals(SKILL)){called[0]=true;throw new IllegalStateException("Declared corridor native contact failure");}}});
        f.begin(()->{double cp=f.cp();BigDecimal before=hp(f.target);boolean thrown=false;try{f.first().tick();}catch(IllegalStateException expected){thrown=true;}
            h.assertTrue(called[0]&&thrown,"real first contact reaches and propagates the declared native callback exception");
            eq(h,hp(f.target),before,"pre-hurt callback exception cannot manufacture damage");
            h.assertTrue(!SpatialPincerCorridor.active(f.p)&&!f.first().isContactEnabled()&&f.cp()==cp,"uncertain contact closes attack authority with no paid-share refund");
            f.post(9,()->{h.assertTrue(f.spawns.media.size()==1&&f.needles()==2,"future scheduled originals never resume after contact exception");f.budget.assertConserved("exception retains actual paid CP and ordinary recovery");});
        });f.finish(23);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=32)
    public static void native_join_exception_cannot_resume_confirmation_or_duplicate_escrow(GameTestHelper h){
        Fixture f=fixture(h,3);targets(f,3);boolean[] called={false};
        f.hook(new Object(){@SubscribeEvent(priority=EventPriority.LOWEST) public void join(EntityJoinLevelEvent event){if(event.getEntity() instanceof DisplacedNeedleEntity medium&&medium.isCorridorNeedle()&&medium.getOwner()==f.p){called[0]=true;throw new IllegalStateException("Declared multi-target native join failure");}}});
        f.preview(()->f.post(4,()->{double cp=f.cp();h.assertFalse(f.tap(),"native join exception prevents confirming the first original");
            h.assertTrue(called[0]&&f.spawns.media.size()==1&&f.cp()==cp&&!SpatialPincerCorridor.active(f.p),"unconfirmed join has no CP debit and no continuation authority");
            int stock=f.needles(),escrow=CorridorNeedleRegistry.get(f.p.serverLevel()).retainedEscrowCount(f.p.getUUID());
            h.assertTrue(stock+escrow==3,"exactly one original remains in resolved inventory or retained custody, never duplicated");
            f.post(9,()->{h.assertTrue(f.spawns.media.size()==1&&f.needles()==stock,"later ticks never replay uncertain publication or refund a second original");h.assertValueEqual(f.budget.debitCount,0,"no admitted publication was charged");});
        }));f.finish(26);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=550)
    public static void frozen_preview_expires_at_first_marks_four_hundredth_tick(GameTestHelper h){
        Fixture f=fixture(h,3);targets(f,3);double cp=f.cp();f.preview(()->{
            for(int i=0;i<100;i++)SpatialPincerCorridor.tick(f.p);
            int remaining = SpatialPincerCorridorRules.PREPARATION_TICKS - 12;
            h.assertValueEqual(SpatialPincerCorridor.remainingTicks(f.p),remaining,"preview and repeated observers never restart preparation lifetime");
            f.post(remaining - 1,()->h.assertTrue(SpatialPincerCorridor.previewed(f.p)&&SpatialPincerCorridor.remainingTicks(f.p)==1,"preview survives only until the first mark's final tick"));
            f.post(remaining,()->h.assertTrue(!SpatialPincerCorridor.previewed(f.p)&&!SpatialPincerCorridor.active(f.p)&&f.cp()==cp&&f.needles()==3&&f.spawns.media.isEmpty(),"the first mark deadline expires the free frozen preview without consumption"));
        });f.finish(SpatialPincerCorridorRules.PREPARATION_TICKS + 1);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_v2_visual_codec_roundtrips_eight_targets_and_last_emission_and_rejects_bad_bounds(GameTestHelper h){
        h.assertTrue(SpatialPincerCorridor.Visual.PROTOCOL_VERSION.equals("2"),"extended NEW341 payload declares its scoped version2 wire contract");
        List<SpatialPincerCorridor.TargetVisual> targets=new ArrayList<>();
        for(int i=0;i<8;i++)targets.add(new SpatialPincerCorridor.TargetVisual(100+i,UUID.randomUUID(),new Vec3(i,1,20)));
        UUID owner=UUID.randomUUID(),medium=UUID.randomUUID(),none=new UUID(0,0);
        var preview=new SpatialPincerCorridor.Visual(99,owner,4,1,1,1,SpatialPincerCorridor.PREVIEW,3,388,
                new Vec3(-2,0,0),Vec3.ZERO,new Vec3(2,0,0),new Vec3(0,1.62,-14),new Vec3(-2,1.2,0),targets.getFirst().endpoint(),
                -1,-1,none,true,"",targets,8);
        var last=new SpatialPincerCorridor.Visual(99,owner,5,1,1,2,SpatialPincerCorridor.PROJECTED,3,0,
                preview.pointA(),preview.pointB(),preview.pointC(),preview.source(),preview.pointB().add(0,1.2,0),targets.getLast().endpoint(),
                7,200,medium,false,"",targets,8);
        for(var packet:List.of(preview,last)){
            var wire=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try{SpatialPincerCorridor.Visual.CODEC.encode(wire,packet);var decoded=SpatialPincerCorridor.Visual.CODEC.decode(wire);
                h.assertTrue(decoded.equals(packet)&&wire.readableBytes()==0,"actual native codec preserves every bounded target identity/endpoint, slot7 and whole-volley count");}
            finally{wire.release();}
        }
        int recordBytes=Integer.BYTES+2*Long.BYTES+3*Double.BYTES;
        for(int corruption=0;corruption<5;corruption++){
            var wire=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try{
                SpatialPincerCorridor.Visual.CODEC.encode(wire,preview);int countOffset=wire.writerIndex()-2-8*recordBytes,second=countOffset+1+recordBytes;
                if(corruption==0)wire.setByte(countOffset,9);
                else if(corruption==1)wire.setByte(countOffset,255);
                else if(corruption==2)wire.setInt(second,targets.getFirst().entity());
                else if(corruption==3){wire.setLong(second+Integer.BYTES,targets.getFirst().uuid().getMostSignificantBits());wire.setLong(second+Integer.BYTES+Long.BYTES,targets.getFirst().uuid().getLeastSignificantBits());}
                else wire.setByte(wire.writerIndex()-1,9);
                boolean refused=false;try{SpatialPincerCorridor.Visual.CODEC.decode(wire);}catch(IllegalArgumentException expected){refused=true;}
                h.assertTrue(refused,"native decode rejects oversized list, duplicate entity/UUID or oversized volley: case="+corruption);
            }finally{wire.release();}
        }
        targets.clear();h.assertTrue(preview.targets().size()==8&&last.targets().size()==8,"constructed native packets defensively retain immutable target snapshots");h.succeed();
    }

    /** This class is excluded from production JARs together with every other GameTest fixture. */
    @net.neoforged.fml.common.EventBusSubscriber(modid="academy",bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
    public static final class NativeFailureBlock{
        static net.minecraft.world.level.block.Block block;static boolean armed;static int reads;static Runnable onRead;
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){
            if(!net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()
                    ||!Arrays.asList(System.getProperty("neoforge.enabledGameTestNamespaces","").split(",")).contains("academy_highpower_spatial_corridor")
                    ||!event.getRegistryKey().equals(Registries.BLOCK))return;
            event.register(Registries.BLOCK,helper->{
                block=new net.minecraft.world.level.block.Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().dynamicShape().noOcclusion()){
                    @Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos at,net.minecraft.world.phys.shapes.CollisionContext context){
                        if(onRead!=null){Runnable callback=onRead;onRead=null;callback.run();}
                        if(armed){reads++;throw new IllegalStateException("Declared native visibility shape failure");}
                        return net.minecraft.world.phys.shapes.Shapes.empty();
                    }
                };
                helper.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy","gametest_corridor_visibility_failure"),block);
            });
        }
    }

    private static void makeInvisible(Fixture f,Zombie target){
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY,200,0));target.setInvisible(true);
        f.h.assertTrue(target.isInvisibleTo(f.p)&&HighPowerCombat.unsupportedReason(target).isEmpty(),"declared native invisibility is the only changed eligibility condition on an otherwise supported hostile");
    }
    private static Fixture fixture(GameTestHelper h,int count){Fixture f=new Fixture(h,true,0,true);f.volleyNeedles=Math.max(3,count);f.stock(f.volleyNeedles);return f;}
    private static List<Zombie> targets(Fixture f,int count){List<Zombie> result=new ArrayList<>();result.add(f.target);for(int i=1;i<count;i++)result.add(f.mob(f.target.position().add(OFFSETS[i],0,0),true));return result;}
    private static Map<UUID,BigDecimal> health(List<Zombie> targets){Map<UUID,BigDecimal> values=new HashMap<>();for(Zombie target:targets)values.put(target.getUUID(),hp(target));return values;}
    private static Zombie entity(Fixture f,SpatialPincerCorridor.TargetVisual target){Entity actual=f.h.getLevel().getEntity(target.uuid());f.h.assertTrue(actual instanceof Zombie&&actual.getId()==target.entity(),"frozen identity still resolves to the original native entity");return (Zombie)actual;}
    private static void assertPreview(Fixture f,List<Zombie> expected,List<SpatialPincerCorridor.TargetVisual> frozen){
        f.h.assertValueEqual(frozen.size(),expected.size(),"preview includes exactly the expected eligible visible hostiles");
        f.h.assertTrue(frozen.getFirst().uuid().equals(f.target.getUUID()),"directly aimed primary remains first");
        Set<UUID> ids=new HashSet<>();for(var target:frozen){ids.add(target.uuid());Zombie actual=entity(f,target);f.h.assertTrue(target.endpoint().equals(actual.getBoundingBox().getCenter()),"preview freezes actual native center at selection time");}
        f.h.assertTrue(ids.size()==expected.size()&&expected.stream().allMatch(target->ids.contains(target.getUUID())),"preview has no duplicate, missing or unexpected identity");
    }
    private static Sheep sheep(Fixture f,Vec3 at){Sheep sheep=EntityType.SHEEP.create(f.h.getLevel());sheep.setNoAi(true);sheep.setNoGravity(true);sheep.setSilent(true);sheep.setPos(at);f.h.assertTrue(f.h.getLevel().addFreshEntity(sheep),"declared native blocker is resident");f.entities.add(sheep);return sheep;}
    private static void hideFromOwner(Fixture f,Zombie target){
        Vec3 at=f.p.getEyePosition().lerp(target.getBoundingBox().getCenter(),.6);
        var blocker=EntityType.CHICKEN.create(f.h.getLevel());blocker.setNoAi(true);blocker.setNoGravity(true);blocker.setSilent(true);blocker.setPos(at.add(0,-.35,0));
        f.h.assertTrue(f.h.getLevel().addFreshEntity(blocker),"declared small native visibility blocker is resident");f.entities.add(blocker);
        f.h.assertTrue(blocker.getBoundingBox().clip(f.p.getEyePosition(),target.getBoundingBox().getCenter()).isPresent(),"independent actual body occludes owner visibility to only this frozen target");
        f.h.assertFalse(blocker.getBoundingBox().clip(f.p.getEyePosition(),f.target.getBoundingBox().getCenter()).isPresent(),"primary owner's visibility remains independently unobstructed");
        var frozen=SpatialPincerCorridor.frozenTargets(f.p);List<Vec3> origins=List.of(f.originA,f.originB,f.originC);
        for(int i=0;i<f.volleyNeedles;i++){
            Vec3 origin=origins.get(i%3),end=frozen.get(i%frozen.size()).endpoint();
            f.h.assertFalse(blocker.getBoundingBox().inflate((double).3F).clip(f.p.getEyePosition(),origin).isPresent()
                    ||blocker.getBoundingBox().inflate((double).3F).clip(origin,end).isPresent(),"visibility blocker leaves actual assigned route "+i+" independently clear");
        }
    }
}
