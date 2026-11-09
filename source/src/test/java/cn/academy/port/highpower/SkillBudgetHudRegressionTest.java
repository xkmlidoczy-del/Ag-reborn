package cn.academy.port.highpower;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Real read-only model, lease, optional packet codec and renderer integration checks. */
public final class SkillBudgetHudRegressionTest {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void denied(Runnable action) { boolean failed = false; try { action.run(); } catch (IllegalArgumentException expected) { failed = true; } check(failed, "invalid resource display rejected"); }
    private static HighPowerRecoveryHudModel.Identity id(UUID owner, int entity, String dimension, long epoch, long sequence) {
        return new HighPowerRecoveryHudModel.Identity(owner, entity, dimension, epoch, sequence);
    }
    public static void main(String[] args) throws Exception {
        for (String category : List.of("electromaster", "meltdowner", "teleporter", "vecmanip")) {
            var value = new SkillBudgetHudModel.Values(category, 90, 100, 200, 7);
            check(value.available() + value.held() <= value.total() && value.total() <= value.maximum(), "available and held CP cannot exceed actual stored budget");
            check(value.reserved() && value.reservedText().equals("10") && value.heldText().equals("7"), "display distinguishes all reserved CP from the control hold subset");
            check(value.availableText().equals("90") && value.totalText().equals("100") && value.maximumText().equals("200"), "read-only values retain actual pool meaning");
            var packet = new SkillBudgetHudPayload.Snapshot(id(UUID.randomUUID(), 2, "minecraft:overworld", 1, 1), value);
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
            try { SkillBudgetHudPayload.Snapshot.CODEC.encode(buffer, packet); check(packet.equals(SkillBudgetHudPayload.Snapshot.CODEC.decode(buffer)) && !buffer.isReadable(), "budget channel roundtrip for " + category); }
            finally { buffer.release(); }
        }
        var clear = new SkillBudgetHudPayload.Snapshot(id(UUID.randomUUID(), 2, "minecraft:overworld", 1, 1), null);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try { SkillBudgetHudPayload.Snapshot.CODEC.encode(buffer, clear); check(clear.equals(SkillBudgetHudPayload.Snapshot.CODEC.decode(buffer)) && !buffer.isReadable(), "explicit unavailable snapshot clears the displayed pool"); }
        finally { buffer.release(); }
        for (double bad : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE, 1e21}) {
            denied(() -> new SkillBudgetHudModel.Values("teleporter", bad, 100, 200, 0));
            denied(() -> new SkillBudgetHudModel.Values("teleporter", 0, bad, 200, 0));
            denied(() -> new SkillBudgetHudModel.Values("teleporter", 0, 0, 200, bad));
        }
        denied(() -> new SkillBudgetHudModel.Values("foreign", 0, 0, 200, 0));
        denied(() -> new SkillBudgetHudModel.Values("teleporter", 0, 0, 0, 0));
        denied(() -> new SkillBudgetHudModel.Values("teleporter", 101, 100, 200, 0));
        denied(() -> new SkillBudgetHudModel.Values("teleporter", 90, 100, 200, 11));
        denied(() -> new SkillBudgetHudModel.Values("teleporter", 90, 100, 99, 0));
        double roundedAvailable = 34309.037550413086, roundedTotal = 36963.265422287725, roundedHeld = 2654.2278718746425;
        double displayed = SkillBudgetHudModel.displayAvailable(roundedAvailable, roundedTotal, roundedHeld);
        check(displayed <= roundedAvailable && displayed + roundedHeld <= roundedTotal, "one-ULP display rounding cannot overstate spendable plus held CP");
        for (double[] roundingCase : new double[][] {
                {.0001891319715761321, .00018773785205661422, .00000139411951951788},
                {.0003037318590937771, .0002553923149851901, .000048339544108587}}) {
            double safe = SkillBudgetHudModel.displayAvailable(roundingCase[2], roundingCase[0], roundingCase[1]);
            check(safe <= roundingCase[2] && safe + roundingCase[1] <= roundingCase[0], "small available ULP cannot leave a one-total-ULP excess hidden");
            new SkillBudgetHudModel.Values("teleporter", safe, roundingCase[0], 200, roundingCase[1]);
        }
        var random = new java.util.Random(1022);
        for (int i = 0; i < 10000; i++) {
            double total = Math.pow(10, -5 + random.nextDouble() * 10);
            double held = total * (i % 2 == 0 ? random.nextDouble() : 1 - Math.pow(10, -16 + random.nextDouble() * 16));
            double available = java.math.BigDecimal.valueOf(total).subtract(java.math.BigDecimal.valueOf(held)).doubleValue();
            double safe = SkillBudgetHudModel.displayAvailable(available, total, held);
            check(safe <= available && safe + held <= total, "decimal hold availability always remains visible within actual budget");
            new SkillBudgetHudModel.Values("teleporter", safe, total, 100000, held);
        }
        check(SkillBudgetHudModel.displayAvailable(90, 100, 7) == 90 && SkillBudgetHudModel.displayAvailable(90, 100, 11) == 90,
                "ordinary values remain exact and invalid excess is not silently repaired");
        var session = new SkillBudgetHudModel.Session(); var owner = UUID.randomUUID();
        Object connection = new Object(), world = new Object(), player = new Object();
        var value = new SkillBudgetHudModel.Values("teleporter", 90, 100, 200, 7);
        check(!session.accept(id(owner, 2, "minecraft:overworld", 1, 1), value, owner, 2, "minecraft:overworld"), "no value before live identity is bound");
        session.synchronize(connection, world, player);
        check(!session.accept(id(UUID.randomUUID(), 2, "minecraft:overworld", 1, 1), value, owner, 2, "minecraft:overworld"), "foreign owner refused");
        check(!session.accept(id(owner, 3, "minecraft:overworld", 1, 1), value, owner, 2, "minecraft:overworld"), "foreign entity refused");
        check(!session.accept(id(owner, 2, "minecraft:the_nether", 1, 1), value, owner, 2, "minecraft:overworld"), "foreign dimension refused");
        check(session.accept(id(owner, 2, "minecraft:overworld", 1, 1), value, owner, 2, "minecraft:overworld"), "fresh owned snapshot accepted");
        check(session.current("teleporter").equals(value) && session.current("vecmanip") == null, "wrong-category values never show through a switch");
        check(!session.accept(id(owner, 2, "minecraft:overworld", 1, 1), value, owner, 2, "minecraft:overworld"), "same-sequence replay refused");
        for (int i = 0; i < 100; i++) session.tick(true);
        check(session.current("teleporter") != null, "a local pause does not expire a valid snapshot");
        for (int i = 0; i < SkillBudgetHudModel.STALE_TICKS; i++) session.tick(false);
        check(session.current("teleporter") == null, "expired values disappear");
        check(session.accept(id(owner, 2, "minecraft:overworld", 1, 2), value, owner, 2, "minecraft:overworld"), "later snapshot renews lease");
        session.synchronize(connection, new Object(), player);
        check(session.current("teleporter") == null && !session.accept(id(owner, 2, "minecraft:overworld", 1, 99), value, owner, 2, "minecraft:overworld"), "world transition retires the prior epoch even if ids are reused");
        check(session.accept(id(owner, 2, "minecraft:the_nether", 2, 1), value, owner, 2, "minecraft:the_nether"), "new world epoch accepted");
        check(session.accept(id(owner, 2, "minecraft:the_nether", 2, 2), null, owner, 2, "minecraft:the_nether") && session.current("teleporter") == null, "explicit unavailable packet removes the pool immediately");
        String source = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/SkillBudgetHudPayload.java"));
        check(source.contains("playToClient(") && !source.contains("playToServer("), "channel has no client write ingress");
        for (String forbidden : List.of(".putDouble(", "initializeBudget(", "trySpend(", ".close(")) check(!source.contains(forbidden), "snapshot cannot mutate balances or release holds: " + forbidden);
        String renderer = Files.readString(Path.of("src/main/java/cn/academy/port/client/HighPowerRecoveryHud.java"));
        check(renderer.contains("BUDGET_SESSION.current(values.category())") && renderer.contains("BUDGET_SESSION.tick(mc.isPaused())"), "renderer uses only live category-scoped leased values");
        check(renderer.contains("skillBudget.availableText()") && renderer.contains("skillBudget.reservedText()") && renderer.contains("skill_budget_cp"), "readable HUD uses spendable CP and names reserved amounts");
        check(renderer.contains("HighPowerRecoveryHudModel.presentation(width, height, cp, keys, notification, chatBox, preferred)")
                && renderer.contains("if (chosen.nativeText() && !exact)") && renderer.contains("HighPowerRecoveryHudModel.displayRows(values, skillBudget != null)"), "heart replacement and drawing share one selected native-or-compact presentation");
        var preset = new cn.academy.port.core.AbilityProgress();
        preset.category = "vecmanip"; preset.level = 5; preset.experience.put("storm_wing", 0d);
        preset.presets.replace(0, new String[] {"", "", "storm_wing", ""}, id -> true);
        check(!SkillBudgetHudModel.currentPresetUsesBudget(preset), "storm-only classic preset never advertises the unrelated expansion wallet");
        preset.experience.put(HighPowerSkills.DEFLECTION_STRIKE, 0d);
        preset.presets.replace(0, new String[] {HighPowerSkills.DEFLECTION_STRIKE, "", "storm_wing", ""}, id -> true);
        check(SkillBudgetHudModel.currentPresetUsesBudget(preset), "mixed classic and usable expansion preset displays its independent wallet");
        preset.experience.remove(HighPowerSkills.DEFLECTION_STRIKE);
        check(!SkillBudgetHudModel.currentPresetUsesBudget(preset), "retained but unlearned raw mapping cannot expose an unused budget");
        preset.experience.put(HighPowerSkills.DEFLECTION_STRIKE, 0d);
        preset.presets.switchTo(1);
        check(!SkillBudgetHudModel.currentPresetUsesBudget(preset), "another preset's expansion skill never makes this preset use its wallet");
        var grounding = new cn.academy.port.core.AbilityProgress();
        grounding.category = "electromaster"; grounding.level = 5;
        grounding.experience.put(HighPowerSkills.GROUNDING_RETURN, 0d);
        grounding.presets.replace(0, new String[] {HighPowerSkills.GROUNDING_RETURN, "", "", ""}, id -> true);
        check(!SkillBudgetHudModel.currentPresetUsesBudget(grounding), "audited zero-CP grounding-only preset does not advertise an unused wallet");
        grounding.experience.put(HighPowerSkills.FORKED_ARC, 0d);
        grounding.presets.replace(0, new String[] {HighPowerSkills.GROUNDING_RETURN, HighPowerSkills.FORKED_ARC, "", ""}, id -> true);
        check(SkillBudgetHudModel.currentPresetUsesBudget(grounding), "grounding mixed with a real paid usable skill retains its wallet");
        grounding.experience.remove(HighPowerSkills.FORKED_ARC);
        check(!SkillBudgetHudModel.currentPresetUsesBudget(grounding), "unlearned paid mapping cannot make a zero-CP grounding preset expose a wallet");
        var idle = new HighPowerRecoveryHudModel.Values("100", "100", "8", "20", "full", "0", 0, 0, 0, true);
        var classicRows = HighPowerRecoveryHudModel.displayRows(idle, false);
        check(classicRows.budgetY() < 0 && classicRows.rows().height() < 30, "classic-only idle surface paints exactly HP and shield without a CP row or blank footer");
        check(HighPowerRecoveryHudModel.displayRows(idle, true).budgetY() >= 0, "mixed preset adds its real spendable CP row");
        check(renderer.contains("var skillBudget = ordinaryBudget(values)") && renderer.contains("return presetUsesBudget() ? budget(values) : null"), "ordinary render and native plan share the current usable-preset predicate");
        check(renderer.substring(renderer.indexOf("private static void detail(")).contains("var skillBudget = budget(values)"), "ALT retains every fresh exact wallet regardless of preset mapping");
        layoutCounterexamples();
        System.out.println("PASS " + checks + " skill CP budget, ownership, stale lease, codec and read-only HUD checks");
    }
    private static void layoutCounterexamples() {
        for (String category : List.of("electromaster", "meltdowner", "teleporter", "vecmanip")) {
            boolean em = category.equals("electromaster"), md = category.equals("meltdowner");
            for (String status : List.of("full", "paused", "saturated")) {
                var values = new HighPowerRecoveryHudModel.Values("90", "100", "8", "20", status,
                        status.equals("saturated") ? "2" : "0", status.equals("saturated") ? 10 : 0,
                        status.equals("saturated") ? 4 : 0, status.equals("paused") ? 20 : 0,
                        true, "0", em ? "20" : "0", false, category, "0", md ? "10" : "0", false);
                for (boolean show : new boolean[] {false, true}) {
                    var painted = HighPowerRecoveryHudModel.displayRows(values, show);
                    check(painted.rows().height() <= 56 && painted.rows().healthBarY() < painted.rows().height()
                            && painted.rows().shieldBarY() < painted.rows().height(), "actual classic/mixed painted content stays within 56px");
                    check((painted.budgetY() >= 0) == show, "budget row tracks current preset usefulness only");
                    if (HighPowerRecoveryHudModel.ordinaryStatusVisible(values)) check(painted.rows().statusY() >= 0
                            && painted.rows().statusY() + 8 <= painted.rows().height(), "blocked recovery reason retains its complete row");
                }
                var compact = HighPowerRecoveryHudModel.budgetRows(values);
                var rows = compact.rows();
                check(rows.height() <= 56 && !rows.showRate(), "budget replaces duplicate header/rate rather than growing the compact panel");
                check(rows.healthBarY() + 2 <= rows.shieldY() && rows.shieldBarY() + 2 <= compact.budgetY()
                        && compact.budgetY() + 9 <= rows.statusY() && rows.statusY() + 9 <= rows.height(),
                        "HP, shield, spendable CP and actual recovery reason have distinct readable rows");
                if (rows.showReserve()) check(rows.reserveBarY() + 2 <= compact.budgetY(), "real learned reserve also retains its own row");
            }
        }
        for (String language : List.of("en_us", "zh_cn")) {
            var numbers = HighPowerRecoveryHudModel.hudNumberLocale(language);
            var sections = new java.util.ArrayList<HighPowerRecoveryHudModel.NativeSection>();
            sections.add(new HighPowerRecoveryHudModel.NativeSection("ACA / MD " + (language.equals("zh_cn") ? "能力状态" : "Ability status") + " [ALT]", null, 3));
            sections.add(new HighPowerRecoveryHudModel.NativeSection("HP", HighPowerRecoveryHudModel.pool("90000000000000000000", "100000000000000000000", numbers).text(), 0));
            sections.add(new HighPowerRecoveryHudModel.NativeSection(language.equals("zh_cn") ? "等离子热壳 霸体" : "Plasma shell SUPER ARMOR", HighPowerRecoveryHudModel.pool("8", "20", numbers).text(), 1));
            sections.add(new HighPowerRecoveryHudModel.NativeSection(language.equals("zh_cn") ? "消散储备" : "Curtain reserve", "0 / 10", 2));
            sections.add(new HighPowerRecoveryHudModel.NativeSection(language.equals("zh_cn") ? "自然恢复 +2 HP/秒" : "Natural +2 HP/s next 4t", null, 5));
            sections.add(new HighPowerRecoveryHudModel.NativeSection(language.equals("zh_cn") ? "技能储备CP" : "Skill reserve CP", "90 / 200", 6));
            sections.add(new HighPowerRecoveryHudModel.NativeSection(language.equals("zh_cn") ? "饱和恢复" : "Saturated recovery", null, 4));
            sections.add(new HighPowerRecoveryHudModel.NativeSection(language.equals("zh_cn") ? "过载突破：下次施放+30%" : "Overload breakthrough: next cast +30%", null, 6));
            for (int[] viewport : new int[][] {{320, 240}, {427, 240}, {640, 360}, {960, 540}}) {
                int width = viewport[0], height = viewport[1];
                var cp = new HighPowerRecoveryHudModel.Box(width - 205, 12, 194, 40);
                var keys = new HighPowerRecoveryHudModel.Box(width - 188, (int)((height - 48.3) / 2 + 30), 185, 88);
                var notification = new HighPowerRecoveryHudModel.Box(0, 15, 130, 43);
                var chat = new HighPowerRecoveryHudModel.Box(0, height - 130, Math.min(width, 336), 102);
                java.util.function.ToIntFunction<String> measure = text -> text.codePoints().map(c -> c < 128 ? 6 : 9).sum();
                var nativePlan = HighPowerRecoveryHudModel.nativePlan(sections, width, 9, (text, available) -> {
                    var result = new java.util.ArrayList<String>(); var line = new StringBuilder(); int used = 0;
                    for (int point : text.codePoints().toArray()) { int size = point < 128 ? 6 : 9;
                        if (used + size > available && !line.isEmpty()) { result.add(line.toString()); line.setLength(0); used = 0; }
                        line.appendCodePoint(point); used += size;
                    }
                    if (!line.isEmpty()) result.add(line.toString()); return result;
                }, measure);
                var polished = List.of(
                        new HighPowerRecoveryHudModel.NativeSection("HP", HighPowerRecoveryHudModel.pool("90000000000000000000", "100000000000000000000", numbers).text(), 0),
                        new HighPowerRecoveryHudModel.NativeSection(language.equals("zh_cn") ? "护盾 · 霸体" : "Shield · ARMOR", "8 / 20", 1),
                        new HighPowerRecoveryHudModel.NativeSection(language.equals("zh_cn") ? "消散储备 储存" : "Curtain IDLE", "0 / 10", 2),
                        new HighPowerRecoveryHudModel.NativeSection(language.equals("zh_cn") ? "储备CP" : "Reserve CP", "90 / 200", 6));
                var polishedPlan = HighPowerRecoveryHudModel.nativePlan(polished, width, 9, (text, available) -> {
                    var result = new java.util.ArrayList<String>(); var line = new StringBuilder(); int used = 0;
                    for (int point : text.codePoints().toArray()) { int size = point < 128 ? 6 : 9;
                        if (used + size > available && !line.isEmpty()) { result.add(line.toString()); line.setLength(0); used = 0; }
                        line.appendCodePoint(point); used += size;
                    }
                    if (!line.isEmpty()) result.add(line.toString()); return result;
                }, measure);
                check(polishedPlan != null, "polished real four-row idle panel retains HP, shield, learned reserve and spendable CP");
                var polishedSelection = HighPowerRecoveryHudModel.presentation(width, height, cp, keys, notification, chat, polishedPlan);
                check(polishedSelection != null && polishedSelection.box().within(width, height)
                        && !polishedSelection.box().intersects(cp) && !polishedSelection.box().intersects(keys)
                        && !polishedSelection.box().intersects(notification) && !polishedSelection.box().intersects(chat),
                        "polished actual native rectangle or full compact fallback stays disjoint at " + width + "x" + height);
                check(nativePlan != null && nativePlan.height() > 56, "real nativePlan produces its full eight-section rectangle " + language);
                var selected = HighPowerRecoveryHudModel.presentation(width, height, cp, keys, notification, chat, nativePlan);
                check(selected != null && selected.box().within(width, height) && !selected.box().intersects(cp)
                        && !selected.box().intersects(keys) && !selected.box().intersects(notification) && !selected.box().intersects(chat),
                        "actual preferred-native-or-compact rectangle stays visible and disjoint at " + width + "x" + height + "/" + language);
                if (width <= 427) check(!selected.nativeText() && selected.box().height() == 56,
                        "crowded 320/427 by 240 view falls back to the complete compact HP/shield/CP panel");
            }
        }
    }

}
