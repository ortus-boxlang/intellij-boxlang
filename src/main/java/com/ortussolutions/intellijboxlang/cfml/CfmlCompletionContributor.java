package com.ortussolutions.intellijboxlang.cfml;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public final class CfmlCompletionContributor extends CompletionContributor implements com.intellij.openapi.project.DumbAware {

	public CfmlCompletionContributor() {
		extend( CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {

			@Override
			protected void addCompletions( @NotNull CompletionParameters parameters, @NotNull ProcessingContext context,
			    @NotNull CompletionResultSet result ) {
				var file = parameters.getOriginalFile();
				if ( !CfmlEditorContext.isExpression( file, file.getViewProvider().getContents(), parameters.getOffset(), true ) )
					return;
				var call = CfmlParameterInfoHandler.findCall( file, parameters.getOffset() );
				if ( call != null && file.getText().substring( call.argumentStart, parameters.getOffset() ).strip().matches( "[A-Za-z0-9_$]*" ) ) {
					for ( var parameter : call.function.params() ) {
						if ( call.suppliedArguments.contains( parameter.name().toLowerCase( java.util.Locale.ROOT ) ) )
							continue;
						result.addElement( LookupElementBuilder.create( parameter, parameter.name() )
						    .withCaseSensitivity( false ).withTailText( " =", true ).withTypeText( parameter.type() )
						    .withInsertHandler( ( insertion, item ) -> {
							    var document = insertion.getDocument();
							    int next	= insertion.getTailOffset();
							    while ( next < document.getTextLength() && Character.isWhitespace( document.getCharsSequence().charAt( next ) ) )
								    next++;
							    if ( next < document.getTextLength() && document.getCharsSequence().charAt( next ) == '=' ) {
								    insertion.getEditor().getCaretModel().moveToOffset( next + 1 );
							    } else {
								    document.insertString( insertion.getTailOffset(), "=" );
								    insertion.getEditor().getCaretModel().moveToOffset( insertion.getTailOffset() );
							    }
							    if ( insertion.getCompletionChar() == '=' )
								    insertion.setAddCompletionChar( false );
						    } ) );
					}
				}

				var functions = new java.util.LinkedHashMap<String, CfmlFunctionCatalog.Function>();
				for ( var projectFunction : CfmlProjectFunctions.at( file, parameters.getOffset() ) ) {
					functions.putIfAbsent( projectFunction.name().toLowerCase( java.util.Locale.ROOT ), projectFunction );
				}
				if ( CfmlEditorContext.isExpression( file, file.getViewProvider().getContents(), parameters.getOffset() ) ) {
					for ( var builtin : CfmlFunctionCatalog.functions() )
						functions.putIfAbsent( builtin.name().toLowerCase( java.util.Locale.ROOT ), builtin );
				}
				for ( var function : functions.values() ) {
					result.addElement( LookupElementBuilder.create( function, function.name() )
					    .withCaseSensitivity( false )
					    .withTailText( " " + function.syntax(), true )
					    .withTypeText( function.returns() )
					    .withInsertHandler( ( insertion, item ) -> {
						    int tail	= insertion.getTailOffset();
						    var document = insertion.getDocument();
						    int next	= tail;
						    while ( next < document.getTextLength() && Character.isWhitespace( document.getCharsSequence().charAt( next ) ) )
							    next++;
						    if ( next < document.getTextLength() && document.getCharsSequence().charAt( next ) == '(' ) {
							    insertion.getEditor().getCaretModel().moveToOffset( next + 1 );
						    } else {
							    document.insertString( tail, "()" );
							    insertion.getEditor().getCaretModel().moveToOffset( tail + ( function.params().isEmpty() ? 2 : 1 ) );
						    }
						    if ( insertion.getCompletionChar() == '(' )
							    insertion.setAddCompletionChar( false );
						    if ( !function.params().isEmpty() ) {
							    com.intellij.codeInsight.AutoPopupController.getInstance( insertion.getProject() )
							        .autoPopupParameterInfo( insertion.getEditor(), null );
						    }
					    } ) );
				}
			}
		} );
	}
}
