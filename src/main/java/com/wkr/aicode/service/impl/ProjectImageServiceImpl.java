package com.wkr.aicode.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wkr.aicode.mapper.ProjectImageMapper;
import com.wkr.aicode.model.entity.ProjectImage;
import com.wkr.aicode.service.ProjectImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目图片 服务层实现。
 *
 * @author <a>百夜</a>
 */
@Service
@Slf4j
public class ProjectImageServiceImpl extends ServiceImpl<ProjectImageMapper, ProjectImage> implements ProjectImageService {

    @Override
    public List<ProjectImage> listByProjectId(Long projectId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("projectId", projectId)
                .orderBy("sortOrder", true);
        return this.list(wrapper);
    }

    @Override
    public void deleteByProjectId(Long projectId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("projectId", projectId);
        this.remove(wrapper);
    }
}
