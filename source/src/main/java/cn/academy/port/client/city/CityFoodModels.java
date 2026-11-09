package cn.academy.port.client.city;

import cn.academy.port.city.CityCatalog;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/** Keep readable catalogue sprites in inventories and show food with depth in the world. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CityFoodModels {
    private CityFoodModels() {}
    private static ModelResourceLocation physical(String id) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("academy", "item/city_food_3d/" + id));
    }
    private static ModelResourceLocation icon(String id) {
        return ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath("academy", id));
    }
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        for (var good : CityCatalog.GOODS) if (good.tier() != null) event.register(physical(good.id()));
    }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        for (var good : CityCatalog.GOODS) if (good.tier() != null) {
            BakedModel sprite = event.getModels().get(icon(good.id()));
            BakedModel solid = event.getModels().get(physical(good.id()));
            if (sprite != null && solid != null) event.getModels().put(icon(good.id()), new ContextModel(sprite, solid));
        }
    }
    private static final class ContextModel extends BakedModelWrapper<BakedModel> {
        private final BakedModel solid;
        ContextModel(BakedModel sprite, BakedModel solid) { super(sprite); this.solid=solid; }
        @Override public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            return (context == ItemDisplayContext.GUI ? originalModel : solid).applyTransform(context, pose, leftHand);
        }
        @Override public boolean isGui3d() { return true; }
    }
}
