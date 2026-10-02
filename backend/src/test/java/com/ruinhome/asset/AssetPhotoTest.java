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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "upload-dir=${java.io.tmpdir}/ruinhome-asset-photo-test")
@Transactional
class AssetPhotoTest {

    @Autowired
    private AssetService assetService;
    @Autowired
    private AssetPhotoService assetPhotoService;
    @Autowired
    private HouseService houseService;
    @Autowired
    private PersonService personService;
    @Autowired
    private RoomService roomService;

    @AfterEach
    void tearDown() throws IOException {
        SecurityContextHolder.clearContext();
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "ruinhome-asset-photo-test");
        if (Files.exists(dir)) {
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    private void asUser(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
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

    private AssetDtos.AssetResponse createAsset(Long roomId, String code) {
        return assetService.create(new AssetDtos.AssetRequest(
                roomId, code, "Giuong doi", AssetCategory.GIUONG, 4_500_000L,
                LocalDate.of(2025, 1, 10), AssetCondition.GOOD, null));
    }

    private MockMultipartFile jpeg(String name) {
        return new MockMultipartFile("file", name, "image/jpeg", new byte[] {1, 2, 3, 4});
    }

    private MockMultipartFile byType(String contentType) {
        return new MockMultipartFile("file", "tep", contentType, new byte[] {1, 2, 3, 4});
    }

    @Test
    void uploadReadAndDeleteAssetPhoto() {
        asRoot();
        Long assetId = createAsset(createRoom("NHA-AP-1", "A101"), "TS-AP-A101").id();

        var uploaded = assetPhotoService.uploadAssetPhoto(assetId, jpeg("truoc.jpg"));

        assertThat(uploaded.id()).isNotNull();
        assertThat(uploaded.stage()).isNull();
        assertThat(uploaded.originalName()).isEqualTo("truoc.jpg");
        assertThat(uploaded.contentUrl()).isEqualTo("/api/assets/" + assetId + "/photos/"
                + uploaded.id() + "/content");
        assertThat(assetPhotoService.listAssetPhotos(assetId)).hasSize(1);

        var content = assetPhotoService.findAssetContent(assetId, uploaded.id());
        assertThat(content.contentType()).isEqualTo("image/jpeg");
        assertThat(content.bytes()).containsExactly(1, 2, 3, 4);

        assetPhotoService.deleteAssetPhoto(assetId, uploaded.id());
        assertThat(assetPhotoService.listAssetPhotos(assetId)).isEmpty();
        assertThatThrownBy(() -> assetPhotoService.findAssetContent(assetId, uploaded.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void assetPhotoLimitIsFive() {
        asRoot();
        Long assetId = createAsset(createRoom("NHA-AP-2", "A102"), "TS-AP-A102").id();

        var uploaded = new java.util.ArrayList<AssetPhotoService.AssetPhotoResponse>();
        for (int i = 0; i < AssetPhotoService.MAX_PHOTOS; i++) {
            uploaded.add(assetPhotoService.uploadAssetPhoto(assetId, jpeg("anh-" + i + ".jpg")));
        }

        assertThatThrownBy(() -> assetPhotoService.uploadAssetPhoto(assetId, jpeg("anh-6.jpg")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Tối đa 5 ảnh");
                });

        uploaded.forEach(photo -> assetPhotoService.deleteAssetPhoto(assetId, photo.id()));
        assertThat(assetPhotoService.listAssetPhotos(assetId)).isEmpty();
    }

    @Test
    void rejectsEmptyAndWrongTypeFiles() {
        asRoot();
        Long assetId = createAsset(createRoom("NHA-AP-3", "A103"), "TS-AP-A103").id();

        assertThatThrownBy(() -> assetPhotoService.uploadAssetPhoto(assetId,
                new MockMultipartFile("file", "rong.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("Vui lòng chọn tệp ảnh"));

        assertThatThrownBy(() -> assetPhotoService.uploadAssetPhoto(assetId, byType("application/pdf")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("Chỉ chấp nhận ảnh JPG, PNG hoặc WebP"));
    }

    @Test
    void repairPhotoTracksStageAndCleansUpWithRepair() {
        asRoot();
        Long assetId = createAsset(createRoom("NHA-AP-4", "A104"), "TS-AP-A104").id();
        var repair = assetService.createRepair(assetId, new AssetDtos.AssetRepairRequest(
                LocalDate.of(2026, 9, 3), "Thay phu tung", 300_000L, AssetRepairStatus.PENDING, null, null));

        var before = assetPhotoService.uploadRepairPhoto(assetId, repair.id(), AssetPhotoStage.TRUOC,
                jpeg("truoc.jpg"));
        var after = assetPhotoService.uploadRepairPhoto(assetId, repair.id(), AssetPhotoStage.SAU,
                jpeg("sau.jpg"));

        assertThat(before.stage()).isEqualTo(AssetPhotoStage.TRUOC);
        assertThat(after.stage()).isEqualTo(AssetPhotoStage.SAU);
        assertThat(before.contentUrl()).contains("/repairs/" + repair.id() + "/photos/");
        assertThat(assetPhotoService.listRepairPhotos(assetId, repair.id())).hasSize(2);
        assertThat(assetPhotoService.listAssetPhotos(assetId)).isEmpty();

        assetService.deleteRepair(assetId, repair.id());
        assertThatThrownBy(() -> assetPhotoService.listRepairPhotos(assetId, repair.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void repairPhotoRequiresStage() {
        asRoot();
        Long assetId = createAsset(createRoom("NHA-AP-5", "A105"), "TS-AP-A105").id();
        var repair = assetService.createRepair(assetId, new AssetDtos.AssetRepairRequest(
                LocalDate.of(2026, 9, 3), "Ve sinh may lanh", 300_000L, AssetRepairStatus.DONE, null, null));

        assertThatThrownBy(() -> assetPhotoService.uploadRepairPhoto(assetId, repair.id(), null, jpeg("a.jpg")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("Vui lòng chọn ảnh trước hoặc ảnh sau"));
    }

    @Test
    void repairOfAnotherAssetIsNotFound() {
        asRoot();
        Long roomA = createRoom("NHA-AP-6", "A106");
        Long roomB = createRoom("NHA-AP-6B", "B106");
        var first = createAsset(roomA, "TS-AP-A106");
        var second = createAsset(roomB, "TS-AP-B106");
        var repair = assetService.createRepair(first.id(), new AssetDtos.AssetRepairRequest(
                LocalDate.of(2026, 9, 3), "Thay block", 1_800_000L, AssetRepairStatus.PENDING, null, null));

        assertThatThrownBy(() -> assetPhotoService.uploadRepairPhoto(second.id(), repair.id(),
                AssetPhotoStage.TRUOC, jpeg("a.jpg")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Không tìm thấy lịch sử sửa chữa");
                });
    }

    @Test
    void photoOfAnotherAssetIsNotFound() {
        asRoot();
        Long roomA = createRoom("NHA-AP-7", "A107");
        Long roomB = createRoom("NHA-AP-7B", "B107");
        var first = createAsset(roomA, "TS-AP-A107");
        var second = createAsset(roomB, "TS-AP-B107");
        var photo = assetPhotoService.uploadAssetPhoto(first.id(), jpeg("truoc.jpg"));

        assertThatThrownBy(() -> assetPhotoService.findAssetContent(second.id(), photo.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Không tìm thấy ảnh");
                });

        assetPhotoService.deleteAssetPhoto(first.id(), photo.id());
    }

    @Test
    void softDeleteAssetRemovesItsPhotos() {
        asRoot();
        Long assetId = createAsset(createRoom("NHA-AP-8", "A108"), "TS-AP-A108").id();
        assetPhotoService.uploadAssetPhoto(assetId, jpeg("truoc.jpg"));

        assetService.softDelete(assetId);

        assertThatThrownBy(() -> assetPhotoService.listAssetPhotos(assetId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void userWithoutHouseScopeIsForbidden() {
        asRoot();
        Long assetId = createAsset(createRoom("NHA-AP-9", "A109"), "TS-AP-A109").id();
        asUser("nguoidung");

        assertThatThrownBy(() -> assetPhotoService.uploadAssetPhoto(assetId, jpeg("truoc.jpg")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getMessage()).contains("Bạn không có quyền với nhà này");
                });
    }
}
