package cn.academy.port.city.defense.armor;

import cn.academy.port.client.city.o5.SetArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/** 一套装甲的四个部位共用同一个 geo 模型（assets/mcarmor/geo/item/armor/<setName>.geo.json），
 *  GeckoLib 会按槽位只显示对应的骨骼（armorHead / armorBody / ...）。 */
public class DefenseArmorItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String setName;
    public boolean isO5() { return setName.equals("black_gold"); }

    public DefenseArmorItem(Holder<ArmorMaterial> material, Type type, Properties props, String setName) {
        super(material, type, props);
        this.setName = setName;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { /* 静态模型，无动画 */ }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return this.cache; }
}
