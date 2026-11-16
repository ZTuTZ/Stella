package com.futureself;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Future Self 微信小程序后端入口（V1）。
 *
 * <p>单体应用；模块划分见实施文档 §9/§30：
 * auth / user / profile / goal / story / affirmation / generation /
 * ai.text / ai.audio / moderation / storage / admin。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FutureSelfApplication {

    public static void main(String[] args) {
        SpringApplication.run(FutureSelfApplication.class, args);
    }
}
