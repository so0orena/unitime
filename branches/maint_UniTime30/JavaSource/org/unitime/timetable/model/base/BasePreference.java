/*
 * UniTime 3.0 (University Course Timetabling & Student Sectioning Application)
 * Copyright (C) 2007, UniTime.org, and individual contributors
 * as indicated by the @authors tag.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*/
package org.unitime.timetable.model.base;

import java.io.Serializable;


/**
 * This is an object that contains data related to the  table.
 * Do not modify this class because it will be overwritten if the configuration file
 * related to this class is modified.
 *
 * @hibernate.class
 *  table=""
 */

public abstract class BasePreference  implements Serializable {

	public static String REF = "Preference";


	// constructors
	public BasePreference () {
		initialize();
	}

	/**
	 * Constructor for primary key
	 */
	public BasePreference (java.lang.Long uniqueId) {
		this.setUniqueId(uniqueId);
		initialize();
	}

	/**
	 * Constructor for required fields
	 */
	public BasePreference (
		java.lang.Long uniqueId,
		org.unitime.timetable.model.PreferenceGroup owner,
		org.unitime.timetable.model.PreferenceLevel prefLevel) {

		this.setUniqueId(uniqueId);
		this.setOwner(owner);
		this.setPrefLevel(prefLevel);
		initialize();
	}

	protected void initialize () {}



	private int hashCode = Integer.MIN_VALUE;

	// primary key
	private java.lang.Long uniqueId;

	// many to one
	private org.unitime.timetable.model.PreferenceGroup owner;
	private org.unitime.timetable.model.PreferenceLevel prefLevel;



	/**
	 * Return the unique identifier of this class
     * @hibernate.id
     *  generator-class="sequence"
     *  column="UNIQUEID"
     */
	public java.lang.Long getUniqueId () {
		return uniqueId;
	}

	/**
	 * Set the unique identifier of this class
	 * @param uniqueId the new ID
	 */
	public void setUniqueId (java.lang.Long uniqueId) {
		this.uniqueId = uniqueId;
		this.hashCode = Integer.MIN_VALUE;
	}




	/**
	 * Return the value associated with the column: OWNER_ID
	 */
	public org.unitime.timetable.model.PreferenceGroup getOwner () {
		return owner;
	}

	/**
	 * Set the value related to the column: OWNER_ID
	 * @param owner the OWNER_ID value
	 */
	public void setOwner (org.unitime.timetable.model.PreferenceGroup owner) {
		this.owner = owner;
	}



	/**
	 * Return the value associated with the column: PREF_LEVEL_ID
	 */
	public org.unitime.timetable.model.PreferenceLevel getPrefLevel () {
		return prefLevel;
	}

	/**
	 * Set the value related to the column: PREF_LEVEL_ID
	 * @param prefLevel the PREF_LEVEL_ID value
	 */
	public void setPrefLevel (org.unitime.timetable.model.PreferenceLevel prefLevel) {
		this.prefLevel = prefLevel;
	}





	public boolean equals (Object obj) {
		if (null == obj) return false;
		if (!(obj instanceof org.unitime.timetable.model.Preference)) return false;
		else {
			org.unitime.timetable.model.Preference preference = (org.unitime.timetable.model.Preference) obj;
			if (null == this.getUniqueId() || null == preference.getUniqueId()) return false;
			else return (this.getUniqueId().equals(preference.getUniqueId()));
		}
	}

	public int hashCode () {
		if (Integer.MIN_VALUE == this.hashCode) {
			if (null == this.getUniqueId()) return super.hashCode();
			else {
				String hashStr = this.getClass().getName() + ":" + this.getUniqueId().hashCode();
				this.hashCode = hashStr.hashCode();
			}
		}
		return this.hashCode;
	}


	public String toString () {
		return super.toString();
	}


}