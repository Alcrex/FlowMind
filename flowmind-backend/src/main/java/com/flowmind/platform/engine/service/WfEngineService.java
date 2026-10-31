package com.flowmind.platform.engine.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.common.security.LoginUser;
import com.flowmind.platform.engine.dto.*;
import com.flowmind.platform.engine.entity.*;
import com.flowmind.platform.engine.mapper.*;
import com.flowmind.platform.engine.runtime.*;
import com.flowmind.platform.engine.runtime.WorkflowDagExecutor.ExecutionPlan;
import com.flowmind.platform.engine.vo.ExecRecordDetailVO;
import com.flowmind.platform.workflow.dto.*;
import com.flowmind.platform.workflow.entity.WfNodeEdge;
import com.flowmind.platform.workflow.mapper.WfWorkflowMapper;
import com.flowmind.platform.workflow.service.WfWorkflowService;
import com.flowmind.platform.workflow.vo.WorkflowDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.*;
import java.util.function.BiConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class WfEngineService {
    private final WfWorkflowService workflows;
    private final WfWorkflowMapper workflowRows;
    private final WfExecRecordMapper records;
    private final WfExecNodeLogMapper logs;
    private final EngineAccountMapper accounts;
    private final DagTopologicalSorter sorter;
    private final WorkflowDagExecutor executor;

    public ExecutionPlan prepare(WorkflowExecuteReqDTO request, LoginUser user) {
        var canvas = workflows.detail(request.getWorkflowId(), user);
        if ((request.getCanvasOverride() != null || "TEACHER_REVIEW".equals(request.getTriggerMode()))
            && !Set.of("ADMIN", "TEACHER").contains(user.roleType())
            && !workflows.canEdit(workflows.requireWorkflow(request.getWorkflowId()), user)) throw new BusinessException(ErrorCode.FORBIDDEN);
        List<WfNodeEdge> nodes = canvas.nodes(), edges = canvas.edges();
        if (request.getCanvasOverride() != null) {
            nodes = request.getCanvasOverride().getNodes().stream().map(n -> { var e = new WfNodeEdge(); BeanUtils.copyProperties(n,e); e.setElementType("NODE"); return e; }).toList();
            edges = request.getCanvasOverride().getEdges().stream().map(n -> { var e = new WfNodeEdge(); BeanUtils.copyProperties(n,e); e.setElementType("EDGE"); return e; }).toList();
        }
        var sorted = sorter.sortAndValidate(nodes, edges);
        Integer quota = accounts.quota(user.id());
        if (quota == null) throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        if (quota <= 0) throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
        return new ExecutionPlan(request.getWorkflowId(), canvas.workflow().getWfName(), request.getTriggerMode(),
            new LinkedHashMap<>(request.getInputPayload()), sorted, edges);
    }
    public ExecRecordDetailVO execute(ExecutionPlan plan, LoginUser user, BiConsumer<String,Object> emit) { return executor.execute(plan, user, emit); }
    public ExecRecordDetailVO executeSync(WorkflowExecuteReqDTO request, LoginUser user) { return execute(prepare(request,user),user,(name,data) -> {}); }
    public PageResult<WfExecRecord> query(ExecRecordQueryDTO dto, LoginUser user) {
        var q = new LambdaQueryWrapper<WfExecRecord>();
        // Logs may contain private prompts: only the trigger user or an administrator can browse them.
        if (!"ADMIN".equals(user.roleType())) q.eq(WfExecRecord::getTriggerUserId, user.id());
        q.eq(dto.getWorkflowId()!=null, WfExecRecord::getWorkflowId, dto.getWorkflowId())
            .eq(StringUtils.hasText(dto.getExecStatus()), WfExecRecord::getExecStatus, dto.getExecStatus())
            .eq(StringUtils.hasText(dto.getTriggerMode()), WfExecRecord::getTriggerMode, dto.getTriggerMode())
            .ge(dto.getMinDuration()!=null, WfExecRecord::getDurationMs, dto.getMinDuration())
            .le(dto.getMaxDuration()!=null, WfExecRecord::getDurationMs, dto.getMaxDuration())
            .ge(dto.getStartTime()!=null, WfExecRecord::getStartedAt, dto.getStartTime())
            .le(dto.getEndTime()!=null, WfExecRecord::getStartedAt, dto.getEndTime())
            .orderByDesc(WfExecRecord::getStartedAt).orderByDesc(WfExecRecord::getId);
        var page = records.selectPage(new Page<>(dto.getPageNum(),dto.getPageSize()),q);
        var ids = page.getRecords().stream().map(WfExecRecord::getWorkflowId).distinct().toList();
        Map<Long,String> names = new HashMap<>();
        if (!ids.isEmpty()) workflowRows.selectBatchIds(ids).forEach(w -> names.put(w.getId(),w.getWfName()));
        page.getRecords().forEach(r -> r.setWfName(names.get(r.getWorkflowId())));
        return new PageResult<>(page.getRecords(),page.getTotal(),page.getCurrent(),page.getSize());
    }
    public ExecRecordDetailVO detail(Long id, LoginUser user) {
        var record = records.selectById(id);
        if (record == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!Objects.equals(user.id(),record.getTriggerUserId()) && !"ADMIN".equals(user.roleType())) throw new BusinessException(ErrorCode.FORBIDDEN);
        var workflow = workflowRows.selectById(record.getWorkflowId());
        record.setWfName(workflow == null ? "" : workflow.getWfName());
        return new ExecRecordDetailVO(record,logs.selectList(new LambdaQueryWrapper<WfExecNodeLog>()
            .eq(WfExecNodeLog::getExecRecordId,id).orderByAsc(WfExecNodeLog::getStepOrder)));
    }
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailVO textToFlow(TextToFlowReqDTO request, LoginUser user) {
        String prompt = request.getPrompt().strip(); String lower = prompt.toLowerCase(Locale.ROOT);
        var create = new WorkflowCreateDTO(); create.setWfName(prompt.substring(0,Math.min(80,prompt.length())));
        create.setDescription(prompt); create.setCategory(request.getCategory()); create.setTags("AI生成"); create.setIsPublic(0);
        var created = workflows.create(create,user);
        var nodes = new ArrayList<CanvasSaveDTO.NodeDTO>(); var edges = new ArrayList<CanvasSaveDTO.EdgeDTO>();
        nodes.add(node("node_input_1","INPUT",80,220,Map.of("variables",List.of(Map.of("name","text","type","string","required",true)))));
        String previous = "node_input_1"; String variable = "{{node_input_1.text}}"; double x = 360;
        if (contains(lower,"脚本","清洗","提取","转换")) {
            nodes.add(node("node_code_1","CODE",x,220,Map.of("ruleType","TRIM","expression",variable,"scriptLang","TEXT")));
            edges.add(edge(previous,"node_code_1","default",edges.size())); previous = "node_code_1"; variable = "{{node_code_1.output}}"; x+=280;
        }
        boolean branch = contains(lower,"判断","分支","如果","条件");
        String falseBranch = null;
        if (branch) {
            nodes.add(node("node_condition_1","CONDITION",x,220,Map.of("fieldExpr",variable,"operator","CONTAINS","targetValue",lower.contains("java") ? "Java" : "?")));
            edges.add(edge(previous,"node_condition_1","default",edges.size())); previous="node_condition_1"; x+=280;
            falseBranch = "node_llm_fallback";
            nodes.add(node(falseBranch,"LLM",x,340,Map.of("model","DEEPSEEK","temperature",0.4,"systemPrompt","处理未命中条件的输入，提供一般性帮助。","userPromptTemplate",variable)));
            edges.add(edge(previous,falseBranch,"false",edges.size()));
        }
        if (contains(lower,"知识库","规约","检索","rag")) {
            nodes.add(node("node_know_1","KNOWLEDGE",x,branch?100:220,Map.of("knowledgeBase","ALL","queryTemplate",variable,"topK",3)));
            edges.add(edge(previous,"node_know_1",branch?"true":"default",edges.size())); previous="node_know_1";
            variable = "参考规范：{{node_know_1.output}}\n用户输入：" + variable; x+=280;
        }
        nodes.add(node("node_llm_1","LLM",x,branch?100:220,Map.of("model","DEEPSEEK","temperature",0.4,"systemPrompt",prompt,"userPromptTemplate",variable)));
        edges.add(edge(previous,"node_llm_1",previous.equals("node_condition_1")?"true":"default",edges.size())); x+=280;
        nodes.add(node("node_output_1","OUTPUT",x,220,Map.of("outputFormat","MARKDOWN","resultTemplate",branch?"{{node_llm_1.output}}{{node_llm_fallback.output}}":"{{node_llm_1.output}}")));
        edges.add(edge("node_llm_1","node_output_1","default",edges.size()));
        if (falseBranch!=null) edges.add(edge(falseBranch,"node_output_1","default",edges.size()));
        var save = new CanvasSaveDTO(); save.setWfName(create.getWfName()); save.setDescription(prompt); save.setIsPublic(0);
        save.setNodes(nodes); save.setEdges(edges); save.setCreateSnapshot(true); save.setNewVersionTag("v1.0.1");
        var result = workflows.saveCanvas(created.workflow().getId(),save,user);
        sorter.sortAndValidate(result.nodes(),result.edges());
        log.info("Generated workflow {} for user {}",result.workflow().getId(),user.id()); return result;
    }
    private boolean contains(String text, String... terms) { return Arrays.stream(terms).anyMatch(text::contains); }
    private CanvasSaveDTO.NodeDTO node(String key,String type,double x,double y,Map<String,Object> config) {
        var node = new CanvasSaveDTO.NodeDTO(); node.setElementKey(key); node.setNodeType(type); node.setLabelName(switch(type) {
            case "INPUT" -> "用户输入"; case "KNOWLEDGE" -> "规范知识检索"; case "CODE" -> "文本清洗"; case "CONDITION" -> "条件路由";
            case "LLM" -> "智能分析"; default -> "结果输出";
        }); node.setPositionJson(Map.of("x",x,"y",y)); node.setConfigJson(config); return node;
    }
    private CanvasSaveDTO.EdgeDTO edge(String source,String target,String handle,int index) {
        var edge = new CanvasSaveDTO.EdgeDTO(); edge.setElementKey("edge_"+index); edge.setSourceKey(source); edge.setTargetKey(target); edge.setSourceHandle(handle); return edge;
    }
}
