package cn.academy.port.client;

import cn.academy.port.highpower.core.TacticalCoordinateRules;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.UUID;

/** Layout, physical key edges, receipt lifetime, fixed visual budgets and border precision. */
public final class TacticalCoordinateClientRegressionTest {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (int width : new int[]{320,426,640,960}) for (int height : new int[]{180,240,360,540}) {
            var layout = TacticalCoordinateUiState.layout(width,height); var p = layout.panel();
            check(p.x() >= 0 && p.y() >= 0 && p.x()+p.width() <= width && p.y()+p.height() <= height, "native panel fits scaled viewport");
            for (int slot = 0; slot < 6; slot++) {
                var tab = layout.tab(slot);
                check(tab.width() >= 40 && inside(tab,p), "all six numbered targets remain mouse-addressable at320px");
                if (slot > 0) { var before = layout.tab(slot-1); check(before.x()+before.width() < tab.x(), "coordinate targets never overlap"); }
            }
            for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++) check(inside(layout.button(column,row),p), "all four explicit actions remain visible");
        }
        check(TacticalCoordinateUiState.selected(0,-1)==5 && TacticalCoordinateUiState.selected(5,1)==0,"slot navigation wraps within six targets");
        check(TacticalCoordinateUiState.normalizedYaw(-90)==270 && TacticalCoordinateUiState.normalizedYaw(450)==90,"display yaw remains normalized");
        var keys = new TacticalCoordinateUiState.Keys();
        keys.blockHeld(257); check(!keys.press(257,true),"opening with Enter down never confirms");
        keys.release(257); check(keys.press(257,true),"fresh focused Enter produces one edge");
        check(!keys.press(257,true),"held Enter cannot repeat accept or confirm");
        keys.release(257); check(!keys.press(257,false),"Enter while typing or elsewhere cannot act");
        check(!keys.press(257,true),"moving focus while Enter is held does not create an edge");
        keys.release(257); check(keys.press(257,true),"release restores deliberate keyboard activation");
        check(keys.press(262,true) && !keys.press(262,true),"arrow key repeat cannot send multiple menu mutations");
        keys.release(262); check(!keys.press(262,false),"arrows with child focus never change target");
        keys.clear(); check(keys.press(262,true),"closed screen clears its input state");
        var receipts = new TacticalCoordinateUiState.Receipts();
        var first = new TacticalCoordinateUiState.Identity(1,new UUID(353,1));
        var other = new TacticalCoordinateUiState.Identity(1,new UUID(353,2));
        check(receipts.accept(first,1),"authenticated first snapshot admitted");
        check(!receipts.accept(first,1)&&!receipts.accept(first,0),"duplicate and old snapshots rejected");
        check(receipts.accept(first,2),"newer authoritative revision admitted");
        check(receipts.accept(other,1),"separate invite nonce has a separate sequence");
        receipts.retire(first); check(!receipts.accept(first,99),"late receipt cannot reopen a retired menu");
        check(receipts.accept(other,2),"retiring a menu leaves unrelated invitation intact");
        receipts.clear(); check(receipts.accept(first,1),"verified connection replacement resets bounded receipt state");
        for (int i=2;i<100;i++) check(receipts.accept(new TacticalCoordinateUiState.Identity(i,new UUID(353,i)),1),"bounded replay cache does not disable menu after64 uses");
        int inviteTicks = TacticalCoordinateRules.INVITE_TICKS;
        long deadline = TacticalCoordinateUiState.invitationDeadline(100,inviteTicks,Long.MAX_VALUE);
        check(deadline==100+inviteTicks,"invitation retains the full authorized local lifetime");
        check(TacticalCoordinateUiState.invitationDeadline(150,inviteTicks,deadline)==deadline,"review refresh cannot extend frozen expiry");
        check(TacticalCoordinateUiState.invitationDeadline(150,30,deadline)==180,"shorter authoritative expiry tightens local deadline");
        check(TacticalCoordinateUiState.invitationDeadline(100,inviteTicks+1,Long.MAX_VALUE)==deadline,"overlong receipt cannot extend consent window");
        check(TacticalCoordinateUiState.confirmedArrival("success","",true),"only clean confirmed positive movement earns arrival cue");
        check(!TacticalCoordinateUiState.confirmedArrival("end","transport_unconfirmed",true),"observed motion on paid uncertain failure never earns arrival cue");
        check(!TacticalCoordinateUiState.confirmedArrival("end","",true),"terminal cleanup cannot masquerade as successful arrival");
        check(!TacticalCoordinateUiState.confirmedArrival("success","transport_unconfirmed",true),"error reason suppresses cue even with malformed success phase");
        check(!TacticalCoordinateUiState.confirmedArrival("success","",false),"success without actual local movement cannot create cue");
        check(!TacticalCoordinateUiState.confirmedArrival("end","teammate_moved",false),"sender teammate status cannot produce a local arrival cue");
        reviewOpening();
        geometry();
        System.out.println("TacticalCoordinateClientRegressionTest: "+checks+" checks passed; native rendering and human consent QA remain separate");
    }
    private static boolean inside(TacticalCoordinateUiState.Rect rect,TacticalCoordinateUiState.Rect parent) {
        return rect.x()>=parent.x()&&rect.y()>=parent.y()&&rect.x()+rect.width()<=parent.x()+parent.width()&&rect.y()+rect.height()<=parent.y()+parent.height();
    }
    private static void reviewOpening() {
        var gate = new TacticalCoordinateUiState.ReviewGate();
        UUID invitation = new UUID(353,501), newerInvitation = new UUID(353,502);
        Object initiatingChat = new Object(), newerChat = new Object(), unrelatedMenu = new Object();
        check(!gate.active() && !gate.allows(null,1),"network invitation alone cannot authorize a screen");
        gate.begin(invitation,initiatingChat,1);
        check(gate.expects(invitation,1) && gate.allows(initiatingChat,1),"explicit click may replace the exact chat that stays open after RUN_COMMAND");
        check(gate.allows(null,1),"typed command may open review after vanilla closes chat");
        check(!gate.allows(newerChat,1),"a later chat cannot be replaced by a delayed review");
        check(!gate.allows(unrelatedMenu,1),"an unrelated menu cannot be replaced by a delayed review");
        check(!gate.expects(newerInvitation,1),"other invite nonce cannot satisfy the explicit request");
        check(!gate.expects(invitation,2) && !gate.allows(initiatingChat,2),"old connection or world generation cannot open review");
        gate.begin(newerInvitation,newerChat,1);
        gate.cancel(invitation);
        check(gate.expects(newerInvitation,1),"old terminal packet cannot cancel a newer explicit invitation");
        gate.cancel(newerInvitation);
        check(!gate.active() && !gate.allows(newerChat,1),"matching end or expiry retires review authority");
        gate.begin(invitation,initiatingChat,1); gate.clear();
        check(!gate.expects(invitation,1),"disconnect/death clears pending review authority");
        gate.begin(invitation,null,2);
        check(gate.allows(null,2) && !gate.allows(initiatingChat,2),"a request initiated in world cannot seize a later chat");
    }
    private static void geometry() {
        check(TacticalCoordinateMesh.MAX_OUTLINES==6&&TacticalCoordinateMesh.MAX_OUTLINES*TacticalCoordinateMesh.STROKES_PER_OUTLINE<=64,"six outlines fit fixed global stroke cap");
        var feet = new Point(30_000_000.01,65,30_000_000.02);
        var relative = TacticalCoordinateMesh.relative(feet,new Point(30_000_000,64,30_000_000));
        check(Math.abs(relative.x()-.01)<1e-8&&Math.abs(relative.z()-.02)<1e-8&&relative.y()==1,"camera-relative double subtraction preserves border centimetres");
        for (float yaw:new float[]{-180,-90,0,90,179.9F}) {
            var lines=TacticalCoordinateMesh.outline(feet,yaw); check(lines.size()==10,"all headings retain bounded standing shape");
            for(var line:lines) check(line.from().finite()&&line.to().finite()&&line.from().distanceSquared(line.to())>0,"standing strokes stay finite and nonzero");
        }
        check(TacticalCoordinateMesh.outline(feet,Float.NaN).isEmpty(),"malformed yaw cannot enter renderer");
        check(TacticalCoordinateMesh.endpoint(feet).size()==4,"confirmed endpoint keeps original small four-stroke cue");
        for(int age=0;age<7;age++)check(TacticalCoordinateMesh.frame(age)==age&&TacticalCoordinateMesh.alpha(age)>0,"exact original seven confirmation frames");
        check(TacticalCoordinateMesh.alpha(7)==0&&TacticalCoordinateMesh.alpha(-1)==0,"confirmation cue cannot persist");
    }
}
