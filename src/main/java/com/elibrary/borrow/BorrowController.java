package com.elibrary.borrow;

import com.elibrary.user.LibraryUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class BorrowController {

    private final BorrowService borrowService;

    public BorrowController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @PostMapping("/borrow/works/{workId}")
    public String borrow(@PathVariable("workId") Long workId,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        Long userId = currentUserId(authentication);
        borrowService.borrowByWork(userId, workId);
        redirectAttributes.addFlashAttribute("message", "借阅成功");
        return "redirect:/my-borrows";
    }

    @GetMapping("/my-borrows")
    public String myBorrows(Authentication authentication, Model model) {
        Long userId = currentUserId(authentication);
        List<BorrowRecordView> records = borrowService.listMyBorrows(userId);
        model.addAttribute("pageTitle", "我的借阅");
        model.addAttribute("records", records);
        return "borrow/my-borrows";
    }

    @PostMapping("/borrow/records/{id}/return")
    public String returnCopy(@PathVariable("id") Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        Long userId = currentUserId(authentication);
        borrowService.returnByRecordId(userId, id);
        redirectAttributes.addFlashAttribute("message", "归还成功");
        return "redirect:/my-borrows";
    }

    private Long currentUserId(Authentication authentication) {
        LibraryUserPrincipal principal = (LibraryUserPrincipal) authentication.getPrincipal();
        return principal.getId();
    }
}
