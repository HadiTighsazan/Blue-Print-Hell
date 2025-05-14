package com.blueprinthell.ui;

public interface MainMenuListener {
    enum Action { START, SETTINGS, EXIT }
    void onAction(Action action);
}