package com.banksystem.common.iso20022;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.math.BigDecimal;
import java.util.List;

@JacksonXmlRootElement(localName = "Document", namespace = "urn:iso:std:iso:20022:tech:xsd:camt.053.001.10")
@JsonIgnoreProperties(ignoreUnknown = true)
public record Camt053Dto(
    @JsonProperty("groupHeader")
    @JacksonXmlProperty(localName = "GrpHdr")
    GroupHeader groupHeader,

    @JsonProperty("statement")
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Stmt")
    List<Statement> statement) {

  public record GroupHeader(
      @JsonProperty("messageIdentification")
      @JacksonXmlProperty(localName = "MsgId")
      String messageIdentification,

      @JsonProperty("creationDateTime")
      @JacksonXmlProperty(localName = "CreDtTm")
      String creationDateTime) {}

  public record Statement(
      @JsonProperty("statementIdentification")
      @JacksonXmlProperty(localName = "Id")
      String statementIdentification,

      @JsonProperty("electronicSequenceNumber")
      @JacksonXmlProperty(localName = "ElctrncSeqNb")
      Long electronicSequenceNumber,

      @JsonProperty("account")
      @JacksonXmlProperty(localName = "Acct")
      Account account,

      @JsonProperty("balance")
      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "Bal")
      List<Balance> balance,

      @JsonProperty("entry")
      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "Ntry")
      List<Entry> entry) {}

  public record Account(
      @JsonProperty("accountNumber")
      @JacksonXmlProperty(localName = "Id")
      String accountNumber,

      @JsonProperty("currency")
      @JacksonXmlProperty(localName = "Ccy")
      String currency,

      @JsonProperty("ownerName")
      @JacksonXmlProperty(localName = "OwnrNm")
      String ownerName) {}

  public record Balance(
      @JsonProperty("type")
      @JacksonXmlProperty(localName = "Tp")
      String type, // OPBD (Opening), CLBD (Closing), PRCD (Previously Closed)

      @JsonProperty("amount")
      @JacksonXmlProperty(localName = "Amt")
      Amount amount,

      @JsonProperty("creditDebitIndicator")
      @JacksonXmlProperty(localName = "CdtDbtInd")
      String creditDebitIndicator, // CRDT, DBIT

      @JsonProperty("date")
      @JacksonXmlProperty(localName = "Dt")
      String date) {}

  public record Amount(
      @JsonProperty("currency")
      @JacksonXmlProperty(isAttribute = true, localName = "Ccy")
      String currency,

      @JsonProperty("value")
      @JacksonXmlProperty(localName = "Val")
      BigDecimal value) {}

  public record Entry(
      @JsonProperty("entryReference")
      @JacksonXmlProperty(localName = "NtryRef")
      String entryReference,

      @JsonProperty("amount")
      @JacksonXmlProperty(localName = "Amt")
      Amount amount,

      @JsonProperty("creditDebitIndicator")
      @JacksonXmlProperty(localName = "CdtDbtInd")
      String creditDebitIndicator, // CRDT, DBIT

      @JsonProperty("status")
      @JacksonXmlProperty(localName = "Sts")
      String status, // BOOK, INFO

      @JsonProperty("bookingDate")
      @JacksonXmlProperty(localName = "BookgDt")
      String bookingDate,

      @JsonProperty("bankTransactionCode")
      @JacksonXmlProperty(localName = "BkTxCd")
      String bankTransactionCode,

      @JsonProperty("entryDetails")
      @JacksonXmlProperty(localName = "NtryDtls")
      EntryDetails entryDetails) {}

  public record EntryDetails(
      @JsonProperty("transactionDetails")
      @JacksonXmlProperty(localName = "TxDtls")
      TransactionDetails transactionDetails) {}

  public record TransactionDetails(
      @JsonProperty("endToEndIdentification")
      @JacksonXmlProperty(localName = "EndToEndId")
      String endToEndIdentification,

      @JsonProperty("creditor")
      @JacksonXmlProperty(localName = "Cdtr")
      PartyIdentification creditor,

      @JsonProperty("debtor")
      @JacksonXmlProperty(localName = "Dbtr")
      PartyIdentification debtor,

      @JsonProperty("remittanceInformation")
      @JacksonXmlProperty(localName = "RmtInf")
      RemittanceInformation remittanceInformation) {}

  public record PartyIdentification(
      @JsonProperty("name")
      @JacksonXmlProperty(localName = "Nm")
      String name) {}

  public record RemittanceInformation(
      @JsonProperty("unstructured")
      @JacksonXmlProperty(localName = "Ustrd")
      String unstructured) {}
}
