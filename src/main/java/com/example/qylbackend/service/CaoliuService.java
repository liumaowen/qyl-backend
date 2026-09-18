package com.example.qylbackend.service;

import com.example.qylbackend.dto.CaoliuConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 草榴配置解析服务
 * 完整流程: cao4.ai → page.html (config_data) → jumpUrl → jump.html (__ENC_HTML) → fianl.html (__NUXT_DATA__)
 */
@Service
public class CaoliuService {

    private static final Logger logger = LoggerFactory.getLogger(CaoliuService.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int TIMEOUT = 30000;
    private static final Random random = new Random();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // SSL 信任所有证书（测试用）
    private static final SSLSocketFactory TRUST_ALL_SOCKET_FACTORY;

    static {
        try {
            TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            TRUST_ALL_SOCKET_FACTORY = sc.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("初始化 SSL 失败", e);
        }
    }

    /**
     * 获取完整配置（4步流程）
     */
    public Mono<CaoliuConfig> getFullConfig(String initialUrl) {
        return Mono.fromCallable(() -> {
            logger.info("步骤1: 获取重定向地址...");

            // 步骤1: 获取重定向地址
            String redirectUrl = getRedirectLocation(initialUrl);
            logger.info("✓ 重定向地址: {}", redirectUrl);

            // 步骤2: 获取 page.html，解析 config_data
            logger.info("步骤2: 解析 page.html 获取 config_data...");
            PageConfig pageConfig = parsePageConfig(redirectUrl);
            logger.info("✓ homeAddress: {}", pageConfig.homeAddress);

            // 步骤3: 从 jumpUrl 中随机选一个并展开通配符
            logger.info("步骤3: 选择 jumpUrl...");
            String jumpUrl = getEntryUrl(pageConfig.jumpUrl);
            logger.info("✓ 选中的 jumpUrl: {}", jumpUrl);

            // 步骤4: 访问 jumpUrl，解析 __ENC_HTML
            logger.info("步骤4: 解析 __ENC_HTML...");
            String encHtml = getEncHtml(jumpUrl);
            String finalHtml = decodeEncHtml(encHtml);
            logger.info("✓ fianl.html 解码成功");

            // 步骤5: 解析 fianl.html 中的 __NUXT_DATA__
            logger.info("步骤5: 解析 __NUXT_DATA__...");
            NuxtData nuxtData = parseNuxtData(finalHtml);
            logger.info("✓ tenantId: {}, webSiteId: {}", nuxtData.tenantId, nuxtData.webSiteId);

            // 合并配置
            CaoliuConfig config = mergeConfigs(pageConfig, nuxtData);
            logger.info("========== 配置解析完成 ==========");
            logger.info("imgDomain: {}", config.imgDomain());
            logger.info("videoDomain: {}", config.videoDomain());
            logger.info("baseUrl: {}", config.baseUrl());

            return config;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 步骤1: 获取重定向地址
     */
    private String getRedirectLocation(String url) throws Exception {
        org.jsoup.Connection.Response response = Jsoup.connect(url)
                .sslSocketFactory(TRUST_ALL_SOCKET_FACTORY)
                .userAgent(USER_AGENT)
                .followRedirects(false)
                .timeout(TIMEOUT)
                .execute();

        String location = response.header("Location");
        if (location == null || location.isEmpty()) {
            throw new RuntimeException("未找到重定向地址");
        }
        return location;
    }

    /**
     * 步骤2: 解析 page.html 中的 config_data
     */
    private PageConfig parsePageConfig(String url) throws Exception {
        String html = Jsoup.connect(url)
                .sslSocketFactory(TRUST_ALL_SOCKET_FACTORY)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT)
                .get()
                .html();

        // 匹配: var config_data = "..."
        Pattern pattern = Pattern.compile("var\\s+config_data\\s*=\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);

        if (!matcher.find()) {
            throw new RuntimeException("无法从 HTML 中提取 config_data");
        }

        String encodedData = matcher.group(1);
        String decodedJson = decodeConfigData(encodedData);
        logger.debug("config_data 解码后: {}", decodedJson.substring(0, Math.min(200, decodedJson.length())));

        Map<String, Object> jsonMap = objectMapper.readValue(decodedJson, Map.class);
        return PageConfig.fromJson(jsonMap);
    }

    /**
     * 解码 config_data - 移除字母，保留数字转字符
     */
    private String decodeConfigData(String payload) {
        String[] parts = payload.split("[a-zA-Z]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                try {
                    int code = Integer.parseInt(part);
                    sb.append((char) code);
                } catch (NumberFormatException e) {
                    // 忽略无法解析的部分
                }
            }
        }
        return sb.toString();
    }

    /**
     * 从 jumpUrl 中随机选一个并展开通配符
     */
    private String getEntryUrl(String jumpUrl) {
        if (jumpUrl == null || jumpUrl.isEmpty()) {
            throw new RuntimeException("jumpUrl 为空");
        }

        String[] urls = jumpUrl.split("[;；]");
        List<String> validUrls = new ArrayList<>();
        for (String url : urls) {
            String trimmed = url.trim();
            if (!trimmed.isEmpty()) {
                validUrls.add(trimmed);
            }
        }

        if (validUrls.isEmpty()) {
            throw new RuntimeException("jumpUrl 列表为空");
        }

        String selected = validUrls.get(random.nextInt(validUrls.size()));
        return formatWildcardDomain(selected);
    }

    /**
     * 处理通配符域名
     */
    private String formatWildcardDomain(String url) {
        if (!url.contains("*")) {
            return url;
        }

        // 匹配协议和域名
        Pattern pattern = Pattern.compile("^(https?://)?([^/?#]+)(.*)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(url);

        if (!matcher.find()) {
            return url;
        }

        String protocol = matcher.group(1) != null ? matcher.group(1) : "";
        String hostname = matcher.group(2);
        String path = matcher.group(3) != null ? matcher.group(3) : "";

        if (!hostname.startsWith("*.")) {
            return url;
        }

        String randomSubdomain = generateRandomSubdomain();
        String newHostname = hostname.substring(2);
        return protocol + randomSubdomain + "." + newHostname + path;
    }

    /**
     * 生成随机子域名（9位字母数字）
     */
    private String generateRandomSubdomain() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        sb.append(chars.charAt(random.nextInt(26))); // 首字母
        for (int i = 1; i < 9; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 步骤3: 获取 jump.html 中的 __ENC_HTML
     */
    private String getEncHtml(String url) throws Exception {
        String html = Jsoup.connect(url)
                .sslSocketFactory(TRUST_ALL_SOCKET_FACTORY)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT)
                .get()
                .html();

        // 匹配: window.__ENC_HTML = "..."
        Pattern pattern = Pattern.compile("window\\.__ENC_HTML\\s*=\\s*\"([^\"]+)\"", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);

        if (!matcher.find()) {
            throw new RuntimeException("无法从 jump.html 中提取 __ENC_HTML");
        }

        return matcher.group(1);
    }

    /**
     * 解码 __ENC_HTML
     */
    private String decodeEncHtml(String encodedData) {
        logger.debug("__ENC_HTML 编码数据长度: {}", encodedData.length());
        String decoded = decodeConfigData(encodedData);
        logger.debug("解码后的 fianl.html 长度: {}", decoded.length());
        return decoded;
    }

    /**
     * 步骤4: 解析 fianl.html 中的 __NUXT_DATA__
     */
    private NuxtData parseNuxtData(String html) throws Exception {
        // 匹配 __NUXT_DATA__
        Pattern pattern = Pattern.compile("<script[^>]*id=\"__NUXT_DATA__\"[^>]*>([\\s\\S]*?)</script>");
        Matcher matcher = pattern.matcher(html);

        if (!matcher.find()) {
            throw new RuntimeException("无法从 fianl.html 中提取 __NUXT_DATA__");
        }

        String nuxtDataJson = matcher.group(1);
        logger.debug("__NUXT_DATA__ JSON 长度: {}", nuxtDataJson.length());

        // 解析 devalue 格式
        List<Object> arr = objectMapper.readValue(nuxtDataJson, List.class);
        Map<String, Object> nuxtData = NuxtDataParser.parse(arr);

        return NuxtData.fromMap(nuxtData);
    }

    /**
     * 合并 pageConfig 和 nuxtData
     */
    private CaoliuConfig mergeConfigs(PageConfig pageConfig, NuxtData nuxtData) {

        return new CaoliuConfig(
                // 来自 page.html
                pageConfig.time,
                pageConfig.homeAddress,
                pageConfig.jumpUrl,
                pageConfig.fastUrl,
                pageConfig.autoRedirect,
                pageConfig.pageTitle,
                pageConfig.pageSubtitle,
                pageConfig.badgeText,
                pageConfig.saveTipText,
                pageConfig.countdownSuffix,
                pageConfig.mainButtonText,
                pageConfig.fastButtonText,
                pageConfig.footerText,
                pageConfig.recommendText,
                pageConfig.copiedText,
                // 来自 fianl.html
                nuxtData.tenantId,
                nuxtData.webSiteId,
                nuxtData.aesKey0,
                nuxtData.imgDomain,
                nuxtData.videoDomain,
                nuxtData.baseUrl,
                nuxtData.searchDomain
        );
    }

    /**
     * page.html 中的配置
     */
    private static class PageConfig {
        int time = 5;
        String homeAddress = "aksl.dpdns.org;slkk.dpdns.org";
        String jumpUrl = "";
        String fastUrl = "";
        boolean autoRedirect = true;
        String pageTitle = "保存回家入口，随时找到我们";
        String pageSubtitle = "建议收藏本页，并保存至少一个永久地址";
        String badgeText = "永久入口";
        String saveTipText = "点击上方地址即可复制，请妥善保存";
        String countdownSuffix = "后自动进入本站";
        String mainButtonText = "进入本站";
        String fastButtonText = "进入极速纯净版";
        String footerText = "如当前入口无法访问，请使用上方永久地址重新进入";
        String recommendText = "推荐";
        String copiedText = "已复制";

        static PageConfig fromJson(Map<String, Object> json) {
            PageConfig config = new PageConfig();
//            config.time = json.containsKey("time") ? ((Number) json.get("time")).intValue() : 5;
//            config.homeAddress = config.homeAddress;
            config.jumpUrl = (String) json.getOrDefault("jumpUrl", "");
            config.fastUrl = (String) json.getOrDefault("fastUrl", "");
//            config.autoRedirect = json.containsKey("autoRedirect") ? (Boolean) json.get("autoRedirect") : true;
//            config.pageTitle = (String) json.getOrDefault("pageTitle", config.pageTitle);
//            config.pageSubtitle = (String) json.getOrDefault("pageSubtitle", config.pageSubtitle);
//            config.badgeText = (String) json.getOrDefault("badgeText", config.badgeText);
//            config.saveTipText = (String) json.getOrDefault("saveTipText", config.saveTipText);
//            config.countdownSuffix = (String) json.getOrDefault("countdownSuffix", config.countdownSuffix);
//            config.mainButtonText = (String) json.getOrDefault("mainButtonText", config.mainButtonText);
//            config.fastButtonText = (String) json.getOrDefault("fastButtonText", config.fastButtonText);
//            config.footerText = (String) json.getOrDefault("footerText", config.footerText);
//            config.recommendText = (String) json.getOrDefault("recommendText", config.recommendText);
//            config.copiedText = (String) json.getOrDefault("copiedText", config.copiedText);
            return config;
        }
    }

    /**
     * fianl.html 中的 NuxtData
     */
    private static class NuxtData {
        String tenantId = "";
        int webSiteId = 0;
        String jsonApi = "";
        String aesKey0 = "";
        int resourceDomainCount = 0;
        String imgDomain = "";
        String videoDomain = "";
        String baseUrl = "";
        String searchDomain = "";

        static NuxtData fromMap(Map<String, Object> nuxtData) {
            NuxtData data = new NuxtData();

            // 提取 globalStore
            Map<String, Object> pinia = (Map<String, Object>) nuxtData.getOrDefault("pinia", Collections.emptyMap());
            Map<String, Object> globalStore = (Map<String, Object>) pinia.getOrDefault("globalStore", Collections.emptyMap());

            data.tenantId = String.valueOf(globalStore.getOrDefault("tenantId", ""));
            data.webSiteId = globalStore.containsKey("webSiteId") ? ((Number) globalStore.get("webSiteId")).intValue() : 0;
            data.jsonApi = (String) globalStore.getOrDefault("jsonApi", "");
            data.aesKey0 = (String) globalStore.getOrDefault("aesKey0", "");

            // 提取 appConfig
            Map<String, Object> appConfig = (Map<String, Object>) globalStore.getOrDefault("appConfig",
                    globalStore.getOrDefault("appConfigNew", Collections.emptyMap()));

            // 提取 resourceDomains
            List<Map<String, Object>> domains = (List<Map<String, Object>>) appConfig.getOrDefault("resourceDomains", Collections.emptyList());
            data.resourceDomainCount = domains.size();

            // 按 category 分类并提取
            Map<Integer, List<String>> categoryUrls = new HashMap<>();
            for (Map<String, Object> domain : domains) {
                int category = domain.containsKey("category") ? ((Number) domain.get("category")).intValue() : 0;
                String url = (String) domain.getOrDefault("url", "");
                if (!url.isEmpty()) {
                    categoryUrls.computeIfAbsent(category, k -> new ArrayList<>()).add(url);
                }
            }

            // 提取 category 1/2/9 的第一个
            data.imgDomain = categoryUrls.getOrDefault(1, Collections.emptyList()).stream().findFirst().orElse("");
            data.videoDomain = categoryUrls.getOrDefault(2, Collections.emptyList()).stream().findFirst().orElse("");
            data.baseUrl = categoryUrls.getOrDefault(9, Collections.emptyList()).stream().findFirst().orElse("");
            data.searchDomain = categoryUrls.getOrDefault(8, Collections.emptyList()).stream().findFirst().orElse("");
            return data;
        }
    }

    /**
     * NuxtData devalue 格式解析器
     */
    private static class NuxtDataParser {
        private final List<Object> arr;
        private final Map<Integer, Object> resolved = new HashMap<>();

        NuxtDataParser(List<Object> arr) {
            this.arr = arr;
        }

        static Map<String, Object> parse(List<Object> arr) {
            NuxtDataParser parser = new NuxtDataParser(arr);
            Object result = parser.resolve(0);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
            return Collections.emptyMap();
        }

        Object resolve(int idx) {
            if (resolved.containsKey(idx)) {
                return resolved.get(idx);
            }
            if (idx < 0 || idx >= arr.size()) {
                return null;
            }

            Object item = arr.get(idx);

            // 字符串
            if (item instanceof String str) {
                // 类型包装标记
                if (str.equals("ShallowReactive") || str.equals("Reactive") ||
                    str.equals("Set") || str.equals("NuxtError")) {
                    if (idx + 1 < arr.size() && arr.get(idx + 1) instanceof Number) {
                        int nextIdx = ((Number) arr.get(idx + 1)).intValue();
                        Object r = resolve(nextIdx);
                        resolved.put(idx, r);
                        return r;
                    }
                }
                resolved.put(idx, str);
                return str;
            }

            // null/bool
            if (item == null || item instanceof Boolean) {
                resolved.put(idx, item);
                return item;
            }

            // 数字
            if (item instanceof Number) {
                resolved.put(idx, item);
                return item;
            }

            // 数组
            if (item instanceof List<?> list) {

                // 类型包装: ["ShallowReactive", n]
                if (list.size() >= 2 && list.get(0) instanceof String &&
                    (list.get(0).equals("ShallowReactive") || list.get(0).equals("Reactive") || list.get(0).equals("NuxtError"))) {
                    if (list.get(1) instanceof Number) {
                        int nextIdx = ((Number) list.get(1)).intValue();
                        Object r = resolve(nextIdx);
                        resolved.put(idx, r);
                        return r;
                    }
                }

                // Set 类型
                if (list.size() == 1 && "Set".equals(list.get(0))) {
                    List<Object> empty = new ArrayList<>();
                    resolved.put(idx, empty);
                    return empty;
                }

                // 普通数组
                List<Object> result = new ArrayList<>();
                resolved.put(idx, result);
                for (Object v : list) {
                    if (v instanceof Number) {
                        result.add(resolve(((Number) v).intValue()));
                    } else {
                        result.add(v);
                    }
                }
                return result;
            }

            // 对象
            if (item instanceof Map) {
                Map<String, Object> result = new HashMap<>();
                resolved.put(idx, result);
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    Object v = entry.getValue();
                    if (v instanceof Number) {
                        int intVal = ((Number) v).intValue();
                        if (intVal == -1) {
                            result.put(key, null);
                        } else {
                            result.put(key, resolve(intVal));
                        }
                    } else {
                        result.put(key, v);
                    }
                }
                return result;
            }

            resolved.put(idx, item);
            return item;
        }
    }
}
