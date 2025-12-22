package com.sentrius.sag;

import com.sentrius.sag.model.*;
import java.util.*;

public class SAGModelVisitor extends SAGBaseVisitor<Object> {

    @Override
    public Message visitMessage(SAGParser.MessageContext ctx) {
        Header header = (Header) visit(ctx.header());
        List<Statement> statements = new ArrayList<>();
        
        if (ctx.body() != null) {
            for (SAGParser.StatementContext stmtCtx : ctx.body().statement()) {
                Statement stmt = (Statement) visit(stmtCtx);
                if (stmt != null) {
                    statements.add(stmt);
                }
            }
        }
        
        return new Message(header, statements);
    }

    @Override
    public Header visitHeader(SAGParser.HeaderContext ctx) {
        int version = Integer.parseInt(ctx.version().INT().getText());
        String messageId = extractValue(ctx.msgId().IDENT().getText());
        String source = extractValue(ctx.src().IDENT().getText());
        String destination = extractValue(ctx.dst().IDENT().getText());
        long timestamp = Long.parseLong(ctx.timestamp().INT().getText());
        
        String correlation = null;
        if (ctx.correlation() != null) {
            String corrText = ctx.correlation().IDENT() != null ? 
                ctx.correlation().IDENT().getText() : null;
            correlation = corrText != null && !"-".equals(corrText) ? corrText : null;
        }
        
        Integer ttl = null;
        if (ctx.ttl() != null) {
            ttl = Integer.parseInt(ctx.ttl().INT().getText());
        }
        
        return new Header(version, messageId, source, destination, timestamp, correlation, ttl);
    }

    @Override
    public Statement visitActionStmt(SAGParser.ActionStmtContext ctx) {
        SAGParser.VerbCallContext verbCallCtx = ctx.verbCall();
        String verb = verbCallCtx.IDENT().getText();
        
        List<Object> args = new ArrayList<>();
        Map<String, Object> namedArgs = new HashMap<>();
        
        if (verbCallCtx.argList() != null) {
            for (SAGParser.ArgContext argCtx : verbCallCtx.argList().arg()) {
                if (argCtx.namedArg() != null) {
                    String name = argCtx.namedArg().IDENT().getText();
                    Object value = visit(argCtx.namedArg().value());
                    namedArgs.put(name, value);
                } else {
                    Object value = visit(argCtx.value());
                    args.add(value);
                }
            }
        }
        
        String policy = null;
        String policyExpr = null;
        if (ctx.policyClause() != null) {
            policy = ctx.policyClause().IDENT().getText();
            if (ctx.policyClause().expr() != null) {
                policyExpr = ctx.policyClause().expr().getText();
            }
        }
        
        String priority = null;
        if (ctx.priorityClause() != null) {
            priority = ctx.priorityClause().PRIORITY().getText();
        }
        
        String reason = null;
        if (ctx.reasonClause() != null) {
            if (ctx.reasonClause().STRING() != null) {
                reason = unquote(ctx.reasonClause().STRING().getText());
            } else if (ctx.reasonClause().expr() != null) {
                reason = ctx.reasonClause().expr().getText();
            }
        }
        
        return new ActionStatement(verb, args, namedArgs, policy, policyExpr, priority, reason);
    }

    @Override
    public Statement visitQueryStmt(SAGParser.QueryStmtContext ctx) {
        Object expr = visit(ctx.expr());
        Object constraint = null;
        if (ctx.constraint() != null) {
            constraint = visit(ctx.constraint().expr());
        }
        return new QueryStatement(expr, constraint);
    }

    @Override
    public Statement visitAssertStmt(SAGParser.AssertStmtContext ctx) {
        String path = ctx.path().getText();
        Object value = visit(ctx.value());
        return new AssertStatement(path, value);
    }

    @Override
    public Statement visitControlStmt(SAGParser.ControlStmtContext ctx) {
        Object condition = visit(ctx.expr());
        Statement thenStmt = (Statement) visit(ctx.statement(0));
        Statement elseStmt = null;
        if (ctx.statement().size() > 1) {
            elseStmt = (Statement) visit(ctx.statement(1));
        }
        return new ControlStatement(condition, thenStmt, elseStmt);
    }

    @Override
    public Statement visitEventStmt(SAGParser.EventStmtContext ctx) {
        String eventName = ctx.IDENT().getText();
        List<Object> args = new ArrayList<>();
        Map<String, Object> namedArgs = new HashMap<>();
        
        if (ctx.argList() != null) {
            for (SAGParser.ArgContext argCtx : ctx.argList().arg()) {
                if (argCtx.namedArg() != null) {
                    String name = argCtx.namedArg().IDENT().getText();
                    Object value = visit(argCtx.namedArg().value());
                    namedArgs.put(name, value);
                } else {
                    Object value = visit(argCtx.value());
                    args.add(value);
                }
            }
        }
        
        return new EventStatement(eventName, args, namedArgs);
    }

    @Override
    public Statement visitErrorStmt(SAGParser.ErrorStmtContext ctx) {
        String errorCode = ctx.IDENT().getText();
        String message = null;
        if (ctx.STRING() != null) {
            message = unquote(ctx.STRING().getText());
        }
        return new ErrorStatement(errorCode, message);
    }

    @Override
    public Object visitValString(SAGParser.ValStringContext ctx) {
        return unquote(ctx.STRING().getText());
    }

    @Override
    public Object visitValInt(SAGParser.ValIntContext ctx) {
        return Integer.parseInt(ctx.INT().getText());
    }

    @Override
    public Object visitValFloat(SAGParser.ValFloatContext ctx) {
        return Double.parseDouble(ctx.FLOAT().getText());
    }

    @Override
    public Object visitValBool(SAGParser.ValBoolContext ctx) {
        return Boolean.parseBoolean(ctx.BOOL().getText());
    }

    @Override
    public Object visitValNull(SAGParser.ValNullContext ctx) {
        return null;
    }

    @Override
    public Object visitValPath(SAGParser.ValPathContext ctx) {
        return ctx.path().getText();
    }

    @Override
    public Object visitValList(SAGParser.ValListContext ctx) {
        List<Object> list = new ArrayList<>();
        if (ctx.list().value() != null) {
            for (SAGParser.ValueContext valueCtx : ctx.list().value()) {
                list.add(visit(valueCtx));
            }
        }
        return list;
    }

    @Override
    public Object visitValObject(SAGParser.ValObjectContext ctx) {
        Map<String, Object> map = new HashMap<>();
        if (ctx.object().member() != null) {
            for (SAGParser.MemberContext memberCtx : ctx.object().member()) {
                String key = unquote(memberCtx.STRING().getText());
                Object value = visit(memberCtx.value());
                map.put(key, value);
            }
        }
        return map;
    }

    @Override
    public Object visitOrExpr(SAGParser.OrExprContext ctx) {
        return ctx.getText();
    }

    @Override
    public Object visitAndExpr(SAGParser.AndExprContext ctx) {
        return ctx.getText();
    }

    @Override
    public Object visitRelExpr(SAGParser.RelExprContext ctx) {
        return ctx.getText();
    }

    @Override
    public Object visitAddExpr(SAGParser.AddExprContext ctx) {
        return ctx.getText();
    }

    @Override
    public Object visitMulExpr(SAGParser.MulExprContext ctx) {
        return ctx.getText();
    }

    @Override
    public Object visitPrimaryExpr(SAGParser.PrimaryExprContext ctx) {
        return visit(ctx.primary());
    }

    @Override
    public Object visitPrimary(SAGParser.PrimaryContext ctx) {
        if (ctx.value() != null) {
            return visit(ctx.value());
        }
        if (ctx.expr() != null) {
            return visit(ctx.expr());
        }
        return null;
    }

    private String extractValue(String text) {
        return text;
    }

    private String unquote(String quoted) {
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
}
