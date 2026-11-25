package com.wkr.aicode.controller;

import com.wkr.aicode.annotation.AuthCheck;
import com.wkr.aicode.common.BaseResponse;
import com.wkr.aicode.common.ResultUtils;
import com.wkr.aicode.core.model.ModelStep;
import com.wkr.aicode.service.PlatformModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.wkr.aicode.admin.AdminPermission.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class PlatformModelController {
    private final PlatformModelService models;

    @GetMapping("/models")
    @AuthCheck(MODEL_READ)
    public BaseResponse<List<PlatformModelService.ModelView>> list() {
        return ResultUtils.success(models.list());
    }

    @PostMapping("/models")
    @AuthCheck(MODEL_WRITE)
    public BaseResponse<PlatformModelService.ModelView> create(@RequestBody PlatformModelService.Input input) {
        return ResultUtils.success(models.save(null, input));
    }

    @PutMapping("/models/{id}")
    @AuthCheck(MODEL_WRITE)
    public BaseResponse<PlatformModelService.ModelView> update(@PathVariable long id, @RequestBody PlatformModelService.Input input) {
        return ResultUtils.success(models.save(id, input));
    }

    @DeleteMapping("/models/{id}")
    @AuthCheck(MODEL_WRITE)
    public BaseResponse<Boolean> delete(@PathVariable long id) {
        models.delete(id);
        return ResultUtils.success(true);
    }

    @GetMapping("/model-bindings")
    @AuthCheck(BINDING_READ)
    public BaseResponse<PlatformModelService.BindingsView> listBindings() {
        return ResultUtils.success(models.listBindings());
    }

    @PutMapping("/model-bindings/{step}")
    @AuthCheck(BINDING_WRITE)
    public BaseResponse<Boolean> bind(@PathVariable ModelStep step, @RequestBody BindingInput input) {
        models.bind(step, input.modelConfigId());
        return ResultUtils.success(true);
    }

    public record BindingInput(Long modelConfigId) { }
}
