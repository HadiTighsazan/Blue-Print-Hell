package com.blueprinthell.ui;

import com.blueprinthell.engine.NetworkController;
import com.blueprinthell.engine.TimelineController;
import com.blueprinthell.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GameScreen – مسئول گردآوری لایهٔ گرافیکی شبکه، HUD و منطق بازی.
 * پس از استخراج SoundManager و InputBinder وزن کلاس سبک‌تر شده است.
 */
public class GameScreen extends JLayeredPane {

    /* ======================= اجزای داخلی ======================= */
    private NetworkView   networkView;
    private HudPanel      hudPanel;
    private GameController gameController;

    /* ======================= مدل و کنترلر ====================== */
    private List<SystemBox> systems;
    private List<Wire>      wires;
    private NetworkController  networkController;
    private TimelineController timelineCtrl;

    /* ======================= ورودی و وضعیت ===================== */
    private InputManager inputManager;
    private InputBinder  inputBinder;
    private boolean      deleteMode = false;

    /* ======================= HUD ============================== */
    private JSlider timeSlider;

    /* ======================= دادهٔ بازی ======================= */
    private int    totalPackets;
    private double maxWireLength;
    private static final double WIRE_LENGTH_PER_PORT = 200;

    /* ======================= صدا ============================== */
    private final SoundManager sounds = SoundManager.get();

    /* =========================================================== */
    /*                          سازنده                             */
    /* =========================================================== */
    public GameScreen() {
        setLayout(null);
        setFocusable(true);

        inputBinder = new InputBinder(this,
                KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
                () -> scrub(+60), () -> scrub(-60),
                this::toggleDeleteMode);

        bindDeleteMouse();
        addComponentListener(resizeListener);
    }

    /* ---------------- Listener تغییر اندازه ---------------- */
    private final ComponentAdapter resizeListener = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent e) {
            if (wires == null) return;
            if (networkView != null) networkView.setBounds(0,0,getWidth(),getHeight());
            if (hudPanel   != null) hudPanel.setBounds(0,0,getWidth(),40);
            if (networkView != null) networkView.getPreviewLayer().setBounds(0,0,getWidth(),getHeight());
            wires.forEach(w -> w.setBounds(0,0,getWidth(),getHeight()));
        }
    };

    /* ======================= Load Level ======================== */
    public void loadLevel(int levelIndex) {
        SettingsListener listener = (SettingsListener) SwingUtilities.getWindowAncestor(this);

        removeAll();
        if (gameController != null) gameController.pause();

        /* ---------- ساخت سیستم‌ها و کنترلر شبکه ---------- */
        int cx = getWidth()/2, cy = getHeight()/2;
        systems = LevelLoader.load(levelIndex, cx, cy);
        int totalPorts = systems.stream().mapToInt(s -> s.getInPorts().size()+s.getOutPorts().size()).sum();
        maxWireLength = totalPorts * WIRE_LENGTH_PER_PORT;
        wires = new ArrayList<>();
        networkController = new NetworkController(wires, systems, maxWireLength);
        timelineCtrl      = new TimelineController(networkController, 0);

        /* ------------------ View و ورودی ------------------ */
        inputManager = new InputManager(networkController);
        networkView  = new NetworkView(inputManager);
        networkView.setBounds(0,0,getWidth(),getHeight());
        add(networkView, DEFAULT_LAYER);

        inputManager.registerHitContainer(this);
        inputManager.registerEventContainer(networkView.getPreviewLayer());

        systems.forEach(s -> {
            s.getOutPorts().forEach(inputManager::registerPort);
            s.getInPorts().forEach(inputManager::registerPort);
            s.addComponentListener(moveListenerFor(s));
        });
        networkView.setSystemsAndWires(systems, wires);

        inputManager.setWireCreatedCallback(w -> {
            wires.add(w);
            add(w, DEFAULT_LAYER);
            w.setBounds(0,0,getWidth(),getHeight());
            updateHUD();
            sounds.connect();
        });

        /* ----------------------- HUD ----------------------- */
        hudPanel = new HudPanel(() -> onStart(null), this::openShop, this::togglePausePlay);
        hudPanel.setBounds(0,0,getWidth(),40);
        add(hudPanel, PALETTE_LAYER);
        timeSlider = hudPanel.getTimeSlider();
        timeSlider.setVisible(false);

        int originPorts = systems.stream().filter(s -> s.getInPorts().isEmpty()).mapToInt(s -> s.getOutPorts().size()).sum();
        totalPackets = originPorts * 3;
        updateHUD();
    }

    /* ---------- Listener جابه‌جایی SystemBox (طول منفی) ---------- */
    private ComponentAdapter moveListenerFor(SystemBox s) {
        return new ComponentAdapter() {
            Point lastPos = s.getLocation();
            @Override public void componentMoved(ComponentEvent e) {
                networkView.setSystemsAndWires(systems, wires);
                updateHUD();
                double used = wires.stream().mapToDouble(w -> {
                    Point p1 = SwingUtilities.convertPoint(w.getSrcPort(), w.getSrcPort().getWidth()/2, w.getSrcPort().getHeight()/2, GameScreen.this);
                    Point p2 = SwingUtilities.convertPoint(w.getDstPort(), w.getDstPort().getWidth()/2, w.getDstPort().getHeight()/2, GameScreen.this);
                    return p1.distance(p2);
                }).sum();
                if (maxWireLength - used < 0) {
                    s.setLocation(lastPos);
                    Toolkit.getDefaultToolkit().beep();
                    networkView.setSystemsAndWires(systems, wires);
                    updateHUD();
                } else {
                    lastPos = s.getLocation();
                }
            }
        };
    }

    /* ======================== Start ============================ */
    private void onStart(ActionEvent e) {
        if (!areAllPortsConnected()) { Toolkit.getDefaultToolkit().beep(); return; }
        if (gameController != null) { gameController.stopAll(); gameController = null; }

        List<Port> originPorts = systems.stream()
                .filter(s -> s.getInPorts().isEmpty())
                .flatMap(s -> s.getOutPorts().stream())
                .collect(Collectors.toList());

        gameController = new GameController(networkController, timelineCtrl,
                originPorts, systems, totalPackets,
                this::onGameUpdate, sounds::impact,
                this::triggerGameOver, this::triggerMissionPassed);

        gameController.start();
        hudPanel.setButtonsState(false, true);

    }

    /* ========= به‌روزرسانی کلیدها از SettingsScreen ========= */
    public void updateKeyBindings(int newRewind, int newForward) {
        if (inputBinder != null) inputBinder.rebind(newRewind, newForward);
    }

    private void scrub(int delta) {
        // توقف موقت تایمرها تا هنگام اسکراب بستهٔ جدید ساخته نشود
        if (gameController != null) gameController.pause();

        int offset = timelineCtrl.getCurrentOffset() + delta;
        offset = Math.max(0, Math.min(offset, timelineCtrl.getSnapshotCount() - 1));
        timelineCtrl.scrubTo(offset);
        networkView.syncToModel(networkController.getPackets());

        // ریست پرچم تا پس از Play دوباره بسته‌ها از مبدأ آزاد شوند
        if (gameController != null) gameController.resetGenerationFlag();
    }




    private void toggleDeleteMode() {
        deleteMode = !deleteMode;
        setCursor(deleteMode ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                : Cursor.getDefaultCursor());
    }

    /* ======================= Pause/Resume ====================== */
    private void togglePausePlay() {
        if (gameController == null) return;
        if (timelineCtrl.isPlaying()) {
            gameController.pause();
            hudPanel.setButtonsState(false, false);
        } else {
            gameController.resume();
            hudPanel.setButtonsState(false, true);
            requestFocusInWindow();
        }
    }

    /* ======================= Frame Update ====================== */
    private void onGameUpdate() {
        networkView.syncToModel(networkController.getPackets());
        updateHUD();
    }

    /* --------------------------- Shop -------------------------- */
    private void openShop() {
        if (gameController != null) gameController.pause();
        timelineCtrl.pause();
        networkView.syncToModel(networkController.getPackets());
        ShopDialog dlg = new ShopDialog(SwingUtilities.getWindowAncestor(this), networkController, this::updateHUD);
        dlg.setVisible(true);
        if (gameController != null) {
            gameController.resume();
            gameController.resetMissionFlag();
        }
        requestFocusInWindow();
    }

    /* ---------------- Game Over / Mission Passed -------------- */
    private void triggerGameOver() {
        sounds.gameover();
        sounds.stopBg();
        if (gameController != null) gameController.pause();
        GameOverScreen gos = new GameOverScreen((SettingsListener) SwingUtilities.getWindowAncestor(this));
        gos.setBounds(0, 0, getWidth(), getHeight());
        add(gos, MODAL_LAYER);
        revalidate(); repaint();
    }

    private void triggerMissionPassed() {
        if (gameController != null) gameController.pause();
        MissionPassedScreen mps = new MissionPassedScreen((SettingsListener) SwingUtilities.getWindowAncestor(this));
        mps.setBounds(0, 0, getWidth(), getHeight());
        add(mps, MODAL_LAYER);
        revalidate(); repaint();
    }

    /* ------------------------ HUD ----------------------------- */
    private void updateHUD() {
        double used = wires.stream().mapToDouble(w -> {
            Point p1 = SwingUtilities.convertPoint(w.getSrcPort(), w.getSrcPort().getWidth()/2, w.getSrcPort().getHeight()/2, this);
            Point p2 = SwingUtilities.convertPoint(w.getDstPort(), w.getDstPort().getWidth()/2, w.getDstPort().getHeight()/2, this);
            return p1.distance(p2);
        }).sum();
        hudPanel.update(maxWireLength - used, networkController.getCoins(), networkController.getPacketLoss(), totalPackets);
        hudPanel.setButtonsState(areAllPortsConnected(), timelineCtrl.isPlaying());
    }

    /** آیا همهٔ پورت‌ها متصل‌اند؟ */
    private boolean areAllPortsConnected() {
        return systems.stream().flatMap(s -> {
            List<Port> all = new ArrayList<>();
            all.addAll(s.getOutPorts()); all.addAll(s.getInPorts());
            return all.stream();
        }).allMatch(p -> wires.stream().anyMatch(w -> w.getSrcPort()==p || w.getDstPort()==p));
    }

    /* ----------------------- Delete --------------------------- */
    private void bindDeleteMouse() {
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!deleteMode || !SwingUtilities.isLeftMouseButton(e)) return;
                Wire w = findWireAt(e.getPoint(), 12);
                if (w == null) w = findWireAt(e.getPoint(), 20);
                if (w != null) {
                    networkController.removeWire(w);
                    wires.remove(w);
                    networkView.setSystemsAndWires(systems, wires);
                    updateHUD();
                }
            }
        });
    }

    private Wire findWireAt(Point click, double tol) {
        Wire best = null; double bestDist = tol;
        for (Wire w : wires) {
            Point src = SwingUtilities.convertPoint(w.getSrcPort(), w.getSrcPort().getWidth()/2, w.getSrcPort().getHeight()/2, this);
            Point dst = SwingUtilities.convertPoint(w.getDstPort(), w.getDstPort().getWidth()/2, w.getDstPort().getHeight()/2, this);
            double d = pointToSegmentDistance(click, src, dst);
            if (d < bestDist) { bestDist = d; best = w; }
        }
        return best;
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
}
