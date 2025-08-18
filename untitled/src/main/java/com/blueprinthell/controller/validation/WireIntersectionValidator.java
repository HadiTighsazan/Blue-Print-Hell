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

/**
 * اعتبارسنجی تداخل سیم‌ها با سیستم‌ها.
 * ایده: «هستهٔ داخلی» هر SystemBox (به‌صورت درصدی از ابعاد) نباید توسط مسیر سیم‌ها قطع شود.
 * برای سگمنت اول/آخر که به پورت‌ها وصل می‌شوند، عبور از «حاشیهٔ مجاز» بیرونی آزاد است،
 * ولی ورود به هستهٔ داخلی همچنان ممنوع می‌ماند.
 */
public final class WireIntersectionValidator {

    /** درصد ناحیهٔ مرکزیِ ممنوعه نسبت به ضلع کوچکتر باکس (مثلاً 0.8 یعنی 80% مرکزی ممنوع). */
    private static final double INNER_REGION_PERCENT = 0.8;

    /** حاشیهٔ مجاز: (1 - INNER_REGION_PERCENT) / 2  (برای هر طرف) */
    private static final double ALLOWED_MARGIN_PERCENT =
            (1.0 - INNER_REGION_PERCENT) / 2.0;

    private WireIntersectionValidator() {
        // Utility — نباید نمونه‌سازی شود
    }

    /**
     * آیا همهٔ سیم‌ها معتبرند (مسیر هیچ‌کدام از «هستهٔ داخلی» سیستم‌ها عبور نمی‌کند)؟
     */
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

    /**
     * اعتبارسنجی یک سیم در برابر همهٔ باکس‌ها.
     */
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

    /**
     * آیا یک سگمنت از سیم با «هستهٔ داخلی» باکس تداخل دارد؟
     * برای سگمنت‌های متصل به پورت (اول/آخر)، عبور در حاشیهٔ بیرونی مجاز است،
     * ولی اگر ورود قابل‌توجهی به هستهٔ داخلی داشته باشند، نامعتبر می‌شوند.
     */
    private static boolean segmentIntersectsBoxInnerRegion(Point p1,
                                                           Point p2,
                                                           SystemBoxModel box,
                                                           boolean isFirstSegment,
                                                           boolean isLastSegment) {
        final Rectangle inner = calculateInnerRegion(box);
        final Rectangle outer = new Rectangle(box.getX(), box.getY(), box.getWidth(), box.getHeight());

        // اگر هیچ برشی با کل باکس هم نباشد، قطعاً برشی با هستهٔ داخلی هم نیست
        final Line2D seg = new Line2D.Double(p1, p2);
        if (!seg.intersects(outer)) {
            return false;
        }

        // اگر سگمنت اول/آخر باشد، وضعیت "اتصال به پورت" را در نظر بگیریم:
        if (isFirstSegment || isLastSegment) {
            final boolean p1InMargin = outer.contains(p1) && !inner.contains(p1);
            final boolean p2InMargin = outer.contains(p2) && !inner.contains(p2);

            // اگر یکی از نقاط در حاشیهٔ مجاز باشد، یعنی این سگمنت احتمالاً به پورت وصل است.
            // در این حالت اجازهٔ عبور از حاشیه را می‌دهیم، اما ورود به هستهٔ داخلی را نه.
            if (p1InMargin || p2InMargin) {
                return entersInnerRegionMeaningfully(p1, p2, inner);
            }
        }

        // برای سایر حالات، اگر برش با هستهٔ داخلی وجود داشته باشد، نامعتبر است.
        return seg.intersects(inner);
    }

    /**
     * محاسبهٔ «هستهٔ داخلی» باکس با توجه به درصد تنظیم‌شده.
     * هستهٔ داخلی، ناحیهٔ مرکزی است که عبور سیم از آن ممنوع است.
     */
    private static Rectangle calculateInnerRegion(SystemBoxModel box) {
        final int bw = Math.max(0, box.getWidth());
        final int bh = Math.max(0, box.getHeight());
        final int minSide = Math.min(bw, bh);

        // حاشیه بر اساس ضلع کوچکتر تا هستهٔ مرکزی یکدست باشد.
        final int margin = (int) Math.floor(minSide * ALLOWED_MARGIN_PERCENT);

        final int x = box.getX() + margin;
        final int y = box.getY() + margin;
        final int w = Math.max(0, bw - (margin << 1));
        final int h = Math.max(0, bh - (margin << 1));

        return new Rectangle(x, y, w, h);
    }

    /**
     * بررسی دقیق‌تر برای سگمنت‌هایی که احتمال اتصال به پورت دارند:
     * اگر «ورود معنادار» به هستهٔ داخلی رخ دهد (بر اساس نمونه‌برداری)، true برمی‌گرداند.
     */
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
        final int samples = 10; // در صورت نیاز قابل تنظیم
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

    /**
     * برگرداندن لیست سیم‌های نامعتبر.
     */
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

    /**
     * ساخت متن خطا برای نمایش به کاربر (یا null اگر خطایی وجود نداشت).
     */
    public static String getValidationMessage(List<WireModel> wires, List<SystemBoxModel> boxes) {
        final List<WireModel> invalid = findInvalidWires(wires, boxes);
        if (invalid.isEmpty()) return null;
        return String.format("تعداد %d سیم از روی سیستم‌ها عبور می‌کنند. لطفاً مسیر سیم‌ها را تغییر دهید.", invalid.size());
    }

    // --- گزینه‌های پیکربندی برنامه‌محور (در صورت نیاز) ---

    /**
     * درصد «هستهٔ داخلی» (برای تست/تیون‌کردن در زمان اجرا).
     * پیشنهاد: فقط در ابزارهای دیباگ استفاده شود.
     */
    public static void setInnerRegionPercent(double innerPercent) {
        // هشدار: این مقدار ثابت تعریف شده؛ اگر می‌خواهید زمان اجرا تنظیم‌پذیر باشد،
        // فیلدها را non-final کنید یا از Config پروژه بخوانید. این متد را نگه داشتیم
        // تا الگوی استفاده روشن باشد. در نسخهٔ drop-in فعلی تغییری اعمال نمی‌کند.
        // (برای سازگاری با درخواست «قابل جایگذاری» بدون تغییر ساختار Config پروژه.)
    }
}
