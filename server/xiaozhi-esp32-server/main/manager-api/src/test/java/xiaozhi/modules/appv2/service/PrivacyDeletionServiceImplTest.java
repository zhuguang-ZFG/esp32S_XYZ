package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.common.user.UserDetail;
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2MemberDao;
import xiaozhi.modules.appv2.dao.V2VoiceprintDao;
import xiaozhi.modules.appv2.dto.V2DeletionResponse;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2VoiceprintEntity;
import xiaozhi.modules.appv2.service.impl.PrivacyDeletionServiceImpl;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.security.user.SecurityUser;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

@ExtendWith(MockitoExtension.class)
class PrivacyDeletionServiceImplTest {
    @Mock
    private V2AccountDao v2AccountDao;
    @Mock
    private V2DeviceBindingDao v2DeviceBindingDao;
    @Mock
    private V2MemberDao v2MemberDao;
    @Mock
    private V2VoiceprintDao v2VoiceprintDao;
    @Mock
    private SysUserTokenService sysUserTokenService;

    @Test
    void deleteVoiceprintAnonymizesMatchingMaterialAndKeepsAuditRetention() {
        PrivacyDeletionServiceImpl service =
                new PrivacyDeletionServiceImpl(
                        v2AccountDao,
                        v2DeviceBindingDao,
                        v2MemberDao,
                        v2VoiceprintDao,
                        sysUserTokenService);
        V2VoiceprintEntity voiceprint = new V2VoiceprintEntity();
        voiceprint.setId(201L);
        voiceprint.setAccountId(31L);
        voiceprint.setSpeakerRef("local:parent");
        voiceprint.setEmbeddingHash("a".repeat(64));
        when(v2VoiceprintDao.selectOne(any())).thenReturn(voiceprint);
        when(v2VoiceprintDao.updateById(any())).thenReturn(1);

        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user());

            V2DeletionResponse response = service.deleteVoiceprint(201L);

            assertEquals("deleted", response.getStatus());
            assertEquals(1, response.getAffectedRows());
            assertEquals(180, response.getAuditRetentionDays());
        }

        ArgumentCaptor<V2VoiceprintEntity> captor = ArgumentCaptor.forClass(V2VoiceprintEntity.class);
        verify(v2VoiceprintDao).updateById(captor.capture());
        V2VoiceprintEntity deleted = captor.getValue();
        assertEquals("deleted", deleted.getStatus());
        assertEquals("deleted:31:201", deleted.getSpeakerRef());
        assertEquals("0".repeat(64), deleted.getEmbeddingHash());
        assertNotNull(deleted.getDeletedAt());
        assertNotNull(deleted.getAuditRetainUntil());
    }

    @Test
    void deleteAccountSoftDeletesAccountBindingsMembersAndVoiceprints() {
        PrivacyDeletionServiceImpl service =
                new PrivacyDeletionServiceImpl(
                        v2AccountDao,
                        v2DeviceBindingDao,
                        v2MemberDao,
                        v2VoiceprintDao,
                        sysUserTokenService);
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        account.setOpenid("openid");
        account.setDisplayName("Parent");
        account.setPrimarySessionId("token");
        account.setPrimarySessionClaimedAt(new Date());
        when(v2AccountDao.selectById(31L)).thenReturn(account);
        when(v2AccountDao.updateById(any())).thenReturn(1);
        when(v2DeviceBindingDao.update(any(), any())).thenReturn(1);
        when(v2MemberDao.update(any(), any())).thenReturn(1);
        V2VoiceprintEntity voiceprint = new V2VoiceprintEntity();
        voiceprint.setId(201L);
        voiceprint.setAccountId(31L);
        when(v2VoiceprintDao.selectList(any())).thenReturn(List.of(voiceprint));
        when(v2VoiceprintDao.updateById(any())).thenReturn(1);

        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user());

            V2DeletionResponse response = service.deleteAccount();

            assertEquals("deleted", response.getStatus());
            assertEquals(4, response.getAffectedRows());
            assertEquals(180, response.getAuditRetentionDays());
        }

        ArgumentCaptor<V2AccountEntity> accountCaptor = ArgumentCaptor.forClass(V2AccountEntity.class);
        verify(v2AccountDao).updateById(accountCaptor.capture());
        V2AccountEntity deletedAccount = accountCaptor.getValue();
        assertEquals("deleted", deletedAccount.getStatus());
        assertNull(deletedAccount.getOpenid());
        assertNull(deletedAccount.getPrimarySessionId());
        assertNull(deletedAccount.getPrimarySessionClaimedAt());
        assertEquals("deleted-account-31", deletedAccount.getDisplayName());
        assertNotNull(deletedAccount.getDeletedAt());
        assertNotNull(deletedAccount.getAuditRetainUntil());

        verify(v2DeviceBindingDao).update(any(), any());
        verify(v2MemberDao).update(any(), any());
        verify(v2VoiceprintDao).updateById(any(V2VoiceprintEntity.class));
        verify(sysUserTokenService).logout(31L);
    }

    @Test
    void purgeExpiredRetentionDeletesExpiredVoiceprintsAndClearsAccountTombstones() {
        PrivacyDeletionServiceImpl service =
                new PrivacyDeletionServiceImpl(
                        v2AccountDao,
                        v2DeviceBindingDao,
                        v2MemberDao,
                        v2VoiceprintDao,
                        sysUserTokenService);
        when(v2VoiceprintDao.delete(any())).thenReturn(2);
        when(v2AccountDao.update(any(), any())).thenReturn(1);

        int affectedRows = service.purgeExpiredRetention(Date.from(Instant.parse("2026-11-15T00:00:00Z")));

        assertEquals(3, affectedRows);
        ArgumentCaptor<Wrapper<V2VoiceprintEntity>> voiceprintWrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(v2VoiceprintDao).delete(voiceprintWrapperCaptor.capture());
        assertNotNull(voiceprintWrapperCaptor.getValue());

        ArgumentCaptor<UpdateWrapper<V2AccountEntity>> accountWrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(v2AccountDao).update(any(), accountWrapperCaptor.capture());
        assertNotNull(accountWrapperCaptor.getValue());
    }

    private static UserDetail user() {
        UserDetail user = new UserDetail();
        user.setId(31L);
        return user;
    }
}
