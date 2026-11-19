package cn.aca.infinity;

import net.minecraft.ChatFormatting;

public enum Stone {
    SPACE("space", 0xff3ca8ff, 0xffb8e6ff, 0xff091933, ChatFormatting.AQUA, 50, 100),
    REALITY("reality", 0xffff334b, 0xffffb3bd, 0xff2b0710, ChatFormatting.RED, 60, 120),
    POWER("power", 0xffb448ff, 0xffe8b8ff, 0xff200836, ChatFormatting.LIGHT_PURPLE, 45, 110),
    MIND("mind", 0xffffd63a, 0xfffff4b8, 0xff2e2206, ChatFormatting.YELLOW, 65, 130),
    TIME("time", 0xff2eff8a, 0xffbeffda, 0xff062b17, ChatFormatting.GREEN, 240, 160),
    SOUL("soul", 0xffff9428, 0xffffdfb5, 0xff2e1505, ChatFormatting.GOLD, 35, 200);

    public static final int ALL_MASK = (1 << values().length) - 1;

    public final String id;
    public final int color;
    public final int coreBright;
    public final int darkSheath;
    public final ChatFormatting chatColor;
    public final int primaryCooldown;
    public final int secondaryCooldown;

    Stone(String id, int color, int coreBright, int darkSheath, ChatFormatting chatColor,
          int primaryCooldown, int secondaryCooldown) {
        this.id = id;
        this.color = color;
        this.coreBright = coreBright;
        this.darkSheath = darkSheath;
        this.chatColor = chatColor;
        this.primaryCooldown = primaryCooldown;
        this.secondaryCooldown = secondaryCooldown;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public String itemId() {
        return id + "_stone";
    }

    public String translationKey() {
        return "stone.aca_infinity." + id;
    }

    public String primarySkillKey() {
        return "skill.aca_infinity." + id + ".primary";
    }

    public String secondarySkillKey() {
        return "skill.aca_infinity." + id + ".secondary";
    }

    public String comboKey() {
        return "combo.aca_infinity." + id;
    }

    public static Stone byOrdinal(int index) {
        Stone[] all = values();
        return all[Math.floorMod(index, all.length)];
    }
}
