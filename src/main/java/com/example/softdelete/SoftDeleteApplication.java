package com.example.softdelete;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SoftDeleteApplication {

	public static void main(String[] args) {
		SpringApplication.run(SoftDeleteApplication.class, args);
	}

}
