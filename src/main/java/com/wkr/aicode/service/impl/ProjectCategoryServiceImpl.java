package com.wkr.aicode.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wkr.aicode.mapper.ProjectCategoryMapper;
import com.wkr.aicode.model.entity.ProjectCategory;
import com.wkr.aicode.service.ProjectCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 项目分类 服务层实现。
 *
 * @author <a>百夜</a>
 */
@Service
@Slf4j
public class ProjectCategoryServiceImpl extends ServiceImpl<ProjectCategoryMapper, ProjectCategory> implements ProjectCategoryService {

}
