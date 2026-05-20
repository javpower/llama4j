package com.llama4j.session;

import com.llama4j.native_.SessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemorySessionStoreTest {

    private InMemorySessionStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySessionStore();
    }

    @Nested
    @DisplayName("save and find")
    class SaveAndFindTests {

        @Test
        @DisplayName("should save and retrieve session by ID")
        void shouldSaveAndFind() {
            Session session = Session.create("model-7b");
            store.save(session);

            Optional<Session> found = store.findById(session.id());
            assertTrue(found.isPresent());
            assertEquals(session.id(), found.get().id());
            assertEquals("model-7b", found.get().modelId());
        }

        @Test
        @DisplayName("should return empty optional for non-existent ID")
        void shouldReturnEmptyForMissing() {
            Optional<Session> found = store.findById("does-not-exist");
            assertTrue(found.isEmpty());
        }

        @Test
        @DisplayName("should throw NPE when saving null session")
        void shouldThrowOnNullSave() {
            assertThrows(NullPointerException.class, () -> store.save(null));
        }

        @Test
        @DisplayName("should track size correctly")
        void shouldTrackSize() {
            assertEquals(0, store.size());

            store.save(Session.create("m1"));
            store.save(Session.create("m2"));
            assertEquals(2, store.size());
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("should delete session by ID")
        void shouldDeleteSession() {
            Session session = Session.create("model-7b");
            store.save(session);
            assertTrue(store.exists(session.id()));

            store.delete(session.id());
            assertFalse(store.exists(session.id()));
            assertTrue(store.findById(session.id()).isEmpty());
        }

        @Test
        @DisplayName("delete non-existent session should not throw")
        void deleteNonExistentShouldNotThrow() {
            assertDoesNotThrow(() -> store.delete("ghost-id"));
        }
    }

    @Nested
    @DisplayName("exists")
    class ExistsTests {

        @Test
        @DisplayName("should return true for existing session")
        void shouldReturnTrueForExisting() {
            Session session = Session.create("model-7b");
            store.save(session);
            assertTrue(store.exists(session.id()));
        }

        @Test
        @DisplayName("should return false for non-existent session")
        void shouldReturnFalseForMissing() {
            assertFalse(store.exists("no-such-id"));
        }
    }

    @Nested
    @DisplayName("updateKvCache")
    class UpdateKvCacheTests {

        @Test
        @DisplayName("should update KV cache state on existing session")
        void shouldUpdateKvCache() {
            Session session = Session.create("model-7b");
            store.save(session);

            assertNull(store.findById(session.id()).get().kvCacheState());

            SessionState state = new SessionState(new byte[]{1, 2, 3, 4});
            store.updateKvCache(session.id(), state);

            Session updated = store.findById(session.id()).orElseThrow();
            assertNotNull(updated.kvCacheState());
            assertEquals(4, updated.kvCacheState().size());
        }

        @Test
        @DisplayName("should not create session when updating KV cache on non-existent session")
        void shouldNotCreateOnUpdateKvCacheForMissing() {
            SessionState state = new SessionState(new byte[]{1, 2, 3});
            store.updateKvCache("ghost-id", state);

            assertTrue(store.findById("ghost-id").isEmpty());
            assertEquals(0, store.size());
        }
    }
}
