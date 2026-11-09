package cn.academy.port;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.SkillPresets;
import cn.academy.port.preset.PresetSkills;
import cn.academy.port.client.ClassicActivationKey;
import cn.academy.port.client.ClassicInputLatch;
import cn.academy.port.client.ClassicPresetEditor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Deterministic preset/state/codec tests. This does not launch Minecraft or validate pixels. */
public final class PresetRegressionTest {
    private static int assertions;
    private static void check(boolean condition,String label){assertions++;if(!condition)throw new AssertionError(label);}
    private static void close(double actual,double expected,String label){check(Math.abs(actual-expected)<.000001,label);}
    private static AbilityProgress learned(){var s=new AbilityProgress();s.selectCategory("electromaster");s.setLevel(5);s.learn("arc_gen");s.learn("charging");s.learn("railgun");s.learn("brain_course");s.learn("mag_manip");return s;}
    public static void main(String[] args)throws Exception{
        var state=learned();var presets=state.presets;
        check(SkillPresets.MAX_PRESETS==4&&SkillPresets.MAX_KEYS==4,"classic dimensions");
        for(int page=0;page<4;page++)for(int slot=0;slot<4;slot++)check(presets.skill(page,slot).isEmpty(),"learning never auto-binds");
        check(presets.edit(0,0,"arc_gen",id->PresetSkills.selectable(state,id)),"learned gameplay may be selected");
        check(presets.edit(0,1,"charging",id->PresetSkills.selectable(state,id)),"second mapping");
        check(presets.edit(1,3,"railgun",id->PresetSkills.selectable(state,id)),"independent page");
        check(presets.switchTo(1),"valid selected index");
        check(presets.currentContains("railgun")&&!presets.currentContains("arc_gen"),"current membership only");
        String[] before=presets.copy(0);long revision=presets.revision();
        for(int page:new int[]{Integer.MIN_VALUE,-1,4,Integer.MAX_VALUE})check(!presets.edit(page,0,"arc_gen",id->true),"malformed page rejected");
        for(int slot:new int[]{Integer.MIN_VALUE,-1,4,Integer.MAX_VALUE})check(!presets.edit(0,slot,"railgun",id->true),"malformed slot rejected");
        for(String id:new String[]{"unknown","electron_bomb","brain_course","vec_accel","arc_gen",null,"x".repeat(49)})check(!presets.edit(0,1,id,skill->PresetSkills.selectable(state,skill)),"invalid, passive, unported, foreign, duplicate and null rejected");
        check(Arrays.equals(before,presets.copy(0))&&presets.revision()==revision,"rejected edit is atomic");
        check(!presets.replace(0,new String[]{"arc_gen","railgun","charging","charging"},id->true),"duplicate bulk edit rejected");
        check(!presets.replace(0,new String[]{"arc_gen"},id->true),"wrong slot count rejected");
        check(!presets.replace(0,null,id->true),"null preset rejected");
        check(!presets.replace(0,new String[]{"arc_gen","","",""},null),"null validator rejected");
        String[] isolated=presets.copy(0);isolated[0]="railgun";check(presets.skill(0,0).equals("arc_gen"),"copy not mutable alias");
        check(presets.edit(0,1,"",id->PresetSkills.selectable(state,id)),"empty selection removes");
        check(PresetSkills.available(state,0).stream().map(SkillCatalog.Skill::id).toList().equals(java.util.List.of("charging","mag_manip","railgun")),"selector retains classic catalog order and excludes used skills");
        state.setCooldown("arc_gen",20);state.tickCooldowns();check(state.cooldownMaximum("arc_gen")==20&&state.cooldowns.get("arc_gen")==19,"cooldown stores independent captured maximum");
        state.setCooldown("arc_gen",5);check(state.cooldownMaximum("arc_gen")==20&&state.cooldowns.get("arc_gen")==19,"classic shorter overlapping cooldown cannot shrink either bound");
        var tag=AbilityStorage.encode(state);check(tag.getInt("schema")==3,"schema migration is explicit");
        var out=new ByteArrayOutputStream();NbtIo.writeCompressed(tag,out);
        var cold=AbilityStorage.decode(NbtIo.readCompressed(new ByteArrayInputStream(out.toByteArray()),NbtAccounter.unlimitedHeap()));
        check(cold.presets.current()==1&&cold.presets.currentSkill(3).equals("railgun"),"cold compressed-NBT preset round trip");
        check(cold.presets.skill(0,0).equals("arc_gen")&&cold.presets.revision()==state.presets.revision(),"all pages and revision round trip");
        check(cold.cooldownMaximum("arc_gen")==20&&cold.cooldowns.get("arc_gen")==19,"captured max cooldown survives cold codec");
        check(cold.experience.equals(state.experience),"presets do not alter learning");
        for(int index:new int[]{-1,Integer.MIN_VALUE,4,Integer.MAX_VALUE}){
            var corrupt=tag.copy();corrupt.getCompound("presets").putInt("current",index);
            check(AbilityStorage.decode(corrupt).presets.current()==0,"corrupt index safely defaults");
        }
        var corrupt=tag.copy();var pages=corrupt.getCompound("presets");pages.putLong("revision",-99);
        var page=pages.getCompound("0");page.putString("0","arc_gen");page.putString("1","arc_gen");page.putString("2","brain_course");page.putString("3","vec_accel");
        pages.getCompound("1").putString("0","electron_bomb");pages.getCompound("1").putString("1","unknown");pages.putString("2","not a mapping");page.putString("4","railgun");pages.put("999",page.copy());
        cold=AbilityStorage.decode(corrupt);
        check(cold.presets.revision()==0&&cold.presets.skill(0,0).equals("arc_gen"),"corrupt revision and first duplicate recovery");
        for(int slot=1;slot<4;slot++)check(cold.presets.skill(0,slot).isEmpty(),"duplicate, passive and unported saved slots dropped");
        check(cold.presets.skill(1,0).isEmpty()&&cold.presets.skill(1,1).isEmpty(),"foreign and unknown saved slots dropped");
        for(int slot=0;slot<4;slot++)check(cold.presets.skill(2,slot).isEmpty(),"wrong mapping NBT type is empty");
        var noLearning=tag.copy();noLearning.getCompound("skills").remove("railgun");var rawSaved=AbilityStorage.decode(noLearning);check(rawSaved.presets.currentSkill(3).equals("railgun")&&!rawSaved.learned("railgun")&&!PresetSkills.mappedUsable(rawSaved,"railgun"),"source raw mapping survives unlearning without authorizing cast or manufacturing learning");
        var tooLow=tag.copy();tooLow.putInt("level",1);rawSaved=AbilityStorage.decode(tooLow);check(rawSaved.presets.currentSkill(3).equals("railgun")&&!PresetSkills.mappedUsable(rawSaved,"railgun"),"source raw mapping survives command level decrease without authorizing below-level cast");
        var legacy=tag.copy();legacy.putInt("schema",1);legacy.remove("presets");cold=AbilityStorage.decode(legacy);
        check(cold.learned("railgun")&&cold.presets.current()==0,"legacy learned progress survives empty preset migration");
        for(int p=0;p<4;p++)for(int slot=0;slot<4;slot++)check(cold.presets.skill(p,slot).isEmpty(),"legacy has no silent bindings");
        var malformed=tag.copy();malformed.putString("presets","invalid");cold=AbilityStorage.decode(malformed);check(cold.presets.current()==0&&cold.presets.currentSkill(0).isEmpty(),"wrong preset compound type safe");
        presets.switchTo(3);state.selectCategory("teleporter");check(presets.current()==3,"classic category clear keeps selected index");
        for(int p=0;p<4;p++)for(int slot=0;slot<4;slot++)check(presets.skill(p,slot).isEmpty(),"category clears every mapping");
        var vector=new AbilityProgress();vector.selectCategory("vecmanip");vector.setLevel(1);vector.learn("dir_shock");vector.learn("ground_shock");
        check(vector.presets.edit(0,0,"ground_shock",id->PresetSkills.selectable(vector,id)),"merged GroundShock selectable with actual implementation");
        check(!vector.presets.edit(0,1,"arc_gen",id->PresetSkills.selectable(vector,id)),"merged allowlist still enforces category learning");
        // Exercise a genuinely missing source active skill in its own category as the port grows.
        var missing=SkillCatalog.ALL.stream().filter(skill->skill.category().equals("vecmanip")&&skill.controllable()&&!PresetSkills.IMPLEMENTED.contains(skill.id())).findFirst();
        vector.setLevel(5);
        if(missing.isPresent()){
            String id=missing.orElseThrow().id();vector.learn(id);
            check(!PresetSkills.selectable(vector,id)&&!vector.presets.edit(0,1,id,value->PresetSkills.selectable(vector,value)),"learned same-category level-valid unported skill still rejected");
            var unported=AbilityStorage.encode(vector);unported.getCompound("presets").getCompound("0").putString("1",id);
            check(AbilityStorage.decode(unported).presets.skill(0,1).isEmpty(),"same-category learned unported saved slot dropped");
        }else check(SkillCatalog.ALL.stream().filter(skill->skill.category().equals("vecmanip")&&skill.controllable()).allMatch(skill->PresetSkills.IMPLEMENTED.contains(skill.id())),"all source vector active skills are integrated");
        check(AbilityStorage.decode(AbilityStorage.encode(vector)).presets.currentSkill(0).equals("ground_shock"),"merged GroundShock mapping survives codec");
        var ui=new ClassicPresetEditor();check(ui.browsing()==0,"editor starts page zero, not current gameplay preset");
        check(ui.click(0,2,0)&&ui.selectorSlot()==2,"first row click opens selector");check(ui.click(0,3,1)&&ui.selectorSlot()==-1,"second row click closes existing selector without reopening");
        check(ui.click(3,1,10)&&ui.browsing()==3&&ui.transiting(10),"inactive page click starts browsing transition");
        check(!ui.click(1,0,11),"transition input disabled");close(ui.x(0,10),0,"from position");close(ui.x(3,10),375,"to position");
        close(ui.x(0,185),-187.5,"midpoint position");close(ui.alpha(0,185),.65,"midpoint from alpha");close(ui.scale(3,185),.9,"midpoint to scale");
        check(!ui.transiting(360),"350ms transition ends");close(ui.x(3,360),0,"destination centered");close(ui.alpha(0,360),.3,"inactive alpha");close(ui.scale(0,360),.8,"inactive scale");
        check(ui.click(3,1,361)&&ui.selectorSlot()==1,"browse completed selector");ui.close();check(ui.selectorSlot()==-1&&!ui.transiting(362),"closing cancels transient selection only");
        check(state.presets.current()==3,"UI browsing never selects a gameplay preset");
        var key=new ClassicActivationKey();check(!key.update(true,true,1000),"activation begins on down");check(!key.displayNumbers(1199)&&key.displayNumbers(1200),"numbers appear at200ms");
        check(key.update(false,true,1299),"299ms release is short");check(!key.displayNumbers(1300),"up hides numbers");key.update(true,true,2000);check(!key.update(false,true,2300),"300ms release is long");
        key.update(true,true,3000);check(!key.update(false,false,3100),"GUI release does not toggle");key.replaceSession(true,4000);check(!key.update(false,true,4001),"session replacement release cannot toggle");
        var latch=new ClassicInputLatch();latch.update(true,true);latch.replaceSession(true);check(!latch.update(true,true).press(),"preset switch retains physical-down suppression");latch.update(false,true);check(latch.update(true,true).press(),"new press after switch permitted");
        System.out.println("PASS "+assertions+" classic preset, corrupt-save, editor, activation and delegate assertions (no Minecraft launch)");
    }
}
