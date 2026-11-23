package com.example.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:mysql://localhost:3307/messaging?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
		"spring.datasource.username=user",
		"spring.datasource.password=password",
		"spring.jpa.hibernate.ddl-auto=update"
})
class MessagingApplicationTests {

	@Test
	void contextLoads() {
	}

}
