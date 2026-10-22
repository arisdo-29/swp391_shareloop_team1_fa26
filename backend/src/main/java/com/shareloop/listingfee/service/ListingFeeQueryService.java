package com.shareloop.listingfee.service;

import com.shareloop.listingfee.dto.DisplayWindow;
import com.shareloop.listingfee.dto.HeldFeeItem;
import com.shareloop.listingfee.dto.ListingFeeConfigResponse;
import com.shareloop.listingfee.repository.HeldFeeRow;
import com.shareloop.listingfee.repository.ItemDisplayWindowRow;
import com.shareloop.listingfee.repository.ItemFeeRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Các truy vấn chỉ-đọc của module listingfee cho module khác và cho FE. Cố ý chỉ phụ thuộc ItemFeeRepository và
 * ConfigService, không phụ thuộc ItemService: ItemService gọi service này, nên nếu service này gọi ngược lại
 * ItemService thì Spring sẽ báo vòng phụ thuộc.
 */
@Service
// readOnly = true: transaction chỉ đọc, Hibernate bỏ qua bước kiểm tra thay đổi (dirty checking) khi kết thúc.
@Transactional(readOnly = true)
public class ListingFeeQueryService {

    // Bài GIVE được đăng miễn phí: không có khoá cấu hình vì đây là quy tắc cố định, Admin không đổi được.
    private static final int GIVE_POST_FEE = 0;

    private final ItemFeeRepository itemFeeRepository;
    private final ConfigService configService;

    public ListingFeeQueryService(ItemFeeRepository itemFeeRepository, ConfigService configService) {
        this.itemFeeRepository = itemFeeRepository;
        this.configService = configService;
    }

    /**
     * Đọc hạn hiển thị (expire_at) và hạn đẩy (boosted_until) của nhiều bài bằng một query. Danh sách rỗng thì trả
     * map rỗng, không query. Bài không tìm thấy thì không có trong map.
     */
    public Map<Long, DisplayWindow> findDisplayWindows(Collection<Long> itemIds) {
        Map<Long, DisplayWindow> displayWindows = new HashMap<>();
        if (itemIds == null || itemIds.isEmpty()) {
            return displayWindows;
        }
        for (ItemDisplayWindowRow row : itemFeeRepository.findDisplayWindowRows(itemIds)) {
            displayWindows.put(row.getId(), new DisplayWindow(row.getExpireAt(), row.getBoostedUntil()));
        }
        return displayWindows;
    }

    /**
     * Danh sách bài đang giữ phí của một người (FR-15): bài SWAP chờ duyệt giữ phí đăng, bài đã duyệt chờ duyệt bản
     * sửa giữ phí sửa. Bài GIVE (giữ 0), bài đã duyệt xong hoặc bị từ chối không có trong danh sách.
     */
    public List<HeldFeeItem> findHeldFees(long userId) {
        List<HeldFeeItem> heldFees = new ArrayList<>();
        for (HeldFeeRow row : itemFeeRepository.findHeldFeeRows(userId)) {
            heldFees.add(new HeldFeeItem(
                    row.getId(),
                    row.getTitle(),
                    row.getOfferType(),
                    row.getStatus(),
                    row.getPendingFee(),
                    row.getPendingFeeType()));
        }
        return heldFees;
    }

    /**
     * Bảng phí và thời hạn hiện hành; mọi con số đọc từ ConfigService nên Admin đổi cấu hình là thấy ngay. Riêng phí
     * đăng bài GIVE luôn 0 (chỉ bài SWAP mất phí đăng).
     */
    public ListingFeeConfigResponse getFeeConfig() {
        return new ListingFeeConfigResponse(
                configService.getInt(ConfigKey.POST_FEE),
                GIVE_POST_FEE,
                configService.getInt(ConfigKey.EDIT_FEE),
                configService.getInt(ConfigKey.RENEW_FEE),
                configService.getInt(ConfigKey.RENEW_EXPIRED_WINDOW_DAYS),
                configService.getInt(ConfigKey.BOOST_FEE),
                configService.getInt(ConfigKey.BOOST_DAYS),
                configService.getInt(ConfigKey.BOOST_MAX_DAYS),
                configService.getInt(ConfigKey.POST_DISPLAY_DAYS),
                configService.getInt(ConfigKey.POST_DISPLAY_DAYS_GOLD));
    }
}
