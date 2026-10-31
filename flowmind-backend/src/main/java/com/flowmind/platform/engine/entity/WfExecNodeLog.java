package com.flowmind.platform.engine.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("wf_exec_node_log")
public class WfExecNodeLog {
    @TableId(type = IdType.AUTO) private Long id;
    private Long execRecordId;
    private Integer stepOrder;
    private String nodeKey;
    private String nodeName;
    private String nodeType;
    private String stepStatus;
    private Integer tokenCost;
    private Integer costMs;
    private String nodeInput;
    private String nodeOutput;
    private LocalDateTime createdAt;
}
