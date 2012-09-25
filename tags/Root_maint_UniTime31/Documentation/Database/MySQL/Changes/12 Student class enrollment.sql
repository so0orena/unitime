/*
 * UniTime 3.1 (University Timetabling Application)
 * Copyright (C) 2008, UniTime LLC
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

/**
  * Add course offering id to StudentClassEnrollment table
  */
  
alter table student_class_enrl add course_offering_id decimal(20,0);

alter table student_class_enrl 
  add constraint fk_student_class_enrl_course foreign key (course_offering_id)
  references course_offering (uniqueid) on delete cascade;
  
/*
 * Update database version
 */

update application_config set value='12' where name='tmtbl.db.version';

commit;
  