package com.ortussolutions.intellijboxlang.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.ortussolutions.intellijboxlang.runtime.BoxLangRuntimeInstaller;
import com.ortussolutions.intellijboxlang.runtime.BoxLangVersionCatalog;
import com.ortussolutions.intellijboxlang.runtime.BoxLangVersionInfo;
import com.ortussolutions.intellijboxlang.runtime.ForgeBoxDebuggerInstaller;
import com.ortussolutions.intellijboxlang.runtime.ForgeBoxLspInstaller;
import com.ortussolutions.intellijboxlang.runtime.ForgeBoxVersionFetcher;
import com.ortussolutions.intellijboxlang.runtime.InstalledModuleStatus;
import com.ortussolutions.intellijboxlang.runtime.ModuleStatusResolver;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import javax.swing.JComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Application-level (global) BoxLang settings configurable.
 * Shown under Settings → Languages &amp; Frameworks → BoxLang.
 * Manages defaults that apply to all projects unless overridden per-project.
 */
public final class BoxLangApplicationConfigurable implements Configurable {

	private static final Logger	LOG	= Logger.getInstance( BoxLangApplicationConfigurable.class );

	private BoxLangSettingsForm	form;

	/** Cached latest versions, populated asynchronously when the settings page opens. */
	private volatile String		cachedRuntimeLatestVersion;
	private volatile String		cachedLspLatestVersion;
	private volatile String		cachedDebuggerLatestVersion;

	@Override
	public @Nls( capitalization = Nls.Capitalization.Title ) String getDisplayName() {
		return "BoxLang";
	}

	@Override
	public @Nullable JComponent createComponent() {
		form = new BoxLangSettingsForm();

		form.setDownloadListener( new BoxLangSettingsForm.DownloadListener() {

			@Override
			public void onDownloadRuntime() {
				pickAndDownloadRuntime();
			}

			@Override
			public void onChangeRuntime() {
				pickAndDownloadRuntime();
			}

			@Override
			public void onDeleteRuntime() {
				deleteRuntime();
			}

			@Override
			public void onDownloadLsp() {
				pickAndDownloadLsp();
			}

			@Override
			public void onChangeLsp() {
				pickAndDownloadLsp();
			}

			@Override
			public void onDeleteLsp() {
				deleteLsp();
			}

			@Override
			public void onDownloadDebugger() {
				pickAndDownloadDebugger();
			}

			@Override
			public void onChangeDebugger() {
				pickAndDownloadDebugger();
			}

			@Override
			public void onDeleteDebugger() {
				deleteDebugger();
			}
		} );

		fetchLatestVersionsAsync();

		return form.getPanel();
	}

	@Override
	public boolean isModified() {
		if ( form == null ) {
			return false;
		}
		return form.isModified( BoxLangApplicationSettings.getInstance().getSettings() );
	}

	@Override
	public void apply() {
		if ( form == null ) {
			return;
		}
		form.apply( BoxLangApplicationSettings.getInstance().getSettings() );
	}

	@Override
	public void reset() {
		if ( form == null ) {
			return;
		}
		form.reset( BoxLangApplicationSettings.getInstance().getSettings() );
		form.updateModuleStatus( null, cachedRuntimeLatestVersion, cachedLspLatestVersion, cachedDebuggerLatestVersion );
	}

	@Override
	public void disposeUIResources() {
		if ( form != null ) {
			form.setDownloadListener( null );
		}
		form = null;
	}

	// -------------------------------------------------------------------------
	// Async latest version fetch
	// -------------------------------------------------------------------------

	private void fetchLatestVersionsAsync() {
		ProgressManager.getInstance().run( new Task.Backgroundable( null, "Checking for BoxLang Updates", false ) {

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				indicator.setIndeterminate( true );
				String	runtimeLatest	= null;
				String	lspLatest		= null;
				String	debuggerLatest	= null;

				try {
					BoxLangVersionInfo	info	= BoxLangVersionCatalog.resolveLatestInfo();
					String				name	= info.name();
					// Strip "boxlang-" prefix for display
					runtimeLatest = name.startsWith( "boxlang-" ) ? name.substring( "boxlang-".length() ) : name;
				} catch ( IOException e ) {
					LOG.warn( "Failed to fetch latest BoxLang runtime version", e );
				}

				try {
					List<String> lspVersions = ForgeBoxVersionFetcher.fetchLspVersions();
					if ( !lspVersions.isEmpty() ) {
						lspLatest = lspVersions.get( 0 );
					}
				} catch ( IOException e ) {
					LOG.warn( "Failed to fetch LSP versions from ForgeBox", e );
				}

				try {
					List<String> debuggerVersions = ForgeBoxVersionFetcher.fetchDebuggerVersions();
					if ( !debuggerVersions.isEmpty() ) {
						debuggerLatest = debuggerVersions.get( 0 );
					}
				} catch ( IOException e ) {
					LOG.warn( "Failed to fetch debugger versions from ForgeBox", e );
				}

				final String	finalRuntimeLatest	= runtimeLatest;
				final String	finalLspLatest		= lspLatest;
				final String	finalDebuggerLatest	= debuggerLatest;

				ApplicationManager.getApplication().invokeLater( () -> {
					cachedRuntimeLatestVersion	= finalRuntimeLatest;
					cachedLspLatestVersion		= finalLspLatest;
					cachedDebuggerLatestVersion	= finalDebuggerLatest;
					if ( form != null ) {
						form.updateModuleStatus( null, cachedRuntimeLatestVersion, cachedLspLatestVersion,
						    cachedDebuggerLatestVersion );
					}
				} );
			}
		} );
	}

	// -------------------------------------------------------------------------
	// Runtime Download / Change / Delete
	// -------------------------------------------------------------------------

	private void pickAndDownloadRuntime() {
		ProgressManager.getInstance().run( new Task.Backgroundable( null, "Fetching BoxLang Runtime Versions", true ) {

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				try {
					indicator.setText( "Fetching available versions from S3..." );
					List<String>	versions		= BoxLangVersionCatalog.fetchVersionNames();
					// Strip "boxlang-" prefix for display
					List<String>	displayVersions	= versions.stream()
					    .map( v -> v.startsWith( "boxlang-" ) ? v.substring( "boxlang-".length() ) : v )
					    .toList();

					ApplicationManager.getApplication().invokeLater( () -> {
						String selectedDisplay = VersionPickerDialog.showAndGetVersion( null, "BoxLang Runtime", displayVersions );
						if ( selectedDisplay != null ) {
							// Re-add "boxlang-" prefix to look up the download URL
							String resolvedVersion = selectedDisplay.startsWith( "boxlang-" )
							    ? selectedDisplay
							    : "boxlang-" + selectedDisplay;
							downloadRuntimeVersion( resolvedVersion, selectedDisplay );
						}
					} );
				} catch ( IOException e ) {
					LOG.warn( "Failed to fetch BoxLang runtime versions", e );
				}
			}
		} );
	}

	private void downloadRuntimeVersion( String resolvedVersion, String displayVersion ) {
		ProgressManager.getInstance().run( new Task.Backgroundable( null, "Downloading BoxLang Runtime", true ) {

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				try {
					BoxLangVersionInfo info = BoxLangVersionCatalog.resolveVersionInfo( resolvedVersion );
					if ( info == null ) {
						LOG.warn( "Could not resolve BoxLang version info for: " + resolvedVersion );
						return;
					}
					BoxLangRuntimeInstaller.installRuntime( resolvedVersion, info.downloadUrl(), indicator );

					ApplicationManager.getApplication().invokeLater( () -> {
						BoxLangApplicationSettings.getInstance().getSettings().boxLangVersion = displayVersion;
						if ( form != null ) {
							form.updateModuleStatus( null, cachedRuntimeLatestVersion, cachedLspLatestVersion,
							    cachedDebuggerLatestVersion );
						}
					} );
				} catch ( IOException e ) {
					LOG.warn( "Failed to download BoxLang runtime version " + resolvedVersion, e );
				}
			}
		} );
	}

	private void deleteRuntime() {
		InstalledModuleStatus status = ModuleStatusResolver.resolveRuntimeStatus( null );
		if ( !status.installed || status.path == null ) {
			return;
		}

		// The jar is at <cacheRoot>/<resolvedVersion>/<resolvedVersion>.jar — delete the version dir
		Path	jarPath		= Path.of( status.path );
		Path	versionDir	= jarPath.getParent();
		String	displayPath	= versionDir != null ? versionDir.toString() : status.path;

		int		confirm		= Messages.showYesNoDialog(
		    ( com.intellij.openapi.project.Project ) null,
		    "Delete the installed BoxLang runtime at:\n" + displayPath + "\n\nThis cannot be undone.",
		    "Delete BoxLang Runtime",
		    Messages.getWarningIcon() );

		if ( confirm != Messages.YES ) {
			return;
		}

		final Path toDelete = versionDir != null ? versionDir : jarPath;

		ProgressManager.getInstance().run( new Task.Backgroundable( null, "Deleting BoxLang Runtime", false ) {

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				indicator.setText( "Deleting runtime files..." );
				indicator.setIndeterminate( true );
				try {
					deleteRecursively( toDelete );
				} catch ( IOException e ) {
					LOG.warn( "Failed to delete BoxLang runtime at " + toDelete, e );
				}

				ApplicationManager.getApplication().invokeLater( () -> {
					BoxLangApplicationSettings.getInstance().getSettings().boxLangVersion = null;
					if ( form != null ) {
						form.updateModuleStatus( null, cachedRuntimeLatestVersion, cachedLspLatestVersion,
						    cachedDebuggerLatestVersion );
					}
				} );
			}
		} );
	}

	// -------------------------------------------------------------------------
	// LSP Download / Change / Delete
	// -------------------------------------------------------------------------

	private void pickAndDownloadLsp() {
		ProgressManager.getInstance().run( new Task.Backgroundable( null, "Fetching LSP Versions", true ) {

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				try {
					indicator.setText( "Fetching available versions from ForgeBox..." );
					List<String> versions = ForgeBoxVersionFetcher.fetchLspVersions();

					ApplicationManager.getApplication().invokeLater( () -> {
						String selectedVersion = VersionPickerDialog.showAndGetVersion( null, "BoxLang LSP", versions );
						if ( selectedVersion != null ) {
							downloadLspVersion( selectedVersion );
						}
					} );
				} catch ( IOException e ) {
					LOG.warn( "Failed to fetch LSP versions", e );
				}
			}
		} );
	}

	private void downloadLspVersion( String version ) {
		ProgressManager.getInstance().run( new Task.Backgroundable( null, "Downloading BoxLang LSP", true ) {

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				try {
					Path targetDir = resolveGlobalBoxLangHome().resolve( "modules" ).resolve( "bx-lsp" );
					ForgeBoxLspInstaller.install( version, targetDir, indicator );

					ApplicationManager.getApplication().invokeLater( () -> {
						BoxLangApplicationSettings.getInstance().getSettings().lspVersion = version;
						if ( form != null ) {
							form.updateModuleStatus( null, cachedRuntimeLatestVersion, cachedLspLatestVersion,
							    cachedDebuggerLatestVersion );
						}
					} );
				} catch ( IOException e ) {
					LOG.warn( "Failed to download LSP version " + version, e );
				}
			}
		} );
	}

	private void deleteLsp() {
		InstalledModuleStatus status = ModuleStatusResolver.resolveLspStatus( null );
		if ( !status.installed || status.path == null ) {
			return;
		}

		int confirm = Messages.showYesNoDialog(
		    ( com.intellij.openapi.project.Project ) null,
		    "Delete the installed LSP module at:\n" + status.path + "\n\nThis cannot be undone.",
		    "Delete BoxLang LSP",
		    Messages.getWarningIcon() );

		if ( confirm != Messages.YES ) {
			return;
		}

		Path	modulePath	= Path.of( status.path );
		Path	toDelete	= modulePath;

		ProgressManager.getInstance().run( new Task.Backgroundable( null, "Deleting BoxLang LSP", false ) {

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				indicator.setText( "Deleting LSP module files..." );
				indicator.setIndeterminate( true );
				try {
					deleteRecursively( toDelete );
				} catch ( IOException e ) {
					LOG.warn( "Failed to delete LSP module at " + toDelete, e );
				}

				ApplicationManager.getApplication().invokeLater( () -> {
					BoxLangApplicationSettings.getInstance().getSettings().lspVersion = null;
					if ( form != null ) {
						form.updateModuleStatus( null, cachedRuntimeLatestVersion, cachedLspLatestVersion,
						    cachedDebuggerLatestVersion );
					}
				} );
			}
		} );
	}

	// -------------------------------------------------------------------------
	// Debugger Download / Change / Delete
	// -------------------------------------------------------------------------

	private void pickAndDownloadDebugger() {
		ProgressManager.getInstance().run( new Task.Backgroundable( null, "Fetching Debugger Versions", true ) {

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				try {
					indicator.setText( "Fetching available versions from ForgeBox..." );
					List<String> versions = ForgeBoxVersionFetcher.fetchDebuggerVersions();

					ApplicationManager.getApplication().invokeLater( () -> {
						String selectedVersion = VersionPickerDialog.showAndGetVersion( null, "BoxLang Debugger", versions );
						if ( selectedVersion != null ) {
							downloadDebuggerVersion( selectedVersion );
						}
					} );
				} catch ( IOException e ) {
					LOG.warn( "Failed to fetch debugger versions", e );
				}
			}
		} );
	}

	private void downloadDebuggerVersion( String version ) {
		ProgressManager.getInstance().run( new Task.Backgroundable( null, "Downloading BoxLang Debugger", true ) {

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				try {
					// Install into the global BoxLang home modules directory
					Path targetDir = resolveGlobalBoxLangHome().resolve( "modules" ).resolve( "bx-debugger" );
					ForgeBoxDebuggerInstaller.install( version, targetDir, indicator );

					ApplicationManager.getApplication().invokeLater( () -> {
						BoxLangApplicationSettings.getInstance().getSettings().debuggerVersion = version;
						if ( form != null ) {
							form.updateModuleStatus( null, cachedRuntimeLatestVersion, cachedLspLatestVersion,
							    cachedDebuggerLatestVersion );
						}
					} );
				} catch ( IOException e ) {
					LOG.warn( "Failed to download debugger version " + version, e );
				}
			}
		} );
	}

	private void deleteDebugger() {
		InstalledModuleStatus status = ModuleStatusResolver.resolveDebuggerStatus( null );
		if ( !status.installed || status.path == null ) {
			return;
		}

		Path	modulePath	= Path.of( status.path );
		String	displayPath	= modulePath.toString();

		int		confirm		= Messages.showYesNoDialog(
		    ( com.intellij.openapi.project.Project ) null,
		    "Delete the installed Debugger module at:\n" + displayPath + "\n\nThis cannot be undone.",
		    "Delete BoxLang Debugger",
		    Messages.getWarningIcon() );

		if ( confirm != Messages.YES ) {
			return;
		}

		final Path toDelete = modulePath;

		ProgressManager.getInstance().run( new Task.Backgroundable( null, "Deleting BoxLang Debugger", false ) {

			@Override
			public void run( @NotNull ProgressIndicator indicator ) {
				indicator.setText( "Deleting Debugger module files..." );
				indicator.setIndeterminate( true );
				try {
					if ( toDelete != null ) {
						deleteRecursively( toDelete );
					}
				} catch ( IOException e ) {
					LOG.warn( "Failed to delete Debugger module at " + toDelete, e );
				}

				ApplicationManager.getApplication().invokeLater( () -> {
					BoxLangApplicationSettings.getInstance().getSettings().debuggerVersion = null;
					if ( form != null ) {
						form.updateModuleStatus( null, cachedRuntimeLatestVersion, cachedLspLatestVersion,
						    cachedDebuggerLatestVersion );
					}
				} );
			}
		} );
	}

	/**
	 * Returns the effective global BoxLang home directory.
	 * Uses the user-configured {@code boxLangHome} setting when set; otherwise falls back to
	 * {@code ~/.boxlang} via {@link BoxLangStoragePaths#getUserBoxLangHome()}.
	 */
	private static Path resolveGlobalBoxLangHome() {
		BoxLangSettingsState settings = BoxLangApplicationSettings.getInstance().getSettings();
		if ( settings.boxLangHome != null && !settings.boxLangHome.isBlank() ) {
			return Path.of( settings.boxLangHome );
		}
		return BoxLangStoragePaths.getUserBoxLangHome();
	}

	private static void deleteRecursively( Path path ) throws IOException {
		if ( !Files.exists( path ) ) {
			return;
		}
		Files.walkFileTree( path, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult visitFile( Path file, BasicFileAttributes attrs ) throws IOException {
				Files.delete( file );
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory( Path dir, IOException exc ) throws IOException {
				if ( exc != null ) {
					throw exc;
				}
				Files.delete( dir );
				return FileVisitResult.CONTINUE;
			}
		} );
	}
}
