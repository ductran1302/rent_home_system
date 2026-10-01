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

    @Query("select p from Person p where (:areaScope is null or p.areaAdmin = :areaScope)")
    Page<Person> findAllInArea(@Param("areaScope") String areaScope, Pageable pageable);

    @Query("""
            select p from Person p
            where p.active = true
              and (:areaScope is null or p.areaAdmin = :areaScope)
              and (lower(p.fullName) like lower(concat('%', :q, '%'))
                   or p.idNumber like concat('%', :q, '%')
                   or p.phone like concat('%', :q, '%'))
            """)
    Page<Person> searchInArea(@Param("q") String q, @Param("areaScope") String areaScope,
                              Pageable pageable);
}
