package com.elibrary.admin;

import com.elibrary.common.PageResult;
import com.elibrary.digital.DigitalItem;
import com.elibrary.digital.DigitalItemService;
import com.elibrary.physical.ContentType;
import com.elibrary.physical.PhysicalCopy;
import com.elibrary.physical.PhysicalWork;
import com.elibrary.physical.PhysicalWorkService;
import com.elibrary.user.LibraryUserPrincipal;
import com.elibrary.user.Role;
import com.elibrary.user.User;
import com.elibrary.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    PhysicalWorkService physicalWorkService;
    @Mock
    DigitalItemService digitalItemService;
    @Mock
    UserService userService;

    @InjectMocks
    AdminPhysicalController adminPhysicalController;
    @InjectMocks
    AdminDigitalController adminDigitalController;
    @InjectMocks
    AdminUserController adminUserController;

    @Test
    @DisplayName("实体管理-列表新建编辑复本删除应调用服务并返回视图")
    void physicalAdmin_flows_shouldDelegateAndReturnViews() {
        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        PageResult<PhysicalWork> page = new PageResult<PhysicalWork>(
                Collections.<PhysicalWork>emptyList(), 0, 1, 10);
        when(physicalWorkService.listForAdmin(isNull(), eq(1), isNull())).thenReturn(page);
        assertThat(adminPhysicalController.list(null, 1, model)).isEqualTo("admin/physical-list");
        assertThat(model.getAttribute("keyword")).isEqualTo("");

        assertThat(adminPhysicalController.createForm(model)).isEqualTo("admin/physical-form");
        assertThat(model.getAttribute("editing")).isEqualTo(false);

        PhysicalWork created = new PhysicalWork();
        created.setId(5L);
        LibraryUserPrincipal principal = new LibraryUserPrincipal(9L, "admin", "h", Role.ADMIN);
        when(physicalWorkService.create(eq("T"), eq("A"), eq(ContentType.BOOK),
                isNull(), isNull(), eq(9L))).thenReturn(created);
        assertThat(adminPhysicalController.create(
                "T", "A", ContentType.BOOK, null, null, principal, ra))
                .isEqualTo("redirect:/admin/physical/5/copies");

        PhysicalWork work = new PhysicalWork();
        work.setId(5L);
        work.setTitle("书");
        when(physicalWorkService.requireById(5L)).thenReturn(work);
        assertThat(adminPhysicalController.editForm(5L, model)).isEqualTo("admin/physical-form");
        assertThat(adminPhysicalController.edit(
                5L, "T2", "A2", ContentType.JOURNAL, "d", "i", ra))
                .isEqualTo("redirect:/admin/physical");
        verify(physicalWorkService).update(5L, "T2", "A2", ContentType.JOURNAL, "d", "i");

        assertThat(adminPhysicalController.delete(5L, ra)).isEqualTo("redirect:/admin/physical");
        verify(physicalWorkService).softDelete(5L);

        when(physicalWorkService.listCopies(5L)).thenReturn(Collections.<PhysicalCopy>emptyList());
        assertThat(adminPhysicalController.copies(5L, model)).isEqualTo("admin/physical-copies");
        assertThat(adminPhysicalController.addCopy(5L, "C1", ra))
                .isEqualTo("redirect:/admin/physical/5/copies");
        verify(physicalWorkService).addCopy(5L, "C1");
        assertThat(adminPhysicalController.deleteCopy(8L, 5L, ra))
                .isEqualTo("redirect:/admin/physical/5/copies");
        verify(physicalWorkService).softDeleteCopy(8L);
    }

    @Test
    @DisplayName("电子书管理-列表创建编辑删除应调用服务并返回视图")
    void digitalAdmin_flows_shouldDelegateAndReturnViews() {
        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        PageResult<DigitalItem> page = new PageResult<DigitalItem>(
                Collections.<DigitalItem>emptyList(), 0, 1, 10);
        when(digitalItemService.listForAdmin(eq("k"), eq(1), isNull())).thenReturn(page);
        assertThat(adminDigitalController.list("k", 1, model)).isEqualTo("admin/digital-list");
        assertThat(model.getAttribute("keyword")).isEqualTo("k");

        assertThat(adminDigitalController.createForm(model)).isEqualTo("admin/digital-form");

        MultipartFile file = mock(MultipartFile.class);
        LibraryUserPrincipal principal = new LibraryUserPrincipal(1L, "a", "h", Role.ADMIN);
        assertThat(adminDigitalController.create(
                "T", "A", ContentType.BOOK, null, null, file, principal, ra))
                .isEqualTo("redirect:/admin/digital");
        verify(digitalItemService).create(eq("T"), eq("A"), eq(ContentType.BOOK),
                isNull(), isNull(), eq(file), eq(1L));

        DigitalItem item = new DigitalItem();
        item.setId(3L);
        when(digitalItemService.requireById(3L)).thenReturn(item);
        assertThat(adminDigitalController.editForm(3L, model)).isEqualTo("admin/digital-form");
        assertThat(adminDigitalController.edit(
                3L, "T", "A", ContentType.BOOK, null, null, null, ra))
                .isEqualTo("redirect:/admin/digital");
        verify(digitalItemService).update(3L, "T", "A", ContentType.BOOK, null, null, null);
        assertThat(adminDigitalController.delete(3L, ra)).isEqualTo("redirect:/admin/digital");
        verify(digitalItemService).softDelete(3L);
    }

    @Test
    @DisplayName("用户管理-列表授撤ADMIN应调用服务")
    void userAdmin_flows_shouldDelegate() {
        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        when(userService.listUsers()).thenReturn(Collections.<User>emptyList());
        assertThat(adminUserController.list(model)).isEqualTo("admin/users");
        assertThat(adminUserController.grantAdmin(2L, ra)).isEqualTo("redirect:/admin/users");
        verify(userService).grantAdmin(2L);
        assertThat(adminUserController.revokeAdmin(2L, ra)).isEqualTo("redirect:/admin/users");
        verify(userService).revokeAdmin(2L);
    }
}
