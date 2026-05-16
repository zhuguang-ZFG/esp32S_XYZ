package xiaozhi.modules.appv2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.common.utils.Result;
import xiaozhi.modules.appv2.dto.V2DeletionResponse;
import xiaozhi.modules.appv2.dto.V2DeviceRmaRequest;
import xiaozhi.modules.appv2.dto.V2DeviceRmaResponse;
import xiaozhi.modules.appv2.dto.V2DeviceSupplyResponse;
import xiaozhi.modules.appv2.dto.V2DeviceSupplyUpdateRequest;
import xiaozhi.modules.appv2.dto.V2DeviceTransferRequest;
import xiaozhi.modules.appv2.dto.V2DeviceTransferResponse;
import xiaozhi.modules.appv2.dto.V2MemberCreateRequest;
import xiaozhi.modules.appv2.dto.V2MemberResponse;
import xiaozhi.modules.appv2.dto.V2PendingVoiceTaskResponse;
import xiaozhi.modules.appv2.dto.V2SelfCheckHistoryResponse;
import xiaozhi.modules.appv2.dto.V2SubmitTaskResponse;
import xiaozhi.modules.appv2.dto.V2TaskApprovalRequest;
import xiaozhi.modules.appv2.entity.V2DeviceRmaEventEntity;
import xiaozhi.modules.appv2.service.AppV2Service;
import xiaozhi.modules.appv2.service.DeviceRmaService;
import xiaozhi.modules.appv2.service.DeviceSupplyService;
import xiaozhi.modules.appv2.service.DeviceTransferService;
import xiaozhi.modules.appv2.service.MemberService;
import xiaozhi.modules.appv2.service.PrivacyDeletionService;
import xiaozhi.modules.appv2.service.VoiceprintEnrollmentService;

@ExtendWith(MockitoExtension.class)
class AppV2ControllerTest {
    @Mock
    private AppV2Service appV2Service;
    @Mock
    private VoiceprintEnrollmentService voiceprintEnrollmentService;
    @Mock
    private MemberService memberService;
    @Mock
    private PrivacyDeletionService privacyDeletionService;
    @Mock
    private DeviceTransferService deviceTransferService;
    @Mock
    private DeviceSupplyService deviceSupplyService;
    @Mock
    private DeviceRmaService deviceRmaService;

    @Test
    void createMemberDelegatesToMemberService() {
        AppV2Controller controller = controller();
        V2MemberCreateRequest request = new V2MemberCreateRequest();
        request.setDeviceId("dev-1");
        request.setDisplayName("Child");
        request.setMemberType("child");
        V2MemberResponse member = new V2MemberResponse(202L, "dev-1", "Child", "child", "child", "active");
        when(memberService.create(request)).thenReturn(member);

        Result<V2MemberResponse> response = controller.createMember(request);

        assertEquals(0, response.getCode());
        assertEquals(202L, response.getData().getMemberId());
        assertEquals("child", response.getData().getMemberType());
        verify(memberService).create(request);
    }

    @Test
    void listMembersDelegatesToMemberService() {
        AppV2Controller controller = controller();
        when(memberService.listByDevice("dev-1")).thenReturn(List.of(
                new V2MemberResponse(101L, "dev-1", "Parent", "owner", "owner", "active"),
                new V2MemberResponse(202L, "dev-1", "Child", "child", "child", "active")));

        Result<List<V2MemberResponse>> response = controller.listMembers("dev-1");

        assertEquals(0, response.getCode());
        assertEquals(2, response.getData().size());
        assertEquals("owner", response.getData().get(0).getMemberType());
        assertEquals("child", response.getData().get(1).getMemberType());
        verify(memberService).listByDevice("dev-1");
    }

    @Test
    void deleteVoiceprintDelegatesToPrivacyDeletionService() {
        AppV2Controller controller = controller();
        when(privacyDeletionService.deleteVoiceprint(201L))
                .thenReturn(new V2DeletionResponse("deleted", 1, 180));

        Result<V2DeletionResponse> response = controller.deleteVoiceprint(201L);

        assertEquals(0, response.getCode());
        assertEquals("deleted", response.getData().getStatus());
        assertEquals(180, response.getData().getAuditRetentionDays());
        verify(privacyDeletionService).deleteVoiceprint(201L);
    }

    @Test
    void deleteAccountDelegatesToPrivacyDeletionService() {
        AppV2Controller controller = controller();
        when(privacyDeletionService.deleteAccount())
                .thenReturn(new V2DeletionResponse("deleted", 4, 180));

        Result<V2DeletionResponse> response = controller.deleteAccount();

        assertEquals(0, response.getCode());
        assertEquals(4, response.getData().getAffectedRows());
        verify(privacyDeletionService).deleteAccount();
    }

    @Test
    void requestDeviceTransferDelegatesToTransferService() {
        AppV2Controller controller = controller();
        V2DeviceTransferRequest request = new V2DeviceTransferRequest();
        request.setTargetUnionid("target-union");
        when(deviceTransferService.requestTransfer("dev-1", request))
                .thenReturn(new V2DeviceTransferResponse(7L, "dev-1", 31L, 42L, "pending"));

        Result<V2DeviceTransferResponse> response = controller.requestDeviceTransfer("dev-1", request);

        assertEquals(0, response.getCode());
        assertEquals("pending", response.getData().getStatus());
        verify(deviceTransferService).requestTransfer("dev-1", request);
    }

    @Test
    void acceptDeviceTransferDelegatesToTransferService() {
        AppV2Controller controller = controller();
        when(deviceTransferService.acceptTransfer(7L))
                .thenReturn(new V2DeviceTransferResponse(7L, "dev-1", 31L, 42L, "accepted"));

        Result<V2DeviceTransferResponse> response = controller.acceptDeviceTransfer(7L);

        assertEquals(0, response.getCode());
        assertEquals("accepted", response.getData().getStatus());
        verify(deviceTransferService).acceptTransfer(7L);
    }

    @Test
    void cancelDeviceTransferDelegatesToTransferService() {
        AppV2Controller controller = controller();
        when(deviceTransferService.cancelTransfer(7L))
                .thenReturn(new V2DeviceTransferResponse(7L, "dev-1", 31L, 42L, "cancelled"));

        Result<V2DeviceTransferResponse> response = controller.cancelDeviceTransfer(7L);

        assertEquals(0, response.getCode());
        assertEquals("cancelled", response.getData().getStatus());
        verify(deviceTransferService).cancelTransfer(7L);
    }

    @Test
    void listPendingIncomingDeviceTransfersDelegatesToTransferService() {
        AppV2Controller controller = controller();
        when(deviceTransferService.listPendingIncomingTransfers())
                .thenReturn(List.of(new V2DeviceTransferResponse(7L, "dev-1", 31L, 42L, "pending")));

        Result<List<V2DeviceTransferResponse>> response = controller.listPendingIncomingDeviceTransfers();

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("pending", response.getData().get(0).getStatus());
        verify(deviceTransferService).listPendingIncomingTransfers();
    }

    @Test
    void updateDeviceSuppliesDelegatesToSupplyService() {
        AppV2Controller controller = controller();
        V2DeviceSupplyUpdateRequest request = new V2DeviceSupplyUpdateRequest();
        request.setPaperSlotState("loaded");
        when(deviceSupplyService.updateSupplies("dev-1", request))
                .thenReturn(new V2DeviceSupplyResponse("dev-1", "loaded", null, 80, java.math.BigDecimal.ZERO));

        Result<V2DeviceSupplyResponse> response = controller.updateDeviceSupplies("dev-1", request);

        assertEquals(0, response.getCode());
        assertEquals("loaded", response.getData().getPaperSlotState());
        verify(deviceSupplyService).updateSupplies("dev-1", request);
    }

    @Test
    void voiceTaskApprovalEndpointsDelegateToAppService() {
        AppV2Controller controller = controller();
        V2TaskApprovalRequest request = new V2TaskApprovalRequest();
        request.setReason("ok");
        when(appV2Service.approveVoiceTask("task-1", request))
                .thenReturn(new V2SubmitTaskResponse("task-1", "accepted"));
        when(appV2Service.rejectVoiceTask("task-2", request))
                .thenReturn(new V2SubmitTaskResponse("task-2", "rejected"));

        assertEquals("accepted", controller.approveVoiceTask("task-1", request).getData().getStatus());
        assertEquals("rejected", controller.rejectVoiceTask("task-2", request).getData().getStatus());
        verify(appV2Service).approveVoiceTask("task-1", request);
        verify(appV2Service).rejectVoiceTask("task-2", request);
    }

    @Test
    void listPendingVoiceTasksDelegatesToAppService() {
        AppV2Controller controller = controller();
        when(appV2Service.listPendingVoiceTasks("dev-1"))
                .thenReturn(List.of(new V2PendingVoiceTaskResponse(
                        "task-1", "dev-1", "voice-req-1", "write_text",
                        "{\"text\":\"hello\"}", null, "pending_primary_approval", null)));

        Result<List<V2PendingVoiceTaskResponse>> response = controller.listPendingVoiceTasks("dev-1");

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("pending_primary_approval", response.getData().get(0).getStatus());
        verify(appV2Service).listPendingVoiceTasks("dev-1");
    }

    @Test
    void listSelfCheckHistoryDelegatesToAppService() {
        AppV2Controller controller = controller();
        when(appV2Service.listSelfCheckHistory("dev-1"))
                .thenReturn(List.of(new V2SelfCheckHistoryResponse(
                        9L, "dev-1", "startup", "startup", "pass", "nvs:pass", "{}", null)));

        Result<List<V2SelfCheckHistoryResponse>> response = controller.listSelfCheckHistory("dev-1");

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("pass", response.getData().get(0).getStatus());
        verify(appV2Service).listSelfCheckHistory("dev-1");
    }

    @Test
    void deviceRmaEndpointsDelegateToRmaService() {
        AppV2Controller controller = controller();
        V2DeviceRmaRequest request = new V2DeviceRmaRequest();
        request.setActivationCode("ACT-NEW");
        when(deviceRmaService.startRepair("dev-1", request))
                .thenReturn(new V2DeviceRmaResponse("dev-1", 31L, "rma_in_progress", "rma_in_progress", null));
        when(deviceRmaService.completeRepair("dev-1", request))
                .thenReturn(new V2DeviceRmaResponse("dev-1", 31L, "bound", "active", null));
        when(deviceRmaService.confirmReturn("dev-1", request))
                .thenReturn(new V2DeviceRmaResponse("dev-1", 31L, "returned", "returned", null));
        when(deviceRmaService.restockReturned("dev-1", request))
                .thenReturn(new V2DeviceRmaResponse("dev-1", 31L, "provisioned_unbound", "returned", "ACT-NEW"));
        when(deviceRmaService.disposeDevice("dev-1", request))
                .thenReturn(new V2DeviceRmaResponse("dev-1", 31L, "disposed", "disposed", null));
        V2DeviceRmaEventEntity event = new V2DeviceRmaEventEntity();
        event.setDeviceId("dev-1");
        event.setAction("dispose");
        event.setTicketRef("RMA-1001");
        when(deviceRmaService.listAuditEvents("dev-1")).thenReturn(List.of(event));

        assertEquals("rma_in_progress", controller.startDeviceRepair("dev-1", request).getData().getDeviceStatus());
        assertEquals("bound", controller.completeDeviceRepair("dev-1", request).getData().getDeviceStatus());
        assertEquals("returned", controller.confirmDeviceReturn("dev-1", request).getData().getDeviceStatus());
        assertEquals("ACT-NEW", controller.restockReturnedDevice("dev-1", request).getData().getActivationCode());
        assertEquals("disposed", controller.disposeDevice("dev-1", request).getData().getDeviceStatus());
        assertEquals("RMA-1001", controller.listDeviceRmaEvents("dev-1").getData().get(0).getTicketRef());
        verify(deviceRmaService).startRepair("dev-1", request);
        verify(deviceRmaService).completeRepair("dev-1", request);
        verify(deviceRmaService).confirmReturn("dev-1", request);
        verify(deviceRmaService).restockReturned("dev-1", request);
        verify(deviceRmaService).disposeDevice("dev-1", request);
        verify(deviceRmaService).listAuditEvents("dev-1");
    }

    @Test
    void deviceRmaEndpointsAllowSuperAdminOrRmaOperatorPermission() throws Exception {
        for (String methodName : List.of(
                "startDeviceRepair",
                "completeDeviceRepair",
                "confirmDeviceReturn",
                "restockReturnedDevice",
                "disposeDevice")) {
            Method method = AppV2Controller.class.getMethod(methodName, String.class, V2DeviceRmaRequest.class);
            RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);
            assertEquals(Logical.OR, permissions.logical());
            List<String> values = Arrays.asList(permissions.value());
            org.junit.jupiter.api.Assertions.assertTrue(values.contains("sys:role:superAdmin"));
            org.junit.jupiter.api.Assertions.assertTrue(values.contains("appv2:device:rma"));
        }
        Method eventMethod = AppV2Controller.class.getMethod("listDeviceRmaEvents", String.class);
        RequiresPermissions eventPermissions = eventMethod.getAnnotation(RequiresPermissions.class);
        assertEquals(Logical.OR, eventPermissions.logical());
        List<String> eventValues = Arrays.asList(eventPermissions.value());
        org.junit.jupiter.api.Assertions.assertTrue(eventValues.contains("sys:role:superAdmin"));
        org.junit.jupiter.api.Assertions.assertTrue(eventValues.contains("appv2:device:rma"));
    }

    private AppV2Controller controller() {
        return new AppV2Controller(appV2Service, voiceprintEnrollmentService, memberService, privacyDeletionService,
                deviceTransferService, deviceSupplyService, deviceRmaService);
    }
}
