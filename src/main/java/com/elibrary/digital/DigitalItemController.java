package com.elibrary.digital;

import com.elibrary.common.PageResult;
import com.elibrary.file.LocalPdfStorage;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

@Controller
public class DigitalItemController {

    private static final String SEARCH_TIP = "请输入有效搜索信息";

    private final DigitalItemService digitalItemService;
    private final LocalPdfStorage localPdfStorage;

    public DigitalItemController(DigitalItemService digitalItemService,
                                 LocalPdfStorage localPdfStorage) {
        this.digitalItemService = digitalItemService;
        this.localPdfStorage = localPdfStorage;
    }

    @GetMapping("/digital")
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                       HttpServletRequest request,
                       Model model) {
        model.addAttribute("pageTitle", "电子书");
        model.addAttribute("keyword", keyword == null ? "" : keyword);

        boolean searchSubmitted = request.getParameterMap().containsKey("keyword");
        String kw = DigitalItemService.normalizeKeyword(keyword);

        if (!searchSubmitted) {
            model.addAttribute("pageResult", emptyPage(page));
            model.addAttribute("awaitingSearch", true);
            return "digital/list";
        }
        if (kw == null) {
            model.addAttribute("pageResult", emptyPage(page));
            model.addAttribute("searchError", SEARCH_TIP);
            return "digital/list";
        }

        model.addAttribute("pageResult", digitalItemService.searchForReader(kw, page, null));
        return "digital/list";
    }

    @GetMapping("/digital/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        DigitalItem item = digitalItemService.getActiveDetail(id);
        model.addAttribute("pageTitle", item.getTitle());
        model.addAttribute("item", item);
        return "digital/detail";
    }

    @GetMapping("/digital/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable("id") Long id) {
        DigitalItem item = digitalItemService.requireActive(id);
        Resource resource = localPdfStorage.asResource(item.getPdfPath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    private static PageResult<DigitalItem> emptyPage(Integer page) {
        int pageNum = page == null || page < 1 ? 1 : page;
        return new PageResult<DigitalItem>(Collections.<DigitalItem>emptyList(), 0L, pageNum, 10);
    }
}
