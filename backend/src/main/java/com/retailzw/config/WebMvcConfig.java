package com.retailzw.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final BillingAccessInterceptor billingAccessInterceptor;
    private final AdminAccountInterceptor adminAccountInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAccountInterceptor).addPathPatterns("/admin/**");
        registry.addInterceptor(billingAccessInterceptor)
                .addPathPatterns("/shop/**", "/api/**");
    }
}
