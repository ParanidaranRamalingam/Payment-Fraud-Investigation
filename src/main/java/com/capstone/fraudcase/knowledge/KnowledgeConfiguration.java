package com.capstone.fraudcase.knowledge;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PolicyProperties.class)
public class KnowledgeConfiguration {
}
