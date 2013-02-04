/*
 * UniTime 3.2 (University Timetabling Application)
 * Copyright (C) 2010, UniTime LLC, and individual contributors
 * as indicated by the @authors tag.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
*/
package org.unitime.timetable.gwt.shared;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * @author Tomas Muller
 */
public class SimpleEditInterface implements IsSerializable {
	
	public static enum Type implements IsSerializable {
		area("Academic Area"),
		classification("Academic Classification"),
		major("Major"),
		minor("Minor"),
		group("Student Group"),
		consent("Offering Consent Type"),
		creditFormat("Course Credit Format"),
		creditType("Course Credit Type"),
		creditUnit("Course Credit Unit"),
		position("Position Type"),
		sectioning("Student Scheduling Status Type"),
		roles("Role"),
		permissions("Permission"),
		examType("Examination Type"),
		eventStatus("Event Status", "Event Statuses"),
		featureType("Room Feature Type"),
		instructorRole("Instructor Role"),
		dateMapping("Event Date Mapping"),
		stdEvtNote("Standard Event Note"),
		;
	
		private String iSingular, iPlural;
		
		Type(String singular, String plural) {
			iSingular = singular; iPlural = plural;
		}
		
		Type(String singular) {
			this(singular, singular + "s");
		}
		
		public String getTitle() { return iPlural; }
		public String getTitlePlural() { return iPlural; }
		public String getTitleSingular() { return iSingular; }
	}
	
	public static enum FieldType implements IsSerializable {
		text, textarea, number, toggle, list, multi, students, person, date, parent;
	}
	
	public static enum Flag implements IsSerializable {
		HIDDEN,
		READ_ONLY,
		UNIQUE,
		NOT_EMPTY,
		PARENT_NOT_EMPTY,
		FLOAT,
		NEGATIVE,
		SHOW_PARENT_IF_EMPTY,
		;
		
		public int toInt() { return 1 << ordinal(); }
		public boolean has(int flags) { return (flags & toInt()) == toInt(); }
	}
	
	private Type iType = null;
	private List<Record> iRecords = new ArrayList<Record>();
	private Field[] iFields = null;
	private boolean iEditable = true, iAddable = true, iSaveOrder = true;
	private int[] iSort = null;
	private Long iSessionId = null;
	private String iSessionName = null;
	
	public SimpleEditInterface() {
	}

	public SimpleEditInterface(Type type, Field... fields) {
		iType = type;
		iFields = fields;
	}
	
	public boolean isSaveOrder() { return iSaveOrder; }
	public void setSaveOrder(boolean saveOrder) { iSaveOrder = saveOrder; } 
	
	public Long getSessionId() { return iSessionId; }
	public void setSessionId(Long sessionId) { iSessionId = sessionId; }
	
	public String getSessionName() { return iSessionName; }
	public void setSessionName(String sessionName) { iSessionName = sessionName; }
	
	public Type getType() { return iType; }
	
	public List<Record> getRecords() { return iRecords; }
	public Record addRecord(Long uniqueId, boolean deletable) {
		Record r = new Record(uniqueId, iFields.length, deletable);
		for (int i = 0; i < iFields.length; i++)
			if (!iFields[i].isEditable()) r.setField(i, null, false);
		iRecords.add(r);
		return r;
	}
	
	public Record addRecord(Long uniqueId) {
		return addRecord(uniqueId, true);
	}
	
	public Record insertEmptyRecord(int pos) {
		Record r = new Record(null, iFields.length);
		iRecords.add(pos, r);
		return r;
	}
	
	public void moveRecord(int row, int before) {
		Record r = iRecords.get(row);
		iRecords.remove(row);
		iRecords.add(before + (row < before ? -1 : 0), r);
	}
	
	public Record getRecord(Long uniqueId) {
		for (Record r: iRecords)
			if (r.getUniqueId() != null && r.getUniqueId().equals(uniqueId))
				return r;
		return null;
	}
	
	public List<Record> getNewRecords() {
		List<Record> ret = new ArrayList<Record>();
		for (Record r: iRecords) {
			if (r.getUniqueId() != null || r.isEmpty()) continue;
			ret.add(r);
		}
		return ret;
	}
	
	public Field[] getFields() { return iFields; }
	
	public int indexOf(String name) {
		for (int i = 0; i < iFields.length; i++)
			if (iFields[i].getName().equals(name)) return i;
		return -1;
	}
	
	public boolean isEditable() { return iEditable; }
	public void setEditable(boolean editable) { iEditable = editable; }
	
	public boolean isAddable() { return iAddable; }
	public void setAddable(boolean addable) { iAddable = addable; }
	
	public int[] getSortBy() { return iSort; }
	public void setSortBy(int... columns) { iSort = columns; }
	public RecordComparator getComparator() {
		return new RecordComparator();
	}
	
	public class RecordComparator implements Comparator<Record> {

		public int compare(int index, Record r1, Record r2) {
			if (getFields()[0].getType() == FieldType.parent) {
				Record p1 = ("+".equals(r1.getField(0)) || "-".equals(r1.getField(0)) ? null : getRecord(Long.valueOf(r1.getField(0))));
				Record p2 = ("+".equals(r2.getField(0)) || "-".equals(r2.getField(0)) ? null : getRecord(Long.valueOf(r2.getField(0))));
				if ((p1 == null ? r1 : p1).equals(p2 == null ? r2 : p2)) { // same parents
					if (p1 != null && p2 == null) return 1; // r1 is already a parent
					if (p1 == null && p2 != null) return -1; // r2 is already a parent
					// same level
				} else if (p1 != null || p2 != null) { // different parents
					return compare(index, p1 == null ? r1 : p1, p2 == null ? r2 : p2); // compare parents
				}
			}
			if (index < 0)
				return (r1.getUniqueId() == null ? r2.getUniqueId() == null ? 0 : 1 : r1.getUniqueId() == null ? -1 : r1.getUniqueId().compareTo(r2.getUniqueId()));
			Field field = getFields()[index]; 
			String s1 = r1.getText(field, index);
			String s2 = r2.getText(field, index);
			if (s1 == null) return (s2 == null ? 0 : 1);
			if (s2 == null) return -1;
			switch (field.getType()) {
			case students:
				return new Integer(s1.isEmpty() ? 0 : s1.split("\\n").length).compareTo(s2.isEmpty() ? 0 : s2.split("\\n").length);
			default:
				try {
					Double d1 = Double.parseDouble(s1.isEmpty() ? "0": s1);
					Double d2 = Double.parseDouble(s2.isEmpty() ? "0": s2);
					return d1.compareTo(d2);
				} catch (NumberFormatException e) {
					return s1.compareTo(s2);
				}
			}
		}
		
		public int compare(Record r1, Record r2) {
			if (getSortBy() != null) {
				for (int i: getSortBy()) {
					int cmp = compare(i, r1, r2);
					if (cmp != 0) return cmp;
				}
			} else {
				for (int i = 0; i < r1.getValues().length; i++) {
					int cmp = compare(i, r1, r2);
					if (cmp != 0) return cmp;
				}
			}
			return compare(-1, r1, r2);
		}
	}
	
	public static class Record implements IsSerializable {
		private Long iUniqueId = null;
		private String[] iValues = null;
		private boolean[] iEditable = null;
		private boolean iDeletable = true;
		
		public Record() {
		}
		
		public Record(Long uniqueId, int nrFields, boolean deletable) {
			iUniqueId = uniqueId;
			iValues = new String[nrFields];
			iEditable = new boolean[nrFields];
			for (int i = 0; i < nrFields; i++) {
				iValues[i] = null;
				iEditable[i] = true;
			}
			iDeletable = deletable;
		}
		
		public Record(Long uniqueId, int nrFields) {
			this(uniqueId, nrFields, true);
		}
		
		public Long getUniqueId() { return iUniqueId; }
		public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }
		
		public void setField(int index, String value, boolean editable) {
			iValues[index] = value;
			iEditable[index] = editable;
		}
		
		public void setField(int index, String value) {
			iValues[index] = value;
		}
		
		public String getField(int index) {
			return iValues[index];
		}
		
		public boolean isEditable(int index) {
			return iEditable[index];
		}
		
		public boolean isEditable() {
			for (boolean editable: iEditable)
				if (editable) return true;
			return false;
		}

		public void addToField(int index, String value) {
			if (iValues[index] == null)
				iValues[index] = value;
			else
				iValues[index] += "|" + value;
		}
		
		public String[] getValues(int index) {
			return (iValues[index] == null ? new String[] {} : iValues[index].split("\\|"));
		}
		
		public String[] getValues() { return iValues; }
		public void setValues(String[] values) { iValues = values; }
		
		public String getText(Field f, int index) {
			String value = getField(index);
			if (value == null) return "";
			if (f.getType() == FieldType.list) {
				for (ListItem item: f.getValues()) {
					if (item.getValue().equals(value)) return item.getText();
				}
			} else if (f.getType() == FieldType.multi) {
				String text = "";
				for (String val: getValues(index)) {
					for (ListItem item: f.getValues()) {
						if (item.getValue().equals(val)) {
							if (!text.isEmpty()) text += ", ";
							text += item.getText();
						}
					}
				}
				return text;
			}
			return value;
		}
		
		public boolean isEmpty() {
			if (getUniqueId() != null) return false;
			for (String v: iValues) {
				if (v != null && !v.isEmpty()) return false;
			}
			return true;
		}
		
		public boolean isDeletable() { return iDeletable; }
		public void setDeletable(boolean deletable) { iDeletable = deletable; }
		
		@Override
		public boolean equals(Object o) {
			if (o == null || !(o instanceof Record)) return false;
			Record r = (Record)o;
			if (getUniqueId() != null) return getUniqueId().equals(r.getUniqueId());
			return (r.getUniqueId() != null ? false : super.equals(o));
		}
	}
	
	public static class ListItem implements IsSerializable {
		private String iValue, iText;
		public ListItem() {}
		public ListItem(String value, String text) {
			iValue = value; iText = text;
		}
		public String getValue() { return iValue; }
		public String getText() { return iText; }
	}
	
	public static class Field implements IsSerializable {
		private String iName = null;
		private FieldType iType = null;
		private int iLength = 0, iWidth = 0, iHeight = 1, iFlags = 0;
		private List<ListItem> iValues = null;
		
		public Field() {}
		
		public Field(String name, FieldType type, int width, int height, int length, Flag... flags) {
			iName = name;
			iType = type;
			iWidth = width;
			iHeight = height;
			iLength = length;
			iFlags = 0;
			for (Flag flag: flags)
				if (flag != null)
					iFlags = iFlags | flag.toInt();
		}
				
		public Field(String name, FieldType type, int width, Flag... flags) {
			this(name, type, width, 1, 0, flags);
		}
		
		public Field(String name, FieldType type, int width, int length, Flag... flags) {
			this(name, type, width, 1, length, flags);
		}
		
		public Field(String name, FieldType type, int width, List<ListItem> values, Flag... flags) {
			this(name, type, width, 0, flags);
			iValues = values;
		}
		
		public String getName() { return iName; }
		public FieldType getType() { return iType; }
		public int getLength() { return iLength; }
		public int getWidth() { return iWidth; }
		public int getHeight() { return iHeight; }
		public List<ListItem> getValues() { return iValues; }
		public void addValue(ListItem item) {
			if (iValues == null) iValues = new ArrayList<ListItem>();
			iValues.add(item);
		}
		public boolean isEditable() { return !Flag.READ_ONLY.has(iFlags); }
		public boolean isVisible() { return !Flag.HIDDEN.has(iFlags); }
		public boolean isUnique() { return Flag.UNIQUE.has(iFlags); }
		public boolean isNotEmpty() { return Flag.NOT_EMPTY.has(iFlags); }
		public boolean isParentNotEmpty() { return Flag.PARENT_NOT_EMPTY.has(iFlags); }
		public boolean isAllowFloatingPoint() { return Flag.FLOAT.has(iFlags); }
		public boolean isAllowNegative() { return Flag.NEGATIVE.has(iFlags); }
		public boolean isShowParentWhenEmpty() { return Flag.SHOW_PARENT_IF_EMPTY.has(iFlags); }
		
		public int hashCode() {
			return getName().hashCode();
		}
		
		public boolean equals(Object o) {
			if (o == null || !(o instanceof Field)) return false;
			return getName().equals(((Field)o).getName());
		}
	}
}
