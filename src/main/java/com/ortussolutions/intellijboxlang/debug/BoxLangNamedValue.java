package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.*;
import org.eclipse.lsp4j.debug.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a named variable value in the debugger's Variables panel.
 * Maps DAP Variable data to IntelliJ's XNamedValue interface.
 *
 * Supports:
 * - Displaying variable name, value, and type
 * - Expanding composite values (structs, arrays, objects) via child variable references
 */
public class BoxLangNamedValue extends XNamedValue {

	private static final Logger			LOG	= Logger.getInstance( BoxLangNamedValue.class );

	private final BoxLangDebugProcess	debugProcess;
	private final Variable				variable;

	public BoxLangNamedValue( @NotNull BoxLangDebugProcess debugProcess,
	    @NotNull Variable variable ) {
		super( variable.getName() != null ? variable.getName() : "<unnamed>" );
		this.debugProcess	= debugProcess;
		this.variable		= variable;
	}

	@Override
	public void computePresentation( @NotNull XValueNode node, @NotNull XValuePlace place ) {
		String	value		= variable.getValue() != null ? variable.getValue() : "";
		String	type		= variable.getType();
		boolean	hasChildren	= variable.getVariablesReference() > 0;

		// Use the XValueNode to display the value
		node.setPresentation(
		    null,  // icon - null uses default
		    type,  // type string
		    value, // value string
		    hasChildren // whether this node can be expanded
		);
	}

	@Override
	public void computeChildren( @NotNull XCompositeNode node ) {
		int ref = variable.getVariablesReference();
		if ( ref <= 0 ) {
			node.addChildren( XValueChildrenList.EMPTY, true );
			return;
		}

		BoxLangDapService dapService = debugProcess.getDapService();
		if ( !dapService.isConnected() ) {
			node.addChildren( XValueChildrenList.EMPTY, true );
			return;
		}

		dapService.variables( ref )
		    .thenAccept( variablesResponse -> {
			    XValueChildrenList children = new XValueChildrenList();

			    if ( variablesResponse != null && variablesResponse.getVariables() != null ) {
				    for ( Variable childVar : variablesResponse.getVariables() ) {
					    children.add( new BoxLangNamedValue( debugProcess, childVar ) );
				    }
			    }

			    node.addChildren( children, true );
		    } )
		    .exceptionally( ex -> {
			    LOG.warn( "Failed to fetch child variables for: " + variable.getName(), ex );
			    node.addChildren( XValueChildrenList.EMPTY, true );
			    return null;
		    } );
	}

	@Override
	public @Nullable XValueModifier getModifier() {
		// Variable modification not yet supported
		return null;
	}
}
