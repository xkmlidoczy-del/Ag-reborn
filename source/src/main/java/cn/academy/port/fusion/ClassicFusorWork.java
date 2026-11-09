/* AcademyCraft1.0.7 TileImagFusor work-state arithmetic. GPLv3. See NOTICE. */
package cn.academy.port.fusion;

/** Original tick ordering, including blocked-work energy debit and nonpersisted progress. */
public final class ClassicFusorWork {
    public static final double CAPACITY=2000, BANDWIDTH=50, CONSUME_PER_TICK=12, WORK_SPEED=1.0/120;
    public static final int TANK_SIZE=8000, PER_UNIT=1000;
    public interface Access {
        int recipeForInput();
        boolean inputMatches(int recipe);
        boolean enoughInput(int recipe);
        boolean outputTypeMatches(int recipe);
        boolean outputAvailable(int recipe);
        int liquid();
        double pullEnergy(double amount);
        void complete(int recipe);
    }
    private int recipe=-1, checkCooldown=10;
    private double progress;
    public static int liquidRequired(int recipe) { return switch(recipe){case 0->3000;case 1->8000;default->0;}; }
    public int recipe(){return recipe;}
    public double progress(){return recipe>=0?progress:0;}
    public boolean working(){return recipe>=0;}
    public boolean actionBlocked(Access access){return recipe<0||!access.enoughInput(recipe)||!access.outputAvailable(recipe)||access.liquid()<liquidRequired(recipe);}
    public void reset(){recipe=-1;progress=0;checkCooldown=10;}
    public void tick(Access access) {
        if(recipe<0&&--checkCooldown<=0){checkCooldown=10;int matched=access.recipeForInput();if(matched>=0&&matched<=1){recipe=matched;progress=0;}}
        if(recipe<0)return;
        // Exact source short-circuit ordering: bad input consumes no power; low liquid/output mismatch
        // happens after the non-atomic 12 IF pull and therefore still drains the source buffer.
        if(!access.inputMatches(recipe)||access.pullEnergy(CONSUME_PER_TICK)!=CONSUME_PER_TICK||access.liquid()<liquidRequired(recipe)||!access.outputTypeMatches(recipe)){recipe=-1;progress=0;return;}
        if(!actionBlocked(access)){
            progress+=WORK_SPEED;
            if(progress>=1){int done=recipe;access.complete(done);recipe=-1;progress=0;checkCooldown=0;}
        }
    }
}
