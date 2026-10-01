package com.ruinhome.person;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.user.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final UserAccountRepository userAccountRepository;
    private final CurrentUserService currentUserService;

    public PersonService(PersonRepository personRepository, UserAccountRepository userAccountRepository,
                         CurrentUserService currentUserService) {
        this.personRepository = personRepository;
        this.userAccountRepository = userAccountRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public Page<PersonDtos.PersonResponse> list(String q, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("fullName"));
        String areaScope = currentUserService.areaOrNull();
        Page<Person> result;
        if (q == null || q.isBlank()) {
            result = personRepository.findAllInArea(areaScope, pageable);
        } else {
            result = personRepository.searchInArea(q.trim(), areaScope, pageable);
        }
        return result.map(PersonDtos::toResponse);
    }

    @Transactional(readOnly = true)
    public PersonDtos.PersonResponse get(Long id) {
        return PersonDtos.toResponse(find(id));
    }

    @Transactional
    public PersonDtos.PersonResponse create(PersonDtos.PersonRequest request) {
        Person person = new Person();
        apply(person, request);
        person.setAreaAdmin(currentUserService.areaForWrite());
        person.setCreatedBy(currentUserService.username());
        return PersonDtos.toResponse(personRepository.save(person));
    }

    @Transactional
    public PersonDtos.PersonResponse update(Long id, PersonDtos.PersonRequest request) {
        Person person = find(id);
        apply(person, request);
        person.setUpdatedBy(currentUserService.username());
        return PersonDtos.toResponse(personRepository.saveAndFlush(person));
    }

    @Transactional
    public void softDelete(Long id) {
        Person person = find(id);
        if (userAccountRepository.existsByPersonIdAndEnabledTrue(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Người này đang có tài khoản hoạt động, vui lòng tắt tài khoản trước khi xoá");
        }
        person.setActive(false);
        person.setUpdatedBy(currentUserService.username());
        personRepository.save(person);
    }

    private Person find(Long id) {
        Person person = personRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người"));
        String areaScope = currentUserService.areaOrNull();
        if (areaScope != null && !areaScope.equals(person.getAreaAdmin())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người");
        }
        return person;
    }

    private void apply(Person person, PersonDtos.PersonRequest request) {
        person.setFullName(request.fullName().trim());
        person.setIdNumber(blankToNull(request.idNumber()));
        person.setPhone(blankToNull(request.phone()));
        person.setAddress(blankToNull(request.address()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
