package com.example.qylbackend.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Slf4j
@Aspect
@Component
public class WebLogAspect {

    @Pointcut("execution(* com.example.qylbackend.controller.*.*(..))")
    public void webLog() {}

    @Around("webLog()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // WebFlux环境：通过ReactiveRequestContextHolder获取ServerWebExchange
        Mono<ServerWebExchange> exchangeMono = Mono.deferContextual(ctx -> 
            Mono.justOrEmpty(ctx.getOrEmpty(ServerWebExchange.class))
        );

        // 记录请求开始日志（在响应式流中执行）
        Mono<Void> logRequestMono = exchangeMono.doOnNext(exchange -> {
            log.debug("==================== 请求开始 ====================");
            log.debug("请求URL: {}", exchange.getRequest().getURI());
            log.debug("请求方法: {}", exchange.getRequest().getMethod());
            log.debug("请求控制器方法: {}.{}", 
                joinPoint.getSignature().getDeclaringTypeName(), 
                joinPoint.getSignature().getName());
        }).then();

        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long cost = System.currentTimeMillis() - start;

        // 响应式结果处理（Mono/Flux）
        if (result instanceof Mono) {
            return ((Mono<?>) result)
                .flatMap(res -> {  // 使用flatMap保留原始结果
                    log.debug("响应结果: {}", res);
                    log.debug("接口耗时: {}ms", cost);
                    log.debug("==================== 请求结束 ====================\n");
                    return logRequestMono.thenReturn(res);  // 执行日志后返回原始结果
                });
        } else {
            log.debug("响应结果: {}", result);
            log.debug("接口耗时: {}ms", cost);
            log.debug("==================== 请求结束 ====================\n");
            return result;
        }
    }
}