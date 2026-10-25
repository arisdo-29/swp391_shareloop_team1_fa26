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
import com.shareloop.item.dto.UpdateItemRequest;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.repository.ItemRepository;
import com.shareloop.listingfee.dto.DisplayWindow;
import com.shareloop.listingfee.service.ListingFeeQueryService;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.media.service.MediaService;
import com.shareloop.moderation.ModerationResult;
import com.shareloop.moderation.event.ItemScreeningRequestedEvent;
import com.shareloop.moderation.service.ContentModerationService;
import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.service.RequestQueryService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    @Mock
    ListingFeeQueryService listingFeeQueryService;

    @Mock
    RequestQueryService requestQueryService;

    @Mock
    ReputationService reputationService;

    @Mock
    UserService userService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ItemService itemService;

    @BeforeEach
    void allowCleanContent() {
        lenient()
                .when(contentModerationService.checkPost(anyLong(), any(), any()))
                .thenReturn(ModerationResult.pass());
        lenient().when(reputationService.getRank(10L)).thenReturn(Rank.BRONZE);
        lenient().when(configService.getInt(ConfigKey.MAX_ACTIVE_ITEMS_BRONZE)).thenReturn(3);
        // Mock mặc định trả false nên phải cho phép đăng bài, riêng test 0 sao sẽ ghi đè
        lenient().when(reputationService.canPost(anyLong())).thenReturn(true);
        lenient().when(userService.findBriefs(any())).thenReturn(Map.of());
    }

    @Test
    void createItem_whenTrustStarsZero_throwsReputationBlockedAndHoldsNoFee() {
        // given
        when(reputationService.canPost(10L)).thenReturn(false);
        CreateItemRequest request = new CreateItemRequest(
                1L, 1L, OfferType.GIVE, "Sách Toán", "Sách mới", null, ItemCondition.NEW, null, null, threeImages());

        // when
        assertThatThrownBy(() -> itemService.createItem(10L, request))
                // then
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_REPUTATION_BLOCKED);
        verify(itemRepository, never()).saveAndFlush(any(Item.class));
        verifyNoInteractions(postFeeService, contentModerationService, mediaService);
    }

    @Test
    void createItem_whenTrustStarsRemain_createsItemAndHoldsFee() {
        // given
        imageLimits();
        when(reputationService.canPost(10L)).thenReturn(true);
        when(itemRepository.saveAndFlush(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 100L);
            return item;
        });
        CreateItemRequest request = new CreateItemRequest(
                1L, 1L, OfferType.GIVE, "Sách Toán", "Sách mới", null, ItemCondition.NEW, null, null, threeImages());

        // when
        ItemResponse response = itemService.createItem(10L, request);

        // then
        assertThat(response.id()).isEqualTo(100L);
        verify(postFeeService).holdPostFee(100L, 10L);
    }

    @Test
    void updateItem_whenTrustStarsZero_throwsReputationBlockedAndHoldsNoFee() {
        // given
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null);
        ReflectionTestUtils.setField(item, "id", 5L);
        when(itemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(reputationService.canPost(10L)).thenReturn(false);
        UpdateItemRequest request = new UpdateItemRequest(
                1L, 1L, OfferType.GIVE, "Sách mới", "Mô tả mới", null, ItemCondition.NEW, null, null, null, null, null);

        // when
        assertThatThrownBy(() -> itemService.updateItem(5L, 10L, request))
                // then
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_REPUTATION_BLOCKED);
        verifyNoInteractions(postFeeService);
    }

    @Test
    void createItemAtBronzeLimitReturnsConfiguredLimit() {
        when(itemRepository.countByDonorIdAndStatusInAndIsActiveTrue(
                        10L, List.of(ItemStatus.PENDING_REVIEW, ItemStatus.APPROVED)))
                .thenReturn(3L);
        CreateItemRequest request = new CreateItemRequest(
                1L, 1L, OfferType.GIVE, "Sách Toán", "Sách mới", null, ItemCondition.NEW, null, null, threeImages());

        assertThatThrownBy(() -> itemService.createItem(10L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ItemErrorCode.ITEM_ACTIVE_LIMIT_REACHED);
                    assertThat(businessException.getMessage()).contains("3");
                    assertThat(businessException.getDetails()).containsEntry("limit", 3);
                });
        verify(itemRepository, never()).saveAndFlush(any(Item.class));
        verifyNoInteractions(postFeeService);
    }

    @Test
    void createItemUsesSilverConfiguredLimit() {
        when(reputationService.getRank(10L)).thenReturn(Rank.SILVER);
        when(configService.getInt(ConfigKey.MAX_ACTIVE_ITEMS_SILVER)).thenReturn(7);
        when(itemRepository.countByDonorIdAndStatusInAndIsActiveTrue(
                        10L, List.of(ItemStatus.PENDING_REVIEW, ItemStatus.APPROVED)))
                .thenReturn(7L);
        CreateItemRequest request = new CreateItemRequest(
                1L,
                1L,
                OfferType.SWAP,
                "Sách Toán",
                "Sách mới",
                "Sách Văn",
                ItemCondition.NEW,
                null,
                null,
                threeImages());

        assertThatThrownBy(() -> itemService.createItem(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("7");
        verify(configService).getInt(ConfigKey.MAX_ACTIVE_ITEMS_SILVER);
    }

    @Test
    void diamondRankSkipsItemCount() {
        when(reputationService.getRank(10L)).thenReturn(Rank.DIAMOND);
        when(configService.getInt(ConfigKey.MAX_ACTIVE_ITEMS_DIAMOND)).thenReturn(-1);
        imageLimits();
        CreateItemRequest request = new CreateItemRequest(
                1L, 1L, OfferType.GIVE, "Sách Toán", "Sách mới", null, ItemCondition.NEW, null, null, threeImages());
        when(itemRepository.saveAndFlush(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 100L);
            return item;
        });

        itemService.createItem(10L, request);

        verify(itemRepository, never()).countByDonorIdAndStatusInAndIsActiveTrue(anyLong(), any());
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
        verify(eventPublisher)
                .publishEvent(
                        new ItemScreeningRequestedEvent(100L, 10L, 1L, "GIVE", "Sách Toán", "Sách mới", null, "NEW"));
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
        assertThat(response.donorName()).isNull();
        assertThat(response.donorTrustStars()).isNull();
    }

    @Test
    void getItem_includesDonorNameAndReputationOnlyOnDetail() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách", "Mô tả", null, ItemCondition.NEW, null, null);
        item.approve();
        ReflectionTestUtils.setField(item, "id", 1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(userService.findBriefs(any()))
                .thenReturn(Map.of(10L, new UserBrief(10L, "Nguyễn An", "hidden@example.test")));
        when(reputationService.getReputationSummary(10L)).thenReturn(new ReputationSummary(10L, 4, Rank.GOLD, 0.9));

        ItemResponse response = itemService.getItem(1L, null);

        assertThat(response.donorName()).isEqualTo("Nguyễn An");
        assertThat(response.donorTrustStars()).isEqualTo(4);
        assertThat(response.donorRank()).isEqualTo(Rank.GOLD);
        assertThat(response.donorAsDescribedRate()).isEqualTo(0.9);
        assertThat(response.toString()).doesNotContain("hidden@example.test");
    }

    @Test
    void listPublicItemsPromotesOnlyConfiguredBoostCountAndKeepsFilteredTotal() {
        Item boostedFirst = itemWithId(1L, "Boosted first");
        Item boostedSecond = itemWithId(2L, "Boosted second");
        Item unboosted = itemWithId(4L, "Newest regular");
        when(configService.getInt(ConfigKey.BOOST_MAX_PER_PAGE)).thenReturn(2);
        when(listingFeeQueryService.findCurrentlyBoostedItemIds(any())).thenReturn(List.of(1L, 2L, 3L));
        when(itemRepository.findBoostedVisibleByFilters(
                        ItemStatus.APPROVED,
                        null,
                        null,
                        null,
                        null,
                        List.of(1L, 2L, 3L),
                        PageRequest.of(
                                0,
                                2,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Order.desc("createdAt"),
                                        org.springframework.data.domain.Sort.Order.desc("id")))))
                .thenReturn(new PageImpl<>(List.of(boostedFirst, boostedSecond), PageRequest.of(0, 2), 3));
        when(itemRepository.findUnboostedVisibleByFilters(
                        org.mockito.ArgumentMatchers.eq(ItemStatus.APPROVED),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.eq(List.of(1L, 2L, 3L)),
                        any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(6);
                    return new PageImpl<>(List.of(unboosted), pageable, 1);
                });
        when(listingFeeQueryService.findDisplayWindows(List.of(1L, 2L, 4L))).thenReturn(Map.of());

        var response = itemService.listPublicItems(null, null, null, null, null, 0, 3);

        assertThat(response.items())
                .extracting(ItemResponse::title)
                .containsExactly("Boosted first", "Boosted second", "Newest regular");
        assertThat(response.totalElements()).isEqualTo(4);
    }

    private Item itemWithId(long id, String title) {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, title, "Mô tả", null, ItemCondition.NEW, null, null);
        item.approve();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
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
    void listMyItems_withManyItems_readsDisplayWindowsOnceForAllItems() {
        // given: ba bài, chỉ bài 1 đã duyệt và đã đẩy
        Item item1 = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách 1", "Mô tả", null, ItemCondition.NEW, null, null);
        Item item2 = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách 2", "Mô tả", null, ItemCondition.NEW, null, null);
        Item item3 = new Item(10L, 1L, 1L, OfferType.GIVE, "Sách 3", "Mô tả", null, ItemCondition.NEW, null, null);
        ReflectionTestUtils.setField(item1, "id", 1L);
        ReflectionTestUtils.setField(item2, "id", 2L);
        ReflectionTestUtils.setField(item3, "id", 3L);
        Instant expireAt = Instant.parse("2026-11-18T00:00:00Z");
        Instant boostedUntil = Instant.parse("2026-10-22T00:00:00Z");
        when(itemRepository.findByDonorIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(item1, item2, item3));
        when(listingFeeQueryService.findDisplayWindows(List.of(1L, 2L, 3L)))
                .thenReturn(Map.of(1L, new DisplayWindow(expireAt, boostedUntil)));

        // when
        List<ItemResponse> responses = itemService.listMyItems(10L);

        // then: một lần đọc cho cả danh sách; bài không có trong map thì hai field null
        verify(listingFeeQueryService, times(1)).findDisplayWindows(any());
        assertThat(responses.get(0).expireAt()).isEqualTo(expireAt);
        assertThat(responses.get(0).boostedUntil()).isEqualTo(boostedUntil);
        assertThat(responses.get(1).expireAt()).isNull();
        assertThat(responses.get(2).boostedUntil()).isNull();
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
    void hideAndUnhideOnlyChangeVisibility() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Book", "Description", null, ItemCondition.NEW, null, null);
        item.approve();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        itemService.hide(1L);
        assertThat(item.isActive()).isFalse();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.APPROVED);
        itemService.unhide(1L);
        assertThat(item.isActive()).isTrue();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.APPROVED);
    }

    @Test
    void hideAllOfUserOnlyHidesTheirActiveItems() {
        Item first = new Item(10L, 1L, 1L, OfferType.GIVE, "Book", "Description", null, ItemCondition.NEW, null, null);
        Item second = new Item(10L, 1L, 1L, OfferType.GIVE, "Pen", "Description", null, ItemCondition.NEW, null, null);
        when(itemRepository.findByDonorIdAndIsActiveTrue(10L)).thenReturn(List.of(first, second));

        itemService.hideAllOfUser(10L);

        assertThat(first.isActive()).isFalse();
        assertThat(second.isActive()).isFalse();
        verify(itemRepository).findByDonorIdAndIsActiveTrue(10L);
    }

    @Test
    void markTradedAndExpiredRequireValidTransitions() {
        Item traded = new Item(10L, 1L, 1L, OfferType.GIVE, "Book", "Description", null, ItemCondition.NEW, null, null);
        Item expired = new Item(10L, 1L, 1L, OfferType.GIVE, "Pen", "Description", null, ItemCondition.NEW, null, null);
        traded.approve();
        expired.approve();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(traded));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(expired));

        itemService.markTraded(1L);
        itemService.markExpired(2L);
        assertThat(traded.getStatus()).isEqualTo(ItemStatus.TRADED);
        assertThat(expired.getStatus()).isEqualTo(ItemStatus.EXPIRED);
        assertThatThrownBy(() -> itemService.markTraded(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);
        assertThatThrownBy(() -> itemService.markExpired(2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);
    }

    @Test
    void removeReleasesHeldFeeAndRejectsReservedItem() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Book", "Description", null, ItemCondition.NEW, null, null);
        ReflectionTestUtils.setField(item, "id", 1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(postFeeService.hasHeldFee(1L)).thenReturn(true);

        assertThatThrownBy(() -> itemService.remove(1L, 11L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_NOT_OWNER);
        itemService.remove(1L, 10L);
        assertThat(item.isDeleted()).isTrue();
        verify(postFeeService).releaseHeldFee(1L, "DELETED");

        Item reserved =
                new Item(10L, 1L, 1L, OfferType.GIVE, "Pen", "Description", null, ItemCondition.NEW, null, null);
        reserved.approve();
        reserved.reserve();
        ReflectionTestUtils.setField(reserved, "id", 2L);
        when(itemRepository.findById(2L)).thenReturn(Optional.of(reserved));
        assertThatThrownBy(() -> itemService.remove(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_REMOVE_NOT_ALLOWED);
        assertThat(reserved.isDeleted()).isFalse();
    }

    @Test
    void removeRejectsAdvancedRequest() {
        Item item = new Item(10L, 1L, 1L, OfferType.GIVE, "Book", "Description", null, ItemCondition.NEW, null, null);
        ReflectionTestUtils.setField(item, "id", 1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(requestQueryService.hasReservedOrHigherRequest(1L)).thenReturn(true);

        assertThatThrownBy(() -> itemService.remove(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ItemErrorCode.ITEM_REMOVE_NOT_ALLOWED);
        assertThat(item.isDeleted()).isFalse();
        verifyNoInteractions(postFeeService);
    }
}
