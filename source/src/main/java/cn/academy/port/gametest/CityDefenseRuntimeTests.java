package cn.academy.port.gametest;

import cn.academy.port.city.*;
import cn.academy.port.city.defense.*;
import cn.academy.port.fleet.*;
import cn.academy.port.highpower.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("academy_city_defense")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class CityDefenseRuntimeTests {
    private static final String TEMPLATE="defense_empty";
    @SubscribeEvent public static void template(LevelEvent.Load event){
        if(!GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof ServerLevel w))return;
        var t=new CompoundTag();var size=new ListTag();for(int n:new int[]{84,12,84})size.add(IntTag.valueOf(n));t.put("size",size);
        var pos=new ListTag();for(int i=0;i<3;i++)pos.add(IntTag.valueOf(0));var cell=new CompoundTag();cell.put("pos",pos);cell.putInt("state",0);var blocks=new ListTag();blocks.add(cell);
        var air=new CompoundTag();air.putString("Name","minecraft:air");var palette=new ListTag();palette.add(air);t.put("blocks",blocks);t.put("palette",palette);t.put("entities",new ListTag());
        w.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_city_defense",TEMPLATE)).load(w.registryAccess().lookupOrThrow(Registries.BLOCK),t);
    }
    private static Vec3 arena(GameTestHelper h){CityDefense.allowTestWorld(h.getLevel());for(int x=1;x<83;x++)for(int z=1;z<83;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);return Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(40,2,40)));}
    private static CityNpcEntity resident(GameTestHelper h,Vec3 p){var n=CityNpcContent.NPC.get().create(h.getLevel());n.moveTo(p.x,p.y,p.z,0,0);n.setNoAi(true);n.setInvulnerable(true);h.getLevel().addFreshEntity(n);n.combat();return n;}
    private static Husk suspect(GameTestHelper h,Vec3 p){var e=EntityType.HUSK.create(h.getLevel());e.moveTo(p.x,p.y,p.z,0,0);e.setNoAi(true);e.setPersistenceRequired();e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);e.setHealth(1000);h.getLevel().addFreshEntity(e);return e;}
    private static GuardianEntity officer(GameTestHelper h,Vec3 p,int role){var g=CityDefenseContent.GUARD.get().create(h.getLevel());g.moveTo(p.x,p.y,p.z,0,0);g.prepare(UUID.randomUUID(),role,3,h.getLevel().getGameTime()+2000,"测试");g.setNoAi(true);h.getLevel().addFreshEntity(g);return g;}
    private static void clean(ServerLevel w,CityDefenseData.Team t){for(var id:t.officers){var e=w.getEntity(id);if(e!=null){e.stopRiding();e.discard();}}for(var id:t.vehicles){var e=w.getEntity(id);if(e!=null)e.discard();}CityDefenseData.get(w).teams.remove(t.id);}
    @GameTest(template=TEMPLATE,batch="defense01",timeoutTicks=100)
    public static void real_damage_dispatches_ten_with_three_real_vehicles_and_no_duplicate_wave(GameTestHelper h){
        var p=arena(h);var n=resident(h,p);var e=suspect(h,p.add(8,0,0));float hp=n.getHealth();
        h.assertTrue(n.hurt(h.getLevel().damageSources().mobAttack(e),2),"ambient invulnerability admits real civilian harm in city");h.assertTrue(n.getHealth()<hp,"real injury occurred");
        var data=CityDefenseData.get(h.getLevel());var t=data.teams.values().stream().filter(x->x.victim.equals(n.getUUID())).findFirst().orElseThrow();
        h.assertTrue(t.officers.size()==10&&new HashSet<>(t.officers).size()==10,"exactly ten unique officers");h.assertTrue(t.vehicles.size()==3,"two patrol SUVs plus one rescue van");
        h.assertTrue(t.vehicles.stream().map(h.getLevel()::getEntity).mapToInt(Entity::countPlayerPassengers).sum()==0,"NPC crew uses actual vehicle seats");
        int boarded=t.vehicles.stream().map(h.getLevel()::getEntity).mapToInt(v->v.getPassengers().size()).sum();h.assertTrue(boarded==10,"all ten board real seats");
        for(int i=0;i<20;i++)h.assertTrue(CityDefense.dispatch(h.getLevel(),n,e)==t,"repeat hits coalesce");
        h.assertTrue(data.teams.size()==1,"one incident, one response");clean(h.getLevel(),t);n.discard();e.discard();h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="defense02",timeoutTicks=100)
    public static void environment_guard_and_outside_city_do_not_dispatch(GameTestHelper h){
        var p=arena(h);var n=resident(h,p);n.setInvulnerable(false);n.hurt(h.getLevel().damageSources().fall(),1);
        h.assertTrue(CityDefenseData.get(h.getLevel()).teams.isEmpty(),"environment has no suspect");
        var g=officer(h,p.add(3,0,0),0);n.invulnerableTime=0;n.hurt(h.getLevel().damageSources().mobAttack(g),1);h.assertTrue(CityDefenseData.get(h.getLevel()).teams.isEmpty(),"guard damage cannot chain dispatches");
        var nether=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(nether!=null&&!CityDefense.enabled(nether),"overworld/nether are not city by default");
        g.discard();n.discard();h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="defense03",timeoutTicks=100)
    public static void actual_trace_stops_at_civilian_and_wall_and_guard_ammunition_is_bounded(GameTestHelper h){
        var p=arena(h);var g=officer(h,p,0);var target=suspect(h,p.add(0,0,12));g.setTarget(target);var n=resident(h,p.add(0,0,6));
        float health=target.getHealth(),civilian=n.getHealth();h.assertTrue(!FleetBallistics.clearShot(g,target),"civilian blocks the muzzle ray");
        FleetBallistics.fire(g,2,target.getBoundingBox().getCenter().subtract(g.getEyePosition().add(0,-.12,0)));
        h.assertTrue(target.getHealth()==health&&n.getHealth()==civilian,"guardian cannot fire through or damage civilian");
        n.discard();h.assertTrue(FleetBallistics.clearShot(g,target),"clear target is detected");
        for(int y=0;y<3;y++)h.getLevel().setBlockAndUpdate(BlockPos.containing(p.add(0,y,6)),Blocks.STONE.defaultBlockState());
        h.assertTrue(!FleetBallistics.clearShot(g,target),"solid wall clips hitscan");
        GunItem.ammo(g.getMainHandItem(),Integer.MAX_VALUE);h.assertTrue(GunItem.ammo(g.getMainHandItem())==24,"server ammo capacity clamps");g.discard();target.discard();h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="defense04",timeoutTicks=110)
    public static void guardian_reload_conserves_real_magazines_through_save_and_resume(GameTestHelper h){
        var p=arena(h);var g=officer(h,p,0);GunItem.ammo(g.getMainHandItem(),3);int total=g.energy();h.assertTrue(g.beginReload(),"staged reload begins");
        h.runAfterDelay(18,()->{
            h.assertTrue(!GunItem.hasMagazine(g.getMainHandItem()),"old module detached at authored release frame");h.assertTrue(g.energy()==total,"energy conserved after release");
            var tag=new CompoundTag();g.saveWithoutId(tag);var copy=CityDefenseContent.GUARD.get().create(h.getLevel());copy.load(tag);copy.setUUID(UUID.randomUUID());g.discard();h.getLevel().addFreshEntity(copy);
            h.assertTrue(copy.energy()==total&&copy.reloading(),"inventory and action survive save");
            h.runAfterDelay(60,()->{h.assertTrue(copy.energy()==total&&GunItem.ammo(copy.getMainHandItem())==24&&!copy.reloading(),"resume commits insertion once, no resource refill");copy.discard();h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch="defense05",timeoutTicks=100)
    public static void guard_uses_existing_player_curve_and_incident_checkpoint_keeps_entity_ids(GameTestHelper h){
        var p=arena(h);var g=officer(h,p,0);
        h.assertTrue(HighPowerCombat.enabled(g),"approved numeric adapter");h.assertTrue(CityNpcPower.maxHealth(g).compareTo(new java.math.BigDecimal("63240"))==0,"Lv3 uses unchanged player 20D health");
        h.assertTrue(Math.abs(FleetBallistics.damage(g,2)-316.2)<.001,"gun is original raw5 x D/50");
        var n=resident(h,p);var e=suspect(h,p.add(8,0,0));var t=CityDefense.dispatch(h.getLevel(),n,e);
        var saved=CityDefenseData.get(h.getLevel()).save(new CompoundTag(),h.getLevel().registryAccess());var copy=CityDefenseData.load(saved).team(t.id);
        h.assertTrue(copy.officers.equals(t.officers)&&copy.vehicles.equals(t.vehicles)&&copy.threats.equals(t.threats),"reload preserves entity ownership, never respawns replacements");
        h.assertTrue(copy.expires==t.expires&&copy.center.equals(t.center),"duty clock and incident location persist");clean(h.getLevel(),t);g.discard();n.discard();e.discard();h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="defense06",timeoutTicks=400)
    public static void vehicles_really_drive_then_disembark_and_team_fires_and_flanks(GameTestHelper h){
        var p=arena(h);var n=resident(h,p);var e=suspect(h,p.add(6,0,0));HighPowerCombat.enroll(e,3);var t=CityDefense.dispatch(h.getLevel(),n,e);h.assertTrue(t!=null,"response created");
        var first=(LabVehicle)h.getLevel().getEntity(t.vehicles.getFirst());Vec3 start=first.position();var flank=(GuardianEntity)h.getLevel().getEntity(t.officers.get(4));
        var previous=new Vec3[]{flank.position()};double[] footTravel={0};h.onEachTick(()->{if(!flank.isPassenger())footTravel[0]+=flank.position().distanceTo(previous[0]);previous[0]=flank.position();});
        h.runAfterDelay(80,()->{h.assertTrue(first.position().distanceTo(start)>2,"AI driver moved the physical vehicle");});
        h.runAfterDelay(300,()->{
            int shots=0,foot=0,held=0;for(var id:t.officers)if(h.getLevel().getEntity(id) instanceof GuardianEntity g){shots+=g.shots();held+=g.heldFire();if(!g.isPassenger())foot++;}
            h.assertTrue(foot==10,"all officers safely disembarked");h.assertTrue(shots>0&&e.getHealth()<1000,"real shots damage suspect");h.assertTrue(flank.position().distanceToSqr(p)>9&&footTravel[0]>2,"flanker walks to a distinct position: "+footTravel[0]);
            clean(h.getLevel(),t);n.discard();e.discard();h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch="defense07",timeoutTicks=120)
    public static void medic_uses_limited_charges_and_escorts_real_injured_resident(GameTestHelper h){
        var p=arena(h);var n=resident(h,p);n.setHealth(8);var e=suspect(h,p.add(8,0,0));var t=CityDefense.dispatch(h.getLevel(),n,e);
        var medic=(GuardianEntity)h.getLevel().getEntity(t.officers.get(8));medic.stopRiding();medic.setPos(p.x+1,p.y,p.z);
        h.assertTrue(CityDefense.escorted(n),"civilian daily AI yields to temporary protection");
        h.runAfterDelay(70,()->{h.assertTrue(n.getHealth()>8&&medic.medicalCharges()<6&&medic.medicalCharges()>=4,"actual healing consumes finite supplies hp="+n.getHealth()+" charges="+medic.medicalCharges()+" action="+medic.action()+" distance="+medic.distanceToSqr(n));clean(h.getLevel(),t);h.assertTrue(!CityDefense.escorted(n),"daily AI lease released");n.discard();e.discard();h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch="defense08",timeoutTicks=100)
    public static void six_craftable_vehicles_and_magazines_match_dimensions_and_registered_recipes(GameTestHelper h){
        for(int i=0;i<6;i++){
            var gun=InteractionLab.GUNS[i].get();h.assertTrue(gun.capacity()==InteractionLab.CAPS[i],"distinct gun capacity");h.assertTrue(InteractionLab.VEHICLES[i].get().variant==i,"six deployment items");
            for(String recipe:new String[]{InteractionLab.GUN_IDS[i],InteractionLab.CAR_IDS[i]+"_crate","g0"+(i+1)+"_energy_magazine"})h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("academy",recipe)).isPresent(),"craftable "+recipe);
        }
        h.assertTrue(VehicleConfig.get(1).seats.size()==21&&VehicleConfig.get(3).seats.size()==4&&VehicleConfig.get(4).seats.size()==3,"real exported seat layouts retained");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="defense09",timeoutTicks=100)
    public static void player_six_guns_use_same_curve_and_real_one_round_debits(GameTestHelper h){
        var at=arena(h);var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"FleetNumbers"));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.moveTo(at.x,at.y,at.z,0,0);h.assertTrue(HighPowerCombat.enroll(p,3),"player enrolled on unchanged Lv3 curve");
        var enemy=suspect(h,at.add(0,0,12));HighPowerCombat.enroll(enemy,3);
        for(int i=0;i<6;i++){
            var gun=new net.minecraft.world.item.ItemStack(InteractionLab.GUNS[i].get());GunItem.ammo(gun,3);p.getInventory().setItem(p.getInventory().selected,gun);enemy.invulnerableTime=0;
            var before=CityNpcPower.health(enemy);LabGameplay.request(p,new LabNetwork.Request("fire",0,0));
            var expected=java.math.BigDecimal.valueOf(InteractionLab.DAMAGE[i]).multiply(new java.math.BigDecimal("3162")).divide(new java.math.BigDecimal("50")).multiply(new java.math.BigDecimal("0.984"));
            var actual=before.subtract(CityNpcPower.health(enemy));h.assertTrue(actual.subtract(expected).abs().doubleValue()<.01,"actual player gun native damage follows shared D/50 after armor: "+actual+" vs "+expected);
            h.assertTrue(GunItem.ammo(gun)==2,"one server shot consumes exactly one energy unit");
        }enemy.discard();h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="defense10",timeoutTicks=100)
    public static void distinct_neighborhood_responses_obey_simultaneous_team_limit(GameTestHelper h){
        arena(h);Vec3 p1=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5,2,5))),p2=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(79,2,5))),p3=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(42,2,79)));
        CityNpcEntity n1=resident(h,p1),n2=resident(h,p2),n3=resident(h,p3);Husk e1=suspect(h,p1.add(1,0,0)),e2=suspect(h,p2.add(1,0,0)),e3=suspect(h,p3.add(1,0,0));
        var a=CityDefense.dispatch(h.getLevel(),n1,e1);var b=CityDefense.dispatch(h.getLevel(),n2,e2);h.assertTrue(a!=null&&b!=null&&a!=b,"two independent ten-officer responses");
        h.assertTrue(CityDefense.dispatch(h.getLevel(),n3,e3)==null,"third response cannot exceed default cap");h.assertTrue(CityDefenseData.get(h.getLevel()).teams.values().stream().mapToInt(t->t.officers.size()).sum()==20,"maximum twenty officers");
        clean(h.getLevel(),a);clean(h.getLevel(),b);for(var e:new Entity[]{n1,n2,n3,e1,e2,e3})e.discard();h.succeed();
    }
}
