package viper.com.claudecode.core;

import viper.com.claudecode.Config;

/**
 * s10: 构建系统提示词。
 */
public final class SystemPrompt {

    private SystemPrompt() {}

    public static String build() {
        return "You are a coding agent at " + Config.WORKDIR + ". Use tools to solve tasks.\n" +
               "Prefer task_create/task_update/task_list for multi-step work. " +
               "Use TodoWrite for short checklists.\n" +
               "Use task for subagent delegation. Use load_skill for specialized knowledge.\n" +
               "Skills: " + Context.SKILLS.descriptions();
    }
}
