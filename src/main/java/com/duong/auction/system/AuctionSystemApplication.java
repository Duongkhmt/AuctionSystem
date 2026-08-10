package com.duong.auction.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuctionSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuctionSystemApplication.class, args);
	}

}
	