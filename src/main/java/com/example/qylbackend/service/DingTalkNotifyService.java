package com.example.qylbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class DingTalkNotifyService {

    private final WebClient webClient;

    @Value("${dingtalk.webhook}")
    private String webhookUrl;

    @Value("${dingtalk.secret}")
    private String secret;

    public DingTalkNotifyService() {
        this.webClient = WebClient.create();
    }

    public void sendAsync(String title, String content) {
        String signUrl = sign();
        Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of(
                        "title", title,
                        "text", content
                )
        );

        webClient.post()
                .uri(signUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                        resp -> {},
                        err -> System.err.println("钉钉通知发送失败: " + err.getMessage())
                );
    }

    private String sign() {
        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
            return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            System.err.println("钉钉签名计算失败: " + e.getMessage());
            return webhookUrl;
        }
    }
}
