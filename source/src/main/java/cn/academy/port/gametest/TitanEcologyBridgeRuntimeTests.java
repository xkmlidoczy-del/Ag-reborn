package cn.academy.port.gametest;

import cn.academy.port.AbilityDamage;
import cn.academy.port.titan.TitanBlocks;
import dev.titanecology.AnchorBridge;
import dev.titanecology.EcoSpeciesProfile.Chain;
import dev.titanecology.TitanEcologyMod;
import dev.titanecology.TransformerMobEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("academy_real_attributes")
@PrefixGameTestTemplate(false)
public final class TitanEcologyBridgeRuntimeTests {
    private static final Map<Chain,String> ANCHORS = Map.of(
            Chain.PARASITE, "ossuary_dragon", Chain.FLESH, "lava_rex",
            Chain.FUNGAL, "grove_titan", Chain.TRANSFORMER, "clockwork_rex");

    private TitanEcologyBridgeRuntimeTests() {}

    @GameTest(template = "highpower_empty", batch = "titan_ecology_bridge")
    public static void exposedAcademyTitansActivateOnlyTheirOwnAnchor(GameTestHelper h) {
        var level = h.getLevel();
        var air = h.absolutePos(new BlockPos(4, 40, 4));
        for (var entry : ANCHORS.entrySet()) {
            var titan = TitanBlocks.TYPES.get(entry.getValue()).get().create(level);
            titan.moveTo(Vec3.atBottomCenterOf(air));
            titan.setNoAi(true);
            try {
                for (Chain chain : Chain.values()) {
                    h.assertFalse(AnchorBridge.hasAnchorField(level, air, chain, 2), "Unexpected baseline " + chain);
                }
                h.assertTrue(level.addFreshEntity(titan), "Add ACA titan " + entry.getValue());
                for (Chain chain : Chain.values()) {
                    h.assertTrue(AnchorBridge.hasAnchorField(level, air, chain, 2) == (chain == entry.getKey()),
                            entry.getValue() + " field for " + chain);
                }
            } finally {
                titan.discard();
            }
        }
        for (String id : TitanBlocks.IDS) {
            h.assertTrue(TitanBlocks.TYPES.get(id).get().is(AnchorBridge.NEIGHBOR_TITANS) == !ANCHORS.containsValue(id),
                    "Incorrect neighboring titan tag for " + id);
        }
        h.succeed();
    }

    @GameTest(template = "highpower_empty", batch = "titan_ecology_bridge")
    public static void academyPlayerSkillDebitsAnEcologyResident(GameTestHelper h) {
        var player = new CanonExpansionARuntimeTests.Fixture(h);
        var wolfType = TitanEcologyMod.SPECIES_TYPES.get("te_combiner_vanguard_wolf").get();
        var wolf = h.spawn(wolfType, 6, 3, 4);
        var beetle = h.spawn(TitanEcologyMod.SPECIES_TYPES.get("te_tallow_roller_beetle").get(), 5, 3, 4);
        try {
            wolf.setNoAi(true);
            beetle.setNoAi(true);
            float before = wolf.getHealth();
            float shell = wolf.shellHp();
            h.assertTrue(shell >= 12, "Intact mechanical wolf shell");
            h.assertTrue(AbilityDamage.attack(player.p, "electromaster.arc_gen", wolf, 12.0, true),
                    "Academy skill was rejected");
            h.assertTrue(Math.abs(shell - wolf.shellHp() - 12) < 0.01, "Incorrect shell debit");
            h.assertTrue(Math.abs(before - wolf.getHealth() - 7.8) < 0.01, "Shell should absorb 35% of the hit");
            float beetleHealth = beetle.getHealth();
            h.assertTrue(beetle.shellHp() == 0, "Unshielded ecology target");
            h.assertTrue(AbilityDamage.attack(player.p, "electromaster.arc_gen", beetle, 12.0, true),
                    "Academy skill was rejected by unshielded target");
            h.assertTrue(Math.abs(beetleHealth - beetle.getHealth() - 12) < 0.01, "Incorrect unshielded damage");
            h.succeed();
        } finally {
            wolf.discard();
            beetle.discard();
            player.close();
        }
    }

    @GameTest(template = "highpower_empty", batch = "titan_ecology_bridge")
    public static void externalArmorStacksWithNativeTransformation(GameTestHelper h) {
        var type = TitanEcologyMod.SPECIES_TYPES.get("te_foundry_crawler_carrier").get();
        var carrier = (TransformerMobEntity) h.spawn(type, 4, 3, 4);
        try {
            carrier.setNoAi(true);
            var armor = carrier.getAttribute(Attributes.ARMOR);
            armor.addTransientModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath("academy", "test_external_armor"),
                    5.0, AttributeModifier.Operation.ADD_VALUE));
            h.assertTrue(armor.getValue() == 27.0, "Base armor plus external bonus");
            h.assertTrue(carrier.requestMorph(true), "Enter alternate form");
            h.assertTrue(armor.getValue() == 35.0, "External armor should stack on the native 30-point form");
            h.succeed();
        } finally {
            carrier.discard();
        }
    }
}
