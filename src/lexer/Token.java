package lexer;

// Lab 1, exercise 8: read the MiniJava specification carefully,
// and fill in other possible tokens.
public class Token {
    // alphabetically ordered
    public enum Kind {
        ADD,
        MINUS,
        TIMES,
        DIVIDE,
        CLASS,
        COMMA,
        DOT,
        EOF,
        ID,
        INT,
        BOOLEAN,
        LBRACKET,
        LBRACE,
        LENGTH,
        LPAREN,
        NEW,
        RBRACKET,
        RBRACE,
        RPAREN,
        SEMICOLON,
        PUBLIC,
        STATIC,
        VOID,
        STRING,
        IF,
        ELSE,
        WHILE,
        RETURN,
        ASSIGN,
        INTEGER_LITERAL,
        LESS,
        GREATER,
        NOT,
        AND,
        THIS,
        TRUE,
        FALSE,
        EXTENDS,
    }

    // kind of the token
    public Kind kind;
    // extra lexeme for this token, if any
    public String lexeme;
    // position of the token in the source file: (row, column)
    public Integer rowNum;
    public Integer colNum;


    public Token(Kind kind,
                 Integer rowNum,
                 Integer colNum) {
        this.kind = kind;
        this.rowNum = rowNum;
        this.colNum = colNum;
    }

    public Token(Kind kind,
                 String lexeme,
                 Integer rowNum,
                 Integer colNum) {
        this.kind = kind;
        this.lexeme = lexeme;
        this.rowNum = rowNum;
        this.colNum = colNum;
    }

    @Override
    public String toString() {
        String s;

        s = STR."""
        : \{(this.lexeme == null) ? "<NONE>" : this.lexeme}
        : at row \{this.rowNum == null ? "<null>" : rowNum.toString()}
        : at column \{this.colNum == null ? "<null>" : colNum.toString()}
        """;
        return this.kind + s;
    }
}