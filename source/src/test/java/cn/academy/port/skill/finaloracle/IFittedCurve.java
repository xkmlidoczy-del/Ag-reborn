/* Original LambdaLib1.2.3 unchanged class body. MIT, Copyright Lambda Innovation. Package/import-only relocation. */
package cn.academy.port.skill.finaloracle;
import java.io.IOException;
import java.util.*;
import com.google.gson.*;
import com.google.gson.stream.*;
import cn.academy.port.skill.finaloracle.OracleVec;
public interface IFittedCurve {
    
    public static class Point implements Comparable<Point> {
        public double x, y;
        public Point() {}
        public Point(double _x, double _y) {
            x = _x;
            y = _y;
        }
        
        @Override
        public int compareTo(Point arg0) {
            return ((Double) x).compareTo(arg0.x);
        }
    }
    
    /**
     * Add a point to fit for the curve.
     * Note that normally you shouldn't add the same point twice. Bad things like zero division might occur.
     */
    void addPoint(double x, double y);
    
    /**
     * Get the yval of the curve at given x coordinate.
     */
    double valueAt(double x);
    
    /**
     * @return The count of control points
     */
    int pointCount();
    
    /**
     * @return The control point with index i
     * @throws IndexOutofBoundsException
     */
    Point getPoint(int i);
    
    /**
     * Remove all the control points and reset the curve.
     */
    void reset();
    
}
