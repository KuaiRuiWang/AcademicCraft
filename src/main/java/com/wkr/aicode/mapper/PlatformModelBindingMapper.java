package com.wkr.aicode.mapper;

import com.mybatisflex.core.BaseMapper;
import com.wkr.aicode.model.entity.PlatformModelBinding;
import com.wkr.aicode.model.entity.PlatformModel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PlatformModelBindingMapper extends BaseMapper<PlatformModelBinding> {
    @Insert("INSERT INTO platform_model_binding (stepKey, modelConfigId) VALUES (#{stepKey}, #{modelId}) "
            + "ON DUPLICATE KEY UPDATE modelConfigId = #{modelId}")
    void bind(@Param("stepKey") String stepKey, @Param("modelId") long modelId);

    @Select("SELECT m.* FROM platform_model_binding b JOIN platform_model m ON m.id = b.modelConfigId WHERE b.stepKey = #{stepKey}")
    PlatformModel findModel(@Param("stepKey") String stepKey);
}
