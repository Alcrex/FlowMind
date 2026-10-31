package com.flowmind.platform.engine.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.common.security.LoginUser;
import com.flowmind.platform.engine.dto.TemplateMarketQueryDTO;
import com.flowmind.platform.workflow.entity.WfWorkflow;
import com.flowmind.platform.workflow.mapper.*;
import com.flowmind.platform.workflow.service.WfWorkflowService;
import com.flowmind.platform.workflow.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateMarketService {
    private final WfWorkflowMapper rows;
    private final WorkflowReadMapper reads;
    private final WfWorkflowService workflows;
    private static final LoginUser VISITOR = new LoginUser(0L,"visitor","访客","VISITOR","");
    private LambdaQueryWrapper<WfWorkflow> published() { return new LambdaQueryWrapper<WfWorkflow>().eq(WfWorkflow::getStatus,1).eq(WfWorkflow::getIsPublic,1); }
    public PageResult<WorkflowSummaryVO> query(TemplateMarketQueryDTO dto) {
        var q = published();
        if (StringUtils.hasText(dto.getKeyword())) q.and(k -> k.like(WfWorkflow::getWfName,dto.getKeyword()).or().like(WfWorkflow::getDescription,dto.getKeyword()).or().like(WfWorkflow::getTags,dto.getKeyword()));
        q.eq(StringUtils.hasText(dto.getCategory()),WfWorkflow::getCategory,dto.getCategory())
            .like(StringUtils.hasText(dto.getTag()),WfWorkflow::getTags,dto.getTag())
            .ge(dto.getMinNodeCount()!=null,WfWorkflow::getNodeCount,dto.getMinNodeCount())
            .le(dto.getMaxNodeCount()!=null,WfWorkflow::getNodeCount,dto.getMaxNodeCount());
        switch(dto.getSortBy()) { case "popular" -> q.orderByDesc(WfWorkflow::getForkCount); case "likes" -> q.orderByDesc(WfWorkflow::getLikeCount); default -> q.orderByDesc(WfWorkflow::getUpdatedAt); }
        q.orderByDesc(WfWorkflow::getId);
        var page = rows.selectPage(new Page<>(dto.getPageNum(),dto.getPageSize()),q);
        return new PageResult<>(page.getRecords().stream().map(w -> { var vo = new WorkflowSummaryVO(); BeanUtils.copyProperties(w,vo); vo.setCreatorName(reads.userName(w.getCreatorId())); return vo; }).toList(),page.getTotal(),page.getCurrent(),page.getSize());
    }
    private void requirePublished(Long id, boolean lock) {
        var q = published().eq(WfWorkflow::getId,id); if (lock) q.last("FOR UPDATE");
        if (rows.selectOne(q)==null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    public WorkflowDetailVO detail(Long id) { requirePublished(id,false); return workflows.detail(id,VISITOR); }
    @Transactional(rollbackFor = Exception.class)
    public boolean like(Long id) {
        int changed = rows.update(null,new LambdaUpdateWrapper<WfWorkflow>().eq(WfWorkflow::getId,id)
            .eq(WfWorkflow::getStatus,1).eq(WfWorkflow::getIsPublic,1).setSql("like_count = like_count + 1"));
        if (changed!=1) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); return true;
    }
    @Transactional(rollbackFor = Exception.class)
    public Long fork(Long id, LoginUser user) {
        requirePublished(id,true);
        var cloned = workflows.cloneWorkflow(id,user);
        rows.update(null,new LambdaUpdateWrapper<WfWorkflow>().eq(WfWorkflow::getId,id).setSql("fork_count = fork_count + 1"));
        log.info("Forked template {} to {} for user {}",id,cloned.workflow().getId(),user.id()); return cloned.workflow().getId();
    }
}
