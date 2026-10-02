package com.ruinhome.billing;

import com.ruinhome.auth.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class FeeConfigService {

    private final FeeTypeRepository feeTypeRepository;
    private final FeeRateRepository feeRateRepository;
    private final CurrentUserService currentUserService;

    public FeeConfigService(FeeTypeRepository feeTypeRepository,
                            FeeRateRepository feeRateRepository,
                            CurrentUserService currentUserService) {
        this.feeTypeRepository = feeTypeRepository;
        this.feeRateRepository = feeRateRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<BillingDtos.FeeTypeResponse> listFeeTypes() {
        return feeTypeRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(type -> new BillingDtos.FeeTypeResponse(
                        type.getId(), type.getCode(), type.getName(), type.getUnit(), type.isActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BillingDtos.FeeRateResponse> listFeeRates(Long feeTypeId) {
        return feeRateRepository.search(feeTypeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BillingDtos.FeeRateResponse upsertFeeRate(BillingDtos.FeeRateUpsertRequest request) {
        if (!currentUserService.isRoot()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ quản trị viên gốc mới được cấu hình giá phí");
        }
        BillingSupport.validatePeriod(request.period());
        FeeType feeType = feeTypeRepository.findById(request.feeTypeId())
                .filter(FeeType::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy loại phí"));
        FeeRate rate = feeRateRepository.findByFeeTypeIdAndPeriod(feeType.getId(), request.period())
                .orElseGet(FeeRate::new);
        rate.setFeeType(feeType);
        rate.setPeriod(request.period());
        rate.setPrice(request.price());
        return toResponse(feeRateRepository.save(rate));
    }

    private BillingDtos.FeeRateResponse toResponse(FeeRate rate) {
        return new BillingDtos.FeeRateResponse(
                rate.getId(),
                rate.getFeeType().getId(),
                rate.getFeeType().getCode(),
                rate.getFeeType().getName(),
                rate.getFeeType().getUnit(),
                rate.getPeriod(),
                rate.getPrice());
    }
}
