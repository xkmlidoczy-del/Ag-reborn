package cn.academy.port.highpower.core;

/** One-time release of the former generated-off candidate. Later operator choices are never overwritten. */
public final class MagneticGroundStepAdmission {
    public static final int RELEASE_VERSION=1;
    public record Decision(boolean enabled,int version,boolean changed) {}
    public static Decision migrate(boolean enabled,int version){
        if(version<0)throw new IllegalArgumentException("Nonnegative feature admission version required");
        return version<RELEASE_VERSION?new Decision(true,RELEASE_VERSION,true):new Decision(enabled,version,false);
    }
    private MagneticGroundStepAdmission() {}
}
