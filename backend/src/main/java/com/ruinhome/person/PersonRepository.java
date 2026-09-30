package com.ruinhome.person;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PersonRepository extends JpaRepository<Person, Long> {

    List<Person> findByActiveTrueOrderByFullNameAsc();

    Page<Person> findAllByActiveTrue(Pageable pageable);

    boolean existsByIdNumberAndActiveTrue(String idNumber);

    @Query("""
            select p from Person p
            where p.active = true
              and (lower(p.fullName) like lower(concat('%', :q, '%'))
                   or p.idNumber like concat('%', :q, '%')
                   or p.phone like concat('%', :q, '%'))
            """)
    Page<Person> search(@Param("q") String q, Pageable pageable);
}
