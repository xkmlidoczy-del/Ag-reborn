/* AcademyCraft OreDictionary-to-modern-tags compatibility boundary. See NOTICE. */
package cn.academy.port.skill;

/** Pure semantic tag mapping, intentionally excluding worldgen replacement/biome tags. */
public final class MineOreTagRules {
    private MineOreTagRules() {}
    public static boolean semanticOrePath(String path) {
        return path != null && (path.equals("ores") || path.startsWith("ores/")
                || path.equals("ores_in_ground") || path.startsWith("ores_in_ground/")
                || path.endsWith("_ores"));
    }
}
