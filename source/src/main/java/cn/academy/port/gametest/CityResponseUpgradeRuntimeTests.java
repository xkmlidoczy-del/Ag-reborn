package cn.academy.port.gametest;

import cn.academy.port.city.defense.*;
import cn.academy.port.city.defense.armor.*;
import cn.academy.port.highpower.*;
import cn.academy.port.titan.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("academy_response_upgrade")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class CityResponseUpgradeRuntimeTests {
    private static final String TEMPLATE="response_empty";
    @SubscribeEvent public static void template(LevelEvent.Load event){
        if(!GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof ServerLevel w))return;
        var t=new CompoundTag();var size=new ListTag();for(int n:new int[]{96,48,96})size.add(IntTag.valueOf(n));t.put("size",size);
        var pos=new ListTag();for(int i=0;i<3;i++)pos.add(IntTag.valueOf(0));var cell=new CompoundTag();cell.put("pos",pos);cell.putInt("state",0);var blocks=new ListTag();blocks.add(cell);
        var air=new CompoundTag();air.putString("Name","minecraft:air");var palette=new ListTag();palette.add(air);t.put("blocks",blocks);t.put("palette",palette);t.put("entities",new ListTag());
        w.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_response_upgrade",TEMPLATE)).load(w.registryAccess().lookupOrThrow(Registries.BLOCK),t);
    }
    private record Scene(ServerLevel world,Vec3 at,Villager civilian,Husk enemy,CityDefenseData.Team team){}
    private static Scene scene(GameTestHelper h){
        var w=h.getLevel();CityDefense.allowTestWorld(w);for(int x=1;x<95;x++)for(int z=1;z<95;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var p=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(47,2,47)));var n=EntityType.VILLAGER.create(w);n.moveTo(p.x,p.y,p.z,0,0);n.setNoAi(true);w.addFreshEntity(n);
        var enemy=EntityType.HUSK.create(w);enemy.moveTo(p.x+7,p.y,p.z,0,0);enemy.setNoAi(true);enemy.setInvulnerable(true);enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);enemy.setHealth(1000);enemy.getAttribute(Attributes.ARMOR).setBaseValue(0);w.addFreshEntity(enemy);
        n.hurt(w.damageSources().mobAttack(enemy),1);var team=CityDefenseData.get(w).teams.values().stream().filter(t->t.victim.equals(n.getUUID())).findFirst().orElseThrow();
        for(var id:team.officers)if(w.getEntity(id) instanceof GuardianEntity g){g.setNoAi(true);g.stopRiding();}
        return new Scene(w,p,n,enemy,team);
    }
    private static GuardianEntity guard(Scene s,int i){return (GuardianEntity)s.world.getEntity(s.team.officers.get(i));}
    private static void killed(Scene s,int i){var g=guard(s,i);HighPowerCombat.spendShield(g,HighPowerCombat.shield(g));g.invulnerableTime=0;g.hurt(s.world.damageSources().genericKill(),Float.MAX_VALUE);if(g.isAlive())throw new IllegalStateException("Native casualty did not die");}
    private static void weaken(Scene s,int i){var g=guard(s,i);HighPowerCombat.spendShield(g,HighPowerCombat.shield(g));g.invulnerableTime=0;g.hurt(s.world.damageSources().mobAttack(s.enemy),CityNpcPower.maxHealth(g).multiply(new java.math.BigDecimal("0.94")).floatValue());}
    private static void clean(Scene s){
        for(var id:s.team.officers){var e=s.world.getEntity(id);if(e!=null)e.discard();}for(var id:s.team.vehicles){var e=s.world.getEntity(id);if(e!=null)e.discard();}for(var id:s.team.operatives){var e=s.world.getEntity(id);if(e!=null)e.discard();}
        CityDefenseData.get(s.world).teams.remove(s.team.id);s.civilian.discard();s.enemy.discard();
    }
    @GameTest(template=TEMPLATE,batch="response01",timeoutTicks=100)
    public static void ten_regular_officers_wear_real_four_piece_netherite_grade_police_armor(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(2,()->{for(int i=0;i<10;i++){var g=guard(s,i);h.assertTrue(DefenseArmor.full(g,false),"all ten have four authentic police pieces");h.assertTrue(g.getArmorValue()==20&&g.getAttributeValue(Attributes.ARMOR_TOUGHNESS)==12,"source netherite-grade armor");h.assertTrue(CityNpcPower.maxHealth(g).compareTo(new java.math.BigDecimal("63240"))==0,"existing Lv3 HP unchanged");}
        h.assertTrue(!O5Reinforcements.failed(s.world,s.team)&&!O5Reinforcements.update(s.world,s.team),"healthy response does not escalate");clean(s);h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch="response02",timeoutTicks=100)
    public static void three_actual_deaths_call_exactly_five_once_even_after_entire_team_dies(GameTestHelper h){
        var s=scene(h);killed(s,0);killed(s,1);h.assertTrue(s.team.casualties.size()==2&&!O5Reinforcements.update(s.world,s.team),"two deaths below configured threshold");killed(s,2);
        h.assertTrue(O5Reinforcements.update(s.world,s.team),"three native deaths trigger reinforcement");h.assertTrue(s.team.operatives.size()==5&&new HashSet<>(s.team.operatives).size()==5,"five distinct operatives");
        for(var id:s.team.operatives){var g=(O5OperativeEntity)s.world.getEntity(id);h.assertTrue(g.descending()&&DefenseArmor.full(g,true)&&g.getY()>s.at.y+20,"full O5 suits spawn in actual sky descent");h.assertTrue(Math.abs(g.getBbHeight()-3.6)<.01,"source doubled body");}
        for(int i=3;i<10;i++)killed(s,i);for(int i=0;i<10;i++)h.assertTrue(!O5Reinforcements.update(s.world,s.team),"never repeats");h.assertTrue(s.team.casualties.size()==10&&s.team.operatives.size()==5,"all deaths retained, no replacement regulars");clean(s);h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response03",timeoutTicks=100)
    public static void all_survivors_low_health_trigger_but_one_healthy_officer_prevents_it(GameTestHelper h){
        var s=scene(h);for(int i=0;i<9;i++)weaken(s,i);h.assertTrue(!O5Reinforcements.failed(s.world,s.team),"one healthy officer is sufficient to prevent low-health trigger");weaken(s,9);
        h.assertTrue(O5Reinforcements.failed(s.world,s.team)&&O5Reinforcements.update(s.world,s.team),"all ten are truly below 30 percent exact health");clean(s);h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response04",timeoutTicks=100)
    public static void missing_or_unloaded_officers_are_neither_casualties_nor_low_health_evidence(GameTestHelper h){
        var s=scene(h);for(int i=0;i<9;i++)weaken(s,i);guard(s,9).discard();h.assertTrue(s.team.casualties.isEmpty()&&!O5Reinforcements.failed(s.world,s.team),"missing UUID is never a death or a low-health vote");clean(s);h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response05",timeoutTicks=100)
    public static void checkpoint_preserves_wave_ids_casualties_and_inflight_operatives(GameTestHelper h){
        var s=scene(h);for(int i=0;i<3;i++)killed(s,i);O5Reinforcements.update(s.world,s.team);var saved=CityDefenseData.get(s.world).save(new CompoundTag(),s.world.registryAccess());var copy=CityDefenseData.load(saved).team(s.team.id);
        h.assertTrue(copy.o5Called&&copy.casualties.equals(s.team.casualties)&&copy.operatives.equals(s.team.operatives)&&!O5Reinforcements.update(s.world,copy),"checkpoint cannot request a second wave");
        var g=(O5OperativeEntity)s.world.getEntity(copy.operatives.getFirst());var tag=new CompoundTag();g.saveWithoutId(tag);var restored=CityDefenseContent.O5.get().create(s.world);restored.load(tag);
        h.assertTrue(restored.descending()&&restored.isNoGravity()&&restored.response().equals(s.team.id)&&DefenseArmor.full(restored,true)&&restored.confirmedHits()==g.confirmedHits(),"inflight state, equipment and private count round-trip");clean(s);h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response06",timeoutTicks=100)
    public static void actual_protected_civilian_death_also_escalates_without_officer_deaths(GameTestHelper h){
        var s=scene(h);s.civilian.invulnerableTime=0;s.civilian.hurt(s.world.damageSources().mobAttack(s.enemy),1000);h.assertTrue(!s.civilian.isAlive()&&s.team.civilianLost,"real civilian death recorded");h.assertTrue(s.team.casualties.isEmpty()&&O5Reinforcements.update(s.world,s.team),"failure alone requests five");clean(s);h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response07",timeoutTicks=100)
    public static void roofed_scene_defers_entire_wave_and_retries_when_five_safe_columns_exist(GameTestHelper h){
        var s=scene(h);for(int x=1;x<95;x++)for(int z=1;z<95;z++)h.setBlock(new BlockPos(x,6,z),Blocks.STONE);for(int i=0;i<3;i++)killed(s,i);
        h.assertTrue(!O5Reinforcements.update(s.world,s.team)&&!s.team.o5Called&&s.team.operatives.isEmpty(),"no partial spawn inside buildings");
        for(int x=1;x<95;x++)for(int z=1;z<95;z++)h.setBlock(new BlockPos(x,6,z),Blocks.AIR);h.assertTrue(O5Reinforcements.update(s.world,s.team),"deferred complete wave retries");clean(s);h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response08",timeoutTicks=140)
    public static void native_descent_lands_five_and_keeps_every_ground_block_intact(GameTestHelper h){
        var s=scene(h);for(int i=0;i<3;i++)killed(s,i);h.assertTrue(O5Reinforcements.update(s.world,s.team),"spawned");
        h.runAfterDelay(85,()->{for(var id:s.team.operatives){var g=(O5OperativeEntity)s.world.getEntity(id);h.assertTrue(g!=null&&!g.descending()&&!g.isNoGravity()&&g.getY()<s.at.y+.2,"native descent completed");}
            for(int x=1;x<95;x++)for(int z=1;z<95;z++)h.assertTrue(h.getBlockState(new BlockPos(x,1,z)).is(Blocks.STONE),"landing never edits terrain");
            h.assertTrue(s.world.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new net.minecraft.world.phys.AABB(s.at,s.at).inflate(64)).isEmpty(),"debris is visual, no falling-block entities");clean(s);h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch="response09",timeoutTicks=100)
    public static void full_o5_is_invulnerable_but_removing_a_piece_revokes_protection(GameTestHelper h){
        var s=scene(h);var g=CityDefenseContent.O5.get().create(s.world);g.prepareDrop(s.team.id,0,s.team.expires,s.at.add(0,0,4),0);g.setNoAi(true);s.world.addFreshEntity(g);s.team.operatives.add(g.getUUID());
        float hp=g.getHealth();h.assertTrue(!g.hurt(s.world.damageSources().mobAttack(s.enemy),100000)&&!g.hurt(s.world.damageSources().fall(),100)&&g.getHealth()==hp,"full suit rejects damage");
        g.setItemSlot(EquipmentSlot.CHEST,net.minecraft.world.item.ItemStack.EMPTY);h.assertTrue(!g.armored()&&g.hurt(s.world.damageSources().mobAttack(s.enemy),2),"ordinary hurt resumes after unequip");clean(s);h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response10",timeoutTicks=100)
    public static void o5_strikes_only_approved_threat_and_uses_private_confirmed_hit_factorials(GameTestHelper h){
        var s=scene(h);var g=CityDefenseContent.O5.get().create(s.world);g.prepareDrop(s.team.id,0,s.team.expires,s.at.add(3,0,0),0);g.setNoAi(true);s.world.addFreshEntity(g);s.team.operatives.add(g.getUUID());
        h.assertTrue(!g.strike(s.civilian)&&!g.strike(guard(s,0)),"no civilian or teammate damage");s.enemy.setInvulnerable(false);float before=s.enemy.getHealth();
        h.assertTrue(g.strike(s.enemy)&&g.confirmedHits()==1&&s.enemy.getHealth()==before-1,"first confirmed native hit is 1 factorial");s.enemy.invulnerableTime=0;before=s.enemy.getHealth();h.assertTrue(g.strike(s.enemy)&&g.confirmedHits()==2&&s.enemy.getHealth()==before-2,"second is 2 factorial");
        s.enemy.setInvulnerable(true);h.assertTrue(!g.strike(s.enemy)&&g.confirmedHits()==2,"blocked contact never increments");clean(s);h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response11",timeoutTicks=100)
    public static void fiftieth_confirmed_contact_erases_a_nonplayer_threat(GameTestHelper h){
        var s=scene(h);var g=CityDefenseContent.O5.get().create(s.world);g.prepareDrop(s.team.id,0,s.team.expires,s.at.add(3,0,0),0);g.setNoAi(true);var tag=new CompoundTag();g.saveWithoutId(tag);tag.putLong("O5Hits",49);g.load(tag);s.world.addFreshEntity(g);s.team.operatives.add(g.getUUID());
        h.assertTrue(g.strike(s.enemy)&&s.enemy.isRemoved()&&g.confirmedHits()==50,"50th removes even invulnerable nonplayer threat through the authored mechanic");clean(s);h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response12",timeoutTicks=100)
    public static void all_twelve_v9_types_keep_their_profiles_and_original_dimensions(GameTestHelper h){
        h.assertTrue(TitanBlocks.TYPES.size()==12,"twelve imported types");for(var entry:TitanBlocks.TYPES.entrySet()){var e=entry.getValue().get().create(h.getLevel());h.assertTrue(e!=null&&e.species().equals(entry.getKey()),"correct registration identity");h.assertTrue(e.getMaxHealth()==e.profile().health()&&e.getBbHeight()>=24,"unchanged V9 health and giant height");h.assertTrue(TitanBlocks.IDS.length*TitanSounds.EVENTS.length==144,"all original sound events");}h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response13",timeoutTicks=100)
    public static void natural_zones_reject_other_dimensions_populated_core_and_ordinary_parks(GameTestHelper h){
        h.assertTrue(!CityTitanZones.dimension(Level.OVERWORLD)&&!CityTitanZones.dimension(Level.NETHER)&&!CityTitanZones.dimension(Level.END)&&CityTitanZones.dimension(cn.academy.port.city.portal.CityPortalTravel.CITY),"only academy city dimension");
        for(int x=-1600;x<=1600;x+=64)for(int z=-1600;z<=1600;z+=64)if(cn.academy.port.city.CityPlan.district(x,z)!=21)h.assertTrue(!CityTitanZones.quietPlan(x,z,30),"populated core cannot be a natural zone");
        boolean found=false;for(int x=-1950;x<-1250;x+=16)for(int z=-1150;z<-450;z+=16)found|=CityTitanZones.quietPlan(x,z,30);h.assertTrue(found,"mountain wilderness contains valid zones");h.assertTrue(!CityTitanZones.rural(Integer.MIN_VALUE,0)&&!CityTitanZones.rural(1000000,0),"out-of-plan positions rejected");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response14",timeoutTicks=100)
    public static void natural_director_never_spawns_any_species_in_overworld(GameTestHelper h){
        for(var id:TitanBlocks.IDS)h.assertTrue(CityTitanSpawns.trySpawn(h.getLevel(),id,h.absolutePos(new BlockPos(2,2,2)))==null,"world seed or biome cannot bypass dimension restriction");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response15",timeoutTicks=100)
    public static void global_natural_spawn_event_gate_preserves_explicit_spawn_egg_testing(GameTestHelper h){
        var e=TitanBlocks.TYPES.get("ironback").get().create(h.getLevel());e.moveTo(h.absoluteVec(new Vec3(3,3,3)));
        var natural=new net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent(e,h.getLevel(),e.getX(),e.getY(),e.getZ(),h.getLevel().getCurrentDifficultyAt(e.blockPosition()),MobSpawnType.NATURAL,null,null);CityTitanSpawns.gate(natural);h.assertTrue(natural.isSpawnCancelled(),"external natural spawn is blocked outside city");
        h.assertTrue(!CityTitanSpawns.automatic(MobSpawnType.SPAWN_EGG)&&!CityTitanSpawns.automatic(MobSpawnType.COMMAND),"explicit QA/admin spawns remain explicit");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response16",timeoutTicks=100)
    public static void v9_environment_interaction_cannot_grief_noncity_world_or_city_building_palette(GameTestHelper h){
        var s=scene(h);var e=TitanBlocks.TYPES.get("lava_rex").get().create(s.world);e.moveTo(s.at);h.assertTrue(TitanEnvironment.interact(s.world,e,s.at,30)==0,"no world changes outside city wilderness");h.assertTrue(!CityTitanZones.terrainAllowed(s.world,BlockPos.containing(s.at).below()),"explicit palette-and-dimension guard");clean(s);h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response17",timeoutTicks=520)
    public static void autonomous_joint_response_eliminates_lv5_threat_without_rifles_stalling_melee(GameTestHelper h){
        var s=scene(h);h.assertTrue(HighPowerCombat.enroll(s.enemy,5),"actual Lv5 target enrolled");s.enemy.setInvulnerable(false);
        for(int i=0;i<3;i++)killed(s,i);h.assertTrue(O5Reinforcements.update(s.world,s.team),"actual loss escalation");
        for(int i=3;i<10;i++)guard(s,i).setNoAi(false);
        h.runAfterDelay(450,()->{long contacts=0;for(var id:s.team.operatives)if(s.world.getEntity(id) instanceof O5OperativeEntity g)contacts+=g.confirmedHits();
            h.assertTrue(!s.enemy.isAlive()&&contacts>=5,"autonomous cooperative response defeats 20-million-HP threat using native contacts");clean(s);h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch="response18",timeoutTicks=100)
    public static void pinned_visual_library_preserves_strict_contract_and_unknown_versions_still_refuse(GameTestHelper h){
        var base=new HashMap<>(Map.of("minecraft","1.21.1","neoforge","21.1.252","academy","0.1.0-m41-highpower.0.81.5-city-a3.63-mech.1"));var packs=Set.of("vanilla","mod/neoforge","mod/academy");
        h.assertTrue(VanillaCombatContract.runtimeReason(base,packs).isEmpty()&&VanillaMiningContract.runtimeReason(base,packs).isEmpty(),"original base remains approved");
        base.put("geckolib","4.7");var visual=Set.of("vanilla","mod/neoforge","mod/academy","mod/geckolib");h.assertTrue(VanillaCombatContract.runtimeReason(base,visual).isEmpty()&&VanillaMiningContract.runtimeReason(base,visual).isEmpty(),"bundled exact visual runtime preserves both strict contracts");
        base.put("acamech","0.4.3");var withMech=Set.of("vanilla","mod/neoforge","mod/academy","mod/geckolib","mod/acamech");
        h.assertTrue(VanillaCombatContract.runtimeReason(base,withMech).isEmpty()&&VanillaMiningContract.runtimeReason(base,withMech).isEmpty(),"reviewed mech preserves both strict contracts");
        base.put("acamech","0.4.4");h.assertTrue(VanillaCombatContract.runtimeReason(base,withMech).isPresent()&&VanillaMiningContract.runtimeReason(base,withMech).isPresent(),"unknown mech version refused");
        base.remove("acamech");h.assertTrue(VanillaCombatContract.runtimeReason(base,withMech).isPresent()&&VanillaMiningContract.runtimeReason(base,withMech).isPresent(),"orphan mech pack refused");
        base.put("geckolib","4.8");h.assertTrue(VanillaCombatContract.runtimeReason(base,visual).isPresent()&&VanillaMiningContract.runtimeReason(base,visual).isPresent(),"unreviewed version refused");base.put("geckolib","4.7");base.put("foreign_hook","1");h.assertTrue(VanillaCombatContract.runtimeReason(base,visual).isPresent(),"unknown hooks still refused");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="response19",timeoutTicks=100,skyAccess=true)
    public static void water_placement_fits_a_sloping_bed_without_editing_water_or_terrain(GameTestHelper h){
        for(int x=32;x<=62;x++)for(int z=32;z<=62;z++){
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<=8;y++)h.setBlock(new BlockPos(x,y,z),Blocks.WATER);
            if(x>=54)for(int y=2;y<=4;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        }
        var at=h.absolutePos(new BlockPos(47,2,47));var p=CityTitanSpawns.waterPosition(h.getLevel(),at,22);
        com.mojang.logging.LogUtils.getLogger().info("QA_WATER_PLACEMENT at={} actual={} expected={} surface={}",at,p,h.absolutePos(new BlockPos(47,5,47)),h.getLevel().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at));
        h.assertTrue(p!=null&&Math.abs(p.y-h.absolutePos(new BlockPos(47,5,47)).getY()-.05)<.001,"feet fit the highest bed across the whole giant footprint");
        var e=TitanBlocks.TYPES.get("tidal_sail").get().create(h.getLevel());h.assertTrue(CityTitanSpawns.collisionFree(h.getLevel(),e,p),"whole thirty-block body clears the slope");
        h.assertTrue(h.getBlockState(new BlockPos(54,4,47)).is(Blocks.STONE)&&h.getBlockState(new BlockPos(47,2,47)).is(Blocks.WATER),"placement does not modify the basin");
        h.setBlock(new BlockPos(47,10,47),Blocks.STONE);h.assertTrue(CityTitanSpawns.waterPosition(h.getLevel(),at,22)==null,"dry structures are rejected");h.succeed();
    }
}
