package cn.academy.port.gametest;

import cn.academy.port.AbilityDamage;
import cn.academy.port.AbilityStorage;
import cn.academy.port.canon.EntityAbilityRuntime;
import cn.academy.port.city.*;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import java.math.BigDecimal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;

/** Real entity/damage/save coverage; no fabricated player identity is used by the NPC. */
@GameTestHolder("academy_city_power")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class CityNpcPlayerParityRuntimeTests {
    private static final String TEMPLATE="city_power_empty",BATCH="city_power";
    @SubscribeEvent public static void template(LevelEvent.Load event) {
        if(!GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof ServerLevel world))return;
        var tag=new CompoundTag();var size=new ListTag();for(int n:new int[]{24,12,24})size.add(IntTag.valueOf(n));
        var pos=new ListTag();for(int n=0;n<3;n++)pos.add(IntTag.valueOf(0));
        var cell=new CompoundTag();cell.put("pos",pos);cell.putInt("state",0);var blocks=new ListTag();blocks.add(cell);
        var air=new CompoundTag();air.putString("Name","minecraft:air");var palette=new ListTag();palette.add(air);
        tag.put("size",size);tag.put("blocks",blocks);tag.put("palette",palette);tag.put("entities",new ListTag());
        world.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_city_power",TEMPLATE))
                .load(world.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }
    private static CityNpcEntity npc(GameTestHelper h,String profile,int level,int x) {
        var n=CityNpcContent.NPC.get().create(h.getLevel());var data=new CompoundTag();
        data.putString("Esper",profile);data.putInt("EsperLevel",level);n.readAdditionalSaveData(data);n.setNoAi(true);
        var pos=h.absolutePos(new BlockPos(x,2,4));h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        n.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);h.getLevel().addFreshEntity(n);n.combat();return n;
    }
    private static net.minecraft.world.entity.monster.Husk target(GameTestHelper h,int x) {
        var n=EntityType.HUSK.create(h.getLevel());n.setNoAi(true);var p=h.absolutePos(new BlockPos(x,2,4));
        h.getLevel().setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());n.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);h.getLevel().addFreshEntity(n);return n;
    }
    private static void eq(GameTestHelper h,BigDecimal a,BigDecimal b,String message){h.assertTrue(a.subtract(b).abs().compareTo(new BigDecimal("0.01"))<0,message+": "+a+" != "+b);}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void all_twelve_categories_use_player_caps_at_all_five_levels(GameTestHelper h) {
        EsperLegacyBypass.force(true);
        try {
            for(var p:CityEspers.Profile.values())if(p!=CityEspers.Profile.NONE)for(int level=1;level<=5;level++) {
                var n=npc(h,p.id(),level,3);var curve=ContinuousHighPowerGrowth.sample(BigDecimal.valueOf(level));
                h.assertTrue(HighPowerCombat.enabled(n),p+" enrolled at "+level);
                eq(h,CityNpcPower.maxHealth(n),curve.maxHealth(),"player maximum health");
                eq(h,HighPowerCombat.shield(n),curve.maxShield(),"player base shield");
                h.assertTrue(n.getMaxHealth()==20,"bounded native adapter");
                h.assertTrue(Math.abs(n.combat().additionalMaximum()-HighPowerGrowth.cpMaximumAt(BigDecimal.valueOf(level)))<.001,"same additional CP cap");
                h.assertTrue(n.combat().ability().maxCp()==ClassicRules.BASE_CP[level],"same classic CP cap");n.discard();
            }
            h.succeed();
        } finally {EsperLegacyBypass.force(false);}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_hit_spends_base_shield_before_health_and_overflow_is_exact(GameTestHelper h) {
        EsperLegacyBypass.force(true);
        try {
            var n=npc(h,"electro",1,3);var attacker=target(h,5);var hp=CityNpcPower.health(n);
            h.assertTrue(n.hurt(h.getLevel().damageSources().mobAttack(attacker),25),"native shield hit accepted");
            eq(h,HighPowerCombat.shield(n),new BigDecimal("75"),"shield debit");eq(h,CityNpcPower.health(n),hp,"HP protected");
            n.invulnerableTime=0;h.assertTrue(n.hurt(h.getLevel().damageSources().mobAttack(attacker),125),"overflow accepted");
            eq(h,HighPowerCombat.shield(n),BigDecimal.ZERO,"shield exhausted");eq(h,CityNpcPower.health(n),hp.subtract(new BigDecimal("50")),"true overflow");
            n.discard();attacker.discard();h.succeed();
        } finally {EsperLegacyBypass.force(false);}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void classic_npc_cast_uses_player_damage_and_original_cp_overload(GameTestHelper h) {
        EsperLegacyBypass.force(true);
        var n=npc(h,"electro",3,3);var t=target(h,7);HighPowerCombat.enroll(t,3);
        var d=EntityAbilityRuntime.definition("arc_gen");var cost=n.combat().runtime().cost(d);double cp=n.combat().ability().cp;
        var expected=BigDecimal.valueOf(ClassicRules.arc(.52).damage()).multiply(new BigDecimal("3162")).divide(new BigDecimal("50"));
        eq(h,BigDecimal.valueOf(cost.damage()),expected,"same player damage formula");
        h.assertTrue(n.combat().runtime().begin(d,t,false),"real NPC cast started");
        h.assertTrue(n.combat().ability().cp<cp&&n.combat().ability().overload>0,"classic resources charged");
        var before=CityNpcPower.health(t);
        h.runAfterDelay(16,()->{try {
            eq(h,before.subtract(CityNpcPower.health(t)),expected.multiply(new BigDecimal("0.984")),"actual native damage after Husk armor");h.succeed();
        }finally{n.discard();t.discard();EsperLegacyBypass.force(false);}});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void paper_cast_uses_player_damage_separate_cp_and_real_material(GameTestHelper h) {
        EsperLegacyBypass.force(true);
        var n=npc(h,"paper",3,3);var t=target(h,7);HighPowerCombat.enroll(t,3);
        var d=EntityAbilityRuntime.definition("paper_blade");double cp=n.combat().additionalCp(),classic=n.combat().ability().cp;
        var cost=n.combat().runtime().cost(d);h.assertTrue(cost.cp()==2&&cost.overload()==0&&cost.cooldown()==30,"same player costs");
        h.assertTrue(n.combat().runtime().begin(d,t,false),"real paper cast accepted");
        h.assertTrue(n.combat().additionalCp()==cp-2&&n.combat().ability().cp==classic,"correct CP pool");
        h.assertTrue(n.combat().save().getInt("paper")==23,"one real sheet consumed");var before=CityNpcPower.health(t);
        h.runAfterDelay(18,()->{try {
            eq(h,before.subtract(CityNpcPower.health(t)),new BigDecimal("1866.8448"),"player tier-one damage after Husk armor");h.succeed();
        }finally{n.discard();t.discard();EsperLegacyBypass.force(false);}});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void npc_checkpoint_preserves_injury_shield_both_cp_pools_and_cooldown(GameTestHelper h) {
        EsperLegacyBypass.force(true);
        try {
            var n=npc(h,"paper",3,3);var attacker=target(h,5);
            h.assertTrue(n.hurt(h.getLevel().damageSources().mobAttack(attacker),33000),"real injury after shield exhaustion");
            HighPowerCombat.rechargeShieldFraction(n,new BigDecimal("0.25"));
            h.assertTrue(CityNpcPower.health(n).compareTo(CityNpcPower.maxHealth(n))<0&&HighPowerCombat.shield(n).signum()>0,"injured HP and partially recovered shield");
            n.combat().ability().cp-=100;h.assertTrue(n.combat().spendAdditional("paper_blade",2,30),"reserve debit");
            var saved=n.combat().save();var restored=npc(h,"paper",3,9);restored.combat().load(saved);
            eq(h,HighPowerCombat.shield(restored),HighPowerCombat.shield(n),"no shield refill");
            eq(h,CityNpcPower.health(restored),CityNpcPower.health(n),"no HP refill");
            h.assertTrue(restored.combat().ability().cp==n.combat().ability().cp&&restored.combat().additionalCp()==n.combat().additionalCp(),"both balances retained");
            h.assertTrue(restored.combat().ability().cooldowns.get("paper_blade")==30,"cooldown retained");
            var legacy=saved.copy();legacy.remove("player_power");legacy.remove("skill_cp");legacy.putFloat("health",25);
            restored.combat().load(legacy);eq(h,CityNpcPower.health(restored),new BigDecimal("31620"),"classic injury fraction retained");
            n.discard();restored.discard();attacker.discard();h.succeed();
        } finally {EsperLegacyBypass.force(false);}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void sparring_limits_use_true_pools_in_both_directions(GameTestHelper h) {
        EsperLegacyBypass.force(true);
        var n=npc(h,"electro",3,3);var world=h.getLevel();var server=world.getServer();
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ParityTest"),false);
        var player=new net.minecraft.server.level.ServerPlayer(server,world,cookie.gameProfile(),cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);
        // Keep the connected player's simulation area on the native test structures.
        player.moveTo(n.getX()+2,n.getY(),n.getZ(),0,0);player.connection.resetPosition();
        AbilityStorage.get(player).changeCategoryClassic("electromaster");AbilityStorage.get(player).setLevel(3);
        AbilityStorage.get(player).learn("arc_gen");AbilityStorage.get(player).activated=true;HighPowerCombat.enroll(player,3);
        h.runAfterDelay(65,()->{try {
            player.moveTo(n.getX()+2,n.getY(),n.getZ(),0,0);player.connection.resetPosition();
            h.assertTrue(n.combat().sparringIncoming(player,new BigDecimal("10000")).signum()==0,"outside-duel exact hit refused");
            h.assertTrue(n.combat().startDuel(player),"consensual duel started");
            var hp=CityNpcPower.health(player);h.assertTrue(AbilityDamage.attackEntity(n,"electromaster.arc_gen",player,1e9,false),"NPC native hit accepted");
            h.assertTrue(CityNpcPower.health(player).compareTo(hp.multiply(new BigDecimal("0.15")))>=0,"player survives at the true floor");
            n.invulnerableTime=0;AbilityDamage.attack(player,"electromaster.arc_gen",n,1e9);
            h.assertTrue(n.isAlive()&&CityNpcPower.health(n).compareTo(CityNpcPower.maxHealth(n).multiply(new BigDecimal("0.15")))>=0,"NPC survives at the true floor");
            h.succeed();
        } finally {n.discard();server.getPlayerList().remove(player);player.discard();channel.finishAndReleaseAll();EsperLegacyBypass.force(false);}});
    }
}
