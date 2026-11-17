package com.wkr.aicode.ai.message;

import cn.hutool.json.JSONUtil;

/**
 * 工具给模型和界面共用的执行结果，避免把“已返回”误当作“执行成功”。
 */
public record ToolResult(String status, String summary, String content) {

    public String toJson() {
        return JSONUtil.createObj().set("status", status).set("summary", summary).set("content", content).toString();
    }

    public static ToolResult parse(String result) {
        if (result != null && JSONUtil.isTypeJSONObject(result)) {
            var object = JSONUtil.parseObj(result);
            String status = object.getStr("status");
            if ("success".equals(status) || "error".equals(status) || "unchanged".equals(status)) {
                return new ToolResult(status, object.getStr("summary", ""), object.getStr("content"));
            }
        }
        return new ToolResult("unknown", "工具已返回结果", result);
    }
}
