package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.UUID;

/** Authenticated immutable pair, bounded phase visuals, pause, and world-border precision. */
public final class CornerExchangeVisualRegressionTest {
    private static int checks;
    private static final Point A=new Point(0,64,0), B=new Point(24,64,0);
    private static final UUID OWNER=new UUID(333,1);
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    private static CornerExchangeState.Frame frame(long input,long sequence,int phase,int life,Point a,Point b,int warning) {
        return new CornerExchangeState.Frame(new CornerExchangeState.Identity(19,OWNER,71,input,1),sequence,phase,A,B,
                CornerExchangeMesh.markBounds(B,null,null),phase==2,"",life,a,b,false,warning);
    }
    public static void main(String[] args) {
        var state=new CornerExchangeState();var first=frame(1,1,0,400,A,null,0);
        check(state.accept(first,true)&&state.markerVisible(),"first authenticated standing mark");
        check(!state.accept(first,true),"sequence replay denied");
        for(int i=0;i<100;i++)check(state.tick(true,true)==null,"paused ownership retained");
        check(state.remainingTicks()==400&&state.markerVisible(),"pause advances neither lease nor heartbeat");
        for(int i=0;i<31;i++)check(state.tick(false,true)==null,"heartbeat silence is cosmetic only");
        check(!state.markerVisible()&&state.active()!=null&&state.remainingTicks()==369,"stale marker hides without cancellation");
        check(state.accept(frame(1,2,0,390,A,null,0),true)&&state.remainingTicks()==369,"heartbeat cannot renew local pair lease");
        check(!state.accept(frame(1,3,0,400,A,null,0),true),"server remaining increase rejected");
        check(!state.accept(frame(1,3,0,380,A,B,0),true),"B requires a fresh native input");
        check(!state.accept(frame(2,3,0,380,new Point(1,64,0),B,0),true),"A is immutable");
        check(state.accept(frame(2,3,0,380,A,B,0),true)&&state.remainingTicks()==369,"fresh B preserves400-tick origin");
        check(state.accept(frame(3,4,1,360,A,B,3),true)&&state.warningVisible(),"server3-tick warning distinct from arrival");
        for(int i=0;i<50;i++)state.tick(true,true);
        check(state.warningRemainingTicks()==3&&state.remainingTicks()==360,"pause freezes warning and pair independently");
        for(int i=0;i<3;i++)check(state.tick(false,true)==null,"warning expiry is not terminal");
        check(!state.warningVisible()&&state.active().phase()==1&&state.remainingTicks()==357,"expired warning never fabricates success");
        check(state.accept(frame(3,5,2,357,A,B,0),true)&&state.markerVisible(),"confirmed swap retains both marks");
        check(!state.accept(frame(3,6,2,356,A,B,0),true),"success cue plays once per input");
        check(!state.accept(frame(3,6,1,356,A,B,2),true),"completed input cannot replay warning");
        check(state.accept(frame(3,6,0,350,A,B,0),true),"idle heartbeat after success keeps stable token");
        check(state.accept(frame(4,7,4,340,A,B,0),true)&&state.markerVisible(),"ordinary refusal retains pair");
        check(!state.accept(frame(4,8,1,339,A,B,3),true),"refused nonce cannot later execute");
        check(state.accept(frame(5,8,1,330,A,B,3),true),"fresh retry begins new warning");
        var ended=frame(5,9,3,0,null,null,0);check(state.accept(ended,true)&&state.active()==null,"clear retires pair");
        check(!state.accept(frame(6,10,0,300,A,B,0),true),"cleared token cannot revive");
        state.resetSession();check(state.accept(first,true),"new verified connection resets fence");state.cancel(71,1);
        check(!state.accept(frame(1,2,0,399,A,null,0),true),"cancel rejects delayed receipt");
        var expiry=new CornerExchangeState();expiry.accept(frame(1,1,0,1,A,null,0),true);
        check(expiry.tick(false,true)!=null&&!expiry.markerVisible(),"one remaining tick expires exactly");
        check(!CornerExchangeState.valid(frame(1,1,0,400,A,new Point(cn.academy.port.highpower.core.CornerExchangeRules.RANGE + .001,64,0),0)),"pair respects the configured distance");
        check(!CornerExchangeState.valid(frame(1,1,1,400,A,B,4)),"fourth warning tick rejected");
        check(!CornerExchangeState.valid(frame(1,1,0,CornerExchangeState.PAIR_LIFE_TICKS + 1,A,null,0)),"life above400 rejected");
        var observer=new CornerExchangeState();check(!observer.accept(first,false),"private marks stay owner-only");
        check(observer.accept(frame(3,4,1,360,A,B,3),false)&&observer.accept(frame(3,5,2,357,A,B,0),false),"observer actual warning-to-success admitted");
        var bounds=CornerExchangeMesh.markBounds(A,null,null);
        check(Math.abs(bounds.maxY()-bounds.minY()-1.8)<1e-8&&Math.abs(bounds.maxX()-bounds.minX()-.6)<1e-8,"full standing-body box");
        check(CornerExchangeMesh.boxLines(bounds).size()==12&&CornerExchangeMesh.directionArrow(bounds,0).size()==3,"fixed geometry budget");
        check(CornerExchangeMesh.directionArrow(bounds,0).getFirst().to().z()>0&&CornerExchangeMesh.directionArrow(bounds,180).getFirst().to().z()<0,"live reversal arrows are180degrees apart");
        check(CornerExchangeMesh.insideCamera(bounds,new Point(0,65.62,0)),"firstperson inside-body camera suppresses biped surface");
        check(!CornerExchangeMesh.insideCamera(bounds,new Point(0,65.62,-4)),"thirdperson can show confirmed biped");
        for(int i=0;i<7;i++)check(CornerExchangeMesh.frame(i)==i&&CornerExchangeMesh.alpha(i)>0,"all7 original confirmed frames");
        check(CornerExchangeMesh.alpha(7)==0&&CornerExchangeMesh.alpha(-1)==0,"confirmed effects strictly bounded7frames");
        var large=CornerExchangeState.relative(new Point(30_000_000.01,64,30_000_000.02),new Point(30_000_000,64,30_000_000));
        check(Math.abs(large.x()-.01)<1e-8&&Math.abs(large.z()-.02)<1e-8,"double subtraction preserves far-world centimetres");
        check(CornerExchangeState.labelScale(.1,.1,50)==0&&CornerExchangeState.labelScale(-1,1,50)==0,"near/behindcamera labels hidden");
        check(CornerExchangeState.labelScale(.4,.4,50)*50<=.4*.22+1e-6,"near text width limited by depth");
        check(CornerExchangeState.nearbyMark(frame(2,3,0,300,A,B,0),new Point(.75,64.125,0))==1,"preview proximity uses full source tolerances");
        guidanceLayout();
        System.out.println("CornerExchangeVisualRegressionTest: "+checks+" pure checks passed (no native rendered visual claim)");
    }
    private static void guidanceLayout() {
        var beforePair = CornerExchangeHudLayout.fit(393,272,2,9);
        var pairReady = CornerExchangeHudLayout.fit(393,272,4,9);
        check(beforePair != null && pairReady != null && beforePair.top() == 164 && pairReady.top() == beforePair.top(),
                "1178x814 at GUI scale3 keeps two/four guidance rows on the same below-crosshair anchor");
        check(pairReady.scale() == 1 && pairReady.top()*3 > 330 && (pairReady.top()+pairReady.height())*3 <= (272-48)*3,
                "actual recovery panel y162..330 is avoided while four rows remain above hotbar reserve");
        for (int scale=1;scale<=4;scale++) {
            int width=(1178+scale-1)/scale, height=(814+scale-1)/scale;
            var layout=CornerExchangeHudLayout.fit(width,height,4,9);
            check(layout != null,"four rows stay readable at GUI scale"+scale);
            check(layout.top()>=height/2+CornerExchangeHudLayout.MINIMUM_GAP
                    && layout.top()+layout.height()<=height-CornerExchangeHudLayout.BOTTOM_RESERVE,
                    "scaled viewport guidance never rises above crosshair or enters hotbar");
        }
        for (int width : new int[]{144,160,240,393,589}) for (int height : new int[]{180,204,272,407})
            for (int rows : new int[]{2,4,6,12}) {
                var layout=CornerExchangeHudLayout.fit(width,height,rows,9);
                if(layout==null) continue;
                check(layout.scale()>=.8f&&layout.scale()<=1&&layout.top()>=height/2+16
                        &&layout.top()+layout.height()<=height-48,"wrapped/narrow guidance fits readable lower band");
                check(layout.textWidth()<=width-24&&layout.textWidth()*layout.scale()<=width-24,
                        "wrapped/narrow guidance preserves horizontal margins");
            }
        check(CornerExchangeHudLayout.fit(240,180,4,9)==null
                &&CornerExchangeHudLayout.fit(240,180,2,9)!=null,
                "short viewport requests mode-and-clear compact fallback without upward relocation");
        check(CornerExchangeHudLayout.fit(143,272,2,9)==null
                &&CornerExchangeHudLayout.fit(240,100,2,9)==null,
                "unreadably narrow or shallow viewport hides guidance instead of overlapping other UI");
        check(CornerExchangeHudLayout.fit(393,272,0,9)==null
                &&CornerExchangeHudLayout.fit(393,272,65,9)==null
                &&CornerExchangeHudLayout.fit(393,272,4,0)==null,
                "invalid text budgets cannot create offscreen guidance");
    }

}
