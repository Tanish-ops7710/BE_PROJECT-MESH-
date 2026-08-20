package com.demo.upimesh.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping({"/dashboard", "/developer", "/admin"})
    public String dashboard() {
        return "dashboard";
    }
}
