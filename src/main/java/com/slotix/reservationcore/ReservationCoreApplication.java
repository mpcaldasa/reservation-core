package com.slotix.reservationcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReservationCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReservationCoreApplication.class, args);
	}

}
