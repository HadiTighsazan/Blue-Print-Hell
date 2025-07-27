package com.blueprinthell.model;

import java.awt.*;
import java.util.List;


public final class WirePhysics {

    private WirePhysics() {  }


    public static double length(WirePath path) {
        double len = 0.0;
        List<Point> pts = path.controlPoints();
        for (int i = 1; i < pts.size(); i++) {
            len += pts.get(i - 1).distance(pts.get(i));
        }
        return len;
    }


    public static Point pointAt(WirePath path, double t) {
        List<Point> pts = path.controlPoints();
        if (t <= 0) return pts.get(0);
        if (t >= 1) return pts.get(pts.size() - 1);

        double total = length(path);
        double traversed = 0.0;
        for (int i = 1; i < pts.size(); i++) {
            Point a = pts.get(i - 1);
            Point b = pts.get(i);
            double segLen = a.distance(b);
            if (traversed + segLen >= t * total) {
                double localT = (t * total - traversed) / segLen;
                int x = (int) Math.round(a.x + localT * (b.x - a.x));
                int y = (int) Math.round(a.y + localT * (b.y - a.y));
                return new Point(x, y);
            }
            traversed += segLen;
        }
        return pts.get(pts.size() - 1);
    }


    public static double distanceTo(WirePath path, Point p) {
        double min = Double.MAX_VALUE;
        List<Point> pts = path.controlPoints();
        for (int i = 1; i < pts.size(); i++) {
            min = Math.min(min, distancePointToSegment(p, pts.get(i - 1), pts.get(i)));
        }
        return min;
    }

    public static boolean contains(WirePath path, Point p, double tol) {
        return distanceTo(path, p) <= tol;
    }


    private static double distancePointToSegment(Point p, Point a, Point b) {
        double vx = b.x - a.x; double vy = b.y - a.y;
        double wx = p.x - a.x; double wy = p.y - a.y;
        double c2 = vx*vx + vy*vy;
        double t = (c2 == 0) ? 0 : (vx*wx + vy*wy) / c2;
        if (t < 0)      t = 0;
        else if (t > 1) t = 1;
        double projX = a.x + t * vx;
        double projY = a.y + t * vy;
        double dx = p.x - projX;
        double dy = p.y - projY;
        return Math.hypot(dx, dy);
    }
}
