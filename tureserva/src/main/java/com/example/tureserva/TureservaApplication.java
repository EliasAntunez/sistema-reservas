package com.example.tureserva;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TureservaApplication {

	public static void main(String[] args) {
		SpringApplication.run(TureservaApplication.class, args);
	}

}
