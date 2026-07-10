package br.com.transferhub.transferapi;

import org.springframework.boot.SpringApplication;

public class TestTransferApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(TransferApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
