package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;

public class BoxLangScopeValueTest extends BasePlatformTestCase {

	public void testScopeValueLoadsChildrenUsingScopeReference() {
		Variable javaVar = new Variable();
		javaVar.setName( "java" );
		Variable cliVar = new Variable();
		cliVar.setName( "cli" );

		VariablesResponse response = new VariablesResponse();
		response.setVariables( new Variable[] { javaVar, cliVar } );

		FakeDapService	dapService	= new FakeDapService( getProject(), response );
		Scope			scope		= new Scope();
		scope.setName( "server" );
		scope.setVariablesReference( 42 );

		BoxLangScopeValue		scopeValue	= new BoxLangScopeValue(
		    scope,
		    () -> dapService,
		    variable -> leafValue( variable.getName() )
		);
		RecordingValueNode		valueNode	= new RecordingValueNode();
		RecordingCompositeNode	composite	= new RecordingCompositeNode();

		scopeValue.computePresentation( valueNode, XValuePlace.TREE );
		scopeValue.computeChildren( composite );

		assertTrue( valueNode.hasChildren );
		assertEquals( 42, dapService.lastRequestedReference );
		assertNotNull( composite.children );
		assertEquals( 2, composite.children.size() );
		assertEquals( "java", composite.children.getName( 0 ) );
		assertEquals( "cli", composite.children.getName( 1 ) );
	}

	public void testScopeValueTreatsZeroReferenceAsExpandableForBoxLangAdapter() {
		Variable child = new Variable();
		child.setName( "java" );

		VariablesResponse response = new VariablesResponse();
		response.setVariables( new Variable[] { child } );

		FakeDapService	dapService	= new FakeDapService( getProject(), response );
		Scope			scope		= new Scope();
		scope.setName( "server" );
		scope.setVariablesReference( 0 );

		BoxLangScopeValue		scopeValue	= new BoxLangScopeValue(
		    scope,
		    () -> dapService,
		    variable -> leafValue( variable.getName() )
		);
		RecordingValueNode		valueNode	= new RecordingValueNode();
		RecordingCompositeNode	composite	= new RecordingCompositeNode();

		scopeValue.computePresentation( valueNode, XValuePlace.TREE );
		scopeValue.computeChildren( composite );

		assertTrue( valueNode.hasChildren );
		assertEquals( 0, dapService.lastRequestedReference );
		assertNotNull( composite.children );
		assertEquals( 1, composite.children.size() );
		assertEquals( "java", composite.children.getName( 0 ) );
	}

	public void testScopeValueReturnsEmptyWhenDisconnected() {
		Scope scope = new Scope();
		scope.setName( "request" );
		scope.setVariablesReference( 99 );

		FakeDapService dapService = new FakeDapService( getProject(), new VariablesResponse() );
		dapService.connected = false;

		BoxLangScopeValue		scopeValue	= new BoxLangScopeValue(
		    scope,
		    () -> dapService,
		    variable -> leafValue( variable.getName() )
		);
		RecordingCompositeNode	composite	= new RecordingCompositeNode();
		scopeValue.computeChildren( composite );

		assertNotNull( composite.children );
		assertEquals( 0, composite.children.size() );
		assertEquals( -1, dapService.lastRequestedReference );
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

		private final VariablesResponse	response;
		private boolean					connected				= true;
		private int						lastRequestedReference	= -1;

		private FakeDapService( @NotNull Project project, @NotNull VariablesResponse response ) {
			super( project );
			this.response = response;
		}

		@Override
		public boolean isConnected() {
			return connected;
		}

		@Override
		public CompletableFuture<VariablesResponse> variables( int variablesReference ) {
			lastRequestedReference = variablesReference;
			return CompletableFuture.completedFuture( response );
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

	private static final class RecordingValueNode implements XValueNode {

		private boolean hasChildren;

		@Override
		public void setPresentation( Icon icon, String type, String value, boolean hasChildren ) {
			this.hasChildren = hasChildren;
		}

		@Override
		public void setPresentation( Icon icon, XValuePresentation presentation, boolean hasChildren ) {
			this.hasChildren = hasChildren;
		}

		@Override
		public void setFullValueEvaluator( XFullValueEvaluator fullValueEvaluator ) {
		}

		@Override
		public boolean isObsolete() {
			return false;
		}
	}
}
