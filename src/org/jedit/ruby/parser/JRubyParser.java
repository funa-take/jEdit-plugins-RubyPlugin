package org.jedit.ruby.parser;

import org.jruby.ast.*;
import org.jruby.lexer.LexerSource;
import org.jruby.lexer.yacc.SyntaxException;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.parser.RubyParser;
import org.jruby.parser.ParserConfiguration;
// import org.jruby.parser.DefaultRubyParser;
import org.jruby.parser.RubyParserResult;
import org.jruby.common.NullWarnings;
import org.jruby.common.IRubyWarnings;
import org.jedit.ruby.ast.Member;
import org.jedit.ruby.RubyPlugin;
import org.jruby.Ruby;

import java.util.List;
import java.io.StringReader;
import java.io.Reader;

import java.io.*;
import org.jruby.common.*;
import org.jruby.parser.*;
import org.jruby.ast.*;
import org.jruby.*;
import org.jruby.lexer.*;


/**
 * @author robmckinnon at users.sourceforge.net
 */
public final class JRubyParser {
  
  private static final JRubyParser instance = new JRubyParser();
  
  private static String found = "found";
  private static String expected = "expected";
  private static String nothing = "nothing";
  
  private IRubyWarnings warnings;
  
  /** singleton private constructor */
  private JRubyParser() {
  }
  
  public static void setFoundLabel(String found) {
    JRubyParser.found = found;
  }
  
  public static void setExpectedLabel(String expected) {
    JRubyParser.expected = expected;
  }
  
  public static void setNothingLabel(String nothing) {
    JRubyParser.nothing = nothing;
  }
  
  static List<Member> getMembers(String text, List<Member> methodMembers, List<org.jedit.ruby.parser.RubyParser.WarningListener> listeners, String filePath, LineCounter lineCounter) {
    return instance.parse(text, listeners, methodMembers, filePath, lineCounter);
  }
  
  private List<Member> parse(String text, List<org.jedit.ruby.parser.RubyParser.WarningListener> listeners, List<Member> methodMembers, String filePath, LineCounter lineCounter) {
    this.warnings = new Warnings(listeners);
    
    RubyNodeVisitor visitor = new RubyNodeVisitor(lineCounter, methodMembers, listeners);
    // RubyNodeVisitor visitor = new RubyNodeVisitor();
    List<Member> members = null;
    try {
      Ruby runtime = Ruby.getGlobalRuntime();
      
      // StringBuilder sb = new StringBuilder();
      // sb.append("a=20\n");
      // sb.append("a='aaa'\n");
      // sb.append("a='あいうえお'\n");
      // sb.append("a='あいうえお'\n");
      // sb.append("def ほげ \n");
      // sb.append("end \n");
      // ByteArrayInputStream is = new ByteArrayInputStream(sb.toString().getBytes());
      
      ByteArrayInputStream is = new ByteArrayInputStream(text.getBytes("UTF-8"));
      
      RubyArray source = new RubyArray(runtime, 1);
      RubyWarnings warning = new RubyWarnings(runtime);
      
      RubyIO io = new RubyIO(runtime, is);
      GetsLexerSource lexerSource = new GetsLexerSource(filePath, 0, io, source);
      lexerSource.setEncoding(org.jcodings.specific.UTF8Encoding.INSTANCE);
      
      ParserConfiguration config = new ParserConfiguration(runtime, 0, false, false, false);
      config.setDefaultEncoding(org.jcodings.specific.UTF8Encoding.INSTANCE);
      
      org.jruby.parser.RubyParser parser = new org.jruby.parser.RubyParser(lexerSource, warning);
      RubyParserResult result = parser.parse(config);
      Node node = result.getAST();
      
      if (node != null) {
        node.accept(visitor);
      }
      members = visitor.getMembers();      
    } catch (SyntaxException e){
      for (org.jedit.ruby.parser.RubyParser.WarningListener listener : listeners) {
        listener.error(e.getPosition(), e.getMessage());
      }
      String message = org.jedit.ruby.parser.RubyParser.getEndLine(e.getPosition()) + ": " + e.getMessage();
      RubyPlugin.log(message, getClass());
      members = null;
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    return members;
  }
  
  // private Node parse(String name, Reader content, ParserConfiguration config) {
  // // // DefaultRubyParser parser = new DefaultRubyParser() {
  // // // /** Hack to ensure we get original error message */
  // // // public void yyerror(String message, String[] expected, String found) {
  // // // try {
  // // // super.yyerror(message, expected, found);
  // // // } catch (SyntaxException e) {
  // // // String errorMessage = formatErrorMessage(message, expected, found);
  // // // throw new SyntaxException(e.getPosition(), errorMessage);
  // // // }
  // // // }
  // // // };
  // // // 
  // // // parser.setWarnings(warnings);
  // // LexerSource lexerSource = LexerSource.getSource(name, content, 0, true);
  // // RubyParser parser = new RubyParser(lexerSource, warnings) {
  // // public void yyerror(String message, String[] expected, String found) {
  // // try {
  // // super.yyerror(message, expected, found);
  // // } catch (SyntaxException e) {
  // // String errorMessage = formatErrorMessage(message, expected, found);
  // // throw new SyntaxException(e.getPosition(), errorMessage);
  // // }
  // // }
  // // };
  // // RubyParserResult result = parser.parse(config, lexerSource);
  // // return result.getAST();
  // return null;
  // }
  // 
  private static String formatErrorMessage(String message, String[] expectedValues, String found) {
    if (message.equals("syntax error")) {
      message = "";
    }
    
    StringBuffer buffer = new StringBuffer(message);
    if (found != null) {
      buffer.append(JRubyParser.found).append(" ").append(reformatValue(found)).append("; ");
      buffer.append(expected).append(" ");
      if (expectedValues == null || expectedValues.length == 0) {
        buffer.append(nothing);
      } else {
        for (String value : expectedValues) {
          value = reformatValue(value);
          buffer.append(value).append(", ");
        }
      }
    }
    
    return buffer.toString();
  }
  
  private static String reformatValue(String value) {
    if (value.startsWith("k")) {
      value = "'" + value.substring(1).toLowerCase() + "'";
    } else if (value.startsWith("t")) {
      value = value.substring(1).toLowerCase();
    }
    return value;
  }
  
  private static final class Warnings extends NullWarnings {
    private final List<org.jedit.ruby.parser.RubyParser.WarningListener> listeners;
    
    public Warnings(List<org.jedit.ruby.parser.RubyParser.WarningListener> listeners) {
      super(Ruby.getGlobalRuntime());
      this.listeners = listeners;
    }
    @Override 
    public final void warn(IRubyWarnings.ID id, ISourcePosition position, String message) {
      for (org.jedit.ruby.parser.RubyParser.WarningListener listener : listeners) {
        listener.warn(id, position, message);
      }
    }
    
    @Override
    public final void warn(IRubyWarnings.ID id, String message) {
      for (org.jedit.ruby.parser.RubyParser.WarningListener listener : listeners) {
        listener.warn(id, message);
      }
    }
    
    @Override
    public final void warning(IRubyWarnings.ID id, ISourcePosition position, String message) {
      for (org.jedit.ruby.parser.RubyParser.WarningListener listener : listeners) {
        listener.warning(id, position, message);
      }
    }
    
    @Override
    public final void warning(IRubyWarnings.ID id, String message) {
      for (org.jedit.ruby.parser.RubyParser.WarningListener listener : listeners) {
        listener.warning(id, message);
      }
    }
  }
  
}
