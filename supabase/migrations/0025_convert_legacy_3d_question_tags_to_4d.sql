-- Convert previously saved in-app 3D-prefixed question concept tags to the
-- current interactive 4D learning terminology. The underlying models remain
-- 3D objects with motion, rotation and changing states.
update edulock.questions
set concept_key = regexp_replace(concept_key, '^3d:', '4d:')
where concept_key like '3d:%';

-- Parent-created question IDs and answers are preserved; only the concept tag changes.
