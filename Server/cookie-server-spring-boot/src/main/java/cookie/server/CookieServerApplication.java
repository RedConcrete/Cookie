package cookie.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CookieServerApplication {
	public static void main(String[] args) {
		SpringApplication.run(CookieServerApplication.class, args);
	}
}
