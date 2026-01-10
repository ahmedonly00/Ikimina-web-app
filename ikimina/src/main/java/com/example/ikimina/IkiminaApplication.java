package com.example.ikimina;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IkiminaApplication {

	public static void main(String[] args) {
		SpringApplication.run(IkiminaApplication.class, args);
	}

}
