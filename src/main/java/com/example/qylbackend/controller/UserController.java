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
import com.example.qylbackend.repository.AppVersionRepository;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import com.example.qylbackend.repository.AdRepository;
import com.example.qylbackend.model.Ad;
import com.example.qylbackend.repository.ConfigEntryRepository;
import com.example.qylbackend.model.ConfigEntry;

@RestController
@RequestMapping("/api")
public class UserController {

    private final WebClient webClient;
    private final WebClient apiopenClient = WebClient.create("https://api.apiopen.top");
    private final WebClient mmpClient = WebClient.create("https://api.mmp.cc");
    private final WebClient linkCheckClient = WebClient.create();

    @Value("${file.upload-dir.apks}")
    private String apkUploadDir;

    @Autowired
    private AppVersionRepository appVersionRepository; // 注入 AppVersionRepository
    @Autowired
    private AdRepository adRepository; // 注入广告Repository
    @Autowired
    private ConfigEntryRepository configEntryRepository; // 注入配置表Repository
    @Autowired
    private DeviceInfoEntryRepository deviceInfoRepository; // 注入配置表Repository

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

    // --- 数据库操作示例 ---

    /**
     * 新增一个视频到数据库
     * @param video 请求体中的JSON数据会自动映射到Video对象
     * @return 保存后的Video对象
     */
    // @PostMapping("/videos")
    // public Mono<Video> addVideo(@RequestBody Video video) {
    //     // videoRepository.save 是一个阻塞操作，使用 Mono.fromCallable 包装以适应WebFlux
    //     return Mono.fromCallable(() -> videoRepository.save(video));
    // }

    /**
     * 从数据库获取所有视频
     * @return 所有Video对象的列表
     */
    // @GetMapping("/videos")
    // public Flux<Video> getAllVideos() {
    //     // videoRepository.findAll 是一个阻塞操作，使用 Flux.fromIterable 包装
    //     return Flux.fromIterable(videoRepository.findAll());
    // }

    /**
     * 根据分类获取视频
     * @param category URL路径参数
     * @return 指定分类的Video对象列表
     */
    // @GetMapping("/videos/category/{category}")
    // public Flux<Video> getVideosByCategory(@PathVariable String category) {
    //     return Flux.fromIterable(videoRepository.findByCategory(category));
    // }

    // --- 代理接口 ---

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
        return deviceInfoRepository.save(deviceInfo);
    }

    // 查询设备信息（通过deviceId）
    @GetMapping("/getdevice")
    public List<DeviceInfo> getDevice() {
        return deviceInfoRepository.findAll();
    }
}