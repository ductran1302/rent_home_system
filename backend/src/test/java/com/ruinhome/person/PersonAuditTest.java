package com.ruinhome.person;

import com.ruinhome.user.Role;
import com.ruinhome.user.UserAccount;
import com.ruinhome.user.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PersonAuditTest {

    @Autowired
    private PersonService personService;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSetsCreatorAndLeavesUpdatedEmpty() {
        var created = personService.create(new PersonDtos.PersonRequest(
                "Nguyen Van Test", null, "0900000000", null));

        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNull();
        assertThat(created.createdBy()).isEqualTo("admin");
        assertThat(created.updatedBy()).isNull();
    }

    @Test
    void updateSetsUpdaterAndTimestamp() {
        var created = personService.create(new PersonDtos.PersonRequest(
                "Nguyen Van Test", null, null, null));
        var updated = personService.update(created.id(), new PersonDtos.PersonRequest(
                "Nguyen Van Test 2", null, null, null));

        assertThat(updated.updatedAt()).isNotNull();
        assertThat(updated.updatedBy()).isEqualTo("admin");
    }

    @Test
    void softDeleteBlockedWhenActiveAccountLinked() {
        var created = personService.create(new PersonDtos.PersonRequest(
                "Nguyen Van Test", null, null, null));
        UserAccount account = new UserAccount();
        account.setUsername("test-" + System.nanoTime());
        account.setPasswordHash("hash");
        account.setRole(Role.USER);
        account.setPerson(personRepository.findById(created.id()).orElseThrow());
        userAccountRepository.save(account);

        assertThatThrownBy(() -> personService.softDelete(created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void softDeleteWorksWhenNoActiveAccount() {
        var created = personService.create(new PersonDtos.PersonRequest(
                "Nguyen Van Test", null, null, null));

        personService.softDelete(created.id());

        var person = personRepository.findById(created.id()).orElseThrow();
        assertThat(person.isActive()).isFalse();
        assertThat(person.getUpdatedBy()).isEqualTo("admin");
    }

    @Test
    void listExcludesSoftDeletedPerson() {
        var created = personService.create(new PersonDtos.PersonRequest(
                "Nguyen Van Bi Xoa", null, null, null));
        personService.softDelete(created.id());

        var withoutQuery = personService.list(null, 0, 200).getContent().stream()
                .map(PersonDtos.PersonResponse::fullName)
                .toList();
        assertThat(withoutQuery).doesNotContain("Nguyen Van Bi Xoa");

        var withQuery = personService.list("Bi Xoa", 0, 200).getContent();
        assertThat(withQuery).isEmpty();
    }
}
