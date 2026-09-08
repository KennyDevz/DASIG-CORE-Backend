package edu.cit.dasig_core;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class DasigCoreApplication {

	@Value("${app.business-timezone:Asia/Manila}")
	private String businessTimezone;

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone(businessTimezone));
	}

	public static void main(String[] args) {
		SpringApplication.run(DasigCoreApplication.class, args);
	}

}
