package com.shareloop.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shareloop.catalog.service.CategoryAttributeService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.CreateItemRequest;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.repository.ItemRepository;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.media.service.MediaService;
import com.shareloop.moderation.ModerationResult;
import com.shareloop.moderation.service.ContentModerationService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    ItemRepository itemRepository;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    JdbcClient jdbcClient;

    @Mock
    MediaService mediaService;

    @Mock
    ConfigService configService;

    @Mock
    CategoryAttributeService categoryAttributeService;

    @Mock
    ContentModerationService contentModerationService;

    @Mock
    PostFeeService postFeeService;

    @InjectMocks
    ItemService itemService;

    @BeforeEach
    void allowCleanContent() {
        lenient()
                .when(contentModerationService.checkPost(anyLong(), any(), any()))
                .thenReturn(ModerationResult.pass());
    }

    @Test
    @DisplayName("Tạo bài GIVE thành công ở trạng thái PENDING_REVIEW")
    void createItem_give_success() {
        imageLimits();
        CreateItemRequest request = new CreateItemRequest(
                1L,
                1L,
                OfferType.GIVE,
                "Sách Toán",
                "Sách mới",
                null,
                ItemCondition.NEW,
                null,
                "NXB Trẻ",
                threeImages());

        when(itemRepository.saveAndFlush(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 100L);
            return item;
        });

        ItemResponse response = itemService.createItem(10L, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.donorId()).isEqualTo(10L);
        assertThat(response.offerType()).isEqualTo(OfferType.GIVE);
        assertThat(response.status()).isEqualTo(ItemStatus.PENDING_REVIEW);
        verify(postFeeService).holdPostFee(100L, 10L);
    }

    @Test
    @DisplayName("Tạo bài SWAP thành công khi có desiredItem")
    void createItem_swap_withDesiredItem_success() {
        imageLimits();
        CreateItemRequest request = new CreateItemRequest(
                1L,
                1L,
                OfferType.SWAP,
                "Sách Java",
                "Sách hay",
                "Sách C#",
                ItemCondition.GOOD,
                null,
                null,
                threeImages());

        when(itemRepository.saveAndFlush(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 101L);
            return item;
        });

        ItemResponse response = itemService.createItem(10L, request);

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.desiredItem()).isEqualTo("Sách C#");
        assertThat(response.offerType()).isEqualTo(OfferType.SWAP);
    }

    @Test
    @DisplayName("Tạo bài SWAP ném lỗi khi thiếu desiredItem")
    void createItem_swap_missingDesiredItem_throwsException() {
        CreateItemRequest request = new CreateItemRequest(
                1L, 1L, OfferType.SWAP, "Sách Java", "Sách hay", null, ItemCondition.GOOD, null, null);

        assertThatThrownBy(() -> itemService.createItem(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_SWAP_DESIRED_ITEM_REQUIRED);
    }

    @Test
    @DisplayName("Tạo bài DEFECTIVE ném lỗi khi thiếu defectNote")
    void createItem_defective_missingDefectNote_throwsException() {
        CreateItemRequest request = new CreateItemRequest(
                1L, 1L, OfferType.GIVE, "Tai nghe hỏng", "Chỉ nghe 1 bên", null, ItemCondition.DEFECTIVE, "  ", null);

        assertThatThrownBy(() -> itemService.createItem(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_DEFECT_NOTE_REQUIRED);
    }

    @Test
    @DisplayName("Tạo bài DEFECTIVE thành công khi có defectNote và ảnh lỗi")
    void createItem_defective_withDefectNote_success() {
        imageLimits();
        CreateItemRequest request = new CreateItemRequest(
                1L,
                1L,
                OfferType.GIVE,
                "Bàn phím liệt phím",
                "Cần người rành sửa",
                null,
                ItemCondition.DEFECTIVE,
                "Liệt phím Space",
                "Logitech",
                threeImages(),
                List.of("/api/v1/media/4"),
                null);

        when(itemRepository.saveAndFlush(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 102L);
            return item;
        });

        ItemResponse response = itemService.createItem(10L, request);

        assertThat(response.id()).isEqualTo(102L);
        assertThat(response.defectNote()).isEqualTo("Liệt phím Space");
        assertThat(response.defectImages()).containsExactly("/api/v1/media/4");
    }

    @Test
    @DisplayName("Xem bài APPROVED: Khách vãng lai xem được")
    void getItem_approved_public_returnsItem() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null);
        item.approve();
        ReflectionTestUtils.setField(item, "id", 1L);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        ItemResponse response = itemService.getItem(1L, null);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(ItemStatus.APPROVED);
    }

    @Test
    @DisplayName("Xem bài PENDING_REVIEW: Chủ bài xem được")
    void getItem_pendingReview_owner_returnsItem() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null);
        ReflectionTestUtils.setField(item, "id", 1L);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        ItemResponse response = itemService.getItem(1L, 10L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(ItemStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("Xem bài PENDING_REVIEW: Người khác xem bị báo ITEM_NOT_FOUND (404)")
    void getItem_pendingReview_otherUser_throwsNotFound() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null);
        ReflectionTestUtils.setField(item, "id", 1L);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> itemService.getItem(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    void hiddenApprovedItemIsNotPublic() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null);
        item.approve();
        item.deactivate();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> itemService.getItem(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("Xem bài không tồn tại ném lỗi ITEM_NOT_FOUND")
    void getItem_notFound_throwsNotFound() {
        when(itemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.getItem(999L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("Xem danh sách bài đăng của tôi trả về bài của donorId")
    void listMyItems_returnsDonorItems() {
        Item item1 = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách 1", "Mô tả 1", null, ItemCondition.NEW, null, null);
        ReflectionTestUtils.setField(item1, "id", 1L);

        when(itemRepository.findByDonorIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(item1));

        List<ItemResponse> responses = itemService.listMyItems(10L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).title()).isEqualTo("Sách 1");
        verify(itemRepository).findByDonorIdOrderByCreatedAtDesc(10L);
    }

    @Test
    @DisplayName("Tạo bài có danh sách ảnh thành công")
    void createItem_withImages_success() {
        imageLimits();
        CreateItemRequest request = new CreateItemRequest(
                1L,
                1L,
                OfferType.GIVE,
                "Sách Toán",
                "Sách mới",
                null,
                ItemCondition.NEW,
                null,
                "NXB Trẻ",
                List.of("/api/v1/media/7", "/api/v1/media/8", "/api/v1/media/9"));

        when(itemRepository.saveAndFlush(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 102L);
            return item;
        });

        ItemResponse response = itemService.createItem(10L, request);

        assertThat(response.id()).isEqualTo(102L);
        assertThat(response.images()).containsExactly("/api/v1/media/7", "/api/v1/media/8", "/api/v1/media/9");
        verify(mediaService).attach(List.of(7L, 8L, 9L), 10L);
        verify(jdbcClient, times(3)).sql(startsWith("INSERT INTO item_media_mappings"));
        verify(jdbcClient, never()).sql(startsWith("INSERT INTO media_files"));
    }

    @Test
    void createItem_rejectsExternalImageUrl() {
        imageLimits();
        CreateItemRequest request = new CreateItemRequest(
                1L,
                1L,
                OfferType.GIVE,
                "Book",
                "Description",
                null,
                ItemCondition.NEW,
                null,
                null,
                List.of("/api/v1/media/7", "https://example.com/image.jpg", "/api/v1/media/9"));

        assertThatThrownBy(() -> itemService.createItem(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_INVALID_IMAGE);
        verify(itemRepository, never()).saveAndFlush(any(Item.class));
    }

    @Test
    void createItemRejectsImageCountOutsideThreeToEight() {
        imageLimits();
        for (List<String> images : List.of(
                List.<String>of(),
                List.of("/api/v1/media/1", "/api/v1/media/2"),
                java.util.stream.LongStream.rangeClosed(1, 9)
                        .mapToObj(id -> "/api/v1/media/" + id)
                        .toList())) {
            CreateItemRequest request = new CreateItemRequest(
                    1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null, images);
            assertThatThrownBy(() -> itemService.createItem(10L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_INVALID_IMAGE_COUNT)
                    .hasFieldOrPropertyWithValue("field", "images");
        }
        verify(mediaService, never()).attach(any(), anyLong());
        verify(itemRepository, never()).saveAndFlush(any(Item.class));
    }

    private static List<String> threeImages() {
        return List.of("/api/v1/media/1", "/api/v1/media/2", "/api/v1/media/3");
    }

    private void imageLimits() {
        when(configService.getInt(ConfigKey.ITEM_PHOTOS_MIN)).thenReturn(3);
        when(configService.getInt(ConfigKey.ITEM_PHOTOS_MAX)).thenReturn(8);
    }

    @Test
    @DisplayName("Giữ chỗ bài đăng thành công")
    void reserve_success() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null);
        item.approve();
        ReflectionTestUtils.setField(item, "id", 1L);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        itemService.reserve(1L);

        assertThat(item.getStatus()).isEqualTo(ItemStatus.RESERVED);

        assertThatThrownBy(() -> itemService.reserve(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.RESERVED);
    }

    @Test
    void reserveRejectsUnapprovedAndMissingItems() {
        Item pending = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> itemService.reserve(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);
        assertThat(pending.getStatus()).isEqualTo(ItemStatus.PENDING_REVIEW);

        when(itemRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> itemService.reserve(2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    void releaseOnlyReservedItem() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null);
        item.approve();
        item.reserve();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        itemService.release(1L);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.APPROVED);
        assertThatThrownBy(() -> itemService.release(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.APPROVED);

        when(itemRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> itemService.release(2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    void markTradedSetsStatusToTraded() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null);
        item.approve();
        item.reserve();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        itemService.markTraded(1L);

        assertThat(item.getStatus()).isEqualTo(ItemStatus.TRADED);
    }

    @Test
    void markApprovedTransitionsOnlyPendingReview() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        itemService.markApproved(1L);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.APPROVED);

        assertThatThrownBy(() -> itemService.markApproved(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);
    }

    @Test
    void markRejectedTransitionsOnlyPendingReview() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        itemService.markRejected(1L);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.REJECTED);

        assertThatThrownBy(() -> itemService.markRejected(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);
    }

    @Test
    void markApprovedRejectsMissingItem() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> itemService.markApproved(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    void temporaryGatewaysFailBeforeChangingAnItem() {
        assertThatThrownBy(() -> itemService.hide(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("hide");
        assertThatThrownBy(() -> itemService.unhide(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("unhide");
        assertThatThrownBy(() -> itemService.remove(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("remove");
        assertThatThrownBy(() -> itemService.hideAllOfUser(10L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("hideAllOfUser");
        verifyNoInteractions(itemRepository);
    }
}
