package com.wkr.aicode.service;

import com.mybatisflex.core.service.IService;
import com.wkr.aicode.model.entity.ProjectImage;

import java.util.List;

/**
 * 项目图片 服务层。
 *
 * @author <a>百夜</a>
 */
public interface ProjectImageService extends IService<ProjectImage> {

    /**
     * 根据项目ID查询图片列表
     */
    List<ProjectImage> listByProjectId(Long projectId);

    /**
     * 根据项目ID删除所有图片
     */
    void deleteByProjectId(Long projectId);
}
