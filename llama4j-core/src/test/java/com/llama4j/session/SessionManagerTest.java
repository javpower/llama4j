package com.llama4j.session;

import com.llama4j.native_.LlamaContext;
import com.llama4j.native_.SessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionManagerTest {

    private SessionManager manager;

    @Mock
    private LlamaContext mockContext;

    @BeforeEach
    void setUp() {
        manager = new SessionManager(new InMemorySessionStore());
    }

    @Nested
    @DisplayName("createSession")
    class CreateSessionTests {

        @Test
        @DisplayName("should create session with unique ID and modelId")
        void shouldCreateSession() {
            Session session = manager.createSession("qwen2.5-7b");

            assertNotNull(session.id());
            assertEquals("qwen2.5-7b", session.modelId());
            assertNotNull(session.createdAt());
            assertNotNull(session.lastActiveAt());
            assertNull(session.kvCacheState());
        }

        @Test
        @DisplayName("should create sessions with different IDs")
        void shouldCreateUniqueIds() {
            Session s1 = manager.createSession("model-a");
            Session s2 = manager.createSession("model-b");

            assertNotEquals(s1.id(), s2.id());
        }
    }

    @Nested
    @DisplayName("getSession")
    class GetSessionTests {

        @Test
        @DisplayName("should retrieve existing session")
        void shouldGetExistingSession() {
            Session created = manager.createSession("qwen2.5-7b");
            Session found = manager.getSession(created.id());

            assertEquals(created.id(), found.id());
            assertEquals(created.modelId(), found.modelId());
        }

        @Test
        @DisplayName("should throw SessionNotFoundException for non-existent session")
        void shouldThrowOnMissingSession() {
            SessionNotFoundException ex = assertThrows(SessionNotFoundException.class,
                    () -> manager.getSession("no-such-id"));
            assertEquals("no-such-id", ex.getSessionId());
        }
    }

    @Nested
    @DisplayName("deleteSession")
    class DeleteSessionTests {

        @Test
        @DisplayName("should delete existing session")
        void shouldDeleteSession() {
            Session session = manager.createSession("qwen2.5-7b");
            manager.deleteSession(session.id());

            assertThrows(SessionNotFoundException.class,
                    () -> manager.getSession(session.id()));
        }

        @Test
        @DisplayName("delete non-existent session should not throw")
        void shouldNotThrowOnDeleteMissing() {
            assertDoesNotThrow(() -> manager.deleteSession("ghost-id"));
        }
    }

    @Nested
    @DisplayName("resumeSession")
    class ResumeSessionTests {

        @Test
        @DisplayName("should resume session and load KV cache")
        void shouldResumeAndLoadKvCache() {
            Session created = manager.createSession("qwen2.5-7b");
            SessionState state = new SessionState(new byte[]{1, 2, 3});

            // Simulate a checkpoint so the session has KV cache
            InMemorySessionStore store = new InMemorySessionStore();
            store.save(created);
            store.updateKvCache(created.id(), state);
            SessionManager mgr = new SessionManager(store);

            Session resumed = mgr.resumeSession(created.id(), mockContext);

            verify(mockContext).loadSession(state);
            assertEquals(created.id(), resumed.id());
        }

        @Test
        @DisplayName("should throw SessionNotFoundException when resuming non-existent session")
        void shouldThrowOnResumeMissing() {
            assertThrows(SessionNotFoundException.class,
                    () -> manager.resumeSession("ghost-id", mockContext));
        }
    }

    @Nested
    @DisplayName("checkpoint")
    class CheckpointTests {

        @Test
        @DisplayName("should save KV cache from context")
        void shouldSaveCheckpoint() {
            Session session = manager.createSession("qwen2.5-7b");
            SessionState state = new SessionState(new byte[]{10, 20, 30});
            when(mockContext.saveSession()).thenReturn(state);

            manager.checkpoint(session.id(), mockContext);

            verify(mockContext).saveSession();
        }

        @Test
        @DisplayName("should throw SessionNotFoundException for non-existent session")
        void shouldThrowOnCheckpointMissing() {
            assertThrows(SessionNotFoundException.class,
                    () -> manager.checkpoint("ghost-id", mockContext));
        }
    }

    @Test
    @DisplayName("constructor should throw NPE when store is null")
    void shouldThrowOnNullStore() {
        assertThrows(NullPointerException.class, () -> new SessionManager(null));
    }
}
