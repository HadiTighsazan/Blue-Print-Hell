package com.blueprinthell.ui;


public interface SettingsListener {
    void onSoundVolumeChanged(int newVolume);
    void onKeyBindingsRequested();
    void onBack();
}