package cn.academy.port.city;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Furnishing and service points are generated column by column with the city. */
final class CityServicePlan {
    enum Role { NONE, MENTOR, LIBRARIAN, EXCHANGE, CANTEEN, FOOD_STREET, WEST, EAST, AIRPORT,
        CAMPUS_MEDIC, FIELD_MEDIC, ENGINEER, RESEARCH_CONTACT, CURATOR, LOGISTICS, COACH, RESERVOIR,
        CAMPUS_DESK, CENTRAL_DESK, OFFICE_DESK }
    record Site(Role role, BlockPos home, BlockPos shelf, BlockPos waiting, CityNpcEntity.Look look, String shop) {}
    static final List<Site> SITES = List.of(
            new Site(Role.MENTOR, new BlockPos(-227,0,-97), new BlockPos(-229,0,-101), null, CityNpcEntity.Look.TEACHER, ""),
            new Site(Role.LIBRARIAN, new BlockPos(-224,0,-41), new BlockPos(-227,0,-41), null, CityNpcEntity.Look.STUDENT_BOOKS, ""),
            new Site(Role.EXCHANGE, new BlockPos(-76,0,433), new BlockPos(-79,0,433), null, CityNpcEntity.Look.RAIL_STAFF, "exchange"),
            new Site(Role.CANTEEN, new BlockPos(-160,0,-106), new BlockPos(-158,0,-111), new BlockPos(-160,0,-101), CityNpcEntity.Look.SHOP_CLERK, "canteen"),
            new Site(Role.FOOD_STREET, new BlockPos(580,0,-1030), new BlockPos(582,0,-1033), new BlockPos(580,0,-1026), CityNpcEntity.Look.VENDOR, "food_street"),
            new Site(Role.WEST, new BlockPos(-85,0,568), new BlockPos(-88,0,579), new BlockPos(-85,0,565), CityNpcEntity.Look.SHOP_CLERK, "west"),
            new Site(Role.EAST, new BlockPos(-45,0,568), new BlockPos(-48,0,579), new BlockPos(-45,0,565), CityNpcEntity.Look.BARISTA, "east"),
            new Site(Role.AIRPORT, new BlockPos(1607,0,-1820), new BlockPos(1609,0,-1824), new BlockPos(1607,0,-1816), CityNpcEntity.Look.AIRPORT_STAFF, "airport"),
            new Site(Role.CAMPUS_MEDIC, new BlockPos(-234,0,-51), new BlockPos(-237,0,-51), null, CityNpcEntity.Look.MEDIC, ""),
            new Site(Role.FIELD_MEDIC, new BlockPos(576,0,-1842), new BlockPos(574,0,-1842), null, CityNpcEntity.Look.MEDIC, ""),
            new Site(Role.ENGINEER, new BlockPos(-794,0,-1460), new BlockPos(-792,0,-1460), null, CityNpcEntity.Look.ENGINEER, ""),
            new Site(Role.RESEARCH_CONTACT, new BlockPos(-225,0,-1843), new BlockPos(-227,0,-1843), null, CityNpcEntity.Look.RESEARCHER, ""),
            new Site(Role.CURATOR, new BlockPos(-225,0,-1045), new BlockPos(-227,0,-1045), null, CityNpcEntity.Look.RESEARCHER, ""),
            new Site(Role.LOGISTICS, new BlockPos(1375,0,-1042), new BlockPos(1377,0,-1042), null, CityNpcEntity.Look.TECHNICIAN, ""),
            new Site(Role.COACH, new BlockPos(-1825,0,-244), new BlockPos(-1827,0,-244), null, CityNpcEntity.Look.ATHLETE, ""),
            new Site(Role.RESERVOIR, new BlockPos(-1312,9,-820), new BlockPos(-1310,9,-820), null, CityNpcEntity.Look.ENGINEER, ""),
            new Site(Role.CAMPUS_DESK,new BlockPos(111,0,85),new BlockPos(110,0,81),null,CityNpcEntity.Look.STUDENT_BOOKS,""),
            new Site(Role.CENTRAL_DESK,new BlockPos(111,0,485),new BlockPos(110,0,481),null,CityNpcEntity.Look.RAIL_STAFF,""),
            new Site(Role.OFFICE_DESK,new BlockPos(111,0,1085),new BlockPos(110,0,1081),null,CityNpcEntity.Look.OFFICE_STAFF,"")
    );
    static final BlockPos LAB_LECTERN = new BlockPos(-224,0,-95);
    static final BlockPos LIBRARY_TRANSFER = new BlockPos(-234,0,-44), LIBRARY_WIRING = new BlockPos(-214,0,-44);
    private static final BlockState MARKER = Blocks.CYAN_GLAZED_TERRACOTTA.defaultBlockState();
    private CityServicePlan() {}

    static boolean marker(BlockState state) { return state.is(Blocks.CYAN_GLAZED_TERRACOTTA); }
    static boolean open(Role role, long dayTime) {
        long time = Math.floorMod(dayTime, 24000);
        return switch (role) {
            case MENTOR, CANTEEN, EAST -> time < 13000;
            case FOOD_STREET -> time < 18000;
            default -> true;
        };
    }
    static Site site(Role role) { return SITES.stream().filter(site -> site.role() == role).findFirst().orElseThrow(); }
    record Column(Map<Integer, BlockState> states, int top) { BlockState block(int y) { return states.get(y); } }
    private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    private static final class Layout {
        static final Map<Long, Column> COLUMNS = build();
        private static Map<Long, Column> build() {
            var columns = new HashMap<Long, Map<Integer, BlockState>>();
            // The station service kiosk occupies a previously unfurnished plaza edge.
            for (int x=-82; x<=-71; x++) for (int z=428; z<=437; z++) {
                put(columns, new BlockPos(x,-1,z), Blocks.SMOOTH_QUARTZ.defaultBlockState());
                for (int y=0; y<=3; y++) put(columns, new BlockPos(x,y,z), Blocks.AIR.defaultBlockState());
                if (z==437 || (x==-82 || x==-71) && z>=434)
                    for (int y=0; y<=3; y++) put(columns,new BlockPos(x,y,z),Blocks.WHITE_CONCRETE.defaultBlockState());
                put(columns, new BlockPos(x,4,z), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            }
            for (var site : SITES) {
                if (site.role().ordinal() >= Role.FIELD_MEDIC.ordinal()&&site.role().ordinal()<=Role.RESERVOIR.ordinal()) {
                    // Compact staffed posts on the existing landmark forecourts. D22 also joins the approach path.
                    var home=site.home();
                    for(int dx=-3;dx<=3;dx++) for(int dz=-2;dz<=2;dz++) {
                        put(columns,home.offset(dx,-1,dz),Blocks.SMOOTH_QUARTZ.defaultBlockState());
                        for(int dy=0;dy<=3;dy++) put(columns,home.offset(dx,dy,dz),Blocks.AIR.defaultBlockState());
                        put(columns,home.offset(dx,4,dz),Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
                    }
                    for(int dx:new int[]{-3,3}) put(columns,home.offset(dx,0,-2),Blocks.BARREL.defaultBlockState());
                    put(columns,home.offset(0,0,2),Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
                    put(columns,home.offset(0,3,-1),CityBlocks.block("ceiling_light").defaultBlockState());
                    if(site.role()==Role.ENGINEER) for(int x=-799;x<=-797;x++) for(int z=-1462;z<=-1458;z++) {
                        put(columns,new BlockPos(x,-1,z),Blocks.SMOOTH_QUARTZ.defaultBlockState());
                        put(columns,new BlockPos(x,0,z),Blocks.AIR.defaultBlockState());
                        put(columns,new BlockPos(x,1,z),Blocks.AIR.defaultBlockState());
                    }
                }
                for (var point : new BlockPos[]{site.home(),site.shelf(),site.waiting()}) if (point != null) {
                    put(columns,point,Blocks.AIR.defaultBlockState());
                    put(columns,point.above(),Blocks.AIR.defaultBlockState());
                }
                put(columns, site.home().below(), MARKER);
            }
            var lectern = Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH)
                    .setValue(LecternBlock.HAS_BOOK, true);
            put(columns, LAB_LECTERN, lectern);
            put(columns, LIBRARY_TRANSFER, lectern);
            put(columns, LIBRARY_WIRING, lectern);
            // Reception desk beside the laboratory entrance, with the central aisle clear.
            counter(columns, -230, -228, -95, Blocks.SPRUCE_SLAB.defaultBlockState());
            // Library reception and book stacks.
            counter(columns, -228, -221, -43, Blocks.SPRUCE_SLAB.defaultBlockState());
            counter(columns, -237, -233, -49, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            put(columns,new BlockPos(-239,0,-51),Blocks.BARREL.defaultBlockState());
            for (int x : new int[]{-239,-238,-237,-213,-212,-211}) for (int y=0; y<=2; y++)
                put(columns, new BlockPos(x,y,-37), Blocks.BOOKSHELF.defaultBlockState());
            // Canteen, food street, both kiosks, central service and airport counters.
            counter(columns, -163, -157, -103, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            counter(columns, 579, 581, -1028, Blocks.SPRUCE_SLAB.defaultBlockState());
            counter(columns, -87, -83, 567, Blocks.SPRUCE_SLAB.defaultBlockState());
            counter(columns, -47, -43, 567, Blocks.SPRUCE_SLAB.defaultBlockState());
            counter(columns, -79, -73, 431, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            counter(columns, 1604, 1610, -1818, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            // Coloured shelves and overhead lights make the counters legible at a glance.
            shelf(columns, -164, -156, -113, Blocks.BARREL.defaultBlockState());
            shelf(columns, 577, 583, -1035, Blocks.BARREL.defaultBlockState());
            shelf(columns, -90, -80, 581, Blocks.BARREL.defaultBlockState());
            shelf(columns, -50, -40, 581, Blocks.BARREL.defaultBlockState());
            shelf(columns, 1603, 1611, -1826, Blocks.BARREL.defaultBlockState());
            BlockState lamp = CityBlocks.block("ceiling_light").defaultBlockState();
            for (var site : SITES) if (site.role()!=Role.MENTOR && site.role()!=Role.LIBRARIAN) {
                put(columns, site.home().above(4), lamp);
                if (site.waiting()!=null) put(columns, site.waiting().below(), Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
            }
            var result = new HashMap<Long, Column>();
            columns.forEach((k, v) -> result.put(k, new Column(Map.copyOf(v), v.keySet().stream().mapToInt(Integer::intValue).max().orElse(0))));
            return Map.copyOf(result);
        }
        private static void put(Map<Long, Map<Integer, BlockState>> columns, BlockPos pos, BlockState state) {
            columns.computeIfAbsent(key(pos.getX(),pos.getZ()), ignored -> new HashMap<>()).put(pos.getY(),state);
        }
        private static void counter(Map<Long, Map<Integer, BlockState>> columns, int x1, int x2, int z, BlockState block) {
            for (int x=x1; x<=x2; x++) put(columns, new BlockPos(x,0,z),block);
        }
        private static void shelf(Map<Long, Map<Integer, BlockState>> columns, int x1, int x2, int z, BlockState block) {
            for (int x=x1; x<=x2; x++) if ((x-x1)%2==0) for (int y=0; y<=1; y++) put(columns,new BlockPos(x,y,z),block);
        }
    }
    static Column at(int x, int z) { return Layout.COLUMNS.get(key(x,z)); }
    static void initialize(BlockPos pos, BlockEntity entity) {
        if (!(entity instanceof LecternBlockEntity lectern)) return;
        if (LAB_LECTERN.equals(pos)) lectern.setBook(CityGoods.referenceBook("ability_development_guide"));
        else if (LIBRARY_TRANSFER.equals(pos)) lectern.setBook(CityGoods.referenceBook("transfer_leaflet"));
        else if (LIBRARY_WIRING.equals(pos)) lectern.setBook(CityGoods.referenceBook("if_wiring_card"));
    }
}
