package com.ortussolutions.intellijboxlang.lsp;

import com.ortussolutions.intellijboxlang.highlighting.BoxLangTextAttributes;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;

public class BoxLangLspAnnotatorTest extends TestCase {

	public void testMapSemanticToken_mapsDefaultLibraryFunctionAsBuiltIn() {
		assertSame(
		    BoxLangTextAttributes.BUILTIN_FUNCTION,
		    BoxLangLspAnnotator.mapSemanticToken( "function", Set.of( "defaultLibrary" ) )
		);
	}

	public void testMapSemanticToken_mapsMethodInvocationAsFunctionCall() {
		assertSame(
		    BoxLangTextAttributes.METHOD_CALL,
		    BoxLangLspAnnotator.mapSemanticToken( "method", Set.of() )
		);
	}

	public void testMapSemanticToken_mapsDefaultLibraryMethodAsMemberFunction() {
		assertSame(
		    BoxLangTextAttributes.MEMBER_FUNCTION,
		    BoxLangLspAnnotator.mapSemanticToken( "method", Set.of( "defaultLibrary" ) )
		);
	}

	public void testMapSemanticToken_mapsMethodDeclarationAsFunctionName() {
		assertSame(
		    BoxLangTextAttributes.FUNCTION_NAME,
		    BoxLangLspAnnotator.mapSemanticToken( "method", Set.of( "declaration" ) )
		);
	}

	public void testDecodeTokenModifiers_decodesLegendBits() {
		Set<String> modifiers = BoxLangLspAnnotator.decodeTokenModifiers(
		    0b101,
		    List.of( "declaration", "defaultLibrary", "definition" )
		);
		assertEquals( Set.of( "declaration", "definition" ), modifiers );
	}
}
