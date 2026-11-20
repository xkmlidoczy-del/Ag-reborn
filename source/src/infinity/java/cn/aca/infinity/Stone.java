package cn.aca.infinity;

import net.minecraft.ChatFormatting;

public enum Stone {
    SPACE("space", 0xff3ca8ff, 0xffb8e6ff, 0xff091933, ChatFormatting.AQUA),
    REALITY("reality", 0xffff334b, 0xffffb3bd, 0xff2b0710, ChatFormatting.RED),
    POWER("power", 0xffb448ff, 0xffe8b8ff, 0xff200836, ChatFormatting.LIGHT_PURPLE),
    MIND("mind", 0xffffd63a, 0xfffff4b8, 0xff2e2206, ChatFormatting.YELLOW),
    TIME("time", 0xff2eff8a, 0xffbeffda, 0xff062b17, ChatFormatting.GREEN),
    SOUL("soul", 0xffff9428, 0xffffdfb5, 0xff2e1505, ChatFormatting.GOLD);

    public static final int ALL_MASK = (1 << values().length) - 1;
    public static final int PRIMARY_COOLDOWN_TICKS = 4;
    public static final int SECONDARY_COOLDOWN_TICKS = 6;

    public final String id;
    public final int color;
    public final int coreBright;
    public final int darkSheath;
    public final ChatFormatting chatColor;
    public final int primaryCooldown = PRIMARY_COOLDOWN_TICKS;
    public final int secondaryCooldown = SECONDARY_COOLDOWN_TICKS;

    Stone(String id, int color, int coreBright, int darkSheath, ChatFormatting chatColor) {
        this.id = id;
        this.color = color;
        this.coreBright = coreBright;
        this.darkSheath = darkSheath;
        this.chatColor = chatColor;
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
