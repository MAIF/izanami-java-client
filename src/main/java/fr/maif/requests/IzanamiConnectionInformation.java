package fr.maif.requests;

import java.util.Map;

/**
 * Class that contains everything needed to establish connection with Izanami.
 */
public class IzanamiConnectionInformation {
    public final String clientId;
    public final String clientSecret;
    public final String url;

    private IzanamiConnectionInformation(String url, String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.url = url;
    }

    /**
     * Create an empty connectionInformation
     * @return a new EmptyConnectionInformation
     */
    public static EmptyConnectionInformation connectionInformation() {
        return new EmptyConnectionInformation();
    }

    /**
     * Provide headers to use for remote izanami call
     * @return headers
     */
    public Map<String, String> headers() {
        return Map.of(
                "Izanami-Client-Id", clientId,
                "Izanami-Client-Secret", clientSecret
        );
    }

    public static class EmptyConnectionInformation {
        /**
         * Remote izanami URL
         * @param url url of remote izanami, it should include "/api", for instance "https://my-remote-izanami/api"
         * @return a new UrlConnectionInformation that contains given url
         */
        public UrlConnectionInformation withUrl(String url) {
            return new UrlConnectionInformation(url);
        }
    }

    public static class UrlConnectionInformation {
        public final String url;

        private UrlConnectionInformation(String url) {
            this.url = url;
        }

        /**
         * Client id to use for remote izanami requests
         * @param clientId client id
         * @return a new ClientIdConnectionInformation that contains given url and client id
         */
        public ClientIdConnectionInformation withClientId(String clientId) {
            return new ClientIdConnectionInformation(url, clientId);
        }
    }

    public static class ClientIdConnectionInformation {
        public final String url;
        public final String clientId;

        private ClientIdConnectionInformation(String url, String clientId) {
            this.clientId = clientId;
            this.url = url;
        }

        /**
         * Client secret to use for remote izanami requests
         * @param clientSecret client secret
         * @return a new IzanamiConnectionInformation that contains given url, client id and client secret
         */
        public IzanamiConnectionInformation withClientSecret(String clientSecret) {
            return new IzanamiConnectionInformation(url, clientId, clientSecret);
        }
    }
}
