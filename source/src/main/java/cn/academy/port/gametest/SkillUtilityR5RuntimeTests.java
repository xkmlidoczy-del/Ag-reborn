package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.SkillTuning;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Additional function assertions for utilities not individually asserted by the generic cast arena. */
@GameTestHolder("academy_function_r5")
@PrefixGameTestTemplate(false)
public final class SkillUtilityR5RuntimeTests {
    private SkillUtilityR5RuntimeTests() {}
    @GameTestGenerator public static Collection<TestFunction> utilities(){
        var result=new ArrayList<TestFunction>();
        for(String id:List.of("hydro_lift","hydro_brake","hydro_repel","float_item_push","float_item_pull"))
            result.add(new TestFunction("utility_"+id,"academy_function_r5."+id+"_actual_motion_matches_description","academy_function_r5:room",Rotation.NONE,150,0,true,false,1,1,false,h->run(h,id)));
        return result;
    }
    private static BigDecimal pool(net.minecraft.world.entity.LivingEntity e){return new BigDecimal(HighPowerCombat.healthText(e)).add(HighPowerCombat.shield(e));}
    private static void run(GameTestHelper h,String id){
        var f=new SkillFunctionR5RuntimeTests.Fixture(h,id.startsWith("hydro_")?"hydro_hand":"float_dial",id,1);
        if(id.startsWith("hydro_")){
            f.p.setNoGravity(false);
            for(int x=-1;x<=1;x++)for(int y=0;y<3;y++)for(int z=-1;z<=1;z++)f.block(f.at.add(x,y,z),Blocks.WATER);
            var target=id.equals("hydro_repel")?f.target(0,4,true):f.p;
            if(target!=f.p){target.setNoGravity(false);for(int y=0;y<3;y++)f.block(f.at.add(0,y,4),Blocks.WATER);f.aim(target.getBoundingBox().getCenter());}
            target.setDeltaMovement(.4,-.3,.2);target.baseTick();f.p.baseTick();
            h.assertTrue(target.isInWaterOrBubble(),"actual submerged target");
            Vec3 old=target.getDeltaMovement();var hp=pool(target);double cp=HighPowerResources.cp(f.p);
            h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("slot_press","0")),"normal mapped water utility cast");
            Vec3 expected=switch(id){
                case "hydro_lift"->new Vec3(old.x*.5,SkillTuning.number(id,"vertical_speed"),old.z*.5);
                case "hydro_brake"->new Vec3(old.x*SkillTuning.number(id,"horizontal_retention"),Math.max(-.08,Math.min(.08,old.y)),old.z*SkillTuning.number(id,"horizontal_retention"));
                default->new Vec3(0,Math.max(0,old.y),SkillTuning.number(id,"horizontal_speed"));
            };
            h.assertTrue(target.getDeltaMovement().distanceTo(expected)<1e-6,"actual lift/brake/repel velocity, not only a visual or block change");
            h.assertTrue(pool(target).compareTo(hp)==0&&HighPowerResources.cp(f.p)<cp,"utility causes no direct damage and pays the finite cost");
            com.mojang.logging.LogUtils.getLogger().info("FUNCTION_R5_UTILITY skill={} actualVelocity={} expected={} noDamage=true",id,target.getDeltaMovement(),expected);h.succeed();return;
        }
        var holder=new ItemEntity[1];double[] before={0};
        var state=AbilityStorage.get(f.p);if(id.equals("float_item_pull"))h.assertTrue(state.presets.edit(0,1,"float_item_lift",state::learned),"bind real prerequisite before the lease");
        h.runAfterDelay(65,()->{
            var item=net.neoforged.neoforge.common.CommonHooks.onPlayerTossEvent(f.p,new ItemStack(Items.COBBLESTONE),false);
            h.assertTrue(item!=null,"native Q toss records ownership");f.entities.add(item);holder[0]=item;item.setPickUpDelay(400);
            item.setPos(f.p.getEyePosition().add(0,-.1,3));item.setDeltaMovement(Vec3.ZERO);f.aim(item.getBoundingBox().getCenter());
            double cp=HighPowerResources.cp(f.p);
            h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("slot_press",id.equals("float_item_pull")?"1":"0")),"real owned-item action accepted");
            h.assertTrue(HighPowerResources.cp(f.p)<cp,"finite item action cost");
            if(id.equals("float_item_push")){
                Vec3 velocity=item.getDeltaMovement();h.assertTrue(Math.abs(velocity.z-SkillTuning.number(id,"horizontal_speed"))<1e-6&&Math.abs(velocity.y-.08)<1e-6,"real outward item impulse");
                h.assertTrue(item.getItem().getCount()==1,"no item creation or duplication");h.succeed();
            }
        });
        if(id.equals("float_item_pull")){
            h.runAfterDelay(67,()->{var item=holder[0];f.aim(item.getBoundingBox().getCenter());before[0]=item.position().subtract(f.p.position()).multiply(1,0,1).length();
                h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("slot_press","0")),"real pull changes the owned active item's mode");});
            h.runAfterDelay(78,()->{var item=holder[0];double after=item.position().subtract(f.p.position()).multiply(1,0,1).length();
                h.assertTrue(after<before[0]-.1&&item.getItem().getCount()==1&&!item.isRemoved(),"owned item really moves closer without automatic pickup or duplication");
                com.mojang.logging.LogUtils.getLogger().info("FUNCTION_R5_UTILITY skill={} beforeDistance={} afterDistance={} actualVelocity={}",id,before[0],after,item.getDeltaMovement());h.succeed();});
        }
    }
}
