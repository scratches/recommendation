package demo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
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

import reactor.Environment;
import reactor.rx.Stream;
import reactor.rx.Streams;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

import demo.StoreDetails.Recommendation;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@EnableDiscoveryClient
@EnableCircuitBreaker
@RestController
public class RecommendationApplication {

	@Autowired
	private StoreService stores;

	@PostConstruct
	public void init() {
		Environment.initializeIfEmpty();
	}

	@RequestMapping("/{customerId}")
	public DeferredResult<List<StoreDetails>> recommend(@PathVariable String customerId)
			throws Exception {
		return toDeferredResult(fetch(customerId));
	}

	public static void main(String[] args) {
		SpringApplication.run(RecommendationApplication.class, args);
	}

	private Stream<StoreDetails> fetch(String customerId) {
		Stream<StoreDetails> details = nearbyStores(customerId).flatMap(store -> {
			StoreDetails result = new StoreDetails(store);
			return recommendationsForStore(store).map(recommendation -> {
				result.getRecommendations().add(recommendation);
				return result;
			}).defaultIfEmpty(result);
		});
		return details;
	}

	private Stream<Store> nearbyStores(String customerId) {
		// Don't load empty pages
		final AtomicInteger length = new AtomicInteger(1);
		// Global timeout after 1 sec
		final AtomicBoolean cancelled = new AtomicBoolean(false);
		Environment.timer().submit(time -> cancelled.set(true));
		// @formatter:off	
		return Streams
				.range(0, 9) // at most 10 pages
				.takeWhile(stream -> cancelled.get() && length.get()>0)
				.flatMap(
					pageNumber -> {
						Collection<Store> list = stores.stores(customerId, pageNumber);
						length.set(list.size());
						return Streams.from(list);
					}).log("Stores");
// @formatter:on
	}

	private Stream<Recommendation> recommendationsForStore(Store store) {
		return Streams.from(stores.recommendations(store)).log("Recommendations");
	}

	private static <T> DeferredResult<List<T>> toDeferredResult(Stream<T> publisher) {
		DeferredResult<List<T>> deferred = new DeferredResult<List<T>>();
		publisher.log("Result").buffer().defaultIfEmpty(Collections.emptyList())
				.consume((List<T> result) -> deferred.setResult(result));
		return deferred;
	}

}

@Component
@Slf4j
class StoreService {

	private final DiscoveryClient client;

	private Collection<Store> defaultStores = Collections.emptyList();

	@Autowired
	public StoreService(DiscoveryClient client) {
		this.client = client;
	}

	public void setDefaultStores(Store... defaultStores) {
		this.defaultStores = Arrays.asList(defaultStores);
	}

	@HystrixCommand(fallbackMethod = "emptyStores")
	public Collection<Store> stores(String customerId, long pageNumber) {
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

	@HystrixCommand(fallbackMethod = "emptyRecommendations")
	public Collection<Recommendation> recommendations(Store store) {
		throw new UnsupportedOperationException("No recommendations available");
	}

	protected Collection<Store> emptyStores(String id, long pageNumber) {
		log.info("Empty stores: " + pageNumber);
		return pageNumber == 0 ? defaultStores : Collections.emptyList();
	}

	protected Collection<Recommendation> emptyRecommendations(Store input) {
		return Collections.emptyList();
	}

}