package cn.academy.port.highpower.core;

/** Restart-safe saved-world anchor; native server ticks, not world-tick phase, advance a paid period. */
public record MagneticDomainClock(long savedWorldTick, long serverTickAtStart) {
    public MagneticDomainClock {
        if(savedWorldTick<0||savedWorldTick>Long.MAX_VALUE-20||serverTickAtStart<0)throw new IllegalArgumentException("Invalid domain clock anchor");
    }
    public long at(long currentServerTick) {
        if(currentServerTick<serverTickAtStart)return -1;
        long elapsed=currentServerTick-serverTickAtStart;
        return elapsed>Long.MAX_VALUE-20-savedWorldTick?-1:savedWorldTick+elapsed;
    }
}
