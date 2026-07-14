package com.wkr.aicode.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.wkr.aicode.model.dto.project.ProjectAddRequest;
import com.wkr.aicode.model.dto.project.ProjectQueryRequest;
import com.wkr.aicode.model.dto.project.ProjectUpdateRequest;
import com.wkr.aicode.model.entity.Project;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.vo.project.ProjectVO;

import java.util.List;

/**
 * 项目商品 服务层。
 *
 * @author <a>百夜</a>
 */
public interface ProjectService extends IService<Project> {

    /**
     * 发布项目
     */
    Long createProject(ProjectAddRequest addRequest, User loginUser);

    /**
     * 更新项目
     */
    boolean updateProject(ProjectUpdateRequest updateRequest, User loginUser);

    /**
     * 获取项目VO（含作者和图片信息）
     */
    ProjectVO getProjectVO(Project project);

    /**
     * 批量获取项目VO列表
     */
    List<ProjectVO> getProjectVOList(List<Project> projectList);

    /**
     * 构建查询条件
     */
    QueryWrapper getQueryWrapper(ProjectQueryRequest queryRequest);

    /**
     * 增加浏览量
     */
    void incrementViewCount(Long projectId);
}
