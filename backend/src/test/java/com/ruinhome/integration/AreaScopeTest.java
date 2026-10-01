package com.ruinhome.integration;

import com.ruinhome.contract.ContractService;
import com.ruinhome.house.HouseDtos;
import com.ruinhome.house.HouseService;
import com.ruinhome.person.PersonDtos;
import com.ruinhome.person.PersonService;
import com.ruinhome.stats.StatsService;
import com.ruinhome.user.Role;
import com.ruinhome.user.UserAccount;
import com.ruinhome.user.UserAccountRepository;
import com.ruinhome.user.UserDtos;
import com.ruinhome.user.UserService;
import org.junit.jupiter.api.AfterEach;
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
class AreaScopeTest {

    @Autowired
    private HouseService houseService;
    @Autowired
    private PersonService personService;
    @Autowired
    private UserService userService;
    @Autowired
    private ContractService contractService;
    @Autowired
    private StatsService statsService;
    @Autowired
    private UserAccountRepository userAccountRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void as(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private UserAccount createTenant(String username) {
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash("hash");
        account.setRole(Role.ADMIN);
        account.setEnabled(true);
        account.setAreaAdmin(username);
        account.setRoot(false);
        return userAccountRepository.save(account);
    }

    @Test
    void tenantAdminSeesOnlyOwnAreaHouses() {
        as("admin", Role.ADMIN);
        int rootHouseCount = houseService.list().size();
        assertThat(rootHouseCount).isGreaterThan(0);
        Long seedHouseId = houseService.list().get(0).id();
        createTenant("tenanta");

        as("tenanta", Role.ADMIN);
        var owner = personService.create(new PersonDtos.PersonRequest(
                "Chu Nha Tenant A", null, null, null));
        houseService.create(new HouseDtos.HouseRequest(
                "NHA-TENANT-A", "Nha Tro Tenant A", "Dia chi Tenant A", owner.id(), null, null));

        assertThat(houseService.list()).extracting(HouseDtos.HouseResponse::code)
                .containsExactly("NHA-TENANT-A");
        assertThatThrownBy(() -> houseService.get(seedHouseId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        as("admin", Role.ADMIN);
        assertThat(houseService.list()).hasSize(rootHouseCount + 1);
    }

    @Test
    void tenantAdminSeesOnlyOwnAreaPersons() {
        createTenant("tenantb");

        as("admin", Role.ADMIN);
        var rootPerson = personService.create(new PersonDtos.PersonRequest(
                "Nguoi Goc Admin", null, null, null));

        as("tenantb", Role.ADMIN);
        var tenantPerson = personService.create(new PersonDtos.PersonRequest(
                "Nguoi Tenant B", null, null, null));
        assertThat(personService.list(null, 0, 50).getContent())
                .extracting(PersonDtos.PersonResponse::fullName)
                .containsExactly("Nguoi Tenant B");
        assertThatThrownBy(() -> personService.get(rootPerson.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        as("admin", Role.ADMIN);
        assertThat(personService.list(null, 0, 200).getContent())
                .extracting(PersonDtos.PersonResponse::fullName)
                .contains(rootPerson.fullName(), tenantPerson.fullName());
    }

    @Test
    void tenantAdminAccountScope() {
        var tenant = createTenant("tenantc");
        Long rootId = userAccountRepository.findByUsername("admin").orElseThrow().getId();

        as("tenantc", Role.ADMIN);
        assertThat(userService.list()).extracting(UserDtos.UserResponse::username)
                .containsExactly("tenantc");

        assertThatThrownBy(() -> userService.update(rootId,
                new UserDtos.UserUpdateRequest(null, "ADMIN", null, null, null, true)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        var updated = userService.update(tenant.getId(),
                new UserDtos.UserUpdateRequest(null, "ADMIN", null, null, null, true));
        assertThat(updated.role()).isEqualTo(Role.ADMIN);

        as("admin", Role.ADMIN);
        assertThat(userService.list()).extracting(UserDtos.UserResponse::username)
                .contains("admin", "tenantc");
    }

    @Test
    void tenantStatsAndContractSearchScopedToArea() {
        createTenant("tenantd");

        as("admin", Role.ADMIN);
        long rootHouseCount = statsService.stats().houseCount();
        assertThat(rootHouseCount).isGreaterThan(0);
        assertThat(contractService.search(null, null, null, 0, 20).getContent()).isNotEmpty();
        Long seedHouseId = houseService.list().get(0).id();

        as("tenantd", Role.ADMIN);
        var owner = personService.create(new PersonDtos.PersonRequest(
                "Chu Nha Tenant D", null, null, null));
        houseService.create(new HouseDtos.HouseRequest(
                "NHA-TENANT-D", "Nha Tro Tenant D", "Dia chi Tenant D", owner.id(), null, null));

        assertThat(statsService.stats().houseCount()).isEqualTo(1);
        assertThat(contractService.search(null, null, null, 0, 20).getContent()).isEmpty();
        assertThat(contractService.search(seedHouseId, null, null, 0, 20).getContent()).isEmpty();

        as("admin", Role.ADMIN);
        assertThat(statsService.stats().houseCount()).isEqualTo(rootHouseCount + 1);
    }

    @Test
    void managerSeesOnlyTenantAreaPersons() {
        createTenant("tename");

        as("tename", Role.ADMIN);
        personService.create(new PersonDtos.PersonRequest(
                "Nguoi Tenant E", null, null, null));
        UserAccount manager = new UserAccount();
        manager.setUsername("quanlyte");
        manager.setPasswordHash("hash");
        manager.setRole(Role.MANAGER);
        manager.setEnabled(true);
        manager.setAreaAdmin("tename");
        userAccountRepository.save(manager);

        as("quanlyte", Role.MANAGER);
        assertThat(personService.list(null, 0, 50).getContent())
                .extracting(PersonDtos.PersonResponse::fullName)
                .containsExactly("Nguoi Tenant E");
    }
}
