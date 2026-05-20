package com.llama4j.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolDefinitionTest {

    @Nested
    @DisplayName("creation")
    class CreationTests {

        @Test
        @DisplayName("should create valid definition")
        void shouldCreateValidDefinition() {
            ToolParameter p = new ToolParameter("x", "desc");
            ToolDefinition def = new ToolDefinition("my_tool", "A test tool", List.of(p));

            assertEquals("my_tool", def.name());
            assertEquals("A test tool", def.description());
            assertEquals(1, def.parameters().size());
            assertEquals("x", def.parameters().get(0).name());
        }

        @Test
        @DisplayName("should create with null parameters defaulting to empty list")
        void shouldDefaultNullParametersToEmptyList() {
            ToolDefinition def = new ToolDefinition("tool", "desc", null);
            assertTrue(def.parameters().isEmpty());
        }

        @Test
        @DisplayName("should throw NPE when name is null")
        void shouldThrowOnNullName() {
            assertThrows(NullPointerException.class,
                    () -> new ToolDefinition(null, "desc", List.of()));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when name is blank")
        void shouldThrowOnBlankName() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolDefinition("  ", "desc", List.of()));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when name is empty")
        void shouldThrowOnEmptyName() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolDefinition("", "desc", List.of()));
        }

        @Test
        @DisplayName("should throw NPE when description is null")
        void shouldThrowOnNullDescription() {
            assertThrows(NullPointerException.class,
                    () -> new ToolDefinition("tool", null, List.of()));
        }

        @Test
        @DisplayName("parameters list should be immutable")
        void parametersShouldBeImmutable() {
            ToolDefinition def = new ToolDefinition("tool", "desc",
                    List.of(new ToolParameter("a", "desc")));
            assertThrows(UnsupportedOperationException.class,
                    () -> def.parameters().add(new ToolParameter("b", "desc")));
        }
    }

    @Nested
    @DisplayName("toOpenAISchema")
    class ToOpenAISchemaTests {

        @Test
        @DisplayName("should produce valid schema with parameters")
        void shouldProduceSchemaWithParameters() {
            ToolParameter p1 = new ToolParameter("city", "City name", "string", true, List.of());
            ToolParameter p2 = new ToolParameter("unit", "Temperature unit", "string", false, List.of());
            ToolDefinition def = new ToolDefinition("get_weather", "Get weather", List.of(p1, p2));

            Map<String, Object> schema = def.toOpenAISchema();

            assertEquals("function", schema.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) schema.get("function");
            assertEquals("get_weather", function.get("name"));
            assertEquals("Get weather", function.get("description"));

            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) function.get("parameters");
            assertEquals("object", params.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) params.get("properties");
            assertEquals(2, properties.size());
            assertTrue(properties.containsKey("city"));
            assertTrue(properties.containsKey("unit"));

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) params.get("required");
            assertEquals(1, required.size());
            assertEquals("city", required.get(0));
        }

        @Test
        @DisplayName("should produce schema without parameters block when no parameters")
        void shouldOmitParametersWhenEmpty() {
            ToolDefinition def = new ToolDefinition("ping", "Ping", List.of());

            Map<String, Object> schema = def.toOpenAISchema();

            assertEquals("function", schema.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) schema.get("function");
            assertEquals("ping", function.get("name"));
            assertNull(function.get("parameters"));
        }

        @Test
        @DisplayName("should include enum values when present")
        void shouldIncludeEnumValues() {
            ToolParameter p = new ToolParameter("color", "Pick a color", "string", true,
                    List.of("red", "green", "blue"));
            ToolDefinition def = new ToolDefinition("pick_color", "Pick", List.of(p));

            Map<String, Object> schema = def.toOpenAISchema();

            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) schema.get("function");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) function.get("parameters");
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) params.get("properties");

            @SuppressWarnings("unchecked")
            Map<String, Object> colorProp = (Map<String, Object>) properties.get("color");
            @SuppressWarnings("unchecked")
            List<String> enumValues = (List<String>) colorProp.get("enum");
            assertEquals(List.of("red", "green", "blue"), enumValues);
        }
    }
}
