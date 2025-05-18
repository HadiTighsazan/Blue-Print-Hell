package com.blueprinthell.ui;

import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.engine.TimelineController;
import com.blueprinthell.model.*;

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

    private int totalPackets;
    private SettingsListener listener;

    private Clip bgClip, impactClip, connectClip, gameoverClip;

    private int rewindKey = KeyEvent.VK_LEFT;
    private int forwardKey = KeyEvent.VK_RIGHT;



    public GameScreen() {
        setLayout(null);
        setFocusable(true);
        initKeyBindings();
        initSounds();
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!deleteMode || !SwingUtilities.isLeftMouseButton(e))
                    return;
                Point click = e.getPoint();
                Wire w = findWireAt(click, 5);
                if (w != null) {
                    networkController.removeWire(w);
                    wires.remove(w);
                    remove(w);
                    syncViewToModel2();
                    updateHUD();
                }
            }
        });

    }

    public void updateKeyBindings(int newRewindKey, int newForwardKey) {
        this.rewindKey = newRewindKey;
        this.forwardKey = newForwardKey;
        applyKeyBindings();
    }

    private void initKeyBindings() {
        applyKeyBindings();
        bindToggleDelete();
    }

    private void applyKeyBindings() {
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        int fps = 60;
        im.clear();
        am.clear();
        im.put(KeyStroke.getKeyStroke(rewindKey, 0), "rewind1s");
        am.put("rewind1s", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (timelineCtrl.isPlaying()) timelineCtrl.pause();
                int offset = timelineCtrl.getCurrentOffset() + fps;
                offset = Math.min(offset, timelineCtrl.getSnapshotCount() - 1);
                timelineCtrl.scrubTo(offset);
                syncViewToModel2();
            }
        });
        im.put(KeyStroke.getKeyStroke(forwardKey, 0), "forward1s");
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
    }

    private void bindToggleDelete() {
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
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
        if (url == null) throw new IOException("Audio resource not found: " + fileName);
        AudioInputStream ais = AudioSystem.getAudioInputStream(url);
        Clip clip = AudioSystem.getClip();
        clip.open(ais);
        return clip;
    }

    public void loadLevel(int levelIndex) {
        removeAll();
        if (gameTimer != null && gameTimer.isRunning()) gameTimer.stop();
        if (listener == null) listener = (SettingsListener) SwingUtilities.getWindowAncestor(this);
        int cx = getWidth()/2, cy = getHeight()/2;
        switch (levelIndex) {
            case 1:
                int w1=100,h1=60,g1=150;
                systems = Arrays.asList(
                        new SystemBox(cx-g1, cy-g1, w1, h1, 0, PortShape.SQUARE, 2, PortShape.TRIANGLE),
                        new SystemBox(cx+g1, cy-g1, w1, h1, 2, PortShape.TRIANGLE, 0, PortShape.SQUARE),
                        new SystemBox(cx-g1, cy+g1, w1, h1, 1, PortShape.SQUARE, 1, PortShape.SQUARE),
                        new SystemBox(cx+g1, cy+g1, w1, h1, 1, PortShape.TRIANGLE,1, PortShape.TRIANGLE)
                );
                break;
            case 2:
                int w2=80,h2=50,g2=200;
                systems = Arrays.asList(
                        new SystemBox(cx+g2, cy,        w2,h2, 0,PortShape.SQUARE, 2,PortShape.SQUARE),
                        new SystemBox(cx-g2, cy,        w2,h2, 2,PortShape.TRIANGLE,0,PortShape.TRIANGLE),
                        new SystemBox(cx,        cy+g2, w2,h2, 0,PortShape.SQUARE, 1,PortShape.TRIANGLE),
                        new SystemBox(cx,        cy-g2, w2,h2, 1,PortShape.TRIANGLE,1,PortShape.SQUARE),
                        new SystemBox(cx+g2, cy+g2, w2,h2,1,PortShape.SQUARE, 1,PortShape.SQUARE),
                        new SystemBox(cx+g2, cy-g2, w2,h2,1,PortShape.TRIANGLE,1,PortShape.TRIANGLE),
                        new SystemBox(cx-g2, cy+g2, w2,h2,1,PortShape.SQUARE, 1,PortShape.TRIANGLE),
                        new SystemBox(cx-g2, cy-g2, w2,h2,1,PortShape.TRIANGLE,1,PortShape.SQUARE)
                );
                break;
            default:
                systems = Arrays.asList(
                        new SystemBox( 80,  80,100,60,0,PortShape.SQUARE,1,PortShape.SQUARE),
                        new SystemBox(280,  80,100,60,1,PortShape.TRIANGLE,1,PortShape.SQUARE),
                        new SystemBox(480,  80,100,60,1,PortShape.SQUARE,1,PortShape.TRIANGLE),
                        new SystemBox( 80, 280,100,60,1,PortShape.SQUARE,1,PortShape.SQUARE),
                        new SystemBox(280, 280,100,60,1,PortShape.TRIANGLE,1,PortShape.TRIANGLE),
                        new SystemBox(480, 280,100,60,1,PortShape.SQUARE,0,PortShape.SQUARE)
                );
                break;
        }
        int originPorts = systems.stream()
                .filter(s -> s.getInPorts().isEmpty())
                .mapToInt(s -> s.getOutPorts().size())
                .sum();
        totalPackets = originPorts * 3;

        wires = new ArrayList<>();
        networkController = new NetworkController(wires, systems,1500);
        timelineCapacity=0;
        timelineCtrl=new TimelineController(networkController,timelineCapacity);
        systems.forEach(s->{add(s);setLayer(s,JLayeredPane.DEFAULT_LAYER);} );
        inputManager=new InputManager(networkController);
        inputManager.setWireCreatedCallback(w->{wires.add(w);add(w,JLayeredPane.DEFAULT_LAYER);w.setBounds(0,0,getWidth(),getHeight());updateHUD();playConnect();});
        previewLayer=new WirePreviewLayer(inputManager);
        previewLayer.setBounds(0,0,getWidth(),getHeight());previewLayer.setEnabled(false);add(previewLayer,JLayeredPane.PALETTE_LAYER);
        inputManager.registerHitContainer(this);
        inputManager.registerEventContainer(previewLayer);
        systems.forEach(s->{s.getOutPorts().forEach(inputManager::registerPort);s.getInPorts().forEach(inputManager::registerPort);}   );
        initHUD();SwingUtilities.invokeLater(this::requestFocusInWindow);
        addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){int w=getWidth(),h=getHeight();previewLayer.setBounds(0,0,w,h);wires.forEach(wr->wr.setBounds(0,0,w,h));if(hudPanel!=null)hudPanel.setBounds(0,0,w,hudPanel.getHeight());}});
        revalidate();repaint();
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


    }






    private void onStart(ActionEvent e) {
        List<Port> originPorts = systems.stream()
                .filter(s -> s.getInPorts().isEmpty())
                .flatMap(s -> s.getOutPorts().stream())
                .collect(Collectors.toList());
        int cycles = 3;

        Timer releaseTimer = new Timer(2000, null);
        releaseTimer.addActionListener(new ActionListener() {
            int cycleCount = 0;
            Random rand = new Random();

            @Override
            public void actionPerformed(ActionEvent evt) {
                if (cycleCount >= cycles) {
                    releaseTimer.stop();
                    return;
                }
                for (Port out : originPorts) {
                    Wire w = wires.stream()
                            .filter(x -> x.getSrcPort() == out)
                            .findFirst().orElse(null);
                    PacketType type = rand.nextBoolean()
                            ? PacketType.SQUARE
                            : PacketType.TRIANGLE;
                    if (w != null) {
                        Packet p = new Packet(type, 100);
                        w.attachPacket(p, 0.0);
                        add(p, JLayeredPane.DEFAULT_LAYER);
                    } else {
                        networkController.incrementPacketLoss();
                        playImpact();
                        updateHUD();
                    }
                }
                cycleCount++;
            }
        });
        releaseTimer.setInitialDelay(0);
        releaseTimer.start();

        timelineCtrl.resume();

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

                updateHUD();

                if (networkController.getPacketLoss() * 2 >= totalPackets) {
                    triggerGameOver();
                }
                else if (allPacketsProcessed()) {
                    triggerMissionPassed();
                }

            }
            repaint();
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

    private void checkGameOver() {
        if (networkController.getPacketLoss() * 2 >= totalPackets) {
            triggerGameOver();
        }
    }


    private boolean allPacketsProcessed() {
        boolean noInFlight = networkController.getPackets().isEmpty();
        boolean noInBuffer = systems.stream()
                .filter(s -> !s.getOutPorts().isEmpty())
                .flatMap(s -> s.getBuffer().stream())
                .findAny().isEmpty();
        return noInFlight && noInBuffer;
    }



    private void triggerGameOver() {
        if (gameTimer != null && gameTimer.isRunning()) gameTimer.stop();
        playGameOverSound(); bgClip.stop();
        GameOverScreen gos = new GameOverScreen(listener);
        gos.setBounds(0, 0, getWidth(), getHeight());
        add(gos, JLayeredPane.MODAL_LAYER); revalidate(); repaint();
    }

    private void triggerMissionPassed() {
        if (gameTimer != null && gameTimer.isRunning()) gameTimer.stop();
        MissionPassedScreen mps = new MissionPassedScreen(listener);
        mps.setBounds(0, 0, getWidth(), getHeight());
        add(mps, JLayeredPane.MODAL_LAYER);
        revalidate(); repaint();
    }


    private double pointToSegmentDistance(Point p, Point p1, Point p2) {
        double x0 = p.x, y0 = p.y;
        double x1 = p1.x, y1 = p1.y;
        double x2 = p2.x, y2 = p2.y;
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx*dx + dy*dy;
        if (len2 == 0) return p.distance(p1);
        double t = ((x0 - x1)*dx + (y0 - y1)*dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double projx = x1 + t*dx, projy = y1 + t*dy;
        return p.distance(projx, projy);
    }


    private Wire findWireAt(Point click, double tol) {
        Wire best = null;
        double bestDist = tol;
        for (Wire w : wires) {
            Point src = SwingUtilities.convertPoint(
                    w.getSrcPort(), w.getSrcPort().getWidth()/2, w.getSrcPort().getHeight()/2, this);
            Point dst = SwingUtilities.convertPoint(
                    w.getDstPort(), w.getDstPort().getWidth()/2, w.getDstPort().getHeight()/2, this);
            double d = pointToSegmentDistance(click, src, dst);
            if (d < bestDist) {
                bestDist = d;
                best = w;
            }
        }
        return best;
    }



    private void playBg() {
        if (bgClip != null) bgClip.loop(Clip.LOOP_CONTINUOUSLY);
    }
    private void playImpact() {
        if (impactClip == null) return;
        if (impactClip.isRunning()) impactClip.stop();
        impactClip.setFramePosition(0);
        impactClip.start();
    }
    private void playConnect() {
        if (connectClip == null) return;
        if (connectClip.isRunning()) connectClip.stop();
        connectClip.setFramePosition(0);
        connectClip.start();
    }
    private void playGameOverSound() {
        if (gameoverClip == null) return;
        if (gameoverClip.isRunning()) gameoverClip.stop();
        gameoverClip.setFramePosition(0);
        gameoverClip.start();
    }


}
