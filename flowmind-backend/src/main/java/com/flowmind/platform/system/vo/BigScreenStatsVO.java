package com.flowmind.platform.system.vo;
import java.time.LocalDateTime;
import java.util.List;
public record BigScreenStatsVO(Kpi kpi, SankeyData sankeyData, List<DeptRanking> deptRanking, List<HourlyTrend> hourlyTrend) {
    public record Kpi(long totalUsers, long totalWorkflows, long totalExecutions, long totalTokensUsed) {}
    public record SankeyNode(String name) {}
    public record SankeyLink(String source, String target, long value) {}
    public record SankeyData(List<SankeyNode> nodes, List<SankeyLink> links) {}
    public record DeptRanking(String deptClass, long workflowCount, long execCount) {}
    public record HourlyTrend(String hour, long callCount, double avgLatencyMs) {}
    public record TokenFlow(String deptClass, String roleType, String modelProvider, long tokens) {}
    public record CallSample(LocalDateTime createdAt, long durationMs) {}
}
