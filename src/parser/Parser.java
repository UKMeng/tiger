package parser;

import lexer.Lexer;
import lexer.Token;
import util.Todo;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.HashSet;

import static java.lang.System.exit;

public class Parser {
    String inputFileName;
    BufferedInputStream inputStream;
    Lexer lexer;
    Token current;
    Token next = null;

    public Parser(String fileName) {
        this.inputFileName = fileName;
    }

    // /////////////////////////////////////////////
    // utility methods to connect the lexer and the parser.
    private void advance() {
        if (next == null) {
            current = lexer.nextToken();
            next = lexer.nextToken();
        } else {
            current = next;
            next = lexer.nextToken();
        }
    }

    private void eatToken(Token.Kind kind) {
        if (kind.equals(current.kind)) {
            advance();
            return;
        }
        System.out.println(STR."Expects: \{kind}");
        System.out.println(STR."But got: \{current.kind}");
        error("syntax error");
    }

    private void error(String errMsg) {

        System.out.println(STR."\{inputFileName}:\{current.rowNum}:\{current.colNum} Error: \{errMsg}\n");
        System.out.println(STR."\{lexer.getCurrentLine()}");
        for (int i = 1; i < current.colNum; i++) {
            System.out.print(" ");
        }
        System.out.println(STR."^");
        exit(1);
    }

    private void error(String errMsg, boolean flag) {
        if (flag) {
            System.out.println(STR."\{inputFileName}: Error: \{errMsg}, compilation aborting...\n");
            exit(1);
        }
    }

    // ////////////////////////////////////////////////////////////
    // The followings are methods for parsing.

    // A bunch of parsing methods to parse expressions.
    // The messy parts are to deal with precedence and associativity.

    // ExpList -> Exp ExpRest*
    // ->
    // ExpRest -> , Exp
    private void parseExpList() {
        if (current.kind.equals(Token.Kind.RPAREN))
            return;
        parseExp();
        while (current.kind.equals(Token.Kind.COMMA)) {
            advance();
            parseExp();
        }
        return;
    }

    // AtomExp -> (exp)
    // -> INTEGER_LITERAL
    // -> true
    // -> false
    // -> this
    // -> id
    // -> new int [exp]
    // -> new id ()
    private void parseAtomExp() {
        switch (current.kind) {
            case LPAREN:
                advance();
                parseExp();
                eatToken(Token.Kind.RPAREN);
                return;
            case ID:
                advance();
                return;
            case NEW: {
                advance();
                switch (current.kind) {
                    case INT:
                        advance();
                        eatToken(Token.Kind.LBRACKET);
                        parseExp();
                        eatToken(Token.Kind.RBRACKET);
                        return;
                    case ID:
                        advance();
                        eatToken(Token.Kind.LPAREN);
                        eatToken(Token.Kind.RPAREN);
                        return;
                    default:
                        throw new Todo();
                }
            }
            case INTEGER_LITERAL: {
                advance();
                return;
            }
            case TRUE: {
                advance();
                return;
            }
            case FALSE: {
                advance();
                return;
            }
            case THIS: {
                advance();
                return;
            }
            default:
                throw new Todo();
        }
    }

    // NotExp -> AtomExp
    // -> AtomExp .id (expList)
    // -> AtomExp [exp]
    // -> AtomExp .length
    private void parseNotExp() {
        parseAtomExp();
        while (current.kind.equals(Token.Kind.DOT) ||
                current.kind.equals(Token.Kind.LBRACKET)) {
            if (current.kind.equals(Token.Kind.DOT)) {
                advance();
                if (current.kind.equals(Token.Kind.LENGTH)) {
                    advance();
                    return;
                }
                eatToken(Token.Kind.ID);
                eatToken(Token.Kind.LPAREN);
                parseExpList();
                eatToken(Token.Kind.RPAREN);
            } else {
                advance();
                parseExp();
                eatToken(Token.Kind.RBRACKET);
            }
        }
        return;
    }

    // TimesExp -> ! TimesExp
    // -> NotExp
    private void parseTimesExp() {
        if (current.kind.equals(Token.Kind.NOT)) {
            advance();
            parseTimesExp();
        } else {
            parseNotExp();
        }
        return;
    }

    // AddSubExp -> TimesExp * TimesExp
    // -> TimesExp
    private void parseAddSubExp() {
        parseTimesExp();
        if (current.kind.equals(Token.Kind.TIMES)) {
            advance();
            parseTimesExp();
        }
        return;
    }

    // LtExp -> AddSubExp + AddSubExp
    // -> AddSubExp - AddSubExp
    // -> AddSubExp
    private void parseLtExp() {
        parseAddSubExp();
        if (current.kind.equals(Token.Kind.ADD) || current.kind.equals(Token.Kind.MINUS)) {
            advance();
            parseAddSubExp();
        }
        return;
    }

    // AndExp -> LtExp < LtExp
    // -> LtExp
    private void parseAndExp() {
        parseLtExp();
        if (current.kind.equals(Token.Kind.LESS)) {
            advance();
            parseLtExp();
        }
        return;
    }

    // Exp -> AndExp && AndExp
    // -> AndExp
    private void parseExp() {
        parseAndExp();
        if (current.kind.equals(Token.Kind.AND)) {
            advance();
            parseAndExp();
        }
        return;
    }

    // Statement -> { Statement* }
    // -> if ( Exp ) Statement else Statement
    // -> while ( Exp ) Statement
    // -> System.out.println ( Exp ) ;
    // -> id = Exp ;
    // -> id [ Exp ]= Exp ;
    private void parseStatement() {
        switch(current.kind) {
            case LBRACE:
                advance();
                parseStatements();
                eatToken(Token.Kind.RBRACE);
                return;
            case IF:
                advance();
                eatToken(Token.Kind.LPAREN);
                parseExp();
                eatToken(Token.Kind.RPAREN);
                parseStatement();
                if(current.kind.equals(Token.Kind.ELSE)) {
                    eatToken(Token.Kind.ELSE);
                    parseStatement();
                }
                return;
            case WHILE:
                advance();
                eatToken(Token.Kind.LPAREN);
                parseExp();
                eatToken(Token.Kind.RPAREN);
                parseStatement();
                return;
            case ID:
                if (current.lexeme.equals("System")) {
                    advance();
                    eatToken(Token.Kind.DOT);
                    if (current.lexeme.equals("out")) {
                        advance();
                        eatToken(Token.Kind.DOT);
                        if (current.lexeme.equals("println")) {
                            advance();
                            eatToken(Token.Kind.LPAREN);
                            parseExp();
                            eatToken(Token.Kind.RPAREN);
                            eatToken(Token.Kind.SEMICOLON);
                            return;
                        }
                    }
                }
                advance();
                if (current.kind.equals(Token.Kind.LBRACKET)) {
                    advance();
                    parseExp();
                    eatToken(Token.Kind.RBRACKET);
                    eatToken(Token.Kind.ASSIGN);
                    parseExp();
                    eatToken(Token.Kind.SEMICOLON);
                    return;
                } else if (current.kind.equals(Token.Kind.ASSIGN)) {
                    advance();
                    parseExp();
                    eatToken(Token.Kind.SEMICOLON);
                    return;
                }
        }
        throw new Todo();
    }

    // Statements -> Statement Statements
    // ->
    private void parseStatements() {
        while (current.kind.equals(Token.Kind.LBRACE) ||
                current.kind.equals(Token.Kind.IF) ||
                current.kind.equals(Token.Kind.WHILE) ||
                current.kind.equals(Token.Kind.ID)) {
            parseStatement();
        }
        return;
    }

    // Type -> int []
    // -> boolean
    // -> int
    // -> id
    private void parseType() {
        switch (current.kind) {
            case INT:
                advance();
                if (current.kind.equals(Token.Kind.LBRACKET)) {
                    advance();
                    eatToken(Token.Kind.RBRACKET);
                }
                return;
            case STRING:
                advance();
                if (current.kind.equals(Token.Kind.LBRACKET)) {
                    advance();
                    eatToken(Token.Kind.RBRACKET);
                }
                return;
            case BOOLEAN:
                advance();
                return;
            case ID:
                advance();
                return;
            case VOID:
                advance();
                return;
            default:
                throw new Todo();
        }
    }

    // VarDecl -> Type id ;
    private void parseVarDecl() {
        // to parse the "Type" non-terminal in this method,
        // instead of writing a fresh one.
        parseType();
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.SEMICOLON);
        return;
    }

    // VarDecls -> VarDecl VarDecls
    // ->
    private void parseVarDecls() {
        while (current.kind.equals(Token.Kind.INT) ||
                current.kind.equals(Token.Kind.BOOLEAN) ||
                current.kind.equals(Token.Kind.ID)) {
            if (current.kind.equals(Token.Kind.ID)) {
                if(!next.kind.equals(Token.Kind.ID)) {
                    return;
                }
            }
            parseVarDecl();
        }
        return;
    }

    // FormalList -> Type id FormalRest*
    // ->
    // FormalRest -> , Type id
    private void parseFormalList() {
        if (current.kind == Token.Kind.RPAREN) return;
        if (current.kind == Token.Kind.COMMA) advance();
        parseType();
        eatToken(Token.Kind.ID);
        parseFormalList();
    }

    // Method -> public Type id ( FormalList )
    // { VarDecl* Statement* return Exp ;}
    private void parseMethod() {
        // to parse a method.
        eatToken(Token.Kind.PUBLIC);
        parseType();
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.LPAREN);
        parseFormalList();
        eatToken(Token.Kind.RPAREN);
        eatToken(Token.Kind.LBRACE);
        parseVarDecls();
        parseStatements();
        eatToken(Token.Kind.RETURN);
        parseExp();
        eatToken(Token.Kind.SEMICOLON);
        eatToken(Token.Kind.RBRACE);
    }

    // MethodDecls -> MethodDecl MethodDecls
    // ->
    private void parseMethodDecls() {
        while(current.kind.equals(Token.Kind.PUBLIC)) {
            parseMethod();
        }
    }

    // ClassDecl -> class id { VarDecl* MethodDecl* }
    // -> class id extends id { VarDecl* MethodDecl* }
    private void parseClassDecl() {
        eatToken(Token.Kind.CLASS);
        eatToken(Token.Kind.ID);
        if (current.kind.equals(Token.Kind.EXTENDS)) {
            eatToken(Token.Kind.EXTENDS);
            eatToken(Token.Kind.ID);
        }
        eatToken(Token.Kind.LBRACE);
        parseVarDecls();
        if (current.kind.equals(Token.Kind.PUBLIC)) {
            parseMethodDecls();
        }
        eatToken(Token.Kind.RBRACE);
    }

    // ClassDecls -> ClassDecl ClassDecls
    // ->
    private void parseClassDecls() {
        while (current.kind.equals(Token.Kind.CLASS)) {
            parseClassDecl();
        }
        return;
    }

    // MainClass -> class id {
    //   public static void main ( String [] id ) {
    //     Statement
    //   }
    // }
    private void parseMainClass() {
        // Lab 1. Exercise 11: Fill in the missing code
        // to parse a main class as described by the
        // grammar above.
        eatToken(Token.Kind.CLASS);
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.LBRACE);
        eatToken(Token.Kind.PUBLIC);
        eatToken(Token.Kind.STATIC);
        parseType();
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.LPAREN);
        parseType();
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.RPAREN);
        eatToken(Token.Kind.LBRACE);
        parseStatement();
        eatToken(Token.Kind.RBRACE);
        eatToken(Token.Kind.RBRACE);
    }

    // Program -> MainClass ClassDecl*
    private void parseProgram() {
        parseMainClass();
        parseClassDecls();
        eatToken(Token.Kind.EOF);
        return;
    }

    private void initParser() {
        try {
            this.inputStream = new BufferedInputStream(new FileInputStream(this.inputFileName));
        } catch (Exception e) {
            error(STR."unable to open file", true);
        }

        this.lexer = new Lexer(this.inputFileName, this.inputStream);
        this.current = lexer.nextToken();
    }

    private void finalizeParser() {
        try {
            this.inputStream.close();
        } catch (Exception e) {
            error("unable to close file", true);
        }
    }

    public Object parse() {
        initParser();
        parseProgram();
        finalizeParser();
        return null;
    }
}
