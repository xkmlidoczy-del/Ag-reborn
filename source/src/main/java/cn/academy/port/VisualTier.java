package cn.academy.port;

/**
 * Client-only presentation tier shared by every optional high-power material. Pure data: no gameplay,
 * server or damage geometry reads it. OFF keeps the exact classic/previous rendering; LOW is the
 * conservative frozen look; STANDARD and CINEMATIC widen only decorative envelopes and add decorative layers.
 */
public enum VisualTier {
    OFF(0, 1, 1, 1, 0, 1),
    LOW(1, 1, 1, 1, 0, 1),
    STANDARD(2, 1.8, 1.9, 2, 1, 1),
    CINEMATIC(3, 2.8, 3.0, 5, 3, .65);

    /** Multiplier for decorative half-widths (never for core/hit geometry). */
    public final double widthScale;
    /** Multiplier for the angular half-width cap that keeps ribbons from swallowing the view. */
    public final double angularScale;
    /** Multiplier for per-controller stroke/quad ceilings and the global quad ceiling. */
    public final int budgetScale;
    /** Number of extra decorative layer groups: 0 none, 1 corona+flares, 2 adds sparks and arcs, 3 adds particles, spokes, dispersion and ground light. */
    public final int layers;
    /** Additive-light gain: wide tiers lower per-layer brightness so stacked glow does not wash out to white. */
    public final double glow;
    private final int rank;

    VisualTier(int rank, double widthScale, double angularScale, int budgetScale, int layers, double glow) {
        this.glow = glow;
        this.rank = rank;
        this.widthScale = widthScale;
        this.angularScale = angularScale;
        this.budgetScale = budgetScale;
        this.layers = layers;
    }

    public boolean atLeast(VisualTier other) { return rank >= other.rank; }
    public boolean enhanced() { return layers > 0; }
}
