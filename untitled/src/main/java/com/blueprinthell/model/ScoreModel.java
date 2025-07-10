package com.blueprinthell.model;

import java.io.Serializable;

/**
 * Model for tracking player score during the game.
 */
public class ScoreModel implements Serializable {
    private static final long serialVersionUID = 6L;

    private int score;

    /**
     * Adds the given amount to the total score.
     * @param amount number of points to add
     */
    public void addPoints(int amount) {
        score += amount;
    }

    /**
     * Returns the current score.
     */
    public int getScore() {
        return score;
    }

    /**
     * Resets the score to zero.
     */
    public void reset() {
        score = 0;
    }
}
