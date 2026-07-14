package com.wkr.aicode.service;

import com.mybatisflex.core.service.IService;
import com.wkr.aicode.model.dto.project.ProjectReviewAddRequest;
import com.wkr.aicode.model.entity.ProjectReview;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.vo.project.ProjectReviewVO;

import java.util.List;

/**
 * 项目评价 服务层。
 *
 * @author <a>百夜</a>
 */
public interface ProjectReviewService extends IService<ProjectReview> {

    /**
     * 发表评价
     */
    Long addReview(ProjectReviewAddRequest addRequest, User loginUser);

    /**
     * 获取项目评价列表
     */
    List<ProjectReviewVO> getReviewVOList(Long projectId);

    /**
     * 获取项目评价VO
     */
    ProjectReviewVO getReviewVO(ProjectReview review);
}
