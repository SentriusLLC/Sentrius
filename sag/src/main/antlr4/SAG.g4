grammar SAG;

// --- PARSER RULES ---

message     : header NL body EOF ;

header      : 'H' WS version WS msgId WS src WS dst WS timestamp (WS correlation)? (WS ttl)? ;
version     : 'v' WS INT ;
msgId       : 'id=' IDENT ;
src         : 'src=' IDENT ;
dst         : 'dst=' IDENT ;
timestamp   : 'ts=' INT ;
correlation : 'corr=' (IDENT | '-') ;
ttl         : 'ttl=' INT ;

body        : statement (';' WS? statement)* ';'? ;

statement   : actionStmt 
            | queryStmt 
            | assertStmt 
            | controlStmt 
            | eventStmt 
            | errorStmt ;

// Action with Reason and Policy
actionStmt  : 'DO' WS verbCall (WS policyClause)? (WS priorityClause)? (WS reasonClause)? ;
verbCall    : IDENT '(' argList? ')' ;
argList     : arg (',' WS? arg)* ;
arg         : value | namedArg ;
namedArg    : IDENT '=' value ;

reasonClause : 'BECAUSE' WS (STRING | expr) ;

queryStmt    : 'Q' WS expr (WS constraint)? ;
constraint   : 'WHERE' WS expr ;

assertStmt   : 'A' WS path WS '=' WS value ;

controlStmt  : 'IF' WS expr WS 'THEN' WS statement (WS 'ELSE' WS statement)? ;

eventStmt    : 'EVT' WS IDENT '(' argList? ')' ;

errorStmt    : 'ERR' WS IDENT (WS STRING)? ;

policyClause : 'P:' IDENT (':' expr)? ;
priorityClause : 'PRIO=' PRIORITY ;

// Expression Precedence (High to Low)
expr        : left=expr op='||' right=expr      # OrExpr
            | left=expr op='&&' right=expr      # AndExpr
            | left=expr op=('=='|'!='|'>'|'<'|'>='|'<=') right=expr # RelExpr
            | left=expr op=('+'|'-') right=expr   # AddExpr
            | left=expr op=('*'|'/') right=expr   # MulExpr
            | primary                             # PrimaryExpr
            ;

primary     : value 
            | '(' expr ')' ;

value       : STRING    # valString
            | INT       # valInt
            | FLOAT     # valFloat
            | BOOL      # valBool
            | 'null'    # valNull
            | path      # valPath
            | list      # valList
            | object    # valObject
            ;

path        : IDENT ('.' IDENT)* ;
list        : '[' (value (',' WS? value)*)? ']' ;
object      : '{' (member (',' WS? member)*)? '}' ;
member      : STRING WS? ':' WS? value ;

// --- LEXER RULES ---

PRIORITY    : 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL' ;
BOOL        : 'true' | 'false' ;
INT         : [0-9]+ ;
FLOAT       : [0-9]+ '.' [0-9]+ ;
IDENT       : [a-zA-Z] [a-zA-Z0-9_.-]* ;
STRING      : '"' (~["\\] | '\\' .)* '"' ;
WS          : [ \t]+ ;
NL          : [\r\n]+ ;
