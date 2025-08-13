package com.blueprinthell.controller;

import com.blueprinthell.snapshot.NetworkSnapshot;
public interface NetworkController {
    NetworkSnapshot captureSnapshot();
    void restoreState(NetworkSnapshot snapshot);
}