package com.blueprinthell.model;

/**
 * Marker interface for models that can be updated each simulation tick.
 */
public interface Updatable {
    /**
     * Update the state of this model by dt seconds.
     */
    void update(double dt);
}
