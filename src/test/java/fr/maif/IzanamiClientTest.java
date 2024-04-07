package fr.maif;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import fr.maif.errors.IzanamiException;
import fr.maif.requests.IzanamiConnectionInformation;
import fr.maif.requests.SpecificFeatureRequest;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static fr.maif.Mocks.*;
import static fr.maif.requests.IzanamiConnectionInformation.connectionInformation;
import static fr.maif.requests.FeatureRequest.newFeatureRequest;
import static fr.maif.requests.SingleFeatureRequest.newSingleFeatureRequest;
import static fr.maif.FeatureClientErrorStrategy.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

public class IzanamiClientTest {
    static WireMockServer mockServer;

    @BeforeAll
    public static void init() {
        mockServer = new WireMockServer(options().port(9999));
        mockServer.start();
    }

    @BeforeEach
    public void beforeEach() {
        mockServer.setGlobalFixedDelay(10);
    }

    @AfterAll
    public static void tearDown() {
        mockServer.stop();
    }

    @AfterEach
    public void resetMocks() {
        mockServer.resetAll();
    }

    @Test
    public void should_recompute_feature_locally_when_requested_with_different_parameters() throws InterruptedException {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", false).withOverload(overload(true).withCondition(condition(true).withRule(userListRule("foo"))));
        String stub = newResponse().withFeature(id, featureStub).toJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        mockServer.stubFor(WireMock.get("/api/v2/features?conditions=true&features=" + id + "&user=bar")
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(stub)
                )
        );

        var client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .enabled(true)
                        .build()
                )
                .build();

        var result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)
                        .withUser("bar")
        ).join();
        assertThat(result).isFalse();


        mockServer.resetAll();

        result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)
                        .withUser("foo")
        ).join();
        assertThat(result).isTrue();

        result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)
                        .withUser("bar")
        ).join();
        assertThat(result).isFalse();
    }

    @Test
    public void should_allow_to_bypass_cache() throws InterruptedException {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", false).withOverload(overload(false));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        mockServer.stubFor(WireMock.get("/api/v2/features?conditions=true&features=" + id + "&user=bar")
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                ));

        var client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation.connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration.newBuilder().enabled(true).build())
                .build();

        var result = client.checkFeatureActivation(
                newSingleFeatureRequest("ae5dd05d-4e90-4ce7-bee7-3751750fdeaa")
                        .withUser("bar")).join();
        assertThat(result).isFalse();

        featureStub.active = true;

        mockServer.stubFor(WireMock.get("/api/v2/features?conditions=true&features=ae5dd05d-4e90-4ce7-bee7-3751750fdeaa&user=bar")
                .withHeader("Izanami-Client-Id", equalTo("THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS"))
                .withHeader("Izanami-Client-Secret", equalTo("THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS"))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                ));

        result = client.checkFeatureActivation(
                newSingleFeatureRequest("ae5dd05d-4e90-4ce7-bee7-3751750fdeaa")
                        .withUser("bar")
                        .ignoreCache(true)
        ).join();
        assertThat(result).isTrue();
    }

    @Test
    public void cache_bypass_should_update_cache() throws InterruptedException {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", false).withOverload(overload(false));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        mockServer.stubFor(WireMock.get("/api/v2/features?conditions=true&features=" + id)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).build();

        var result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)).join();

        assertThat(result).isFalse();

        featureStub.active(true).withOverload(overload(true));

        mockServer.stubFor(WireMock.get("/api/v2/features?conditions=true&features=" + id)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                ));


        result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)
                        .ignoreCache(true)
        ).join();
        assertThat(result).isTrue();

        mockServer.resetAll();

        result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)
        ).join();
        assertThat(result).isTrue();
    }

    @Test
    public void should_not_use_cache_for_script_feature() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true).withScript("foo"));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        mockServer.stubFor(WireMock.get("/api/v2/features?conditions=true&features=" + id)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).build();

        var result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)).join();

        assertThat(result).isTrue();

        featureStub.active = false;

        mockServer.stubFor(WireMock.get("/api/v2/features?conditions=true&features=" + id)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)).join();

        assertThat(result).isFalse();
    }


    @Test
    public void should_not_use_cache_if_disabled() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features?conditions=true&features=" + id;

        mockServer.stubFor(WireMock.get(url)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(false).build()
                ).build();

        var result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)).join();

        assertThat(result).isTrue();

        featureStub.active = false;

        mockServer.stubFor(WireMock.get("/api/v2/features?conditions=true&features=" + id)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)).join();

        assertThat(result).isFalse();
        var count = mockServer.countRequestsMatching(getRequestedFor(urlEqualTo(url)).build()).getCount();
        assertThat(count).isEqualTo(2);

    }


    @Test
    public void should_use_cache_even_if_disabled_when_query_fails() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features?conditions=true&features=" + id;

        mockServer.stubFor(WireMock.get(url)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(false).build()
                ).build();

        var result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)).join();

        assertThat(result).isTrue();

        mockServer.resetAll();

        result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)).join();

        assertThat(result).isTrue();
    }

    @Test
    public void should_use_cache_even_if_ignored_when_query_fails() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features?conditions=true&features=" + id;

        mockServer.stubFor(WireMock.get(url)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).build();

        var result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)).join();

        assertThat(result).isTrue();

        mockServer.resetAll();

        result = client.checkFeatureActivation(
                newSingleFeatureRequest(id).ignoreCache(true)).join();

        assertThat(result).isTrue();
    }

    @Test
    public void should_not_use_cache_on_failed_query_if_specified() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features?conditions=true&features=" + id;

        mockServer.stubFor(WireMock.get(url)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).build();

        var result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)).join();

        assertThat(result).isTrue();

        mockServer.resetAll();

        assertThatThrownBy(() -> {
            client.checkFeatureActivation(
                    newSingleFeatureRequest(id)
                            .ignoreCache(true)
                            .withErrorStrategy(failStrategy().fallbackOnLastKnownStrategy(false))
            ).join();
        });
    }

    @Test
    public void should_prioritize_feature_cache_instruction_over_query() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features?conditions=true&features=" + id;

        mockServer.stubFor(WireMock.get(url)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).build();

        var result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)
        ).join();
        assertThat(result).isTrue();

        assertThat(featureStub.active).isTrue();
        featureStub.active = false;

        mockServer.resetAll();

        mockServer.stubFor(WireMock.get(url)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var multipleResult = client.checkFeatureActivations(
                newFeatureRequest()
                        .withFeatures(
                                SpecificFeatureRequest.feature(id).ignoreCache(true)
                        ).ignoreCache(false)
        ).join();

        assertThat(multipleResult.get(id)).isFalse();
    }

    @Test
    public void fail_strategy_should_throw_an_exception_when_needed() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).withErrorStrategy(failStrategy())
                .build();

        assertThatThrownBy(() -> {
            client.checkFeatureActivation(
                    newSingleFeatureRequest(id)
            ).join();
        }).isInstanceOf(CompletionException.class).hasCauseInstanceOf(IzanamiException.class);
    }

    @Test
    public void default_value_strategy_should_return_given_value() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).withErrorStrategy(defaultValueStrategy(true))
                .build();

        var result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)
        ).join();

        assertThat(result).isTrue();
    }

    @Test
    public void callback_strategy_should_return_callback_value() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).withErrorStrategy(callbackStrategy(err -> {
                    callbackCalled.set(true);
                    return CompletableFuture.completedFuture(true);
                }))
                .build();

        var result = client.checkFeatureActivation(
                newSingleFeatureRequest(id)
        ).join();

        assertThat(result).isTrue();
        assertThat(callbackCalled.get()).isTrue();
    }


    @Test
    public void feature_error_strategy_should_prevail_over_query() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                )
                .build();

        var result = client.checkFeatureActivations(
                newFeatureRequest()
                        .withFeatures(
                                SpecificFeatureRequest
                                        .feature(id)
                                        .withErrorStrategy(defaultValueStrategy(true))
                        )
                        .withErrorStrategy(failStrategy())
        ).join();

        assertThat(result.get(id)).isTrue();
    }

    @Test
    public void feature_error_strategy_should_prevail_over_global() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).withErrorStrategy(failStrategy())
                .build();

        var result = client.checkFeatureActivations(
                newFeatureRequest()
                        .withFeatures(
                                SpecificFeatureRequest
                                        .feature(id)
                                        .withErrorStrategy(defaultValueStrategy(true))
                        )
        ).join();

        assertThat(result.get(id)).isTrue();
    }

    @Test
    public void query_error_strategy_should_prevail_over_global() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).withErrorStrategy(failStrategy())
                .build();

        var result = client.checkFeatureActivations(
                newFeatureRequest()
                        .withFeatures(id)
                        .withErrorStrategy(defaultValueStrategy(true))
        ).join();

        assertThat(result.get(id)).isTrue();
    }

    @Test
    public void fail_strategy_should_throw_for_single_feature_request() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).withErrorStrategy(failStrategy())
                .build();

        assertThatThrownBy(() -> {
            client.checkFeatureActivation(
                    newSingleFeatureRequest(id)
            ).join();
        }).hasCauseInstanceOf(IzanamiException.class);
    }

    @Test
    public void fail_strategy_should_throw_for_multiple_feature_query() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).withErrorStrategy(failStrategy())
                .build();

        assertThatThrownBy(() -> {
            client.checkFeatureActivations(
                    newFeatureRequest().withFeatures(id)
            ).join();
        }).hasCauseInstanceOf(IzanamiException.class);
    }

    @Test
    public void should_return_all_features_activation_for_multi_feature_query() {
        String id1 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String id2 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeae";
        var featureStub1 = Mocks.feature("bar", true).withOverload(overload(true));
        var featureStub2 = Mocks.feature("bar", false).withOverload(overload(true));
        var response = newResponse().withFeature(id1, featureStub1).withFeature(id2, featureStub2);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features?conditions=true&features=" + id1 + "," + id2;
        String url2 = "/api/v2/features?conditions=true&features=" + id2 + "," + id1;

        mockServer.stubFor(WireMock.get(url)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );
        mockServer.stubFor(WireMock.get(url2)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).build();

        var result = client.checkFeatureActivations(
                newFeatureRequest().withFeatures(id1, id2)
        ).join();

        assertThat(result.get(id1)).isTrue();
        assertThat(result.get(id2)).isFalse();
    }


    @Test
    public void should_use_error_strategy_for_missing_feature_in_multi_feature_query() {
        String id1 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String id2 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeae";
        var featureStub1 = Mocks.feature("bar", true).withOverload(overload(true));
        var response = newResponse().withFeature(id1, featureStub1);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features?conditions=true&features=" + id1 + "," + id2;
        String url2 = "/api/v2/features?conditions=true&features=" + id2 + "," + id1;

        mockServer.stubFor(WireMock.get(url)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );
        mockServer.stubFor(WireMock.get(url2)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).withErrorStrategy(defaultValueStrategy(false))
                .build();

        var result = client.checkFeatureActivations(
                newFeatureRequest().withFeatures(id1, id2)
        ).join();

        assertThat(result.get(id1)).isTrue();
        assertThat(result.get(id2)).isFalse();
    }


    @Test
    public void should_use_individual_strategies_when_query_fails_if_defined() {
        String id1 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String id2 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeae";
        String id3 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeao";
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features?conditions=true&features=" + id1 + "," + id2 + "," + id3;
        String url2 = "/api/v2/features?conditions=true&features=" + id1 + "," + id3 + "," + id2;
        String url3 = "/api/v2/features?conditions=true&features=" + id2 + "," + id1 + "," + id3;
        String url4 = "/api/v2/features?conditions=true&features=" + id2 + "," + id3 + "," + id1;
        String url5 = "/api/v2/features?conditions=true&features=" + id3 + "," + id1 + "," + id2;
        String url6 = "/api/v2/features?conditions=true&features=" + id3 + "," + id2 + "," + id1;

        List.of(url, url2, url3, url4, url5, url6).forEach(u -> {
            mockServer.stubFor(WireMock.get(u)
                    .withHeader("Izanami-Client-Id", equalTo(clientId))
                    .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                    .willReturn(WireMock.serverError()
                            .withBody("foo")
                    )
            );
        });

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).withErrorStrategy(failStrategy())
                .build();

        var result = client.checkFeatureActivations(
                newFeatureRequest()
                        .withFeatures(
                                SpecificFeatureRequest.feature(id1).withErrorStrategy(defaultValueStrategy(true)),
                                SpecificFeatureRequest.feature(id2).withErrorStrategy(callbackStrategy(err -> CompletableFuture.completedFuture(false))),
                                SpecificFeatureRequest.feature(id3).withErrorStrategy(nullValueStrategy())
                        )
        ).join();

        assertThat(result.get(id1)).isTrue();
        assertThat(result.get(id2)).isFalse();
        assertThat(result.get(id3)).isNull();
    }

    @Test
    public void should_return_activation_status_for_given_context() {
        String id1 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String id2 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeae";
        var featureStub1 = Mocks.feature("bar", true).withOverload(overload(true)).withOverload("foo", overload(false));
        var featureStub2 = Mocks.feature("bar", true).withOverload(overload(false)).withOverload("foo", overload(true));
        var response = newResponse().withFeature(id1, featureStub1).withFeature(id2, featureStub2);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                        .withQueryParam("conditions", equalTo("true"))
                        .withQueryParam("features", equalTo(id1 + "," + id2))
                        .withQueryParam("context", absent())
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        featureStub1.active = false;
        featureStub2.active = true;
        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id1 + "," + id2))
                .withQueryParam("context", equalTo("foo"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).build();

        var result = client.checkFeatureActivations(
                newFeatureRequest().withFeatures(id1, id2).withContext("foo")
        ).join();

        assertThat(result.get(id1)).isFalse();
        assertThat(result.get(id2)).isTrue();

        // Test cache
        result = client.checkFeatureActivations(
                newFeatureRequest().withFeatures(id1, id2).withContext("foo")
        ).join();

        assertThat(result.get(id1)).isFalse();
        assertThat(result.get(id2)).isTrue();

        result = client.checkFeatureActivations(
                newFeatureRequest().withFeatures(id1, id2)
        ).join();

        assertThat(result.get(id1)).isTrue();
        assertThat(result.get(id2)).isFalse();
    }


    @Test
    public void should_handle_context_hierarchy_correctly() {
        String id1 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String id2 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeae";
        String id3 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeao";
        var featureStub1 = Mocks.feature("bar1", true).withOverload(overload(true));
        var featureStub2 = Mocks.feature("bar2", false).withOverload(overload(true)).withOverload("foo", overload(true)).withOverload("foo/bar", overload(false));
        var featureStub3 = Mocks.feature("bar3", true).withOverload(overload(false)).withOverload("foo", overload(true));
        var response = newResponse().withFeature(id1, featureStub1).withFeature(id2, featureStub2).withFeature(id3, featureStub3);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id1 + "," + id2 + "," + id3))
                .withQueryParam("context", equalTo("foo/bar"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).build();

        var result = client.checkFeatureActivations(
                newFeatureRequest().withFeatures(id1, id2, id3).withContext("foo/bar")
        ).join();

        assertThat(result.get(id1)).isTrue();
        assertThat(result.get(id2)).isFalse();
        assertThat(result.get(id3)).isTrue();

        // Test cache
        result = client.checkFeatureActivations(
                newFeatureRequest().withFeatures(id1, id2, id3).withContext("foo/bar")
        ).join();

        assertThat(result.get(id1)).isTrue();
        assertThat(result.get(id2)).isFalse();
        assertThat(result.get(id3)).isTrue();
    }


    @Test
    public void empty_multiple_query_should_return_empty_map() {
        var client = IzanamiClient
            .newBuilder(
                    connectionInformation()
                            .withUrl("http://localhost:9999/api")
                            .withClientId("foo")
                            .withClientSecret("bar")
            ).withCacheConfiguration(
                    FeatureCacheConfiguration.newBuilder().enabled(true).build()
            ).build();
        var result = client.checkFeatureActivations(newFeatureRequest()).join();

        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void empty_single_query_should_throw() {
        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId("foo")
                                .withClientSecret("bar")
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).build();
        assertThatThrownBy(() -> client.checkFeatureActivation(newSingleFeatureRequest(null)).join())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void single_queries_with_cache_ignore_should_ignore_cache() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features?conditions=true&features=" + id;

        mockServer.stubFor(WireMock.get(url)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).build();


        var result = client.checkFeatureActivation(newSingleFeatureRequest(id)).join();

        assertThat(result).isTrue();

        featureStub.active = false;
        mockServer.stubFor(WireMock.get(url)
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        result = client.checkFeatureActivation(newSingleFeatureRequest(id)).join();
        assertThat(result).isTrue();

        result = client.checkFeatureActivation(newSingleFeatureRequest(id).ignoreCache(true)).join();
        assertThat(result).isFalse();

    }

    @Test
    public void multiple_queries_with_cache_ignore_should_ignore_cache() {
        String id1 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String id2 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeae";
        var featureStub1 = Mocks.feature("bar1", true).withOverload(overload(true));
        var featureStub2 = Mocks.feature("bar2", false).withOverload(overload(false));
        var response = newResponse().withFeature(id1, featureStub1).withFeature(id2, featureStub2);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id1 + "," + id2))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).build();


        var result = client.checkFeatureActivations(newFeatureRequest().withFeatures(id1, id2)).join();

        assertThat(result.get(id1)).isTrue();
        assertThat(result.get(id2)).isFalse();

        featureStub1.active = false;
        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id1 + "," + id2))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        result = client.checkFeatureActivations(newFeatureRequest().withFeatures(id1, id2)).join();
        assertThat(result.get(id1)).isTrue();
        assertThat(result.get(id2)).isFalse();

        result = client.checkFeatureActivations(newFeatureRequest().withFeatures(id1, id2).ignoreCache(true)).join();
        assertThat(result.get(id1)).isFalse();
        assertThat(result.get(id2)).isFalse();
    }

    @Test
    public void query_timeout_should_apply() {
        String id1 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub1 = Mocks.feature("bar1", true).withOverload(overload(true));
        var response = newResponse().withFeature(id1, featureStub1);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features";

        mockServer.setGlobalFixedDelay(5000);
        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id1))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder().enabled(true).build()
                ).withCallTimeout(Duration.ofSeconds(2L))
                .withErrorStrategy(defaultValueStrategy(false))
                .build();


        var result = client.checkFeatureActivations(newFeatureRequest().withFeatures(id1)).join();

        assertThat(result.get(id1)).isFalse();
    }

    @Test
    public void cache_should_be_refreshed_at_specified_periods() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar1", true).withOverload(overload(true));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder()
                                .withRefreshInterval(Duration.ofSeconds(2L))
                                .enabled(true)
                                .build()
                )
                .build();

        var result = client.checkFeatureActivation(newSingleFeatureRequest(id)).join();
        assertThat(result).isTrue();

        featureStub.conditions.put("", overload(false));
        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );
        result = client.checkFeatureActivation(newSingleFeatureRequest(id)).join();
        assertThat(result).isTrue();

        await().atMost(5, SECONDS).until(() -> {
            var localResult = client.checkFeatureActivation(newSingleFeatureRequest(id)).join();
            return !localResult;
        });

    }

    @Test
    public void cache_should_not_be_cleared_if_refresh_fails() throws InterruptedException {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar1", true).withOverload(overload(true));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder()
                                .withRefreshInterval(Duration.ofSeconds(2L))
                                .enabled(true)
                                .build()
                )
                .build();

        var result = client.checkFeatureActivation(newSingleFeatureRequest(id)).join();
        assertThat(result).isTrue();

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.serverError())
        );
        result = client.checkFeatureActivation(newSingleFeatureRequest(id)).join();
        assertThat(result).isTrue();

        Thread.sleep(10_000);

        var localResult = client.checkFeatureActivation(newSingleFeatureRequest(id).withErrorStrategy(defaultValueStrategy(false))).join();
        assertThat(localResult).isTrue();

    }

    @Test
    public void cache_should_not_be_cleared_if_refresh_timeout() throws InterruptedException {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar1", true).withOverload(overload(true));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder()
                                .withRefreshInterval(Duration.ofSeconds(2L))
                                .enabled(true)
                                .build()
                )
                .withCallTimeout(Duration.ofSeconds(1L))
                .build();

        var result = client.checkFeatureActivation(newSingleFeatureRequest(id)).join();
        assertThat(result).isTrue();


        mockServer.setGlobalFixedDelay(10_000);
        Thread.sleep(5000);

        var localResult = client.checkFeatureActivation(newSingleFeatureRequest(id).withErrorStrategy(defaultValueStrategy(false))).join();
        assertThat(localResult).isTrue();
    }

    @Test
    public void preload_should_aliment_cache() throws InterruptedException {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar1", true).withOverload(overload(true));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder()
                                .enabled(true)
                                .build()
                )
                .withPreloadedFeatures(id)
                .build();

        client.isLoaded().join();

        mockServer.resetAll();
        var result = client.checkFeatureActivation(newSingleFeatureRequest(id)).join();
        assertThat(result).isTrue();
    }

    @Test
    public void preload_failure_should_no_aliment_cache() throws InterruptedException {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar1", true).withOverload(overload(true));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.serverError())
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                ).withCacheConfiguration(
                        FeatureCacheConfiguration.newBuilder()
                                .enabled(true)
                                .build()
                )
                .withPreloadedFeatures(id)
                .build();

        client.isLoaded().join();

        mockServer.resetAll();
        var result = client.checkFeatureActivation(newSingleFeatureRequest(id)).join();
        assertThat(result).isNull();

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );
        result = client.checkFeatureActivation(newSingleFeatureRequest(id)).join();
        assertThat(result).isTrue();
    }

    @Test
    public void request_with_payload_should_trigger_POST_queries()  {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar1", true).withOverload(overload(true));
        var response = newResponse().withFeature(id, featureStub);
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/features";

        mockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo(url)).withRequestBody(equalToJson("{\"foo\": \"bar\"}"))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json")
                        .withBody(response.toJson())
                )
        );

        var client = IzanamiClient
                .newBuilder(
                        connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .build();

        var result = client.checkFeatureActivation(newSingleFeatureRequest(id).withPayload("{\"foo\": \"bar\"}")).join();
        assertThat(result).isTrue();
    }
}
