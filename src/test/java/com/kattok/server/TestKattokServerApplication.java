package com.kattok.server;

import org.springframework.boot.SpringApplication;

public class TestKattokServerApplication {

	public static void main(String[] args) {
		SpringApplication.from(KattokServerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
