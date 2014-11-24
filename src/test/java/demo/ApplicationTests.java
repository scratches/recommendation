package demo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import demo.Address.Point;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = RecommendationApplication.class)
@IntegrationTest("server.port=0")
@WebAppConfiguration
public class ApplicationTests {

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private StoreService service;

	@Test
	public void contextLoads() {
		service.setDefaultStores(new Store("Default", new Address("Windy Avenue",
				"Nowhere", "99999", new Point(0, 0))));
		new TestRestTemplate().getForObject("http://localhost:" + port + "/1",
				String.class);
	}

}
