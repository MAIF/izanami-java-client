package fr.maif;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import fr.maif.requests.FeatureRequest;
import fr.maif.requests.IzanamiConnectionInformation;
import fr.maif.requests.SingleFeatureRequest;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static fr.maif.Mocks.*;
import static fr.maif.requests.IzanamiConnectionInformation.connectionInformation;
import static fr.maif.requests.SingleFeatureRequest.newSingleFeatureRequest;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;

public class SSEClientTest {
    static WireMockServer mockServer;
    static IzanamiClient client;

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
        client.close().join();
        mockServer.resetAll();
    }

    @Test
    public void should_open_connection_on_first_query() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String stub = newResponse().withFeature(id, featureStub).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";


        String eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n";

        mockServer.stubFor(WireMock.get(urlPathEqualTo("/api/v2/events"))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(okForContentType("text/event-stream", eventStream))
        );

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();

        var result = client.checkFeatureActivation(SingleFeatureRequest.newSingleFeatureRequest(id)).join();

        assertThat(result).isTrue();
    }


    @Test
    public void should_open_connection_before_first_query_if_preload_is_specified() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String stub = newResponse().withFeature(id, featureStub).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";


        String eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n";

        mockServer.stubFor(WireMock.get(urlPathEqualTo("/api/v2/events"))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(okForContentType("text/event-stream", eventStream))
        );

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .withPreloadedFeatures(id)
                .build();

        client.isLoaded().join();
        mockServer.resetAll();

        var result = client.checkFeatureActivation(SingleFeatureRequest.newSingleFeatureRequest(id)).join();

        assertThat(result).isTrue();
    }

    @Test
    public void should_update_cache_with_feature_update_events() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String stub = newResponse().withFeature(id, featureStub).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";


        String eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n";

        featureStub.conditions.get("").enabled = false;
        eventStream += "id:eventid2\n" +
                "event:FEATURE_UPDATED\n" +
                "data:" + featureStub.toUpdatedEvent(id) + "\n\n";

        mockServer.stubFor(WireMock.get(urlPathEqualTo("/api/v2/events"))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withChunkedDribbleDelay(10, 3000)
                        .withBody(eventStream)));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();
        client.isLoaded().join();

        var result = client.checkFeatureActivation(SingleFeatureRequest.newSingleFeatureRequest(id)).join();
        assertThat(result).isTrue();

        await().atMost(Duration.ofSeconds(8)).until(() ->
                !client.checkFeatureActivation(SingleFeatureRequest.newSingleFeatureRequest(id)).join()
        );
    }

    @Test
    public void should_remove_feature_from_cache_on_feature_deleted_event() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String stub = newResponse().withFeature(id, featureStub).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";


        String eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n";

        featureStub.conditions.get("").enabled = false;
        eventStream += "id:eventid2\n" +
                "event:FEATURE_DELETED\n" +
                "data:" + featureDeletedEvent(id) + "\n\n";

        mockServer.stubFor(WireMock.get(urlPathEqualTo("/api/v2/events"))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withChunkedDribbleDelay(10, 2000)
                        .withBody(eventStream)));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();
        client.isLoaded().join();

        var result = client.checkFeatureActivation(
                SingleFeatureRequest.newSingleFeatureRequest(id)
        ).join();
        assertThat(result).isTrue();

        eventStream = "id:eventid3\n" +
                "event:FEATURE_STATES\n" +
                "data:" + emptyFeatureStatesEvent() + "\n\n";
        mockServer.resetMappings();
        mockServer.stubFor(WireMock.get(urlPathEqualTo("/api/v2/events"))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(eventStream)));


        await().atMost(Duration.ofSeconds(8)).until(() ->
                !client.checkFeatureActivation(SingleFeatureRequest.newSingleFeatureRequest(id).withErrorStrategy(FeatureClientErrorStrategy.defaultValueStrategy(false))).join()
        );
    }

    @Test
    public void should_remove_feature_from_cache_on_feature_deleted_event_null_default() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String stub = newResponse().withFeature(id, featureStub).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";


        String eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n";

        featureStub.conditions.get("").enabled = false;
        eventStream += "id:eventid2\n" +
                "event:FEATURE_DELETED\n" +
                "data:" + featureDeletedEvent(id) + "\n\n";

        mockServer.stubFor(WireMock.get(urlPathEqualTo("/api/v2/events"))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withChunkedDribbleDelay(10, 2000)
                        .withBody(eventStream)));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();
        client.isLoaded().join();

        var result = client.checkFeatureActivation(
                SingleFeatureRequest.newSingleFeatureRequest(id)
        ).join();
        assertThat(result).isTrue();

        eventStream = "id:eventid3\n" +
                "event:FEATURE_STATES\n" +
                "data:" + emptyFeatureStatesEvent() + "\n\n";
        mockServer.resetMappings();
        mockServer.stubFor(WireMock.get(urlPathEqualTo("/api/v2/events"))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .withQueryParam("features", equalTo(id))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(eventStream)));


        await().atMost(Duration.ofSeconds(8)).until(() ->
                client.checkFeatureActivation(SingleFeatureRequest.newSingleFeatureRequest(id)).join() == null
        );
    }

    @Test
    public void should_enrich_cache_with_added_feature_event() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String id2 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeab";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String stub = newResponse().withFeature(id, featureStub).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";


        String eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n";

        featureStub.conditions.get("").enabled = false;
        eventStream += "id:eventid2\n" +
                "event:FEATURE_CREATED\n" +
                "data:" + featureStub.toAddedEvent(id2) + "\n\n";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .withQueryParam("features", equalTo(id + "," + id2))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withChunkedDribbleDelay(10, 2000)
                        .withBody(eventStream)));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();
        client.isLoaded().join();

        var res = client.checkFeatureActivations(FeatureRequest.newFeatureRequest().withFeatures(id, id2)).join();
        assertThat(res.get(id)).isTrue();
        assertThat(res.get(id2)).isNull();

        await().atMost(Duration.ofSeconds(8)).until(() -> {
            var res2 = client.checkFeatureActivations(FeatureRequest.newFeatureRequest().withFeatures(id, id2)).join();
            return res2.get(id) && Objects.nonNull(res2.get(id2)) && !res2.get(id2);
        });
    }

    @Test
    public void should_take_timeout_into_account_if_server_does_not_respond_in_time() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";


        String eventStream = "";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .withQueryParam("features", equalTo(id))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(eventStream)));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();
        client.isLoaded().join();

        await().atMost(Duration.ofSeconds(5L)).until(() -> {
            var res = client.checkFeatureActivation(
                    FeatureRequest.newSingleFeatureRequest(id)
                            .withCallTimeout(Duration.ofSeconds(2L))
            ).join();

            return res == null;
        });
    }

    @Test
    public void should_use_error_strategy_when_server_throws() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .withQueryParam("features", equalTo(id))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Somethign went wrong !\"}")));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();
        client.isLoaded().join();

        var res = client.checkFeatureActivation(
                FeatureRequest.newSingleFeatureRequest(id)
                        .withErrorStrategy(FeatureClientErrorStrategy.defaultValueStrategy(false))
                        .withCallTimeout(Duration.ofSeconds(2L))
        ).join();

        assertThat(res).isFalse();
    }

    @Test
    public void should_retrieve_script_activation_on_initial_query() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String id2 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeab";
        var featureStub2 = Mocks.feature("bar", false).withOverload(overload(false).withScript("myscript"));
        String stub = newResponse().withFeature(id, featureStub).withFeature(id2, featureStub2).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";


        String eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id + "," + id2))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(eventStream)));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();
        client.isLoaded().join();

        var res = client.checkFeatureActivations(FeatureRequest.newFeatureRequest().withFeatures(id, id2)).join();
        assertThat(res.get(id)).isTrue();
        assertThat(res.get(id2)).isFalse();
    }

    @Test
    public void should_call_remote_izanami_on_second_script_feature_query() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String id2 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeab";
        var featureStub2 = Mocks.feature("bar", false).withOverload(overload(false).withScript("myscript"));
        String stub = newResponse().withFeature(id, featureStub).withFeature(id2, featureStub2).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";


        String eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id + "," + id2))
                .withQueryParam("user", equalTo("foo"))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(eventStream)));

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/features"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id2))
                .withQueryParam("conditions", equalTo("true"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(newResponse().withFeature(id2, featureStub2).toJson())));

        featureStub2.active = true;
        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/features"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id2))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("user", equalTo("foo"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(newResponse().withFeature(id2, featureStub2).toJson())));


        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();
        client.isLoaded().join();

        var res = client.checkFeatureActivations(FeatureRequest.newFeatureRequest().withFeatures(id, id2).withUser("foo")).join();
        assertThat(res.get(id)).isTrue();
        assertThat(res.get(id2)).isFalse();

        var res2 = client.checkFeatureActivations(FeatureRequest.newFeatureRequest().withFeatures(id, id2).withUser("foo")).join();
        assertThat(res2.get(id)).isTrue();
        assertThat(res2.get(id2)).isTrue();
    }

    @Test
    public void should_apply_error_strategy_on_server_error_for_script_feature() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String id2 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeab";
        var featureStub2 = Mocks.feature("bar", false).withOverload(overload(false).withScript("myscript"));
        String stub = newResponse().withFeature(id, featureStub).withFeature(id2, featureStub2).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        String eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id + "," + id2))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(eventStream)));

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/features"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id2))
                .withQueryParam("conditions", equalTo("true"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"Whoops !\"}")));


        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();
        client.isLoaded().join();

        var res = client.checkFeatureActivations(FeatureRequest.newFeatureRequest().withFeatures(id, id2)).join();
        assertThat(res.get(id)).isTrue();
        assertThat(res.get(id2)).isFalse();

        var res2 = client.checkFeatureActivations(FeatureRequest.newFeatureRequest()
                .withFeatures(id, id2)
                .withErrorStrategy(FeatureClientErrorStrategy.defaultValueStrategy(true))
        ).join();
        assertThat(res2.get(id)).isTrue();
        assertThat(res2.get(id2)).isTrue();
    }


    @Test
    public void should_apply_query_timeout_for_specific_script_queries() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String id2 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeab";
        var featureStub2 = Mocks.feature("bar", false).withOverload(overload(false).withScript("myscript"));
        String stub = newResponse().withFeature(id, featureStub).withFeature(id2, featureStub2).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        String eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id + "," + id2))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(eventStream)));

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/features"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id2))
                .withQueryParam("conditions", equalTo("true"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(newResponse().withFeature(id2, featureStub2).toJson())));


        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();
        client.isLoaded().join();

        var res = client.checkFeatureActivations(FeatureRequest.newFeatureRequest().withFeatures(id, id2)).join();
        assertThat(res.get(id)).isTrue();
        assertThat(res.get(id2)).isFalse();

        mockServer.setGlobalFixedDelay(5000);
        var res2 = client.checkFeatureActivations(FeatureRequest.newFeatureRequest()
                .withFeatures(id, id2)
                .withCallTimeout(Duration.ofSeconds(2L))
                .withErrorStrategy(FeatureClientErrorStrategy.defaultValueStrategy(true))
        ).join();
        assertThat(res2.get(id)).isTrue();
        assertThat(res2.get(id2)).isTrue();
    }


    @Test
    public void request_with_payload_should_trigger_POST_queries() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String id2 = "ae5dd05d-4e90-4ce7-bee7-3751750fdeab";
        var featureStub2 = Mocks.feature("bar", false).withOverload(overload(false).withScript("myscript"));
        String stub = newResponse().withFeature(id, featureStub).withFeature(id2, featureStub2).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        String eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n";

        mockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id + "," + id2))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(eventStream)));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();
        client.isLoaded().join();

        var res = client.checkFeatureActivations(FeatureRequest.newFeatureRequest()
                .withFeatures(id, id2)
                .withPayload("{\"foo\": \"bar\"}")
        ).join();
        assertThat(res.get(id)).isTrue();
        assertThat(res.get(id2)).isFalse();
    }


    @Test
    public void should_respect_timeout_when_calling_sse_endpoint() {
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        mockServer.setGlobalFixedDelay(5000);
        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo("foo"))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("")));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .withCallTimeout(Duration.ofSeconds(2L))
                .build();

        assertThat(client.checkFeatureActivation(SingleFeatureRequest.newSingleFeatureRequest("foo")).join()).isNull();
    }

   @Test
    public void should_reconnect_when_connection_is_closed_by_server() throws InterruptedException {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String stub = newResponse().withFeature(id, featureStub).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        String eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(eventStream)));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .withServerSentEventKeepAliveInterval(Duration.ofSeconds(2))
                        .build()
                )
                .withCallTimeout(Duration.ofSeconds(2L))
                .build();
        client.isLoaded().join();
        assertThat(client.checkFeatureActivation(SingleFeatureRequest.newSingleFeatureRequest(id)).join()).isTrue();

        mockServer.resetAll();
        mockServer.shutdownServer();
        System.out.println("MOCK SERVER IS DOWN");

        Thread.sleep(25000);

        featureStub.active = false;
        featureStub.conditions.get("").enabled = false;
        eventStream =
                "id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + newResponse().withFeature(id, featureStub).toSSEJson() + "\n\n";
        System.out.println("RESTARTING MOCK SERVER");
        mockServer.start();
        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(eventStream)));


        await().atMost(Duration.ofSeconds(5L)).until(() -> !client.checkFeatureActivation(SingleFeatureRequest.newSingleFeatureRequest(id)).join());
    }

    @Test
    public void should_reconnect_on_background_timeout() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String stub = newResponse().withFeature(id, featureStub).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        StringBuilder eventStream =
                new StringBuilder("id:eventid\n" +
                        "event:FEATURE_STATES\n" +
                        "data:" + stub + "\n\n");
        for(int i = 0; i < 10_000; i++) {
            eventStream.append("\n                 ");
        }

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withChunkedDribbleDelay(10, 15_000)
                        .withBody(eventStream.toString())));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .withServerSentEventKeepAliveInterval(Duration.ofSeconds(2))
                        .build()
                )
                .withCallTimeout(Duration.ofSeconds(2L))
                .build();
        client.isLoaded().join();
        assertThat(client.checkFeatureActivation(SingleFeatureRequest.newSingleFeatureRequest(id)).join()).isTrue();


        String newEventStream = "id:eventid\n" +
                "event:FEATURE_STATES\n" +
                "data:" + stub + "\n\n";

        featureStub.conditions.get("").enabled = false;
        newEventStream += "id:eventid2\n" +
                "event:FEATURE_UPDATED\n" +
                "data:" + featureStub.toUpdatedEvent(id) + "\n\n";
        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(newEventStream)));
        await().atMost(Duration.ofSeconds(20L)).pollInterval(Duration.ofSeconds(1L)).until(() -> !client.checkFeatureActivation(SingleFeatureRequest.newSingleFeatureRequest(id)).join());
    }

    @Test
    public void should_use_error_strategy_if_backend_returns_erroneous_status_code() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String stub = newResponse().withFeature(id, featureStub).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";

        String newEventStream = "id:eventid\n" +
                "event:FEATURE_STATES\n" +
                "data:" + stub + "\n\n";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v2/events"))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("{\"error\": \"some error\"}")));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();

        await().atMost(1, SECONDS).until(() -> client.checkFeatureActivation(newSingleFeatureRequest(id)).join() == null);
    }

    @Test
    public void should_reconnect_if_backend_returns_erroneous_status_code() {
        String id = "ae5dd05d-4e90-4ce7-bee7-3751750fdeaa";
        var featureStub = Mocks.feature("bar", true).withOverload(overload(true));
        String stub = newResponse().withFeature(id, featureStub).toSSEJson();
        String clientId = "THIS_IS_NOT_A_REAL_DATA_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String clientSecret = "THIS_IS_NOT_A_REAL_SECRET_PLEASE_DONT_FILE_AN_ISSUE_ABOUT_THIS";
        String url = "/api/v2/events";

        String newEventStream = "id:eventid\n" +
                "event:FEATURE_STATES\n" +
                "data:" + stub + "\n\n";

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("{\"error\": \"some error\"}")));

        client = IzanamiClient.newBuilder(
                        IzanamiConnectionInformation
                                .connectionInformation()
                                .withUrl("http://localhost:9999/api")
                                .withClientId(clientId)
                                .withClientSecret(clientSecret)
                )
                .withCacheConfiguration(FeatureCacheConfiguration
                        .newBuilder()
                        .shouldUseServerSentEvent(true)
                        .build()
                )
                .build();

        assertThat(client.checkFeatureActivation(newSingleFeatureRequest(id)).join()).isNull();

        mockServer.resetMappings();
        mockServer.resetRequests();
       assertThat(mockServer.countRequestsMatching(getRequestedFor(WireMock.urlPathEqualTo(url)).build()).getCount()).isZero();

        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(url))
                .withHeader("Izanami-Client-Id", equalTo(clientId))
                .withHeader("Izanami-Client-Secret", equalTo(clientSecret))
                .withQueryParam("features", equalTo(id))
                .withQueryParam("conditions", equalTo("true"))
                .withQueryParam("refreshInterval", equalTo("600"))
                .withQueryParam("keepAliveInterval", equalTo("25"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(newEventStream)));


        await().atMost(10, SECONDS).until(() -> mockServer.countRequestsMatching(getRequestedFor(WireMock.urlPathEqualTo(url)).build()).getCount() == 1);
        mockServer.resetMappings();
        assertThat(client.checkFeatureActivation(newSingleFeatureRequest(id)).join()).isTrue();


    }

}
