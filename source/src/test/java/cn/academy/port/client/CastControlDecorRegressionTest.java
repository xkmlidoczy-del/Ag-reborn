package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.highpower.core.CastPose;
import cn.academy.port.client.RailThunderArrayGeometry.Point;

public final class CastControlDecorRegressionTest {
    private static int checks;
    private static void check(boolean b,String why) { checks++; if(!b) throw new AssertionError(why); }
    public static void main(String[] args) {
        var origin=new Point(0,1,0); var forward=new Point(0,0,1); var tier=VisualTier.CINEMATIC;
        for(var a:CastPose.Archetype.values()) for(double age:new double[]{0,3,12,40,200}) {
            var parts=CastControlDecor.fx(a,origin,forward,age,1,tier);
            check(parts.equals(CastControlDecor.fx(a,origin,forward,age,1,tier)),"deterministic interface: "+a);
            check(parts.strokes().size()+parts.sprites().size()+parts.decals().size()<256,"local interface bounded: "+a);
            if(a.ordinal()>=CastPose.Archetype.LANCE.ordinal()) check(!parts.equals(FxLib.Parts.EMPTY),"flagship control interface present: "+a);
            check(CastControlDecor.fx(a,origin,forward,age,0,tier).equals(FxLib.Parts.EMPTY),"no orphan interface after gesture");
            check(CastControlDecor.fx(a,origin,forward,Double.NaN,1,tier).equals(FxLib.Parts.EMPTY),"invalid time silent");
        }
        check(CastControlDecor.fx(CastPose.Archetype.CANOPY,origin,new Point(0,0,0),1,1,tier).equals(FxLib.Parts.EMPTY),"invalid axis silent");
        System.out.println("PASS "+checks+" control interface assertions");
    }
}
