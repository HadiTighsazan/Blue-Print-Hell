package com.blueprinthell.motion;

import com.blueprinthell.model.PacketModel;


public interface MotionStrategy {


    void update(PacketModel packet, double dt);
}
