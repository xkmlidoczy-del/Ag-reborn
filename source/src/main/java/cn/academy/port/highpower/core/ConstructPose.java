package cn.academy.port.highpower.core;

/** Server-cue-driven, client-only presentation clock. Wire phases match ConstructRuntime; no gameplay dependency. */
public final class ConstructPose {
    public static final int PREVIEW=0, REFUSED=1, HOLD=2, THROWN=3, LANDED=4, END=5, DASH=6;
    public static final int PULL_TICKS=12, BLEND_TICKS=5, RELAX_TICKS=8;
    private ConstructPose() {}

    public static final class Timeline {
        private long token=-1;
        private int phase=END;
        private double began, received, yaw0;
        private CastPose.Motion fromRight=CastPose.Motion.NONE, fromLeft=CastPose.Motion.NONE;

        public boolean accept(long nextToken, int nextPhase, double now, double yaw) {
            if (nextToken<=0 || nextToken<token || nextPhase<PREVIEW || nextPhase>DASH || !Double.isFinite(now) || now<0 || !Double.isFinite(yaw)) return false;
            // A late packet cannot revive a released body. New preview attempts carry a new token.
            boolean released=phase>=THROWN && phase!=DASH;
            // DASH is numbered last on the wire but comes first in a body's life, so it is neither "released" nor a late cue after HOLD.
            if (nextToken==token && (now<received || released && nextPhase<phase || phase==HOLD && nextPhase==PREVIEW
                    || nextPhase==DASH && (phase==HOLD || released))) return false;
            if (nextToken==token && nextPhase==phase) { received=now; return true; }
            fromRight=sample(now,yaw,true); fromLeft=sample(now,yaw,false);
            token=nextToken; phase=nextPhase; began=received=now; yaw0=yaw;
            return true;
        }
        public double age(double now) { return Math.max(0,now-began); }
        public int phase() { return phase; }
        /** Whole-body crouch (0..1) and forward lean (radians) for the dash: coil, run, and the squat that rises into the grab. */
        public Stance stance(double now) {
            if (token<0 || !Double.isFinite(now) || now<began) return Stance.NONE;
            double age=age(now);
            double relax=1-smoothStep(Math.max(0,now-received-life())/RELAX_TICKS);
            Stance s=switch(phase) {
                case DASH -> age<CROUCH_TICKS ? new Stance(smoothStep(age/CROUCH_TICKS),.2*smoothStep(age/CROUCH_TICKS))
                        : new Stance(1-.7*smoothStep((age-CROUCH_TICKS)/4),.2+.35*smoothStep((age-CROUCH_TICKS)/4));
                case HOLD -> { double u=1-smoothStep(age/10); yield new Stance(.3*u,.55*u); }
                default -> Stance.NONE;
            };
            return new Stance(s.crouch()*relax,s.lean()*relax);
        }
        public boolean expired(double now) { return now-received>life()+RELAX_TICKS+BLEND_TICKS; }
        private int life() { return switch(phase) { case PREVIEW -> ConstructRules.PREVIEW_TICKS; case HOLD, DASH -> 8; case THROWN,LANDED -> 16; default -> 0; }; }

        public CastPose.Motion sample(double now, double yaw, boolean right) {
            if (token<0 || !Double.isFinite(now) || !Double.isFinite(yaw) || now<began) return CastPose.Motion.NONE;
            double age=age(now), turn=turn(yaw-yaw0);
            CastPose.Motion target=switch(phase) {
                case PREVIEW -> pose(CastPose.Archetype.CONSTRUCT_LOCK,30,right);
                case DASH -> dash(age);
                case HOLD -> CastPose.mix(pose(CastPose.Archetype.CONSTRUCT_PULL,30,right), steer(turn,right), age/PULL_TICKS);
                case THROWN -> pose(CastPose.Archetype.CONSTRUCT_THROW,age+1,right);
                case LANDED -> pose(CastPose.Archetype.CONSTRUCT_LAND,30,right);
                default -> CastPose.Motion.NONE;
            };
            CastPose.Motion motion=CastPose.mix(right?fromRight:fromLeft,target,age/BLEND_TICKS);
            return CastPose.mix(motion,CastPose.Motion.NONE,Math.max(0,now-received-life())/RELAX_TICKS);
        }
    }
    public static final int CROUCH_TICKS=ConstructRules.DASH_CROUCH_TICKS;
    public record Stance(double crouch, double lean) { public static final Stance NONE=new Stance(0,0); }
    private static double smoothStep(double x) { double t=x!=x?0:Math.max(0,Math.min(1,x)); return t*t*(3-2*t); }
    /** Coil with both arms drawn back, then run with the arms swept behind: the hands are never in front of the body. */
    private static CastPose.Motion dash(double age) {
        double coil=smoothStep(age/CROUCH_TICKS), run=smoothStep((age-CROUCH_TICKS)/4);
        double back=.55+.55*run, out=.12+.1*run;
        var arms=new CastPose.Arms(back,out,.12,back,-out,-.12,0);
        var hand=new CastPose.Hand(.02*coil,-.2*coil,.18*coil,-22*coil,0,0);
        return new CastPose.Motion(hand,arms,1);
    }
    private static CastPose.Motion pose(CastPose.Archetype a,double age,boolean right) {
        return CastPose.sample(a,age,-1,right);
    }
    private static double turn(double delta) {
        if(!Double.isFinite(delta)) return 0;
        delta=((delta+180)%360+360)%360-180;
        return Math.max(-1,Math.min(1,delta/60));
    }
    private static CastPose.Motion steer(double turn,boolean right) {
        var p=pose(CastPose.Archetype.CONSTRUCT_STEER,30,right); var h=p.hand(); var a=p.arms();
        double sign=right?1:-1, t=turn*sign;
        var hand=new CastPose.Hand(h.x(),h.y(),h.z(),h.rotX(),h.rotY()+12*t,h.rotZ()+24*t);
        var arms=right?new CastPose.Arms(a.rx(),a.ry()+.18*t,a.rz()+.4*t,a.lx(),a.ly(),a.lz(),a.lean())
                :new CastPose.Arms(a.rx(),a.ry(),a.rz(),a.lx(),a.ly()+.18*t,a.lz()+.4*t,a.lean());
        return new CastPose.Motion(hand,arms,1);
    }
}
