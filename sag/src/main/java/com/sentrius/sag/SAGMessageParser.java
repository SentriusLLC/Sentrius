package com.sentrius.sag;

import com.sentrius.sag.model.Message;
import org.antlr.v4.runtime.*;

public class SAGMessageParser {

    public static Message parse(String input) throws SAGParseException {
        try {
            CharStream charStream = CharStreams.fromString(input);
            SAGLexer lexer = new SAGLexer(charStream);
            lexer.removeErrorListeners();
            lexer.addErrorListener(ThrowingErrorListener.INSTANCE);
            
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            SAGParser parser = new SAGParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(ThrowingErrorListener.INSTANCE);
            
            SAGParser.MessageContext messageContext = parser.message();
            
            SAGModelVisitor visitor = new SAGModelVisitor();
            return (Message) visitor.visit(messageContext);
        } catch (Exception e) {
            throw new SAGParseException("Failed to parse SAG message: " + e.getMessage(), e);
        }
    }

    private static class ThrowingErrorListener extends BaseErrorListener {
        public static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                              int line, int charPositionInLine, String msg, RecognitionException e) {
            throw new RuntimeException("Syntax error at line " + line + ":" + charPositionInLine + " - " + msg);
        }
    }
}
