package com.ortussolutions.intellijboxlang.actions;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.UIUtil;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import javax.swing.Action;

public class BoxLangToolingStatusActionTest extends BasePlatformTestCase {

	public void testReportIsEditableAndCopiedOnlyOnExplicitAction() {
		CopyPasteManager.getInstance().setContents( new StringSelection( "existing clipboard" ) );
		var dialog = new BoxLangToolingStatusAction.ReportDialog( getProject(), "original report with local details" );
		try {
			assertEquals( "existing clipboard", CopyPasteManager.getInstance().getContents( DataFlavor.stringFlavor ) );
			var preview = UIUtil.findComponentOfType( dialog.createCenterPanel(), JBTextArea.class );
			assertNotNull( preview );
			assertTrue( preview.isEditable() );
			preview.setText( "reviewed report" );
			Action copy = Arrays.stream( dialog.createActions() ).filter( a -> "Copy Report".equals( a.getValue( Action.NAME ) ) ).findFirst().orElseThrow();
			copy.actionPerformed( new ActionEvent( dialog, ActionEvent.ACTION_PERFORMED, "copy" ) );
			assertEquals( "reviewed report", CopyPasteManager.getInstance().getContents( DataFlavor.stringFlavor ) );
		} finally {
			Disposer.dispose( dialog.getDisposable() );
		}
	}
}
