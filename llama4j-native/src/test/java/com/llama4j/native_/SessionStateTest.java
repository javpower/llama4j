package com.llama4j.native_;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionStateTest {

    @Test
    void testCreation() {
        byte[] data = {1, 2, 3, 4, 5};
        SessionState state = new SessionState(data);

        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, state.data());
    }

    @Test
    void testNullData() {
        assertThrows(IllegalArgumentException.class, () -> new SessionState(null));
    }

    @Test
    void testDataDefensiveCopy() {
        byte[] original = {10, 20, 30};
        SessionState state = new SessionState(original);

        // data() must return a copy -- mutating the returned array
        // must not affect subsequent calls to data()
        byte[] first = state.data();
        first[0] = 99;
        first[1] = 99;
        first[2] = 99;

        byte[] second = state.data();
        assertArrayEquals(new byte[]{10, 20, 30}, second,
            "data() should return a defensive copy");
    }

    @Test
    void testSize() {
        assertEquals(0, new SessionState(new byte[0]).size());
        assertEquals(5, new SessionState(new byte[5]).size());
        assertEquals(1024, new SessionState(new byte[1024]).size());
    }
}
