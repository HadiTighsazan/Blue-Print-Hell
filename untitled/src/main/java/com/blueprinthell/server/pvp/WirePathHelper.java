package com.blueprinthell.server.pvp;

import com.blueprinthell.shared.protocol.NetworkProtocol.WireLayout;
import com.blueprinthell.shared.protocol.NetworkProtocol.WireLayout.Point2D;
import java.util.List;

/**
 * کلاس کمکی برای انجام محاسبات هندسی روی WireLayout در سمت سرور.
 * این کلاس جایگزین WirePhysics کلاینت می‌شود.
 */
public final class WirePathHelper {

    private WirePathHelper() {}

    /**
     * طول کل یک مسیر سیم را محاسبه می‌کند.
     */
    public static double length(WireLayout wire) {
        if (wire == null || wire.path == null || wire.path.size() < 2) {
            return 0.0;
        }
        double len = 0.0;
        List<Point2D> pts = wire.path;
        for (int i = 1; i < pts.size(); i++) {
            len += distance(pts.get(i - 1), pts.get(i));
        }
        return len;
    }

    /**
     * مختصات یک نقطه را بر اساس درصد پیشرفت (progress) روی سیم محاسبه می‌کند.
     */
    public static Point2D pointAt(WireLayout wire, double progress) {
        List<Point2D> pts = wire.path;
        if (progress <= 0) return pts.get(0);
        if (progress >= 1) return pts.get(pts.size() - 1);

        double totalLength = length(wire);
        double targetDist = progress * totalLength;
        double traversed = 0.0;

        for (int i = 1; i < pts.size(); i++) {
            Point2D p1 = pts.get(i - 1);
            Point2D p2 = pts.get(i);
            double segmentLength = distance(p1, p2);

            if (traversed + segmentLength >= targetDist) {
                double localProgress = (targetDist - traversed) / segmentLength;
                int x = (int) Math.round(p1.x + localProgress * (p2.x - p1.x));
                int y = (int) Math.round(p1.y + localProgress * (p2.y - p1.y));
                return new Point2D(x, y);
            }
            traversed += segmentLength;
        }
        return pts.get(pts.size() - 1);
    }

    private static double distance(Point2D p1, Point2D p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}