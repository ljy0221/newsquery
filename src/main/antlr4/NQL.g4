grammar NQL;

// === Parser Rules ===

query       : expr EOF ;

expr        : expr AND expr          # andExpr
            | expr OR expr           # orExpr
            | NOT expr               # notExpr
            | '(' expr ')'           # groupExpr
            | keywordExpr            # kwExpr
            | fieldExpr              # fieldExprRule
            ;

keywordExpr : KEYWORD '(' STRING ')' ('*' NUMBER)? ;

fieldExpr   : field compOp value
            | field IN '[' valueList ']'
            ;

field       : SENTIMENT | SOURCE | CATEGORY | COUNTRY | PUBLISHED_AT | SCORE ;

compOp      : EQ | NEQ | GTE | LTE | GT | LT ;

value       : STRING | NUMBER ;

valueList   : value (',' value)* ;

// === Lexer Rules ===

KEYWORD      : 'keyword' ;
AND          : 'AND' ;
OR           : 'OR' ;
IN           : 'IN' ;

SENTIMENT    : 'sentiment' ;
SOURCE       : 'source' ;
CATEGORY     : 'category' ;
COUNTRY      : 'country' ;
PUBLISHED_AT : 'publishedAt' ;
SCORE        : 'score' ;

EQ           : '==' ;
NEQ          : '!=' ;
NOT          : '!'   ;
GTE          : '>=' ;
LTE          : '<=' ;
GT           : '>' ;
LT           : '<' ;

NUMBER       : [0-9]+ ('.' [0-9]+)? ;
STRING       : '"' (~["\r\n])* '"' ;
WS           : [ \t\r\n]+ -> skip ;
