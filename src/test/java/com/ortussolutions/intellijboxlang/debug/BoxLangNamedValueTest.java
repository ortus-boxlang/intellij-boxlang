package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArgumentsFilter;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;

public class BoxLangNamedValueTest extends BasePlatformTestCase {

	public void testNamedVariableChildrenUseNamedFilter() {
		Variable parent = new Variable();
		parent.setName( "java" );
		parent.setValue( "{}" );
		parent.setVariablesReference( 77 );
		parent.setNamedVariables( 2 );

		Variable childOne = new Variable();
		childOne.setName( "version" );
		Variable childTwo = new Variable();
		childTwo.setName( "vendor" );

		FakeDapService dapService = new FakeDapService( getProject() );
		dapService.filteredNamedResponse	= responseWith( childOne, childTwo );
		dapService.unfilteredResponse		= responseWith();

		BoxLangNamedValue		value	= new BoxLangNamedValue(
		    parent,
		    () -> dapService,
		    child -> leafValue( child.getName() )
		);

		RecordingCompositeNode	node	= new RecordingCompositeNode();
		value.computeChildren( node );

		assertEquals( 1, dapService.filteredNamedCalls );
		assertEquals( 0, dapService.unfilteredCalls );
		assertNotNull( node.children );
		assertEquals( 2, node.children.size() );
		assertEquals( "version", node.children.getName( 0 ) );
		assertEquals( "vendor", node.children.getName( 1 ) );
	}

	public void testNamedVariableChildrenFallBackToUnfilteredWhenFilteredEmpty() {
		Variable parent = new Variable();
		parent.setName( "java" );
		parent.setValue( "{}" );
		parent.setVariablesReference( 77 );
		parent.setNamedVariables( 1 );

		Variable fallbackChild = new Variable();
		fallbackChild.setName( "runtime" );

		FakeDapService dapService = new FakeDapService( getProject() );
		dapService.filteredNamedResponse	= responseWith();
		dapService.unfilteredResponse		= responseWith( fallbackChild );

		BoxLangNamedValue		value	= new BoxLangNamedValue(
		    parent,
		    () -> dapService,
		    child -> leafValue( child.getName() )
		);

		RecordingCompositeNode	node	= new RecordingCompositeNode();
		value.computeChildren( node );

		assertEquals( 1, dapService.filteredNamedCalls );
		assertEquals( 1, dapService.unfilteredCalls );
		assertNotNull( node.children );
		assertEquals( 1, node.children.size() );
		assertEquals( "runtime", node.children.getName( 0 ) );
	}

	private static @NotNull VariablesResponse responseWith( Variable... vars ) {
		VariablesResponse response = new VariablesResponse();
		response.setVariables( vars );
		return response;
	}

	private static @NotNull XNamedValue leafValue( @NotNull String name ) {
		return new XNamedValue( name ) {

			@Override
			public void computePresentation( @NotNull XValueNode node, @NotNull XValuePlace place ) {
				node.setPresentation( null, null, "", false );
			}

			@Override
			public void computeChildren( @NotNull XCompositeNode node ) {
				node.addChildren( XValueChildrenList.EMPTY, true );
			}
		};
	}

	private static final class FakeDapService extends BoxLangDapService {

		private VariablesResponse	filteredNamedResponse	= responseWith();
		private VariablesResponse	unfilteredResponse		= responseWith();
		private int					filteredNamedCalls		= 0;
		private int					unfilteredCalls			= 0;

		private FakeDapService( @NotNull Project project ) {
			super( project );
		}

		@Override
		public boolean isConnected() {
			return true;
		}

		@Override
		public CompletableFuture<VariablesResponse> variables( int variablesReference ) {
			unfilteredCalls++;
			return CompletableFuture.completedFuture( unfilteredResponse );
		}

		@Override
		public CompletableFuture<VariablesResponse> variables( int variablesReference,
		    VariablesArgumentsFilter filter,
		    Integer start,
		    Integer count ) {
			if ( filter == VariablesArgumentsFilter.NAMED ) {
				filteredNamedCalls++;
			}
			return CompletableFuture.completedFuture( filteredNamedResponse );
		}
	}

	private static final class RecordingCompositeNode implements XCompositeNode {

		private XValueChildrenList children;

		@Override
		public void addChildren( XValueChildrenList children, boolean last ) {
			this.children = children;
		}

		@Override
		public void tooManyChildren( int remaining ) {
		}

		@Override
		public void setAlreadySorted( boolean alreadySorted ) {
		}

		@Override
		public void setErrorMessage( String errorMessage ) {
		}

		@Override
		public void setErrorMessage( String errorMessage, XDebuggerTreeNodeHyperlink link ) {
		}

		@Override
		public void setMessage( String message, Icon icon, SimpleTextAttributes attributes, XDebuggerTreeNodeHyperlink link ) {
		}

		@Override
		public boolean isObsolete() {
			return false;
		}
	}
}
