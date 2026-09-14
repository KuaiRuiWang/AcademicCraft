package com.wkr.aicode.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 前后端共用的 API 契约
 * 由架构师 Agent 依据用户需求产出，前端 / 后端 Agent 严格遵循
 *
 * @author <a>百夜</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiContract implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 数据模型清单（对应 MySQL 表结构）
     */
    private List<ApiModel> models;

    /**
     * 接口清单
     */
    private List<ApiEndpoint> apis;

    /**
     * 统一响应格式
     */
    private ResponseFormat responseFormat;

    /**
     * 数据模型
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiModel implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 表名
         */
        private String table;

        /**
         * 字段清单
         */
        private List<Field> fields;

        /**
         * 字段定义
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Field implements Serializable {

            @Serial
            private static final long serialVersionUID = 1L;

            /**
             * 字段名
             */
            private String name;

            /**
             * MySQL 字段类型，如 varchar(200)、int、datetime
             */
            private String type;

            /**
             * 是否主键
             */
            private Boolean pk;
        }
    }

    /**
     * 接口定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiEndpoint implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * HTTP 方法：GET / POST / PUT / DELETE
         */
        private String method;

        /**
         * 接口路径，以 /api 开头，路径参数用 :id 形式
         */
        private String path;

        /**
         * 请求体类型描述
         */
        private String request;

        /**
         * 响应体类型描述
         */
        private String response;

        /**
         * 接口说明
         */
        private String description;
    }

    /**
     * 统一响应格式
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseFormat implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 业务状态码，0 表示成功
         */
        private Integer code;

        /**
         * 数据载荷类型描述
         */
        private String data;

        /**
         * 提示信息类型描述
         */
        private String message;
    }
}
