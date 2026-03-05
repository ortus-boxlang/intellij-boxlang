package com.ortussolutions.intellijboxlang.testbox;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility methods for detecting TestBox specs and locating the TestBox runner.
 */
public final class TestBoxUtil {

	/**
	 * Default bundle pattern matching TestBox's default --bundles-pattern.
	 */
	private static final Pattern	SPEC_FILE_PATTERN		= Pattern.compile(
	    ".*(?:Spec|Test).*\\.(?:bx|cfc)$",
	    Pattern.CASE_INSENSITIVE
	);

	/**
	 * Pattern to detect BDD-style run() method in a class.
	 */
	private static final Pattern	BDD_RUN_PATTERN			= Pattern.compile(
	    "(?:^|\\s)(?:public\\s+|private\\s+|remote\\s+|static\\s+)*(?:any\\s+|void\\s+)?function\\s+run\\s*\\(",
	    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
	);

	/**
	 * Pattern to detect describe() calls — BDD style.
	 */
	static final Pattern			DESCRIBE_PATTERN		= Pattern.compile(
	    "\\bdescribe\\s*\\(",
	    Pattern.CASE_INSENSITIVE
	);

	/**
	 * Pattern to detect it() calls — BDD style.
	 */
	static final Pattern			IT_PATTERN				= Pattern.compile(
	    "\\bit\\s*\\(",
	    Pattern.CASE_INSENSITIVE
	);

	/**
	 * Pattern to detect test() calls — BDD alias.
	 */
	static final Pattern			TEST_CALL_PATTERN		= Pattern.compile(
	    "\\btest\\s*\\(",
	    Pattern.CASE_INSENSITIVE
	);

	/**
	 * Pattern to detect xUnit-style test functions (function testXxx).
	 */
	static final Pattern			XUNIT_TEST_PATTERN		= Pattern.compile(
	    "(?:^|\\s)(?:public\\s+|private\\s+|remote\\s+|static\\s+)*(?:any\\s+|void\\s+|boolean\\s+|string\\s+|numeric\\s+|struct\\s+|array\\s+)?function\\s+test\\w+\\s*\\(",
	    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
	);

	/**
	 * Pattern to detect @test annotation (either annotation or metadata style).
	 */
	static final Pattern			ANNOTATION_TEST_PATTERN	= Pattern.compile(
	    "@test\\b",
	    Pattern.CASE_INSENSITIVE
	);

	private TestBoxUtil() {
	}

	/**
	 * Returns true if the file matches the TestBox spec naming convention.
	 */
	public static boolean isTestBoxSpecFile( @Nullable VirtualFile file ) {
		if ( file == null || file.isDirectory() ) {
			return false;
		}
		return SPEC_FILE_PATTERN.matcher( file.getName() ).matches();
	}

	/**
	 * Returns true if the file matches the TestBox spec naming convention.
	 */
	public static boolean isTestBoxSpecFile( @Nullable PsiFile psiFile ) {
		if ( psiFile == null ) {
			return false;
		}
		VirtualFile vf = psiFile.getVirtualFile();
		if ( vf != null ) {
			return isTestBoxSpecFile( vf );
		}
		String name = psiFile.getName();
		return name != null && SPEC_FILE_PATTERN.matcher( name ).matches();
	}

	/**
	 * Returns true if the file content looks like a BDD-style TestBox spec
	 * (has a run() method containing describe/it calls).
	 */
	public static boolean isBddSpec( @Nullable String fileContent ) {
		if ( fileContent == null || fileContent.isEmpty() ) {
			return false;
		}
		return BDD_RUN_PATTERN.matcher( fileContent ).find()
		    && ( DESCRIBE_PATTERN.matcher( fileContent ).find()
		        || IT_PATTERN.matcher( fileContent ).find()
		        || TEST_CALL_PATTERN.matcher( fileContent ).find() );
	}

	/**
	 * Returns true if the file content looks like an xUnit-style TestBox spec
	 * (has function testXxx() or @test annotated functions).
	 */
	public static boolean isXunitSpec( @Nullable String fileContent ) {
		if ( fileContent == null || fileContent.isEmpty() ) {
			return false;
		}
		return XUNIT_TEST_PATTERN.matcher( fileContent ).find()
		    || ANNOTATION_TEST_PATTERN.matcher( fileContent ).find();
	}

	/**
	 * Relative path from a TestBox installation root to the BoxLang CLI runner.
	 * The run/run.bat shell scripts at the root are just bash wrappers that invoke:
	 * boxlang system/runners/BoxLangRunner.bx
	 * We pass BoxLangRunner.bx directly to the Java BoxRunner instead.
	 */
	private static final String RUNNER_RELATIVE_PATH = "system/runners/BoxLangRunner.bx";

	/**
	 * Checks if TestBox is installed in the project.
	 * Supports two scenarios:
	 * 1. Normal consumer project: testbox/ folder at project root containing BoxLangRunner.bx
	 * 2. TestBox repo itself: project root has system/runners/BoxLangRunner.bx
	 */
	public static boolean isTestBoxInstalled( @NotNull Project project ) {
		String basePath = project.getBasePath();
		if ( basePath == null ) {
			return false;
		}

		// Case 1: Normal consumer project with testbox/ subdirectory
		if ( Path.of( basePath, "testbox", RUNNER_RELATIVE_PATH ).toFile().exists() ) {
			return true;
		}

		// Case 2: Project root IS the TestBox installation (e.g., TestBox repo)
		return Path.of( basePath, RUNNER_RELATIVE_PATH ).toFile().exists();
	}

	/**
	 * Returns the path to the TestBox BoxLang CLI runner (BoxLangRunner.bx),
	 * or null if not found.
	 * Checks both testbox/system/runners/BoxLangRunner.bx (consumer project)
	 * and system/runners/BoxLangRunner.bx at project root (TestBox repo).
	 */
	public static @Nullable Path findTestBoxRunner( @NotNull Project project ) {
		String basePath = project.getBasePath();
		if ( basePath == null ) {
			return null;
		}

		// Case 1: Normal consumer project — testbox/system/runners/BoxLangRunner.bx
		Path consumerRunner = Path.of( basePath, "testbox", RUNNER_RELATIVE_PATH );
		if ( consumerRunner.toFile().exists() ) {
			return consumerRunner;
		}

		// Case 2: Project root IS TestBox — system/runners/BoxLangRunner.bx at root
		Path rootRunner = Path.of( basePath, RUNNER_RELATIVE_PATH );
		if ( rootRunner.toFile().exists() ) {
			return rootRunner;
		}

		return null;
	}

	/**
	 * Computes the dot-notation bundle path for a test file relative to the project root.
	 * For example, /project/tests/specs/MySpec.bx -> tests.specs.MySpec
	 */
	public static @Nullable String computeBundlePath( @NotNull Project project, @NotNull VirtualFile file ) {
		String basePath = project.getBasePath();
		if ( basePath == null ) {
			return null;
		}

		String filePath = file.getPath();
		if ( !filePath.startsWith( basePath ) ) {
			return null;
		}

		// Get relative path and strip leading separator
		String relative = filePath.substring( basePath.length() );
		if ( relative.startsWith( "/" ) || relative.startsWith( "\\" ) ) {
			relative = relative.substring( 1 );
		}

		// Remove the file extension
		int dotIndex = relative.lastIndexOf( '.' );
		if ( dotIndex > 0 ) {
			relative = relative.substring( 0, dotIndex );
		}

		// Convert path separators to dots
		return relative.replace( '/', '.' ).replace( '\\', '.' );
	}

	/**
	 * Computes the dot-notation directory path for a directory relative to the project root.
	 * For example, /project/tests/specs -> tests.specs
	 */
	public static @Nullable String computeDirectoryPath( @NotNull Project project, @NotNull String directoryPath ) {
		String basePath = project.getBasePath();
		if ( basePath == null ) {
			return null;
		}

		if ( !directoryPath.startsWith( basePath ) ) {
			return null;
		}

		String relative = directoryPath.substring( basePath.length() );
		if ( relative.startsWith( "/" ) || relative.startsWith( "\\" ) ) {
			relative = relative.substring( 1 );
		}
		if ( relative.endsWith( "/" ) || relative.endsWith( "\\" ) ) {
			relative = relative.substring( 0, relative.length() - 1 );
		}

		return relative.replace( '/', '.' ).replace( '\\', '.' );
	}

	/**
	 * Returns the file extension in lowercase, or empty string if none.
	 */
	public static @NotNull String getExtension( @NotNull VirtualFile file ) {
		String ext = file.getExtension();
		return ext != null ? ext.toLowerCase( Locale.ROOT ) : "";
	}

	/**
	 * Returns true if the file is a BoxLang class file (.bx or .cfc).
	 */
	public static boolean isClassFile( @NotNull VirtualFile file ) {
		String ext = getExtension( file );
		return "bx".equals( ext ) || "cfc".equals( ext );
	}
}
