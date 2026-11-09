package cn.academy.port.core;
import cn.academy.port.*;
import cn.academy.port.client.ClassicDeveloperLayout;
import cn.academy.port.develop.*;
import cn.academy.port.preset.PresetSkills;
import java.util.*;
/** Tests observable catalog/developer/tree/preset behavior rather than a duplicate configuration parser. */
public final class ClassicSkillConfigurationIntegrationTest {
 private static int checks;private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 public static void main(String[] args){var arc=ClassicSkillConfiguration.key("electromaster","arc_gen");var charging=ClassicSkillConfiguration.key("electromaster","charging");var manip=ClassicSkillConfiguration.key("electromaster","mag_manip");var brain=ClassicSkillConfiguration.key("electromaster","brain_course");var off=new ClassicSkillConfiguration.Flags(false,false);
  try{SkillCatalog.initializeConfiguration(Map.of(arc,off,manip,off,brain,off));var s=new AbilityProgress();s.selectCategory("electromaster");s.setLevel(1);var child=SkillCatalog.find(s.category,"charging").orElseThrow();
   check(SkillCatalog.ALL.size()==50&&SkillCatalog.CONFIG_KEYS.size()==41,"all source registrations and generic aliases retained");
   check(SkillCatalog.canLearn(s,child)&&DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,child),"disabled initial parent removed in both actual learning validators");
   var node=ClassicDeveloperLayout.category(s.category).stream().filter(n->n.id().equals("charging")).findFirst().orElseThrow();check(node.enabled()&&node.parent().isEmpty()&&node.conditions().stream().noneMatch(c->c.get("type").getAsString().equals("skill_dependency")),"developer and terminal tree show true root and omit disabled condition");
   check(ClassicDeveloperLayout.category(s.category).stream().filter(n->n.id().equals("arc_gen")).noneMatch(ClassicDeveloperLayout.Node::enabled),"disabled node hidden without deleting identity");
   check(SkillCatalog.levelSkillCount(s)==1,"only enabled level1 controllable contributes denominator");
   s.learn("arc_gen");check(!PresetSkills.selectable(s,"arc_gen")&&PresetSkills.mappedUsable(s,"arc_gen"),"disabled skill cannot be newly selected, old learned mapping remains source usable");
   check(s.presets.edit(0,0,"arc_gen",id->PresetSkills.mappedUsable(s,id))&&s.presets.currentSkill(0).equals("arc_gen"),"source raw old preset survives");
   s.setLevel(2);var movement=SkillCatalog.find(s.category,"mag_movement").orElseThrow();check(!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,movement),"independent additional dependency still gates portable child");s.learn("charging");s.experience.put("charging",.7);check(DevelopmentActions.canLearn(s,DeveloperType.NORMAL,movement),"remaining 70percent extra condition enables normal learning");
   s.setLevel(4);s.learn("thunder_bolt");s.experience.put("thunder_bolt",.3);check(DevelopmentActions.canLearn(s,DeveloperType.ADVANCED,SkillCatalog.find(s.category,"railgun").orElseThrow()),"disabled manipulation extra condition removed but thunder parent retained");
   s.learn("brain_course");double bonus=s.maxCp();SkillCatalog.refreshConfiguration(Map.of(charging,off,brain,off));check(s.maxCp()==bonus&&s.learned("brain_course"),"disabled already learned passive continues source bonus");
   check(ClassicDeveloperLayout.category(s.category).stream().filter(n->n.id().equals("charging")).allMatch(n->n.parent().isEmpty()),"live reenable does not recreate omitted startup parent");
   s.setLevel(1);check(SkillCatalog.levelSkillCount(s)==1,"live enabled denominator updates while graph stays frozen");SkillCatalog.refreshConfiguration(Map.of(arc,off,charging,off));check(SkillCatalog.levelSkillCount(s)==0&&s.levelProgress(0)==1&&s.canLevelUp(0),"zero enabled active count preserves source immediately full progress");
   check(s.learned("arc_gen")&&s.presets.currentSkill(0).equals("arc_gen"),"reload never erases learned or preexisting mapped identity");
  }finally{SkillCatalog.resetConfiguration();}
  System.out.println("PASS "+checks+" integrated source catalog, learning, frozen tree, active denominator, passive and old preset contracts");
 }
}
