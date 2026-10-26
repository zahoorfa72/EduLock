-- Wider Pakistan/India starter banks. These are practice items, not a claim of complete
-- or officially endorsed board-specific syllabus coverage.

-- Copy English international starter questions into regional banks for every listed subject.
-- Preserve local items when a country/grade/subject/language/concept is already present.
insert into edulock.curriculum_questions
(country_code,curriculum_name,grade_level,age_min,age_max,subject_code,subject_name,language_code,topic,prompt,question_type,options,answer_key,concept_key,source_note)
select region.country_code, region.curriculum_name,
       q.grade_level, q.age_min, q.age_max, q.subject_code, q.subject_name,
       q.language_code, q.topic, q.prompt, q.question_type, q.options, q.answer_key,
       q.concept_key,
       region.curriculum_name || ' starter practice adapted from the international bank; verify alignment with the current state or board syllabus.'
from edulock.curriculum_questions q
cross join (values
  ('PK'::text, 'Pakistan curriculum starter bank'::text),
  ('IN'::text, 'India curriculum starter bank'::text)
) as region(country_code, curriculum_name)
where q.country_code = 'ZZ'
  and q.language_code = 'en'
  and not exists (
    select 1 from edulock.curriculum_questions existing
    where existing.country_code = region.country_code
      and existing.grade_level = q.grade_level
      and existing.subject_code = q.subject_code
      and existing.language_code = q.language_code
      and existing.concept_key = q.concept_key
  );

-- Extra numeric practice in Urdu, Sindhi and Hindi. The age filter stays strict in the API.
insert into edulock.curriculum_questions
(country_code,curriculum_name,grade_level,age_min,age_max,subject_code,subject_name,language_code,topic,prompt,question_type,options,answer_key,concept_key,source_note)
select locale.country_code, locale.curriculum_name, g.grade_level,
       greatest(3, g.grade_level + 4), least(19, g.grade_level + 7),
       'math', 'Mathematics', locale.language_code,
       case when g.grade_level <= 3 then 'Arithmetic'
            when g.grade_level <= 6 then 'Multiplication'
            when g.grade_level <= 9 then 'Equations'
            else 'Functions' end,
       case
         when locale.language_code = 'ur' and g.grade_level <= 3 then format('حساب کریں: %s + %s = ؟', g.grade_level, g.grade_level * 2)
         when locale.language_code = 'ur' and g.grade_level <= 6 then format('حاصل ضرب نکالیں: %s × %s = ؟', g.grade_level, g.grade_level + 1)
         when locale.language_code = 'ur' and g.grade_level <= 9 then format('مساوات حل کریں: 2x + %s = %s۔ x کی قیمت کیا ہے؟', g.grade_level, g.grade_level * 3)
         when locale.language_code = 'ur' then format('اگر f(x) = x² + %s x ہو تو f′(1) معلوم کریں۔', g.grade_level)
         when locale.language_code = 'sd' and g.grade_level <= 3 then format('حساب ڪريو: %s + %s = ؟', g.grade_level, g.grade_level * 2)
         when locale.language_code = 'sd' and g.grade_level <= 6 then format('حاصل ضرب ڪڍو: %s × %s = ؟', g.grade_level, g.grade_level + 1)
         when locale.language_code = 'sd' and g.grade_level <= 9 then format('مساوات حل ڪريو: 2x + %s = %s. x جي قيمت ڇا آهي؟', g.grade_level, g.grade_level * 3)
         when locale.language_code = 'sd' then format('جيڪڏهن f(x) = x² + %s x آهي ته f′(1) ڳوليو.', g.grade_level)
         when locale.language_code = 'hi' and g.grade_level <= 3 then format('हल करें: %s + %s = ?', g.grade_level, g.grade_level * 2)
         when locale.language_code = 'hi' and g.grade_level <= 6 then format('गुणा कीजिए: %s × %s = ?', g.grade_level, g.grade_level + 1)
         when locale.language_code = 'hi' and g.grade_level <= 9 then format('समीकरण हल करें: 2x + %s = %s। x का मान क्या है?', g.grade_level, g.grade_level * 3)
         else format('यदि f(x) = x² + %s x है, तो f′(1) ज्ञात करें।', g.grade_level)
       end,
       'numeric', '[]'::jsonb,
       jsonb_build_object('answer',
         case when g.grade_level <= 3 then (g.grade_level * 3)::text
              when g.grade_level <= 6 then (g.grade_level * (g.grade_level + 1))::text
              when g.grade_level <= 9 then g.grade_level::text
              else (g.grade_level + 2)::text end),
       locale.language_code || '-math-grade-' || g.grade_level::text,
       locale.curriculum_name || ' localized starter practice; verify terminology and syllabus level with the school board.'
from generate_series(1, 13) as g(grade_level)
cross join (values
  ('PK'::text, 'Pakistan Urdu starter bank'::text, 'ur'::text),
  ('PK'::text, 'Pakistan Sindhi starter bank'::text, 'sd'::text),
  ('IN'::text, 'India Hindi starter bank'::text, 'hi'::text)
) as locale(country_code, curriculum_name, language_code)
where not exists (
  select 1 from edulock.curriculum_questions existing
  where existing.country_code = locale.country_code
    and existing.grade_level = g.grade_level
    and existing.subject_code = 'math'
    and existing.language_code = locale.language_code
    and existing.concept_key = locale.language_code || '-math-grade-' || g.grade_level::text
);

-- Multilingual literacy topics across grades. Advanced-language items are starter practice and
-- should be reviewed against the learner's current textbook before being used for assessment.
insert into edulock.curriculum_questions
(country_code,curriculum_name,grade_level,age_min,age_max,subject_code,subject_name,language_code,topic,prompt,question_type,options,answer_key,concept_key,source_note)
select locale.country_code, locale.curriculum_name, g.grade_level,
       greatest(3, g.grade_level + 4), least(19, g.grade_level + 7),
       locale.subject_code, locale.subject_name, locale.language_code, 'Language practice',
       case
         when locale.language_code = 'ur' and g.grade_level <= 3 then 'لفظ “خوش” کا مخالف کیا ہے؟'
         when locale.language_code = 'ur' and g.grade_level <= 6 then 'جملے میں اسم پہچانیں: “علی کتاب پڑھتا ہے۔”'
         when locale.language_code = 'ur' and g.grade_level <= 9 then 'لفظ “علم” کا مترادف کیا ہے؟'
         when locale.language_code = 'ur' then 'درج ذیل میں درست جملہ کون سا ہے؟'
         when locale.language_code = 'sd' and g.grade_level <= 3 then 'لفظ “وڏو” جو ابتڙ ڇا آهي؟'
         when locale.language_code = 'sd' and g.grade_level <= 6 then 'جملو “علي ڪتاب پڙهي ٿو.” ۾ اسم ڪهڙو آهي؟'
         when locale.language_code = 'sd' and g.grade_level <= 9 then 'لفظ “علم” جو هم معنيٰ لفظ ڪهڙو آهي؟'
         when locale.language_code = 'sd' then 'ڪهڙو جملو درست آهي؟'
         when locale.language_code = 'hi' and g.grade_level <= 3 then '“खुश” का विलोम क्या है?'
         when locale.language_code = 'hi' and g.grade_level <= 6 then 'वाक्य में संज्ञा पहचानें: “सीमा किताब पढ़ती है।”'
         when locale.language_code = 'hi' and g.grade_level <= 9 then '“ज्ञान” का समानार्थी शब्द क्या है?'
         else 'निम्नलिखित में से सही वाक्य कौन सा है?'
       end,
       'multiple_choice',
       case
         when locale.language_code = 'ur' and g.grade_level <= 3 then '["اداس","خوبصورت","تیز"]'::jsonb
         when locale.language_code = 'ur' and g.grade_level <= 6 then '["علی","پڑھتا","ہے"]'::jsonb
         when locale.language_code = 'ur' and g.grade_level <= 9 then '["دانش","شور","اندھیرا"]'::jsonb
         when locale.language_code = 'ur' then '["مجھے کتاب پڑھنی ہے۔","مجھے کتاب پڑھنا ہے۔","میں کتاب پڑھتے ہے۔"]'::jsonb
         when locale.language_code = 'sd' and g.grade_level <= 3 then '["ننڍو","تيز","سٺو"]'::jsonb
         when locale.language_code = 'sd' and g.grade_level <= 6 then '["علي","پڙهي","ٿو"]'::jsonb
         when locale.language_code = 'sd' and g.grade_level <= 9 then '["ڄاڻ","شور","اونداهي"]'::jsonb
         when locale.language_code = 'sd' then '["مان ڪتاب پڙهان ٿو.","مان ڪتاب پڙهندا آهيان اسڪول.","مان ڪتاب پڙهي آهي."]'::jsonb
         when locale.language_code = 'hi' and g.grade_level <= 3 then '["उदास","सुंदर","तेज़"]'::jsonb
         when locale.language_code = 'hi' and g.grade_level <= 6 then '["सीमा","पढ़ती","है"]'::jsonb
         when locale.language_code = 'hi' and g.grade_level <= 9 then '["विद्या","शोर","अंधेरा"]'::jsonb
         else '["मुझे पुस्तक पढ़नी है।","मुझे पुस्तक पढ़ना है।","मैं पुस्तक पढ़ते है।"]'::jsonb
       end,
       jsonb_build_object('answer',
         case
           when locale.language_code = 'ur' and g.grade_level <= 3 then 'اداس'
           when locale.language_code = 'ur' and g.grade_level <= 6 then 'علی'
           when locale.language_code = 'ur' and g.grade_level <= 9 then 'دانش'
           when locale.language_code = 'ur' then 'مجھے کتاب پڑھنی ہے۔'
           when locale.language_code = 'sd' and g.grade_level <= 3 then 'ننڍو'
           when locale.language_code = 'sd' and g.grade_level <= 6 then 'علي'
           when locale.language_code = 'sd' and g.grade_level <= 9 then 'ڄاڻ'
           when locale.language_code = 'sd' then 'مان ڪتاب پڙهان ٿو.'
           when locale.language_code = 'hi' and g.grade_level <= 3 then 'उदास'
           when locale.language_code = 'hi' and g.grade_level <= 6 then 'सीमा'
           when locale.language_code = 'hi' and g.grade_level <= 9 then 'विद्या'
           else 'मुझे पुस्तक पढ़नी है।'
         end),
       locale.language_code || '-language-grade-' || g.grade_level::text,
       locale.curriculum_name || ' language starter practice; ask a teacher to confirm advanced grammar examples.'
from generate_series(1, 13) as g(grade_level)
cross join (values
  ('PK'::text, 'Pakistan Urdu starter bank'::text, 'urdu'::text, 'Urdu'::text, 'ur'::text),
  ('PK'::text, 'Pakistan Sindhi starter bank'::text, 'sindhi'::text, 'Sindhi'::text, 'sd'::text),
  ('IN'::text, 'India Hindi starter bank'::text, 'hindi'::text, 'Hindi'::text, 'hi'::text)
) as locale(country_code, curriculum_name, subject_code, subject_name, language_code)
where not exists (
  select 1 from edulock.curriculum_questions existing
  where existing.country_code = locale.country_code
    and existing.grade_level = g.grade_level
    and existing.subject_code = locale.subject_code
    and existing.language_code = locale.language_code
    and existing.concept_key = locale.language_code || '-language-grade-' || g.grade_level::text
);
