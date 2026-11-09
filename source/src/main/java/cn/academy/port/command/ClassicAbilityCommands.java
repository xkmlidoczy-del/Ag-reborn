/* AcademyCraft1.0.7 CommandAIMBase/CommandACACH adaptation. GPLv3; see NOTICE. */
package cn.academy.port.command;

import cn.academy.port.*;
import cn.academy.port.achievements.*;
import cn.academy.port.core.AbilityProgress;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Classic self/admin command surface. Syntax and nonfinite input failures are bounded modern adaptations. */
public final class ClassicAbilityCommands {
 private static final String[] HELP={"help","cat","catlist","learn","learn_all","reset","learned","skills","fullcp","level","exp","cd_clear","maxout"};
 private static final String PERSISTED="PlayerPersisted",CHEATS="aim_cheats";
 private ClassicAbilityCommands(){}
 public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
  dispatcher.register(Commands.literal("aim").requires(s->s.hasPermission(4))
   .executes(c->self(c.getSource(),new String[0]))
   .then(Commands.argument("arguments",StringArgumentType.greedyString()).executes(c->self(c.getSource(),words(StringArgumentType.getString(c,"arguments"))))));
  dispatcher.register(Commands.literal("aimp").requires(s->s.hasPermission(4))
   .executes(c->{message(c.getSource(),"ac.command.aim.help");return 0;})
   .then(Commands.argument("arguments",StringArgumentType.greedyString()).executes(c->admin(c.getSource(),words(StringArgumentType.getString(c,"arguments"))))));
  dispatcher.register(Commands.literal("acach").requires(s->s.hasPermission(4))
   .executes(c->{literal(c.getSource(),"Usage: /acach ACHIEVEMENT_NAME [PLAYER_NAME]");return 0;})
   .then(Commands.argument("arguments",StringArgumentType.greedyString()).executes(c->achievement(c.getSource(),words(StringArgumentType.getString(c,"arguments"))))));
 }
 private static String[] words(String value){return value.isBlank()?new String[0]:value.trim().split("\\s+");}
 public static boolean active(ServerPlayer player){return player.getPersistentData().getCompound(PERSISTED).getBoolean(CHEATS);}
 private static void active(ServerPlayer player,boolean value){var tag=player.getPersistentData().getCompound(PERSISTED);tag.putBoolean(CHEATS,value);player.getPersistentData().put(PERSISTED,tag);}
 private static int self(CommandSourceStack source,String[] args){
  ServerPlayer p=source.getPlayer();if(p==null){message(source,"ac.command.noplayer");return 0;}
  if(!active(p)&&p.server.getWorldData().isAllowCommands())active(p,true);
  if(args.length==1&&(args[0].equals("cheats_on")||args[0].equals("cheats_off"))){boolean on=args[0].equals("cheats_on");active(p,on);success(source);if(on)message(source,"ac.command.aim.warning");return 1;}
  if(!active(p)&&!p.getAbilities().instabuild){message(source,"ac.command.aim.notactive");return 0;}
  if(args.length==0){message(source,"ac.command.aim.help");return 1;}return run(source,p,args);
 }
 private static ServerPlayer exact(CommandSourceStack source,String name){return source.getServer().getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals(name)).findFirst().orElse(null);}
 private static int admin(CommandSourceStack source,String[] args){if(args.length==0){message(source,"ac.command.aim.help");return 0;}var p=exact(source,args[0]);if(p==null){message(source,"ac.command.noplayer");return 0;}return run(source,p,Arrays.copyOfRange(args,1,args.length));}
 private static List<SkillCatalog.Skill> skills(AbilityProgress state){return (cn.academy.port.canon.CanonSkills.activeCategory(state.category)?cn.academy.port.canon.CanonSkills.ALL:SkillCatalog.ALL).stream().filter(s->s.category().equals(state.category)).toList();}
 private static SkillCatalog.Skill skill(AbilityProgress state,String value){try{int index=Integer.parseInt(value);var all=skills(state);return index>=0&&index<all.size()?all.get(index):null;}catch(NumberFormatException ignored){return SkillCatalog.find(state.category,value).orElse(null);}}
 private static boolean category(CommandSourceStack source,AbilityProgress state){if(state.hasCategory())return true;message(source,"ac.command.aim.nonecathint");return false;}
 private static int invalid(CommandSourceStack source){message(source,"ac.command.invalid");return 0;}
 private static void success(CommandSourceStack source){message(source,"ac.command.successful");}
 private static void message(CommandSourceStack source,String key,Object...args){source.sendSuccess(()->Component.translatable(key,args),false);}
 private static void literal(CommandSourceStack source,String text){source.sendSuccess(()->Component.literal(text),false);}
 private static void learnHighPower(AbilityProgress state){for(var skill:cn.academy.port.highpower.HighPowerSkills.ALL)if(skill.category().equals(state.category)&&!state.learned(skill.id()))state.learn(skill.id());}
 private static int run(CommandSourceStack source,ServerPlayer p,String[] args){
  if(args.length==0)return invalid(source);if(AbilityConsumption.busy(p))return invalid(source);var state=AbilityStorage.get(p);String command=args[0];boolean changed=false;
  switch(command){
   case "?","help"->{for(String entry:HELP)message(source,"ac.command.aim."+entry);return 1;}
   case "cat"->{if(args.length==1){message(source,"ac.command.aim.curcat",Component.translatable(state.hasCategory()?"ac.ability."+state.category+".name":"ac.command.aim.nonecat"));return 1;}if(args.length!=2)return invalid(source);if(!cn.academy.port.develop.DevelopmentActions.CATEGORIES.contains(args[1])){message(source,"ac.command.aim.nocat");return 0;}if(!state.category.equals(args[1])){AcademyGameplay.cancelForAdministrativeMutation(p);state.changeCategoryClassic(args[1]);changed=true;}success(source);}
   case "catlist"->{message(source,"ac.command.aim.cats");var cats=cn.academy.port.develop.DevelopmentActions.CATEGORIES;for(int i=0;i<cats.size();i++){String cat=cats.get(i);final int index=i;source.sendSuccess(()->Component.literal("#"+index+" "+cat+": ").append(Component.translatable("ac.ability."+cat+".name")),false);}return 1;}
   case "learn","unlearn"->{if(args.length!=2)return invalid(source);if(!category(source,state))return 0;var target=skill(state,args[1]);if(target==null){message(source,"ac.command.aim.noskill");return 0;}if(command.equals("learn"))state.learn(target.id());else{AcademyGameplay.cancelForAdministrativeMutation(p);state.unlearn(target.id());}changed=true;}
   case "learn_all"->{if(!category(source,state))return 0;state.learnAllClassic(skills(state).stream().map(SkillCatalog.Skill::id).toList());changed=true;success(source);}
   case "learn_hp"->{if(!category(source,state))return 0;learnHighPower(state);changed=true;success(source);}
   case "maxhp"->{if(!category(source,state))return 0;AcademyGameplay.cancelForAdministrativeMutation(p);if(state.level!=5)state.setLevel(5);state.learnAllClassic(skills(state).stream().map(SkillCatalog.Skill::id).toList());learnHighPower(state);state.cp=state.maxCp();state.overload=0;state.levelExperience=100F;changed=true;success(source);}
   case "reset"->{AcademyGameplay.cancelForAdministrativeMutation(p);state.changeCategoryClassic("");changed=true;success(source);}
   case "learned"->{var names=new StringBuilder();boolean begin=true;for(var target:skills(state))if(state.learned(target.id())){names.append(begin?"":", "+target.id());begin=false;}message(source,"ac.command.aim.learned.format",names.toString());return 1;}
   case "skills"->{if(!category(source,state))return 0;var all=skills(state);for(int i=0;i<all.size();i++){var target=all.get(i);final int index=i;source.sendSuccess(()->Component.literal("#"+index+" "+target.id()+": ").append(Component.translatable("ac.ability."+(cn.academy.port.core.ClassicSkillConfiguration.GENERIC_SKILLS.contains(target.id())?"generic":target.category())+"."+target.id()+".name")),false);}return 1;}
   case "level"->{if(args.length==1){literal(source,Integer.toString(state.level));return 1;}if(args.length!=2)return invalid(source);if(!category(source,state))return 0;int value;try{value=Integer.parseInt(args[1]);}catch(NumberFormatException bad){return invalid(source);}if(value<1||value>5){message(source,"ac.command.aim.outofrange",1,5);return 0;}if(state.level!=value){AcademyGameplay.cancelForAdministrativeMutation(p);state.setLevel(value);changed=true;}success(source);}
   case "fullcp"->{if(!category(source,state))return 0;state.cp=state.maxCp();state.overload=0;changed=true;success(source);}
   case "exp"->{if(args.length<2||args.length>3)return invalid(source);if(!category(source,state))return 0;var target=skill(state,args[1]);if(target==null){message(source,"ac.command.aim.noskill");return 0;}String name="ac.ability."+(cn.academy.port.core.ClassicSkillConfiguration.GENERIC_SKILLS.contains(target.id())?"generic":target.category())+"."+target.id()+".name";if(args.length==2){message(source,"ac.command.aim.curexp",Component.translatable(name),(float)state.exp(target.id())*100F);return 1;}float value;try{value=Float.parseFloat(args[2]);}catch(NumberFormatException bad){return invalid(source);}if(!Float.isFinite(value))return invalid(source);if(value<0||value>1){message(source,"ac.command.aim.outofrange",0F,1F);return 0;}if(state.learned(target.id())){state.setSkillExp(target.id(),value);changed=true;}success(source);}
   case "cd_clear"->{if(!category(source,state))return 0;state.cooldowns.clear();state.cooldownMaxTicks.clear();changed=true;success(source);}
   case "maxout"->{if(!category(source,state))return 0;state.levelExperience=100F;changed=true;success(source);}
   default->{message(source,"ac.command.aim.nocomm");return 0;}
  }
  if(changed){certify(p,command);AbilityStorage.save(p);AcademyNetwork.sync(p);}return 1;
 }
 /** Admin edits skip the developer, so canon players are enrolled here and level edits rebaseline the earned-capacity contract. */
 private static void certify(ServerPlayer p,String command){
  cn.academy.port.highpower.HighPowerProgression.certifyCanon(p);
  if((command.equals("level")||command.equals("maxhp"))&&cn.academy.port.highpower.HighPowerCombat.enabled(p)&&cn.academy.port.highpower.HighPowerCombat.earnedLevel(p)!=AbilityStorage.get(p).level
     &&cn.academy.port.highpower.HighPowerCombat.resetGrowthContract(p))cn.academy.port.highpower.HighPowerGrowth.invalidate(p);
 }
 private static int achievement(CommandSourceStack source,String[] args){if(args.length==0){literal(source,"Usage: /acach ACHIEVEMENT_NAME [PLAYER_NAME]");return 0;}String playerName=args.length>1?args[1]:source.getTextName();var p=source.getServer().getPlayerList().getPlayerByName(playerName);if(p==null){message(source,"ac.command.noplayer");return 0;}if(ClassicAchievementCatalog.get(args[0])==null){literal(source,"No such achievement found");return 0;}ClassicAchievements.trigger(p,args[0]);success(source);return 1;}
}
