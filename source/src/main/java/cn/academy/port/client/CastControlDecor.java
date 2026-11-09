package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.highpower.core.CastPose;

/** Small control interfaces at the casting hand. World-scale effects remain owned by each skill. */
public final class CastControlDecor {
    private CastControlDecor() {}
    public static FxLib.Parts fx(CastPose.Archetype profile, Point hand, Point forward, double age, double strength, VisualTier tier) {
        if(profile==null || tier==null || tier.layers<3 || !FxMotifs.ok(hand,forward) || !Double.isFinite(age+strength) || age<0 || strength<=0) return FxLib.Parts.EMPTY;
        var fr=FxMotifs.frame(forward); if(fr==null) return FxLib.Parts.EMPTY;
        Point f=fr[0],s=fr[1],u=fr[2]; double a=210*Math.min(1,strength);
        var out=new FxLib.Builder();
        switch(profile) {
            case LANCE -> out.add(FxMotifs.packets(hand,hand.plus(f.scale(1.4)),4,age,.08,.1,0xB9C8DD,a,tier));
            case RAIL_DEPLOY, GRID_DEPLOY -> {
                for(int k=-1;k<=1;k++) out.add(FxMotifs.panel(hand.plus(s.scale(k*.28)).plus(f.scale(.12*Math.abs(k))),s,u,.1,.14,0,0x72CFFF,a,tier));
            }
            case FIELD_TUNE, DISCHARGE -> {
                out.add(FxMotifs.gauge(hand,s,u,.32,16,12,0x6FCBFF,a,tier));
                out.add(FxMotifs.orbit(hand,s,f,.4,3,age*.045,.035,0xD3ECFF,a,tier));
            }
            case ACCELERATOR -> {
                for(int k=0;k<3;k++) out.add(FxMotifs.gauge(hand.plus(f.scale(k*.25)),s,u,.16+.035*k,8,6,0x7FFFA4,a,tier));
            }
            case CANOPY -> {
                for(int k=-1;k<=1;k++) out.add(FxMotifs.packets(hand,hand.plus(new Point(k*.6,1.4,.3)),3,age,.05,.08,0x8CFFC3,a,tier));
            }
            case PHASE_ROUTE -> {
                out.add(FxMotifs.panel(hand,s,u,.38,.18,0,0xB8A8FF,a,tier));
                out.add(FxMotifs.packets(hand.minus(s.scale(.3)),hand.plus(s.scale(.3)),4,age,.08,.06,0xD8CAFF,a,tier));
            }
            case PHASE_GATE -> {
                out.add(FxMotifs.panel(hand.plus(s.scale(.27)),s,u,.12,.3,0,0xBCA5FF,a,tier));
                out.add(FxMotifs.panel(hand.minus(s.scale(.27)),s,u,.12,.3,0,0x85E6FF,a,tier));
            }
            case VECTOR_CAGE -> out.add(FxMotifs.converging(hand,6,.5,((age*.06)%1+1)%1,441,0xBFEAFF,a,tier));
            case VECTOR_DOMAIN -> {
                out.add(FxMotifs.chevrons(hand,s,.6,2,.08,age*.02,0xE2F4FF,a,tier));
                out.add(FxMotifs.chevrons(hand,u,.6,2,.08,age*.02,0x8FD8FF,a,tier));
                out.add(FxMotifs.chevrons(hand,f,.6,2,.08,age*.02,0x7FF5E8,a,tier));
            }
            case FLIGHT, BRAKE -> out.add(FxMotifs.chevrons(hand,profile==CastPose.Archetype.BRAKE?f.scale(-1):f,.8,3,.12,age*.06,0xDCE8F2,a,tier));
            case LISTEN -> out.add(FxMotifs.gauge(hand,s,u,.2,12,(int)(age/2)%12+1,0x8FD8FF,a,tier));
            default -> { }
        }
        return out.build();
    }
}
