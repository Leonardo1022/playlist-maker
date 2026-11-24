package br.com.playlistmaker.adaptadores.saida.spotify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ConfiguracaoSpotifyClient {

    @Value("${spotify.api-url}")
    private String apiUrl;

    @Value("${spotify.accounts-url}")
    private String accountsUrl;

    @Bean(name = "spotifyApiRestClient")
    public RestClient spotifyApiRestClient() {
        return RestClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    @Bean(name = "spotifyAccountsRestClient")
    public RestClient spotifyAccountsRestClient() {
        return RestClient.builder()
                .baseUrl(accountsUrl)
                .build();
    }
}
