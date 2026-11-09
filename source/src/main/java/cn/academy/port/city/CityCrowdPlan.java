package cn.academy.port.city;

import cn.academy.port.city.CityNpcEntity.Activity;
import cn.academy.port.city.CityNpcEntity.Look;
import java.util.List;
import net.minecraft.core.BlockPos;

/** District quotas are targets for one active neighborhood, with shared limits in CityPopulation. */
public final class CityCrowdPlan {
    static final int NEIGHBORHOOD = 96;

    public enum Period {
        MORNING, DAY, EVENING, NIGHT;

        public static Period at(long dayTime) {
            int tick = (int) Math.floorMod(dayTime, 24_000L);
            if (tick < 3_000 || tick >= 23_000) return MORNING;
            if (tick < 9_500) return DAY;
            if (tick < 14_000) return EVENING;
            return NIGHT;
        }
    }

    public enum Role {
        STUDENT(11, 0.90, Activity.CHAT, Activity.READ, Activity.DRINK, Look.STUDENT_BAG, Look.STUDENT_SPORT, Look.STUDENT_BOOKS, Look.STUDENT_BLAZER, Look.STUDENT_CASUAL),
        OFFICE(17, 1.06, Activity.PHONE, Activity.PHONE, Activity.CHECK, Look.COMMUTER, Look.OFFICE_STAFF),
        RESIDENT(9, 0.77, Activity.CHAT, Activity.EAT, Activity.WAVE, Look.RESIDENT, Look.VISITOR),
        VISITOR(13, 0.84, Activity.PHOTO, Activity.CHAT, Activity.DRINK, Look.VISITOR, Look.CASUAL_VISITOR, Look.TRAVELER),
        SHOPKEEPER(5, 0.77, Activity.TEND, Activity.TEND, Activity.WAVE, Look.SHOP_CLERK, Look.VENDOR, Look.BARISTA),
        RESEARCHER(10, 0.85, Activity.CHECK, Activity.READ, Activity.CHAT, Look.RESEARCHER, Look.TEACHER),
        GUARD(12, 0.80, Activity.SCAN, Activity.CHECK, Activity.SCAN, Look.GUARD),
        TECHNICIAN(9, 0.79, Activity.CHECK, Activity.TEND, Activity.SCAN, Look.TECHNICIAN, Look.ENGINEER),
        ATHLETE(16, 1.15, Activity.STRETCH, Activity.STRETCH, Activity.CHAT, Look.ATHLETE),
        PERFORMER(6, 0.80, Activity.PERFORM, Activity.WAVE, Activity.PERFORM, Look.PERFORMER),
        AIRPORT(9, 0.92, Activity.CHECK, Activity.SCAN, Activity.WAVE, Look.AIRPORT_STAFF),
        RAIL(10, 0.84, Activity.CHECK, Activity.SCAN, Activity.WAVE, Look.RAIL_STAFF),
        MEDICAL(8, 0.80, Activity.CHECK, Activity.READ, Activity.CHAT, Look.MEDIC);

        final int radius;
        final double speed;
        final List<Activity> activities;
        final List<Look> looks;

        Role(int radius, double speed, Activity first, Activity second, Activity third, Look... looks) {
            this.radius = radius;
            this.speed = speed;
            this.activities = List.of(first, second, third);
            this.looks = List.of(looks);
        }

        public List<Look> looks() { return looks; }
    }

    public record Profile(int morning, int day, int evening, int night, List<Role> roles) {
        public int target(Period period) {
            return switch (period) {
                case MORNING -> morning;
                case DAY -> day;
                case EVENING -> evening;
                case NIGHT -> night;
            };
        }
    }

    private static final Profile[] DISTRICTS = {
            null,
            people(6, 8, 5, 2, Role.OFFICE, Role.OFFICE, Role.GUARD, Role.RESIDENT),                // D1 civic
            people(4, 4, 3, 1, Role.TECHNICIAN, Role.TECHNICIAN, Role.GUARD, Role.RESEARCHER),     // D2 trials
            people(4, 8, 6, 2, Role.RESEARCHER, Role.VISITOR, Role.VISITOR, Role.GUARD),           // D3 expo
            people(5, 11, 13, 5, Role.SHOPKEEPER, Role.VISITOR, Role.RESIDENT, Role.OFFICE),      // D4 food
            people(8, 10, 7, 2, Role.STUDENT, Role.RESEARCHER, Role.STUDENT, Role.RESIDENT),      // D5 university
            people(4, 9, 14, 8, Role.VISITOR, Role.PERFORMER, Role.VISITOR, Role.SHOPKEEPER),    // D6 entertainment
            people(13, 9, 12, 2, Role.STUDENT, Role.STUDENT, Role.STUDENT, Role.RESEARCHER, Role.MEDICAL, Role.GUARD), // D7 schools
            people(6, 6, 8, 2, Role.RESIDENT, Role.RESIDENT, Role.STUDENT, Role.SHOPKEEPER),      // D8 housing
            people(7, 9, 10, 3, Role.RESIDENT, Role.SHOPKEEPER, Role.OFFICE, Role.VISITOR),       // D9 local retail
            people(6, 6, 8, 2, Role.RESIDENT, Role.OFFICE, Role.STUDENT),                         // D10 housing
            people(4, 5, 6, 1, Role.RESIDENT, Role.RESIDENT, Role.SHOPKEEPER),                     // D11 quiet streets
            people(6, 7, 8, 2, Role.RESIDENT, Role.STUDENT, Role.SHOPKEEPER, Role.STUDENT),        // D12 learning
            people(5, 7, 8, 2, Role.RESIDENT, Role.OFFICE, Role.VISITOR),                          // D13 mixed
            people(3, 6, 6, 1, Role.RESIDENT, Role.VISITOR, Role.RESIDENT),                        // D14 edge
            people(12, 15, 15, 7, Role.OFFICE, Role.RAIL, Role.VISITOR, Role.SHOPKEEPER, Role.GUARD), // D15 business
            people(6, 8, 9, 3, Role.RESIDENT, Role.SHOPKEEPER, Role.VISITOR, Role.OFFICE),        // D16 local shops
            people(6, 5, 6, 2, Role.TECHNICIAN, Role.GUARD, Role.TECHNICIAN, Role.OFFICE),       // D17 freight
            people(5, 7, 6, 2, Role.RESIDENT, Role.RESEARCHER, Role.TECHNICIAN, Role.MEDICAL),    // D18 mixed
            people(5, 6, 7, 2, Role.RESIDENT, Role.STUDENT, Role.VISITOR),                         // D19 community
            people(6, 10, 11, 3, Role.ATHLETE, Role.ATHLETE, Role.TECHNICIAN, Role.VISITOR),      // D20 sports
            people(2, 4, 3, 1, Role.TECHNICIAN, Role.VISITOR, Role.RESEARCHER),                   // D21 reservoir
            people(1, 2, 1, 1, Role.TECHNICIAN),                                                  // D22 wind farm
            people(5, 9, 8, 4, Role.AIRPORT, Role.VISITOR, Role.AIRPORT, Role.GUARD)              // D23 terminal
    };

    private CityCrowdPlan() {}

    private static Profile people(int morning, int day, int evening, int night, Role... roles) {
        return new Profile(morning, day, evening, night, List.of(roles));
    }

    public static Profile profile(int district) { return DISTRICTS[district]; }

    record Hub(int cellX, int cellZ) {
        int x() {
            if (cellX == -1 && (cellZ == -1 || cellZ == 5)) return -65;
            int[] anchor = CityDistrictLandmarks.crowdAnchor(cellX, cellZ);
            if (anchor != null) return anchor[0];
            return cellX * NEIGHBORHOOD + NEIGHBORHOOD / 2;
        }
        int z() {
            if (cellX == -1 && cellZ == -1) return -26;
            if (cellX == -1 && cellZ == 5) return 569;
            int[] anchor = CityDistrictLandmarks.crowdAnchor(cellX, cellZ);
            if (anchor != null) return anchor[1];
            return cellZ * NEIGHBORHOOD + NEIGHBORHOOD / 2;
        }
        int district() { return CityPlan.district(x(), z()); }
        long seed(long worldSeed, int slot) {
            return worldSeed ^ (cellX * 341873128712L) ^ (cellZ * 132897987541L) ^ (slot * 0x9E3779B97F4A7C15L);
        }
    }

    /** The street plan determines intended floor height. The server still checks the actual blocks. */
    public static BlockPos ground(long planSeed, int district, int x, int z, boolean crossing) {
        var sample = CityPlan.sample(planSeed, x, z);
        if (sample.district() != district) return null;
        if (district == 7 || district == 15) {
            var route = CitySampleRoute.at(x, z);
            if (route != null && "minecraft:air".equals(route.block(0))
                    && "minecraft:air".equals(route.block(1))) return new BlockPos(x, 0, z);
        }
        if (CityDistrictLandmarks.ownsLot(x, z)) {
            var landmark = CityDistrictLandmarks.at(planSeed, x, z);
            if (landmark != null && "minecraft:air".equals(landmark.block(0))
                    && "minecraft:air".equals(landmark.block(1))
                    && !"minecraft:water[level=0]".equals(landmark.block(-1))) return new BlockPos(x, 0, z);
            return null;
        }
        return switch (sample.kind()) {
            case SIDEWALK, PLAZA, PARK -> new BlockPos(x, 0, z);
            case TERRAIN -> sample.height() <= 18 ? new BlockPos(x, sample.height(), z) : null;
            case GROUND -> district == 22 ? new BlockPos(x, 0, z) : null;
            case ROAD, MARKING -> crossing && CityPlan.isLocalCrossing(x, z) ? new BlockPos(x, 0, z) : null;
            default -> null;
        };
    }
}
