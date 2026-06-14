package dev.javuk.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundTest {

    @AfterEach
    void restore() {
        Sound.setEnabled(true);
    }

    @Test
    void toggleFlipsState() {
        Sound.setEnabled(false);
        assertFalse(Sound.enabled());
        Sound.setEnabled(true);
        assertTrue(Sound.enabled());
    }

    @Test
    void configureSetsState() {
        Sound.configure(false);
        assertFalse(Sound.enabled());
    }

    @Test
    void playNeverThrowsRegardlessOfState() {
        // In the test JVM there is no TTY, so play() is a silent no-op either way.
        Sound.setEnabled(false);
        assertDoesNotThrow(() -> Sound.play(Sound.Event.TURN_COMPLETE));
        Sound.setEnabled(true);
        assertDoesNotThrow(() -> Sound.play(Sound.Event.PERMISSION));
        assertDoesNotThrow(() -> Sound.play(Sound.Event.ERROR));
        assertDoesNotThrow(() -> Sound.play(null));
    }
}
