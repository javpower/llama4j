package com.llama4j.tools;

import com.llama4j.exception.ToolNotFoundException;
import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ── Helper class with @Tool annotated methods for scan tests ──

    static class WeatherTools {
        @Tool(name = "get_weather", description = "Get current weather for a city")
        public String getWeather(
                @ToolParam(description = "City name", type = "string") String city,
                @ToolParam(description = "Temperature unit", type = "string", required = false) String unit) {
            return "Weather in " + city + ": 22C";
        }

        @Tool(description = "Get current time")  // name defaults to method name
        public String getCurrentTime() {
            return "12:00";
        }
    }

    // ── Scan and Register Tests ──

    @Nested
    @DisplayName("scanAndRegister")
    class ScanAndRegisterTests {

        @Test
        @DisplayName("should discover and register all @Tool annotated methods")
        void shouldDiscoverAnnotatedMethods() {
            registry.scanAndRegister(new WeatherTools());

            assertEquals(2, registry.size());
            assertTrue(registry.getDefinition("get_weather").isPresent());
            assertTrue(registry.getDefinition("getCurrentTime").isPresent());
        }

        @Test
        @DisplayName("should use annotation name when specified")
        void shouldUseAnnotationName() {
            registry.scanAndRegister(new WeatherTools());

            Optional<ToolDefinition> def = registry.getDefinition("get_weather");
            assertTrue(def.isPresent());
            assertEquals("get_weather", def.get().name());
            assertEquals("Get current weather for a city", def.get().description());
        }

        @Test
        @DisplayName("should fall back to method name when annotation name is blank")
        void shouldFallBackToMethodName() {
            registry.scanAndRegister(new WeatherTools());

            Optional<ToolDefinition> def = registry.getDefinition("getCurrentTime");
            assertTrue(def.isPresent());
            assertEquals("getCurrentTime", def.get().name());
        }

        @Test
        @DisplayName("should extract parameter definitions from @ToolParam annotations")
        void shouldExtractParameterDefinitions() {
            registry.scanAndRegister(new WeatherTools());

            ToolDefinition def = registry.getDefinition("get_weather").orElseThrow();
            assertEquals(2, def.parameters().size());

            ToolParameter cityParam = def.parameters().get(0);
            assertEquals("city", cityParam.name());
            assertEquals("City name", cityParam.description());
            assertEquals("string", cityParam.type());
            assertTrue(cityParam.required());

            ToolParameter unitParam = def.parameters().get(1);
            assertEquals("unit", unitParam.name());
            assertEquals("Temperature unit", unitParam.description());
            assertFalse(unitParam.required());
        }

        @Test
        @DisplayName("should throw NPE when target is null")
        void shouldThrowOnNullTarget() {
            assertThrows(NullPointerException.class, () -> registry.scanAndRegister(null));
        }
    }

    // ── Programmatic Register Tests ──

    @Nested
    @DisplayName("programmatic register")
    class ProgrammaticRegisterTests {

        @Test
        @DisplayName("should register definition with handler")
        void shouldRegisterWithHandler() {
            ToolDefinition def = new ToolDefinition("calculator", "Do math",
                    List.of(new ToolParameter("expression", "Math expression")));
            registry.register(def, args -> "42");

            assertEquals(1, registry.size());
            assertTrue(registry.getDefinition("calculator").isPresent());
        }

        @Test
        @DisplayName("should throw NPE when definition is null")
        void shouldThrowOnNullDefinition() {
            assertThrows(NullPointerException.class,
                    () -> registry.register(null, args -> "result"));
        }

        @Test
        @DisplayName("should throw NPE when handler is null")
        void shouldThrowOnNullHandler() {
            ToolDefinition def = new ToolDefinition("x", "desc", List.of());
            assertThrows(NullPointerException.class,
                    () -> registry.register(def, null));
        }
    }

    // ── Execute Tests ──

    @Nested
    @DisplayName("execute")
    class ExecuteTests {

        @Test
        @DisplayName("should execute programmatic tool via ToolHandler")
        void shouldExecuteProgrammaticTool() {
            ToolDefinition def = new ToolDefinition("echo", "Echo back",
                    List.of(new ToolParameter("text", "Text to echo")));
            registry.register(def, args -> args.get("text").asText());

            ToolCall call = new ToolCall("call-1", "echo", "{\"text\":\"hello\"}");
            ToolResult result = registry.execute(call);

            assertTrue(result.success());
            assertEquals("call-1", result.toolCallId());
            assertEquals("hello", result.content());
        }

        @Test
        @DisplayName("should execute annotated tool via reflection")
        void shouldExecuteAnnotatedTool() {
            registry.scanAndRegister(new WeatherTools());

            ToolCall call = ToolCall.of("get_weather", "{\"city\":\"Tokyo\"}");
            ToolResult result = registry.execute(call);

            assertTrue(result.success());
            assertTrue(result.content().contains("Tokyo"));
        }

        @Test
        @DisplayName("should throw ToolNotFoundException for unknown tool")
        void shouldThrowOnNonExistentTool() {
            ToolCall call = ToolCall.of("nonexistent", "{}");

            ToolNotFoundException ex = assertThrows(ToolNotFoundException.class,
                    () -> registry.execute(call));
            assertEquals("nonexistent", ex.getToolName());
        }

        @Test
        @DisplayName("should throw NPE when call is null")
        void shouldThrowOnNullCall() {
            assertThrows(NullPointerException.class, () -> registry.execute(null));
        }
    }

    // ── Unregister Tests ──

    @Nested
    @DisplayName("unregister")
    class UnregisterTests {

        @Test
        @DisplayName("should remove tool after unregister")
        void shouldRemoveTool() {
            ToolDefinition def = new ToolDefinition("temp", "Temporary", List.of());
            registry.register(def, args -> "ok");

            assertEquals(1, registry.size());
            registry.unregister("temp");
            assertEquals(0, registry.size());
            assertTrue(registry.getDefinition("temp").isEmpty());
        }

        @Test
        @DisplayName("should make unregistered tool un-executable")
        void shouldMakeUnregisteredToolUnExecutable() {
            ToolDefinition def = new ToolDefinition("temp", "Temporary", List.of());
            registry.register(def, args -> "ok");
            registry.unregister("temp");

            ToolCall call = ToolCall.of("temp", "{}");
            assertThrows(ToolNotFoundException.class, () -> registry.execute(call));
        }
    }

    // ── Get Definitions Tests ──

    @Nested
    @DisplayName("getDefinitions")
    class GetDefinitionsTests {

        @Test
        @DisplayName("should return empty collection for empty registry")
        void shouldReturnEmptyWhenNoToolsRegistered() {
            Collection<ToolDefinition> defs = registry.getDefinitions();
            assertTrue(defs.isEmpty());
        }

        @Test
        @DisplayName("should return all registered definitions")
        void shouldReturnAllDefinitions() {
            registry.register(new ToolDefinition("a", "Tool A", List.of()), args -> "a");
            registry.register(new ToolDefinition("b", "Tool B", List.of()), args -> "b");

            Collection<ToolDefinition> defs = registry.getDefinitions();
            assertEquals(2, defs.size());
        }
    }

    // ── toOpenAISchema integration test ──

    @Test
    @DisplayName("toOpenAISchema should produce valid schema from registry definition")
    void toOpenAISchemaShouldProduceValidMap() {
        ToolParameter param = new ToolParameter("city", "City name", "string", true, List.of());
        ToolDefinition def = new ToolDefinition("get_weather", "Get weather", List.of(param));
        registry.register(def, args -> "sunny");

        ToolDefinition registered = registry.getDefinition("get_weather").orElseThrow();
        Map<String, Object> schema = registered.toOpenAISchema();

        assertEquals("function", schema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) schema.get("function");
        assertEquals("get_weather", function.get("name"));
        assertEquals("Get weather", function.get("description"));
        assertNotNull(function.get("parameters"));
    }
}
