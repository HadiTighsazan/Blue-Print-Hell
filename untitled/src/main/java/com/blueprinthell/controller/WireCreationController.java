package com.blueprinthell.controller;

import com.blueprinthell.config.Config;
import com.blueprinthell.model.*;
import com.blueprinthell.view.*;
import com.blueprinthell.view.screens.GameScreenView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

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

    public WireCreationController(GameScreenView gameView,
                                  SimulationController simulation,
                                  List<SystemBoxModel> boxes,
                                  List<WireModel> wires,
                                  Map<WireModel, SystemBoxModel> destMap,
                                  WireUsageModel usageModel,
                                  CoinModel coinModel,
                                  Runnable networkChanged) {
        this.gameView  = gameView;
        this.simulation = simulation;
        this.boxes     = boxes;
        this.wires     = wires;
        this.destMap   = destMap;
        this.usageModel = usageModel;
        this.coinModel  = coinModel;
        this.networkChanged = networkChanged;

        this.area = gameView.getGameArea();
        area.setLayout(null);

        for (WireModel w : wires) {
            destMap.put(w, findDestBox(w.getDstPort()));
            lockedInputs.add(w.getDstPort());
            usageModel.useWire(w.getLength());
        }

        overlay = new Overlay();
        area.add(overlay);
        overlay.setBounds(0,0,area.getWidth(),area.getHeight());
        overlay.setVisible(false);
        area.addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){overlay.setSize(area.getSize());}});

        previewListener = new MouseMotionAdapter(){@Override public void mouseMoved(MouseEvent e){if(!drawing)return; Point p=SwingUtilities.convertPoint(e.getComponent(),e.getPoint(),overlay); overlay.updateLine(startPt,p);}};

        attachToPorts(area);
        area.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(drawing) cancelPreview();}});
    }

    private void attachToPorts(Container c){for(Component comp:c.getComponents()){if(comp instanceof PortView pv){pv.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){handlePortClick(pv);} });} else if(comp instanceof Container inner && comp!=overlay){attachToPorts(inner);} }}

    private void handlePortClick(PortView pv){PortModel pm=pv.getModel(); if(!drawing){if(!pm.isInput()){drawing=true; startPort=pm; Point center=new Point(pv.getWidth()/2,pv.getHeight()/2); startPt=SwingUtilities.convertPoint(pv,center,overlay); overlay.beginPreview(); overlay.updateLine(startPt,startPt); overlay.setVisible(true); area.addMouseMotionListener(previewListener); area.setComponentZOrder(overlay,area.getComponentCount()-1);}} else {if(pm.isInput() && startPort.isCompatibleWith(pm) && !lockedInputs.contains(pm)){WireModel wm=new WireModel(startPort,pm); double len=wm.getLength(); if(!usageModel.useWire(len)){Toolkit.getDefaultToolkit().beep(); cancelPreview(); return;} wires.add(wm); destMap.put(wm,findDestBox(pm)); lockedInputs.add(pm); PortView srcPV=findPortView(area,startPort); WireView wv=new WireView(wm,srcPV,pv); wv.setBounds(0,0,area.getWidth(),area.getHeight()); area.add(wv,0); area.setComponentZOrder(overlay,area.getComponentCount()-1); area.revalidate(); area.repaint(); if(networkChanged!=null) networkChanged.run(); new WireEditorController(area, wm, wv, gameView.getSystemBoxViews(), coinModel, usageModel, networkChanged);} cancelPreview();}}

    private void cancelPreview(){drawing=false; overlay.clearLine(); overlay.setVisible(false); area.removeMouseMotionListener(previewListener); overlay.endPreview();}

    public void freePortsForWire(WireModel wm){lockedInputs.remove(wm.getDstPort()); usageModel.freeWire(wm.getLength()); if(networkChanged!=null) networkChanged.run();}

    private SystemBoxModel findDestBox(PortModel pm){return boxes.stream().filter(b->b.getInPorts().contains(pm)).findFirst().orElseThrow();}

    private PortView findPortView(Container c, PortModel pm){for(Component comp:c.getComponents()){if(comp instanceof PortView pv && pv.getModel()==pm) return pv; if(comp instanceof Container inner){PortView f=findPortView(inner,pm); if(f!=null) return f;}} return null;}

    private static class Overlay extends JComponent{
        private Point p1,p2; @Override public boolean contains(int x,int y){return false;} void beginPreview(){ } void endPreview(){ } @Override protected void paintComponent(Graphics g){super.paintComponent(g); if(p1!=null&&p2!=null){Graphics2D g2=(Graphics2D)g.create(); g2.setColor(Color.RED); g2.setStroke(new BasicStroke(Config.STROKE_WIDTH_WIRE)); g2.drawLine(p1.x,p1.y,p2.x,p2.y); g2.dispose();}}
        void updateLine(Point a,Point b){p1=a;p2=b; repaint();} void clearLine(){p1=p2=null; repaint();}
    }
}
