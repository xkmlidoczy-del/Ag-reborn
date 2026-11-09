package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.canon.CanonLiquidCombatExpansion;
import cn.academy.port.canon.CanonLiquidEntities;
import cn.academy.port.canon.CanonSkillsCombatB;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real materialized bodies, contact geometry and closed combat accounting. */
@GameTestHolder("academy_canon_media")
@PrefixGameTestTemplate(false)
public final class CanonLiquidCombatExpansionRuntimeTests {
    private CanonLiquidCombatExpansionRuntimeTests() {}
    @GameTest(template="empty",batch="canon_liquid_contact",timeoutTicks=80)
    public static void contact_grapple_requires_real_body_and_stops_after_deactivation(GameTestHelper h){
        var f=new Fixture(h,"liquid_grapple");Zombie target=null;
        try{
            double cp=HighPowerResources.cp(f.p);h.assertFalse(CanonLiquidCombatExpansion.cast(f.p,"liquid_grapple"),"no medium body denies ability");
            h.assertTrue(HighPowerResources.cp(f.p)==cp,"refused cast is free");var body=f.createBody();target=f.contactTarget();
            target.setDeltaMovement(.4,0,0);f.select("liquid_grapple");f.aim(target);
            h.assertTrue(CanonLiquidCombatExpansion.cast(f.p,"liquid_grapple"),"real touching hostile is grappled");
            Zombie t=target;h.runAtTickTime(2,()->{
                h.assertTrue(Math.abs(t.getDeltaMovement().x)<.4,"real hostile horizontal velocity is restrained");
                AbilityStorage.get(f.p).setActivateState(false);t.setDeltaMovement(.4,0,0);
                h.runAtTickTime(4,()->{h.assertTrue(Math.abs(t.getDeltaMovement().x)>.1,"deactivation ends velocity restraint");
                    t.discard();f.close();h.succeed();});
            });
        }catch(Throwable e){if(target!=null)target.discard();f.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_liquid_contact",timeoutTicks=80)
    public static void contact_cut_uses_actual_body_and_exact_damage(GameTestHelper h){strike(h,"liquid_contact_cut");}
    @GameTest(template="empty",batch="canon_liquid_contact",timeoutTicks=80)
    public static void contact_crush_uses_actual_body_and_exact_damage(GameTestHelper h){strike(h,"liquid_contact_crush");}
    private static void strike(GameTestHelper h,String id){
        var f=new Fixture(h,id);Zombie target=null;
        try{
            var body=f.createBody();target=f.contactTarget();h.assertTrue(HighPowerCombat.enroll(target,5),"target exact ledger enrolled");
            f.select(id);f.aim(target);BigDecimal before=total(target);double cp=HighPowerResources.cp(f.p);
            h.assertTrue(CanonLiquidCombatExpansion.cast(f.p,id),"real shadow contacts and hits target: "+id);
            h.assertTrue(total(target).compareTo(before)<0&&HighPowerResources.cp(f.p)<cp,"exact damage and resource paid");
            target.discard();f.close();h.succeed();
        }catch(Throwable e){if(target!=null)target.discard();f.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_liquid_contact",timeoutTicks=80)
    public static void contact_sweep_shares_one_exact_budget(GameTestHelper h){
        var f=new Fixture(h,"liquid_contact_sweep");Zombie a=null,b=null;
        try{
            var body=f.createBody();a=f.contactTarget();b=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(5.3,3,4.7));b.setNoGravity(true);
            h.assertTrue(HighPowerCombat.enroll(a,5)&&HighPowerCombat.enroll(b,5),"both enemies have exact ledgers");
            f.select("liquid_contact_sweep");BigDecimal beforeA=total(a),beforeB=total(b),budget=HighPowerGrowth.values(f.p).damage().multiply(cn.academy.port.highpower.CanonEmission.levelFactor(cn.academy.port.canon.CanonSkills.find("liquid_contact_sweep").orElseThrow().level()));
            h.assertTrue(CanonLiquidCombatExpansion.cast(f.p,"liquid_contact_sweep"),"real shadow sweeps contacting hostiles");
            BigDecimal paidA=beforeA.subtract(total(a)),paidB=beforeB.subtract(total(b));
            h.assertTrue(paidA.signum()>0&&paidB.signum()>0&&paidA.add(paidB).compareTo(budget)<=0,"both hits share one exact budget");
            a.discard();b.discard();f.close();h.succeed();
        }catch(Throwable e){if(a!=null)a.discard();if(b!=null)b.discard();f.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_liquid_contact",timeoutTicks=80)
    public static void contact_cut_rejects_body_moved_inside_attack_callback(GameTestHelper h){
        var f=new Fixture(h,"liquid_contact_cut");Zombie target=null;MoveBody hook=null;
        try{
            var body=f.createBody();target=f.contactTarget();h.assertTrue(HighPowerCombat.enroll(target,5),"target exact ledger enrolled");
            f.select("liquid_contact_cut");f.aim(target);hook=new MoveBody(f.p,target,body);NeoForge.EVENT_BUS.register(hook);
            BigDecimal before=total(target);double cp=HighPowerResources.cp(f.p);
            h.assertFalse(CanonLiquidCombatExpansion.cast(f.p,"liquid_contact_cut"),"moved contact body denies settlement");
            h.assertTrue(hook.calls==1&&total(target).compareTo(before)==0,"actual callback cannot hit after contact is lost");
            h.assertTrue(HighPowerResources.cp(f.p)<cp,"already paid CP is not refunded");h.succeed();
        }finally{if(hook!=null)NeoForge.EVENT_BUS.unregister(hook);if(target!=null)target.discard();f.close();}
    }
    @GameTest(template="empty",batch="canon_liquid_contact",timeoutTicks=80)
    public static void contact_cut_rejects_visible_enemy_without_physical_contact(GameTestHelper h){
        var f=new Fixture(h,"liquid_contact_cut");Zombie target=null;
        try{
            f.createBody();target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,8));target.setNoGravity(true);
            f.select("liquid_contact_cut");f.aim(target);double cp=HighPowerResources.cp(f.p);
            h.assertFalse(CanonLiquidCombatExpansion.cast(f.p,"liquid_contact_cut"),"visible but remote target is not contact");
            h.assertTrue(HighPowerResources.cp(f.p)==cp,"no contact consumes no CP");h.succeed();
        }finally{if(target!=null)target.discard();f.close();}
    }
    private static BigDecimal total(Zombie e){return new BigDecimal(HighPowerCombat.healthText(e)).add(HighPowerCombat.shield(e));}
    public static final class MoveBody{
        final ServerPlayer owner;final Zombie target;final CanonLiquidEntities.Shadow body;int calls;
        MoveBody(ServerPlayer owner,Zombie target,CanonLiquidEntities.Shadow body){this.owner=owner;this.target=target;this.body=body;}
        @SubscribeEvent public void attack(SkillAttackEvent event){if(event.player==owner&&event.target==target
                &&event.skill.equals("liquid_shadow.liquid_contact_cut")){calls++;body.setPos(body.position().add(4,0,0));}}
    }
    private static final class Fixture{
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final String id;boolean closed;
        Fixture(GameTestHelper h,String id){
            this.h=h;this.id=id;var world=h.getLevel();UUID uuid=UUID.randomUUID();var cookie=CommonListenerCookie.createInitial(new GameProfile(uuid,"LC"+uuid.toString().substring(0,8)),false);
            p=new ServerPlayer(world.getServer(),world,cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);
            channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);world.getServer().getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().instabuild=false;p.moveTo(h.absoluteVec(new Vec3(4.5,3,2.5)),0,0);p.setYHeadRot(0);
            BlockPos center=p.blockPosition();for(int x=(center.getX()-32)>>4;x<=(center.getX()+32)>>4;x++)
                for(int z=(center.getZ()-32)>>4;z<=(center.getZ()+32)>>4;z++)world.getChunk(x,z);
            h.setBlock(new BlockPos(4,2,2),Blocks.STONE);h.setBlock(new BlockPos(4,2,4),Blocks.STONE);p.setOnGround(true);
            var s=AbilityStorage.get(p);s.changeCategoryClassic("liquid_shadow");s.setLevel(5);s.learn("liquid_scout");s.learn(id);s.setActivateState(true);select(id);
            h.assertTrue(HighPowerCombat.enroll(p,5),"actual exact owner ledger");HighPowerResources.initializeBudget(p);
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}
                public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        CanonLiquidEntities.Shadow createBody(){
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get()));select("liquid_scout");
            h.assertTrue(CanonSkillsCombatB.cast(p,"liquid_scout"),"real medium creates body");
            var found=p.serverLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,p.getBoundingBox().inflate(5));
            h.assertTrue(found.size()==1,"one owner body exists");return found.getFirst();
        }
        Zombie contactTarget(){Zombie t=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,5.32));t.setNoGravity(true);return t;}
        void aim(Zombie t){p.lookAt(EntityAnchorArgument.Anchor.EYES,t.getBoundingBox().getCenter());p.setYHeadRot(p.getYRot());}
        void select(String name){var s=AbilityStorage.get(p);s.presets.edit(0,0,name,s::learned);}
        void close(){if(closed)return;closed=true;try{p.server.getPlayerList().remove(p);}finally{channel.finishAndReleaseAll();}}
    }
}
