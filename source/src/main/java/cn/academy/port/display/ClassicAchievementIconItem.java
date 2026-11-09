/* AcademyCraft1.0.7 ItemAchievement/DUMMY_ITEM hidden dynamic display equivalent. GPLv3. */
package cn.academy.port.display;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
/** Stable texture resource components replace process-local numeric metadata. Hidden and inert. */
public final class ClassicAchievementIconItem extends Item {
    public static final ResourceLocation NULL_TEXTURE=ResourceLocation.fromNamespaceAndPath("academy","textures/null.png");
    private static final String KEY="academy_achievement_texture";
    public ClassicAchievementIconItem(Properties properties){super(properties);}
    public static ItemStack getStack(String texture){return getStack(ResourceLocation.fromNamespaceAndPath("academy","textures/"+texture+(texture.endsWith(".png")?"":".png")));}
    public static ItemStack getStack(ResourceLocation texture){var stack=new ItemStack(ClassicDisplayItems.ACHIEVEMENT_ICON.get());var tag=new CompoundTag();tag.putString(KEY,texture.toString());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return stack;}
    public static ResourceLocation texture(ItemStack stack){if(stack==null||stack.isEmpty()||!(stack.getItem() instanceof ClassicAchievementIconItem))return NULL_TEXTURE;var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();String value=data.getString(KEY);if(value.isEmpty())return NULL_TEXTURE;var texture=ResourceLocation.tryParse(value);return texture==null?NULL_TEXTURE:texture;}
}
