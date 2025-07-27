package com.blueprinthell.controller;


public interface NetworkController {

    NetworkSnapshot captureSnapshot();


    void restoreState(NetworkSnapshot snapshot);
}
