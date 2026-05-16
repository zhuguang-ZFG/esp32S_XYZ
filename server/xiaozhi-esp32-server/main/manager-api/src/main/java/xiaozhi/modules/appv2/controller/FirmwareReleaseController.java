package xiaozhi.modules.appv2.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
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
import xiaozhi.modules.appv2.dto.V2FirmwareInstallResultRequest;
import xiaozhi.modules.appv2.dto.V2FirmwareReleasePublishRequest;
import xiaozhi.modules.appv2.dto.V2FirmwareReleaseResponse;
import xiaozhi.modules.appv2.service.firmware.FirmwareReleaseService;

@Validated
@AllArgsConstructor
@RestController
@RequestMapping("/admin/firmware-releases")
@Tag(name = "M5 firmware release management")
public class FirmwareReleaseController {
    private final FirmwareReleaseService firmwareReleaseService;

    @PostMapping
    @RequiresPermissions("sys:role:superAdmin")
    @Operation(summary = "Publish a design-time dev firmware release")
    public Result<V2FirmwareReleaseResponse> publishFirmwareRelease(
            @Validated @RequestBody V2FirmwareReleasePublishRequest request) {
        return new Result<V2FirmwareReleaseResponse>().ok(V2FirmwareReleaseResponse.fromEntity(
                firmwareReleaseService.publishDevRelease(
                        request.getReleaseId(),
                        request.getVersion(),
                        request.getUrl(),
                        request.getSha256(),
                        request.getSignature(),
                        request.getRolloutPercent(),
                        request.getFailureThresholdPercent())));
    }

    @PostMapping("/{releaseId}/install-result")
    @RequiresPermissions("sys:role:superAdmin")
    @Operation(summary = "Record a firmware install result for rollout safety")
    public Result<V2FirmwareReleaseResponse> recordFirmwareInstallResult(@PathVariable String releaseId,
            @Validated @RequestBody V2FirmwareInstallResultRequest request) {
        return new Result<V2FirmwareReleaseResponse>().ok(V2FirmwareReleaseResponse.fromEntity(
                firmwareReleaseService.recordInstallResult(releaseId, Boolean.TRUE.equals(request.getSuccess()))));
    }
}
