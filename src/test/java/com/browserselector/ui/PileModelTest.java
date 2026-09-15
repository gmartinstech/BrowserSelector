package com.browserselector.ui;

import com.browserselector.model.Browser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Contract for the link pile: add, assign, drain — and the single-link invariant. */
class PileModelTest {

    private static final Browser FIREFOX =
        new Browser("firefox", "Mozilla Firefox", Path.of("/usr/bin/firefox"));

    @Test
    void addExtractsDomainAndStartsUnassigned() {
        var pile = new PileModel();
        pile.add("https://example.org/some/page");
        assertEquals(1, pile.size());
        var entry = pile.entries().get(0);
        assertEquals("https://example.org/some/page", entry.url());
        assertEquals("example.org", entry.domain());
        assertNull(entry.assigned());
        assertFalse(entry.privateMode());
    }

    @Test
    void singleLinkModeIsExactlyOneEntry() {
        var pile = new PileModel();
        pile.add("https://a.example/1");
        assertTrue(pile.singleLinkMode());
        pile.add("https://b.example/2");
        assertFalse(pile.singleLinkMode());
        pile.remove(List.of(0, 1));
        assertFalse(pile.singleLinkMode()); // an emptied pile is not "single-link"
        assertEquals(0, pile.size());
    }

    @Test
    void assignMarksSelectedRowsOnly() {
        var pile = new PileModel();
        pile.add("https://a.example/1");
        pile.add("https://b.example/2");
        pile.add("https://c.example/3");
        pile.assign(List.of(0, 2), FIREFOX, false);
        assertEquals(FIREFOX, pile.entries().get(0).assigned());
        assertNull(pile.entries().get(1).assigned());
        assertEquals(FIREFOX, pile.entries().get(2).assigned());
        assertEquals(2, pile.assignedCount());
    }

    @Test
    void shiftMarksPrivate_plainAssignKeepsExistingFlag() {
        var pile = new PileModel();
        pile.add("https://a.example/1");
        pile.assign(List.of(0), FIREFOX, true);
        assertTrue(pile.entries().get(0).privateMode());
        // re-assign without shift: assignment replaced, private flag stays
        pile.assign(List.of(0), FIREFOX, false);
        assertTrue(pile.entries().get(0).privateMode());
    }

    @Test
    void removeDropsOnlySelectedRows() {
        var pile = new PileModel();
        pile.add("https://a.example/1");
        pile.add("https://b.example/2");
        pile.add("https://c.example/3");
        pile.remove(List.of(1));
        assertEquals(2, pile.size());
        assertEquals("https://a.example/1", pile.entries().get(0).url());
        assertEquals("https://c.example/3", pile.entries().get(1).url());
    }

    @Test
    void drainAssignedReturnsAssignedInOrderAndKeepsUnassigned() {
        var pile = new PileModel();
        pile.add("https://a.example/1");
        pile.add("https://b.example/2");
        pile.add("https://c.example/3");
        pile.assign(List.of(0, 2), FIREFOX, false);
        var drained = pile.drainAssigned();
        assertEquals(2, drained.size());
        assertEquals("https://a.example/1", drained.get(0).url());
        assertEquals("https://c.example/3", drained.get(1).url());
        assertEquals(1, pile.size());
        assertEquals("https://b.example/2", pile.entries().get(0).url());
        assertEquals(0, pile.assignedCount());
    }
}
