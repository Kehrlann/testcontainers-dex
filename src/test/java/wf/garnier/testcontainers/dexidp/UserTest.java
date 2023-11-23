package wf.garnier.testcontainers.dexidp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
