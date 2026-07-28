package com.AccountReceivableManagement.cdc.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class BooleanFromIntegerDeserializer extends JsonDeserializer<Boolean> {

    @Override
    public Boolean deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

        if (p.currentToken().isBoolean()) {
            return p.getBooleanValue();
        }

        if (p.currentToken().isNumeric()) {
            return p.getIntValue() != 0;
        }

        if (p.currentToken() == JsonToken.VALUE_STRING) {
            String value = p.getValueAsString().trim();

            if (value.isEmpty()) {
                return null;
            }

            return Boolean.parseBoolean(value);
        }

        return null;
    }
}
