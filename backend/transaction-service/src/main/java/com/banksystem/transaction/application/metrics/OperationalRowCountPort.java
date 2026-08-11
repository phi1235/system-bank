package com.banksystem.transaction.application.metrics;

public interface OperationalRowCountPort {

  long transferOrders();

  long auditLogs();
}
