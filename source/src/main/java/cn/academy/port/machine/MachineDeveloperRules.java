/* AcademyCraft 1.0.7 BlockDeveloper and LambdaLib BlockMulti geometry. See NOTICE. */
package cn.academy.port.machine;

import java.util.List;

/** Engine-independent, exact source eight-cell placement and yaw orientation. */
public final class MachineDeveloperRules {
    public enum Facing { NORTH, EAST, SOUTH, WEST }
    public record Cell(int x, int y, int z) {}
    public static final List<Cell> CELLS = List.of(new Cell(0,0,0),new Cell(0,1,0),
            new Cell(0,0,1),new Cell(0,1,1),new Cell(0,2,1),
            new Cell(0,0,2),new Cell(0,1,2),new Cell(0,2,2));
    private MachineDeveloperRules() {}
    public static Facing fromYaw(float yaw) {
        return Facing.values()[Math.floorMod((int)Math.floor(yaw * 4.0F / 360.0F + 0.5D),4)];
    }
    public static Cell offset(int part, Facing facing) {
        if(part<0 || part>=CELLS.size())throw new IllegalArgumentException("part must be 0..7");
        var cell=CELLS.get(part);
        return switch(facing) {
            case NORTH -> cell;
            case EAST -> new Cell(-cell.z(),cell.y(),cell.x());
            case SOUTH -> new Cell(-cell.x(),cell.y(),-cell.z());
            case WEST -> new Cell(cell.z(),cell.y(),-cell.x());
        };
    }
}
