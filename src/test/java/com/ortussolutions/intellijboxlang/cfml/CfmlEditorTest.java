package com.ortussolutions.intellijboxlang.cfml;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.Arrays;

public class CfmlEditorTest extends BasePlatformTestCase {

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		com.intellij.testFramework.ExtensionTestUtil.maskExtensions(
		    com.intellij.openapi.extensions.ExtensionPointName.create( "com.intellij.annotator" ), java.util.List.of(), getTestRootDisposable() );
		com.intellij.lang.LanguageAnnotators.INSTANCE.clearCache( com.ortussolutions.intellijboxlang.BoxLangLanguage.INSTANCE );
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			super.tearDown();
		} finally {
			com.intellij.lang.LanguageAnnotators.INSTANCE.clearCache( com.ortussolutions.intellijboxlang.BoxLangLanguage.INSTANCE );
		}
	}

	private void typeTag( String before, String after ) {
		myFixture.configureByText( "example.cfm", before );
		myFixture.type( '>' );
		myFixture.checkResult( after );
	}

	public void testOutputClosingTag() {
		typeTag( "<cfoutput<caret>", "<cfoutput><caret></cfoutput>" );
	}

	public void testPreservesTagCase() {
		typeTag( "<CFOUTPUT<caret>", "<CFOUTPUT><caret></CFOUTPUT>" );
	}

	public void testDoesNotDuplicateExistingClose() {
		typeTag( "<cfoutput<caret>body</cfoutput>", "<cfoutput><caret>body</cfoutput>" );
	}

	public void testNestedSameTagKeepsOuterClose() {
		typeTag( "<cfif a><cfif b<caret></cfif>", "<cfif a><cfif b><caret></cfif></cfif>" );
	}

	public void testNestedExistingCloseNotDuplicated() {
		typeTag( "<cfif a><cfif b<caret></cfif></cfif>", "<cfif a><cfif b><caret></cfif></cfif>" );
	}

	public void testVoidTagNotClosed() {
		typeTag( "<cfset x = 1<caret>", "<cfset x = 1><caret>" );
	}

	public void testSelfClosingTagNotClosed() {
		typeTag( "<cfoutput /<caret>", "<cfoutput /><caret>" );
	}

	public void testEndTagNotClosed() {
		typeTag( "</cfoutput<caret>", "</cfoutput><caret>" );
	}

	public void testCommentNotClosed() {
		typeTag( "<!--- <cfoutput<caret> --->", "<!--- <cfoutput><caret> --->" );
	}

	public void testStringNotClosed() {
		typeTag( "<cfscript>x = '<cfoutput<caret>';</cfscript>", "<cfscript>x = '<cfoutput><caret>';</cfscript>" );
	}

	public void testAttributeGreaterThanNotClosed() {
		typeTag( "<cfoutput query=\"a<caret>\">", "<cfoutput query=\"a><caret>\">" );
	}

	public void testMultilineAttributes() {
		typeTag( "<cfoutput\n query=\"a>b\"<caret>", "<cfoutput\n query=\"a>b\"><caret></cfoutput>" );
	}

	public void testOptionalBodyTagNotClosed() {
		typeTag( "<cftransaction action=\"commit\"<caret>", "<cftransaction action=\"commit\"><caret>" );
	}

	public void testHtmlNotChanged() {
		typeTag( "<div<caret>", "<div><caret>" );
	}

	public void testBoxLangFileNotChanged() {
		myFixture.configureByText( "example.bx", "<cfoutput<caret>" );
		myFixture.type( '>' );
		myFixture.checkResult( "<cfoutput><caret>" );
	}

	public void testNamedArgumentHintUsesNameInsteadOfPosition() {
		myFixture.configureByText( "named.cfc", "arrayAppend(merge=tr<caret>, array=[])" );
		var call = CfmlParameterInfoHandler.findCall( myFixture.getFile(), myFixture.getCaretOffset() );
		assertNotNull( call );
		assertEquals( "merge", call.function.params().get( call.parameter ).name() );
	}

	public void testNamedArgumentHintSkipsLeadingCommentAndIgnoresCase() {
		myFixture.configureByText( "named.cfc", "arrayAppend(array=[], /* flags */ MERGE=tr<caret>)" );
		var call = CfmlParameterInfoHandler.findCall( myFixture.getFile(), myFixture.getCaretOffset() );
		assertNotNull( call );
		assertEquals( "merge", call.function.params().get( call.parameter ).name() );
	}

	public void testUnknownNamedArgumentDoesNotHighlightWrongParameter() {
		myFixture.configureByText( "named.cfc", "arrayAppend(unknown=tr<caret>)" );
		assertEquals( -1, CfmlParameterInfoHandler.findCall( myFixture.getFile(), myFixture.getCaretOffset() ).parameter );
	}

	public void testNamedArgumentCompletionInsertsEquals() {
		complete( "named.cfc", "arrayAppend(mer<caret>)", "merge", "arrayAppend(merge=<caret>)" );
	}

	public void testNamedArgumentCompletionPreservesEquals() {
		complete( "named.cfc", "arrayAppend(mer<caret>=true)", "merge", "arrayAppend(merge=<caret>true)" );
	}

	public void testNamedArgumentCompletionOmitsAlreadySuppliedNames() {
		myFixture.configureByText( "named.cfc", "arrayAppend(array=[1,2], ar<caret>)" );
		var items = myFixture.completeBasic();
		assertNotNull( items );
		assertFalse( Arrays.stream( items ).anyMatch( i -> i.getObject() instanceof CfmlFunctionCatalog.Parameter p && p.name().equalsIgnoreCase( "array" ) ) );
	}

	private void complete( String name, String before, String function, String after ) {
		myFixture.configureByText( name, before );
		var items = myFixture.completeBasic();
		if ( items != null ) {
			var item = Arrays.stream( items ).filter( value -> value.getLookupString().equalsIgnoreCase( function ) ).findFirst().orElseThrow();
			myFixture.getLookup().setCurrentItem( item );
			myFixture.finishLookup( '\n' );
		}
		myFixture.checkResult( after );
	}

	public void testFunctionCompletionInCfc() {
		complete( "example.cfc", "x = arrayAp<caret>", "arrayAppend", "x = arrayAppend(<caret>)" );
	}

	public void testFunctionCompletionCaseInsensitive() {
		complete( "example.cfc", "x = ARRAYAP<caret>", "arrayAppend", "x = ARRAYAPPEND(<caret>)" );
	}

	public void testCompletionInCfset() {
		complete( "example.cfm", "<cfset x = arrayAp<caret>", "arrayAppend", "<cfset x = arrayAppend(<caret>)" );
	}

	public void testCompletionInCfscript() {
		complete( "example.cfm", "<cfscript>x = arrayAp<caret></cfscript>", "arrayAppend", "<cfscript>x = arrayAppend(<caret>)</cfscript>" );
	}

	public void testCompletionInInterpolation() {
		complete( "example.cfm", "<cfoutput>#arrayAp<caret>#</cfoutput>", "arrayAppend", "<cfoutput>#arrayAppend(<caret>)#</cfoutput>" );
	}

	public void testCompletionInAttributeInterpolation() {
		complete( "example.cfm", "<cfoutput query=\"#arrayAp<caret>#\">", "arrayAppend", "<cfoutput query=\"#arrayAppend(<caret>)#\">" );
	}

	public void testCompletionKeepsExistingParentheses() {
		complete( "example.cfc", "x = arrayAp<caret>(a, b)", "arrayAppend", "x = arrayAppend(<caret>a, b)" );
	}

	public void testZeroArgumentCaretAfterParentheses() {
		complete( "example.cfc", "x = createUU<caret>", "createUUID", "x = createUUID()<caret>" );
	}

	private void noFunctions( String name, String before ) {
		myFixture.configureByText( name, before );
		var items = myFixture.completeBasic();
		if ( items != null )
			assertFalse( Arrays.stream( items ).anyMatch( item -> item.getObject() instanceof CfmlFunctionCatalog.Function ) );
		assertEquals( before.replace( "<caret>", "" ), myFixture.getFile().getText() );
	}

	public void testNoCompletionInComments() {
		noFunctions( "example.cfc", "// arrayAp<caret>" );
	}

	public void testNoCompletionInStrings() {
		noFunctions( "example.cfc", "x = 'arrayAp<caret>';" );
	}

	public void testNoCompletionForMembers() {
		noFunctions( "example.cfc", "x.arrayAp<caret>" );
	}

	public void testNoCompletionInPlainTemplateText() {
		noFunctions( "example.cfm", "arrayAp<caret>" );
	}

	public void testNoCompletionInTagName() {
		noFunctions( "example.cfm", "<cfarrayAp<caret>" );
	}

	public void testNoCompletionInBoxLangFiles() {
		noFunctions( "example.bx", "arrayAp<caret>" );
	}

	public void testNoCompletionInFunctionDeclaration() {
		noFunctions( "example.cfc", "function arrayAp<caret>" );
	}

	public void testParameterInfoHandlesNestedValues() {
		myFixture.configureByText( "example.cfc", "arrayAppend([1, 2], {x: 'a,b', y: foo(1,2)}, <caret>)" );
		var call = CfmlParameterInfoHandler.findCall( myFixture.getFile(), myFixture.getCaretOffset() );
		assertNotNull( call );
		assertEquals( "arrayAppend", call.function.name() );
		assertEquals( 2, call.parameter );
	}

	public void testParameterInfoUsesInnermostFunction() {
		myFixture.configureByText( "example.cfc", "arrayAppend(a, len(<caret>))" );
		var call = CfmlParameterInfoHandler.findCall( myFixture.getFile(), myFixture.getCaretOffset() );
		assertNotNull( call );
		assertEquals( "len", call.function.name() );
		assertEquals( 0, call.parameter );
	}

	public void testParameterInfoIgnoresMembers() {
		myFixture.configureByText( "example.cfc", "obj.arrayAppend(<caret>)" );
		assertNull( CfmlParameterInfoHandler.findCall( myFixture.getFile(), myFixture.getCaretOffset() ) );
	}

	public void testExistingCloseBeforeUnclosedSibling() {
		typeTag( "<cfoutput<caret></cfoutput><cfoutput>", "<cfoutput><caret></cfoutput><cfoutput>" );
	}

	public void testUnknownTagNotClosed() {
		typeTag( "<cfoutput-foo<caret>", "<cfoutput-foo><caret>" );
	}

	public void testParameterInfoIgnoresStringAndCommentDelimiters() {
		myFixture.configureByText( "example.cfc", "arrayAppend(a, '(' /* ) , */, <caret>)" );
		var call = CfmlParameterInfoHandler.findCall( myFixture.getFile(), myFixture.getCaretOffset() );
		assertNotNull( call );
		assertEquals( "arrayAppend", call.function.name() );
		assertEquals( 2, call.parameter );
	}

	public void testQuickDocumentationAtFunctionCall() {
		myFixture.configureByText( "example.cfc", "x = arrayAp<caret>pend(a, b);" );
		var	provider	= new CfmlDocumentationProvider();
		var	element		= provider.getCustomDocumentationElement( myFixture.getEditor(), myFixture.getFile(), null, myFixture.getCaretOffset() );
		assertNotNull( element );
		assertTrue( provider.generateDoc( element, null ).contains( "(required)" ) );
	}

	public void testCompletionLookupDocumentation() {
		myFixture.configureByText( "example.cfc", "x = array<caret>" );
		var items = myFixture.completeBasic();
		assertNotNull( items );
		var	item		= Arrays.stream( items ).filter( value -> value.getLookupString().equals( "arrayAppend" ) ).findFirst().orElseThrow();
		var	provider	= new CfmlDocumentationProvider();
		var	element		= provider.getDocumentationElementForLookupItem( myFixture.getPsiManager(), item.getObject(), myFixture.getFile() );
		assertNotNull( element );
		assertTrue( provider.generateDoc( element, null ).contains( "arrayAppend" ) );
	}

	public void testParameterInfoPopupThroughRegisteredHandler() {
		myFixture.configureByText( "example.cfc", "arrayAppend(a, <caret>)" );
		assertNotNull( CfmlParameterInfoHandler.findCall( myFixture.getFile(), myFixture.getCaretOffset() ) );
		assertTrue( Arrays.stream( com.intellij.codeInsight.hint.ShowParameterInfoHandler.getHandlers( getProject(), myFixture.getFile().getLanguage() ) )
		    .anyMatch( handler -> handler instanceof CfmlParameterInfoHandler ) );
		new com.intellij.codeInsight.hint.ShowParameterInfoHandler().invoke( getProject(), myFixture.getEditor(), myFixture.getFile() );
		com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching( "parameter popup",
		    () -> com.intellij.codeInsight.hint.ParameterInfoControllerBase.findControllerAtOffset( myFixture.getEditor(), 11 ) != null, 10 );
		var controller = com.intellij.codeInsight.hint.ParameterInfoControllerBase.findControllerAtOffset( myFixture.getEditor(), 11 );
		assertEquals( "arrayAppend", ( ( CfmlFunctionCatalog.Function ) controller.getObjects()[ 0 ] ).name() );
		com.intellij.openapi.util.Disposer.dispose( controller );
	}

	public void testNestedTemplateCommentNotClosed() {
		typeTag( "<!--- outer <!--- inner ---> <cfoutput<caret> --->", "<!--- outer <!--- inner ---> <cfoutput><caret> --->" );
	}

	public void testNoCompletionInsideNestedComment() {
		noFunctions( "example.cfc", "<!--- outer <!--- inner ---> arrayAp<caret> --->" );
	}

	public void testAcceptCompletionWithOpeningParenthesis() {
		myFixture.configureByText( "example.cfc", "x = array<caret>" );
		var	items	= myFixture.completeBasic();
		var	item	= Arrays.stream( items ).filter( value -> value.getLookupString().equals( "arrayAppend" ) ).findFirst().orElseThrow();
		myFixture.getLookup().setCurrentItem( item );
		myFixture.finishLookup( '(' );
		myFixture.checkResult( "x = arrayAppend(<caret>)" );
	}

	public void testCatalogHasSignaturesAndDocumentation() {
		assertTrue( CfmlFunctionCatalog.functions().size() > 700 );
		var function = CfmlFunctionCatalog.functions().stream().filter( f -> f.name().equals( "arrayAppend" ) ).findFirst().orElseThrow();
		assertEquals( "array", function.params().getFirst().name() );
		assertTrue( function.params().getFirst().required() );
		assertTrue( CfmlDocumentationProvider.documentation( function ).contains( "https://cfdocs.org/arrayAppend" ) );
	}
}
