-- EduLock country-aware curriculum and age-fit learning bank.
-- The question bank is read only through Edge Functions so answer keys never reach child devices.

create schema if not exists edulock;

alter table edulock.children
  add column if not exists country_code text not null default 'PK',
  add column if not exists school_grade integer not null default 1,
  add column if not exists age_years integer not null default 8;
alter table edulock.children
  drop constraint if exists children_country_code_check;
alter table edulock.children
  add constraint children_country_code_check check (country_code ~ '^[A-Z]{2}$');
alter table edulock.children
  drop constraint if exists children_school_grade_check;
alter table edulock.children
  add constraint children_school_grade_check check (school_grade between 0 and 13);
alter table edulock.children
  drop constraint if exists children_age_years_check;
alter table edulock.children
  add constraint children_age_years_check check (age_years between 3 and 19);

alter table edulock.subjects
  add column if not exists country_code text not null default 'PK',
  add column if not exists grade_level integer not null default 1,
  add column if not exists curriculum_name text not null default 'Parent-created',
  add column if not exists curriculum_subject_code text not null default '';
alter table edulock.subjects
  drop constraint if exists subjects_country_code_check;
alter table edulock.subjects
  add constraint subjects_country_code_check check (country_code ~ '^[A-Z]{2}$');
alter table edulock.subjects
  drop constraint if exists subjects_grade_level_check;
alter table edulock.subjects
  add constraint subjects_grade_level_check check (grade_level between 0 and 13);

alter table edulock.questions
  add column if not exists curriculum_question_id uuid;

create unique index if not exists questions_subject_curriculum_question_uidx
  on edulock.questions(subject_id, curriculum_question_id);

create table if not exists edulock.curriculum_questions (
  id uuid primary key default gen_random_uuid(),
  country_code text not null default 'ZZ' check (country_code ~ '^[A-Z]{2}$'),
  curriculum_name text not null default 'International foundational starter bank',
  region_code text not null default '',
  grade_level integer not null check (grade_level between 0 and 13),
  age_min integer not null check (age_min between 3 and 19),
  age_max integer not null check (age_max between age_min and 19),
  subject_code text not null check (char_length(subject_code) between 2 and 40),
  subject_name text not null check (char_length(subject_name) between 2 and 120),
  language_code text not null default 'en' check (char_length(language_code) between 2 and 16),
  topic text not null default '',
  prompt text not null check (char_length(prompt) between 1 and 10000),
  question_type text not null check (question_type in ('multiple_choice','true_false','short_answer','numeric')),
  options jsonb not null default '[]'::jsonb check (jsonb_typeof(options) = 'array'),
  answer_key jsonb not null default '{}'::jsonb,
  concept_key text not null default 'general',
  source_note text not null default 'Foundational starter content; verify local curriculum alignment.',
  active boolean not null default true,
  created_at timestamptz not null default now()
);
create index if not exists curriculum_questions_filter_idx
  on edulock.curriculum_questions(country_code, grade_level, subject_code, language_code, age_min, age_max)
  where active = true;

alter table edulock.curriculum_questions enable row level security;
revoke all on edulock.curriculum_questions from public, anon, authenticated;
grant select, insert, update, delete on edulock.curriculum_questions to service_role;

-- Keep question-bank answer keys server-only. The following are foundational practice items,
-- not a claim that every item is an official country's examination/syllabus question.
insert into edulock.curriculum_questions
(country_code,curriculum_name,grade_level,age_min,age_max,subject_code,subject_name,language_code,topic,prompt,question_type,options,answer_key,concept_key,source_note)
values
('ZZ','International foundational starter bank',1,5,8,'math','Mathematics','en','Counting','Which number comes after 4?','multiple_choice','["3","5","6"]','{"answer":"5"}','counting','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',1,5,8,'math','Mathematics','en','Shapes','How many sides does a triangle have?','multiple_choice','["2","3","4"]','{"answer":"3"}','2d-shapes','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',1,5,8,'math','Mathematics','en','Addition','What is 2 + 3?','multiple_choice','["4","5","6"]','{"answer":"5"}','addition-within-10','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',1,5,8,'english','English','en','Letters','Which letter comes first in the English alphabet?','multiple_choice','["A","B","C"]','{"answer":"A"}','alphabet-order','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',1,5,8,'english','English','en','Vocabulary','Which word names an animal?','multiple_choice','["Table","Cat","Blue"]','{"answer":"Cat"}','word-categories','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',1,5,8,'science','General Science','en','Living things','Which one is a living thing?','multiple_choice','["Stone","Tree","Cup"]','{"answer":"Tree"}','living-things','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',1,5,8,'science','General Science','en','Senses','Which body part helps you hear?','multiple_choice','["Ears","Knees","Hands"]','{"answer":"Ears"}','five-senses','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',2,6,9,'math','Mathematics','en','Place value','In 34, how many tens are there?','multiple_choice','["3","4","34"]','{"answer":"3"}','place-value-tens','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',2,6,9,'math','Mathematics','en','Addition','What is 16 + 7?','numeric','[]','{"answer":"23"}','addition-with-regrouping','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',2,6,9,'math','Mathematics','en','Subtraction','What is 15 - 6?','numeric','[]','{"answer":"9"}','subtraction-within-20','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',2,6,9,'english','English','en','Grammar','Which word is a naming word (noun)?','multiple_choice','["Run","School","Quickly"]','{"answer":"School"}','nouns','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',2,6,9,'english','English','en','Reading','Choose the opposite of hot.','multiple_choice','["Warm","Cold","Bright"]','{"answer":"Cold"}','antonyms','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',2,6,9,'science','General Science','en','Plants','Which part of a plant usually takes in water from the soil?','multiple_choice','["Root","Flower","Fruit"]','{"answer":"Root"}','plant-parts','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',2,6,9,'science','General Science','en','Materials','Which material is usually transparent?','multiple_choice','["Clear glass","Wood","Brick"]','{"answer":"Clear glass"}','material-properties','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',3,7,10,'math','Mathematics','en','Multiplication','What is 6 × 4?','numeric','[]','{"answer":"24"}','multiplication-facts','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',3,7,10,'math','Mathematics','en','Fractions','What fraction means one of four equal parts?','multiple_choice','["1/2","1/3","1/4"]','{"answer":"1/4"}','unit-fractions','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',3,7,10,'english','English','en','Grammar','Choose the correct sentence.','multiple_choice','["She are happy.","She is happy.","She am happy."]','{"answer":"She is happy."}','subject-verb-agreement','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',3,7,10,'english','English','en','Punctuation','Which mark ends a question?','multiple_choice','["?","!","."]','{"answer":"?"}','question-mark','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',3,7,10,'science','General Science','en','Solar system','Which star gives Earth most of its light and heat?','multiple_choice','["The Sun","The Moon","Polaris"]','{"answer":"The Sun"}','sun-earth','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',3,7,10,'science','General Science','en','States of matter','What is water called when it freezes?','multiple_choice','["Steam","Ice","Rain"]','{"answer":"Ice"}','states-of-matter','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',4,8,11,'math','Mathematics','en','Division','What is 56 ÷ 7?','numeric','[]','{"answer":"8"}','division-facts','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',4,8,11,'math','Mathematics','en','Perimeter','A square has sides of 5 cm. What is its perimeter in cm?','numeric','[]','{"answer":"20"}','perimeter-square','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',4,8,11,'english','English','en','Vocabulary','What is a synonym for begin?','multiple_choice','["Start","Finish","Close"]','{"answer":"Start"}','synonyms','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',4,8,11,'english','English','en','Parts of speech','Which word is an adjective in “the small bird sang”?','multiple_choice','["bird","small","sang"]','{"answer":"small"}','adjectives','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',4,8,11,'science','General Science','en','Human body','Which organ pumps blood around the body?','multiple_choice','["Lungs","Heart","Stomach"]','{"answer":"Heart"}','circulatory-system','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',4,8,11,'science','General Science','en','Energy','Which is a renewable energy source?','multiple_choice','["Sunlight","Coal","Petrol"]','{"answer":"Sunlight"}','renewable-energy','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',5,9,12,'math','Mathematics','en','Decimals','What is 0.25 + 0.5?','numeric','[]','{"answer":"0.75"}','decimal-addition','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',5,9,12,'math','Mathematics','en','Fractions','What is 3/4 of 20?','numeric','[]','{"answer":"15"}','fraction-of-quantity','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',5,9,12,'english','English','en','Grammar','Choose the past tense of “teach”.','multiple_choice','["Teached","Taught","Teaching"]','{"answer":"Taught"}','irregular-verbs','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',5,9,12,'english','English','en','Reading','What is the main idea of a paragraph?','short_answer','[]','{"answer":"The most important point"}','main-idea','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',5,9,12,'science','General Science','en','Earth science','What process changes liquid water into water vapour?','multiple_choice','["Freezing","Evaporation","Melting"]','{"answer":"Evaporation"}','water-cycle-evaporation','Foundational practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',5,9,12,'science','General Science','en','Ecosystems','What do green plants use sunlight to help make?','multiple_choice','["Food","Plastic","Sand"]','{"answer":"Food"}','photosynthesis-intro','Foundational practice; not an official national syllabus.'),
('PK','Pakistan foundational starter bank',1,5,8,'math','Mathematics','en','Counting','What number comes after 9?','numeric','[]','{"answer":"10"}','counting-to-10','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',1,5,8,'english','English','en','Vocabulary','Which word is a greeting?','multiple_choice','["Hello","Table","Green"]','{"answer":"Hello"}','greetings','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',1,5,8,'science','General Science','en','Environment','Which is a source of light in the daytime?','multiple_choice','["Sun","Pillow","Shoes"]','{"answer":"Sun"}','daylight','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',2,6,9,'math','Mathematics','en','Place value','What is the value of 5 in 52?','multiple_choice','["5","50","2"]','{"answer":"50"}','place-value','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',2,6,9,'english','English','en','Grammar','Choose the plural of “child”.','multiple_choice','["Childs","Children","Childes"]','{"answer":"Children"}','plural-nouns','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',2,6,9,'science','General Science','en','Water','Which action helps save water?','multiple_choice','["Leave the tap open","Close the tap after use","Play with running water"]','{"answer":"Close the tap after use"}','water-conservation','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',3,7,10,'math','Mathematics','en','Multiplication','What is 8 × 5?','numeric','[]','{"answer":"40"}','multiplication-table','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',3,7,10,'english','English','en','Grammar','Complete the sentence: They ___ playing.','multiple_choice','["is","are","am"]','{"answer":"are"}','subject-verb-agreement','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',3,7,10,'science','General Science','en','Living things','What do plants need from sunlight to help them grow?','short_answer','[]','{"answer":"Energy"}','plant-growth','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',4,8,11,'math','Mathematics','en','Fractions','Which fraction is greater: 1/2 or 1/4?','multiple_choice','["1/2","1/4"]','{"answer":"1/2"}','compare-fractions','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',4,8,11,'english','English','en','Vocabulary','Choose a synonym for quick.','multiple_choice','["Fast","Late","Quiet"]','{"answer":"Fast"}','synonyms','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',4,8,11,'science','General Science','en','Health','Which food group helps build and repair body tissues?','multiple_choice','["Protein-rich foods","Sweets only","Soft drinks"]','{"answer":"Protein-rich foods"}','nutrition-protein','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',5,9,12,'math','Mathematics','en','Decimals','What is 2.5 + 1.25?','numeric','[]','{"answer":"3.75"}','decimal-addition','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',5,9,12,'english','English','en','Grammar','Choose the correct past-tense sentence.','multiple_choice','["I go yesterday.","I went yesterday.","I going yesterday."]','{"answer":"I went yesterday."}','past-tense','Pakistan-adapted starter practice; confirm with the local board.'),
('PK','Pakistan foundational starter bank',5,9,12,'science','General Science','en','Energy','Which energy source is renewable?','multiple_choice','["Solar energy","Coal","Natural gas"]','{"answer":"Solar energy"}','renewable-energy','Pakistan-adapted starter practice; confirm with the local board.');
