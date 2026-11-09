package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerRecoveryHudModel.Box;
import java.util.ArrayList;
import java.util.List;

/** NEW351-only measured text layout. Exact status survives crowding in a compact top-margin strip. */
public final class MultidimensionalLandingArrayHudLayout {
    public static final int PADDING = 4, MARGIN = 8, MAX_PANEL_WIDTH = 220;
    public static final float MIN_SCALE = .8f;
    public record Layout(Box bounds, float scale, int lineStep, boolean compact) {}
    private MultidimensionalLandingArrayHudLayout() {}

    public static Layout fit(int width, int height, List<Integer> rowWidths, int compactWidth,
                             int fontHeight, List<Box> instruments) {
        if (width < 144 || height < 140 || fontHeight <= 0 || fontHeight > 32 || rowWidths == null
                || rowWidths.isEmpty() || rowWidths.size() > 8 || rowWidths.stream().anyMatch(n -> n == null || n <= 0 || n > 10000)
                || compactWidth <= 0 || compactWidth > 10000 || instruments == null || instruments.stream().anyMatch(b -> b == null)) return null;
        var reserved = new ArrayList<>(instruments);
        reserved.add(new Box(Math.max(0,width/2-110),height-64,220,64));
        reserved.add(new Box(width/2-20,height/2-20,40,40));
        int widest = rowWidths.stream().mapToInt(Integer::intValue).max().orElse(0), step = fontHeight+1;
        for (float scale : new float[]{1f,.9f,MIN_SCALE}) {
            int panelWidth = Math.max(112,(int)Math.ceil(widest*(double)scale)+PADDING*2);
            int panelHeight = (int)Math.ceil(rowWidths.size()*step*(double)scale)+PADDING*2;
            if (panelWidth > MAX_PANEL_WIDTH || panelWidth > width-MARGIN*2) continue;
            var xs = new ArrayList<Integer>(List.of(MARGIN,width-panelWidth-MARGIN));
            var ys = new ArrayList<Integer>();
            // Existing left/right instruments determine safe slots, including customized HUD positions.
            for (Box b : instruments) { ys.add(b.y()+b.height()+4); xs.add(b.x()+b.width()+4); }
            ys.add(MARGIN); ys.add(height/2+24); ys.add(height-68-panelHeight);
            for (int x : xs) for (int y : ys) {
                var candidate = new Box(x,y,panelWidth,panelHeight);
                if (fits(candidate,width,height,reserved)) return new Layout(candidate,scale,step,false);
            }
        }
        // One complete line, never a truncated counter or a smaller-than-readable substitute.
        for (float scale : new float[]{1f,.9f,MIN_SCALE}) {
            int stripWidth = (int)Math.ceil(compactWidth*(double)scale)+4;
            int stripHeight = (int)Math.ceil(fontHeight*(double)scale)+2;
            var strip = new Box((width-stripWidth)/2,1,stripWidth,stripHeight);
            if (stripWidth <= width-8 && fits(strip,width,height,reserved)) return new Layout(strip,scale,step,true);
        }
        return null;
    }

    private static boolean fits(Box candidate, int width, int height, List<Box> reserved) {
        return candidate.within(width,height) && reserved.stream().noneMatch(candidate::intersects);
    }
}
