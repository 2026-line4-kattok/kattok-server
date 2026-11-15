package com.kattok.server.home.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class HomeConfiguration {
	@Bean
	public Clock homeClock() {
		return Clock.systemUTC();
	}
}
