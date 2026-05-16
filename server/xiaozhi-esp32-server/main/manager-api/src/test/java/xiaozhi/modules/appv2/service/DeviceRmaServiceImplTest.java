package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.MessageUtils;
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2ActivationCodeDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2DeviceDao;
import xiaozhi.modules.appv2.dao.V2DeviceRmaEventDao;
import xiaozhi.modules.appv2.dto.V2DeviceRmaRequest;
import xiaozhi.modules.appv2.dto.V2DeviceRmaResponse;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2ActivationCodeEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2DeviceEntity;
import xiaozhi.modules.appv2.entity.V2DeviceRmaEventEntity;
import xiaozhi.modules.appv2.service.impl.DeviceRmaServiceImpl;
import xiaozhi.modules.security.user.SecurityUser;

@ExtendWith(MockitoExtension.class)
class DeviceRmaServiceImplTest {
    @Mock
    private V2DeviceDao v2DeviceDao;
    @Mock
    private V2DeviceBindingDao v2DeviceBindingDao;
    @Mock
    private V2ActivationCodeDao v2ActivationCodeDao;
    @Mock
    private V2DeviceRmaEventDao v2DeviceRmaEventDao;
    @Mock
    private V2AccountDao v2AccountDao;

    @Test
    void repairFlowMovesBoundDeviceOutAndBackWithoutDroppingAccountLink() {
        DeviceRmaServiceImpl service =
                new DeviceRmaServiceImpl(v2DeviceDao, v2DeviceBindingDao, v2ActivationCodeDao, v2DeviceRmaEventDao,
                        v2AccountDao);
        V2DeviceEntity device = device("bound");
        V2DeviceBindingEntity binding = binding("active");
        binding.setAccountId(88L);
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);

        UserDetail user = user();
        try (MockedStatic<SecurityUser> mockedSecurityUser = org.mockito.Mockito.mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            V2DeviceRmaRequest request = new V2DeviceRmaRequest();
            request.setTicketRef("RMA-1001");
            V2DeviceRmaResponse started = service.startRepair("dev-1", request);

            assertEquals("rma_in_progress", started.getDeviceStatus());
            assertEquals("rma_in_progress", started.getBindingStatus());
            assertEquals(88L, started.getAccountId());
            assertEquals(Boolean.FALSE, binding.getIsPrimary());

            device.setStatus("rma_in_progress");
            binding.setBindingStatus("rma_in_progress");
            V2DeviceRmaResponse completed = service.completeRepair("dev-1", new V2DeviceRmaRequest());

            assertEquals("bound", completed.getDeviceStatus());
            assertEquals("active", completed.getBindingStatus());
            assertEquals(Boolean.TRUE, binding.getIsPrimary());
            assertNull(binding.getUnboundAt());

            ArgumentCaptor<V2DeviceRmaEventEntity> eventCaptor =
                    ArgumentCaptor.forClass(V2DeviceRmaEventEntity.class);
            verify(v2DeviceRmaEventDao, times(2)).insert(eventCaptor.capture());
            V2DeviceRmaEventEntity startAudit = eventCaptor.getAllValues().get(0);
            assertEquals("repair_start", startAudit.getAction());
            assertEquals("dev-1", startAudit.getDeviceId());
            assertEquals("SN-001", startAudit.getDeviceSn());
            assertEquals(31L, startAudit.getOperatorAccountId());
            assertEquals("bound", startAudit.getFromDeviceStatus());
            assertEquals("rma_in_progress", startAudit.getToDeviceStatus());
            assertEquals("active", startAudit.getFromBindingStatus());
            assertEquals("rma_in_progress", startAudit.getToBindingStatus());
            assertEquals("RMA-1001", startAudit.getTicketRef());
            assertNotNull(startAudit.getCreatedAt());
            V2DeviceRmaEventEntity completeAudit = eventCaptor.getAllValues().get(1);
            assertEquals("repair_complete", completeAudit.getAction());
            assertEquals("rma_in_progress", completeAudit.getFromDeviceStatus());
            assertEquals("bound", completeAudit.getToDeviceStatus());
            assertEquals("rma_in_progress", completeAudit.getFromBindingStatus());
            assertEquals("active", completeAudit.getToBindingStatus());
        }
    }

    @Test
    void returnFlowUnbindsAndRestockRefreshesActivationWithoutTaskMigration() {
        DeviceRmaServiceImpl service =
                new DeviceRmaServiceImpl(v2DeviceDao, v2DeviceBindingDao, v2ActivationCodeDao, v2DeviceRmaEventDao,
                        v2AccountDao);
        V2DeviceEntity device = device("bound");
        V2DeviceBindingEntity binding = binding("active");
        V2ActivationCodeEntity activation = activation("bound", "ACT-OLD");
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2ActivationCodeDao.selectOne(any())).thenReturn(activation);

        UserDetail user = user();
        try (MockedStatic<SecurityUser> mockedSecurityUser = org.mockito.Mockito.mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            V2DeviceRmaResponse returned = service.confirmReturn("dev-1", new V2DeviceRmaRequest());

            assertEquals("returned", returned.getDeviceStatus());
            assertEquals("returned", returned.getBindingStatus());
            assertEquals("revoked", activation.getStatus());
            assertEquals(Boolean.FALSE, binding.getIsPrimary());

            V2DeviceRmaRequest restock = new V2DeviceRmaRequest();
            restock.setActivationCode("ACT-NEW");
            restock.setFactoryCleaned(Boolean.TRUE);
            restock.setEvidenceRef("RMA-1001");
            device.setStatus("returned");
            binding.setBindingStatus("returned");
            V2DeviceRmaResponse restocked = service.restockReturned("dev-1", restock);

            assertEquals("provisioned_unbound", restocked.getDeviceStatus());
            assertEquals("returned", restocked.getBindingStatus());
            assertEquals("ACT-NEW", restocked.getActivationCode());
            assertEquals("ACT-NEW", activation.getActivationCode());
            assertEquals("provisioned", activation.getStatus());
            assertNull(activation.getUsedAt());
        }
    }

    @Test
    void restockReturnedGeneratesFreshActivationWhenNoReplacementCodeProvided() {
        DeviceRmaServiceImpl service =
                new DeviceRmaServiceImpl(v2DeviceDao, v2DeviceBindingDao, v2ActivationCodeDao, v2DeviceRmaEventDao,
                        v2AccountDao);
        V2DeviceEntity device = device("returned");
        V2DeviceBindingEntity binding = binding("returned");
        V2ActivationCodeEntity activation = activation("revoked", "ACT-OLD");
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2ActivationCodeDao.selectOne(any())).thenReturn(activation);

        UserDetail user = user();
        try (MockedStatic<SecurityUser> mockedSecurityUser = org.mockito.Mockito.mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            V2DeviceRmaResponse restocked = service.restockReturned("dev-1", cleanedRequest());

            assertEquals("provisioned_unbound", restocked.getDeviceStatus());
            assertNotNull(restocked.getActivationCode());
            assertNotEquals("ACT-OLD", restocked.getActivationCode());
            assertEquals(restocked.getActivationCode(), activation.getActivationCode());
            assertEquals("provisioned", activation.getStatus());
            assertNull(activation.getUsedAt());
        }
    }

    @Test
    void disposeRevokesActivationAndMarksKnownBindingDisposed() {
        DeviceRmaServiceImpl service =
                new DeviceRmaServiceImpl(v2DeviceDao, v2DeviceBindingDao, v2ActivationCodeDao, v2DeviceRmaEventDao,
                        v2AccountDao);
        V2DeviceEntity device = device("returned");
        V2DeviceBindingEntity binding = binding("returned");
        V2ActivationCodeEntity activation = activation("provisioned", "ACT-OLD");
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2ActivationCodeDao.selectOne(any())).thenReturn(activation);

        UserDetail user = user();
        try (MockedStatic<SecurityUser> mockedSecurityUser = org.mockito.Mockito.mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            V2DeviceRmaResponse disposed = service.disposeDevice("dev-1", cleanedRequest());

            assertEquals("disposed", disposed.getDeviceStatus());
            assertEquals("disposed", disposed.getBindingStatus());
            assertEquals("revoked", activation.getStatus());
            ArgumentCaptor<V2DeviceEntity> deviceCaptor = ArgumentCaptor.forClass(V2DeviceEntity.class);
            verify(v2DeviceDao).updateById(deviceCaptor.capture());
            assertEquals("disposed", deviceCaptor.getValue().getStatus());
        }
    }

    @Test
    void restockAndDisposeRequireFactoryCleaningEvidence() {
        DeviceRmaServiceImpl service =
                new DeviceRmaServiceImpl(v2DeviceDao, v2DeviceBindingDao, v2ActivationCodeDao, v2DeviceRmaEventDao,
                        v2AccountDao);
        V2DeviceEntity device = device("returned");
        V2DeviceBindingEntity binding = binding("returned");
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);

        UserDetail user = user();
        try (MockedStatic<SecurityUser> mockedSecurityUser = org.mockito.Mockito.mockStatic(SecurityUser.class);
                MockedStatic<MessageUtils> mockedMessageUtils = org.mockito.Mockito.mockStatic(MessageUtils.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);
            mockedMessageUtils.when(() -> MessageUtils.getMessage(ErrorCode.PARAMS_GET_ERROR))
                    .thenReturn("factory cleaning evidence required");

            assertThrows(RenException.class, () -> service.restockReturned("dev-1", new V2DeviceRmaRequest()));
            assertThrows(RenException.class, () -> service.disposeDevice("dev-1", new V2DeviceRmaRequest()));
        }
    }

    @Test
    void listAuditEventsReturnsRecentDeviceEventsForEvidenceExport() {
        DeviceRmaServiceImpl service =
                new DeviceRmaServiceImpl(v2DeviceDao, v2DeviceBindingDao, v2ActivationCodeDao, v2DeviceRmaEventDao,
                        v2AccountDao);
        V2DeviceEntity device = device("disposed");
        V2DeviceRmaEventEntity event = new V2DeviceRmaEventEntity();
        event.setDeviceId("dev-1");
        event.setAction("dispose");
        event.setTicketRef("RMA-1001");
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        when(v2DeviceRmaEventDao.selectList(any())).thenReturn(List.of(event));

        try (MockedStatic<SecurityUser> mockedSecurityUser = org.mockito.Mockito.mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user());

            List<V2DeviceRmaEventEntity> events = service.listAuditEvents("dev-1");

            assertEquals(1, events.size());
            assertEquals("dispose", events.get(0).getAction());
            assertEquals("RMA-1001", events.get(0).getTicketRef());
            verify(v2DeviceRmaEventDao).selectList(any());
        }
    }

    @Test
    void startRepairRejectsDeletedOperatorAccountBeforeDeviceLookup() {
        DeviceRmaServiceImpl service =
                new DeviceRmaServiceImpl(v2DeviceDao, v2DeviceBindingDao, v2ActivationCodeDao, v2DeviceRmaEventDao,
                        v2AccountDao);
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        account.setStatus("deleted");
        when(v2AccountDao.selectById(31L)).thenReturn(account);

        try (MockedStatic<SecurityUser> mockedSecurityUser = org.mockito.Mockito.mockStatic(SecurityUser.class);
                MockedStatic<MessageUtils> mockedMessageUtils = org.mockito.Mockito.mockStatic(MessageUtils.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user());
            mockedMessageUtils.when(() -> MessageUtils.getMessage(org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn("account disabled");

            assertThrows(RenException.class, () -> service.startRepair("dev-1", new V2DeviceRmaRequest()));
        }

        verify(v2DeviceDao, org.mockito.Mockito.never()).selectById(any());
        verify(v2DeviceBindingDao, org.mockito.Mockito.never()).selectOne(any());
        verify(v2DeviceRmaEventDao, org.mockito.Mockito.never()).insert(any());
    }

    private static V2DeviceEntity device(String status) {
        V2DeviceEntity device = new V2DeviceEntity();
        device.setId("dev-1");
        device.setDeviceSn("SN-001");
        device.setStatus(status);
        return device;
    }

    private static V2DeviceBindingEntity binding(String status) {
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setId(9L);
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus(status);
        binding.setIsPrimary(Boolean.TRUE);
        return binding;
    }

    private static V2ActivationCodeEntity activation(String status, String code) {
        V2ActivationCodeEntity activation = new V2ActivationCodeEntity();
        activation.setId(7L);
        activation.setDeviceId("dev-1");
        activation.setDeviceSn("SN-001");
        activation.setActivationCode(code);
        activation.setStatus(status);
        activation.setUsedAt(new java.util.Date());
        return activation;
    }

    private static UserDetail user() {
        UserDetail user = new UserDetail();
        user.setId(31L);
        return user;
    }

    private static V2DeviceRmaRequest cleanedRequest() {
        V2DeviceRmaRequest request = new V2DeviceRmaRequest();
        request.setFactoryCleaned(Boolean.TRUE);
        request.setEvidenceRef("RMA-1001");
        return request;
    }
}
