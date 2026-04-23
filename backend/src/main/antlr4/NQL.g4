grammar NQL;

// === Parser Rules ===

query       : expr (groupByClause)? (limitClause)? EOF ;

expr        : expr AND expr          # andExpr
            | expr OR expr           # orExpr
            | NOT expr               # notExpr
            | '(' expr ')'           # groupExpr
            | keywordExpr            # kwExpr
            | fieldExpr              # fieldExprRule
            | STAR                   # matchAllExpr
            ;

groupByClause : GROUP BY field ;

limitClause : LIMIT NUMBER ;

keywordExpr : KEYWORD '(' STRING ')' ('*' NUMBER)? (boostFunc)? ;

boostFunc   : BOOST boostType '(' boostArg ')' ;

boostType   : RECENCY | TREND | POPULARITY ;

boostArg    : field | field ',' NUMBER ;  // field 또는 field, number

fieldExpr   : field compOp value
            | field IN '[' valueList ']'
            | field BETWEEN value AND value
            ;

field       : SENTIMENT | SOURCE | CATEGORY | COUNTRY | PUBLISHED_AT | SCORE ;

compOp      : EQ | NEQ | GTE | LTE | GT | LT | CONTAINS | LIKE ;

value       : STRING | NUMBER ;

valueList   : value (',' value)* ;

// === Lexer Rules ===

KEYWORD      : 'keyword' ;
AND          : 'AND' ;
OR           : 'OR' ;
IN           : 'IN' ;
BETWEEN      : 'BETWEEN' ;
CONTAINS     : 'CONTAINS' ;
LIKE         : 'LIKE' ;
GROUP        : 'GROUP' ;
BY           : 'BY' ;
LIMIT        : 'LIMIT' ;
BOOST        : 'BOOST' ;
RECENCY      : 'recency' ;
TREND        : 'trend' ;
POPULARITY   : 'popularity' ;

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

STAR         : '*' ;
NUMBER       : [0-9]+ ('.' [0-9]+)? ;
STRING       : '"' (~["\r\n])* '"' ;
WS           : [ \t\r\n]+ -> skip ;
