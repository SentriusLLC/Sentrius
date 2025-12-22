package com.sentrius.sag;

import com.sentrius.sag.model.*;

import java.util.List;
import java.util.Map;

/**
 * Minifies SAG messages to reduce token usage and provides token counting.
 * Implements the "Wire Format" mode for efficient message transmission.
 */
public class MessageMinifier {
    
    /**
     * Convert a Message to its minified string representation.
     * Removes all optional whitespace and optimizes the format.
     * 
     * @param message The message to minify
     * @return The minified SAG message string
     */
    public static String toMinifiedString(Message message) {
        return toMinifiedString(message, false);
    }
    
    /**
     * Convert a Message to its minified string representation.
     * 
     * @param message The message to minify
     * @param useRelativeTimestamp If true, use relative timestamps when possible
     * @return The minified SAG message string
     */
    public static String toMinifiedString(Message message, boolean useRelativeTimestamp) {
        StringBuilder sb = new StringBuilder();
        
        // Header: H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890
        Header header = message.getHeader();
        sb.append("H v ").append(header.getVersion());
        sb.append(" id=").append(header.getMessageId());
        sb.append(" src=").append(header.getSource());
        sb.append(" dst=").append(header.getDestination());
        
        // Timestamp - use original value (relative timestamps would need a base time)
        sb.append(" ts=").append(header.getTimestamp());
        
        if (header.getCorrelation() != null) {
            sb.append(" corr=").append(header.getCorrelation());
        }
        
        if (header.getTtl() != null) {
            sb.append(" ttl=").append(header.getTtl());
        }
        
        sb.append("\n");
        
        // Body - statements
        for (int i = 0; i < message.getStatements().size(); i++) {
            Statement stmt = message.getStatements().get(i);
            sb.append(minifyStatement(stmt));
            if (i < message.getStatements().size() - 1) {
                sb.append(";");
            }
        }
        
        return sb.toString();
    }
    
    private static String minifyStatement(Statement stmt) {
        if (stmt instanceof ActionStatement) {
            return minifyAction((ActionStatement) stmt);
        } else if (stmt instanceof QueryStatement) {
            return minifyQuery((QueryStatement) stmt);
        } else if (stmt instanceof AssertStatement) {
            return minifyAssert((AssertStatement) stmt);
        } else if (stmt instanceof ControlStatement) {
            return minifyControl((ControlStatement) stmt);
        } else if (stmt instanceof EventStatement) {
            return minifyEvent((EventStatement) stmt);
        } else if (stmt instanceof ErrorStatement) {
            return minifyError((ErrorStatement) stmt);
        }
        return "";
    }
    
    private static String minifyAction(ActionStatement action) {
        StringBuilder sb = new StringBuilder();
        sb.append("DO ").append(action.getVerb()).append("(");
        
        // Positional args
        for (int i = 0; i < action.getArgs().size(); i++) {
            sb.append(minifyValue(action.getArgs().get(i)));
            if (i < action.getArgs().size() - 1 || !action.getNamedArgs().isEmpty()) {
                sb.append(",");
            }
        }
        
        // Named args
        int idx = 0;
        for (Map.Entry<String, Object> entry : action.getNamedArgs().entrySet()) {
            sb.append(entry.getKey()).append("=").append(minifyValue(entry.getValue()));
            if (idx < action.getNamedArgs().size() - 1) {
                sb.append(",");
            }
            idx++;
        }
        
        sb.append(")");
        
        if (action.getPolicy() != null) {
            sb.append(" P:").append(action.getPolicy());
            if (action.getPolicyExpr() != null) {
                sb.append(":").append(action.getPolicyExpr());
            }
        }
        
        if (action.getPriority() != null) {
            sb.append(" PRIO=").append(action.getPriority());
        }
        
        if (action.getReason() != null) {
            sb.append(" BECAUSE ");
            // Check if reason contains operators (is an expression)
            if (action.getReason().contains(">") || action.getReason().contains("<") ||
                action.getReason().contains("==") || action.getReason().contains("!=")) {
                // It's an expression, don't quote
                sb.append(action.getReason());
            } else {
                // It's a string
                sb.append("\"").append(escapeString(action.getReason())).append("\"");
            }
        }
        
        return sb.toString();
    }
    
    private static String minifyQuery(QueryStatement query) {
        StringBuilder sb = new StringBuilder();
        sb.append("Q ");
        sb.append(query.getExpression());
        if (query.getConstraint() != null) {
            sb.append(" WHERE ").append(query.getConstraint());
        }
        return sb.toString();
    }
    
    private static String minifyAssert(AssertStatement assertStmt) {
        return "A " + assertStmt.getPath() + " = " + minifyValue(assertStmt.getValue());
    }
    
    private static String minifyControl(ControlStatement control) {
        StringBuilder sb = new StringBuilder();
        sb.append("IF ").append(control.getCondition());
        sb.append(" THEN ").append(minifyStatement(control.getThenStatement()));
        if (control.getElseStatement() != null) {
            sb.append(" ELSE ").append(minifyStatement(control.getElseStatement()));
        }
        return sb.toString();
    }
    
    private static String minifyEvent(EventStatement event) {
        StringBuilder sb = new StringBuilder();
        sb.append("EVT ").append(event.getEventName()).append("(");
        
        for (int i = 0; i < event.getArgs().size(); i++) {
            sb.append(minifyValue(event.getArgs().get(i)));
            if (i < event.getArgs().size() - 1 || !event.getNamedArgs().isEmpty()) {
                sb.append(",");
            }
        }
        
        int idx = 0;
        for (Map.Entry<String, Object> entry : event.getNamedArgs().entrySet()) {
            sb.append(entry.getKey()).append("=").append(minifyValue(entry.getValue()));
            if (idx < event.getNamedArgs().size() - 1) {
                sb.append(",");
            }
            idx++;
        }
        
        sb.append(")");
        return sb.toString();
    }
    
    private static String minifyError(ErrorStatement error) {
        StringBuilder sb = new StringBuilder();
        sb.append("ERR ").append(error.getErrorCode());
        if (error.getMessage() != null) {
            sb.append(" \"").append(escapeString(error.getMessage())).append("\"");
        }
        return sb.toString();
    }
    
    private static String minifyValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeString((String) value) + "\"";
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append(minifyValue(list.get(i)));
                if (i < list.size() - 1) {
                    sb.append(",");
                }
            }
            sb.append("]");
            return sb.toString();
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            StringBuilder sb = new StringBuilder("{");
            int idx = 0;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                sb.append("\"").append(escapeString(entry.getKey())).append("\":");
                sb.append(minifyValue(entry.getValue()));
                if (idx < map.size() - 1) {
                    sb.append(",");
                }
                idx++;
            }
            sb.append("}");
            return sb.toString();
        }
        return value.toString();
    }
    
    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Count approximate tokens in a SAG message.
     * Uses a simple heuristic: roughly 4 characters per token.
     * 
     * @param sagMessage The SAG message string
     * @return Approximate token count
     */
    public static int countTokens(String sagMessage) {
        // Simple token counting: ~4 chars per token is a common heuristic
        return (int) Math.ceil(sagMessage.length() / 4.0);
    }
    
    /**
     * Compare token usage between SAG and equivalent JSON.
     * 
     * @param message The SAG message
     * @return A TokenComparison object with statistics
     */
    public static TokenComparison compareWithJSON(Message message) {
        String sagMinified = toMinifiedString(message);
        String jsonEquivalent = toJSONEquivalent(message);
        
        int sagTokens = countTokens(sagMinified);
        int jsonTokens = countTokens(jsonEquivalent);
        int saved = jsonTokens - sagTokens;
        double percentSaved = (saved * 100.0) / jsonTokens;
        
        return new TokenComparison(sagMinified.length(), jsonEquivalent.length(),
                                  sagTokens, jsonTokens, saved, percentSaved);
    }
    
    private static String toJSONEquivalent(Message message) {
        // Create a rough JSON equivalent for comparison
        StringBuilder json = new StringBuilder("{");
        
        Header h = message.getHeader();
        json.append("\"header\":{");
        json.append("\"version\":").append(h.getVersion()).append(",");
        json.append("\"messageId\":\"").append(h.getMessageId()).append("\",");
        json.append("\"source\":\"").append(h.getSource()).append("\",");
        json.append("\"destination\":\"").append(h.getDestination()).append("\",");
        json.append("\"timestamp\":").append(h.getTimestamp());
        if (h.getCorrelation() != null) {
            json.append(",\"correlation\":\"").append(h.getCorrelation()).append("\"");
        }
        if (h.getTtl() != null) {
            json.append(",\"ttl\":").append(h.getTtl());
        }
        json.append("},");
        
        json.append("\"statements\":[");
        for (int i = 0; i < message.getStatements().size(); i++) {
            Statement stmt = message.getStatements().get(i);
            json.append("{\"type\":\"").append(stmt.getClass().getSimpleName()).append("\"");
            
            if (stmt instanceof ActionStatement) {
                ActionStatement a = (ActionStatement) stmt;
                json.append(",\"verb\":\"").append(a.getVerb()).append("\"");
                if (!a.getArgs().isEmpty()) {
                    json.append(",\"args\":").append(a.getArgs());
                }
                if (!a.getNamedArgs().isEmpty()) {
                    json.append(",\"namedArgs\":").append(a.getNamedArgs());
                }
            }
            
            json.append("}");
            if (i < message.getStatements().size() - 1) {
                json.append(",");
            }
        }
        json.append("]}");
        
        return json.toString();
    }
    
    /**
     * Represents a comparison between SAG and JSON token usage.
     */
    public static class TokenComparison {
        private final int sagLength;
        private final int jsonLength;
        private final int sagTokens;
        private final int jsonTokens;
        private final int tokensSaved;
        private final double percentSaved;
        
        public TokenComparison(int sagLength, int jsonLength, int sagTokens, 
                             int jsonTokens, int tokensSaved, double percentSaved) {
            this.sagLength = sagLength;
            this.jsonLength = jsonLength;
            this.sagTokens = sagTokens;
            this.jsonTokens = jsonTokens;
            this.tokensSaved = tokensSaved;
            this.percentSaved = percentSaved;
        }
        
        public int getSagLength() { return sagLength; }
        public int getJsonLength() { return jsonLength; }
        public int getSagTokens() { return sagTokens; }
        public int getJsonTokens() { return jsonTokens; }
        public int getTokensSaved() { return tokensSaved; }
        public double getPercentSaved() { return percentSaved; }
        
        @Override
        public String toString() {
            return String.format("SAG: %d chars (%d tokens) vs JSON: %d chars (%d tokens) - Saved: %d tokens (%.1f%%)",
                sagLength, sagTokens, jsonLength, jsonTokens, tokensSaved, percentSaved);
        }
    }
}
