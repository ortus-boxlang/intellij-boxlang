package com.ortussolutions.intellijboxlang.project;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a BoxLang project template from the GitHub boxlang-templates organization.
 */
public final class GitHubTemplate {

	private final String	name;
	private final String	description;
	@SerializedName( "html_url" )
	private final String	htmlUrl;
	@SerializedName( "clone_url" )
	private final String	cloneUrl;
	@SerializedName( "default_branch" )
	private final String	defaultBranch;
	private final boolean	isBuiltIn;

	/**
	 * Constructor for GitHub API responses (deserialized via Gson).
	 */
	public GitHubTemplate(
	    @NotNull String name,
	    @Nullable String description,
	    @Nullable String htmlUrl,
	    @Nullable String cloneUrl,
	    @Nullable String defaultBranch ) {
		this( name, description, htmlUrl, cloneUrl, defaultBranch, false );
	}

	/**
	 * Full constructor including built-in flag.
	 */
	public GitHubTemplate(
	    @NotNull String name,
	    @Nullable String description,
	    @Nullable String htmlUrl,
	    @Nullable String cloneUrl,
	    @Nullable String defaultBranch,
	    boolean isBuiltIn ) {
		this.name			= name;
		this.description	= description != null ? description : "";
		this.htmlUrl		= htmlUrl;
		this.cloneUrl		= cloneUrl;
		this.defaultBranch	= defaultBranch != null ? defaultBranch : "main";
		this.isBuiltIn		= isBuiltIn;
	}

	/**
	 * Create a built-in template from the fallback enum.
	 */
	public static GitHubTemplate fromBuiltIn( BoxLangProjectTemplateType type ) {
		return new GitHubTemplate(
		    type.name().toLowerCase().replace( "_", "-" ),
		    type.getDescription(),
		    null,
		    null,
		    null,
		    true
		);
	}

	@NotNull
	public String getName() {
		return name;
	}

	/**
	 * Returns a display-friendly name by converting kebab-case to Title Case.
	 */
	@NotNull
	public String getDisplayName() {
		if ( name == null || name.isEmpty() ) {
			return "";
		}
		String[]		parts	= name.split( "-" );
		StringBuilder	sb		= new StringBuilder();
		for ( String part : parts ) {
			if ( !part.isEmpty() ) {
				if ( sb.length() > 0 ) {
					sb.append( " " );
				}
				sb.append( Character.toUpperCase( part.charAt( 0 ) ) );
				if ( part.length() > 1 ) {
					sb.append( part.substring( 1 ) );
				}
			}
		}
		return sb.toString();
	}

	@NotNull
	public String getDescription() {
		return description;
	}

	@Nullable
	public String getHtmlUrl() {
		return htmlUrl;
	}

	@Nullable
	public String getCloneUrl() {
		return cloneUrl;
	}

	@NotNull
	public String getDefaultBranch() {
		return defaultBranch;
	}

	/**
	 * Returns true if this is a built-in fallback template (not from GitHub).
	 */
	public boolean isBuiltIn() {
		return isBuiltIn;
	}

	/**
	 * Returns the URL to download the repository as a ZIP file.
	 */
	@Nullable
	public String getZipDownloadUrl() {
		if ( htmlUrl == null ) {
			return null;
		}
		return htmlUrl + "/archive/refs/heads/" + defaultBranch + ".zip";
	}

	@Override
	public String toString() {
		return getDisplayName();
	}

	@Override
	public boolean equals( Object obj ) {
		if ( this == obj )
			return true;
		if ( obj == null || getClass() != obj.getClass() )
			return false;
		GitHubTemplate that = ( GitHubTemplate ) obj;
		return name.equals( that.name );
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}
