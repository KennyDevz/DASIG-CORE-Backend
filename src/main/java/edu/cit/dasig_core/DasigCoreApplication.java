package edu.cit.dasig_core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class DasigCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(DasigCoreApplication.class, args);
	}

}
