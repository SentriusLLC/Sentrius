package io.dataguardians.sso.integrations.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.dataguardians.security.TokenProvider;
import io.dataguardians.sso.integrations.exceptions.HttpException;

public abstract class DataGenerator<T> {

    protected final TokenProvider token;
    protected final GenerativeAPI api;

    protected final GeneratorConfiguration config;

    public DataGenerator(TokenProvider token, GenerativeAPI generator, GeneratorConfiguration config) {
        this.token = token;
        this.api = generator;
        this.config = config;
    }

    protected abstract String generateInput(String on);

    public abstract T generate(String on) throws HttpException, JsonProcessingException;

}
