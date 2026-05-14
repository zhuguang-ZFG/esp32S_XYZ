package xiaozhi.modules.appv2.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.appv2.dto.V2BindDeviceRequest;
import xiaozhi.modules.appv2.dto.V2BindDeviceResponse;
import xiaozhi.modules.appv2.dto.V2LoginRequest;
import xiaozhi.modules.appv2.dto.V2LoginResponse;
import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.dto.V2SubmitTaskResponse;
import xiaozhi.modules.appv2.service.AppV2Service;

@Validated
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1")
@Tag(name = "v2 client api")
public class AppV2Controller {
    private final AppV2Service appV2Service;

    @PostMapping("/login")
    @Operation(summary = "Minimal v2 login")
    public Result<V2LoginResponse> login(@Validated @RequestBody V2LoginRequest request) {
        return new Result<V2LoginResponse>().ok(appV2Service.login(request));
    }

    @PostMapping("/devices/bind")
    @Operation(summary = "Bind a device by serial number and activation code")
    public Result<V2BindDeviceResponse> bindDevice(@Validated @RequestBody V2BindDeviceRequest request) {
        return new Result<V2BindDeviceResponse>().ok(appV2Service.bindDevice(request));
    }

    @PostMapping("/devices/{deviceId}/tasks")
    @Operation(summary = "Submit a minimal motion task")
    public Result<V2SubmitTaskResponse> submitTask(@PathVariable String deviceId,
            @Validated @RequestBody V2SubmitTaskRequest request) {
        return new Result<V2SubmitTaskResponse>().ok(appV2Service.submitTask(deviceId, request));
    }
}
