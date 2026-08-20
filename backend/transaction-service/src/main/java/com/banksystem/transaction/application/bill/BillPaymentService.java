package com.banksystem.transaction.application.bill;

import java.time.Instant;

import com.banksystem.common.api.PageResponse;
import com.banksystem.transaction.api.dto.BillDtos.BillCategoryResponse;
import com.banksystem.transaction.api.dto.BillDtos.BillInquiryRequest;
import com.banksystem.transaction.api.dto.BillDtos.BillInquiryResponse;
import com.banksystem.transaction.api.dto.BillDtos.BillPayRequest;
import com.banksystem.transaction.api.dto.BillDtos.BillPayResponse;
import com.banksystem.transaction.api.dto.BillDtos.BillPaymentHistoryResponse;
import com.banksystem.transaction.api.dto.BillDtos.BillProviderResponse;
import com.banksystem.transaction.domain.bill.BillCategoryRepository;
import com.banksystem.transaction.domain.bill.BillCustomerEntity;
import com.banksystem.transaction.domain.bill.BillCustomerRepository;
import com.banksystem.transaction.domain.bill.BillPaymentEntity;
import com.banksystem.transaction.domain.bill.BillPaymentRepository;
import com.banksystem.transaction.domain.bill.BillProviderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillPaymentService {

  private final BillCategoryRepository categoryRepo;
  private final BillProviderRepository providerRepo;
  private final BillCustomerRepository customerRepo;
  private final BillPaymentRepository paymentRepo;

  public BillPaymentService(
      BillCategoryRepository categoryRepo,
      BillProviderRepository providerRepo,
      BillCustomerRepository customerRepo,
      BillPaymentRepository paymentRepo) {
    this.categoryRepo = categoryRepo;
    this.providerRepo = providerRepo;
    this.customerRepo = customerRepo;
    this.paymentRepo = paymentRepo;
  }

  /** List all active bill categories sorted by display order. */
  public List<BillCategoryResponse> listCategories() {
    return categoryRepo.findAllByActiveTrueOrderByDisplayOrder().stream()
        .map(e -> new BillCategoryResponse(
            e.getId(), e.getName(), e.getIconUrl(),
            e.getIcon(), e.getSampleCode(), e.getThemeClass(),
            e.getDisplayOrder()))
        .toList();
  }

  /** List providers, optionally filtered by categoryId. */
  public List<BillProviderResponse> listProviders(String categoryId) {
    var entities = (categoryId != null && !categoryId.isBlank())
        ? providerRepo.findAllByCategoryIdAndActiveTrue(categoryId)
        : providerRepo.findAllByActiveTrue();
    return entities.stream()
        .map(e -> new BillProviderResponse(e.getId(), e.getCategoryId(), e.getName(), e.getCode()))
        .toList();
  }

  /**
   * Look up bill customer info from the database.
   * Queries bill_customers table by provider_id + customer_code.
   */
  public BillInquiryResponse inquireBill(BillInquiryRequest req) {
    BillCustomerEntity customer = customerRepo
        .findByProviderIdAndCustomerCode(req.providerId(), req.customerCode())
        .orElseThrow(() -> new IllegalArgumentException(
            "Không tìm thấy hóa đơn cho mã khách hàng: " + req.customerCode()));

    return new BillInquiryResponse(
        customer.getCustomerName(),
        customer.getAmount(),
        customer.getPeriod(),
        customer.getProviderId(),
        customer.getCustomerCode()
    );
  }

  /**
   * Execute bill payment:
   * 1. Look up customer from DB to get real name + amount
   * 2. Persist BillPaymentEntity with status COMPLETED
   * 3. Mark bill_customers record as PAID
   */
  @Transactional
  public BillPayResponse payBill(UUID customerId, BillPayRequest req) {
    // Validate provider exists
    var provider = providerRepo.findById(req.providerId())
        .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + req.providerId()));

    // Look up customer from DB for real customer name
    BillCustomerEntity billCustomer = customerRepo
        .findByProviderIdAndCustomerCode(req.providerId(), req.customerCode())
        .orElse(null);

    String customerName = billCustomer != null ? billCustomer.getCustomerName() : req.customerCode();

    // Create payment record
    var payment = new BillPaymentEntity();
    payment.setId(UUID.randomUUID());
    payment.setCustomerId(customerId);
    payment.setCategoryId(provider.getCategoryId());
    payment.setProviderId(req.providerId());
    payment.setCustomerCode(req.customerCode());
    payment.setCustomerName(customerName);
    payment.setAmount(req.amount());
    payment.setFee(BigDecimal.ZERO);
    payment.setStatus("COMPLETED");
    payment.setTransactionRef("BP-" + Instant.now().toEpochMilli());
    payment.setCreatedAt(Instant.now());

    paymentRepo.save(payment);

    // Mark bill_customers as PAID
    if (billCustomer != null) {
      billCustomer.setStatus("PAID");
      customerRepo.save(billCustomer);
    }

    return new BillPayResponse(
        payment.getId(),
        payment.getStatus(),
        payment.getTransactionRef(),
        payment.getAmount(),
        payment.getFee(),
        payment.getCreatedAt()
    );
  }

  /** Bill payment history for current customer. */
  public PageResponse<BillPaymentHistoryResponse> history(UUID customerId, Integer page, Integer size) {
    int pg = page != null ? page : 0;
    int sz = size != null ? Math.min(size, 50) : 20;
    var result = paymentRepo.findAllByCustomerIdOrderByCreatedAtDesc(
        customerId, PageRequest.of(pg, sz));
    var items = result.getContent().stream()
        .map(e -> new BillPaymentHistoryResponse(
            e.getId(), e.getCategoryId(), e.getProviderId(),
            e.getCustomerCode(), e.getCustomerName(),
            e.getAmount(), e.getFee(), e.getStatus(),
            e.getTransactionRef(), e.getCreatedAt()))
        .toList();
    return new PageResponse<>(items, pg, sz, result.getTotalElements(), result.getTotalPages());
  }
}
