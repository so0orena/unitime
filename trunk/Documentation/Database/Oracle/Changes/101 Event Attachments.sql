/*
 * UniTime 3.4 (University Timetabling Application)
 * Copyright (C) 2012, UniTime LLC
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

alter table event_note add attached_file blob;
alter table event_note add attached_name varchar2(260 char);
alter table event_note add attached_content varchar2(260 char);
alter table event_note add user_id varchar2(40 char);

create table event_note_meeting (
	note_id number(20,0) constraint nn_event_note_note not null,
	meeting_id number(20,0) constraint nn_event_note_mtg not null
);
alter table event_note_meeting add constraint pk_event_note_meeting primary key (note_id, meeting_id);

alter table event_note_meeting add constraint fk_event_note_note foreign key (note_id)
	references event_note (uniqueid) on delete cascade;

alter table event_note_meeting add constraint fk_event_note_mtg foreign key (meeting_id)
	references meeting (uniqueid) on delete cascade;

/*
 * Update database version
 */

update application_config set value='101' where name='tmtbl.db.version';

commit;
