package com.llama4j.web.controller;

import com.llama4j.agent.CliAgent;
import com.llama4j.core.Model;
import com.llama4j.core.ModelRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ModelRegistry modelRegistry;
    private final CliAgent cliAgent;

    public ModelController(ModelRegistry modelRegistry, CliAgent cliAgent) {
        this.modelRegistry = modelRegistry;
        this.cliAgent = cliAgent;
    }

    @GetMapping
    public ResponseEntity<?> listModels() {
        List<Map<String, Object>> models = new ArrayList<>();
        for (String name : modelRegistry.modelNames()) {
            Model m = modelRegistry.get(name);
            if (m != null) {
                models.add(Map.of(
                    "name", name,
                    "modelName", m.getModelName(),
                    "isDefault", name.equals(modelRegistry.defaultModelName())
                ));
            }
        }
        return ResponseEntity.ok(Map.of(
            "models", models,
            "current", cliAgent.getModelName()
        ));
    }

    @PostMapping("/switch")
    public ResponseEntity<?> switchModel(@RequestBody Map<String, String> body) {
        String modelName = body.get("modelName");
        Model model = modelRegistry.get(modelName);
        if (model == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Model not found: " + modelName));
        }
        cliAgent.switchModel(model);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "current", cliAgent.getModelName()
        ));
    }
}
