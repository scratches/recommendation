package demo;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.util.concurrent.ListenableFutureTask;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import reactor.core.Reactor;
import rx.Observable;
import rx.subscriptions.Subscriptions;

import com.netflix.discovery.DiscoveryClient;

import demo.StoreDetails.Recommendation;
import demo.lifecycle.EnableLifecycle;
import demo.lifecycle.Start;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@EnableLifecycle
@EnableEurekaClient
@RestController
public class RecommendationApplication {

	@Autowired
	private Reactor reactor;

	@Autowired
	private RecommendationService service;

	@RequestMapping("/{customerId}")
	public DeferredResult<List<StoreDetails>> recommend(@PathVariable String customerId)
			throws Exception {
		return toDeferredResult(service.fetch(customerId));
	}

	@Start
	public void run() throws Exception {
	}

	public static void main(String[] args) {
		SpringApplication.run(RecommendationApplication.class, args);
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
		return Observable.create(observer -> {
			try {
				for (T item : collection.get()) {
					observer.onNext(item);
				}
				observer.onCompleted();
			}
			catch (Exception e) {
				observer.onError(e);
			}
			return Subscriptions.empty();
		});

	}

}

@Component
class RecommendationService {

	private DiscoveryClient client;

	@Autowired
	public RecommendationService(DiscoveryClient client) {
		this.client = client;
	}

	public Observable<StoreDetails> fetch(String customerId) {
		Observable<StoreDetails> details = nearbyStores(customerId)
				.<StoreDetails> flatMap(
						store -> {
							Observable<Recommendation> recommendations = RecommendationApplication
									.collectionToObservable(recommendations(store));
							return recommendations.buffer(500, TimeUnit.MILLISECONDS, 10)
									.first().map(list -> {
										StoreDetails result = new StoreDetails(store);
										result.getRecommendations().addAll(list);
										return result;
									});
						});
		return details;
	}

	public Observable<Store> nearbyStores(String customerId) {
		return RecommendationApplication.collectionToObservable(stores(customerId));
	}

	private Future<Collection<Store>> stores(String customerId) {
		String url = client.getNextServerFromEureka("CUSTOMERS", false).getHomePageUrl();
		final ListenableFutureTask<Collection<Store>> future = new ListenableFutureTask<Collection<Store>>(
				() -> {
					final Collection<Store> stores = new Traverson(new URI(url + "customers/" + customerId),
							MediaTypes.HAL_JSON).follow("stores-nearby").toObject(
							new ParameterizedTypeReference<Resources<Store>>() {
							}).getContent();
					return stores;
				});
		// We need to get the result populated somehow
		Executors.newSingleThreadExecutor().submit(future);
		return future;
	}

	private Future<List<Recommendation>> recommendations(Store store) {
		ListenableFutureTask<List<Recommendation>> future = new ListenableFutureTask<List<Recommendation>>(
				() -> {
					return Arrays.asList(new Recommendation(store.getId()));
				});
		// We need to get the result populated somehow
		Executors.newSingleThreadExecutor().submit(future);
		return future;
	}

}
