# claude-code-java

> 一个 **小而完整** 的 Capstone Teaching Agent —— 用 Java 23 重写自 `s_full.py`，
> 通过 Anthropic Messages 协议，把 LLM 变成可调用 25 种工具、能并行多智能体协作的"代码助手"。

---

## 特性一览

- **25 个内置工具** —— 文件读写、Bash/PowerShell、Grep、Glob、Skill、Subagent、Task、Team、Inbox……
- **多智能体协作** —— `lead` 与多个 `teammate` 通过 `MessageBus` 收发消息，支持 `shutdown_request` / `plan_approval`。
- **自动上下文压缩** —— 超过 `TOKEN_THRESHOLD`（默认 10w tokens）触发摘要，保留最近 N 轮原文。
- **大输出持久化** —— 工具产物自动落盘到 `.task_outputs/`，对话里只留预览片段。
- **Skill 动态加载** —— `skills/` 目录下放置 Markdown 即可被 LLM 通过 `use_skill` 调用。
- **协议级兼容** —— 任何兼容 Anthropic 协议的网关都可一键接入（已内置对 DeepSeek `/anthropic` 端点的支持）。

---

## 快速开始

### 1. 准备环境

| 依赖 | 版本 |
|---|---|
| JDK | 23+ |
| Maven | 3.9+ |

### 2. 配置 `.env`

在项目根目录新建 `.env`：

```dotenv
# 使用 DeepSeek 的 Anthropic 兼容端点
ANTHROPIC_BASE_URL=https://api.deepseek.com/anthropic
ANTHROPIC_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxx
MODEL_ID=deepseek-chat
```

> 如果想直连 Anthropic 官方，只需删除 `ANTHROPIC_BASE_URL`，并改用 `ANTHROPIC_AUTH_TOKEN`。

### 3. 运行

```bash
mvn -q -DskipTests compile
mvn -q exec:java
```

进入交互式 REPL：

```text
s_full >> 帮我把项目里所有 TODO 都列出来
```

### 4. 内置斜杠命令

| 命令 | 作用 |
|---|---|
| `/compact` | 立即压缩对话上下文 |
| `/tasks`   | 列出所有任务（pending/in_progress/completed） |
| `/team`    | 列出已招募的 teammate |
| `/inbox`   | 查看 lead 的收件箱 |
| `q` / `exit` / 空行 | 退出 |

---

## 项目结构

```
src/main/java/viper/com/claudecode
├── Main.java                  # REPL 入口
├── Config.java                # 全局常量与环境变量
├── AnthropicClient.java       # Anthropic Messages API 客户端
├── core/
│   ├── AgentLoop.java         # 智能体主循环
│   ├── ToolDispatch.java      # 25 个工具的注册与分派
│   ├── SystemPrompt.java      # 系统提示词构建
│   └── Context.java           # 全局共享上下文
├── tools/                     # base_tools / persisted_output / Subagent
├── managers/                  # Todo / Task / Background / MessageBus / Skill / Teammate
├── compress/Compression.java  # 自动摘要压缩
└── model/                     # Message / ContentBlock / ToolDefinition / AnthropicResponse
```

---

## 与 Python 原版对照

| Python (`s_full.py`) | Java |
|---|---|
| `s01_agent_loop` | `core/AgentLoop.java` |
| `s02_tools` | `core/ToolDispatch.java` + `tools/BaseTools.java` |
| `s03_todo` | `managers/TodoManager.java` |
| `s04_subagent` | `tools/Subagent.java` |
| `s05_skills` | `managers/SkillLoader.java` |
| `s06_compress` | `compress/Compression.java` + `tools/PersistedOutput.java` |
| `s07_tasks` | `managers/TaskManager.java` |
| `s08_background` | `managers/BackgroundManager.java` |
| `s09_messaging` | `managers/MessageBus.java` + `managers/TeammateManager.java` |
| `s10_governance` | `core/ToolDispatch.java` (shutdown / plan_approval) |
| `s11_robustness` | `AgentLoop` 的错误注入与退避 |

---

## License

MIT — 仅作为教学用途，欢迎拷贝、改造、二次发行。
