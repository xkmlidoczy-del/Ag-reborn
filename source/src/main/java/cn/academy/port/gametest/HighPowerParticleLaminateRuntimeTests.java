package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ParticleLaminateGeometry;
import cn.academy.port.highpower.core.ParticleLaminateRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native authenticated placement and paid pulses. Tests never fabricate controllers or damage permits. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerParticleLaminateRuntimeTests {
    private static final String TEMPLATE="highpower_empty",BATCH="highpower_particle_laminate",SKILL="meltdowner.particle_laminate";
    private static final TicketType<UUID> FAR_FIXTURE_TICKET=TicketType.create("academy_particle_laminate_far_fixture",UUID::compareTo);
    private HighPowerParticleLaminateRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void particle_laminate_three_real_origins_share_exact_one_pulse_budget(GameTestHelper h){
        Fixture f=new Fixture(h);double initial=f.cp();List<Zombie> targets=new ArrayList<>();List<DamageSource> sources=new ArrayList<>();BigDecimal[] budget={BigDecimal.ZERO};
        f.arm(false,()->{budget[0]=f.damage(false);targets.addAll(f.trio());f.hook(new Object(){@SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(targets.contains(e.getEntity()))sources.add(e.getSource());}});});
        h.runAtTickTime(6,()->{for(Zombie target:targets)eq(h,hp(target),cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"edge warning precedes all first damage");});
        h.runAtTickTime(11,()->{
            BigDecimal total=targets.stream().map(t->cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(t))).reduce(BigDecimal.ZERO,BigDecimal::add);eq(h,total,budget[0],"one whole 2.5D pulse exactly");
            var receipt=ParticleLaminate.receipt(f.p);h.assertTrue(receipt!=null&&receipt.paid()&&receipt.attempts()==3&&receipt.successes()==3,"three independent first-body allocations");
            h.assertTrue(receipt.path().size()==3&&receipt.path().stream().map(ParticleLaminate.SegmentVisual::start).distinct().count()==3,"receipt retains real distinct panel emitters");
            h.assertTrue(sources.size()==3&&sources.stream().allMatch(s->s.is(DamageTypes.PLAYER_ATTACK)&&s.getEntity()==f.p),"each ordinary native source keeps player attack semantics");
            for(DamageSource source:sources)h.assertTrue(receipt.path().stream().anyMatch(ray->ray.start().equals(source.getSourcePosition())),"damage origin is its actual endpoint on first panel");
            near(h,f.debit(initial),3,"two .5 CP plates plus one2 CP pulse");h.assertFalse(HighPowerCombat.attack(f.p,SKILL,targets.getFirst(),BigDecimal.ONE),"generic combat cannot forge NEW241 permit");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void particle_laminate_same_body_on_all_three_rays_gets_one_complete_share(GameTestHelper h){
        Fixture f=new Fixture(h);List<Zombie> target=new ArrayList<>();BigDecimal[] budget={BigDecimal.ZERO};f.arm(false,()->{budget[0]=f.damage(false);target.add(f.target(f.rayPoint(1,.5),false));});
        h.runAtTickTime(11,()->{eq(h,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(target.getFirst())),budget[0],"same body deduplicated before equal budget partition");h.assertTrue(ParticleLaminate.receipt(f.p).attempts()==1&&ParticleLaminate.receipt(f.p).uniqueTargets()==1,"one identity and one paid native attempt");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void particle_laminate_second_release_captures_low_mode_and_later_crouch_is_irrelevant(GameTestHelper h){
        Fixture f=new Fixture(h);double initial=f.cp();List<Zombie> target=new ArrayList<>();BigDecimal[] budget={BigDecimal.ZERO};f.arm(true,()->{budget[0]=f.damage(true);target.add(f.target(f.rayPoint(1,.5),false));f.p.setShiftKeyDown(false);});
        h.runAtTickTime(11,()->{eq(h,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(target.getFirst())),budget[0],"captured low mode exact .8D");h.assertTrue(ParticleLaminate.receipt(f.p).lowMode(),"stance snapshot survives release");near(h,f.debit(initial),1.64,"low pulse .32 multiplier, same placement costs");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void particle_laminate_corridor_gaps_empty_pulses_and_native_passive_occlusion_are_free(GameTestHelper h){
        Fixture f=new Fixture(h);List<Zombie> target=new ArrayList<>();double[] initial={0};double[] xp={0};
        f.arm(false,()->{
            Vec3 middle=f.rayPoint(1,.5);target.add(f.target(middle.add(.8,0,0),true));target.add(f.target(middle.add(0,0,1),false));
            var passive=EntityType.COW.create(h.getLevel());passive.setNoAi(true);passive.setNoGravity(true);passive.setPos(middle.add(0,-.4,-1));h.getLevel().addFreshEntity(passive);f.entities.add(passive);
            initial[0]=f.cp();f.credits=BigDecimal.ZERO;xp[0]=AbilityStorage.get(f.p).experience.getOrDefault(ParticleLaminate.ID,0.0);
        });
        h.runAtTickTime(11,()->{for(Zombie entity:target)eq(h,hp(entity),cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"gap target and target behind passive untouched");near(h,f.debit(initial[0]),0,"empty/occluded pulse does not spend CP");near(h,AbilityStorage.get(f.p).experience.getOrDefault(ParticleLaminate.ID,0.0),xp[0],"no idle mastery");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void particle_laminate_callbacks_discard_allocations_without_reacquisition_or_refund(GameTestHelper h){
        Fixture f=new Fixture(h);List<Zombie> targets=new ArrayList<>(),newcomers=new ArrayList<>();boolean[] called={false},reentry={true};double[] initial={0};BigDecimal[] budget={BigDecimal.ZERO};
        f.arm(false,()->{
            targets.addAll(f.trio());initial[0]=f.cp();f.credits=BigDecimal.ZERO;budget[0]=f.damage(false);
            f.hook(new Object(){@SubscribeEvent public void skill(SkillAttackEvent e){if(e.player!=f.p||!e.skill.equals(SKILL)||e.target!=targets.getFirst())return;
                called[0]=true;reentry[0]=ParticleLaminate.start(f.p,f.wire(3));e.amount=0;
                Vec3 old=targets.get(1).position();targets.get(1).setPos(old.add(5,0,0));newcomers.add(f.target(old.add(0,targets.get(1).getBbHeight()/2,0),true));
            }});
        });
        h.runAtTickTime(11,()->{
            h.assertTrue(called[0]&&!reentry[0],"real paid callback cannot replace resolving context");eq(h,hp(targets.get(0)),cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"zeroed first allocation discarded");eq(h,hp(targets.get(1)),cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"moved second target misses");
            eq(h,hp(newcomers.getFirst()),cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"replacement never inherits committed allocation");eq(h,cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(targets.get(2))),ParticleLaminateRules.shares(budget[0],3).getLast(),"last identity keeps original exact remainder");
            near(h,f.debit(initial[0]),2,"all callback misses remain paid once");h.assertTrue(ParticleLaminate.start(f.p,f.wire(3)),"rejected reentry did not poison later authentic recall input");h.assertFalse(ParticleLaminate.active(f.p),"active tap recalls free");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void particle_laminate_native_hook_wall_and_amplification_are_spent_misses(GameTestHelper h){
        Fixture f=new Fixture(h);List<Zombie> targets=new ArrayList<>();boolean[] called={false};double[] initial={0};
        f.arm(false,()->{
            targets.addAll(f.trio());initial[0]=f.cp();f.credits=BigDecimal.ZERO;f.hook(new Object(){
                @SubscribeEvent public void skill(SkillAttackEvent e){if(e.skill.equals(SKILL)&&e.target==targets.getFirst()){called[0]=true;e.amount*=2;}}
                @SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==targets.get(1))f.block(BlockPos.containing(f.rayPoint(1,.04)));}
            });
        });
        h.runAtTickTime(11,()->{h.assertTrue(called[0],"actual skill callback reached after one payment");eq(h,hp(targets.get(0)),cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"positive amplification refused");eq(h,hp(targets.get(1)),cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"native hook new upstream wall revokes frozen segment");near(h,f.debit(initial[0]),2,"no event-driven refund");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void particle_laminate_five_opportunities_never_exceed_ten_unique_targets(GameTestHelper h){
        Fixture f=new Fixture(h);List<Zombie> current=new ArrayList<>();List<Zombie> all=new ArrayList<>();List<Integer> counts=new ArrayList<>();
        f.arm(false,()->{current.addAll(f.trio());all.addAll(current);});
        for(int pulse=0;pulse<5;pulse++){
            final int index=pulse;h.runAtTickTime(11+20*pulse,()->{
                var receipt=ParticleLaminate.receipt(f.p);h.assertTrue(receipt!=null&&receipt.pulseIndex()==index+1,"five spaced windows advance once");counts.add(receipt.uniqueTargets());
                if(index<4){for(Zombie target:current)target.setPos(target.position().add(10,0,0));current.clear();current.addAll(f.trio());all.addAll(current);}
            });
        }
        h.runAtTickTime(105,()->{h.assertTrue(counts.equals(List.of(3,6,9,10,10)),"activation identities stop at exactly ten");h.assertTrue(all.stream().filter(t->hp(t).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth())<0).count()==10,"only ten actual distinct victims across all pulses");h.assertFalse(ParticleLaminate.active(f.p),"100 tick field expires without refresh");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=48)
    public static void particle_laminate_unreleased_holds_expire_without_payment(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp();h.assertTrue(ParticleLaminate.start(f.p,f.wire(1)),"authenticated free hold");f.pump();
        h.runAtTickTime(43,()->{h.assertFalse(ParticleLaminate.active(f.p),"first free hold has strict40 tick bound");near(h,f.debit(cp),0,"unreleased hold never charged");h.assertFalse(ParticleLaminate.release(f.p,f.wire(1)),"expired release cannot resurrect");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=210)
    public static void particle_laminate_incomplete_anchor_has_immutable_finite_expiry(GameTestHelper h){
        Fixture f=new Fixture(h);f.place(1,false);long anchored=ParticleLaminate.anchoredAt(f.p);f.pump();
        h.runAtTickTime(180,()->{h.assertTrue(ParticleLaminate.start(f.p,f.wire(2)),"late second placement preview allowed");h.assertTrue(ParticleLaminate.anchoredAt(f.p)==anchored,"preview cannot refresh first anchor");});
        h.runAtTickTime(203,()->{h.assertFalse(ParticleLaminate.active(f.p),"unreleased late second placement does not extend200 tick anchor");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void particle_laminate_recall_and_logout_preserve_owner_pulse_fence(GameTestHelper h){
        Fixture f=new Fixture(h);f.arm(false,()->f.target(f.rayPoint(1,.5),false));
        h.runAtTickTime(11,()->{
            long pulse=f.p.getPersistentData().getCompound("academy:particle_laminate").getLong("pulseAt");double cp=f.cp();
            h.assertTrue(ParticleLaminate.start(f.p,f.wire(3))&&!ParticleLaminate.active(f.p),"active press recalls");near(h,f.cp(),cp,"recall has no cost or refund");
            h.assertTrue(f.p.getPersistentData().getCompound("academy:particle_laminate").getLong("pulseAt")==pulse,"recall does not erase physical pulse fence");
            ParticleLaminate.remove(f.p);h.assertTrue(f.p.getPersistentData().getCompound("academy:particle_laminate").getLong("pulseAt")==pulse,"logout cleanup also preserves persisted pulse fence");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void particle_laminate_shield_direction_uses_actual_first_panel_emitter(GameTestHelper h){
        Fixture f=new Fixture(h);List<Zombie> targets=new ArrayList<>();List<DamageSource> sources=new ArrayList<>();boolean[] block={false};
        f.arm(false,()->{
            targets.add(f.target(f.rayPoint(1,.5),false));Zombie target=targets.getFirst();target.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));target.startUsingItem(InteractionHand.OFF_HAND);face(target,180);
            // Caster moves past target; fixed first-panel arrival remains on target's shield-facing side.
            f.p.setPos(f.rayPoint(1,.9).add(3,-1.62,0));f.p.connection.resetPosition();
            f.hook(new Object(){@SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target)sources.add(e.getSource());}@SubscribeEvent public void shield(LivingShieldBlockEvent e){if(e.getEntity()==target)block[0]=e.getBlocked();}});
        });
        h.runAtTickTime(7,()->face(targets.getFirst(),180));
        h.runAtTickTime(11,()->{h.assertTrue(targets.getFirst().isBlocking()&&block[0],"native raised front shield blocks real panel side");eq(h,hp(targets.getFirst()),cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"caster's opposite side never bypasses shield");h.assertTrue(!sources.isEmpty()&&f.rays().stream().anyMatch(ray->vec(ray.start()).equals(sources.getFirst().getSourcePosition())),"source position is fixed emitter, not current caster");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void particle_laminate_native_trace_refuses_unloaded_and_closed_solid_ties(GameTestHelper h){
        Fixture f=new Fixture(h);Vec3 at=f.p.position().add(0,1,4);BlockPos block=BlockPos.containing(at.add(0,0,1));f.block(block);
        Vec3 start=new Vec3(block.getX()+.5,block.getY()+.5,block.getZ()-1),end=new Vec3(start.x,start.y,block.getZ()-.035);
        var hit=trace(f.p,start,end);h.assertTrue(hit.permitted&&hit.contact&&hit.status==ParticleLaminate.BLOCKED,"exact terminal solid tangent wins native closed ray");
        Vec3 unloaded=new Vec3(12000007,100,12000007);int chunks=h.getLevel().getChunkSource().getLoadedChunksCount();h.assertFalse(h.getLevel().hasChunkAt(BlockPos.containing(unloaded)),"remote fixture starts unloaded");
        h.assertFalse(trace(f.p,unloaded,unloaded.add(0,0,64)).permitted,"first missing prefix refuses before native reads");h.assertTrue(h.getLevel().getChunkSource().getLoadedChunksCount()==chunks,"ray never forces chunks");
        h.assertFalse(trace(f.p,start,start.add(0,0,Math.nextUp(64))).permitted,"64m contract stays closed");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_border",timeoutTicks=10)
    public static void particle_laminate_panel_full_volume_and_native_prefix_obey_border(GameTestHelper h){
        Fixture f=new Fixture(h);var border=h.getLevel().getWorldBorder();double x=border.getCenterX(),z=border.getCenterZ(),size=border.getSize();
        try{border.setCenter(f.p.getX(),f.p.getZ());border.setSize(6);double cp=f.cp();h.assertTrue(ParticleLaminate.start(f.p,f.wire(1)),"owner itself inside border");h.assertFalse(ParticleLaminate.release(f.p,f.wire(1)),"fixed slab outside border cannot be purchased");near(h,f.cp(),cp,"border refusal free");}finally{border.setCenter(x,z);border.setSize(size);}h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void particle_laminate_lifecycle_and_replayed_input_cannot_restore_anchor(GameTestHelper h){
        Fixture f=new Fixture(h);f.place(1,false);var first=ParticleLaminate.panels(f.p);h.assertFalse(first.isEmpty(),"paid immutable anchor exists");
        h.assertFalse(ParticleLaminate.start(f.p,f.wire(1)),"replayed physical press refused");AbilityStorage.get(f.p).presets.edit(0,0,"",id->true);ParticleLaminate.tick(f.p);h.assertFalse(ParticleLaminate.active(f.p),"preset mutation retires field");
        ParticleLaminate.logout(new PlayerEvent.PlayerLoggedOutEvent(f.p));h.assertTrue(ParticleLaminate.panels(f.p).isEmpty(),"logout retains no controller/entity references");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void particle_laminate_native_body_callback_cannot_purchase_detached_anchor(GameTestHelper h){
        Fixture f=new Fixture(h);boolean[] armed={false},called={false};
        var body=new net.minecraft.world.entity.decoration.ArmorStand(EntityType.ARMOR_STAND,h.getLevel()){
            @Override public boolean canBeHitByProjectile(){if(armed[0]){called[0]=true;ParticleLaminate.abort(f.p);}return false;}
        };
        body.setNoGravity(true);body.setPos(f.p.getEyePosition().add(0,-.5,3));h.getLevel().addFreshEntity(body);f.entities.add(body);
        BlockPos wall=BlockPos.containing(f.p.getEyePosition().add(0,0,8));f.block(wall);h.assertTrue(ParticleLaminate.start(f.p,f.wire(1)),"real held preview before callback is armed");double cp=f.cp();armed[0]=true;
        h.assertFalse(ParticleLaminate.release(f.p,f.wire(1)),"native candidate callback retirement refuses detached commit");h.assertTrue(called[0]&&!ParticleLaminate.active(f.p),"actual entity callback canceled owner during acquisition");near(h,f.cp(),cp,"detached acquisition cannot debit CP");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void particle_laminate_native_body_callback_cannot_buy_detached_pulse(GameTestHelper h){
        Fixture f=new Fixture(h);boolean[] armed={false},called={false};double[] initial={0};List<Zombie> targets=new ArrayList<>();
        f.arm(false,()->{
            targets.add(f.target(f.rayPoint(1,.5),false));var body=new net.minecraft.world.entity.decoration.ArmorStand(EntityType.ARMOR_STAND,h.getLevel()){
                @Override public boolean canBeHitByProjectile(){if(armed[0]&&h.getLevel().getServer().overworld().getGameTime()-ParticleLaminate.armedAt(f.p)>=ParticleLaminateRules.WARMUP_TICKS){called[0]=true;ParticleLaminate.abort(f.p);}return false;}
            };
            body.setNoGravity(true);body.setPos(f.rayPoint(1,.12).add(0,-.5,0));h.getLevel().addFreshEntity(body);f.entities.add(body);initial[0]=f.cp();f.credits=BigDecimal.ZERO;
        });
        h.runAtTickTime(7,()->armed[0]=true);
        h.runAtTickTime(11,()->{h.assertTrue(called[0]&&!ParticleLaminate.active(f.p),"real native candidate callback retired field");eq(h,hp(targets.getFirst()),cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),"detached field emits no damage");near(h,f.debit(initial[0]),0,"detached pulse never pays");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void particle_laminate_nonhittable_item_flood_is_bounded_before_filtering(GameTestHelper h){
        Fixture f=new Fixture(h);Vec3 origin=f.p.getEyePosition().add(0,0,4);
        for(int i=0;i<65;i++){
            var item=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),origin.x,origin.y,origin.z,new ItemStack(Items.COBBLESTONE));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);h.getLevel().addFreshEntity(item);f.entities.add(item);
        }
        NativeTrace trace=trace(f.p,origin,origin.add(0,0,1));h.assertFalse(trace.permitted,"65 nonhittable raw spatial candidates fail closed before projectile filtering");h.assertTrue(trace.status==ParticleLaminate.REFUSED,"dense irrelevant entities cannot create an uncharged scan");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_dimension",timeoutTicks=10)
    public static void particle_laminate_dimension_callback_stops_before_destination_collision_query(GameTestHelper h){
        Fixture f=new Fixture(h);var other=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(other!=null,"real alternate native level");
        Vec3 origin=f.p.getEyePosition().add(0,0,3);other.getChunk(BlockPos.containing(origin));BlockPos destinationSolid=BlockPos.containing(origin);other.setBlockAndUpdate(destinationSolid,Blocks.STONE.defaultBlockState());
        boolean[] called={false};var body=new net.minecraft.world.entity.decoration.ArmorStand(EntityType.ARMOR_STAND,h.getLevel()){
            @Override public boolean canBeHitByProjectile(){if(!called[0]){called[0]=true;f.p.teleportTo(other,f.p.getX(),f.p.getY(),f.p.getZ(),Set.of(),0,0);}return false;}
        };
        body.setNoGravity(true);body.setPos(origin.add(0,-.5,0));h.getLevel().addFreshEntity(body);f.entities.add(body);double cp=f.cp();
        try{
            NativeTrace result=trace(f.p,origin,origin.add(0,0,1));h.assertTrue(called[0]&&f.p.serverLevel()==other,"actual eligibility callback changed owner dimension");
            h.assertFalse(result.permitted||result.contact,"trace refuses immediately before observing destination solid under old-world permission");h.assertTrue(result.point.equals(origin),"no unchecked destination prefix advances");near(h,f.cp(),cp,"world mutation grants no purchase");
        }finally{other.setBlockAndUpdate(destinationSolid,Blocks.AIR.defaultBlockState());}h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_far",timeoutTicks=20)
    public static void particle_laminate_three_far_targets_complete_all_native_guards_within_shared_work_limit(GameTestHelper h){
        // Reproduce R3's exact native coordinates independently of randomized GameTest layout.
        Vec3 fixedOrigin=new Vec3(1124056.5,-42,7917348.5);
        int cx=((int)Math.floor(fixedOrigin.x))>>4,cz=((int)Math.floor(fixedOrigin.z))>>4;
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz;z<=cz+5;z++)h.getLevel().getChunk(x,z);
        Fixture f=new Fixture(h,fixedOrigin);List<Zombie> targets=new ArrayList<>();BigDecimal[] budget={BigDecimal.ZERO};
        // getChunk's UNKNOWN ticket lasts one tick. Hold the deliberately extended fixture
        // through the real delayed pulse, independently of template/mock-player tickets.
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz;z<=cz+5;z++)f.holdChunk(x,z);
        f.clearOldFarFixtureTargets();
        List<ParticleLaminateGeometry.Segment> frozenRays=new ArrayList<>();List<NativeTrace> before=new ArrayList<>();List<String> observed=new ArrayList<>();
        f.hook(new Object(){@SubscribeEvent public void skill(SkillAttackEvent e){if(e.player==f.p&&e.skill.equals(SKILL)&&observed.size()<3)observed.add("skill target="+e.target.getId()+" amount="+e.amount);}});
        // Explicit fixture preparation loads the test corridor; production lookup never loads it.
        f.place(1,false);f.pump();h.runAtTickTime(2,()->{
            f.tickPlayer();f.clearOldFarFixtureTargets();f.p.setPos(f.origin.add(0,0,60));f.p.connection.resetPosition();f.place(2,false);f.p.setPos(f.origin.add(3,0,30));f.p.connection.resetPosition();budget[0]=f.damage(false);
            frozenRays.addAll(f.rays());for(int i=0;i<3;i++)targets.add(f.target(f.rayPoint(i,.9),true));
        });
        h.runAtTickTime(7,()->{h.assertTrue(f.heldChunks.stream().allMatch(chunk->h.getLevel().hasChunk(chunk.x,chunk.z)),"all eighteen retained far fixture chunks remain loaded before pulse");for(var ray:frozenRays)before.add(trace(f.p,vec(ray.start()),vec(ray.end())));});
        h.runAtTickTime(11,()->{
            var receipt=ParticleLaminate.receipt(f.p);
            boolean complete=receipt!=null&&receipt.successes()==3&&receipt.attempts()==3;
            if(!complete){String detail=" receipt="+receipt+" active="+ParticleLaminate.active(f.p)+" owner="+f.p.position()+" panels="+ParticleLaminate.panels(f.p)
                        +" rays="+frozenRays+" before="+before+" after="+frozenRays.stream().map(ray->trace(f.p,vec(ray.start()),vec(ray.end()))).toList()
                        +" targets="+targets.stream().map(t->t.getId()+":"+t.position()+":"+t.getBoundingBox()+":hp="+hp(t)).toList()+" observed="+observed;
                org.slf4j.LoggerFactory.getLogger("academy.particle_laminate_test").error("NEW241 far fixture failure:{}",detail);
            }
            h.assertTrue(complete,"all three54m native contacts survive each nested guard; receipt="+(receipt==null?"null":receipt.attempts()+" attempts,"+receipt.successes()+" successes,"+receipt.prefixes()+" prefixes")+"; see bounded NEW241 far fixture log");
            eq(h,targets.stream().map(t->cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(t))).reduce(BigDecimal.ZERO,BigDecimal::add),budget[0],"far pulse still closes exact whole budget");
            h.assertTrue(receipt.prefixes()>1000&&receipt.prefixes()<=ParticleLaminateRules.MAX_PREFIXES,"reported work includes repeated real guard traces under finite shared cap");h.succeed();
        });
    }
    private record NativeTrace(Vec3 point,int status,boolean permitted,boolean contact,String target,int prefixes,boolean workValid) {}
    /** Read-only native geometry seam; reflection never grants an emission or changes live state. */
    private static NativeTrace trace(ServerPlayer p,Vec3 start,Vec3 end){
        return trace(p,start,end,true);
    }
    private static NativeTrace trace(ServerPlayer p,Vec3 start,Vec3 end,boolean includeCaster){
        try{var method=ParticleLaminate.class.getDeclaredMethod("nativeTrace",ServerPlayer.class,Vec3.class,Vec3.class,boolean.class,ParticleLaminateRules.Work.class);method.setAccessible(true);var work=new ParticleLaminateRules.Work();Object hit=method.invoke(null,p,start,end,includeCaster,work);
            var point=hit.getClass().getDeclaredMethod("point");var status=hit.getClass().getDeclaredMethod("status");var permitted=hit.getClass().getDeclaredMethod("permitted");var contact=hit.getClass().getDeclaredMethod("contact");var target=hit.getClass().getDeclaredMethod("target");point.setAccessible(true);status.setAccessible(true);permitted.setAccessible(true);contact.setAccessible(true);target.setAccessible(true);
            Entity body=(Entity)target.invoke(hit);return new NativeTrace((Vec3)point.invoke(hit),(Integer)status.invoke(hit),(Boolean)permitted.invoke(hit),(Boolean)contact.invoke(hit),body==null?"none":body.getClass().getSimpleName()+"#"+body.getId(),work.prefixes(),work.valid());
        }catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}
    }
    private static BigDecimal hp(LivingEntity target){return new BigDecimal(HighPowerCombat.healthText(target));}
    private static void eq(GameTestHelper h,BigDecimal a,BigDecimal b,String why){h.assertTrue(a.compareTo(b)==0,why+": "+a+" / "+b);}
    private static void near(GameTestHelper h,double a,double b,String why){h.assertTrue(Math.abs(a-b)<1e-7,why+": "+a+" / "+b);}
    private static void face(LivingEntity entity,float yaw){entity.setYRot(yaw);entity.setXRot(0);entity.setYHeadRot(yaw);entity.setYBodyRot(yaw);}
    private static Vec3 vec(Vector v){return new Vec3(v.x(),v.y(),v.z());}
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final Vec3 origin;
        final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final Set<BlockPos> blocks=new LinkedHashSet<>();
        final UUID chunkTicket=UUID.randomUUID();final Set<ChunkPos> heldChunks=new LinkedHashSet<>();
        BigDecimal credits=BigDecimal.ZERO;boolean closed,pumping;
        Fixture(GameTestHelper h){this(h,null);}
        Fixture(GameTestHelper h,Vec3 absoluteOrigin){
            this.h=h;var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"PL"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;origin=absoluteOrigin==null?h.absoluteVec(new Vec3(12.5,18,5.5)):absoluteOrigin;p.moveTo(origin.x,origin.y,origin.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();
            h.assertTrue(HighPowerCombat.enroll(p,4),"native authenticated level4 caster");var state=AbilityStorage.get(p);state.selectCategory("meltdowner");state.setLevel(4);state.learn(DualPoint.ID);state.learn(FoldedMirrorArray.ID);state.learn(DisintegrationSweep.ID);state.learn(ParticleLaminate.ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,ParticleLaminate.ID,id->true),"bind approved NEW241 prerequisite chain");HighPowerResources.initializeBudget(p);epoch();
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        void arm(boolean low,Runnable after){place(1,false);pump();h.runAtTickTime(2,()->{tickPlayer();p.setPos(origin.add(0,0,8));p.connection.resetPosition();place(2,low);p.setPos(origin);p.connection.resetPosition();h.assertTrue(ParticleLaminate.panels(p).size()==2,"two native releases arm frozen field");after.run();});}
        void place(long input,boolean low){
            face(p,0);p.setShiftKeyDown(low);BlockPos wall=BlockPos.containing(p.getEyePosition().add(0,0,8));block(wall);
            NativeTrace before=heldChunks.isEmpty()?null:trace(p,p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().normalize().scale(64)),false);
            double cpBefore=cp();var previous=ParticleLaminate.panels(p);
            h.assertTrue(ParticleLaminate.start(p,wire(input)),"real authenticated aim hold starts");boolean released=ParticleLaminate.release(p,wire(input));
            if(!released)org.slf4j.LoggerFactory.getLogger("academy.particle_laminate_test").error("NEW241 placement failure input={} owner={} eye={} look={} traceBefore={} beforePanels={} afterPanels={} cpBefore={} cpAfter={} cooldown={} active={} nativeOwner={} chunks={}",input,p.position(),p.getEyePosition(),p.getLookAngle(),before,previous,ParticleLaminate.panels(p),cpBefore,cp(),AbilityStorage.get(p).cooldowns,ParticleLaminate.active(p),h.getLevel().getEntity(p.getId())==p,heldChunks.stream().map(c->c+":"+h.getLevel().hasChunk(c.x,c.z)+":"+h.getLevel().areEntitiesLoaded(c.toLong())).toList());
            h.assertTrue(released,"loaded visible full slab commits on release; input="+input+", trace="+before+"; see bounded placement failure log");
            h.getLevel().setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());blocks.remove(wall);
        }
        List<ParticleLaminateGeometry.Segment> rays(){var panels=ParticleLaminate.panels(p);var first=panels.getFirst();var second=panels.getLast();return ParticleLaminateGeometry.segments(new ParticleLaminateGeometry.Panel(0,new Vector(first.center().x,first.center().y,first.center().z),new Vector(first.normal().x,first.normal().y,first.normal().z)),new ParticleLaminateGeometry.Panel(1,new Vector(second.center().x,second.center().y,second.center().z),new Vector(second.normal().x,second.normal().y,second.normal().z))).orElseThrow();}
        Vec3 rayStart(int index){return vec(rays().get(index).start());}
        Vec3 rayPoint(int index,double along){var ray=rays().get(index);return vec(ray.start()).lerp(vec(ray.end()),along);}
        List<Zombie> trio(){List<Zombie> targets=new ArrayList<>();for(int i=0;i<3;i++)targets.add(target(rayPoint(i,.13),true));return targets;}
        Zombie target(Vec3 center,boolean baby){
            Zombie target=EntityType.HUSK.create(h.getLevel());target.setBaby(baby);target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setPos(center.add(0,-target.getBbHeight()/2,0));h.getLevel().addFreshEntity(target);
            h.assertTrue(HighPowerCombat.enroll(target,5),"native supported hostile target");target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health",cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().toPlainString());
            if(!heldChunks.isEmpty())target.getPersistentData().putString("academy:particle_laminate_far_fixture","r3-reproducer");entities.add(target);return target;
        }
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        double cp(){return HighPowerResources.cp(p);}
        BigDecimal damage(boolean low){return ParticleLaminateRules.damage(HighPowerGrowth.values(p).damage(),low);}
        void block(BlockPos pos){h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());blocks.add(pos);}
        void holdChunk(int x,int z){ChunkPos chunk=new ChunkPos(x,z);if(heldChunks.add(chunk)){h.getLevel().getChunkSource().addRegionTicket(FAR_FIXTURE_TICKET,chunk,2,chunkTicket);h.getLevel().getChunk(x,z);}}
        /** Only this isolated fixed arena's tagged targets, or the exact verified R3/R4 debris. */
        void clearOldFarFixtureTargets(){
            AABB arena=new AABB(origin.add(-1,-1,59),origin.add(1,3,62));List<Zombie> loaded=new ArrayList<>();
            h.getLevel().getEntities(EntityTypeTest.forClass(Zombie.class),arena,e->true,loaded,17);h.assertTrue(loaded.size()<=16,"bounded far fixture cleanup candidate count");
            List<Vec3> legacyPositions=List.of(origin.add(.384,.49249999284744,60.23880962),origin.add(0,1.13249999284744,60.23880962),origin.add(-.384,1.77249999284744,60.23880962));
            int removed=0;
            for(Zombie target:loaded){
                boolean signature=target.getType()==EntityType.HUSK&&target.isBaby()&&target.isNoAi()&&target.isNoGravity()&&HighPowerCombat.enabled(target)&&HighPowerCombat.level(target)==5;
                boolean ours=target.getPersistentData().getString("academy:particle_laminate_far_fixture").equals("r3-reproducer")
                        ||legacyPositions.stream().anyMatch(at->at.distanceToSqr(target.position())<1e-12);
                if(signature&&ours){target.discard();removed++;}
            }
            if(removed>0)org.slf4j.LoggerFactory.getLogger("academy.particle_laminate_test").info("NEW241 removed {} verified stale far fixture targets from retained test arena",removed);
        }
        void tickPlayer(){if(!closed){BigDecimal before=new BigDecimal(cp());p.doTick();credits=credits.add(new BigDecimal(cp()).subtract(before));}}
        void pump(){if(pumping)return;pumping=true;h.onEachTick(this::tickPlayer);}
        double debit(double initial){return new BigDecimal(initial).add(credits).subtract(new BigDecimal(cp())).doubleValue();}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);blocks.forEach(pos->h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState()));ParticleLaminate.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);heldChunks.forEach(chunk->h.getLevel().getChunkSource().removeRegionTicket(FAR_FIXTURE_TICKET,chunk,2,chunkTicket));heldChunks.clear();channel.finishAndReleaseAll();}
    }
}
