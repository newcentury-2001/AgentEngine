package com.agentengine.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AssistantPageController {

    @GetMapping("/assistant")
    public String assistantPage() {
        return "forward:/assistant.html";
    }
}
