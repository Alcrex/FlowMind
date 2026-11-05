package com.flowmind.platform.engine.runtime;

import com.flowmind.platform.common.api.ErrorCode;
import com.flowmind.platform.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Loads course markdown and ranks paragraphs by term frequency, with title matches counted twice. */
@Component
public class KnowledgeBase {
    private final List<Passage> passages = new ArrayList<>();

    @PostConstruct
    public void load() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] files = resolver.getResources("classpath:knowledge/*.md");
        if (files.length == 0) throw new IllegalStateException("知识库目录为空");
        for (Resource file : files) {
            String name = file.getFilename();
            String body = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String base = switch (name == null ? "" : name) {
                case "ali-java-spec.md" -> "ALI_JAVA_SPEC";
                case "owasp-web.md" -> "OWASP";
                case "spring-boot.md" -> "SPRING_BOOT";
                case "vue3-spec.md" -> "VUE3";
                default -> "";
            };
            if (base.isEmpty()) continue;
            String title = "未命名";
            var current = new StringBuilder();
            for (String line : body.split("\\R")) {
                if (line.startsWith("## ")) {
                    add(base, name, title, current);
                    title = line.substring(3).strip();
                    current.setLength(0);
                } else current.append(line).append('\n');
            }
            add(base, name, title, current);
        }
        if (passages.isEmpty()) throw new IllegalStateException("知识库没有可用段落");
    }

    private void add(String base, String file, String title, StringBuilder body) {
        String content = body.toString().strip();
        if (!content.isEmpty()) passages.add(new Passage(base, file, title, content));
    }

    public String retrieve(String base, String query, int topK) {
        if (topK < 1 || topK > 10) throw new BusinessException(ErrorCode.PARAM_ERROR, "topK 范围为 1~10");
        String normalized = switch (base) {
            case "OWASP_WEB" -> "OWASP";
            case "SPRING_BOOT_BEST" -> "SPRING_BOOT";
            case "VUE3_SPEC" -> "VUE3";
            default -> base;
        };
        if (!"ALL".equals(normalized) && passages.stream().noneMatch(p -> p.base().equals(normalized)))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "知识库不存在：" + base);
        List<String> terms = terms(query);
        var selected = passages.stream()
            .filter(p -> "ALL".equals(normalized) || p.base().equals(normalized))
            .map(p -> new Scored(p, score(p, terms)))
            .filter(s -> s.score() > 0)
            .sorted(Comparator.comparingInt(Scored::score).reversed())
            .limit(topK)
            .map(s -> "来源：" + s.passage().file() + " / " + s.passage().title() + "\n" + s.passage().content())
            .toList();
        if (selected.isEmpty()) return "没有匹配段落";
        return String.join("\n", selected);
    }

    private int score(Passage passage, List<String> terms) {
        String title = passage.title().toLowerCase(Locale.ROOT);
        String content = passage.content().toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (content.contains(term)) score++;
            if (title.contains(term)) score++;
        }
        return score;
    }

    private List<String> terms(String query) {
        if (query == null || query.isBlank()) return List.of();
        String lower = query.toLowerCase(Locale.ROOT);
        var terms = new ArrayList<String>();
        var current = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isWhitespace(c) || "，。！？、；：,.!?;:".indexOf(c) >= 0) {
                if (current.length() > 0) { terms.add(current.toString()); current.setLength(0); }
            } else current.append(c);
        }
        if (current.length() > 0) terms.add(current.toString());
        return terms.stream().filter(t -> t.length() > 1).distinct().toList();
    }

    private record Passage(String base, String file, String title, String content) {}
    private record Scored(Passage passage, int score) {}
}
