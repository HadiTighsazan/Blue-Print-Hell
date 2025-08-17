package com.blueprinthell.controller.ui.editor;

import com.blueprinthell.model.PortModel;
import com.blueprinthell.model.SystemBoxModel;
import com.blueprinthell.model.WireModel;
import com.blueprinthell.model.WireUsageModel;
import com.blueprinthell.view.SystemBoxView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * کنترلر درگ باکس‌ها با پشتیبانی از حالت «یک‌بار درگ» (Scroll of Sisyphus).
 * - قبل از Start: درگ آزاد (مگر جایی دیگر قفل کرده باشید)
 * - بعد از خرید Scroll of Sisyphus: فقط یک جابه‌جایی مجاز است؛ سپس قفل سراسری تا ریست مرحله
 * - هنگام درگ در حالت سیسیفوس: کلمپ به شعاع، جلوگیری از عبور سیم‌ها از باکس‌ها
 * - بروزرسانی WireUsageModel بر اساس تغییر طول سیم‌های متصل
 */
public class SystemBoxDragController extends MouseAdapter implements MouseMotionListener {

    private final SystemBoxModel model;
    private final SystemBoxView view;
    private final List<WireModel> wires;
    private final WireUsageModel usageModel;

    private Point offset;
    private final Map<WireModel, Double> oldLengths;

    // در دسترس/غیرفعال بودن عمومی درگ
    private static volatile boolean DRAG_ENABLED = true;

    // --- حالت سیسیفوس (یک‌بار درگ) ---
    private static boolean SISYPHUS_MODE = false;
    private static int SISY_RADIUS_PX = 120;
    private static Predicate<SystemBoxModel> SISY_FILTER = null;    // فیلتر باکس مجاز (غیرمرجع)
    private static List<WireModel> SISY_WIRES = null;               // همهٔ سیم‌ها برای چک برخورد
    private static List<SystemBoxView> SISY_OBSTACLES = null;       // فعلاً استفاده نمی‌شود
    private static Runnable SISY_ON_FINISH = null;                  // کال‌بک اتمام
    private static Point SISY_START_POS = null;                     // نقطهٔ شروع در مختصات parent

    // قفل سخت تا ریست مرحله
    private static boolean GLOBAL_DRAG_LOCK = false;

    // فلگ‌های کمکی (در صورت نیاز برای دیباگ/لاگ)
    private static boolean SISY_ARMED = false;  // فعال شده ولی هنوز مصرف نشده
    private static boolean SISY_USED  = false;  // مصرف شد
    private static boolean SISY_MOVED = false;  // در این تلاش واقعاً جابه‌جا شد

    public SystemBoxDragController(SystemBoxModel model,
                                   SystemBoxView view,
                                   List<WireModel> wires,
                                   WireUsageModel usageModel) {
        this.model = model;
        this.view = view;
        this.wires = wires;
        this.usageModel = usageModel;
        this.oldLengths = new HashMap<>();

        view.addMouseListener(this);
        view.addMouseMotionListener(this);
    }

    // -------------------- Mouse Events --------------------

    @Override
    public void mousePressed(MouseEvent e) {
        // قبلی: if (!DRAG_ENABLED) return;
        if (!DRAG_ENABLED && !SISYPHUS_MODE) return;

        // اگر قفل سراسری فعاله و در حالت سیسیفوس نیستیم، هیچ درگی مجاز نیست
        if (!SISYPHUS_MODE && GLOBAL_DRAG_LOCK) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        if (!DRAG_ENABLED) return;

        // اگر سیسیفوس فعاله: فقط باکس‌های مجاز (غیرمرجع) و ثبت نقطهٔ شروع
        if (SISYPHUS_MODE) {
            if (SISY_FILTER != null && !SISY_FILTER.test(model)) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            SISY_START_POS = new Point(view.getX(), view.getY()); // مختصات محلی نسبت به parent
            SISY_MOVED = false;
        }

        // محاسبهٔ offset درگ
        offset = e.getPoint();

        // ذخیرهٔ طول فعلی سیم‌های متصل به این باکس
        oldLengths.clear();
        for (WireModel wire : wires) {
            if (belongsToThisBox(wire.getSrcPort()) || belongsToThisBox(wire.getDstPort())) {
                oldLengths.put(wire, wire.getLength());
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!DRAG_ENABLED && !SISYPHUS_MODE) return;

        if (!DRAG_ENABLED) return;

        // مختصات محلی جدید نسبت به parent (بدون onScreen => بدون "پرش")
        int newX = view.getX() + e.getX() - offset.x;
        int newY = view.getY() + e.getY() - offset.y;

        // قیود حالت سیسیفوس
        if (SISYPHUS_MODE && SISY_START_POS != null) {
            int targetX = newX;
            int targetY = newY;

            // 1) کلمپ به شعاع
            int dx = targetX - SISY_START_POS.x;
            int dy = targetY - SISY_START_POS.y;
            double dist = Math.hypot(dx, dy);
            if (dist > SISY_RADIUS_PX) {
                double scale = SISY_RADIUS_PX / Math.max(1e-9, dist);
                targetX = SISY_START_POS.x + (int) Math.round(dx * scale);
                targetY = SISY_START_POS.y + (int) Math.round(dy * scale);
            }

            // 2) جلوگیری از عبور سیم‌ها از داخل باکس‌ها
            Rectangle newBounds = new Rectangle(targetX, targetY, view.getWidth(), view.getHeight());
            if (intersectsAnyWireExceptOwn(newBounds, model)) {
                return; // نقطهٔ نامعتبر؛ همین step را قبول نکن
            }

            newX = targetX;
            newY = targetY;

            if (newX != SISY_START_POS.x || newY != SISY_START_POS.y) {
                SISY_MOVED = true;
            }
        }

        // جابه‌جایی ویو و مدل
        view.setLocation(newX, newY);
        model.setX(newX);
        model.setY(newY);

        // بروزرسانی مصرف طول سیم‌ها
        for (Map.Entry<WireModel, Double> entry : oldLengths.entrySet()) {
            WireModel wire = entry.getKey();
            double previous = entry.getValue();
            double current = wire.getLength();
            double delta = current - previous;
            if (delta > 0) {
                usageModel.useWire(delta);
            } else if (delta < 0) {
                usageModel.freeWire(-delta);
            }
            entry.setValue(current);
        }

        JComponent parent = (JComponent) view.getParent();
        parent.revalidate();
        parent.repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // پایان حالت یک‌باره و «قفلِ سخت» تا ریست مرحله
        if (SISYPHUS_MODE) {
            SISYPHUS_MODE = false;
            if (SISY_MOVED) SISY_USED = true;

            SISY_START_POS = null;
            SISY_MOVED = false;

            forceDragLockUntilStageReset(); // از این لحظه، هیچ درگی مجاز نیست

            if (SISY_ON_FINISH != null) {
                try { SISY_ON_FINISH.run(); } catch (Throwable ignore) {}
            }
        }

        // اگر قفل سراسری فعاله، مطمئن شو کسی نتونه درگ رو دوباره فعال کنه
        if (GLOBAL_DRAG_LOCK) {
            DRAG_ENABLED = false;
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // intentionally empty
    }

    // -------------------- API عمومی --------------------

    /**
     * فعال‌سازی حالت «یک‌بار درگ» برای Scroll of Sisyphus.
     * @param radiusPx شعاع مجاز جابه‌جایی از نقطهٔ شروع (پیکسل)
     * @param filter   فقط اگر باکس از نظر شما مجازه (مثلاً غیرمرجع: هم in و هم out دارد)
     * @param wires    لیست همهٔ سیم‌ها (برای چک برخورد با باکس‌ها)
     * @param obstacles فعلاً استفاده نمی‌شود (رزرو)
     * @param onFinish کال‌بک اتمام (اختیاری)
     */
    public static void enableSisyphusOneShot(
            int radiusPx,
            Predicate<SystemBoxModel> filter,
            List<WireModel> wires,
            List<SystemBoxView> obstacles,
            Runnable onFinish
    ) {
        SISYPHUS_MODE  = true;
        SISY_ARMED     = true;
        SISY_USED      = false;
        SISY_MOVED     = false;
        SISY_START_POS = null;

        SISY_RADIUS_PX = Math.max(1, radiusPx);
        SISY_FILTER    = filter;
        SISY_WIRES     = wires;
        SISY_OBSTACLES = obstacles; // رزرو برای آینده
        SISY_ON_FINISH = onFinish;

        setDragEnabled(true); // فعلاً آزاد؛ بعد از رهاسازی، قفل سراسری می‌شود
    }

    /** قفل سخت را تا ریست مرحله فعال می‌کند. */
    public static void forceDragLockUntilStageReset() {
        GLOBAL_DRAG_LOCK = true;
        DRAG_ENABLED = false;
    }

    /** پاک‌کردن قفل سخت — فقط هنگام ساخت/لود مرحلهٔ جدید صدا زده شود. */
    public static void clearDragLock() {
        GLOBAL_DRAG_LOCK = false;
    }

    /** فعال/غیرفعال‌کردن درگ عمومی (قفل سخت اولویت دارد). */
    public static void setDragEnabled(boolean enabled) {
        // اگر قفل سراسری فعاله، فقط وقتی اجازه بده درگ روشن شه که در حالت سیسیفوس باشیم
        if (GLOBAL_DRAG_LOCK && enabled && !SISYPHUS_MODE) {
            DRAG_ENABLED = false;
            return;
        }
        DRAG_ENABLED = enabled;
    }


    // -------------------- کمکی‌ها --------------------

    private boolean belongsToThisBox(PortModel p) {
        if (p == null) return false;
        return model.getInPorts().contains(p) || model.getOutPorts().contains(p);
    }

    /**
     * آیا مستطیل newBounds با هر قطعه از مسیر سیم‌ها (به‌جز سیم‌های متصل به همین باکس) برخورد دارد؟
     */
    private boolean intersectsAnyWireExceptOwn(Rectangle newBounds, SystemBoxModel movedBox) {
        if (SISY_WIRES == null) return false;

        for (WireModel w : SISY_WIRES) {
            // سیم‌های متصل به همین باکس را مستثنا کن
            PortModel src = w.getSrcPort();
            PortModel dst = w.getDstPort();
            if (belongsToThisBox(src) || belongsToThisBox(dst)) continue;

            List<Point> cps = w.getPath().getPoints(); // فرض: لیست نقاط مسیر
            for (int i = 0; i < cps.size() - 1; i++) {
                Point a = cps.get(i), b = cps.get(i + 1);
                if (segmentIntersectsRectExcludingEndpoints(a, b, newBounds)) return true;
            }
        }
        return false;
    }

    private static boolean segmentIntersectsRectExcludingEndpoints(Point a, Point b, Rectangle r) {
        if (!new java.awt.geom.Line2D.Double(a, b).intersects(r)) return false;
        // اگر یکی از دو سر داخل رکت هست، وسط پاره‌خط را چک کن تا تماس انتهایی شمارش نشود
        if (r.contains(a) || r.contains(b)) {
            double mx = (a.x + b.x) / 2.0;
            double my = (a.y + b.y) / 2.0;
            return r.contains(mx, my);
        }
        return true;
    }
}
