package com.elibrary.admin;

import com.elibrary.user.User;
import com.elibrary.user.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        List<User> users = userService.listUsers();
        model.addAttribute("pageTitle", "用户管理");
        model.addAttribute("users", users);
        return "admin/users";
    }

    @PostMapping("/{id}/grant-admin")
    public String grantAdmin(@PathVariable("id") Long id, RedirectAttributes ra) {
        userService.grantAdmin(id);
        ra.addFlashAttribute("message", "已设为管理员");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/revoke-admin")
    public String revokeAdmin(@PathVariable("id") Long id, RedirectAttributes ra) {
        userService.revokeAdmin(id);
        ra.addFlashAttribute("message", "已撤销管理员");
        return "redirect:/admin/users";
    }
}
