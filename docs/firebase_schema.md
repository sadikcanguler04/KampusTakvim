# Firebase Realtime Database Schema

Project URL: `https://kampustakvim-default-rtdb.europe-west1.firebasedatabase.app/`

## Collections

### `users/{username}`

- `username`: Login user name.
- `password`: SHA-256 hashed password.
- `role`: `ADMIN` or `TEACHER`.
- `teacherId`: Linked lecturer id for teacher accounts.
- `mustChangePassword`: `true` for auto-generated first-login accounts.

### `temporary_credentials/{teacherId}`

- `teacherId`: Linked lecturer id.
- `teacherName`: Display name shown to admin.
- `username`: Auto-generated teacher login name.
- `temporaryPassword`: AES-encrypted temporary password while first login is pending.
- `createdAt`: Creation timestamp.
- `consumed`: `true` after the teacher changes the temporary password.
- `consumedAt`: Password-change timestamp.

### `teachers/{teacherId}`

- `id`: Lecturer id.
- `name`, `surname`, `title`, `department`: AES-encrypted profile fields.
- `departmentId`: Linked `departments/{departmentId}` key.
- `scheduleStatus`: `PENDING`, `APPROVED`, `REJECTED`, or `ADMIN_PROPOSAL`.
- `adminNote`, `teacherNote`: AES-encrypted notes.

### `departments/{departmentId}`

- `id`: Normalized department key.
- `name`: Display name.

### `courses/{courseId}`

- `id`: Stable course key, usually `{courseCode}_{teacherId}`.
- `code`: Course code.
- `name`: AES-encrypted course name.
- `departmentId`: Linked department key.
- `teacherId`: Lecturer assigned to the course.

### `classrooms/{roomCode}`

- `id`: Classroom key.
- `roomCode`: Display room code.
- `capacity`: Classroom capacity.
- `department`: Department display name.
- `departmentId`: Linked department key.

### `schedule_entries/{entryId}`

- `courseCode`, `courseName`: Course information.
- `teacherId`: Assigned lecturer.
- `classroomId`: Assigned classroom.
- `day`: Weekday index, Monday is `0`.
- `timeSlot`: Slot index.

### `availability/{teacherId}/{day}_{timeSlot}`

Legacy teacher calendar mirror used by teacher and global schedule screens.

### `proposals/{teacherId}/{day}_{timeSlot}`

Draft schedule proposal sent by admin to the lecturer.

### `verification_codes/{code}`

Manual teacher activation code records.

### `messages/{conversationId}`

Admin-teacher message records.
