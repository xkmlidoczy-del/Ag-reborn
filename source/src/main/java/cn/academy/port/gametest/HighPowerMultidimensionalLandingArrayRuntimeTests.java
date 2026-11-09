package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.DamageMitigation;
import cn.academy.port.highpower.core.DefenseProfile;
import cn.academy.port.highpower.core.MultidimensionalLandingArrayRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW351 native server acceptance, independent of controller internals.
 * The declared 64x8x112 template contains every changed block, entity and explicit chunk ticket.
 * Mocked-network resident players, learned flags, six original needles, supported floors and stationary
 * native husks are fixtures. Genuine input epochs create all attack authority; native world entity ticks
 * produce all array contacts. These tests make no claim about client input/rendering or natural learning.
 */
@GameTestHolder("academy_highpower_array")
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid="academy")
public final class HighPowerMultidimensionalLandingArrayRuntimeTests {
    private static final String TEMPLATE="highpower_landing_array", BATCH="highpower_multidimensional_landing_array";
    private static final String ID=MultidimensionalLandingArray.ID, SKILL="teleporter.multidimensional_landing_array";
    private static final String FENCES="academy:multidimensional_landing_array_fences";
    private static final TicketType<UUID> TICKET=TicketType.create("academy_landing_array_fixture",UUID::compareTo);
    private HighPowerMultidimensionalLandingArrayRuntimeTests() {}
    private static BigDecimal n(String value){return new BigDecimal(value);}
    private static BigDecimal hp(LivingEntity e){return HighPowerCombat.enabled(e)?n(HighPowerCombat.healthText(e)):new BigDecimal(Float.toString(e.getHealth()));}
    private static void eq(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+": "+actual+" != "+expected);}
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-6,why+": "+actual+" != "+expected);}
    /** Oracle intentionally does not call the production total/share helpers. */
    private static BigDecimal whole(Fixture f){return HighPowerGrowth.values(f.p).damage().multiply(n("4")).min(CombatAmounts.CAP);}
    private static BigDecimal share(BigDecimal total,int i,int count){BigDecimal each=total.divide(BigDecimal.valueOf(count),CombatAmounts.MAX_ABSOLUTE_SCALE,RoundingMode.DOWN);return i==count-1?total.subtract(each.multiply(BigDecimal.valueOf(count-1))):each;}
    private static double cpShare(double total,int i,int count){double each=total/count,used=0;for(int j=0;j<count-1;j++)used+=each;return i==count-1?total-used:each;}
    private static BigDecimal discounted(Fixture f){BigDecimal original=share(whole(f),0,f.shots);return original.multiply(n("5")).divide(BigDecimal.valueOf(original.doubleValue()),MathContext.DECIMAL128);}

    @SubscribeEvent public static void installTemplate(net.neoforged.neoforge.event.level.LevelEvent.Load event){
        if(!net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return;
        CompoundTag root=new CompoundTag(),cell=new CompoundTag(),air=new CompoundTag();ListTag size=new ListTag(),position=new ListTag(),blocks=new ListTag(),palette=new ListTag();
        for(int dimension:new int[]{64,8,112})size.add(IntTag.valueOf(dimension));for(int axis=0;axis<3;axis++)position.add(IntTag.valueOf(0));
        cell.put("pos",position);cell.putInt("state",0);blocks.add(cell);air.putString("Name","minecraft:air");palette.add(air);
        root.put("size",size);root.put("blocks",blocks);root.put("palette",palette);root.put("entities",new ListTag());
        level.getStructureManager().getOrCreate(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy_highpower_array",TEMPLATE)).load(level.registryAccess().lookupOrThrow(Registries.BLOCK),root);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=14)
    public static void native_request_codec_preserves_new351_actions_and_original_utf_bounds(GameTestHelper h){
        Fixture f=new Fixture(h,1,true);double cp=f.cp();String representative=f.wire(1),maxAuthenticated=f.wire(Long.MAX_VALUE);
        String maximalForm=new HighPowerInput.Input(3,Long.MAX_VALUE,Integer.MAX_VALUE,Long.MAX_VALUE).wire();
        h.assertValueEqual(maximalForm.length(),52,"maximal canonical slot/input/actor/epoch payload stays inside the existing 64-character bound");
        h.assertTrue(HighPowerInput.parse(maximalForm)!=null,"maximal canonical authentication fields survive their original parser limits");
        for(String action:List.of("hp_mla_start","hp_mla_release","hp_mla_abort")){
            h.assertTrue(action.length()<=32,"every NEW351 action fits the unchanged action field");
            for(String value:List.of(representative,maxAuthenticated,maximalForm)){
                AcademyNetwork.Request decoded=requestRoundTrip(h,new AcademyNetwork.Request(action,value));
                h.assertTrue(decoded.action().equals(action)&&decoded.value().equals(value),"actual Request.CODEC preserves every NEW351 action and complete input identity");
                if(!value.equals(maximalForm))h.assertTrue(HighPowerBracerSession.authenticated(f.p,HighPowerInput.parse(decoded.value())),"representative and maximum-sequence payload remain authenticated for the actual resident owner");
            }
        }
        h.assertTrue(f.cp()==cp&&f.needles()==6&&f.spawns.media.isEmpty()&&MultidimensionalLandingArray.preparedPoints(f.p)==0,"codec round trips never themselves grant server admission or mutate resources");
        AcademyNetwork.Request boundary=requestRoundTrip(h,new AcademyNetwork.Request("a".repeat(32),"v".repeat(64)));
        h.assertTrue(boundary.action().length()==32&&boundary.value().length()==64,"existing exact 32/64 UTF limits remain accepted without truncation");
        String oldRelease="hp_multidimensional_array_release";h.assertValueEqual(oldRelease.length(),33,"actual disconnected client action exceeds the original 32-character bound by one");
        requestCodecRefuses(h,new AcademyNetwork.Request(oldRelease,representative),"obsolete 33-character NEW351 release remains refused");
        requestCodecRefuses(h,new AcademyNetwork.Request("hp_mla_start","v".repeat(65)),"65-character value remains refused without widening shared protocol bounds");
        h.assertFalse(HighPowerBracerSession.authenticated(f.p,HighPowerInput.parse(maximalForm)),"synthetic maximum actor/epoch form roundtrip cannot impersonate the resident owner");
        for(String action:List.of("hp_mla_start","hp_mla_release","hp_mla_abort"))h.assertFalse(AcademyGameplay.requestFromClient(f.p,requestRoundTrip(h,new AcademyNetwork.Request(action,maximalForm))),"successfully decoded forged identity still fails actual server admission");
        f.aim(f.points.getFirst());
        h.assertTrue(AcademyGameplay.requestFromClient(f.p,requestRoundTrip(h,new AcademyNetwork.Request("hp_mla_start",representative))),"actual encoded/decoded NEW351 start reaches authenticated server dispatch");
        h.assertValueEqual(MultidimensionalLandingArray.preparedPoints(f.p),0,"decoded genuine press retains release-only mark authority");
        h.assertTrue(AcademyGameplay.requestFromClient(f.p,requestRoundTrip(h,new AcademyNetwork.Request("hp_mla_release",representative))),"actual encoded/decoded matching NEW351 release reaches native mark capture");
        h.assertValueEqual(MultidimensionalLandingArray.preparedPoints(f.p),1,"genuine decoded release creates exactly one native mark");
        h.assertTrue(AcademyGameplay.requestFromClient(f.p,requestRoundTrip(h,new AcademyNetwork.Request("hp_mla_abort",f.wire(2)))),"actual encoded/decoded NEW351 abort reaches authenticated server cancellation");
        h.assertTrue(MultidimensionalLandingArray.preparedPoints(f.p)==0&&f.cp()==cp&&f.needles()==6&&f.spawns.media.isEmpty(),"bounded genuine wire flow finishes free without publication or resource side effects");h.succeed();
    }
    private static AcademyNetwork.Request requestRoundTrip(GameTestHelper h,AcademyNetwork.Request request){
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{AcademyNetwork.Request.CODEC.encode(buffer,request);AcademyNetwork.Request decoded=AcademyNetwork.Request.CODEC.decode(buffer);h.assertTrue(buffer.readableBytes()==0,"native request codec consumes exactly the complete serialized frame");return decoded;}finally{buffer.release();}
    }
    private static void requestCodecRefuses(GameTestHelper h,AcademyNetwork.Request request,String why){
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());boolean encodeRefused=false,decodeRefused=false;
        try{
            try{AcademyNetwork.Request.CODEC.encode(buffer,request);}catch(EncoderException expected){encodeRefused=true;}
            buffer.clear();buffer.writeUtf(request.action(),request.action().length());buffer.writeUtf(request.value(),request.value().length());
            try{AcademyNetwork.Request.CODEC.decode(buffer);}catch(DecoderException expected){decodeRefused=true;}
        }finally{buffer.release();}
        h.assertTrue(encodeRefused&&decodeRefused,why+" on both native encode and decode paths");
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110) public static void one_target_keeps_six_frozen_native_attempts(GameTestHelper h){countCase(h,1);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110) public static void two_targets_keep_six_frozen_native_attempts(GameTestHelper h){countCase(h,2);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110) public static void three_targets_keep_six_frozen_native_attempts(GameTestHelper h){countCase(h,3);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110) public static void four_targets_keep_six_frozen_native_attempts(GameTestHelper h){countCase(h,4);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110) public static void five_targets_keep_six_frozen_native_attempts(GameTestHelper h){countCase(h,5);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110) public static void six_targets_conserve_one_whole_round_budget(GameTestHelper h){countCase(h,6);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110) public static void seven_targets_cycle_six_physical_custody_slots(GameTestHelper h){countCase(h,7);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110) public static void eight_targets_cycle_six_physical_custody_slots(GameTestHelper h){countCase(h,8);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110) public static void nine_targets_keep_nine_exact_native_shares(GameTestHelper h){countCase(h,9);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110) public static void ten_targets_keep_ten_exact_native_shares(GameTestHelper h){countCase(h,10);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110) public static void eleven_targets_keep_eleven_exact_native_shares(GameTestHelper h){countCase(h,11);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110) public static void twelve_targets_keep_twelve_exact_native_shares(GameTestHelper h){countCase(h,12);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=115) public static void thirteen_targets_keep_thirteen_exact_native_shares(GameTestHelper h){countCase(h,13);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=120) public static void fourteen_targets_keep_fourteen_exact_native_shares(GameTestHelper h){countCase(h,14);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=125) public static void fifteen_targets_keep_fifteen_exact_native_shares(GameTestHelper h){countCase(h,15);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=130) public static void sixteen_targets_receive_the_final_exact_decimal_residual(GameTestHelper h){countCase(h,16);}
    private static void countCase(GameTestHelper h,int count){
        Fixture f=new Fixture(h,count,true);BigDecimal total=whole(f);Map<UUID,BigDecimal> before=new HashMap<>();Map<UUID,Observation> observations=new HashMap<>();
        for(Zombie target:f.targets){before.put(target.getUUID(),hp(target));observations.put(target.getUUID(),f.observe(target));}
        List<MultidimensionalLandingArray.TargetVisual> frozen=new ArrayList<>();
        f.begin(()->{frozen.addAll(MultidimensionalLandingArray.frozenTargets(f.p));h.assertValueEqual(frozen.size(),count,"native cone freezes exactly T visible hostile identities");
            h.assertTrue(frozen.getFirst().uuid().equals(f.targets.getFirst().getUUID()),"independent genuine crosshair primary stays first");
            h.assertValueEqual(MultidimensionalLandingArray.plannedNeedles(f.p),Math.max(6,count),"N=max(6,T)");
        },()->{
            h.assertTrue(MultidimensionalLandingArray.frozenTargets(f.p).equals(frozen),"confirmation preserves the free preview identities and endpoints");
            for(int i=1;i<f.shots;i++){final int shot=i;f.post(4*i-1,()->h.assertValueEqual(f.spawns.media.size(),shot,"next shot is never early"));f.post(4*i,()->h.assertValueEqual(f.spawns.media.size(),shot+1,"one shot follows each four observed native ticks"));}
            f.post(4*(f.shots-1)+20,()->{
                h.assertValueEqual(f.spawns.media.size(),f.shots,"exact frozen attempt count reached");BigDecimal allocated=BigDecimal.ZERO,loss=BigDecimal.ZERO;
                for(int i=0;i<f.shots;i++){
                    BorrowedNeedleEntity medium=f.spawns.media.get(i);Vec3 origin=f.origins.get(i%6),endpoint=frozen.get(i%count).position();
                    h.assertTrue(medium.shotIndex()==i&&medium.slotIndex()==i%6,"physical shot i reuses exactly custody slot i%6");
                    h.assertValueEqual(f.spawns.ticks.get(i),f.started+4*i,"native publication has no catch-up cadence");
                    h.assertTrue(f.spawns.origins.get(i).equals(origin)&&f.spawns.motions.get(i).equals(endpoint.subtract(origin).normalize().scale(1.6)),"native shot uses source i%6 and frozen target i%T");
                    allocated=allocated.add(share(total,i,f.shots));
                }
                eq(h,allocated,total,"independent decimal allocations sum to min(4D,CAP), including final residual");
                int posts=0;for(var visual:frozen){Zombie target=f.target(visual.uuid());Observation observation=observations.get(visual.uuid());
                    eq(h,hp(target),before.get(visual.uuid()).subtract(observation.applied),"each accepted native contact spends only its immutable assigned share");
                    loss=loss.add(observation.applied);posts+=observation.posts;
                    if(count>=6){h.assertValueEqual(observation.posts,1,"one actual native hurt per distinct frozen target");eq(h,observation.applied,share(total,frozen.indexOf(visual),f.shots),"target owns its original exact share");}
                }
                if(count>=6)eq(h,loss,total,"actual distinct-target HP debits conserve the whole round budget");
                var r=MultidimensionalLandingArray.receipt(f.p);h.assertTrue(r.spent()==f.shots&&r.published()==f.shots&&r.paid()==f.shots&&r.contacts()==f.shots&&r.hurts()==posts,"terminal receipt agrees with independent native contacts and hurt callbacks: "+r);
                h.assertTrue(!MultidimensionalLandingArray.active(f.p)&&f.needles()==6&&BorrowedNeedleEscrow.retainedCount(f.p)==0,"six originals return once only when the entire round ends");
                h.assertTrue(f.spawns.maxActive<=6&&f.spawns.maxActive>0&&f.spawns.heldWholeRound,"no more than six real flights coexist; all six originals remain retained during the round");
                h.assertValueEqual(f.budget.debitCount,f.shots,"every native publication has one witnessed represented CP debit");f.budget.conserved();
                near(h,AbilityStorage.get(f.p).exp(ID),0,"the round grants no automatic mastery");
            });
        });f.finish(4*(f.shots-1)+53);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void six_genuine_short_releases_are_free_and_keep_first_mark_lifetime(GameTestHelper h){
        Fixture f=new Fixture(h,1,true);double cp=f.cp();f.ready(()->{
            h.assertTrue(MultidimensionalLandingArray.preparedPoints(f.p)==6&&MultidimensionalLandingArray.marked(f.p)&&!MultidimensionalLandingArray.active(f.p),"six native floor marks remain preparation");
            h.assertValueEqual(MultidimensionalLandingArray.remainingTicks(f.p),376,"later marks retain the first anchor's 400 observed tick lifetime");
            for(int i=0;i<6;i++)h.assertTrue(f.origins.get(i).equals(f.surfaces.get(i).add(0,1.2,0)),"each origin is the real native UP contact lifted 1.2m");
            near(h,f.cp(),cp,"six marks cost no CP");h.assertTrue(f.needles()==6&&BorrowedNeedleEscrow.retainedCount(f.p)==0&&f.spawns.media.isEmpty(),"preparation does not escrow or project originals");h.assertTrue(f.p.position().equals(f.source),"surface marks never move the player");
        });f.finish(25);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=34)
    public static void seventh_release_freezes_free_preview_and_eighth_release_admits(GameTestHelper h){
        Fixture f=new Fixture(h,1,true);f.begin(()->{h.assertTrue(f.inputs==7&&MultidimensionalLandingArray.previewed(f.p)&&!MultidimensionalLandingArray.active(f.p),"only the seventh genuine release freezes targets");
            h.assertTrue(f.needles()==6&&BorrowedNeedleEscrow.retainedCount(f.p)==0&&f.spawns.media.isEmpty(),"the entire preview stays free");},()->{
            h.assertTrue(f.inputs==8&&MultidimensionalLandingArray.active(f.p)&&f.spawns.media.size()==1&&BorrowedNeedleEscrow.retainedCount(f.p)==6&&f.needles()==0,"eighth genuine release reserves all six originals and publishes one physical shot");
            h.assertFalse(MultidimensionalLandingArray.receipt(f.p).freeOrigin(),"short release admits fixed-origin mode");MultidimensionalLandingArray.cancelActive(f.p);
        });f.finish(29);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=34)
    public static void preview_and_confirmation_input_replay_never_reacquire_or_repay(GameTestHelper h){
        Fixture f=new Fixture(h,3,true);List<MultidimensionalLandingArray.TargetVisual> frozen=new ArrayList<>();
        f.begin(()->{frozen.addAll(MultidimensionalLandingArray.frozenTargets(f.p));String wire=f.wire(f.inputs);double cp=f.cp();
            h.assertFalse(MultidimensionalLandingArray.press(f.p,wire)||MultidimensionalLandingArray.release(f.p,wire),"seventh input cannot also confirm");h.assertTrue(f.cp()==cp&&f.spawns.media.isEmpty(),"preview replay never debits");f.aim(f.source.add(15,0,0));
        },()->{String wire=f.wire(f.inputs);double cp=f.cp();h.assertFalse(MultidimensionalLandingArray.release(f.p,wire)||MultidimensionalLandingArray.press(f.p,wire),"eighth input cannot publish again");
            h.assertTrue(f.cp()==cp&&f.spawns.media.size()==1&&MultidimensionalLandingArray.frozenTargets(f.p).equals(frozen),"fresh confirmation preserves frozen preview despite a changed aim");MultidimensionalLandingArray.cancelActive(f.p);
        });f.finish(29);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=14)
    public static void press_alone_mismatched_release_and_stale_epoch_have_no_authority(GameTestHelper h){
        Fixture f=new Fixture(h,1,true);f.aim(f.points.getFirst());String wire=f.wire(++f.inputs);h.assertTrue(MultidimensionalLandingArray.press(f.p,wire),"genuine owner press begins gesture");
        h.assertValueEqual(MultidimensionalLandingArray.preparedPoints(f.p),0,"press alone creates no mark");
        h.assertFalse(MultidimensionalLandingArray.release(f.p,f.wire(f.inputs+1)),"different input number cannot finish an owner's press");
        var input=HighPowerInput.parse(wire);String foreign=new HighPowerInput.Input(input.slot(),input.input(),input.actor(),input.epoch()+1).wire();
        h.assertFalse(MultidimensionalLandingArray.release(f.p,foreign),"other epoch cannot close current gesture");
        h.assertTrue(MultidimensionalLandingArray.release(f.p,wire),"matching genuine release alone captures one point");h.assertValueEqual(MultidimensionalLandingArray.preparedPoints(f.p),1,"exactly one authentic mark");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=42)
    public static void holding_six_real_ticks_after_preview_admits_supported_free_point_mode(GameTestHelper h){
        Fixture f=new Fixture(h,1,true);f.preview(()->{f.aim(f.points.get(2));var surface=f.nativeSurface();Vec3 player=f.p.position();f.post(4,()->{
            h.assertTrue(f.press(),"fresh held confirmation starts after free preview");String wire=f.wire(f.inputs);
            for(int i=0;i<200;i++)MultidimensionalLandingArray.tick(f.p);h.assertValueEqual(MultidimensionalLandingArray.heldTicks(f.p),0,"same tick calls never accelerate hold");
            f.post(5,()->h.assertTrue(MultidimensionalLandingArray.heldTicks(f.p)==5&&!MultidimensionalLandingArray.active(f.p)&&f.spawns.media.isEmpty(),"five physical ticks remain free"));
            f.post(6,()->{f.spawns.payment();h.assertTrue(MultidimensionalLandingArray.active(f.p)&&MultidimensionalLandingArray.receipt(f.p).freeOrigin()&&f.spawns.media.size()==1,"sixth physical hold tick admits free-point mode");
                h.assertTrue(f.spawns.origins.getFirst().equals(surface.add(0,1.2,0))&&f.p.position().equals(player),"actual supported surface launches a needle and never teleports player");
                h.assertTrue(MultidimensionalLandingArray.release(f.p,wire),"matching held release ends free-point authority");h.assertTrue(!MultidimensionalLandingArray.active(f.p)&&f.needles()==6,"release returns retained originals once");});
        });});f.finish(36);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=42)
    public static void held_free_point_mode_refuses_unsupported_aim_without_escrow_or_cost(GameTestHelper h){
        Fixture f=new Fixture(h,1,true);f.preview(()->f.post(4,()->{f.aim(f.source.add(0,40,10));double cp=f.cp();h.assertTrue(f.press(),"held request records genuine input");
            f.post(6,()->{h.assertTrue(!MultidimensionalLandingArray.active(f.p)&&f.spawns.media.isEmpty()&&BorrowedNeedleEscrow.retainedCount(f.p)==0&&f.needles()==6,"unsupported point never starts a round");
                h.assertTrue(MultidimensionalLandingArray.receipt(f.p).reason().equals("unsupported_origin"),"free mode reports the supported-surface boundary");near(h,f.cp(),cp,"unsupported free point costs no CP");});}));f.finish(36);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=42)
    public static void crouch_hold_clear_has_priority_over_preview_and_free_point_admission(GameTestHelper h){
        Fixture f=new Fixture(h,1,true);f.preview(()->f.post(4,()->{f.p.setShiftKeyDown(true);f.aim(f.points.getFirst());double cp=f.cp();h.assertTrue(f.press(),"genuine crouch hold starts");
            f.post(6,()->{h.assertFalse(MultidimensionalLandingArray.active(f.p)||MultidimensionalLandingArray.marked(f.p),"crouch clear ends preparation before any held admission");
                h.assertTrue(MultidimensionalLandingArray.receipt(f.p).reason().equals("cleared")&&f.spawns.media.isEmpty()&&f.needles()==6,"clear never publishes or borrows");near(h,f.cp(),cp,"clear costs no CP");});}));f.finish(36);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=415)
    public static void preparation_expires_on_four_hundred_physical_ticks_without_acceleration(GameTestHelper h){
        Fixture f=new Fixture(h,1,true);f.mark(0);for(int i=0;i<1000;i++)MultidimensionalLandingArray.tick(f.p);
        h.assertValueEqual(MultidimensionalLandingArray.remainingTicks(f.p),400,"duplicate observations do not age marks");
        f.post(399,()->h.assertTrue(MultidimensionalLandingArray.marked(f.p)&&MultidimensionalLandingArray.remainingTicks(f.p)==1,"first mark survives its last eligible tick"));
        f.post(400,()->h.assertTrue(!MultidimensionalLandingArray.marked(f.p)&&MultidimensionalLandingArray.receipt(f.p).reason().equals("expired")&&f.needles()==6&&f.spawns.media.isEmpty(),"physical expiry stays free and terminal"));f.finish(401);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void request_fence_advances_once_per_physical_tick(GameTestHelper h){
        Fixture f=new Fixture(h,1,true);f.mark(0);double cp=f.cp();f.aim(f.points.get(1));h.assertFalse(f.tap(),"second same-tick mark is fenced");
        for(int i=0;i<100;i++)MultidimensionalLandingArray.pre(new ServerTickEvent.Pre(()->true,f.p.server));
        h.assertValueEqual(f.p.getPersistentData().getCompound(FENCES).getInt("request"),4,"duplicate Pre events do not drain the fence");
        f.post(3,()->h.assertTrue(f.p.getPersistentData().getCompound(FENCES).getInt("request")==1,"three observed ticks leave one request tick"));
        f.post(4,()->{h.assertTrue(f.tap()&&MultidimensionalLandingArray.preparedPoints(f.p)==2,"fourth physical tick permits a fresh release");near(h,f.cp(),cp,"fence rejection costs no CP");});f.finish(5);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=38)
    public static void duplicate_controller_observations_never_catch_up_or_repay(GameTestHelper h){
        Fixture f=new Fixture(h,1,true);f.begin(()->{double cp=f.cp();for(int i=0;i<1000;i++)MultidimensionalLandingArray.tick(f.p);for(int i=0;i<100;i++)MultidimensionalLandingArray.pre(new ServerTickEvent.Pre(()->true,f.p.server));
            h.assertTrue(f.spawns.media.size()==1&&f.cp()==cp&&MultidimensionalLandingArray.remainingTicks(f.p)==160,"same-tick calls do not emit, repay or age the round");
            f.post(3,()->h.assertValueEqual(f.spawns.media.size(),1,"no queued second shot before four actual ticks"));f.post(4,()->{h.assertValueEqual(f.spawns.media.size(),2,"one physical due tick emits one next shot");MultidimensionalLandingArray.cancelActive(f.p);});});f.finish(33);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=34) public static void five_originals_refuse_seventh_preview_without_cost(GameTestHelper h){stockRefusal(h,0);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=34) public static void decorated_originals_refuse_seventh_preview_without_cost(GameTestHelper h){stockRefusal(h,1);}
    private static void stockRefusal(GameTestHelper h,int mode){Fixture f=new Fixture(h,1,true);if(mode==0)f.p.getOffhandItem().shrink(1);else f.p.getOffhandItem().set(DataComponents.CUSTOM_NAME,Component.literal("declared decorated original"));
        f.ready(()->{f.aim(f.targets.getFirst().getBoundingBox().getCenter());double cp=f.cp();h.assertFalse(f.tap(),"preview requires all six plain originals");
            h.assertTrue(f.spawns.media.isEmpty()&&BorrowedNeedleEscrow.retainedCount(f.p)==0&&!MultidimensionalLandingArray.previewed(f.p),"refusal never partially freezes or escrows");near(h,f.cp(),cp,"stock refusal is free");});f.finish(25);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=36)
    public static void stock_lost_after_preview_refuses_whole_admission(GameTestHelper h){Fixture f=new Fixture(h,9,true);f.preview(()->{f.p.getOffhandItem().shrink(1);double cp=f.cp();f.post(4,()->{
        h.assertFalse(f.tap(),"five originals cannot confirm even an N=9 plan");h.assertTrue(f.spawns.media.isEmpty()&&BorrowedNeedleEscrow.retainedCount(f.p)==0&&f.needles()==5,"refusal neither shrinks the plan nor reserves a partial round");near(h,f.cp(),cp,"failed admission costs no CP");});});f.finish(29);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=36)
    public static void insufficient_shared_cp_refuses_whole_round_before_reservation(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.preview(()->f.post(4,()->{var budget=f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);budget.putDouble("cp",0);f.p.getPersistentData().put(HighPowerResources.DATA_KEY,budget);
        h.assertFalse(f.tap(),"whole-round shared CP gate precedes borrowing");h.assertTrue(f.cp()==0&&f.needles()==6&&BorrowedNeedleEscrow.retainedCount(f.p)==0&&f.spawns.media.isEmpty(),"declared depleted resource fixture never partially reserves or debits");}));f.finish(29);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=34)
    public static void first_paid_shot_sets_actual_twenty_tick_cast_and_growth_cooldown_fences(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.begin(()->{var fences=f.p.getPersistentData().getCompound(FENCES);
        h.assertTrue(fences.getInt("cast")==20&&fences.getInt("cooldown")==MultidimensionalLandingArrayRules.cooldownTicks(HighPowerGrowth.values(f.p).position()),"first actual payment sets bounded cast and continuous-Q cooldown fences");
        MultidimensionalLandingArray.cancelActive(f.p);double cp=f.cp();h.assertTrue(f.needles()==6&&f.cp()==cp,"cancel returns originals without refunding actual CP debit");});f.finish(29);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=34)
    public static void cancel_before_admission_leaves_all_six_originals_unreserved(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.preview(()->{double cp=f.cp();MultidimensionalLandingArray.cancelActive(f.p);
        h.assertTrue(f.cp()==cp&&f.needles()==6&&BorrowedNeedleEscrow.retainedCount(f.p)==0&&f.spawns.media.isEmpty()&&!MultidimensionalLandingArray.marked(f.p),"free cancel ends all target and source authority");});f.finish(25);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=50)
    public static void cancel_after_two_publications_returns_six_once_and_stops_future_debits(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.begin(()->f.post(4,()->{double cp=f.cp();MultidimensionalLandingArray.cancelActive(f.p);
        h.assertTrue(f.needles()==6&&BorrowedNeedleEscrow.retainedCount(f.p)==0&&f.spawns.media.stream().noneMatch(BorrowedNeedleEntity::isLeaseActive)&&f.cp()==cp,"cancel revokes all flights and returns the entire retained stock without CP refund");
        MultidimensionalLandingArray.cancelActive(f.p);BorrowedNeedleEscrow.claim(f.p);h.assertValueEqual(f.needles(),6,"repeated cancel or claim never returns a seventh original");f.post(9,()->{h.assertTrue(f.spawns.media.size()==2&&f.budget.debitCount==2,"canceled queue never emits or debits again");f.budget.conserved();});}));f.finish(43);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=34) public static void slot_mapping_change_after_preview_ends_admission_authority(GameTestHelper h){contextCase(h,0);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=34) public static void preset_revision_change_after_preview_ends_admission_authority(GameTestHelper h){contextCase(h,1);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=34) public static void ability_deactivation_after_preview_ends_admission_authority(GameTestHelper h){contextCase(h,2);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=34) public static void inherited_owner_epoch_after_session_rotation_cannot_confirm(GameTestHelper h){contextCase(h,3);}
    private static void contextCase(GameTestHelper h,int mode){Fixture f=new Fixture(h,1,true);f.preview(()->{String old=f.wire(f.inputs+1);var state=AbilityStorage.get(f.p);
        if(mode==0)state.presets.edit(0,0,SpatialPincerCorridor.ID,state::learned);else if(mode==1)state.presets.edit(0,1,SafeLandingProbe.ID,state::learned);else if(mode==2)state.activated=false;else {LegacySingleKeyOwnerEpoch.forget(f.p);LegacySingleKeyOwnerEpoch.current(f.p,f.p.connection,f.p.serverLevel(),state);}
        f.post(4,()->{double cp=f.cp();if(mode==3)h.assertFalse(MultidimensionalLandingArray.press(f.p,old)||MultidimensionalLandingArray.release(f.p,old),"old owner/session epoch cannot confirm");else h.assertFalse(f.tap(),"changed context cannot confirm frozen authority");
            h.assertTrue(f.spawns.media.isEmpty()&&f.needles()==6&&BorrowedNeedleEscrow.retainedCount(f.p)==0,"context rejection remains free");near(h,f.cp(),cp,"changed owner context never pays");});});f.finish(29);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=14)
    public static void late_learning_does_not_authorize_a_press_from_an_unmapped_slot(GameTestHelper h){Fixture f=new Fixture(h,1,true);var state=AbilityStorage.get(f.p);state.presets.edit(0,0,SpatialPincerCorridor.ID,state::learned);f.aim(f.points.getFirst());
        h.assertFalse(f.tap(),"learned NEW351 does not inherit NEW341 slot authority");h.assertTrue(f.needles()==6&&f.spawns.media.isEmpty()&&MultidimensionalLandingArray.preparedPoints(f.p)==0,"unmapped input remains free and markless");
        h.assertTrue(state.presets.edit(0,0,ID,state::learned)&&f.tap(),"explicit fresh NEW351 mapping admits a new genuine input");h.assertValueEqual(MultidimensionalLandingArray.preparedPoints(f.p),1,"fresh mapped input creates only one mark");h.succeed();}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=14)
    public static void other_owner_identity_and_unmapped_slot_cannot_borrow_saved_marks(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.mark(0);var genuine=HighPowerInput.parse(f.wire(++f.inputs));
        h.assertFalse(MultidimensionalLandingArray.press(f.p,new HighPowerInput.Input(1,genuine.input(),genuine.actor(),genuine.epoch()).wire()),"unmapped slot cannot extend the owner's preparation");
        h.assertFalse(MultidimensionalLandingArray.press(f.p,new HighPowerInput.Input(0,genuine.input()+1,genuine.actor()+1,genuine.epoch()).wire()),"foreign entity identity has no authority");
        h.assertTrue(MultidimensionalLandingArray.preparedPoints(f.p)==1&&f.needles()==6,"saved mark and six originals stay with genuine mapped owner");h.succeed();}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=14)
    public static void authenticated_abort_without_context_retires_delayed_older_press(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.aim(f.points.getFirst());
        h.assertFalse(MultidimensionalLandingArray.abort(f.p,f.wire(20)),"absent context has nothing to cancel");
        h.assertFalse(MultidimensionalLandingArray.press(f.p,f.wire(15)),"authenticated abort high watermark retires delayed older press even without context");
        h.assertTrue(MultidimensionalLandingArray.press(f.p,f.wire(21))&&MultidimensionalLandingArray.release(f.p,f.wire(21)),"strictly newer genuine input can still mark");h.assertValueEqual(MultidimensionalLandingArray.preparedPoints(f.p),1,"new input receives exactly one mark");h.succeed();}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=14)
    public static void authenticated_abort_twenty_closes_press_ten_and_blocks_press_fifteen(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.aim(f.points.getFirst());
        h.assertTrue(MultidimensionalLandingArray.press(f.p,f.wire(10)),"genuine press ten opens gesture");h.assertTrue(MultidimensionalLandingArray.abort(f.p,f.wire(20)),"genuine newer abort twenty ends its current context");
        h.assertFalse(MultidimensionalLandingArray.release(f.p,f.wire(10))||MultidimensionalLandingArray.press(f.p,f.wire(15)),"delayed release ten and press fifteen cannot revive canceled gesture");h.assertTrue(f.spawns.media.isEmpty()&&f.needles()==6&&MultidimensionalLandingArray.preparedPoints(f.p)==0,"abort remains markless and free");h.succeed();}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=14)
    public static void older_abort_cannot_clear_a_newer_genuine_pending_gesture(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.aim(f.points.getFirst());
        h.assertTrue(MultidimensionalLandingArray.press(f.p,f.wire(30)),"genuine press thirty opens current gesture");h.assertFalse(MultidimensionalLandingArray.abort(f.p,f.wire(29)),"older abort cannot clear newer owner input");
        h.assertTrue(MultidimensionalLandingArray.release(f.p,f.wire(30)),"matching current gesture still releases normally");h.assertValueEqual(MultidimensionalLandingArray.preparedPoints(f.p),1,"older abort preserves current mark authority");h.succeed();}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=14)
    public static void busy_new_press_never_lends_its_identity_to_an_older_release(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.aim(f.points.getFirst());
        h.assertTrue(MultidimensionalLandingArray.press(f.p,f.wire(10)),"press ten owns pending gesture");h.assertFalse(MultidimensionalLandingArray.press(f.p,f.wire(11)),"busy newer press cannot replace pending gesture");
        h.assertFalse(MultidimensionalLandingArray.release(f.p,f.wire(11)),"busy newer input has no release authority");h.assertTrue(MultidimensionalLandingArray.release(f.p,f.wire(10)),"captured original identity finishes only its own gesture");
        h.assertTrue(MultidimensionalLandingArray.receipt(f.p).input()==10&&MultidimensionalLandingArray.preparedPoints(f.p)==1,"saved mark receipt retains original genuine release identity");h.succeed();}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void dead_frozen_target_loses_its_shares_without_automatic_retargeting(GameTestHelper h){unavailableCase(h,0);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void lost_eye_line_of_sight_revokes_assigned_target_without_redistribution(GameTestHelper h){unavailableCase(h,1);}
    private static void unavailableCase(GameTestHelper h,int mode){Fixture f=new Fixture(h,6,true);List<MultidimensionalLandingArray.TargetVisual> frozen=new ArrayList<>();Map<UUID,BigDecimal> before=new HashMap<>();
        for(Zombie target:f.targets)before.put(target.getUUID(),hp(target));f.begin(()->frozen.addAll(MultidimensionalLandingArray.frozenTargets(f.p)),()->{
            Zombie removed=f.target(frozen.get(2).uuid());if(mode==0)removed.discard();else{Vec3 line=f.p.getEyePosition().lerp(removed.getBoundingBox().getCenter(),.85);f.block(BlockPos.containing(line),Blocks.STONE.defaultBlockState());}
            Zombie newcomer=f.mob(f.source.add(11,0,58),true);BigDecimal extra=hp(newcomer);
            f.post(42,()->{h.assertTrue(f.spawns.media.stream().noneMatch(m->m.shotIndex()==2),"unavailable frozen identity never publishes its assigned original");
                eq(h,hp(newcomer),extra,"new hostile cannot inherit a lost target's immutable share");
                h.assertTrue(MultidimensionalLandingArray.receipt(f.p).spent()==6&&MultidimensionalLandingArray.receipt(f.p).paid()<=5,"failed assigned attempt is burned without CP redistribution");
                for(int i=0;i<6;i++){Zombie target=f.target(frozen.get(i).uuid());if(target==removed||target.isRemoved())continue;
                    h.assertTrue(before.get(target.getUUID()).subtract(hp(target)).compareTo(share(whole(f),i,6))<=0,"remaining hostile never receives the lost share");}
                h.assertTrue(f.needles()==6&&!MultidimensionalLandingArray.active(f.p),"terminal round returns six originals once");});});f.finish(73);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=62)
    public static void moved_target_keeps_frozen_endpoint_and_never_hits_a_replacement_body(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.begin(()->{Vec3 endpoint=MultidimensionalLandingArray.frozenTargets(f.p).getFirst().position();BigDecimal before=hp(f.targets.getFirst());
        f.targets.getFirst().setPos(f.targets.getFirst().position().add(5,0,0));Zombie replacement=f.mob(endpoint.add(0,-.975,0),true);BigDecimal other=hp(replacement);
        f.post(18,()->{eq(h,hp(f.targets.getFirst()),before,"frozen aim never homes to a moved target");eq(h,hp(replacement),other,"replacement body cannot receive another entity's share");MultidimensionalLandingArray.cancelActive(f.p);});});f.finish(49);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=58)
    public static void actual_first_friendly_body_blocks_native_contact_without_receiving_damage(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.begin(()->{BorrowedNeedleEntity medium=f.first();Vec3 at=medium.position().add(medium.getDeltaMovement().scale(2));Sheep body=EntityType.SHEEP.create(h.getLevel());body.setNoAi(true);body.setNoGravity(true);body.setPos(at.add(0,-.5,0));h.assertTrue(h.getLevel().addFreshEntity(body),"declared native friendly body occupies actual first flight segment");f.entities.add(body);BigDecimal target=hp(f.targets.getFirst());float sheep=body.getHealth();
        f.post(3,()->{eq(h,hp(f.targets.getFirst()),target,"first native nonhostile body stops the assigned target");near(h,body.getHealth(),sheep,"native blocker receives no array damage");h.assertTrue(!medium.isLeaseActive(),"first body retires its one contact authority");MultidimensionalLandingArray.cancelActive(f.p);});});f.finish(34);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=44)
    public static void actual_first_solid_collision_stops_native_flight_without_hidden_clip_damage(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.begin(()->{BorrowedNeedleEntity medium=f.first();f.block(BlockPos.containing(medium.position().add(medium.getDeltaMovement().scale(2))),Blocks.STONE.defaultBlockState());BigDecimal before=hp(f.targets.getFirst());
        f.post(3,()->{eq(h,hp(f.targets.getFirst()),before,"real first solid collision cannot become predicted endpoint damage");h.assertFalse(medium.isLeaseActive(),"solid contact retires physical projection");MultidimensionalLandingArray.cancelActive(f.p);});});f.finish(34);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=36)
    public static void lost_sixth_saved_support_refuses_admission_without_partial_payment(GameTestHelper h){Fixture f=new Fixture(h,1,true);f.preview(()->{f.block(BlockPos.containing(f.surfaces.get(5)).below(),Blocks.AIR.defaultBlockState());double cp=f.cp();f.post(4,()->{
        h.assertFalse(f.tap(),"every mandatory saved anchor must still be supported");h.assertTrue(f.spawns.media.isEmpty()&&f.needles()==6&&BorrowedNeedleEscrow.retainedCount(f.p)==0,"invalid sixth anchor never partially admits");near(h,f.cp(),cp,"lost support is free");});});f.finish(29);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=38)
    public static void second_native_publisher_refusal_preserves_six_originals_and_only_first_cp_share(GameTestHelper h){Fixture f=new Fixture(h,1,true);boolean[] called={false};f.hook(new Object(){@SubscribeEvent(priority=EventPriority.HIGHEST) public void join(EntityJoinLevelEvent e){if(e.getEntity() instanceof BorrowedNeedleEntity m&&f.p.getUUID().equals(m.ownerId())&&m.shotIndex()==1){called[0]=true;e.setCanceled(true);}}});
        f.begin(()->f.post(5,()->{h.assertTrue(called[0]&&!MultidimensionalLandingArray.active(f.p),"actual second publication refusal ends the fixed round");h.assertTrue(MultidimensionalLandingArray.receipt(f.p).paid()==1&&f.budget.debitCount==1,"failed publication never repays or refunds first committed CP");
            h.assertTrue(f.needles()+BorrowedNeedleEscrow.retainedCount(f.p)==6,"all six physical originals remain conserved after partial publication failure");f.budget.conserved();}));f.finish(35);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void native_thrown_contact_has_exact_fixed_share_and_one_hurt(GameTestHelper h){Fixture f=new Fixture(h,6,true);Observation hit=f.observe(f.targets.getFirst());BigDecimal before=hp(f.targets.getFirst());
        f.begin(()->{BorrowedNeedleEntity medium=f.first();
            h.assertTrue(medium.isNoGravity(),"closed native flight retains its no-gravity property");
            h.assertTrue(medium.noPhysics,"closed native flight retains its controller-owned movement property");
            Vec3 expected=MultidimensionalLandingArray.frozenTargets(f.p).getFirst().position().subtract(f.origins.getFirst()).normalize().scale(1.6),actual=medium.getDeltaMovement();
            h.assertTrue(actual.x==expected.x&&actual.y==expected.y&&actual.z==expected.z,"native motion exactly preserves all components of the frozen origin-to-endpoint vector: actual="+actual+" expected="+expected);
            h.assertTrue(Math.abs(actual.length()-1.6)<=Math.ulp(1.6),"normalizing and scaling native doubles keeps speed within one ULP: "+actual.length());
            f.post(18,()->{eq(h,hp(f.targets.getFirst()),before.subtract(share(whole(f),0,6)),"real first contact debits the exact fixed sixth of min(4D,CAP)");h.assertTrue(hit.incoming==1&&hit.posts==1,"one real native incoming callback produces one native hurt");
                assertSource(h,hit.source,f.p,medium);MultidimensionalLandingArray.cancelActive(f.p);});});f.finish(49);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=14)
    public static void generic_skill_text_and_null_closed_permit_cannot_forge_array_damage(GameTestHelper h){Fixture f=new Fixture(h,1,true);BigDecimal before=hp(f.targets.getFirst());double cp=f.cp();
        h.assertFalse(HighPowerCombat.attack(f.p,SKILL,f.targets.getFirst(),n("5"))||HighPowerCombat.attackMultidimensionalLandingArray(null),"generic skill name and null authority cannot enter closed native array contact");eq(h,hp(f.targets.getFirst()),before,"forgery leaves exact target HP intact");near(h,f.cp(),cp,"forgery leaves actual shared CP intact");h.succeed();}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void zero_share_callback_loses_attempt_without_redistribution(GameTestHelper h){amountCase(h,0);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void amplified_share_callback_cannot_bypass_whole_round_cap(GameTestHelper h){amountCase(h,1);}
    private static void amountCase(GameTestHelper h,int mode){Fixture f=new Fixture(h,6,true);BigDecimal before=hp(f.targets.getFirst());boolean[] called={false};f.hook(new Object(){@SubscribeEvent public void skill(SkillAttackEvent e){if(e.target==f.targets.getFirst()&&SKILL.equals(e.skill)){called[0]=true;e.amount=mode==0?0:e.amount*2;}}});
        f.begin(()->f.post(42,()->{h.assertTrue(called[0],"actual native contact reaches skill amount callback");eq(h,hp(f.targets.getFirst()),before,"zero or increased share cannot spend immutable allocated damage");
            h.assertTrue(MultidimensionalLandingArray.receipt(f.p).paid()==6&&MultidimensionalLandingArray.receipt(f.p).hurts()==5,"refused share is spent once and never reassigned to the other five targets");h.assertValueEqual(f.needles(),6,"amount refusal still returns six physical originals once");}));f.finish(73);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=70)
    public static void armor_protection_resistance_absorption_match_independent_native_thrown_control(GameTestHelper h){Fixture f=new Fixture(h,1,true);Zombie target=f.targets.getFirst(),control=f.mob(f.source.add(17,0,58),false);defenses(h,target);defenses(h,control);f.hook(new Discount(target));Observation hit=f.observe(target),ordinary=f.observe(control);BigDecimal before=hp(target);
        f.begin(()->{f.post(17,()->{h.assertTrue(hit.posts>=1,"actual array native damage passed through defense callbacks");BorrowedNeedleEntity medium=f.first();Vec3 origin=hit.source.getSourcePosition().add(control.position().subtract(target.position()));
            h.assertTrue(control.hurt(new DamageSource(f.p.damageSources().thrown(medium,f.p).typeHolder(),medium,f.p,origin),5),"independent ordinary native THROWN control is accepted");
            h.assertTrue(hit.armor>0&&hit.effects>0&&hit.enchantments>0&&hit.absorption==.5F,"all ordinary native defenses reduce actual closed array contact");
            h.assertTrue(hit.armor==ordinary.armor&&hit.effects==ordinary.effects&&hit.enchantments==ordinary.enchantments&&hit.absorption==ordinary.absorption&&Math.abs(hit.damage-ordinary.damage)<.00001,"native damage observations match independent THROWN control");
            BigDecimal loss=DamageMitigation.mitigate(discounted(f),new DefenseProfile(BigDecimal.valueOf(target.getArmorValue()),new BigDecimal(Float.toString((float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS))),1,4)).subtract(n(".5"));
            eq(h,hp(target),before.subtract(loss),"exact target applies native reducers to the independently mapped skill-hook ratio");h.assertTrue(target.getAbsorptionAmount()==0&&control.getAbsorptionAmount()==0,"actual absorption pools consume once");MultidimensionalLandingArray.cancelActive(f.p);});});f.finish(48);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=65)
    public static void raised_native_shield_uses_actual_incoming_needle_segment_direction(GameTestHelper h){shieldCase(h,true);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=65)
    public static void native_shield_back_hemisphere_keeps_normal_thrown_damage(GameTestHelper h){shieldCase(h,false);}
    private static void shieldCase(GameTestHelper h,boolean blocked){Fixture f=new Fixture(h,1,true);Zombie target=f.targets.getFirst();float yaw=blocked?180:0;shield(target,yaw);f.hook(new Discount(target));Observation hit=f.observe(target);BigDecimal before=hp(target);
        f.begin(()->{face(target,yaw);h.assertTrue(target.isBlocking(),"real preparation elapsed native shield raise delay");f.post(17,()->{
            h.assertTrue(hit.incoming==1&&hit.blocked==blocked,"actual native shield hemisphere decides one first contact");eq(h,hp(target),blocked?before:before.subtract(discounted(f)),"native directional decision gates exact contact loss");assertSource(h,hit.source,f.p,f.first());MultidimensionalLandingArray.cancelActive(f.p);});});f.finish(48);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=65)
    public static void existing_native_iframes_do_not_respend_real_absorption(GameTestHelper h){Fixture f=new Fixture(h,1,true);Zombie target=f.targets.getFirst();target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,2));f.hook(new Discount(target));
        f.begin(()->f.post(13,()->{target.setAbsorptionAmount(10);BigDecimal before=hp(target);h.assertTrue(target.hurt(f.p.damageSources().playerAttack(f.p),6),"ordinary actual native hurt establishes stronger iframe than the discounted share");
            h.assertTrue(target.getAbsorptionAmount()==4,"ordinary hit spends six absorption once");f.post(4,()->{eq(h,hp(target),before,"weaker actual array hit preserves native iframe");h.assertTrue(target.getAbsorptionAmount()==4&&MultidimensionalLandingArray.receipt(f.p).hurts()==0,"closed array path cannot spend a second pool or bypass iframe");MultidimensionalLandingArray.cancelActive(f.p);});}));f.finish(48);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=65) public static void skill_callback_owner_motion_invalidates_native_damage_transaction(GameTestHelper h){mutationCase(h,0);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=65) public static void incoming_callback_target_motion_invalidates_native_damage_transaction(GameTestHelper h){mutationCase(h,1);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=65) public static void damage_pre_callback_geometry_change_invalidates_native_damage_transaction(GameTestHelper h){mutationCase(h,2);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=65) public static void late_absorption_callback_preserves_hp_and_pool_on_context_change(GameTestHelper h){mutationCase(h,3);}
    private static void mutationCase(GameTestHelper h,int stage){Fixture f=new Fixture(h,1,true);Zombie target=f.targets.getFirst();f.hook(new Discount(target));Mutation mutation=new Mutation(f,stage);f.hook(mutation);Observation hit=f.observe(target);BigDecimal before=hp(target);
        if(stage==3){target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,0));target.setAbsorptionAmount(2);}
        f.begin(()->f.post(18,()->{h.assertTrue(mutation.called,"real native stage callback was reached");eq(h,hp(target),before,"late callback mutation revokes closed transaction before HP debit");h.assertTrue(hit.damage==0&&!f.first().isLeaseActive(),"one contact is retired after final validation veto");
            if(stage==3)h.assertTrue(mutation.sawPre&&target.getAbsorptionAmount()==2,"late final absorption veto preserves real original pool");MultidimensionalLandingArray.cancelActive(f.p);}));f.finish(49);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=65)
    public static void late_native_absorption_amount_rewrite_is_vetoed_before_hp_or_pool_commit(GameTestHelper h){Fixture f=new Fixture(h,1,true);Zombie target=f.targets.getFirst();f.hook(new Discount(target));target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,0));target.setAbsorptionAmount(2);BigDecimal before=hp(target);boolean[] called={false};
        f.hook(new Object(){@SubscribeEvent(priority=EventPriority.LOWEST) public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target)e.addReductionModifier(DamageContainer.Reduction.ABSORPTION,(container,amount)->{called[0]=true;container.setNewDamage(container.getNewDamage()+1);return amount;});}});
        f.begin(()->f.post(18,()->{h.assertTrue(called[0],"late native modifier executes after the closed source's final observer");eq(h,hp(target),before,"rewritten native amount cannot commit immutable array share");h.assertTrue(target.getAbsorptionAmount()==2&&!f.first().isLeaseActive(),"final amount veto preserves native pool and consumes one contact");MultidimensionalLandingArray.cancelActive(f.p);}));f.finish(49);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=65)
    public static void unknown_skill_callback_exception_fails_closed_without_replay(GameTestHelper h){unknownCallback(h,0);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=65)
    public static void unknown_post_damage_callback_exception_never_repeats_committed_hurt(GameTestHelper h){unknownCallback(h,1);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=65)
    public static void unknown_normal_post_callback_before_exact_adapter_commits_no_exact_hp(GameTestHelper h){unknownCallback(h,2);}
    private static void unknownCallback(GameTestHelper h,int stage){Fixture f=new Fixture(h,1,true);Zombie target=f.targets.getFirst();BigDecimal before=hp(target);boolean[] called={false},committed={false};f.hook(new Object(){
        @SubscribeEvent public void skill(SkillAttackEvent e){if(stage==0&&e.target==target&&SKILL.equals(e.skill)&&!called[0]){called[0]=true;throw new IllegalStateException("declared NEW351 skill callback fixture");}}
        @SubscribeEvent public void beforeAdapter(LivingDamageEvent.Post e){if(stage==2&&e.getEntity()==target&&!called[0]){called[0]=true;committed[0]=hp(target).compareTo(before)==0;throw new IllegalStateException("declared NEW351 pre-adapter post damage fixture");}}
        // Static LOWEST exact adapter is registered before this fixture. This later LOWEST observer
        // verifies actual committed HP before injecting the intended post-commit exception.
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(LivingDamageEvent.Post e){if(stage==1&&e.getEntity()==target&&!called[0]){called[0]=true;committed[0]=before.subtract(hp(target)).compareTo(share(whole(f),0,6))==0;throw new IllegalStateException("declared NEW351 post damage fixture");}}
    });f.begin(()->f.post(18,()->{h.assertTrue(called[0]&&!MultidimensionalLandingArray.active(f.p),"uncertain native callback ends the entire round");BigDecimal after=hp(target);
        if(stage==0)eq(h,after,before,"pre-hurt exception cannot commit target damage");else if(stage==2){h.assertTrue(committed[0],"normal Post observer independently saw unchanged exact ledger before throwing");eq(h,after,before,"pre-adapter Post exception commits no exact HP loss");}else{h.assertTrue(committed[0],"late observer witnessed exact assigned HP share already committed before its exception");eq(h,before.subtract(after),share(whole(f),0,6),"post-hurt exception retains exactly the already committed immutable share");}
        h.assertTrue(f.spawns.media.stream().noneMatch(BorrowedNeedleEntity::isLeaseActive),"unknown callback never leaves a live attack projection");f.post(8,()->{eq(h,hp(target),after,"uncertain callback cannot replay a hurt");h.assertValueEqual(f.needles()+BorrowedNeedleEscrow.retainedCount(f.p),6,"uncertainty never duplicates retained originals");});}));f.finish(57);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=195)
    public static void six_in_flight_limit_and_forty_tick_native_lease_bound_a_long_round(GameTestHelper h){Fixture f=new Fixture(h,16,true,16.5,92.5);BigDecimal before=hp(f.targets.getFirst());f.begin(()->{
        BorrowedNeedleEntity medium=f.first();f.post(20,()->{h.assertValueEqual(f.spawns.media.size(),6,"six long native flights coexist at most");h.assertTrue(f.spawns.maxActive==6&&BorrowedNeedleEscrow.retainedCount(f.p)==6,"all six physical originals stay retained while in flight");});
        f.post(23,()->h.assertValueEqual(f.spawns.media.size(),6,"seventh shot waits for its own custody slot; no seventh flight"));
        f.post(40,()->h.assertFalse(medium.isLeaseActive(),"native physical authority expires within forty real ticks"));
        f.post(160,()->{h.assertFalse(MultidimensionalLandingArray.active(f.p),"no round authority survives the actual 160-tick deadline");h.assertTrue(f.spawns.maxActive<=6&&f.spawns.ticks.stream().allMatch(t->t-f.started<160),"stalled slots never emit catch-up batches past the deadline");eq(h,hp(f.targets.getFirst()),before,"out-of-flight endpoint causes no predicted or hidden hit");h.assertValueEqual(f.needles()+BorrowedNeedleEscrow.retainedCount(f.p),6,"terminal long round conserves all six originals");});});f.finish(189);}

    private static void face(LivingEntity e,float yaw){e.setYRot(yaw);e.setYHeadRot(yaw);e.yBodyRot=yaw;e.setXRot(0);}
    private static void shield(LivingEntity e,float yaw){face(e,yaw);e.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));e.startUsingItem(InteractionHand.OFF_HAND);}
    private static void defenses(GameTestHelper h,LivingEntity e){ItemStack helmet=new ItemStack(Items.DIAMOND_HELMET);helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),4);helmet.set(DataComponents.UNBREAKABLE,new Unbreakable(true));e.setItemSlot(EquipmentSlot.HEAD,helmet);e.getAttribute(Attributes.ARMOR).setBaseValue(5);e.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,200,0));e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,0));e.setAbsorptionAmount(.5F);}
    private static void assertSource(GameTestHelper h,DamageSource source,ServerPlayer owner,BorrowedNeedleEntity medium){
        h.assertTrue(source!=null&&source.is(DamageTypes.THROWN)&&source.is(DamageTypeTags.IS_PROJECTILE)&&source.getEntity()==owner&&source.getDirectEntity()==medium&&source.getSourcePosition()!=null&&source.getSourcePosition().equals(source.sourcePositionRaw()),"native THROWN damage preserves actual borrowed needle, owner and incoming physical segment source");
        h.assertFalse(source.is(DamageTypeTags.BYPASSES_ARMOR)||source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)||source.is(DamageTypeTags.BYPASSES_EFFECTS)||source.is(DamageTypeTags.BYPASSES_RESISTANCE)||source.is(DamageTypeTags.BYPASSES_SHIELD)||source.is(DamageTypeTags.BYPASSES_COOLDOWN)||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)||source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypeTags.IS_EXPLOSION),"native array source introduces no ordinary defense or iframe bypass");
    }
    public static final class Discount {final LivingEntity target;Discount(LivingEntity target){this.target=target;}@SubscribeEvent(priority=EventPriority.HIGHEST) public void skill(SkillAttackEvent e){if(e.target==target&&SKILL.equals(e.skill))e.amount=5;}}
    public static final class Mutation {
        final Fixture f;final int stage;boolean called,sawPre;Mutation(Fixture f,int stage){this.f=f;this.stage=stage;}
        void change(){if(called)return;called=true;if(stage==0)f.p.setPos(f.p.position().add(.1,0,0));else if(stage==1)f.targets.getFirst().setPos(f.targets.getFirst().position().add(0,1,0));else if(stage==2)f.block(BlockPos.containing(f.first().position()),Blocks.STONE.defaultBlockState());else AbilityStorage.get(f.p).activated=false;}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent e){if(stage==0&&e.target==f.targets.getFirst()&&SKILL.equals(e.skill))change();}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()!=f.targets.getFirst())return;if(stage==1)change();if(stage==3)e.addReductionModifier(DamageContainer.Reduction.ABSORPTION,(container,amount)->{change();return amount;});}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre e){if(e.getEntity()!=f.targets.getFirst())return;sawPre=true;if(stage==2)change();}
    }
    public static final class Observation {
        final LivingEntity target;BigDecimal applied=BigDecimal.ZERO;DamageSource source;int incoming,posts;boolean blocked;float damage,armor,effects,enchantments,absorption;
        Observation(LivingEntity target){this.target=target;}
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target){incoming++;source=e.getSource();}}
        @SubscribeEvent public void shield(LivingShieldBlockEvent e){if(e.getEntity()==target)blocked=e.getBlocked();}
        @SubscribeEvent public void post(LivingDamageEvent.Post e){if(e.getEntity()!=target)return;posts++;damage=e.getNewDamage();armor=e.getReduction(DamageContainer.Reduction.ARMOR);effects=e.getReduction(DamageContainer.Reduction.MOB_EFFECTS);enchantments=e.getReduction(DamageContainer.Reduction.ENCHANTMENTS);absorption=e.getReduction(DamageContainer.Reduction.ABSORPTION);
            if(e.getSource().getDirectEntity() instanceof BorrowedNeedleEntity m&&damage>0){Fixture f=Fixture.OWNERS.get(m.ownerId());if(f!=null)applied=applied.add(share(whole(f),m.shotIndex(),f.shots));}}
    }
    public static final class Spawns {
        final Fixture f;final List<BorrowedNeedleEntity> media=new ArrayList<>();final List<Integer> ticks=new ArrayList<>();final List<Vec3> origins=new ArrayList<>(),motions=new ArrayList<>();
        int inspected,maxActive;double before;boolean heldWholeRound=true;
        Spawns(Fixture f){this.f=f;}
        @SubscribeEvent public void join(EntityJoinLevelEvent e){if(!(e.getEntity() instanceof BorrowedNeedleEntity medium)||!f.p.getUUID().equals(medium.ownerId())||medium.shotIndex()<0)return;
            media.add(medium);f.entities.add(medium);ticks.add(f.p.server.getTickCount());origins.add(medium.position());motions.add(medium.getDeltaMovement());before=f.cp();
            f.h.assertFalse(medium.isAdmitted(),"native join exposes staged inert projection before CP payment");f.h.assertValueEqual(BorrowedNeedleEscrow.retainedCount(f.p),6,"every pending publication retains exactly six physical originals");f.h.assertValueEqual(f.needles(),0,"originals remain out of inventory until whole-round return");
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void pre(ServerTickEvent.Pre e){if(e.getServer()!=f.p.server)return;payment();sample();}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post e){if(e.getServer()==f.p.server)sample();}
        void sample(){int count=(int)media.stream().filter(BorrowedNeedleEntity::isLeaseActive).count();maxActive=Math.max(maxActive,count);if(MultidimensionalLandingArray.active(f.p))heldWholeRound&=BorrowedNeedleEscrow.retainedCount(f.p)==6&&f.needles()==0;}
        void payment(){if(media.size()==inspected)return;BorrowedNeedleEntity last=media.getLast();if(last.isAdmitted())f.budget.debit(before,f.cp(),cpShare(f.cost,last.shotIndex(),f.shots));inspected=media.size();sample();}
    }
    /** Separate observations of native recovery and actual native publication debits. */
    public static final class BudgetLedger {
        final Fixture f;final BigDecimal initial;BigDecimal recovered=BigDecimal.ZERO,debited=BigDecimal.ZERO;int debitCount;double before,maximum,recovery;long previous,now;boolean hadPrevious;
        BudgetLedger(Fixture f){this.f=f;initial=BigDecimal.valueOf(f.cp());}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e){if(e.getEntity()!=f.p)return;before=f.cp();var data=f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);hadPrevious=data.contains("last_tick");previous=data.getLong("last_tick");now=f.p.level().getGameTime();var growth=HighPowerGrowth.values(f.p);maximum=growth.cpMaximum();recovery=growth.cpRecovery()/20;}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void after(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e){if(e.getEntity()!=f.p)return;double after=f.cp(),expected=hadPrevious&&previous==now?before:Math.min(maximum,before+recovery);f.h.assertTrue(Double.doubleToLongBits(after)==Double.doubleToLongBits(expected),"native player recovery has exactly its independently witnessed continuous-Q amount");recovered=recovered.add(BigDecimal.valueOf(after).subtract(BigDecimal.valueOf(before)));}
        void debit(double before,double after,double amount){f.h.assertTrue(Double.doubleToLongBits(after)==Double.doubleToLongBits(before-amount),"one native publication pays exactly its represented whole-round CP share");debited=debited.add(BigDecimal.valueOf(before).subtract(BigDecimal.valueOf(after)));debitCount++;}
        void conserved(){eq(f.h,BigDecimal.valueOf(f.cp()),initial.add(recovered).subtract(debited),"actual CP recovery and publication debits conserve independently witnessed budget");}
    }
    public static final class PostCheck {
        final Fixture f;final int tick;final Runnable action;boolean ran;PostCheck(Fixture f,int delay,Runnable action){this.f=f;tick=f.p.server.getTickCount()+delay;this.action=action;}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post e){if(ran||e.getServer()!=f.p.server||e.getServer().getTickCount()!=tick)return;ran=true;try{action.run();}catch(Throwable failure){if(f.failure==null)f.failure=failure;}}
    }
    static final class Fixture implements AutoCloseable {
        static final Map<UUID,Fixture> OWNERS=new HashMap<>();
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final Vec3 source;final int shots;final List<Vec3> points=new ArrayList<>(),surfaces=new ArrayList<>(),origins=new ArrayList<>();final List<Zombie> targets=new ArrayList<>();
        final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final List<PostCheck> checks=new ArrayList<>();final Map<BlockPos,BlockState> changed=new LinkedHashMap<>();final Set<ChunkPos> held=new LinkedHashSet<>();final UUID ticket=UUID.randomUUID();final Spawns spawns;final BudgetLedger budget;
        long inputs;int started;double cost;Throwable failure;boolean closed;
        Fixture(GameTestHelper h,int count,boolean exact){this(h,count,exact,40.5,64.5);}
        Fixture(GameTestHelper h,int count,boolean exact,double anchorZ,double targetZ){
            this.h=h;shots=Math.max(6,count);source=h.absoluteVec(new Vec3(32.5,5,6.5));AABB bounds=h.getBounds();
            h.assertTrue(bounds.contains(h.absoluteVec(new Vec3(16,4,3)))&&bounds.contains(h.absoluteVec(new Vec3(50,7,Math.min(98,targetZ+3)))),"every declared fixture floor, player and hostile body fits the actual supplied template");
            for(int x=16;x<=49;x++)for(int z=3;z<=Math.ceil(targetZ)+2;z++)block(h.absolutePos(new BlockPos(x,4,z)),Blocks.STONE.defaultBlockState());
            for(int i=0;i<6;i++)points.add(h.absoluteVec(new Vec3(27.5+2*i,5,anchorZ)));
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"LA"+UUID.randomUUID().toString().substring(0,10)),false);p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);acknowledgeLogin();
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=false;p.getAbilities().instabuild=false;p.moveTo(source.x,source.y,source.z,0,0);p.setPose(Pose.STANDING);p.setShiftKeyDown(false);p.setNoGravity(false);p.fallDistance=0;p.setOnGround(true);p.move(MoverType.SELF,new Vec3(0,-.0784,0));p.setDeltaMovement(0,-.0784,0);p.connection.resetPosition();
            h.assertTrue(p.onGround()&&p.position().equals(source),"actual native downward collision establishes supported safe standing source");h.assertTrue(HighPowerCombat.enroll(p,5),"declared actual LV5 caster enrollment");
            var state=AbilityStorage.get(p);state.selectCategory(HighPowerCategories.TELEPORTER);state.setLevel(5);state.learn(SafeLandingProbe.ID);state.learn(DisplacedProjection.ID);state.learn(CrossSubstitution.ID);state.learn(SpatialPincerCorridor.ID);state.learn(ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,ID,state::learned),"learned NEW351 explicitly occupies mapped slot zero");HighPowerResources.initializeBudget(p);p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AcademyCraft.NEEDLE.get(),6));
            for(int i=0;i<count;i++){double offset=i==0?0:((i+1)/2)*1.3*(i%2==1?1:-1);targets.add(mob(h.absoluteVec(new Vec3(32.5+offset,5,targetZ)),exact));}
            OWNERS.put(p.getUUID(),this);budget=new BudgetLedger(this);hook(budget);spawns=new Spawns(this);hook(spawns);hook(new HighPowerPhaseSidestepRuntimeTests.NativePlayerPump(p,h.testInfo.getTimeoutTicks()+5));
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Zombie mob(Vec3 at,boolean exact){Zombie m=EntityType.HUSK.create(h.getLevel());m.setNoAi(true);m.setNoGravity(true);m.setSilent(true);m.getAttribute(Attributes.ARMOR).setBaseValue(0);m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);m.setPos(at);h.assertTrue(h.getBounds().contains(m.getBoundingBox().minX,m.getBoundingBox().minY,m.getBoundingBox().minZ)&&h.getBounds().contains(m.getBoundingBox().maxX,m.getBoundingBox().maxY,m.getBoundingBox().maxZ),"declared hostile body remains inside template");h.assertTrue(h.getLevel().addFreshEntity(m),"declared native stationary hostile admitted");entities.add(m);if(exact)h.assertTrue(HighPowerCombat.enroll(m,5),"declared exact hostile enrollment");return m;}
        Zombie target(UUID uuid){return targets.stream().filter(t->t.getUUID().equals(uuid)).findFirst().orElseThrow();}
        void acknowledgeLogin(){channel.runPendingTasks();channel.flushOutbound();channel.runPendingTasks();Object packet;while((packet=channel.readOutbound())!=null){if(packet instanceof ClientboundPlayerPositionPacket position)p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));ReferenceCountUtil.release(packet);}}
        void hold(ChunkPos chunk){if(held.add(chunk)){h.getLevel().getChunkSource().addRegionTicket(TICKET,chunk,2,ticket);h.getLevel().getChunk(chunk.x,chunk.z);}}
        void block(BlockPos pos,BlockState state){h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)),"changed native fixture block remains inside template");hold(new ChunkPos(pos));changed.putIfAbsent(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,state);}
        void aim(Vec3 at){Vec3 d=at.subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p))).wire();}
        boolean press(){return MultidimensionalLandingArray.press(p,wire(++inputs));}
        boolean tap(){String wire=wire(++inputs);return MultidimensionalLandingArray.press(p,wire)&&MultidimensionalLandingArray.release(p,wire);}
        Vec3 nativeSurface(){var hit=h.getLevel().clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(96)),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,p));h.assertTrue(hit.getType()==HitResult.Type.BLOCK&&hit.getDirection()==net.minecraft.core.Direction.UP,"independent native fixture ray confirms actual first UP surface");return hit.getLocation();}
        void mark(int i){aim(points.get(i));Vec3 surface=nativeSurface();h.assertTrue(tap(),"genuine authenticated short release captures floor "+i+": "+MultidimensionalLandingArray.receipt(p));surfaces.add(surface);origins.add(surface.add(0,1.2,0));}
        void ready(Runnable action){mark(0);for(int i=1;i<6;i++){final int index=i;post(4*i,()->mark(index));}post(24,action);}
        void preview(Runnable action){ready(()->{aim(targets.getFirst().getBoundingBox().getCenter());double before=cp();h.assertTrue(tap(),"genuine seventh short release freezes free preview: "+MultidimensionalLandingArray.receipt(p));h.assertTrue(MultidimensionalLandingArray.previewed(p)&&spawns.media.isEmpty()&&needles()==6&&BorrowedNeedleEscrow.retainedCount(p)==0,"preview acquires no physical stock or publication");near(h,cp(),before,"native seventh preview consumes no CP");cost=MultidimensionalLandingArrayRules.cpCost(HighPowerGrowth.values(p).position());action.run();});}
        void begin(Runnable action){begin(()->{},action);}
        void begin(Runnable preview,Runnable action){preview(()->{preview.run();post(4,()->{started=p.server.getTickCount();double before=cp();h.assertTrue(tap(),"genuine eighth fresh release confirms original preview: "+MultidimensionalLandingArray.receipt(p));near(h,cp(),before-cpShare(cost,0,shots),"first native publication pays only its immutable represented CP share");spawns.payment();action.run();});});}
        BorrowedNeedleEntity first(){return spawns.media.getFirst();}
        double cp(){return HighPowerResources.cp(p);}
        int needles(){int count=0;for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack stack=p.getInventory().getItem(i);if(stack.is(AcademyCraft.NEEDLE.get()))count+=stack.getCount();}return count;}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        Observation observe(LivingEntity e){Observation observation=new Observation(e);hook(observation);return observation;}
        void post(int delay,Runnable action){PostCheck check=new PostCheck(this,delay,action);checks.add(check);hook(check);}
        void verify(){if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;if(failure!=null)throw new AssertionError(failure);h.assertTrue(checks.stream().allMatch(c->c.ran),"every requested independent actual native Post observation executed");}
        void finish(int delay){h.runAfterDelay(delay,()->{verify();h.succeed();});}
        public void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);MultidimensionalLandingArray.clear(p);OWNERS.remove(p.getUUID());HighPowerBracerSession.remove(p);AbilityStorage.remove(p);p.server.getPlayerList().remove(p);entities.forEach(Entity::discard);changed.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));held.forEach(chunk->h.getLevel().getChunkSource().removeRegionTicket(TICKET,chunk,2,ticket));channel.finishAndReleaseAll();}
    }
}
