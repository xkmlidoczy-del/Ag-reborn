package cn.academy.port.qa;

import cn.academy.port.city.*;
import cn.academy.port.city.defense.*;
import cn.academy.port.fleet.*;
import cn.academy.port.highpower.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;

/** Isolated packaged-client acceptance. The dispatch begins with the real damage event. */
final class CityDefenseQa {
    private static UUID response,civilian,suspect;
    private static final Map<UUID,Vec3> carStarts=new HashMap<>();
    private static final Set<String> actions=new HashSet<>();
    private static long start;
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException("QA_DEFENSE "+reason);}
    private static <T>T server(Minecraft mc,java.util.function.Supplier<T> body){return mc.getSingleplayerServer().submit(body).join();}
    static void step(Minecraft mc,String arg){
        server(mc,()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var world=player.serverLevel();
            switch(arg){
                case "init"->{
                    require(Boolean.getBoolean("academy.qa.confirmOwnWorld"),"isolated world required");player.setGameMode(GameType.SPECTATOR);CityDefense.allowTestWorld(world);
                    var n=CityNpcContent.NPC.get().create(world);n.moveTo(-65.5,0,-25.5,0,0);
                    try{var m=CityNpcEntity.class.getDeclaredMethod("prepareAmbient",CityNpcEntity.Look.class,CityNpcEntity.Activity.class);m.setAccessible(true);m.invoke(n,CityNpcEntity.Look.STUDENT_BOOKS,CityNpcEntity.Activity.READ);}catch(Exception e){throw new IllegalStateException(e);}
                    n.setCustomName(net.minecraft.network.chat.Component.literal("受保护的学生"));n.setCustomNameVisible(true);world.addFreshEntity(n);n.combat();n.setHealth(10);civilian=n.getUUID();
                    var e=EntityType.HUSK.create(world);e.moveTo(-65.5,0,-17.5,180,0);e.setNoAi(true);e.setPersistenceRequired();world.addFreshEntity(e);HighPowerCombat.enroll(e,3);suspect=e.getUUID();
                    require(n.hurt(world.damageSources().mobAttack(e),2),"real civilian injury");
                    var team=CityDefenseData.get(world).teams.values().stream().filter(t->t.victim.equals(civilian)).findFirst().orElseThrow();response=team.id;
                    require(team.officers.size()==10,"exact ten officers");require(team.vehicles.size()==3,"three response vehicles");
                    for(var id:team.vehicles)carStarts.put(id,world.getEntity(id).position());start=world.getGameTime();
                    cn.academy.port.fleet.InteractionLab.LOGGER.info("QA_DEFENSE_BEGIN team={} officers={} vehicles={}",response,team.officers.size(),team.vehicles.size());
                }
                case "sample"->{var t=CityDefenseData.get(world).team(response);for(var id:t.officers)if(world.getEntity(id) instanceof GuardianEntity g)actions.add(g.action().name());}
                case "check"->{
                    var team=CityDefenseData.get(world).team(response);int shots=0,foot=0,reload=0;var roles=new ArrayList<Map<String,Object>>();
                    for(var id:team.officers)if(world.getEntity(id) instanceof GuardianEntity g){shots+=g.shots();if(!g.isPassenger())foot++;if(g.reloading())reload++;actions.add(g.action().name());
                        roles.add(Map.of("role",GuardianEntity.roleName(g.role()),"action",g.action().name(),"shots",g.shots(),"energy",g.energy(),"medical",g.medicalCharges(),"position",g.position().toString()));}
                    var moves=new ArrayList<Double>();for(var id:team.vehicles)if(world.getEntity(id) instanceof LabVehicle v)moves.add(v.position().distanceTo(carStarts.get(id)));
                    var victim=(CityNpcEntity)world.getEntity(civilian);var target=(LivingEntity)world.getEntity(suspect);
                    require(foot==10&&shots>0,"dismount and real gunfire");require(moves.stream().allMatch(d->d>1),"each vehicle actually drove");require(victim.isAlive()&&victim.getHealth()>8,"civilian healed and protected");
                    var receipt=new LinkedHashMap<String,Object>();receipt.put("team",response);receipt.put("officers",roles);receipt.put("vehicle_distance",moves);receipt.put("shots",shots);receipt.put("actions",actions);receipt.put("civilian_health",victim.getHealth());receipt.put("suspect_health",CityNpcPower.health(target).toPlainString());receipt.put("ticks",world.getGameTime()-start);
                    try{java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("defense-acceptance.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(receipt));}catch(Exception e){throw new IllegalStateException(e);}
                    cn.academy.port.fleet.InteractionLab.LOGGER.info("QA_DEFENSE_PASS {}",receipt);
                }
                case "guncheck"->{
                    var stack=player.getMainHandItem();var gun=(GunItem)stack.getItem();int actual=GunItem.ammo(stack)+CoilReloads.reserve(player,gun.variant),initial=player.getPersistentData().getInt("fleet_expected");
                    require(actual<initial&&GunItem.ammo(stack)==gun.capacity()&&!CoilReloads.active(player),"firing debits energy and J completes real module transfer");
                    cn.academy.port.fleet.InteractionLab.LOGGER.info("QA_GUN_INPUT_PASS variant={} spent={} loaded={}",gun.variant,initial-actual,GunItem.ammo(stack));
                }
                case "pointer"->{
                    var s=cn.academy.port.AbilityStorage.get(player);s.presets.edit(0,0,"mental_neural_sting",s::learned);s.presets.edit(0,2,"",s::learned);cn.academy.port.AbilityStorage.save(player);cn.academy.port.AcademyNetwork.sync(player);
                }
                case "gun"->{player.setGameMode(GameType.CREATIVE);player.stopRiding();player.getInventory().clearContent();player.teleportTo(-65.5,0,-40.5);player.setYRot(0);player.setXRot(0);var gun=new net.minecraft.world.item.ItemStack(InteractionLab.GUNS[2].get());GunItem.ammo(gun,8);player.getInventory().setItem(0,gun);player.getInventory().setItem(1,CoilMagazineItem.create(2,24));}
                case "end"->{var t=CityDefenseData.get(world).team(response);t.closedAt=world.getGameTime();CityDefenseData.get(world).setDirty();var e=world.getEntity(suspect);if(e!=null)e.discard();var n=world.getEntity(civilian);if(n!=null)n.discard();}
                default->throw new IllegalArgumentException(arg);
            }return null;
        });
    }
}
