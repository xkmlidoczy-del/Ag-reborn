package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;

/** NEW141 owns one immutable physical press; only the server classifies its duration. */
public final class ThunderNetInputState {
    public record Held(HighPowerInput.Input identity,int physicalKey) {}
    private Held latest;
    private boolean holding;
    public void begin(HighPowerInput.Input identity,int physicalKey){
        if(identity==null||HighPowerInput.parse(identity.wire())==null||physicalKey==0)
            throw new IllegalArgumentException("Valid authenticated input and physical key required");
        latest=new Held(identity,physicalKey);holding=true;
    }
    public HighPowerInput.Input release(HighPowerInput.Input identity){
        if(!holding||latest==null||!latest.identity.equals(identity))return null;
        holding=false;return latest.identity;
    }
    /** Silent classic snapshot rebuilds must not swallow the physical release of this held input. */
    public HighPowerInput.Input releaseWhenUp(boolean physicalDown){return !physicalDown&&latest!=null?release(latest.identity):null;}
    public HighPowerInput.Input cancel(){var result=latest==null?null:latest.identity;reset();return result;}
    public HighPowerInput.Input cancel(HighPowerInput.Input identity){return latest!=null&&latest.identity.equals(identity)?cancel():null;}
    public HighPowerInput.Input cancelWhenBlocked(boolean blocked){return blocked?cancel():null;}
    public Held current(){return latest;}
    public boolean holding(){return holding;}
    public void reset(){latest=null;holding=false;}
}
