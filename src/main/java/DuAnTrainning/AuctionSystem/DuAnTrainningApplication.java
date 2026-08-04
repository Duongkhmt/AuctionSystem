package DuAnTrainning.AuctionSystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DuAnTrainningApplication {

	public static void main(String[] args) {
		SpringApplication.run(DuAnTrainningApplication.class, args);
	}

}
