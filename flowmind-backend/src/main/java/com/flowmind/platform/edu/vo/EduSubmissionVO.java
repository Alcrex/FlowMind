package com.flowmind.platform.edu.vo;
import com.fasterxml.jackson.databind.JsonNode;
import com.flowmind.platform.edu.entity.EduSubmission;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class EduSubmissionVO extends EduSubmission {
    private String taskTitle;
    private String targetClass;
    private String studentName;
    private String studentNumber;
    private JsonNode rubricJson;
}
