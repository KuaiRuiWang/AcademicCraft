package com.wkr.aicode.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.wkr.aicode.model.dto.app.AppAddRequest;
import com.wkr.aicode.model.dto.app.AppQueryRequest;
import com.wkr.aicode.model.entity.App;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.vo.app.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;


/**
 * 应用 服务层。
 *
 * @author <a>百夜</a>
 */
public interface AppService extends IService<App> {

    Long createApp(AppAddRequest appAddRequest, User loginUser);

    void generateAppScreenshotAsync(Long appId, String appUrl);

    AppVO getAppVO(App app);

    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    List<AppVO> getAppVOList(List<App> appList);

    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    Flux<Map<String, Object>> chatToGenCodeEvents(Long appId, String message, String runId, User loginUser);

    String deployApp(Long appId, User loginUser);

    /**
     * 停止应用容器（docker compose down，保留数据卷）
     */
    void stopAppContainer(Long appId, User loginUser);

    /**
     * 重启应用容器（docker compose up -d，复用镜像与数据卷）
     */
    void restartAppContainer(Long appId, User loginUser);

    /**
     * 删除应用容器：容器/数据卷/镜像/反代配置/端口全部清理
     */
    void deleteAppContainer(Long appId, User loginUser);

}
