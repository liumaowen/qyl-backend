package com.example.qylbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebConfig implements WebFluxConfigurer {

    @Value("${file.upload-dir.apks}")
    private String apkUploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /apks/** 的URL请求映射到服务器上的物理路径
        // "file:" 前缀是必须的，表示这是一个文件系统路径
        registry.addResourceHandler("/apks/**")
                .addResourceLocations("file:" + apkUploadDir);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 配置CORS，解决跨域问题
        registry.addMapping("/api/**") // 对 /api/ 下的所有路径生效
                .allowedOrigins("*") // 允许所有来源（生产环境建议指定具体前端域名）
                // 如果有多个，可以像这样写：.allowedOrigins("https://www.qylapp.com", "https://admin.qylapp.com")
                .allowedMethods("*") // 允许所有HTTP方法 (GET, POST, PUT, DELETE, OPTIONS等)
                .allowedHeaders("*") // 允许所有请求头
                .allowCredentials(false) // 是否允许发送Cookie
                .maxAge(3600); // 预检请求的缓存时间（秒）
    }
} 