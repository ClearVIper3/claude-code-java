package viper.com.claudecode.managers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * s05: 加载 skills 目录下的 SKILL.md 文件，解析 YAML 元数据头。
 */
public class SkillLoader {

    public static class Skill {
        public Map<String, String> meta;
        public String body;
        public Skill(Map<String, String> meta, String body) { this.meta = meta; this.body = body; }
    }

    private final Map<String, Skill> skills = new TreeMap<>();

    public SkillLoader(Path skillsDir) {
        if (skillsDir == null || !Files.exists(skillsDir)) return;
        try (var stream = Files.walk(skillsDir)) {
            List<Path> files = stream.filter(p -> p.getFileName().toString().equals("SKILL.md")).sorted().toList();
            Pattern hdr = Pattern.compile("^---\\n(.*?)\\n---\\n(.*)", Pattern.DOTALL);
            for (Path f : files) {
                String text = Files.readString(f);
                Matcher m = hdr.matcher(text);
                Map<String, String> meta = new LinkedHashMap<>();
                String body = text;
                if (m.find()) {
                    String[] lines = m.group(1).trim().split("\\r?\\n");
                    for (String line : lines) {
                        int idx = line.indexOf(':');
                        if (idx > 0) {
                            meta.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                        }
                    }
                    body = m.group(2).trim();
                }
                String name = meta.getOrDefault("name", f.getParent().getFileName().toString());
                skills.put(name, new Skill(meta, body));
            }
        } catch (IOException e) {
            // ignore
        }
    }

    public String descriptions() {
        if (skills.isEmpty()) return "(no skills)";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var e : skills.entrySet()) {
            if (!first) sb.append('\n');
            first = false;
            sb.append("  - ").append(e.getKey()).append(": ")
              .append(e.getValue().meta.getOrDefault("description", "-"));
        }
        return sb.toString();
    }

    public String load(String name) {
        Skill s = skills.get(name);
        if (s == null) return "Error: Unknown skill '" + name + "'. Available: " + String.join(", ", skills.keySet());
        return "<skill name=\"" + name + "\">\n" + s.body + "\n</skill>";
    }
}
