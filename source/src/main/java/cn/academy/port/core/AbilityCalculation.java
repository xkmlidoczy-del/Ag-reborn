/* Classic CalcEvent calculation seam, adapted under GPLv3. See NOTICE. */
package cn.academy.port.core;
/** Pure common calculation requests; hooks are transient and never serialized. */
public final class AbilityCalculation {
    private AbilityCalculation() {}
    public enum Kind { MAX_CP, MAX_OVERLOAD, CP_RECOVERY, OVERLOAD_RECOVERY }
    public static final class Request {
        public final Kind kind;
        public float value;
        private final double initial;
        private final int initialBits;
        public Request(Kind kind,double initial) {
            this.kind=kind;this.initial=initial;value=(float)initial;initialBits=Float.floatToRawIntBits(value);
        }
        /** Keep the established ledger precision when no calculation listener changes the source float. */
        public double result() {
            double result=Float.floatToRawIntBits(value)==initialBits?initial:value;
            return Double.isFinite(result)&&result>=0?result:0;
        }
    }
    @FunctionalInterface public interface Mutation { void post(Request request); }
}
