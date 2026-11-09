package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;

/** Latest emitted input for one persistent skill; separate instances prevent cross-skill cancellation.
 * The historical class name is retained for the existing tether regression contract. */
public final class PolarityTetherInputTracker {
    private HighPowerInput.Input latest;
    public void remember(HighPowerInput.Input input){
        if(input==null||HighPowerInput.parse(input.wire())==null)throw new IllegalArgumentException("Valid high-power input required");
        latest=input;
    }
    public HighPowerInput.Input cancel(){var result=latest;latest=null;return result;}
    public HighPowerInput.Input cancelWhenBlocked(boolean blocked){return blocked?cancel():null;}
    public void reset(){latest=null;}
}
