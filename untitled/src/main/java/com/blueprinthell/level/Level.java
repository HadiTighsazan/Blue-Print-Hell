package com.blueprinthell.level;


public interface Level {

    int getLevelNumber();


    String getName();


    LevelDefinition getDefinition();


    double getWireBudget();


    int getPacketsPerPort();


    double getMaxLossRatio();


    default String getDescription() {
        return "";
    }
}