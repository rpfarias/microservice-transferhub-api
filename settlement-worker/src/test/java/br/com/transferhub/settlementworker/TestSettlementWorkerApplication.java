package br.com.transferhub.settlementworker;

import org.springframework.boot.SpringApplication;

public class TestSettlementWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.from(SettlementWorkerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
