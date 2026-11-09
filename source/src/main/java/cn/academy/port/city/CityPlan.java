package cn.academy.port.city;

import cn.academy.port.train.TrainLine;
import cn.academy.port.train.AirportLine;
import cn.academy.port.train.NorthSouthLine;
import cn.academy.port.train.CityRingLine;
import cn.academy.port.train.CityMetroLine;
import cn.academy.port.train.CityMetroNorthLine;
import cn.academy.port.train.CityMetroAirportLine;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Grey-box plan of the story city: a 5x5 grid of 800-block cells with two corners cut away (23 school districts),
 * 24-block arterials on the cell borders, a 64-block street grid inside each cell and per-district massing.
 * Pure function of (seed, x, z): any chunk can be generated on its own and the same seed always gives the same city.
 * District placement is an adaptation, not the canon map (see docs/STORY_CITY_MASTER_PLAN_20261103.md).
 */
public final class CityPlan {
    public static final int CELL = 800;
    public static final int HALF = 2000;
    public static final int ARTERIAL_HALF = 12;
    public static final int BLOCK = 64;

    /** Row (north to south) by column (west to east); 0 is sea. */
    private static final int[][] GRID = {
            {0, 22, 2, 18, 23},
            {21, 19, 3, 4, 17},
            {20, 1, 7, 6, 16},
            {8, 5, 15, 9, 10},
            {11, 12, 13, 14, 0}};

    public enum Kind { SEA, WATER, ROAD, MARKING, SIDEWALK, PLAZA, PARK, GROUND, BUILDING, LANDMARK, TERRAIN, RUNWAY, TURBINE }

    /** height: blocks above the ground surface (y=0 is the first block above it) for BUILDING, LANDMARK, TERRAIN and TURBINE. */
    /**
     * a, b: position inside the building footprint (0..bw-1, 0..bd-1); bw, bd: footprint size, 0 when the sample has no facade;
     * hash: per-lot value for deterministic variation. WATER uses negative height for depth in blocks.
     */
    public record Sample(Kind kind, int district, int height, int a, int b, int bw, int bd, long hash) {
        public Sample(Kind kind, int district, int height) { this(kind, district, height, 0, 0, 0, 0, 0L); }
    }

    private CityPlan() {}

    public static int district(int x, int z) {
        if (x < -HALF || x >= HALF || z < -HALF || z >= HALF) return 0;
        return GRID[Math.floorDiv(z + HALF, CELL)][Math.floorDiv(x + HALF, CELL)];
    }

    private static long mix(long a) {
        a += 0x9E3779B97F4A7C15L;
        a = (a ^ (a >>> 30)) * 0xBF58476D1CE4E5B9L;
        a = (a ^ (a >>> 27)) * 0x94D049BB133111EBL;
        return a ^ (a >>> 31);
    }

    private static double rand(long seed, long a, long b, long c) {
        return (mix(seed ^ mix(a * 31 + b * 131071 + c * 8191)) >>> 11) / (double) (1L << 53);
    }

    private static double smooth(long seed, double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = rand(seed, x0, z0, 1), b = rand(seed, x0 + 1, z0, 1), c = rand(seed, x0, z0 + 1, 1), d = rand(seed, x0 + 1, z0 + 1, 1);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    public static Sample sample(long seed, int x, int z) {
        int d = district(x, z);
        if (d == 0) return new Sample(Kind.SEA, 0, 0);
        if (CityTransit.groundConcourse(x, z)) return new Sample(Kind.PLAZA, d, 0);
        int lx = Math.floorMod(x + HALF, CELL), lz = Math.floorMod(z + HALF, CELL);
        boolean ax = lx < ARTERIAL_HALF || lx >= CELL - ARTERIAL_HALF, az = lz < ARTERIAL_HALF || lz >= CELL - ARTERIAL_HALF;
        if (ax || az) {                                              // arterial road with a dashed centre line on the cell border
            boolean line = (!ax || !az) && ((ax && (lx == 0 || lx == CELL - 1) && (lz / 4) % 2 == 0)
                    || (az && (lz == 0 || lz == CELL - 1) && (lx / 4) % 2 == 0));
            return new Sample(line ? Kind.MARKING : Kind.ROAD, d, 0);
        }
        int u = lx - ARTERIAL_HALF, w = lz - ARTERIAL_HALF;
        long cell = Math.floorDiv(z + HALF, CELL) * 8L + Math.floorDiv(x + HALF, CELL);
        if (d == 21) return mountain(seed, x, z, u, w);
        if (d == 22) {                                               // wind-turbine forest
            boolean pole = Math.floorMod(x, 24) == 12 && Math.floorMod(z, 24) == 12;
            return pole ? new Sample(Kind.TURBINE, d, 40 + (int) (rand(seed, x, z, 7) * 30)) : new Sample(Kind.GROUND, d, 0);
        }
        if (d == 23) return airfield(u, w, d);
        if (d == 7 && Math.abs(u - 388) < 96 && Math.abs(w - 388) < 96) return new Sample(Kind.PLAZA, d, 0);
        int bx = u / BLOCK, bz = w / BLOCK, ru = u % BLOCK, rw = w % BLOCK;
        if (ru < 8 || rw < 8) {
            boolean zebra = (ru < 8 && rw >= 8 && rw < 12 && ru % 2 == 0)
                    || (rw < 8 && ru >= 8 && ru < 12 && rw % 2 == 0);
            return new Sample(zebra ? Kind.MARKING : Kind.ROAD, d, 0);
        }
        if (ru < 12 || ru >= 60 || rw < 12 || rw >= 60) return new Sample(Kind.SIDEWALK, d, 0);
        // Reserve whole lots, preserving the road grid, so the new viaduct never cuts through a generated tower.
        if (CityAirportTransit.overlapsLot(x - ru + 12, z - rw + 12, 48, 48)
                || CityNorthSouthTransit.overlapsLot(x - ru + 12, z - rw + 12, 48, 48)
                || CityRingTransit.overlapsLot(x - ru + 12, z - rw + 12, 48, 48)
                || CityMetroTransit.entranceReserved(x - ru + 12, z - rw + 12, 48, 48) || CityMetroNorthTransit.entranceReserved(x - ru + 12, z - rw + 12, 48, 48) || CityMetroAirportTransit.entranceReserved(x - ru + 12, z - rw + 12, 48, 48)) {
            // Alternate paved squares with green space instead of leaving a continuous bare strip.
            Kind openSpace = Math.floorMod(bx * 17 + bz * 31 + d, 3) == 0 ? Kind.PLAZA : Kind.PARK;
            return new Sample(openSpace, d, 0);
        }
        if (d == 7 && bx == 3 && bz == 3) return new Sample(Kind.LANDMARK, d, 70);   // windowless building
        if (d == 15 && bx == 6 && bz == 6) return new Sample(Kind.LANDMARK, d, 300); // station tower
        int[] p = CityDistrictZoning.profile(d, bx, bz);
        double r = rand(seed, cell, bx, bz);
        if (r * 100 < p[2]) return new Sample(Kind.PARK, d, 0);
        double centre = 1 - Math.min(1, Math.max(Math.abs(bx - 6), Math.abs(bz - 6)) / 6.0);
        double t = d == 15 || d == 3 ? 0.25 * rand(seed, cell, bx + 99, bz) + 0.75 * centre * centre : Math.pow(rand(seed, cell, bz, bx), 1.5);
        int floors = Math.max(2, (p[0] + (int) ((p[1] - p[0]) * t)) / 4);
        int bw = 24 + 4 * (int) (rand(seed, cell, bx + 7, bz) * 7), bd = 20 + 4 * (int) (rand(seed, cell, bz + 13, bx) * 7);
        int ox = (48 - bw) / 2, oz = (48 - bd) / 2, lu = ru - 12, lw = rw - 12;
        if (lu < ox || lu >= ox + bw || lw < oz || lw >= oz + bd)             // courtyard around the building
            return new Sample(d == 7 || d == 20 || d == 5 ? Kind.PARK : Kind.PLAZA, d, 0);
        long lot = mix(seed ^ mix(cell * 977 + bx * 31 + bz));
        return new Sample(Kind.BUILDING, d, floors * 4, lu - ox, lw - oz, bw, bd, lot);
    }

    static boolean isLocalCrossing(int x, int z) {
        int lx = Math.floorMod(x + HALF, CELL), lz = Math.floorMod(z + HALF, CELL);
        if (lx < ARTERIAL_HALF || lx >= CELL - ARTERIAL_HALF
                || lz < ARTERIAL_HALF || lz >= CELL - ARTERIAL_HALF) return false;
        int ru = (lx - ARTERIAL_HALF) % BLOCK, rw = (lz - ARTERIAL_HALF) % BLOCK;
        return (ru < 8 && rw >= 8 && rw < 12) || (rw < 8 && ru >= 8 && ru < 12);
    }

    private static Sample mountain(long seed, int x, int z, int u, int w) {
        // A single meandering basin with a shallow shelf, fed from the long western ridges.
        double lakeX = (u - 521 - 15 * Math.sin((w - 395) / 68.0)) / 192;
        double lakeZ = (w - 426 - 24 * Math.sin((u - 405) / 82.0)) / 149;
        double shore = Math.hypot(lakeX, lakeZ) - 1;
        if (shore < 0) {
            int depth = 1 + (int) (17 * ease(-shore / 0.8));
            return new Sample(Kind.WATER, 21, -depth);
        }

        double fold = smooth(seed, x / 190.0, z / 190.0);
        double spine = 209 + 45 * Math.sin((w - 165) / 179.0) + 24 * (fold - 0.5);
        double crest = ridge(u - spine, w - 392, 94, 280);
        double spur = ridge(u - spine - 74, w - 161, 92, 147);
        double saddle = 0.76 + 0.24 * ease(Math.abs(w - 362) / 115.0);
        double edge = Math.min(Math.min(u, 775 - u), Math.min(w, 775 - w));
        double bank = ease(shore / 0.48);
        double relief = 10 + 102 * crest * saddle + 30 * spur + 12 * fold;
        return new Sample(Kind.TERRAIN, 21, (int) (relief * bank * ease(edge / 88.0)));
    }

    private static double ridge(double across, double along, double width, double length) {
        double x = across / width, z = along / length;
        return Math.exp(-(x * x + z * z));
    }

    private static double ease(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    private static Sample airfield(int u, int w, int d) {
        if (w >= 300 && w < 360 && u >= 40 && u < 736) return new Sample(Kind.RUNWAY, d, 0);
        if (u >= 380 && u < 440 && w >= 120 && w < 180) return new Sample(Kind.BUILDING, d, 22);   // terminal
        if (u >= 380 && u < 440 && w >= 180 && w < 300) return new Sample(Kind.PLAZA, d, 0);      // apron link
        return new Sample(Kind.GROUND, d, 0);
    }

    // ------------------------------------------------------------------ top-down map
    private static final Color[] DISTRICT = new Color[24];
    static {
        int[] rgb = {0x202020, 0xF0F0F0, 0x808080, 0x20B0C0, 0xF08020, 0x60A0F0, 0xE040C0, 0xF0E040, 0x70B0D0, 0x20A0A0, 0xE08030,
                0x806040, 0xB0B0B0, 0x90C040, 0xE090B0, 0xA040E0, 0x4060B0, 0x906030, 0x408050, 0x8060A0, 0x80D030, 0x408040, 0xB0B0B0, 0x303030};
        for (int i = 0; i < 24; i++) DISTRICT[i] = new Color(rgb[i]);
    }

    /** Writes a top-down map (4 blocks per pixel) with district numbers: {@code CityPlan <out.png> [seed]}. */
    public static void main(String[] args) throws Exception {
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20261103L;
        int px = 1024, step = 4;
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        boolean[] seen = new boolean[24];
        for (int j = 0; j < px; j++) {
            for (int i = 0; i < px; i++) {
                int x = -HALF + i * step, z = -HALF + j * step;
                Sample s = sample(seed, x, z);
                seen[s.district()] = true;
                Color base = DISTRICT[s.district()];
                Color c = switch (s.kind()) {
                    case SEA -> new Color(30, 60, 110);
                    case WATER -> new Color(60, 110, 200);
                    case ROAD, MARKING -> new Color(60, 60, 64);
                    case SIDEWALK -> new Color(150, 150, 154);
                    case PLAZA -> new Color(220, 220, 200);
                    case PARK -> new Color(70, 140, 70);
                    case GROUND -> new Color(110, 110, 100);
                    case RUNWAY -> new Color(20, 20, 24);
                    case TURBINE -> Color.WHITE;
                    case TERRAIN -> new Color(Math.min(255, 60 + s.height()), Math.min(255, 110 + s.height() / 2), 60);
                    case LANDMARK -> Color.WHITE;
                    case BUILDING -> shade(base, 0.55 + 0.45 * Math.min(1, s.height() / 160.0));
                };
                img.setRGB(i, j, c.getRGB());
            }
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 34f));
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                int d = GRID[row][col];
                if (d == 0) continue;
                String text = "D" + d;
                int cx = (int) ((col * CELL + CELL / 2) / (double) step), cy = (int) ((row * CELL + CELL / 2) / (double) step);
                g.setColor(new Color(0, 0, 0, 170));
                g.drawString(text, cx - 21, cy + 13);
                g.setColor(Color.WHITE);
                g.drawString(text, cx - 22, cy + 12);
            }
        }
        drawOtherRail(g, step);
        drawExpress(g, step);
        g.dispose();
        for (int d = 1; d <= 23; d++) if (!seen[d]) throw new IllegalStateException("district " + d + " missing");
        if (!sample(seed, -HALF + 1, -HALF + 1).kind().equals(Kind.SEA)) throw new IllegalStateException("corner must be sea");
        if (!sample(seed, 123, -456).equals(sample(seed, 123, -456))) throw new IllegalStateException("not deterministic");
        ImageIO.write(img, "png", new File(args.length > 0 ? args[0] : "city_plan.png"));
        System.out.println("23 districts present; map written");
    }

    private static void drawExpress(Graphics2D g, int step) {
        int railZ = ((int) TrainLine.Z + HALF) / step;
        int left = (TrainLine.STATIONS.getFirst().x() + HALF) / step;
        int right = (TrainLine.STATIONS.getLast().x() + HALF) / step;
        g.setColor(new Color(18, 34, 48));
        g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(left, railZ, right, railZ);
        g.setColor(new Color(102, 241, 209));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(left, railZ, right, railZ);
        g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 15f));
        String[] names = {"West End", "West City", "Central", "East City", "East End"};
        for (int i = 0; i < TrainLine.STATIONS.size(); i++) {
            var stop = TrainLine.STATIONS.get(i);
            int x = (stop.x() + HALF) / step;
            String label = i + "  " + names[i];
            int width = g.getFontMetrics().stringWidth(label) + 14;
            int labelX = Math.min(1000 - width, Math.max(3, x - width / 2));
            g.setColor(new Color(18, 34, 48, 235));
            g.fillRoundRect(labelX, railZ + 13, width, 22, 5, 5);
            g.fillOval(x - 8, railZ - 8, 16, 16);
            g.setColor(new Color(102, 241, 209));
            g.fillOval(x - 5, railZ - 5, 10, 10);
            g.setColor(Color.WHITE);
            g.drawString(label, labelX + 7, railZ + 29);
        }
    }

    private static void drawOtherRail(Graphics2D g, int step) {
        var ring = new java.awt.geom.Path2D.Double();
        for (double d = 0; d < CityRingLine.LENGTH; d += 8) {
            var p = CityRingLine.point(d);
            double x = (p.x() + HALF) / step, z = (p.z() + HALF) / step;
            if (d == 0) ring.moveTo(x, z); else ring.lineTo(x, z);
        }
        ring.closePath();
        outlined(g, ring, new Color(240, 106, 202));
        for (var stop : CityRingLine.STATIONS)
            marker(g, (stop.x() + HALF) / step, (stop.z() + HALF) / step, new Color(240, 106, 202));

        var airport = new java.awt.geom.Path2D.Double();
        for (double d = 0; d < AirportLine.LENGTH; d += 8) {
            var p = AirportLine.point(d);
            double x = (p.x() + HALF) / step, z = (p.z() + HALF) / step;
            if (d == 0) airport.moveTo(x, z); else airport.lineTo(x, z);
        }
        var end = AirportLine.point(AirportLine.LENGTH);
        airport.lineTo((end.x() + HALF) / step, (end.z() + HALF) / step);
        outlined(g, airport, new Color(255, 183, 68));
        for (var p : new AirportLine.Point[]{AirportLine.point(0), end})
            marker(g, (int) ((p.x() + HALF) / step), (int) ((p.z() + HALF) / step), new Color(255, 183, 68));

        var north = new java.awt.geom.Line2D.Double((NorthSouthLine.X + HALF) / (double) step,
                (NorthSouthLine.STATIONS.getFirst().z() + HALF) / (double) step,
                (NorthSouthLine.X + HALF) / (double) step,
                (NorthSouthLine.STATIONS.getLast().z() + HALF) / (double) step);
        outlined(g, north, new Color(128, 181, 255));
        for (var stop : NorthSouthLine.STATIONS)
            marker(g, (NorthSouthLine.X + HALF) / step, (stop.z() + HALF) / step, new Color(128, 181, 255));

        var metroAirport = new java.awt.geom.Line2D.Double((CityMetroAirportLine.STATIONS.getFirst().x() + HALF) / (double) step,
                (CityMetroAirportLine.Z + HALF) / (double) step,
                (CityMetroAirportLine.STATIONS.getLast().x() + HALF) / (double) step,
                (CityMetroAirportLine.Z + HALF) / (double) step);
        outlined(g, metroAirport, new Color(150, 221, 105));
        for (var stop : CityMetroAirportLine.STATIONS)
            marker(g, (stop.x() + HALF) / step, (CityMetroAirportLine.Z + HALF) / step, new Color(150, 221, 105));

        var metroNorth = new java.awt.geom.Line2D.Double((CityMetroNorthLine.X + HALF) / (double) step,
                (CityMetroNorthLine.STATIONS.getFirst().z() + HALF) / (double) step,
                (CityMetroNorthLine.X + HALF) / (double) step,
                (CityMetroNorthLine.STATIONS.getLast().z() + HALF) / (double) step);
        outlined(g, metroNorth, new Color(178, 138, 255));
        for (var stop : CityMetroNorthLine.STATIONS)
            marker(g, (CityMetroNorthLine.X + HALF) / step, (stop.z() + HALF) / step, new Color(178, 138, 255));

        var metro = new java.awt.geom.Line2D.Double((CityMetroLine.STATIONS.getFirst().x() + HALF) / (double) step,
                (CityMetroLine.Z + HALF) / (double) step,
                (CityMetroLine.STATIONS.getLast().x() + HALF) / (double) step,
                (CityMetroLine.Z + HALF) / (double) step);
        outlined(g, metro, new Color(255, 109, 99));
        for (var stop : CityMetroLine.STATIONS)
            marker(g, (stop.x() + HALF) / step, (CityMetroLine.Z + HALF) / step, new Color(255, 109, 99));

        g.setColor(new Color(18, 34, 48, 220)); g.fillRoundRect(10, 10, 185, 171, 10, 10);
        g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        String[] labels = {"East–West", "North–South", "Airport", "Ring (alternating)", "Underground E–W", "Underground N–S", "Airport metro"};
        Color[] colors = {new Color(102,241,209), new Color(128,181,255), new Color(255,183,68), new Color(240,106,202), new Color(255,109,99), new Color(178,138,255), new Color(150,221,105)};
        for (int i = 0; i < labels.length; i++) {
            g.setColor(colors[i]); g.fillRect(20, 23 + i * 21, 22, 4);
            g.setColor(Color.WHITE); g.drawString(labels[i], 49, 30 + i * 21);
        }
    }

    private static void outlined(Graphics2D g, java.awt.Shape shape, Color color) {
        g.setColor(new Color(18, 34, 48)); g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape); g.setColor(color); g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(shape);
    }

    private static void marker(Graphics2D g, int x, int z, Color color) {
        g.setColor(new Color(18, 34, 48)); g.fillOval(x-6,z-6,12,12);
        g.setColor(color); g.fillOval(x-3,z-3,6,6);
    }

    private static Color shade(Color c, double f) {
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f));
    }
}
