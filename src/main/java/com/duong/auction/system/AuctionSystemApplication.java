package com.duong.auction.system;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuctionSystemApplication {

	public static void main(String[] args) {
		// 🟢 TỰ ĐỘNG NẠP BIẾN MÔI TRƯỜNG TỪ FILE .ENV VÀO SYSTEM PROPERTIES
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

		SpringApplication.run(AuctionSystemApplication.class, args);
	}

}
	