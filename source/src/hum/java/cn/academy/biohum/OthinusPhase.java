package cn.academy.biohum;

import java.util.Locale;

/** The worlds shown in NT9, plus three scenes from its unnamed sequence. */
public enum OthinusPhase {
    DARK("黑色世界", "othinus_world_dark", 18000, false, 0x8161bf),
    ALPHA("α · 被世界追捕", "othinus_world_alpha", 12850, false, 0xdb643c),
    BETA("β · 被取代的日常", "othinus_world_beta", 11600, false, 0xe6b276),
    BURIAL("无名轮回 · 封闭的葬礼", "othinus_world_loops", 17000, false, 0x9e98bf),
    SEA("无名轮回 · 海上漂流", "othinus_world_loops", 13400, true, 0x569eae),
    SPACE("无名轮回 · 宇宙放逐", "othinus_world_loops", 18000, false, 0x9ca6d7),
    OMEGA("Ω · 幸福的世界", "othinus_world_omega", 4700, false, 0xe7cd8b),
    DUEL("少女位相 · 几千亿", "othinus_world_duel", 18100, false, 0x9072db),
    RETURN("回到原来的世界", "othinus_world_return", 6000, false, 0xe9e2ce);

    public final String title;
    public final String skill;
    public final int time;
    public final boolean rain;
    public final int color;

    OthinusPhase(String title, String skill, int time, boolean rain, int color) {
        this.title = title;
        this.skill = skill;
        this.time = time;
        this.rain = rain;
        this.color = color;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public boolean empty() { return this == DARK || this == SPACE; }

    public static OthinusPhase read(String id) {
        for (var phase : values()) if (phase.id().equals(id)) return phase;
        return null;
    }
}
