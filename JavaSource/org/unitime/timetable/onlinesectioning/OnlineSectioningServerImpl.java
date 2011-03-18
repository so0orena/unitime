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
package org.unitime.timetable.onlinesectioning;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.sf.cpsolver.coursett.model.Placement;
import net.sf.cpsolver.coursett.model.RoomLocation;
import net.sf.cpsolver.coursett.model.TimeLocation;
import net.sf.cpsolver.ifs.util.DistanceMetric;
import net.sf.cpsolver.studentsct.model.Assignment;
import net.sf.cpsolver.studentsct.model.Config;
import net.sf.cpsolver.studentsct.model.Course;
import net.sf.cpsolver.studentsct.model.CourseRequest;
import net.sf.cpsolver.studentsct.model.Enrollment;
import net.sf.cpsolver.studentsct.model.FreeTimeRequest;
import net.sf.cpsolver.studentsct.model.Offering;
import net.sf.cpsolver.studentsct.model.Request;
import net.sf.cpsolver.studentsct.model.Section;
import net.sf.cpsolver.studentsct.model.Student;
import net.sf.cpsolver.studentsct.model.Subpart;
import net.sf.cpsolver.studentsct.reservation.Reservation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.unitime.timetable.ApplicationProperties;
import org.unitime.timetable.gwt.server.DayCode;
import org.unitime.timetable.gwt.shared.ClassAssignmentInterface;
import org.unitime.timetable.gwt.shared.CourseRequestInterface;
import org.unitime.timetable.gwt.shared.SectioningException;
import org.unitime.timetable.gwt.shared.SectioningExceptionType;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.onlinesectioning.updates.CheckAllOfferingsAction;
import org.unitime.timetable.onlinesectioning.updates.ReloadAllData;

/**
 * @author Tomas Muller
 */
public class OnlineSectioningServerImpl implements OnlineSectioningServer {
    private Log iLog = LogFactory.getLog(OnlineSectioningServerImpl.class);
	private AcademicSessionInfo iAcademicSession = null;
	private Hashtable<Long, CourseInfo> iCourseForId = new Hashtable<Long, CourseInfo>();
	private Hashtable<String, TreeSet<CourseInfo>> iCourseForName = new Hashtable<String, TreeSet<CourseInfo>>();
	private TreeSet<CourseInfo> iCourses = new TreeSet<CourseInfo>();
	private DistanceMetric iDistanceMetric = null;
	
	private Hashtable<Long, Course> iCourseTable = new Hashtable<Long, Course>();
	private Hashtable<Long, Section> iClassTable = new Hashtable<Long, Section>();
	private Hashtable<Long, Student> iStudentTable = new Hashtable<Long, Student>();
	private Hashtable<Long, Offering> iOfferingTable = new Hashtable<Long, Offering>();
	
	private ReentrantReadWriteLock iLock = new ReentrantReadWriteLock();
	private MultiLock iMultiLock;
	private Map<Long, Lock> iOfferingLocks = new Hashtable<Long, Lock>();
	private AsyncExecutor iExecutor;
	private Queue<Runnable> iExecutorQueue = new LinkedList<Runnable>();
	
	OnlineSectioningServerImpl(Long sessionId) throws SectioningException {
		org.hibernate.Session hibSession = SessionDAO.getInstance().createNewSession();
		try {
			Session session = SessionDAO.getInstance().get(sessionId, hibSession);
			if (session == null)
				throw new SectioningException(SectioningExceptionType.SESSION_NOT_EXIST, (sessionId == null ? "null" : sessionId.toString()));
			iAcademicSession = new AcademicSessionInfo(session);
			iLog = LogFactory.getLog(OnlineSectioningServerImpl.class.getName() + ".server[" + iAcademicSession.toCompactString() + "]");
			iMultiLock = new MultiLock(iAcademicSession);
			execute(new ReloadAllData());
			if (iAcademicSession.isSectioningEnabled())
				execute(new CheckAllOfferingsAction());
			iExecutor = new AsyncExecutor();
			iExecutor.start();
		} catch (Throwable t) {
			if (t instanceof SectioningException) throw (SectioningException)t;
			throw new SectioningException(SectioningExceptionType.UNKNOWN, t);
		} finally {
			hibSession.close();
		}
		iDistanceMetric = new DistanceMetric(
				DistanceMetric.Ellipsoid.valueOf(ApplicationProperties.getProperty("unitime.distance.ellipsoid", DistanceMetric.Ellipsoid.LEGACY.name())));
	}
	
	@Override
	public DistanceMetric getDistanceMetric() {
		return iDistanceMetric;
	}
	
	@Override
	public AcademicSessionInfo getAcademicSession() { return iAcademicSession; }

	@Override
	public CourseInfo getCourseInfo(String course) {
		iLock.readLock().lock();
		try {
			if (course.indexOf('-') >= 0) {
				String courseName = course.substring(0, course.indexOf('-')).trim();
				String title = course.substring(course.indexOf('-') + 1).trim();
				TreeSet<CourseInfo> infos = iCourseForName.get(courseName.toLowerCase());
				if (infos!= null && !infos.isEmpty())
					for (CourseInfo info: infos)
						if (title.equalsIgnoreCase(info.getTitle())) return info;
				return null;
			} else {
				TreeSet<CourseInfo> infos = iCourseForName.get(course.toLowerCase());
				if (infos!= null && !infos.isEmpty()) return infos.first();
				return null;
			}
		} finally {
			iLock.readLock().unlock();
		}
	}

	@Override
	public CourseInfo getCourseInfo(Long courseId) {
		iLock.readLock().lock();
		try {
			return iCourseForId.get(courseId);
		} finally {
			iLock.readLock().unlock();
		}
	}
	
	@Override
	public Student getStudent(Long studentId) {
		iLock.readLock().lock();
		try {
			return iStudentTable.get(studentId);
		} finally {
			iLock.readLock().unlock();
		}
	}
	
	@Override
	public Course getCourse(Long courseId) {
		iLock.readLock().lock();
		try {
			return iCourseTable.get(courseId);
		} finally {
			iLock.readLock().unlock();
		}
	}
	
	@Override
	public ClassAssignmentInterface getAssignment(Long studentId) {
		iLock.readLock().lock();
		try {
			Student student = iStudentTable.get(studentId);
			if (student == null) return null;
	        ClassAssignmentInterface ret = new ClassAssignmentInterface();
			int nrUnassignedCourses = 0;
			int nrAssignedAlt = 0;
			boolean assigned = false;
			for (Request request: student.getRequests()) {
				if (request.getAssignment() != null) assigned = true;
				ClassAssignmentInterface.CourseAssignment ca = new ClassAssignmentInterface.CourseAssignment();
				if (request instanceof CourseRequest) {
					CourseRequest r = (CourseRequest)request;
					Course course = (request.getAssignment() == null ? r.getCourses().get(0) : r.getAssignment().getCourse());
					if (isOfferingLocked(course.getOffering().getId()))
						ca.setLocked(true);
					ca.setAssigned(r.getAssignment() != null);
					ca.setCourseId(course.getId());
					ca.setSubject(course.getSubjectArea());
					ca.setCourseNbr(course.getCourseNumber());
					if (r.getAssignment() == null) {
						TreeSet<Enrollment> overlap = new TreeSet<Enrollment>(new Comparator<Enrollment>() {
							public int compare(Enrollment e1, Enrollment e2) {
								return e1.getRequest().compareTo(e2.getRequest());
							}
						});
						Hashtable<CourseRequest, TreeSet<Section>> overlapingSections = new Hashtable<CourseRequest, TreeSet<Section>>();
						Collection<Enrollment> avEnrls = r.getAvaiableEnrollmentsSkipSameTime();
						for (Iterator<Enrollment> e = avEnrls.iterator(); e.hasNext();) {
							Enrollment enrl = e.next();
							overlaps: for (Request q: student.getRequests()) {
								if (q.equals(request)) continue;
								Enrollment x = q.getAssignment();
								if (x == null || x.getAssignments() == null || x.getAssignments().isEmpty()) continue;
						        for (Iterator<Assignment> i = x.getAssignments().iterator(); i.hasNext();) {
						        	Assignment a = i.next();
									if (a.isOverlapping(enrl.getAssignments())) {
										overlap.add(x);
										if (x.getRequest() instanceof CourseRequest) {
											CourseRequest cr = (CourseRequest)x.getRequest();
											TreeSet<Section> ss = overlapingSections.get(cr);
											if (ss == null) { ss = new TreeSet<Section>(); overlapingSections.put(cr, ss); }
											ss.add((Section)a);
										}
										break overlaps;
									}
						        }
							}
							for (Enrollment q: overlap) {
								if (q.getRequest() instanceof FreeTimeRequest) {
									FreeTimeRequest f = (FreeTimeRequest)q.getRequest();
									ca.addOverlap("Free Time " +
										DayCode.toString(f.getTime().getDayCode()) + " " + 
										f.getTime().getStartTimeHeader() + " - " +
										f.getTime().getEndTimeHeader());
								} else {
									CourseRequest cr = (CourseRequest)q.getRequest();
									Course o = q.getCourse();
									String ov = o.getSubjectArea() + " " + o.getCourseNumber();
									if (overlapingSections.get(cr).size() == 1)
										for (Iterator<Section> i = overlapingSections.get(cr).iterator(); i.hasNext();) {
											Section s = i.next();
											ov += " " + s.getSubpart().getName();
											if (i.hasNext()) ov += ",";
										}
									ca.addOverlap(ov);
								}
							}
							nrUnassignedCourses++;
							int alt = nrUnassignedCourses;
							for (Request q: student.getRequests()) {
								if (q.equals(request)) continue;
								Enrollment x = q.getAssignment();
								if (x == null || x.getAssignments() == null || x.getAssignments().isEmpty()) continue;
								if (x.getRequest().isAlternative() && x.getRequest() instanceof CourseRequest) {
									if (--alt == 0) {
										Course o = x.getCourse();
										ca.setInstead(o.getSubjectArea() + " " +o.getCourseNumber());
										break;
									}
								}
							}
							if (avEnrls.isEmpty()) ca.setNotAvailable(true);
						}
					} else {
						if (r.isAlternative() && r.isAssigned()) nrAssignedAlt++;
						TreeSet<Section> sections = new TreeSet<Section>(new EnrollmentSectionComparator());
						sections.addAll(r.getAssignment().getSections());
						boolean hasAlt = false;
						if (r.getCourses().size() > 1) {
							hasAlt = true;
						} else if (course.getOffering().getConfigs().size() > 1) {
							hasAlt = true;
						} else {
							for (Iterator<Subpart> i = ((Config)course.getOffering().getConfigs().get(0)).getSubparts().iterator(); i.hasNext();) {
								Subpart s = i.next();
								if (s.getSections().size() > 1) { hasAlt = true; break; }
							}
						}
						for (Iterator<Section> i = sections.iterator(); i.hasNext();) {
							Section section = (Section)i.next();
							ClassAssignmentInterface.ClassAssignment a = ca.addClassAssignment();
							a.setAlternative(r.isAlternative());
							a.setClassId(section.getId());
							a.setSubpart(section.getSubpart().getName());
							a.setSection(section.getName(course.getId()));
							a.setLimit(new int[] {section.getEnrollments().size(), section.getLimit()});
							if (section.getTime() != null) {
								for (DayCode d : DayCode.toDayCodes(section.getTime().getDayCode()))
									a.addDay(d.getIndex());
								a.setStart(section.getTime().getStartSlot());
								a.setLength(section.getTime().getLength());
								a.setBreakTime(section.getTime().getBreakTime());
								a.setDatePattern(section.getTime().getDatePatternName());
							}
							if (section.getRooms() != null) {
								for (Iterator<RoomLocation> e = section.getRooms().iterator(); e.hasNext(); ) {
									RoomLocation rm = e.next();
									a.addRoom(rm.getName());
								}
							}
							if (section.getChoice().getInstructorNames() != null && !section.getChoice().getInstructorNames().isEmpty()) {
								String[] instructors = section.getChoice().getInstructorNames().split(":");
								for (String instructor: instructors) {
									String[] nameEmail = instructor.split("\\|");
									a.addInstructor(nameEmail[0]);
									a.addInstructoEmailr(nameEmail.length < 2 ? "" : nameEmail[1]);
								}
							}
							if (section.getParent() != null)
								a.setParentSection(section.getParent().getName(course.getId()));
							a.setSubpartId(section.getSubpart().getId());
							a.setHasAlternatives(hasAlt);
							int dist = 0;
							String from = null;
							for (Request q: student.getRequests()) {
								Enrollment x = q.getAssignment();
								if (x == null || !x.isCourseRequest() || x.getAssignments() == null || x.getAssignments().isEmpty()) continue;
								for (Iterator<Section> j=x.getSections().iterator(); j.hasNext();) {
									Section s = j.next();
									if (s == section || s.getTime() == null) continue;
									int d = distance(s, section);
									if (d > dist) {
										dist = d;
										from = "";
										for (Iterator<RoomLocation> k = s.getRooms().iterator(); k.hasNext();)
											from += k.next().getName() + (k.hasNext() ? ", " : "");
									}
									if (d > s.getTime().getBreakTime()) {
										a.setDistanceConflict(true);
									}
								}
							}
							a.setBackToBackDistance(dist);
							a.setBackToBackRooms(from);
							a.setSaved(true);
							if (a.getParentSection() == null)
								a.setParentSection(getCourseInfo(course.getId()).getConsent());
							a.setExpected(section.getSpaceExpected());
						}
					}
				} else if (request instanceof FreeTimeRequest) {
					FreeTimeRequest r = (FreeTimeRequest)request;
					ca.setAssigned(r.getAssignment() != null);
					ca.setCourseId(null);
					if (r.getAssignment() == null) {
						overlaps: for (Request q: student.getRequests()) {
							if (q.equals(request)) continue;
							Enrollment x = q.getAssignment();
							if (x == null || x.getAssignments() == null || x.getAssignments().isEmpty()) continue;
					        for (Iterator<Assignment> i = x.getAssignments().iterator(); i.hasNext();) {
					        	Assignment a = i.next();
								if (r.isOverlapping(a)) {
									if (x.getRequest() instanceof FreeTimeRequest) {
										FreeTimeRequest f = (FreeTimeRequest)x.getRequest();
										ca.addOverlap("Free Time " +
											DayCode.toString(f.getTime().getDayCode()) + " " + 
											f.getTime().getStartTimeHeader() + " - " +
											f.getTime().getEndTimeHeader());
									} else {
										Course o = x.getCourse();
										Section s = (Section)a;
										ca.addOverlap(o.getSubjectArea() + " " + o.getCourseNumber() + " " + s.getSubpart().getName());
									}
									break overlaps;
								}
					        }
						}
						if (ca.getOverlaps() == null)
							ca.setAssigned(true);
					}
					ClassAssignmentInterface.ClassAssignment a = ca.addClassAssignment();
					a.setAlternative(r.isAlternative());
					for (DayCode d : DayCode.toDayCodes(r.getTime().getDayCode()))
						a.addDay(d.getIndex());
					a.setStart(r.getTime().getStartSlot());
					a.setLength(r.getTime().getLength());
				}
				ret.add(ca);
			}
			if (!assigned) return null;
			return ret;
		} finally {
			iLock.readLock().unlock();
		}
	}
		

	@Override
	public int distance(Section s1, Section s2) {
        if (s1.getPlacement()==null || s2.getPlacement()==null) return 0;
        TimeLocation t1 = s1.getTime();
        TimeLocation t2 = s2.getTime();
        if (!t1.shareDays(t2) || !t1.shareWeeks(t2)) return 0;
        int a1 = t1.getStartSlot(), a2 = t2.getStartSlot();
        if (a1+t1.getNrSlotsPerMeeting()==a2) {
            return Placement.getDistanceInMinutes(getDistanceMetric(), s1.getPlacement(), s2.getPlacement());
        }
        /*
        else if (a2+t2.getNrSlotsPerMeeting()==a1) {
        	return Placement.getDistance(s1.getPlacement(), s2.getPlacement());
        }
        */
        return 0;
    }	
	
	@Override
	public CourseRequestInterface getRequest(Long studentId) {
		iLock.readLock().lock();
		try {
			Student student = iStudentTable.get(studentId);
			if (student == null) return null;
			CourseRequestInterface request = new CourseRequestInterface();
			request.setSaved(true);
			request.setAcademicSessionId(getAcademicSession().getUniqueId());
			TreeSet<Request> requests = new TreeSet<Request>(new Comparator<Request>() {
				public int compare(Request d1, Request d2) {
					if (d1.isAlternative() && !d2.isAlternative()) return 1;
					if (!d1.isAlternative() && d2.isAlternative()) return -1;
					int cmp = new Integer(d1.getPriority()).compareTo(d2.getPriority());
					if (cmp != 0) return cmp;
					return new Long(d1.getId()).compareTo(d2.getId());
				}
			});
			requests.addAll(student.getRequests());
			CourseRequestInterface.Request lastRequest = null;
			int lastRequestPriority = -1;
			for (Request cd: requests) {
				CourseRequestInterface.Request r = null;
				if (cd instanceof FreeTimeRequest) {
					FreeTimeRequest ftr = (FreeTimeRequest)cd;
					CourseRequestInterface.FreeTime ft = new CourseRequestInterface.FreeTime();
					ft.setStart(ftr.getTime().getStartSlot());
					ft.setLength(ftr.getTime().getLength());
					for (DayCode day : DayCode.toDayCodes(ftr.getTime().getDayCode()))
						ft.addDay(day.getIndex());
					if (lastRequest != null && lastRequestPriority == cd.getPriority()) {
						r = lastRequest;
						lastRequest.addRequestedFreeTime(ft);
						lastRequest.setRequestedCourse(lastRequest.getRequestedCourse() + ", " + ft.toString());
					} else {
						r = new CourseRequestInterface.Request();
						r.addRequestedFreeTime(ft);
						r.setRequestedCourse(ft.toString());
						if (cd.isAlternative())
							request.getAlternatives().add(r);
						else
							request.getCourses().add(r);
					}
				} else if (cd instanceof CourseRequest) {
					r = new CourseRequestInterface.Request();
					int order = 0;
					for (Course course: ((CourseRequest)cd).getCourses()) {
						CourseInfo c = iCourseForId.get(course.getId());
						if (c == null) continue;
						switch (order) {
							case 0: 
								r.setRequestedCourse(c.getSubjectArea() + " " + c.getCourseNbr() + (c.hasUniqueName() ? "" : " - " + c.getTitle()));
								break;
							case 1:
								r.setFirstAlternative(c.getSubjectArea() + " " + c.getCourseNbr() + (c.hasUniqueName() ? "" : " - " + c.getTitle()));
								break;
							case 2:
								r.setSecondAlternative(c.getSubjectArea() + " " + c.getCourseNbr() + (c.hasUniqueName() ? "" : " - " + c.getTitle()));
							}
						order++;
						}
					if (r.hasRequestedCourse()) {
						if (cd.isAlternative())
							request.getAlternatives().add(r);
						else
							request.getCourses().add(r);
					}
					lastRequest = r;
					lastRequestPriority = cd.getPriority();
				}
			}
			return request;
		} finally {
			iLock.readLock().unlock();
		}
	}


	@Override
	public Collection<CourseInfo> findCourses(String query, Integer limit) {
		iLock.readLock().lock();
		try {
			List<CourseInfo> ret = new ArrayList<CourseInfo>(limit == null ? 100 : limit);
			String queryInLowerCase = query.toLowerCase();
			for (CourseInfo c : iCourses) {
				if (c.matchCourseName(queryInLowerCase)) ret.add(c);
				if (limit != null && ret.size() == limit) return ret;
			}
			if (queryInLowerCase.length() > 2) {
				for (CourseInfo c : iCourses) {
					if (c.matchTitle(queryInLowerCase)) ret.add(c);
					if (limit != null && ret.size() == limit) return ret;
				}
			}
			return ret;
		} finally {
			iLock.readLock().unlock();
		}
	}
	
	@Override
	public URL getSectionUrl(Long courseId, Section section) {
		if (OnlineSectioningService.sSectionUrlProvider == null) return null;
		return OnlineSectioningService.sSectionUrlProvider.getSectionUrl(getAcademicSession(), courseId, section);
	}
		
	@Override
	public Collection<String> checkCourses(CourseRequestInterface req) {
		ArrayList<String> notFound = new ArrayList<String>();
		for (CourseRequestInterface.Request cr: req.getCourses()) {
			if (!cr.hasRequestedFreeTime() && cr.hasRequestedCourse() && getCourseInfo(cr.getRequestedCourse()) == null)
				notFound.add(cr.getRequestedCourse());
			if (cr.hasFirstAlternative() && getCourseInfo(cr.getFirstAlternative()) == null)
				notFound.add(cr.getFirstAlternative());
			if (cr.hasSecondAlternative() && getCourseInfo(cr.getSecondAlternative()) == null)
				notFound.add(cr.getSecondAlternative());
		}
		for (CourseRequestInterface.Request cr: req.getAlternatives()) {
			if (cr.hasRequestedCourse() && getCourseInfo(cr.getRequestedCourse()) == null)
				notFound.add(cr.getRequestedCourse());
			if (cr.hasFirstAlternative() && getCourseInfo(cr.getFirstAlternative()) == null)
				notFound.add(cr.getFirstAlternative());
			if (cr.hasSecondAlternative() && getCourseInfo(cr.getSecondAlternative()) == null)
				notFound.add(cr.getSecondAlternative());
		}
		return notFound;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public List<Section> getSections(CourseInfo courseInfo) {
		iLock.readLock().lock();
		try {
			ArrayList<Section> sections = new ArrayList<Section>();
			Course course = iCourseTable.get(courseInfo.getUniqueId());
			if (course == null) return sections;
			for (Iterator<Config> e=course.getOffering().getConfigs().iterator(); e.hasNext();) {
				Config cfg = e.next();
				for (Iterator<Subpart> f=cfg.getSubparts().iterator(); f.hasNext();) {
					Subpart subpart = f.next();
					for (Iterator<Section> g=subpart.getSections().iterator(); g.hasNext();) {
						Section section = g.next();
						sections.add(section);
					}
				}
			}
			return sections;
		} finally {
			iLock.readLock().unlock();
		}
	}
	
	public static class EnrollmentSectionComparator implements Comparator<Section> {
	    public boolean isParent(Section s1, Section s2) {
			Section p1 = s1.getParent();
			if (p1==null) return false;
			if (p1.equals(s2)) return true;
			return isParent(p1, s2);
		}

		public int compare(Section a, Section b) {
			if (isParent(a, b)) return 1;
	        if (isParent(b, a)) return -1;

	        int cmp = a.getSubpart().getInstructionalType().compareToIgnoreCase(b.getSubpart().getInstructionalType());
			if (cmp != 0) return cmp;
			
			return Double.compare(a.getId(), b.getId());
		}
	}
	
	@Override
	public Section getSection(Long classId) {
		iLock.readLock().lock();
		try {
			return iClassTable.get(classId);
		} finally {
			iLock.readLock().unlock();
		}
	}
	
	public static class DummyReservation extends Reservation {
		private int iPriority;
		private boolean iOver;
		private int iLimit;
		private boolean iApply;
		
		public DummyReservation(long id, Offering offering, int priority, boolean over, int limit, boolean apply) {
			super(id, offering);
			iPriority = priority;
			iOver = over;
			iLimit = limit;
			iApply = apply;
		}
		
		@Override
		public boolean canAssignOverLimit() {
			return iOver;
		}

		@Override
		public double getLimit() {
			return iLimit;
		}

		@Override
		public int getPriority() {
			return iPriority;
		}

		@Override
		public boolean isApplicable(Student student) {
			return iApply;
		}
		
	}

	@Override
	public void remove(Student student) {
		iLock.writeLock().lock();
		try {
			Student s = iStudentTable.get(student.getId());
			if (s != null) {
				for (Request r: s.getRequests()) {
			        for (Request request : student.getRequests()) {
			            if (request instanceof CourseRequest) {
			                for (Course course: ((CourseRequest) request).getCourses())
			                    course.getRequests().remove(request);
			            }
					if (r.getAssignment() != null)
						r.unassign(0);
			        }
				}
				iStudentTable.remove(student.getId());
			}
		} finally {
			iLock.writeLock().unlock();
		}
	}

	@Override
	public void update(Student student) {
		iLock.writeLock().lock();
		try {
			iStudentTable.put(student.getId(), student);
			for (Request r: student.getRequests()) {
				if (r.getInitialAssignment() == null) {
					if (r.getAssignment() != null)
						r.unassign(0);
				} else {
					if (r.getAssignment() == null || !r.getAssignment().equals(r.getInitialAssignment()))
						r.assign(0, r.getInitialAssignment());
				}
			}

		} finally {
			iLock.writeLock().unlock();
		}
	}

	@Override
	public void remove(Offering offering) {
		iLock.writeLock().lock();
		try {
			for (Course course: offering.getCourses()) {
				CourseInfo ci = iCourseForId.get(course.getId());
				if (ci != null) {
					TreeSet<CourseInfo> courses = iCourseForName.get(ci.toString());
					if (courses != null) {
						courses.remove(ci);
						if (courses.isEmpty()) {
							iCourseForName.remove(ci.toString());
						} else if (courses.size() == 1) {
							for (CourseInfo x: courses)
								x.setHasUniqueName(true);
						}
					}
					iCourseForId.remove(ci.getUniqueId());
					iCourses.remove(ci);
				}
				iCourseTable.remove(course.getId());
			}
			iOfferingTable.remove(offering.getId());
			for (Config config: offering.getConfigs()) {
				for (Subpart subpart: config.getSubparts())
					for (Section section: subpart.getSections())
						iClassTable.remove(section.getId());
				for (Enrollment enrollment: new ArrayList<Enrollment>(config.getEnrollments()))
					enrollment.variable().unassign(0);
			}
		} finally {
			iLock.writeLock().unlock();
		}
	}

	@Override
	public void update(CourseInfo info) {
		iLock.writeLock().lock();
		try {
			CourseInfo old = iCourseForId.get(info.getUniqueId());
			iCourseForId.put(info.getUniqueId(), info);
			TreeSet<CourseInfo> courses = iCourseForName.get(info.toString());
			if (courses == null) {
				courses = new TreeSet<CourseInfo>();
				iCourseForName.put(info.toString(), courses);
			}
			if (old != null) {
				courses.remove(old);
				iCourses.remove(old);
			}
			courses.add(info);
			iCourses.add(info);
			if (courses.size() == 1) 
				for (CourseInfo x: courses) x.setHasUniqueName(true);
			else if (courses.size() > 1)
				for (CourseInfo x: courses) x.setHasUniqueName(false);
		} finally {
			iLock.writeLock().unlock();
		}
	}

	@Override
	public void update(Offering offering) {
		iLock.writeLock().lock();
		try {
			Offering old = iOfferingTable.get(offering.getId());
			if (old != null) remove(old);
			for (Course course: offering.getCourses())
				iCourseTable.put(course.getId(), course);
			iOfferingTable.put(offering.getId(), offering);
			for (Config config: offering.getConfigs())
				for (Subpart subpart: config.getSubparts())
					for (Section section: subpart.getSections())
						iClassTable.put(section.getId(), section);
		} finally {
			iLock.writeLock().unlock();
		}
	}

	@Override
	public Offering getOffering(Long offeringId) {
		iLock.readLock().lock();
		try {
			return iOfferingTable.get(offeringId);
		} finally {
			iLock.readLock().unlock();
		}
	}

	@Override
	public void clearAll() {
		iLock.writeLock().lock();
		try {
			iClassTable.clear();
			iStudentTable.clear();
			iOfferingTable.clear();
			iCourseTable.clear();
			iCourseForId.clear();
			iCourseForName.clear();
			iCourses.clear();	
		} finally {
			iLock.writeLock().unlock();
		}
	}
	
	@Override
    public void clearAllStudents() {
		iLock.writeLock().lock();
		try {
			for (Student student: iStudentTable.values()) {
				for (Iterator<Request> e = student.getRequests().iterator(); e.hasNext();) {
					Request r = (Request)e.next();
					if (r.getAssignment() != null) r.unassign(0);
				}
			}
			iStudentTable.clear();
		} finally {
			iLock.writeLock().unlock();
		}
    }

	@Override
	public <E> E execute(OnlineSectioningAction<E> action) throws SectioningException {
		try {
			OnlineSectioningHelper h = new OnlineSectioningHelper();
			h.addMessageHandler(new OnlineSectioningHelper.DefaultMessageLogger(LogFactory.getLog(OnlineSectioningServer.class.getName() + "." + action.name() + "[" + getAcademicSession().toCompactString() + "]")));
			return action.execute(this, h);
		} catch (Exception e) {
			iLog.error("Execution of " + action.name() + " failed: " + e.getMessage(), e);
			if (e instanceof SectioningException)
				throw (SectioningException)e;
			throw new SectioningException(SectioningExceptionType.UNKNOWN, e);
		}
	}
	
	public Lock readLock() {
		iLock.readLock().lock();
		return new Lock() {
			public void release() {
				iLock.readLock().unlock();
			}
		};
	}

	public Lock lockAll() {
		iLock.writeLock().lock();
		return new Lock() {
			public void release() {
				iLock.writeLock().unlock();
			}
		};
	}
	
	public Lock lockStudent(Long studentId, Collection<Long> offeringIds) {
		Set<Long> ids = new HashSet<Long>();
		iLock.readLock().lock();
		try {
			ids.add(-studentId);
			if (offeringIds != null)
				ids.addAll(offeringIds);
			
			Student student = iStudentTable.get(studentId);
			
			if (student != null)
				for (Request r: student.getRequests()) {
					Offering o = (r.getAssignment() == null ? null : r.getAssignment().getOffering());
					if (o != null) ids.add(o.getId());
				}
		} finally {
			iLock.readLock().unlock();
		}
		return iMultiLock.lock(ids);
	}
	
	public Lock lockOffering(Long offeringId, Collection<Long> studentIds) {
		Set<Long> ids = new HashSet<Long>();
		iLock.readLock().lock();
		try {
			ids.add(offeringId);
			if (studentIds != null)
				for (Long studentId: studentIds)
				ids.add(-studentId);
			
			Offering offering = iOfferingTable.get(offeringId);
			
			if (offering != null)
				for (Config config: offering.getConfigs())
					for (Enrollment enrollment: config.getEnrollments())
						ids.add(-enrollment.getStudent().getId());
		} finally {
			iLock.readLock().unlock();
		}
		return iMultiLock.lock(ids);
	}
	
	public Lock lockClass(Long classId, Collection<Long> studentIds) {
		Set<Long> ids = new HashSet<Long>();
		iLock.readLock().lock();
		try {
			if (studentIds != null)
				for (Long studentId: studentIds)
				ids.add(-studentId);
			
			Section section = iClassTable.get(classId);
			if (section != null) {
				for (Enrollment enrollment: section.getEnrollments())
					ids.add(-enrollment.getStudent().getId());
				ids.add(section.getSubpart().getConfig().getOffering().getId());
			}
		} finally {
			iLock.readLock().unlock();
		}
		return iMultiLock.lock(ids);
	}
	
	private Long getOfferingIdFromCourseName(String courseName) {
		if (courseName == null) return null;
		CourseInfo c = getCourseInfo(courseName);
		if (c == null) return null;
		Course course = iCourseTable.get(c.getUniqueId());
		return (course == null ? null : course.getOffering().getId());
	}
	
	public Lock lockRequest(CourseRequestInterface request) {
		Set<Long> ids = new HashSet<Long>();
		iLock.readLock().lock();
		try {
			if (request.getStudentId() != null)
				ids.add(-request.getStudentId());
			for (CourseRequestInterface.Request r: request.getCourses()) {
				if (r.hasRequestedCourse()) {
					Long id = getOfferingIdFromCourseName(r.getRequestedCourse());
					if (id != null) ids.add(id);
				}
				if (r.hasFirstAlternative()) {
					Long id = getOfferingIdFromCourseName(r.getFirstAlternative());
					if (id != null) ids.add(id);
				}
				if (r.hasSecondAlternative()) {
					Long id = getOfferingIdFromCourseName(r.getSecondAlternative());
					if (id != null) ids.add(id);
				}
			}
			for (CourseRequestInterface.Request r: request.getAlternatives()) {
				if (r.hasRequestedCourse()) {
					Long id = getOfferingIdFromCourseName(r.getRequestedCourse());
					if (id != null) ids.add(id);
				}
				if (r.hasFirstAlternative()) {
					Long id = getOfferingIdFromCourseName(r.getFirstAlternative());
					if (id != null) ids.add(id);
				}
				if (r.hasSecondAlternative()) {
					Long id = getOfferingIdFromCourseName(r.getSecondAlternative());
					if (id != null) ids.add(id);
				}
			}
		} finally {
			iLock.readLock().unlock();
		}
		return iMultiLock.lock(ids);
	}
	
	public void notifyStudentChanged(Long studentId, List<Request> oldRequests, List<Request> newRequests) {
		Student student = getStudent(studentId);
		if (student != null) {
			String message = "Student " + student.getId() + " changed.";
			if (oldRequests != null) {
				message += "\n  Previous schedule:";
				for (Request r: oldRequests) {
					message += "\n    " + r.getName() + (r instanceof FreeTimeRequest || r.getInitialAssignment() != null ? "" : " NOT ASSIGNED");
					if (r instanceof CourseRequest && r.getInitialAssignment() != null) {
						for (Section s: r.getInitialAssignment().getSections()) {
							message += "\n      " + s.getSubpart().getName() + " " + s.getName(r.getInitialAssignment().getCourse().getId())
								+ (s.getTime() == null ? "" : " " + s.getTime().getLongName())
								+ (s.getNrRooms() == 0 ? "" : " " + s.getPlacement().getRoomName(", "));
						}
					}
				}
			}
			if (newRequests != null) {
				message += "\n  New schedule:";
				for (Request r: newRequests) {
					message += "\n    " + r.getName() + (r instanceof FreeTimeRequest || r.getInitialAssignment() != null ? "" : " NOT ASSIGNED");
					if (r instanceof CourseRequest && r.getInitialAssignment() != null) {
						for (Section s: r.getInitialAssignment().getSections()) {
							message += "\n      " + s.getSubpart().getName() + " " + s.getName(r.getInitialAssignment().getCourse().getId())
								+ (s.getTime() == null ? "" : " " + s.getTime().getLongName())
								+ (s.getNrRooms() == 0 ? "" : " " + s.getPlacement().getRoomName(", "));
						}
					}
				}
			}
			iLog.info(message);
		}
	}
	
	@Override
	public void notifyStudentChanged(Long studentId, Request request, Enrollment oldEnrollment) {
		Student student = getStudent(studentId);
		if (student != null) {
			String message = "Student " + student.getId() + " changed.";
			if (oldEnrollment != null) {
				message += "\n  Previous assignment:";
				message += "\n    " + request.getName() + (request instanceof FreeTimeRequest || oldEnrollment != null ? "" : " NOT ASSIGNED");
				if (request instanceof CourseRequest && oldEnrollment != null) {
					for (Section s: oldEnrollment.getSections()) {
						message += "\n      " + s.getSubpart().getName() + " " + s.getName(oldEnrollment.getCourse().getId())
							+ (s.getTime() == null ? "" : " " + s.getTime().getLongName())
							+ (s.getNrRooms() == 0 ? "" : " " + s.getPlacement().getRoomName(", "));
					}
				}
			}
			message += "\n  New schedule:";
			message += "\n    " + request.getName() + (request instanceof FreeTimeRequest || request.getInitialAssignment() != null ? "" : " NOT ASSIGNED");
			if (request instanceof CourseRequest && request.getInitialAssignment() != null) {
				for (Section s: request.getInitialAssignment().getSections()) {
					message += "\n      " + s.getSubpart().getName() + " " + s.getName(request.getInitialAssignment().getCourse().getId())
						+ (s.getTime() == null ? "" : " " + s.getTime().getLongName())
						+ (s.getNrRooms() == 0 ? "" : " " + s.getPlacement().getRoomName(", "));
				}
			}
			iLog.info(message);
		}
	}

	
	@Override
	public boolean isOfferingLocked(Long offeringId) {
		synchronized (iOfferingLocks) {
			return iOfferingLocks.containsKey(offeringId);
		}
	}

	@Override
	public void lockOffering(Long offeringId) {
		synchronized (iOfferingLocks) {
			if (iOfferingLocks.containsKey(offeringId)) return;
		}
		Lock lock = iMultiLock.lock(offeringId);
		synchronized (iOfferingLocks) {
			if (iOfferingLocks.containsKey(offeringId))
				lock.release();
			else
				iOfferingLocks.put(offeringId, lock);
		}
	}

	@Override
	public void unlockOffering(Long offeringId) {
		synchronized (iOfferingLocks) {
			Lock lock = iOfferingLocks.remove(offeringId);
			if (lock != null)
				lock.release();
		}
	}
	
	@Override
	public Collection<Long> getLockedOfferings() {
		synchronized (iOfferingLocks) {
			return new ArrayList<Long>(iOfferingLocks.keySet());
		}
	}
	
	@Override
	public void releaseAllOfferingLocks() {
		synchronized (iOfferingLocks) {
			for (Lock lock: iOfferingLocks.values())
				lock.release();
			iOfferingLocks.clear();
		}
	}

	@Override
	public <E> void execute(final OnlineSectioningAction<E> action, final Callback<E> callback) throws SectioningException {
		synchronized (iExecutorQueue) {
			iExecutorQueue.offer(new Runnable() {
				@Override
				public void run() {
					try {
						callback.onSuccess(execute(action));
					} catch (Throwable t) {
						callback.onFailure(t);
					}
				}
			});
			iExecutorQueue.notify();
		}
	}
	
	public class AsyncExecutor extends Thread {
		private boolean iStop = false;
		
		public AsyncExecutor() {
			setName("AsyncExecutor[" + getAcademicSession() + "]");
			setDaemon(true);
		}
		
		public void run() {
			Runnable job;
			while (!iStop) {
				synchronized (iExecutorQueue) {
					job = iExecutorQueue.poll();
					if (job == null) {
						try {
							iLog.info("Executor is waiting for a new job...");
							iExecutorQueue.wait();
						} catch (InterruptedException e) {}
						continue;
					}		
				}
				job.run();
			}
			iLog.info("Executor stopped.");
		}
		
	}
	
	@Override
	public void unload() {
		if (iExecutor != null) {
			iExecutor.iStop = true;
			synchronized (iExecutorQueue) {
				iExecutorQueue.notify();
			}
		}
	}
}
