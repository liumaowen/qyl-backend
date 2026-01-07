package com.example.qylbackend.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ApiParseService {

    private final WebClient webClient;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final Random random = new Random();

    public ApiParseService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("").build();
    }

    /**
     * 获取第一页的重定向URL
     * @param originalUrl 原始URL
     * @return 重定向后的URL
     */
    public Mono<String> getFirstUrl(String originalUrl) {
        return fetchWithRetry(originalUrl)
                .map(html -> parseFirstUrlFromHtml(html, originalUrl))
                .defaultIfEmpty(originalUrl);
    }

    /**
     * 获取第二页的URL
     * @param firstUrl 第一页URL
     * @return 第二页URL
     */
    public Mono<String> getSecondUrl(String firstUrl) {
        return fetchWithRetry(firstUrl)
                .map(this::parseSecondUrlFromHtml)
                .defaultIfEmpty("无法从第一页面解析第二地址");
    }

    /**
     * 带重试机制的HTTP请求
     * @param url 请求URL
     * @return HTML内容
     */
    private Mono<String> fetchWithRetry(String url) {
        return Mono.fromCallable(() -> {
            for (int i = 0; i < MAX_RETRIES; i++) {
                try {
                    String html = Jsoup.connect(url)
                            .userAgent(USER_AGENT)
                            .timeout(30000) // 30秒超时
                            .get()
                            .html();

                    if (html != null && !html.trim().isEmpty()) {
                        return html;
                    }
                } catch (Exception e) {
                    if (i == MAX_RETRIES - 1) {
                        throw e;
                    }
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("请求被中断", ie);
                    }
                }
            }
            throw new RuntimeException("所有重试均失败");
        });
    }

    /**
     * 从HTML中解析第一重定向URL
     * @param html HTML内容
     * @param originalUrl 原始URL（用于提取主机名和路径）
     * @return 解析后的URL
     */
    private String parseFirstUrlFromHtml(String html, String originalUrl) {
        try {
            Document doc = Jsoup.parse(html);
            Elements scripts = doc.select("script");

            // 查找包含window.location.href的script标签
            for (Element script : scripts) {
                String scriptContent = script.html();

                // Case 1: 简单的window.location.href重定向
                if (scriptContent.contains("window.location.href")) {
                    Pattern pattern = Pattern.compile("window\\.location\\.href = '([^']+)'");
                    Matcher matcher = pattern.matcher(scriptContent);
                    if (matcher.find()) {
                        String redirectedUrl = matcher.group(1);
                        if (redirectedUrl != null) {
                            return redirectedUrl;
                        }
                    }
                }

                // Case 2: 查找目标服务器地址
                if (scriptContent.contains("strU = \"http://")) {
                    // 提取目标服务器
                    Pattern serverPattern = Pattern.compile("strU = \"http://([^\"]+)\"");
                    Matcher serverMatcher = serverPattern.matcher(scriptContent);
                    if (serverMatcher.find()) {
                        String baseUrl = "http://" + serverMatcher.group(1);

                        // 提取参数解析逻辑
                        Pattern dPattern = Pattern.compile("btoa\\(([^)]+)\\)");
                        Matcher dMatcher = dPattern.matcher(scriptContent);

                        if (dMatcher.find() && dMatcher.find()) { // 查找两次btoa调用
                            // 构建重定向URL
                            String hostname = extractHostname(originalUrl);
                            String path = extractPath(originalUrl);

                            // Base64编码（Java的Base64编码需要处理）
                            String encodedHostname = base64Encode(hostname);
                            String encodedPath = base64Encode(path);

                            return baseUrl + "?d=" + encodedHostname + "&p=" + encodedPath;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析第一页URL时出错: " + e.getMessage());
        }

        return originalUrl;
    }

    /**
     * 从HTML中解析第二页URL
     * @param html HTML内容
     * @return 第二页URL
     */
    private String parseSecondUrlFromHtml(String html) {
        try {
            Document doc = Jsoup.parse(html);
            Elements scripts = doc.select("script");

            String scriptContentWithDomains = null;
            for (Element script : scripts) {
                if (script.html().contains("mainDomains")) {
                    scriptContentWithDomains = script.html();
                    break;
                }
            }

            if (scriptContentWithDomains != null) {
                // 使用正则表达式提取mainDomains数组
                Pattern pattern = Pattern.compile("const mainDomains = (\\[[^\\]]+\\])");
                Matcher matcher = pattern.matcher(scriptContentWithDomains);

                if (matcher.find()) {
                    String domainsJson = matcher.group(1).replace("'", "\"");
                    // 解析JSON并模拟JavaScript逻辑
                    String[] mainDomains = parseJsonArray(domainsJson);

                    if (mainDomains != null && mainDomains.length > 0) {
                        // 生成随机子域名
                        String subdomain = generateRandomSubdomain();

                        // 随机选择主域名
                        String mainDomain = mainDomains[random.nextInt(mainDomains.length)];

                        // 构建最终URL
                        return "https://" + subdomain + "." + mainDomain + "/";
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析第二页URL时出错: " + e.getMessage());
        }

        return "无法从第一页面解析第二地址";
    }

    /**
     * 提取主机名
     */
    private String extractHostname(String url) {
        Pattern pattern = Pattern.compile("^https?://([^/]+)");
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 提取路径（包括查询参数）
     */
    private String extractPath(String url) {
        int pathIndex = url.indexOf('/', 8); // 跳过 http:// 或 https://
        return pathIndex == -1 ? "" : url.substring(pathIndex);
    }

    /**
     * Base64编码（简化版本）
     */
    private String base64Encode(String text) {
        return java.util.Base64.getEncoder().encodeToString(text.getBytes());
    }

    /**
     * 解析JSON数组（简化版本）
     */
    private String[] parseJsonArray(String json) {
        try {
            // 移除数组符号并分割
            json = json.trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1);
            }

            // 分割并清理元素
            String[] elements = json.split(",");
            String[] result = new String[elements.length];

            for (int i = 0; i < elements.length; i++) {
                result[i] = elements[i].trim().replaceAll("^\"|\"$", "");
            }

            return result;
        } catch (Exception e) {
            System.err.println("解析JSON数组失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 生成随机子域名（15个字符）
     */
    private String generateRandomSubdomain() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 15; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }
}