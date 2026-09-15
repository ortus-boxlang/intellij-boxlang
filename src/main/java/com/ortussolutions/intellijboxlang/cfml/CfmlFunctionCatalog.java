package com.ortussolutions.intellijboxlang.cfml;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class CfmlFunctionCatalog {

	public record Parameter( String name, String type, boolean required, String description ) {
	}

	public record Function( String name, String syntax, String returns, String description, List<Parameter> params, String origin, String sourceName,
	    String member ) {

		public Function( String name, String syntax, String returns, String description, List<Parameter> params ) {
			this( name, syntax, returns, description, params, "CFDocs" );
		}

		public Function( String name, String syntax, String returns, String description, List<Parameter> params, String origin ) {
			this( name, syntax, returns, description, params, origin, name, null );
		}

		public Function {
			sourceName	= sourceName == null ? name : sourceName;
			origin		= origin == null ? "CFDocs" : origin;
			params		= params == null ? List.of() : List.copyOf( params );
		}
	}

	private CfmlFunctionCatalog() {
	}

	private static class Holder {

		private static final List<Function> FUNCTIONS = load();

		private static List<Function> load() {
			try ( var input = new InputStreamReader( Objects.requireNonNull(
			    CfmlFunctionCatalog.class.getResourceAsStream( "/cfml/functions.json" ) ), StandardCharsets.UTF_8 ) ) {
				return List.copyOf( Arrays.asList( new Gson().fromJson( input, Function[].class ) ) );
			} catch ( java.io.IOException e ) {
				throw new IllegalStateException( "Unable to read bundled CFML functions", e );
			}
		}
	}

	public static List<Function> functions() {
		return Holder.FUNCTIONS;
	}
}
