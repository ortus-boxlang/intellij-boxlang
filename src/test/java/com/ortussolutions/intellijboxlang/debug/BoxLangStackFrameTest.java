package com.ortussolutions.intellijboxlang.debug;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import org.eclipse.lsp4j.debug.Scope;
import org.jetbrains.annotations.NotNull;

public class BoxLangStackFrameTest extends BasePlatformTestCase {

	public void testBuildScopeChildrenPreservesScopeOrderAndNames() {
		Scope serverScope = new Scope();
		serverScope.setName( "server" );
		Scope requestScope = new Scope();
		requestScope.setName( "request" );
		Scope variablesScope = new Scope();
		variablesScope.setName( "variables" );

		XValueChildrenList children = BoxLangStackFrame.buildScopeChildren(
		    new Scope[] { serverScope, requestScope, variablesScope },
		    scope -> namedValue( scope.getName() )
		);

		assertEquals( 3, children.size() );
		assertEquals( "server", children.getName( 0 ) );
		assertEquals( "request", children.getName( 1 ) );
		assertEquals( "variables", children.getName( 2 ) );
	}

	private static @NotNull XNamedValue namedValue( @NotNull String name ) {
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
}
