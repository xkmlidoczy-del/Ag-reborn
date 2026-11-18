package cn.academy.port.client.city.o5;


import cn.academy.port.city.defense.armor.DefenseArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Armor body textures are bound directly by the entity renderer. */
public class SetArmorRenderer extends GeoArmorRenderer<DefenseArmorItem> {
    private final boolean translucentVisor;
    public SetArmorRenderer(String setName) {
        super(new DefaultedItemGeoModel<>(ResourceLocation.fromNamespaceAndPath("academy", "armor/" + setName)) {
            @Override public ResourceLocation getTextureResource(DefenseArmorItem item) {
                return ResourceLocation.fromNamespaceAndPath("academy", "textures/models/armor/" + setName + ".png");
            }
        });
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
