package viper.com.claudecode.model;

import java.util.Map;

/**
 * 工具定义。对应 Python 中 tools 列表里的字典。
 */
public class ToolDefinition {
    public String name;
    public String description;
    public Map<String, Object> inputSchema;

    public ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public Map<String, Object> toApiMap() {
        return Map.of(
                "name", name,
                "description", description,
                "input_schema", inputSchema
        );
    }
}
