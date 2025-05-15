// GameScreen.java
package com.blueprinthell.ui;

import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.engine.TimelineController;
import com.blueprinthell.engine.SnapshotManager;
import com.blueprinthell.engine.NetworkSnapshot;
import com.blueprinthell.model.Packet;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.Port;
import com.blueprinthell.model.SystemBox;
import com.blueprinthell.model.Wire;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GameScreen extends JLayeredPane {
    private List<SystemBox>       systems;
    private List<Wire>            wires;
    private NetworkController     networkController;
    private InputManager          inputManager;
    private WirePreviewLayer      previewLayer;

    // HUD
    private JPanel    hudPanel;
    private JLabel    lblWire, lblCoins, lblLoss;
    private JButton   btnStart, btnShop;
    private JSlider   sliderTime;
    private JButton   btnPausePlay;
    private Timer     gameTimer;

    // Timeline/Snapshots
    private TimelineController timelineCtrl;
    private SnapshotManager    snapMgr;
    private int                timelineCapacity;

    public GameScreen() {
        setLayout(null);
    }

    public void loadLevel(int levelIndex) {
        // ۰) پاکسازی
        removeAll();
        if (gameTimer != null && gameTimer.isRunning()) gameTimer.stop();

        // ۱) سیستم‌ها و سیم‌ها
        if (levelIndex == 1) {
            systems = Arrays.asList(
                    new SystemBox(100, 200, 120, 80, 0, 1),
                    new SystemBox(350, 200, 120, 80, 1, 1),
                    new SystemBox(600, 200, 120, 80, 1, 0)
            );
            wires = new ArrayList<>();
        }
        else {
            systems = Arrays.asList(
                    new SystemBox(80,  80, 100, 60, 0, 1),
                    new SystemBox(280, 80, 100, 60, 1, 1),
                    new SystemBox(480, 80, 100, 60, 1, 1),
                    new SystemBox(80,  280,100, 60, 1, 1),
                    new SystemBox(280, 280,100, 60, 1, 1),
                    new SystemBox(480, 280,100, 60, 1, 0)
            );
            wires = new ArrayList<>();
        }

        // ۲) Network & Timeline
        timelineCapacity    = 5 * 60; // (۵s × ۶۰fps)
        networkController   = new NetworkController(wires, systems, 1500);
        timelineCtrl        = new TimelineController(networkController, timelineCapacity);
        snapMgr             = new SnapshotManager(timelineCapacity);

        // ۳) افزودن سیستم‌ها و سیم‌ها
        systems.forEach(s -> add(s, JLayeredPane.DEFAULT_LAYER));

        // ۴) Input + Preview
        inputManager = new InputManager(networkController);

        inputManager.setWireCreatedCallback(w -> {
            wires.add(w);                    // مدل رو به‌روز کن
            add(w, JLayeredPane.DEFAULT_LAYER);   // و سیم رو روی صفحه بیار
            w.setBounds(0, 0, getWidth(), getHeight());
            repaint();
        });

        previewLayer = new WirePreviewLayer(inputManager);
        previewLayer.setBounds(0, 0, getWidth(), getHeight());
        add(previewLayer, JLayeredPane.DRAG_LAYER);

        previewLayer.setEnabled(true);


        // بازتنظیم با resize:
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = getWidth(), h = getHeight();
                previewLayer.setBounds(0,0,w,h);
                wires.forEach(wr -> wr.setBounds(0,0,w,h));
                if (hudPanel!=null) hudPanel.setBounds(0,0,w,hudPanel.getHeight());
            }
        });

        // ثبت رویدادهای ماوس
        inputManager.registerHitContainer(this);
        inputManager.registerEventContainer(previewLayer);
        systems.forEach(s -> {
            s.getOutPorts().forEach(inputManager::registerPort);
            s.getInPorts().forEach(inputManager::registerPort);
        });

        // ۵) HUD
        initHUD();

        revalidate();
        repaint();

        SwingUtilities.invokeLater(() -> {
            int W = getWidth(), H = getHeight();
            previewLayer.setBounds(0, 0, W, H);
            for (Wire wire : wires) {
                wire.setBounds(0, 0, W, H);
            }
            if (hudPanel != null) {
                hudPanel.setBounds(0, 0, W, hudPanel.getHeight());
            }
        });
    }

    private void initHUD() {
        if (hudPanel!=null) remove(hudPanel);

        hudPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10,5));
        hudPanel.setBackground(new Color(0,0,0,160));
        hudPanel.setBounds(0,0,getWidth(),40);

        lblWire      = new JLabel();
        lblCoins     = new JLabel();
        lblLoss      = new JLabel();
        btnStart     = new JButton("Start");
        btnShop      = new JButton("Shop");
        sliderTime   = new JSlider(0, timelineCapacity-1,0);
        btnPausePlay = new JButton("Pause");

        btnStart.addActionListener(this::onStart);
        btnShop .addActionListener(e->openShop());

        sliderTime.setPreferredSize(new Dimension(200,20));
        sliderTime.addChangeListener(e-> {
            if (!sliderTime.getValueIsAdjusting() && !timelineCtrl.isPlaying()) {
                NetworkSnapshot snap = snapMgr.getSnapshotFramesAgo(sliderTime.getValue());
                networkController.restoreState(snap);
                repaint();
            }
        });

        btnPausePlay.addActionListener(e-> {
            if (timelineCtrl.isPlaying()) {
                timelineCtrl.pause();
                btnPausePlay.setText("Play");
            } else {
                NetworkSnapshot snap = snapMgr.getSnapshotFramesAgo(sliderTime.getValue());
                networkController.restoreState(snap);
                timelineCtrl.resume();
                sliderTime.setValue(0);
                btnPausePlay.setText("Pause");
            }
        });

        hudPanel.add(lblWire);
        hudPanel.add(lblCoins);
        hudPanel.add(lblLoss);
        hudPanel.add(btnStart);
        hudPanel.add(btnShop);
        hudPanel.add(sliderTime);
        hudPanel.add(btnPausePlay);

        add(hudPanel, JLayeredPane.PALETTE_LAYER);
        updateHUD();
    }

    private void updateHUD() {
        lblWire .setText("Wire Left: " + String.format("%.0f", networkController.getRemainingWireLength()));
        lblCoins.setText("Coins: "   + networkController.getCoins());
        lblLoss .setText("Loss: "    + networkController.getPacketLoss());
    }

    private void onStart(ActionEvent e) {
        systems.stream()
                .filter(s->s.getInPorts().isEmpty())
                .forEach(s->{
                    Port out = s.getOutPorts().get(0);
                    Wire w = wires.stream()
                            .filter(x->x.getSrcPort()==out)
                            .findFirst().orElse(null);
                    if (w!=null) {
                        Packet p = new Packet(PacketType.SQUARE,100);
                        w.attachPacket(p,0.0);
                        add(p, JLayeredPane.DEFAULT_LAYER);
                    }
                });
        if (gameTimer!=null) gameTimer.stop();
        gameTimer = new Timer(16, ev->{
            double dt = 0.016;
            if (timelineCtrl.isPlaying()) {
                networkController.tick(dt);
                timelineCtrl.recordFrame();
                if (sliderTime.getValue()!=0) sliderTime.setValue(0);
            } else {
                NetworkSnapshot snap = snapMgr.getSnapshotFramesAgo(sliderTime.getValue());
                networkController.restoreState(snap);
            }
            updateHUD();
            repaint();
        });
        gameTimer.start();
        btnStart.setEnabled(false);
    }

    private void openShop() {
        ShopDialog dlg = new ShopDialog(SwingUtilities.getWindowAncestor(this), networkController);
        dlg.setVisible(true);
        updateHUD();
    }
}
