package com.shareloop.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.CreateItemRequest;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.repository.ItemRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    ItemRepository itemRepository;

    @InjectMocks
    ItemService itemService;

    @Test
    @DisplayName("Tạo bài GIVE thành công ở trạng thái PENDING_REVIEW")
    void createItem_give_success() {
        CreateItemRequest request = new CreateItemRequest(
                1L, 1L, OfferType.GIVE, "Sách Toán", "Sách mới", null, ItemCondition.NEW, null, "NXB Trẻ");

        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 100L);
            return item;
        });

        ItemResponse response = itemService.createItem(10L, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.donorId()).isEqualTo(10L);
        assertThat(response.offerType()).isEqualTo(OfferType.GIVE);
        assertThat(response.status()).isEqualTo(ItemStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("Tạo bài SWAP thành công khi có desiredItem")
    void createItem_swap_withDesiredItem_success() {
        CreateItemRequest request = new CreateItemRequest(
                1L, 1L, OfferType.SWAP, "Sách Java", "Sách hay", "Sách C#", ItemCondition.GOOD, null, null);

        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
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
    @DisplayName("Tạo bài DEFECTIVE thành công khi có defectNote")
    void createItem_defective_withDefectNote_success() {
        CreateItemRequest request = new CreateItemRequest(
                1L,
                1L,
                OfferType.GIVE,
                "Bàn phím liệt phím",
                "Cần người rành sửa",
                null,
                ItemCondition.DEFECTIVE,
                "Liệt phím Space",
                "Logitech");

        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 102L);
            return item;
        });

        ItemResponse response = itemService.createItem(10L, request);

        assertThat(response.id()).isEqualTo(102L);
        assertThat(response.defectNote()).isEqualTo("Liệt phím Space");
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
}
