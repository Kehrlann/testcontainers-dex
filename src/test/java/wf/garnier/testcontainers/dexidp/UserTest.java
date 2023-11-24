package wf.garnier.testcontainers.dexidp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class UserTest {

    @Test
    void user() {
        var user = new DexContainer.User("user", "user@example.com", "password");

        assertThat(user.username()).isEqualTo("user");
        assertThat(user.email()).isEqualTo("user@example.com");
        assertThat(user.clearTextPassword()).isEqualTo("password");
    }

    @Test
    void bcryptHash() {
        var user = new DexContainer.User("user", "user@example.com", "password");

        assertThat(BCrypt.checkpw("password", user.bcryptPassword())).isTrue();
    }

    @Test
    void mustHaveUsername() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.User(null, "valid@example.com", "valid"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.User("", "valid@example.com", "valid"));
    }

    @Test
    void mustHaveEmail() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.User("valid", null, "valid"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.User("valid", "", "valid"));
    }

    @Test
    void mustHavePassword() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.User("valid", "valid@example.com", null));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DexContainer.User("valid", "valid@example.com", ""));
    }
}
