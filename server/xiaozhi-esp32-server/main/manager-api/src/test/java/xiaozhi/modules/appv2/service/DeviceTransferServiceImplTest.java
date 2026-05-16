package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
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
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2DeviceTransferRequestDao;
import xiaozhi.modules.appv2.dao.V2MemberDao;
import xiaozhi.modules.appv2.dao.V2VoiceprintDao;
import xiaozhi.modules.appv2.dto.V2DeviceTransferRequest;
import xiaozhi.modules.appv2.dto.V2DeviceTransferResponse;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2DeviceTransferRequestEntity;
import xiaozhi.modules.appv2.entity.V2VoiceprintEntity;
import xiaozhi.modules.appv2.service.impl.DeviceTransferServiceImpl;
import xiaozhi.modules.security.user.SecurityUser;

@ExtendWith(MockitoExtension.class)
class DeviceTransferServiceImplTest {
    @Mock
    private V2AccountDao v2AccountDao;
    @Mock
    private V2DeviceBindingDao v2DeviceBindingDao;
    @Mock
    private V2DeviceTransferRequestDao v2DeviceTransferRequestDao;
    @Mock
    private V2MemberDao v2MemberDao;
    @Mock
    private V2VoiceprintDao v2VoiceprintDao;
    @Mock
    private DeviceServerMotionGateway deviceServerMotionGateway;
    @Mock
    private ProductNotificationOutboxService productNotificationOutboxService;

    @Test
    void requestTransferCreatesPendingRequestForTargetUnionid() {
        DeviceTransferServiceImpl service = service();
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding(31L, "dev-1"));
        V2AccountEntity target = new V2AccountEntity();
        target.setId(42L);
        target.setUnionid("target-union");
        target.setStatus("active");
        when(v2AccountDao.selectOne(any())).thenReturn(target);

        V2DeviceTransferRequest request = new V2DeviceTransferRequest();
        request.setTargetUnionid(" target-union ");
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user(31L));

            V2DeviceTransferResponse response = service.requestTransfer(" dev-1 ", request);

            assertEquals("pending", response.getStatus());
            assertEquals(31L, response.getSourceAccountId());
            assertEquals(42L, response.getTargetAccountId());
        }

        ArgumentCaptor<V2DeviceTransferRequestEntity> captor =
                ArgumentCaptor.forClass(V2DeviceTransferRequestEntity.class);
        verify(v2DeviceTransferRequestDao).insert(captor.capture());
        assertEquals("dev-1", captor.getValue().getDeviceId());
        assertEquals("target-union", captor.getValue().getTargetUnionid());
        assertNotNull(captor.getValue().getRequestedAt());
        verify(productNotificationOutboxService)
                .enqueuePendingDeviceTransfer(42L, "dev-1", captor.getValue().getId());
    }

    @Test
    void requestTransferRejectsDeletedAccountTombstoneBeforeBindingLookup() {
        DeviceTransferServiceImpl service = service();
        V2AccountEntity deleted = new V2AccountEntity();
        deleted.setId(31L);
        deleted.setStatus("deleted");
        when(v2AccountDao.selectById(31L)).thenReturn(deleted);

        V2DeviceTransferRequest request = new V2DeviceTransferRequest();
        request.setTargetUnionid("target-union");
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user(31L));
            try (MockedStatic<MessageUtils> mockedMessageUtils = mockStatic(MessageUtils.class)) {
                mockedMessageUtils.when(() -> MessageUtils.getMessage(ErrorCode.ACCOUNT_DISABLE))
                        .thenReturn("account disabled");

                RenException ex = assertThrows(
                        RenException.class,
                        () -> service.requestTransfer("dev-1", request));

                assertEquals(ErrorCode.ACCOUNT_DISABLE, ex.getCode());
                verify(v2DeviceBindingDao, never()).selectOne(any());
                verify(v2DeviceTransferRequestDao, never()).insert(any());
                verify(productNotificationOutboxService, never()).enqueuePendingDeviceTransfer(any(), any(), any());
            }
        }
    }

    @Test
    void acceptTransferMovesBindingAndClearsOldMembersVoiceprintsAndCache() {
        DeviceTransferServiceImpl service = service();
        V2DeviceTransferRequestEntity transfer = new V2DeviceTransferRequestEntity();
        transfer.setId(7L);
        transfer.setDeviceId("dev-1");
        transfer.setSourceAccountId(31L);
        transfer.setTargetAccountId(42L);
        transfer.setTargetUnionid("target-union");
        transfer.setStatus("pending");
        when(v2DeviceTransferRequestDao.selectOne(any())).thenReturn(transfer);
        V2DeviceBindingEntity sourceBinding = activeBinding(31L, "dev-1");
        sourceBinding.setId(11L);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(sourceBinding);
        V2AccountEntity sourceAccount = new V2AccountEntity();
        sourceAccount.setId(31L);
        sourceAccount.setPrimarySessionId("old-primary");
        sourceAccount.setPrimarySessionClaimedAt(new Date());
        when(v2AccountDao.selectById(42L)).thenReturn(null);
        when(v2AccountDao.selectById(31L)).thenReturn(sourceAccount);
        V2VoiceprintEntity voiceprint = new V2VoiceprintEntity();
        voiceprint.setId(201L);
        voiceprint.setAccountId(31L);
        voiceprint.setDeviceId("dev-1");
        voiceprint.setSpeakerRef("local:parent");
        voiceprint.setEmbeddingHash("a".repeat(64));
        when(v2VoiceprintDao.selectList(any())).thenReturn(List.of(voiceprint));

        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user(42L));

            V2DeviceTransferResponse response = service.acceptTransfer(7L);

            assertEquals("accepted", response.getStatus());
            assertEquals(31L, response.getSourceAccountId());
            assertEquals(42L, response.getTargetAccountId());
        }

        ArgumentCaptor<V2DeviceBindingEntity> updatedBinding =
                ArgumentCaptor.forClass(V2DeviceBindingEntity.class);
        verify(v2DeviceBindingDao).updateById(updatedBinding.capture());
        assertEquals("transferred", updatedBinding.getValue().getBindingStatus());
        assertEquals(Boolean.FALSE, updatedBinding.getValue().getIsPrimary());

        ArgumentCaptor<V2DeviceBindingEntity> insertedBinding =
                ArgumentCaptor.forClass(V2DeviceBindingEntity.class);
        verify(v2DeviceBindingDao).insert(insertedBinding.capture());
        assertEquals(42L, insertedBinding.getValue().getAccountId());
        assertEquals("active", insertedBinding.getValue().getBindingStatus());

        verify(v2MemberDao).update(any(), any());
        ArgumentCaptor<V2VoiceprintEntity> deletedVoiceprint =
                ArgumentCaptor.forClass(V2VoiceprintEntity.class);
        verify(v2VoiceprintDao).updateById(deletedVoiceprint.capture());
        assertEquals("deleted", deletedVoiceprint.getValue().getStatus());
        assertEquals("deleted:31:201", deletedVoiceprint.getValue().getSpeakerRef());
        assertEquals("0".repeat(64), deletedVoiceprint.getValue().getEmbeddingHash());
        assertNotNull(deletedVoiceprint.getValue().getAuditRetainUntil());
        verify(deviceServerMotionGateway).clearVoiceprintCache("dev-1", "device_transfer");
        verify(productNotificationOutboxService).resolveDeviceTransfer(7L);
        verify(v2AccountDao).updateById(sourceAccount);
        assertEquals(null, sourceAccount.getPrimarySessionId());
        assertEquals(null, sourceAccount.getPrimarySessionClaimedAt());
    }

    @Test
    void cancelTransferMarksOwnPendingRequestCancelledWithoutChangingBindingsOrCache() {
        DeviceTransferServiceImpl service = service();
        V2DeviceTransferRequestEntity transfer = new V2DeviceTransferRequestEntity();
        transfer.setId(7L);
        transfer.setDeviceId("dev-1");
        transfer.setSourceAccountId(31L);
        transfer.setTargetAccountId(42L);
        transfer.setTargetUnionid("target-union");
        transfer.setStatus("pending");
        when(v2DeviceTransferRequestDao.selectOne(any())).thenReturn(transfer);

        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user(31L));

            V2DeviceTransferResponse response = service.cancelTransfer(7L);

            assertEquals("cancelled", response.getStatus());
            assertEquals(31L, response.getSourceAccountId());
            assertEquals(42L, response.getTargetAccountId());
        }

        ArgumentCaptor<V2DeviceTransferRequestEntity> captor =
                ArgumentCaptor.forClass(V2DeviceTransferRequestEntity.class);
        verify(v2DeviceTransferRequestDao).updateById(captor.capture());
        assertEquals("cancelled", captor.getValue().getStatus());
        verify(v2DeviceBindingDao, never()).updateById(any());
        verify(v2DeviceBindingDao, never()).insert(any());
        verify(v2MemberDao, never()).update(any(), any());
        verify(v2VoiceprintDao, never()).updateById(any());
        verify(deviceServerMotionGateway, never()).clearVoiceprintCache(any(), any());
        verify(productNotificationOutboxService).cancelDeviceTransfer(7L);
    }

    @Test
    void listPendingIncomingTransfersReturnsOnlyCurrentTargetPendingRequests() {
        DeviceTransferServiceImpl service = service();
        V2DeviceTransferRequestEntity transfer = new V2DeviceTransferRequestEntity();
        transfer.setId(9L);
        transfer.setDeviceId("dev-2");
        transfer.setSourceAccountId(31L);
        transfer.setTargetAccountId(42L);
        transfer.setTargetUnionid("target-union");
        transfer.setStatus("pending");
        when(v2DeviceTransferRequestDao.selectList(any())).thenReturn(List.of(transfer));

        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user(42L));

            List<V2DeviceTransferResponse> responses = service.listPendingIncomingTransfers();

            assertEquals(1, responses.size());
            assertEquals(9L, responses.get(0).getTransferId());
            assertEquals("dev-2", responses.get(0).getDeviceId());
            assertEquals("pending", responses.get(0).getStatus());
        }
    }

    private DeviceTransferServiceImpl service() {
        return new DeviceTransferServiceImpl(v2AccountDao, v2DeviceBindingDao, v2DeviceTransferRequestDao,
                v2MemberDao, v2VoiceprintDao, deviceServerMotionGateway, productNotificationOutboxService);
    }

    private static V2DeviceBindingEntity activeBinding(Long accountId, String deviceId) {
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(accountId);
        binding.setDeviceId(deviceId);
        binding.setBindingStatus("active");
        binding.setIsPrimary(Boolean.TRUE);
        return binding;
    }

    private static UserDetail user(Long accountId) {
        UserDetail user = new UserDetail();
        user.setId(accountId);
        return user;
    }
}
