package com.factory.collab.service;

import com.factory.collab.dto.TemplateDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TemplateCodec {
    private final ObjectMapper mapper;

    public String encode(TemplateDefinition def) {
        try {
            return mapper.writeValueAsString(def);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public TemplateDefinition decode(String json) {
        try {
            return mapper.readValue(json, TemplateDefinition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt template definition", e);
        }
    }
}
