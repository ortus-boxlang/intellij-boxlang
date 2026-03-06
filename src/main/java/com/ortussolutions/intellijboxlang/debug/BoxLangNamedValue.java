package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.*;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArgumentsFilter;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Represents a named variable value in the debugger's Variables panel.
 * Maps DAP Variable data to IntelliJ's XNamedValue interface.
 *
 * Supports:
 * - Displaying variable name, value, and type
 * - Expanding composite values (structs, arrays, objects) via child variable references
 */
public class BoxLangNamedValue extends XNamedValue {

	private static final Logger						LOG	= Logger.getInstance( BoxLangNamedValue.class );

	private final Variable							variable;
	private final Supplier<BoxLangDapService>		dapServiceSupplier;
	private final Function<Variable, XNamedValue>	childFactory;

	public BoxLangNamedValue( @NotNull BoxLangDebugProcess debugProcess,
	    @NotNull Variable variable ) {
		this( variable, debugProcess::getDapService, childVar -> new BoxLangNamedValue( debugProcess, childVar ) );
	}

	BoxLangNamedValue(
	    @NotNull Variable variable,
	    @NotNull Supplier<BoxLangDapService> dapServiceSupplier,
	    @NotNull Function<Variable, XNamedValue> childFactory ) {
		super( variable.getName() != null ? variable.getName() : "<unnamed>" );
		this.variable			= variable;
		this.dapServiceSupplier	= dapServiceSupplier;
		this.childFactory		= childFactory;
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

		BoxLangDapService dapService = dapServiceSupplier.get();
		if ( !dapService.isConnected() ) {
			node.addChildren( XValueChildrenList.EMPTY, true );
			return;
		}

		fetchChildVariables( dapService, ref )
		    .thenAccept( variablesResponse -> {
			    XValueChildrenList children = new XValueChildrenList();

			    if ( variablesResponse != null && variablesResponse.getVariables() != null ) {
				    for ( Variable childVar : variablesResponse.getVariables() ) {
					    children.add( childFactory.apply( childVar ) );
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

	private CompletableFuture<VariablesResponse> fetchChildVariables( @NotNull BoxLangDapService dapService, int ref ) {
		Integer	namedCount		= variable.getNamedVariables();
		Integer	indexedCount	= variable.getIndexedVariables();
		boolean	hasNamed		= namedCount != null && namedCount > 0;
		boolean	hasIndexed		= indexedCount != null && indexedCount > 0;

		if ( !hasNamed && !hasIndexed ) {
			return dapService.variables( ref );
		}

		CompletableFuture<List<Variable>>	namedFuture		= hasNamed
		    ? dapService.variables( ref, VariablesArgumentsFilter.NAMED, 0, namedCount ).thenApply( BoxLangNamedValue::extractVariables )
		    : CompletableFuture.completedFuture( List.of() );

		CompletableFuture<List<Variable>>	indexedFuture	= hasIndexed
		    ? dapService.variables( ref, VariablesArgumentsFilter.INDEXED, 0, indexedCount ).thenApply( BoxLangNamedValue::extractVariables )
		    : CompletableFuture.completedFuture( List.of() );

		return namedFuture.thenCombine( indexedFuture, ( named, indexed ) -> {
			VariablesResponse	merged		= new VariablesResponse();
			List<Variable>		combined	= new ArrayList<>( named.size() + indexed.size() );
			combined.addAll( named );
			combined.addAll( indexed );
			merged.setVariables( combined.toArray( new Variable[ 0 ] ) );
			return merged;
		} ).thenCompose( merged -> {
			if ( merged.getVariables() != null && merged.getVariables().length > 0 ) {
				return CompletableFuture.completedFuture( merged );
			}
			return dapService.variables( ref );
		} ).exceptionallyCompose( ex -> dapService.variables( ref ) );
	}

	private static @NotNull List<Variable> extractVariables( @Nullable VariablesResponse response ) {
		if ( response == null || response.getVariables() == null || response.getVariables().length == 0 ) {
			return List.of();
		}
		return List.of( response.getVariables() );
	}

	@Override
	public @Nullable XValueModifier getModifier() {
		// Variable modification not yet supported
		return null;
	}
}
