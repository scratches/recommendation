package demo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
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

import reactor.core.Reactor;
import rx.Observable;
import rx.Subscriber;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.ObservableResult;

import demo.StoreDetails.Recommendation;
import demo.lifecycle.EnableLifecycle;
import demo.lifecycle.Start;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@EnableLifecycle
@EnableEurekaClient
@EnableHystrix
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

	@Start
	public void run() throws Exception {
	}

	public static void main(String[] args) {
		SpringApplication.run(RecommendationApplication.class, args);
	}

	private Observable<StoreDetails> fetch(String customerId) {
		Observable<StoreDetails> details = stores.nearbyStores(customerId)
				.<StoreDetails> flatMap(
						store -> {
							Observable<Recommendation> recommendations = stores
									.recommendationsForStore(store.getId());
							return recommendations.buffer(500, TimeUnit.MILLISECONDS, 10)
									.first().map(list -> {
										StoreDetails result = new StoreDetails(store);
										result.getRecommendations().addAll(list);
										return result;
									});
						});
		return details;
	}

	public static <T> DeferredResult<List<T>> toDeferredResult(Observable<T> observable) {
		DeferredResult<List<T>> deferred = new DeferredResult<List<T>>();
		List<T> list = new ArrayList<T>();
		observable.subscribe(result -> list.add(result), e -> deferred.setErrorResult(e),
				() -> deferred.setResult(list));
		return deferred;
	}

	public static <S extends Future<C>, C extends Collection<T>, T> Observable<T> collectionToObservable(
			S collection) {
		return Observable.create((Subscriber<? super T> observer) -> {
			try {
				for (T item : collection.get()) {
					observer.onNext(item);
				}
				observer.onCompleted();
			}
			catch (Exception e) {
				observer.onError(e);
			}
		});

	}

}

@Component
class StoreService {

	private LameService lame;

	@Autowired
	public StoreService(LameService lame) {
		this.lame = lame;
	}

	public Observable<Recommendation> recommendationsForStore(String storeId) {
		// TODO: iterate over pageable results from rest template
		return Observable.create((Subscriber<? super Recommendation> observer) -> {
			lame.recommendationsForStore(storeId).subscribe((list) -> {
				for (Recommendation recommendation : list) {
					observer.onNext(recommendation);
				}
			}, (e) -> {
				observer.onError(e);
			}, () -> {
				observer.onCompleted();
			});
		});
	}

	public Observable<Store> nearbyStores(String customerId) {
		// TODO: iterate over pageable results from rest template
		return Observable.create((Subscriber<? super Store> observer) -> {
			lame.nearbyStores(customerId).subscribe((list) -> {
				for (Store recommendation : list) {
					observer.onNext(recommendation);
				}
			}, (e) -> {
				observer.onError(e);
			}, () -> {
				observer.onCompleted();
			});
		});
	}

}

@Component
class LameService {

	private final DiscoveryClient client;

	@Autowired
	public LameService(DiscoveryClient client) {
		this.client = client;
	}

	@HystrixCommand(fallbackMethod = "emptyStream")
	public Observable<Collection<Recommendation>> recommendationsForStore(String storeId) {
		return new ObservableResult<Collection<Recommendation>>() {
			@Override
			public Collection<Recommendation> invoke() {
				return recommendations(storeId);
			}
		};
	}

	@HystrixCommand(fallbackMethod = "emptyStream")
	public Observable<Collection<Store>> nearbyStores(String customerId) {
		return new ObservableResult<Collection<Store>>() {
			@Override
			public Collection<Store> invoke() {
				return stores(customerId);
			}
		};
	}

	protected <T> Collection<T> emptyStream(String id) {
		return Collections.emptyList();
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