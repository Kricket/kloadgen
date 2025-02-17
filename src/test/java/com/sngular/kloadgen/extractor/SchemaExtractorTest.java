/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sngular.kloadgen.extractor;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.sngular.kloadgen.common.SchemaRegistryEnum;
import com.sngular.kloadgen.exception.KLoadGenException;
import com.sngular.kloadgen.extractor.extractors.AvroExtractor;
import com.sngular.kloadgen.extractor.extractors.JsonExtractor;
import com.sngular.kloadgen.extractor.extractors.ProtoBufExtractor;
import com.sngular.kloadgen.extractor.impl.SchemaExtractorImpl;
import com.sngular.kloadgen.testutil.FileHelper;
import com.sngular.kloadgen.testutil.ParsedSchemaUtil;
import com.sngular.kloadgen.util.JMeterHelper;
import com.sngular.kloadgen.util.SchemaRegistryKeyHelper;
import com.squareup.wire.schema.internal.parser.MessageElement;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.Schema.Field;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.util.JMeterUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchemaExtractorTest {

  private final FileHelper fileHelper = new FileHelper();

  private Properties properties = new Properties();

  private SchemaExtractorImpl schemaExtractor;

  private MockedStatic<JMeterHelper> jMeterHelperMockedStatic;

  private MockedStatic<JMeterContextService> jMeterContextServiceMockedStatic;

  @Mock
  private AvroExtractor avroExtractor;

  @Mock
  private JsonExtractor jsonExtractor;

  @Mock
  private ProtoBufExtractor protoBufExtractor;

  @BeforeEach
  void setUp() {
    final File file = new File("src/test/resources");
    final String absolutePath = file.getAbsolutePath();
    JMeterUtils.loadJMeterProperties(absolutePath + "/kloadgen.properties");
    final JMeterContext jmcx = JMeterContextService.getContext();
    jmcx.setVariables(new JMeterVariables());
    JMeterUtils.setLocale(Locale.ENGLISH);
    schemaExtractor = new SchemaExtractorImpl(avroExtractor, jsonExtractor, protoBufExtractor);
    jMeterHelperMockedStatic = Mockito.mockStatic(JMeterHelper.class);
    jMeterContextServiceMockedStatic = Mockito.mockStatic(JMeterContextService.class, RETURNS_DEEP_STUBS);
  }

  @AfterEach
  void tearDown() {
    jMeterHelperMockedStatic.close();
    jMeterContextServiceMockedStatic.close();
    properties.clear();
  }

  @Test
  @DisplayName("Test flatPropertiesList with AVRO")
  void testFlatPropertiesListWithAVRO() throws IOException, RestClientException {

    final File testFile = fileHelper.getFile("/avro-files/embedded-avros-example-test.avsc");
    properties.setProperty(SchemaRegistryKeyHelper.SCHEMA_REGISTRY_NAME, SchemaRegistryEnum.CONFLUENT.toString());

    Mockito.when(avroExtractor.getParsedSchema(Mockito.anyString())).thenCallRealMethod();
    final ParsedSchema parsedSchema = schemaExtractor.schemaTypesList(testFile, "AVRO");

    jMeterHelperMockedStatic.when(() -> JMeterHelper.getParsedSchema(Mockito.anyString(), Mockito.any(Properties.class))).thenReturn(parsedSchema);
    jMeterContextServiceMockedStatic.when(() -> JMeterContextService.getContext().getProperties()).thenReturn(properties);
    Mockito.doNothing().when(avroExtractor).processField(Mockito.any(Field.class), Mockito.anyList(), ArgumentMatchers.eq(true), ArgumentMatchers.eq(false));
    schemaExtractor.flatPropertiesList("avroSubject");

    Mockito.verify(avroExtractor, Mockito.times(2)).processField(Mockito.any(Field.class), Mockito.anyList(), ArgumentMatchers.eq(true), ArgumentMatchers.eq(false));

  }

  @Test
  @DisplayName("Test flatPropertiesList with Json")
  void testFlatPropertiesListWithJson() throws IOException, RestClientException {

    final File testFile = fileHelper.getFile("/jsonschema/basic.jcs");
    final ParsedSchema parsedSchema = schemaExtractor.schemaTypesList(testFile, "JSON");
    properties.setProperty(SchemaRegistryKeyHelper.SCHEMA_REGISTRY_NAME, SchemaRegistryEnum.CONFLUENT.toString());

    jMeterContextServiceMockedStatic.when(() -> JMeterContextService.getContext().getProperties()).thenReturn(properties);
    jMeterHelperMockedStatic.when(() -> JMeterHelper.getParsedSchema(Mockito.anyString(), Mockito.any(Properties.class))).thenReturn(parsedSchema);
    Mockito.when(jsonExtractor.processSchema(Mockito.any(JsonNode.class))).thenReturn(new ArrayList<>());

    schemaExtractor.flatPropertiesList("jsonSubject");

    Mockito.verify(jsonExtractor).processSchema(Mockito.any(JsonNode.class));

  }

  @Test
  @DisplayName("Test flatPropertiesList with Protobuf")
  void testFlatPropertiesListWithProtobuf() throws RestClientException, IOException {

    final File testFile = fileHelper.getFile("/proto-files/easyTest.proto");
    final ParsedSchema parsedSchema = schemaExtractor.schemaTypesList(testFile, "PROTOBUF");
    properties.setProperty(SchemaRegistryKeyHelper.SCHEMA_REGISTRY_NAME, SchemaRegistryEnum.CONFLUENT.toString());

    jMeterContextServiceMockedStatic.when(() -> JMeterContextService.getContext().getProperties()).thenReturn(properties);
    jMeterHelperMockedStatic.when(() -> JMeterHelper.getParsedSchema(Mockito.anyString(), Mockito.any(Properties.class))).thenReturn(parsedSchema);
    Mockito.doNothing().when(protoBufExtractor).processField(Mockito.any(MessageElement.class), Mockito.anyList(), Mockito.anyList(), ArgumentMatchers.eq(false), Mockito.any());

    schemaExtractor.flatPropertiesList("protobufSubject");
    Mockito.verify(protoBufExtractor).processField(Mockito.any(), Mockito.anyList(), Mockito.anyList(), ArgumentMatchers.eq(false), Mockito.any());


  }

  @Test
  @DisplayName("Test flatPropertiesList throws exception schema type not supported")
  void testFlatPropertiesListWithException() throws IOException, RestClientException {

    final ParsedSchema parsedSchema = new ParsedSchemaUtil();
    properties.setProperty(SchemaRegistryKeyHelper.SCHEMA_REGISTRY_NAME, SchemaRegistryEnum.CONFLUENT.toString());

    jMeterContextServiceMockedStatic.when(() -> JMeterContextService.getContext().getProperties()).thenReturn(properties);

    jMeterHelperMockedStatic.when(() -> JMeterHelper.getParsedSchema(Mockito.anyString(), Mockito.any(Properties.class))).thenReturn(parsedSchema);
    Assertions.assertThatExceptionOfType(KLoadGenException.class)
              .isThrownBy(() -> schemaExtractor.flatPropertiesList("exceptionSubject")
              ).withMessage(String.format("Schema type not supported %s", parsedSchema.schemaType()));

  }

}
