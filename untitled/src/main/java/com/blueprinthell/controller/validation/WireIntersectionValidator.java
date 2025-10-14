package com.blueprinthell.controller.validation;

import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WirePath;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public final class WireIntersectionValidator {

    private static final double INNER_REGION_PERCENT = 0.8;

    private static final double ALLOWED_MARGIN_PERCENT =
            (1.0 - INNER_REGION_PERCENT) / 2.0;

    private WireIntersectionValidator() {
    }


    public static boolean areAllWiresValid(List<WireModel> wires, List<SystemBoxModel> boxes) {
        if (wires == null || wires.isEmpty() || boxes == null || boxes.isEmpty()) {
            return true;
        }
        for (WireModel wire : wires) {
            if (!isWireValid(wire, boxes)) {
                return false;
            }
        }
        return true;
    }


    public static boolean isWireValid(WireModel wire, List<SystemBoxModel> boxes) {
        if (wire == null) return true;

        final WirePath path = wire.getPath();
        if (path == null) return true;

        final List<Point> pts = path.getPoints();
        if (pts == null || pts.size() < 2) return true;

        final int lastSegIndex = pts.size() - 2;

        for (int i = 0; i < pts.size() - 1; i++) {
            final Point p1 = pts.get(i);
            final Point p2 = pts.get(i + 1);
            if (p1 == null || p2 == null) continue;

            final boolean isFirstSegment = (i == 0);
            final boolean isLastSegment  = (i == lastSegIndex);

            for (SystemBoxModel box : boxes) {
                if (box == null) continue;

                if (segmentIntersectsBoxInnerRegion(p1, p2, box, isFirstSegment, isLastSegment)) {
                    return false;
                }
            }
        }
        return true;
    }


    private static boolean segmentIntersectsBoxInnerRegion(Point p1,
                                                           Point p2,
                                                           SystemBoxModel box,
                                                           boolean isFirstSegment,
                                                           boolean isLastSegment) {
        final Rectangle inner = calculateInnerRegion(box);
        final Rectangle outer = new Rectangle(box.getX(), box.getY(), box.getWidth(), box.getHeight());

        final Line2D seg = new Line2D.Double(p1, p2);
        if (!seg.intersects(outer)) {
            return false;
        }

        if (isFirstSegment || isLastSegment) {
            final boolean p1InMargin = outer.contains(p1) && !inner.contains(p1);
            final boolean p2InMargin = outer.contains(p2) && !inner.contains(p2);

            if (p1InMargin || p2InMargin) {
                return entersInnerRegionMeaningfully(p1, p2, inner);
            }
        }

        return seg.intersects(inner);
    }


    private static Rectangle calculateInnerRegion(SystemBoxModel box) {
        final int bw = Math.max(0, box.getWidth());
        final int bh = Math.max(0, box.getHeight());
        final int minSide = Math.min(bw, bh);

        final int margin = (int) Math.floor(minSide * ALLOWED_MARGIN_PERCENT);

        final int x = box.getX() + margin;
        final int y = box.getY() + margin;
        final int w = Math.max(0, bw - (margin << 1));
        final int h = Math.max(0, bh - (margin << 1));

        return new Rectangle(x, y, w, h);
    }


    private static boolean entersInnerRegionMeaningfully(Point p1, Point p2, Rectangle inner) {
        // اگر مرکز سگمنت داخل است، قطعاً ورود معنادار داریم.
        final int midX = (p1.x + p2.x) >>> 1;
        final int midY = (p1.y + p2.y) >>> 1;
        if (inner.contains(midX, midY)) {
            return true;
        }

        final double len = p1.distance(p2);
        if (len <= 0.0) {
            return inner.contains(p1);
        }

        // نمونه‌برداری یکنواخت روی سگمنت
        final int samples = 10;
        int inside = 0;

        for (int i = 1; i < samples; i++) {
            final double t = (double) i / samples;
            final int x = (int) Math.round(p1.x + t * (p2.x - p1.x));
            final int y = (int) Math.round(p1.y + t * (p2.y - p1.y));
            if (inner.contains(x, y)) {
                inside++;
            }
        }

        // اگر بیش از نصف نقاط نمونه داخل هسته باشند، ورود معنادار تلقی می‌شود.
        return inside > samples / 2;
    }


    public static List<WireModel> findInvalidWires(List<WireModel> wires, List<SystemBoxModel> boxes) {
        final List<WireModel> out = new ArrayList<>();
        if (wires == null || boxes == null || boxes.isEmpty()) return out;

        for (WireModel w : wires) {
            if (w == null) continue;
            if (!isWireValid(w, boxes)) {
                out.add(w);
            }
        }
        return out;
    }


    public static String getValidationMessage(List<WireModel> wires, List<SystemBoxModel> boxes) {
        final List<WireModel> invalid = findInvalidWires(wires, boxes);
        if (invalid.isEmpty()) return null;
        return String.format("تعداد %d سیم از روی سیستم‌ها عبور می‌کنند. لطفاً مسیر سیم‌ها را تغییر دهید.", invalid.size());
    }


}
