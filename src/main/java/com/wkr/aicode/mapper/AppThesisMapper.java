package com.wkr.aicode.mapper;

import com.mybatisflex.core.BaseMapper;
import com.wkr.aicode.model.entity.AppThesis;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AppThesisMapper extends BaseMapper<AppThesis> {
    @Select("SELECT * FROM app_thesis WHERE appId = #{appId} FOR UPDATE")
    AppThesis selectForUpdate(@Param("appId") Long appId);
}
