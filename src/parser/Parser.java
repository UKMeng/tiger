package parser;

import ast.Ast;
import ast.PrettyPrinter;
import lexer.Lexer;
import lexer.Token;
import util.Todo;
import util.Trace;

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
            if(next != null) {
                current = next;
                next = null;
            } else {
                current = lexer.nextToken();
            }
    }

    private void eatToken(Token.Kind kind) throws Exception {
        if (kind.equals(current.kind)) {
            //if (current.rowNum == -1) return;
            advance();
            return;
        }
        System.out.println(STR."Expects: \{kind}");
        System.out.println(STR."But got: \{current.kind}");
        error("syntax error");
    }

    private void error(String errMsg) throws Exception {
        System.out.println(STR."\{inputFileName}:\{current.rowNum}:\{current.colNum} Error: \{errMsg}");
        System.out.println(STR."\{lexer.getCurrentLine()}");
        for (int i = 1; i < current.colNum; i++) {
            System.out.print(" ");
        }
        System.out.println(STR."^");
        advance();
        throw new Exception();
    }

    private void error(String errMsg, boolean flag) {
        if (flag) {
            System.out.println(STR."\{inputFileName}: Error: \{errMsg}, compilation aborting...\n");
            exit(1);
        }
    }

    private void errorShift(String errMsg, Token.Kind kind) {
        System.out.println(errMsg);
        //System.out.println();
        next = current;
        current = new Token(kind, -1, -1);
    }

    // ////////////////////////////////////////////////////////////
    // The followings are methods for parsing.

    // A bunch of parsing methods to parse expressions.
    // The messy parts are to deal with precedence and associativity.

    // ExpList -> Exp ExpRest*
    // ->
    // ExpRest -> , Exp
    private void parseExpList() throws Exception {
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
    private void parseAtomExp() throws Exception {
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
    private void parseNotExp() throws Exception {
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
    private void parseTimesExp() throws Exception {
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
    private void parseAddSubExp() throws Exception {
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
    private void parseLtExp() throws Exception {
        parseAddSubExp();
        if (current.kind.equals(Token.Kind.ADD) || current.kind.equals(Token.Kind.MINUS)) {
            advance();
            parseAddSubExp();
        }
        return;
    }

    // AndExp -> LtExp < LtExp
    // -> LtExp
    private void parseAndExp() throws Exception {
        parseLtExp();
        if (current.kind.equals(Token.Kind.LESS)) {
            advance();
            parseLtExp();
        }
        return;
    }

    // Exp -> AndExp && AndExp
    // -> AndExp
    private void parseExp() throws Exception {
        parseAndExp();
        if (current.kind.equals(Token.Kind.AND)) {
            advance();
            parseAndExp();
        }
        return;
    }


    private static boolean needRbrace = false;

    // Statement -> { Statement* }
    // -> if ( Exp ) Statement else Statement
    // -> while ( Exp ) Statement
    // -> System.out.println ( Exp ) ;
    // -> id = Exp ;
    // -> id [ Exp ]= Exp ;
    private void parseStatement() {
        switch(current.kind) {
            case LBRACE: {
                try {
                    needRbrace = true;
                    advance();
                    parseStatements();
                } catch (Exception e) {
                    errorShift("Parse Error in parseStatement(LBRACE)", Token.Kind.RBRACE);
                }
                try {
                    needRbrace = false;
                    eatToken(Token.Kind.RBRACE);
                    return;
                } catch (Exception e) {
                    //System.out.println("Parse Error in parseStatement(LBRACE)");
                }
            }
            case IF:
                try {
                    advance();
                    eatToken(Token.Kind.LPAREN);
                    parseExp();
                    eatToken(Token.Kind.RPAREN);
                    parseStatement();
                    if (current.kind.equals(Token.Kind.ELSE)) {
                        eatToken(Token.Kind.ELSE);
                        parseStatement();
                    }
                    return;
                } catch (Exception e) {
                    errorShift("Parse Error in parseStatement(IF)", Token.Kind.SEMICOLON);
                }

            case WHILE:
                try {
                    advance();
                    eatToken(Token.Kind.LPAREN);
                    parseExp();
                    eatToken(Token.Kind.RPAREN);
                    parseStatement();
                    return;
                } catch (Exception e) {
                    errorShift("Parse Error in parseStatement(WHILE)", Token.Kind.SEMICOLON);
                }

            case ID:
                try {
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
                } catch (Exception e) {
                    //System.out.println("Parse Error in parseStatement(ID)");
                    //errorShift("Parse Error in parseStatement(ID)", Token.Kind.SEMICOLON);
                }
        }
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
        if (needRbrace && !current.kind.equals(Token.Kind.RBRACE)) {
            errorShift("Parse Error in parseStatements", Token.Kind.RBRACE);
        }
        return;
    }

    // Type -> int []
    // -> boolean
    // -> int
    // -> id
    private void parseType() throws Exception {
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
        try {
            parseType();
            eatToken(Token.Kind.ID);
            eatToken(Token.Kind.SEMICOLON);
        } catch (Exception e) {
            //errorShift("Parse Error in parseVarDecl", Token.Kind.SEMICOLON);
        }
    }

    // VarDecls -> VarDecl VarDecls
    // ->
    private void parseVarDecls() {
        while (current.kind.equals(Token.Kind.INT) ||
                current.kind.equals(Token.Kind.BOOLEAN) ||
                current.kind.equals(Token.Kind.ID)) {
            if (current.kind.equals(Token.Kind.ID)) {
                if(!lexer.peekNextToken().kind.equals(Token.Kind.ID)) {
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
    private void parseFormalList() throws Exception {
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
        try {
            eatToken(Token.Kind.PUBLIC);
            parseType();
            eatToken(Token.Kind.ID);
            eatToken(Token.Kind.LPAREN);
            parseFormalList();
            eatToken(Token.Kind.RPAREN);
        } catch (Exception e) {
            errorShift("Parse Error in parseMethod", Token.Kind.LBRACE);
        }
        try {
            eatToken(Token.Kind.LBRACE);
            parseVarDecls();
            parseStatements();
            eatToken(Token.Kind.RETURN);
            parseExp();
            eatToken(Token.Kind.SEMICOLON);
            eatToken(Token.Kind.RBRACE);
        } catch (Exception e) {
            errorShift("Parse Error in parseMethod", Token.Kind.RBRACE);
        }
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
        try {
            eatToken(Token.Kind.CLASS);
            eatToken(Token.Kind.ID);
            if (current.kind.equals(Token.Kind.EXTENDS)) {
                eatToken(Token.Kind.EXTENDS);
                eatToken(Token.Kind.ID);
            }
        } catch (Exception e) {
            errorShift("Parse Error in parseClassDecl", Token.Kind.LBRACE);
        }
        try {
            eatToken(Token.Kind.LBRACE);
            parseVarDecls();
            if (current.kind.equals(Token.Kind.PUBLIC)) {
                parseMethodDecls();
            }
        } catch (Exception e) {
            errorShift("Parse Error in parseClassDecl", Token.Kind.RBRACE);
        }
        try {
            eatToken(Token.Kind.RBRACE);
        } catch (Exception e) {
           // errorShift("Parse Error in parseClassDecl", Token.Kind.RBRACE);
        }

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
        try {
            eatToken(Token.Kind.CLASS);
            eatToken(Token.Kind.ID);
        } catch (Exception e) {
            errorShift("Parse Error in parseMainClass", Token.Kind.LBRACE);
        }
        try {
            eatToken(Token.Kind.LBRACE);
            eatToken(Token.Kind.PUBLIC);
            eatToken(Token.Kind.STATIC);
            parseType();
            eatToken(Token.Kind.ID);
            eatToken(Token.Kind.LPAREN);
            parseType();
            eatToken(Token.Kind.ID);
            eatToken(Token.Kind.RPAREN);
        } catch (Exception e) {
            errorShift("Parse Error in parseMainClass", Token.Kind.LBRACE);
        }
        try {
            eatToken(Token.Kind.LBRACE);
            parseStatement();
            eatToken(Token.Kind.RBRACE);
        } catch (Exception e) {
            errorShift("Parse Error in parseMainClass", Token.Kind.RBRACE);
        }
        try {
            eatToken(Token.Kind.RBRACE);
        } catch (Exception e) {
            System.out.println("Parse Error in parseMainClass");
            // errorShift("Parse Error in parseMainClass", Token.Kind.RBRACE);
        }

    }

    // Program -> MainClass ClassDecl*
    private Ast.Program.T parseProgram(Object obj) {
        parseMainClass();
        parseClassDecls();
        eatToken(Token.Kind.EOF);
        return null;
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

    public Ast.Program.T parse() {
        initParser();
        Trace<Object, Ast.Program.T> trace =
                new Trace<>("parser.Parser.parse",
                        this::parseProgram,
                        this.inputFileName,
                        (s) -> System.out.println(STR."parsing: \{s}"),
                        new PrettyPrinter()::ppProgram);
        Ast.Program.T ast = trace.doit();
        finalizeParser();
        return ast;
    }
}
