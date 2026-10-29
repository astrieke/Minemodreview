package dev.strata.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodexGraphTest {

    private final CodexGraph graph = new CodexGraph(List.of(
            new CodexGraph.Node("first_core", Set.of()),
            new CodexGraph.Node("crystal_age", Set.of("first_core")),
            new CodexGraph.Node("transplanter", Set.of("crystal_age", "first_core"))));

    @Test
    void unlockFollowsPrerequisites() {
        assertEquals(List.of("first_core"), graph.unlockable(Set.of()));
        assertTrue(graph.canUnlock("crystal_age", Set.of("first_core")));
        assertFalse(graph.canUnlock("transplanter", Set.of("first_core")));
        assertFalse(graph.canUnlock("first_core", Set.of("first_core")));
        assertFalse(graph.canUnlock("unknown", Set.of()));
    }

    @Test
    void validGraphHasNoErrors() {
        assertEquals(List.of(), graph.validate());
    }

    @Test
    void reportsUnknownReferenceAndCycle() {
        CodexGraph broken = new CodexGraph(List.of(
                new CodexGraph.Node("a", Set.of("b")),
                new CodexGraph.Node("b", Set.of("a")),
                new CodexGraph.Node("c", Set.of("typo"))));
        List<String> errors = broken.validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("unbekannten Knoten 'typo'")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("Zyklus")), errors.toString());
    }
}
