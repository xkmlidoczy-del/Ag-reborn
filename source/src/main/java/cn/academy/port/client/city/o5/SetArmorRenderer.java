package cn.academy.port.client.city.o5;


import cn.academy.port.city.defense.armor.DefenseArmorItem;
import java.util.function.Consumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
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

    public static void provide(String setName, Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<?> renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(
                    T livingEntity, ItemStack stack, EquipmentSlot slot, HumanoidModel<T> original) {
                if (this.renderer == null) this.renderer = new SetArmorRenderer(setName);
                this.renderer.prepForRender(livingEntity, stack, slot, original);
                return this.renderer;
            }
        });
    }

    @Override
    public RenderType getRenderType(DefenseArmorItem item, ResourceLocation texture,
                                   MultiBufferSource buffers, float partialTick) {
        return translucentVisor ? RenderType.entityTranslucent(texture)
                : super.getRenderType(item, texture, buffers, partialTick);
    }
}
