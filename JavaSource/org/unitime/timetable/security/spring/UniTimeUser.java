/*
 * UniTime 3.4 (University Timetabling Application)
 * Copyright (C) 2012, UniTime LLC, and individual contributors
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
package org.unitime.timetable.security.spring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.ManagerRole;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.model.dao.TimetableManagerDAO;
import org.unitime.timetable.security.roles.HasDepartmentIds;
import org.unitime.timetable.security.roles.HasInstructorIds;
import org.unitime.timetable.security.roles.HasManagerId;
import org.unitime.timetable.security.roles.HasSessionId;
import org.unitime.timetable.security.roles.HasStudentId;
import org.unitime.timetable.security.roles.Role;
import org.unitime.timetable.security.roles.UniTimeRoles;

public class UniTimeUser implements UserDetails {
	private static final long serialVersionUID = 1L;
	
	private String iUserName;
	private String iUserId;
	private String iPassword;
	private String iName;
	private String iEmail;
	private Long iSessionId = null;
	private Role iRole = null;
	
	public UniTimeUser() {}
	
	public UniTimeUser(String username, String password, String id) {
		iUserName = username;
		iUserId = id;
		iPassword = password;
	}
	
	public Long getSessionId() { return iSessionId; }
	public void setSessionId(Long sessionId) { iSessionId = sessionId; }
	public boolean hasSessionId() { return iSessionId != null; }
	
	public Role getRole() { return iRole; }
	public void setRole(Role role) { iRole = role; }
	public boolean hasRole() { return iRole != null; }
	
	public String getName() { return (iName == null ? iUserName : iName); }
	public String getEmail() { return iEmail; }
	
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		Set<SimpleAuthority> authorities = new TreeSet<SimpleAuthority>();
		org.hibernate.Session hibSession = null;
		try {
			hibSession = TimetableManagerDAO.getInstance().createNewSession();
			
			TimetableManager mgr = (TimetableManager)hibSession.createQuery(
					"from TimetableManager where externalUniqueId = :externalUniqueId")
					.setMaxResults(1)
					.setString("externalUniqueId", getExternalUniqueId())
					.uniqueResult();
			if (mgr != null) {
				iName = mgr.getName();
				iEmail = mgr.getEmailAddress();
				for (Department dept: mgr.getDepartments()) {
					authorities.add(new SimpleAuthority("DEPT_" + dept.getUniqueId()));
					authorities.add(new SimpleAuthority("SESN_" + dept.getSessionId()));
				}
				for (ManagerRole role: mgr.getManagerRoles()) {
					authorities.add(new SimpleAuthority("ROLE_" + role.getRole().getReference().toUpperCase().replace(' ', '_')));
				}
			}
			if (((Number)hibSession.createQuery(
					"select count(s) from Student s where s.externalUniqueId = :externalUniqueId")
					.setString("externalUniqueId", getExternalUniqueId())
					.uniqueResult()).intValue() > 0) {
				authorities.add(new SimpleAuthority("ROLE_STUDENT"));
			}
			if (((Number)hibSession.createQuery(
					"select count(i) from DepartmentalInstructor i where i.externalUniqueId = :externalUniqueId")
					.setString("externalUniqueId", getExternalUniqueId())
					.uniqueResult()).intValue() > 0) {
				authorities.add(new SimpleAuthority("ROLE_INSTRUCTOR"));
			}
		} finally {
			hibSession.close();
		}
		return authorities;
	}

	@Override
	public String getPassword() { return iPassword;}

	@Override
	public String getUsername() { return iUserName; }
	
	public String getExternalUniqueId() { return iUserId; }
	
	@Override
	public boolean isAccountNonExpired() { return true; }

	@Override
	public boolean isAccountNonLocked() { return true; }

	@Override
	public boolean isCredentialsNonExpired() { return true; }

	@Override
	public boolean isEnabled() { return true; }
	
	public List<Role> getRoles(Long sessionId) {
		List<Role> roles = new ArrayList<Role>();
		org.hibernate.Session hibSession = null;
		try {
			hibSession = TimetableManagerDAO.getInstance().createNewSession();
			
			// Check for TimetableManager
			TimetableManager mgr = (TimetableManager)hibSession.createQuery(
					"from TimetableManager where externalUniqueId = :externalUniqueId")
					.setMaxResults(1)
					.setString("externalUniqueId", getExternalUniqueId())
					.uniqueResult();
			if (mgr != null) {
				iName = mgr.getName();
				iEmail = mgr.getEmailAddress();
				List<Long> departmentIds = new ArrayList<Long>();
				for (Department dept: mgr.getDepartments()) {
					if (sessionId.equals(dept.getSessionId())) {
						departmentIds.add(dept.getUniqueId());
					}
				}
				for (ManagerRole mr: mgr.getManagerRoles()) {
					Class<? extends Role> roleClass = UniTimeRoles.fromReference(mr.getRole().getReference());
					if (roleClass != null) {
						try {
							Role role = roleClass.newInstance();
							if (role instanceof HasSessionId) {
								if (departmentIds.isEmpty()) continue;
								((HasSessionId)role).setSessionId(sessionId);
							}
							if (role instanceof HasDepartmentIds) {
								if (departmentIds.isEmpty()) continue;
								((HasDepartmentIds)role).setDepartmentIds(departmentIds);
							}
							if (role instanceof HasManagerId) {
								((HasManagerId)role).setManagerId(mgr.getUniqueId());
							}
							roles.add(role);
						} catch (InstantiationException e) {
						} catch (IllegalAccessException e) {
						}
					}
				}
			}
			
			// Check for Student
			Student student = (Student)hibSession.createQuery(
					"from Student where session.uniqueId = :sessionId and externalUniqueId = :externalUniqueId")
					.setMaxResults(1)
					.setLong("sessionId", sessionId)
					.setString("externalUniqueId", getExternalUniqueId())
					.uniqueResult();
			if (student != null) {
				try {
					Role role = UniTimeRoles.ROLE_STUDENT.toRole().newInstance();
					if (role instanceof HasStudentId)
						((HasStudentId)role).setStudentId(student.getUniqueId());
					if (role instanceof HasSessionId)
						((HasSessionId)role).setSessionId(sessionId);
					roles.add(role);
				} catch (InstantiationException e) {
				} catch (IllegalAccessException e) {
				}
				if (mgr == null) {
					iName = student.getName(DepartmentalInstructor.sNameFormatLastFirstMiddle);
					iEmail = student.getEmail();
				}
			}
			
			// Check for DepartmentalInstructor(s)
			List<DepartmentalInstructor> instructors = (List<DepartmentalInstructor>)hibSession.createQuery(
				"from DepartmentalInstructor where department.session.uniqueId = :sessionId and externalUniqueId = :externalUniqueId")
				.setMaxResults(1)
				.setLong("sessionId", sessionId)
				.setString("externalUniqueId", getExternalUniqueId())
				.list();
			if (!instructors.isEmpty()) {
				try {
					Role role = UniTimeRoles.ROLE_INSTRUCTOR.toRole().newInstance();
					List<Long> instructorIds = new ArrayList<Long>();
					List<Long> departmentIds = new ArrayList<Long>();
					for (DepartmentalInstructor instructor: instructors) {
						instructorIds.add(instructor.getUniqueId());
						departmentIds.add(instructor.getDepartment().getUniqueId());
						if (iName == null) iName = instructor.getName(DepartmentalInstructor.sNameFormatLastFirstMiddle);
						if (iEmail == null) iEmail = instructor.getEmail();
					}
					if (role instanceof HasInstructorIds)
						((HasInstructorIds)role).setInstructorIds(instructorIds);
					if (role instanceof HasSessionId)
						((HasSessionId)role).setSessionId(sessionId);
					if (role instanceof HasDepartmentIds)
						((HasDepartmentIds)role).setDepartmentIds(departmentIds);
					roles.add(role);
				} catch (InstantiationException e) {
				} catch (IllegalAccessException e) {
				}
			}
		} finally {
			hibSession.close();
		}
		return roles;
	}
	
	private static class SimpleAuthority implements GrantedAuthority, Comparable<SimpleAuthority> {
		private static final long serialVersionUID = 1L;
		private String iAuthority;
		
		private SimpleAuthority(String authority) {
			iAuthority = authority;
		}

		@Override
		public String getAuthority() {
			return iAuthority;
		}
		
		public String toString() {
			return iAuthority;
		}

		@Override
		public int compareTo(SimpleAuthority other) {
			return getAuthority().compareTo(other.getAuthority());
		}
	}

}