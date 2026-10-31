package com.flowmind.platform.edu.vo;
import java.util.List;
public record EduAnalyticsVO(Summary summary, List<ScoreBucket> scoreBuckets, List<RadarDimension> radarDimensions,
    List<DailyTrend> dailySubmissionTrend, List<ErrorNode> errorNodeDistribution) {
    public record Summary(long totalSubmissions, long gradedCount, double passRate, double avgScore) {}
    public record ScoreBucket(String bucket, long count) {}
    public record RadarDimension(String dimension, double avgRate) {}
    public record DailyTrend(String date, long submitCount, long debugCount) {}
    public record ErrorNode(String nodeType, long errorCount) {}
}
