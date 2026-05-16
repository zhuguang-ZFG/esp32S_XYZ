package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

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
import xiaozhi.modules.appv2.dao.V2DeviceSupplyDao;
import xiaozhi.modules.appv2.dto.V2DeviceSupplyResponse;
import xiaozhi.modules.appv2.dto.V2DeviceSupplyUpdateRequest;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2DeviceSupplyEntity;
import xiaozhi.modules.appv2.service.impl.DeviceSupplyServiceImpl;
import xiaozhi.modules.appv2.service.safety.SafetyValidationException;
import xiaozhi.modules.security.user.SecurityUser;

@ExtendWith(MockitoExtension.class)
class DeviceSupplyServiceImplTest {
    @Mock
    private V2DeviceSupplyDao v2DeviceSupplyDao;
    @Mock
    private V2DeviceBindingDao v2DeviceBindingDao;
    @Mock
    private V2AccountDao v2AccountDao;

    @Test
    void updateSuppliesCreatesManualStateForBoundDevice() {
        DeviceSupplyServiceImpl service = new DeviceSupplyServiceImpl(v2DeviceSupplyDao, v2DeviceBindingDao, v2AccountDao);
        V2DeviceSupplyUpdateRequest request = new V2DeviceSupplyUpdateRequest();
        request.setPaperSlotState("loaded");
        request.setPenInkPercentEst(75);
        request.setResetPenMileage(true);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding());
        when(v2DeviceSupplyDao.selectById("dev-1")).thenReturn(null);

        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = org.mockito.Mockito.mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            V2DeviceSupplyResponse response = service.updateSupplies(" dev-1 ", request);

            assertEquals("dev-1", response.getDeviceId());
            assertEquals("loaded", response.getPaperSlotState());
            assertEquals(75, response.getPenInkPercentEst());
            assertEquals(BigDecimal.ZERO, response.getPenMileageMm());
            ArgumentCaptor<V2DeviceSupplyEntity> captor = ArgumentCaptor.forClass(V2DeviceSupplyEntity.class);
            verify(v2DeviceSupplyDao).insert(captor.capture());
            assertEquals("dev-1", captor.getValue().getDeviceId());
            assertEquals("loaded", captor.getValue().getPaperSlotState());
        }
    }

    @Test
    void updateSuppliesRejectsInvalidPaperState() {
        DeviceSupplyServiceImpl service = new DeviceSupplyServiceImpl(v2DeviceSupplyDao, v2DeviceBindingDao, v2AccountDao);
        V2DeviceSupplyUpdateRequest request = new V2DeviceSupplyUpdateRequest();
        request.setPaperSlotState("missing");
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding());

        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = org.mockito.Mockito.mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);
            try (MockedStatic<MessageUtils> mockedMessageUtils = org.mockito.Mockito.mockStatic(MessageUtils.class)) {
                mockedMessageUtils.when(() -> MessageUtils.getMessage(org.mockito.ArgumentMatchers.anyInt()))
                        .thenReturn("invalid params");

                assertThrows(RenException.class, () -> service.updateSupplies("dev-1", request));
                verify(v2DeviceSupplyDao, never()).insert(any());
                verify(v2DeviceSupplyDao, never()).updateById(any());
            }
        }
    }

    @Test
    void updateSuppliesRejectsInkPercentOutsideRange() {
        DeviceSupplyServiceImpl service = new DeviceSupplyServiceImpl(v2DeviceSupplyDao, v2DeviceBindingDao, v2AccountDao);
        V2DeviceSupplyUpdateRequest request = new V2DeviceSupplyUpdateRequest();
        request.setPenInkPercentEst(101);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding());

        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = org.mockito.Mockito.mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);
            try (MockedStatic<MessageUtils> mockedMessageUtils = org.mockito.Mockito.mockStatic(MessageUtils.class)) {
                mockedMessageUtils.when(() -> MessageUtils.getMessage(org.mockito.ArgumentMatchers.anyInt()))
                        .thenReturn("invalid params");

                assertThrows(RenException.class, () -> service.updateSupplies("dev-1", request));
                verify(v2DeviceSupplyDao, never()).insert(any());
                verify(v2DeviceSupplyDao, never()).updateById(any());
            }
        }
    }

    @Test
    void requirePaperReadyRejectsWriteWhenMarkedEmpty() {
        DeviceSupplyServiceImpl service = new DeviceSupplyServiceImpl(v2DeviceSupplyDao, v2DeviceBindingDao, v2AccountDao);
        V2DeviceSupplyEntity supply = new V2DeviceSupplyEntity();
        supply.setDeviceId("dev-1");
        supply.setPaperSlotState("empty");
        when(v2DeviceSupplyDao.selectById("dev-1")).thenReturn(supply);

        SafetyValidationException error =
                assertThrows(SafetyValidationException.class,
                        () -> service.requirePaperReadyForWrite("dev-1", "write_text"));

        assertEquals("E_NO_PAPER", error.getErrorCode());
    }

    @Test
    void requirePaperReadyIgnoresNonWritingCapabilities() {
        DeviceSupplyServiceImpl service = new DeviceSupplyServiceImpl(v2DeviceSupplyDao, v2DeviceBindingDao, v2AccountDao);

        service.requirePaperReadyForWrite("dev-1", "home");

        verify(v2DeviceSupplyDao, never()).selectById(any());
    }

    @Test
    void recordCompletedRunPathMileageAccumulatesLineSegmentLength() {
        DeviceSupplyServiceImpl service = new DeviceSupplyServiceImpl(v2DeviceSupplyDao, v2DeviceBindingDao, v2AccountDao);
        V2DeviceSupplyEntity supply = new V2DeviceSupplyEntity();
        supply.setDeviceId("dev-1");
        supply.setPaperSlotState("loaded");
        supply.setPenMileageMm(BigDecimal.valueOf(2.5));
        when(v2DeviceSupplyDao.selectById("dev-1")).thenReturn(supply);

        service.recordCompletedRunPathMileage(" dev-1 ", """
                {"path":[
                  {"cmd":"M","x":0,"y":0,"z":0},
                  {"cmd":"L","x":3,"y":4,"z":0},
                  {"cmd":"M","x":10,"y":10,"z":0},
                  {"cmd":"L","x":10,"y":13,"z":0}
                ]}
                """);

        ArgumentCaptor<V2DeviceSupplyEntity> captor = ArgumentCaptor.forClass(V2DeviceSupplyEntity.class);
        verify(v2DeviceSupplyDao).updateById(captor.capture());
        assertEquals(0, BigDecimal.valueOf(10.5).compareTo(captor.getValue().getPenMileageMm()));
        verify(v2DeviceSupplyDao, never()).insert(any());
    }

    @Test
    void recordCompletedRunPathMileageCreatesUnknownSupplyWhenMissing() {
        DeviceSupplyServiceImpl service = new DeviceSupplyServiceImpl(v2DeviceSupplyDao, v2DeviceBindingDao, v2AccountDao);
        when(v2DeviceSupplyDao.selectById("dev-1")).thenReturn(null);

        service.recordCompletedRunPathMileage("dev-1", """
                {"path":[
                  {"cmd":"M","x":0,"y":0},
                  {"cmd":"L","x":0,"y":2}
                ]}
                """);

        ArgumentCaptor<V2DeviceSupplyEntity> captor = ArgumentCaptor.forClass(V2DeviceSupplyEntity.class);
        verify(v2DeviceSupplyDao).insert(captor.capture());
        assertEquals("dev-1", captor.getValue().getDeviceId());
        assertEquals("unknown", captor.getValue().getPaperSlotState());
        assertEquals(0, BigDecimal.valueOf(2.0).compareTo(captor.getValue().getPenMileageMm()));
    }

    @Test
    void updateSuppliesRejectsDeletedAccountTombstoneBeforeBindingLookup() {
        DeviceSupplyServiceImpl service = new DeviceSupplyServiceImpl(v2DeviceSupplyDao, v2DeviceBindingDao, v2AccountDao);
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        account.setStatus("deleted");
        when(v2AccountDao.selectById(31L)).thenReturn(account);

        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = org.mockito.Mockito.mockStatic(SecurityUser.class);
                MockedStatic<MessageUtils> mockedMessageUtils = org.mockito.Mockito.mockStatic(MessageUtils.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);
            mockedMessageUtils.when(() -> MessageUtils.getMessage(org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn("account disabled");

            assertThrows(RenException.class, () -> service.updateSupplies("dev-1", new V2DeviceSupplyUpdateRequest()));
        }

        verify(v2DeviceBindingDao, never()).selectOne(any());
        verify(v2DeviceSupplyDao, never()).insert(any());
        verify(v2DeviceSupplyDao, never()).updateById(any());
    }

    private static V2DeviceBindingEntity activeBinding() {
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        return binding;
    }
}
