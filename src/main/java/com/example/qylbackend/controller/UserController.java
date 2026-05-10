package com.example.qylbackend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.example.qylbackend.dto.ApkInfo;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import com.example.qylbackend.model.AppVersion;
import com.example.qylbackend.model.DeviceInfo;
import com.example.qylbackend.model.MyOrder;
import com.example.qylbackend.model.Suggest;
import com.example.qylbackend.repository.AppVersionRepository;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.example.qylbackend.repository.AdRepository;
import com.example.qylbackend.model.Ad;
import com.example.qylbackend.repository.ConfigEntryRepository;
import com.example.qylbackend.repository.DeviceInfoRepository;
import com.example.qylbackend.repository.DeviceRepository;
import com.example.qylbackend.repository.MyOrderRepository;
import com.example.qylbackend.repository.SuggestRepository;
import com.example.qylbackend.model.ConfigEntry;
import com.example.qylbackend.model.Device;
import com.example.qylbackend.service.ApiParseService;
import com.example.qylbackend.utils.MD5Utils;

@RestController
@RequestMapping("/api")
public class UserController {

    private final WebClient webClient;
    private final WebClient apiopenClient = WebClient.create("https://api.apiopen.top");
    private final WebClient mmpClient = WebClient.create("https://api.mmp.cc");
    private final WebClient linkCheckClient = WebClient.create();
    public static final String MERCHANTNUM = "628977442052521984"; // 商户号
    public static final String SECRETKEY = "7bc70475e405fcbfbfcf192e1b552a59"; // 商户密钥

    @Value("${file.upload-dir.apks}")
    private String apkUploadDir;

    @Autowired
    private AppVersionRepository appVersionRepository; // 注入 AppVersionRepository
    @Autowired
    private AdRepository adRepository; // 注入广告Repository
    @Autowired
    private ConfigEntryRepository configEntryRepository; // 注入配置表Repository
    @Autowired
    private DeviceInfoRepository deviceInfoRepository; // 注入配置表Repository
    @Autowired
    private MyOrderRepository orderRepository; // 注入配置表Repository
    @Autowired
    private DeviceRepository deviceRepository; // 注入配置表Repository
    @Autowired
    private SuggestRepository suggestRepository; // 注入配置表Repository
    @Autowired
    private ApiParseService apiParseService; // 注入API解析服务

    // 注入WebClient
    public UserController(WebClient webClient) {
        this.webClient = webClient;
    }

    // 示例接口：获取用户信息
    @GetMapping("/user")
    public String getUserInfo() {
        return "{\"id\": 1, \"name\": \"张三\", \"email\": \"zhangsan@example.com\"}";
    }

    // --- App 版本管理接口 ---

    /**
     * 新增一个App版本信息
     * @param appVersion app版本信息
     * @return 保存后的版本信息
     */
    @PostMapping("/versions")
    public Mono<AppVersion> addAppVersion(@RequestBody AppVersion appVersion) {
        // 自动设置创建时间为当前服务器时间
        appVersion.setCreatedAt(LocalDateTime.now());
        return Mono.fromCallable(() -> appVersionRepository.save(appVersion));
    }

    /**
     * 获取最新的App版本信息
     * @return 最新的版本信息
     */
    @GetMapping("/versions/latest")
    public Mono<AppVersion> getLatestAppVersion() {
        // findTopByOrderByCreatedAtDesc 是一个阻塞操作
        // Mono.justOrEmpty 会在 Optional 为空时返回一个空的 Mono，避免了空指针异常
        return Mono.justOrEmpty(appVersionRepository.findTopByOrderByCreatedAtDesc());
    }

    // 代理 apiopen.top
    @GetMapping("/getMiniVideo")
    public Mono<String> getMiniVideo(@RequestParam int page, @RequestParam int size) {
        return apiopenClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/getMiniVideo")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .retrieve()
                .onStatus(
                        status -> status.isError(), // 当状态码为4xx/5xx时触发
                        clientResponse -> Mono.error(new RuntimeException("第三方接口异常: " + clientResponse.statusCode()))
                )
                .bodyToMono(String.class);
    }
    // 检查链接是否有效（模拟浏览器访问）
    private Mono<Boolean> isValidLink(String url) {
        if (url == null || url.trim().isEmpty()) {
            return Mono.just(false);
        }
        
        // 基本URL格式检查
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Mono.just(false);
        }
        
        return linkCheckClient.head()
                .uri(url)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5)) // 5秒超时
                .map(response -> {
                    int statusCode = response.getStatusCode().value();
                    // 2xx和3xx状态码都认为是有效的（包括重定向）
                    boolean isValid = (statusCode >= 200 && statusCode < 400);
                    if (!isValid) {
                        System.out.println("链接无效: " + url + " (状态码: " + statusCode + ")");
                    }
                    return isValid;
                })
                .onErrorResume(e -> {
                    // 记录错误信息，包括网络错误、超时等
                    System.out.println("链接检查失败: " + url + " (错误: " + e.getMessage() + ")");
                    return Mono.just(false);
                });
    }
    // 代理 mmp.cc 视频1
    @GetMapping("/ksvideo")
    public Mono<String> getKsVideo() {
        String[] ids = {"jk", "YuMeng", "NvDa", "NvGao", "ReWu", "QingCun", "SheJie", "ChuanDa", "GaoZhiLiangXiaoJieJie", "HanFu", "HeiSi", "BianZhuang", "LuoLi", "TianMei", "BaiSi"};
        ObjectMapper objectMapper = new ObjectMapper(); 
        return Flux.fromArray(ids)
                .flatMap(id -> mmpClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/ksvideo")
                                .queryParam("type", "json")
                                .queryParam("id", id)
                                .build())
                        .retrieve()
                        .onStatus(
                                status -> status.isError(), // 当状态码为4xx/5xx时触发
                                clientResponse -> Mono.error(new RuntimeException("第三方接口异常: " + clientResponse.statusCode()))
                        )
                        .bodyToMono(String.class)
                        .onErrorResume(e -> {
                            System.err.println("API call for id " + id + " failed: " + e.getMessage());
                            return Mono.empty();
                        }))
                .collectList()
                .flatMap(results -> {
                    // 解析所有JSON响应并提取视频信息
                    List<Map<String, Object>> allVideos = new ArrayList<>();
                    
                    for (String result : results) {
                            try {
                                // 如果解析数组失败，尝试解析单个对象
                                Map<String, Object> video = objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
                                allVideos.add(video);
                            } catch (Exception ex) {
                                System.err.println("Failed to parse JSON: " + result + ", error: " + ex.getMessage());
                            }
                    }
                    
                    // 提取所有视频URL
                    List<String> videoUrls = allVideos.stream()
                            .map(v -> (String) v.get("link"))
                            .filter(url -> url != null && !url.trim().isEmpty())
                            .collect(Collectors.toList());
                    
                    if (videoUrls.isEmpty()) {
                        return Mono.just("[]");
                    }
                    
                    // 并发检查链接有效性，模拟浏览器访问
                    return Flux.fromIterable(videoUrls)
                            .flatMap(url -> isValidLink(url)
                                    .map(isValid -> new AbstractMap.SimpleEntry<>(url, isValid)), 5) // 并发度设为5
                            .collectList()
                            .map(urlValidityMap -> {
                                // 获取有效的URL集合
                                Set<String> validUrls = urlValidityMap.stream()
                                        .filter(entry -> entry.getValue())
                                        .map(Map.Entry::getKey)
                                        .collect(Collectors.toSet());
                                
                                // 过滤出有效的视频信息
                                List<Map<String, Object>> validVideos = allVideos.stream()
                                        .filter(v -> v.get("link") != null && validUrls.contains(v.get("link")))
                                        .collect(Collectors.toList());
                                
                                try {
                                    return objectMapper.writeValueAsString(validVideos);
                                } catch (Exception e) {
                                    System.err.println("Failed to serialize valid videos: " + e.getMessage());
                                    return "[]";
                                }
                            });
                });
    }

    // 代理 mmp.cc 视频2
    @GetMapping("/miss")
    public Mono<String> getMiss() {
        return mmpClient.get()
                .uri("/api/miss?type=json")
                .retrieve()
                .onStatus(
                        status -> status.isError(), // 当状态码为4xx/5xx时触发
                        clientResponse -> Mono.error(new RuntimeException("第三方接口异常: " + clientResponse.statusCode()))
                )
                .bodyToMono(String.class);
    }

    // 代理 mmp.cc 视频3
    @GetMapping("/shortvideo")
    public Mono<String> getShortVideo() {
        return mmpClient.get()
                .uri("/api/shortvideo?type=json")
                .retrieve()
                .onStatus(
                        status -> status.isError(), // 当状态码为4xx/5xx时触发
                        clientResponse -> Mono.error(new RuntimeException("第三方接口异常: " + clientResponse.statusCode()))
                )
                .bodyToMono(String.class);
    }

    // 获取APK文件列表
    @GetMapping("/apks")
    public List<ApkInfo> getApkList() {
        Path apkPath = Paths.get(apkUploadDir);
        if (!Files.isDirectory(apkPath)) {
            System.err.println("APK directory not found: " + apkUploadDir);
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(apkPath)) {
            return paths
                    .filter(path -> !Files.isDirectory(path))
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.toLowerCase().endsWith(".apk"))
                    .map(fileName -> {
                        String downloadUrl = "/apks/" + fileName;
                        return new ApkInfo(fileName, downloadUrl);
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error reading APK directory: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询所有广告信息
     * @return 广告列表
     */
    @GetMapping("/ads")
    public List<Ad> getAllAds() {
        return adRepository.findAll();
    }

    /**
     * 查询所有配置项
     * @return 配置项列表
     */
    @GetMapping("/configs")
    public List<ConfigEntry> getAllConfigs() {
        return configEntryRepository.findAll();
    }

    /**
     * 根据key查询配置项
     * @param key 配置项key
     * @return 配置项对象或null
     */
    @GetMapping("/config")
    public ConfigEntry getConfigByKey(@RequestParam String key) {
        return configEntryRepository.findByKey(key);
    }

    /**
     * 根据key查询配置项
     * @param key 配置项key
     * @return 配置项对象或null
     */
    @GetMapping("/config/yanzheng")
    public Map<String, Object> yanzheng(@RequestParam String value) {
        Map<String, Object> result = new HashMap<>();
        String key = "qyl_shunle_koul";
        ConfigEntry con = configEntryRepository.findByKey(key);
        if (con == null || !con.getValue().equals(value)) {
            result.put("result", false);
            
        }else {
            result.put("result", true);
        }
        return result;
    }
    // 保存设备信息
    @PostMapping("/savedevice")
    public DeviceInfo saveDevice(@RequestBody DeviceInfo deviceInfo) {
        if (deviceInfo.getDeviceId() != null) {
            Device device = deviceRepository.findByDeviceId(deviceInfo.getDeviceId());
            if (device != null) {
                device.setLastUseTime(LocalDateTime.now());
            } else {
                device = new Device();
                device.setDeviceId(deviceInfo.getDeviceId());
                device.setFirstUseTime(LocalDateTime.now());
                device.setLastUseTime(LocalDateTime.now());
            }
            deviceRepository.save(device);
        }
        return deviceInfoRepository.save(deviceInfo);
    }

    // 保存建议
    @PostMapping("/savesuggest")
    public Suggest saveSuggest(@RequestBody Suggest suggest) {
        return suggestRepository.save(suggest);
    }

    // 查询设备信息（通过deviceId）
    @GetMapping("/getdevice")
    public List<DeviceInfo> getDevice() {
        return deviceInfoRepository.findAll();
    }
    // 创建订单
    @GetMapping("/createorder")
    public Mono<Map<String, Object>> createOrder(@RequestParam String deviceId, @RequestParam String payType) {
        Map<String, Object> result = new HashMap<>();

        String merchantNum = MERCHANTNUM;// 商户号
        String secretKey = SECRETKEY;//商户密钥
        String notifyUrl = "https://slkk.dpdns.org/api/notify";// 填写您的接收支付成功的异步通知地址
        String amount = "10.00";// 支付金额
        // String payType = payType;// "alipay" "wechat" 请求支付类型
        String payApiUrl = "https://api-4s15w84vxa0w.zhifu.fm.it88168.com/api/startOrder";// 发起订单地址
        String orderNo = generateOrderNo(deviceId);// 商户订单号

        // 对notifyUrl进行编码以用于签名（按支付接口规范）
        String encodedNotifyUrlForSign = java.net.URLEncoder.encode(notifyUrl, java.nio.charset.StandardCharsets.UTF_8);

        // 构建签名字符串（使用编码后的notifyUrl进行签名计算）
        String signStr = merchantNum + orderNo + amount + encodedNotifyUrlForSign + secretKey;
        String sign = MD5Utils.md5(signStr);// md5签名

        // 保存订单
        MyOrder order = new MyOrder();
        order.setDeviceId(deviceId);
        order.setNo(orderNo);
        order.setState("0");
        order.setFirstUseTime(LocalDateTime.now());
        orderRepository.save(order);

        String paramStr = buildQueryParams(Map.of(
                "merchantNum", merchantNum,
                "orderNo", orderNo,
                "amount", amount,
                "notifyUrl", notifyUrl, // 传递原始URL，由buildQueryParams方法进行编码
                "payType", payType,
                "attch", deviceId,
                "sign", sign,
                "payDuration","1",
                "subject", "一杯奶茶"
        ));

        String apiUrl = payApiUrl + "?" + paramStr;

        return webClient.post()
                .uri(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .map(response -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        Map<String, Object> respMap = mapper.readValue(response, new TypeReference<Map<String, Object>>() {});
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) respMap.get("data");
                        String payUrl = data != null ? (String) data.get("payUrl") : null;
                        result.put("success", true);
                        result.put("payUrl", payUrl);
                    } catch (Exception e) {
                        result.put("success", false);
                        result.put("msg", "解析支付响应失败: " + e.getMessage());
                    }
                    return result;
                })
                .onErrorResume(e -> {
                    result.put("success", false);
                    result.put("msg", "发起支付订单失败: " + e.getMessage());
                    return Mono.just(result);
                });
    }

    private String generateOrderNo(String deviceId) {
        // 获取当前时间戳
        String timestamp = String.valueOf(System.currentTimeMillis());

        // 将deviceId转换为数字（取其hashCode的绝对值）
        String deviceNumeric = String.valueOf(Math.abs(deviceId.hashCode()));

        // 组合并截取，确保总长度不超过32位
        String combined = deviceNumeric + timestamp;

        if (combined.length() <= 32) {
            return combined;
        } else {
            // 如果超过32位，则截取前32位
            return combined.substring(0, 32);
        }
    }

    private String buildQueryParams(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = params.get(key);
            try {
                value = java.net.URLEncoder.encode(value, "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            sb.append(key).append("=").append(value);
            if (i < keys.size() - 1) {
                sb.append("&");
            }
        }
        return sb.toString();
    }

    private String buildQueryParamsForUrl(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = params.get(key);
            sb.append(key).append("=").append(value);
            if (i < keys.size() - 1) {
                sb.append("&");
            }
        }
        return sb.toString();
    }

    // 验证回调签名
    @GetMapping("/notify")
    public String notify(@RequestParam Map<String, String> map){
        System.out.println("收到支付回调: " + map.toString());
        String merchantNum = map.getOrDefault("merchantNum", "");
        String orderNo = map.getOrDefault("orderNo", "");
        String amount = map.getOrDefault("amount", "");
        String sign = map.getOrDefault("sign", "");
        String state = map.getOrDefault("state", "");
        // 验证商户ID
        if (!MERCHANTNUM.equals(merchantNum)) {
          System.out.println("验证商户ID失败: ");
          return "false";
        }
        String signStr = state + merchantNum + orderNo + amount + SECRETKEY;
        if (!sign.equals(MD5Utils.md5(signStr))) {
            System.out.println("签名验证失败: " + " (计算的签名: " + MD5Utils.md5(signStr) + ", 回调中的签名: " + sign + ")");
            return "false";
        }
        List<MyOrder> orders = orderRepository.findByNoAndState(orderNo, "0");
        if (orders.isEmpty()) {
            System.out.println("订单不存在或状态不正确: " + orderNo);
            return "false";
        }
        MyOrder order = orders.get(0);
        if ("1".equals(order.getState())) {
            System.out.println("订单已付款成功: " + orderNo);
            return "success";
        }
        order.setState(state);
        order.setLastUseTime(LocalDateTime.now());
        orderRepository.save(order);
        System.out.println("订单状态更新: " + orderNo);
        return "success";
    }

    // 查询订单是否付款成功
    @GetMapping("/getstate")
    public Map<String, Object> getState(@RequestParam String deviceId ) {
        Map<String, Object> result = new HashMap<>();
        List<MyOrder> orders = orderRepository.findByDeviceIdAndState(deviceId, "1");
        if (orders.isEmpty()) {
            result.put("state", false);
        } else {
            result.put("state", true);
        }
        return result;
    }

    // --- API解析接口 ---

    /**
     * 获取第一页的重定向URL
     * @param url 原始URL
     * @return 重定向后的URL
     */
    @GetMapping("/parse/first-url")
    public Mono<String> parseFirstUrl(@RequestParam String url) {
        return apiParseService.getFirstUrl(url);
    }

    /**
     * 获取第二页的URL
     * @param url 第一页URL
     * @return 第二页URL
     */
    @GetMapping("/parse/second-url")
    public Mono<String> parseSecondUrl(@RequestParam String url) {
        return apiParseService.getSecondUrl(url);
    }

    /**
     * 获取完整流程的URL（从第一页到第二页）
     * @param originalUrl 原始URL
     * @return 最终的第二页URL
     */
    @GetMapping("/parse/complete")
    public Mono<String> parseCompleteUrl(@RequestParam String originalUrl) {
        return apiParseService.getFirstUrl(originalUrl)
                .flatMap(firstUrl -> {
                    if (firstUrl.equals(originalUrl)) {
                        // 如果没有重定向，直接尝试解析第二页
                        return apiParseService.getSecondUrl(firstUrl);
                    }
                    return apiParseService.getSecondUrl(firstUrl);
                });
    }
}