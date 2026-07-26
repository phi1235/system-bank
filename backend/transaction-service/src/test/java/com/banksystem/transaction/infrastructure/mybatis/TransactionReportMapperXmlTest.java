package com.banksystem.transaction.infrastructure.mybatis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/**
 * Parses the mapper XML without a database. Catches what the compiler cannot: malformed XML,
 * unresolvable javaType/constructor mappings, broken include refids, and statement ids that
 * drift from {@link TransactionReportMapper} method names.
 */
class TransactionReportMapperXmlTest {

  private static final String RESOURCE = "mybatis/TransactionReportMapper.xml";

  @Test
  void xmlParsesAndCoversEveryMapperMethod() throws Exception {
    Configuration configuration = new Configuration();
    try (InputStream in = Resources.getResourceAsStream(RESOURCE)) {
      new XMLMapperBuilder(in, configuration, RESOURCE, configuration.getSqlFragments()).parse();
    }

    String namespace = TransactionReportMapper.class.getName();
    Arrays.stream(TransactionReportMapper.class.getDeclaredMethods())
        .forEach(
            method ->
                assertTrue(
                    configuration.hasStatement(namespace + "." + method.getName()),
                    "Missing mapped statement for " + method.getName()));
  }
}
