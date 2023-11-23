package wf.garnier.testcontainers.dexidp;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ClientTest {

    @Test
    void canonicalConstructor() {
        var client = new DexContainer.Client(
                "client-id",
                "client-secret",
                List.of(
                        "https://one.example.com/callback",
                        "https://two.example.com/callback"
                )
        );

        assertThat(client.clientId()).isEqualTo("client-id");
        assertThat(client.clientSecret()).isEqualTo("client-secret");
        assertThat(client.redirectUris()).containsExactly("https://one.example.com/callback", "https://two.example.com/callback");
        assertThat(client.redirectUri()).isEqualTo("https://one.example.com/callback");
    }

    @Test
    void singleRedirectUri() {
        var client = new DexContainer.Client(
                "client-id",
                "client-secret",
                "https://one.example.com/callback"
        );

        assertThat(client.clientId()).isEqualTo("client-id");
        assertThat(client.clientSecret()).isEqualTo("client-secret");
        assertThat(client.redirectUris()).containsExactly("https://one.example.com/callback");
        assertThat(client.redirectUri()).isEqualTo("https://one.example.com/callback");
    }


    @Test
    void mustHaveRedirectUri() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.Client("client-id", "client-secret", Collections.emptyList()));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.Client("client-id", "client-secret", (String) null));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.Client("client-id", "client-secret", (List<String>) null));
    }

    @Test
    void mustHaveClientId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.Client(null, "client-secret", List.of("https://example.com")));
    }

    @Test
    void mustHaveClientSecret() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.Client("client-id", null, List.of("http://example.com")));

    }
}

