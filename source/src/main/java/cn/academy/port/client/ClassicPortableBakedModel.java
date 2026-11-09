package cn.academy.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import java.util.IdentityHashMap;
import java.util.Map;

/** Immutable context routing: retain original2D charge overrides in GUI, route physical contexts to source OBJ. */
public final class ClassicPortableBakedModel extends BakedModelWrapper<BakedModel> {
    private final Map<BakedModel,ClassicPortableBakedModel> variants;
    private final ItemOverrides overrides;
    private final BakedModel physical;
    public ClassicPortableBakedModel(BakedModel original){this(original,new IdentityHashMap<>());}
    private ClassicPortableBakedModel(BakedModel original,Map<BakedModel,ClassicPortableBakedModel> variants){
        super(original);this.variants=variants;variants.put(original,this);
        physical=new BakedModelWrapper<>(original){
            @Override public boolean isCustomRenderer(){return true;}
            @Override public boolean isGui3d(){return true;}
            @Override public boolean usesBlockLight(){return true;}
            @Override public ItemTransforms getTransforms(){return ItemTransforms.NO_TRANSFORMS;}
        };
        overrides=new ItemOverrides(){
            @Override public BakedModel resolve(BakedModel model,ItemStack stack,ClientLevel level,LivingEntity entity,int seed){
                BakedModel resolved=originalModel.getOverrides().resolve(originalModel,stack,level,entity,seed);
                if(resolved==null)return null;
                var found=ClassicPortableBakedModel.this.variants.get(resolved);
                return found==null?new ClassicPortableBakedModel(resolved,ClassicPortableBakedModel.this.variants):found;
            }
        };
    }
    @Override public ItemOverrides getOverrides(){return overrides;}
    @Override public BakedModel applyTransform(ItemDisplayContext context,PoseStack poses,boolean leftHand){
        // Do not mutate a shared model with a last-rendered context; GUI/ground can render in either order.
        return context==ItemDisplayContext.GUI?originalModel.applyTransform(context,poses,leftHand):physical;
    }
    // The dropped-item helper reads this metadata before applyTransform. A flat icon's ground .5 scale
    // must not change the source custom model's native bob height when our actual OBJ scale is1.
    @Override public ItemTransforms getTransforms(){return ItemTransforms.NO_TRANSFORMS;}
    @Override public boolean isGui3d(){return true;}
}
