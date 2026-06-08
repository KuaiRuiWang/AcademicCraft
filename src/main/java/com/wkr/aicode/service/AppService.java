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

    String deployApp(Long appId, User loginUser);

}
