package com.blueprinthell;

import com.blueprinthell.ui.MainMenuListener;
import com.blueprinthell.ui.MainMenuScreen;

import javax.swing.*;
import java.awt.*;


public class GameApp extends JFrame implements MainMenuListener {
    private enum State { MAIN_MENU, LEVEL_SELECT, PLAYING, PAUSED, SHOP, SETTINGS }

    private final JPanel cardsContainer;
    private final CardLayout cardLayout;

    public GameApp() {
        super("Blue‑Print‑Hell");
        cardLayout = new CardLayout();
        cardsContainer = new JPanel(cardLayout);
        initScreens();

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(cardsContainer, BorderLayout.CENTER);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        showState(State.MAIN_MENU);
    }

    private void initScreens() {
        MainMenuScreen mainMenu = new MainMenuScreen(this);
        cardsContainer.add(mainMenu, State.MAIN_MENU.name());

        JPanel levelSelect = new JPanel();
        levelSelect.add(new JLabel("Level Select - Coming Soon"));
        cardsContainer.add(levelSelect, State.LEVEL_SELECT.name());

        JPanel settings = new JPanel();
        settings.add(new JLabel("Settings - Coming Soon"));
        cardsContainer.add(settings, State.SETTINGS.name());
    }

    private void showState(State state) {
        cardLayout.show(cardsContainer, state.name());
    }

    @Override
    public void onAction(Action action) {
        switch (action) {
            case START:    showState(State.LEVEL_SELECT); break;
            case SETTINGS: showState(State.SETTINGS);     break;
            case EXIT:     System.exit(0);               break;
        }
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            GameApp app = new GameApp();
            app.setVisible(true);
        });
    }
}
