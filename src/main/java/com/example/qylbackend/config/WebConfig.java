package com.example.qylbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir.apks}")
    private String apkUploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /apks/** 的URL请求映射到服务器上的物理路径
        // "file:" 前缀是必须的，表示这是一个文件系统路径
        registry.addResourceHandler("/apks/**")
                .addResourceLocations("file:" + apkUploadDir);
    }
} 