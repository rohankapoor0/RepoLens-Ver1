package com.repolens.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Controller routing user-facing pages: shareable report cards, scoring rubric, and MCP tab.
 */
@Controller
public class ReportCardController {

    @GetMapping("/u/{username}")
    public String reportCard(@PathVariable("username") String username) {
        return "forward:/report.html";
    }

    @GetMapping("/rubric")
    public String scoringRubric() {
        return "forward:/rubric.html";
    }

    @GetMapping({"/evaluator", "/app"})
    public String evaluator() {
        return "forward:/repolens-clean/RepoLensClean.html";
    }

    @GetMapping("/mcp")
    public String mcpTab() {
        return "forward:/mcp.html";
    }

    @GetMapping(value = "/api/mcp/download-info", produces = "application/json")
    @ResponseBody
    public Map<String, Object> downloadInfo() {
        return Map.of(
            "name", "repolens-mcp",
            "version", "1.0.0-SNAPSHOT",
            "releaseUrl", "https://github.com/rohankapoor0/RepoLens-Ver1/releases/latest",
            "jarName", "repolens-mcp.jar",
            "javaRequired", "17+"
        );
    }
}
