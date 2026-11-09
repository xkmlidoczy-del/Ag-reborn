package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.canon.CanonPaperContent;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.story.*;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.phys.*;
import static cn.academy.port.story.UrbanStoryProgress.*;

/** Isolated normal-world end-to-end driver. No quest progress is injected or repaired.
 * Admin setup supplies travel, loadout and learning; completion uses ordinary client packets,
 * actual paper skill input, real native combat, real NPC pathfinding and production persistence. */
final class UrbanStoryFormalQa {
    private record Site(String id,BoundingBox box,Map<String,BlockPos> blocks) {}
    private record Action(String kind,BlockPos block,int entity,Vec3 aim,Objective objective) {}
    private static final Map<String,Site> SITES=new HashMap<>();
    private static CompletableFuture<?> pending;
    private static Approach route;
    private static Action action,deferred;private static int settleTicks;
    private static UUID owner;
    private static long deadline;
    private static int pause,attempts,escortStep,releaseKey=-1;
    private static Objective last;
    private static int count;
    private static final List<Vec3> ESCORT=new ArrayList<>();
    private UrbanStoryFormalQa() {}
    static boolean step(Minecraft mc,String op) {
        if(!Boolean.getBoolean("academy.qa.showcase")||mc.getSingleplayerServer()==null||mc.player==null)
            throw new IllegalStateException("urban formal requires an isolated integrated QA world");
        Path directory=mc.gameDirectory.toPath().toAbsolutePath();
        if(!directory.toString().contains("urban-formal"))throw new IllegalStateException("urban-formal directory marker required");
        if(op.startsWith("restart-check:")){
            route=Approach.valueOf(op.substring(14).toUpperCase(Locale.ROOT));
            if(pending==null){var id=mc.player.getUUID();pending=mc.getSingleplayerServer().submit(()->{
                var progress=UrbanStoryStorage.read(mc.getSingleplayerServer().getPlayerList().getPlayer(id)).orElseThrow();
                if(progress.chapter()!=Chapter.COMPLETE||progress.snapshot().size()!=14||progress.snapshot().values().stream().anyMatch(a->a!=route))
                    throw new IllegalStateException("restart progress mismatch: "+progress.snapshot());
                return true;});return false;}
            if(!pending.isDone())return false;pending.join();pending=null;count=14;receipt(mc,"restart-verified",null);return true;
        }
        if(op.startsWith("setup:")) {
            if(pending==null){route=Approach.valueOf(op.substring(6).toUpperCase(Locale.ROOT));owner=mc.player.getUUID();
                deadline=System.currentTimeMillis()+900_000;pending=mc.getSingleplayerServer().submit(()->setup(mc));return false;}
            if(!pending.isDone())return false;pending.join();pending=null;receipt(mc,"setup",null);return true;
        }
        if(op.equals("run"))return run(mc);
        if(op.equals("check")){if(count!=14)throw new IllegalStateException("incomplete story: "+count);return true;}
        throw new IllegalArgumentException(op);
    }
    private static void setup(Minecraft mc) {
        var server=mc.getSingleplayerServer();var world=server.overworld();var p=server.getPlayerList().getPlayer(owner);
        if(world.getSeed()!=251027||!world.structureManager().shouldGenerateStructures())throw new IllegalStateException("seed251027 normal generated world required");
        var saved=UrbanStoryStorage.read(p).orElseThrow();count=saved.snapshot().size();
        if(count>0&&!Boolean.getBoolean("academy.qa.urban.resume"))throw new IllegalStateException("fresh player required");
        if(saved.snapshot().values().stream().anyMatch(method->method!=route))throw new IllegalStateException("saved route mismatch");
        server.setDifficulty(Difficulty.NORMAL,true);world.setDayTime(6000);
        world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
        String[] ids={"urban_campus","urban_station","urban_facility"};
        for(int i=0;i<ids.length;i++) {
            var type=world.registryAccess().registryOrThrow(Registries.STRUCTURE).get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy",ids[i]));
            var set=world.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy",ids[i]));
            var placement=(net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement)set.placement();
            var generation=world.getChunkSource().getGeneratorState();StructureStart start=null;
            for(int region=0;region<81;region++){
                int rx=region%9-4,rz=region/9-4;var cp=placement.getPotentialStructureChunk(world.getSeed(),rx*placement.spacing(),rz*placement.spacing());
                if(!placement.isStructureChunk(generation,cp.x,cp.z))continue;
                var candidate=world.getChunk(cp.x,cp.z).getStartForStructure(type);
                if(candidate!=null&&candidate.isValid()){start=candidate;break;}
            }
            if(start==null)throw new IllegalStateException("natural seeded structure missing "+ids[i]);
            var box=start.getBoundingBox();
            for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++){
                world.getChunk(x,z);world.setChunkForced(x,z,true);
            }
            Map<String,BlockPos> blocks=new HashMap<>();
            for(BlockPos pos:BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())){
                var state=world.getBlockState(pos);String key=state.is(Blocks.LECTERN)?"console":state.is(Blocks.CARTOGRAPHY_TABLE)?"table":
                        state.is(Blocks.GOLD_BLOCK)?"gold":state.is(Blocks.AMETHYST_BLOCK)?"podium":
                        state.is(Blocks.CYAN_TERRACOTTA)?"traces":state.is(Blocks.BOOKSHELF)?"records":"";
                if(state.is(Blocks.LEVER)){
                    var floor=world.getBlockState(pos.below());key=floor.is(Blocks.MAGENTA_TERRACOTTA)?"master":floor.is(Blocks.RED_TERRACOTTA)?"red":
                        floor.is(Blocks.YELLOW_TERRACOTTA)?"yellow":floor.is(Blocks.LIME_TERRACOTTA)?"green":"";
                }
                if(!key.isEmpty())blocks.put(key,pos.immutable());
            }
            if(!blocks.keySet().containsAll(List.of("console","table","gold")))throw new IllegalStateException("generated structure markers incomplete "+ids[i]+blocks);
            if(ids[i].equals("urban_station")&&!blocks.keySet().containsAll(List.of("records","traces")))
                throw new IllegalStateException("natural station lost investigation markers: "+blocks);
            SITES.put(ids[i],new Site(ids[i],box,blocks));
            com.mojang.logging.LogUtils.getLogger().info("QA_URBAN_SITE {} bounds={} markers={}",ids[i],box,blocks);
        }
        p.setGameMode(GameType.SURVIVAL);
        var ability=AbilityStorage.get(p);ability.changeCategoryClassic("paper_psychokinesis");ability.setLevel(5);
        for(var skill:cn.academy.port.canon.CanonSkills.ALL)if(skill.category().equals(ability.category))ability.learn(skill.id());
        ability.setActivateState(true);ability.presets.edit(0,2,"paper_walkway",ability::learned);
        p.getPersistentData().remove(HighPowerCombat.DATA_KEY);
        if(!HighPowerCombat.enroll(p,5))throw new IllegalStateException("QA LV5 enrollment refused");
        HighPowerResources.initializeBudget(p);AbilityStorage.save(p);AcademyNetwork.sync(p);
        p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION,20000,2));
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,20000,3));
    }
    private static boolean run(Minecraft mc) {
        if(releaseKey>=0){System.clearProperty("academy.qa.key."+releaseKey);mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),releaseKey,0,0,0);releaseKey=-1;}
        if(System.currentTimeMillis()>deadline)throw new IllegalStateException("story timeout "+route+" "+last+" attempts="+attempts);
        if(pause-->0)return false;
        if(deferred!=null){if(settleTicks-->0)return false;Action settled=deferred;deferred=null;return perform(mc,settled);}
        if(pending==null){pending=mc.getSingleplayerServer().submit(()->plan(mc));return false;}
        if(!pending.isDone())return false;
        Object result=pending.join();pending=null;
        if(result instanceof Boolean done&&done){receipt(mc,"complete",null);return true;}
        if(!(result instanceof Action next)){pause=10;return false;}
        deferred=next;settleTicks=5;return false;
    }
    private static boolean perform(Minecraft mc,Action next){
        action=next;if(action.objective()!=last){last=action.objective();attempts=0;receipt(mc,"objective-start",last);}
        if(++attempts>160)throw new IllegalStateException("objective stalled: "+route+" "+last+" "+action.kind());
        if(mc.screen!=null)mc.setScreen(null);
        if(action.aim()!=null)look(mc,action.aim());
        boolean shift=action.kind().equals("block") && (route==Approach.COMBAT && action.objective()!=Objective.CALIBRATOR_DEFEATED
                ||route==Approach.EQUIPMENT && action.objective()==Objective.CALIBRATOR_DEFEATED);
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,
                shift?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:
                net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        // Client position/rotation packets precede the actual interaction packet.
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot(),mc.player.onGround()));
        switch(action.kind()) {
            case "block" -> mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(action.aim(),Direction.UP,action.block(),false));
            case "npc" -> {var e=mc.level.getEntity(action.entity());if(e!=null)mc.gameMode.interact(mc.player,e,InteractionHand.MAIN_HAND);}
            case "attack" -> {var e=mc.level.getEntity(action.entity());if(e!=null){mc.gameMode.attack(mc.player,e);mc.player.swing(InteractionHand.MAIN_HAND);}}
            case "cast" -> {
                var key=cn.academy.port.client.AcademyClient.SLOTS[2].getKey().getValue();long window=mc.getWindow().getWindow();
                System.setProperty("academy.qa.key."+key,"true");mc.keyboardHandler.keyPress(window,key,0,org.lwjgl.glfw.GLFW.GLFW_PRESS,0);releaseKey=key;
            }
            case "escort","wait" -> {}
            default -> throw new IllegalStateException(action.kind());
        }
        pause=action.kind().equals("cast")?40:action.kind().equals("escort")?20:22;return false;
    }
    private static Object plan(Minecraft mc) {
        var p=mc.getSingleplayerServer().getPlayerList().getPlayer(owner);var world=p.serverLevel();var progress=UrbanStoryStorage.read(p).orElseThrow();
        if(progress.snapshot().size()>count){
            count=progress.snapshot().size();receipt(mc,"objective-complete",last);
            if(last!=null&&progress.approach(last).orElse(null)!=route)throw new IllegalStateException("wrong route "+last+" "+progress.approach(last));
            attempts=0;escortStep=0;ESCORT.clear();
        }
        if(progress.chapter()==Chapter.COMPLETE)return true;
        Objective objective=Arrays.stream(Objective.values()).filter(o->o.chapter()==progress.chapter()&&!progress.completed(o)).findFirst().orElseThrow();
        String siteId=objective==Objective.BROADCAST_LEAD?"urban_campus":objective.chapter().ordinal()<2?"urban_station":"urban_facility";
        Site site=SITES.get(siteId);
        if(objective==Objective.CALIBRATOR_EVACUATION) {
            var student=npc(world,site,"protocol_student");if(student==null)return null;
            if(student.getPersistentData().hasUUID("academy:urban_escort_owner"))return escort(p,student,site,objective);
        }
        if(objective==Objective.CALIBRATOR_DEFEATED){
            BlockPos podium=site.blocks().get("podium");var bosses=world.getEntitiesOfClass(UrbanCalibrator.Boss.class,new AABB(podium).inflate(24),b->b.isAlive()&&!b.isRemoved());
            if(bosses.isEmpty()){equip(p,ItemStack.EMPTY,false);return atBlock(p,podium,objective);}
            if(route==Approach.COMBAT){var b=bosses.getFirst();equip(p,new ItemStack(Items.NETHERITE_SWORD),false);return atEntity(p,b,objective,"attack");}
            if(route==Approach.EQUIPMENT){equip(p,new ItemStack(Items.REDSTONE,3),true);return atBlock(p,podium,objective);}
        }
        if(route==Approach.ABILITY)return paper(p,site,objective);
        if(route==Approach.COMBAT) {
            var guards=world.getEntitiesOfClass(Zombie.class,new AABB(site.box().minX(),site.box().minY(),site.box().minZ(),site.box().maxX()+1,site.box().maxY()+1,site.box().maxZ()+1),z->z.isAlive()&&z.getPersistentData().getBoolean("academy:urban_guard"));
            if(!guards.isEmpty()){equip(p,new ItemStack(Items.NETHERITE_SWORD),false);return atEntity(p,guards.getFirst(),objective,"attack");}
            equip(p,new ItemStack(Items.NETHERITE_SWORD),true);return atBlock(p,site.blocks().get("table"),objective);
        }
        String role=switch(objective){case BROADCAST_LEAD->"broadcast_guide";case BROADCAST_RESCUE->"injured_student";case STUDENT_WITNESS->"student_witness";case PROTOCOL_RESCUE,CALIBRATOR_EVACUATION->"protocol_student";default->"";};
        equip(p,new ItemStack(objective==Objective.BROADCAST_RESCUE||objective==Objective.PROTOCOL_RESCUE?Items.HONEY_BOTTLE:objective==Objective.CALIBRATOR_EVACUATION?Items.LEAD:objective==Objective.STUDENT_RECORDS||objective==Objective.STUDENT_WITNESS?Items.BOOK:objective==Objective.STUDENT_TRACES?Items.COMPASS:Items.AIR),false);
        if(!role.isEmpty()){var resident=npc(world,site,role);return resident==null?null:atEntity(p,resident,objective,"npc");}
        String marker=switch(objective){case BROADCAST_SOURCE,PROTOCOL_ACCESS->"console";case STUDENT_RECORDS->"records";case STUDENT_TRACES->"traces";case PROTOCOL_ARRAY->"master";case CALIBRATOR_NODE_A->"red";case CALIBRATOR_NODE_B->"yellow";case CALIBRATOR_NODE_C->"green";default->throw new IllegalStateException(objective.name());};
        return atBlock(p,site.blocks().get(marker),objective);
    }
    private static Action paper(ServerPlayer p,Site site,Objective objective) {
        var table=site.blocks().get("table");var gold=site.blocks().get("gold");BlockPos center=gold.above();
        Direction toward=null;for(Direction d:Direction.Plane.HORIZONTAL)if(table.relative(d,2).below(2).equals(center))toward=d;
        if(toward==null)throw new IllegalStateException("physical puzzle markers mismatch");Direction side=toward.getClockWise();
        boolean built=true;for(int i=-1;i<=1;i++)built&=CanonPaperContent.owned(p,center.relative(side,i));
        if(built){equip(p,ItemStack.EMPTY,false);move(p,center.getX()+.5,center.getY()+1,center.getZ()+.5);return new Action("block",table,0,table.getCenter(),objective);}
        equip(p,new ItemStack(Items.PAPER,64),false);
        if(attempts%10==0)com.mojang.logging.LogUtils.getLogger().info("QA_PAPER_PREFLIGHT category={} level={} ready={} usable={} activated={} preset={} cp={} material={} feet={}",
            AbilityStorage.get(p).category,AbilityStorage.get(p).level,HighPowerResources.ready(p),AbilityStorage.get(p).canUse("paper_walkway"),AbilityStorage.get(p).activated,
            AbilityStorage.get(p).presets.currentSkill(2),HighPowerResources.cp(p),p.getMainHandItem(),p.position());
        BlockPos approach=center.relative(side,3);
        if(!p.serverLevel().getBlockState(approach).isAir()||!p.serverLevel().getBlockState(approach.above()).isAir())side=side.getOpposite();
        BlockPos edge=center.relative(side,-1);Vec3 feet=edge.relative(side,4).getCenter().add(0,-.5,0);
        move(p,feet.x,feet.y,feet.z);return new Action("cast",gold,0,edge.below().getCenter().add(0,.5,0),objective);
    }
    private static Action escort(ServerPlayer p,Villager student,Site site,Objective objective) {
        var world=p.serverLevel();
        if(ESCORT.isEmpty()) {
            var candidates=new ArrayList<BlockPos>();BlockPos origin=student.blockPosition();
            var tag=net.minecraft.tags.TagKey.create(Registries.STRUCTURE,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy","urban_facility"));
            for(BlockPos input:BlockPos.betweenClosed(origin.offset(-20,-2,-20),origin.offset(20,8,20))){
                BlockPos pos=input.immutable();if(!world.hasChunkAt(pos)||!world.getBlockState(pos).isAir()||!world.getBlockState(pos.above()).isAir()
                        ||!world.getBlockState(pos.below()).isFaceSturdy(world,pos.below(),Direction.UP)
                        ||world.structureManager().getStructureWithPieceAt(pos,tag).isValid())continue;
                boolean margin=true;
                for(int dx=-2;dx<=2;dx+=2)for(int dz=-2;dz<=2;dz+=2)
                    if(world.structureManager().getStructureWithPieceAt(pos.offset(dx,0,dz),tag).isValid())margin=false;
                if(margin)candidates.add(pos);
            }
            candidates.sort(java.util.Comparator.comparingDouble(pos->pos.distSqr(origin)));
            net.minecraft.world.level.pathfinder.Path selected=null;
            for(BlockPos candidate:candidates.subList(0,Math.min(256,candidates.size()))){
                var path=student.getNavigation().createPath(candidate,0);
                if(path!=null&&path.canReach()){selected=path;break;}
            }
            if(selected==null)throw new IllegalStateException("generated facility has no reachable native evacuation exit");
            for(int i=1;i<selected.getNodeCount();i++){
                var node=selected.getNode(i);ESCORT.add(new Vec3(node.x+.5,node.y,node.z+.5));
            }
            if(ESCORT.isEmpty())throw new IllegalStateException("empty native evacuation path");
        }
        Vec3 goal=ESCORT.get(Math.min(escortStep,ESCORT.size()-1));move(p,goal.x,goal.y,goal.z);
        if(student.position().subtract(goal).multiply(1,0,1).lengthSqr()<4.0 && Math.abs(student.getY()-goal.y)<.6 && escortStep<ESCORT.size()-1)escortStep++;
        try{var report=new JsonObject();report.addProperty("step",escortStep);report.addProperty("student",student.position().toString());
            report.addProperty("player",p.position().toString());report.addProperty("goal",goal.toString());report.addProperty("navigationDone",student.getNavigation().isDone());
            Files.writeString(Path.of(System.getProperty("academy.qa.urban.directory","/tmp")).resolve("urban-escort-live.json"),report+"\n");
        }catch(Exception error){throw new IllegalStateException(error);}
        return new Action("escort",null,student.getId(),student.getEyePosition(),objective);
    }
    private static Villager npc(ServerLevel world,Site site,String role){return world.getEntitiesOfClass(Villager.class,new AABB(site.box().minX()-4,site.box().minY()-4,site.box().minZ()-4,site.box().maxX()+5,site.box().maxY()+5,site.box().maxZ()+5),v->v.isAlive()&&role.equals(v.getPersistentData().getString(UrbanStoryClues.WITNESS_KEY))).stream().findFirst().orElse(null);}
    private static Action atBlock(ServerPlayer p,BlockPos pos,Objective o){if(pos==null)throw new IllegalStateException("missing marker "+o);standNear(p,pos);return new Action("block",pos,0,pos.getCenter(),o);}
    private static Action atEntity(ServerPlayer p,Entity target,Objective o,String kind){standNear(p,target.blockPosition());return new Action(kind,null,target.getId(),target.getEyePosition(),o);}
    private static void standNear(ServerPlayer p,BlockPos target){var w=p.serverLevel();
        for(int dy=0;dy>=-3;dy--)for(Direction d:Direction.Plane.HORIZONTAL){BlockPos pos=target.relative(d,2).offset(0,dy,0);
            if(w.getBlockState(pos).isAir()&&w.getBlockState(pos.above()).isAir()&&w.getBlockState(pos.below()).isFaceSturdy(w,pos.below(),Direction.UP)){
                move(p,pos.getX()+.5,pos.getY(),pos.getZ()+.5);return;}}
        throw new IllegalStateException("no safe approach "+target);}
    private static void move(ServerPlayer p,double x,double y,double z){p.teleportTo(x,y,z);p.setDeltaMovement(Vec3.ZERO);}
    private static void equip(ServerPlayer p,ItemStack stack,boolean shift){p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.setShiftKeyDown(shift);p.getInventory().setChanged();}
    private static void look(Minecraft mc,Vec3 target){Vec3 d=target.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));mc.player.setYHeadRot(mc.player.getYRot());}
    private static synchronized void receipt(Minecraft mc,String event,Objective objective){var row=new JsonObject();row.addProperty("event",event);row.addProperty("route",route==null?"":route.name());row.addProperty("objective",objective==null?"":objective.name());row.addProperty("completed",count);row.addProperty("time",System.currentTimeMillis());
        try{Files.writeString(mc.gameDirectory.toPath().resolve("urban-formal-receipts.jsonl"),row+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new IllegalStateException(e);}
        com.mojang.logging.LogUtils.getLogger().info("QA_URBAN_FORMAL {}",row);}
}
