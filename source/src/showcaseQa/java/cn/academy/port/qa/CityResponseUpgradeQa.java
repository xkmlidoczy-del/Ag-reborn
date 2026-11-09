package cn.academy.port.qa;

import cn.academy.port.city.*;
import cn.academy.port.city.defense.*;
import cn.academy.port.city.defense.armor.*;
import cn.academy.port.city.portal.CityPortalTravel;
import cn.academy.port.fleet.*;
import cn.academy.port.highpower.*;
import cn.academy.port.titan.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Owned city fixture; damage, death events, reinforcement, falling physics and melee remain production paths. */
final class CityResponseUpgradeQa {
    private static UUID response,victim,suspect,ambient,car,titan;
    private static long floorHash;private static BlockPos ruralLand,ruralWater;private static int naturalFrequency=12;
    private static final Vec3 SCENE=new Vec3(-65.5,0,4.5);
    private static final Map<String,Object> RECEIPT=new LinkedHashMap<>();
    private static final List<Map<String,Object>> CREATURES=new ArrayList<>();
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException("QA_RESPONSE "+reason);}
    private static void receipt(Minecraft mc){try{var root=Path.of(System.getProperty("academy.qa.response.root"));Files.createDirectories(root);Files.writeString(root.resolve("response-receipt.json"),new GsonBuilder().setPrettyPrinting().create().toJson(RECEIPT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static CityNpcEntity person(ServerLevel world,Vec3 at,CityNpcEntity.Look look){
        var n=CityNpcContent.NPC.get().create(world);n.moveTo(at);n.setNoAi(true);
        try{var m=CityNpcEntity.class.getDeclaredMethod("prepareAmbient",CityNpcEntity.Look.class,CityNpcEntity.Activity.class);m.setAccessible(true);m.invoke(n,look,CityNpcEntity.Activity.STAND);}catch(Exception e){throw new IllegalStateException(e);}
        world.addFreshEntity(n);n.combat();return n;
    }
    private static long floor(ServerLevel w){long h=1;for(int x=-110;x<=-20;x++)for(int z=-40;z<=50;z++)h=h*31+net.minecraft.world.level.block.Block.getId(w.getBlockState(new BlockPos(x,-1,z)));return h;}
    static void step(Minecraft mc,String op){
        require(Boolean.getBoolean("academy.qa.confirmOwnWorld"),"owned QA save required");
        mc.getSingleplayerServer().submit(()->{
            ServerPlayer p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var city=p.server.getLevel(CityPortalTravel.CITY);require(city!=null,"real city dimension exists");
            if(op.equals("init")){
                p.setGameMode(GameType.SPECTATOR);city.setDayTime(6000);city.setWeatherParameters(12000,0,false,false);city.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);
                for(int x=-110;x<=-20;x++)for(int z=-40;z<=50;z++)city.setBlock(new BlockPos(x,-1,z),CityBlocks.block("asphalt").defaultBlockState(),2);
                p.teleportTo(city,-76,3,-10,-40,10);floorHash=floor(city);
                var student=person(city,SCENE.add(-4,0,0),CityNpcEntity.Look.STUDENT_BOOKS);victim=student.getUUID();
                var e=EntityType.HUSK.create(city);e.moveTo(SCENE.add(0,0,8));e.setNoAi(true);e.setPersistenceRequired();city.addFreshEntity(e);require(HighPowerCombat.enroll(e,5),"native high-power target enrolled");e.setInvulnerable(true);suspect=e.getUUID();
                require(student.hurt(city.damageSources().mobAttack(e),2),"real civilian damage dispatched police");
                var team=CityDefenseData.get(city).teams.values().stream().filter(t->t.victim.equals(victim)).findFirst().orElseThrow();response=team.id;
                for(int i=0;i<10;i++){var g=(GuardianEntity)city.getEntity(team.officers.get(i));g.stopRiding();g.setNoAi(true);g.moveTo(-76+(i%5)*2.4,0,-1+(i/5)*3,0,0);require(DefenseArmor.full(g,false),"all ten wearing source police armor");}
                var officer=person(city,SCENE.add(-10,0,-4),CityNpcEntity.Look.GUARD);ambient=officer.getUUID();
                RECEIPT.put("dimension",city.dimension().location().toString());RECEIPT.put("initial_officers",team.officers.size());RECEIPT.put("fixture","owned city floor repaved for camera; civilian injury, casualties and O5 response use native server gameplay");
                InteractionLab.LOGGER.info("QA_RESPONSE_BEGIN incident={}",response);
            }else if(op.equals("fail")){
                var team=CityDefenseData.get(city).team(response);
                for(int i=0;i<3;i++){var g=(GuardianEntity)city.getEntity(team.officers.get(i));HighPowerCombat.spendShield(g,HighPowerCombat.shield(g));g.invulnerableTime=0;require(g.hurt(city.damageSources().genericKill(),Float.MAX_VALUE)&&!g.isAlive(),"real native officer casualty");}
                require(team.casualties.size()==3,"three confirmed death events");for(var id:team.officers)if(city.getEntity(id) instanceof GuardianEntity g&&g.isAlive())g.setNoAi(false);
                InteractionLab.LOGGER.info("QA_RESPONSE_NATIVE_CASUALTIES {}",team.casualties);
            }else if(op.equals("land")){
                var team=CityDefenseData.get(city).team(response);require(team.o5Called&&team.operatives.size()==5,"automatic director dispatched five");
                for(var id:team.operatives){var g=(O5OperativeEntity)city.getEntity(id);require(g!=null&&g.armored()&&!g.descending()&&g.getBbHeight()>3.5,"all five actually landed in source O5 suits");}
                require(floor(city)==floorHash,"landing only visual, floor unchanged");require(DefenseArmor.full((LivingEntity)city.getEntity(ambient),false),"ambient police received real armor too");
                RECEIPT.put("casualties",3);RECEIPT.put("reinforcements",5);RECEIPT.put("native_landing",true);RECEIPT.put("ground_unchanged",true);receipt(mc);InteractionLab.LOGGER.info("QA_RESPONSE_LANDING_PASS five=true blocks_unchanged=true");
            }else if(op.equals("release")){((LivingEntity)city.getEntity(suspect)).setInvulnerable(false);
            }else if(op.equals("check")){
                var team=CityDefenseData.get(city).team(response);var list=new ArrayList<Map<String,Object>>();long hits=0;
                for(var id:team.operatives){var g=(O5OperativeEntity)city.getEntity(id);require(g!=null&&g.isAlive()&&g.armored(),"all five survive");hits+=g.confirmedHits();list.add(Map.of("uuid",id.toString(),"position",g.position().toString(),"hits",g.confirmedHits(),"exact_max_health",CityNpcPower.maxHealth(g).toPlainString()));}
                var enemy=city.getEntity(suspect);InteractionLab.LOGGER.info("QA_RESPONSE_COMBAT_STATE operatives={} enemy={}",list,enemy instanceof LivingEntity living?CityNpcPower.health(living):"removed");require(enemy==null||!enemy.isAlive(),"native autonomous O5 attacks eliminated enrolled threat");require(hits>=5,"real confirmed melee contacts");require(floor(city)==floorHash,"combat does not edit floor");
                RECEIPT.put("operatives",list);RECEIPT.put("confirmed_melee_hits",hits);RECEIPT.put("enemy_eliminated",true);receipt(mc);InteractionLab.LOGGER.info("QA_RESPONSE_PASS {}",RECEIPT);
            }else if(op.startsWith("camera=")){
                String[] values=op.substring(7).split(",");double[] v=new double[6];for(int i=0;i<6;i++)v[i]=Double.parseDouble(values[i]);Vec3 direction=new Vec3(v[3]-v[0],v[4]-v[1],v[5]-v[2]);
                float yaw=(float)-Math.toDegrees(Math.atan2(direction.x,direction.z)),pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));
                p.teleportTo(city,v[0],v[1]-p.getEyeHeight(),v[2],yaw,pitch);
            }else if(op.equals("cleanup")){
                var team=CityDefenseData.get(city).team(response);if(team!=null){team.closedAt=city.getGameTime();CityDefenseData.get(city).setDirty();}
                for(var id:new UUID[]{suspect,victim,ambient})if(id!=null&&city.getEntity(id)!=null)city.getEntity(id).discard();
            }else if(op.startsWith("car=")){
                if(car!=null&&city.getEntity(car)!=null)city.getEntity(car).discard();int v=Integer.parseInt(op.substring(4));var vehicle=InteractionLab.CAR.get().create(city);vehicle.variant(v);vehicle.moveTo(SCENE.x,SCENE.y,SCENE.z,0,0);require(city.addFreshEntity(vehicle),"vehicle added");car=vehicle.getUUID();
            }else if(op.equals("door")){((LabVehicle)city.getEntity(car)).toggleDoor();
            }else if(op.equals("rural")){
                p.setGameMode(GameType.SPECTATOR);city.setDayTime(6000);city.setWeatherParameters(12000,0,false,false);
                if(car!=null&&city.getEntity(car)!=null)city.getEntity(car).discard();
                city.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,p.server);
                for(int x=-1850;x<-1350&&(ruralLand==null||ruralWater==null);x+=12)for(int z=-1050;z<-550;z+=12){
                    if(!CityTitanZones.quietPlan(x,z,30))continue;var sample=CityPlan.sample(CityTitanZones.PLAN_SEED,x,z);
                    if(ruralLand==null&&sample.kind()==CityPlan.Kind.TERRAIN&&sample.height()>40&&sample.height()<100)ruralLand=new BlockPos(x,0,z);
                    if(ruralWater==null&&sample.kind()==CityPlan.Kind.WATER&&sample.height()<-12)ruralWater=new BlockPos(x,0,z);
                }
                require(ruralLand!=null&&ruralWater!=null,"real wilderness land and deep lake found");
                p.teleportTo(city,ruralLand.getX()-40,30,ruralLand.getZ()+40,-135,10);
                for(var at:List.of(ruralLand,ruralWater))for(int cx=-4;cx<=4;cx++)for(int cz=-4;cz<=4;cz++)city.getChunk((at.getX()>>4)+cx,(at.getZ()>>4)+cz);
                RECEIPT.put("land_zone",ruralLand.toShortString());RECEIPT.put("water_zone",ruralWater.toShortString());
                for(var id:TitanBlocks.IDS)require(CityTitanSpawns.trySpawn(city,id,BlockPos.ZERO)==null,"D7 core denied");
                RECEIPT.put("all_twelve_rejected_in_core",true);InteractionLab.LOGGER.info("QA_TITAN_RURAL_READY land={} water={}",ruralLand,ruralWater);
            }else if(op.startsWith("titan=")){
                if(titan!=null&&city.getEntity(titan)!=null)city.getEntity(titan).discard();String id=op.substring(6);var at=TitanProfiles.get(id).mobility()==TitanProfiles.Mobility.SWIM?ruralWater:ruralLand;TitanEntity e=null;
                p.teleportTo(city,at.getX()-40,35,at.getZ()+40,-135,10);
                for(int cx=-4;cx<=4;cx++)for(int cz=-4;cz<=4;cz++)city.getChunk((at.getX()>>4)+cx,(at.getZ()>>4)+cz);
                for(int dx:new int[]{0,12,-12,24,-24,36,-36}){for(int dz:new int[]{0,12,-12,24,-24,36,-36}){e=CityTitanSpawns.trySpawn(city,id,at.offset(dx,0,dz));if(e!=null)break;}if(e!=null)break;}
                if(e==null)InteractionLab.LOGGER.info("QA_TITAN_SITE_DEBUG id={} loaded={} allowed={} top={} material={}",id,city.hasChunkAt(at),CityTitanZones.allowed(city,at,22),city.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at),city.getBlockState(city.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at).below()));
                require(e!=null,"production natural-spawn function accepted rural "+id);titan=e.getUUID();e.setNoAi(true);e.setNoGravity(true);
                double x=e.getX()-66,y=e.getY()+e.getBbHeight()*.80,z=e.getZ()+66;Vec3 look=e.position().add(0,e.getBbHeight()*.53,0).subtract(x,y,z);float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.atan2(look.y,Math.hypot(look.x,look.z)));p.teleportTo(city,x,y,z,yaw,pitch);
                CREATURES.add(Map.of("id",id,"position",e.position().toString(),"district",CityPlan.district(e.getBlockX(),e.getBlockZ()),"mobility",e.profile().mobility().name(),"health",e.getMaxHealth()));RECEIPT.put("creatures",CREATURES);receipt(mc);InteractionLab.LOGGER.info("QA_TITAN_NATURAL_PASS id={} at={}",id,e.position());
            }else if(op.equals("roar")){
                var e=(TitanEntity)city.getEntity(titan);TitanSounds.play(e,"roar",e.position().add(0,e.getBbHeight()*.5,0),7f);e.triggerAnim("action","roar");
            }else if(op.equals("auto-start")){
                var all=new ArrayList<Entity>();for(var e:city.getAllEntities())if(e instanceof TitanEntity)all.add(e);all.forEach(Entity::discard);
                naturalFrequency=TitanConfig.NATURAL_ONE_IN.get();TitanConfig.NATURAL_ONE_IN.set(1);p.setGameMode(GameType.CREATIVE);
                int y=city.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,ruralLand.getX(),ruralLand.getZ());p.teleportTo(city,ruralLand.getX(),y+2,ruralLand.getZ(),-45,10);
                RECEIPT.put("scheduled_spawn_fixture","non-spectator creative observer, naturalSpawnOneIn temporarily 1; no call to trySpawn during this interval");RECEIPT.put("scheduled_start_tick",city.getGameTime());
            }else if(op.equals("auto-check")){
                TitanEntity found=null;for(var e:city.getAllEntities())if(e instanceof TitanEntity t&&t.isAlive()){found=t;break;}
                require(found!=null,"real LevelTickEvent scheduler produced a fresh natural giant");TitanConfig.NATURAL_ONE_IN.set(naturalFrequency);p.setGameMode(GameType.SPECTATOR);titan=found.getUUID();
                double x=found.getX()-66,y=found.getY()+found.getBbHeight()*.8,z=found.getZ()+66;Vec3 d=found.position().add(0,found.getBbHeight()*.53,0).subtract(x,y,z);
                p.teleportTo(city,x,y,z,(float)-Math.toDegrees(Math.atan2(d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
                RECEIPT.put("scheduled_natural_spawn",Map.of("id",found.species(),"position",found.position().toString(),"tick",city.getGameTime(),"restored_frequency",naturalFrequency));receipt(mc);InteractionLab.LOGGER.info("QA_SCHEDULED_NATURAL_PASS species={} at={}",found.species(),found.position());
            }else if(op.equals("titans-check")){
                require(CREATURES.size()==12&&CREATURES.stream().map(x->x.get("id")).distinct().count()==12,"all twelve naturally permitted species rendered");
                RECEIPT.put("all_twelve_rural_natural_spawns",true);receipt(mc);InteractionLab.LOGGER.info("QA_TITANS_ALL_PASS {}",CREATURES);
            }else throw new IllegalArgumentException(op);
        }).join();
    }
}
