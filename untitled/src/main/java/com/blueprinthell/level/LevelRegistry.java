package com.blueprinthell.level;

import com.blueprinthell.level.levels.*;
import java.util.*;


public final class LevelRegistry {

    private static final List<Level> LEVELS = List.of(
            new Level1(),
            new Level2(),
            new Level3(),
            new Level4(),
            new Level5()
    );

    private LevelRegistry() {}


    public static List<Level> getAllLevels() {
        return Collections.unmodifiableList(LEVELS);
    }


    public static Level getLevel(int levelNumber) {
        if (levelNumber < 1 || levelNumber > LEVELS.size()) {
            throw new IllegalArgumentException("Invalid level number: " + levelNumber);
        }
        return LEVELS.get(levelNumber - 1);
    }


    public static int getTotalLevels() {
        return LEVELS.size();
    }

    public static boolean isValidLevel(int levelNumber) {
        return levelNumber >= 1 && levelNumber <= LEVELS.size();
    }
}