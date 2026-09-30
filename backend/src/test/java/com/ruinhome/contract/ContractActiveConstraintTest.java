package com.ruinhome.contract;

import com.ruinhome.house.House;
import com.ruinhome.house.HouseRepository;
import com.ruinhome.person.Person;
import com.ruinhome.person.PersonRepository;
import com.ruinhome.room.Room;
import com.ruinhome.room.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ContractActiveConstraintTest {

    @Autowired
    private ContractRepository contractRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private HouseRepository houseRepository;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void onlyOneActiveContractPerRoom() {
        Person owner = newPerson("Nguyen Van Chu");
        Person tenant = newPerson("Tran Thi Thue");
        House house = newHouse(owner);
        Room room = newRoom(house, "T1");

        contractRepository.saveAndFlush(newActiveContract(room, tenant));
        assertThatThrownBy(() -> contractRepository.saveAndFlush(newActiveContract(room, tenant)))
                .isInstanceOfAny(DataIntegrityViolationException.class,
                        org.springframework.orm.jpa.JpaSystemException.class);
    }

    private Person newPerson(String fullName) {
        Person p = new Person();
        p.setFullName(fullName);
        return personRepository.save(p);
    }

    private House newHouse(Person owner) {
        House h = new House();
        h.setCode("N-TEST-" + System.nanoTime());
        h.setName("Nha kiem thu");
        h.setAddress("123 Test");
        h.setOwner(owner);
        h.setManager(owner);
        return houseRepository.save(h);
    }

    private Room newRoom(House house, String roomNumber) {
        Room r = new Room();
        r.setHouse(house);
        r.setRoomNumber(roomNumber);
        return roomRepository.save(r);
    }

    private Contract newActiveContract(Room room, Person holder) {
        Contract c = new Contract();
        c.setRoom(room);
        c.setHolder(holder);
        c.setMonthlyRent(3_000_000L);
        c.setStartDate(LocalDate.now());
        c.setEndDate(LocalDate.now().plusYears(1));
        c.setStatus(ContractStatus.ACTIVE);
        return c;
    }
}
