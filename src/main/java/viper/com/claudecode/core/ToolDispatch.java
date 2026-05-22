package viper.com.claudecode.core;

import viper.com.claudecode.Config;
import viper.com.claudecode.model.ToolDefinition;
import viper.com.claudecode.tools.BaseTools;
import viper.com.claudecode.tools.Subagent;

import java.util.*;
import java.util.function.Function;

/**
 * s02: 工具分发表 + s10 shutdown/plan_approval 协议处理。
 * 对应 Python 的 TOOL_HANDLERS 字典与 TOOLS 列表。
 */
public final class ToolDispatch {

    private ToolDispatch() {}

    /** 工具处理函数：输入 = (input map + tool_use_id 注入), 输出 = 字符串。 */
    public static final Map<String, Function<Map<String, Object>, String>> HANDLERS = buildHandlers();

    /** 工具定义列表，发给模型。 */
    public static final List<ToolDefinition> TOOLS = buildTools();

    private static Map<String, Function<Map<String, Object>, String>> buildHandlers() {
        Map<String, Function<Map<String, Object>, String>> m = new HashMap<>();

        m.put("bash", kw -> BaseTools.runBash(str(kw.get("command")), str(kw.get("tool_use_id"))));
        m.put("read_file", kw -> BaseTools.runRead(str(kw.get("path")), str(kw.get("tool_use_id")),
                kw.get("limit") instanceof Number n ? n.intValue() : null));
        m.put("write_file", kw -> BaseTools.runWrite(str(kw.get("path")), str(kw.get("content"))));
        m.put("edit_file", kw -> BaseTools.runEdit(str(kw.get("path")),
                str(kw.get("old_text")), str(kw.get("new_text"))));

        m.put("TodoWrite", kw -> {
            //noinspection unchecked
            List<Map<String, Object>> items = (List<Map<String, Object>>) kw.get("items");
            return Context.TODO.update(items);
        });

        m.put("task", kw -> Subagent.run(str(kw.get("prompt")),
                kw.getOrDefault("agent_type", "Explore").toString(), Context.CLIENT));

        m.put("load_skill", kw -> Context.SKILLS.load(str(kw.get("name"))));

        m.put("compress", kw -> "Compressing...");

        m.put("background_run", kw -> Context.BG.run(str(kw.get("command")),
                kw.get("timeout") instanceof Number n ? n.intValue() : 120));
        m.put("check_background", kw -> Context.BG.check(kw.get("task_id") == null ? null : str(kw.get("task_id"))));

        m.put("task_create", kw -> Context.TASK_MGR.create(str(kw.get("subject")),
                kw.get("description") == null ? "" : str(kw.get("description"))));
        m.put("task_get", kw -> Context.TASK_MGR.get(((Number) kw.get("task_id")).intValue()));
        m.put("task_update", kw -> {
            int tid = ((Number) kw.get("task_id")).intValue();
            String status = kw.get("status") == null ? null : str(kw.get("status"));
            //noinspection unchecked
            List<Integer> addBy = (List<Integer>) kw.get("add_blocked_by");
            //noinspection unchecked
            List<Integer> addBlocks = (List<Integer>) kw.get("add_blocks");
            return Context.TASK_MGR.update(tid, status, addBy, addBlocks);
        });
        m.put("task_list", kw -> Context.TASK_MGR.listAll());

        m.put("spawn_teammate", kw -> Context.TEAM.spawn(str(kw.get("name")),
                str(kw.get("role")), str(kw.get("prompt"))));
        m.put("list_teammates", kw -> Context.TEAM.listAll());

        m.put("send_message", kw -> Context.BUS.send("lead", str(kw.get("to")), str(kw.get("content")),
                kw.getOrDefault("msg_type", "message").toString(), null));
        m.put("read_inbox", kw -> {
            try {
                return viper.com.claudecode.AnthropicClient.MAPPER.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(Context.BUS.readInbox("lead"));
            } catch (Exception e) { return "[]"; }
        });
        m.put("broadcast", kw -> Context.BUS.broadcast("lead", str(kw.get("content")),
                Context.TEAM.memberNames()));

        m.put("shutdown_request", kw -> handleShutdownRequest(str(kw.get("teammate"))));
        m.put("plan_approval", kw -> handlePlanReview(str(kw.get("request_id")),
                Boolean.TRUE.equals(kw.get("approve")),
                kw.get("feedback") == null ? "" : str(kw.get("feedback"))));

        m.put("idle", kw -> "Lead does not idle.");
        m.put("claim_task", kw -> Context.TASK_MGR.claim(((Number) kw.get("task_id")).intValue(), "lead"));

        return m;
    }

    /** s10: 给 teammate 发 shutdown_request 消息并记录请求。 */
    public static String handleShutdownRequest(String teammate) {
        String reqId = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("target", teammate);
        req.put("status", "pending");
        Context.SHUTDOWN_REQUESTS.put(reqId, req);
        Context.BUS.send("lead", teammate, "Please shut down.", "shutdown_request",
                Map.of("request_id", reqId));
        return "Shutdown request " + reqId + " sent to '" + teammate + "'";
    }

    /** s10: 处理计划审批回复。 */
    public static String handlePlanReview(String requestId, boolean approve, String feedback) {
        Map<String, Object> req = Context.PLAN_REQUESTS.get(requestId);
        if (req == null) return "Error: Unknown plan request_id '" + requestId + "'";
        req.put("status", approve ? "approved" : "rejected");
        Context.BUS.send("lead", str(req.get("from")), feedback, "plan_approval_response",
                Map.of("request_id", requestId, "approve", approve, "feedback", feedback));
        return "Plan " + req.get("status") + " for '" + req.get("from") + "'";
    }

    private static List<ToolDefinition> buildTools() {
        List<ToolDefinition> t = new ArrayList<>();
        t.add(td("bash", "Run a shell command.", obj("properties",
                Map.of("command", Map.of("type", "string")),
                "required", List.of("command"))));
        t.add(td("read_file", "Read file contents.", obj("properties",
                Map.of("path", Map.of("type", "string"),
                       "limit", Map.of("type", "integer")),
                "required", List.of("path"))));
        t.add(td("write_file", "Write content to file.", obj("properties",
                Map.of("path", Map.of("type", "string"),
                       "content", Map.of("type", "string")),
                "required", List.of("path", "content"))));
        t.add(td("edit_file", "Replace exact text in file.", obj("properties",
                Map.of("path", Map.of("type", "string"),
                       "old_text", Map.of("type", "string"),
                       "new_text", Map.of("type", "string")),
                "required", List.of("path", "old_text", "new_text"))));
        t.add(td("TodoWrite", "Update task tracking list.", obj("properties",
                Map.of("items", Map.of(
                        "type", "array",
                        "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "content", Map.of("type", "string"),
                                        "status", Map.of("type", "string", "enum",
                                                List.of("pending", "in_progress", "completed")),
                                        "activeForm", Map.of("type", "string")),
                                "required", List.of("content", "status", "activeForm")))),
                "required", List.of("items"))));
        t.add(td("task", "Spawn a subagent for isolated exploration or work.", obj("properties",
                Map.of("prompt", Map.of("type", "string"),
                       "agent_type", Map.of("type", "string", "enum", List.of("Explore", "general-purpose"))),
                "required", List.of("prompt"))));
        t.add(td("load_skill", "Load specialized knowledge by name.", obj("properties",
                Map.of("name", Map.of("type", "string")),
                "required", List.of("name"))));
        t.add(td("compress", "Manually compress conversation context.", obj("properties", Map.of())));
        t.add(td("background_run", "Run command in background thread.", obj("properties",
                Map.of("command", Map.of("type", "string"),
                       "timeout", Map.of("type", "integer")),
                "required", List.of("command"))));
        t.add(td("check_background", "Check background task status.", obj("properties",
                Map.of("task_id", Map.of("type", "string")))));
        t.add(td("task_create", "Create a persistent file task.", obj("properties",
                Map.of("subject", Map.of("type", "string"),
                       "description", Map.of("type", "string")),
                "required", List.of("subject"))));
        t.add(td("task_get", "Get task details by ID.", obj("properties",
                Map.of("task_id", Map.of("type", "integer")),
                "required", List.of("task_id"))));
        t.add(td("task_update", "Update task status or dependencies.", obj("properties",
                Map.of("task_id", Map.of("type", "integer"),
                       "status", Map.of("type", "string", "enum",
                               List.of("pending", "in_progress", "completed", "deleted")),
                       "add_blocked_by", Map.of("type", "array", "items", Map.of("type", "integer")),
                       "add_blocks", Map.of("type", "array", "items", Map.of("type", "integer"))),
                "required", List.of("task_id"))));
        t.add(td("task_list", "List all tasks.", obj("properties", Map.of())));
        t.add(td("spawn_teammate", "Spawn a persistent autonomous teammate.", obj("properties",
                Map.of("name", Map.of("type", "string"),
                       "role", Map.of("type", "string"),
                       "prompt", Map.of("type", "string")),
                "required", List.of("name", "role", "prompt"))));
        t.add(td("list_teammates", "List all teammates.", obj("properties", Map.of())));
        t.add(td("send_message", "Send a message to a teammate.", obj("properties",
                Map.of("to", Map.of("type", "string"),
                       "content", Map.of("type", "string"),
                       "msg_type", Map.of("type", "string", "enum", new ArrayList<>(Config.VALID_MSG_TYPES))),
                "required", List.of("to", "content"))));
        t.add(td("read_inbox", "Read and drain the lead's inbox.", obj("properties", Map.of())));
        t.add(td("broadcast", "Send message to all teammates.", obj("properties",
                Map.of("content", Map.of("type", "string")),
                "required", List.of("content"))));
        t.add(td("shutdown_request", "Request a teammate to shut down.", obj("properties",
                Map.of("teammate", Map.of("type", "string")),
                "required", List.of("teammate"))));
        t.add(td("plan_approval", "Approve or reject a teammate's plan.", obj("properties",
                Map.of("request_id", Map.of("type", "string"),
                       "approve", Map.of("type", "boolean"),
                       "feedback", Map.of("type", "string")),
                "required", List.of("request_id", "approve"))));
        t.add(td("idle", "Enter idle state.", obj("properties", Map.of())));
        t.add(td("claim_task", "Claim a task from the board.", obj("properties",
                Map.of("task_id", Map.of("type", "integer")),
                "required", List.of("task_id"))));
        return t;
    }

    private static ToolDefinition td(String name, String desc, Map<String, Object> schema) {
        Map<String, Object> full = new LinkedHashMap<>();
        full.put("type", "object");
        full.putAll(schema);
        return new ToolDefinition(name, desc, full);
    }

    private static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }
}
