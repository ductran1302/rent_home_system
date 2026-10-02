package com.ruinhome.asset;

import com.ruinhome.house.HouseDtos;
import com.ruinhome.house.HouseService;
import com.ruinhome.person.PersonDtos;
import com.ruinhome.person.PersonService;
import com.ruinhome.room.RoomDtos;
import com.ruinhome.room.RoomService;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AssetTest {

    @Autowired
    private AssetService assetService;
    @Autowired
    private HouseService houseService;
    @Autowired
    private PersonService personService;
    @Autowired
    private RoomService roomService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void asRoot() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private Long createRoom(String houseCode, String roomNumber) {
        var owner = personService.create(new PersonDtos.PersonRequest("Chu Nha " + houseCode, null, null, null));
        var house = houseService.create(new HouseDtos.HouseRequest(
                houseCode, "Nha " + houseCode, "Dia chi " + houseCode, owner.id(), null, null));
        return roomService.create(new RoomDtos.RoomRequest(house.id(), roomNumber, null, null)).id();
    }

    private AssetDtos.AssetResponse createAsset(Long roomId, String code, String name,
                                                AssetCategory category, AssetCondition condition) {
        return assetService.create(new AssetDtos.AssetRequest(
                roomId, code, name, category, 4_500_000L, LocalDate.of(2025, 1, 10), condition, null));
    }

    private AssetDtos.AssetRepairRequest repairRequest(AssetRepairStatus status) {
        return new AssetDtos.AssetRepairRequest(LocalDate.of(2026, 9, 3), "Thay phu tung",
                300_000L, status, null, null);
    }

    @Test
    void createAndGetAsset() {
        asRoot();
        Long roomId = createRoom("NHA-AS-1", "A101");

        var created = createAsset(roomId, "TS-AS-A101", "Giuong doi 1m6", AssetCategory.GIUONG,
                AssetCondition.GOOD);

        assertThat(created.id()).isNotNull();
        assertThat(created.roomId()).isEqualTo(roomId);
        assertThat(created.roomNumber()).isEqualTo("A101");
        assertThat(created.repairCount()).isZero();
        assertThat(created.repairCost()).isZero();

        var fetched = assetService.get(created.id());
        assertThat(fetched.name()).isEqualTo("Giuong doi 1m6");
        assertThat(fetched.category()).isEqualTo(AssetCategory.GIUONG);
        assertThat(fetched.price()).isEqualTo(4_500_000L);
    }

    @Test
    void duplicateAssetCodeRejected() {
        asRoot();
        Long roomId = createRoom("NHA-AS-2", "A102");
        createAsset(roomId, "TS-AS-A102", "Tu lanh 90 lit", AssetCategory.TU_LANH, AssetCondition.USED);

        assertThatThrownBy(() -> createAsset(roomId, "TS-AS-A102", "Tu lanh khac",
                AssetCategory.TU_LANH, AssetCondition.GOOD))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void searchFiltersByRoomAndCategory() {
        asRoot();
        Long roomA = createRoom("NHA-AS-3", "A103");
        Long roomB = createRoom("NHA-AS-4", "B103");
        createAsset(roomA, "TS-AS-A103", "Dieu hoa 1.5HP", AssetCategory.DIEU_HOA, AssetCondition.GOOD);
        createAsset(roomB, "TS-AS-B103", "Tu quan ao", AssetCategory.TU, AssetCondition.USED);

        var byRoom = assetService.search(null, roomA, null, null, null, 0, 20);
        assertThat(byRoom.getContent()).extracting(AssetDtos.AssetResponse::code)
                .containsExactly("TS-AS-A103");

        var byCategory = assetService.search(null, roomB, null, AssetCategory.TU, "tu", 0, 20);
        assertThat(byCategory.getContent()).extracting(AssetDtos.AssetResponse::code)
                .containsExactly("TS-AS-B103");
    }

    @Test
    void markingRepairDoneRelaxesBrokenCondition() {
        asRoot();
        Long roomId = createRoom("NHA-AS-5", "A105");
        var asset = createAsset(roomId, "TS-A105-01", "Binh nong lanh", AssetCategory.BINH_NONG_LANH,
                AssetCondition.BROKEN);

        var repair = assetService.createRepair(asset.id(), repairRequest(AssetRepairStatus.DONE));

        assertThat(repair.status()).isEqualTo(AssetRepairStatus.DONE);
        assertThat(repair.doneAt()).isEqualTo(LocalDate.now());
        assertThat(assetService.get(asset.id()).condition()).isEqualTo(AssetCondition.USED);
    }

    @Test
    void pendingRepairKeepsCondition() {
        asRoot();
        Long roomId = createRoom("NHA-AS-6", "A106");
        var asset = createAsset(roomId, "TS-A106-01", "May lanh 1HP", AssetCategory.DIEU_HOA,
                AssetCondition.NEEDS_REPAIR);

        var repair = assetService.createRepair(asset.id(), repairRequest(AssetRepairStatus.PENDING));

        assertThat(repair.doneAt()).isNull();
        assertThat(assetService.get(asset.id()).condition()).isEqualTo(AssetCondition.NEEDS_REPAIR);
    }

    @Test
    void assetShowsRepairCountAndCost() {
        asRoot();
        Long roomId = createRoom("NHA-AS-7", "A107");
        var asset = createAsset(roomId, "TS-A107-01", "May lanh 2HP", AssetCategory.DIEU_HOA,
                AssetCondition.BROKEN);
        assetService.createRepair(asset.id(), new AssetDtos.AssetRepairRequest(
                LocalDate.of(2026, 9, 3), "Ve sinh may lanh", 300_000L, AssetRepairStatus.DONE, null, null));
        assetService.createRepair(asset.id(), new AssetDtos.AssetRepairRequest(
                LocalDate.of(2026, 9, 20), "Thay block", 1_800_000L, AssetRepairStatus.PENDING, null, null));

        var fetched = assetService.get(asset.id());
        assertThat(fetched.repairCount()).isEqualTo(2);
        assertThat(fetched.repairCost()).isEqualTo(2_100_000L);

        var repairs = assetService.listRepairs(asset.id());
        assertThat(repairs).hasSize(2);
        assertThat(repairs).extracting(AssetDtos.AssetRepairResponse::assetCode)
                .containsOnly("TS-A107-01");
    }

    @Test
    void searchRepairsFiltersByStatus() {
        asRoot();
        Long roomId = createRoom("NHA-AS-8", "A108");
        var asset = createAsset(roomId, "TS-A108-01", "Tu lanh", AssetCategory.TU_LANH, AssetCondition.GOOD);
        assetService.createRepair(asset.id(), repairRequest(AssetRepairStatus.PENDING));
        assetService.createRepair(asset.id(), repairRequest(AssetRepairStatus.DONE));

        var pending = assetService.searchRepairs(null, roomId, AssetRepairStatus.PENDING, null, 0, 20);
        assertThat(pending.getTotalElements()).isEqualTo(1);

        var all = assetService.searchRepairs(null, roomId, null, "phu tung", 0, 20);
        assertThat(all.getTotalElements()).isEqualTo(2);
    }

    @Test
    void repairOfAnotherAssetIsNotFound() {
        asRoot();
        Long roomId = createRoom("NHA-AS-9", "A109");
        var first = createAsset(roomId, "TS-A109-01", "Giuong", AssetCategory.GIUONG, AssetCondition.GOOD);
        var second = createAsset(roomId, "TS-A109-02", "Ban hoc", AssetCategory.BAN_GHE, AssetCondition.GOOD);
        var repair = assetService.createRepair(first.id(), repairRequest(AssetRepairStatus.PENDING));

        assertThatThrownBy(() -> assetService.updateRepair(second.id(), repair.id(),
                repairRequest(AssetRepairStatus.DONE)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void softDeleteHidesAsset() {
        asRoot();
        Long roomId = createRoom("NHA-AS-10", "A110");
        var asset = createAsset(roomId, "TS-A110-01", "TV 43 inch", AssetCategory.TV, AssetCondition.GOOD);

        assetService.softDelete(asset.id());

        assertThatThrownBy(() -> assetService.get(asset.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(assetService.search(null, roomId, null, null, null, 0, 20).getContent()).isEmpty();
    }

    @Test
    void cannotMoveAssetToAnotherHouse() {
        asRoot();
        Long roomA = createRoom("NHA-AS-11", "A111");
        Long roomB = createRoom("NHA-AS-12", "B111");
        var asset = createAsset(roomA, "TS-A111-01", "Giuong doi", AssetCategory.GIUONG, AssetCondition.GOOD);

        assertThatThrownBy(() -> assetService.update(asset.id(), new AssetDtos.AssetRequest(
                roomB, "TS-A111-01", "Giuong doi", AssetCategory.GIUONG, 4_500_000L,
                LocalDate.of(2025, 1, 10), AssetCondition.GOOD, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Không thể chuyển tài sản sang nhà khác");
                });
    }
}
