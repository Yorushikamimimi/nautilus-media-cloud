package com.nautilus.dispatch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 任务调度示例 — Java 控制面（Spring Boot）。
 *
 * <p>模块目录名 amy-dispatch-center 为历史命名。
 */
@SpringBootApplication(scanBasePackages = {
        "com.nautilus.dispatch",
        "com.nautilus.common"
})
@MapperScan("com.nautilus.dispatch.mapper")
@EnableScheduling
@EnableTransactionManagement
public class DispatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchApplication.class, args);
        System.out.println("""
                
                ╔═══════════════════════════════════════════════════════════╗
                ║                                                           ║
                ║     🎵 Amy Dispatch Center 启动成功                       ║
                ║                                                           ║
                ║     夜行 - 任务调度控制面（示例）                          ║
                ║     Theme: Yorushika (ヨルシカ)                           ║
                ║                                                           ║
                ║     API Base URL: http://localhost:8080/api/v1/tasks     ║
                ║                                                           ║
                ╚═══════════════════════════════════════════════════════════╝
                """);
    }
}
