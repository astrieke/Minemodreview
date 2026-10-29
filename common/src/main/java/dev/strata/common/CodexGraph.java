package dev.strata.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Die Knoten des Erdkodex und ihre Voraussetzungen.
 *
 * <p>Pack-Autoren definieren die Knoten per Datapack. {@link #validate()} meldet
 * Tippfehler und Zyklen mit verständlichen Texten, statt das Spiel abstürzen zu lassen.
 */
public final class CodexGraph {

    public record Node(String id, Set<String> requires) {
        public Node {
            requires = Set.copyOf(requires);
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();

    public CodexGraph(Iterable<Node> nodes) {
        for (Node node : nodes) this.nodes.put(node.id(), node);
    }

    public boolean contains(String id) {
        return nodes.containsKey(id);
    }

    public Set<String> ids() {
        return nodes.keySet();
    }

    /** Ein Knoten ist freischaltbar, wenn er existiert, noch fehlt und alle Voraussetzungen erfüllt sind. */
    public boolean canUnlock(String id, Set<String> unlocked) {
        Node node = nodes.get(id);
        return node != null && !unlocked.contains(id) && unlocked.containsAll(node.requires());
    }

    public List<String> unlockable(Set<String> unlocked) {
        List<String> result = new ArrayList<>();
        for (String id : nodes.keySet()) {
            if (canUnlock(id, unlocked)) result.add(id);
        }
        return result;
    }

    /** Liefert alle Fehler im Graphen. Eine leere Liste heißt: alles in Ordnung. */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        for (Node node : nodes.values()) {
            for (String req : node.requires()) {
                if (!nodes.containsKey(req)) {
                    errors.add("Knoten '" + node.id() + "' verlangt unbekannten Knoten '" + req + "'");
                }
            }
        }
        Map<String, Integer> state = new HashMap<>(); // 1 = in Arbeit, 2 = fertig
        for (String id : nodes.keySet()) {
            findCycle(id, state, new ArrayList<>(), errors, new HashSet<>());
        }
        return errors;
    }

    private void findCycle(String id, Map<String, Integer> state, List<String> path,
                           List<String> errors, Set<String> reported) {
        Integer s = state.get(id);
        if (s != null && s == 2) return;
        if (s != null && s == 1) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(id), path.size()));
            cycle.add(id);
            String text = String.join(" -> ", cycle);
            if (reported.add(text)) errors.add("Zyklus im Kodex: " + text);
            return;
        }
        Node node = nodes.get(id);
        if (node == null) return;
        state.put(id, 1);
        path.add(id);
        for (String req : node.requires()) findCycle(req, state, path, errors, reported);
        path.remove(path.size() - 1);
        state.put(id, 2);
    }
}
