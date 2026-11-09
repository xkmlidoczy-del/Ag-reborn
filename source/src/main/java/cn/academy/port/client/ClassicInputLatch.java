/* Source-derived ClientRuntime realState/logicalState edge ordering. See NOTICE. */
package cn.academy.port.client;
public final class ClassicInputLatch {
    private boolean active, physical;
    public record Transition(boolean press,boolean tick,boolean release,boolean abort) {}
    public Transition update(boolean down,boolean permitted){
        boolean tick=down&&active&&permitted;
        boolean press=down&&!active&&!physical&&permitted;
        boolean release=!down&&active&&permitted;
        boolean abort=active&&!permitted;
        if(press)active=true;
        if(release||abort)active=false;
        physical=down;
        return new Transition(press,tick,release,abort);
    }
    public boolean active(){return active;}
    public void abort(){active=false;}
    /** A world/player replacement must not turn an already held key into a new action. */
    public void replaceSession(boolean down){active=false;physical=down;}
}
