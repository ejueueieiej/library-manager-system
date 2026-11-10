package org.libaray_manager_system.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Aiconfig {
    @Bean
    public ChatClient librarychatClient(ChatClient.Builder builder){
        return builder
                .build();
    }
}
