package md.utm.faf.pad.player.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Plain unit test - no Spring needed. Run with: mvn test */
class ProgressionTest {

    @Test
    void newPlayerIsLevelOne() {
        assertEquals(1, Progression.levelFor(0));
        assertEquals(500, Progression.xpToNextLevel(0));
    }

    @Test
    void matchesTheContractExample() {
        // contract PATCH /xp: 3420 XP (level 7) + 250 XP = 3670 XP -> level 8, leveled up
        assertEquals(7, Progression.levelFor(3420));
        assertEquals(8, Progression.levelFor(3420 + 250));
        assertEquals(80, Progression.xpToNextLevel(3420));
    }

    @Test
    void reachingTheThresholdLevelsUp() {
        assertEquals(1, Progression.levelFor(499));
        assertEquals(2, Progression.levelFor(500));
    }
}
