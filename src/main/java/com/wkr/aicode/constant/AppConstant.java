package com.wkr.aicode.constant;

public interface AppConstant {

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost";

    /**
     * 全栈应用产物目录名前缀
     */
    String FULLSTACK_PROJECT_DIR_PREFIX = "fullstack_project_";

    /**
     * 全栈应用产物根目录，如 tmp/code_output/fullstack_project_123
     *
     * @param appId 应用 ID
     * @return 产物根目录路径
     */
    static String fullstackProjectDir(Long appId) {
        return CODE_OUTPUT_ROOT_DIR + "/" + FULLSTACK_PROJECT_DIR_PREFIX + appId;
    }
}
