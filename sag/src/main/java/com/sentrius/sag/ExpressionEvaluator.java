package com.sentrius.sag;

import org.antlr.v4.runtime.*;

/**
 * Evaluates SAG expressions against a Context.
 * Supports comparison operators, logical operators, and arithmetic operators.
 */
public class ExpressionEvaluator {
    
    /**
     * Evaluate an expression string against a context.
     * @param expression The expression to evaluate (e.g., "balance > 1000")
     * @param context The context containing variable values
     * @return The result of the evaluation
     * @throws SAGParseException if the expression cannot be parsed or evaluated
     */
    public static Object evaluate(String expression, Context context) throws SAGParseException {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Remove all whitespace from the expression since the grammar doesn't handle it in expressions
            String cleanExpression = expression.replaceAll("\\s+", "");
            
            CharStream charStream = CharStreams.fromString(cleanExpression);
            SAGLexer lexer = new SAGLexer(charStream);
            lexer.removeErrorListeners();
            lexer.addErrorListener(ThrowingErrorListener.INSTANCE);
            
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            SAGParser parser = new SAGParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(ThrowingErrorListener.INSTANCE);
            
            // Parse expression
            SAGParser.ExprContext exprContext = parser.expr();
            
            return evaluateExpr(exprContext, context);
        } catch (Exception e) {
            throw new SAGParseException("Failed to evaluate expression: " + e.getMessage(), e);
        }
    }
    
    private static Object evaluateExpr(SAGParser.ExprContext ctx, Context context) {
        if (ctx instanceof SAGParser.OrExprContext) {
            SAGParser.OrExprContext orCtx = (SAGParser.OrExprContext) ctx;
            Object left = evaluateExpr(orCtx.left, context);
            Object right = evaluateExpr(orCtx.right, context);
            return toBoolean(left) || toBoolean(right);
        } else if (ctx instanceof SAGParser.AndExprContext) {
            SAGParser.AndExprContext andCtx = (SAGParser.AndExprContext) ctx;
            Object left = evaluateExpr(andCtx.left, context);
            Object right = evaluateExpr(andCtx.right, context);
            return toBoolean(left) && toBoolean(right);
        } else if (ctx instanceof SAGParser.RelExprContext) {
            SAGParser.RelExprContext relCtx = (SAGParser.RelExprContext) ctx;
            Object left = evaluateExpr(relCtx.left, context);
            Object right = evaluateExpr(relCtx.right, context);
            String op = relCtx.op.getText();
            return evaluateRelational(left, right, op);
        } else if (ctx instanceof SAGParser.AddExprContext) {
            SAGParser.AddExprContext addCtx = (SAGParser.AddExprContext) ctx;
            Object left = evaluateExpr(addCtx.left, context);
            Object right = evaluateExpr(addCtx.right, context);
            String op = addCtx.op.getText();
            return evaluateArithmetic(left, right, op);
        } else if (ctx instanceof SAGParser.MulExprContext) {
            SAGParser.MulExprContext mulCtx = (SAGParser.MulExprContext) ctx;
            Object left = evaluateExpr(mulCtx.left, context);
            Object right = evaluateExpr(mulCtx.right, context);
            String op = mulCtx.op.getText();
            return evaluateArithmetic(left, right, op);
        } else if (ctx instanceof SAGParser.PrimaryExprContext) {
            SAGParser.PrimaryExprContext primaryCtx = (SAGParser.PrimaryExprContext) ctx;
            return evaluatePrimary(primaryCtx.primary(), context);
        }
        
        return null;
    }
    
    private static Object evaluatePrimary(SAGParser.PrimaryContext ctx, Context context) {
        if (ctx.value() != null) {
            return evaluateValue(ctx.value(), context);
        } else if (ctx.expr() != null) {
            return evaluateExpr(ctx.expr(), context);
        }
        return null;
    }
    
    private static Object evaluateValue(SAGParser.ValueContext ctx, Context context) {
        if (ctx instanceof SAGParser.ValStringContext) {
            String text = ((SAGParser.ValStringContext) ctx).STRING().getText();
            return unquote(text);
        } else if (ctx instanceof SAGParser.ValIntContext) {
            return Integer.parseInt(((SAGParser.ValIntContext) ctx).INT().getText());
        } else if (ctx instanceof SAGParser.ValFloatContext) {
            return Double.parseDouble(((SAGParser.ValFloatContext) ctx).FLOAT().getText());
        } else if (ctx instanceof SAGParser.ValBoolContext) {
            return Boolean.parseBoolean(((SAGParser.ValBoolContext) ctx).BOOL().getText());
        } else if (ctx instanceof SAGParser.ValNullContext) {
            return null;
        } else if (ctx instanceof SAGParser.ValPathContext) {
            String path = ((SAGParser.ValPathContext) ctx).path().getText();
            return context.get(path);
        }
        return null;
    }
    
    private static boolean evaluateRelational(Object left, Object right, String op) {
        if (left == null || right == null) {
            if ("==".equals(op)) {
                return left == right;
            } else if ("!=".equals(op)) {
                return left != right;
            }
            return false;
        }
        
        switch (op) {
            case "==":
                return compareEquals(left, right);
            case "!=":
                return !compareEquals(left, right);
            case ">":
            case "<":
            case ">=":
            case "<=":
                // For comparison operators, both operands must be numbers
                if (!(left instanceof Number && right instanceof Number)) {
                    throw new IllegalArgumentException("Cannot compare non-numeric values with " + op);
                }
                return compareNumbers(left, right, op);
            default:
                return false;
        }
    }
    
    private static boolean compareNumbers(Object left, Object right, String op) {
        Double leftNum = toDouble(left);
        Double rightNum = toDouble(right);
        int comparison = leftNum.compareTo(rightNum);
        
        switch (op) {
            case ">":
                return comparison > 0;
            case "<":
                return comparison < 0;
            case ">=":
                return comparison >= 0;
            case "<=":
                return comparison <= 0;
            default:
                return false;
        }
    }
    
    private static boolean compareEquals(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        
        // Handle number comparisons
        if (left instanceof Number && right instanceof Number) {
            return toDouble(left).equals(toDouble(right));
        }
        
        // Direct equality for other types
        return left.equals(right);
    }
    
    private static Object evaluateArithmetic(Object left, Object right, String op) {
        Double leftNum = toDouble(left);
        Double rightNum = toDouble(right);
        
        switch (op) {
            case "+":
                return leftNum + rightNum;
            case "-":
                return leftNum - rightNum;
            case "*":
                return leftNum * rightNum;
            case "/":
                if (rightNum == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                return leftNum / rightNum;
            default:
                return null;
        }
    }
    
    private static Double toDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        throw new IllegalArgumentException("Cannot convert to number: " + obj);
    }
    
    private static boolean toBoolean(Object obj) {
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue() != 0;
        }
        if (obj instanceof String) {
            return !((String) obj).isEmpty();
        }
        return obj != null;
    }
    
    private static String unquote(String quoted) {
        if (quoted.startsWith("\"") && quoted.endsWith("\"")) {
            return quoted.substring(1, quoted.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
        }
        return quoted;
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
