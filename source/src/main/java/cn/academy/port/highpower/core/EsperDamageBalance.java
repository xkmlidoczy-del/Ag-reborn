package cn.academy.port.highpower.core;

import java.math.BigDecimal;

public final class EsperDamageBalance {
    private static final BigDecimal COUNTER_LIMIT = BigDecimal.valueOf(60);

    private EsperDamageBalance() {}

    /** A passive counter spends a share of this hit's actual shield debit, limited by ordinary capacity. */
    public static float passiveCounter(BigDecimal absorbed, BigDecimal ordinaryCapacity, int strength) {
        return absorbed.multiply(BigDecimal.valueOf(strength * 5L, 2))
                .min(ordinaryCapacity.movePointLeft(1)).min(COUNTER_LIMIT).floatValue();
    }
}
