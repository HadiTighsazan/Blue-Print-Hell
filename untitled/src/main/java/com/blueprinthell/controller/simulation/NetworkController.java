package com.blueprinthell.controller.simulation;

import com.blueprinthell.snapshot.NetworkSnapshot;
public interface NetworkController {
    NetworkSnapshot captureSnapshot();
    void restoreState(NetworkSnapshot snapshot);
}