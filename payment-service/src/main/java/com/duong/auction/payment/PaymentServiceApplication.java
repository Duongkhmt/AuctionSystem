package com.duong.auction.payment;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        // 🟢 TỰ ĐỘNG NẠP BIẾN MÔI TRƯỜNG TỪ FILE .ENV VÀO SYSTEM PROPERTIES
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}

