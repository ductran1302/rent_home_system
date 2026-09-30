package com.ruinhome.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @Query("select a from UserAccount a left join fetch a.person where a.username = :username")
    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByPersonIdAndEnabledTrue(Long personId);

    @Query("select a from UserAccount a left join fetch a.person order by a.id")
    List<UserAccount> findAllWithPerson();
}
