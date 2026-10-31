package com.flowmind.platform.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.common.security.*;
import com.flowmind.platform.workflow.dto.*;
import com.flowmind.platform.workflow.entity.*;
import com.flowmind.platform.workflow.mapper.*;
import com.flowmind.platform.workflow.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WfWorkflowService {
    private final WfWorkflowMapper workflows;
    private final WfNodeEdgeMapper elements;
    private final WorkflowReadMapper reads;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    public record AccessChanged(Long workflowId, boolean resetCanvas) {}

    private LambdaQueryWrapper<WfWorkflow> active() { return new LambdaQueryWrapper<WfWorkflow>().eq(WfWorkflow::getStatus, 1); }
    private void collaboratorCondition(LambdaQueryWrapper<WfWorkflow> q, Long userId) {
        q.apply("JSON_CONTAINS(collaborator_ids, {0})", userId.toString());
    }
    private void visible(LambdaQueryWrapper<WfWorkflow> q, LoginUser user, String scope) {
        switch (scope) {
            case "MINE" -> q.eq(WfWorkflow::getCreatorId, user.id());
            case "COLLAB" -> { q.ne(WfWorkflow::getCreatorId, user.id()); collaboratorCondition(q, user.id()); }
            default -> {
                if (!"ADMIN".equals(user.roleType())) q.and(v -> v.eq(WfWorkflow::getCreatorId, user.id())
                    .or(c -> collaboratorCondition(c, user.id())).or().eq(WfWorkflow::getIsPublic, 1));
            }
        }
    }
    public PageResult<WorkflowSummaryVO> query(WorkflowQueryDTO dto, LoginUser user) {
        var q = active();
        visible(q, user, dto.getScope());
        if (StringUtils.hasText(dto.getKeyword())) q.and(k -> k.like(WfWorkflow::getWfName, dto.getKeyword())
            .or().like(WfWorkflow::getDescription, dto.getKeyword()).or().like(WfWorkflow::getTags, dto.getKeyword()));
        q.eq(StringUtils.hasText(dto.getCategory()), WfWorkflow::getCategory, dto.getCategory())
            .eq(dto.getIsPublic() != null, WfWorkflow::getIsPublic, dto.getIsPublic())
            .ge(dto.getMinNodeCount() != null, WfWorkflow::getNodeCount, dto.getMinNodeCount())
            .le(dto.getMaxNodeCount() != null, WfWorkflow::getNodeCount, dto.getMaxNodeCount())
            .ge(dto.getStartTime() != null, WfWorkflow::getUpdatedAt, dto.getStartTime())
            .le(dto.getEndTime() != null, WfWorkflow::getUpdatedAt, dto.getEndTime())
            .orderByDesc(WfWorkflow::getUpdatedAt).orderByDesc(WfWorkflow::getId);
        var page = workflows.selectPage(new Page<>(dto.getPageNum(), dto.getPageSize()), q);
        return new PageResult<>(page.getRecords().stream().map(this::summary).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }
    public WorkspaceDashboardVO dashboard(LoginUser user) {
        long mine = workflows.selectCount(active().eq(WfWorkflow::getCreatorId, user.id()));
        var cq = active().ne(WfWorkflow::getCreatorId, user.id()); collaboratorCondition(cq, user.id());
        long collab = workflows.selectCount(cq);
        var recent = active().and(v -> v.eq(WfWorkflow::getCreatorId, user.id()).or(c -> collaboratorCondition(c, user.id())))
            .orderByDesc(WfWorkflow::getUpdatedAt).last("LIMIT 6");
        Long quota = reads.quota(user.id());
        return new WorkspaceDashboardVO(mine, collab, "STUDENT".equals(user.roleType()) ? reads.pendingTasks(user.id()) : 0,
            quota == null ? 0 : quota, workflows.selectList(recent).stream().map(this::summary).toList());
    }
    private WorkflowSummaryVO summary(WfWorkflow workflow) {
        var vo = new WorkflowSummaryVO(); BeanUtils.copyProperties(workflow, vo);
        vo.setCreatorName(reads.userName(workflow.getCreatorId())); return vo;
    }
    public WfWorkflow requireWorkflow(Long id) {
        WfWorkflow workflow = workflows.selectById(id);
        if (workflow == null || workflow.getStatus() != 1) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return workflow;
    }
    private WfWorkflow lock(Long id) {
        WfWorkflow workflow = workflows.selectOne(active().eq(WfWorkflow::getId, id).last("FOR UPDATE"));
        if (workflow == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return workflow;
    }
    public boolean canEdit(WfWorkflow w, LoginUser user) {
        return Objects.equals(w.getCreatorId(), user.id()) || (w.getCollaboratorIds() != null && w.getCollaboratorIds().contains(user.id()));
    }
    public void requireEdit(Long id, LoginUser user) { requireEdit(requireWorkflow(id), user); }
    private void requireEdit(WfWorkflow w, LoginUser user) {
        if (!canEdit(w, user)) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    private void requireOwner(WfWorkflow w, LoginUser user) {
        if (!Objects.equals(w.getCreatorId(), user.id())) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    public WorkflowDetailVO detail(Long id, LoginUser user) {
        var w = requireWorkflow(id);
        if (!canEdit(w, user) && w.getIsPublic() != 1 && !"ADMIN".equals(user.roleType())
            && !("TEACHER".equals(user.roleType()) && reads.canReview(id, user.id()) > 0)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return detail(w);
    }
    private WorkflowDetailVO detail(WfWorkflow w) {
        var rows = elements.selectList(new LambdaQueryWrapper<WfNodeEdge>().eq(WfNodeEdge::getWorkflowId, w.getId()).orderByAsc(WfNodeEdge::getId));
        return new WorkflowDetailVO(summary(w), rows.stream().filter(e -> "NODE".equals(e.getElementType())).toList(),
            rows.stream().filter(e -> "EDGE".equals(e.getElementType())).toList());
    }
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailVO create(WorkflowCreateDTO dto, LoginUser user) {
        var w = new WfWorkflow(); BeanUtils.copyProperties(dto, w);
        w.setWfCode("WF-" + UUID.randomUUID()); w.setCreatorId(user.id()); w.setCollaboratorIds(new ArrayList<>());
        w.setNodeCount(2); w.setEdgeCount(0); w.setForkCount(0); w.setLikeCount(0); w.setVersionTag("v1.0.0");
        w.setStatus(1); w.setCreatedAt(LocalDateTime.now()); w.setUpdatedAt(w.getCreatedAt()); w.setHistorySnapshotsJson(json.createArrayNode());
        workflows.insert(w);
        insertDefault(w.getId(), "node_input_1", "INPUT", "输入", 80.0);
        insertDefault(w.getId(), "node_output_1", "OUTPUT", "输出", 640.0);
        archive(w, user); workflows.updateById(w);
        log.info("Created workflow {} by {}", w.getId(), user.id()); return detail(w);
    }
    private void insertDefault(Long workflowId, String key, String type, String label, double x) {
        var e = new WfNodeEdge(); e.setWorkflowId(workflowId); e.setElementKey(key); e.setElementType("NODE");
        e.setNodeType(type); e.setLabelName(label); e.setPositionJson(Map.of("x", x, "y", 220.0));
        e.setConfigJson("INPUT".equals(type) ? Map.of("variables", List.of()) : Map.of("outputFormat", "MARKDOWN", "resultTemplate", ""));
        insertElement(e);
    }
    private void insertElement(WfNodeEdge element) {
        element.setId(null); element.setCreatedAt(LocalDateTime.now()); element.setUpdatedAt(element.getCreatedAt()); elements.insert(element);
    }
    public void validateCanvas(CanvasSaveDTO dto) {
        Set<String> keys = new HashSet<>(), nodes = new HashSet<>();
        for (var n : dto.getNodes()) {
            if (!keys.add(n.getElementKey())) invalid("元素 Key 重复"); nodes.add(n.getElementKey());
            var p = n.getPositionJson();
            if (p == null || p.get("x") == null || p.get("y") == null || !Double.isFinite(p.get("x")) || !Double.isFinite(p.get("y"))) invalid("节点坐标必须为有限数值");
        }
        for (var e : dto.getEdges()) {
            if (!keys.add(e.getElementKey())) invalid("元素 Key 重复");
            if (!nodes.contains(e.getSourceKey()) || !nodes.contains(e.getTargetKey())) invalid("连线引用的节点不存在");
        }
    }
    private void invalid(String message) { throw new BusinessException(ErrorCode.PARAM_ERROR, message); }
    private ArrayNode history(WfWorkflow w) {
        return w.getHistorySnapshotsJson() instanceof ArrayNode a ? a.deepCopy() : json.createArrayNode();
    }
    private boolean hasVersion(ArrayNode snapshots, String version) {
        for (JsonNode s : snapshots) if (version.equals(s.path("versionTag").asText())) return true;
        return false;
    }
    private void archive(WfWorkflow w, LoginUser user) {
        ArrayNode snapshots = history(w);
        if (hasVersion(snapshots, w.getVersionTag())) return;
        var canvas = detail(w); ObjectNode snapshot = json.createObjectNode();
        snapshot.put("versionTag", w.getVersionTag()); snapshot.put("savedAt", LocalDateTime.now().toString()); snapshot.put("savedBy", user.id());
        snapshot.put("wfName", w.getWfName()); snapshot.put("description", w.getDescription());
        snapshot.set("nodes", json.valueToTree(canvas.nodes())); snapshot.set("edges", json.valueToTree(canvas.edges()));
        snapshots.add(snapshot); w.setHistorySnapshotsJson(snapshots);
    }
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailVO saveCanvas(Long id, CanvasSaveDTO dto, LoginUser user) {
        var w = lock(id); requireEdit(w, user); validateCanvas(dto);
        if (!Objects.equals(w.getIsPublic(), dto.getIsPublic())) requireOwner(w, user);
        if (dto.isCreateSnapshot()) {
            if (!StringUtils.hasText(dto.getNewVersionTag()) || hasVersion(history(w), dto.getNewVersionTag())) invalid("新版本号不能为空或重复");
            archive(w, user); w.setVersionTag(dto.getNewVersionTag());
        }
        elements.delete(new LambdaQueryWrapper<WfNodeEdge>().eq(WfNodeEdge::getWorkflowId, id));
        for (var node : dto.getNodes()) {
            var e = new WfNodeEdge(); BeanUtils.copyProperties(node, e); e.setWorkflowId(id); e.setElementType("NODE"); insertElement(e);
        }
        for (var edge : dto.getEdges()) {
            var e = new WfNodeEdge(); BeanUtils.copyProperties(edge, e); e.setWorkflowId(id); e.setElementType("EDGE"); insertElement(e);
        }
        w.setWfName(dto.getWfName()); w.setDescription(dto.getDescription()); w.setIsPublic(dto.getIsPublic());
        w.setNodeCount(dto.getNodes().size()); w.setEdgeCount(dto.getEdges().size()); w.setUpdatedAt(LocalDateTime.now());
        if (dto.isCreateSnapshot()) archive(w, user);
        workflows.updateById(w); log.info("Saved workflow {}: {} nodes, {} edges", id, w.getNodeCount(), w.getEdgeCount());
        return detail(w);
    }
    public JsonNode versions(Long id, LoginUser user) { var w = requireWorkflow(id); requireEdit(w, user); return history(w); }
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailVO rollback(Long id, String version, LoginUser user) {
        var w = lock(id); requireEdit(w, user);
        JsonNode selected = null;
        for (JsonNode s : history(w)) if (version.equals(s.path("versionTag").asText())) selected = s;
        if (selected == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "版本快照不存在");
        archive(w, user);
        elements.delete(new LambdaQueryWrapper<WfNodeEdge>().eq(WfNodeEdge::getWorkflowId, id));
        for (String kind : List.of("nodes", "edges")) for (JsonNode n : selected.path(kind)) {
            var e = json.convertValue(n, WfNodeEdge.class); e.setWorkflowId(id); insertElement(e);
        }
        w.setVersionTag(version); w.setNodeCount(selected.path("nodes").size()); w.setEdgeCount(selected.path("edges").size());
        w.setWfName(selected.path("wfName").asText(w.getWfName())); w.setDescription(selected.path("description").asText(""));
        w.setUpdatedAt(LocalDateTime.now()); workflows.updateById(w);
        events.publishEvent(new AccessChanged(id, true)); log.info("Rolled back workflow {} to {}", id, version); return detail(w);
    }
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailVO cloneWorkflow(Long id, LoginUser user) {
        var source = detail(id, user);
        var w = new WfWorkflow(); BeanUtils.copyProperties(source.workflow(), w);
        w.setId(null); w.setWfCode("WF-" + UUID.randomUUID()); w.setWfName(source.workflow().getWfName().substring(0, Math.min(120, source.workflow().getWfName().length())) + " 副本");
        w.setCreatorId(user.id()); w.setCollaboratorIds(List.of()); w.setIsPublic(0); w.setForkCount(0); w.setLikeCount(0);
        w.setHistorySnapshotsJson(json.createArrayNode()); w.setVersionTag("v1.0.0"); w.setCreatedAt(LocalDateTime.now()); w.setUpdatedAt(w.getCreatedAt()); workflows.insert(w);
        for (var row : java.util.stream.Stream.concat(source.nodes().stream(), source.edges().stream()).toList()) {
            var e = new WfNodeEdge(); BeanUtils.copyProperties(row, e); e.setWorkflowId(w.getId()); insertElement(e);
        }
        archive(w, user); workflows.updateById(w); return detail(w);
    }
    @Transactional(rollbackFor = Exception.class)
    public boolean collaborators(Long id, List<Long> ids, LoginUser user) {
        var w = lock(id); requireOwner(w, user);
        var unique = ids.stream().distinct().filter(member -> !member.equals(w.getCreatorId())).toList();
        for (Long member : unique) if (reads.activeUser(member) != 1) invalid("协作用户不存在或已禁用：" + member);
        w.setCollaboratorIds(unique); w.setUpdatedAt(LocalDateTime.now()); workflows.updateById(w);
        events.publishEvent(new AccessChanged(id, false)); return true;
    }
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id, LoginUser user) {
        var w = lock(id); requireOwner(w, user);
        elements.delete(new LambdaQueryWrapper<WfNodeEdge>().eq(WfNodeEdge::getWorkflowId, id));
        workflows.deleteById(id); events.publishEvent(new AccessChanged(id, false)); log.info("Deleted workflow {}", id); return true;
    }
}
