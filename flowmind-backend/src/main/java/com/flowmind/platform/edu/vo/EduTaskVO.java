package com.flowmind.platform.edu.vo;
import com.flowmind.platform.edu.entity.EduTask;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class EduTaskVO extends EduTask {
    private String teacherName;
    private Long mySubmissionId;
    private Integer myReviewStatus;
    private java.math.BigDecimal myScore;
}
