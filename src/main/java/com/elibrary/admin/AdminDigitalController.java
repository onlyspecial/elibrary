package com.elibrary.admin;

import com.elibrary.common.PageResult;
import com.elibrary.digital.DigitalItem;
import com.elibrary.digital.DigitalItemService;
import com.elibrary.physical.ContentType;
import com.elibrary.user.LibraryUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/digital")
public class AdminDigitalController {

    private final DigitalItemService digitalItemService;

    public AdminDigitalController(DigitalItemService digitalItemService) {
        this.digitalItemService = digitalItemService;
    }

    @GetMapping
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                       Model model) {
        PageResult<DigitalItem> result = digitalItemService.listForAdmin(keyword, page, null);
        model.addAttribute("pageTitle", "电子书管理");
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("pageResult", result);
        return "admin/digital-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("pageTitle", "上传电子书");
        model.addAttribute("item", new DigitalItem());
        model.addAttribute("contentTypes", ContentType.values());
        model.addAttribute("formAction", "/admin/digital");
        model.addAttribute("editing", false);
        return "admin/digital-form";
    }

    @PostMapping
    public String create(@RequestParam("title") String title,
                         @RequestParam("author") String author,
                         @RequestParam("contentType") ContentType contentType,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam(value = "isbnOrIssn", required = false) String isbnOrIssn,
                         @RequestParam("pdfFile") MultipartFile pdfFile,
                         @AuthenticationPrincipal LibraryUserPrincipal principal,
                         RedirectAttributes ra) {
        digitalItemService.create(title, author, contentType, description, isbnOrIssn,
                pdfFile, principal.getId());
        ra.addFlashAttribute("message", "电子书已创建");
        return "redirect:/admin/digital";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        DigitalItem item = digitalItemService.requireById(id);
        model.addAttribute("pageTitle", "编辑电子书");
        model.addAttribute("item", item);
        model.addAttribute("contentTypes", ContentType.values());
        model.addAttribute("formAction", "/admin/digital/" + id + "/edit");
        model.addAttribute("editing", true);
        return "admin/digital-form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable("id") Long id,
                       @RequestParam("title") String title,
                       @RequestParam("author") String author,
                       @RequestParam("contentType") ContentType contentType,
                       @RequestParam(value = "description", required = false) String description,
                       @RequestParam(value = "isbnOrIssn", required = false) String isbnOrIssn,
                       @RequestParam(value = "pdfFile", required = false) MultipartFile pdfFile,
                       RedirectAttributes ra) {
        digitalItemService.update(id, title, author, contentType, description, isbnOrIssn, pdfFile);
        ra.addFlashAttribute("message", "电子书已更新");
        return "redirect:/admin/digital";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes ra) {
        digitalItemService.softDelete(id);
        ra.addFlashAttribute("message", "电子书已下架");
        return "redirect:/admin/digital";
    }
}
