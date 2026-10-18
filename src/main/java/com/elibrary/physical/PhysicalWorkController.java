package com.elibrary.physical;

import com.elibrary.common.PageResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

@Controller
public class PhysicalWorkController {

    private static final String SEARCH_TIP = "请输入有效搜索信息";

    private final PhysicalWorkService physicalWorkService;

    public PhysicalWorkController(PhysicalWorkService physicalWorkService) {
        this.physicalWorkService = physicalWorkService;
    }

    @GetMapping("/physical")
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                       HttpServletRequest request,
                       Model model) {
        model.addAttribute("pageTitle", "实体馆藏");
        model.addAttribute("keyword", keyword == null ? "" : keyword);

        boolean searchSubmitted = request.getParameterMap().containsKey("keyword");
        String kw = PhysicalWorkService.normalizeKeyword(keyword);

        if (!searchSubmitted) {
            model.addAttribute("pageResult", emptyPage(page));
            model.addAttribute("awaitingSearch", true);
            return "physical/list";
        }
        if (kw == null) {
            model.addAttribute("pageResult", emptyPage(page));
            model.addAttribute("searchError", SEARCH_TIP);
            return "physical/list";
        }

        model.addAttribute("pageResult", physicalWorkService.searchForReader(kw, page, null));
        return "physical/list";
    }

    @GetMapping("/physical/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        PhysicalWork work = physicalWorkService.getActiveDetail(id);
        model.addAttribute("pageTitle", work.getTitle());
        model.addAttribute("work", work);
        return "physical/detail";
    }

    private static PageResult<PhysicalWork> emptyPage(Integer page) {
        int pageNum = page == null || page < 1 ? 1 : page;
        return new PageResult<PhysicalWork>(Collections.<PhysicalWork>emptyList(), 0L, pageNum, 10);
    }
}
