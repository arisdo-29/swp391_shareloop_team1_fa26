package com.shareloop.item;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Danh sách email tài khoản test được bỏ qua giới hạn đăng bài (chỉ cấu hình ở dev và staging).
 * Spring tự đọc key shareloop.post-limit-bypass-emails trong file yml hoặc biến môi trường
 * SHARELOOP_POSTLIMITBYPASSEMAILS (nhiều email cách nhau bằng dấu phẩy).
 */
@Component // đăng ký bean để Spring bind cấu hình, không cần sửa ShareLoopApplication
@ConfigurationProperties(prefix = "shareloop")
public class PostLimitProperties {

    private List<String> postLimitBypassEmails = new ArrayList<>();

    public List<String> getPostLimitBypassEmails() {
        return postLimitBypassEmails;
    }

    public void setPostLimitBypassEmails(List<String> postLimitBypassEmails) {
        this.postLimitBypassEmails = postLimitBypassEmails;
    }

    /**
     * Trả true nếu email nằm trong danh sách bỏ qua giới hạn. So sánh không phân biệt hoa thường; email null thì false.
     */
    public boolean canBypassPostLimit(String email) {
        if (email == null) {
            return false;
        }
        for (String bypassEmail : postLimitBypassEmails) {
            if (bypassEmail.trim().equalsIgnoreCase(email.trim())) {
                return true;
            }
        }
        return false;
    }
}
