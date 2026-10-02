package com.ruinhome.contract;

import com.ruinhome.asset.AssetCategory;
import com.ruinhome.asset.AssetCondition;
import com.ruinhome.asset.AssetDtos;
import com.ruinhome.asset.AssetRepairStatus;
import com.ruinhome.asset.AssetService;
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
class ContractAssetTest {

    private static final LocalDate CONTRACT_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate CONTRACT_END = LocalDate.of(2027, 9, 30);

    @Autowired
    private ContractService contractService;
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

    private Long createOwner() {
        return personService.create(
                new PersonDtos.PersonRequest("Chu Nha CA", null, null, null)).id();
    }

    private Long createRoom(String houseCode, String roomNumber, Long ownerId) {
        var house = houseService.create(new HouseDtos.HouseRequest(
                houseCode, "Nha " + houseCode, "Dia chi " + houseCode, ownerId, null, null));
        return roomService.create(new RoomDtos.RoomRequest(house.id(), roomNumber, null, null)).id();
    }

    private AssetDtos.AssetResponse createAsset(Long roomId, String code, AssetCondition condition) {
        return assetService.create(new AssetDtos.AssetRequest(
                roomId, code, "Tai san " + code, AssetCategory.GIUONG, 4_500_000L,
                LocalDate.of(2025, 1, 10), condition, null));
    }

    private ContractDtos.ContractResponse createContract(Long roomId, Long holderId, List<Long> assetIds) {
        return contractService.create(new ContractDtos.ContractCreateRequest(
                roomId, holderId, 3_500_000L, CONTRACT_START, CONTRACT_END, null, assetIds, null, null));
    }

    @Test
    void handoverOnCreateIsListed() {
        asRoot();
        Long owner = createOwner();
        Long roomId = createRoom("NHA-CA-1", "A201", owner);
        var asset = createAsset(roomId, "TS-CA-A201", AssetCondition.USED);

        var contract = createContract(roomId, owner, List.of(asset.id()));
        var list = contractService.listContractAssets(contract.id());

        assertThat(list.items()).hasSize(1);
        var item = list.items().get(0);
        assertThat(item.code()).isEqualTo("TS-CA-A201");
        assertThat(item.condition()).isEqualTo(AssetCondition.USED);
        assertThat(item.handoverCondition()).isEqualTo(AssetCondition.USED);
        assertThat(item.returnCondition()).isNull();
        assertThat(item.returnedAt()).isNull();
        assertThat(list.summary().total()).isEqualTo(1);
        assertThat(list.summary().brokenCount()).isZero();
    }

    @Test
    void assetFromAnotherRoomRejected() {
        asRoot();
        Long owner = createOwner();
        Long roomA = createRoom("NHA-CA-2", "A202", owner);
        Long roomB = createRoom("NHA-CA-3", "B202", owner);
        var asset = createAsset(roomB, "TS-CA-B202", AssetCondition.GOOD);

        assertThatThrownBy(() -> createContract(roomA, owner, List.of(asset.id())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("thuộc phòng của hợp đồng");
                });
    }

    @Test
    void updateContractReplacesHandover() {
        asRoot();
        Long owner = createOwner();
        Long roomId = createRoom("NHA-CA-4", "A204", owner);
        var first = createAsset(roomId, "TS-CA-A204-1", AssetCondition.GOOD);
        var second = createAsset(roomId, "TS-CA-A204-2", AssetCondition.GOOD);
        var contract = createContract(roomId, owner, List.of(first.id()));

        contractService.update(contract.id(), new ContractDtos.ContractUpdateRequest(
                contract.monthlyRent(), CONTRACT_END, null, List.of(second.id()), null, null));

        var list = contractService.listContractAssets(contract.id());
        assertThat(list.items()).extracting(ContractDtos.ContractAssetItemResponse::code)
                .containsExactly("TS-CA-A204-2");
    }

    @Test
    void returnAssetMarksReturnedAndUpdatesCondition() {
        asRoot();
        Long owner = createOwner();
        Long roomId = createRoom("NHA-CA-5", "A205", owner);
        var asset = createAsset(roomId, "TS-CA-A205", AssetCondition.GOOD);
        var contract = createContract(roomId, owner, List.of(asset.id()));

        var item = contractService.returnAsset(contract.id(), asset.id(),
                new ContractDtos.ContractAssetReturnRequest(AssetCondition.BROKEN,
                        LocalDate.of(2026, 10, 10)));

        assertThat(item.returnCondition()).isEqualTo(AssetCondition.BROKEN);
        assertThat(item.returnedAt()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(assetService.get(asset.id()).condition()).isEqualTo(AssetCondition.BROKEN);
        assertThat(contractService.listContractAssets(contract.id()).summary().brokenCount()).isEqualTo(1);
    }

    @Test
    void softDeleteBlockedWhileHandedOver() {
        asRoot();
        Long owner = createOwner();
        Long roomId = createRoom("NHA-CA-6", "A206", owner);
        var asset = createAsset(roomId, "TS-CA-A206", AssetCondition.GOOD);
        createContract(roomId, owner, List.of(asset.id()));

        assertThatThrownBy(() -> assetService.softDelete(asset.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("đang được giao trong hợp đồng");
                });
    }

    @Test
    void softDeleteAllowedAfterContractTerminated() {
        asRoot();
        Long owner = createOwner();
        Long roomId = createRoom("NHA-CA-7", "A207", owner);
        var asset = createAsset(roomId, "TS-CA-A207", AssetCondition.GOOD);
        var contract = createContract(roomId, owner, List.of(asset.id()));

        contractService.terminate(contract.id());

        assetService.softDelete(asset.id());
        assertThatThrownBy(() -> assetService.get(asset.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void repairCostCountsOnlyInsideContractPeriod() {
        asRoot();
        Long owner = createOwner();
        Long roomId = createRoom("NHA-CA-8", "A208", owner);
        var asset = createAsset(roomId, "TS-CA-A208", AssetCondition.GOOD);
        var contract = createContract(roomId, owner, List.of(asset.id()));
        assetService.createRepair(asset.id(), new AssetDtos.AssetRepairRequest(
                LocalDate.of(2026, 10, 5), "Ve sinh", 300_000L, AssetRepairStatus.DONE, null, null));
        assetService.createRepair(asset.id(), new AssetDtos.AssetRepairRequest(
                LocalDate.of(2028, 1, 5), "Thay ban", 500_000L, AssetRepairStatus.DONE, null, null));

        var list = contractService.listContractAssets(contract.id());

        assertThat(list.items().get(0).repairCount()).isEqualTo(2);
        assertThat(list.items().get(0).repairCost()).isEqualTo(800_000L);
        assertThat(list.summary().repairCost()).isEqualTo(300_000L);
    }

    @Test
    void returnAssetOfAnotherContractIsNotFound() {
        asRoot();
        Long owner = createOwner();
        Long roomId = createRoom("NHA-CA-9", "A209", owner);
        var asset = createAsset(roomId, "TS-CA-A209", AssetCondition.GOOD);
        var contract = createContract(roomId, owner, List.of(asset.id()));

        assertThatThrownBy(() -> contractService.returnAsset(contract.id(), asset.id() + 999_000,
                new ContractDtos.ContractAssetReturnRequest(AssetCondition.GOOD, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
