package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.service.impl.PrimarySessionServiceImpl;

@ExtendWith(MockitoExtension.class)
class PrimarySessionServiceImplTest {
    @Mock
    private V2AccountDao v2AccountDao;
    @Mock
    private V2DeviceBindingDao v2DeviceBindingDao;

    @Test
    void requirePrimaryForWriteAllowsMatchingPrimarySession() {
        PrimarySessionServiceImpl service = new PrimarySessionServiceImpl(v2AccountDao, v2DeviceBindingDao);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding(31L, "dev-1"));
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        account.setPrimarySessionId("token-1");
        account.setPrimarySessionClaimedAt(new Date());
        when(v2AccountDao.selectById(31L)).thenReturn(account);

        service.requirePrimaryForWrite(31L, "dev-1", " token-1 ");

        verify(v2AccountDao).selectById(31L);
    }

    @Test
    void requirePrimaryForWriteRejectsNonPrimarySessionWithENotPrimary() {
        PrimarySessionServiceImpl service = new PrimarySessionServiceImpl(v2AccountDao, v2DeviceBindingDao);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding(31L, "dev-1"));
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        account.setPrimarySessionId("token-primary");
        account.setPrimarySessionClaimedAt(new Date());
        when(v2AccountDao.selectById(31L)).thenReturn(account);

        PrimarySessionException error = assertThrows(PrimarySessionException.class,
                () -> service.requirePrimaryForWrite(31L, "dev-1", "token-secondary"));

        assertEquals("E_NOT_PRIMARY", error.getCode());
        assertEquals("current session is not primary", error.getMessage());
    }

    @Test
    void requireVoiceAllowedForWriteAllowsWhenNoPrimarySessionClaimed() {
        PrimarySessionServiceImpl service = new PrimarySessionServiceImpl(v2AccountDao, v2DeviceBindingDao);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding(31L, "dev-1"));
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        when(v2AccountDao.selectById(31L)).thenReturn(account);

        service.requireVoiceAllowedForWrite(31L, "dev-1");

        verify(v2AccountDao).selectById(31L);
    }

    @Test
    void requireVoiceAllowedForWriteRejectsWhenPrimarySessionClaimed() {
        PrimarySessionServiceImpl service = new PrimarySessionServiceImpl(v2AccountDao, v2DeviceBindingDao);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding(31L, "dev-1"));
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        account.setPrimarySessionId("token-primary");
        account.setPrimarySessionClaimedAt(new Date());
        when(v2AccountDao.selectById(31L)).thenReturn(account);

        PrimarySessionException error = assertThrows(PrimarySessionException.class,
                () -> service.requireVoiceAllowedForWrite(31L, "dev-1"));

        assertEquals("E_NOT_PRIMARY", error.getCode());
        assertEquals("primary session blocks voice write", error.getMessage());
    }

    @Test
    void claimPrimaryStoresCurrentSession() {
        PrimarySessionServiceImpl service = new PrimarySessionServiceImpl(v2AccountDao, v2DeviceBindingDao);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding(31L, "dev-1"));
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        account.setPrimarySessionId("old-token");
        when(v2AccountDao.selectById(31L)).thenReturn(account);

        service.claimPrimary(31L, "dev-1", " token-new ");

        ArgumentCaptor<V2AccountEntity> captor = ArgumentCaptor.forClass(V2AccountEntity.class);
        verify(v2AccountDao).updateById(captor.capture());
        assertEquals("token-new", captor.getValue().getPrimarySessionId());
        assertNotNull(captor.getValue().getPrimarySessionClaimedAt());
    }

    @Test
    void requirePrimaryForWriteReleasesExpiredPrimaryLease() {
        PrimarySessionServiceImpl service = new PrimarySessionServiceImpl(v2AccountDao, v2DeviceBindingDao);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding(31L, "dev-1"));
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        account.setPrimarySessionId("token-primary");
        account.setPrimarySessionClaimedAt(new Date(System.currentTimeMillis() - 61_000L));
        when(v2AccountDao.selectById(31L)).thenReturn(account);

        PrimarySessionException error = assertThrows(PrimarySessionException.class,
                () -> service.requirePrimaryForWrite(31L, "dev-1", "token-primary"));

        assertEquals("E_NOT_PRIMARY", error.getCode());
        assertEquals("primary session is not claimed", error.getMessage());
        ArgumentCaptor<V2AccountEntity> captor = ArgumentCaptor.forClass(V2AccountEntity.class);
        verify(v2AccountDao).updateById(captor.capture());
        assertNull(captor.getValue().getPrimarySessionId());
        assertNull(captor.getValue().getPrimarySessionClaimedAt());
    }

    @Test
    void requireVoiceAllowedForWriteAllowsAfterExpiredPrimaryLeaseIsReleased() {
        PrimarySessionServiceImpl service = new PrimarySessionServiceImpl(v2AccountDao, v2DeviceBindingDao);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding(31L, "dev-1"));
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        account.setPrimarySessionId("token-primary");
        account.setPrimarySessionClaimedAt(new Date(System.currentTimeMillis() - 61_000L));
        when(v2AccountDao.selectById(31L)).thenReturn(account);

        service.requireVoiceAllowedForWrite(31L, "dev-1");

        ArgumentCaptor<V2AccountEntity> captor = ArgumentCaptor.forClass(V2AccountEntity.class);
        verify(v2AccountDao).updateById(captor.capture());
        assertNull(captor.getValue().getPrimarySessionId());
        assertNull(captor.getValue().getPrimarySessionClaimedAt());
    }

    private static V2DeviceBindingEntity activeBinding(Long accountId, String deviceId) {
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(accountId);
        binding.setDeviceId(deviceId);
        binding.setBindingStatus("active");
        return binding;
    }
}
