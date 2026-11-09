package cn.academy.port.client;

/** QA-only, default-off observations. No permission, packet, player, world, or input writes. */
final class WindPressureLiftTrace {
    static final boolean ENABLED = Boolean.getBoolean("academy.qa.windLiftTrace");
    private static final int PER_ACTIVATION = 240, TOTAL_LIMIT = 1024;
    private static final System.Logger LOG = System.getLogger("academy.qa.wind_pressure_lift");
    private static long input, epoch, started;
    private static int used, total;
    private WindPressureLiftTrace() {}
    static synchronized void start(long nextInput,long nextEpoch) {
        if(!ENABLED || nextInput<=0 || nextEpoch<=0 || total>=TOTAL_LIMIT) return;
        if(input==nextInput && epoch==nextEpoch) return;
        input=nextInput;epoch=nextEpoch;used=0;started=System.nanoTime();
    }
    static synchronized void record(long currentInput,long currentEpoch,String stage,String detail) {
        if(!ENABLED || currentInput<=0 || currentEpoch<=0 || input<=0 || epoch<=0
                || currentInput!=input || currentEpoch!=epoch || used>=PER_ACTIVATION || total>=TOTAL_LIMIT) return;
        used++;total++;
        LOG.log(System.Logger.Level.INFO,"NEW443_QA input="+input+" epoch="+epoch+" line="+used+" total="+total
                +" elapsedUs="+Math.max(0,(System.nanoTime()-started)/1000)+" stage="+stage+" "+detail);
    }
}
