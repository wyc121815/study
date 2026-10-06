package com.example.backend.controller;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 示例接口,用于验证前后端联通。
 */
@RestController
@RequestMapping("/api")
public class HelloController {

    /**
     * GET /api/hello?name=xxx
     */
    @GetMapping("/hello")
    public Map<String, Object> hello(@RequestParam(defaultValue = "World") String name) {
        return Map.of(
                "message", "Hello, " + name + "!",
                "service", "backend",
                "time", OffsetDateTime.now().toString()
        );
    }
}

