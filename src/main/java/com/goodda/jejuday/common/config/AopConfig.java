package com.goodda.jejuday.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(exposeProxy = true) // ★ AopContext.currentProxy() 사용 가능하게
public class AopConfig {}