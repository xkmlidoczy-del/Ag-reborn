/* AcademyCraft1.0.7 BlockNode/TileMatrix/BlockMatrix + LambdaLib BlockMulti, GPLv3. See NOTICE. */
package cn.academy.port.wireless;

import java.util.List;
import cn.academy.port.machine.MachineDeveloperRules;

public final class ClassicWirelessRules {
    public enum NodeType {
        BASIC(15000,150,9,5), STANDARD(50000,300,12,10), ADVANCED(200000,900,19,20);
        public final int energy,bandwidth,range,capacity;
        NodeType(int energy,int bandwidth,int range,int capacity){this.energy=energy;this.bandwidth=bandwidth;this.range=range;this.capacity=capacity;}
    }
    public record Cell(int x,int y,int z) {}
    public static final List<Cell> MATRIX_CELLS=List.of(new Cell(0,0,0),new Cell(0,0,1),new Cell(1,0,1),new Cell(1,0,0),new Cell(0,1,0),new Cell(0,1,1),new Cell(1,1,1),new Cell(1,1,0));
    private ClassicWirelessRules(){}
    public static Cell offset(int part,MachineDeveloperRules.Facing facing){var cell=MATRIX_CELLS.get(part);return switch(facing){case NORTH->cell;case EAST->new Cell(-cell.z(),cell.y(),cell.x());case SOUTH->new Cell(-cell.x(),cell.y(),-cell.z());case WEST->new Cell(cell.z(),cell.y(),-cell.x());};}
    public static double sanitize(double energy,double capacity){return Double.isFinite(energy)?Math.max(0,Math.min(capacity,energy)):0;}
    public static int level(double energy,double capacity){return (int)Math.min(4,Math.round(4*sanitize(energy,capacity)/capacity));}
    public static int matrixCapacity(int core,int plates){return core>=1&&core<=3&&plates==3?8*core:0;}
    public static double matrixBandwidth(int core,int plates){return core>=1&&core<=3&&plates==3?core*core*60:0;}
    public static double matrixRange(int core,int plates){return core>=1&&core<=3&&plates==3?24*Math.sqrt(core):0;}
}
