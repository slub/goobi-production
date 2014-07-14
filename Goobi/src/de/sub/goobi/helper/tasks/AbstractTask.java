/**
 * This file is part of the Goobi Application - a Workflow tool for the support
 * of mass digitization.
 * 
 * (c) 2014 Goobi. Digialisieren im Verein e.V. &lt;contact@goobi.org&gt;
 * 
 * Visit the websites for more information.
 *     		- http://www.goobi.org/en/
 *     		- https://github.com/goobi
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU General Public License cover the whole combination. As a special
 * exception, the copyright holders of this library give you permission to link
 * this library with independent modules to produce an executable, regardless of
 * the license terms of these independent modules, and to copy and distribute
 * the resulting executable under terms of your choice, provided that you also
 * meet, for each linked independent module, the terms and conditions of the
 * license of that module. An independent module is a module which is not
 * derived from or based on this library. If you modify this library, you may
 * extend this exception to your version of the library, but you are not obliged
 * to do so. If you do not wish to do so, delete this exception statement from
 * your version.
 */
package de.sub.goobi.helper.tasks;

import java.util.concurrent.TimeUnit;

import org.joda.time.Duration;

import de.sub.goobi.helper.Helper;

public class AbstractTask extends Thread {
	/**
	 * The enum Actions lists the available instructions to the housekeeper what
	 * to do with a terminated thread. These are:
	 * 
	 * <dl>
	 * <dt><code>DELETE_IMMEDIATELY</code></dt>
	 * <dd>The thread shall be disposed of as soon as is has gracefully stopped.
	 * </dd>
	 * <dt><code>KEEP_FOR_A_WHILE</code></dt>
	 * <dd>The default behaviour: A thread that terminated either normally or
	 * abnormally is kept around in memory for a while and then removed
	 * automatically. Numeric and temporary limits can be configured.</dd>
	 * <dt><code>PREPARE_FOR_RESTART</code></dt>
	 * <dd>If the thread was interrupted by a user, replace it by a new one,
	 * passing in the state of the old one to be able to coninue work.</dd>
	 * </dl>
	 * 
	 * @author Matthias Ronge &lt;matthias.ronge@zeutschel.de&gt;
	 */
	public enum Behaviour {
		DELETE_IMMEDIATELY, KEEP_FOR_A_WHILE, PREPARE_FOR_RESTART
	}

	private static final Behaviour DEFAULT_BEHAVIOUR = Behaviour.KEEP_FOR_A_WHILE;

	protected String detail = null; // a string telling details, which file is processed or which error occurred
	protected Exception exception = null; // an exception caught
	private int progress = 0; // a value from 0 to 100
	private Behaviour behaviourAfterTermination; // what to do if the thread crashed
	private Long passedAway = null;

	Duration getDurationDead() {
		if (passedAway == null) {
			return null;
		}
		long elapsed = System.nanoTime() - passedAway;
		return new Duration(TimeUnit.MILLISECONDS.convert(elapsed, TimeUnit.NANOSECONDS));
	}

	public int getProgress() {
		return progress;
	}

	TaskState getTaskState() {
		switch (getState()) {
		case NEW:
			return TaskState.NEW;
		case TERMINATED:
			if (behaviourAfterTermination == null) {
				behaviourAfterTermination = DEFAULT_BEHAVIOUR;
			}
			if (exception != null) {
				return TaskState.CRASHED;
			}
			if (Behaviour.PREPARE_FOR_RESTART.equals(behaviourAfterTermination)) {
				return TaskState.STOPPED;
			} else {
				return TaskState.FINISHED;
			}
		default:
			if (isInterrupted()) {
				return TaskState.STOPPING;
			} else {
				return TaskState.WORKING;
			}
		}
	}

	public String getWorkDetail() {
		return detail;
	}

	protected void setNameDetail(String detail) {
		StringBuilder composer = new StringBuilder(119);
		composer.append(Helper.getTranslation(getClass().getSimpleName()));
		if (detail != null) {
			composer.append(": ");
			composer.append(detail);
		}
		super.setName(composer.toString());
	}

	protected void setProgress(int progress) {
		if (progress >= 0 || progress <= 100) {
			this.progress = progress;
		} else if (exception == null) {
			exception = new IllegalArgumentException(String.valueOf(progress));
		}
	}

	protected void setWorkDetail(String detail) {
		this.detail = detail;
	}

	public void interrupt(Behaviour mode) {
		behaviourAfterTermination = mode;
	}

	Behaviour getBehaviourAfterTermination() {
		return behaviourAfterTermination;
	}

	Exception getException() {
		return exception;
	}

	void setTimeOfDeath() {
		passedAway = System.nanoTime();
	}

}
