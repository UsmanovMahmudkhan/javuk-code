package dev.javuk.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistrySubsetTest {

    @Test
    void keepsOnlyNamedTools() {
        ToolRegistry sub = Tools.defaultRegistry().subset(List.of("Read", "Grep", "Glob", "List"));
        assertEquals(4, sub.all().size());
        assertTrue(sub.has("Read"));
        assertTrue(sub.has("Grep"));
        assertFalse(sub.has("Write"));
        assertFalse(sub.has("Bash"));
    }

    @Test
    void ignoresUnknownNamesAndIsCaseInsensitive() {
        ToolRegistry sub = Tools.defaultRegistry().subset(List.of("read", "NoSuchTool", "BASH"));
        assertEquals(2, sub.all().size());
        assertTrue(sub.has("Read"));
        assertTrue(sub.has("Bash"));
    }

    @Test
    void emptyRequestYieldsEmptyRegistry() {
        assertTrue(Tools.defaultRegistry().subset(List.of()).all().isEmpty());
    }
}
