package com.luzihao.core.diagnose;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 结构化证据清单：每条证据有唯一 evidenceId，模型引用与后端校验都以它为准 */
@Component
public class EvidenceBundle {

    public record Evidence(String evidenceId, String content) {}

    private final List<Evidence> all = new ArrayList<>();

    public void add(String evidenceId, String content) { all.add(new Evidence(evidenceId, content)); }
    public int size() { return all.size(); }
    public List<String> ids() { return all.stream().map(Evidence::evidenceId).toList(); }
    public long countByPrefix(String prefix) {
        return all.stream().filter(e -> e.evidenceId().startsWith(prefix)).count();
    }

    /** 组装给模型的上下文：证据区块 + 问题。evidenceId 是唯一引用凭证 */
    public String toContext(String question) {
        StringBuilder sb = new StringBuilder("问题：").append(question).append("\n\n<证据>\n");
        for (Evidence e : all) {
            sb.append("[").append(e.evidenceId()).append("] ").append(e.content()).append("\n");
        }
        return sb.append("</证据>").toString();
    }
}
