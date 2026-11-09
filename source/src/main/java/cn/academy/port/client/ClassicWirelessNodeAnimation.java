/* AcademyCraft1.0.7 GuiNode.StateContext exact one-step-per-render animation, GPLv3. */
package cn.academy.port.client;

/** Original state changes reset frame zero; a long frame advances once rather than skipping art frames. */
public final class ClassicWirelessNodeAnimation {
    private boolean linked;
    private boolean initialized;
    private int frame;
    private long lastChange;
    public int update(boolean connected,long now) {
        if(!initialized||linked!=connected){initialized=true;linked=connected;frame=0;lastChange=now;}
        if(now-lastChange>=(linked?800:3000)){lastChange=now;frame=(frame+1)%(linked?8:2);}
        return (linked?0:8)+frame;
    }
}
