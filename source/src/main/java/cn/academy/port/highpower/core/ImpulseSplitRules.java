package cn.academy.port.highpower.core;

import java.util.Optional;

/** NEW453 impulse split: one intercepted impact divided into two shares along two chosen directions, never more energy than the one. */
public final class ImpulseSplitRules {
    /** Directions closer than 20 degrees are one direction, not two. */
    private static final double MAX_COSINE = Math.cos(Math.toRadians(20));
    public record Pair(V3 first, V3 second) {}
    private ImpulseSplitRules() {}

    public static Optional<Pair> split(V3 momentum, V3 a, V3 b) {
        if (momentum == null || a == null || b == null || !momentum.finite() || !a.finite() || !b.finite()) return Optional.empty();
        double magnitude = momentum.length();
        V3 da = a.unit(), db = b.unit();
        if (!Double.isFinite(magnitude) || magnitude < 1e-12 || da.length() < .5 || db.length() < .5 || da.dot(db) > MAX_COSINE) return Optional.empty();
        // Wide sides retain the equal-energy split. Near-parallel sides must slow down so their vector sum cannot amplify impulse.
        double sum=da.plus(db).length();
        double factor=Math.nextDown(Math.sqrt(.5));
        if(sum>0)factor=Math.min(factor,Math.nextDown(1/sum));
        double share=magnitude*factor,before=momentum.dot(momentum);
        for(int i=0;i<16;i++){
            V3 first=da.scale(share),second=db.scale(share);
            if(first.plus(second).length()<=magnitude&&first.dot(first)+second.dot(second)<=before)
                return Optional.of(new Pair(first,second));
            share=Math.nextDown(share);
        }
        return Optional.empty();
    }

    public record DamageBudget(int total,int first,int second) {
        public DamageBudget { if(total<0||first<0||second<0||(long)first+second!=total)throw new IllegalArgumentException("split integer budget"); }
    }
    /** Exact pinned native order: length -> float; a present weapon also rounds base to float, then double product/clamp/ceil. */
    public static DamageBudget damageBudget(double speed,double base,boolean weaponPresent){
        int total=MultiAxisReflectionRules.nativeArrowDamage(speed,base,weaponPresent).intValueExact();
        int second=total/2;return new DamageBudget(total,total-second,second);
    }
    /** A float damage operand may round a large int cap upward. Always round the cap down instead. */
    public static float capNativeDamage(int budget,float ordinary){
        if(budget<0||!Float.isFinite(ordinary)||ordinary<=0)return 0;
        float cap=(float)budget;if((double)cap>budget)cap=Math.nextDown(cap);
        return Math.min(ordinary,cap);
    }

    /** Both sides must lead away from the caster's front axis' back half and be two distinct directions. */
    public static boolean validDirections(V3 front, V3 a, V3 b) {
        if (front == null || a == null || b == null || !front.finite() || !a.finite() || !b.finite()) return false;
        V3 f = front.unit(), da = a.unit(), db = b.unit();
        if (f.length() < .5 || da.length() < .5 || db.length() < .5 || da.dot(db) > MAX_COSINE) return false;
        return da.dot(f) > -.2 && db.dot(f) > -.2;
    }
}
