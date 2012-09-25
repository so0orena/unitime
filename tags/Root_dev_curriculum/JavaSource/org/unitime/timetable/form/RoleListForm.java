/*
 * UniTime 3.1 (University Timetabling Application)
 * Copyright (C) 2008, UniTime LLC, and individual contributors
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
package org.unitime.timetable.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * MyEclipse Struts
 * Creation date: 03-17-2005
 *
 * XDoclet definition:
 * @struts:form name="roleListForm"
 */
public class RoleListForm extends ActionForm {
	private static final long serialVersionUID = 3546920294733526840L;

    private Long iSessionId;
    private Long iRoleId;

    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		return errors;
    }

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        iSessionId = null; iRoleId = null;
    }

    public Long getSessionId() {
        return iSessionId;
    }
    
    public void setSessionId(Long sessionId) {
        iSessionId = sessionId;
    }
    
    public Long getRoleId() {
        return iRoleId;
    }
    
    public void setRoleId(Long roleId) {
        iRoleId = roleId;
    }
}