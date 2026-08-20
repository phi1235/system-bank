package com.banksystem.common.iso20022;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JacksonXmlRootElement(localName = "Document", namespace = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.11")
@JsonIgnoreProperties(ignoreUnknown = true)
public record Pain001Dto(
    @JsonProperty("groupHeader")
    @JacksonXmlProperty(localName = "GrpHdr")
    GroupHeader groupHeader,

    @JsonProperty("paymentInformation")
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "PmtInf")
    List<PaymentInformation> paymentInformation) {

  public record GroupHeader(
      @JsonProperty("messageIdentification")
      @JacksonXmlProperty(localName = "MsgId")
      String messageIdentification,

      @JsonProperty("creationDateTime")
      @JacksonXmlProperty(localName = "CreDtTm")
      String creationDateTime,

      @JsonProperty("numberOfTransactions")
      @JacksonXmlProperty(localName = "NbOfTxs")
      Integer numberOfTransactions,

      @JsonProperty("controlSum")
      @JacksonXmlProperty(localName = "CtrlSum")
      BigDecimal controlSum,

      @JsonProperty("initiatingParty")
      @JacksonXmlProperty(localName = "InitgPty")
      PartyIdentification initiatingParty) {}

  public record PartyIdentification(
      @JsonProperty("name")
      @JacksonXmlProperty(localName = "Nm")
      String name,

      @JsonProperty("identification")
      @JacksonXmlProperty(localName = "Id")
      String identification) {}

  public record PaymentInformation(
      @JsonProperty("paymentInformationIdentification")
      @JacksonXmlProperty(localName = "PmtInfId")
      String paymentInformationIdentification,

      @JsonProperty("paymentMethod")
      @JacksonXmlProperty(localName = "PmtMtd")
      String paymentMethod,

      @JsonProperty("requestedExecutionDate")
      @JacksonXmlProperty(localName = "ReqdExctnDt")
      String requestedExecutionDate,

      @JsonProperty("debtor")
      @JacksonXmlProperty(localName = "Dbtr")
      PartyIdentification debtor,

      @JsonProperty("debtorAccount")
      @JacksonXmlProperty(localName = "DbtrAcct")
      AccountIdentification debtorAccount,

      @JsonProperty("debtorAgent")
      @JacksonXmlProperty(localName = "DbtrAgt")
      AgentIdentification debtorAgent,

      @JsonProperty("creditTransferTransactionInformation")
      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "CdtTrfTxInf")
      List<CreditTransferTransactionInformation> creditTransferTransactionInformation) {}

  public record AccountIdentification(
      @JsonProperty("accountNumber")
      @JacksonXmlProperty(localName = "Id")
      String accountNumber,

      @JsonProperty("currency")
      @JacksonXmlProperty(localName = "Ccy")
      String currency) {}

  public record AgentIdentification(
      @JsonProperty("bic")
      @JacksonXmlProperty(localName = "BIC")
      String bic,

      @JsonProperty("bankCode")
      @JacksonXmlProperty(localName = "BankCd")
      String bankCode) {}

  public record CreditTransferTransactionInformation(
      @JsonProperty("paymentIdentification")
      @JacksonXmlProperty(localName = "PmtId")
      PaymentIdentification paymentIdentification,

      @JsonProperty("amount")
      @JacksonXmlProperty(localName = "Amt")
      Amount amount,

      @JsonProperty("creditor")
      @JacksonXmlProperty(localName = "Cdtr")
      PartyIdentification creditor,

      @JsonProperty("creditorAccount")
      @JacksonXmlProperty(localName = "CdtrAcct")
      AccountIdentification creditorAccount,

      @JsonProperty("creditorAgent")
      @JacksonXmlProperty(localName = "CdtrAgt")
      AgentIdentification creditorAgent,

      @JsonProperty("remittanceInformation")
      @JacksonXmlProperty(localName = "RmtInf")
      RemittanceInformation remittanceInformation) {}

  public record PaymentIdentification(
      @JsonProperty("instructionIdentification")
      @JacksonXmlProperty(localName = "InstrId")
      String instructionIdentification,

      @JsonProperty("endToEndIdentification")
      @JacksonXmlProperty(localName = "EndToEndId")
      String endToEndIdentification) {}

  public record Amount(
      @JsonProperty("currency")
      @JacksonXmlProperty(isAttribute = true, localName = "Ccy")
      String currency,

      @JsonProperty("value")
      @JacksonXmlProperty(localName = "InstdAmt")
      BigDecimal value) {}

  public record RemittanceInformation(
      @JsonProperty("unstructured")
      @JacksonXmlProperty(localName = "Ustrd")
      String unstructured) {}
}
