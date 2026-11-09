package cn.academy.port.client;

/** Engine-free lifetime fence for native witnesses. It never grants payment or changes movement. */
public final class WindPressureLiftTravelState {
    public record Frame(Object declaringClass, String method, String descriptor) {}
    public record Identity(Object client, Object world, Object player, Object connection, long generation) {
        private boolean valid() {
            return client != null && world != null && player != null && connection != null && generation >= 0;
        }
    }
    /** Opaque capabilities are accepted only by exact object identity, never by their numeric serial. */
    public static final class Run {
        private final Identity identity;
        private final Object thread;
        private final long serial;
        private int planned = -1, nextIndex;
        private boolean invalid;
        private Run(Identity identity, Object thread, long serial) {
            this.identity = identity; this.thread = thread; this.serial = serial;
        }
        public long serial() { return serial; }
        public int plannedCount() { return planned; }
        public boolean catchup() { return planned > 2; }
    }
    public static final class Step {
        private final Run run;
        private final long serial;
        private final int index;
        private boolean retired;
        private Step(Run run, long serial, int index) { this.run = run; this.serial = serial; this.index = index; }
        public Run run() { return run; }
        public long serial() { return serial; }
        public int index() { return index; }
    }
    public static final class Travel {
        private final Step step;
        private final long serial;
        private Travel(Step step, long serial) { this.step = step; this.serial = serial; }
        public Step step() { return step; }
        public long serial() { return serial; }
    }
    private long nextRun, nextStep, nextTravel;
    private Run run;
    private Step step;
    private Travel travel;

    public Run beginRun(Identity identity, Object thread) {
        if (run != null) { invalidate(); return null; }
        if (identity == null || !identity.valid() || thread == null || nextRun == Long.MAX_VALUE) return null;
        return run = new Run(identity, thread, ++nextRun);
    }
    /** Observe the exact native Timer result; a duplicate plan cannot reset an index or allowance. */
    public boolean plan(Run candidate, int nativeCount, boolean ticking) {
        if (candidate == null || run != candidate || candidate.invalid) return false;
        if (candidate.planned != -1 || nativeCount < 0) { invalidate(); return false; }
        candidate.planned = ticking ? Math.min(10, nativeCount) : 0;
        return true;
    }
    public Step beginStep(Run candidate, Identity current, Object thread) {
        if (!ownsRun(candidate, current, thread)) return null;
        if (step != null || candidate.planned < 0 || candidate.nextIndex >= candidate.planned
                || candidate.catchup() || nextStep == Long.MAX_VALUE) { invalidate(); return null; }
        return step = new Step(candidate, ++nextStep, candidate.nextIndex++);
    }
    /** An unauthenticated public call cannot retire or steal the genuine native step. */
    public Travel beginTravel(Step candidate, Identity current, Object thread, boolean authenticNativeChain) {
        if (!authenticNativeChain || candidate == null || step != candidate
                || !ownsRun(candidate.run, current, thread)) return null;
        if (travel != null) { invalidate(); return null; }
        if (candidate.retired || nextTravel == Long.MAX_VALUE) return null;
        candidate.retired = true; // retire before any proposal/collision callback
        return travel = new Travel(candidate, ++nextTravel);
    }
    public boolean owns(Travel candidate, Identity current, Object thread) {
        return candidate != null && travel == candidate && step == candidate.step && step.retired
                && ownsRun(step.run, current, thread);
    }
    public void endTravel(Travel candidate) { if (candidate != null && travel == candidate) travel = null; }
    public void endStep(Step candidate) {
        if (candidate == null || step != candidate) return;
        if (travel != null) invalidate();
        travel = null; step = null;
    }
    public void endRun(Run candidate) {
        if (candidate == null || run != candidate) return;
        candidate.invalid = true; travel = null; step = null; run = null;
    }
    public void invalidate() { if (run != null) run.invalid = true; }
    public boolean allowsStart(Run candidate, Identity current, Object thread) {
        return ownsRun(candidate,current,thread) && candidate.planned >= 0 && candidate.planned <= 2;
    }
    private boolean ownsRun(Run candidate, Identity current, Object thread) {
        return candidate != null && run == candidate && !candidate.invalid && thread == candidate.thread
                && sameIdentity(candidate.identity, current);
    }
    public static boolean sameIdentity(Identity a, Identity b) {
        return a != null && b != null && a.valid() && b.valid() && a.client == b.client && a.world == b.world
                && a.player == b.player && a.connection == b.connection && a.generation == b.generation;
    }
    public static boolean stackPrefix(java.util.List<Frame> actual, java.util.List<Frame> expected) {
        if(actual==null || expected==null || expected.isEmpty() || expected.size()>40 || actual.size()<expected.size() || actual.size()>48)return false;
        for(int i=0;i<expected.size();i++) {
            Frame a=actual.get(i),e=expected.get(i);
            if(a==null || e==null || e.declaringClass==null || a.declaringClass!=e.declaringClass
                    || !java.util.Objects.equals(a.method,e.method) || !java.util.Objects.equals(a.descriptor,e.descriptor))return false;
        }
        return true;
    }
}
