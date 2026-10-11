package dev.auxy.warbcraft.core.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelationBookTest {

    @Test
    void relationsDefaultToZeroAndAreClamped() {
        RelationBook book = new RelationBook();
        assertEquals(0, book.get("u1", "king"));
        assertEquals(10, book.change("u1", "king", 10));
        assertEquals(RelationBook.MAX, book.change("u1", "king", Integer.MAX_VALUE));
        assertEquals(RelationBook.MIN, book.change("u1", "king", Integer.MIN_VALUE));
        assertEquals(0, book.get("u2", "king"));
    }
}
