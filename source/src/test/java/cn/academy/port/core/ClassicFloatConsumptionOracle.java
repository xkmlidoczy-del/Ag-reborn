/* Independent transcription of canonical CPData.java:291-321,337-347,370-400.
 * Oracle only: no production classes/constants. Original immutable sources and hashes are packaged. */
package cn.academy.port.core;
import java.util.function.Consumer;
final class ClassicFloatConsumptionOracle {
    static final float[] CP_CAP={0,900,1000,1500,1700,12000},O_CAP={0,40,70,80,100,500};
    static final class Cost {float cp,overload;Cost(float cp,float overload){this.cp=cp;this.overload=overload;}}
    float cp,overload,maxCp=8000,maxOverload=500,extraCp,extraOverload;
    int level=5,cpDelay,overloadDelay;boolean fine=true;
    boolean perform(float cp,float overload,boolean force,boolean creative,Consumer<Cost> event,Runnable onOverload){
        Cost result=new Cost(cp,overload);event.accept(result);cp=result.cp;overload=result.overload;
        boolean success;
        if(!creative){success=consumeCP(cp,force);if(success)addOverload(overload,onOverload);}else success=true;
        if(success){extraCp=Math.min(CP_CAP[level],extraCp+cp*.0025F);float add=clampf(0,10,overload*.0058F);extraOverload+=add;if(extraOverload>O_CAP[level])extraOverload=O_CAP[level];}
        return success;
    }
    private boolean consumeCP(float amount,boolean force){if(!force&&cp<amount)return false;cp=Math.max(0,cp-amount);cpDelay=15;return true;}
    private void addOverload(float amount,Runnable callback){overload=Math.min(maxOverload+extraOverload,overload+amount);overloadDelay=32;if(overload==maxOverload+extraOverload){callback.run();fine=false;}}
    private static float clampf(float min,float max,float value){if(value<min)return min;else if(value>max)return max;else return value;}
}
