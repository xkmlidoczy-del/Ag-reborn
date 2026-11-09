package cn.academy.port.fusion.phase;

/** Only the two random operations used by the 1.0.7 generator, in source order. */
public interface PhaseLiquidRandom {
    int nextInt(int bound);
    double nextDouble();
}
