// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.context;

import com.microsoft.alm.plugin.authentication.AuthHelper;
import com.microsoft.alm.plugin.authentication.AuthenticationInfo;
import com.microsoft.alm.plugin.context.ServerContext;
import com.microsoft.alm.plugin.services.HttpProxyService;
import com.microsoft.alm.plugin.services.PluginServiceProvider;
import org.apache.commons.lang.StringUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.VersionInfo;
import org.glassfish.jersey.SslConfigurator;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.client.spi.ConnectorProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;

/**
 * JAXRS Rest Client helper
 */
public class RestClientHelper {

    public static Client getClient(final String serverUri, final String accessTokenValue) {
        final Credentials credentials = new UsernamePasswordCredentials("accessToken", accessTokenValue);
        final ClientConfig clientConfig = getClientConfig(ServerContext.Type.VSO_DEPLOYMENT, credentials, serverUri,
                PluginServiceProvider.getInstance().getHttpProxyService().useHttpProxy());

        final Client localClient = ClientBuilder.newClient(clientConfig);
        return localClient;
    }

    public static Client getClient(final ServerContext.Type type, final AuthenticationInfo authenticationInfo) {
        final ClientConfig clientConfig = getClientConfig(type, authenticationInfo,
                PluginServiceProvider.getInstance().getHttpProxyService().useHttpProxy());
        final Client localClient = ClientBuilder.newClient(clientConfig);
        return localClient;
    }

    public static ClientConfig getClientConfig(final ServerContext.Type type,
                                               final Credentials credentials,
                                               final String serverUri,
                                               final boolean includeProxySettings) {

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, credentials);

        final ConnectorProvider connectorProvider = new ApacheConnectorProvider();

        final ClientConfig clientConfig = new ClientConfig().connectorProvider(connectorProvider);
        clientConfig.property(ApacheClientProperties.CREDENTIALS_PROVIDER, credentialsProvider);
        clientConfig.property(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.BUFFERED);

        // For TFS OnPrem we only support NTLM authentication right now. Since 2016 servers support Basic as well,
        // we need to let the server and client negotiate the protocol instead of preemptively assuming Basic.
        // TODO: This prevents PATs from being used OnPrem. We need to fix this soon to support PATs onPrem.
        clientConfig.property(ApacheClientProperties.PREEMPTIVE_BASIC_AUTHENTICATION, type != ServerContext.Type.TFS);

        //Define a local HTTP proxy
        if (includeProxySettings) {
            final HttpProxyService proxyService = PluginServiceProvider.getInstance().getHttpProxyService();
            final String proxyUrl = proxyService.getProxyURL();
            clientConfig.property(ClientProperties.PROXY_URI, proxyUrl);
            if (proxyService.isAuthenticationRequired()) {
                // To work with authenticated proxies and TFS, we provide the proxy credentials if they are registered
                final AuthScope ntlmAuthScope =
                        new AuthScope(proxyService.getProxyHost(), proxyService.getProxyPort(),
                                AuthScope.ANY_REALM, AuthScope.ANY_SCHEME);
                credentialsProvider.setCredentials(ntlmAuthScope,
                        new UsernamePasswordCredentials(proxyService.getUserName(), proxyService.getPassword()));
            }
        }

        // if this is a onPrem server and the uri starts with https, we need to setup ssl
        if (isSSLEnabledOnPrem(type, serverUri)) {
            clientConfig.property(ApacheClientProperties.SSL_CONFIG, getSslConfigurator());
        }

        // register a filter to set the User Agent header
        clientConfig.register(new ClientRequestFilter() {
            @Override
            public void filter(final ClientRequestContext requestContext) throws IOException {
                // The default user agent is something like "Jersey/2.6"
                final String defaultUserAgent = VersionInfo.getUserAgent("Apache-HttpClient", "org.apache.http.client", HttpClientBuilder.class);
                // We get the user agent string from the Telemetry context
                final String userAgent = PluginServiceProvider.getInstance().getTelemetryContextInitializer().getUserAgent(defaultUserAgent);
                // Finally, we can add the header
                requestContext.getHeaders().add(HttpHeaders.USER_AGENT, userAgent);
            }
        });

        return clientConfig;
    }

    public static ClientConfig getClientConfig(final ServerContext.Type type,
                                               final AuthenticationInfo authenticationInfo,
                                               final boolean includeProxySettings) {
        final Credentials credentials = AuthHelper.getCredentials(type, authenticationInfo);

        return getClientConfig(type, credentials, authenticationInfo.getServerUri(), includeProxySettings);
    }

    public static boolean isSSLEnabledOnPrem(final ServerContext.Type type, final String serverUri) {
        return type == ServerContext.Type.TFS && serverUri.toLowerCase().startsWith("https://");
    }

    public static SslConfigurator getSslConfigurator() {
        /**
         * Set up trust store and key store for the https connection.
         *
         * http://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html
         * See table 6.
         */
        final SslConfigurator sslConfigurator = SslConfigurator.newInstance();

        // Trust stores are used to store CA certificates. It is used to trust the server connection.
        setupTrustStore(sslConfigurator);

        // Key stores are used to store client certificates.  It is used to authenticate the client.
        setupKeyStore(sslConfigurator);

        return sslConfigurator;
    }

    private static SslConfigurator setupTrustStore(final SslConfigurator sslConfigurator) {
        // Create trust store from .cer
        // keytool.exe  -import -trustcacerts -alias root -file cacert.cer -keystore truststore.jks
        final String trustStore = System.getProperty("javax.net.ssl.trustStore");
        final String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword", StringUtils.EMPTY);

        final String trustStoreType = System.getProperty("javax.net.ssl.trustStoreType", "JKS");
        final String trustManagerFactoryAlgorithm = System.getProperty("ssl.TrustManagerFactory.algorithm", "PKIX");

        if (trustStore != null) {
            sslConfigurator
                    .trustStoreFile(trustStore)
                    .trustStorePassword(trustStorePassword)
                    .trustStoreType(trustStoreType)
                    .trustManagerFactoryAlgorithm(trustManagerFactoryAlgorithm)
                    .securityProtocol("SSL");
        }

        return sslConfigurator;
    }

    private static SslConfigurator setupKeyStore(final SslConfigurator sslConfigurator) {
        // Create keystore from pkx:
        // keytool -importkeystore -srckeystore mycert.pfx -srcstoretype pkcs12 -destkeystore keystore.jks -deststoretype JKS
        final String keyStore = System.getProperty("javax.net.ssl.keyStore");
        final String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword", StringUtils.EMPTY);

        if (keyStore != null) {
            sslConfigurator
                    .keyStoreFile(keyStore)
                    .keyStorePassword(keyStorePassword);
        }

        return sslConfigurator;
    }
}
