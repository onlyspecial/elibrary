package com.elibrary.web;

import com.elibrary.borrow.BorrowController;
import com.elibrary.borrow.BorrowRecordView;
import com.elibrary.borrow.BorrowService;
import com.elibrary.common.BusinessException;
import com.elibrary.common.ExceptionLevel;
import com.elibrary.common.ExceptionMessages;
import com.elibrary.common.GlobalExceptionHandler;
import com.elibrary.common.PageResult;
import com.elibrary.digital.DigitalItem;
import com.elibrary.digital.DigitalItemController;
import com.elibrary.digital.DigitalItemService;
import com.elibrary.file.LocalPdfStorage;
import com.elibrary.physical.PhysicalWork;
import com.elibrary.physical.PhysicalWorkController;
import com.elibrary.physical.PhysicalWorkService;
import com.elibrary.user.AuthController;
import com.elibrary.user.LibraryUserDetailsService;
import com.elibrary.user.LibraryUserPrincipal;
import com.elibrary.user.Role;
import com.elibrary.user.User;
import com.elibrary.user.UserMapper;
import com.elibrary.user.UserService;
import com.elibrary.user.dto.RegisterForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicControllerTest {

    @Mock
    PhysicalWorkService physicalWorkService;
    @Mock
    DigitalItemService digitalItemService;
    @Mock
    LocalPdfStorage localPdfStorage;
    @Mock
    BorrowService borrowService;
    @Mock
    UserService userService;
    @Mock
    UserMapper userMapper;

    @Test
    @DisplayName("实体公开页-未搜索不查库；有关键词才搜索；详情正常")
    void physicalController_listAndDetail_shouldReturnViews() {
        PhysicalWorkController controller = new PhysicalWorkController(physicalWorkService);
        Model model = new ConcurrentModel();
        MockHttpServletRequest idle = new MockHttpServletRequest();
        assertThat(controller.list(null, 1, idle, model)).isEqualTo("physical/list");
        assertThat(model.getAttribute("awaitingSearch")).isEqualTo(true);
        assertThat(model.getAttribute("keyword")).isEqualTo("");

        MockHttpServletRequest emptySearch = new MockHttpServletRequest();
        emptySearch.addParameter("keyword", "  ");
        Model emptyModel = new ConcurrentModel();
        assertThat(controller.list("  ", 1, emptySearch, emptyModel)).isEqualTo("physical/list");
        assertThat(emptyModel.getAttribute("searchError")).isEqualTo("请输入有效搜索信息");

        PageResult<PhysicalWork> page = new PageResult<PhysicalWork>(
                Collections.<PhysicalWork>emptyList(), 0, 1, 10);
        when(physicalWorkService.searchForReader(eq("活着"), eq(1), isNull())).thenReturn(page);
        MockHttpServletRequest searched = new MockHttpServletRequest();
        searched.addParameter("keyword", "活着");
        Model searchModel = new ConcurrentModel();
        assertThat(controller.list("活着", 1, searched, searchModel)).isEqualTo("physical/list");
        verify(physicalWorkService).searchForReader(eq("活着"), eq(1), isNull());

        PhysicalWork work = new PhysicalWork();
        work.setId(1L);
        work.setTitle("书名");
        when(physicalWorkService.getActiveDetail(1L)).thenReturn(work);
        assertThat(controller.detail(1L, model)).isEqualTo("physical/detail");
        assertThat(model.getAttribute("pageTitle")).isEqualTo("书名");
    }

    @Test
    @DisplayName("电子书公开页-搜索与详情预览应inline PDF")
    void digitalController_listDetailPreview_shouldInlinePdf() {
        DigitalItemController controller = new DigitalItemController(digitalItemService, localPdfStorage);
        Model model = new ConcurrentModel();
        PageResult<DigitalItem> page = new PageResult<DigitalItem>(
                Collections.<DigitalItem>emptyList(), 0, 1, 10);
        when(digitalItemService.searchForReader(eq("q"), eq(1), isNull())).thenReturn(page);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter("keyword", "q");
        assertThat(controller.list("q", 1, req, model)).isEqualTo("digital/list");
        assertThat(model.getAttribute("keyword")).isEqualTo("q");

        DigitalItem item = new DigitalItem();
        item.setId(2L);
        item.setTitle("电子书");
        item.setPdfPath("a.pdf");
        when(digitalItemService.getActiveDetail(2L)).thenReturn(item);
        assertThat(controller.detail(2L, model)).isEqualTo("digital/detail");

        when(digitalItemService.requireActive(2L)).thenReturn(item);
        Resource resource = new ByteArrayResource("%PDF".getBytes());
        when(localPdfStorage.asResource("a.pdf")).thenReturn(resource);
        ResponseEntity<Resource> response = controller.preview(2L);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("inline");
        assertThat(response.getBody()).isSameAs(resource);
    }

    @Test
    @DisplayName("借阅控制器-借阅我的借阅归还应调用服务")
    void borrowController_flows_shouldDelegate() {
        BorrowController controller = new BorrowController(borrowService);
        LibraryUserPrincipal principal = new LibraryUserPrincipal(7L, "u", "h", Role.READER);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        RedirectAttributes ra = new RedirectAttributesModelMap();
        Model model = new ConcurrentModel();

        assertThat(controller.borrow(3L, auth, ra)).isEqualTo("redirect:/my-borrows");
        verify(borrowService).borrowByWork(7L, 3L);

        when(borrowService.listMyBorrows(7L)).thenReturn(Collections.<BorrowRecordView>emptyList());
        assertThat(controller.myBorrows(auth, model)).isEqualTo("borrow/my-borrows");

        assertThat(controller.returnCopy(9L, auth, ra)).isEqualTo("redirect:/my-borrows");
        verify(borrowService).returnByRecordId(7L, 9L);
    }

    @Test
    @DisplayName("认证控制器-注册成功失败与登录页")
    void authController_registerBranches_shouldHandle() {
        AuthController controller = new AuthController(userService);
        Model model = new ConcurrentModel();
        assertThat(controller.index()).isEqualTo("index");
        assertThat(controller.loginPage()).isEqualTo("auth/login");
        assertThat(controller.registerPage(model)).isEqualTo("auth/register");

        RegisterForm form = new RegisterForm();
        form.setUsername("alice");
        form.setPassword("secret1");
        BindingResult errors = new BeanPropertyBindingResult(form, "form");
        errors.rejectValue("username", "size");
        assertThat(controller.register(form, errors, new RedirectAttributesModelMap()))
                .isEqualTo("auth/register");

        BindingResult ok = new BeanPropertyBindingResult(form, "form");
        RedirectAttributes ra = new RedirectAttributesModelMap();
        assertThat(controller.register(form, ok, ra)).isEqualTo("redirect:/login");
        verify(userService).register("alice", "secret1");

        BindingResult dup = new BeanPropertyBindingResult(form, "form");
        doThrow(new BusinessException("用户名已存在")).when(userService).register(anyString(), anyString());
        assertThat(controller.register(form, dup, ra)).isEqualTo("auth/register");
        assertThat(dup.hasFieldErrors("username")).isTrue();
    }

    @Test
    @DisplayName("注册-ERROR级业务异常-字段错误应为网络异常文案")
    void register_whenErrorBusinessException_shouldRejectWithNetworkMessage() {
        AuthController controller = new AuthController(userService);
        RegisterForm form = new RegisterForm();
        form.setUsername("alice");
        form.setPassword("secret1");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        RedirectAttributes ra = new RedirectAttributesModelMap();
        doThrow(new BusinessException(ExceptionLevel.ERROR, "内部错误"))
                .when(userService).register(anyString(), anyString());

        assertThat(controller.register(form, bindingResult, ra)).isEqualTo("auth/register");
        assertThat(bindingResult.getFieldError("username").getDefaultMessage())
                .isEqualTo(ExceptionMessages.NETWORK_ERROR);
    }

    @Test
    @DisplayName("全局异常-有Referer时应回跳否则到error")
    void globalExceptionHandler_shouldRedirectByReferer() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        BusinessException ex = new BusinessException("出错了");

        Object noReferer = handler.handleBusinessException(ex, request, ra);
        assertThat(((RedirectView) noReferer).getUrl()).isEqualTo("/error");

        request.addHeader("Referer", "http://localhost/physical/1");
        Object withReferer = handler.handleBusinessException(ex, request, new RedirectAttributesModelMap());
        assertThat(((RedirectView) withReferer).getUrl()).isEqualTo("http://localhost/physical/1");
    }

    @Test
    @DisplayName("用户详情服务-存在与不存在用户")
    void libraryUserDetailsService_loadUser_shouldWrapOrThrow() {
        LibraryUserDetailsService service = new LibraryUserDetailsService(userMapper);
        when(userMapper.findByUsername("missing")).thenReturn(null);
        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("bob");
        user.setPasswordHash("HASH");
        user.setRole(Role.READER);
        when(userMapper.findByUsername("bob")).thenReturn(user);
        LibraryUserPrincipal principal = (LibraryUserPrincipal) service.loadUserByUsername("bob");
        assertThat(principal.getId()).isEqualTo(1L);
        assertThat(principal.getUsername()).isEqualTo("bob");
        assertThat(principal.getPassword()).isEqualTo("HASH");
        assertThat(principal.getRole()).isEqualTo(Role.READER);
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_READER");
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("PageResult-总页数边界")
    void pageResult_getTotalPages_shouldHandleZeroSize() {
        PageResult<String> zeroSize = new PageResult<String>(Collections.<String>emptyList(), 5, 1, 0);
        assertThat(zeroSize.getTotalPages()).isEqualTo(0);
        PageResult<String> normal = new PageResult<String>(Collections.<String>emptyList(), 21, 1, 10);
        assertThat(normal.getTotalPages()).isEqualTo(3);
    }
}
