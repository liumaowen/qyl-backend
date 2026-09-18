package com.example.qylbackend.controller;

import com.example.qylbackend.model.ConfigEntry;
import com.example.qylbackend.repository.ConfigEntryRepository;
import com.example.qylbackend.service.CaoliuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 草榴配置 API
 */
@RestController
@RequestMapping("/api/caoliu")
public class CaoliuController {

    private static final Logger logger = LoggerFactory.getLogger(CaoliuController.class);

    @Autowired
    private CaoliuService caoliuService;
    @Autowired
    private ConfigEntryRepository configEntryRepository; // 注入配置表Repository

    /**
     * 获取完整配置（4步流程）
     * @param url 初始 URL，默认 https://cao4.ai
     * @return 完整配置 JSON
     */
    @GetMapping("/config")
    public Mono<Map<String, Object>> getConfig(@RequestParam(required = false) String url) {
        if (url == null || url.isEmpty()) {
            ConfigEntry con = configEntryRepository.findByKey("caoliuInitialUrl");
            if (con != null) {
                url = con.getValue();
            }
        }
        if (url == null || url.isEmpty()) {
            url = "https://cao4.ai";
        }

        logger.info("获取草榴配置，初始 URL: {}", url);

        Map<String, Object> result = new HashMap<>();
        return caoliuService.getFullConfig(url)
                .map(config -> {
                    result.put("success", true);
                    result.put("data", config);
                    return result;
                })
                .onErrorResume(e -> {
                    logger.error("获取配置失败: {}", e.getMessage(), e);
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    return Mono.just(result);
                });
    }
}
