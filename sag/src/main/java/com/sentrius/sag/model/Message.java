package com.sentrius.sag.model;

import java.util.List;

public class Message {
    private final Header header;
    private final List<Statement> statements;

    public Message(Header header, List<Statement> statements) {
        this.header = header;
        this.statements = statements;
    }

    public Header getHeader() {
        return header;
    }

    public List<Statement> getStatements() {
        return statements;
    }

    @Override
    public String toString() {
        return "Message{header=" + header + ", statements=" + statements + "}";
    }
}
