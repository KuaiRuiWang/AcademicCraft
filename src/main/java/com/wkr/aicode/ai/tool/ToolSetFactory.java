package com.wkr.aicode.ai.tool;

import com.wkr.aicode.model.enums.AgentRoleEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 专属工具集工厂
 * 每个 Agent 构建时创建独立的工具实例，工作区（产物根目录 + 角色）绑定为实例字段，
 * 避免并行 Agent 相互越权写入，也避免 ThreadLocal 在工具执行线程中丢失上下文
 *
 * @author <a>百夜</a>
 */
@Slf4j
@Component
public class ToolSetFactory {

    /**
     * 创建指定角色的工具集
     *
     * @param projectDir 产物根目录（如 tmp/code_output/fullstack_project_{appId}）
     * @param role       Agent 角色
     * @return 工具实例列表
     */
    public List<Object> createFullstackTools(String projectDir, AgentRoleEnum role) {
        AgentWorkspace workspace = new AgentWorkspace(projectDir, role);
        List<Object> tools = List.of(
                withWorkspace(new FileWriteTool(), workspace),
                withWorkspace(new FileModifyTool(), workspace),
                withWorkspace(new FileReadTool(), workspace),
                withWorkspace(new FileDeleteTool(), workspace),
                withWorkspace(new FileDirReadTool(), workspace),
                new ExitTool()
        );
        log.info("已为角色 {} 创建工作区工具集，根目录：{}", role.getValue(), workspace.getProjectRoot());
        return tools;
    }

    private BaseTool withWorkspace(BaseTool tool, AgentWorkspace workspace) {
        tool.setWorkspace(workspace);
        return tool;
    }
}
