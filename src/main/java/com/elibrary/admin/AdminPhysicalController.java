package com.elibrary.admin;

import com.elibrary.common.PageResult;
import com.elibrary.physical.ContentType;
import com.elibrary.physical.PhysicalCopy;
import com.elibrary.physical.PhysicalWork;
import com.elibrary.physical.PhysicalWorkService;
import com.elibrary.user.LibraryUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/physical")
public class AdminPhysicalController {

    private final PhysicalWorkService physicalWorkService;

    public AdminPhysicalController(PhysicalWorkService physicalWorkService) {
        this.physicalWorkService = physicalWorkService;
    }

    @GetMapping
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                       Model model) {
        PageResult<PhysicalWork> result = physicalWorkService.listForAdmin(keyword, page, null);
        model.addAttribute("pageTitle", "实体书目管理");
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("pageResult", result);
        return "admin/physical-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("pageTitle", "新建实体书目");
        model.addAttribute("work", new PhysicalWork());
        model.addAttribute("contentTypes", ContentType.values());
        model.addAttribute("formAction", "/admin/physical");
        model.addAttribute("editing", false);
        return "admin/physical-form";
    }

    @PostMapping
    public String create(@RequestParam("title") String title,
                         @RequestParam("author") String author,
                         @RequestParam("contentType") ContentType contentType,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam(value = "isbnOrIssn", required = false) String isbnOrIssn,
                         @AuthenticationPrincipal LibraryUserPrincipal principal,
                         RedirectAttributes ra) {
        PhysicalWork created = physicalWorkService.create(
                title, author, contentType, description, isbnOrIssn, principal.getId());
        ra.addFlashAttribute("message", "书目已创建");
        return "redirect:/admin/physical/" + created.getId() + "/copies";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        PhysicalWork work = physicalWorkService.requireById(id);
        model.addAttribute("pageTitle", "编辑实体书目");
        model.addAttribute("work", work);
        model.addAttribute("contentTypes", ContentType.values());
        model.addAttribute("formAction", "/admin/physical/" + id + "/edit");
        model.addAttribute("editing", true);
        return "admin/physical-form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable("id") Long id,
                       @RequestParam("title") String title,
                       @RequestParam("author") String author,
                       @RequestParam("contentType") ContentType contentType,
                       @RequestParam(value = "description", required = false) String description,
                       @RequestParam(value = "isbnOrIssn", required = false) String isbnOrIssn,
                       RedirectAttributes ra) {
        physicalWorkService.update(id, title, author, contentType, description, isbnOrIssn);
        ra.addFlashAttribute("message", "书目已更新");
        return "redirect:/admin/physical";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes ra) {
        physicalWorkService.softDelete(id);
        ra.addFlashAttribute("message", "书目已下架");
        return "redirect:/admin/physical";
    }

    @GetMapping("/{id}/copies")
    public String copies(@PathVariable("id") Long id, Model model) {
        PhysicalWork work = physicalWorkService.requireById(id);
        List<PhysicalCopy> copies = physicalWorkService.listCopies(id);
        model.addAttribute("pageTitle", "复本管理");
        model.addAttribute("work", work);
        model.addAttribute("copies", copies);
        return "admin/physical-copies";
    }

    @PostMapping("/{id}/copies")
    public String addCopy(@PathVariable("id") Long id,
                          @RequestParam("copyNo") String copyNo,
                          RedirectAttributes ra) {
        physicalWorkService.addCopy(id, copyNo);
        ra.addFlashAttribute("message", "复本已添加");
        return "redirect:/admin/physical/" + id + "/copies";
    }

    @PostMapping("/copies/{copyId}/delete")
    public String deleteCopy(@PathVariable("copyId") Long copyId,
                             @RequestParam("workId") Long workId,
                             RedirectAttributes ra) {
        physicalWorkService.softDeleteCopy(copyId);
        ra.addFlashAttribute("message", "复本已下架");
        return "redirect:/admin/physical/" + workId + "/copies";
    }
}
