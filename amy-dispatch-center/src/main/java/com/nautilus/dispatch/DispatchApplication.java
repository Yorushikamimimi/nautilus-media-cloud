package com.nautilus.dispatch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 流媒体调度中台 - 核心控制面启动类
 * 
 * Amy Dispatch Center
 * Powered by Yorushika (ヨルシカ)
 *
 * @author Nautilus Media Cloud
 */
@SpringBootApplication(scanBasePackages = {
        "com.nautilus.dispatch",
        "com.nautilus.common"
})
@MapperScan("com.nautilus.dispatch.mapper")
@EnableTransactionManagement
public class DispatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchApplication.class, args);
        System.out.println("""
                
                ╔═══════════════════════════════════════════════════════════╗
                ║                                                           ║
                ║     🎵 Amy Dispatch Center 启动成功                       ║
                ║                                                           ║
                ║     夜行 - 流媒体调度中台核心控制面                        ║
                ║     Powered by Yorushika (ヨルシカ)                       ║
                ║                                                           ║
                ║     API Base URL: http://localhost:8080/api/v1/tasks     ║
                ║                                                           ║
                ╚═══════════════════════════════════════════════════════════╝
                """);
    }
}
