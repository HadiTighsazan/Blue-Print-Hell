package com.blueprinthell.ui;

import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.engine.TimelineController;
import com.blueprinthell.engine.NetworkSnapshot;
import com.blueprinthell.model.Packet;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.Port;
import com.blueprinthell.model.SystemBox;
import com.blueprinthell.model.Wire;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class GameScreen extends JLayeredPane {
    private List<SystemBox> systems;
    private List<Wire> wires;
    private NetworkController networkController;
    private InputManager inputManager;
    private WirePreviewLayer previewLayer;

    private JPanel hudPanel;
    private JLabel lblWire, lblCoins, lblLoss;
    private JButton btnStart, btnShop, btnPausePlay;
    private JSlider sliderTime;
    private Timer gameTimer;

    private TimelineController timelineCtrl;
    private int timelineCapacity;

    private boolean deleteMode = false;

    public GameScreen() {
        setLayout(null);
        setFocusable(true);
        initKeyBindings();

        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!deleteMode || !SwingUtilities.isLeftMouseButton(e)) return;
                Component under = SwingUtilities.getDeepestComponentAt(
                        GameScreen.this, e.getX(), e.getY());
                if (under instanceof Wire w) {
                    networkController.removeWire(w);
                    wires.remove(w);
                    remove(w);
                    syncViewToModel2();
                }
            }
        });
    }

    private void initKeyBindings() {
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        int fps = 60;
        // Rewind one second
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "rewind1s");
        am.put("rewind1s", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (timelineCtrl.isPlaying()) timelineCtrl.pause();
                int offset = timelineCtrl.getCurrentOffset() + fps;
                offset = Math.min(offset, timelineCtrl.getSnapshotCount() - 1);
                timelineCtrl.scrubTo(offset);
                syncViewToModel2();
            }
        });
        // Forward one second
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "forward1s");
        am.put("forward1s", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!timelineCtrl.isPlaying()) {
                    int offset = timelineCtrl.getCurrentOffset() - fps;
                    offset = Math.max(offset, 0);
                    timelineCtrl.scrubTo(offset);
                    syncViewToModel2();
                }
            }
        });
        // Toggle delete mode
        im.put(KeyStroke.getKeyStroke("SPACE"), "toggleDelete");
        am.put("toggleDelete", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                deleteMode = !deleteMode;
                setCursor(deleteMode
                        ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });
    }

    /** Load level and initialize everything */
    public void loadLevel(int levelIndex) {
        removeAll();
        if (gameTimer != null && gameTimer.isRunning()) gameTimer.stop();

        if (levelIndex == 1) {
            int cx = 500, cy = 300;
            int w = 100, h = 60, gap = 120;
            systems = Arrays.asList(
                    new SystemBox(cx - gap, cy - gap, w, h, 0, 1),
                    new SystemBox(cx + gap, cy - gap, w, h, 0, 1),
                    new SystemBox(cx - gap, cy + gap, w, h, 1, 0),
                    new SystemBox(cx + gap, cy + gap, w, h, 1, 0)
            );
        } else {
            systems = Arrays.asList(
                    new SystemBox(80, 80, 100, 60, 0, 1),
                    new SystemBox(280, 80, 100, 60, 1, 1),
                    new SystemBox(480, 80, 100, 60, 1, 1),
                    new SystemBox(80, 280, 100, 60, 1, 1),
                    new SystemBox(280, 280, 100, 60, 1, 1),
                    new SystemBox(480, 280, 100, 60, 1, 0)
            );
        }
        wires = new ArrayList<>();

        timelineCapacity = 5 * 60;
        networkController = new NetworkController(wires, systems, 1500);
        timelineCtrl = new TimelineController(networkController, timelineCapacity);

        systems.forEach(s -> {
            add(s);
            setLayer(s, JLayeredPane.DEFAULT_LAYER);
        });

        inputManager = new InputManager(networkController);
        inputManager.setWireCreatedCallback(w -> {
            wires.add(w);
            add(w, JLayeredPane.DEFAULT_LAYER);
            w.setBounds(0, 0, getWidth(), getHeight());
            updateHUD();
        });

        previewLayer = new WirePreviewLayer(inputManager);
        previewLayer.setBounds(0, 0, getWidth(), getHeight());
        previewLayer.setEnabled(false);
        add(previewLayer, JLayeredPane.PALETTE_LAYER);

        inputManager.registerHitContainer(this);
        inputManager.registerEventContainer(previewLayer);
        systems.forEach(s -> {
            s.getOutPorts().forEach(inputManager::registerPort);
            s.getInPorts().forEach(inputManager::registerPort);
        });

        initHUD();
        SwingUtilities.invokeLater(this::requestFocusInWindow);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int w = getWidth(), h = getHeight();
                previewLayer.setBounds(0, 0, w, h);
                wires.forEach(wr -> wr.setBounds(0, 0, w, h));
                if (hudPanel != null) hudPanel.setBounds(0, 0, w, hudPanel.getHeight());
            }
        });

        revalidate(); repaint();
    }

    private void initHUD() {
        if (hudPanel != null) remove(hudPanel);
        hudPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        hudPanel.setBackground(new Color(0, 0, 0, 160));
        hudPanel.setBounds(0, 0, getWidth(), 40);

        lblWire = new JLabel(); lblCoins = new JLabel(); lblLoss = new JLabel();
        btnStart = new JButton("Start"); btnShop = new JButton("Shop");
        sliderTime = new JSlider(0, 0, 0);
        sliderTime.setEnabled(false);
        btnPausePlay = new JButton("Pause");

        btnStart.addActionListener(this::onStart);
        btnShop.addActionListener(e -> openShop());

        btnPausePlay.addActionListener(e -> {
            if (timelineCtrl.isPlaying()) {
                timelineCtrl.pause();
                btnPausePlay.setText("Play");
                sliderTime.setEnabled(true);
                int sz = timelineCtrl.getSnapshotCount();
                sliderTime.setMaximum(Math.max(0, sz - 1));
                sliderTime.setValue(0);
                if (sz > 0) {
                    timelineCtrl.scrubTo(0);
                    syncViewToModel2();
                }
            } else {
                timelineCtrl.resume();
                btnPausePlay.setText("Pause");
                sliderTime.setEnabled(false);
                sliderTime.setValue(0);
                syncViewToModel2();
                SwingUtilities.invokeLater(this::requestFocusInWindow);
            }
        });

        sliderTime.addChangeListener(e -> {
            if (sliderTime.isEnabled() && !sliderTime.getValueIsAdjusting() && !timelineCtrl.isPlaying()) {
                int val = sliderTime.getValue();
                int count = timelineCtrl.getSnapshotCount();
                if (val >= 0 && val < count) {
                    timelineCtrl.scrubTo(val);
                    syncViewToModel2();
                }
            }
        });

        hudPanel.add(lblWire); hudPanel.add(lblCoins); hudPanel.add(lblLoss);
        hudPanel.add(btnStart); hudPanel.add(btnShop); hudPanel.add(sliderTime);
        hudPanel.add(btnPausePlay);
        add(hudPanel, JLayeredPane.PALETTE_LAYER);
        updateHUD();
    }

    private void syncViewToModel2() {
        // Remove packets no longer in model
        Set<Packet> modelPackets = new HashSet<>(networkController.getPackets());
        // Identify and remove stale components
        for (Component comp : getComponents()) {
            if (comp instanceof Packet p && !modelPackets.contains(p)) {
                remove(p);
            }
        }
        // Add missing packets and update position
        for (Packet p : modelPackets) {
            if (p.getParent() == null) {
                add(p, JLayeredPane.DEFAULT_LAYER);
            }
            p.updatePosition();
        }
        revalidate(); repaint();
    }

    private void updateHUD() {
        lblWire.setText("Wire Left: " + String.format("%.0f", networkController.getRemainingWireLength()));
        lblCoins.setText("Coins: "      + networkController.getCoins());
        lblLoss.setText("Loss: "       + networkController.getPacketLoss());
    }

    private void onStart(ActionEvent e) {
        systems.stream()
                .filter(s -> s.getInPorts().isEmpty())
                .forEach(s -> {
                    Port out = s.getOutPorts().get(0);
                    Wire w = wires.stream()
                            .filter(x -> x.getSrcPort() == out)
                            .findFirst().orElse(null);
                    if (w != null) {
                        Packet p = new Packet(PacketType.SQUARE, 100);
                        w.attachPacket(p, 0.0);
                        add(p, JLayeredPane.DEFAULT_LAYER);
                    } else {
                        networkController.incrementPacketLoss();
                    }
                });
        updateHUD();
        if (gameTimer != null) gameTimer.stop();
        gameTimer = new Timer(16, ev -> {
            double dt = 0.016;
            if (timelineCtrl.isPlaying()) {
                networkController.tick(dt);
                timelineCtrl.recordFrame();
                syncViewToModel2();
                int avail = timelineCtrl.getSnapshotCount();
                if (avail > 0) {
                    sliderTime.setMaximum(avail - 1);
                    if (sliderTime.getValue() != 0) sliderTime.setValue(0);
                }
            }
            updateHUD(); repaint();
        });
        gameTimer.start();
        btnStart.setEnabled(false);
    }

    private void openShop() {
        if (gameTimer != null && gameTimer.isRunning()) {
            gameTimer.stop();
        }
        timelineCtrl.pause();
        syncViewToModel2();
        ShopDialog dlg = new ShopDialog(
                SwingUtilities.getWindowAncestor(this),
                networkController, this::updateHUD
        );
        dlg.setVisible(true);
        timelineCtrl.resume();
        syncViewToModel2();
        if (gameTimer != null) gameTimer.start();
    }
}
