package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.List;
import static cn.academy.port.highpower.core.ScatterLocatorRules.*;

/** Approved preparation bounds and real NEW221 geometry at the extended placement distance. */
public final class ScatterLocatorRulesRegressionTest {
    private static int checks;
    public static void main(String[] args){
        String[] expected={"2","2","1",".2",".02"};
        for(int level=1;level<=5;level++)check(controlFee(BigDecimal.valueOf(level))==Double.parseDouble(expected[level-1]),"frozen control-fee anchor "+level);
        check(controlFee(new BigDecimal("2.5"))<2&&controlFee(new BigDecimal("2.5"))>1,"fee follows continuous growth");
        check(LIFETIME==cn.academy.port.core.SkillTuning.number("scatter_locator_plate", "duration_ticks")&&HOLD_LIMIT==100&&RECALL_TICKS==12&&PREVIEW_INTERVAL==4,"bounded edit, recall and nonrenewable lifetime");
        check(PLACE_RANGE==24&&TOTAL_RANGE==40&&MAX_PLATES==1&&MAX_REFLECTIONS==1&&MAX_SEGMENTS==2,"single extended locator keeps the native whole-shot budget");
        check(AIR_PLACE_RANGE==22&&AIR_PLACE_RANGE<PLACE_RANGE,"air default leaves release-time movement room inside the same hard range");
        check(Math.hypot(PLACE_RANGE,.35)>PLACE_RANGE,"placing on the hard boundary would reject an ordinary uncrouch");
        check(Math.hypot(AIR_PLACE_RANGE+1,.35)<PLACE_RANGE,"air default tolerates uncrouching and a one-meter retreat");
        check(Math.hypot(AIR_PLACE_RANGE+2.1,.35)>PLACE_RANGE,"air default does not authorize a release outside the hard24m sphere");
        check(!expired(100,99+LIFETIME,LIFETIME)&&expired(100,100+LIFETIME,LIFETIME),"first-placement expiry at the configured tick");
        check(!expired(100,199,HOLD_LIMIT)&&expired(100,200,HOLD_LIMIT),"provisional edit expires at100 ticks");
        check(expired(100,99,LIFETIME)&&expired(-1,100,LIFETIME)&&expired(100,100,0),"invalid clocks fail closed");
        check(!clock(Long.MAX_VALUE)&&clock(Long.MAX_VALUE-LIFETIME-1),"expiry arithmetic cannot overflow");
        check(ready(-1,100,1)&&!ready(100,100,1)&&ready(100,101,1)&&!ready(-1,100,0),"physical input rate remains bounded");
        var hand=new FoldedMirrorGeometry.Vector(0,0,0);
        var center=new FoldedMirrorGeometry.Vector(24,0,0);
        var incoming=new FoldedMirrorGeometry.Vector(1,0,0);
        var outgoing=new FoldedMirrorGeometry.Vector(0,0,1);
        var normal=FoldedMirrorGeometry.normalFor(incoming,outgoing).orElseThrow();
        var plate=new FoldedMirrorGeometry.Plate(0,center,normal);
        var contact=FoldedMirrorGeometry.nearestContact(List.of(plate),hand,incoming,TOTAL_RANGE,-1).orElseThrow();
        check(contact.reflects()&&contact.distance()==24,"extended plane is a real central reflection");
        check(Math.abs(contact.reflectedDirection().dot(outgoing)-1)<1e-12,"native reflection follows stored normal");
        check(TOTAL_RANGE-contact.distance()==16,"extended first leg leaves16m rather than resetting range");
        check(FoldedMirrorGeometry.nearestContact(List.of(plate),contact.point(),contact.reflectedDirection(),16,contact).isEmpty(),"single-plane departure cannot produce a second reflection");
        check(FoldedMirrorGeometry.normalFor(incoming,incoming).isEmpty(),"straight-through edit cannot invent a normal");
        check(FoldedMirrorGeometry.contact(plate,new FoldedMirrorGeometry.Vector(25,0,0),new FoldedMirrorGeometry.Vector(-1,0,0),40).orElseThrow().kind()==FoldedMirrorGeometry.ContactKind.BACK_FACE,"fixed plane cannot turn into automatic targeting");
        System.out.println("PASS "+checks+" NEW223 fee, lifetime and native fixed-plane geometry checks");
    }
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}
