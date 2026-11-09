/* AcademyCraft 1.0.7 TileMetalFormer literal work state, GPLv3. See NOTICE. */
package cn.academy.port.former;

/** Source scan/work ordering, deliberately including partial and blocked power debits. */
public final class ClassicMetalFormerWork {
    public static final double CAPACITY=3000, BANDWIDTH=50, CONSUME_PER_TICK=13.3;
    public static final int WORK_TICKS=60, SCAN_TICKS=5;
    public enum Mode { PLATE, INCISE, ETCH, REFINE }
    public interface Access {
        int recipeForInput(Mode mode);
        boolean accepts(int recipe,Mode mode);
        boolean outputAvailable(int recipe);
        double pullEnergy(double amount);
        void complete(int recipe);
    }
    private Mode mode=Mode.PLATE;
    private int current=-1,counter;
    public void tick(Access access) {
        if(current>=0) {
            // The source pulls first. Even removed input, mode mismatch and full output consume IF.
            if(access.pullEnergy(CONSUME_PER_TICK)==CONSUME_PER_TICK&&!actionBlocked(access)) {
                if(++counter==WORK_TICKS){access.complete(current);current=-1;counter=0;}
            } else {current=-1;counter=0;}
        } else if(++counter==SCAN_TICKS){current=access.recipeForInput(mode);counter=0;}
    }
    public boolean actionBlocked(Access access){return current<0||!access.accepts(current,mode)||!access.outputAvailable(current);}
    public void cycleMode(int delta){int next=mode.ordinal()+delta;if(next>=Mode.values().length)next=0;else if(next<0)next=Mode.values().length-1;mode=Mode.values()[next];}
    /** Source saves mode only: current recipe and the shared idle/work counter never survive reload. */
    public void loadMode(int ordinal){mode=ordinal>=0&&ordinal<Mode.values().length?Mode.values()[ordinal]:Mode.PLATE;current=-1;counter=0;}
    public void reset(){current=-1;counter=0;}
    public Mode mode(){return mode;}
    public int recipe(){return current;}
    public int counter(){return counter;}
    public boolean working(){return current>=0;}
    public double progress(){return working()?(double)counter/WORK_TICKS:0;}
}
