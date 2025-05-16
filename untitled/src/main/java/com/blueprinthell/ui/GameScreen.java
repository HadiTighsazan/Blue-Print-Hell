package com.blueprinthell.ui;

import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.engine.TimelineController;
import com.blueprinthell.model.Packet;
import com.blueprinthell.model.PacketType;
import com.blueprinthell.model.Port;
import com.blueprinthell.model.SystemBox;
import com.blueprinthell.model.Wire;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * پنل اصلی بازی: مدیریت منطق و نمایش گرافیکی شبکه
 */
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

    private int totalPackets;
    private SettingsListener listener;

    private Clip bgClip, impactClip, connectClip, gameoverClip;

    public GameScreen() {
        setLayout(null);
        setFocusable(true);
        initKeyBindings();
        initSounds();
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

    private void initSounds() {
        try {
            bgClip = loadClip("bg_loop.wav");
            impactClip = loadClip("impact_thud.wav");
            connectClip = loadClip("connect_click.wav");
            gameoverClip = loadClip("gameover_jingle.wav");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Clip loadClip(String fileName) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        URL url = getClass().getClassLoader().getResource("resource/" + fileName);
        if (url == null) {
            throw new IOException("Audio resource not found: " + fileName);
        }
        AudioInputStream ais = AudioSystem.getAudioInputStream(url);
        Clip clip = AudioSystem.getClip();
        clip.open(ais);
        return clip;
    }
    /** Load level and initialize everything */
    public void loadLevel(int levelIndex) {
        removeAll();
        if (gameTimer != null && gameTimer.isRunning()) gameTimer.stop();

        if (listener == null) {
            listener = (SettingsListener) SwingUtilities.getWindowAncestor(this);
        }
        switch (levelIndex) {
            case 1: totalPackets = 2;  break;
            case 2: totalPackets = 8;  break;
            default: totalPackets = levelIndex * 2; break;
        }

        if (levelIndex == 1) {
            int cx = 500, cy = 300, w = 100, h = 60, gap = 120;
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

        networkController = new NetworkController(wires, systems, 1500);
        timelineCapacity   = 0;
        timelineCtrl       = new TimelineController(networkController, timelineCapacity);

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
            playConnect();
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

        revalidate();
        repaint();
        // شروع موسیقی پس‌زمینه
    }

    private void initHUD() {
        if (hudPanel != null) remove(hudPanel);
        hudPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        hudPanel.setBackground(new Color(0, 0, 0, 160));
        hudPanel.setBounds(0, 0, getWidth(), 40);

        lblWire = new JLabel();
        lblCoins = new JLabel();
        lblLoss = new JLabel();
        btnStart = new JButton("Start");
        btnShop = new JButton("Shop");
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
        hudPanel.add(btnStart); hudPanel.add(btnShop); hudPanel.add(sliderTime); hudPanel.add(btnPausePlay);
        add(hudPanel, JLayeredPane.PALETTE_LAYER);
        updateHUD();
    }

    private void syncViewToModel2() {
        Set<Packet> modelPackets = new HashSet<>(networkController.getPackets());
        for (Component comp : getComponents()) {
            if (comp instanceof Packet p && !modelPackets.contains(p)) {
                remove(p);
            }
        }
        for (Packet p : modelPackets) {
            if (p.getParent() == null) add(p, JLayeredPane.DEFAULT_LAYER);
            p.updatePosition();
        }
        revalidate(); repaint();
    }

    private void updateHUD() {
        lblWire.setText("Wire Left: " + String.format("%.0f", networkController.getRemainingWireLength()));
        lblCoins.setText("Coins: "      + networkController.getCoins());
        lblLoss.setText("Loss: "       + networkController.getPacketLoss() + " / " + totalPackets);
        checkGameOver();
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
                        playImpact();
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
        if (gameTimer != null && gameTimer.isRunning()) gameTimer.stop();
        timelineCtrl.pause(); syncViewToModel2();
        ShopDialog dlg = new ShopDialog(SwingUtilities.getWindowAncestor(this), networkController, this::updateHUD);
        dlg.setVisible(true);
        timelineCtrl.resume(); syncViewToModel2();
        if (gameTimer != null) gameTimer.start();
    }

    // بررسی رسیدن تعداد بسته‌های از دست رفته به نصف کل بسته‌ها
    private void checkGameOver() {
        if (networkController.getPacketLoss() * 2 >= totalPackets) {
            triggerGameOver();
        }
    }

    // نمایش لایه‌ی Game Over و پخش صدا
    private void triggerGameOver() {
        if (gameTimer != null && gameTimer.isRunning()) gameTimer.stop();
        playGameOverSound(); bgClip.stop();
        GameOverScreen gos = new GameOverScreen(listener);
        gos.setBounds(0, 0, getWidth(), getHeight());
        add(gos, JLayeredPane.MODAL_LAYER); revalidate(); repaint();
    }

    // پخش موسیقی پس‌زمینه
    private void playBg() {
        if (bgClip != null) bgClip.loop(Clip.LOOP_CONTINUOUSLY);
    }
    // پخش صدای برخورد
    private void playImpact() {
        if (impactClip == null) return;
        if (impactClip.isRunning()) impactClip.stop();
        impactClip.setFramePosition(0);
        impactClip.start();
    }
    // پخش صدای اتصال سیم
    private void playConnect() {
        if (connectClip == null) return;
        if (connectClip.isRunning()) connectClip.stop();
        connectClip.setFramePosition(0);
        connectClip.start();
    }
    // پخش صدای گیم‌اور
    private void playGameOverSound() {
        if (gameoverClip == null) return;
        if (gameoverClip.isRunning()) gameoverClip.stop();
        gameoverClip.setFramePosition(0);
        gameoverClip.start();
    }
}
