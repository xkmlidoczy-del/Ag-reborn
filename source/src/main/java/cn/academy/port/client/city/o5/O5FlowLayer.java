package cn.academy.port.client.city.o5;

import cn.academy.port.city.defense.armor.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Static armor fallback retains the original UV flow; articulated operatives use FullBodyRig. */
public final class O5FlowLayer extends GeoRenderLayer<DefenseArmorItem> {
    private final SetArmorRenderer armor;
    O5FlowLayer(SetArmorRenderer renderer){super(renderer);armor=renderer;}
    @Override public void render(PoseStack ps,DefenseArmorItem item,BakedGeoModel model,RenderType source,MultiBufferSource buffers,VertexConsumer original,float partial,int light,int overlay){
        if(!item.isO5()||!(armor.getCurrentEntity() instanceof net.minecraft.world.entity.LivingEntity entity)||!DefenseArmor.full(entity,true))return;
        int phase=Math.floorMod((int)((entity.level().getGameTime()+partial)*.22),8);
        var type=RenderType.entityTranslucent(ResourceLocation.fromNamespaceAndPath("academy","textures/item/armor/black_gold_flow_"+phase+".png"));
        getRenderer().reRender(model,ps,buffers,item,type,buffers.getBuffer(type),partial,0xF000F0,overlay,0x52FFFFFF);
    }
}
