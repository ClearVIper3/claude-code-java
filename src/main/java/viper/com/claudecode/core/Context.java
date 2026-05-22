package viper.com.claudecode.core;

import viper.com.claudecode.AnthropicClient;
import viper.com.claudecode.Config;
import viper.com.claudecode.managers.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局单例容器，持有所有管理器与请求/计划字典。
 * 对应 Python 的全局变量 (TODO, SKILLS, TASK_MGR, BG, BUS, TEAM, shutdown_requests, plan_requests)。
 */
public final class Context {

    public static final AnthropicClient CLIENT = new AnthropicClient();
    public static final TodoManager TODO = new TodoManager();
    public static final SkillLoader SKILLS = new SkillLoader(Config.SKILLS_DIR);
    public static final TaskManager TASK_MGR = new TaskManager();
    public static final BackgroundManager BG = new BackgroundManager();
    public static final MessageBus BUS = new MessageBus();
    public static final TeammateManager TEAM = new TeammateManager(BUS, TASK_MGR, CLIENT);

    /** s10: shutdown 请求字典 */
    public static final Map<String, Map<String, Object>> SHUTDOWN_REQUESTS = new HashMap<>();
    /** s10: 计划审批请求字典 */
    public static final Map<String, Map<String, Object>> PLAN_REQUESTS = new HashMap<>();

    private Context() {}
}
