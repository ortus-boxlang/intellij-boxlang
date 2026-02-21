package com.ortussolutions.intellijboxlang.debug;

import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Properties for BoxLang line breakpoints.
 * This class can be extended to support conditional breakpoints, hit counts, etc.
 */
public class BoxLangBreakpointProperties extends XBreakpointProperties<BoxLangBreakpointProperties> {

	/**
	 * Optional condition expression for conditional breakpoints.
	 */
	private String	condition;

	/**
	 * Optional hit count - breakpoint triggers after this many hits.
	 */
	private int		hitCount	= 0;

	/**
	 * Optional log expression - logs this expression instead of breaking.
	 */
	private String	logExpression;

	public BoxLangBreakpointProperties() {
	}

	@Nullable
	public String getCondition() {
		return condition;
	}

	public void setCondition( @Nullable String condition ) {
		this.condition = condition;
	}

	public int getHitCount() {
		return hitCount;
	}

	public void setHitCount( int hitCount ) {
		this.hitCount = hitCount;
	}

	@Nullable
	public String getLogExpression() {
		return logExpression;
	}

	public void setLogExpression( @Nullable String logExpression ) {
		this.logExpression = logExpression;
	}

	/**
	 * Returns the state to be persisted.
	 */
	@Nullable
	@Override
	public BoxLangBreakpointProperties getState() {
		return this;
	}

	/**
	 * Loads the state from a persisted instance.
	 */
	@Override
	public void loadState( @Nullable BoxLangBreakpointProperties state ) {
		if ( state != null ) {
			this.condition		= state.condition;
			this.hitCount		= state.hitCount;
			this.logExpression	= state.logExpression;
		}
	}
}
