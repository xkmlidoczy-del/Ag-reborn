package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.city.CityNpcContent;
import cn.academy.port.city.CityNpcEntity;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.highpower.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Production guard ON: native unarmed Zombie.doHurtTarget, not a fabricated skill or a percent-HP oracle.
 * Test-only class is excluded from the production jar. Each level is a bounded batch of twelve categories. */
@GameTestHolder("academy_damage_ledger_audit")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class DamageLedgerAuditRuntimeTests {
    private static final String TEMPLATE="damage_ledger_empty";
    private static final BigDecimal THREE=new BigDecimal("3"), PARTIAL=new BigDecimal("1.25");
    @SubscribeEvent public static void template(LevelEvent.Load event) {
        if(!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel world))return;
        CompoundTag tag=new CompoundTag(), cell=new CompoundTag(),air=new CompoundTag();
        ListTag size=new ListTag(),pos=new ListTag(),blocks=new ListTag(),palette=new ListTag();
        for(int n:new int[]{24,12,24})size.add(IntTag.valueOf(n));
        for(int n=0;n<3;n++)pos.add(IntTag.valueOf(0));
        cell.put("pos",pos);cell.putInt("state",0);blocks.add(cell);air.putString("Name","minecraft:air");palette.add(air);
        tag.put("size",size);tag.put("blocks",blocks);tag.put("palette",palette);tag.put("entities",new ListTag());
        world.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_damage_ledger_audit",TEMPLATE))
                .load(world.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }
    @GameTest(template=TEMPLATE,batch="damage_ledger_lv1",timeoutTicks=200)
    public static void electromaster_lv1_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"electromaster","electro",1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv1",timeoutTicks=200)
    public static void meltdowner_lv1_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"meltdowner","melt",1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv1",timeoutTicks=200)
    public static void teleporter_lv1_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"teleporter","teleport",1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv1",timeoutTicks=200)
    public static void vecmanip_lv1_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"vecmanip","vector",1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv1",timeoutTicks=200)
    public static void mental_out_lv1_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"mental_out","mental",1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv1",timeoutTicks=200)
    public static void aero_hand_lv1_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"aero_hand","air",1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv1",timeoutTicks=200)
    public static void float_dial_lv1_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"float_dial","float",1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv1",timeoutTicks=200)
    public static void nitrogen_armor_lv1_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"nitrogen_armor","nitrogen",1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv1",timeoutTicks=200)
    public static void dark_matter_lv1_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"dark_matter","dark",1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv1",timeoutTicks=200)
    public static void hydro_hand_lv1_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"hydro_hand","water",1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv1",timeoutTicks=200)
    public static void paper_psychokinesis_lv1_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"paper_psychokinesis","paper",1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv1",timeoutTicks=200)
    public static void liquid_shadow_lv1_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"liquid_shadow","shadow",1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv2",timeoutTicks=200)
    public static void electromaster_lv2_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"electromaster","electro",2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv2",timeoutTicks=200)
    public static void meltdowner_lv2_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"meltdowner","melt",2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv2",timeoutTicks=200)
    public static void teleporter_lv2_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"teleporter","teleport",2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv2",timeoutTicks=200)
    public static void vecmanip_lv2_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"vecmanip","vector",2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv2",timeoutTicks=200)
    public static void mental_out_lv2_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"mental_out","mental",2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv2",timeoutTicks=200)
    public static void aero_hand_lv2_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"aero_hand","air",2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv2",timeoutTicks=200)
    public static void float_dial_lv2_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"float_dial","float",2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv2",timeoutTicks=200)
    public static void nitrogen_armor_lv2_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"nitrogen_armor","nitrogen",2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv2",timeoutTicks=200)
    public static void dark_matter_lv2_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"dark_matter","dark",2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv2",timeoutTicks=200)
    public static void hydro_hand_lv2_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"hydro_hand","water",2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv2",timeoutTicks=200)
    public static void paper_psychokinesis_lv2_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"paper_psychokinesis","paper",2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv2",timeoutTicks=200)
    public static void liquid_shadow_lv2_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"liquid_shadow","shadow",2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv3",timeoutTicks=200)
    public static void electromaster_lv3_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"electromaster","electro",3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv3",timeoutTicks=200)
    public static void meltdowner_lv3_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"meltdowner","melt",3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv3",timeoutTicks=200)
    public static void teleporter_lv3_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"teleporter","teleport",3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv3",timeoutTicks=200)
    public static void vecmanip_lv3_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"vecmanip","vector",3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv3",timeoutTicks=200)
    public static void mental_out_lv3_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"mental_out","mental",3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv3",timeoutTicks=200)
    public static void aero_hand_lv3_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"aero_hand","air",3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv3",timeoutTicks=200)
    public static void float_dial_lv3_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"float_dial","float",3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv3",timeoutTicks=200)
    public static void nitrogen_armor_lv3_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"nitrogen_armor","nitrogen",3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv3",timeoutTicks=200)
    public static void dark_matter_lv3_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"dark_matter","dark",3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv3",timeoutTicks=200)
    public static void hydro_hand_lv3_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"hydro_hand","water",3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv3",timeoutTicks=200)
    public static void paper_psychokinesis_lv3_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"paper_psychokinesis","paper",3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv3",timeoutTicks=200)
    public static void liquid_shadow_lv3_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"liquid_shadow","shadow",3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv4",timeoutTicks=200)
    public static void electromaster_lv4_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"electromaster","electro",4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv4",timeoutTicks=200)
    public static void meltdowner_lv4_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"meltdowner","melt",4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv4",timeoutTicks=200)
    public static void teleporter_lv4_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"teleporter","teleport",4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv4",timeoutTicks=200)
    public static void vecmanip_lv4_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"vecmanip","vector",4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv4",timeoutTicks=200)
    public static void mental_out_lv4_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"mental_out","mental",4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv4",timeoutTicks=200)
    public static void aero_hand_lv4_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"aero_hand","air",4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv4",timeoutTicks=200)
    public static void float_dial_lv4_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"float_dial","float",4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv4",timeoutTicks=200)
    public static void nitrogen_armor_lv4_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"nitrogen_armor","nitrogen",4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv4",timeoutTicks=200)
    public static void dark_matter_lv4_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"dark_matter","dark",4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv4",timeoutTicks=200)
    public static void hydro_hand_lv4_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"hydro_hand","water",4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv4",timeoutTicks=200)
    public static void paper_psychokinesis_lv4_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"paper_psychokinesis","paper",4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv4",timeoutTicks=200)
    public static void liquid_shadow_lv4_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"liquid_shadow","shadow",4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv5",timeoutTicks=200)
    public static void electromaster_lv5_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"electromaster","electro",5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv5",timeoutTicks=200)
    public static void meltdowner_lv5_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"meltdowner","melt",5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv5",timeoutTicks=200)
    public static void teleporter_lv5_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"teleporter","teleport",5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv5",timeoutTicks=200)
    public static void vecmanip_lv5_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"vecmanip","vector",5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv5",timeoutTicks=200)
    public static void mental_out_lv5_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"mental_out","mental",5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv5",timeoutTicks=200)
    public static void aero_hand_lv5_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"aero_hand","air",5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv5",timeoutTicks=200)
    public static void float_dial_lv5_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"float_dial","float",5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv5",timeoutTicks=200)
    public static void nitrogen_armor_lv5_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"nitrogen_armor","nitrogen",5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv5",timeoutTicks=200)
    public static void dark_matter_lv5_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"dark_matter","dark",5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv5",timeoutTicks=200)
    public static void hydro_hand_lv5_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"hydro_hand","water",5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv5",timeoutTicks=200)
    public static void paper_psychokinesis_lv5_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"paper_psychokinesis","paper",5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_lv5",timeoutTicks=200)
    public static void liquid_shadow_lv5_zombie_full_partial_empty_player_and_npc(GameTestHelper h) { matrix(h,"liquid_shadow","shadow",5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv1_repeated(GameTestHelper h) { repeated(h,1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv1_small(GameTestHelper h) { small(h,1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv1_mitigated(GameTestHelper h) { mitigated(h,1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv1_cooldown(GameTestHelper h) { cooldown(h,1); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv2_repeated(GameTestHelper h) { repeated(h,2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv2_small(GameTestHelper h) { small(h,2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv2_mitigated(GameTestHelper h) { mitigated(h,2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv2_cooldown(GameTestHelper h) { cooldown(h,2); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv3_repeated(GameTestHelper h) { repeated(h,3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv3_small(GameTestHelper h) { small(h,3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv3_mitigated(GameTestHelper h) { mitigated(h,3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv3_cooldown(GameTestHelper h) { cooldown(h,3); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv4_repeated(GameTestHelper h) { repeated(h,4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv4_small(GameTestHelper h) { small(h,4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv4_mitigated(GameTestHelper h) { mitigated(h,4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv4_cooldown(GameTestHelper h) { cooldown(h,4); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv5_repeated(GameTestHelper h) { repeated(h,5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv5_small(GameTestHelper h) { small(h,5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv5_mitigated(GameTestHelper h) { mitigated(h,5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_edges",timeoutTicks=220)
    public static void lv5_cooldown(GameTestHelper h) { cooldown(h,5); }

    @GameTest(template=TEMPLATE,batch="damage_ledger_ai",timeoutTicks=260)
    public static void autonomous_zombie_without_shield(GameTestHelper h) {
        Fixture f=new Fixture(h,"autonomous"); ServerPlayer p=f.player("electromaster",5,4); Zombie z=f.zombie(6);
        f.ready(()->{
            f.shield(p,BigDecimal.ZERO); Balances before=balances(p); Recorder r=f.recorder(p);
            for(int x=2;x<=7;x++)for(int zz=2;zz<=7;zz++){h.setBlock(new BlockPos(x,1,zz),Blocks.STONE);h.setBlock(new BlockPos(x,5,zz),Blocks.STONE);}
            f.place(z,4,5);z.setNoGravity(false);z.setNoAi(false);z.setTarget(p);z.getNavigation().moveTo(p,1);z.setRemainingFireTicks(0);
            f.hook(new Object(){
                @SubscribeEvent(priority=EventPriority.LOWEST) public void hit(LivingDamageEvent.Post e){
                    if(e.getEntity()!=p)return;
                    z.setNoAi(true);z.setTarget(null);z.getNavigation().stop();
                    h.runAfterDelay(5,()->{f.check(p,before,THREE,THREE,BigDecimal.ZERO,r,"autonomous_native_ai");h.succeed();});
                }
            });
        });
    }

    private static void matrix(GameTestHelper h,String category,String profile,int level) {
        Fixture f=new Fixture(h,category+"_lv"+level); List<Runnable> stable=new ArrayList<>();
        for(int state=0;state<3;state++) {
            final int index=state; ServerPlayer p=f.player(category,level,4+state*6);
            CityNpcEntity n=f.npc(profile,level,4+state*6); Zombie zp=f.zombie(5+state*6), zn=f.zombie(5+state*6);
            f.staged.add(()->{
                for(LivingEntity victim:new LivingEntity[]{p,n}) {
                    BigDecimal shield=index==0?new BigDecimal(victim.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getString("maxShield")):index==1?PARTIAL:BigDecimal.ZERO;
                    f.shield(victim,shield); Balances before=balances(victim); Recorder r=f.recorder(victim);
                    Zombie attacker=victim==p?zp:zn;
                    h.assertFalse(HighPowerCombat.enabled(attacker),"ordinary zombie is not a high-power attacker");
                    h.assertTrue(attacker.getMainHandItem().isEmpty() && attacker.getOffhandItem().isEmpty(),"unarmed vanilla zombie");
                    // Real melee reach also activates the category's deferred counter/phase traits.
                    attacker.setPos(victim.position().add(0,0,1.3));attacker.setDeltaMovement(Vec3.ZERO);
                    h.assertTrue(attacker.distanceTo(victim)<2,"zombie is at actual melee distance");
                    victim.invulnerableTime=0;
                    h.assertTrue(attacker.doHurtTarget(victim),"native zombie melee accepted");
                    BigDecimal s=THREE.min(shield), hp=THREE.subtract(s);
                    String label=(victim==p?"player":"city_npc")+"_"+(index==0?"full":index==1?"partial":"empty");
                    f.check(victim,before,THREE,hp,s,r,label);
                    Balances settled=balances(victim);
                    stable.add(()->{f.same(victim,settled,label+"_after_6ticks");f.emit(victim,before,r,label+"_after_6ticks");});
                }
            });
        }
        f.ready(()->{f.staged.forEach(Runnable::run);h.runAfterDelay(6,()->{stable.forEach(Runnable::run);h.succeed();});});
    }
    private static void repeated(GameTestHelper h,int level) {
        Fixture f=new Fixture(h,"repeated_lv"+level);ServerPlayer shielded=f.player("electromaster",level,4),bare=f.player("electromaster",level,12);
        Zombie z=f.zombie(20);
        f.ready(()->{
            f.shield(bare,BigDecimal.ZERO);
            for(ServerPlayer p:new ServerPlayer[]{shielded,bare}) {
                Balances before=balances(p); Recorder r=f.recorder(p);
                // Separate hit admissions: reset only the native invulnerability timer. The no-reset case is below.
                for(int i=0;i<20;i++){p.invulnerableTime=0;h.assertTrue(z.doHurtTarget(p),"independent native melee "+i);}
                BigDecimal total=new BigDecimal("60"),s=total.min(before.s);
                f.check(p,before,total,total.subtract(s),s,r,"twenty_independent_zombie_hits");
                h.assertTrue(r.posts==20,"exactly one post per admitted hit: "+r.posts);
            }
            Balances a=balances(shielded),b=balances(bare);
            h.runAfterDelay(6,()->{f.same(shielded,a,"repeated_full_delayed");f.same(bare,b,"repeated_empty_delayed");h.succeed();});
        });
    }
    private static void small(GameTestHelper h,int level) {
        Fixture f=new Fixture(h,"small_lv"+level);ServerPlayer shielded=f.player("electromaster",level,4),bare=f.player("electromaster",level,12);
        Zombie z=f.zombie(20);
        f.ready(()->{
            f.shield(bare,BigDecimal.ZERO);
            for(ServerPlayer p:new ServerPlayer[]{shielded,bare})for(String value:new String[]{"0.01","0.1","1","5"}) {
                BigDecimal amount=new BigDecimal(value);Balances before=balances(p);Recorder r=f.recorder(p);p.invulnerableTime=0;
                h.assertTrue(p.hurt(h.getLevel().damageSources().mobAttack(z),amount.floatValue()),"native small mob-attack accepted");
                BigDecimal s=amount.min(before.s);f.check(p,before,amount,amount.subtract(s),s,r,"small_mob_attack_"+value);
                f.unhook(r);
            }
            Balances a=balances(shielded),b=balances(bare);
            h.runAfterDelay(6,()->{f.same(shielded,a,"small_full_delayed");f.same(bare,b,"small_empty_delayed");h.succeed();});
        });
    }
    private static void mitigated(GameTestHelper h,int level) {
        Fixture f=new Fixture(h,"mitigated_lv"+level);ServerPlayer iron=f.player("electromaster",level,4),resistance=f.player("electromaster",level,10),absorption=f.player("electromaster",level,16);
        Zombie z=f.zombie(20);
            iron.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));iron.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
            iron.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));iron.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
        f.ready(()->{
            h.assertTrue(iron.getArmorValue()==15,"iron equipment attributes have received native ticks: "+iron.getArmorValue());
            f.shield(iron,BigDecimal.ZERO);f.shield(resistance,BigDecimal.ZERO);
            resistance.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,200,0));
            absorption.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,0));absorption.setAbsorptionAmount(1.25F);
            h.assertTrue(absorption.getAbsorptionAmount()==1.25F,"native absorption effect establishes the required maximum");
            for(ServerPlayer p:new ServerPlayer[]{iron,resistance,absorption}) {
                Balances before=balances(p);Recorder r=f.recorder(p);p.invulnerableTime=0;h.assertTrue(z.doHurtTarget(p),"native mitigated hit");
                h.assertTrue(r.posts==1 && r.damage.signum()>0 && r.damage.compareTo(THREE)<0,"native mitigation reduces the three-point input armor="+p.getArmorValue()+" posts="+r.posts+" damage="+r.damage);
                if(p==absorption)h.assertTrue(p.getAbsorptionAmount()==0 && r.damage.compareTo(new BigDecimal("1.75"))==0,"native absorption consumed exactly once");
                BigDecimal s=r.damage.min(before.s);f.check(p,before,r.damage,r.damage.subtract(s),s,r,p==iron?"iron_armor":p==resistance?"resistance_I":"native_absorption_1.25");
            }
            Balances a=balances(iron),b=balances(resistance),c=balances(absorption);
            h.runAfterDelay(6,()->{f.same(iron,a,"iron_delayed");f.same(resistance,b,"resistance_delayed");f.same(absorption,c,"absorption_delayed");h.succeed();});
        });
    }
    private static void cooldown(GameTestHelper h,int level) {
        Fixture f=new Fixture(h,"cooldown_lv"+level);ServerPlayer p=f.player("electromaster",level,4);Zombie z=f.zombie(8);
        f.ready(()->{
            f.shield(p,BigDecimal.ZERO);Balances before=balances(p);Recorder r=f.recorder(p);p.invulnerableTime=0;
            h.assertTrue(z.doHurtTarget(p),"first three-point zombie hit");
            h.assertFalse(z.doHurtTarget(p),"equal hit refused during hurt cooldown");
            h.assertFalse(p.hurt(h.getLevel().damageSources().mobAttack(z),.1F),"weaker hit refused during hurt cooldown");
            h.assertTrue(p.hurt(h.getLevel().damageSources().mobAttack(z),5),"only stronger residual accepted");
            f.check(p,before,new BigDecimal("5"),new BigDecimal("5"),BigDecimal.ZERO,r,"cooldown_3_3_0.1_5_total5");
            h.assertTrue(r.posts==2,"two admitted settlements, not four: "+r.posts);Balances after=balances(p);
            h.runAfterDelay(6,()->{f.same(p,after,"cooldown_delayed");h.succeed();});
        });
    }
    private record Balances(BigDecimal h,BigDecimal s) {}
    private static Balances balances(LivingEntity entity) {if(!HighPowerCombat.enabled(entity))throw new IllegalStateException("invalid ledger");var tag=entity.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);return new Balances(new BigDecimal(tag.getString("health")),HighPowerCombat.shield(entity));}
    public static final class Recorder {
        final LivingEntity victim;int inputs,posts;BigDecimal damage=BigDecimal.ZERO;String type="";float incoming;
        Recorder(LivingEntity victim){this.victim=victim;}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void input(LivingIncomingDamageEvent e) {
            if(e.getEntity()==victim){inputs++;incoming=e.getAmount();type=e.getSource().typeHolder().unwrapKey().map(k->k.location().toString()).orElse("");}
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(LivingDamageEvent.Post e) {
            if(e.getEntity()==victim){posts++;damage=damage.add(new BigDecimal(Float.toString(e.getNewDamage())));}
        }
    }
    private static final class Fixture {
        final GameTestHelper h;final String name;final List<ServerPlayer> players=new ArrayList<>();final List<Entity> entities=new ArrayList<>();
        final Map<ServerPlayer,EmbeddedChannel> channels=new IdentityHashMap<>();final List<Object> hooks=new ArrayList<>();final List<Runnable> staged=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h,String name){
            this.h=h;this.name=name;EsperLegacyBypass.force(true);h.getLevel().setDayTime(18000);
            h.getLevel().getServer().setDifficulty(Difficulty.NORMAL,true);
            h.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,h.getLevel().getServer());
            h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,h.getLevel().getServer());
            h.testInfo.addListener(new GameTestListener(){
                public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}
                public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}
            });
        }
        ServerPlayer player(String category,int level,int x){
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"DL"+UUID.randomUUID().toString().substring(0,10)),false);
            var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());players.add(p);
            Connection c=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(c);channels.put(p,channel);
            NetworkRegistry.configureMockConnection(c);server.getPlayerList().placeNewPlayer(c,p,cookie);drain(p);
            p.setGameMode(GameType.SURVIVAL);place(p,x,4);p.setNoGravity(true);
            h.assertTrue(HighPowerCombat.enroll(p,level),"enrolled real native player");
            var state=AbilityStorage.get(p);state.selectCategory(category);state.setLevel(level);state.activated=true;
            HighPowerResources.initializeBudget(p);hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p,h.testInfo.getTimeoutTicks()+5));return p;
        }
        CityNpcEntity npc(String profile,int level,int x){
            var n=CityNpcContent.NPC.get().create(h.getLevel());var tag=new CompoundTag();tag.putString("Esper",profile);tag.putInt("EsperLevel",level);n.readAdditionalSaveData(tag);n.setNoAi(true);n.setNoGravity(true);place(n,x,12);
            h.assertTrue(h.getLevel().addFreshEntity(n),"native city NPC joined");entities.add(n);n.combat();return n;
        }
        Zombie zombie(int x){
            var z=EntityType.ZOMBIE.create(h.getLevel());z.setNoAi(true);z.setNoGravity(true);place(z,x,20);z.setPersistenceRequired();
            // The roof prevents incidental sunlight damage; no weapon, attack attribute or strength effects are added.
            h.setBlock(new BlockPos(x,5,20),Blocks.STONE);h.assertTrue(h.getLevel().addFreshEntity(z),"vanilla zombie joined");entities.add(z);return z;
        }
        void place(Entity e,int x,int z){h.setBlock(new BlockPos(x,1,z),Blocks.STONE);Vec3 at=h.absoluteVec(new Vec3(x+.5,2,z+.5));e.moveTo(at.x,at.y,at.z,0,0);if(e instanceof ServerPlayer p)p.connection.resetPosition();}
        void ready(Runnable run){h.runAfterDelay(61,()->{h.assertFalse(EsperLegacyBypass.standAside(),"production esper guard enabled");h.assertTrue(EsperProtectionConfig.categoryShields(),"production category shields enabled");players.forEach(p->{p.doTick();drain(p);});run.run();});}
        void shield(LivingEntity p,BigDecimal amount){var tag=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);tag.putString("shield",amount.toPlainString());tag.putLong("lastShieldSpend",p.level().getGameTime());}
        Recorder recorder(LivingEntity p){Recorder r=new Recorder(p);hook(r);return r;}
        void hook(Object o){hooks.add(o);NeoForge.EVENT_BUS.register(o);}void unhook(Object o){NeoForge.EVENT_BUS.unregister(o);hooks.remove(o);}
        void eq(BigDecimal a,BigDecimal b,String message){h.assertTrue(a.compareTo(b)==0,message+": "+a+" != "+b);}
        void same(LivingEntity p,Balances expected,String label){Balances actual=balances(p);eq(actual.h,expected.h,label+" no delayed HP debit");eq(actual.s,expected.s,label+" no delayed shield debit");}
        void check(LivingEntity p,Balances before,BigDecimal damage,BigDecimal hp,BigDecimal shield,Recorder r,String label){
            eq(r.damage,damage,label+" native post-damage witness");Balances after=balances(p);
            eq(before.h.subtract(after.h),hp,label+" absolute HP debit");eq(before.s.subtract(after.s),shield,label+" absolute shield debit");
            eq(before.h.subtract(after.h).add(before.s.subtract(after.s)),damage,label+" combined damage conserved");
            h.assertTrue(p.isAlive() && p.getHealth()>0,"small incoming damage cannot kill healthy enrolled body");emit(p,before,r,label);
        }
        void emit(LivingEntity p,Balances before,Recorder r,String label){Balances after=balances(p);System.out.println("AG_DAMAGE_AUDIT\t"+name+"\t"+label+"\t"+p.getClass().getSimpleName()+"\t"+r.type+"\t"+r.incoming+"\t"+r.damage.toPlainString()+"\t"+before.h.toPlainString()+"\t"+after.h.toPlainString()+"\t"+before.s.toPlainString()+"\t"+after.s.toPlainString()+"\t"+r.inputs+"\t"+r.posts+"\t"+p.getHealth());}
        void drain(ServerPlayer p){var c=channels.get(p);c.runPendingTasks();c.flushOutbound();c.runPendingTasks();Object packet;while((packet=c.readOutbound())!=null){if(packet instanceof ClientboundPlayerPositionPacket pos)p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(pos.getId()));ReferenceCountUtil.release(packet);}}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);for(ServerPlayer p:players){NewSkillRuntime.remove(p);DevelopmentController.remove(p);AbilityStorage.remove(p);LegacySingleKeyOwnerEpoch.forget(p);p.server.getPlayerList().remove(p);}entities.forEach(Entity::discard);channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);EsperLegacyBypass.force(false);}
    }
}
