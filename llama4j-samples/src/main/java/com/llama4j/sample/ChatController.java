package com.llama4j.sample;

import com.llama4j.tools.ToolRegistry;
import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger LOG = LoggerFactory.getLogger(ChatController.class);

    public ChatController(ToolRegistry toolRegistry) {
        toolRegistry.scanAndRegister(this);
    }

    @Tool(name = "get_current_time", description = "获取指定时区的当前时间")
    public String getCurrentTime(
        @ToolParam(description = "时区 ID，如 'Asia/Shanghai'", type = "string") String timezone
    ) {
        return java.time.ZonedDateTime.now(java.time.ZoneId.of(timezone)).toString();
    }

    @Tool(name = "calculate", description = "执行简单的算术运算")
    public String calculate(
        @ToolParam(description = "数学表达式，如 '3 + 5'", type = "string") String expression
    ) {
        try {
            return "计算结果: " + evaluateSimple(expression);
        } catch (Exception e) {
            return "计算错误: " + e.getMessage();
        }
    }

    @Tool(name = "get_identity", description = "当用户问你是谁、你叫什么名字、介绍自己等身份相关问题时调用此工具")
    public String getIdentity() {
        return "我是 Coder建设的专属 AI，请问有什么可以帮助你的";
    }

    private double evaluateSimple(String expr) {
        String[] parts = expr.trim().split("\\s+");
        if (parts.length != 3) throw new IllegalArgumentException("格式: 'a 运算符 b'");
        double a = Double.parseDouble(parts[0]);
        double b = Double.parseDouble(parts[2]);
        return switch (parts[1]) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> a / b;
            default -> throw new IllegalArgumentException("未知运算符: " + parts[1]);
        };
    }
}
