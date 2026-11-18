package cn.aca.omnitrix;

import java.util.List;
import net.minecraft.world.entity.EntityDimensions;

public enum Form {
    HEATBLAST(.69f, 1.90f, 1.70f, 1.12, 10, 5, 2200, 22, 60, 88_000),
    WILDMUTT(.83f, 1.20f, .94f, 1.34, 12, 6, 2600, 60, 28, 96_000),
    DIAMONDHEAD(.85f, 2.14f, 1.90f, .96, 20, 12, 2400, 85, 32, 140_000),
    XLR8(.61f, 1.74f, 1.52f, 1.82, 9, 4, 1800, 24, 16, 76_000),
    GREY_MATTER(.31f, .49f, .40f, .95, 6, 3, 1200, 10, 22, 65_000),
    FOUR_ARMS(1.00f, 2.46f, 2.18f, 1.12, 18, 10, 3800, 20, 58, 128_000),
    STINKFLY(.72f, 1.52f, 1.28f, 1.02, 8, 4, 1700, 28, 18, 78_000),
    RIPJAWS(.70f, 1.84f, 1.58f, .92, 12, 6, 2800, 16, 28, 95_000),
    UPGRADE(.70f, 1.98f, 1.72f, 1.04, 14, 7, 2100, 24, 32, 105_000),
    GHOSTFREAK(.55f, 1.91f, 1.65f, 1.08, 8, 4, 1900, 110, 60, 82_000),
    ALIEN_X(.83f, 2.39f, 2.12f, 1.42, 30, 20, 15_000_000, 96, 64, 1_000_000_000);

    public static final List<Form> CLASSIC = List.of(HEATBLAST, WILDMUTT, DIAMONDHEAD, XLR8, GREY_MATTER,
            FOUR_ARMS, STINKFLY, RIPJAWS, UPGRADE, GHOSTFREAK);
    public final float width, height, eye;
    public final double speed, armor, toughness, attack, baseBarrier;
    public final int primaryCooldown, secondaryCooldown;

    Form(float width, float height, float eye, double speed, double armor, double toughness,
         double attack, int primary, int secondary, double baseBarrier) {
        this.width = width;
        this.height = height;
        this.eye = eye;
        this.speed = speed;
        this.armor = armor;
        this.toughness = toughness;
        this.attack = attack;
        primaryCooldown = primary;
        secondaryCooldown = secondary;
        this.baseBarrier = baseBarrier;
    }

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public String asset(Kind kind) {
        return (kind == Kind.CARNITRIX ? "carni_" : (kind == Kind.CELESTIAL && this != ALIEN_X) ? "x_" : "") + id();
    }
    public String translationKey(Kind kind) { return "form.aca_omnitrix." + asset(kind); }
    public boolean flies() { return this == ALIEN_X || this == STINKFLY; }
    public boolean canUse(Kind kind) {
        return kind == Kind.CARNITRIX || kind == Kind.CELESTIAL || this != ALIEN_X;
    }

    public double speed(Kind kind) {
        if (kind == Kind.CELESTIAL) {
            return switch (this) {
                case XLR8 -> 2.15;
                case ALIEN_X -> 1.48;
                default -> speed + .12;
            };
        }
        return speed;
    }

    public double maxHealthBonus(Kind kind) {
        if (this == ALIEN_X && kind == Kind.CELESTIAL) return 100.0; // Total 120.0 HP (6 compact heart rows + 1B Barrier + 100% Aegis)
        if (kind == Kind.CELESTIAL) {
            return switch (this) {
                case DIAMONDHEAD, FOUR_ARMS -> 80;
                case RIPJAWS, WILDMUTT, UPGRADE -> 70;
                case HEATBLAST, STINKFLY, GHOSTFREAK, XLR8 -> 60;
                case GREY_MATTER -> 40;
                case ALIEN_X -> 100;
            };
        }
        double classicBonus = switch (this) {
            case DIAMONDHEAD, FOUR_ARMS -> 40;
            case ALIEN_X -> 60;
            case RIPJAWS, WILDMUTT, UPGRADE, HEATBLAST -> 30;
            case STINKFLY, GHOSTFREAK, XLR8 -> 20;
            case GREY_MATTER -> 10;
        };
        return kind == Kind.CARNITRIX ? classicBonus * 1.35 : classicBonus;
    }

    public double armor(Kind kind) {
        if (this == ALIEN_X && kind == Kind.CELESTIAL) return 30;
        if (kind == Kind.CELESTIAL) return Math.min(28, armor + 12);
        return kind == Kind.CARNITRIX ? Math.min(26, armor + 4) : armor;
    }

    public double toughness(Kind kind) {
        if (this == ALIEN_X && kind == Kind.CELESTIAL) return 20;
        if (kind == Kind.CELESTIAL) return Math.min(20, toughness + 10);
        return kind == Kind.CARNITRIX ? toughness + 3 : toughness;
    }

    public double attack(Kind kind) {
        if (this == ALIEN_X) return kind == Kind.CELESTIAL ? 15_000_000 : 85_000;
        if (kind == Kind.CELESTIAL) return attack * 14.0; // ~16,800 - 53,200 melee
        return kind == Kind.CARNITRIX ? attack * 1.6 : attack;
    }

    public double knockbackResistance(Kind kind) {
        if (this == ALIEN_X && kind == Kind.CELESTIAL) return 1.0;
        if (kind == Kind.CELESTIAL) {
            return this == FOUR_ARMS || this == DIAMONDHEAD ? .90 : .72;
        }
        return switch (this) {
            case DIAMONDHEAD, FOUR_ARMS, ALIEN_X -> .55;
            case WILDMUTT, RIPJAWS, UPGRADE -> .35;
            default -> .20;
        };
    }

    public double maxBarrier(Kind kind) {
        if (this == ALIEN_X) return kind == Kind.CELESTIAL ? 1_000_000_000.0 : 450_000.0;
        if (kind == Kind.CELESTIAL) {
            return switch (this) {
                case DIAMONDHEAD -> 5_500_000;
                case FOUR_ARMS -> 4_800_000;
                case UPGRADE, RIPJAWS -> 3_600_000;
                case WILDMUTT -> 3_400_000;
                case HEATBLAST -> 3_200_000;
                case GHOSTFREAK, STINKFLY -> 2_800_000;
                case XLR8 -> 2_500_000;
                case GREY_MATTER -> 1_900_000;
                case ALIEN_X -> 1_000_000_000;
            };
        }
        return kind == Kind.CARNITRIX ? baseBarrier * 1.65 : baseBarrier;
    }

    public String tierLabel(Kind kind) {
        if (this == ALIEN_X && kind == Kind.CELESTIAL) return "LV.5+ Ω";
        if (kind == Kind.CELESTIAL) return "LV.4+";
        if (kind == Kind.CARNITRIX) return this == ALIEN_X ? "LV.4" : "LV.3+";
        return "LV.3";
    }

    public int primaryCooldown(Kind kind) {
        if (kind != Kind.CELESTIAL) return primaryCooldown;
        return switch (this) {
            case HEATBLAST -> 16;
            case WILDMUTT -> 45;
            case DIAMONDHEAD -> 65;
            case XLR8 -> 18;
            case GREY_MATTER -> 8;
            case FOUR_ARMS -> 15;
            case STINKFLY -> 20;
            case RIPJAWS -> 13;
            case UPGRADE -> 18;
            case GHOSTFREAK -> 85;
            case ALIEN_X -> primaryCooldown;
        };
    }

    public int secondaryCooldown(Kind kind) {
        if (kind != Kind.CELESTIAL) return secondaryCooldown;
        return switch (this) {
            case HEATBLAST -> 42;
            case WILDMUTT -> 20;
            case DIAMONDHEAD -> 24;
            case XLR8 -> 12;
            case GREY_MATTER -> 18;
            case FOUR_ARMS -> 42;
            case STINKFLY -> 14;
            case RIPJAWS -> 22;
            case UPGRADE -> 24;
            case GHOSTFREAK -> 40;
            case ALIEN_X -> secondaryCooldown;
        };
    }

    public EntityDimensions dimensions(Kind kind) {
        float factor = kind == Kind.CARNITRIX ? 1.06f : 1;
        return EntityDimensions.scalable(width * factor, height * factor).withEyeHeight(eye * factor);
    }

    public static Form at(int index) { return index >= 0 && index < values().length ? values()[index] : null; }
}
