package xiaozhi.modules.appv2.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.Logical;
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
import xiaozhi.modules.appv2.dto.V2BindDeviceRequest;
import xiaozhi.modules.appv2.dto.V2BindDeviceResponse;
import xiaozhi.modules.appv2.dto.V2DeletionResponse;
import xiaozhi.modules.appv2.dto.V2DeviceRmaRequest;
import xiaozhi.modules.appv2.dto.V2DeviceRmaResponse;
import xiaozhi.modules.appv2.dto.V2DeviceSupplyResponse;
import xiaozhi.modules.appv2.dto.V2DeviceSupplyUpdateRequest;
import xiaozhi.modules.appv2.dto.V2DeviceTransferRequest;
import xiaozhi.modules.appv2.dto.V2DeviceTransferResponse;
import xiaozhi.modules.appv2.dto.V2LoginRequest;
import xiaozhi.modules.appv2.dto.V2LoginResponse;
import xiaozhi.modules.appv2.dto.V2MemberCreateRequest;
import xiaozhi.modules.appv2.dto.V2MemberResponse;
import xiaozhi.modules.appv2.dto.V2PendingVoiceTaskResponse;
import xiaozhi.modules.appv2.dto.V2SelfCheckHistoryResponse;
import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.dto.V2SubmitTaskResponse;
import xiaozhi.modules.appv2.dto.V2TaskApprovalRequest;
import xiaozhi.modules.appv2.dto.V2VoiceprintEnrollRequest;
import xiaozhi.modules.appv2.dto.V2VoiceprintEnrollResponse;
import xiaozhi.modules.appv2.entity.V2DeviceRmaEventEntity;
import xiaozhi.modules.appv2.service.AppV2Service;
import xiaozhi.modules.appv2.service.DeviceRmaService;
import xiaozhi.modules.appv2.service.DeviceSupplyService;
import xiaozhi.modules.appv2.service.DeviceTransferService;
import xiaozhi.modules.appv2.service.MemberService;
import xiaozhi.modules.appv2.service.PrivacyDeletionService;
import xiaozhi.modules.appv2.service.VoiceprintEnrollmentService;

@Validated
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1")
@Tag(name = "v2 client api")
public class AppV2Controller {
    private final AppV2Service appV2Service;
    private final VoiceprintEnrollmentService voiceprintEnrollmentService;
    private final MemberService memberService;
    private final PrivacyDeletionService privacyDeletionService;
    private final DeviceTransferService deviceTransferService;
    private final DeviceSupplyService deviceSupplyService;
    private final DeviceRmaService deviceRmaService;
    private static final String PERMISSION_SUPER_ADMIN = "sys:role:superAdmin";
    private static final String PERMISSION_RMA_OPERATOR = "appv2:device:rma";

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

    @PostMapping("/tasks/{taskId}/approve")
    @Operation(summary = "Primary session approves a pending voice task")
    public Result<V2SubmitTaskResponse> approveVoiceTask(@PathVariable String taskId,
            @RequestBody(required = false) V2TaskApprovalRequest request) {
        return new Result<V2SubmitTaskResponse>().ok(appV2Service.approveVoiceTask(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/reject")
    @Operation(summary = "Primary session rejects a pending voice task")
    public Result<V2SubmitTaskResponse> rejectVoiceTask(@PathVariable String taskId,
            @RequestBody(required = false) V2TaskApprovalRequest request) {
        return new Result<V2SubmitTaskResponse>().ok(appV2Service.rejectVoiceTask(taskId, request));
    }

    @PostMapping("/devices/{deviceId}/voice-tasks/pending")
    @Operation(summary = "List pending voice tasks waiting for primary approval")
    public Result<List<V2PendingVoiceTaskResponse>> listPendingVoiceTasks(@PathVariable String deviceId) {
        return new Result<List<V2PendingVoiceTaskResponse>>().ok(appV2Service.listPendingVoiceTasks(deviceId));
    }

    @PostMapping("/devices/{deviceId}/self-check/history")
    @Operation(summary = "List recent self-check diagnostic history for a bound device")
    public Result<List<V2SelfCheckHistoryResponse>> listSelfCheckHistory(@PathVariable String deviceId) {
        return new Result<List<V2SelfCheckHistoryResponse>>().ok(appV2Service.listSelfCheckHistory(deviceId));
    }

    @PostMapping("/voiceprints/enroll")
    @Operation(summary = "Enroll a minimal v2 voiceprint")
    public Result<V2VoiceprintEnrollResponse> enrollVoiceprint(
            @Validated @RequestBody V2VoiceprintEnrollRequest request) {
        return new Result<V2VoiceprintEnrollResponse>().ok(voiceprintEnrollmentService.enroll(request));
    }

    @PostMapping("/members")
    @Operation(summary = "Create a minimal v2 family member")
    public Result<V2MemberResponse> createMember(@Validated @RequestBody V2MemberCreateRequest request) {
        return new Result<V2MemberResponse>().ok(memberService.create(request));
    }

    @PostMapping("/devices/{deviceId}/members/list")
    @Operation(summary = "List active v2 family members for a bound device")
    public Result<List<V2MemberResponse>> listMembers(@PathVariable String deviceId) {
        return new Result<List<V2MemberResponse>>().ok(memberService.listByDevice(deviceId));
    }

    @PostMapping("/voiceprints/{voiceprintId}/delete")
    @Operation(summary = "Soft-delete a v2 voiceprint and anonymize matching material")
    public Result<V2DeletionResponse> deleteVoiceprint(@PathVariable Long voiceprintId) {
        return new Result<V2DeletionResponse>().ok(privacyDeletionService.deleteVoiceprint(voiceprintId));
    }

    @PostMapping("/account/delete")
    @Operation(summary = "Soft-delete the current v2 account")
    public Result<V2DeletionResponse> deleteAccount() {
        return new Result<V2DeletionResponse>().ok(privacyDeletionService.deleteAccount());
    }

    @PostMapping("/devices/{deviceId}/transfer")
    @Operation(summary = "Request a v2 device transfer to another account")
    public Result<V2DeviceTransferResponse> requestDeviceTransfer(@PathVariable String deviceId,
            @Validated @RequestBody V2DeviceTransferRequest request) {
        return new Result<V2DeviceTransferResponse>().ok(deviceTransferService.requestTransfer(deviceId, request));
    }

    @PostMapping("/device-transfers/{transferId}/accept")
    @Operation(summary = "Accept a pending v2 device transfer")
    public Result<V2DeviceTransferResponse> acceptDeviceTransfer(@PathVariable Long transferId) {
        return new Result<V2DeviceTransferResponse>().ok(deviceTransferService.acceptTransfer(transferId));
    }

    @PostMapping("/device-transfers/{transferId}/cancel")
    @Operation(summary = "Cancel a pending v2 device transfer")
    public Result<V2DeviceTransferResponse> cancelDeviceTransfer(@PathVariable Long transferId) {
        return new Result<V2DeviceTransferResponse>().ok(deviceTransferService.cancelTransfer(transferId));
    }

    @PostMapping("/device-transfers/pending-incoming")
    @Operation(summary = "List pending v2 device transfers waiting for the current account")
    public Result<List<V2DeviceTransferResponse>> listPendingIncomingDeviceTransfers() {
        return new Result<List<V2DeviceTransferResponse>>().ok(deviceTransferService.listPendingIncomingTransfers());
    }

    @PostMapping("/devices/{deviceId}/supplies")
    @Operation(summary = "Update manually maintained v2 device consumable state")
    public Result<V2DeviceSupplyResponse> updateDeviceSupplies(@PathVariable String deviceId,
            @Validated @RequestBody V2DeviceSupplyUpdateRequest request) {
        return new Result<V2DeviceSupplyResponse>().ok(deviceSupplyService.updateSupplies(deviceId, request));
    }

    @PostMapping("/devices/{deviceId}/rma/start")
    @RequiresPermissions(value = {PERMISSION_SUPER_ADMIN, PERMISSION_RMA_OPERATOR}, logical = Logical.OR)
    @Operation(summary = "Start a minimal v2 device repair RMA flow")
    public Result<V2DeviceRmaResponse> startDeviceRepair(@PathVariable String deviceId,
            @RequestBody(required = false) V2DeviceRmaRequest request) {
        return new Result<V2DeviceRmaResponse>().ok(deviceRmaService.startRepair(deviceId, request));
    }

    @PostMapping("/devices/{deviceId}/rma/complete")
    @RequiresPermissions(value = {PERMISSION_SUPER_ADMIN, PERMISSION_RMA_OPERATOR}, logical = Logical.OR)
    @Operation(summary = "Complete a minimal v2 device repair RMA flow")
    public Result<V2DeviceRmaResponse> completeDeviceRepair(@PathVariable String deviceId,
            @RequestBody(required = false) V2DeviceRmaRequest request) {
        return new Result<V2DeviceRmaResponse>().ok(deviceRmaService.completeRepair(deviceId, request));
    }

    @PostMapping("/devices/{deviceId}/return")
    @RequiresPermissions(value = {PERMISSION_SUPER_ADMIN, PERMISSION_RMA_OPERATOR}, logical = Logical.OR)
    @Operation(summary = "Confirm a minimal v2 device return")
    public Result<V2DeviceRmaResponse> confirmDeviceReturn(@PathVariable String deviceId,
            @RequestBody(required = false) V2DeviceRmaRequest request) {
        return new Result<V2DeviceRmaResponse>().ok(deviceRmaService.confirmReturn(deviceId, request));
    }

    @PostMapping("/devices/{deviceId}/restock")
    @RequiresPermissions(value = {PERMISSION_SUPER_ADMIN, PERMISSION_RMA_OPERATOR}, logical = Logical.OR)
    @Operation(summary = "Restock a returned v2 device with a fresh activation code")
    public Result<V2DeviceRmaResponse> restockReturnedDevice(@PathVariable String deviceId,
            @RequestBody(required = false) V2DeviceRmaRequest request) {
        return new Result<V2DeviceRmaResponse>().ok(deviceRmaService.restockReturned(deviceId, request));
    }

    @PostMapping("/devices/{deviceId}/dispose")
    @RequiresPermissions(value = {PERMISSION_SUPER_ADMIN, PERMISSION_RMA_OPERATOR}, logical = Logical.OR)
    @Operation(summary = "Mark a v2 device as disposed")
    public Result<V2DeviceRmaResponse> disposeDevice(@PathVariable String deviceId,
            @RequestBody(required = false) V2DeviceRmaRequest request) {
        return new Result<V2DeviceRmaResponse>().ok(deviceRmaService.disposeDevice(deviceId, request));
    }

    @PostMapping("/devices/{deviceId}/rma/events")
    @RequiresPermissions(value = {PERMISSION_SUPER_ADMIN, PERMISSION_RMA_OPERATOR}, logical = Logical.OR)
    @Operation(summary = "List local v2 device RMA audit events for evidence export")
    public Result<List<V2DeviceRmaEventEntity>> listDeviceRmaEvents(@PathVariable String deviceId) {
        return new Result<List<V2DeviceRmaEventEntity>>().ok(deviceRmaService.listAuditEvents(deviceId));
    }
}
