package com.ruinhome.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @Query("select a from UserAccount a left join fetch a.person where a.username = :username")
    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByPersonIdAndEnabledTrue(Long personId);

    Optional<UserAccount> findFirstByPersonIdAndBankAccountIsNotNullOrderByIdAsc(Long personId);

    @Query("select a from UserAccount a left join fetch a.person order by a.id")
    List<UserAccount> findAllWithPerson();

    List<UserAccount> findByPersonIdInAndEnabledTrue(Collection<Long> personIds);

    @Query("""
            select a from UserAccount a
            where a.enabled = true
              and a.role in (com.ruinhome.user.Role.ADMIN, com.ruinhome.user.Role.MANAGER)
              and (a.root = true or coalesce(a.areaAdmin, a.username) = :area)
            """)
    List<UserAccount> findAreaStaff(@Param("area") String area);

    @Query("""
            select a from UserAccount a
            where a.enabled = true
              and a.role = com.ruinhome.user.Role.MANAGER
              and a.managerEndDate is not null
              and a.managerEndDate >= :from
              and a.managerEndDate <= :to
            """)
    List<UserAccount> findManagersEndingBetween(@Param("from") LocalDate from,
                                                @Param("to") LocalDate to);
}
