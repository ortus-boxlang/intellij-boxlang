package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.Variable;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Represents a DAP scope as a top-level expandable node in IntelliJ variables view.
 */
public class BoxLangScopeValue extends XNamedValue {

	private static final Logger						LOG	= Logger.getInstance( BoxLangScopeValue.class );

	private final Scope								scope;
	private final Supplier<BoxLangDapService>		dapServiceSupplier;
	private final Function<Variable, XNamedValue>	childFactory;

	public BoxLangScopeValue( @NotNull BoxLangDebugProcess debugProcess, @NotNull Scope scope ) {
		this( scope, debugProcess::getDapService, variable -> new BoxLangNamedValue( debugProcess, variable ) );
	}

	BoxLangScopeValue(
	    @NotNull Scope scope,
	    @NotNull Supplier<BoxLangDapService> dapServiceSupplier,
	    @NotNull Function<Variable, XNamedValue> childFactory ) {
		super( scope.getName() != null && !scope.getName().isBlank() ? scope.getName() : "<scope>" );
		this.scope				= scope;
		this.dapServiceSupplier	= dapServiceSupplier;
		this.childFactory		= childFactory;
	}

	@Override
	public void computePresentation( @NotNull XValueNode node, @NotNull XValuePlace place ) {
		boolean hasChildren = scope.getVariablesReference() >= 0;
		node.setPresentation( null, null, "", hasChildren );
	}

	@Override
	public void computeChildren( @NotNull XCompositeNode node ) {
		int ref = scope.getVariablesReference();
		if ( ref < 0 ) {
			node.addChildren( XValueChildrenList.EMPTY, true );
			return;
		}

		BoxLangDapService dapService = dapServiceSupplier.get();
		if ( !dapService.isConnected() ) {
			node.addChildren( XValueChildrenList.EMPTY, true );
			return;
		}

		dapService.variables( ref )
		    .thenAccept( variablesResponse -> {
			    XValueChildrenList children = new XValueChildrenList();
			    if ( variablesResponse != null && variablesResponse.getVariables() != null ) {
				    for ( Variable variable : variablesResponse.getVariables() ) {
					    children.add( childFactory.apply( variable ) );
				    }
			    }
			    node.addChildren( children, true );
		    } )
		    .exceptionally( ex -> {
			    LOG.warn( "Failed to fetch variables for scope: " + getName(), ex );
			    node.addChildren( XValueChildrenList.EMPTY, true );
			    return null;
		    } );
	}
}
