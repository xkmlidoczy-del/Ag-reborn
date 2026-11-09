package cn.academy.port.highpower.mixin;
public class NativeAnchorPostMixinPlugin {
    public static boolean available=true;
    public static boolean isReviewedPostSupported() {
        return available;
    }
    public static void disableAfterCallbackFailure(Throwable t) {
        available=false;
    }
}
