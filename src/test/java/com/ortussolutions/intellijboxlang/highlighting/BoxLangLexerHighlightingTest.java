package com.ortussolutions.intellijboxlang.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.ortussolutions.intellijboxlang.lexer.BoxLangLexer;
import com.ortussolutions.intellijboxlang.lexer.BoxLangTokenTypes;
import java.util.ArrayList;
import java.util.List;

public final class BoxLangLexerHighlightingTest extends BasePlatformTestCase {

	// ── Basic script tokens ────────────────────────────────────────────────

	public void testScriptLexerTokens() {
		List<IElementType> tokens = lexTokens( "component demo { function main() { return 1; } }" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STORAGE_TYPE,     // component
		    BoxLangTokenTypes.IDENTIFIER,        // demo
		    BoxLangTokenTypes.BRACE,             // {
		    BoxLangTokenTypes.STORAGE_TYPE,      // function
		    BoxLangTokenTypes.FUNCTION_NAME,     // main
		    BoxLangTokenTypes.PAREN,             // (
		    BoxLangTokenTypes.PAREN,             // )
		    BoxLangTokenTypes.BRACE,             // {
		    BoxLangTokenTypes.KEYWORD,           // return
		    BoxLangTokenTypes.NUMBER,            // 1
		    BoxLangTokenTypes.SEMICOLON,         // ;
		    BoxLangTokenTypes.BRACE,             // }
		    BoxLangTokenTypes.BRACE              // }
		);
	}

	public void testTemplateLexerTokens() {
		List<IElementType> tokens = lexTokens( "<bx:output>Hi</bx:output>" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.TAG,
		    BoxLangTokenTypes.IDENTIFIER,
		    BoxLangTokenTypes.TAG
		);
	}

	// ── String interpolation ───────────────────────────────────────────────

	public void testStringInterpolationTokens() {
		// Test basic interpolation: "Hello, #name#!"
		List<IElementType> tokens = lexTokens( "\"Hello, #name#!\"" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STRING,     // Opening quote "
		    BoxLangTokenTypes.STRING,     // "Hello, "
		    BoxLangTokenTypes.HASH_SIGN,  // #
		    BoxLangTokenTypes.IDENTIFIER, // name
		    BoxLangTokenTypes.HASH_SIGN,  // #
		    BoxLangTokenTypes.STRING,     // "!"
		    BoxLangTokenTypes.STRING      // Closing quote "
		);
	}

	public void testStringInterpolationWithExpression() {
		// Test interpolation with expression: "Result: #foo + bar#"
		List<IElementType> tokens = lexTokens( "\"Result: #foo + bar#\"" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STRING,     // Opening quote "
		    BoxLangTokenTypes.STRING,     // "Result: "
		    BoxLangTokenTypes.HASH_SIGN,  // #
		    BoxLangTokenTypes.IDENTIFIER, // foo
		    BoxLangTokenTypes.OPERATOR,   // +
		    BoxLangTokenTypes.IDENTIFIER, // bar
		    BoxLangTokenTypes.HASH_SIGN,  // #
		    BoxLangTokenTypes.STRING      // Closing quote "
		);
	}

	public void testEscapedHashInString() {
		// Test escaped hash: "Price: ##50"
		List<IElementType> tokens = lexTokens( "\"Price: ##50\"" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STRING, // Opening quote "
		    BoxLangTokenTypes.STRING, // "Price: "
		    BoxLangTokenTypes.STRING, // ## (escaped hash)
		    BoxLangTokenTypes.STRING, // "50"
		    BoxLangTokenTypes.STRING  // Closing quote "
		);
	}

	public void testSingleQuoteStringInterpolation() {
		// Test single quote string with interpolation: 'Hello, #name#!'
		List<IElementType> tokens = lexTokens( "'Hello, #name#!'" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STRING,     // Opening quote '
		    BoxLangTokenTypes.STRING,     // 'Hello, '
		    BoxLangTokenTypes.HASH_SIGN,  // #
		    BoxLangTokenTypes.IDENTIFIER, // name
		    BoxLangTokenTypes.HASH_SIGN,  // #
		    BoxLangTokenTypes.STRING,     // '!'
		    BoxLangTokenTypes.STRING      // Closing quote '
		);
	}

	public void testNestedFunctionCallInInterpolation() {
		// Test function call in interpolation: "Count: #len(items)#"
		List<IElementType> tokens = lexTokens( "\"Count: #len(items)#\"" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STRING,           // Opening quote "
		    BoxLangTokenTypes.STRING,           // "Count: "
		    BoxLangTokenTypes.HASH_SIGN,        // #
		    BoxLangTokenTypes.BUILTIN_FUNCTION, // len
		    BoxLangTokenTypes.PAREN,            // (
		    BoxLangTokenTypes.IDENTIFIER,       // items
		    BoxLangTokenTypes.PAREN,            // )
		    BoxLangTokenTypes.HASH_SIGN,        // #
		    BoxLangTokenTypes.STRING            // Closing quote "
		);
	}

	// ── Documentation comments ─────────────────────────────────────────────

	public void testDocCommentToken() {
		List<IElementType> tokens = lexTokens( "/** doc comment */" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.DOC_COMMENT
		);
	}

	public void testBlockCommentToken() {
		List<IElementType> tokens = lexTokens( "/* block comment */" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.BLOCK_COMMENT
		);
	}

	public void testLineCommentToken() {
		List<IElementType> tokens = lexTokens( "// line comment" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.LINE_COMMENT
		);
	}

	public void testTemplateCommentToken() {
		List<IElementType> tokens = lexTokens( "<!--- template comment --->" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.BLOCK_COMMENT
		);
	}

	// ── Annotations ────────────────────────────────────────────────────────

	public void testAnnotationToken() {
		List<IElementType> tokens = lexTokens( "@output" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.ANNOTATION
		);
	}

	public void testDottedAnnotationToken() {
		List<IElementType> tokens = lexTokens( "@inject.provider" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.ANNOTATION
		);
	}

	// ── Constants ──────────────────────────────────────────────────────────

	public void testConstantTokens() {
		List<IElementType> tokens = lexTokens( "true false null yes no" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.CONSTANT,
		    BoxLangTokenTypes.CONSTANT,
		    BoxLangTokenTypes.CONSTANT,
		    BoxLangTokenTypes.CONSTANT,
		    BoxLangTokenTypes.CONSTANT
		);
	}

	// ── Scope variables ────────────────────────────────────────────────────

	public void testScopeVariableTokens() {
		List<IElementType> tokens = lexTokens( "variables request server application session this local" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.SCOPE_VARIABLE,
		    BoxLangTokenTypes.SCOPE_VARIABLE,
		    BoxLangTokenTypes.SCOPE_VARIABLE,
		    BoxLangTokenTypes.SCOPE_VARIABLE,
		    BoxLangTokenTypes.SCOPE_VARIABLE,
		    BoxLangTokenTypes.SCOPE_VARIABLE,
		    BoxLangTokenTypes.SCOPE_VARIABLE
		);
	}

	public void testScopeVariableAtStartOfChain() {
		// variables.foo — "variables" is scope, "foo" is identifier
		List<IElementType> tokens = lexTokens( "variables.foo" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.SCOPE_VARIABLE, // variables
		    BoxLangTokenTypes.DOT,            // .
		    BoxLangTokenTypes.IDENTIFIER      // foo
		);
	}

	public void testScopeVariableNotAfterDot() {
		// foo.variables — "variables" after a dot is NOT a scope variable
		List<IElementType> tokens = lexTokens( "foo.variables" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.IDENTIFIER,  // foo
		    BoxLangTokenTypes.DOT,         // .
		    BoxLangTokenTypes.IDENTIFIER   // variables (not scope because after dot)
		);
	}

	public void testScopeVariableChainDoesNotBleed() {
		// variables.request.data — only "variables" is scope, "request" after dot is plain identifier
		List<IElementType> tokens = lexTokens( "variables.request.data" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.SCOPE_VARIABLE, // variables
		    BoxLangTokenTypes.DOT,            // .
		    BoxLangTokenTypes.IDENTIFIER,     // request (after dot, not scope)
		    BoxLangTokenTypes.DOT,            // .
		    BoxLangTokenTypes.IDENTIFIER      // data
		);
	}

	public void testLocalScopeVariable() {
		List<IElementType> tokens = lexTokens( "local.setup" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.SCOPE_VARIABLE, // local
		    BoxLangTokenTypes.DOT,            // .
		    BoxLangTokenTypes.IDENTIFIER      // setup
		);
	}

	public void testArgumentsScopeVariable() {
		List<IElementType> tokens = lexTokens( "arguments.name" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.SCOPE_VARIABLE, // arguments
		    BoxLangTokenTypes.DOT,            // .
		    BoxLangTokenTypes.IDENTIFIER      // name
		);
	}

	// ── Storage types ──────────────────────────────────────────────────────

	public void testStorageTypeTokens() {
		List<IElementType> tokens = lexTokens( "class interface function property var" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STORAGE_TYPE,
		    BoxLangTokenTypes.STORAGE_TYPE,
		    BoxLangTokenTypes.STORAGE_TYPE,
		    BoxLangTokenTypes.STORAGE_TYPE,
		    BoxLangTokenTypes.STORAGE_TYPE
		);
	}

	public void testPrimitiveTypeTokens() {
		List<IElementType> tokens = lexTokens( "string numeric boolean array struct query any" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STORAGE_TYPE,
		    BoxLangTokenTypes.STORAGE_TYPE,
		    BoxLangTokenTypes.STORAGE_TYPE,
		    BoxLangTokenTypes.STORAGE_TYPE,
		    BoxLangTokenTypes.STORAGE_TYPE,
		    BoxLangTokenTypes.STORAGE_TYPE,
		    BoxLangTokenTypes.STORAGE_TYPE
		);
	}

	// ── Storage modifiers ──────────────────────────────────────────────────

	public void testStorageModifierTokens() {
		List<IElementType> tokens = lexTokens( "public private static final abstract remote required" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STORAGE_MODIFIER,
		    BoxLangTokenTypes.STORAGE_MODIFIER,
		    BoxLangTokenTypes.STORAGE_MODIFIER,
		    BoxLangTokenTypes.STORAGE_MODIFIER,
		    BoxLangTokenTypes.STORAGE_MODIFIER,
		    BoxLangTokenTypes.STORAGE_MODIFIER,
		    BoxLangTokenTypes.STORAGE_MODIFIER
		);
	}

	// ── Function names ─────────────────────────────────────────────────────

	public void testFunctionNameToken() {
		List<IElementType> tokens = lexTokens( "function greet()" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STORAGE_TYPE,   // function
		    BoxLangTokenTypes.FUNCTION_NAME,  // greet
		    BoxLangTokenTypes.PAREN,          // (
		    BoxLangTokenTypes.PAREN           // )
		);
	}

	public void testFunctionNameWithModifiers() {
		List<IElementType> tokens = lexTokens( "public static function myMethod()" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STORAGE_MODIFIER, // public
		    BoxLangTokenTypes.STORAGE_MODIFIER, // static
		    BoxLangTokenTypes.STORAGE_TYPE,      // function
		    BoxLangTokenTypes.FUNCTION_NAME,     // myMethod
		    BoxLangTokenTypes.PAREN,             // (
		    BoxLangTokenTypes.PAREN              // )
		);
	}

	// ── Built-in functions ─────────────────────────────────────────────────

	public void testBuiltInFunctionTokens() {
		List<IElementType> tokens = lexTokens( "len(x)" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.BUILTIN_FUNCTION, // len
		    BoxLangTokenTypes.PAREN,            // (
		    BoxLangTokenTypes.IDENTIFIER,       // x
		    BoxLangTokenTypes.PAREN             // )
		);
	}

	public void testArrayAppendBif() {
		List<IElementType> tokens = lexTokens( "arrayAppend(items, 1)" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.BUILTIN_FUNCTION, // arrayAppend
		    BoxLangTokenTypes.PAREN,            // (
		    BoxLangTokenTypes.IDENTIFIER,       // items
		    BoxLangTokenTypes.COMMA,            // ,
		    BoxLangTokenTypes.NUMBER,           // 1
		    BoxLangTokenTypes.PAREN             // )
		);
	}

	public void testBifNotRecognizedWithoutParens() {
		// len without parens should be an identifier, not a BIF
		List<IElementType> tokens = lexTokens( "len" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.IDENTIFIER
		);
	}

	// ── Function calls ────────────────────────────────────────────────────

	public void testFunctionCallToken() {
		// describe() is not a BIF, so it should be a FUNCTION_CALL
		List<IElementType> tokens = lexTokens( "describe()" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.FUNCTION_CALL, // describe
		    BoxLangTokenTypes.PAREN,         // (
		    BoxLangTokenTypes.PAREN          // )
		);
	}

	public void testFunctionCallWithArgs() {
		List<IElementType> tokens = lexTokens( "myFunc( x, y )" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.FUNCTION_CALL, // myFunc
		    BoxLangTokenTypes.PAREN,         // (
		    BoxLangTokenTypes.IDENTIFIER,    // x
		    BoxLangTokenTypes.COMMA,         // ,
		    BoxLangTokenTypes.IDENTIFIER,    // y
		    BoxLangTokenTypes.PAREN          // )
		);
	}

	public void testMethodCallAfterDotIsFunctionCall() {
		// obj.method() — method should be FUNCTION_CALL
		List<IElementType> tokens = lexTokens( "obj.method()" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.IDENTIFIER,    // obj
		    BoxLangTokenTypes.DOT,           // .
		    BoxLangTokenTypes.FUNCTION_CALL, // method
		    BoxLangTokenTypes.PAREN,         // (
		    BoxLangTokenTypes.PAREN          // )
		);
	}

	public void testBifStillRecognizedOverFunctionCall() {
		// len() should still be BIF, not generic function call
		List<IElementType> tokens = lexTokens( "len(x)" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.BUILTIN_FUNCTION, // len
		    BoxLangTokenTypes.PAREN,            // (
		    BoxLangTokenTypes.IDENTIFIER,       // x
		    BoxLangTokenTypes.PAREN             // )
		);
	}

	// ── Named arguments ───────────────────────────────────────────────────

	public void testNamedArgumentWithEquals() {
		// Inside parens: title = "A spec" — title is a named argument
		List<IElementType> tokens = lexTokens( "describe( title = \"A spec\" )" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.FUNCTION_CALL,   // describe
		    BoxLangTokenTypes.PAREN,           // (
		    BoxLangTokenTypes.NAMED_ARGUMENT,  // title
		    BoxLangTokenTypes.OPERATOR,        // =
		    BoxLangTokenTypes.STRING,          // "
		    BoxLangTokenTypes.STRING,          // A spec
		    BoxLangTokenTypes.STRING,          // "
		    BoxLangTokenTypes.PAREN            // )
		);
	}

	public void testMultipleNamedArguments() {
		List<IElementType> tokens = lexTokens( "foo( a = 1, b = 2 )" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.FUNCTION_CALL,   // foo
		    BoxLangTokenTypes.PAREN,           // (
		    BoxLangTokenTypes.NAMED_ARGUMENT,  // a
		    BoxLangTokenTypes.OPERATOR,        // =
		    BoxLangTokenTypes.NUMBER,          // 1
		    BoxLangTokenTypes.COMMA,           // ,
		    BoxLangTokenTypes.NAMED_ARGUMENT,  // b
		    BoxLangTokenTypes.OPERATOR,        // =
		    BoxLangTokenTypes.NUMBER,          // 2
		    BoxLangTokenTypes.PAREN            // )
		);
	}

	public void testNamedArgNotTriggeredOutsideParens() {
		// Outside parens, x = 1 is assignment, not named argument
		List<IElementType> tokens = lexTokens( "x = 1" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.IDENTIFIER, // x
		    BoxLangTokenTypes.OPERATOR,   // =
		    BoxLangTokenTypes.NUMBER      // 1
		);
	}

	public void testNamedArgNotTriggeredByDoubleEquals() {
		// Inside parens, x == 1 is comparison, not named argument
		List<IElementType> tokens = lexTokens( "foo( x == 1 )" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.FUNCTION_CALL, // foo
		    BoxLangTokenTypes.PAREN,         // (
		    BoxLangTokenTypes.IDENTIFIER,    // x (== is not =, so not named arg)
		    BoxLangTokenTypes.OPERATOR,      // ==
		    BoxLangTokenTypes.NUMBER,        // 1
		    BoxLangTokenTypes.PAREN          // )
		);
	}

	// ── Word operators ─────────────────────────────────────────────────────

	public void testWordOperatorTokens() {
		List<IElementType> tokens = lexTokens( "a eq b and c gt d mod e" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.IDENTIFIER, // a
		    BoxLangTokenTypes.OPERATOR,   // eq
		    BoxLangTokenTypes.IDENTIFIER, // b
		    BoxLangTokenTypes.OPERATOR,   // and
		    BoxLangTokenTypes.IDENTIFIER, // c
		    BoxLangTokenTypes.OPERATOR,   // gt
		    BoxLangTokenTypes.IDENTIFIER, // d
		    BoxLangTokenTypes.OPERATOR,   // mod
		    BoxLangTokenTypes.IDENTIFIER  // e
		);
	}

	// ── Struct / map keys ─────────────────────────────────────────────────

	public void testStructKeyToken() {
		// Simple struct literal: { key: "value" }
		List<IElementType> tokens = lexTokens( "{ name: \"John\" }" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.BRACE,       // {
		    BoxLangTokenTypes.STRUCT_KEY,   // name
		    BoxLangTokenTypes.OPERATOR,     // :
		    BoxLangTokenTypes.STRING,       // "
		    BoxLangTokenTypes.STRING,       // John
		    BoxLangTokenTypes.STRING,       // "
		    BoxLangTokenTypes.BRACE        // }
		);
	}

	public void testStructMultipleKeys() {
		List<IElementType> tokens = lexTokens( "{ age: 30, city: \"NYC\" }" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.BRACE,       // {
		    BoxLangTokenTypes.STRUCT_KEY,   // age
		    BoxLangTokenTypes.OPERATOR,     // :
		    BoxLangTokenTypes.NUMBER,       // 30
		    BoxLangTokenTypes.COMMA,        // ,
		    BoxLangTokenTypes.STRUCT_KEY,   // city
		    BoxLangTokenTypes.OPERATOR,     // :
		    BoxLangTokenTypes.STRING,       // "
		    BoxLangTokenTypes.STRING,       // NYC
		    BoxLangTokenTypes.STRING,       // "
		    BoxLangTokenTypes.BRACE        // }
		);
	}

	public void testStructKeyNotTriggeredByDoubleColon() {
		// :: is scope resolution, not a struct key separator
		List<IElementType> tokens = lexTokens( "foo::bar" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.IDENTIFIER,   // foo
		    BoxLangTokenTypes.OPERATOR,     // ::
		    BoxLangTokenTypes.IDENTIFIER    // bar
		);
	}

	public void testStructKeyWithKeywordValue() {
		// A classified keyword followed by : should keep its classification
		// e.g. { true: 1 } — "true" is a constant, not a struct key
		List<IElementType> tokens = lexTokens( "{ true: 1 }" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.BRACE,       // {
		    BoxLangTokenTypes.CONSTANT,    // true (stays constant, not struct key)
		    BoxLangTokenTypes.OPERATOR,    // :
		    BoxLangTokenTypes.NUMBER,      // 1
		    BoxLangTokenTypes.BRACE       // }
		);
	}

	public void testStructKeyOnlyForUnclassifiedIdentifiers() {
		// Storage types followed by : keep their classification
		List<IElementType> tokens = lexTokens( "{ string: \"hello\" }" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.BRACE,         // {
		    BoxLangTokenTypes.STORAGE_TYPE,   // string (stays storage type)
		    BoxLangTokenTypes.OPERATOR,       // :
		    BoxLangTokenTypes.STRING,         // "
		    BoxLangTokenTypes.STRING,         // hello
		    BoxLangTokenTypes.STRING,         // "
		    BoxLangTokenTypes.BRACE          // }
		);
	}

	public void testNamedArgumentWithColon() {
		// Named arguments use colon syntax: func(name: value)
		List<IElementType> tokens = lexTokens( "foo( bar: 42 )" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.FUNCTION_CALL, // foo
		    BoxLangTokenTypes.PAREN,        // (
		    BoxLangTokenTypes.STRUCT_KEY,    // bar
		    BoxLangTokenTypes.OPERATOR,     // :
		    BoxLangTokenTypes.NUMBER,       // 42
		    BoxLangTokenTypes.PAREN        // )
		);
	}

	// ── Numbers ────────────────────────────────────────────────────────────

	public void testHexNumberToken() {
		List<IElementType> tokens = lexTokens( "0xFF" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.NUMBER
		);
	}

	public void testFloatNumberToken() {
		List<IElementType> tokens = lexTokens( "3.14" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.NUMBER
		);
	}

	public void testScientificNotationToken() {
		List<IElementType> tokens = lexTokens( "1.5e10" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.NUMBER
		);
	}

	// ── Operators ──────────────────────────────────────────────────────────

	public void testArrowOperators() {
		List<IElementType> tokens = lexTokens( "=> ->" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.OPERATOR, // =>
		    BoxLangTokenTypes.OPERATOR  // ->
		);
	}

	public void testElvisOperator() {
		List<IElementType> tokens = lexTokens( "a ?: b" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.IDENTIFIER, // a
		    BoxLangTokenTypes.OPERATOR,   // ?:
		    BoxLangTokenTypes.IDENTIFIER  // b
		);
	}

	public void testRangeOperator() {
		List<IElementType> tokens = lexTokens( "1..10" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.NUMBER,   // 1
		    BoxLangTokenTypes.OPERATOR, // ..
		    BoxLangTokenTypes.NUMBER    // 10
		);
	}

	public void testSafeNavigationOperator() {
		List<IElementType> tokens = lexTokens( "a?.b" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.IDENTIFIER, // a
		    BoxLangTokenTypes.OPERATOR,   // ?.
		    BoxLangTokenTypes.IDENTIFIER  // b
		);
	}

	public void testSpreadOperator() {
		List<IElementType> tokens = lexTokens( "*:" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.OPERATOR // *:
		);
	}

	public void testTripleEqualsOperator() {
		List<IElementType> tokens = lexTokens( "a === b" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.IDENTIFIER, // a
		    BoxLangTokenTypes.OPERATOR,   // ===
		    BoxLangTokenTypes.IDENTIFIER  // b
		);
	}

	public void testSpaceshipOperator() {
		List<IElementType> tokens = lexTokens( "a <=> b" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.IDENTIFIER, // a
		    BoxLangTokenTypes.OPERATOR,   // <=>
		    BoxLangTokenTypes.IDENTIFIER  // b
		);
	}

	public void testNotEqualsAlternateOperator() {
		List<IElementType> tokens = lexTokens( "a <> b" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.IDENTIFIER, // a
		    BoxLangTokenTypes.OPERATOR,   // <>
		    BoxLangTokenTypes.IDENTIFIER  // b
		);
	}

	// ── Keywords ───────────────────────────────────────────────────────────

	public void testControlFlowKeywords() {
		List<IElementType> tokens = lexTokens( "if else elseif for while do break continue return throw rethrow try catch finally switch case default" );
		for ( IElementType token : tokens ) {
			assertEquals( "Expected KEYWORD token", BoxLangTokenTypes.KEYWORD, token );
		}
	}

	public void testOtherKeywords() {
		List<IElementType> tokens = lexTokens( "import include as in instanceof new extends implements" );
		for ( IElementType token : tokens ) {
			assertEquals( "Expected KEYWORD token", BoxLangTokenTypes.KEYWORD, token );
		}
	}

	// ── Integrated scenario ────────────────────────────────────────────────

	public void testClassDeclarationTokens() {
		List<IElementType> tokens = lexTokens( "class MyClass extends Base {}" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STORAGE_TYPE, // class
		    BoxLangTokenTypes.IDENTIFIER,   // MyClass
		    BoxLangTokenTypes.KEYWORD,      // extends
		    BoxLangTokenTypes.IDENTIFIER,   // Base
		    BoxLangTokenTypes.BRACE,        // {
		    BoxLangTokenTypes.BRACE         // }
		);
	}

	public void testPropertyDeclaration() {
		List<IElementType> tokens = lexTokens( "property string name;" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.STORAGE_TYPE, // property
		    BoxLangTokenTypes.STORAGE_TYPE, // string
		    BoxLangTokenTypes.IDENTIFIER,   // name
		    BoxLangTokenTypes.SEMICOLON     // ;
		);
	}

	public void testImportStatement() {
		List<IElementType> tokens = lexTokens( "import java.util.List;" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.KEYWORD,    // import
		    BoxLangTokenTypes.IDENTIFIER, // java
		    BoxLangTokenTypes.DOT,        // .
		    BoxLangTokenTypes.IDENTIFIER, // util
		    BoxLangTokenTypes.DOT,        // .
		    BoxLangTokenTypes.IDENTIFIER, // List
		    BoxLangTokenTypes.SEMICOLON   // ;
		);
	}

	public void testStructLiteralWithKeys() {
		List<IElementType> tokens = lexTokens( "{ name: \"John\", age: 30 }" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.BRACE,      // {
		    BoxLangTokenTypes.STRUCT_KEY,  // name
		    BoxLangTokenTypes.OPERATOR,   // :
		    BoxLangTokenTypes.STRING,     // "
		    BoxLangTokenTypes.STRING,     // John
		    BoxLangTokenTypes.STRING,     // "
		    BoxLangTokenTypes.COMMA,      // ,
		    BoxLangTokenTypes.STRUCT_KEY,  // age
		    BoxLangTokenTypes.OPERATOR,   // :
		    BoxLangTokenTypes.NUMBER,     // 30
		    BoxLangTokenTypes.BRACE       // }
		);
	}

	public void testStructLiteralWithRequiredKey() {
		List<IElementType> tokens = lexTokens( "{ required: true }" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.BRACE,      // {
		    BoxLangTokenTypes.STRUCT_KEY, // required
		    BoxLangTokenTypes.OPERATOR,   // :
		    BoxLangTokenTypes.CONSTANT,   // true
		    BoxLangTokenTypes.BRACE       // }
		);
	}

	public void testIsNullBifCall() {
		List<IElementType> tokens = lexTokens( "isNull(x)" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.BUILTIN_FUNCTION, // isNull
		    BoxLangTokenTypes.PAREN,            // (
		    BoxLangTokenTypes.IDENTIFIER,       // x
		    BoxLangTokenTypes.PAREN             // )
		);
	}

	public void testCaseInsensitiveKeywords() {
		// BoxLang keywords are case-insensitive
		List<IElementType> tokens = lexTokens( "IF ELSE RETURN True False NULL" );
		assertTokenSequence( tokens,
		    BoxLangTokenTypes.KEYWORD,   // IF
		    BoxLangTokenTypes.KEYWORD,   // ELSE
		    BoxLangTokenTypes.KEYWORD,   // RETURN
		    BoxLangTokenTypes.CONSTANT,  // True
		    BoxLangTokenTypes.CONSTANT,  // False
		    BoxLangTokenTypes.CONSTANT   // NULL
		);
	}

	// ── Helpers ────────────────────────────────────────────────────────────

	private List<IElementType> lexTokens( String text ) {
		Lexer lexer = new BoxLangLexer();
		lexer.start( text );
		List<IElementType> tokens = new ArrayList<>();
		while ( lexer.getTokenType() != null ) {
			IElementType tokenType = lexer.getTokenType();
			if ( tokenType != TokenType.WHITE_SPACE ) {
				tokens.add( tokenType );
			}
			lexer.advance();
		}
		return tokens;
	}

	private void assertTokenSequence( List<IElementType> tokens, IElementType... expected ) {
		assertEquals( "Token count mismatch: " + tokens, expected.length, tokens.size() );
		for ( int i = 0; i < expected.length; i++ ) {
			assertEquals( "Token mismatch at index " + i, expected[ i ], tokens.get( i ) );
		}
	}
}
