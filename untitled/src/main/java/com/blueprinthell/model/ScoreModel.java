package com.blueprinthell.model;

import java.io.Serializable;


public class ScoreModel implements Serializable {
    private static final long serialVersionUID = 6L;

    private int score;


    public void addPoints(int amount) {
        score += amount;
    }


    public int getScore() {
        return score;
    }


    public void reset() {
        score = 0;
    }
}
