package com.example.qylbackend.dto;

/**
 * 草榴配置数据（从 cao4.ai 解析流程中获取）
 * 完整流程: cao4.ai → page.html (config_data) → jumpUrl → jump.html (__ENC_HTML) → fianl.html (__NUXT_DATA__)
 */
public record CaoliuConfig(
        // === 来自 page.html 的 config_data ===
        int time,
        String homeAddress,
        String jumpUrl,
        String fastUrl,
        boolean autoRedirect,
        String pageTitle,
        String pageSubtitle,
        String badgeText,
        String saveTipText,
        String countdownSuffix,
        String mainButtonText,
        String fastButtonText,
        String footerText,
        String recommendText,
        String copiedText,

        // === 来自 fianl.html 的 __NUXT_DATA__ ===
        String tenantId,
        int webSiteId,
        String aesKey0,

        // === 从 resourceDomains 中提取的单个域名 ===
        String imgDomain,
        String videoDomain,
        String baseUrl
) {}
