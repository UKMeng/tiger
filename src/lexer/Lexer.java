package lexer;

import util.Todo;

import java.io.InputStream;
import java.util.HashMap;

import static control.Control.Lexer.dumpToken;

public record Lexer(String fileName,
                    InputStream fileStream) {

    private static final HashMap<String, Token.Kind> keywords;
    private static int lastChar = -2;
    private static int rowNum = 1;
    private static int colNum = 0;
    private static StringBuilder currentLine = new StringBuilder();
    private static Token lastToken = null;

    static {
        keywords = new HashMap<>();
        keywords.put("true", Token.Kind.TRUE);
        keywords.put("false", Token.Kind.FALSE);
        keywords.put("this", Token.Kind.THIS);
        keywords.put("class", Token.Kind.CLASS);
        keywords.put("int", Token.Kind.INT);
        keywords.put("boolean", Token.Kind.BOOLEAN);
        keywords.put("new", Token.Kind.NEW);
        keywords.put("public", Token.Kind.PUBLIC);
        keywords.put("static", Token.Kind.STATIC);
        keywords.put("void", Token.Kind.VOID);
        keywords.put("String", Token.Kind.STRING);
        keywords.put("if", Token.Kind.IF);
        keywords.put("else", Token.Kind.ELSE);
        keywords.put("while", Token.Kind.WHILE);
        keywords.put("return", Token.Kind.RETURN);
        keywords.put("length", Token.Kind.LENGTH);
        keywords.put("extends", Token.Kind.EXTENDS);
    }

    private String GetWord(int c) throws Exception {
        StringBuilder id = new StringBuilder();
        while (c != ' ' && c != '\t' && c != '\n' && c != '\r' && c != -1) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                lastChar = c;
                break;
            }
            id.append((char) c);
            c = this.fileStream.read();
            currentLine.append((char) c);
            colNum++;
        }
        return id.toString();
    }

    // When called, return the next token (refer to the code "Token.java")
    // from the input stream.
    // Return TOKEN_EOF when reaching the end of the input stream.
    private Token nextToken0() throws Exception {
        int c;
        if (lastChar != -2) {
            c = lastChar;
            lastChar = -2;
        } else {
            c = this.fileStream.read();
            colNum++;
        }

        currentLine.append((char) c);

        // skip all kinds of "blanks"
        // think carefully about how to set up "colNum" and "rowNum" correctly?
        while (' ' == c || '\t' == c || '\n' == c || '\r' == c || c == '/') {
            switch(c) {
                case ' ' -> {
                }
                case '\t' -> colNum += 3;
                case '\n' -> {
                    rowNum++;
                    colNum = 0;
                    currentLine = new StringBuilder();
                }
                case '\r' -> {
                    c = this.fileStream.read();
                    if (c == '\n') {
                        rowNum++;
                        colNum = 0;
                        currentLine = new StringBuilder();
                    }
                }
                case '/' -> {
                    c = this.fileStream.read();
                    if (c == '/') {
                        while (c != '\r' && c != '\n' && c != -1) {
                            c = this.fileStream.read();
                        }
                    } else {
                        lastChar = c;
                        return new Token(Token.Kind.DIVIDE, rowNum, colNum);
                    }
                }
            }
            c = this.fileStream.read();
            colNum++;
            currentLine.append((char) c);
        }

        switch (c) {
            case -1 -> {
                // The value for "lineNum" is now "null",
                // you should modify this to an appropriate
                // line number for the "EOF" token.
                return new Token(Token.Kind.EOF, rowNum, colNum);
            }
            case '+' -> {
                return new Token(Token.Kind.ADD, rowNum, colNum);
            }
            case '-' -> {
                return new Token(Token.Kind.MINUS, rowNum, colNum);
            }
            case '*' -> {
                return new Token(Token.Kind.TIMES, rowNum, colNum);
            }
            case '/' -> {
                return new Token(Token.Kind.DIVIDE, rowNum, colNum);
            }
            case ',' -> {
                return new Token(Token.Kind.COMMA, rowNum, colNum);
            }
            case '.' -> {
                return new Token(Token.Kind.DOT, rowNum, colNum);
            }
            case '(' -> {
                return new Token(Token.Kind.LPAREN, rowNum, colNum);
            }
            case ')' -> {
                return new Token(Token.Kind.RPAREN, rowNum, colNum);
            }
            case '[' -> {
                return new Token(Token.Kind.LBRACKET, rowNum, colNum);
            }
            case ']' -> {
                return new Token(Token.Kind.RBRACKET, rowNum, colNum);
            }
            case ';' -> {
                return new Token(Token.Kind.SEMICOLON, rowNum, colNum);
            }
            case '{' -> {
                return new Token(Token.Kind.LBRACE, rowNum, colNum);
            }
            case '}' -> {
                return new Token(Token.Kind.RBRACE, rowNum, colNum);
            }
            case '=' -> {
                return new Token(Token.Kind.ASSIGN, rowNum, colNum);
            }
            case '<' -> {
                return new Token(Token.Kind.LESS, rowNum, colNum);
            }
            case '>' -> {
                return new Token(Token.Kind.GREATER, rowNum, colNum);
            }
            case '!' -> {
                return new Token(Token.Kind.NOT, rowNum, colNum);
            }
            case '&' -> {
                c = this.fileStream.read();
                colNum++;
                if ('&' == c) {
                    return new Token(Token.Kind.AND, rowNum, colNum);
                }
                throw new Error("AND needs two &");
            }

            default -> {
                // Lab 1, exercise 9: supply missing code to
                // recognize other kind of tokens.
                // Hint: think carefully about the basic
                // data structure and algorithms. The code
                // is not that much and may be less than 50 lines.
                // If you find you are writing a lot of code, you
                // are on the wrong way.
                if (Character.isLetter(c)) {
                    int startColNum = colNum;
                    String word = GetWord(c);
                    Token.Kind kind = keywords.get(word);
                    if (kind != null) {
                        return new Token(kind, rowNum, startColNum);
                    }
                    return new Token(Token.Kind.ID, word, rowNum, startColNum);
                } else if (Character.isDigit(c)) {
                    StringBuilder num = new StringBuilder();
                    int startColNum = colNum;
                    while (Character.isDigit(c)) {
                        num.append((char) c);
                        c = this.fileStream.read();
                        colNum++;
                    }
                    lastChar = c;
                    return new Token(Token.Kind.INTEGER_LITERAL, num.toString(), rowNum, startColNum);
                }
                throw new Todo();
            }
        }
    }

    public String getCurrentLine() {
        try {
            String ret;
            if (!currentLine.isEmpty()) {
                char lastChar = currentLine.charAt(currentLine.length() - 1);
                if (lastChar == '\n' || lastChar == '\r') {
                    rowNum++;
                    ret = currentLine.toString();
                    currentLine = new StringBuilder();
                    return ret;
                }
            }
            int c = this.fileStream.read();
            while(c != '\n' && c != '\r' && c != -1) {
                currentLine.append((char) c);
                c = this.fileStream.read();
            }
            if (c == '\r') {
                c = this.fileStream.read();
            }
            rowNum++;
            ret = currentLine.toString();
            currentLine = new StringBuilder();
            return ret;
        } catch(Exception e) {
            return null;
        }
    }

    public Token peekNextToken() {
        try {
            if (lastToken == null) {
                lastToken = this.nextToken0();
            }
            return lastToken;
        } catch (Exception e) {
            //e.printStackTrace();
            System.exit(1);
            return null;
        }

    }

    public Token nextToken() {
        Token t = null;

        try {
            if (lastToken != null) {
                t = lastToken;
                lastToken = null;
            } else {
                t = this.nextToken0();
            }
        } catch (Exception e) {
            //e.printStackTrace();
            System.exit(1);
        }
        if (dumpToken) {
            System.out.println(t);
        }
        return t;
    }
}
