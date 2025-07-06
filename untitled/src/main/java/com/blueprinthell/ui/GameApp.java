package com.blueprinthell.ui;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class GameApp extends JFrame implements MainMenuListener, LevelSelectListener, SettingsListener {
    public enum State { MAIN_MENU, LEVEL_SELECT, PLAYING, PAUSED, SHOP, SETTINGS }

    private final CardLayout cardsLayout = new CardLayout();
    private final JPanel cardsContainer = new JPanel(cardsLayout);
    private final Map<State, JComponent> screens = new HashMap<>();

    private Clip bgClip;

    private final SoundManager sounds = SoundManager.get();


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameApp app = new GameApp();
            app.setVisible(true);
        });
    }

    public GameApp() {
        super("Blue‑Print‑Hell");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1024, 768);
        setLocationRelativeTo(null);

        initScreens();
        sounds.loopBg();

        add(cardsContainer);
        showState(State.MAIN_MENU);
    }



    private Clip loadClip(String fileName) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        URL url = getClass().getClassLoader().getResource(fileName);
        if (url == null) throw new IOException("Audio resource not found: " + fileName);
        AudioInputStream ais = AudioSystem.getAudioInputStream(url);
        Clip clip = AudioSystem.getClip();
        clip.open(ais);
        return clip;
    }

    private void initScreens() {
        MainMenuScreen mainMenu = new MainMenuScreen(this);
        screens.put(State.MAIN_MENU, mainMenu);
        cardsContainer.add(mainMenu, State.MAIN_MENU.name());

        LevelSelectScreen levelSelect = new LevelSelectScreen(this);
        screens.put(State.LEVEL_SELECT, levelSelect);
        cardsContainer.add(levelSelect, State.LEVEL_SELECT.name());

        SettingsScreen settings = new SettingsScreen(this, 50);
        screens.put(State.SETTINGS, settings);
        cardsContainer.add(settings, State.SETTINGS.name());

        GameScreen gameScreen = new GameScreen();
        screens.put(State.PLAYING, gameScreen);
        cardsContainer.add(gameScreen, State.PLAYING.name());
    }

    public void showState(State state) {
        cardsLayout.show(cardsContainer, state.name());
    }

    @Override
    public void onAction(MainMenuListener.Action action) {
        switch (action) {
            case START:    showState(State.LEVEL_SELECT); break;
            case SETTINGS: showState(State.SETTINGS);     break;
            case EXIT:     System.exit(0);               break;
        }
    }

    @Override
    public void onLevelSelected(int levelIndex) {
        GameScreen gs = (GameScreen) screens.get(State.PLAYING);
        gs.loadLevel(levelIndex);
        showState(State.PLAYING);
    }

    @Override
    public void onBack() {
        showState(State.MAIN_MENU);
    }

    @Override
    public void onSoundVolumeChanged(int newVolume) {
        SoundManager.get().setMasterVolume(newVolume);   // ✨ حالا همه‌چیز را تنظیم می‌کند
    }


    @Override
    public void onKeyBindingsRequested() {
        int newRewind  = promptForKey("Press new key for REWIND");
        int newForward = promptForKey("Press new key for FORWARD");
        GameScreen gs = (GameScreen) screens.get(State.PLAYING);
        gs.updateKeyBindings(newRewind, newForward);
    }

    private int promptForKey(String message) {
        JDialog dlg = new JDialog(this, "Key Binding", true);
        JLabel lbl = new JLabel(message, SwingConstants.CENTER);
        dlg.add(lbl);
        dlg.setSize(300, 100);
        dlg.setLocationRelativeTo(this);
        final int[] keyCode = new int[1];
        dlg.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                keyCode[0] = e.getKeyCode();
                dlg.dispose();
            }
        });
        dlg.setVisible(true);
        return keyCode[0];
    }
}
