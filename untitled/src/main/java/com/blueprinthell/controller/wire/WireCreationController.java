package com.blueprinthell.controller.wire;

import com.blueprinthell.config.Config;
import com.blueprinthell.controller.simulation.SimulationController;
import com.blueprinthell.model.*;
import com.blueprinthell.view.*;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

// ⬇️ اضافه‌شده برای چک صریحِ سازگاری شکل‌ها
// import com.blueprinthell.model.PortCompatibility; // unused: wiring has no shape restriction

public class WireCreationController {
    private final GameScreenView      gameView;
    private final SimulationController simulation;
    private final List<SystemBoxModel> boxes;
    private final List<WireModel>     wires;
    private final Map<WireModel, SystemBoxModel> destMap;
    private final Set<PortModel>      lockedInputs = new HashSet<>();
    private final WireUsageModel      usageModel;
    private final CoinModel           coinModel;
    private final Runnable            networkChanged;

    private boolean   drawing   = false;
    private PortModel startPort;
    private Point     startPt;

    private final Overlay             overlay;
    private final MouseMotionListener previewListener;

    private final JPanel area;
    private final Map<PortModel, SystemBoxModel> portToBoxMap;

    public WireCreationController(GameScreenView gameView,
                                  SimulationController simulation,
                                  List<SystemBoxModel> boxes,
                                  List<WireModel> wires,
                                  Map<WireModel, SystemBoxModel> destMap,
                                  WireUsageModel usageModel,
                                  CoinModel coinModel,
                                  Runnable networkChanged) {
        // --- Null checks & sane defaults ---
        this.gameView       = java.util.Objects.requireNonNull(gameView, "gameView is null");
        this.simulation     = java.util.Objects.requireNonNull(simulation, "simulation is null");
        this.boxes          = (boxes != null) ? boxes : new ArrayList<>();
        this.wires          = (wires != null) ? wires : new ArrayList<>();
        this.destMap        = (destMap != null) ? destMap : new java.util.HashMap<>();
        this.usageModel     = java.util.Objects.requireNonNull(usageModel, "usageModel is null");
        this.coinModel      = java.util.Objects.requireNonNull(coinModel, "coinModel is null");
        this.networkChanged = (networkChanged != null) ? networkChanged : () -> {};

        this.area = this.gameView.getGameArea();
        if (this.area == null) {
            throw new IllegalStateException("gameView.getGameArea() returned null");
        }
        area.setLayout(null);

        // ساخت نقشه Port→Box برای استفاده در سیم‌های جدید
        this.portToBoxMap = buildPortToBoxMap(this.boxes);

        // --- پاکسازی سیم‌های نامعتبر از level قبل + ثبت مقصد و قفل ورودی‌ها ---
        List<WireModel> invalidWires = new ArrayList<>();
        for (WireModel w : new ArrayList<>(this.wires)) { // روی کپی loop می‌زنیم تا از CME جلوگیری شود
            if (w == null) {
                invalidWires.add(null);
                continue;
            }

            PortModel dstPort = w.getDstPort();
            SystemBoxModel destBox = (dstPort != null) ? findDestBox(dstPort) : null;

            if (destBox == null) {
                // این سیم نامعتبر است - box مقصد وجود ندارد
                invalidWires.add(w);
                continue;
            }

            // ثبت مقصد و قفل‌کردن پورت ورودی
            this.destMap.put(w, destBox);
            if (dstPort != null) {
                lockedInputs.add(dstPort);
            }

            // ثبت مصرف طول سیم
            this.usageModel.useWire(w.getLength());

            // تنظیم portToBoxMap برای سیم‌های موجود
            w.setPortToBoxMap(this.portToBoxMap);
        }

        // حذف سیم‌های نامعتبر
        if (!invalidWires.isEmpty()) {
            this.wires.removeAll(invalidWires);
        }

        // --- Overlay setup ---
        overlay = new Overlay();
        area.add(overlay);
        overlay.setBounds(0, 0, area.getWidth(), area.getHeight());
        overlay.setVisible(false);
        area.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                overlay.setSize(area.getSize());
            }
        });

        // --- Preview listener (حرکت موس برای خط پیش‌نمایش) ---
        previewListener = new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                if (!drawing) return;
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), overlay);
                overlay.updateLine(startPt, p);
            }
        };

        // --- اتصال به پورت‌ها و کنترل کلیک برای لغو پیش‌نمایش ---
        attachToPorts(area);
        area.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (drawing) cancelPreview();
            }
        });
    }


    private void attachToPorts(Container c){for(Component comp:c.getComponents()){if(comp instanceof PortView pv){pv.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){handlePortClick(pv);} });} else if(comp instanceof Container inner && comp!=overlay){attachToPorts(inner);} }}

    private void handlePortClick(PortView pv){
        PortModel pm=pv.getModel();
        if(!drawing){
            if(!pm.isInput()){
                drawing=true;
                startPort=pm;
                Point center=new Point(pv.getWidth()/2,pv.getHeight()/2);
                startPt=SwingUtilities.convertPoint(pv,center,overlay);
                overlay.beginPreview();
                overlay.updateLine(startPt,startPt);
                overlay.setVisible(true);
                area.addMouseMotionListener(previewListener);
                area.setComponentZOrder(overlay,area.getComponentCount()-1); // مطمئن شویم روی همه دیده می‌شود
            }
        }
        else {
            // در حالت درحال رسم، فقط کلیک روی ورودی‌ها بررسی شود
            if(pm.isInput()){
                // 1) قفل‌بودن ورودی
                if(lockedInputs.contains(pm)){
                    Toolkit.getDefaultToolkit().beep();
                    cancelPreview();
                    return;
                }
                // 2) سازگاری شکل‌ها: خروجیِ شروع → ورودی مقصد
                // 2) محدودیتی از نظر شکل برای سیم‌کشی وجود ندارد (صرفاً جهت مهم است)
                boolean shapeOk = true;
// (no shape-based veto for wiring)

                // 3) شرط قبلی پروژه را هم نگه می‌داریم (خروجی→ورودی + هر منطق اضافی در PortModel)
                if(startPort.canConnectTo(pm)){
                    WireModel wm=new WireModel(startPort,pm);
                    double len=wm.getLength();
                    if(!usageModel.useWire(len)){
                        Toolkit.getDefaultToolkit().beep();
                        cancelPreview();
                        return;
                    }

                    // تنظیم portToBoxMap برای سیم جدید - این خط حیاتی است!
                    wm.setPortToBoxMap(portToBoxMap);

                    wires.add(wm);
                    destMap.put(wm,findDestBox(pm));
                    lockedInputs.add(pm);
                    PortView srcPV=findPortView(area,startPort);
                    WireView wv=new WireView(wm,srcPV,pv);
                    wv.setBounds(0,0,area.getWidth(),area.getHeight());
                    area.add(wv,0);
                    area.setComponentZOrder(overlay,area.getComponentCount()-1);
                    area.revalidate();
                    area.repaint();
                    if(networkChanged!=null) networkChanged.run();
                    new WireEditorController(area, wm, wv, gameView.getSystemBoxViews(), coinModel, usageModel, networkChanged);
                }
                else {
                    // اگر به هر دلیل دیگری PortModel اجازه اتصال ندهد
                    Toolkit.getDefaultToolkit().beep();
                    cancelPreview();
                    return;
                }
            }
            // پس از هر تلاش، پیش‌نمایش را ببندیم (مطابق رفتار قبلی)
            cancelPreview();
        }
    }

    private void cancelPreview(){drawing=false; overlay.clearLine(); overlay.setVisible(false); area.removeMouseMotionListener(previewListener); overlay.endPreview();}

    public void freePortsForWire(WireModel wm){lockedInputs.remove(wm.getDstPort()); usageModel.freeWire(wm.getLength()); if(networkChanged!=null) networkChanged.run();}


    private PortView findPortView(Container c, PortModel pm){for(Component comp:c.getComponents()){if(comp instanceof PortView pv && pv.getModel()==pm) return pv; if(comp instanceof Container inner){PortView f=findPortView(inner,pm); if(f!=null) return f;}} return null;}

    private static class Overlay extends JComponent{
        private Point p1,p2; @Override public boolean contains(int x,int y){return false;} void beginPreview(){ } void endPreview(){ } @Override protected void paintComponent(Graphics g){super.paintComponent(g); if(p1!=null&&p2!=null){Graphics2D g2=(Graphics2D)g.create(); g2.setColor(Color.RED); g2.setStroke(new BasicStroke(Config.STROKE_WIDTH_WIRE)); g2.drawLine(p1.x,p1.y,p2.x,p2.y); g2.dispose();}}
        void updateLine(Point a,Point b){p1=a;p2=b; repaint();} void clearLine(){p1=p2=null; repaint();}
    }
    private static Map<PortModel, SystemBoxModel> buildPortToBoxMap(List<SystemBoxModel> boxes) {
        Map<PortModel, SystemBoxModel> map = new HashMap<>();
        for (SystemBoxModel b : boxes) {
            for (PortModel p : b.getInPorts())  map.put(p, b);
            for (PortModel p : b.getOutPorts()) map.put(p, b);
        }
        return map;
    }
    private SystemBoxModel findDestBox(PortModel pm) {
        return boxes.stream()
                .filter(b -> b.getInPorts().contains(pm))
                .findFirst()
                .orElse(null); // به جای orElseThrow()
    }
}
