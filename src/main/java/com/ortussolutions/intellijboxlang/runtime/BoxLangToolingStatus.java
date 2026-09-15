package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Session-local setup state. Installation and a running language server are separate states. */
public final class BoxLangToolingStatus {

	public enum Phase {
		NOT_STARTED, WAITING, DOWNLOADING, INSTALLED, STARTING, READY, FAILED, CANCELLED
	}

	public record Entry( Phase phase, String detail ) {
	}

	private static final Key<BoxLangToolingStatus>	KEY		= Key.create( "boxlang.tooling.status" );
	private static final BoxLangToolingStatus		GLOBAL	= new BoxLangToolingStatus();
	private final Map<String, Entry>				entries	= new LinkedHashMap<>();
	private final ArrayDeque<String>				events	= new ArrayDeque<>();

	public static BoxLangToolingStatus get( Project project ) {
		if ( project == null )
			return GLOBAL;
		synchronized ( project ) {
			var status = project.getUserData( KEY );
			if ( status == null ) {
				status = new BoxLangToolingStatus();
				project.putUserData( KEY, status );
			}
			return status;
		}
	}

	public synchronized boolean beginDownload( String name ) {
		Entry current = entries.get( name );
		if ( current != null && current.phase() == Phase.DOWNLOADING )
			return false;
		record( name, Phase.DOWNLOADING, "Download in progress; see IDE background tasks." );
		return true;
	}

	public synchronized void record( String name, Phase phase, String detail ) {
		Entry next = new Entry( phase, detail == null ? "" : detail );
		if ( next.equals( entries.put( name, next ) ) )
			return;
		event( name + ": " + phase + " — " + next.detail() );
	}

	public synchronized void event( String text ) {
		events.addLast( Instant.now() + " " + text.substring( 0, Math.min( text.length(), 2000 ) ) );
		while ( events.size() > 100 )
			events.removeFirst();
	}

	public synchronized Map<String, Entry> entries() {
		return Map.copyOf( entries );
	}

	public synchronized List<String> events() {
		return List.copyOf( events );
	}
}
