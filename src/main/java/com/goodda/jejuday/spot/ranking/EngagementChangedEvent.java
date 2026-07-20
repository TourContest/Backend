package com.goodda.jejuday.spot.ranking;

/** 좋아요/댓글이 추가·삭제되어 스팟의 인게이지먼트가 바뀌었음을 알리는 이벤트. */
public record EngagementChangedEvent(Long spotId) {
}
