package com.ortussolutions.intellijboxlang.lsp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves per-file BoxLang app context from static declarations.
 * This intentionally supports only literal/static forms in v1.
 */
public final class BoxLangLspAppContextResolver {

	private static final Set<String>	CONTEXT_FILENAMES									= Set.of(
	    "application.bx",
	    "application.cfc",
	    "moduleconfig.bx",
	    "moduleconfig.cfc"
	);
	private static final Set<String>	BOXLANG_EXTENSIONS									= Set.of(
	    "bx",
	    "bxs",
	    "bxm",
	    "cfc",
	    "cfm"
	);
	private static final Pattern		SINGLE_QUOTED_REFERENCE_PATTERN						= Pattern.compile( "'([^']+)'" );
	private static final Pattern		REGISTER_MAPPING_PATTERN							= Pattern.compile(
	    "(?is)registerMapping\\s*\\(\\s*(['\"])([^'\"]+)\\1\\s*,\\s*((?:[^()]+|\\([^()]*\\))*)\\)"
	);
	private static final Pattern		MAPPING_INDEX_ASSIGN_PATTERN						= Pattern.compile(
	    "(?is)this\\.mappings\\s*\\[\\s*(['\"])([^'\"]+)\\1\\s*\\]\\s*=\\s*([^;\\n]+)"
	);
	private static final Pattern		STRUCT_MAPPING_ASSIGN_PATTERN						= Pattern.compile(
	    "(?is)(?:this\\.)?(?:mappings?|[A-Za-z_][A-Za-z0-9_]*mappings?)\\s*=\\s*\\{(.*?)\\}"
	);
	private static final Pattern		STRUCT_ENTRY_PATTERN								= Pattern.compile(
	    "(?is)(['\"])([^'\"]+)\\1\\s*[:=]\\s*([^,\\n\\r}]+)"
	);
	private static final Pattern		MAPPING_REFERENCE_PATTERN							= Pattern.compile(
	    "(?is)^\\s*this\\.mappings\\s*\\[\\s*(['\"])([^'\"]+)\\1\\s*\\]\\s*$"
	);
	private static final Pattern		MODULE_MAPPING_ASSIGN_PATTERN						= Pattern.compile(
	    "(?is)this\\.(?:mapping|cfmapping)\\s*=\\s*([^;\\n]+)"
	);
	private static final Pattern		VARIABLE_ASSIGN_PATTERN								= Pattern.compile(
	    "(?is)(?:^|[;\\n\\r])\\s*(?:var\\s+)?((?:this|variables|local|request|application|server|session)\\.[A-Za-z_][A-Za-z0-9_]*|[A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^;\\n\\r]+)"
	);
	private static final Pattern		REGISTER_MODULE_DIR_PATTERN							= Pattern.compile(
	    "(?is)registerModuleDirectory\\s*\\(\\s*((?:[^()]+|\\([^()]*\\))*)\\)"
	);
	private static final Pattern		MODULE_DIR_ASSIGN_PATTERN							= Pattern.compile(
	    "(?is)this\\.(?:moduleDirectory|modulesDirectory|moduleDirectories|modulesDirectories|moduleDirs)\\s*=\\s*([^;\\n]+)"
	);
	private static final Pattern		EXPAND_PATH_PATTERN									= Pattern.compile(
	    "(?is)expandPath\\s*\\(\\s*(['\"])([^'\"]+)\\1\\s*\\)"
	);
	private static final Pattern		CURRENT_TEMPLATE_DIR_PATTERN						= Pattern.compile(
	    "(?is)getDirectoryFromPath\\s*\\(\\s*(?:getCurrentTemplatePath|getBaseTemplatePath)\\s*\\(\\s*\\)\\s*\\)"
	);
	private static final Pattern		CURRENT_TEMPLATE_PATH_PATTERN						= Pattern.compile(
	    "(?is)^(?:getCurrentTemplatePath|getBaseTemplatePath)\\s*\\(\\s*\\)\\s*$"
	);
	private static final Pattern		SINGLE_QUOTED_OR_DOUBLE_QUOTED_EXPRESSION_PATTERN	= Pattern.compile(
	    "(?is)^\\s*(['\"])(.*)\\1\\s*$"
	);
	private static final Pattern		INTERPOLATION_PATTERN								= Pattern.compile(
	    "#([A-Za-z_][A-Za-z0-9_\\.]*)#"
	);
	private static final Pattern		STRING_LITERAL_PATTERN								= Pattern.compile(
	    "(['\"])([^'\"]+)\\1"
	);

	public BoxLangLspAppContext resolve( Project project, @Nullable VirtualFile file, @Nullable String manualModules ) {
		Path projectRoot = project.getBasePath() != null ? Path.of( project.getBasePath() ) : null;
		return resolve( projectRoot, toLocalPath( file ), manualModules );
	}

	public BoxLangLspAppContext resolve( @Nullable Path projectRoot, @Nullable Path filePath, @Nullable String manualModules ) {
		Path	appRoot			= findNearestApplicationRoot( filePath, projectRoot );
		Path	resolutionRoot	= appRoot;
		if ( projectRoot != null
		    && Files.isRegularFile( appRoot.resolve( "Application.cfc" ) )
		    && !Files.isRegularFile( appRoot.resolve( "Application.bx" ) ) ) {
			resolutionRoot = projectRoot.toAbsolutePath().normalize();
		}

		Map<String, String>	mappings	= new LinkedHashMap<>();
		LinkedHashSet<Path>	moduleDirs	= new LinkedHashSet<>();
		Deque<Path>			pending		= new ArrayDeque<>();
		LinkedHashSet<Path>	parsedFiles	= new LinkedHashSet<>();

		addConventionalModuleDirs( appRoot, projectRoot, moduleDirs );
		moduleDirs.addAll( parseManualModuleDirectories( manualModules, appRoot, resolutionRoot ) );
		enqueueApplicationConfigFiles( filePath, appRoot, pending );
		enqueueNearestModuleConfigFiles( filePath, appRoot, resolutionRoot, pending );
		enqueueModuleConfigFiles( moduleDirs, pending );

		while ( !pending.isEmpty() ) {
			Path file = pending.removeFirst().toAbsolutePath().normalize();
			if ( !parsedFiles.add( file ) || !Files.isRegularFile( file ) ) {
				continue;
			}
			ParseResult parsed = parseFile( file, appRoot, resolutionRoot );
			for ( Map.Entry<String, String> entry : parsed.mappings.entrySet() ) {
				mappings.putIfAbsent( entry.getKey(), entry.getValue() );
			}
			for ( Path moduleDir : parsed.moduleDirectories ) {
				if ( moduleDirs.add( moduleDir ) ) {
					enqueueModuleConfigFiles( List.of( moduleDir ), pending );
				}
			}
		}

		List<Path>			normalizedModuleDirs	= moduleDirs.stream()
		    .map( path -> path.toAbsolutePath().normalize() )
		    .distinct()
		    .sorted( Comparator.comparing( Path::toString ) )
		    .toList();
		Map<String, String>	normalizedMappings		= mappings.entrySet().stream()
		    .sorted( Map.Entry.comparingByKey( String.CASE_INSENSITIVE_ORDER ) )
		    .collect(
		        LinkedHashMap::new,
		        ( acc, entry ) -> acc.put( entry.getKey(), entry.getValue() ),
		        Map::putAll
		    );

		String				contextHash				= computeContextHash( appRoot, normalizedMappings, normalizedModuleDirs );
		return new BoxLangLspAppContext( appRoot, normalizedMappings, normalizedModuleDirs, contextHash );
	}

	public boolean canResolveMappedClassReference( BoxLangLspAppContext context, String classReference ) {
		return resolveMappedClassPath( context, classReference ) != null;
	}

	public @Nullable Path resolveMappedClassPath( BoxLangLspAppContext context, String classReference ) {
		if ( classReference == null || classReference.isBlank() || !classReference.contains( "." ) ) {
			return null;
		}
		String[] segments = classReference.split( "\\." );
		if ( segments.length < 2 ) {
			return null;
		}
		String alias = canonicalizeAlias( segments[ 0 ] );
		if ( alias.isEmpty() ) {
			return null;
		}

		Path mappingRoot = findMappingRoot( context.mappings(), alias );
		if ( mappingRoot == null ) {
			return null;
		}
		String	relativePath	= String.join( "/", java.util.Arrays.copyOfRange( segments, 1, segments.length ) );
		String	basePath		= relativePath.replace( '.', '/' );
		for ( String extension : List.of( ".bx", ".bxs", ".cfc", ".cfm", ".bxm" ) ) {
			Path candidate = mappingRoot.resolve( basePath + extension ).toAbsolutePath().normalize();
			if ( Files.isRegularFile( candidate ) ) {
				return candidate;
			}
		}
		return null;
	}

	public boolean isContextRelevantFile( @Nullable VirtualFile file ) {
		if ( file == null || !file.isInLocalFileSystem() ) {
			return false;
		}
		return isContextRelevantPath( Path.of( file.getPath() ) );
	}

	public boolean isContextRelevantPath( @Nullable String pathText ) {
		if ( pathText == null || pathText.isBlank() ) {
			return false;
		}
		return isContextRelevantPath( Path.of( pathText ) );
	}

	public boolean isContextRelevantPath( @Nullable Path path ) {
		if ( path == null ) {
			return false;
		}
		String fileName = path.getFileName() != null ? path.getFileName().toString().toLowerCase( Locale.ROOT ) : "";
		if ( CONTEXT_FILENAMES.contains( fileName ) ) {
			return true;
		}
		String extension = extension( fileName );
		return BOXLANG_EXTENSIONS.contains( extension ) && path.toString().toLowerCase( Locale.ROOT ).contains( "module" );
	}

	public static boolean isExtendsOrImplementsDiagnostic( Diagnostic diagnostic ) {
		if ( diagnostic == null ) {
			return false;
		}
		Either<String, Integer> code = diagnostic.getCode();
		if ( code != null && code.isLeft() && code.getLeft() != null ) {
			String normalized = code.getLeft().trim();
			if ( normalized.equals( "invalidExtends" ) || normalized.equals( "invalidImplements" ) ) {
				return true;
			}
		}
		String message = diagnostic.getMessage();
		if ( message == null ) {
			return false;
		}
		String lower = message.toLowerCase( Locale.ROOT );
		return lower.contains( "extends reference" ) || lower.contains( "implements reference" );
	}

	public static @Nullable String extractClassReference( Diagnostic diagnostic ) {
		if ( diagnostic == null || diagnostic.getMessage() == null ) {
			return null;
		}
		Matcher matcher = SINGLE_QUOTED_REFERENCE_PATTERN.matcher( diagnostic.getMessage() );
		if ( matcher.find() ) {
			return matcher.group( 1 ).trim();
		}
		return null;
	}

	static Path findNearestApplicationRoot( @Nullable Path filePath, @Nullable Path projectRoot ) {
		Path fallback = projectRoot != null
		    ? projectRoot.toAbsolutePath().normalize()
		    : filePath != null ? filePath.toAbsolutePath().normalize().getParent() : Path.of( "." ).toAbsolutePath().normalize();
		if ( filePath == null ) {
			return fallback;
		}
		Path cursor = Files.isDirectory( filePath ) ? filePath : filePath.getParent();
		while ( cursor != null ) {
			if ( Files.isRegularFile( cursor.resolve( "Application.bx" ) ) ) {
				return cursor.toAbsolutePath().normalize();
			}
			if ( Files.isRegularFile( cursor.resolve( "Application.cfc" ) ) ) {
				return cursor.toAbsolutePath().normalize();
			}
			if ( projectRoot != null ) {
				Path projectRootNormalized = projectRoot.toAbsolutePath().normalize();
				if ( !cursor.startsWith( projectRootNormalized ) ) {
					break;
				}
			}
			cursor = cursor.getParent();
		}
		return fallback;
	}

	static List<Path> parseManualModuleDirectories( @Nullable String manualModules, Path appRoot, Path sourceDirectory ) {
		if ( manualModules == null || manualModules.isBlank() ) {
			return List.of();
		}
		List<Path> paths = new ArrayList<>();
		for ( String token : manualModules.split( "[,\\n;]" ) ) {
			Path resolved = toResolvedPath( token, sourceDirectory, appRoot );
			if ( resolved != null ) {
				paths.add( resolved );
			}
		}
		return paths;
	}

	private static void addConventionalModuleDirs( Path appRoot, @Nullable Path projectRoot, LinkedHashSet<Path> moduleDirs ) {
		addExistingDirectories( appRoot, List.of( "modules_app", "modules", ".boxlang/modules" ), moduleDirs );
		if ( projectRoot != null ) {
			Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
			if ( !normalizedProjectRoot.equals( appRoot ) ) {
				addExistingDirectories( normalizedProjectRoot, List.of( ".boxlang/modules" ), moduleDirs );
			}
		}
	}

	private static void addExistingDirectories( Path root, List<String> dirNames, LinkedHashSet<Path> moduleDirs ) {
		for ( String dirName : dirNames ) {
			Path dir = root.resolve( dirName ).toAbsolutePath().normalize();
			if ( Files.isDirectory( dir ) ) {
				moduleDirs.add( dir );
			}
		}
	}

	private static void enqueueModuleConfigFiles( Iterable<Path> moduleDirs, Deque<Path> queue ) {
		for ( Path moduleDir : moduleDirs ) {
			if ( moduleDir == null || !Files.isDirectory( moduleDir ) ) {
				continue;
			}
			enqueueIfFile( queue, moduleDir.resolve( "ModuleConfig.bx" ) );
			enqueueIfFile( queue, moduleDir.resolve( "ModuleConfig.cfc" ) );
			try ( var stream = Files.list( moduleDir ) ) {
				stream.filter( Files::isDirectory )
				    .forEach( child -> {
					    enqueueIfFile( queue, child.resolve( "ModuleConfig.bx" ) );
					    enqueueIfFile( queue, child.resolve( "ModuleConfig.cfc" ) );
				    } );
			} catch ( IOException ignored ) {
				// Best-effort static discovery.
			}
		}
	}

	private static void enqueueApplicationConfigFiles( @Nullable Path filePath, Path appRoot, Deque<Path> queue ) {
		enqueueIfFile( queue, appRoot.resolve( "Application.bx" ) );
		enqueueIfFile( queue, appRoot.resolve( "Application.cfc" ) );
		if ( filePath == null ) {
			return;
		}
		Path cursor = Files.isDirectory( filePath ) ? filePath : filePath.getParent();
		while ( cursor != null ) {
			enqueueIfFile( queue, cursor.resolve( "Application.bx" ) );
			enqueueIfFile( queue, cursor.resolve( "Application.cfc" ) );
			if ( cursor.equals( appRoot ) ) {
				break;
			}
			if ( !cursor.startsWith( appRoot ) ) {
				break;
			}
			cursor = cursor.getParent();
		}
	}

	private static void enqueueNearestModuleConfigFiles( @Nullable Path filePath, Path appRoot, Path resolutionRoot, Deque<Path> queue ) {
		enqueueModuleConfigAtRoot( queue, appRoot );
		if ( resolutionRoot != null && !resolutionRoot.toAbsolutePath().normalize().equals( appRoot.toAbsolutePath().normalize() ) ) {
			enqueueModuleConfigAtRoot( queue, resolutionRoot );
		}
		if ( filePath == null ) {
			return;
		}
		Path cursor = Files.isDirectory( filePath ) ? filePath : filePath.getParent();
		while ( cursor != null ) {
			enqueueModuleConfigAtRoot( queue, cursor );
			if ( cursor.equals( appRoot ) ) {
				break;
			}
			if ( !cursor.startsWith( appRoot ) ) {
				break;
			}
			cursor = cursor.getParent();
		}
	}

	private static void enqueueModuleConfigAtRoot( Deque<Path> queue, Path root ) {
		if ( root == null ) {
			return;
		}
		enqueueIfFile( queue, root.resolve( "ModuleConfig.bx" ) );
		enqueueIfFile( queue, root.resolve( "ModuleConfig.cfc" ) );
	}

	private static void enqueueIfFile( Deque<Path> queue, Path candidate ) {
		if ( Files.isRegularFile( candidate ) ) {
			queue.add( candidate );
		}
	}

	private static ParseResult parseFile( Path file, Path appRoot, Path resolutionRoot ) {
		try {
			String						text				= Files.readString( file, StandardCharsets.UTF_8 );
			Path						sourceDir			= file.getParent() != null ? file.getParent() : appRoot;

			Map<String, String>			mappings			= new LinkedHashMap<>();
			Map<String, String>			knownVariables		= new LinkedHashMap<>();
			List<MappingCandidate>		pendingMappings		= new ArrayList<>( extractMappingCandidates( text ) );
			List<VariableAssignment>	pendingVariables	= new ArrayList<>( extractVariableAssignments( text ) );

			int							maxPasses			= Math.max( 1, pendingMappings.size() + pendingVariables.size() + 4 );
			for ( int pass = 0; pass < maxPasses; pass++ ) {
				boolean progress = false;

				for ( int i = pendingVariables.size() - 1; i >= 0; i-- ) {
					VariableAssignment	assignment	= pendingVariables.get( i );
					String				value		= evaluateExpressionToString(
					    assignment.expression(),
					    sourceDir,
					    resolutionRoot,
					    mappings,
					    knownVariables
					);
					if ( value == null || value.isBlank() ) {
						continue;
					}
					storeVariableValue( knownVariables, assignment.name(), value );
					pendingVariables.remove( i );
					progress = true;
				}

				for ( int i = pendingMappings.size() - 1; i >= 0; i-- ) {
					MappingCandidate	candidate	= pendingMappings.get( i );
					String				alias		= canonicalizeAlias( candidate.alias() );
					if ( !alias.isEmpty() ) {
						Path path = resolveMappingCandidatePath(
						    candidate.pathExpression(),
						    sourceDir,
						    resolutionRoot,
						    mappings,
						    knownVariables
						);
						if ( path != null ) {
							mappings.putIfAbsent( alias, path.toString() );
							pendingMappings.remove( i );
							progress = true;
						}
					}
				}

				if ( !progress ) {
					break;
				}
			}

			if ( file.getFileName() != null
			    && ( "moduleconfig.bx".equalsIgnoreCase( file.getFileName().toString() )
			        || "moduleconfig.cfc".equalsIgnoreCase( file.getFileName().toString() ) ) ) {
				String	moduleAlias		= extractAssignedExpression( text, MODULE_MAPPING_ASSIGN_PATTERN );
				String	evaluatedAlias	= evaluateExpressionToString( moduleAlias, sourceDir, resolutionRoot, mappings, knownVariables );
				String	alias			= canonicalizeAlias( evaluatedAlias != null ? evaluatedAlias : moduleAlias );
				if ( !alias.isEmpty() ) {
					mappings.putIfAbsent( alias, sourceDir.toAbsolutePath().normalize().toString() );
				}
			}

			LinkedHashSet<Path> moduleDirectories = new LinkedHashSet<>();
			for ( String expression : extractModuleDirectoryExpressions( text ) ) {
				String	evaluated			= evaluateExpressionToString( expression, sourceDir, resolutionRoot, mappings, knownVariables );
				Path	resolvedEvaluated	= resolveExpressionPath( expression, evaluated, sourceDir, resolutionRoot );
				if ( resolvedEvaluated != null ) {
					moduleDirectories.add( resolvedEvaluated );
				}
				for ( String literal : extractLiteralPathValues( expression ) ) {
					String	interpolated	= resolveInterpolations( literal, knownVariables );
					Path	path			= toResolvedPath( interpolated, sourceDir, resolutionRoot );
					if ( path != null ) {
						moduleDirectories.add( path );
					}
				}
			}
			return new ParseResult( mappings, moduleDirectories.stream().toList() );
		} catch ( IOException ignored ) {
			return ParseResult.EMPTY;
		}
	}

	private static List<MappingCandidate> extractMappingCandidates( String text ) {
		List<MappingCandidate>	candidates		= new ArrayList<>();
		Matcher					registerMatcher	= REGISTER_MAPPING_PATTERN.matcher( text );
		while ( registerMatcher.find() ) {
			candidates.add( new MappingCandidate( registerMatcher.group( 2 ), registerMatcher.group( 3 ) ) );
		}
		Matcher indexedAssignmentMatcher = MAPPING_INDEX_ASSIGN_PATTERN.matcher( text );
		while ( indexedAssignmentMatcher.find() ) {
			candidates.add( new MappingCandidate( indexedAssignmentMatcher.group( 2 ), indexedAssignmentMatcher.group( 3 ) ) );
		}
		Matcher structMatcher = STRUCT_MAPPING_ASSIGN_PATTERN.matcher( text );
		while ( structMatcher.find() ) {
			String	block			= structMatcher.group( 1 );
			Matcher	entryMatcher	= STRUCT_ENTRY_PATTERN.matcher( block );
			while ( entryMatcher.find() ) {
				candidates.add( new MappingCandidate( entryMatcher.group( 2 ), entryMatcher.group( 3 ) ) );
			}
		}
		return candidates;
	}

	private static List<String> extractModuleDirectoryExpressions( String text ) {
		List<String>	expressions		= new ArrayList<>();
		Matcher			registerMatcher	= REGISTER_MODULE_DIR_PATTERN.matcher( text );
		while ( registerMatcher.find() ) {
			expressions.add( registerMatcher.group( 1 ) );
		}
		Matcher assignMatcher = MODULE_DIR_ASSIGN_PATTERN.matcher( text );
		while ( assignMatcher.find() ) {
			expressions.add( assignMatcher.group( 1 ) );
		}
		return expressions;
	}

	private static String extractAssignedExpression( String text, Pattern assignmentPattern ) {
		Matcher matcher = assignmentPattern.matcher( text );
		if ( matcher.find() ) {
			return matcher.group( 1 );
		}
		return "";
	}

	private static String extractFirstLiteralPathValue( String expression ) {
		if ( expression == null || expression.isBlank() ) {
			return "";
		}
		Matcher expandMatcher = EXPAND_PATH_PATTERN.matcher( expression );
		if ( expandMatcher.find() ) {
			return expandMatcher.group( 2 );
		}
		Matcher literalMatcher = STRING_LITERAL_PATTERN.matcher( expression );
		if ( literalMatcher.find() ) {
			return literalMatcher.group( 2 );
		}
		return "";
	}

	private static @Nullable Path resolveMappingCandidatePath(
	    String expression,
	    Path sourceDirectory,
	    Path resolutionRoot,
	    Map<String, String> knownMappings,
	    Map<String, String> knownVariables ) {
		String evaluated = evaluateExpressionToString( expression, sourceDirectory, resolutionRoot, knownMappings, knownVariables );
		return resolveExpressionPath( expression, evaluated, sourceDirectory, resolutionRoot );
	}

	private static @Nullable Path resolveExpressionPath(
	    @Nullable String originalExpression,
	    @Nullable String evaluatedValue,
	    Path sourceDirectory,
	    Path resolutionRoot ) {
		if ( evaluatedValue == null || evaluatedValue.isBlank() ) {
			return null;
		}
		if ( originalExpression != null && !isQuotedLiteralExpression( originalExpression ) ) {
			try {
				Path candidate = Path.of( evaluatedValue );
				if ( candidate.isAbsolute() ) {
					return candidate.toAbsolutePath().normalize();
				}
			} catch ( Exception ignored ) {
				// Fall through to path resolution heuristic.
			}
		}
		return toResolvedPath( evaluatedValue, sourceDirectory, resolutionRoot );
	}

	private static boolean isQuotedLiteralExpression( String expression ) {
		if ( expression == null ) {
			return false;
		}
		return SINGLE_QUOTED_OR_DOUBLE_QUOTED_EXPRESSION_PATTERN.matcher( expression.trim() ).matches();
	}

	private static List<String> extractLiteralPathValues( String expression ) {
		if ( expression == null || expression.isBlank() ) {
			return List.of();
		}
		List<String>	values			= new ArrayList<>();
		Matcher			expandMatcher	= EXPAND_PATH_PATTERN.matcher( expression );
		while ( expandMatcher.find() ) {
			values.add( expandMatcher.group( 2 ) );
		}
		Matcher literalMatcher = STRING_LITERAL_PATTERN.matcher( expression );
		while ( literalMatcher.find() ) {
			values.add( literalMatcher.group( 2 ) );
		}
		return values;
	}

	private static @Nullable Path findMappingRoot( Map<String, String> mappings, String alias ) {
		String direct = mappings.get( alias );
		if ( direct != null ) {
			return Path.of( direct );
		}
		String lowerAlias = alias.toLowerCase( Locale.ROOT );
		for ( Map.Entry<String, String> entry : mappings.entrySet() ) {
			if ( entry.getKey().equalsIgnoreCase( lowerAlias ) ) {
				return Path.of( entry.getValue() );
			}
		}
		return null;
	}

	private static List<VariableAssignment> extractVariableAssignments( String text ) {
		List<VariableAssignment>	assignments	= new ArrayList<>();
		Matcher						matcher		= VARIABLE_ASSIGN_PATTERN.matcher( text );
		while ( matcher.find() ) {
			String	name		= matcher.group( 1 );
			String	expression	= matcher.group( 2 );
			if ( name == null || name.isBlank() || expression == null || expression.isBlank() ) {
				continue;
			}
			assignments.add( new VariableAssignment( name.trim(), expression.trim() ) );
		}
		return assignments;
	}

	private static @Nullable String evaluateExpressionToString(
	    @Nullable String expression,
	    Path sourceDirectory,
	    Path resolutionRoot,
	    Map<String, String> knownMappings,
	    Map<String, String> knownVariables ) {
		if ( expression == null ) {
			return null;
		}
		String trimmed = expression.trim();
		if ( trimmed.isEmpty() ) {
			return null;
		}

		List<String> concatenatedParts = splitTopLevelConcatenation( trimmed );
		if ( concatenatedParts.size() > 1 ) {
			StringBuilder concatenated = new StringBuilder();
			for ( String part : concatenatedParts ) {
				String value = evaluateExpressionToString( part, sourceDirectory, resolutionRoot, knownMappings, knownVariables );
				if ( value == null ) {
					return null;
				}
				concatenated.append( value );
			}
			return concatenated.toString();
		}

		if ( CURRENT_TEMPLATE_DIR_PATTERN.matcher( trimmed ).find() ) {
			return sourceDirectory.toAbsolutePath().normalize().toString();
		}
		if ( CURRENT_TEMPLATE_PATH_PATTERN.matcher( trimmed ).matches() ) {
			return sourceDirectory.toAbsolutePath().normalize().toString();
		}

		String expandArgument = extractCallArgument( trimmed, "expandPath" );
		if ( expandArgument != null ) {
			String	resolvedArgument	= evaluateExpressionToString( expandArgument, sourceDirectory, resolutionRoot, knownMappings, knownVariables );
			Path	expandedPath		= toResolvedPath( resolvedArgument, sourceDirectory, resolutionRoot );
			return expandedPath != null ? expandedPath.toString() : null;
		}

		String directoryArgument = extractCallArgument( trimmed, "getDirectoryFromPath" );
		if ( directoryArgument != null ) {
			String resolvedArgument = evaluateExpressionToString( directoryArgument, sourceDirectory, resolutionRoot, knownMappings, knownVariables );
			if ( resolvedArgument == null || resolvedArgument.isBlank() ) {
				return null;
			}
			Path resolvedPath = toResolvedPath( resolvedArgument, sourceDirectory, resolutionRoot );
			if ( resolvedPath == null ) {
				return null;
			}
			Path directory = Files.isDirectory( resolvedPath ) ? resolvedPath : resolvedPath.getParent();
			return directory != null ? directory.toAbsolutePath().normalize().toString() : null;
		}

		Matcher mappingMatcher = MAPPING_REFERENCE_PATTERN.matcher( trimmed );
		if ( mappingMatcher.matches() ) {
			String	alias		= canonicalizeAlias( mappingMatcher.group( 2 ) );
			Path	mappingRoot	= findMappingRoot( knownMappings, alias );
			return mappingRoot != null ? mappingRoot.toAbsolutePath().normalize().toString() : null;
		}

		Matcher quotedMatcher = SINGLE_QUOTED_OR_DOUBLE_QUOTED_EXPRESSION_PATTERN.matcher( trimmed );
		if ( quotedMatcher.matches() ) {
			return resolveInterpolations( quotedMatcher.group( 2 ), knownVariables );
		}

		String variableValue = lookupVariableValue( knownVariables, trimmed );
		if ( variableValue != null ) {
			return variableValue;
		}

		// Best effort: plain path-ish text.
		if ( trimmed.contains( "/" ) || trimmed.contains( "\\" ) ) {
			return resolveInterpolations( trimmed, knownVariables );
		}

		return null;
	}

	private static List<String> splitTopLevelConcatenation( String expression ) {
		List<String>	parts		= new ArrayList<>();
		StringBuilder	current		= new StringBuilder();
		int				parenDepth	= 0;
		char			quote		= 0;
		for ( int i = 0; i < expression.length(); i++ ) {
			char c = expression.charAt( i );
			if ( quote != 0 ) {
				current.append( c );
				if ( c == quote ) {
					quote = 0;
				}
				continue;
			}
			if ( c == '\'' || c == '"' ) {
				quote = c;
				current.append( c );
				continue;
			}
			if ( c == '(' ) {
				parenDepth++;
				current.append( c );
				continue;
			}
			if ( c == ')' ) {
				parenDepth = Math.max( 0, parenDepth - 1 );
				current.append( c );
				continue;
			}
			if ( parenDepth == 0 && ( c == '&' || c == '+' ) ) {
				String part = current.toString().trim();
				if ( !part.isBlank() ) {
					parts.add( part );
				}
				current.setLength( 0 );
				continue;
			}
			current.append( c );
		}
		String finalPart = current.toString().trim();
		if ( !finalPart.isBlank() ) {
			parts.add( finalPart );
		}
		return parts.isEmpty() ? List.of( expression ) : parts;
	}

	private static @Nullable String extractCallArgument( String expression, String functionName ) {
		if ( expression == null || functionName == null ) {
			return null;
		}
		String prefix = functionName + "(";
		if ( !expression.regionMatches( true, 0, prefix, 0, prefix.length() ) || !expression.endsWith( ")" ) ) {
			return null;
		}
		int firstParen = expression.indexOf( '(' );
		if ( firstParen < 0 || expression.length() <= firstParen + 1 ) {
			return null;
		}
		return expression.substring( firstParen + 1, expression.length() - 1 ).trim();
	}

	private static void storeVariableValue( Map<String, String> knownVariables, String name, String value ) {
		if ( knownVariables == null || name == null || name.isBlank() || value == null || value.isBlank() ) {
			return;
		}
		String normalized = normalizeVariableName( name );
		if ( normalized.isBlank() ) {
			return;
		}
		knownVariables.putIfAbsent( normalized, value );
		int dot = normalized.indexOf( '.' );
		if ( dot > 0 && dot < normalized.length() - 1 ) {
			knownVariables.putIfAbsent( normalized.substring( dot + 1 ), value );
		}
	}

	private static @Nullable String lookupVariableValue( Map<String, String> knownVariables, String name ) {
		if ( knownVariables == null || name == null || name.isBlank() ) {
			return null;
		}
		String normalized = normalizeVariableName( name );
		if ( normalized.isBlank() ) {
			return null;
		}
		String direct = knownVariables.get( normalized );
		if ( direct != null ) {
			return direct;
		}
		int dot = normalized.indexOf( '.' );
		if ( dot > 0 && dot < normalized.length() - 1 ) {
			return knownVariables.get( normalized.substring( dot + 1 ) );
		}
		return null;
	}

	private static String normalizeVariableName( String name ) {
		if ( name == null ) {
			return "";
		}
		return name.trim().toLowerCase( Locale.ROOT );
	}

	private static String resolveInterpolations( @Nullable String text, Map<String, String> knownVariables ) {
		if ( text == null || text.isBlank() || knownVariables == null || knownVariables.isEmpty() ) {
			return text == null ? "" : text;
		}
		Matcher			matcher	= INTERPOLATION_PATTERN.matcher( text );
		StringBuffer	buffer	= new StringBuffer();
		while ( matcher.find() ) {
			String	token		= matcher.group( 1 );
			String	replacement	= lookupVariableValue( knownVariables, token );
			if ( replacement == null ) {
				replacement = matcher.group( 0 );
			}
			matcher.appendReplacement( buffer, Matcher.quoteReplacement( replacement ) );
		}
		matcher.appendTail( buffer );
		return buffer.toString();
	}

	private static String canonicalizeAlias( @Nullable String alias ) {
		if ( alias == null ) {
			return "";
		}
		String normalized = alias.trim().replace( "\\", "/" );
		while ( normalized.startsWith( "/" ) ) {
			normalized = normalized.substring( 1 );
		}
		while ( normalized.endsWith( "/" ) ) {
			normalized = normalized.substring( 0, normalized.length() - 1 );
		}
		return normalized.trim();
	}

	private static @Nullable Path toResolvedPath( @Nullable String pathText, Path sourceDirectory, Path resolutionRoot ) {
		if ( pathText == null ) {
			return null;
		}
		String normalized = pathText.trim();
		if ( normalized.isEmpty() ) {
			return null;
		}
		if ( normalized.startsWith( "~/" ) ) {
			String home = System.getProperty( "user.home" );
			if ( home == null || home.isBlank() ) {
				return null;
			}
			return Path.of( home ).resolve( normalized.substring( 2 ) ).toAbsolutePath().normalize();
		}
		String pathValue = normalized;
		if ( ( pathValue.startsWith( "/" ) || pathValue.startsWith( "\\" ) ) && !looksLikeWindowsDrivePath( pathValue ) ) {
			pathValue = trimLeadingSlashes( pathValue );
			return resolutionRoot.resolve( pathValue ).toAbsolutePath().normalize();
		}
		Path candidate = Path.of( pathValue );
		if ( candidate.isAbsolute() ) {
			return candidate.toAbsolutePath().normalize();
		}
		return sourceDirectory.resolve( candidate ).toAbsolutePath().normalize();
	}

	private static boolean looksLikeWindowsDrivePath( String text ) {
		return text.length() > 2 && Character.isLetter( text.charAt( 0 ) ) && text.charAt( 1 ) == ':';
	}

	private static String trimLeadingSlashes( String text ) {
		int index = 0;
		while ( index < text.length() && ( text.charAt( index ) == '/' || text.charAt( index ) == '\\' ) ) {
			index++;
		}
		return text.substring( index );
	}

	private static Path toLocalPath( @Nullable VirtualFile file ) {
		if ( file == null || !file.isInLocalFileSystem() ) {
			return null;
		}
		try {
			return file.toNioPath();
		} catch ( RuntimeException e ) {
			return Path.of( file.getPath() );
		}
	}

	private static String extension( String filename ) {
		int dot = filename.lastIndexOf( '.' );
		if ( dot < 0 || dot == filename.length() - 1 ) {
			return "";
		}
		return filename.substring( dot + 1 );
	}

	static String computeContextHash( Path appRoot, Map<String, String> mappings, List<Path> moduleDirs ) {
		try {
			MessageDigest digest = MessageDigest.getInstance( "SHA-256" );
			digest.update( appRoot.toString().getBytes( StandardCharsets.UTF_8 ) );
			mappings.entrySet().stream()
			    .sorted( Map.Entry.comparingByKey( String.CASE_INSENSITIVE_ORDER ) )
			    .forEach( entry -> {
				    digest.update( ( "|" + entry.getKey() + "=" + entry.getValue() ).getBytes( StandardCharsets.UTF_8 ) );
			    } );
			moduleDirs.stream()
			    .filter( Objects::nonNull )
			    .map( Path::toString )
			    .sorted()
			    .forEach( value -> digest.update( ( "|" + value ).getBytes( StandardCharsets.UTF_8 ) ) );
			byte[]			bytes	= digest.digest();
			StringBuilder	hash	= new StringBuilder( bytes.length * 2 );
			for ( byte value : bytes ) {
				hash.append( String.format( Locale.ROOT, "%02x", value ) );
			}
			return hash.toString();
		} catch ( NoSuchAlgorithmException e ) {
			return Integer.toHexString( Objects.hash( appRoot, mappings, moduleDirs ) );
		}
	}

	private record ParseResult(
	    Map<String, String> mappings,
	    List<Path> moduleDirectories ) {

		private static final ParseResult EMPTY = new ParseResult( Map.of(), List.of() );
	}

	private record MappingCandidate(
	    String alias,
	    String pathExpression ) {
	}

	private record VariableAssignment(
	    String name,
	    String expression ) {
	}
}
