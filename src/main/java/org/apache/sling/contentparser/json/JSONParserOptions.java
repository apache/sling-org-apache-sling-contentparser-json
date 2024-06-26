/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.contentparser.json;

import java.util.Set;

import org.apache.sling.contentparser.api.ParserOptions;
import org.osgi.annotation.versioning.ConsumerType;

/**
 * Defines specific JSON parser options which can be used with the JSON {@link org.apache.sling.contentparser.api.ContentParser}
 * implementations provided by this bundle.
 */
@ConsumerType
public final class JSONParserOptions extends ParserOptions {

    /**
     * List of JSON parser features activated by default.
     */
    public static final Set<JSONParserFeature> DEFAULT_JSON_PARSER_FEATURES = Set.of(JSONParserFeature.COMMENTS);

    private Set<JSONParserFeature> features = DEFAULT_JSON_PARSER_FEATURES;

    /**
     * Set the features the JSON parser should apply when parsing files.
     *
     * @param value JSON parser features
     * @return this
     */
    public JSONParserOptions withFeatures(Set<JSONParserFeature> value) {
        this.features = Set.copyOf(value);
        return this;
    }

    /**
     * Set the features the JSON parser should apply when parsing files.
     *
     * @param values JSON parser features
     * @return this
     */
    public JSONParserOptions withFeatures(JSONParserFeature... values) {
        this.features = Set.of(values);
        return this;
    }

    /**
     * Returns a copy of the features encapsulated by this instance. For modifying the set of features please use the {@code withFeatures}
     * fluid methods.
     *
     * @return the features the JSON parser should apply when parsing files
     */
    public Set<JSONParserFeature> getFeatures() {
        return Set.copyOf(features);
    }
}
