package com.AccountReceivableManagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AccountReceivableManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountReceivableManagementApplication.class, args);
	}

}
