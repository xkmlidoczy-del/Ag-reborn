/* AcademyCraft1.0.7 TerminalMouseHelper relative-input adaptation. GPLv3. */
package cn.academy.port.client.terminal;

/** Relative movement captured before Minecraft drains it; never differences an absolute native cursor. */
final class TerminalPointerInput {
    record Delta(double x,double y){}
    private double pendingX,pendingY;
    void capture(double x,double y){if(Double.isFinite(x)&&Double.isFinite(y)){pendingX+=x;pendingY+=y;}}
    Delta consume(boolean active){Delta result=new Delta(active?pendingX:0,active?pendingY:0);clear();return result;}
    void clear(){pendingX=pendingY=0;}
}
