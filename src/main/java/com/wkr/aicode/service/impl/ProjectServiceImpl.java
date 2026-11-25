package com.wkr.aicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
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
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public Long createProject(ProjectAddRequest addRequest, long adminId) {
        ThrowUtils.throwIf(StrUtil.isBlank(addRequest.getName()), ErrorCode.PARAMS_ERROR, "项目名称不能为空");
        ThrowUtils.throwIf(addRequest.getCategoryId() == null || addRequest.getCategoryId() <= 0,
                ErrorCode.PARAMS_ERROR, "项目分类不能为空");

        // 构造项目对象
        Project project = new Project();
        BeanUtil.copyProperties(addRequest, project);
        project.setPrice(ObjectUtil.defaultIfNull(project.getPrice(), BigDecimal.ZERO));
        validateTemplateForPublication(project);
        project.setAdminAuthorId(adminId);
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

        log.info("项目发布成功，ID: {}，管理员: {}", projectId, adminId);
        return projectId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProject(ProjectUpdateRequest updateRequest, User loginUser) {
        return updateProject(updateRequest, loginUser.getId(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProjectByAdmin(ProjectUpdateRequest updateRequest) {
        return updateProject(updateRequest, null, true);
    }

    private boolean updateProject(ProjectUpdateRequest updateRequest, Long authorId, boolean isAdmin) {
        ThrowUtils.throwIf(updateRequest.getId() == null || updateRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR, "项目ID不能为空");

        // 锁定项目，确保授权和上架状态对应同一版内容。
        Project oldProject = this.getOne(QueryWrapper.create().eq("id", updateRequest.getId()).forUpdate());
        ThrowUtils.throwIf(oldProject == null, ErrorCode.NOT_FOUND_ERROR, "项目不存在");
        // 管理员维护上架模板，原作者可处理自己的草稿和下架项目。
        if (!isAdmin && !Objects.equals(oldProject.getAuthorId(), authorId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限编辑该项目");
        }

        Integer status = ObjectUtil.defaultIfNull(updateRequest.getStatus(), oldProject.getStatus());
        ProjectStatusEnum nextStatus = ProjectStatusEnum.getEnumByValue(status);
        ThrowUtils.throwIf(nextStatus == null, ErrorCode.PARAMS_ERROR, "模板状态无效");
        if (nextStatus == ProjectStatusEnum.PUBLISHED) {
            ThrowUtils.throwIf(!isAdmin, ErrorCode.NO_AUTH_ERROR, "仅管理员可以维护上架模板");
            // 上下架入口会局部更新，按保存后的完整数据检查授权。
            Project publishedTemplate = BeanUtil.copyProperties(oldProject, Project.class);
            BeanUtil.copyProperties(updateRequest, publishedTemplate, CopyOptions.create().ignoreNullValue());
            validateTemplateForPublication(publishedTemplate);
        }

        // 更新项目字段
        Project project = new Project();
        BeanUtil.copyProperties(updateRequest, project);
        project.setEditTime(LocalDateTime.now());
        if (!isAdmin) {
            // 原作者改动后，管理员需要重新核对授权。
            project.setSourceAttribution("");
            project.setLicenseTerms("");
        }
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

    private void validateTemplateForPublication(Project template) {
        ThrowUtils.throwIf(template.getPrice() == null || template.getPrice().signum() != 0,
                ErrorCode.PARAMS_ERROR, "模板库仅开放免费模板，请在编辑页保存为免费模板后上架");
        ThrowUtils.throwIf(!isTemplateLink(template.getSourceUrl()), ErrorCode.PARAMS_ERROR,
                "请提供有效的源码下载地址");
        ThrowUtils.throwIf(StrUtil.isNotBlank(template.getDemoUrl()) && !isTemplateLink(template.getDemoUrl()),
                ErrorCode.PARAMS_ERROR, "请提供有效的演示地址");
        ThrowUtils.throwIf(StrUtil.isBlank(template.getSourceAttribution()) || template.getSourceAttribution().length() > 500,
                ErrorCode.PARAMS_ERROR, "请填写模板来源或授权方（最多 500 字）");
        ThrowUtils.throwIf(StrUtil.isBlank(template.getLicenseTerms()) || template.getLicenseTerms().length() > 1000,
                ErrorCode.PARAMS_ERROR, "请填写许可名称、商用和再分发条件（最多 1000 字）");
    }

    private boolean isTemplateLink(String link) {
        if (StrUtil.isBlank(link) || link.length() > 500) {
            return false;
        }
        try {
            String address = link.strip();
            URI uri = URI.create(address);
            if (uri.getScheme() == null) {
                return address.startsWith("/") && !address.startsWith("//");
            }
            return uri.getHost() != null
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException invalidUrl) {
            return false;
        }
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
        } else if (project.getAdminAuthorId() != null) {
            projectVO.setAuthor(platformAuthor());
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
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = authorIds.isEmpty() ? Map.of() : userService.listByIds(authorIds).stream()
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
            UserVO userVO = project.getAuthorId() != null ? userVOMap.get(project.getAuthorId())
                    : project.getAdminAuthorId() != null ? platformAuthor() : null;
            vo.setAuthor(userVO);
            // 分类名
            vo.setCategoryName(categoryNameMap.getOrDefault(project.getCategoryId(), ""));
            return vo;
        }).collect(Collectors.toList());
    }

    private UserVO platformAuthor() {
        UserVO author = new UserVO();
        author.setUserName("应用工坊");
        return author;
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
    public QueryWrapper getPublicTemplateQuery(ProjectQueryRequest queryRequest) {
        queryRequest.setStatus(ProjectStatusEnum.PUBLISHED.getValue());
        return getQueryWrapper(queryRequest)
                .eq("price", BigDecimal.ZERO)
                .and("TRIM(sourceUrl) <> '' AND TRIM(sourceAttribution) <> '' AND TRIM(licenseTerms) <> ''");
    }

    @Override
    public Project getPublicTemplate(Long projectId) {
        Project template = this.getById(projectId);
        ThrowUtils.throwIf(!isPublicTemplate(template), ErrorCode.NOT_FOUND_ERROR, "模板暂未开放");
        return template;
    }

    @Override
    public boolean isPublicTemplate(Project template) {
        return template != null
                && ProjectStatusEnum.getEnumByValue(template.getStatus()) == ProjectStatusEnum.PUBLISHED
                && template.getPrice() != null && template.getPrice().signum() == 0
                && StrUtil.isAllNotBlank(template.getSourceUrl(), template.getSourceAttribution(), template.getLicenseTerms());
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
