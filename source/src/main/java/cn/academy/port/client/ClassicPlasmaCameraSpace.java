/* Original PlasmaBodyRenderer camera-space contract adaptation. GPLv3; see NOTICE. */
package cn.academy.port.client;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Matches the vertex shader's ModelViewMat * pose order for density uniforms. */
public final class ClassicPlasmaCameraSpace {
    private ClassicPlasmaCameraSpace() {}

    public static Vector4f transform(Matrix4f pose, Matrix4f modelView,
                                     double x, double y, double z, float size) {
        // Preserve this legacy renderer's existing world-float narrowing. Precision
        // modernization is a separate change; this repair only restores the missing matrix.
        Vector4f result = new Vector4f((float) x, (float) y, (float) z, 1);
        pose.transform(result);
        modelView.transform(result);
        result.w = size;
        // Shader upload and fragment both retain the original deliberate z negation.
        return result;
    }
}
