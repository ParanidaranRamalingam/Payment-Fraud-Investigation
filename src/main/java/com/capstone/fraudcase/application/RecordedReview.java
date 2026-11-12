package com.capstone.fraudcase.application;

import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.ReviewDisposition;

public record RecordedReview(String reviewId, ReviewDisposition disposition, CaseStatus resultingStatus) {
}
