package com.ruinhome.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruinhome.user.Role;
import com.ruinhome.user.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthRegisterTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserAccountRepository userAccountRepository;

    private ResultActions postRegister(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("username", username, "password", password))));
    }

    @Test
    void registerCreatesEnabledLandlordAdminWithoutPerson() throws Exception {
        postRegister("chunha01", "password123")
                .andExpect(status().isCreated());

        var account = userAccountRepository.findByUsername("chunha01").orElseThrow();
        assertThat(account.getRole()).isEqualTo(Role.ADMIN);
        assertThat(account.isEnabled()).isTrue();
        assertThat(account.getPerson()).isNull();
        assertThat(account.isRoot()).isFalse();
        assertThat(account.getAreaAdmin()).isEqualTo("chunha01");
        assertThat(account.getPasswordHash()).isNotEqualTo("password123");
    }

    @Test
    void duplicateUsernameRejected() throws Exception {
        postRegister("chunha02", "password123").andExpect(status().isCreated());

        postRegister("chunha02", "password123").andExpect(status().isConflict());
    }

    @Test
    void invalidUsernameRejected() throws Exception {
        postRegister("ab", "password123").andExpect(status().isBadRequest());
        postRegister("nguoi dung", "password123").andExpect(status().isBadRequest());
        postRegister("ten@nhat", "password123").andExpect(status().isBadRequest());

        assertThat(userAccountRepository.existsByUsername("ab")).isFalse();
    }

    @Test
    void passwordLengthBoundsEnforced() throws Exception {
        postRegister("chunha03", "short7c").andExpect(status().isBadRequest());
        postRegister("chunha04", "a".repeat(33)).andExpect(status().isBadRequest());

        assertThat(userAccountRepository.existsByUsername("chunha03")).isFalse();
        assertThat(userAccountRepository.existsByUsername("chunha04")).isFalse();
    }
}
