-- Age/grade-banded starter banks for Physics, Chemistry, Biology, Geography,
-- Health & Safety, Art & Design and Astronomy. These are practice starters, not official
-- board exam material; teachers should check each bank against the current local syllabus.

insert into edulock.curriculum_questions
(country_code,curriculum_name,grade_level,age_min,age_max,subject_code,subject_name,language_code,topic,prompt,question_type,options,answer_key,concept_key,source_note)
select region.country_code, region.curriculum_name, g.grade_level,
       greatest(3,g.grade_level+4), least(19,g.grade_level+7),
       s.code, s.name, 'en',
       case when g.grade_level<=3 then s.topic_early
            when g.grade_level<=6 then s.topic_middle
            when g.grade_level<=9 then s.topic_upper else s.topic_advanced end,
       case
         when s.code='physics' and g.grade_level<=3 then 'Which force pulls a dropped ball toward Earth?'
         when s.code='physics' and g.grade_level<=6 then 'What can friction do to a moving object?'
         when s.code='physics' and g.grade_level<=9 then 'Which quantity is measured in newtons?'
         when s.code='physics' then 'A net force of 12 N acts on a 3 kg object. What is its acceleration in m/s²?'
         when s.code='chemistry' and g.grade_level<=3 then 'Which state of water is ice?'
         when s.code='chemistry' and g.grade_level<=6 then 'Which part of an atom has a negative charge?'
         when s.code='chemistry' and g.grade_level<=9 then 'What is the chemical symbol for oxygen?'
         when s.code='chemistry' then 'A solution has pH 3. Is it acidic, neutral or alkaline?'
         when s.code='biology' and g.grade_level<=3 then 'Which living thing normally grows from a seed?'
         when s.code='biology' and g.grade_level<=6 then 'Which plant part mainly absorbs water from soil?'
         when s.code='biology' and g.grade_level<=9 then 'Which cell structure contains most genetic material?'
         when s.code='biology' then 'What molecule carries inherited genetic instructions?'
         when s.code='geography' and g.grade_level<=3 then 'What does a map help us locate?'
         when s.code='geography' and g.grade_level<=6 then 'Which is the largest ocean on Earth?'
         when s.code='geography' and g.grade_level<=9 then 'What is the long-term pattern of weather in a place called?'
         when s.code='geography' then 'What do contour lines on a topographic map connect?'
         when s.code='health' and g.grade_level<=3 then 'What is a healthy habit before eating?'
         when s.code='health' and g.grade_level<=6 then 'Which meal pattern supports a balanced diet?'
         when s.code='health' and g.grade_level<=9 then 'What should you do first for a minor burn?'
         when s.code='health' then 'Which source should be used for medical advice about a serious injury?'
         when s.code='arts' and g.grade_level<=3 then 'Which tool is commonly used to draw a line?'
         when s.code='arts' and g.grade_level<=6 then 'Which group contains traditional primary paint colours?'
         when s.code='arts' and g.grade_level<=9 then 'What does perspective help artists suggest in a drawing?'
         when s.code='arts' then 'What is visual balance in a composition?'
         when s.code='astronomy' and g.grade_level<=3 then 'What is the bright star at the centre of our Solar System?'
         when s.code='astronomy' and g.grade_level<=6 then 'Which planet is called the Red Planet?'
         when s.code='astronomy' and g.grade_level<=9 then 'What causes the phases of the Moon as seen from Earth?'
         else 'What does orbital speed depend on for an ideal circular orbit around a much larger body?'
       end,
       case when s.code='physics' and g.grade_level>=10 then 'numeric' else 'multiple_choice' end,
       case
         when s.code='physics' and g.grade_level>=10 then '[]'::jsonb
         when s.code='physics' and g.grade_level<=3 then '["Gravity","Magnetism","Friction"]'::jsonb
         when s.code='physics' and g.grade_level<=6 then '["It can slow motion","It always creates mass","It removes gravity"]'::jsonb
         when s.code='physics' then '["Force","Distance","Temperature"]'::jsonb
         when s.code='chemistry' and g.grade_level<=3 then '["Solid","Gas","Plasma only"]'::jsonb
         when s.code='chemistry' and g.grade_level<=6 then '["Electron","Proton","Neutron"]'::jsonb
         when s.code='chemistry' and g.grade_level<=9 then '["O","Ox","Oy"]'::jsonb
         when s.code='chemistry' then '["Acidic","Neutral","Alkaline"]'::jsonb
         when s.code='biology' and g.grade_level<=3 then '["Plant","Stone","Plastic cup"]'::jsonb
         when s.code='biology' and g.grade_level<=6 then '["Roots","Flower petals","Fruit skin only"]'::jsonb
         when s.code='biology' and g.grade_level<=9 then '["Nucleus","Cell membrane only","Vacuole only"]'::jsonb
         when s.code='biology' then '["DNA","Starch","Water"]'::jsonb
         when s.code='geography' and g.grade_level<=3 then '["Places","Sounds","Time travel"]'::jsonb
         when s.code='geography' and g.grade_level<=6 then '["Pacific Ocean","Indian Ocean","Arctic Ocean"]'::jsonb
         when s.code='geography' and g.grade_level<=9 then '["Climate","Weather today","Longitude only"]'::jsonb
         when s.code='geography' then '["Places of equal elevation","Places of equal population","Roads only"]'::jsonb
         when s.code='health' and g.grade_level<=3 then '["Wash hands","Share a dirty cup","Skip cleaning"]'::jsonb
         when s.code='health' and g.grade_level<=6 then '["Variety of food groups","Only sweets","Only one food every day"]'::jsonb
         when s.code='health' and g.grade_level<=9 then '["Cool under clean running water","Rub with soil","Apply an unknown chemical"]'::jsonb
         when s.code='health' then '["Emergency services or qualified health professional","An anonymous rumour","An unlabelled product advertisement"]'::jsonb
         when s.code='arts' and g.grade_level<=3 then '["Pencil","Cooking pan","Thermometer only"]'::jsonb
         when s.code='arts' and g.grade_level<=6 then '["Red, yellow and blue","Green, orange and purple","Black, grey and white"]'::jsonb
         when s.code='arts' and g.grade_level<=9 then '["Depth and distance","Sound volume","A chemical reaction"]'::jsonb
         when s.code='arts' then '["An intentional distribution of visual weight","A guarantee that every object is identical","A rule that only one colour may be used"]'::jsonb
         when s.code='astronomy' and g.grade_level<=3 then '["The Sun","The Moon","Jupiter"]'::jsonb
         when s.code='astronomy' and g.grade_level<=6 then '["Mars","Venus","Neptune"]'::jsonb
         when s.code='astronomy' and g.grade_level<=9 then '["Viewing geometry as the Moon orbits Earth","Earth's shadow every night","Clouds on the Moon"]'::jsonb
         else '["Orbital radius and gravity","The planet's colour only","The number of moons only"]'::jsonb
       end,
       jsonb_build_object('answer',
         case
           when s.code='physics' and g.grade_level>=10 then '4'
           when s.code='physics' and g.grade_level<=3 then 'Gravity'
           when s.code='physics' and g.grade_level<=6 then 'It can slow motion'
           when s.code='physics' then 'Force'
           when s.code='chemistry' and g.grade_level<=3 then 'Solid'
           when s.code='chemistry' and g.grade_level<=6 then 'Electron'
           when s.code='chemistry' and g.grade_level<=9 then 'O'
           when s.code='chemistry' then 'Acidic'
           when s.code='biology' and g.grade_level<=3 then 'Plant'
           when s.code='biology' and g.grade_level<=6 then 'Roots'
           when s.code='biology' and g.grade_level<=9 then 'Nucleus'
           when s.code='biology' then 'DNA'
           when s.code='geography' and g.grade_level<=3 then 'Places'
           when s.code='geography' and g.grade_level<=6 then 'Pacific Ocean'
           when s.code='geography' and g.grade_level<=9 then 'Climate'
           when s.code='geography' then 'Places of equal elevation'
           when s.code='health' and g.grade_level<=3 then 'Wash hands'
           when s.code='health' and g.grade_level<=6 then 'Variety of food groups'
           when s.code='health' and g.grade_level<=9 then 'Cool under clean running water'
           when s.code='health' then 'Emergency services or qualified health professional'
           when s.code='arts' and g.grade_level<=3 then 'Pencil'
           when s.code='arts' and g.grade_level<=6 then 'Red, yellow and blue'
           when s.code='arts' and g.grade_level<=9 then 'Depth and distance'
           when s.code='arts' then 'An intentional distribution of visual weight'
           when s.code='astronomy' and g.grade_level<=3 then 'The Sun'
           when s.code='astronomy' and g.grade_level<=6 then 'Mars'
           when s.code='astronomy' and g.grade_level<=9 then 'Viewing geometry as the Moon orbits Earth'
           else 'Orbital radius and gravity'
         end),
       s.code || '-grade-' || g.grade_level::text,
       region.curriculum_name || ' age/grade-banded starter practice; verify against the current local curriculum.'
from generate_series(0,13) as g(grade_level)
cross join (values
  ('ZZ'::text,'International expanded starter bank'::text),
  ('PK'::text,'Pakistan expanded subject starter bank'::text),
  ('IN'::text,'India expanded subject starter bank'::text)
) as region(country_code,curriculum_name)
cross join (values
  ('physics'::text,'Physics'::text,'Motion'::text,'Forces'::text,'Mechanics'::text,'Advanced physics'::text),
  ('chemistry'::text,'Chemistry'::text,'Materials'::text,'Atoms'::text,'Chemical changes'::text,'Advanced chemistry'::text),
  ('biology'::text,'Biology'::text,'Living things'::text,'Plants'::text,'Cells'::text,'Advanced biology'::text),
  ('geography'::text,'Geography'::text,'Places'::text,'Maps'::text,'Climate'::text,'Advanced geography'::text),
  ('health'::text,'Health & Safety'::text,'Healthy habits'::text,'Nutrition'::text,'First aid'::text,'Health literacy'::text),
  ('arts'::text,'Art & Design'::text,'Shapes'::text,'Colour'::text,'Drawing'::text,'Visual composition'::text),
  ('astronomy'::text,'Astronomy'::text,'Space'::text,'Planets'::text,'Moon and orbits'::text,'Orbital motion'::text)
) as s(code,name,topic_early,topic_middle,topic_upper,topic_advanced)
where not exists (
  select 1 from edulock.curriculum_questions old
  where old.country_code=region.country_code and old.grade_level=g.grade_level
    and old.subject_code=s.code and old.language_code='en'
    and old.concept_key=s.code || '-grade-' || g.grade_level::text
);
