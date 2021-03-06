/*
 * Copyright 2020 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.kafka.schemaregistry.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.everit.json.schema.ValidationException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonSchemaTest {

  private static ObjectMapper objectMapper = new ObjectMapper();

  private static final String recordSchemaString = "{\"properties\": {\n"
      + "     \"null\": {\"type\": \"null\"},\n"
      + "     \"boolean\": {\"type\": \"boolean\"},\n"
      + "     \"number\": {\"type\": \"number\"},\n"
      + "     \"string\": {\"type\": \"string\"},\n"
      + "  },\n"
      + "  \"additionalProperties\": false\n"
      + "}";

  private static final JsonSchema recordSchema = new JsonSchema(recordSchemaString);

  private static final String arraySchemaString = "{\"type\": \"array\", \"items\": { \"type\": "
      + "\"string\" } }";

  private static final JsonSchema arraySchema = new JsonSchema(arraySchemaString);

  private static final String unionSchemaString = "{\n"
      + "  \"oneOf\": [\n"
      + "    { \"type\": \"string\", \"maxLength\": 5 },\n"
      + "    { \"type\": \"number\", \"minimum\": 0 }\n"
      + "  ]\n"
      + "}";

  private static final JsonSchema unionSchema = new JsonSchema(unionSchemaString);

  private static final String enumSchemaString = "{ \"type\": \"string\", \"enum\": [\"red\", "
      + "\"amber\", \"green\"] }";

  private static final JsonSchema enumSchema = new JsonSchema(enumSchemaString);

  @Test
  public void testPrimitiveTypesToJsonSchema() throws Exception {
    Object result = JsonSchemaUtils.toObject(null, createPrimitiveSchema("null"));
    assertTrue(result == null);

    result = JsonSchemaUtils.toObject(jsonTree("true"), createPrimitiveSchema("boolean"));
    assertEquals(true, ((BooleanNode) result).asBoolean());

    result = JsonSchemaUtils.toObject(jsonTree("false"), createPrimitiveSchema("boolean"));
    assertEquals(false, ((BooleanNode) result).asBoolean());

    result = JsonSchemaUtils.toObject(jsonTree("12"), createPrimitiveSchema("number"));
    assertEquals(12, ((NumericNode) result).asInt());

    result = JsonSchemaUtils.toObject(jsonTree("23.2"), createPrimitiveSchema("number"));
    assertEquals(23.2, ((NumericNode) result).asDouble(), 0.1);

    result = JsonSchemaUtils.toObject(jsonTree("\"a string\""), createPrimitiveSchema("string"));
    assertEquals("a string", ((TextNode) result).asText());
  }

  @Test
  public void testRecordToJsonSchema() throws Exception {
    String json = "{\n"
        + "    \"null\": null,\n"
        + "    \"boolean\": true,\n"
        + "    \"number\": 12,\n"
        + "    \"string\": \"string\"\n"
        + "}";

    JsonNode result = (JsonNode) JsonSchemaUtils.toObject(jsonTree(json), recordSchema);
    assertEquals(true, result.get("null").isNull());
    assertEquals(true, result.get("boolean").booleanValue());
    assertEquals(12, result.get("number").intValue());
    assertEquals("string", result.get("string").textValue());
  }

  @Test(expected = ValidationException.class)
  public void testInvalidRecordToJsonSchema() throws Exception {
    String json = "{\n"
        + "    \"null\": null,\n"
        + "    \"boolean\": true,\n"
        + "    \"number\": 12,\n"
        + "    \"string\": \"string\",\n"
        + "    \"badString\": \"string\"\n"
        + "}";

    JsonNode result = (JsonNode) JsonSchemaUtils.toObject(jsonTree(json), recordSchema);
    assertEquals(true, result.get("null").isNull());
    assertEquals(true, result.get("boolean").booleanValue());
    assertEquals(12, result.get("number").intValue());
    assertEquals("string", result.get("string").textValue());
  }

  @Test
  public void testArrayToJsonSchema() throws Exception {
    String json = "[\"one\", \"two\", \"three\"]";

    Object result = JsonSchemaUtils.toObject(jsonTree(json), arraySchema);
    ArrayNode arrayNode = (ArrayNode) result;
    Iterator<JsonNode> elements = arrayNode.elements();
    List<String> strings = new ArrayList<String>();
    while (elements.hasNext()) {
      strings.add(elements.next().textValue());
    }
    assertArrayEquals(new String[]{"one", "two", "three"}, strings.toArray());
  }

  @Test
  public void testUnionToJsonSchema() throws Exception {
    Object result = JsonSchemaUtils.toObject(jsonTree("\"test\""), unionSchema);
    assertEquals("test", ((TextNode) result).asText());

    result = JsonSchemaUtils.toObject(jsonTree("12"), unionSchema);
    assertEquals(12, ((NumericNode) result).asInt());

    try {
      JsonSchemaUtils.toObject(jsonTree("-1"), unionSchema);
      fail("Trying to use negative number should fail");
    } catch (Exception e) {
      // expected
    }
  }

  @Test
  public void testEnumToJsonSchema() throws Exception {
    Object result = JsonSchemaUtils.toObject(jsonTree("\"red\""), enumSchema);
    assertEquals("red", ((TextNode) result).asText());

    try {
      JsonSchemaUtils.toObject(jsonTree("\"yellow\""), enumSchema);
      fail("Trying to use non-enum should fail");
    } catch (Exception e) {
      // expected
    }
  }

  @Test
  public void testPrimitiveTypesToJson() throws Exception {
    JsonNode result = objectMapper.readTree(JsonSchemaUtils.toJson((int) 0));
    assertTrue(result.isNumber());

    result = objectMapper.readTree(JsonSchemaUtils.toJson((long) 0));
    assertTrue(result.isNumber());

    result = objectMapper.readTree(JsonSchemaUtils.toJson(0.1f));
    assertTrue(result.isNumber());

    result = objectMapper.readTree(JsonSchemaUtils.toJson(0.1));
    assertTrue(result.isNumber());

    result = objectMapper.readTree(JsonSchemaUtils.toJson(true));
    assertTrue(result.isBoolean());

    // "Primitive" here refers to JsonSchema primitive types, which are returned as standalone
    // objects,
    // which can't have attached schemas. This includes, for example, Strings and byte[] even
    // though they are not Java primitives

    result = objectMapper.readTree(JsonSchemaUtils.toJson("abcdefg"));
    assertTrue(result.isTextual());
    assertEquals("abcdefg", result.textValue());
  }

  @Test
  public void testRecordToJson() throws Exception {
    String json = "{\n"
        + "    \"null\": null,\n"
        + "    \"boolean\": true,\n"
        + "    \"number\": 12,\n"
        + "    \"string\": \"string\"\n"
        + "}";
    JsonNode data = new ObjectMapper().readTree(json);
    JsonNode result = objectMapper.readTree(JsonSchemaUtils.toJson(data));
    assertTrue(result.isObject());
    assertTrue(result.get("null").isNull());
    assertTrue(result.get("boolean").isBoolean());
    assertEquals(true, result.get("boolean").booleanValue());
    assertTrue(result.get("number").isIntegralNumber());
    assertEquals(12, result.get("number").intValue());
    assertTrue(result.get("string").isTextual());
    assertEquals("string", result.get("string").textValue());
  }

  @Test
  public void testArrayToJson() throws Exception {
    String json = "[\"one\", \"two\", \"three\"]";
    JsonNode data = new ObjectMapper().readTree(json);
    JsonNode result = objectMapper.readTree(JsonSchemaUtils.toJson(data));

    assertTrue(result.isArray());
    assertEquals(3, result.size());
    assertEquals(JsonNodeFactory.instance.textNode("one"), result.get(0));
    assertEquals(JsonNodeFactory.instance.textNode("two"), result.get(1));
    assertEquals(JsonNodeFactory.instance.textNode("three"), result.get(2));
  }

  private static JsonSchema createPrimitiveSchema(String type) {
    String schemaString = String.format("{\"type\" : \"%s\"}", type);
    return new JsonSchema(schemaString);
  }

  private static JsonNode jsonTree(String jsonData) {
    try {
      return objectMapper.readTree(jsonData);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse JSON", e);
    }
  }

}
