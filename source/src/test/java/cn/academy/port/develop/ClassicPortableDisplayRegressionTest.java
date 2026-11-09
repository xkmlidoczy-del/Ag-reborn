package cn.academy.port.develop;

/** Pure finite IF tooltip/gauge/icon parity, including exact rounding boundaries and corrupt amounts. */
public final class ClassicPortableDisplayRegressionTest {
    private static int assertions;
    private static void yes(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        for(int amount=-1000;amount<=11000;amount++){
            double energy=amount+.25,bounded=Math.max(0,Math.min(10000,energy));int damage=(int)Math.round((1-bounded/10000)*13);
            yes(ClassicPortableDisplay.damage(energy)==damage,"source Math.round");yes(ClassicPortableDisplay.width(energy)==13-damage,"source13-step bar");
            yes(ClassicPortableDisplay.visible(energy)==(damage>0),"full gauge hidden");yes(ClassicPortableDisplay.icon(energy)==(damage<3?2:damage>10?0:1),"original charge icon thresholds");
            yes(ClassicPortableDisplay.tooltip(energy).equals(String.format(java.util.Locale.ROOT,"%.0f/10000 IF",bounded)),"source IF tooltip format");
        }
        yes(ClassicPortableDisplay.tooltip(5000).equals("5000/10000 IF"),"half finite charge");yes(ClassicPortableDisplay.tooltip(0).equals("0/10000 IF"),"empty finite charge");
        yes(ClassicPortableDisplay.tooltip(Double.NaN).equals("0/10000 IF"),"NaN bounded");yes(ClassicPortableDisplay.damage(Double.POSITIVE_INFINITY)==13,"nonfinite empty");
        yes(!ClassicPortableDisplay.visible(10000)&&ClassicPortableDisplay.width(10000)==13,"full hidden gauge");
        yes(ClassicPortableDisplay.width(0)==0&&ClassicPortableDisplay.visible(0),"empty zero-width gauge");
        yes(ClassicPortableDisplay.hue(0)==0&&Math.abs(ClassicPortableDisplay.hue(10000)-1/3f)<1e-7,"source red to green HSV");
        for(int damage=1;damage<=13;damage++){
            double boundary=(1-(damage-.5)/13)*10000;
            yes(ClassicPortableDisplay.damage(boundary-.00001)==damage,"below rounding midpoint");yes(ClassicPortableDisplay.damage(boundary+.00001)==damage-1,"above rounding midpoint");
        }
        System.out.println("ClassicPortableDisplayRegressionTest: "+assertions+" assertions passed");
    }
}
