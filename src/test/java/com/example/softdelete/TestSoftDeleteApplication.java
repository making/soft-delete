package com.example.softdelete;

import org.springframework.boot.SpringApplication;

public class TestSoftDeleteApplication {

	public static void main(String[] args) {
		SpringApplication.from(SoftDeleteApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
