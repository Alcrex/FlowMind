package com.flowmind.platform.engine.vo;
import com.flowmind.platform.engine.entity.*;
import java.util.List;
public record ExecRecordDetailVO(WfExecRecord record, List<WfExecNodeLog> nodeLogs) {}
