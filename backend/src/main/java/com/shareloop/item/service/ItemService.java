package com.shareloop.item.service;

import com.shareloop.catalog.service.AreaService;
import com.shareloop.catalog.service.CategoryAttributeService;
import com.shareloop.catalog.service.CategoryService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.PostLimitProperties;
import com.shareloop.item.dto.AdminItemDetail;
import com.shareloop.item.dto.CreateItemRequest;
import com.shareloop.item.dto.FeaturedItemsResponse;
import com.shareloop.item.dto.ItemAttributeValue;
import com.shareloop.item.dto.ItemListPageResponse;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.dto.PostingQuotaResponse;
import com.shareloop.item.dto.UpdateItemRequest;
import com.shareloop.item.dto.UserItemTab;
import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.entity.OperatingStatus;
import com.shareloop.item.event.ItemRemovedEvent;
import com.shareloop.item.mapper.ItemMapper;
import com.shareloop.item.repository.ItemRepository;
import com.shareloop.listingfee.dto.DisplayWindow;
import com.shareloop.listingfee.service.ListingFeeQueryService;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.media.MediaErrorCode;
import com.shareloop.media.service.MediaService;
import com.shareloop.moderation.ModerationResult;
import com.shareloop.moderation.ModerationStatus;
import com.shareloop.moderation.service.ContentModerationService;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.service.RequestQueryService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.service.UserService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Service quản lý bài đăng: tạo mới, xem chi tiết, xem danh sách bài của tôi.
 */
@Service
public class ItemService {
    private static final Pattern MEDIA_URL = Pattern.compile("/api/v1/media/([1-9][0-9]*)");
    private final ItemRepository itemRepository;
    private final JdbcClient jdbcClient;
    private final MediaService mediaService;
    private final ConfigService configService;
    private final RequestQueryService requestQueryService;
    private final ContentModerationService contentModerationService;
    private final PostFeeService postFeeService;
    private final ListingFeeQueryService listingFeeQueryService;
    private final CategoryAttributeService categoryAttributeService;
    private final ReputationService reputationService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final PostLimitProperties postLimitProperties;
    private final CategoryService categoryService;
    private final AreaService areaService;

    public ItemService(
            ItemRepository itemRepository,
            @Autowired(required = false) JdbcClient jdbcClient,
            MediaService mediaService,
            ConfigService configService,
            RequestQueryService requestQueryService,
            ContentModerationService contentModerationService,
            PostFeeService postFeeService,
            ListingFeeQueryService listingFeeQueryService,
            CategoryAttributeService categoryAttributeService,
            ReputationService reputationService,
            UserService userService,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            PostLimitProperties postLimitProperties,
            @Lazy CategoryService categoryService,
            AreaService areaService) {
        this.itemRepository = itemRepository;
        this.jdbcClient = jdbcClient;
        this.mediaService = mediaService;
        this.configService = configService;
        this.requestQueryService = requestQueryService;
        this.contentModerationService = contentModerationService;
        this.postFeeService = postFeeService;
        this.listingFeeQueryService = listingFeeQueryService;
        this.categoryAttributeService = categoryAttributeService;
        this.reputationService = reputationService;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.postLimitProperties = postLimitProperties;
        this.categoryService = categoryService;
        this.areaService = areaService;
    }

    /**
     * Tạo bài đăng mới ở trạng thái PENDING_REVIEW (F07 bước 1).
     * Người có 0 sao uy tín không được đăng bài (FR-92, BR-U05): ném ITEM_REPUTATION_BLOCKED.
     */
    @Transactional
    public ItemResponse createItem(Long donorId, CreateItemRequest request) {
        // Kiểm tra sao uy tín đầu tiên để không giữ phí hay kiểm duyệt cho người đã bị khoá quyền đăng bài
        requireCanPost(donorId);
        categoryService.getById(request.categoryId());
        areaService.getDistrict(request.areaId());

        // SWAP bắt buộc phải có món mong muốn (BR-S02, ck_items_swap_desired)
        if (request.offerType() == OfferType.SWAP
                && (request.desiredItem() == null || request.desiredItem().isBlank())) {
            throw new BusinessException(ItemErrorCode.ITEM_SWAP_DESIRED_ITEM_REQUIRED);
        }

        // Đồ lỗi bắt buộc phải có mô tả lỗi (ck_items_defect_note)
        if (request.condition() == ItemCondition.DEFECTIVE
                && (request.defectNote() == null || request.defectNote().isBlank())) {
            throw new BusinessException(ItemErrorCode.ITEM_DEFECT_NOTE_REQUIRED);
        }
        List<Long> defectMediaIds = parseMediaIds(request.defectImages());
        validateDefectImages(request.condition(), defectMediaIds);
        Optional<Long> operatingAttributeId = requireOperatingStatus(request.categoryId(), request.operatingStatus());
        // Tài khoản test được bỏ qua giới hạn bài Cho và luôn dùng phí thường cho bài Trao đổi.
        if (request.offerType() == OfferType.GIVE && !canBypassPostLimit(donorId)) {
            checkActiveGiveLimit(donorId);
        }

        String moderationText = request.desiredItem() == null
                ? request.description()
                : request.description() + " " + request.desiredItem();
        var moderation = contentModerationService.checkPost(request.categoryId(), request.title(), moderationText);
        if (moderation.status() == ModerationStatus.BLOCK) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_MODERATION_BLOCKED,
                    ItemErrorCode.ITEM_MODERATION_BLOCKED.defaultMessage(),
                    "content",
                    Map.of("reasonCode", moderation.reasonCode(), "matchedTerms", moderation.matchedTerms()));
        }

        Item item = new Item(
                donorId,
                request.categoryId(),
                request.areaId(),
                request.offerType(),
                request.title(),
                request.description(),
                request.desiredItem(),
                request.condition(),
                request.defectNote(),
                request.brand());

        int minImages = configService.getInt(ConfigKey.ITEM_PHOTOS_MIN);
        int maxImages = configService.getInt(ConfigKey.ITEM_PHOTOS_MAX);
        if (request.images() == null
                || request.images().size() < minImages
                || request.images().size() > maxImages) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_INVALID_IMAGE_COUNT,
                    "Bài đăng cần từ %d đến %d ảnh.".formatted(minImages, maxImages),
                    "images",
                    Map.of("min", minImages, "max", maxImages));
        }
        List<Long> mediaIds = parseMediaIds(request.images());
        List<Long> allMediaIds = combineMediaIds(mediaIds, defectMediaIds);
        // PostFeeService reads the new item through JDBC in the same transaction.
        Item saved = itemRepository.saveAndFlush(item);
        postFeeService.holdPostFee(saved.getId(), donorId);
        mediaService.attach(allMediaIds, donorId);
        saveMediaMappings(saved.getId(), mediaIds, "PHOTO");
        saveMediaMappings(saved.getId(), defectMediaIds, "DEFECT");
        operatingAttributeId.ifPresent(id -> saveOperatingStatus(saved.getId(), id, request.operatingStatus()));
        if (moderation.status() == ModerationStatus.REVIEW) {
            saveReviewFlag(saved.getId(), moderation);
        }
        return ItemMapper.toResponse(
                saved,
                mediaUrls(mediaIds),
                mediaUrls(defectMediaIds),
                request.operatingStatus(),
                findDisplayWindow(saved.getId()));
    }

    /**
     * Gửi bản sửa của bài, bài quay về PENDING_REVIEW (F07 bước 4); phí sửa do PostFeeService.holdEditFee quyết.
     * Đổi hình thức được báo lại phí đăng theo lịch sử thu phí của chính bài.
     */
    @Transactional
    public ItemResponse updateItem(long itemId, long userId, UpdateItemRequest request) {
        Item item = requireItem(itemId);
        if (item.getDonorId() != userId) {
            throw new BusinessException(ItemErrorCode.ITEM_NOT_OWNER);
        }
        // Bài của người 0 sao đã bị ẩn, sửa sẽ tốn phí sửa cho bài không hiển thị được (BR-U05)
        requireCanPost(userId);
        if (item.getStatus() == ItemStatus.RESERVED
                || item.getStatus() == ItemStatus.TRADED
                || item.getStatus() == ItemStatus.EXPIRED
                || requestQueryService.hasReservedOrHigherRequest(itemId)) {
            throw new BusinessException(ItemErrorCode.ITEM_EDIT_NOT_ALLOWED);
        }
        if (request.offerType() != item.getOfferType()) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_OFFER_TYPE_CHANGE_NOT_ALLOWED,
                    "Không đổi hình thức Cho tặng / Trao đổi khi sửa bài.");
        }
        if (request.offerType() == OfferType.GIVE
                && item.getStatus() == ItemStatus.REJECTED
                && !canBypassPostLimit(userId)) {
            checkActiveGiveLimit(userId);
        }
        categoryService.getById(request.categoryId());
        areaService.getDistrict(request.areaId());
        validateContent(request.offerType(), request.desiredItem(), request.condition(), request.defectNote());
        String moderationText = request.desiredItem() == null
                ? request.description()
                : request.description() + " " + request.desiredItem();
        var moderation = contentModerationService.checkPost(request.categoryId(), request.title(), moderationText);
        if (moderation.status() == ModerationStatus.BLOCK) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_MODERATION_BLOCKED,
                    ItemErrorCode.ITEM_MODERATION_BLOCKED.defaultMessage(),
                    "content",
                    Map.of("reasonCode", moderation.reasonCode(), "matchedTerms", moderation.matchedTerms()));
        }

        List<Long> newMediaIds = null;
        if (request.images() != null) {
            validateImageCount(request.images());
            newMediaIds = parseMediaIds(request.images());
            if (new HashSet<>(newMediaIds).size() != newMediaIds.size()) {
                throw new BusinessException(MediaErrorCode.MEDIA_INVALID_FILE);
            }
        }
        List<Long> newDefectMediaIds =
                request.defectImages() == null ? findMediaIds(itemId, "DEFECT") : parseMediaIds(request.defectImages());
        if (request.condition() != ItemCondition.DEFECTIVE) {
            if (request.defectImages() != null && !newDefectMediaIds.isEmpty()) {
                throw new BusinessException(
                        ItemErrorCode.ITEM_DEFECT_IMAGES_NOT_ALLOWED,
                        ItemErrorCode.ITEM_DEFECT_IMAGES_NOT_ALLOWED.defaultMessage(),
                        "defectImages",
                        null);
            }
            newDefectMediaIds = List.of();
        }
        validateDefectImages(request.condition(), newDefectMediaIds);
        Optional<Long> operatingAttributeId = requireOperatingStatus(request.categoryId(), request.operatingStatus());
        postFeeService.quoteEdit(itemId, userId);
        postFeeService.holdEditFee(itemId, userId);
        if (requestQueryService.hasReservedOrHigherRequest(itemId)) {
            throw new BusinessException(ItemErrorCode.ITEM_EDIT_NOT_ALLOWED);
        }

        Long previousCategoryId = item.getCategoryId();
        item.submitEdit(
                request.categoryId(),
                request.areaId(),
                request.offerType(),
                request.title(),
                request.description(),
                request.desiredItem(),
                request.condition(),
                request.defectNote(),
                request.brand());
        if (newMediaIds != null || request.defectImages() != null || request.condition() != ItemCondition.DEFECTIVE) {
            replaceMediaMappings(
                    itemId,
                    userId,
                    newMediaIds == null ? findMediaIds(itemId, "PHOTO") : newMediaIds,
                    newDefectMediaIds);
        }
        replaceOperatingStatus(itemId, previousCategoryId, operatingAttributeId, request.operatingStatus());
        if (moderation.status() == ModerationStatus.REVIEW) {
            saveReviewFlag(itemId, moderation);
        }
        return withHiddenState(toResponse(item, findDisplayWindow(itemId)), item);
    }

    /**
     * Xem chi tiết bài đăng:
     * - Bài APPROVED: ai cũng xem được.
     * - Bài TRADED (đã giao dịch thành công): ai cũng xem được ở chế độ chỉ xem, requestable = false để frontend ẩn
     *   nút gửi yêu cầu. Không trả thông tin người nhận hay yêu cầu liên quan.
     * - Chủ bài đăng: xem được mọi trạng thái.
     * - Còn lại (chờ duyệt, bị từ chối, hết hạn, bị ẩn, chủ bài bị khoá): trả về ITEM_NOT_FOUND (404).
     */
    @Transactional(readOnly = true)
    public ItemResponse getItem(Long id, Long currentUserId) {
        Item item = itemRepository.findById(id).orElseThrow(() -> new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));

        boolean isOwner = currentUserId != null && currentUserId.equals(item.getDonorId());
        boolean donorLocked = userService.isLocked(item.getDonorId());
        boolean publiclyVisible = item.isActive() && !donorLocked && isPublicDetailStatus(item.getStatus());
        if (!publiclyVisible && !isOwner) {
            throw new BusinessException(ItemErrorCode.ITEM_NOT_FOUND);
        }

        var donor = userService.findBriefs(List.of(item.getDonorId())).get(item.getDonorId());
        var reputation = reputationService.getReputationSummary(item.getDonorId());
        boolean requestable = item.getStatus() == ItemStatus.APPROVED && item.isActive() && !donorLocked;
        ItemResponse response = toResponse(item, findDisplayWindow(item.getId()))
                .withDonor(
                        donor == null ? null : donor.fullName(),
                        reputation.trustStars(),
                        reputation.rank().name(),
                        reputation.asDescribedRate(),
                        donorLocked)
                .withTradeInfo(findCompletedAt(item), requestable);
        // U04: chỉ chủ bài biết bài đang tạm ẩn; người khác nhận hidden = null và không thấy các mốc đợt đẩy.
        return isOwner ? withHiddenState(response, item) : response.withoutBoostTimeline();
    }

    // Trạng thái người ngoài được xem chi tiết: đang hiển thị, hoặc đã giao dịch xong (xem từ hồ sơ công khai).
    private static boolean isPublicDetailStatus(ItemStatus status) {
        return status == ItemStatus.APPROVED || status == ItemStatus.TRADED;
    }

    // Chỉ bài TRADED mới có thời điểm hoàn tất; bài khác không cần hỏi module request.
    private Instant findCompletedAt(Item item) {
        if (item.getStatus() != ItemStatus.TRADED) {
            return null;
        }
        return requestQueryService
                .findCompletedAtByItemIds(List.of(item.getId()))
                .get(item.getId());
    }

    /**
     * Danh sách bài của một người cho hồ sơ công khai. ACTIVE: bài APPROVED, không ẩn, còn hạn, mới nhất trước.
     * DONE: bài TRADED, không ẩn, kèm completedAt, bài cập nhật gần nhất trước. Không bao giờ trả bài chờ duyệt, bị từ
     * chối, hết hạn, đã gỡ; chủ hồ sơ tự xem cũng theo luật này.
     *
     * @throws BusinessException USER_NOT_FOUND khi người dùng không tồn tại, chưa xác thực, bị khoá hoặc bị cấm
     */
    @Transactional(readOnly = true)
    public ItemListPageResponse listUserItems(long userId, UserItemTab tab, int page, int size) {
        if (!userService.isPubliclyVisible(userId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        Page<Item> items = findUserItemsPage(userId, tab, PageRequest.of(page, size));

        List<Long> itemIds = items.getContent().stream().map(Item::getId).toList();
        Map<Long, DisplayWindow> displayWindows = listingFeeQueryService.findDisplayWindows(itemIds);
        Map<Long, Instant> completedAtByItemId =
                tab == UserItemTab.DONE ? requestQueryService.findCompletedAtByItemIds(itemIds) : Map.of();

        List<ItemResponse> rows = new ArrayList<>();
        for (Item item : items.getContent()) {
            // Bài ở tab ACTIVE đều APPROVED và chủ bài không bị khoá nên còn nhận yêu cầu; bài DONE thì không.
            boolean requestable = tab == UserItemTab.ACTIVE;
            // Hồ sơ công khai do người khác xem: không trả các mốc đợt đẩy.
            rows.add(toResponse(item, displayWindows.get(item.getId()))
                    .withTradeInfo(completedAtByItemId.get(item.getId()), requestable)
                    .withoutBoostTimeline());
        }
        return new ItemListPageResponse(rows, page, size, items.getTotalElements(), items.getTotalPages());
    }

    // DONE sắp theo updated_at vì bài TRADED không sửa được nữa, lần cập nhật cuối là lúc chuyển sang TRADED.
    private Page<Item> findUserItemsPage(long userId, UserItemTab tab, PageRequest pageRequest) {
        if (tab == UserItemTab.DONE) {
            Sort newestFirst = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
            return itemRepository.findByDonorIdAndStatusAndIsActiveTrue(
                    userId, ItemStatus.TRADED, pageRequest.withSort(newestFirst));
        }
        return itemRepository.findPublicActiveByDonor(userId, Instant.now(), pageRequest);
    }

    /**
     * Admin xem chi tiết bài ở mọi trạng thái (kể cả is_active = false) để duyệt bài PENDING_REVIEW.
     * Cố ý tách khỏi getItem để endpoint công khai không bị nới quyền. Ném ITEM_NOT_FOUND khi bài không tồn tại
     * hoặc đã xoá mềm.
     */
    @Transactional(readOnly = true)
    public AdminItemDetail getItemForAdmin(long itemId) {
        Item item = requireItem(itemId);
        ItemResponse response = withHiddenState(toResponse(item, findDisplayWindow(itemId)), item);
        return new AdminItemDetail(response, item.isActive(), findAttributeValues(itemId));
    }

    /**
     * Xem danh sách bài đăng của chính mình.
     */
    @Transactional(readOnly = true)
    public List<ItemResponse> listMyItems(Long currentUserId) {
        List<Item> items = itemRepository.findByDonorIdOrderByCreatedAtDesc(currentUserId);
        Map<Long, DisplayWindow> displayWindows = findDisplayWindows(items);
        List<ItemResponse> responses = new ArrayList<>();
        for (Item item : items) {
            responses.add(withHiddenState(toResponse(item, displayWindows.get(item.getId())), item));
        }
        return responses;
    }

    // Bài đang is_active = false (tự ẩn do báo cáo, Admin ẩn, hết sao) là "đang tạm ẩn". Lý do lấy từ C06 khi có.
    private static ItemResponse withHiddenState(ItemResponse response, Item item) {
        return response.withVisibility(!item.isActive(), null);
    }

    /**
     * Danh sách công khai bài APPROVED còn hiển thị (FR-50). Mọi bộ lọc đều không bắt buộc và kết hợp được với nhau;
     * lọc ở database trước khi phân trang để tổng số luôn đúng.
     */
    @Transactional(readOnly = true)
    public ItemListPageResponse listPublicItems(
            Long categoryId, Long areaId, String q, ItemCondition condition, OfferType offerType, int page, int size) {
        return listPublicItems(categoryId, areaId, q, condition, offerType, page, size, false);
    }

    /** Số bài công khai theo danh mục, dùng cùng điều kiện hiển thị với danh sách Tìm đồ. */
    @Transactional(readOnly = true)
    public Map<Long, Long> countPublicItemsByCategory() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : itemRepository.countPublicItemsByCategory(Instant.now())) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public ItemListPageResponse listPublicItems(
            Long categoryId,
            Long areaId,
            String q,
            ItemCondition condition,
            OfferType offerType,
            int page,
            int size,
            boolean boostedOnly) {
        return listPublicItems(categoryId, areaId, q, condition, offerType, page, size, boostedOnly, "newest");
    }

    @Transactional(readOnly = true)
    public ItemListPageResponse listPublicItems(
            Long categoryId,
            Long areaId,
            String q,
            ItemCondition condition,
            OfferType offerType,
            int page,
            int size,
            boolean boostedOnly,
            String sort) {
        return listPublicItems(
                categoryId, areaId, q, condition, offerType, page, size, boostedOnly, sort, null, null, null);
    }

    /**
     * Danh sách Tìm đồ có xoay vòng bài đẩy (FR-50, C15). Bài đang đẩy khớp bộ lọc xếp thành vòng theo id tăng dần;
     * mỗi trang tối đa boost_max_per_page bài đẩy ở đầu, chọn bằng {@link BoostRotation} theo lượt và người xem. Bài đẩy
     * không lên đầu trang vẫn nằm trong phần bài thường theo thứ tự bình thường, nên không bài nào biến mất hay lặp lại
     * giữa các trang của cùng một lượt. boostedOnly = true ("Tin nổi bật") trả mọi bài đang đẩy theo thứ tự xoay.
     *
     * @param rotationSeed lượt xoay FE gửi lại khi chuyển trang; null thì tính từ giờ hiện tại
     * @param currentUserId người xem đã đăng nhập (null nếu là khách)
     * @param viewerKey số không âm khách gửi lên; bỏ qua khi đã đăng nhập
     */
    @Transactional(readOnly = true)
    public ItemListPageResponse listPublicItems(
            Long categoryId,
            Long areaId,
            String q,
            ItemCondition condition,
            OfferType offerType,
            int page,
            int size,
            boolean boostedOnly,
            String sort,
            Long rotationSeed,
            Long currentUserId,
            Long viewerKey) {
        BrowseFilter filter = new BrowseFilter(
                categoryId,
                areaId,
                condition == null ? null : condition.name(),
                offerType == null ? null : offerType.name(),
                q == null ? "" : q.trim(),
                Instant.now());
        List<Long> ring = itemRepository.findPublicBoostedIds(
                filter.categoryId(), filter.areaId(), filter.condition(), filter.offerType(), filter.q(), filter.now());
        long round = rotationSeed != null ? rotationSeed : currentRotationRound(filter.now());
        long viewer = resolveViewerKey(currentUserId, viewerKey);
        // Số bài đẩy trên một trang nằm trong [0, size]: không vượt cỡ trang, cấu hình âm coi như 0.
        int boostPerPage = Math.max(0, Math.min(configService.getInt(ConfigKey.BOOST_MAX_PER_PAGE), size));

        long total = boostedOnly ? ring.size() : countPublicBrowse(filter);
        int totalPages = (int) ((total + size - 1) / size);
        List<Item> selected = boostedOnly
                ? boostedOnlyPage(ring, round, viewer, boostPerPage, page, size)
                : browsePage(filter, ring, round, viewer, boostPerPage, page, size, sort, totalPages);
        Map<Long, DisplayWindow> windows = findDisplayWindows(selected);
        List<ItemResponse> responses = new ArrayList<>();
        for (Item item : selected) {
            // Danh sách công khai: không trả các mốc đợt đẩy của chủ bài.
            responses.add(toResponse(item, windows.get(item.getId())).withoutBoostTimeline());
        }
        return new ItemListPageResponse(responses, page, size, total, totalPages, round);
    }

    /**
     * Banner bài đẩy ở trang chủ (C15): tối đa {@code limit} bài đang đẩy (không lọc) theo vòng xoay của lượt hiện tại;
     * mỗi boost_rotate_seconds giây đổi sang nhóm kế tiếp, mỗi người xem bắt đầu ở một chỗ khác nhau. Có ít bài hơn
     * limit thì trả hết. Không trả các mốc đợt đẩy của chủ bài.
     *
     * @param limit số bài tối đa của banner (controller giới hạn 1..10)
     * @param currentUserId người xem đã đăng nhập (null nếu là khách)
     * @param viewerKey số không âm khách gửi lên; bỏ qua khi đã đăng nhập
     */
    @Transactional(readOnly = true)
    public FeaturedItemsResponse listFeaturedItems(int limit, Long currentUserId, Long viewerKey) {
        Instant now = Instant.now();
        List<Long> ring = itemRepository.findPublicBoostedIds(null, null, null, null, "", now);
        int rotateSeconds = rotateSeconds();
        long round = now.getEpochSecond() / rotateSeconds;
        List<Long> featuredIds =
                BoostRotation.pick(ring, round, resolveViewerKey(currentUserId, viewerKey), limit, limit);

        List<Item> featured = findItemsInOrder(featuredIds);
        Map<Long, DisplayWindow> windows = findDisplayWindows(featured);
        List<ItemResponse> responses = new ArrayList<>();
        for (Item item : featured) {
            responses.add(toResponse(item, windows.get(item.getId())).withoutBoostTimeline());
        }
        return new FeaturedItemsResponse(
                responses,
                Instant.ofEpochSecond(round * rotateSeconds),
                Instant.ofEpochSecond((round + 1) * rotateSeconds));
    }

    /** Bộ lọc của trang Tìm đồ, gom lại để khỏi truyền sáu tham số qua từng hàm. */
    private record BrowseFilter(
            Long categoryId, Long areaId, String condition, String offerType, String q, Instant now) {}

    private long countPublicBrowse(BrowseFilter filter) {
        return itemRepository.countPublicBrowseItems(
                filter.categoryId(), filter.areaId(), filter.condition(), filter.offerType(), filter.q(), filter.now());
    }

    /**
     * Một trang Tìm đồ. Chỉ {@code boostPerPage * totalPages} bài đầu của vòng xoay (slotIds) được lên đầu trang; trang p
     * lấy slotIds từ vị trí p * boostPerPage. Phần còn lại lấy từ "mọi bài trừ slotIds", lùi offset đúng số bài đẩy đã
     * đứng đầu các trang trước, nên ghép các trang lại thì đủ mọi bài, không trùng.
     */
    private List<Item> browsePage(
            BrowseFilter filter,
            List<Long> ring,
            long round,
            long viewer,
            int boostPerPage,
            int page,
            int size,
            String sort,
            int totalPages) {
        int slotCount = (int) Math.min(ring.size(), (long) boostPerPage * totalPages);
        List<Long> slotIds = BoostRotation.pick(ring, round, viewer, boostPerPage, slotCount);
        List<Long> boostIdsOnPage = sliceIds(slotIds, (long) page * boostPerPage, boostPerPage);
        long boostedBefore = Math.min(slotCount, (long) page * boostPerPage);

        List<Item> selected = new ArrayList<>(findItemsInOrder(boostIdsOnPage));
        int regularLimit = size - boostIdsOnPage.size();
        long regularOffset = (long) page * size - boostedBefore;
        if (regularLimit > 0) {
            selected.addAll(findRegularItems(filter, slotIds, sort, regularLimit, regularOffset));
        }
        return selected;
    }

    // Trang "Tin nổi bật": chỉ bài đang đẩy, cả vòng theo thứ tự đã xoay, rồi phân trang bình thường.
    private List<Item> boostedOnlyPage(List<Long> ring, long round, long viewer, int boostPerPage, int page, int size) {
        List<Long> rotatedIds = BoostRotation.pick(ring, round, viewer, boostPerPage, ring.size());
        return findItemsInOrder(sliceIds(rotatedIds, (long) page * size, size));
    }

    private List<Item> findRegularItems(
            BrowseFilter filter, List<Long> excludedIds, String sort, int limit, long offset) {
        // NOT IN () rỗng là lỗi cú pháp ở PostgreSQL nên truyền id giả khi không loại bài nào.
        List<Long> excluded = excludedIds.isEmpty() ? List.of(ItemRepository.NO_ITEM_ID) : excludedIds;
        return itemRepository.findPublicBrowseItemsExcluding(
                filter.categoryId(),
                filter.areaId(),
                filter.condition(),
                filter.offerType(),
                filter.q(),
                filter.now(),
                excluded,
                sort,
                limit,
                offset);
    }

    // findAllById không giữ thứ tự id truyền vào, nên sắp lại theo đúng thứ tự xoay. Bài vừa bị gỡ thì bỏ qua.
    private List<Item> findItemsInOrder(List<Long> itemIds) {
        Map<Long, Item> itemsById = new HashMap<>();
        for (Item item : itemRepository.findAllById(itemIds)) {
            itemsById.put(item.getId(), item);
        }
        List<Item> ordered = new ArrayList<>();
        for (Long itemId : itemIds) {
            Item item = itemsById.get(itemId);
            if (item != null) {
                ordered.add(item);
            }
        }
        return ordered;
    }

    private static List<Long> sliceIds(List<Long> ids, long fromIndex, int maxCount) {
        if (fromIndex >= ids.size() || maxCount <= 0) {
            return List.of();
        }
        int from = (int) fromIndex;
        return ids.subList(from, Math.min(ids.size(), from + maxCount));
    }

    // Lượt xoay = số giây epoch chia boost_rotate_seconds, nên mọi người cùng đổi nhóm bài đẩy vào một thời điểm.
    private long currentRotationRound(Instant now) {
        return now.getEpochSecond() / rotateSeconds();
    }

    // Cấu hình ≤ 0 thì coi như 1 giây để không chia cho 0.
    private int rotateSeconds() {
        return Math.max(1, configService.getInt(ConfigKey.BOOST_ROTATE_SECONDS));
    }

    // Đã đăng nhập thì dùng id người dùng (mỗi người một nhóm ổn định); khách thì dùng số FE gửi, không có thì 0.
    private static long resolveViewerKey(Long currentUserId, Long viewerKey) {
        if (currentUserId != null) {
            return currentUserId;
        }
        return viewerKey != null ? viewerKey : 0L;
    }

    /** Search gateway for the AI assistant; the database remains the source of every returned item. */
    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> searchApprovedForAi(
            Long categoryId, Long areaId, String q, ItemCondition condition, OfferType offerType, int page, int size) {
        Page<Item> items = itemRepository.searchApprovedForAi(
                categoryId,
                areaId,
                condition == null ? null : condition.name(),
                offerType == null ? null : offerType.name(),
                q == null ? "" : q.trim(),
                PageRequest.of(page, size));
        return toPageResponse(items);
    }

    /**
     * Giữ chỗ bài đăng khi chủ bài chọn người nhận (BR-D01, Issue #38).
     */
    @Transactional
    public void reserve(Long itemId) {
        transition(itemId, ItemStatus.APPROVED, ItemStatus.RESERVED);
    }

    /**
     * Chuyển trạng thái bài đăng sang TRADED khi Request hoàn tất giao dịch (COMPLETED).
     */
    @Transactional
    public void markTraded(long itemId) {
        Item item = requireItem(itemId);
        if (!ItemStatus.canTransition(item.getStatus(), ItemStatus.TRADED)) {
            throw new BusinessException(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);
        }
        item.markTraded();
    }

    /** Nhả giữ chỗ sau khi Request bị huỷ. */
    @Transactional
    public void release(long itemId) {
        transition(itemId, ItemStatus.RESERVED, ItemStatus.APPROVED);
    }

    /**
     * Nhả giữ chỗ nếu món đang RESERVED, món ở trạng thái khác thì bỏ qua. Dùng cho món mang đổi: giao dịch SWAP
     * tạo trước khi món này được giữ chỗ chưa từng đặt nó sang RESERVED, nên huỷ giao dịch đó không được ném lỗi.
     */
    @Transactional
    public void releaseIfReserved(long itemId) {
        if (requireItem(itemId).getStatus() == ItemStatus.RESERVED) {
            release(itemId);
        }
    }

    /**
     * Chuyển đổi loại bài đăng Cho ↔ Trao đổi khi chọn người nhận (CD-02, CD-04, CD-06).
     *
     * @param itemId id bài đăng
     * @param newOfferType loại bài mới
     */
    @Transactional
    public void convertOfferType(long itemId, OfferType newOfferType) {
        Item item = requireItem(itemId);
        item.convertOfferType(newOfferType);
        itemRepository.save(item);
    }

    @Transactional
    public void hide(long itemId) {
        requireItem(itemId).deactivate();
    }

    @Transactional
    public void unhide(long itemId) {
        requireItem(itemId).activate();
    }

    @Transactional
    public void remove(long itemId) {
        removeItem(requireItem(itemId));
    }

    @Transactional
    public void remove(long itemId, long userId) {
        Item item = requireItem(itemId);
        if (item.getDonorId() != userId) {
            throw new BusinessException(ItemErrorCode.ITEM_NOT_OWNER);
        }
        removeItem(item);
    }

    private void removeItem(Item item) {
        long itemId = item.getId();
        if (item.getStatus() == ItemStatus.RESERVED
                || item.getStatus() == ItemStatus.TRADED
                || item.getStatus() == ItemStatus.EXPIRED
                || requestQueryService.hasReservedOrHigherRequest(itemId)) {
            throw new BusinessException(ItemErrorCode.ITEM_REMOVE_NOT_ALLOWED);
        }
        if (postFeeService.hasHeldFee(itemId)) {
            postFeeService.releaseHeldFee(itemId, "DELETED");
        }
        item.markDeleted();
        eventPublisher.publishEvent(new ItemRemovedEvent(itemId, item.getDonorId()));
    }

    /**
     * Ẩn mọi bài APPROVED đang hiển thị của một chủ bài (is_active = false, không đổi status), dùng khi khoá tài khoản.
     *
     * @return id các bài vừa ẩn, để mở khoá thì hiện lại đúng những bài này
     */
    @Transactional
    public List<Long> hideApprovedItemsOfUser(long userId) {
        List<Item> items = itemRepository.findByDonorIdAndStatusAndIsActiveTrue(userId, ItemStatus.APPROVED);
        List<Long> hiddenIds = new ArrayList<>();
        for (Item item : items) {
            item.deactivate();
            hiddenIds.add(item.getId());
        }
        return hiddenIds;
    }

    /** Hiện lại các bài đã bị ẩn khi khoá tài khoản; bài không còn tồn tại thì bỏ qua. */
    @Transactional
    public void unhideItems(List<Long> itemIds) {
        for (Item item : itemRepository.findAllById(itemIds)) {
            item.activate();
        }
    }

    @Transactional
    public void hideAllOfUser(long userId) {
        itemRepository.findByDonorIdAndIsActiveTrue(userId).forEach(Item::deactivate);
    }

    @Transactional
    public void markExpired(long itemId) {
        transition(itemId, ItemStatus.APPROVED, ItemStatus.EXPIRED);
    }

    @Transactional
    public void markApproved(long itemId) {
        transition(itemId, ItemStatus.PENDING_REVIEW, ItemStatus.APPROVED);
    }

    @Transactional
    public void markRejected(long itemId) {
        transition(itemId, ItemStatus.PENDING_REVIEW, ItemStatus.REJECTED);
    }

    private void transition(long itemId, ItemStatus expected, ItemStatus target) {
        Item item = requireItem(itemId);
        if (item.getStatus() != expected || !ItemStatus.canTransition(item.getStatus(), target)) {
            throw new BusinessException(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);
        }
        item.transitionTo(target);
    }

    private Item requireItem(long itemId) {
        return itemRepository.findById(itemId).orElseThrow(() -> new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));
    }

    private List<Long> parseMediaIds(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
                .map(url -> {
                    Matcher matcher = url == null ? null : MEDIA_URL.matcher(url);
                    if (matcher == null || !matcher.matches()) {
                        throw new BusinessException(ItemErrorCode.ITEM_INVALID_IMAGE);
                    }
                    try {
                        return Long.parseLong(matcher.group(1));
                    } catch (NumberFormatException exception) {
                        throw new BusinessException(ItemErrorCode.ITEM_INVALID_IMAGE);
                    }
                })
                .toList();
    }

    private void validateContent(OfferType offerType, String desiredItem, ItemCondition condition, String defectNote) {
        if (offerType == OfferType.SWAP && (desiredItem == null || desiredItem.isBlank())) {
            throw new BusinessException(ItemErrorCode.ITEM_SWAP_DESIRED_ITEM_REQUIRED);
        }
        if (condition == ItemCondition.DEFECTIVE && (defectNote == null || defectNote.isBlank())) {
            throw new BusinessException(ItemErrorCode.ITEM_DEFECT_NOTE_REQUIRED);
        }
    }

    private void validateImageCount(List<String> images) {
        int minImages = configService.getInt(ConfigKey.ITEM_PHOTOS_MIN);
        int maxImages = configService.getInt(ConfigKey.ITEM_PHOTOS_MAX);
        if (images.size() < minImages || images.size() > maxImages) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_INVALID_IMAGE_COUNT,
                    "Bài đăng cần từ %d đến %d ảnh.".formatted(minImages, maxImages),
                    "images",
                    Map.of("min", minImages, "max", maxImages));
        }
    }

    private void requireCanPost(Long userId) {
        if (userService != null && userService.isAdmin(userId)) {
            throw new BusinessException(ItemErrorCode.ITEM_ADMIN_BLOCKED);
        }
        if (!reputationService.canPost(userId)) {
            throw new BusinessException(ItemErrorCode.ITEM_REPUTATION_BLOCKED);
        }
    }

    private boolean canBypassPostLimit(Long donorId) {
        String email = userService.findEmailById(donorId);
        return postLimitProperties.canBypassPostLimit(email);
    }

    private long countActive(Long donorId, OfferType offerType) {
        return itemRepository.countActivePostingItems(donorId, offerType.name(), null, Instant.now());
    }

    private int swapRegularLimit(Long userId) {
        Rank rank = reputationService.getRank(userId);
        ConfigKey limitKey =
                switch (rank) {
                    case BRONZE -> ConfigKey.MAX_ACTIVE_ITEMS_BRONZE;
                    case SILVER -> ConfigKey.MAX_ACTIVE_ITEMS_SILVER;
                    case GOLD -> ConfigKey.MAX_ACTIVE_ITEMS_GOLD;
                    case DIAMOND -> ConfigKey.MAX_ACTIVE_ITEMS_DIAMOND;
                };
        return configService.getInt(limitKey);
    }

    /** Báo phí của bài Trao đổi tiếp theo; loại trừ chính bài đó khi đang đổi từ Cho sang Trao đổi. */
    @Transactional(readOnly = true)
    public int nextPostFee(Long userId, OfferType offerType, Long excludedItemId) {
        if (offerType == OfferType.GIVE) {
            return 0;
        }
        int regularLimit = swapRegularLimit(userId);
        if (canBypassPostLimit(userId) || regularLimit < 0) {
            return configService.getInt(ConfigKey.POST_FEE);
        }
        long active =
                itemRepository.countActivePostingItems(userId, OfferType.SWAP.name(), excludedItemId, Instant.now());
        return configService.getInt(active < regularLimit ? ConfigKey.POST_FEE : ConfigKey.POST_FEE_OVER_QUOTA);
    }

    private void checkActiveGiveLimit(Long donorId) {
        int limit = configService.getInt(ConfigKey.MAX_ACTIVE_GIVE_ITEMS);
        if (limit < 0) {
            return;
        }

        // Serialize submissions by the same owner so concurrent requests cannot exceed the limit.
        jdbcClient
                .sql("SELECT pg_advisory_xact_lock(:ownerId)")
                .param("ownerId", donorId)
                .query()
                .singleRow();
        long activeCount = countActive(donorId, OfferType.GIVE);
        if (activeCount < limit) {
            return;
        }
        throw new BusinessException(
                ItemErrorCode.ITEM_GIVE_LIMIT_REACHED,
                "Bạn chỉ được có tối đa %d bài Cho tặng đang hiển thị hoặc chờ duyệt.".formatted(limit),
                null,
                Map.of("limit", limit, "activeCount", activeCount));
    }

    /**
     * Số bài đang hoạt động theo hình thức; Cho tặng có hạn mức, Trao đổi có ngưỡng phí thường theo hạng.
     * Tài khoản được bỏ qua giới hạn (PostLimitProperties) có hạn mức -1 và luôn dùng phí thường.
     */
    @Transactional(readOnly = true)
    public PostingQuotaResponse getPostingQuota(Long userId) {
        boolean bypass = canBypassPostLimit(userId);
        int giveLimit = bypass ? -1 : configService.getInt(ConfigKey.MAX_ACTIVE_GIVE_ITEMS);
        int swapLimit = bypass ? -1 : swapRegularLimit(userId);
        long swapActive = countActive(userId, OfferType.SWAP);
        int nextPostFee = configService.getInt(
                swapLimit < 0 || swapActive < swapLimit ? ConfigKey.POST_FEE : ConfigKey.POST_FEE_OVER_QUOTA);
        return new PostingQuotaResponse(
                new PostingQuotaResponse.Quota(countActive(userId, OfferType.GIVE), giveLimit),
                new PostingQuotaResponse.Quota(swapActive, swapLimit),
                reputationService.getRank(userId).name(),
                nextPostFee);
    }

    private void replaceMediaMappings(long itemId, long userId, List<Long> photos, List<Long> defects) {
        List<Long> oldMediaIds = jdbcClient
                .sql("SELECT media_id FROM item_media_mappings WHERE item_id = :itemId AND role IN ('PHOTO', 'DEFECT')")
                .param("itemId", itemId)
                .query(Long.class)
                .list();
        List<Long> newMediaIds = combineMediaIds(photos, defects);
        List<Long> added =
                newMediaIds.stream().filter(id -> !oldMediaIds.contains(id)).toList();
        List<Long> removed =
                oldMediaIds.stream().filter(id -> !newMediaIds.contains(id)).toList();
        mediaService.attach(added, userId);
        jdbcClient
                .sql("DELETE FROM item_media_mappings WHERE item_id = :itemId AND role IN ('PHOTO', 'DEFECT')")
                .param("itemId", itemId)
                .update();
        saveMediaMappings(itemId, photos, "PHOTO");
        saveMediaMappings(itemId, defects, "DEFECT");
        mediaService.detach(removed, userId);
    }

    private List<Long> combineMediaIds(List<Long> photos, List<Long> defects) {
        List<Long> combined = java.util.stream.Stream.concat(photos.stream(), defects.stream())
                .toList();
        if (new HashSet<>(combined).size() != combined.size()) {
            throw new BusinessException(MediaErrorCode.MEDIA_INVALID_FILE);
        }
        return combined;
    }

    private void validateDefectImages(ItemCondition condition, List<Long> defectMediaIds) {
        if (condition == ItemCondition.DEFECTIVE && defectMediaIds.isEmpty()) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_DEFECT_IMAGES_REQUIRED,
                    ItemErrorCode.ITEM_DEFECT_IMAGES_REQUIRED.defaultMessage(),
                    "defectImages",
                    null);
        }
        if (condition != ItemCondition.DEFECTIVE && !defectMediaIds.isEmpty()) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_DEFECT_IMAGES_NOT_ALLOWED,
                    ItemErrorCode.ITEM_DEFECT_IMAGES_NOT_ALLOWED.defaultMessage(),
                    "defectImages",
                    null);
        }
    }

    private Optional<Long> requireOperatingStatus(Long categoryId, OperatingStatus operatingStatus) {
        Optional<Long> attributeId = categoryAttributeService.operatingStatusAttributeId(categoryId);
        if (attributeId.isPresent() && operatingStatus == null) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_OPERATING_STATUS_REQUIRED,
                    ItemErrorCode.ITEM_OPERATING_STATUS_REQUIRED.defaultMessage(),
                    "operatingStatus",
                    null);
        }
        if (attributeId.isEmpty() && operatingStatus != null) {
            throw new BusinessException(
                    ItemErrorCode.ITEM_OPERATING_STATUS_NOT_ALLOWED,
                    ItemErrorCode.ITEM_OPERATING_STATUS_NOT_ALLOWED.defaultMessage(),
                    "operatingStatus",
                    null);
        }
        return attributeId;
    }

    private void saveOperatingStatus(long itemId, long attributeId, OperatingStatus status) {
        jdbcClient
                .sql("INSERT INTO item_attribute_mappings (item_id, attribute_id, value) "
                        + "VALUES (:itemId, :attributeId, :value)")
                .param("itemId", itemId)
                .param("attributeId", attributeId)
                .param("value", status.name())
                .update();
    }

    private void replaceOperatingStatus(
            long itemId, long previousCategoryId, Optional<Long> newAttributeId, OperatingStatus status) {
        categoryAttributeService
                .operatingStatusAttributeId(previousCategoryId)
                .ifPresent(id -> jdbcClient
                        .sql(
                                "DELETE FROM item_attribute_mappings WHERE item_id = :itemId AND attribute_id = :attributeId")
                        .param("itemId", itemId)
                        .param("attributeId", id)
                        .update());
        newAttributeId.ifPresent(id -> saveOperatingStatus(itemId, id, status));
    }

    private List<String> mediaUrls(List<Long> mediaIds) {
        return mediaIds.stream().map(id -> "/api/v1/media/" + id).toList();
    }

    // Lưu cả từ khoá đã khớp để Admin ở hàng chờ duyệt thấy ngay vì sao bài bị gắn cờ.
    private void saveReviewFlag(long itemId, ModerationResult moderation) {
        jdbcClient
                .sql("UPDATE items SET ai_result = jsonb_build_object('flag', 'REVIEW', 'reason', :reason, "
                        + "'matchedTerms', CAST(:matchedTerms AS jsonb)) WHERE id = :itemId")
                .param("reason", moderation.reasonCode())
                .param("matchedTerms", objectMapper.writeValueAsString(moderation.matchedTerms()))
                .param("itemId", itemId)
                .update();
    }

    private void saveMediaMappings(Long itemId, List<Long> mediaIds, String role) {
        short order = 0;
        for (Long mediaId : mediaIds) {
            jdbcClient
                    .sql("INSERT INTO item_media_mappings (item_id, media_id, role, sort_order) "
                            + "VALUES (:itemId, :mediaId, :role, :sortOrder)")
                    .param("itemId", itemId)
                    .param("mediaId", mediaId)
                    .param("role", role)
                    .param("sortOrder", order++)
                    .update();
        }
    }

    private List<String> findImages(Long itemId) {
        return findImages(itemId, "PHOTO");
    }

    private List<String> findImages(Long itemId, String role) {
        if (jdbcClient == null || itemId == null) {
            return List.of();
        }
        return jdbcClient
                .sql("SELECT '/api/v1/media/' || m.id FROM item_media_mappings imm "
                        + "JOIN media_files m ON imm.media_id = m.id "
                        + "WHERE imm.item_id = :itemId AND imm.role = :role "
                        + "AND m.is_active AND NOT m.is_deleted ORDER BY imm.sort_order")
                .param("itemId", itemId)
                .param("role", role)
                .query(String.class)
                .list();
    }

    private List<Long> findMediaIds(long itemId, String role) {
        return jdbcClient
                .sql(
                        "SELECT media_id FROM item_media_mappings WHERE item_id = :itemId AND role = :role ORDER BY sort_order")
                .param("itemId", itemId)
                .param("role", role)
                .query(Long.class)
                .list();
    }

    private List<ItemAttributeValue> findAttributeValues(long itemId) {
        if (jdbcClient == null) {
            return List.of();
        }
        return jdbcClient
                .sql("SELECT attribute_id, value FROM item_attribute_mappings "
                        + "WHERE item_id = :itemId ORDER BY attribute_id")
                .param("itemId", itemId)
                .query((resultSet, rowNumber) ->
                        new ItemAttributeValue(resultSet.getLong("attribute_id"), resultSet.getString("value")))
                .list();
    }

    private OperatingStatus findOperatingStatus(long itemId, long categoryId) {
        return categoryAttributeService
                .operatingStatusAttributeId(categoryId)
                .flatMap(id -> jdbcClient
                        .sql("SELECT value FROM item_attribute_mappings "
                                + "WHERE item_id = :itemId AND attribute_id = :attributeId")
                        .param("itemId", itemId)
                        .param("attributeId", id)
                        .query(String.class)
                        .optional())
                .map(OperatingStatus::valueOf)
                .orElse(null);
    }

    private ItemResponse toResponse(Item item, DisplayWindow displayWindow) {
        return ItemMapper.toResponse(
                item,
                findImages(item.getId()),
                findImages(item.getId(), "DEFECT"),
                findOperatingStatus(item.getId(), item.getCategoryId()),
                displayWindow);
    }

    // Đọc một trang bằng một query hạn hiển thị cho cả trang, thay vì một query mỗi bài. Chỉ tìm kiếm AI dùng (kết quả
    // công khai) nên bỏ các mốc đợt đẩy.
    private PageResponse<ItemResponse> toPageResponse(Page<Item> items) {
        Map<Long, DisplayWindow> displayWindows = findDisplayWindows(items.getContent());
        return PageResponse.from(items.map(
                item -> toResponse(item, displayWindows.get(item.getId())).withoutBoostTimeline()));
    }

    // expire_at, boosted_until thuộc module listingfee nên đọc qua ListingFeeQueryService, không map lại ở Item.
    private Map<Long, DisplayWindow> findDisplayWindows(List<Item> items) {
        List<Long> itemIds = items.stream().map(Item::getId).toList();
        return listingFeeQueryService.findDisplayWindows(itemIds);
    }

    private DisplayWindow findDisplayWindow(long itemId) {
        return listingFeeQueryService.findDisplayWindows(List.of(itemId)).get(itemId);
    }
}
