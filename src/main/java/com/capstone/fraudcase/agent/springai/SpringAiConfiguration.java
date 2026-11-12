package com.capstone.fraudcase.agent.springai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FraudAiProperties.class)
public class SpringAiConfiguration {
}
