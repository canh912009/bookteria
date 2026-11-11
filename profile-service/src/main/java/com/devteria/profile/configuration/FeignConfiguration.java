package com.devteria.profile.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;

@Configuration
public class FeignConfiguration {
    @Bean
    public Encoder multipartFormEncoder() {
        return new SpringFormEncoder();
    }
}
