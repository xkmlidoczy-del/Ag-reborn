package cn.academy.port.highpower;
/** GUI reservations only. World rendering does not call this host. */
public final class HighPowerRecoveryHudModel {public record Box(int x,int y,int width,int height){public boolean intersects(Box b){return x<b.x+b.width&&x+width>b.x&&y<b.y+b.height&&y+height>b.y;}public boolean within(int w,int h){return x>=0&&y>=0&&x+width<=w&&y+height<=h;}}public static Box layout(int w,int h,Box...r){throw new UnsupportedOperationException("GUI outside fallback proof");}}
