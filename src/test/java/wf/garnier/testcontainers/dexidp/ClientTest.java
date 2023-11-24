package wf.garnier.testcontainers.dexidp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ClientTest {

    @Test
    void canonicalConstructor() {
        var client = new DexContainer.Client(
                "client-id",
                "client-secret",
                "https://example.com/callback"
        );

        assertThat(client.clientId()).isEqualTo("client-id");
        assertThat(client.clientSecret()).isEqualTo("client-secret");
        assertThat(client.redirectUri()).isEqualTo("https://example.com/callback");
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
        assertThat(client.redirectUri()).isEqualTo("https://one.example.com/callback");
    }


    @Test
    void mustHaveRedirectUri() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.Client("client-id", "client-secret", null));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.Client("client-id", "client-secret", ""));
    }

    @Test
    void mustHaveClientId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.Client(null, "client-secret", "https://valid.example.com"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.Client("", "client-secret", "https://valid.example.com"));
    }

    @Test
    void mustHaveClientSecret() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.Client("client-id", null, "http://valid.example.com"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.Client("client-id", "", "http://valid.example.com"));
    }
}

