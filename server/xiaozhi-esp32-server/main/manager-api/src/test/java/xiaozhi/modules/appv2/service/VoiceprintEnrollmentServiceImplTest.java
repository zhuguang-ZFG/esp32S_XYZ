package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.common.exception.RenException;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.MessageUtils;
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2MemberDao;
import xiaozhi.modules.appv2.dao.V2VoiceprintDao;
import xiaozhi.modules.appv2.dto.V2VoiceprintEnrollRequest;
import xiaozhi.modules.appv2.dto.V2VoiceprintEnrollResponse;
import xiaozhi.modules.appv2.dto.V2VoiceprintCacheEntry;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2MemberEntity;
import xiaozhi.modules.appv2.entity.V2VoiceprintEntity;
import xiaozhi.modules.appv2.service.impl.VoiceprintEnrollmentServiceImpl;
import xiaozhi.modules.security.user.SecurityUser;

@ExtendWith(MockitoExtension.class)
class VoiceprintEnrollmentServiceImplTest {
    @Mock
    private V2DeviceBindingDao v2DeviceBindingDao;
    @Mock
    private V2MemberDao v2MemberDao;
    @Mock
    private V2VoiceprintDao v2VoiceprintDao;
    @Mock
    private V2AccountDao v2AccountDao;

    @Test
    void enrollCreatesMemberAndVoiceprintWithoutPersistingAudio() {
        VoiceprintEnrollmentServiceImpl service =
                new VoiceprintEnrollmentServiceImpl(v2DeviceBindingDao, v2MemberDao, v2VoiceprintDao, v2AccountDao);
        V2VoiceprintEnrollRequest request = request();
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        org.mockito.Mockito.doAnswer(invocation -> {
            V2MemberEntity member = invocation.getArgument(0);
            member.setId(101L);
            return 1;
        }).when(v2MemberDao).insert(any(V2MemberEntity.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            V2VoiceprintEntity voiceprint = invocation.getArgument(0);
            voiceprint.setId(201L);
            return 1;
        }).when(v2VoiceprintDao).insert(any(V2VoiceprintEntity.class));

        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            V2VoiceprintEnrollResponse response = service.enroll(request);

            assertEquals(101L, response.getMemberId());
            assertEquals(201L, response.getVoiceprintId());
            assertEquals("active", response.getStatus());
            org.junit.jupiter.api.Assertions.assertTrue(response.getSpeakerRef().startsWith("local:"));

            ArgumentCaptor<V2MemberEntity> memberCaptor = ArgumentCaptor.forClass(V2MemberEntity.class);
            verify(v2MemberDao).insert(memberCaptor.capture());
            V2MemberEntity member = memberCaptor.getValue();
            assertEquals(31L, member.getAccountId());
            assertEquals("dev-1", member.getDeviceId());
            assertEquals("Parent", member.getDisplayName());
            assertEquals("owner", member.getMemberType());

            ArgumentCaptor<V2VoiceprintEntity> voiceprintCaptor = ArgumentCaptor.forClass(V2VoiceprintEntity.class);
            verify(v2VoiceprintDao).insert(voiceprintCaptor.capture());
            V2VoiceprintEntity voiceprint = voiceprintCaptor.getValue();
            assertEquals(31L, voiceprint.getAccountId());
            assertEquals(101L, voiceprint.getMemberId());
            assertEquals("local_fake_voiceprint", voiceprint.getProvider());
            assertEquals(6000, voiceprint.getSampleDurationMs());
            assertEquals(64, voiceprint.getEmbeddingHash().length());
            assertEquals("active", voiceprint.getStatus());
        }
    }

    @Test
    void enrollRejectsDurationOutsideEnrollmentWindow() {
        VoiceprintEnrollmentServiceImpl service =
                new VoiceprintEnrollmentServiceImpl(v2DeviceBindingDao, v2MemberDao, v2VoiceprintDao, v2AccountDao);
        V2VoiceprintEnrollRequest request = request();
        request.setSampleDurationMs(9000);
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);

        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class);
                MockedStatic<MessageUtils> mockedMessageUtils = mockStatic(MessageUtils.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);
            mockedMessageUtils.when(() -> MessageUtils.getMessage(org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn("bad params");

            assertThrows(RenException.class, () -> service.enroll(request));
        }

        verify(v2MemberDao, never()).insert(any());
        verify(v2VoiceprintDao, never()).insert(any());
    }

    @Test
    void activeCacheForDeviceReturnsVoiceprintsWithMemberMetadata() {
        VoiceprintEnrollmentServiceImpl service =
                new VoiceprintEnrollmentServiceImpl(v2DeviceBindingDao, v2MemberDao, v2VoiceprintDao, v2AccountDao);
        V2VoiceprintEntity voiceprint = new V2VoiceprintEntity();
        voiceprint.setMemberId(101L);
        voiceprint.setDeviceId("dev-1");
        voiceprint.setSpeakerRef("local:parent");
        voiceprint.setEmbeddingHash("a".repeat(64));
        voiceprint.setStatus("active");
        Date expiresAt = new Date(System.currentTimeMillis() - 1000);
        voiceprint.setExpiresAt(expiresAt);
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        V2MemberEntity member = new V2MemberEntity();
        member.setId(101L);
        member.setDisplayName("Parent");
        member.setMemberType("owner");
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2VoiceprintDao.selectList(any())).thenReturn(List.of(voiceprint));
        when(v2MemberDao.selectBatchIds(List.of(101L))).thenReturn(List.of(member));

        List<V2VoiceprintCacheEntry> entries = service.activeCacheForDevice("dev-1");

        assertEquals(1, entries.size());
        assertEquals(101L, entries.get(0).getMemberId());
        assertEquals("Parent", entries.get(0).getDisplayName());
        assertEquals("owner", entries.get(0).getMemberType());
        assertEquals("local:parent", entries.get(0).getSpeakerRef());
        assertEquals("a".repeat(64), entries.get(0).getEmbeddingHash());
        assertEquals(expiresAt, entries.get(0).getExpiresAt());
    }

    @Test
    void activeCacheForDeviceReturnsEmptyWhenDeviceHasNoActiveBinding() {
        VoiceprintEnrollmentServiceImpl service =
                new VoiceprintEnrollmentServiceImpl(v2DeviceBindingDao, v2MemberDao, v2VoiceprintDao, v2AccountDao);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(null);

        List<V2VoiceprintCacheEntry> entries = service.activeCacheForDevice("dev-1");

        assertEquals(0, entries.size());
        verify(v2VoiceprintDao, never()).selectList(any());
        verify(v2MemberDao, never()).selectBatchIds(any());
    }

    @Test
    void enrollRejectsDeletedAccountTombstoneBeforeBindingLookup() {
        VoiceprintEnrollmentServiceImpl service =
                new VoiceprintEnrollmentServiceImpl(v2DeviceBindingDao, v2MemberDao, v2VoiceprintDao, v2AccountDao);
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        account.setStatus("deleted");
        when(v2AccountDao.selectById(31L)).thenReturn(account);

        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class);
                MockedStatic<MessageUtils> mockedMessageUtils = mockStatic(MessageUtils.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);
            mockedMessageUtils.when(() -> MessageUtils.getMessage(org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn("account disabled");

            assertThrows(RenException.class, () -> service.enroll(request()));
        }

        verify(v2DeviceBindingDao, never()).selectOne(any());
        verify(v2MemberDao, never()).insert(any());
        verify(v2VoiceprintDao, never()).insert(any());
    }

    @Test
    void activeCacheForDeviceReturnsEmptyWhenBindingAccountIsDeleted() {
        VoiceprintEnrollmentServiceImpl service =
                new VoiceprintEnrollmentServiceImpl(v2DeviceBindingDao, v2MemberDao, v2VoiceprintDao, v2AccountDao);
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        account.setStatus("deleted");
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2AccountDao.selectById(31L)).thenReturn(account);

        List<V2VoiceprintCacheEntry> entries = service.activeCacheForDevice("dev-1");

        assertEquals(0, entries.size());
        verify(v2VoiceprintDao, never()).selectList(any());
        verify(v2MemberDao, never()).selectBatchIds(any());
    }

    private static V2VoiceprintEnrollRequest request() {
        V2VoiceprintEnrollRequest request = new V2VoiceprintEnrollRequest();
        request.setDeviceId("dev-1");
        request.setDisplayName("Parent");
        request.setMemberType("owner");
        request.setSampleDurationMs(6000);
        request.setAudioBase64(Base64.getEncoder().encodeToString("sample audio".getBytes(StandardCharsets.UTF_8)));
        return request;
    }
}
