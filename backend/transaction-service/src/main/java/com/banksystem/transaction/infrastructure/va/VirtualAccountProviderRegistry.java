package com.banksystem.transaction.infrastructure.va;

import com.banksystem.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class VirtualAccountProviderRegistry {

  private final Map<String, VirtualAccountProvider> providers = new ConcurrentHashMap<>();

  public VirtualAccountProviderRegistry(List<VirtualAccountProvider> providerList) {
    for (VirtualAccountProvider p : providerList) {
      providers.put(p.getProviderCode().toUpperCase(), p);
    }
  }

  public VirtualAccountProvider getProvider(String providerCode) {
    if (providerCode == null) {
      providerCode = "MOCK";
    }
    VirtualAccountProvider provider = providers.get(providerCode.toUpperCase());
    if (provider == null) {
      throw new BusinessException("UNSUPPORTED_VA_PROVIDER", "Unsupported virtual account provider: " + providerCode);
    }
    return provider;
  }
}
