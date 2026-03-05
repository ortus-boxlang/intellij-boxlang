package com.ortussolutions.intellijboxlang.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Service for fetching and caching BoxLang project templates from the GitHub ortus-boxlang organization.
 * Templates are repositories with names starting with "boxlang-starter".
 */
public final class GitHubTemplateService {

	private static final Logger						LOG					= Logger.getInstance( GitHubTemplateService.class );
	private static final String						GITHUB_ORG			= "ortus-boxlang";
	private static final String						TEMPLATE_PREFIX		= "boxlang-starter";
	private static final String						GITHUB_API_URL		= "https://api.github.com/orgs/" + GITHUB_ORG + "/repos?per_page=100";
	private static final String						CACHE_FILE_NAME		= "boxlang-templates-cache.json";
	private static final Gson						GSON				= new GsonBuilder().create();
	private static final Type						TEMPLATE_LIST_TYPE	= new TypeToken<List<GitHubTemplate>>() {
																		}.getType();

	private static volatile GitHubTemplateService	instance;
	private volatile List<GitHubTemplate>			cachedTemplates;

	private GitHubTemplateService() {
	}

	/**
	 * Returns the singleton instance of the service.
	 */
	@NotNull
	public static GitHubTemplateService getInstance() {
		if ( instance == null ) {
			synchronized ( GitHubTemplateService.class ) {
				if ( instance == null ) {
					instance = new GitHubTemplateService();
				}
			}
		}
		return instance;
	}

	/**
	 * Returns the cached templates, loading from disk cache if needed.
	 * Returns built-in fallback templates if no cache exists.
	 */
	@NotNull
	public List<GitHubTemplate> getCachedTemplates() {
		if ( cachedTemplates != null ) {
			return cachedTemplates;
		}

		// Try to load from disk cache
		List<GitHubTemplate> diskCache = loadFromDiskCache();
		if ( diskCache != null && !diskCache.isEmpty() ) {
			cachedTemplates = diskCache;
			return cachedTemplates;
		}

		// Fall back to built-in templates
		cachedTemplates = getBuiltInTemplates();
		return cachedTemplates;
	}

	/**
	 * Returns the built-in fallback templates.
	 */
	@NotNull
	public List<GitHubTemplate> getBuiltInTemplates() {
		return Arrays.stream( BoxLangProjectTemplateType.values() )
		    .map( GitHubTemplate::fromBuiltIn )
		    .collect( Collectors.toList() );
	}

	/**
	 * Fetches templates from GitHub asynchronously.
	 * On success, updates both in-memory and disk cache.
	 * On failure, returns cached templates or built-in fallbacks.
	 */
	@NotNull
	public CompletableFuture<List<GitHubTemplate>> fetchTemplatesAsync() {
		return CompletableFuture.supplyAsync( () -> {
			try {
				List<GitHubTemplate> templates = fetchFromGitHub();
				if ( templates != null && !templates.isEmpty() ) {
					cachedTemplates = templates;
					saveToDiskCache( templates );
					return templates;
				}
			} catch ( Exception e ) {
				LOG.warn( "Failed to fetch templates from GitHub: " + e.getMessage() );
			}
			return getCachedTemplates();
		} );
	}

	/**
	 * Fetches templates from GitHub synchronously.
	 * On success, updates both in-memory and disk cache.
	 * On failure, returns cached templates or built-in fallbacks.
	 */
	@NotNull
	public List<GitHubTemplate> fetchTemplatesSync() {
		try {
			List<GitHubTemplate> templates = fetchFromGitHub();
			if ( templates != null && !templates.isEmpty() ) {
				cachedTemplates = templates;
				saveToDiskCache( templates );
				return templates;
			}
		} catch ( Exception e ) {
			LOG.warn( "Failed to fetch templates from GitHub: " + e.getMessage() );
		}
		return getCachedTemplates();
	}

	/**
	 * Fetches the list of repositories from the GitHub API.
	 * Filters for repositories with names starting with "boxlang-starter".
	 */
	private List<GitHubTemplate> fetchFromGitHub() throws IOException {
		LOG.info( "Fetching templates from GitHub: " + GITHUB_API_URL );
		try ( InputStream input = URI.create( GITHUB_API_URL ).toURL().openStream() ) {
			String					payload		= new String( input.readAllBytes(), StandardCharsets.UTF_8 );
			List<GitHubTemplate>	allRepos	= GSON.fromJson( payload, TEMPLATE_LIST_TYPE );
			if ( allRepos == null ) {
				return new ArrayList<>();
			}
			// Filter for repos starting with "boxlang-starter"
			List<GitHubTemplate> templates = allRepos.stream()
			    .filter( repo -> repo.getName() != null && repo.getName().startsWith( TEMPLATE_PREFIX ) )
			    .collect( Collectors.toList() );
			LOG.info( "Fetched " + templates.size() + " templates from GitHub (filtered from " + allRepos.size() + " repos)" );
			return templates;
		}
	}

	/**
	 * Loads templates from the disk cache.
	 */
	private List<GitHubTemplate> loadFromDiskCache() {
		Path cachePath = getCachePath();
		if ( !Files.exists( cachePath ) ) {
			return null;
		}

		try {
			String					content		= Files.readString( cachePath, StandardCharsets.UTF_8 );
			List<GitHubTemplate>	templates	= GSON.fromJson( content, TEMPLATE_LIST_TYPE );
			LOG.info( "Loaded " + ( templates != null ? templates.size() : 0 ) + " templates from disk cache" );
			return templates;
		} catch ( Exception e ) {
			LOG.warn( "Failed to load templates from disk cache: " + e.getMessage() );
			return null;
		}
	}

	/**
	 * Saves templates to the disk cache.
	 */
	private void saveToDiskCache( List<GitHubTemplate> templates ) {
		Path cachePath = getCachePath();
		try {
			Files.createDirectories( cachePath.getParent() );
			String json = GSON.toJson( templates );
			Files.writeString( cachePath, json, StandardCharsets.UTF_8 );
			LOG.info( "Saved " + templates.size() + " templates to disk cache" );
		} catch ( Exception e ) {
			LOG.warn( "Failed to save templates to disk cache: " + e.getMessage() );
		}
	}

	/**
	 * Returns the path to the disk cache file.
	 */
	@NotNull
	private Path getCachePath() {
		return Path.of( PathManager.getConfigPath(), "boxlang", CACHE_FILE_NAME );
	}

	/**
	 * Clears both in-memory and disk caches.
	 */
	public void clearCache() {
		cachedTemplates = null;
		try {
			Files.deleteIfExists( getCachePath() );
		} catch ( IOException e ) {
			LOG.warn( "Failed to delete disk cache: " + e.getMessage() );
		}
	}
}
