package com.giovannimenzano.jblockchain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JblockchainApplication {

	public static void main(String[] args) {
		SpringApplication.run(JblockchainApplication.class, args);
	}

}
