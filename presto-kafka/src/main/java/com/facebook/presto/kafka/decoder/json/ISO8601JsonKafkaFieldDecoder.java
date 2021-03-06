/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.kafka.decoder.json;

import com.facebook.presto.kafka.KafkaColumnHandle;
import com.facebook.presto.kafka.KafkaFieldValueProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import io.airlift.slice.Slice;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Locale;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * ISO 8601 date format decoder.
 * <p/>
 * Uses hardcoded UTC timezone and english locale.
 */
public class ISO8601JsonKafkaFieldDecoder
        extends JsonKafkaFieldDecoder
{
    @VisibleForTesting
    static final String NAME = "iso8601";

    /**
     * TODO: configurable time zones and locales
     */
    private static final DateTimeFormatter FORMATTER = ISODateTimeFormat.dateTimeParser().withLocale(Locale.ENGLISH).withZoneUTC();

    @Override
    public Set<Class<?>> getJavaTypes()
    {
        return ImmutableSet.<Class<?>>of(long.class, Slice.class);
    }

    @Override
    public String getFieldDecoderName()
    {
        return NAME;
    }

    @Override
    public KafkaFieldValueProvider decode(JsonNode value, KafkaColumnHandle columnHandle)
    {
        requireNonNull(columnHandle, "columnHandle is null");
        requireNonNull(value, "value is null");

        return new ISO8601JsonKafkaValueProvider(value, columnHandle);
    }

    public static class ISO8601JsonKafkaValueProvider
            extends DateTimeJsonKafkaValueProvider
    {
        public ISO8601JsonKafkaValueProvider(JsonNode value, KafkaColumnHandle columnHandle)
        {
            super(value, columnHandle);
        }

        @Override
        protected long getMillis()
        {
            if (isNull()) {
                return 0L;
            }

            if (value.canConvertToLong()) {
                return value.asLong();
            }

            String textValue = value.isValueNode() ? value.asText() : value.toString();
            return FORMATTER.parseMillis(textValue);
        }
    }
}
