package cn.academy.port.gametest;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.canon.CanonExpansionCombatA;
import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.canon.CanonExpansionA;
import cn.academy.port.canon.CanonSkillsCombatA;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import java.math.BigDecimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.minecraft.world.phys.EntityHitResult;

/** Real server entities and accounting; each implemented expansion gets a separate native case. */
@GameTestHolder("academy_canon_a")
@PrefixGameTestTemplate(false)
public final class CanonExpansionARuntimeTests {
    private CanonExpansionARuntimeTests() {}
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_life_scan(GameTestHelper h) { exercise(h,"mental_life_scan"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_intent_scan(GameTestHelper h) { exercise(h,"mental_intent_scan"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_hostile_scan(GameTestHelper h) { exercise(h,"mental_hostile_scan"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_focus_break(GameTestHelper h) { exercise(h,"mental_focus_break"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_target_sense(GameTestHelper h) { exercise(h,"mental_target_sense"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_step(GameTestHelper h) { exercise(h,"dark_step"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_lift(GameTestHelper h) { exercise(h,"dark_lift"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_wing_brake(GameTestHelper h) { exercise(h,"dark_wing_brake"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_wing_rescue(GameTestHelper h) { exercise(h,"dark_wing_rescue"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_dash(GameTestHelper h) { exercise(h,"nitrogen_dash"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_brake(GameTestHelper h) { exercise(h,"nitrogen_brake"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_buoy(GameTestHelper h) { exercise(h,"nitrogen_buoy"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_shove(GameTestHelper h) { exercise(h,"nitrogen_shove"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_brake(GameTestHelper h) { exercise(h,"aero_brake"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_updraft(GameTestHelper h) { exercise(h,"aero_updraft"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_side_step(GameTestHelper h) { exercise(h,"aero_side_step"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_backstep(GameTestHelper h) { exercise(h,"aero_backstep"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_fall_brake(GameTestHelper h) { exercise(h,"aero_fall_brake"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_airborne_turn(GameTestHelper h) { exercise(h,"aero_airborne_turn"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_push(GameTestHelper h) { exercise(h,"aero_push"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_crosswind(GameTestHelper h) { exercise(h,"aero_crosswind"); }
    /** A shove moves a body: creepers (outside the mental-control tag) and AI-less mobs are pushed too, not refused. */
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_push_moves_any_hostile_body(GameTestHelper h) {
        var f=new Fixture(h,"aero_hand","aero_push");var p=f.p;
        try {
            f.loadChunks();h.setBlock(new BlockPos(4,2,2),Blocks.STONE);p.setOnGround(true);
            var creeper=h.spawn(EntityType.CREEPER,new Vec3(4.5,3,5.5));creeper.setNoAi(true);creeper.setNoGravity(true);creeper.setOnGround(true);
            Vec3 before=creeper.getDeltaMovement();double cp=HighPowerResources.cp(p);
            h.assertTrue(CanonSkillsCombatA.cast(p,"aero_push"),"creeper accepted as a shove target");
            h.assertTrue(HighPowerResources.cp(p)<cp,"actual shared CP paid");
            h.assertFalse(creeper.getDeltaMovement().equals(before),"actual creeper impulse");
            h.succeed();
        } finally { f.close(); }
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_disengage(GameTestHelper h) { exercise(h,"mental_disengage"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_attention_shift(GameTestHelper h) { exercise(h,"mental_attention_shift"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_interpose(GameTestHelper h) { exercise(h,"mental_interpose"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_redirect(GameTestHelper h) { exercise(h,"mental_redirect"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_conflict(GameTestHelper h) { exercise(h,"mental_conflict"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_suppress_pair(GameTestHelper h) { exercise(h,"mental_suppress_pair"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_pursuit_break(GameTestHelper h) { exercise(h,"mental_pursuit_break"); }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_item_pull_moves_owned_drop_without_duplication(GameTestHelper h) {
        var f=new Fixture(h,"aero_hand","aero_item_pull");ItemEntity item=null;
        try {
            f.loadChunks();item=f.p.drop(new ItemStack(Items.STONE,4),false);h.assertTrue(item!=null,"actual player toss created");
            item.setPos(f.p.getEyePosition().add(0,-.1,2));item.setDeltaMovement(Vec3.ZERO);item.setNoGravity(true);
            int count=item.getItem().getCount();double cp=HighPowerResources.cp(f.p);
            h.assertTrue(CanonSkillsCombatA.cast(f.p,"aero_item_pull"),"owned visible item pulled");
            h.assertTrue(item.getDeltaMovement().z<0 && item.getItem().getCount()==count && !item.isRemoved(),"real stack moves back intact");
            h.assertTrue(HighPowerResources.cp(f.p)<cp,"real CP paid");h.succeed();
        } finally {if(item!=null)item.discard();f.close();}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_feather_guard(GameTestHelper h) { arrowGuard(h,"dark_matter","dark_feather_guard"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_guard(GameTestHelper h) { arrowGuard(h,"nitrogen_armor","nitrogen_guard"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_arrow_deflect(GameTestHelper h) { arrowGuard(h,"aero_hand","aero_arrow_deflect"); }
    private static void arrowGuard(GameTestHelper h,String category,String id) {
        var f=new Fixture(h,category,id);Arrow arrow=new Arrow(EntityType.ARROW,h.getLevel());
        try {
            f.loadChunks();
            if(category.equals("nitrogen_armor")){f.select("nitrogen_armor");h.assertTrue(CanonSkillsCombatA.cast(f.p,"nitrogen_armor"),"shell active");f.select(id);}
            arrow.setPos(f.p.getEyePosition().add(0,0,3));arrow.setDeltaMovement(0,0,-1);arrow.setNoGravity(true);h.getLevel().addFreshEntity(arrow);
            BigDecimal before=HighPowerCombat.shield(f.p),maximum=new BigDecimal(HighPowerCombat.maxShieldText(f.p));
            double cp=HighPowerResources.cp(f.p);
            h.assertTrue(CanonSkillsCombatA.cast(f.p,id),"actual approaching arrow was deflected");
            h.assertTrue(!arrow.getDeltaMovement().equals(new Vec3(0,0,-1)),"server changes real arrow velocity");
            h.assertTrue(before.subtract(HighPowerCombat.shield(f.p)).compareTo(maximum.multiply(new BigDecimal("0.01")))==0,
                    "exact one-percent shield paid once");
            h.assertTrue(HighPowerResources.cp(f.p)<cp,"real CP paid");h.succeed();
        } finally {arrow.discard();f.close();}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_cone(GameTestHelper h) { exactStrike(h,"aero_cone"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_ground_blast(GameTestHelper h) { exactStrike(h,"aero_ground_blast"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_gust(GameTestHelper h) { exactStrike(h,"aero_gust"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_pressure_lance(GameTestHelper h) { exactStrike(h,"aero_pressure_lance"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_squall(GameTestHelper h) { exactStrike(h,"aero_squall"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_pierce(GameTestHelper h) { exactStrike(h,"dark_pierce"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_shard(GameTestHelper h) { exactStrike(h,"dark_shard"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_shard_salvo(GameTestHelper h) { exactStrike(h,"dark_shard_salvo"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_spread(GameTestHelper h) { exactStrike(h,"dark_spread"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_sweep(GameTestHelper h) { exactStrike(h,"dark_sweep"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_burst(GameTestHelper h) { exactStrike(h,"nitrogen_burst"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_cone(GameTestHelper h) { exactStrike(h,"nitrogen_cone"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_ground_wave(GameTestHelper h) { exactStrike(h,"nitrogen_ground_wave"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_sweep(GameTestHelper h) { exactStrike(h,"nitrogen_sweep"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_uppercut(GameTestHelper h) { exactStrike(h,"nitrogen_uppercut"); }

    private static void exactStrike(GameTestHelper h,String id) {
        var definition=CanonExpansionA.find(id);var f=new Fixture(h,definition.category(),id);
        boolean close=id.equals("nitrogen_ram") || id.equals("nitrogen_impact") || id.equals("dark_wing_charge");
        boolean airborne=id.equals("nitrogen_shock_ring") || id.equals("nitrogen_impact") || id.equals("dark_wing_charge");
        Zombie target=h.spawn(EntityType.ZOMBIE,new Vec3(4.5,airborne?6:3,close?4:5.5));
        try {
            f.loadChunks();target.setNoGravity(true);target.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(target,5),"exact target ledger enrolled");
            if(definition.category().equals("nitrogen_armor")){f.select("nitrogen_armor");
                h.assertTrue(CanonSkillsCombatA.cast(f.p,"nitrogen_armor"),"shell active");f.select(id);}
            if(id.equals("dark_wing_charge")){f.select("dark_wings");h.assertTrue(CanonSkillsCombatA.cast(f.p,"dark_wings"),"wings active");f.select(id);}
            h.setBlock(new BlockPos(4,2,2),Blocks.STONE);f.p.setOnGround(true);
            if(airborne){f.p.moveTo(h.absoluteVec(new Vec3(4.5,6,2.5)),0,0);f.p.setYHeadRot(0);f.p.setOnGround(false);
                if(id.equals("nitrogen_impact"))f.p.setDeltaMovement(0,-.4,0);}
            BigDecimal before=new BigDecimal(HighPowerCombat.healthText(target)).add(HighPowerCombat.shield(target));
            double cp=HighPowerResources.cp(f.p);
            h.assertTrue(CanonSkillsCombatA.cast(f.p,id),"real attack accepted: "+id);
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).add(HighPowerCombat.shield(target)).compareTo(before)<0,
                    "sealed exact damage settled: "+id);
            h.assertTrue(HighPowerResources.cp(f.p)<cp,"shared CP paid: "+id);h.succeed();
        } finally {target.discard();f.close();}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_conflict_ring(GameTestHelper h) { mentalGroup(h,"mental_conflict_ring"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_crossfire(GameTestHelper h) { mentalGroup(h,"mental_crossfire"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_crowd_detach(GameTestHelper h) { mentalGroup(h,"mental_crowd_detach"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_lure_pair(GameTestHelper h) { mentalGroup(h,"mental_lure_pair"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_panic_field(GameTestHelper h) { mentalGroup(h,"mental_panic_field"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_pursuit_field(GameTestHelper h) { mentalGroup(h,"mental_pursuit_field"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_relay_interference(GameTestHelper h) { mentalGroup(h,"mental_relay_interference"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_route_interrupt(GameTestHelper h) { mentalGroup(h,"mental_route_interrupt"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_silence_field(GameTestHelper h) { mentalGroup(h,"mental_silence_field"); }

    private static void mentalGroup(GameTestHelper h,String id) {
        var f=new Fixture(h,"mental_out",id);Fixture ally=null;var mobs=new java.util.ArrayList<Zombie>();
        net.minecraft.world.scores.PlayerTeam team=null;
        try {
            f.loadChunks();
            for(int x=2;x<=9;x++)for(int z=2;z<=11;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            double[][] coords={{4.5,5.5},{6.5,5.5},{6.5,7.5},{4.5,7.5}};
            for(double[] point:coords) {Zombie mob=h.spawn(EntityType.ZOMBIE,new Vec3(point[0],3,point[1]));
                mob.setNoGravity(true);mob.setOnGround(true);mobs.add(mob);}
            boolean route=id.equals("mental_route_interrupt") || id.equals("mental_panic_field") || id.equals("mental_relay_interference");
            if(route) for(Zombie mob:mobs)h.assertTrue(mob.getNavigation().moveTo(mob.getX(),mob.getY(),mob.getZ()+2,1),"real mob path started");
            if(id.equals("mental_crowd_detach")) {
                ally=new Fixture(h,"mental_out","mental_probe");ally.p.moveTo(h.absoluteVec(new Vec3(4.5,3,4)),0,0);ally.p.setShiftKeyDown(true);
                var scoreboard=f.p.server.getScoreboard();team=scoreboard.addPlayerTeam("MX"+UUID.randomUUID().toString().substring(0,8));
                scoreboard.addPlayerToTeam(f.p.getScoreboardName(),team);scoreboard.addPlayerToTeam(ally.p.getScoreboardName(),team);
                for(Zombie mob:mobs)mob.setTarget(ally.p);
            } else if(id.equals("mental_lure_pair")) {
                for(int i=1;i<mobs.size();i++)mobs.get(i).setTarget(f.p);
            } else if(id.equals("mental_silence_field") || id.equals("mental_pursuit_field")) {
                for(Zombie mob:mobs)mob.setTarget(f.p);
            }
            double cp=HighPowerResources.cp(f.p);h.assertTrue(CanonSkillsCombatA.cast(f.p,id),"real control cast: "+id);
            h.assertTrue(HighPowerResources.cp(f.p)<cp,"control paid CP");
            if(route)h.assertTrue(mobs.getFirst().getNavigation().isDone(),"real navigation interrupted");
            else if(id.equals("mental_conflict_ring") || id.equals("mental_crossfire"))
                h.assertTrue(mobs.stream().allMatch(m->m.getTarget() instanceof Zombie),"distinct real hostile targets assigned");
            else if(id.equals("mental_lure_pair"))
                h.assertTrue(mobs.stream().skip(1).filter(m->m.getTarget()==mobs.getFirst()).count()==2,"two attackers redirected to selected hostile");
            else h.assertTrue(mobs.getFirst().getTarget()==null,"real pursuing target cleared");
            AbilityStorage.get(f.p).setActivateState(false);
            for(Zombie mob:mobs)NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(mob));
            h.succeed();
        } finally {for(Zombie mob:mobs)mob.discard();if(team!=null)f.p.server.getScoreboard().removePlayerTeam(team);if(ally!=null)ally.close();f.close();}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_air_curtain(GameTestHelper h) { barrierTest(h,"aero_air_curtain"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_pressure_wall(GameTestHelper h) { barrierTest(h,"aero_pressure_wall"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_guard(GameTestHelper h) { barrierTest(h,"dark_guard"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_panel_dome(GameTestHelper h) { barrierTest(h,"dark_panel_dome"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_shard_screen(GameTestHelper h) { barrierTest(h,"dark_shard_screen"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_plate(GameTestHelper h) { barrierTest(h,"nitrogen_plate"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_shield_line(GameTestHelper h) { barrierTest(h,"nitrogen_shield_line"); }

    private static void barrierTest(GameTestHelper h,String id) {
        var definition=CanonExpansionA.find(id);var f=new Fixture(h,definition.category(),id);Arrow arrow=new Arrow(EntityType.ARROW,h.getLevel());
        try {
            f.loadChunks();if(definition.category().equals("nitrogen_armor")){f.select("nitrogen_armor");
                h.assertTrue(CanonSkillsCombatA.cast(f.p,"nitrogen_armor"),"shell established");f.select(id);}
            double cp=HighPowerResources.cp(f.p);h.assertTrue(CanonSkillsCombatA.cast(f.p,id),"barrier activated");
            h.assertTrue(HighPowerResources.cp(f.p)<cp,"activation paid CP");
            Vec3 hit=f.p.position().add(0,1,.3);
            boolean contact=id.equals("dark_guard");
            if (contact) arrow.setPos(hit.add(0,0,.5));
            else {
                Vec3 eye = f.p.getEyePosition();
                Vec3 centre = CanonExpansionCombatA.frontalBarrier(id)
                        ? eye.add(f.p.getLookAngle().normalize().scale(CanonExpansionCombatA.barrierDistance(id))) : eye;
                var box = CanonExpansionCombatA.barrierBox(centre, id);
                arrow.setPos(centre.x, centre.y, box.maxZ + .35);
            }
            arrow.setDeltaMovement(0,0,-1.2);arrow.setNoGravity(true);h.getLevel().addFreshEntity(arrow);
            BigDecimal before=HighPowerCombat.shield(f.p),max=new BigDecimal(HighPowerCombat.maxShieldText(f.p));
            if(contact){var event=new ProjectileImpactEvent(arrow,new EntityHitResult(f.p,hit));NeoForge.EVENT_BUS.post(event);
                h.assertTrue(event.isCanceled(),"actual impact was cancelled");}
            else NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(f.p));
            h.assertTrue(arrow.getDeltaMovement().z>0,"real crossing arrow reflected");
            h.assertTrue(before.subtract(HighPowerCombat.shield(f.p)).compareTo(max.multiply(new BigDecimal("0.01")))==0,
                    "one exact shield charge");
            h.succeed();
        } finally {arrow.discard();f.close();}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_vertical_jet(GameTestHelper h) { exercise(h,"aero_vertical_jet"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_double_jet(GameTestHelper h) { exercise(h,"aero_double_jet"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_ring_push(GameTestHelper h) {
        var f=new Fixture(h,"aero_hand","aero_ring_push");Zombie target=h.spawn(EntityType.ZOMBIE,new Vec3(4.5,3,5.5));
        try {f.loadChunks();target.setNoGravity(true);Vec3 before=target.getDeltaMovement();
            h.assertTrue(CanonSkillsCombatA.cast(f.p,"aero_ring_push"),"radial pressure emitted");
            h.assertFalse(target.getDeltaMovement().equals(before),"nearby hostile receives a real outward impulse");h.succeed();
        } finally {target.discard();f.close();}
    }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void aero_vortex(GameTestHelper h) {
        var f=new Fixture(h,"aero_hand","aero_vortex");ItemEntity item=null;
        try {f.loadChunks();item=f.p.drop(new ItemStack(Items.STONE,4),false);h.assertTrue(item!=null,"owned block toss created");
            item.setPos(f.p.getEyePosition().add(0,-.1,2));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(CanonSkillsCombatA.cast(f.p,"aero_vortex"),"real owned drop enters air vortex");
            h.assertTrue(Math.abs(item.getDeltaMovement().x)>.3 && item.getItem().getCount()==4,"tangential force moves intact stack");h.succeed();
        } finally {if(item!=null)item.discard();f.close();}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_rocket(GameTestHelper h) { exercise(h,"nitrogen_rocket"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_shard_return(GameTestHelper h) { itemApplication(h,"dark_matter","dark_shard_return"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_shard_orbit(GameTestHelper h) { itemApplication(h,"dark_matter","dark_shard_orbit"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_item_push(GameTestHelper h) { itemApplication(h,"nitrogen_armor","nitrogen_item_push"); }
    private static void itemApplication(GameTestHelper h,String category,String id) {
        var f=new Fixture(h,category,id);ItemEntity item=null;
        try {f.loadChunks();if(category.equals("nitrogen_armor")){f.select("nitrogen_armor");
                h.assertTrue(CanonSkillsCombatA.cast(f.p,"nitrogen_armor"),"shell active");f.select(id);}
            item=f.p.drop(new ItemStack(Items.STONE,4),false);h.assertTrue(item!=null,"real owned drop created");
            item.setPos(f.p.getEyePosition().add(0,-.1,2));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(CanonSkillsCombatA.cast(f.p,id),"real item application accepted");
            h.assertFalse(item.getDeltaMovement().equals(Vec3.ZERO),"real drop received a velocity");
            h.assertTrue(item.getItem().getCount()==4 && !item.isRemoved(),"owned stack remains intact");h.succeed();
        } finally {if(item!=null)item.discard();f.close();}
    }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_shell_reinforce(GameTestHelper h) {
        var f=new Fixture(h,"nitrogen_armor","nitrogen_shell_reinforce");
        try {f.loadChunks();f.select("nitrogen_armor");h.assertTrue(CanonSkillsCombatA.cast(f.p,"nitrogen_armor"),"shell active");
            h.assertTrue(HighPowerCombat.spendShieldFraction(f.p,new BigDecimal("0.10")),"exact shield depleted");
            BigDecimal before=HighPowerCombat.shield(f.p),maximum=new BigDecimal(HighPowerCombat.maxShieldText(f.p));
            f.select("nitrogen_shell_reinforce");double cp=HighPowerResources.cp(f.p);
            h.assertTrue(CanonSkillsCombatA.cast(f.p,"nitrogen_shell_reinforce"),"paid reinforcement accepted");
            h.assertTrue(HighPowerCombat.shield(f.p).subtract(before).compareTo(maximum.multiply(new BigDecimal("0.03")))==0,
                    "exact bounded three-percent shield restored");
            h.assertTrue(HighPowerResources.cp(f.p)<cp,"shared CP consumed");h.succeed();
        } finally {f.close();}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_fall_guard(GameTestHelper h) {
        var f=new Fixture(h,"nitrogen_armor","nitrogen_fall_guard");var hook=new FallWitness(f.p);NeoForge.EVENT_BUS.register(hook);
        try {f.loadChunks();f.select("nitrogen_armor");h.assertTrue(CanonSkillsCombatA.cast(f.p,"nitrogen_armor"),"shell active");
            f.select("nitrogen_fall_guard");h.assertTrue(CanonSkillsCombatA.cast(f.p,"nitrogen_fall_guard"),"fall guard established");
            f.p.causeFallDamage(10,1,f.p.damageSources().fall());
            h.assertTrue(hook.calls==1 && hook.multiplier==.25f,"native fall event receives pressure mitigation");h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(hook);f.close();}
    }
    public static final class FallWitness {
        final ServerPlayer owner;int calls;float multiplier;
        FallWitness(ServerPlayer owner){this.owner=owner;}
        @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST,receiveCanceled=true)
        public void fall(LivingFallEvent event){if(event.getEntity()==owner){calls++;multiplier=event.getDamageMultiplier();}}
    }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_ally_guard(GameTestHelper h) { allyGuardTest(h,"nitrogen_armor","nitrogen_ally_guard"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_bulwark(GameTestHelper h) { allyGuardTest(h,"nitrogen_armor","nitrogen_bulwark"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_wing_shelter(GameTestHelper h) { allyGuardTest(h,"dark_matter","dark_wing_shelter"); }
    private static void allyGuardTest(GameTestHelper h,String category,String id) {
        var owner=new Fixture(h,category,id);var ally=new Fixture(h,"mental_out","mental_probe");
        Arrow arrow=new Arrow(EntityType.ARROW,h.getLevel());var scoreboard=owner.p.server.getScoreboard();
        var team=scoreboard.addPlayerTeam("AG"+UUID.randomUUID().toString().substring(0,8));
        try {owner.loadChunks();ally.loadChunks();
            ally.p.moveTo(h.absoluteVec(new Vec3(4.5,3,4.5)),0,0);ally.p.setShiftKeyDown(true);
            scoreboard.addPlayerToTeam(owner.p.getScoreboardName(),team);scoreboard.addPlayerToTeam(ally.p.getScoreboardName(),team);
            if(category.equals("nitrogen_armor")){owner.select("nitrogen_armor");h.assertTrue(CanonSkillsCombatA.cast(owner.p,"nitrogen_armor"),"shell active");}
            else {owner.select("dark_wings");h.assertTrue(CanonSkillsCombatA.cast(owner.p,"dark_wings"),"wings active");}
            owner.select(id);h.assertTrue(CanonSkillsCombatA.cast(owner.p,id),"consented ally defense activated");
            Vec3 hit=ally.p.position().add(0,1,.3);arrow.setPos(hit.add(0,0,.5));arrow.setDeltaMovement(0,0,-1);arrow.setNoGravity(true);
            h.getLevel().addFreshEntity(arrow);BigDecimal before=HighPowerCombat.shield(owner.p);
            var event=new ProjectileImpactEvent(arrow,new EntityHitResult(ally.p,hit));NeoForge.EVENT_BUS.post(event);
            h.assertTrue(event.isCanceled() && arrow.getDeltaMovement().z>0,"real ally impact intercepted");
            h.assertTrue(HighPowerCombat.shield(owner.p).compareTo(before)<0,"actual owner shield spent");h.succeed();
        } finally {arrow.discard();scoreboard.removePlayerTeam(team);ally.close();owner.close();}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_matter_barrage(GameTestHelper h) { exactStrike(h,"dark_matter_barrage"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_rain(GameTestHelper h) { exactStrike(h,"dark_rain"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_shard_ring(GameTestHelper h) { exactStrike(h,"dark_shard_ring"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_spear_fan(GameTestHelper h) { exactStrike(h,"dark_spear_fan"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void dark_wing_charge(GameTestHelper h) { exactStrike(h,"dark_wing_charge"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_impact(GameTestHelper h) { exactStrike(h,"nitrogen_impact"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_ram(GameTestHelper h) { exactStrike(h,"nitrogen_ram"); }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void nitrogen_shock_ring(GameTestHelper h) { exactStrike(h,"nitrogen_shock_ring"); }

    private static void exercise(GameTestHelper h,String id) {
        var definition=CanonExpansionA.find(id);h.assertTrue(definition!=null,"implemented catalogue entry exists");
        var fixture=new Fixture(h,definition.category(),id); var p=fixture.p;
        Zombie target=null, other=null;
        try {
            fixture.loadChunks();
            boolean mental=id.startsWith("mental_"), shove=id.equals("aero_push") || id.equals("aero_crosswind") || id.equals("nitrogen_shove");
            if (mental || shove) {
                target=h.spawn(EntityType.ZOMBIE,new Vec3(4.5,3,5.5));
                target.setNoGravity(true); target.setOnGround(true);
                if (id.equals("mental_hostile_scan") || id.equals("mental_target_sense") || id.equals("mental_disengage")
                        || id.equals("mental_interpose") || id.equals("mental_pursuit_break") || id.equals("mental_suppress_pair"))target.setTarget(p);
                if (id.equals("mental_interpose") || id.equals("mental_redirect") || id.equals("mental_conflict") || id.equals("mental_suppress_pair")) {
                    other=h.spawn(EntityType.ZOMBIE,new Vec3(6.5,3,5.5)); other.setNoGravity(true);other.setOnGround(true);
                    if(id.equals("mental_suppress_pair"))other.setTarget(p);
                }
                if (id.equals("mental_focus_break")) {
                    for(int z=3;z<=9;z++)h.setBlock(new BlockPos(4,2,z),Blocks.STONE);
                    h.assertTrue(target.getNavigation().moveTo(target.getX(),target.getY(),target.getZ()+3,1),"real target navigation started");
                }
            }
            if (id.startsWith("nitrogen_")) {fixture.select("nitrogen_armor");h.assertTrue(CanonSkillsCombatA.cast(p,"nitrogen_armor"),"real shell established");fixture.select(id);}
            if (id.startsWith("dark_wing_")) {fixture.select("dark_wings");h.assertTrue(CanonSkillsCombatA.cast(p,"dark_wings"),"real wings established");fixture.select(id);}
            boolean airborne=id.equals("aero_fall_brake") || id.equals("aero_airborne_turn") || id.equals("nitrogen_buoy")
                    || id.equals("dark_lift") || id.equals("dark_wing_rescue") || id.equals("aero_vertical_jet") || id.equals("aero_double_jet");
            if (airborne) {p.moveTo(h.absoluteVec(new Vec3(4.5,6,2.5)),0,0);p.setYHeadRot(0);p.setOnGround(false);}
            else {h.setBlock(new BlockPos(4,2,2),Blocks.STONE);p.setOnGround(true);}
            if(id.endsWith("brake") || id.equals("aero_airborne_turn"))p.setDeltaMovement(.4,0,0);
            if(id.equals("aero_fall_brake") || id.equals("nitrogen_buoy") || id.equals("dark_wing_rescue") || id.equals("dark_lift") || id.equals("aero_vertical_jet") || id.equals("aero_double_jet"))p.setDeltaMovement(0,-.4,0);
            Vec3 before=p.getDeltaMovement(), targetBefore=target==null?Vec3.ZERO:target.getDeltaMovement();
            double cp=HighPowerResources.cp(p);
            h.assertTrue(CanonSkillsCombatA.cast(p,id),"server cast accepted: "+id);
            h.assertTrue(HighPowerResources.cp(p)<cp,"actual shared CP paid: "+id);
            if(shove) h.assertFalse(target.getDeltaMovement().equals(targetBefore),"actual mob impulse: "+id);
            else if(!mental) h.assertFalse(p.getDeltaMovement().equals(before),"actual player motion: "+id);
            if(id.equals("mental_focus_break"))h.assertTrue(target.getNavigation().isDone(),"real mob path stopped");
            if(id.equals("mental_disengage") || id.equals("mental_pursuit_break") || id.equals("mental_suppress_pair"))
                h.assertTrue(target.getTarget()==null,"pursuit actually cleared");
            if(id.equals("mental_attention_shift"))h.assertTrue(target.getTarget()==p,"attention actually redirected to caster");
            if(id.equals("mental_redirect") || id.equals("mental_interpose") || id.equals("mental_conflict"))
                h.assertTrue(target.getTarget()==other,"actual target redirected to other hostile");
            if(id.equals("mental_conflict"))h.assertTrue(other.getTarget()==target,"second hostile redirected back");
            if(id.equals("mental_suppress_pair"))h.assertTrue(other.getTarget()==null,"second pursuit cleared");
            double paid=HighPowerResources.cp(p);h.assertFalse(CanonSkillsCombatA.cast(p,id),"same-tick cooldown rejects duplicate");
            h.assertTrue(HighPowerResources.cp(p)==paid,"duplicate is free");
            if (java.util.Set.of("mental_disengage","mental_attention_shift","mental_interpose","mental_redirect",
                    "mental_conflict","mental_suppress_pair","mental_pursuit_break").contains(id)) {
                AbilityStorage.get(p).setActivateState(false);NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(target));
                var expected=(id.equals("mental_disengage") || id.equals("mental_interpose") || id.equals("mental_suppress_pair")
                        || id.equals("mental_pursuit_break")) ? p : null;
                h.assertTrue(target.getTarget()==expected,"lease expiration restores only its own target write");
                if(other!=null && (id.equals("mental_conflict") || id.equals("mental_suppress_pair"))) {
                    NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(other));
                    h.assertTrue(other.getTarget()==(id.equals("mental_suppress_pair")?p:null),"second lease restored");
                }
            }
            h.succeed();
        } finally {if(target!=null)target.discard();if(other!=null)other.discard();fixture.close();}
    }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_target_event_reentry_rolls_back_owned_write(GameTestHelper h) {
        var fixture=new Fixture(h,"mental_out","mental_attention_shift");var p=fixture.p;
        Zombie target=h.spawn(EntityType.ZOMBIE,new Vec3(4.5,3,5.5));target.setNoGravity(true);
        var hook=new TargetReentry(p,target);NeoForge.EVENT_BUS.register(hook);
        try {
            fixture.loadChunks();h.assertTrue(target.getTarget()==null,"hostile initially has no target");
            double before=HighPowerResources.cp(p);
            h.assertFalse(CanonSkillsCombatA.cast(p,"mental_attention_shift"),"reentrant deactivation aborts control");
            h.assertTrue(hook.calls==1 && target.getTarget()==null,"actual target callback fired and owned mutation rolled back");
            h.assertTrue(HighPowerResources.cp(p)<before,"already paid cost remains final");h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(hook);target.discard();fixture.close();}
    }
    public static final class TargetReentry {
        final ServerPlayer owner;final Zombie target;int calls;
        TargetReentry(ServerPlayer owner,Zombie target){this.owner=owner;this.target=target;}
        @SubscribeEvent public void target(LivingChangeTargetEvent event) {
            if(event.getEntity()==target && event.getOriginalAboutToBeSetTarget()==owner) {
                calls++;AbilityStorage.get(owner).setActivateState(false);
            }
        }
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void expansion_multi_target_rechecks_moved_next_target(GameTestHelper h) {
        var f=new Fixture(h,"aero_hand","aero_cone");
        Zombie first=h.spawn(EntityType.ZOMBIE,new Vec3(4.5,3,5.5));
        Zombie second=h.spawn(EntityType.ZOMBIE,new Vec3(5.5,3,7.0));
        var hook=new MoveDuringStrike(f.p,first,second,false);NeoForge.EVENT_BUS.register(hook);
        try {
            f.loadChunks();first.setNoGravity(true);first.setNoAi(true);second.setNoGravity(true);second.setNoAi(true);
            h.assertTrue(HighPowerCombat.enroll(first,5)&&HighPowerCombat.enroll(second,5),"exact target ledgers enrolled");
            BigDecimal before=total(second);h.assertTrue(CanonSkillsCombatA.cast(f.p,"aero_cone"),"paid area strike starts");
            h.assertTrue(hook.calls==1 && total(second).compareTo(before)==0,"callback moves subsequent target outside ability shape");h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(hook);first.discard();second.discard();f.close();}
    }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void expansion_single_target_rechecks_after_attack_callback(GameTestHelper h) {
        var f=new Fixture(h,"dark_matter","dark_shard");Zombie target=h.spawn(EntityType.ZOMBIE,new Vec3(4.5,3,5.5));
        var hook=new MoveDuringStrike(f.p,target,target,true);NeoForge.EVENT_BUS.register(hook);
        try {
            f.loadChunks();target.setNoGravity(true);target.setNoAi(true);h.assertTrue(HighPowerCombat.enroll(target,5),"exact target ledger enrolled");
            BigDecimal before=total(target);h.assertTrue(CanonSkillsCombatA.cast(f.p,"dark_shard"),"paid shard starts");
            h.assertTrue(hook.calls==1 && total(target).compareTo(before)==0,"same-target callback invalidates exact hit");h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(hook);target.discard();f.close();}
    }
    private static BigDecimal total(Zombie target){return new BigDecimal(HighPowerCombat.healthText(target)).add(HighPowerCombat.shield(target));}
    public static final class MoveDuringStrike {
        final ServerPlayer owner;final Zombie first,moved;final boolean same;int calls;
        MoveDuringStrike(ServerPlayer owner,Zombie first,Zombie moved,boolean same){this.owner=owner;this.first=first;this.moved=moved;this.same=same;}
        @SubscribeEvent public void attack(SkillAttackEvent e) {
            if(e.player==owner && e.target==first && e.skill.equals(same?"dark_matter.dark_shard":"aero_hand.aero_cone")) {
                calls++;
                double beyondReach = SkillTuning.number(same ? "dark_shard" : "aero_cone", "reach") + 2;
                moved.setPos(owner.position().add(0,0,beyondReach));
            }
        }
    }
    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_lease_does_not_overwrite_other_target_change(GameTestHelper h) {
        var f=new Fixture(h,"mental_out","mental_attention_shift");
        Zombie subject=h.spawn(EntityType.ZOMBIE,new Vec3(4.5,3,5.5));Zombie other=h.spawn(EntityType.ZOMBIE,new Vec3(6.5,3,5.5));
        try {
            f.loadChunks();subject.setNoGravity(true);other.setNoGravity(true);
            h.assertTrue(CanonSkillsCombatA.cast(f.p,"mental_attention_shift") && subject.getTarget()==f.p,"owned target lease active");
            subject.setTarget(other);AbilityStorage.get(f.p).setActivateState(false);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(subject));
            h.assertTrue(subject.getTarget()==other,"retirement preserves intervening third-party target");h.succeed();
        } finally {subject.discard();other.discard();f.close();}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void mental_existing_and_expansion_controllers_cannot_compete(GameTestHelper h) {
        var f=new Fixture(h,"mental_out","mental_attention_shift");Zombie target=h.spawn(EntityType.ZOMBIE,new Vec3(4.5,3,5.5));
        try {f.loadChunks();target.setNoGravity(true);f.select("mental_disrupt");
            h.assertTrue(CanonSkillsCombatA.cast(f.p,"mental_disrupt"),"legacy mental lease started");
            f.select("mental_attention_shift");double cp=HighPowerResources.cp(f.p);
            h.assertFalse(CanonSkillsCombatA.cast(f.p,"mental_attention_shift"),"expansion cannot steal leased target");
            h.assertTrue(HighPowerResources.cp(f.p)==cp,"lease conflict is free");h.succeed();
        } finally {target.discard();f.close();}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void pursuit_field_blocks_reacquisition_but_preserves_other_target(GameTestHelper h) {
        var f=new Fixture(h,"mental_out","mental_pursuit_field");
        Zombie subject=h.spawn(EntityType.ZOMBIE,new Vec3(4.5,3,6.5));
        Zombie other=h.spawn(EntityType.ZOMBIE,new Vec3(7.5,3,6.5));
        try {
            f.loadChunks();subject.setNoGravity(true);other.setNoGravity(true);subject.setTarget(f.p);
            h.assertTrue(CanonSkillsCombatA.cast(f.p,"mental_pursuit_field"),"pursuit field pays and starts");
            for(int i=0;i<8;i++) {
                subject.setTarget(f.p);
                h.assertTrue(subject.getTarget()==null,"same protected player cannot be immediately reacquired");
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(subject));
            }
            subject.setTarget(other);NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(subject));
            h.assertTrue(subject.getTarget()==other,"another controller's distinct target is retained");
            h.assertFalse(cn.academy.port.canon.CanonExpansionCombatA.mentalLeased(subject),"foreign target retires the lease");
            h.succeed();
        } finally {subject.discard();other.discard();f.close();}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void pursuit_field_deactivation_restores_pursuit(GameTestHelper h) {
        var f=new Fixture(h,"mental_out","mental_pursuit_field");
        Zombie subject=h.spawn(EntityType.ZOMBIE,new Vec3(4.5,3,6.5));
        try {
            f.loadChunks();subject.setNoGravity(true);subject.setTarget(f.p);
            h.assertTrue(CanonSkillsCombatA.cast(f.p,"mental_pursuit_field") && subject.getTarget()==null,"pursuit stopped");
            AbilityStorage.get(f.p).setActivateState(false);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(subject));
            h.assertTrue(subject.getTarget()==f.p,"deactivation restores the prior target");
            h.assertFalse(cn.academy.port.canon.CanonExpansionCombatA.mentalLeased(subject),"deactivation removes the lease");
            h.succeed();
        } finally {subject.discard();f.close();}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=2200)
    public static void pursuit_field_survives_real_ai_and_expires(GameTestHelper h) {
        var f=new Fixture(h,"mental_out","mental_pursuit_field");
        Zombie subject=h.spawn(EntityType.ZOMBIE,new Vec3(4.5,3,8.5));
        var hook=new PursuitAttempts(subject,f.p);NeoForge.EVENT_BUS.register(hook);
        Runnable cleanup=()->{NeoForge.EVENT_BUS.unregister(hook);subject.discard();f.close();};
        try {
            f.loadChunks();
            for(int x=1;x<=10;x++)for(int z=1;z<=11;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            subject.setNoGravity(true);
            subject.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
            subject.targetSelector.addGoal(0,new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                    subject,ServerPlayer.class,0,true,false,candidate->candidate==f.p));
            subject.setTarget(f.p);
            h.assertTrue(!subject.isNoAi() && CanonSkillsCombatA.cast(f.p,"mental_pursuit_field"),"real AI subject receives paid field");
            int duration = SkillTuning.integer("mental_pursuit_field", "duration_ticks");
            // The longer daylight run must keep the AI witness alive independently of sunlight damage.
            subject.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,duration+40,0,false,false));
            hook.calls=0;
            h.runAfterDelay(duration*3/4,()->{
                try {
                    h.assertTrue(hook.calls>0,"vanilla targeting goal actually attempted reacquisition during the lease");
                    h.assertTrue(subject.isAlive()&&f.p.isAlive(),"both real control participants remain alive throughout the long fixture");
                    h.assertTrue(subject.getTarget()==null && cn.academy.port.canon.CanonExpansionCombatA.mentalLeased(subject),
                            "actual AI ticks preserve the interruption and its control lease");
                } catch(Throwable failure) {cleanup.run();throw failure;}
            });
            h.runAfterDelay(duration+7,()->{
                try {
                    h.assertFalse(cn.academy.port.canon.CanonExpansionCombatA.mentalLeased(subject),"native configured lease expired");
                    h.assertTrue(subject.getTarget()==f.p,"normal pursuit resumes after expiry");h.succeed();
                } finally {cleanup.run();}
            });
        } catch(Throwable failure) {cleanup.run();throw failure;}
    }

    @GameTest(template="empty",batch="canon_expansion_a",timeoutTicks=100)
    public static void cleared_mental_leases_hold_their_prior_targets(GameTestHelper h) {
        for(String id:java.util.List.of("mental_disengage","mental_suppress_pair","mental_pursuit_break","mental_silence_field")) {
            var f=new Fixture(h,"mental_out",id);
            Zombie first=h.spawn(EntityType.ZOMBIE,new Vec3(4.5,3,6.5));
            Zombie second=h.spawn(EntityType.ZOMBIE,new Vec3(6.5,3,6.5));
            try {
                f.loadChunks();first.setNoGravity(true);second.setNoGravity(true);first.setTarget(f.p);second.setTarget(f.p);
                h.assertTrue(CanonSkillsCombatA.cast(f.p,id) && first.getTarget()==null,"cleared-target lease starts: "+id);
                for(int i=0;i<8;i++) {
                    first.setTarget(f.p);NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(first));
                    h.assertTrue(first.getTarget()==null,"prior target remains suppressed: "+id);
                }
                AbilityStorage.get(f.p).setActivateState(false);NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(first));
                h.assertTrue(first.getTarget()==f.p,"deactivation restores prior target: "+id);
            } finally {first.discard();second.discard();f.close();}
        }
        h.succeed();
    }

    private static final class PursuitAttempts {
        final Zombie subject;final ServerPlayer protectedPlayer;int calls;
        PursuitAttempts(Zombie subject,ServerPlayer protectedPlayer){this.subject=subject;this.protectedPlayer=protectedPlayer;}
        @SubscribeEvent(receiveCanceled=true)
        public void target(LivingChangeTargetEvent event) {
            if(event.getEntity()==subject && event.getOriginalAboutToBeSetTarget()==protectedPlayer)calls++;
        }
    }

    private static final class Fixture {
        final ServerPlayer p;final EmbeddedChannel channel;
        Fixture(GameTestHelper h,String category,String skill) {
            var level=h.getLevel();UUID uuid=UUID.randomUUID();
            var cookie=CommonListenerCookie.createInitial(new GameProfile(uuid,"EX"+uuid.toString().substring(0,8)),false);
            p=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);level.getServer().getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.moveTo(h.absoluteVec(new Vec3(4.5,3,2.5)),0,0);p.setYHeadRot(0);
            var state=AbilityStorage.get(p);state.changeCategoryClassic(category);state.setLevel(5);
            switch(category) {
                case "mental_out" -> {state.learn("mental_probe");state.learn("mental_disrupt");}
                case "dark_matter" -> { state.learn("dark_blade");state.learn("dark_wings"); }
                case "nitrogen_armor" -> state.learn("nitrogen_armor");
                case "aero_hand" -> { state.learn("aero_jet");state.learn("aero_launch"); }
                default -> throw new IllegalStateException("unexpected category");
            }
            for(var definition:CanonExpansionA.skills())if(definition.category().equals(category))state.learn(definition.id());
            state.setActivateState(true);select(skill);
            h.assertTrue(HighPowerCombat.enroll(p,5),"actual exact-combat owner enrollment");HighPowerResources.initializeBudget(p);
        }
        void loadChunks() {BlockPos at=p.blockPosition();for(int x=(at.getX()-32)>>4;x<=(at.getX()+32)>>4;x++)
            for(int z=(at.getZ()-32)>>4;z<=(at.getZ()+32)>>4;z++)p.serverLevel().getChunk(x,z);}
        void select(String id){var state=AbilityStorage.get(p);state.presets.edit(0,0,id,state::learned);}
        void close(){try{p.server.getPlayerList().remove(p);}finally{channel.finishAndReleaseAll();}}
    }
}
