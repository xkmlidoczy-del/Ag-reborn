package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;

/** A configured physical crouch edge selects the mode once; release never rereads it. */
public final class IronSandLanceInputState {
    public record Held(HighPowerInput.Input identity,boolean orbit) {}
    private Held latest;
    private boolean holding;
    public void begin(HighPowerInput.Input input,boolean orbit){
        if(input==null||HighPowerInput.parse(input.wire())==null)throw new IllegalArgumentException("Valid high-power input required");
        latest=new Held(input,orbit);holding=true;
    }
    public Held release(HighPowerInput.Input input){
        if(!holding||latest==null||!latest.identity.equals(input))return null;
        holding=false;return latest;
    }
    /** Keep the released identity so screen/focus/preset changes can also stop an active volley/orbit. */
    public HighPowerInput.Input cancel(){var input=latest==null?null:latest.identity;reset();return input;}
    public HighPowerInput.Input cancel(HighPowerInput.Input input){return latest!=null&&latest.identity.equals(input)?cancel():null;}
    public HighPowerInput.Input cancelWhenBlocked(boolean blocked){return blocked?cancel():null;}
    public Held current(){return latest;}
    public void reset(){latest=null;holding=false;}
}
