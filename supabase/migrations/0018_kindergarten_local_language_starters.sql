-- Add localized Kindergarten starters for Pakistan and India.
-- These are foundational practice prompts, not an official board syllabus.

insert into edulock.curriculum_questions
(country_code,curriculum_name,grade_level,age_min,age_max,subject_code,subject_name,language_code,topic,prompt,question_type,options,answer_key,concept_key,source_note)
values
('PK','Pakistan Urdu starter bank',0,3,5,'math','Mathematics','ur','Counting','2 + 1 کا جواب کیا ہے؟','numeric','[]','{"answer":"3"}','ur-math-grade-0','Urdu Kindergarten practice; verify school-specific terminology.'),
('PK','Pakistan Sindhi starter bank',0,3,5,'math','Mathematics','sd','Counting','2 + 1 جو جواب ڇا آهي؟','numeric','[]','{"answer":"3"}','sd-math-grade-0','Sindhi Kindergarten practice; verify school-specific terminology.'),
('IN','India Hindi starter bank',0,3,5,'math','Mathematics','hi','Counting','2 + 1 का उत्तर क्या है?','numeric','[]','{"answer":"3"}','hi-math-grade-0','Hindi Kindergarten practice; verify school-specific terminology.'),
('PK','Pakistan Urdu starter bank',0,3,5,'urdu','Urdu','ur','Vocabulary','کون سا لفظ ایک پھل کا نام ہے؟','multiple_choice','["سیب","میز","دروازہ"]','{"answer":"سیب"}','ur-early-urdu-vocabulary','Urdu Kindergarten practice; verify school-specific terminology.'),
('PK','Pakistan Sindhi starter bank',0,3,5,'sindhi','Sindhi','sd','Vocabulary','ڪهڙو لفظ ميوي جو نالو آهي؟','multiple_choice','["انب","ميز","دروازو"]','{"answer":"انب"}','sd-early-sindhi-vocabulary','Sindhi Kindergarten practice; verify school-specific terminology.'),
('IN','India Hindi starter bank',0,3,5,'hindi','Hindi','hi','Vocabulary','कौन-सा शब्द फल का नाम है?','multiple_choice','["सेब","मेज़","दरवाज़ा"]','{"answer":"सेब"}','hi-early-hindi-vocabulary','Hindi Kindergarten practice; verify school-specific terminology.')
on conflict do nothing;

-- The question table has no natural-key constraint, so make the migration safely repeatable.
delete from edulock.curriculum_questions newer
using edulock.curriculum_questions older
where newer.id > older.id
  and newer.country_code = older.country_code
  and newer.grade_level = older.grade_level
  and newer.subject_code = older.subject_code
  and newer.language_code = older.language_code
  and newer.concept_key = older.concept_key
  and newer.concept_key in (
    'ur-math-grade-0','sd-math-grade-0','hi-math-grade-0',
    'ur-early-urdu-vocabulary','sd-early-sindhi-vocabulary','hi-early-hindi-vocabulary'
  );
