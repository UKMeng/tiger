package parser;

import ast.Ast;
import ast.PrettyPrinter;
import lexer.Lexer;
import lexer.Token;
import util.Id;
import util.Todo;
import util.Trace;
import util.Tuple;

import java.util.ArrayList;
import java.util.List;

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

    private String eatToken(Token.Kind kind) throws Exception {
        if (kind.equals(current.kind)) {
            //if (current.rowNum == -1) return;
            String ret = "";
            if (kind.equals(Token.Kind.ID) || kind.equals(Token.Kind.INTEGER_LITERAL)) {
                 ret = current.lexeme;
            }
            advance();
            return ret;
        }
        System.out.println(STR."Expects: \{kind}");
        System.out.println(STR."But got: \{current.kind}");
        error("syntax error");
        return "";
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
    private List<Ast.Exp.T> parseExpList() throws Exception {
        List<Ast.Exp.T> expList = new ArrayList<>();
        if (current.kind.equals(Token.Kind.RPAREN))
            return expList;
        expList.add(parseExp());
        while (current.kind.equals(Token.Kind.COMMA)) {
            advance();
            expList.add(parseExp());
        }
        return expList;
    }

    // AtomExp -> (exp)
    // -> INTEGER_LITERAL
    // -> true
    // -> false
    // -> this
    // -> id
    // -> new int [exp]
    // -> new id ()
    private Ast.Exp.T parseAtomExp() throws Exception {
        switch (current.kind) {
            case LPAREN:
                advance();
                Ast.Exp.T exp = parseExp();
                eatToken(Token.Kind.RPAREN);
                return exp;
            case ID:
                //advance();
                String id = eatToken(Token.Kind.ID);
                return new Ast.Exp.ExpId(new Ast.AstId(Id.newName(id)));
            case NEW: {
                advance();
                switch (current.kind) {
                    case INT:
                        advance();
                        eatToken(Token.Kind.LBRACKET);
                        Ast.Exp.T expIA = parseExp();
                        eatToken(Token.Kind.RBRACKET);
                        return new Ast.Exp.NewIntArray(expIA);
                    case ID:
                        String objectId = eatToken(Token.Kind.ID);
                        eatToken(Token.Kind.LPAREN);
                        eatToken(Token.Kind.RPAREN);
                        return new Ast.Exp.NewObject(Id.newName(objectId));
                    default:
                        throw new Todo();
                }
            }
            case INTEGER_LITERAL: {
                //advance();
                int num = Integer.parseInt(eatToken(Token.Kind.INTEGER_LITERAL));
                return new Ast.Exp.Num(num);
            }
            case TRUE: {
                advance();
                return new Ast.Exp.True();
            }
            case FALSE: {
                advance();
                return new Ast.Exp.False();
            }
            case THIS: {
                advance();
                return new Ast.Exp.This();
            }
            default:
                throw new Todo();
        }
    }

    // NotExp -> AtomExp
    // -> AtomExp .id (expList)
    // -> AtomExp [exp]
    // -> AtomExp .length
    private Ast.Exp.T parseNotExp() throws Exception {
        Ast.Exp.T atom = parseAtomExp();
        while (current.kind.equals(Token.Kind.DOT) ||
                current.kind.equals(Token.Kind.LBRACKET)) {
            if (current.kind.equals(Token.Kind.DOT)) {
                advance();
                if (current.kind.equals(Token.Kind.LENGTH)) {
                    advance();
                    return new Ast.Exp.Length(atom);
                }
                String methodId = eatToken(Token.Kind.ID);
                eatToken(Token.Kind.LPAREN);
                atom = new Ast.Exp.Call(
                        atom,
                        new Ast.AstId(Id.newName(methodId)),
                        parseExpList(),
                        new Tuple.One<>(),
                        new Tuple.One<>());
                eatToken(Token.Kind.RPAREN);
            } else {
                advance();
                Ast.Exp.T index = parseExp();
                eatToken(Token.Kind.RBRACKET);
                return new Ast.Exp.ArraySelect(atom, index);
            }
        }
        return atom;
    }

    // TimesExp -> ! TimesExp
    // -> NotExp
    private Ast.Exp.T parseTimesExp() throws Exception {
        if (current.kind.equals(Token.Kind.NOT)) {
            advance();
            return new Ast.Exp.Uop("!", parseTimesExp());
        } else {
            return parseNotExp();
        }
    }

    // AddSubExp -> TimesExp * TimesExp
    // -> TimesExp
    private Ast.Exp.T parseAddSubExp() throws Exception {
        Ast.Exp.T left = parseTimesExp();
        Ast.Exp.T right = null;
        if (current.kind.equals(Token.Kind.TIMES)) {
            advance();
            right = parseTimesExp();
        }
        return right == null? left : new Ast.Exp.Bop(left, "*", right);
    }

    // LtExp -> AddSubExp + AddSubExp
    // -> AddSubExp - AddSubExp
    // -> AddSubExp
    private Ast.Exp.T parseLtExp() throws Exception {
        Ast.Exp.T left = parseAddSubExp();
        Ast.Exp.T right = null;
        String op = "";
        if (current.kind.equals(Token.Kind.ADD) || current.kind.equals(Token.Kind.MINUS)) {
            if (current.kind.equals(Token.Kind.ADD)) {
                op = "+";
            } else {
                op = "-";
            }
            advance();
            right = parseAddSubExp();
        }
        return right == null? left : new Ast.Exp.Bop(left, op, right);
    }

    // AndExp -> LtExp < LtExp
    // -> LtExp
    private Ast.Exp.T parseAndExp() throws Exception {
        Ast.Exp.T left = parseLtExp();
        Ast.Exp.T right = null;
        if (current.kind.equals(Token.Kind.LESS)) {
            advance();
            right = parseLtExp();
        }
        return right == null? left : new Ast.Exp.Bop(left, "<", right);
    }

    // Exp -> AndExp && AndExp
    // -> AndExp
    private Ast.Exp.T parseExp() throws Exception {
        Ast.Exp.T left = parseAndExp();
        Ast.Exp.T right = null;
        if (current.kind.equals(Token.Kind.AND)) {
            advance();
            right = parseAndExp();
        }
        return right == null? left : new Ast.Exp.BopBool(left, "&&", right);
    }


    private static boolean needRbrace = false;

    // Statement -> { Statement* }
    // -> if ( Exp ) Statement else Statement
    // -> while ( Exp ) Statement
    // -> System.out.println ( Exp ) ;
    // -> id = Exp ;
    // -> id [ Exp ]= Exp ;
    private Ast.Stm.T parseStatement() {
        switch(current.kind) {
            case LBRACE: {
                List<Ast.Stm.T> stms = new ArrayList<>();
                try {
                    needRbrace = true;
                    advance();
                    stms = parseStatements();
                } catch (Exception e) {
                    errorShift("Parse Error in parseStatement(LBRACE)", Token.Kind.RBRACE);
                }
                try {
                    needRbrace = false;
                    eatToken(Token.Kind.RBRACE);
                } catch (Exception e) {
                    //System.out.println("Parse Error in parseStatement(LBRACE)");
                }
                return new Ast.Stm.Block(stms);
            }
            case IF:
                try {
                    advance();
                    eatToken(Token.Kind.LPAREN);
                    Ast.Exp.T cond = parseExp();
                    eatToken(Token.Kind.RPAREN);
                    Ast.Stm.T thenn = parseStatement();
                    Ast.Stm.T elsee = null;
                    if (current.kind.equals(Token.Kind.ELSE)) {
                        eatToken(Token.Kind.ELSE);
                        elsee = parseStatement();
                    }
                    return new Ast.Stm.If(cond, thenn, elsee);
                } catch (Exception e) {
                    errorShift("Parse Error in parseStatement(IF)", Token.Kind.SEMICOLON);
                }

            case WHILE:
                try {
                    advance();
                    eatToken(Token.Kind.LPAREN);
                    Ast.Exp.T cond = parseExp();
                    eatToken(Token.Kind.RPAREN);
                    Ast.Stm.T body = parseStatement();
                    return new Ast.Stm.While(cond, body);
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
                                Ast.Exp.T exp = parseExp();
                                eatToken(Token.Kind.RPAREN);
                                eatToken(Token.Kind.SEMICOLON);
                                return new Ast.Stm.Print(exp);
                            }
                        }
                    }
                    //advance();
                    String leftId = eatToken(Token.Kind.ID);
                    if (current.kind.equals(Token.Kind.LBRACKET)) {
                        advance();
                        Ast.Exp.T index = parseExp();
                        eatToken(Token.Kind.RBRACKET);
                        eatToken(Token.Kind.ASSIGN);
                        Ast.Exp.T exp = parseExp();
                        eatToken(Token.Kind.SEMICOLON);
                        return new Ast.Stm.AssignArray(new Ast.AstId(Id.newName(leftId)), index, exp);
                    } else if (current.kind.equals(Token.Kind.ASSIGN)) {
                        advance();
                        Ast.Exp.T exp = parseExp();
                        eatToken(Token.Kind.SEMICOLON);
                        return new Ast.Stm.Assign(new Ast.AstId(Id.newName(leftId)), exp);
                    }
                } catch (Exception e) {
                    //System.out.println("Parse Error in parseStatement(ID)");
                    //errorShift("Parse Error in parseStatement(ID)", Token.Kind.SEMICOLON);
                }
        }
        return null;
    }

    // Statements -> Statement Statements
    // ->
    private List<Ast.Stm.T> parseStatements() {
        List<Ast.Stm.T> stms = new ArrayList<>();
        while (current.kind.equals(Token.Kind.LBRACE) ||
                current.kind.equals(Token.Kind.IF) ||
                current.kind.equals(Token.Kind.WHILE) ||
                current.kind.equals(Token.Kind.ID)) {
            stms.add(parseStatement());
        }
        if (needRbrace && !current.kind.equals(Token.Kind.RBRACE)) {
            errorShift("Parse Error in parseStatements", Token.Kind.RBRACE);
        }
        return stms;
    }

    // Type -> int []
    // -> boolean
    // -> int
    // -> id
    private Ast.Type.T parseType() throws Exception {
        switch (current.kind) {
            case INT:
                boolean isArray = false;
                advance();
                if (current.kind.equals(Token.Kind.LBRACKET)) {
                    advance();
                    eatToken(Token.Kind.RBRACKET);
                    isArray = true;
                }
                return isArray ? Ast.Type.getIntArray(): Ast.Type.getInt();
            case STRING:
                advance();
                if (current.kind.equals(Token.Kind.LBRACKET)) {
                    advance();
                    eatToken(Token.Kind.RBRACKET);
                }
                return null; // Todo: Add String type
            case BOOLEAN:
                advance();
                return Ast.Type.getBool();
            case ID:
                String id = eatToken(Token.Kind.ID);
                //advance();
                return Ast.Type.getClassType(Id.newName(id));
            case VOID:
                advance();
                return null; // Todo: Add Void Type
            default:
                throw new Todo();
        }
    }

    // VarDecl -> Type id ;
    private Ast.Dec.T parseVarDecl() {
        // to parse the "Type" non-terminal in this method,
        // instead of writing a fresh one.
        try {
            Ast.Type.T type = parseType();
            String id = eatToken(Token.Kind.ID);
            eatToken(Token.Kind.SEMICOLON);
            return new Ast.Dec.Singleton(type, new Ast.AstId(Id.newName(id)));
        } catch (Exception e) {
            //errorShift("Parse Error in parseVarDecl", Token.Kind.SEMICOLON);
        }
        return null;
    }

    // VarDecls -> VarDecl VarDecls
    // ->
    private List<Ast.Dec.T> parseVarDecls() {
        List<Ast.Dec.T> decs = new ArrayList<>();
        while (current.kind.equals(Token.Kind.INT) ||
                current.kind.equals(Token.Kind.BOOLEAN) ||
                current.kind.equals(Token.Kind.ID)) {
            if (current.kind.equals(Token.Kind.ID)) {
                if(!lexer.peekNextToken().kind.equals(Token.Kind.ID)) {
                    return decs;
                }
            }
            decs.add(parseVarDecl());
        }
        return decs;
    }

    // FormalList -> Type id FormalRest*
    // ->
    // FormalRest -> , Type id
    private List<Ast.Dec.T> parseFormalList() throws Exception {
        List<Ast.Dec.T> formals = new ArrayList<>();
        if (current.kind == Token.Kind.RPAREN) return formals;
        if (current.kind == Token.Kind.COMMA) advance();
        Ast.Type.T type = parseType();
        String id = eatToken(Token.Kind.ID);
        formals.add(new Ast.Dec.Singleton(type, new Ast.AstId(Id.newName(id))));
        formals.addAll(parseFormalList());
        return formals;
    }

    // Method -> public Type id ( FormalList )
    // { VarDecl* Statement* return Exp ;}
    private Ast.Method.T parseMethod() {
        // to parse a method.
        Ast.Type.T retType = null;
        String methodID = "";
        List<Ast.Dec.T> formals = new ArrayList<>();
        List<Ast.Dec.T> locals = new ArrayList<>();
        List<Ast.Stm.T> stms = new ArrayList<>();
        Ast.Exp.T retExp = null;
        try {
            eatToken(Token.Kind.PUBLIC);
            retType = parseType();
            methodID = eatToken(Token.Kind.ID);
            eatToken(Token.Kind.LPAREN);
            formals = parseFormalList();
            eatToken(Token.Kind.RPAREN);
        } catch (Exception e) {
            errorShift("Parse Error in parseMethod", Token.Kind.LBRACE);
        }
        try {
            eatToken(Token.Kind.LBRACE);
            locals = parseVarDecls();
            stms = parseStatements();
            eatToken(Token.Kind.RETURN);
            retExp = parseExp();
            eatToken(Token.Kind.SEMICOLON);
            eatToken(Token.Kind.RBRACE);
        } catch (Exception e) {
            errorShift("Parse Error in parseMethod", Token.Kind.RBRACE);
        }
        return new Ast.Method.Singleton(
                retType,
                new Ast.AstId(Id.newName(methodID)),
                formals,
                locals,
                stms,
                retExp
        );
    }

    // MethodDecls -> MethodDecl MethodDecls
    // ->
    private List<Ast.Method.T> parseMethodDecls() {
        List<Ast.Method.T> methods = new ArrayList<>();
        while(current.kind.equals(Token.Kind.PUBLIC)) {
            methods.add(parseMethod());
        }
        return methods;
    }

    // ClassDecl -> class id { VarDecl* MethodDecl* }
    // -> class id extends id { VarDecl* MethodDecl* }
    private Ast.Class.T parseClassDecl() {
        String classID = "";
        String extendClassID = "";
        List<Ast.Dec.T> decs = new ArrayList<>();
        List<Ast.Method.T> methods = new ArrayList<>();

        try {
            eatToken(Token.Kind.CLASS);
            classID = eatToken(Token.Kind.ID);
            if (current.kind.equals(Token.Kind.EXTENDS)) {
                eatToken(Token.Kind.EXTENDS);
                extendClassID = eatToken(Token.Kind.ID);
            }
        } catch (Exception e) {
            errorShift("Parse Error in parseClassDecl", Token.Kind.LBRACE);
        }
        try {
            eatToken(Token.Kind.LBRACE);
            decs = parseVarDecls();
            if (current.kind.equals(Token.Kind.PUBLIC)) {
                methods = parseMethodDecls();
            }
        } catch (Exception e) {
            errorShift("Parse Error in parseClassDecl", Token.Kind.RBRACE);
        }
        try {
            eatToken(Token.Kind.RBRACE);
        } catch (Exception e) {
           // errorShift("Parse Error in parseClassDecl", Token.Kind.RBRACE);
        }
        return new Ast.Class.Singleton(
                Id.newName(classID),
                extendClassID.isEmpty() ? null : Id.newName(extendClassID),
                decs,
                methods,
                new Tuple.One<Ast.Class.T>()); // todo: Add Parent Class
    }

    // ClassDecls -> ClassDecl ClassDecls
    // ->
    private List<Ast.Class.T> parseClassDecls() {
        List<Ast.Class.T> classes = new ArrayList<>();
        while (current.kind.equals(Token.Kind.CLASS)) {
            classes.add(parseClassDecl());
        }
        return classes;
    }

    // MainClass -> class id {
    //   public static void main ( String [] id ) {
    //     Statement
    //   }
    // }
    private Ast.MainClass.T parseMainClass() {
        String classID = "";
        String argID = "";
        Ast.Stm.T stm = null;
        try {
            eatToken(Token.Kind.CLASS);
            classID = eatToken(Token.Kind.ID);
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
            argID = eatToken(Token.Kind.ID);
            eatToken(Token.Kind.RPAREN);
        } catch (Exception e) {
            errorShift("Parse Error in parseMainClass", Token.Kind.LBRACE);
        }
        try {
            eatToken(Token.Kind.LBRACE);
            stm = parseStatement();
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
        return new Ast.MainClass.Singleton(
                Id.newName(classID),
                new Ast.AstId(Id.newName(argID)),
                stm);
    }

    // Program -> MainClass ClassDecl*
    private Ast.Program.T parseProgram(Object obj) {
        Ast.MainClass.T mainClass = parseMainClass();
        List<Ast.Class.T> classes = parseClassDecls();
        try {
            eatToken(Token.Kind.EOF);
        } catch (Exception e) {
            errorShift("Parse Error in parseProgram", Token.Kind.EOF);
        }
        return new Ast.Program.Singleton(mainClass, classes);
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
