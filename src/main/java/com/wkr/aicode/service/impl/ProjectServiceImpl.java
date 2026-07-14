package com.wkr.aicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.mapper.ProjectMapper;
import com.wkr.aicode.model.dto.project.ProjectAddRequest;
import com.wkr.aicode.model.dto.project.ProjectQueryRequest;
import com.wkr.aicode.model.dto.project.ProjectUpdateRequest;
import com.wkr.aicode.model.entity.Project;
import com.wkr.aicode.model.entity.ProjectCategory;
import com.wkr.aicode.model.entity.ProjectImage;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.enums.ProjectStatusEnum;
import com.wkr.aicode.model.vo.project.ProjectVO;
import com.wkr.aicode.model.vo.user.UserVO;
import com.wkr.aicode.service.ProjectCategoryService;
import com.wkr.aicode.service.ProjectImageService;
import com.wkr.aicode.service.ProjectService;
import com.wkr.aicode.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目商品 服务层实现。
 *
 * @author <a>百夜</a>
 */
@Service
@Slf4j
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    @Resource
    private UserService userService;

    @Resource
    private ProjectCategoryService projectCategoryService;

    @Resource
    private ProjectImageService projectImageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(ProjectAddRequest addRequest, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(addRequest.getName()), ErrorCode.PARAMS_ERROR, "项目名称不能为空");
        ThrowUtils.throwIf(addRequest.getCategoryId() == null || addRequest.getCategoryId() <= 0,
                ErrorCode.PARAMS_ERROR, "项目分类不能为空");
        ThrowUtils.throwIf(addRequest.getPrice() == null, ErrorCode.PARAMS_ERROR, "价格不能为空");
        ThrowUtils.throwIf(addRequest.getPrice().compareTo(BigDecimal.ZERO) < 0,
                ErrorCode.PARAMS_ERROR, "价格不能为负数");

        // 构造项目对象
        Project project = new Project();
        BeanUtil.copyProperties(addRequest, project);
        project.setAuthorId(loginUser.getId());
        project.setStatus(ProjectStatusEnum.PUBLISHED.getValue());
        project.setSalesCount(0);
        project.setViewCount(0);
        project.setAvgRating(BigDecimal.ZERO);

        // 保存项目
        boolean result = this.save(project);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "发布项目失败");
        Long projectId = project.getId();

        // 保存项目图片
        if (CollUtil.isNotEmpty(addRequest.getImages())) {
            List<ProjectImage> imageList = new ArrayList<>();
            for (ProjectAddRequest.ProjectImageItem item : addRequest.getImages()) {
                ProjectImage image = new ProjectImage();
                image.setProjectId(projectId);
                image.setImageUrl(item.getImageUrl());
                image.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                image.setType(item.getType() != null ? item.getType() : 0);
                imageList.add(image);
            }
            projectImageService.saveBatch(imageList);
        }

        log.info("项目发布成功，ID: {}, 作者: {}", projectId, loginUser.getId());
        return projectId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProject(ProjectUpdateRequest updateRequest, User loginUser) {
        ThrowUtils.throwIf(updateRequest.getId() == null || updateRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR, "项目ID不能为空");

        // 查询原项目
        Project oldProject = this.getById(updateRequest.getId());
        ThrowUtils.throwIf(oldProject == null, ErrorCode.NOT_FOUND_ERROR, "项目不存在");
        // 仅本人可编辑
        if (!oldProject.getAuthorId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限编辑该项目");
        }

        // 更新项目字段
        Project project = new Project();
        BeanUtil.copyProperties(updateRequest, project);
        project.setEditTime(LocalDateTime.now());
        // 不覆盖的字段
        project.setAuthorId(null);
        project.setSalesCount(null);
        project.setViewCount(null);
        project.setAvgRating(null);

        boolean result = this.updateById(project);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新项目失败");

        // 全量替换图片（先删后加）
        if (updateRequest.getImages() != null) {
            projectImageService.deleteByProjectId(updateRequest.getId());
            if (CollUtil.isNotEmpty(updateRequest.getImages())) {
                List<ProjectImage> imageList = new ArrayList<>();
                for (ProjectAddRequest.ProjectImageItem item : updateRequest.getImages()) {
                    ProjectImage image = new ProjectImage();
                    image.setProjectId(updateRequest.getId());
                    image.setImageUrl(item.getImageUrl());
                    image.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                    image.setType(item.getType() != null ? item.getType() : 0);
                    imageList.add(image);
                }
                projectImageService.saveBatch(imageList);
            }
        }

        log.info("项目更新成功，ID: {}", updateRequest.getId());
        return true;
    }

    @Override
    public ProjectVO getProjectVO(Project project) {
        if (project == null) {
            return null;
        }
        ProjectVO projectVO = new ProjectVO();
        BeanUtil.copyProperties(project, projectVO);

        // 分类名称
        if (project.getCategoryId() != null) {
            ProjectCategory category = projectCategoryService.getById(project.getCategoryId());
            if (category != null) {
                projectVO.setCategoryName(category.getName());
            }
        }

        // 作者信息
        if (project.getAuthorId() != null) {
            User user = userService.getById(project.getAuthorId());
            if (user != null) {
                projectVO.setAuthor(userService.getUserVO(user));
            }
        }

        // 项目图片
        List<ProjectImage> images = projectImageService.listByProjectId(project.getId());
        if (CollUtil.isNotEmpty(images)) {
            List<ProjectVO.ProjectImageVO> imageVOList = images.stream().map(img -> {
                ProjectVO.ProjectImageVO imageVO = new ProjectVO.ProjectImageVO();
                imageVO.setImageUrl(img.getImageUrl());
                imageVO.setSortOrder(img.getSortOrder());
                imageVO.setType(img.getType());
                return imageVO;
            }).collect(Collectors.toList());
            projectVO.setImages(imageVOList);
        }

        return projectVO;
    }

    @Override
    public List<ProjectVO> getProjectVOList(List<Project> projectList) {
        if (CollUtil.isEmpty(projectList)) {
            return new ArrayList<>();
        }

        // 批量获取作者信息
        Set<Long> authorIds = projectList.stream()
                .map(Project::getAuthorId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));

        // 批量获取分类信息
        Set<Long> categoryIds = projectList.stream()
                .map(Project::getCategoryId)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNameMap = projectCategoryService.listByIds(categoryIds).stream()
                .collect(Collectors.toMap(ProjectCategory::getId, ProjectCategory::getName));

        return projectList.stream().map(project -> {
            ProjectVO vo = new ProjectVO();
            BeanUtil.copyProperties(project, vo);
            // 作者
            UserVO userVO = userVOMap.get(project.getAuthorId());
            vo.setAuthor(userVO);
            // 分类名
            vo.setCategoryName(categoryNameMap.getOrDefault(project.getCategoryId(), ""));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(ProjectQueryRequest queryRequest) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        Long id = queryRequest.getId();
        String name = queryRequest.getName();
        Long categoryId = queryRequest.getCategoryId();
        String techTag = queryRequest.getTechTag();
        Integer status = queryRequest.getStatus();
        Long authorId = queryRequest.getAuthorId();
        BigDecimal minPrice = queryRequest.getMinPrice();
        BigDecimal maxPrice = queryRequest.getMaxPrice();
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();

        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", id)
                .like("name", name)
                .eq("categoryId", categoryId)
                .eq("status", status)
                .eq("authorId", authorId);

        // 自定义标签模糊搜索
        if (StrUtil.isNotBlank(techTag)) {
            wrapper.like("techTags", techTag);
        }

        // 价格区间
        if (minPrice != null) {
            wrapper.ge("price", minPrice);
        }
        if (maxPrice != null) {
            wrapper.le("price", maxPrice);
        }

        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            wrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            wrapper.orderBy("createTime", false);
        }

        return wrapper;
    }

    @Override
    public void incrementViewCount(Long projectId) {
        Project project = this.getById(projectId);
        if (project == null) {
            return;
        }
        Project update = new Project();
        update.setId(projectId);
        update.setViewCount(project.getViewCount() + 1);
        this.updateById(update);
    }
}
