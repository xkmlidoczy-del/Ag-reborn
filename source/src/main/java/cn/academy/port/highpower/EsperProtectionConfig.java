package cn.academy.port.highpower;

import cn.academy.port.highpower.core.EsperDamageRules;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owner switches for the esper guard (see {@link EsperDamageRules}). Defaults protect; nothing here ever limits damage an esper deals or receives as damage. */
public final class EsperProtectionConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER.comment("Master switch for the esper guard.").define("enabled", true);
    public static final ModConfigSpec.BooleanValue BLOCK_KILL_COMMAND = BUILDER.comment("Refuse the kill command's erasing damage to enrolled espers. Set false if operators need /kill to work on espers.").define("blockKillCommand", true);
    public static final ModConfigSpec.BooleanValue BLOCK_FORCED_DEATH = BUILDER.comment("Refuse a death that did not come out of the exact ledger (forced native health 0, direct die() calls from other code) and restore the esper's native health.").define("blockForcedDeath", true);
    public static final ModConfigSpec.DoubleValue ENVIRONMENT_SHARE = BUILDER.comment("World damage (fire, lava, drowning, starvation, falling, void...) is multiplied by this share of the esper level's damage unit D, so it stays a real threat at every level. It never depends on current health, so it is not percentage damage. 0 disables the scaling.").defineInRange("environmentShare", 0.001, 0.0, 0.1);
    public static final ModConfigSpec.BooleanValue CATEGORY_SHIELDS = BUILDER.comment("Category shields: an esper's base shield takes ordinary hits before health, grants super armor (no knockback) while any of it is left, and carries its category's trait (electromaster arc counter and lightning recharge, meltdowner burning shell that vaporises projectiles, teleporter phase shift, vector reflection of projectiles and attackers). Health only takes damage once the shield is empty.").define("categoryShields", true);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private EsperProtectionConfig() {}

    public static boolean categoryShields() {
        try { return !SPEC.isLoaded() || CATEGORY_SHIELDS.get(); } catch (RuntimeException unreadable) { return true; }
    }

    public static EsperDamageRules.Policy policy() {
        try {
            if (!SPEC.isLoaded()) return EsperDamageRules.Policy.DEFAULT;
            return new EsperDamageRules.Policy(ENABLED.get(), BLOCK_KILL_COMMAND.get(), BLOCK_FORCED_DEATH.get(), ENVIRONMENT_SHARE.get());
        } catch (RuntimeException unreadable) { return EsperDamageRules.Policy.DEFAULT; }
    }
}
