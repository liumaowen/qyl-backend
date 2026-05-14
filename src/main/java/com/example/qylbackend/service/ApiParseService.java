package com.example.qylbackend.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;
import reactor.core.publisher.Mono;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ApiParseService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final Random random = new Random();

    // AES key and IV from the site's cryptedData.js
    private static final String AES_KEY = "gFzviOY0zOxVq1cu";
    private static final String AES_IV = "ZmA0Osl677UdSrl0";

    private static final SSLSocketFactory TRUST_ALL_SOCKET_FACTORY;
    private static final HostnameVerifier ALLOW_ALL_HOSTS = (hostname, session) -> true;

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
            HttpsURLConnection.setDefaultSSLSocketFactory(TRUST_ALL_SOCKET_FACTORY);
            HttpsURLConnection.setDefaultHostnameVerifier(ALLOW_ALL_HOSTS);
        } catch (Exception e) {
            throw new RuntimeException("初始化忽略SSL校验失败", e);
        }
    }

    public ApiParseService() {
    }

    /**
     * 获取第一页的重定向URL
     */
    public Mono<String> getFirstUrl(String originalUrl) {
        return fetchWithRetry(originalUrl)
                .map(html -> parseFirstUrlFromHtml(html, originalUrl))
                .defaultIfEmpty(originalUrl);
    }

    /**
     * 获取第二页的URL（单个，兼容旧接口）
     */
    public Mono<String> getSecondUrl(String firstUrl) {
        return fetchWithRetry(firstUrl)
                .map(this::parseSecondUrlFromHtml)
                .defaultIfEmpty("无法从第一页面解析第二地址");
    }

    /**
     * 获取第二页的URL列表（3个备选）
     */
    public Mono<List<String>> getSecondUrls(String firstUrl) {
        return fetchWithRetry(firstUrl)
                .map(this::parseSecondUrlsFromHtml)
                .defaultIfEmpty(List.of("无法从第一页面解析第二地址"));
    }

    /**
     * 通过第三页获取最终内容网站地址
     */
    public Mono<List<String>> getFinalUrls(String thirdUrl) {
        return Mono.fromCallable(() -> {
            URI uri = URI.create(thirdUrl);
            String protocol = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            // 提取主域名（后两段）
            String mainDomain = extractMainDomain(host);

            // AES加密请求体
            String jsonPayload = "{\"Domain\":\"" + mainDomain + "\"}";
            String encryptedBody = aesEncrypt(jsonPayload);

            // 构建 API URL
            String portSuffix = (port != -1) ? ":" + port : "";
            String apiUrl = protocol + "://" + host + portSuffix + "/Web/GetJumpURL2";

            // 发送 POST 请求（同步，运行在 boundedElastic 线程）
            org.jsoup.Connection.Response response = Jsoup.connect(apiUrl)
                    .sslSocketFactory(TRUST_ALL_SOCKET_FACTORY)
                    .userAgent(USER_AGENT)
                    .header("Content-Type", "text/plain")
                    .requestBody(encryptedBody)
                    .method(org.jsoup.Connection.Method.POST)
                    .timeout(30000)
                    .ignoreContentType(true)
                    .execute();

            String responseBody = response.body();
            if (responseBody == null || responseBody.isEmpty()) {
                return new ArrayList<String>();
            }

            // AES解密响应
            String decrypted = aesDecrypt(responseBody);
            return parseFinalUrls(decrypted);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 提取主域名（取 hostname 最后两段）
     */
    private String extractMainDomain(String hostname) {
        String[] parts = hostname.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return hostname;
    }

    /**
     * 解析最终URL列表
     */
    private List<String> parseFinalUrls(String json) {
        List<String> urls = new ArrayList<>();
        try {
            // 简单解析 jumpDomains 字段
            Pattern pattern = Pattern.compile("\"jumpDomains\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                String jumpDomains = matcher.group(1);
                String[] domains = jumpDomains.split(",");
                for (String domain : domains) {
                    if (!domain.isEmpty()) {
                        urls.add(domain.trim());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析最终URL时出错: " + e.getMessage());
        }
        return urls;
    }

    /**
     * 带重试机制的HTTP请求
     */
    private Mono<String> fetchWithRetry(String url) {
        return Mono.fromCallable(() -> {
            for (int i = 0; i < MAX_RETRIES; i++) {
                try {
                    String html = Jsoup.connect(url)
                            .sslSocketFactory(TRUST_ALL_SOCKET_FACTORY)
                            .userAgent(USER_AGENT)
                            .timeout(30000)
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
     */
    private String parseFirstUrlFromHtml(String html, String originalUrl) {
        try {
            Document doc = Jsoup.parse(html);
            Elements scripts = doc.select("script");

            for (Element script : scripts) {
                String scriptContent = script.html();

                // Case 1: window.location.href 重定向
                if (scriptContent.contains("window.location.href")) {
                    Pattern pattern = Pattern.compile("window\\.location\\.href = '([^']+)'");
                    Matcher matcher = pattern.matcher(scriptContent);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }

                // Case 2: 目标服务器地址 (strU + btoa)
                if (scriptContent.contains("strU = \"http://")) {
                    Pattern serverPattern = Pattern.compile("strU = \"http://([^\"]+)\"");
                    Matcher serverMatcher = serverPattern.matcher(scriptContent);
                    if (serverMatcher.find()) {
                        String baseUrl = "http://" + serverMatcher.group(1);
                        Pattern dPattern = Pattern.compile("btoa\\(([^)]+)\\)");
                        Matcher dMatcher = dPattern.matcher(scriptContent);
                        if (dMatcher.find() && dMatcher.find()) {
                            String hostname = extractHostname(originalUrl);
                            String path = extractPath(originalUrl);
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
     * 从HTML中解析第二页URL（单个）
     */
    private String parseSecondUrlFromHtml(String html) {
        List<String> urls = parseSecondUrlsFromHtml(html);
        return urls.isEmpty() ? "无法从第一页面解析第二地址" : urls.get(0);
    }

    /**
     * 从HTML中解析第二页URL列表（3个备选）
     */
    private List<String> parseSecondUrlsFromHtml(String html) {
        List<String> urls = new ArrayList<>();
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
                Pattern pattern = Pattern.compile("const mainDomains = (\\[[^\\]]+\\])");
                Matcher matcher = pattern.matcher(scriptContentWithDomains);

                if (matcher.find()) {
                    String domainsJson = matcher.group(1).replace("'", "\"");
                    String[] mainDomains = parseJsonArray(domainsJson);

                    if (mainDomains != null && mainDomains.length > 0) {
                        // 生成3个不同的备选URL
                        for (int i = 0; i < 3; i++) {
                            String subdomain = generateRandomSubdomain();
                            String mainDomain = mainDomains[random.nextInt(mainDomains.length)];
                            urls.add("https://" + subdomain + "." + mainDomain + "/");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析第二页URL时出错: " + e.getMessage());
        }
        return urls;
    }

    /**
     * AES加密 (CBC/PKCS5Padding)
     */
    private String aesEncrypt(String plainText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes(), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(AES_IV.getBytes());
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /**
     * AES解密 (CBC/PKCS5Padding)
     */
    private String aesDecrypt(String encryptedText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes(), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(AES_IV.getBytes());
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }

    private String extractHostname(String url) {
        Pattern pattern = Pattern.compile("^https?://([^/]+)");
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractPath(String url) {
        int pathIndex = url.indexOf('/', 8);
        return pathIndex == -1 ? "" : url.substring(pathIndex);
    }

    private String base64Encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes());
    }

    private String[] parseJsonArray(String json) {
        try {
            json = json.trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1);
            }
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

    private String generateRandomSubdomain() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
