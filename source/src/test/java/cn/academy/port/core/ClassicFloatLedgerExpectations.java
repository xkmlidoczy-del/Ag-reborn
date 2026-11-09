/* Independent test arithmetic taken from unchanged AcademyCraft1.0.7 CPData/AbilityData. See NOTICE. */
package cn.academy.port.core;

/** Source expected ledgers only: no production calls/constants and no mutable gameplay state. */
public final class ClassicFloatLedgerExpectations {
    private ClassicFloatLedgerExpectations() {}
    /** CPData.java:374; successful/forced debits store each intermediate in float. */
    public static float paidCpAfter(double balance,double cost,int payments){
        float remaining=(float)balance,amount=(float)cost;
        for(int i=0;i<payments;i++)remaining=Math.max(0f,remaining-amount);
        return remaining;
    }
    public static float paidCpSequence(double balance,float... costs){
        float remaining=(float)balance;
        for(float cost:costs)remaining=Math.max(0f,remaining-cost);
        return remaining;
    }
    /** AbilityData.java:222-223; clamp the added amount before float cumulative addition. */
    public static float masteryAfter(double initial,float award,int awards){
        float mastery=(float)initial;
        for(int i=0;i<awards;i++)mastery+=Math.min(1f-mastery,award);
        return mastery;
    }
    /** AbilityData.java:316, default category/global multipliers1; full awards even at saturated mastery. */
    public static float progressAfter(double initial,float award,int awards){
        float progress=(float)initial;
        for(int i=0;i<awards;i++)progress+=award;
        return progress;
    }
    /** CPData.java:338 and258, original default maxcp_incr_rate float getter. */
    public static float cpTrainingAfter(double initial,float cost,int payments,float cap){
        float growth=(float)initial;
        for(int i=0;i<payments;i++)growth=Math.min(cap,growth+cost*.0025f);
        return growth;
    }
}
