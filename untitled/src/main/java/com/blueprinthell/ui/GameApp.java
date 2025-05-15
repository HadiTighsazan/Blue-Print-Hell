package com.blueprinthell.ui;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class GameApp extends JFrame implements MainMenuListener, LevelSelectListener, SettingsListener {
    public enum State { MAIN_MENU, LEVEL_SELECT, PLAYING, PAUSED, SHOP, SETTINGS }

    private final CardLayout cardsLayout = new CardLayout();
    private final JPanel cardsContainer = new JPanel(cardsLayout);
    private final Map<State, JComponent> screens = new HashMap<>();

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
        add(cardsContainer);
        showState(State.MAIN_MENU);
    }

    private void initScreens() {
        MainMenuScreen mainMenu = new MainMenuScreen(this);
        screens.put(State.MAIN_MENU, mainMenu);
        cardsContainer.add(mainMenu, State.MAIN_MENU.name());

        LevelSelectScreen levelSelect = new LevelSelectScreen(this);
        screens.put(State.LEVEL_SELECT, levelSelect);
        cardsContainer.add(levelSelect, State.LEVEL_SELECT.name());

        SettingsScreen settings = new SettingsScreen(this);
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
            case START:       showState(State.LEVEL_SELECT); break;
            case SETTINGS:    showState(State.SETTINGS);     break;
            case EXIT:        System.exit(0);               break;
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
        // TODO: اعمال تنظیم حجم صدا
    }
    @Override
    public void onKeyBindingsRequested() {
        // TODO: نمایش دیالوگ تغییر کلیدها
    }

}