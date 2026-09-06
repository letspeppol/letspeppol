package org.letspeppol.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"e-invoice.organisation.api-key=test-api-key",
		"scrada.company-id=test-company-id",
		"scrada.api-key=test-api-key",
		"scrada.password=test-password",
		"scrada.company-key=test-company-key",
		"recommand.api-key=test-api-key",
		"recommand.api-secret=test-api-secret"
})
class ProxyApplicationTests {

	@Test
	void contextLoads() {
	}

}
