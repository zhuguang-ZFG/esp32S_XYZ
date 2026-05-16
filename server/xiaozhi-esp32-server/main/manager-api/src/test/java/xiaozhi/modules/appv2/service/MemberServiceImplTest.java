package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import xiaozhi.modules.appv2.dto.V2MemberCreateRequest;
import xiaozhi.modules.appv2.dto.V2MemberResponse;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2MemberEntity;
import xiaozhi.modules.appv2.service.impl.MemberServiceImpl;
import xiaozhi.modules.security.user.SecurityUser;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {
    @Mock
    private V2DeviceBindingDao v2DeviceBindingDao;
    @Mock
    private V2MemberDao v2MemberDao;
    @Mock
    private V2AccountDao v2AccountDao;

    @Test
    void createAddsChildMemberForBoundDevice() {
        MemberServiceImpl service = new MemberServiceImpl(v2DeviceBindingDao, v2MemberDao, v2AccountDao);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding());
        org.mockito.Mockito.doAnswer(invocation -> {
            V2MemberEntity member = invocation.getArgument(0);
            member.setId(202L);
            return 1;
        }).when(v2MemberDao).insert(any(V2MemberEntity.class));

        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user());

            V2MemberResponse response = service.create(createRequest("Child", "child"));

            assertEquals(202L, response.getMemberId());
            assertEquals("dev-1", response.getDeviceId());
            assertEquals("Child", response.getDisplayName());
            assertEquals("child", response.getRole());
            assertEquals("child", response.getMemberType());
            assertEquals("active", response.getStatus());
        }

        ArgumentCaptor<V2MemberEntity> captor = ArgumentCaptor.forClass(V2MemberEntity.class);
        verify(v2MemberDao).insert(captor.capture());
        V2MemberEntity inserted = captor.getValue();
        assertEquals(31L, inserted.getAccountId());
        assertEquals("dev-1", inserted.getDeviceId());
        assertEquals("Child", inserted.getDisplayName());
        assertEquals("child", inserted.getRole());
        assertEquals("child", inserted.getMemberType());
        assertEquals("active", inserted.getStatus());
    }

    @Test
    void listByDeviceReturnsOwnerAndChildMembers() {
        MemberServiceImpl service = new MemberServiceImpl(v2DeviceBindingDao, v2MemberDao, v2AccountDao);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding());
        when(v2MemberDao.selectList(any())).thenReturn(List.of(member(101L, "Parent", "owner"), member(202L, "Child", "child")));

        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user());

            List<V2MemberResponse> members = service.listByDevice("dev-1");

            assertEquals(2, members.size());
            assertEquals("owner", members.get(0).getMemberType());
            assertEquals("child", members.get(1).getMemberType());
        }
    }

    @Test
    void createRejectsInvalidMemberTypeWithoutInsert() {
        MemberServiceImpl service = new MemberServiceImpl(v2DeviceBindingDao, v2MemberDao, v2AccountDao);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(activeBinding());

        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class);
                MockedStatic<MessageUtils> mockedMessageUtils = mockStatic(MessageUtils.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user());
            mockedMessageUtils.when(() -> MessageUtils.getMessage(org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn("bad params");

            assertThrows(RenException.class, () -> service.create(createRequest("Other", "guest")));
        }

        verify(v2MemberDao, never()).insert(any());
    }

    @Test
    void createRejectsUnboundDevice() {
        MemberServiceImpl service = new MemberServiceImpl(v2DeviceBindingDao, v2MemberDao, v2AccountDao);
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(null);

        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class);
                MockedStatic<MessageUtils> mockedMessageUtils = mockStatic(MessageUtils.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user());
            mockedMessageUtils.when(() -> MessageUtils.getMessage(org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn("device not found");

            assertThrows(RenException.class, () -> service.create(createRequest("Child", "child")));
        }

        verify(v2MemberDao, never()).insert(any());
    }

    @Test
    void createRejectsDeletedAccountTombstoneBeforeBindingLookup() {
        MemberServiceImpl service = new MemberServiceImpl(v2DeviceBindingDao, v2MemberDao, v2AccountDao);
        V2AccountEntity account = new V2AccountEntity();
        account.setId(31L);
        account.setStatus("deleted");
        when(v2AccountDao.selectById(31L)).thenReturn(account);

        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class);
                MockedStatic<MessageUtils> mockedMessageUtils = mockStatic(MessageUtils.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user());
            mockedMessageUtils.when(() -> MessageUtils.getMessage(org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn("account disabled");

            assertThrows(RenException.class, () -> service.create(createRequest("Child", "child")));
        }

        verify(v2DeviceBindingDao, never()).selectOne(any());
        verify(v2MemberDao, never()).insert(any());
    }

    private static V2MemberCreateRequest createRequest(String displayName, String memberType) {
        V2MemberCreateRequest request = new V2MemberCreateRequest();
        request.setDeviceId("dev-1");
        request.setDisplayName(displayName);
        request.setMemberType(memberType);
        return request;
    }

    private static V2DeviceBindingEntity activeBinding() {
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        return binding;
    }

    private static V2MemberEntity member(Long id, String displayName, String memberType) {
        V2MemberEntity member = new V2MemberEntity();
        member.setId(id);
        member.setDeviceId("dev-1");
        member.setDisplayName(displayName);
        member.setRole(memberType);
        member.setMemberType(memberType);
        member.setStatus("active");
        return member;
    }

    private static UserDetail user() {
        UserDetail user = new UserDetail();
        user.setId(31L);
        return user;
    }
}
