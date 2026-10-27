-- Cover all model IDs used by generated starter questions, including the distinct
-- visual-model tags for faith-specific question banks. The question row remains
-- the canonical source, so re-seeding a bank cannot duplicate a subject/index pair.
begin;

drop index if exists edulock.questions_template_seed_identity_uidx;

create unique index questions_template_seed_identity_uidx
on edulock.questions (subject_id, concept_key)
where concept_key ~ '^(4d|3d):[^:]+:[a-z0-9-]+-[0-9]+$'
   or concept_key ~ '^(4d|3d):[^:]+:faith:[a-z-]+:[0-9]+$';

commit;
