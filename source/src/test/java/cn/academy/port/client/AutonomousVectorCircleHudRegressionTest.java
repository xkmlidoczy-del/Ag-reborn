package cn.academy.port.client;

import cn.academy.port.client.AutonomousVectorCircleMesh.Point;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.HighPowerRecoveryHudModel.Box;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Exact shield receipts, unknown-state truth, replay cleanup, localized formatting and measured HUD space. */
public final class AutonomousVectorCircleHudRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(452, 1);
    private static final Point FEET = new Point(0, 64, 0), CENTER = new Point(0, 65, 0), FORWARD = new Point(0, 0, 1);
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static AutonomousVectorCircleState.Frame frame(long sequence, int phase, int quota, boolean ready, String current, String cap) {
        return new AutonomousVectorCircleState.Frame(new AutonomousVectorCircleState.Identity(19, OWNER, 71, 1, 10),
                sequence, phase, FEET, FORWARD, FEET, CENTER, quota, ready, current, cap);
    }
    private static AutonomousVectorCircleState.Controller controller(String current, String cap, boolean local) {
        return new AutonomousVectorCircleState.Controller(frame(1, 0, 8, true, current, cap), local, 0, null, 10, 0);
    }
    private static AutonomousVectorCircleHudModel.Content content(AutonomousVectorCircleState.Controller c, String language) {
        return AutonomousVectorCircleHudModel.content(c, language, (key, fallback) -> language.startsWith("zh") ? switch (key) {
            case "shield_label" -> "矢量圈 · 可用护盾"; case "shield_unknown" -> "不可用";
            case "quota_label" -> "次数"; case "state_ready" -> "就绪"; case "state_paused" -> "暂停";
            default -> fallback;
        } : fallback, (key, fallback) -> fallback);
    }
    public static void main(String[] args) {
        var large = controller("150000000000", "200000000000", true);
        check(AutonomousVectorCircleState.valid(large.frame()), "bounded exact owner balance accepted");
        check(content(large, "zh_cn").shield().equals("矢量圈 · 可用护盾 1500亿 / 2000亿"), "Chinese uses existing 亿 formatter");
        check(content(large, "en_us").shield().equals("Circle · Available shield 150B / 200B"), "English uses existing compact formatter without exponents");
        check(content(controller("10000000000000000", "100000000000000000000", true), "zh_cn").shield().contains("京"), "Chinese 京 keeps large values friendly");
        check(content(controller("10000", "20000", true), "zh_cn").shield().contains("1万 / 2万"), "Chinese 万 maintained");
        check(content(controller("99999999999999999999", "100000000000000000000", true), "en_us").shield().contains("−1"), "one-point deficit never rounds up to full");
        check(content(controller("0.00000000000000000001", "200000000000", true), "en_us").shield().contains("<0.0001"), "tiny positive is never displayed as zero");
        var unknown = content(controller("", "", true), "zh_cn");
        check(unknown.shield().equals("矢量圈 · 可用护盾 不可用") && !unknown.shield().contains("0"), "unknown means unavailable rather than invented zero");
        check(content(controller("0", "200000000000", true), "zh_cn").shield().contains("0 / 2000亿"), "authentic zero stays distinguishable from unavailable");
        check(content(controller("1", "2", false), "en_us") == null, "remote visual cannot expose any shield HUD");
        for (String invalid : List.of("NaN", "Infinity", "-1", "+1", "01", "1.0", "1e11", "1E999999999", "100000000000000000001", "1".repeat(129))) {
            check(!AutonomousVectorCircleState.validShieldPair(invalid, "100000000000000000000"), "invalid exact current rejected");
            check(!AutonomousVectorCircleState.validShieldPair("0", invalid), "invalid exact capacity rejected");
        }
        check(!AutonomousVectorCircleState.validShieldPair(null, "") && !AutonomousVectorCircleState.validShieldPair("", null), "missing fields rejected");
        check(!AutonomousVectorCircleState.validShieldPair("", "0") && !AutonomousVectorCircleState.validShieldPair("0", ""), "partial unknown pair rejected");
        check(!AutonomousVectorCircleState.validShieldPair("3", "2"), "available cannot exceed capacity");
        check(AutonomousVectorCircleState.validShieldPair("0", "0"), "known empty capacity remains a valid exact observation");
        check(!AutonomousVectorCircleState.valid(frame(2, 3, 0, false, "0", "2")), "ENDED cannot retain balance payload");
        check(!AutonomousVectorCircleState.valid(frame(2, 0, 8, true, "1e11", "200000000000")), "malformed numeric receipt rejected before state mutation");
        var state = new AutonomousVectorCircleState();
        check(state.accept(frame(1, 0, 8, true, "150000000000", "200000000000"), true), "owner initial shield receipt");
        check(state.accept(frame(2, 2, 4, false, "100000000000", "200000000000"), true), "four-per-tick pause retains rolling quota and exact total");
        check(content(state.current(OWNER), "zh_cn").status().equals("次数 4/8 · 暂停"), "physical pause never falsely spends all eight admissions");
        check(!state.accept(frame(1, 0, 8, true, "200000000000", "200000000000"), true), "old sequence cannot refill displayed reserve");
        check(state.current(OWNER).frame().availableShield().equals("100000000000"), "rejected stale refill leaves current exact value unchanged");
        check(!state.accept(frame(3, 0, 8, true, "200000000001", "200000000000"), true), "invalid higher-sequence overfill rejected");
        check(state.accept(frame(3, 2, 4, false, "", ""), true), "unknown receipt replaces prior known values");
        check(content(state.current(OWNER), "zh_cn").shield().contains("不可用"), "no stale balance retained across unknown snapshot");
        check(state.accept(frame(4, 0, 8, true, "100000000000", "200000000000"), true), "new server value recovers unknown");
        for (int i = 0; i < 40; i++) state.tick(false, c -> true);
        check(content(state.current(OWNER), "zh_cn") == null, "quiet timeout hides numbers with existing receipt lease");
        check(state.accept(frame(5, 0, 8, true, "120000000000", "200000000000"), true), "authentic newer heartbeat recovers exact total");
        state.clear();
        check(state.current(OWNER) == null && !state.accept(frame(6, 0, 8, true, "200000000000", "200000000000"), true), "reload/menu/category clear fences old owner balance");
        state.resetSession();
        check(state.accept(frame(1, 0, 8, true, "2", "2"), true), "new connection has new sequence namespace");
        state.cancel(71, 1);
        check(!state.accept(frame(2, 0, 8, true, "2", "2"), true) && state.current(OWNER) == null, "explicit off cancellation cannot replay HUD");
        state.resetSession(); state.accept(frame(1, 0, 8, true, "2", "2"), true);
        check(state.accept(frame(2, 3, 0, false, "", ""), true) && state.current(OWNER) == null, "authenticated END removes shield HUD");
        check(!state.accept(frame(3, 0, 8, true, "2", "2"), true), "post-END balance cannot restore HUD");
        for (int[] size : new int[][]{{320,240},{393,272},{426,240},{640,360},{854,480},{960,540},{1280,720},{1920,1080}}) {
            for (String language : List.of("zh_cn", "en_us")) for (var c : List.of(large,
                    controller("99999999999999999999", "100000000000000000000", true), controller("", "", true))) {
                var boxes = instruments(size[0], size[1]);
                var text = content(c, language);
                var layout = AutonomousVectorCircleHudModel.layout(size[0], size[1], 9, text, boxes,
                        AutonomousVectorCircleHudRegressionTest::wrap, AutonomousVectorCircleHudRegressionTest::measure);
                verify(layout, size[0], size[1], boxes, text);
            }
        }
        var moved = List.of(new Box(8,12,194,40), new Box(8,56,201,56), new Box(8,145,185,88), new Box(230,15,130,43));
        verify(AutonomousVectorCircleHudModel.layout(640,360,9,content(large,"en_us"),moved,
                AutonomousVectorCircleHudRegressionTest::wrap,AutonomousVectorCircleHudRegressionTest::measure),640,360,moved,content(large,"en_us"));
        check(AutonomousVectorCircleHudModel.layout(320,240,9,content(large,"en_us"),List.of(new Box(0,0,320,240)),
                AutonomousVectorCircleHudRegressionTest::wrap,AutonomousVectorCircleHudRegressionTest::measure)==null,"fully reserved view cannot overwrite another HUD");
        System.out.println("AutonomousVectorCircleHudRegressionTest: " + checks + " checks passed");
    }
    private static List<Box> instruments(int width,int height) {
        var cp = new Box((int)Math.floor(width-964*.2-12),12,194,40);
        var keys = new Box((int)(width-140*.23)-155,(int)((height-210*.23)*.5+30),185,88);
        var notification = new Box(0,15,130,43);
        var chat = new Box(0,height-131,Math.min(width,336),103);
        var result = new ArrayList<>(List.of(cp,keys,notification,chat));
        var recovery = HighPowerRecoveryHudModel.layout(width,height,cp,keys,notification,chat);
        if(recovery!=null)result.add(recovery);
        return result;
    }
    private static int measure(String text) { return text.codePoints().map(c -> c > 255 ? 9 : c == ' ' ? 4 : 6).sum(); }
    private static List<String> wrap(String text,int width) {
        var result = new ArrayList<String>();
        for(String original:text.split("\\n",-1)) {
            StringBuilder line=new StringBuilder();
            for(int cp:original.codePoints().toArray()) {
                String next=new String(Character.toChars(cp));
                if(!line.isEmpty()&&measure(line+next)>width) {result.add(line.toString());line.setLength(0);}
                line.append(next);
            }
            result.add(line.toString());
        }
        return result;
    }
    private static void verify(AutonomousVectorCircleHudModel.Layout<String> layout,int width,int height,List<Box> boxes,AutonomousVectorCircleHudModel.Content content) {
        check(layout!=null,"both HUD rows fit practical view "+width+"x"+height+" "+content.text());
        var bounds=layout.bounds();check(bounds.within(width,height),"HUD inside viewport");
        for(var box:boxes)check(!bounds.intersects(box),"existing instrument preserved");
        check(!bounds.intersects(new Box(width/2-20,height/2-20,40,40)),"crosshair preserved");
        check(!bounds.intersects(new Box(width/2-110,height-64,220,64)),"native status/hotbar preserved");
        String joined=layout.lines().stream().map(AutonomousVectorCircleHudModel.Line::text).reduce("",String::concat);
        check(joined.equals(content.shield()+content.status()),"all digits and both labels retained without truncation");
        for(var line:layout.lines())check(line.x()>=bounds.x()+4&&line.x()+line.width()<=bounds.x()+bounds.width()-4
                &&line.y()>=bounds.y()+4&&line.y()+9<=bounds.y()+bounds.height(),"native-font line remains inside panel");
    }
}
