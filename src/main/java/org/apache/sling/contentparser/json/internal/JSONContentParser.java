/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.contentparser.json.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParsingException;

import org.apache.commons.io.IOUtils;
import org.apache.sling.contentparser.api.ContentHandler;
import org.apache.sling.contentparser.api.ContentParser;
import org.apache.sling.contentparser.api.ParserHelper;
import org.apache.sling.contentparser.api.ParserOptions;
import org.apache.sling.contentparser.json.JSONParserFeature;
import org.apache.sling.contentparser.json.JSONParserOptions;
import org.osgi.service.component.annotations.Component;

@Component(
        property = {
                ContentParser.SERVICE_PROPERTY_CONTENT_TYPE + "=json"
        }
)
public class JSONContentParser implements ContentParser {

    private static final String JOHNZON_SUPPORT_COMMENTS = "org.apache.johnzon.supports-comments";

    @Override
    public void parse(ContentHandler handler, InputStream is, ParserOptions parserOptions) throws IOException {
        final boolean jsonQuoteTicks;
        final boolean supportComments;
        if (parserOptions instanceof JSONParserOptions) {
            JSONParserOptions jsonParserOptions = (JSONParserOptions) parserOptions;
            jsonQuoteTicks = jsonParserOptions.getFeatures().contains(JSONParserFeature.QUOTE_TICK);
            supportComments = jsonParserOptions.getFeatures().contains(JSONParserFeature.COMMENTS);
        } else {
            jsonQuoteTicks = false;
            supportComments = false;
        }

        /*
         * Implementation note: This parser uses JsonReader instead of the (more memory-efficient)
         * JsonParser Stream API because otherwise it would not be possible to report parent resources
         * including all properties properly before their children.
         */
        Map<String, Object> jsonReaderFactoryConfiguration;
        if (supportComments) {
            jsonReaderFactoryConfiguration = new HashMap<>();
            jsonReaderFactoryConfiguration.put(JOHNZON_SUPPORT_COMMENTS, true);
        } else {
            jsonReaderFactoryConfiguration = Collections.emptyMap();
        }
        final JsonReaderFactory jsonReaderFactory = Json.createReaderFactory(jsonReaderFactoryConfiguration);
        JsonObject jsonObject = jsonQuoteTicks ? toJsonObjectWithJsonTicks(jsonReaderFactory, is) : toJsonObject(jsonReaderFactory, is);
        parse(handler, jsonObject, parserOptions, "/");
    }

    private JsonObject toJsonObject(JsonReaderFactory jsonReaderFactory, InputStream is) throws IOException {
        try (JsonReader reader = jsonReaderFactory.createReader(is)) {
            return reader.readObject();
        } catch (JsonParsingException ex) {
            throw new IOException("Error parsing JSON content.", ex);
        }
    }

    private JsonObject toJsonObjectWithJsonTicks(JsonReaderFactory jsonReaderFactory, InputStream is) throws IOException {
        String jsonString = IOUtils.toString(is, StandardCharsets.UTF_8);

        // convert ticks to double quotes
        jsonString = JSONTicksConverter.tickToDoubleQuote(jsonString);

        try (JsonReader reader = jsonReaderFactory.createReader(new StringReader(jsonString))) {
            return reader.readObject();
        } catch (JsonParsingException ex) {
            throw new IOException("Error parsing JSON content.", ex);
        }
    }

    private void parse(ContentHandler handler, JsonObject object, ParserOptions parserOptions, String path) throws IOException {
        // parse JSON object
        Map<String, Object> properties = new HashMap<>();
        Map<String, JsonObject> children = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
            String childName = entry.getKey();
            Object value = null;
            boolean ignore = false;
            try {
                value = convertValue(parserOptions, entry.getValue());
            } catch (IllegalArgumentException ex) {
                if (parserOptions.getIgnoreResourceNames().contains(childName) || parserOptions.getIgnorePropertyNames()
                        .contains(removePrefixFromPropertyName(parserOptions.getRemovePropertyNamePrefixes(), childName))) {
                    ignore = true;
                } else {
                    throw new IOException(ex);
                }
            }
            boolean isResource = (value instanceof JsonObject);
            if (!ignore) {
                if (isResource) {
                    ignore = parserOptions.getIgnoreResourceNames().contains(childName);
                } else {
                    for (String prefix : parserOptions.getRemovePropertyNamePrefixes()) {
                        if (childName.startsWith(prefix)) {
                            childName = childName.substring(prefix.length());
                            break;
                        }

                    }
                    ignore = parserOptions.getIgnorePropertyNames().contains(childName);
                }
            }
            if (!ignore) {
                if (isResource) {
                    children.put(childName, (JsonObject) value);
                } else {
                    properties.put(childName, value);
                }
            }
        }
        String defaultPrimaryType = parserOptions.getDefaultPrimaryType();
        if (defaultPrimaryType != null && !properties.containsKey("jcr:primaryType")) {
            properties.put("jcr:primaryType", defaultPrimaryType);
        }

        // report current JSON object
        handler.resource(path, properties);

        // parse and report children
        for (Map.Entry<String, JsonObject> entry : children.entrySet()) {
            String childPath = path.endsWith("/") ? path + entry.getKey() : path + "/" + entry.getKey();
            parse(handler, entry.getValue(), parserOptions, childPath);
        }
    }

    private Object convertValue(ParserOptions parserOptions, JsonValue value) {
        switch (value.getValueType()) {
            case STRING:
                String stringValue = ((JsonString) value).getString();
                if (parserOptions.isDetectCalendarValues()) {
                    Calendar calendar = ParserHelper.parseDate(stringValue);
                    if (calendar != null) {
                        return calendar;
                    }
                }
                return stringValue;
            case NUMBER:
                JsonNumber numberValue = (JsonNumber) value;
                if (numberValue.isIntegral()) {
                    return numberValue.longValue();
                } else {
                    return numberValue.bigDecimalValue();
                }
            case TRUE:
                return true;
            case FALSE:
                return false;
            case NULL:
                return null;
            case ARRAY:
                JsonArray arrayValue = (JsonArray) value;
                Object[] values = new Object[arrayValue.size()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = convertValue(parserOptions, arrayValue.get(i));
                }
                return ParserHelper.convertSingleTypeArray(values);
            case OBJECT:
                return value;
            default:
                throw new IllegalArgumentException("Unexpected JSON value type: " + value.getValueType());
        }
    }

    private String removePrefixFromPropertyName(Set<String> prefixes, String propertyName) {
        for (String prefix : prefixes) {
            if (propertyName.startsWith(prefix)) {
                return propertyName.substring(prefix.length());
            }
        }
        return propertyName;
    }


}
