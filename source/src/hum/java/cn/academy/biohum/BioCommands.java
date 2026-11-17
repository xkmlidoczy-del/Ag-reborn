package cn.academy.biohum;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.network.chat.Component;
public final class BioCommands {
 public static void register(RegisterCommandsEvent e){
  var root=Commands.literal("biohum");
  root.then(Commands.literal("spawn").requires(s->s.hasPermission(2)).then(Commands.argument("character",StringArgumentType.word())
   .suggests((c,b)->SharedSuggestionProvider.suggest(Profiles.ALL.keySet(),b))
   .executes(c->spawn(c.getSource(),StringArgumentType.getString(c,"character"),null))
   .then(Commands.argument("phase",StringArgumentType.word())
    .suggests((c,b)->SharedSuggestionProvider.suggest(Skills.phases(StringArgumentType.getString(c,"character")).stream().map(Skills.Phase::id),b))
    .executes(c->spawn(c.getSource(),StringArgumentType.getString(c,"character"),StringArgumentType.getString(c,"phase"))))));
  root.then(Commands.literal("gallery").requires(s->s.hasPermission(2)).executes(c->gallery(c.getSource(),0))
   .then(Commands.argument("batch",IntegerArgumentType.integer(1,(Profiles.ALL.size()+11)/12))
    .executes(c->gallery(c.getSource(),IntegerArgumentType.getInteger(c,"batch")))));
  root.then(Commands.literal("order").requires(s->s.hasPermission(2)).then(Commands.argument("npcs",EntityArgument.entities()).then(Commands.argument("action",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(java.util.List.of("talk","follow","stay","defend","release","character","skill0","skill1","skill2"),b)).executes(c->{var p=c.getSource().getPlayerOrException();int n=0;for(var en:EntityArgument.getEntities(c,"npcs"))if(en instanceof HumNpc a&&a.request(p,StringArgumentType.getString(c,"action")))n++;return n;}))));
  root.then(Commands.literal("cast").requires(s->s.hasPermission(2)).then(Commands.argument("npc",EntityArgument.entity())
   .then(Commands.argument("skill",StringArgumentType.word())
    .suggests((c,b)->SharedSuggestionProvider.suggest(Skills.all().stream().map(Skills.Skill::id),b))
    .executes(c->EntityArgument.getEntity(c,"npc") instanceof HumNpc npc
     &&npc.request(c.getSource().getPlayerOrException(),"cast/"+StringArgumentType.getString(c,"skill"))?1:0))));
  root.then(Commands.literal("windowless")
   .then(Commands.literal("build").requires(s->s.hasPermission(2)).executes(c->WindowlessBuilding.commandBuild(c.getSource(),c.getSource().getPlayerOrException(),false)))
   .then(Commands.literal("relocate").requires(s->s.hasPermission(2)).executes(c->WindowlessBuilding.commandBuild(c.getSource(),c.getSource().getPlayerOrException(),true)))
   .then(Commands.literal("release").requires(s->s.hasPermission(2)).executes(c->WindowlessBuilding.release(c.getSource(),c.getSource().getPlayerOrException())))
   .then(Commands.literal("enter").executes(c->WindowlessBuilding.transfer(c.getSource(),c.getSource().getPlayerOrException(),true)))
   .then(Commands.literal("console").executes(c->WindowlessBuilding.openConsole(c.getSource().getPlayerOrException())))
   .then(Commands.literal("exit").executes(c->WindowlessBuilding.transfer(c.getSource(),c.getSource().getPlayerOrException(),false))));
  root.then(Commands.literal("worldeffects").requires(s->s.hasPermission(2))
   .executes(c->{boolean temporary=WorldEffects.temporary(c.getSource().getLevel());
    c.getSource().sendSuccess(()->Component.literal(temporary?"环境效果 · 临时":"环境效果 · 永久"),false);return 1;})
   .then(Commands.literal("permanent").executes(c->setEnvironment(c.getSource(),false)))
   .then(Commands.literal("temporary").executes(c->setEnvironment(c.getSource(),true))));
  root.then(Commands.literal("othinus").requires(s->s.hasPermission(2))
   .then(Commands.literal("status").executes(c->{
    var state=OthinusRewrite.get(c.getSource().getServer().overworld());
    c.getSource().sendSuccess(()->Component.literal(state.phase()==null?"当前没有世界改写。":
     state.phase().title+" · "+(state.sceneReady()?"已成形":"正在改写")+" · "+
      state.settledChunks()+" 区块 · "+state.changedBlocks()+" 方块"),false);
    return 1;
   }))
   .then(Commands.literal("restore").executes(c->OthinusRewrite.release(c.getSource().getServer().overworld()))));
  root.then(Commands.literal("return").requires(s->s.hasPermission(2)).then(Commands.argument("target",EntityArgument.entity()).executes(c->
   EntityArgument.getEntity(c,"target") instanceof net.minecraft.world.entity.LivingEntity target
    &&CombatSkills.returnFromExile(target)?1:0)));
  root.then(Commands.literal("target").requires(s->s.hasPermission(2)).then(Commands.argument("npc",EntityArgument.entity()).then(Commands.argument("enemy",EntityArgument.entity()).executes(c->{var a=EntityArgument.getEntity(c,"npc");var t=EntityArgument.getEntity(c,"enemy");if(a instanceof HumNpc n&&t instanceof net.minecraft.world.entity.LivingEntity l&&n.validTarget(l)){n.setTarget(l);return 1;}return 0;}))));
  root.then(Commands.literal("status").requires(s->s.hasPermission(2)).then(Commands.argument("npc",EntityArgument.entity()).executes(c->{if(EntityArgument.getEntity(c,"npc") instanceof HumNpc a){c.getSource().sendSuccess(()->Component.literal(a.profile().name()+" / "+a.phaseId()+" / HP "+a.getHealth()+" / mode "+a.mode()+" / cast "+a.casting()),false);return 1;}return 0;})));
  e.getDispatcher().register(root);
 }
 private static int spawn(CommandSourceStack source,String id,String phase){
  var type=BioHum.NPCS.get(id);
  if(type==null||phase!=null&&Skills.phase(id,phase)==null){
   source.sendFailure(Component.literal("未知角色或阶段"));return 0;
  }
  var npc=type.get().create(source.getLevel());
  if(phase!=null)npc.selectPhase(phase);
  var position=source.getPosition();
  npc.moveTo(position.x,position.y,position.z,source.getRotation().y,0);
  return source.getLevel().addFreshEntity(npc)?1:0;
 }
 private static int setEnvironment(CommandSourceStack source,boolean temporary){
  WorldEffects.setTemporary(source.getLevel(),temporary);
  source.sendSuccess(()->Component.literal(temporary?"后续环境变化将自动恢复。":"后续环境变化将永久保留。"),true);
  return 1;
 }
 private static int gallery(CommandSourceStack source,int batch){
  var types=BioHum.NPCS.values().stream();
  if(batch>0)types=types.skip((batch-1)*12L).limit(12);
  var position=source.getPosition();
  int index=0;
  for(var holder:types.toList()){
   var npc=holder.get().create(source.getLevel());
   npc.moveTo(position.x+(index%6)*2.5,position.y,position.z+(index/6)*4,180,0);
   npc.setMode(2,null);
   npc.setNoAi(true);
   npc.setCustomName(Component.literal(npc.profile().name()));
   npc.setCustomNameVisible(true);
   source.getLevel().addFreshEntity(npc);
   index++;
  }
  return index;
 }
}
