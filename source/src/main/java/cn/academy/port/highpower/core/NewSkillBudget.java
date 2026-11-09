package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** CP and cooldown (ticks) curves of the post-0.81.2 skills (seven design rows plus V-ADV-01), evaluated at a continuous growth position. */
public final class NewSkillBudget {
    public enum Skill {
        PULSE_SHIELD("30", "30", "30", "10", "2", "120", "120", "120", "40", "10"),
        RAIL_RELAY("40", "40", "40", "12", "3", "100", "100", "100", "30", "6"),
        REVERSE_JET("10", "10", "10", "4", "1", "100", "100", "100", "30", "6"),
        INCIDENT_BYPASS("60", "40", "20", "8", "2", "160", "100", "60", "20", "4"),
        PHASE_CORRIDOR("50", "50", "50", "20", "4", "200", "200", "200", "60", "10"),
        ZENITH_DOMINION("200", "200", "200", "80", "20", "400", "400", "400", "120", "30"),
        IMPULSE_SPLIT("30", "30", "30", "10", "2", "160", "160", "160", "40", "8"),
        /** V-ADV-01: grab base cost (scaled by block count) and the cooldown after landing or throwing. */
        CONSTRUCT("60", "60", "60", "40", "15", "200", "200", "200", "120", "40");

        private final ContinuousHighPowerGrowth.Curve cp, cooldown;
        Skill(String c1, String c2, String c3, String c4, String c5, String d1, String d2, String d3, String d4, String d5) {
            cp = ContinuousHighPowerGrowth.curve(c1, c2, c3, c4, c5);
            cooldown = ContinuousHighPowerGrowth.curve(d1, d2, d3, d4, d5);
        }
    }
    private NewSkillBudget() {}

    public static double cp(Skill skill, BigDecimal position) { return skill.cp.value(ContinuousHighPowerGrowth.requirePosition(position)).doubleValue(); }
    public static int cooldownTicks(Skill skill, BigDecimal position) {
        return Math.max(1, ContinuousHighPowerGrowth.ceilCooldownTicks(skill.cooldown.value(ContinuousHighPowerGrowth.requirePosition(position))));
    }
}
