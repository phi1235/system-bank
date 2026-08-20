package com.banksystem.common.iso20022;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.List;

@JacksonXmlRootElement(localName = "Document", namespace = "urn:iso:std:iso:20022:tech:xsd:pain.002.001.12")
@JsonIgnoreProperties(ignoreUnknown = true)
public record Pain002Dto(
    @JsonProperty("groupHeader")
    @JacksonXmlProperty(localName = "GrpHdr")
    GroupHeader groupHeader,

    @JsonProperty("originalPaymentInformationAndStatus")
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "OrgnlPmtInfAndSts")
    List<OriginalPaymentInformationAndStatus> originalPaymentInformationAndStatus) {

  public record GroupHeader(
      @JsonProperty("messageIdentification")
      @JacksonXmlProperty(localName = "MsgId")
      String messageIdentification,

      @JsonProperty("creationDateTime")
      @JacksonXmlProperty(localName = "CreDtTm")
      String creationDateTime,

      @JsonProperty("originalMessageIdentification")
      @JacksonXmlProperty(localName = "OrgnlMsgId")
      String originalMessageIdentification) {}

  public record OriginalPaymentInformationAndStatus(
      @JsonProperty("originalPaymentInformationIdentification")
      @JacksonXmlProperty(localName = "OrgnlPmtInfId")
      String originalPaymentInformationIdentification,

      @JsonProperty("paymentInformationStatus")
      @JacksonXmlProperty(localName = "PmtInfSts")
      String paymentInformationStatus,

      @JsonProperty("transactionInformationAndStatus")
      @JacksonXmlElementWrapper(useWrapping = false)
      @JacksonXmlProperty(localName = "TxInfAndSts")
      List<TransactionInformationAndStatus> transactionInformationAndStatus) {}

  public record TransactionInformationAndStatus(
      @JsonProperty("statusIdentification")
      @JacksonXmlProperty(localName = "StsId")
      String statusIdentification,

      @JsonProperty("originalInstructionIdentification")
      @JacksonXmlProperty(localName = "OrgnlInstrId")
      String originalInstructionIdentification,

      @JsonProperty("originalEndToEndIdentification")
      @JacksonXmlProperty(localName = "OrgnlEndToEndId")
      String originalEndToEndIdentification,

      @JsonProperty("transactionStatus")
      @JacksonXmlProperty(localName = "TxSts")
      String transactionStatus, // ACCP, ACSP, ACSC, RJCT

      @JsonProperty("statusReasonInformation")
      @JacksonXmlProperty(localName = "StsRsnInf")
      StatusReasonInformation statusReasonInformation,

      @JsonProperty("effectiveDate")
      @JacksonXmlProperty(localName = "FctvDt")
      String effectiveDate,

      @JsonProperty("clearingSystemReference")
      @JacksonXmlProperty(localName = "ClrSysRef")
      String clearingSystemReference) {}

  public record StatusReasonInformation(
      @JsonProperty("reason")
      @JacksonXmlProperty(localName = "Rsn")
      Reason reason,

      @JsonProperty("additionalInformation")
      @JacksonXmlProperty(localName = "AddtlInf")
      String additionalInformation) {}

  public record Reason(
      @JsonProperty("code")
      @JacksonXmlProperty(localName = "Cd")
      String code) {}
}
