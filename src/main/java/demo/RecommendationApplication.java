package demo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
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

import reactor.core.Reactor;
import reactor.rx.Stream;
import reactor.rx.spec.Streams;

import com.netflix.discovery.DiscoveryClient;

import demo.StoreDetails.Recommendation;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@EnableEurekaClient
@RestController
public class RecommendationApplication {

	@Autowired
	private Reactor reactor;

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

	private Publisher<StoreDetails> fetch(String customerId) {
		Publisher<StoreDetails> details = stores.nearbyStores(customerId)
				.flatMap(
						store -> {
							Stream<Recommendation> recommendations = stores
									.recommendationsForStore(store.getId());
							return recommendations.map(recommendation -> {
										StoreDetails result = new StoreDetails(store);
										result.getRecommendations().add(recommendation);
										return result;
									});
						});
		return details;
	}

	private static <T> DeferredResult<List<T>> toDeferredResult(Publisher<T> publisher) {
		DeferredResult<List<T>> deferred = new DeferredResult<List<T>>();
		List<T> list = new ArrayList<T>();
		publisher.subscribe(new Subscriber<T>() {

			@Override
			public void onSubscribe(Subscription s) {
				s.request(Integer.MAX_VALUE);
			}

			@Override
			public void onNext(T t) {
				list.add(t);
			}

			@Override
			public void onError(Throwable t) {
			}

			@Override
			public void onComplete() {
				deferred.setResult(list);
			}});
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

	public Stream<Recommendation> recommendationsForStore(String storeId) {
		return Streams.defer(recommendations(storeId));
	}

	public Stream<Store> nearbyStores(String customerId) {
		return Streams.defer(stores(customerId));
	}

	private Collection<Store> stores(String customerId) {
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
				.toObject(new ParameterizedTypeReference<Resources<Store>>() {
				}).getContent();
		return stores;
	}

	private List<Recommendation> recommendations(String store) {
		return Arrays.asList(new Recommendation(store));
	}

}