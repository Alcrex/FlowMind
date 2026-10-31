package com.flowmind.platform.engine.runtime;
import com.flowmind.platform.common.api.ErrorCode;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.workflow.entity.WfNodeEdge;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class DagTopologicalSorter {
    private static final Set<String> TYPES = Set.of("INPUT", "OUTPUT", "LLM", "KNOWLEDGE", "CODE", "CONDITION");
    public List<WfNodeEdge> sortAndValidate(List<WfNodeEdge> nodes, List<WfNodeEdge> edges) {
        if (nodes == null || edges == null || nodes.isEmpty() || nodes.size() > 500 || edges.size() > 2000)
            invalid("画布节点或连线数量无效");
        Map<String, WfNodeEdge> byKey = new LinkedHashMap<>();
        Map<String, Integer> degree = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>(), incoming = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (var node : nodes) {
            String key = node.getElementKey();
            if (key == null || key.isBlank() || !keys.add(key) || !TYPES.contains(Objects.toString(node.getNodeType(), "")))
                invalid("节点 Key 重复、为空或节点类型不合法");
            byKey.put(key, node); degree.put(key, 0); outgoing.put(key, new ArrayList<>()); incoming.put(key, new ArrayList<>());
        }
        if (nodes.stream().noneMatch(n -> "INPUT".equals(n.getNodeType())) || nodes.stream().noneMatch(n -> "OUTPUT".equals(n.getNodeType())))
            invalid("工作流必须包含 INPUT 和 OUTPUT 节点");
        for (var edge : edges) {
            String source = edge.getSourceKey(), target = edge.getTargetKey();
            if (edge.getElementKey() == null || !keys.add(edge.getElementKey()) || !byKey.containsKey(source) || !byKey.containsKey(target))
                invalid("连线 Key 重复或引用了不存在的节点");
            String handle = Objects.toString(edge.getSourceHandle(), "default");
            if ("CONDITION".equals(byKey.get(source).getNodeType()) ? !Set.of("true", "false").contains(handle) : !"default".equals(handle))
                invalid("连线输出锚点与节点类型不匹配");
            outgoing.get(source).add(target); incoming.get(target).add(source); degree.merge(target, 1, Integer::sum);
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        degree.forEach((key, count) -> { if (count == 0) queue.add(key); });
        List<WfNodeEdge> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.remove(); sorted.add(byKey.get(current));
            for (String next : outgoing.get(current)) if (degree.merge(next, -1, Integer::sum) == 0) queue.add(next);
        }
        if (sorted.size() != nodes.size()) invalid("工作流画布存在循环依赖环路");
        var fromInputs = reachable(nodes, "INPUT", outgoing);
        var toOutputs = reachable(nodes, "OUTPUT", incoming);
        if (fromInputs.size() != nodes.size() || toOutputs.size() != nodes.size()) invalid("存在无法从输入到达或无法到达输出的孤立节点");
        for (var n : nodes) {
            if ("INPUT".equals(n.getNodeType()) && !incoming.get(n.getElementKey()).isEmpty()) invalid("INPUT 不能存在入边");
            if ("OUTPUT".equals(n.getNodeType()) && !outgoing.get(n.getElementKey()).isEmpty()) invalid("OUTPUT 不能存在出边");
        }
        return sorted;
    }
    private Set<String> reachable(List<WfNodeEdge> nodes, String type, Map<String, List<String>> adjacency) {
        Set<String> seen = new HashSet<>(); var queue = new ArrayDeque<String>();
        nodes.stream().filter(n -> type.equals(n.getNodeType())).forEach(n -> queue.add(n.getElementKey()));
        while (!queue.isEmpty()) { String key = queue.remove(); if (seen.add(key)) queue.addAll(adjacency.get(key)); }
        return seen;
    }
    private void invalid(String message) { throw new BusinessException(ErrorCode.DAG_CYCLE_ERROR, message); }
}
