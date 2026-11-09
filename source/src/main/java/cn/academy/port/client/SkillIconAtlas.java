package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Original 32px functional pictograms. Keeps all legacy texture paths intact for historical visual proofs. */
public final class SkillIconAtlas {
    public record Icon(String category, String skill, int level, boolean active, ResourceLocation texture) {}
    public static final List<Icon> ALL = SkillCatalog.REGISTERED.stream()
            .map(s -> new Icon(s.category(), s.id(), s.level(), s.controllable(), location(s.category(), s.id())))
            .toList();
    private SkillIconAtlas() {}
    private static ResourceLocation location(String category, String skill) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/skill_icons_v2/" + category + "/" + skill + ".png");
    }
    /** Valid catalog identity only; callers never derive a resource path from an arbitrary user string. */
    public static ResourceLocation texture(String category, String skill) {
        return ALL.stream().filter(i -> i.category().equals(category) && i.skill().equals(skill)).map(Icon::texture)
                .findFirst().orElse(ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/skill_tree/cat_not_found.png"));
    }
    public static boolean contains(String category, String skill) {
        return ALL.stream().anyMatch(i -> i.category().equals(category) && i.skill().equals(skill));
    }
}
