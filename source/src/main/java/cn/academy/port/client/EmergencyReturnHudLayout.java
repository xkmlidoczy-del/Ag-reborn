package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerRecoveryHudModel.Box;
import java.util.ArrayList;
import java.util.List;

/** Two short rows fit around existing instruments; no instruction wall or crosshair overlap. */
public final class EmergencyReturnHudLayout {
    public static final int HEIGHT = 25, MIN_WIDTH = 110, MAX_WIDTH = 190;
    private EmergencyReturnHudLayout() {}
    public static Box fit(int width, int height, Box cp, Box keys, Box notification, Box chat, Box recovery) {
        if (width < 220 || height < 140 || cp == null || keys == null || notification == null || chat == null) return null;
        var reserved = new ArrayList<>(List.of(cp, keys, notification, chat,
                new Box(Math.max(0,width/2-110),height-64,220,64),
                new Box(Math.max(0,width/2-175),height/2-12,350,Math.max(24,height/2-52))));
        if (recovery != null) reserved.add(recovery);
        int gapX = Math.max(8, notification.x()+notification.width()+4);
        int gapWidth = Math.min(MAX_WIDTH, cp.x()-gapX-4);
        var candidates = new ArrayList<Box>();
        if (gapWidth >= MIN_WIDTH) candidates.add(new Box(gapX,8,gapWidth,HEIGHT));
        for (int panelWidth : new int[]{MAX_WIDTH,150,MIN_WIDTH}) {
            if (recovery != null) candidates.add(new Box(recovery.x(),recovery.y()+recovery.height()+3,Math.min(panelWidth,recovery.width()),HEIGHT));
            candidates.add(new Box(8,notification.y()+notification.height()+4,panelWidth,HEIGHT));
            candidates.add(new Box(width-panelWidth-8,8,panelWidth,HEIGHT));
        }
        for (var candidate : candidates) if (candidate.within(width,height)
                && reserved.stream().noneMatch(candidate::intersects)) return candidate;
        return null;
    }
}
