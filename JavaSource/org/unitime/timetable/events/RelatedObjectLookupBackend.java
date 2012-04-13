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
package org.unitime.timetable.events;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.unitime.localization.impl.Localization;
import org.unitime.timetable.gwt.command.client.GwtRpcResponseList;
import org.unitime.timetable.gwt.command.server.GwtRpcHelper;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.resources.GwtMessages;
import org.unitime.timetable.gwt.shared.PageAccessException;
import org.unitime.timetable.gwt.shared.EventInterface.RelatedObjectInterface;
import org.unitime.timetable.gwt.shared.EventInterface.RelatedObjectLookupRpcRequest;
import org.unitime.timetable.gwt.shared.EventInterface.RelatedObjectLookupRpcResponse;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.comparators.ClassComparator;
import org.unitime.timetable.model.comparators.InstrOfferingConfigComparator;
import org.unitime.timetable.model.comparators.SchedulingSubpartComparator;
import org.unitime.timetable.model.dao.CourseOfferingDAO;
import org.unitime.timetable.model.dao.SchedulingSubpartDAO;
import org.unitime.timetable.model.dao.SessionDAO;

public class RelatedObjectLookupBackend implements GwtRpcImplementation<RelatedObjectLookupRpcRequest, GwtRpcResponseList<RelatedObjectLookupRpcResponse>> {
	protected static GwtMessages MESSAGES = Localization.create(GwtMessages.class);

	@Override
	public GwtRpcResponseList<RelatedObjectLookupRpcResponse> execute(RelatedObjectLookupRpcRequest request, GwtRpcHelper helper) {
		checkAccess(helper);

		GwtRpcResponseList<RelatedObjectLookupRpcResponse> response = new GwtRpcResponseList<RelatedObjectLookupRpcResponse>();

		org.hibernate.Session hibSession = SessionDAO.getInstance().getSession();
		
		switch (request.getLevel()) {
		case SESSION:
			for (Object[] object: (List<Object[]>)hibSession.createQuery(
					"select s.uniqueId, s.subjectAreaAbbreviation from SubjectArea s " +
					"where s.session.uniqueId = :sessionId " +
					"order by s.subjectAreaAbbreviation"
					).setLong("sessionId", request.getUniqueId()).setCacheable(true).list()) {
				response.add(new RelatedObjectLookupRpcResponse(
						RelatedObjectLookupRpcRequest.Level.SUBJECT,
						(Long)object[0],
						(String)object[1]));
			}
			break;
		case SUBJECT:
			for (Object[] object: (List<Object[]>)hibSession.createQuery(
					"select co.uniqueId, co.courseNbr, co.title from CourseOffering co "+
                    "where co.subjectArea.uniqueId = :subjectAreaId "+
                    "and co.instructionalOffering.notOffered = false " +
                    "order by co.courseNbr"
                    ).setLong("subjectAreaId", request.getUniqueId()).setCacheable(true).list()) {
				response.add(new RelatedObjectLookupRpcResponse(
						RelatedObjectLookupRpcRequest.Level.COURSE,
						(Long)object[0],
						(String)object[1],
						(String)object[2]));
			}
			break;
		case COURSE:
			CourseOffering course = CourseOfferingDAO.getInstance().get(request.getUniqueId());
			if (course == null) break;
			if (course.isIsControl()) {
				RelatedObjectInterface relatedOffering = new RelatedObjectInterface();
				relatedOffering.setType(RelatedObjectInterface.RelatedObjectType.Offering);
				relatedOffering.setUniqueId(course.getInstructionalOffering().getUniqueId());
				relatedOffering.setName(course.getCourseName());
				relatedOffering.addCourseName(course.getCourseName());
				response.add(new RelatedObjectLookupRpcResponse(
						RelatedObjectLookupRpcRequest.Level.OFFERING,
						course.getInstructionalOffering().getUniqueId(),
						"Offering",
						relatedOffering));
			}
			
			RelatedObjectInterface relatedCourse = new RelatedObjectInterface();
			relatedCourse.setType(RelatedObjectInterface.RelatedObjectType.Course);
			relatedCourse.setUniqueId(course.getUniqueId());
			relatedCourse.setName(course.getCourseName());
			relatedCourse.addCourseName(course.getCourseName());
			response.add(new RelatedObjectLookupRpcResponse(
					RelatedObjectLookupRpcRequest.Level.COURSE,
					course.getInstructionalOffering().getUniqueId(),
					"Course",
					relatedCourse));
			
			Set<InstrOfferingConfig> configs = new TreeSet<InstrOfferingConfig>(new InstrOfferingConfigComparator(null));
			configs.addAll(course.getInstructionalOffering().getInstrOfferingConfigs());
			
			Set<SchedulingSubpart> subparts = new TreeSet<SchedulingSubpart>(new SchedulingSubpartComparator(null));
			
			if (!configs.isEmpty()) {
				response.add(new RelatedObjectLookupRpcResponse(
						RelatedObjectLookupRpcRequest.Level.NONE,
						null,
						"-- Configurations --"));
				for (InstrOfferingConfig config: configs) {
					RelatedObjectInterface relatedConfig = new RelatedObjectInterface();
					relatedConfig.setType(RelatedObjectInterface.RelatedObjectType.Config);
					relatedConfig.setUniqueId(config.getUniqueId());
					relatedConfig.setName(config.getName());
					relatedConfig.addCourseName(course.getCourseName());
					response.add(new RelatedObjectLookupRpcResponse(
							RelatedObjectLookupRpcRequest.Level.CONFIG,
							config.getUniqueId(),
							config.getName(),
							relatedConfig));
					subparts.addAll(config.getSchedulingSubparts());
				}
			}
			
			if (!subparts.isEmpty()) {
				response.add(new RelatedObjectLookupRpcResponse(
						RelatedObjectLookupRpcRequest.Level.NONE,
						null,
						"-- Subparts --"));
				for (SchedulingSubpart subpart: subparts) {
		            String name = subpart.getItype().getAbbv();
		            String sufix = subpart.getSchedulingSubpartSuffix();
		            while (subpart.getParentSubpart() != null) {
		                name =  "\u00A0\u00A0\u00A0\u00A0" + name;
		                subpart = subpart.getParentSubpart();
		            }
		            if (subpart.getInstrOfferingConfig().getInstructionalOffering().getInstrOfferingConfigs().size() > 1)
		                name += " [" + subpart.getInstrOfferingConfig().getName() + "]";
					response.add(new RelatedObjectLookupRpcResponse(
							RelatedObjectLookupRpcRequest.Level.SUBPART,
							subpart.getUniqueId(),
							name + (sufix == null || sufix.isEmpty() ? "" : " ("+sufix+")")));
				}
			}
			break;
		case SUBPART:
			course = CourseOfferingDAO.getInstance().get(request.getCourseId());
			SchedulingSubpart subpart = SchedulingSubpartDAO.getInstance().get(request.getUniqueId());
			if (subpart == null) break;
			
			Set<Class_> classes = new TreeSet<Class_>(new ClassComparator(ClassComparator.COMPARE_BY_HIERARCHY));
			classes.addAll(subpart.getClasses());
			
			for (Class_ clazz: classes) {
				String extId = clazz.getClassSuffix(course);
				response.add(new RelatedObjectLookupRpcResponse(
						RelatedObjectLookupRpcRequest.Level.CLASS,
						clazz.getUniqueId(),
						clazz.getSectionNumberString(hibSession),
						(extId == null || extId.isEmpty() || extId.equalsIgnoreCase(clazz.getSectionNumberString(hibSession)) ? null : extId)
						));
			}
			
			break;
		default:
			response.add(new RelatedObjectLookupRpcResponse(
					RelatedObjectLookupRpcRequest.Level.NONE,
					null,
					"N/A"));
			break;
		}

		
		return response;
	}

	public void checkAccess(GwtRpcHelper helper) throws PageAccessException {
		if (helper.getUser() == null) {
			throw new PageAccessException(helper.isHttpSessionNew() ? MESSAGES.authenticationExpired() : MESSAGES.authenticationRequired());
		}
	}

}
