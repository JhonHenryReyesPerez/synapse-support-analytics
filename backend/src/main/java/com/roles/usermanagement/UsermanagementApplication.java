package com.roles.usermanagement;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UsermanagementApplication {

	public static void main(String[] args) {
		// Las fechas se guardan como hora local sin zona; se fija la zona de negocio para que no
		// dependan del servidor o contenedor (los registros existentes están en hora de Bogotá).
		TimeZone.setDefault(TimeZone.getTimeZone(System.getenv().getOrDefault("APP_TIME_ZONE", "America/Bogota")));
		SpringApplication.run(UsermanagementApplication.class, args);
	}

}
