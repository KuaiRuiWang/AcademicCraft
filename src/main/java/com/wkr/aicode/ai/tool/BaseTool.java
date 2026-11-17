package com.wkr.aicode.ai.tool;

import cn.hutool.json.JSONObject;
import com.wkr.aicode.ai.message.ToolResult;
import org.springframework.stereotype.Component;

/**
 * 工具基类
 * 定义所有工具的通用接口
 */

public abstract class BaseTool {

    protected String success(String summary, String content) {
        return new ToolResult("success", summary, content).toJson();
    }

    protected String error(String summary) {
        return new ToolResult("error", summary, null).toJson();
    }

    protected String unchanged(String summary) {
        return new ToolResult("unchanged", summary, null).toJson();
    }

    /**
     * 智能体工作区
     * 为空时表示未绑定工作区，工具按 appId 回退到 Vue 工程模式产物目录（兼容旧逻辑）
     */
    private AgentWorkspace workspace;

    /**
     * 绑定工作区，仅供 {@link ToolSetFactory} 创建 Agent 专属工具实例时调用
     * 注意：Spring 单例工具被多应用共享，禁止对其调用本方法（并发不安全）
     */
    public void setWorkspace(AgentWorkspace workspace) {
        this.workspace = workspace;
    }

    /**
     * 获取当前生效的工作区：优先使用绑定的工作区，否则回退到 Vue 工程模式产物目录
     */
    protected AgentWorkspace resolveWorkspace(Long appId) {
        return workspace != null ? workspace : AgentWorkspace.vueProject(appId);
    }

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    public abstract String getToolName();

    /**
     * 获取工具的中文显示名称
     *
     * @return 工具中文名称
     */
    public abstract String getDisplayName();

    /**
     * 生成工具请求时的返回值（显示给用户）
     *
     * @return 工具请求显示内容
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 生成工具执行结果格式（保存到数据库）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);
}
