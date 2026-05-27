package com.game.bunker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {
    @GetMapping("/")
    public String showMainPage() {
        return "redirect:/app.html";
    }

    @GetMapping("/lobbies")
    public String showLobbies() {
        return "redirect:/app.html";
    }
}
