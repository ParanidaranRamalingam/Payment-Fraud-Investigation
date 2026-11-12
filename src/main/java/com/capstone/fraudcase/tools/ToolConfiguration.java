package com.capstone.fraudcase.tools;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ToolProperties.class)
public class ToolConfiguration {
}
