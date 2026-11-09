import cn.academy.port.city.CityDistrictZoning;
import cn.academy.port.city.CityPlan;
import cn.academy.port.train.TrainLine;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Schematic of the actual district zoning rules; not an in-game capture. */
public final class CityDistrictPreview {
    private static String name(CityDistrictZoning.Use u) {
        return switch (u) {
            case CIVIC -> "行政"; case ACADEMIC -> "校园"; case SCIENCE -> "研究";
            case COMMERCIAL -> "商业"; case RESIDENTIAL -> "住宅"; case INDUSTRIAL -> "工业";
            case LEISURE -> "娱乐"; case SPORTS -> "运动"; case GREEN -> "绿地";
        };
    }
    private static Color color(CityDistrictZoning.Use u) {
        return switch (u) {
            case CIVIC -> new Color(103, 120, 153); case ACADEMIC -> new Color(117, 163, 197);
            case SCIENCE -> new Color(115, 157, 161); case COMMERCIAL -> new Color(211, 142, 113);
            case RESIDENTIAL -> new Color(181, 176, 201); case INDUSTRIAL -> new Color(153, 147, 137);
            case LEISURE -> new Color(190, 135, 183); case SPORTS -> new Color(142, 186, 133);
            case GREEN -> new Color(139, 182, 150);
        };
    }
    public static void main(String[] args) throws Exception {
        int cell = 300, left = 150, top = 150;
        BufferedImage image = new BufferedImage(1800, 1900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(240, 242, 244)); g.fillRect(0, 0, 1800, 1900);
        g.setColor(new Color(30, 43, 53)); g.setFont(new Font("PingFang SC", Font.BOLD, 40));
        g.drawString("学园都市 23 区建筑安排 · 基础样品", left, 70);
        g.setFont(new Font("PingFang SC", Font.PLAIN, 21));
        g.drawString("功能分布示意：地块由模板与程序立面生成；未代表逐栋验收或原著地图。", left, 108);
        for (int row = 0; row < 5; row++) for (int col = 0; col < 5; col++) {
            int x = left + col * cell, y = top + row * cell;
            int d = CityPlan.district(-2000 + col * 800 + 400, -2000 + row * 800 + 400);
            if (d == 0) { g.setColor(new Color(73, 101, 119)); g.fillRect(x, y, cell, cell); continue; }
            if (d <= 20) {
                for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) {
                    var use = CityDistrictZoning.use(d, c * 4, r * 4);
                    g.setColor(color(use)); g.fillRect(x + c * 100, y + r * 100, 100, 100);
                    g.setColor(new Color(255, 255, 255, 80)); g.drawRect(x + c * 100, y + r * 100, 100, 100);
                    g.setColor(new Color(30, 43, 53)); g.setFont(new Font("PingFang SC", Font.PLAIN, 21));
                    g.drawString(name(use), x + c * 100 + 29, y + r * 100 + 59);
                }
            } else {
                g.setColor(d == 21 ? new Color(108, 153, 130) : d == 22 ? new Color(153, 168, 171) : new Color(122, 143, 158));
                g.fillRect(x, y, cell, cell); g.setColor(Color.WHITE); g.setFont(new Font("PingFang SC", Font.PLAIN, 24));
                String[] lines = d == 21 ? new String[]{"山地 · 水库", "坝体 · 天文台", "山路与观景"}
                        : d == 22 ? new String[]{"风机 · 钢架", "维护平台", "南侧步道"} : new String[]{"航站楼 · 跑道", "发射场", "机场入口"};
                for (int i = 0; i < lines.length; i++) g.drawString(lines[i], x + 50, y + 110 + 42 * i);
            }
            g.setColor(new Color(20, 31, 39, 210)); g.fillRoundRect(x + 9, y + 9, 68, 37, 8, 8);
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 21)); g.drawString("D" + d, x + 18, y + 35);
            g.setColor(new Color(240, 242, 244)); g.setStroke(new BasicStroke(4)); g.drawRect(x, y, cell, cell);
        }
        int railY = top + 900;
        g.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(new Color(33, 51, 62));
        g.drawLine(left + 75, railY, left + 1425, railY);
        g.setStroke(new BasicStroke(4)); g.setColor(new Color(95, 237, 204)); g.drawLine(left + 75, railY, left + 1425, railY);
        g.setFont(new Font("PingFang SC", Font.BOLD, 19));
        String[] stops = {"西端", "西城", "中央", "东城", "东端"};
        for (int i = 0; i < TrainLine.STATIONS.size(); i++) {
            int x = left + (TrainLine.STATIONS.get(i).x() + 2000) * 1500 / 4000;
            g.setColor(new Color(33, 51, 62)); g.fillRoundRect(x - 35, railY + 12, 70, 29, 8, 8);
            g.fillOval(x - 9, railY - 9, 18, 18); g.setColor(Color.WHITE); g.drawString(stops[i], x - 20, railY + 34);
        }
        g.setColor(new Color(30, 43, 53)); g.setFont(new Font("PingFang SC", Font.PLAIN, 21));
        g.drawString("青色线：已有五站东西动车线；机场动车接入、其他线路和逐区验收仍待完成。", left, 1715);
        g.drawString("D8–D14、D16、D18、D19 的用途为项目改编，建筑类别表示模板与密度安排。", left, 1750);
        g.dispose(); File out = new File(args[0]); out.getParentFile().mkdirs(); ImageIO.write(image, "png", out);
    }
}
