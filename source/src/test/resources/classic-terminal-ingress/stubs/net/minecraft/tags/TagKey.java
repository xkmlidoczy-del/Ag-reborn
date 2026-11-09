package net.minecraft.tags;
import net.minecraft.resources.ResourceLocation;
public record TagKey(String registry, ResourceLocation location) {
 public static TagKey create(String registry, ResourceLocation location) { return new TagKey(registry,location); }
}
