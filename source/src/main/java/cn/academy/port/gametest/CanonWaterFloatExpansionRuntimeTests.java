package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.canon.CanonFloatExpansion;
import cn.academy.port.canon.CanonSkillsCombatB;
import cn.academy.port.canon.CanonWaterExpansion;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.MagneticItemProvenance;
import cn.academy.port.api.SkillAttackEvent;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.UUID;
import java.math.BigDecimal;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

/** Real connected players, blocks and entities; executed only by the native GameTest server. */
@GameTestHolder("academy_canon_float")
@PrefixGameTestTemplate(false)
public final class CanonWaterFloatExpansionRuntimeTests {
    private CanonWaterFloatExpansionRuntimeTests() {}
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void real_water_source_is_required_to_douse_self(GameTestHelper h){
        var f=new Fixture(h,"hydro_hand","hydro_douse_self");
        try{
            f.p.setRemainingFireTicks(200);double cp=HighPowerResources.cp(f.p);
            h.assertFalse(CanonWaterExpansion.cast(f.p,"hydro_douse_self"),"no real water source rejects dousing");
            h.assertTrue(f.p.isOnFire()&&HighPowerResources.cp(f.p)==cp,"refusal leaves fire and CP intact");
            BlockPos water=f.p.blockPosition().offset(-1,0,0);f.p.serverLevel().setBlock(water,Blocks.WATER.defaultBlockState(),3);
            h.assertTrue(CanonWaterExpansion.cast(f.p,"hydro_douse_self"),"actual source permits dousing");
            h.assertFalse(f.p.isOnFire(),"native fire really cleared");
            h.assertTrue(f.p.serverLevel().getBlockState(water).is(Blocks.WATER),"source is not created or consumed");
            h.assertTrue(HighPowerResources.cp(f.p)<cp,"successful cast pays real CP");h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void candle_extinguish_changes_real_block_and_keeps_water(GameTestHelper h){
        var f=new Fixture(h,"hydro_hand","hydro_candle_out");
        try{
            BlockPos source=f.p.blockPosition().offset(-1,0,0),candle=f.p.blockPosition().offset(0,1,3);
            f.p.serverLevel().setBlock(source,Blocks.WATER.defaultBlockState(),3);
            f.p.serverLevel().setBlock(candle.below(),Blocks.STONE.defaultBlockState(),3);
            f.p.serverLevel().setBlock(candle,Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT,true),3);
            aimBlock(h,f.p,candle);
            h.assertTrue(CanonWaterExpansion.cast(f.p,"hydro_candle_out"),"real targeted candle is doused");
            h.assertFalse(f.p.serverLevel().getBlockState(candle).getValue(BlockStateProperties.LIT),"candle state changed");
            h.assertTrue(f.p.serverLevel().getBlockState(source).is(Blocks.WATER),"water remains real");h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void campfire_extinguish_retains_its_block_entity(GameTestHelper h){
        var f=new Fixture(h,"hydro_hand","hydro_campfire_out");
        try{
            BlockPos source=f.p.blockPosition().offset(-1,0,0),fire=f.p.blockPosition().offset(0,1,3);
            f.p.serverLevel().setBlock(source,Blocks.WATER.defaultBlockState(),3);
            f.p.serverLevel().setBlock(fire.below(),Blocks.STONE.defaultBlockState(),3);
            f.p.serverLevel().setBlock(fire,Blocks.CAMPFIRE.defaultBlockState(),3);
            var entity=f.p.serverLevel().getBlockEntity(fire);h.assertTrue(entity!=null,"real campfire block entity created");
            aimBlock(h,f.p,fire);
            h.assertTrue(CanonWaterExpansion.cast(f.p,"hydro_campfire_out"),"targeted lit campfire is doused");
            h.assertTrue(f.p.serverLevel().getBlockEntity(fire)==entity,"block entity and inventory retained");
            h.assertFalse(f.p.serverLevel().getBlockState(fire).getValue(BlockStateProperties.LIT),"actual campfire turned off");h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void water_jet_rechecks_current_target_after_actual_attack_event(GameTestHelper h){
        var f=new Fixture(h,"hydro_hand","hydro_pressure_jet");
        Zombie mob=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,7.5));var hook=new MoveTarget(f.p,mob);
        NeoForge.EVENT_BUS.register(hook);
        try{
            mob.setNoGravity(true);h.assertTrue(HighPowerCombat.enroll(mob,5),"hostile exact ledger enrolled");
            BlockPos water=f.p.blockPosition().offset(-1,0,0);f.p.serverLevel().setBlock(water,Blocks.WATER.defaultBlockState(),3);
            f.p.lookAt(EntityAnchorArgument.Anchor.EYES,mob.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            BigDecimal before=total(mob);double cp=HighPowerResources.cp(f.p);
            h.assertFalse(CanonWaterExpansion.cast(f.p,"hydro_pressure_jet"),"moved target prevents actual hit");
            h.assertTrue(hook.calls==1&&total(mob).compareTo(before)==0,"native callback moved target and exact ledger stayed intact");
            h.assertTrue(HighPowerResources.cp(f.p)<cp,"already paid resource remains final");h.succeed();
        }finally{NeoForge.EVENT_BUS.unregister(hook);mob.discard();f.close();}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void water_jet_commits_real_exact_damage(GameTestHelper h){
        var f=new Fixture(h,"hydro_hand","hydro_pressure_jet");
        Zombie mob=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,7.5));
        try{
            mob.setNoGravity(true);h.assertTrue(HighPowerCombat.enroll(mob,5),"hostile exact ledger enrolled");
            BlockPos water=f.p.blockPosition().offset(-1,0,0);f.p.serverLevel().setBlock(water,Blocks.WATER.defaultBlockState(),3);
            f.p.lookAt(EntityAnchorArgument.Anchor.EYES,mob.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            BigDecimal before=total(mob);double cp=HighPowerResources.cp(f.p);
            h.assertTrue(CanonWaterExpansion.cast(f.p,"hydro_pressure_jet"),"actual targeted water jet hits");
            h.assertTrue(total(mob).compareTo(before)<0&&HighPowerResources.cp(f.p)<cp,"exact damage and CP are committed");
            h.assertTrue(f.p.serverLevel().getBlockState(water).is(Blocks.WATER),"real source remains");h.succeed();
        }finally{mob.discard();f.close();}
    }
    private static BigDecimal total(Zombie e){return new BigDecimal(HighPowerCombat.healthText(e)).add(HighPowerCombat.shield(e));}
    public static final class MoveTarget{
        final ServerPlayer owner;final Zombie target;int calls;MoveTarget(ServerPlayer p,Zombie t){owner=p;target=t;}
        @SubscribeEvent public void attack(SkillAttackEvent event){
            if(event.player==owner&&event.target==target&&event.skill.equals("hydro_hand.hydro_pressure_jet")){
                calls++;target.setPos(owner.position().add(0,0,20));
            }
        }
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void self_hover_requires_air_and_stops_on_deactivation(GameTestHelper h){
        var f=new Fixture(h,"float_dial","float_hover");
        try{
            f.p.setOnGround(true);double cp=HighPowerResources.cp(f.p);
            h.assertFalse(CanonFloatExpansion.cast(f.p,"float_hover"),"grounded buoyancy refused");
            h.assertTrue(HighPowerResources.cp(f.p)==cp,"refusal does not consume CP");
            f.p.setOnGround(false);f.p.setDeltaMovement(0,-.5,0);
            h.assertTrue(CanonFloatExpansion.cast(f.p,"float_hover"),"airborne owner starts hover");
            h.runAtTickTime(3,()->{
                h.assertTrue(f.p.getDeltaMovement().y>-.2,"real downward speed is reduced");
                AbilityStorage.get(f.p).setActivateState(false);f.p.setDeltaMovement(0,-.5,0);
                h.runAtTickTime(5,()->{h.assertTrue(f.p.getDeltaMovement().y<-.2,"deactivation removes the lease");f.close();h.succeed();});
            });
        }catch(Throwable e){f.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void old_and_new_float_controllers_cannot_lease_same_target(GameTestHelper h){
        var f=new Fixture(h,"float_dial","float_suspend");
        try{
            Zombie mob=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,7.5));
            f.p.lookAt(EntityAnchorArgument.Anchor.EYES,mob.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            h.assertTrue(CanonFloatExpansion.cast(f.p,"float_suspend"),"new suspension starts on actual hostile");
            var s=AbilityStorage.get(f.p);s.learn("float_lift");f.select("float_lift");double cp=HighPowerResources.cp(f.p);
            h.assertFalse(CanonSkillsCombatB.cast(f.p,"float_lift"),"old controller rejects occupied target");
            h.assertTrue(HighPowerResources.cp(f.p)==cp,"lease conflict consumes no CP");mob.discard();h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void new_controller_rejects_existing_old_lease(GameTestHelper h){
        var f=new Fixture(h,"float_dial","float_lift");
        try{
            Zombie mob=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,7.5));
            f.p.lookAt(EntityAnchorArgument.Anchor.EYES,mob.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            h.assertTrue(CanonSkillsCombatB.cast(f.p,"float_lift"),"old controller starts a real lift");
            var s=AbilityStorage.get(f.p);s.learn("float_suspend");f.select("float_suspend");double cp=HighPowerResources.cp(f.p);
            h.assertFalse(CanonFloatExpansion.cast(f.p,"float_suspend"),"new controller rejects old occupied target");
            h.assertTrue(HighPowerResources.cp(f.p)==cp,"refused competition does not debit");mob.discard();h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void ally_ascent_requires_continuous_team_and_sneak_consent(GameTestHelper h){
        var f=new Fixture(h,"float_dial","float_ally_ascent");var ally=new Fixture(h,"float_dial","float_hover");
        var scoreboard=f.p.server.getScoreboard();var team=scoreboard.addPlayerTeam("wft"+UUID.randomUUID().toString().substring(0,8));
        try{
            scoreboard.addPlayerToTeam(f.p.getScoreboardName(),team);scoreboard.addPlayerToTeam(ally.p.getScoreboardName(),team);
            ally.p.moveTo(f.p.position().add(0,0,4),0,0);ally.p.setOnGround(false);ally.p.setDeltaMovement(0,-.5,0);
            f.p.lookAt(EntityAnchorArgument.Anchor.EYES,ally.p.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            double cp=HighPowerResources.cp(f.p);h.assertFalse(CanonFloatExpansion.cast(f.p,"float_ally_ascent"),"no consent means refusal");
            h.assertTrue(HighPowerResources.cp(f.p)==cp,"refused ally cast does not debit");
            ally.p.setShiftKeyDown(true);h.assertTrue(CanonFloatExpansion.cast(f.p,"float_ally_ascent"),"sneaking teammate agrees");
            h.runAtTickTime(3,()->{
                h.assertTrue(ally.p.getDeltaMovement().y>-.3,"real teammate receives upward buoyancy");
                ally.p.setShiftKeyDown(false);ally.p.setDeltaMovement(0,-.5,0);
                h.runAtTickTime(5,()->{h.assertTrue(ally.p.getDeltaMovement().y<-.2,"consent removal ends control");
                    scoreboard.removePlayerTeam(team);f.close();ally.close();h.succeed();});
            });
        }catch(Throwable e){scoreboard.removePlayerTeam(team);f.close();ally.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void float_ally_hover_controls_consenting_teammate(GameTestHelper h){allyMotion(h,"float_ally_hover");}
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void float_ally_descent_controls_consenting_teammate(GameTestHelper h){allyMotion(h,"float_ally_descent");}
    private static void allyMotion(GameTestHelper h,String id){
        var f=new Fixture(h,"float_dial",id);var ally=new Fixture(h,"float_dial","float_hover");
        var scoreboard=f.p.server.getScoreboard();var team=scoreboard.addPlayerTeam("wfam"+UUID.randomUUID().toString().substring(0,8));
        try{
            scoreboard.addPlayerToTeam(f.p.getScoreboardName(),team);scoreboard.addPlayerToTeam(ally.p.getScoreboardName(),team);
            ally.p.moveTo(f.p.position().add(0,0,4),0,0);ally.p.setOnGround(false);ally.p.setDeltaMovement(0,-.5,0);ally.p.setShiftKeyDown(true);
            f.p.lookAt(EntityAnchorArgument.Anchor.EYES,ally.p.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            h.assertTrue(CanonFloatExpansion.cast(f.p,id),"real consenting teammate accepted: "+id);
            h.runAtTickTime(3,()->{
                double y=ally.p.getDeltaMovement().y;
                if(id.equals("float_ally_hover"))h.assertTrue(y>-.2,"ally falling speed reduced by hover");
                else h.assertTrue(y<0&&y>=-.2,"ally descends at a bounded speed");
                scoreboard.removePlayerTeam(team);f.close();ally.close();h.succeed();
            });
        }catch(Throwable e){scoreboard.removePlayerTeam(team);f.close();ally.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void float_hostile_descent_uses_real_hostile_velocity(GameTestHelper h){hostileMotion(h,"float_hostile_descent");}
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void float_hover_field_holds_real_hostiles(GameTestHelper h){hostileMotion(h,"float_hover_field");}
    private static void hostileMotion(GameTestHelper h,String id){
        var f=new Fixture(h,"float_dial",id);Zombie target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,5,6.5));
        try{
            f.p.lookAt(EntityAnchorArgument.Anchor.EYES,target.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            h.assertTrue(CanonFloatExpansion.cast(f.p,id),"actual hostile receives buoyancy lease");
            h.runAtTickTime(3,()->{
                double y=target.getDeltaMovement().y;
                h.assertTrue(y>=-.2,"hostile motion is bounded");
                if(id.equals("float_hostile_descent"))h.assertTrue(y<0,"hostile descends");
                target.discard();f.close();h.succeed();
            });
        }catch(Throwable e){target.discard();f.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void float_rescue_field_brakes_actual_fall(GameTestHelper h){
        var f=new Fixture(h,"float_dial","float_rescue_field");
        try{
            f.p.setOnGround(false);f.p.setDeltaMovement(.4,-.5,0);
            h.assertTrue(CanonFloatExpansion.cast(f.p,"float_rescue_field"),"falling owner is selected by rescue field");
            h.runAtTickTime(3,()->{var v=f.p.getDeltaMovement();h.assertTrue(v.y>=-.2&&Math.abs(v.x)<.4,"real falling velocity is braked");f.close();h.succeed();});
        }catch(Throwable e){f.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void item_shelf_only_accepts_native_owned_drop_and_cleans_up(GameTestHelper h){
        var f=new Fixture(h,"float_dial","float_item_shelf");var other=new Fixture(h,"float_dial","float_item_shelf");
        ItemEntity own=null,foreign=null;
        try{
            foreign=other.p.drop(new ItemStack(Items.STONE,3),false);h.assertTrue(foreign!=null,"foreign native drop created");
            foreign.setPos(f.p.getEyePosition().add(0,-.1,2));foreign.setDeltaMovement(Vec3.ZERO);foreign.setPickUpDelay(100);
            f.p.lookAt(EntityAnchorArgument.Anchor.EYES,foreign.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            double cp=HighPowerResources.cp(f.p);h.assertFalse(CanonFloatExpansion.cast(f.p,"float_item_shelf"),"foreign drop refused");
            h.assertTrue(HighPowerResources.cp(f.p)==cp,"foreign refusal costs no CP");foreign.discard();foreign=null;
            own=f.p.drop(new ItemStack(Items.STONE,4),false);h.assertTrue(own!=null&&MagneticItemProvenance.ownedBy(own,f.p.getUUID()),"native own toss verified");
            own.setPos(f.p.getEyePosition().add(0,-.1,2));own.setDeltaMovement(Vec3.ZERO);own.setPickUpDelay(100);
            f.p.lookAt(EntityAnchorArgument.Anchor.EYES,own.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            h.assertTrue(CanonFloatExpansion.cast(f.p,"float_item_shelf"),"own item enters shelf");ItemEntity item=own;
            h.runAtTickTime(4,()->{
                h.assertTrue(item.isAlive()&&item.getItem().getCount()==4,"actual item remains intact");
                AbilityStorage.get(f.p).setActivateState(false);item.discard();f.close();other.close();h.succeed();
            });
        }catch(Throwable e){if(own!=null)own.discard();if(foreign!=null)foreign.discard();f.close();other.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void float_item_orbit_moves_owned_item_tangentially(GameTestHelper h){itemMotion(h,"float_item_orbit");}
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void float_item_shelf_field_holds_owned_drops(GameTestHelper h){itemMotion(h,"float_item_shelf_field");}
    private static void itemMotion(GameTestHelper h,String id){
        var f=new Fixture(h,"float_dial",id);ItemEntity one=null,two=null;
        try{
            one=f.p.drop(new ItemStack(Items.STONE,3),false);two=f.p.drop(new ItemStack(Items.DIRT,4),false);
            h.assertTrue(one!=null&&two!=null&&MagneticItemProvenance.ownedBy(one,f.p.getUUID())&&MagneticItemProvenance.ownedBy(two,f.p.getUUID()),"real owned drops");
            one.setPos(f.p.getEyePosition().add(0,-.1,2));two.setPos(f.p.getEyePosition().add(1,-.1,2));
            one.setDeltaMovement(Vec3.ZERO);two.setDeltaMovement(Vec3.ZERO);one.setPickUpDelay(100);two.setPickUpDelay(100);
            f.p.lookAt(EntityAnchorArgument.Anchor.EYES,one.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            h.assertTrue(CanonFloatExpansion.cast(f.p,id),"actual owned items controlled: "+id);
            ItemEntity a=one,b=two;Vec3 startA=one.position(),startB=two.position();
            h.runAtTickTime(4,()->{
                h.assertTrue(a.isAlive()&&a.getItem().getCount()==3&&b.isAlive()&&b.getItem().getCount()==4,"stacks remain intact");
                if(id.equals("float_item_orbit"))h.assertTrue(Math.abs(a.getX()-startA.x)>.02,"orbit makes real tangential movement");
                else h.assertTrue(a.position().distanceToSqr(startA)<1&&b.position().distanceToSqr(startB)<1,"both controlled items stay near their anchors");
                a.discard();b.discard();f.close();h.succeed();
            });
        }catch(Throwable e){if(one!=null)one.discard();if(two!=null)two.discard();f.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void float_ascent_moves_up(GameTestHelper h){selfMotion(h,"float_ascent");}
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void float_descent_moves_down(GameTestHelper h){selfMotion(h,"float_descent");}
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void float_air_brake_damps_horizontal_speed(GameTestHelper h){selfMotion(h,"float_air_brake");}
    private static void selfMotion(GameTestHelper h,String id){
        var f=new Fixture(h,"float_dial",id);
        try{
            f.p.setOnGround(false);f.p.setDeltaMovement(.4,-.5,0);
            h.assertTrue(CanonFloatExpansion.cast(f.p,id),"airborne skill creates actual lease: "+id);
            h.runAtTickTime(3,()->{
                var speed=f.p.getDeltaMovement();
                if(id.equals("float_ascent"))h.assertTrue(speed.y>0,"ascent adds real upward buoyancy");
                else if(id.equals("float_descent"))h.assertTrue(speed.y<0&&speed.y>=-.2,"descent is downward and bounded");
                else h.assertTrue(Math.abs(speed.x)<.4&&speed.y>=-.2,"braking damps horizontal and vertical velocity");
                f.close();h.succeed();
            });
        }catch(Throwable e){f.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void water_douses_targeted_hostile(GameTestHelper h){
        var f=new Fixture(h,"hydro_hand","hydro_douse_hostile");
        Zombie target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,7.5));
        try{
            BlockPos source=f.p.blockPosition().offset(-1,0,0);f.p.serverLevel().setBlock(source,Blocks.WATER.defaultBlockState(),3);
            target.setRemainingFireTicks(200);f.p.lookAt(EntityAnchorArgument.Anchor.EYES,target.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            h.assertTrue(CanonWaterExpansion.cast(f.p,"hydro_douse_hostile"),"real targeted burning hostile accepted");
            h.assertFalse(target.isOnFire(),"native fire cleared on hostile");h.assertTrue(f.p.serverLevel().getBlockState(source).is(Blocks.WATER),"source remains");h.succeed();
        }finally{target.discard();f.close();}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void water_douse_ally_requires_consent(GameTestHelper h){
        var f=new Fixture(h,"hydro_hand","hydro_douse_ally");var ally=new Fixture(h,"float_dial","float_hover");
        var scoreboard=f.p.server.getScoreboard();var team=scoreboard.addPlayerTeam("wfd"+UUID.randomUUID().toString().substring(0,8));
        try{
            scoreboard.addPlayerToTeam(f.p.getScoreboardName(),team);scoreboard.addPlayerToTeam(ally.p.getScoreboardName(),team);
            ally.p.moveTo(f.p.position().add(0,0,4),0,0);ally.p.setRemainingFireTicks(200);
            f.p.serverLevel().setBlock(f.p.blockPosition().offset(-1,0,0),Blocks.WATER.defaultBlockState(),3);
            f.p.lookAt(EntityAnchorArgument.Anchor.EYES,ally.p.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            double cp=HighPowerResources.cp(f.p);h.assertFalse(CanonWaterExpansion.cast(f.p,"hydro_douse_ally"),"nonconsenting player refused");
            h.assertTrue(ally.p.isOnFire()&&HighPowerResources.cp(f.p)==cp,"refusal preserves fire and cost");
            ally.p.setShiftKeyDown(true);h.assertTrue(CanonWaterExpansion.cast(f.p,"hydro_douse_ally"),"sneaking teammate consents");
            h.assertFalse(ally.p.isOnFire(),"native fire cleared");h.succeed();
        }finally{scoreboard.removePlayerTeam(team);f.close();ally.close();}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void water_fan_shares_exact_budget_across_real_targets(GameTestHelper h){
        var f=new Fixture(h,"hydro_hand","hydro_fan");
        Zombie a=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(3.5,3,6.5));
        Zombie b=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(5.5,3,6.5));
        try{
            a.setNoGravity(true);b.setNoGravity(true);
            h.assertTrue(HighPowerCombat.enroll(a,5)&&HighPowerCombat.enroll(b,5),"both targets have exact ledgers");
            BlockPos source=f.p.blockPosition().offset(-1,0,0);f.p.serverLevel().setBlock(source,Blocks.WATER.defaultBlockState(),3);
            BigDecimal beforeA=total(a),beforeB=total(b),budget=cn.academy.port.highpower.HighPowerGrowth.values(f.p).damage().multiply(cn.academy.port.highpower.CanonEmission.levelFactor(cn.academy.port.canon.CanonSkills.find("hydro_fan").orElseThrow().level()));
            h.assertTrue(CanonWaterExpansion.cast(f.p,"hydro_fan"),"source-backed fan attacks actual targets");
            BigDecimal paidA=beforeA.subtract(total(a)),paidB=beforeB.subtract(total(b));
            h.assertTrue(paidA.signum()>0&&paidB.signum()>0,"both real targets receive damage");
            h.assertTrue(paidA.add(paidB).compareTo(budget)<=0,"sum does not exceed single cast exact budget");h.succeed();
        }finally{a.discard();b.discard();f.close();}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void hydro_dive_requires_actual_water(GameTestHelper h){submergedSelf(h,"hydro_dive");}
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void hydro_lateral_current_requires_actual_water(GameTestHelper h){submergedSelf(h,"hydro_lateral_current");}
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void hydro_brake_field_controls_submerged_self(GameTestHelper h){submergedSelf(h,"hydro_brake_field");}
    private static void submergedSelf(GameTestHelper h,String id){
        var f=new Fixture(h,"hydro_hand",id);
        try{
            double cp=HighPowerResources.cp(f.p);h.assertFalse(CanonWaterExpansion.cast(f.p,id),"dry cast refused");
            h.assertTrue(HighPowerResources.cp(f.p)==cp,"dry refusal does not debit");
            f.acknowledgeTeleports();f.p.moveTo(h.absoluteVec(new Vec3(4.5,3,2.5)),0,0);f.p.setYHeadRot(0);
            BlockPos origin=f.p.blockPosition();for(BlockPos b:BlockPos.betweenClosed(origin.offset(-1,-1,-1),origin.offset(1,2,1)))
                f.p.serverLevel().setBlock(b,Blocks.WATER.defaultBlockState(),3);
            h.assertTrue(f.p.serverLevel().getFluidState(f.p.blockPosition()).is(net.minecraft.tags.FluidTags.WATER),"actual player block contains water");
            // Embedded players have no client-driven doTick; execute the native entity base tick,
            // which reads the real bounding box and fluid blocks to update water contact.
            f.p.baseTick();
            h.assertTrue(f.p.isInWaterOrBubble(),"native entity actually entered water at "+f.p.position());
            f.p.setDeltaMovement(.3,0,.3);h.assertTrue(CanonWaterExpansion.cast(f.p,id),"submerged skill succeeds");
            Vec3 v=f.p.getDeltaMovement();
            if(id.equals("hydro_dive"))h.assertTrue(v.y<-.3,"real dive impulse points downward");
            else if(id.equals("hydro_lateral_current"))h.assertTrue(Math.abs(v.x)>.3&&Math.abs(v.z)<.01,"real lateral impulse follows look side");
            else h.assertTrue(v.lengthSqr()<.02,"braking reduces actual movement");
            f.close();h.succeed();
        }catch(Throwable e){f.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void hydro_undertow_moves_real_submerged_hostile(GameTestHelper h){
        var f=new Fixture(h,"hydro_hand","hydro_undertow");Zombie target=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,5.5));
        try{
            BlockPos origin=f.p.blockPosition();for(BlockPos b:BlockPos.betweenClosed(origin.offset(-1,-1,-1),origin.offset(1,2,4)))
                f.p.serverLevel().setBlock(b,Blocks.WATER.defaultBlockState(),3);
            target.setNoAi(true);f.p.lookAt(EntityAnchorArgument.Anchor.EYES,target.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            h.runAtTickTime(4,()->{
                h.assertTrue(target.isInWaterOrBubble(),"actual hostile is submerged");target.setDeltaMovement(Vec3.ZERO);
                f.p.lookAt(EntityAnchorArgument.Anchor.EYES,target.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
                h.assertTrue(CanonWaterExpansion.cast(f.p,"hydro_undertow"),"real hostile is pulled");
                h.assertTrue(target.getDeltaMovement().z<0,"velocity points toward owner");target.discard();f.close();h.succeed();
            });
        }catch(Throwable e){target.discard();f.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void hydro_eddy_hits_only_submerged_hostile(GameTestHelper h){
        var f=new Fixture(h,"hydro_hand","hydro_eddy");Zombie wet=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(4.5,3,5.5));
        Zombie dry=h.spawnWithNoFreeWill(EntityType.ZOMBIE,new Vec3(7.5,3,5.5));
        try{
            BlockPos origin=f.p.blockPosition();for(BlockPos b:BlockPos.betweenClosed(origin.offset(-1,-1,-1),origin.offset(1,2,4)))
                f.p.serverLevel().setBlock(b,Blocks.WATER.defaultBlockState(),3);
            wet.setNoAi(true);dry.setNoAi(true);h.assertTrue(HighPowerCombat.enroll(wet,5)&&HighPowerCombat.enroll(dry,5),"real exact ledgers");
            h.runAtTickTime(4,()->{
                h.assertTrue(wet.isInWaterOrBubble()&&!dry.isInWaterOrBubble(),"only first hostile is actually in water");
                BigDecimal beforeWet=total(wet),beforeDry=total(dry);
                h.assertTrue(CanonWaterExpansion.cast(f.p,"hydro_eddy"),"water eddy attacks real submerged hostile");
                h.assertTrue(total(wet).compareTo(beforeWet)<0&&total(dry).compareTo(beforeDry)==0,"dry target receives no damage");
                wet.discard();dry.discard();f.close();h.succeed();
            });
        }catch(Throwable e){wet.discard();dry.discard();f.close();throw e;}
    }
    @GameTest(template="empty",batch="canon_float_expansion",timeoutTicks=80)
    public static void hydro_tow_ally_requires_submersion_and_consent(GameTestHelper h){
        var f=new Fixture(h,"hydro_hand","hydro_tow_ally");var ally=new Fixture(h,"float_dial","float_hover");
        var scoreboard=f.p.server.getScoreboard();var team=scoreboard.addPlayerTeam("wft"+UUID.randomUUID().toString().substring(0,8));
        try{
            scoreboard.addPlayerToTeam(f.p.getScoreboardName(),team);scoreboard.addPlayerToTeam(ally.p.getScoreboardName(),team);
            f.acknowledgeTeleports();ally.acknowledgeTeleports();
            f.p.moveTo(h.absoluteVec(new Vec3(4.5,3,2.5)),0,0);f.p.setYHeadRot(0);
            ally.p.moveTo(f.p.position().add(0,0,3),0,0);
            BlockPos origin=f.p.blockPosition();for(BlockPos b:BlockPos.betweenClosed(origin.offset(-1,-1,-1),origin.offset(1,2,4)))
                f.p.serverLevel().setBlock(b,Blocks.WATER.defaultBlockState(),3);
            h.assertTrue(ally.p.serverLevel().getFluidState(ally.p.blockPosition()).is(net.minecraft.tags.FluidTags.WATER),"actual teammate block contains water");
            ally.p.baseTick();
            h.assertTrue(ally.p.isInWaterOrBubble(),"real teammate submerged at "+ally.p.position());
            f.p.lookAt(EntityAnchorArgument.Anchor.EYES,ally.p.getBoundingBox().getCenter());f.p.setYHeadRot(f.p.getYRot());
            double cp=HighPowerResources.cp(f.p);h.assertFalse(CanonWaterExpansion.cast(f.p,"hydro_tow_ally"),"no consent refuses tow");
            h.assertTrue(HighPowerResources.cp(f.p)==cp,"refused tow has no CP charge");
            ally.p.setShiftKeyDown(true);ally.p.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(CanonWaterExpansion.cast(f.p,"hydro_tow_ally"),"sneaking teammate accepts tow");
            h.assertTrue(ally.p.getDeltaMovement().z<0,"actual tow moves ally toward owner");
            scoreboard.removePlayerTeam(team);f.close();ally.close();h.succeed();
        }catch(Throwable e){scoreboard.removePlayerTeam(team);f.close();ally.close();throw e;}
    }
    private static void aimBlock(GameTestHelper h,ServerPlayer player,BlockPos pos){
        var world=player.serverLevel();var shape=world.getBlockState(pos).getShape(world,pos);
        h.assertFalse(shape.isEmpty(),"target has a real native outline");
        var point=shape.bounds().move(pos).getCenter();player.lookAt(EntityAnchorArgument.Anchor.EYES,point);player.setYHeadRot(player.getYRot());
        Vec3 eye=player.getEyePosition(),end=eye.add(cn.academy.port.skill.ClassicRaytrace.direction(player).scale(6));
        var hit=world.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        h.assertTrue(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos),"server ray hits the intended real outline");
    }
    private static final class Fixture{
        final ServerPlayer p;final EmbeddedChannel channel;boolean closed;
        Fixture(GameTestHelper h,String category,String id){
            var level=h.getLevel();UUID uuid=UUID.randomUUID();var cookie=CommonListenerCookie.createInitial(new GameProfile(uuid,"CWF"+uuid.toString().substring(0,8)),false);
            p=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);
            channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);level.getServer().getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().instabuild=false;p.moveTo(h.absoluteVec(new Vec3(4.5,3,2.5)),0,0);
            BlockPos center=p.blockPosition();for(int x=(center.getX()-32)>>4;x<=(center.getX()+32)>>4;x++)
                for(int z=(center.getZ()-32)>>4;z<=(center.getZ()+32)>>4;z++)level.getChunk(x,z);
            var state=AbilityStorage.get(p);state.changeCategoryClassic(category);state.setLevel(5);state.learn(id);state.setActivateState(true);select(id);
            h.assertTrue(HighPowerCombat.enroll(p,5),"exact ledger enrolled");HighPowerResources.initializeBudget(p);
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}
                public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        void select(String id){var s=AbilityStorage.get(p);s.presets.edit(0,0,id,s::learned);}
        void acknowledgeTeleports(){Object packet;while((packet=channel.readOutbound())!=null){
            try{if(packet instanceof ClientboundPlayerPositionPacket position)
                p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));
            }finally{ReferenceCountUtil.release(packet);}
        }}
        void close(){if(closed)return;closed=true;try{p.server.getPlayerList().remove(p);}finally{channel.finishAndReleaseAll();}}
    }
}
