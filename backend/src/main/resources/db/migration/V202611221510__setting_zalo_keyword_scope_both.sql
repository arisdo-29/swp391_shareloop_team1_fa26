-- Từ khoá "zalo" đã có ở mức REVIEW cho bài đăng, nay áp cả cho chat trước khi chốt lịch (D3).
UPDATE website_attributes
SET extra = jsonb_set(extra, '{scope}', '"BOTH"')
WHERE attr_group = 'BANNED_KEYWORD' AND attr_key = 'zalo' AND NOT is_deleted;
