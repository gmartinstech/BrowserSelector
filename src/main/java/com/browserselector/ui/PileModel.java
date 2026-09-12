package com.browserselector.ui;

import com.browserselector.model.Browser;
import com.browserselector.util.UrlUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Pure state for a pile of links awaiting dispatch. No Swing.
 * Arrival order is sacred — rows never re-sort (spec: "Data flow").
 */
public final class PileModel {

    /** One piled link. assigned == null means "not yet dispatched". */
    public record Entry(String url, String domain, Browser assigned, boolean privateMode) {}

    private final List<Entry> entries = new ArrayList<>();

    public void add(String url) {
        entries.add(new Entry(url, UrlUtils.extractDomain(url), null, false));
    }

    public int size() {
        return entries.size();
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public boolean singleLinkMode() {
        return entries.size() == 1;
    }

    /**
     * Assigns the browser to the given row indices. markPrivate == true marks
     * the rows private (Shift held); false keeps each row's existing flag.
     */
    public void assign(List<Integer> indices, Browser browser, boolean markPrivate) {
        for (int index : new LinkedHashSet<>(indices)) {
            var old = entries.get(index);
            entries.set(index, new Entry(old.url(), old.domain(), browser, markPrivate || old.privateMode()));
        }
    }

    public void remove(List<Integer> indices) {
        new LinkedHashSet<>(indices).stream()
            .sorted((a, b) -> b - a)
            .mapToInt(Integer::intValue)
            .forEach(entries::remove);
    }

    /** Removes and returns assigned entries in order; unassigned entries stay (drain model). */
    public List<Entry> drainAssigned() {
        var drained = entries.stream().filter(e -> e.assigned() != null).toList();
        entries.removeIf(e -> e.assigned() != null);
        return drained;
    }

    public int assignedCount() {
        return (int) entries.stream().filter(e -> e.assigned() != null).count();
    }
}
