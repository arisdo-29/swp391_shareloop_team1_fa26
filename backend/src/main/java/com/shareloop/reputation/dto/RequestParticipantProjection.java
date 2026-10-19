package com.shareloop.reputation.dto;

/**
 * Interface projection hứng thông tin giao dịch cần thiết từ requests và items để xử lý uy tín (Lộ trình 4.7, FR-97).
 */
public interface RequestParticipantProjection {

    Long getDonorId();

    Long getReceiverId();

    String getType();

    String getStatus();
}
