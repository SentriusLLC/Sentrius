package io.sentrius.sso.genai;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.security.TokenProvider;
import io.sentrius.sso.integrations.exceptions.HttpException;

public abstract class DataGenerator<I, T> {

    protected final TokenProvider token;
    protected final GenerativeAPI api;

    protected final GeneratorConfiguration config;

    public DataGenerator(TokenProvider token, GenerativeAPI generator, GeneratorConfiguration config) {
        this.token = token;
        this.api = generator;
        this.config = config;
    }

    protected abstract String generateInput(I on);

    public abstract T generate(I on ) throws HttpException, JsonProcessingException;

}
