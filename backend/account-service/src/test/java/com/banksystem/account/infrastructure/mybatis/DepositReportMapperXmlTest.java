package com.banksystem.account.infrastructure.mybatis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/**
 * Parses the mapper XML without a database: malformed XML, unresolvable constructor mappings and
 * statement ids drifting from {@link DepositReportMapper} method names fail here, not at runtime.
 */
class DepositReportMapperXmlTest {

  private static final String RESOURCE = "mybatis/DepositReportMapper.xml";

  @Test
  void xmlParsesAndCoversEveryMapperMethod() throws Exception {
    Configuration configuration = new Configuration();
    try (InputStream in = Resources.getResourceAsStream(RESOURCE)) {
      new XMLMapperBuilder(in, configuration, RESOURCE, configuration.getSqlFragments()).parse();
    }

    String namespace = DepositReportMapper.class.getName();
    Arrays.stream(DepositReportMapper.class.getDeclaredMethods())
        .forEach(
            method ->
                assertTrue(
                    configuration.hasStatement(namespace + "." + method.getName()),
                    "Missing mapped statement for " + method.getName()));
  }
}
