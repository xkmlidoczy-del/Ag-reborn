/* AcademyCraft1.0.7 renderInventory=false, renderEntityItem=true native context routing. GPLv3. */
package cn.academy.port.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
public final class TerminalInstallerBakedModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel physical;
    public TerminalInstallerBakedModel(BakedModel icon){super(icon);physical=new BakedModelWrapper<>(icon){
        @Override public boolean isCustomRenderer(){return true;}
        @Override public boolean isGui3d(){return true;}
        @Override public boolean usesBlockLight(){return true;}
        @Override public ItemTransforms getTransforms(){return ItemTransforms.NO_TRANSFORMS;}
    };}
    @Override public BakedModel applyTransform(ItemDisplayContext context,PoseStack pose,boolean left){return context==ItemDisplayContext.GUI?originalModel.applyTransform(context,pose,left):physical;}
    @Override public ItemTransforms getTransforms(){return ItemTransforms.NO_TRANSFORMS;}
    @Override public boolean isGui3d(){return true;}
}
