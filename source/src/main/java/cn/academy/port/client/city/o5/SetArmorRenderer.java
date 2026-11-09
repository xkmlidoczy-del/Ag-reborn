package cn.academy.port.client.city.o5;


import cn.academy.port.city.defense.armor.DefenseArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** 模型:  assets/mcarmor/geo/item/armor/<set>.geo.json
 *  贴图:  assets/mcarmor/textures/item/armor/<set>.png
 *  发光:  assets/mcarmor/textures/item/armor/<set>_glowmask.png (AutoGlowingGeoLayer，不透明像素全亮) */
public class SetArmorRenderer extends GeoArmorRenderer<DefenseArmorItem> {
    private final boolean translucentVisor;
    public SetArmorRenderer(String setName) {
        super(new DefaultedItemGeoModel<>(ResourceLocation.fromNamespaceAndPath("academy", "armor/" + setName)));
        this.translucentVisor = setName.equals("special_police");
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        if (setName.equals("black_gold")) addRenderLayer(new O5FlowLayer(this));
    }

    @Override
    public RenderType getRenderType(DefenseArmorItem item, ResourceLocation texture,
                                   MultiBufferSource buffers, float partialTick) {
        return translucentVisor ? RenderType.entityTranslucent(texture)
                : super.getRenderType(item, texture, buffers, partialTick);
    }
}
