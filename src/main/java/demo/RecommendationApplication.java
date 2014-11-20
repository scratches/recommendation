package demo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.client.Traverson;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import reactor.rx.Stream;
import reactor.rx.Streams;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

import demo.StoreDetails.Recommendation;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@EnableEurekaClient
@EnableHystrix
@RestController
public class RecommendationApplication {

	@Autowired
	private StoreService stores;

	@RequestMapping("/{customerId}")
	public DeferredResult<List<StoreDetails>> recommend(@PathVariable String customerId)
			throws Exception {
		return toDeferredResult(fetch(customerId));
	}

	public static void main(String[] args) {
		SpringApplication.run(RecommendationApplication.class, args);
	}

	private Stream<StoreDetails> fetch(String customerId) {
		Stream<StoreDetails> details = stores.nearbyStores(customerId).flatMap(store -> {
			StoreDetails result = new StoreDetails(store);
			return stores.recommendationsForStore(store).map(recommendation -> {
				result.getRecommendations().add(recommendation);
				return result;
			}).defaultIfEmpty(result);
		});
		return details;
	}

	private static <T> DeferredResult<List<T>> toDeferredResult(Stream<T> publisher) {
		DeferredResult<List<T>> deferred = new DeferredResult<List<T>>();
		publisher.log("Result").buffer()
				.consume((List<T> result) -> deferred.setResult(result));
		return deferred;
	}

}

@Component
class StoreService {

	private final DiscoveryClient client;

	@Autowired
	public StoreService(DiscoveryClient client) {
		this.client = client;
	}

	@HystrixCommand(fallbackMethod = "emptyStream")
	public Stream<Recommendation> recommendationsForStore(Store store) {
		// Get at most 10 (or timeout)
		return Streams.defer(recommendations(store)).log("Recommendations");
	}

	@HystrixCommand(fallbackMethod = "emptyStream")
	public Stream<Store> nearbyStores(String customerId) {
		// TODO: global timeout after 1 sec
		return Streams.range(0, 10)
				.flatMap(pageNumber -> Streams.defer(stores(customerId, pageNumber)))
				.log("Stores");
	}

	protected <T> Stream<T> emptyStream(String id) {
		return Streams.empty();
	}

	protected <T> Stream<T> emptyStream(Store input) {
		return Streams.empty();
	}

	private Collection<Store> stores(String customerId, long pageNumber) {
		String url = client.getNextServerFromEureka("CUSTOMERS", false).getHomePageUrl();
		URI base;
		try {
			base = new URI(url + "customers/" + customerId);
		}
		catch (URISyntaxException e) {
			throw new IllegalStateException("Could not create base URI", e);
		}
		final Collection<Store> stores = new Traverson(base, MediaTypes.HAL_JSON)
				.follow("stores-nearby")
				.withTemplateParameters(Collections.singletonMap("page", pageNumber))
				.toObject(new ParameterizedTypeReference<Resources<Store>>() {
				}).getContent();
		return stores;
	}

	private Collection<Recommendation> recommendations(Store store) {
		throw new UnsupportedOperationException("No recommendations available");
	}

}