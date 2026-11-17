package cn.academy.biohum.client;

import cn.academy.biohum.BioHum;
import cn.academy.biohum.HumNpc;
import cn.academy.biohum.Skills;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Held media use the same bone transform as equipment. Inventory transfer stays on the server. */
final class PresentationProps {
    private static final Map<Item,ItemStack> MEDIA=new HashMap<>();

    static void draw(HumNpc npc,NpcModel model,ItemInHandRenderer renderer,PoseStack stack,
                     MultiBufferSource buffers,int light,float partial){
        var skill=Skills.get(npc.technique());
        if(skill==null||npc.signatureAge()+partial>skill.windup()+Math.min(8,skill.recovery())
                ||npc.signatureAge()+partial<2||npc.abortAge()>=4)return;
        Item item=switch(skill.id()){
            case "mental_stop","mental_retreat","mental_assist","mental_memory" -> BioHum.MENTAL_REMOTE.get();
            case "enshuu_guidance","ransuu_burst","judgment_report",
                 "network_triage","network_triage_care","tabigake_survey","seria_briefing",
                 "sister_signal","rensa_scan" -> BioHum.HAND_TERMINAL.get();
            case "analysis","spell_intercept","sheol_fear","orsola_reading","komoe_lesson",
                 "rimea_intelligence" -> Items.BOOK;
            case "shorthand_cast","shorthand_ice","shorthand_flare","shorthand_escape",
                 "onmyo_seal","immortal_talisman","cross_charm" -> Items.PAPER;
            case "aim_track","aim_reacquire" -> BioHum.CRYSTAL.get();
            case "frog_doctor_care","alchemist_rescue","yuiitsu_protocol" -> Items.GLASS_BOTTLE;
            case "coin_ladder" -> Items.GOLD_NUGGET;
            case "dvergr_forge" -> Items.IRON_INGOT;
            default -> null;
        };
        if(item==null)return;
        HumanoidArm arm=switch(skill.id()){
            case "analysis","spell_intercept","sheol_fear","orsola_reading","komoe_lesson","rimea_intelligence" -> HumanoidArm.LEFT;
            default -> HumanoidArm.RIGHT;
        };
        if(!(arm==npc.getMainArm()?npc.getMainHandItem():npc.getOffhandItem()).isEmpty())return;
        stack.pushPose();
        model.translateToHand(arm,stack);
        stack.mulPose(Axis.XP.rotationDegrees(-90));
        stack.mulPose(Axis.YP.rotationDegrees(180));
        boolean left=arm==HumanoidArm.LEFT;
        stack.translate((left?-1:1)/16f,.125,-.625);
        renderer.renderItem(npc,MEDIA.computeIfAbsent(item,ItemStack::new),
                left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,left,stack,buffers,light);
        stack.popPose();
    }

    private PresentationProps(){}
}
