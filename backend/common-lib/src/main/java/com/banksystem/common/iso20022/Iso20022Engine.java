package com.banksystem.common.iso20022;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;

public final class Iso20022Engine {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .enable(SerializationFeature.INDENT_OUTPUT);

  private static final XmlMapper XML_MAPPER = (XmlMapper) new XmlMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .enable(SerializationFeature.INDENT_OUTPUT);

  private Iso20022Engine() {}

  public static Pain001Dto parsePain001(String payload, boolean isXml) throws IOException {
    if (isXml) {
      return XML_MAPPER.readValue(payload, Pain001Dto.class);
    }
    return JSON_MAPPER.readValue(payload, Pain001Dto.class);
  }

  public static String toXml(Object isoDto) throws IOException {
    return XML_MAPPER.writeValueAsString(isoDto);
  }

  public static String toJson(Object isoDto) throws IOException {
    return JSON_MAPPER.writeValueAsString(isoDto);
  }

  public static ObjectMapper jsonMapper() {
    return JSON_MAPPER;
  }

  public static XmlMapper xmlMapper() {
    return XML_MAPPER;
  }
}
