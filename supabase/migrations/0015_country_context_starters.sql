-- Local starter questions for several country contexts and languages.
-- These are curriculum-aware practice examples, not verified complete official syllabi.
insert into edulock.curriculum_questions
(country_code,curriculum_name,grade_level,age_min,age_max,subject_code,subject_name,language_code,topic,prompt,question_type,options,answer_key,concept_key,source_note)
values
('IN','India country-context starter bank',2,6,9,'social','Social Studies','en','Geography','What is the capital of India?','multiple_choice','["New Delhi","Mumbai","Chennai"]','{"answer":"New Delhi"}','india-capital','India context starter; check the current state or board textbook.'),
('IN','India country-context starter bank',2,6,9,'math','Mathematics','en','Addition','What is 27 + 15?','numeric','[]','{"answer":"42"}','two-digit-addition','India context starter; check the current state or board textbook.'),
('IN','India Hindi starter bank',2,6,9,'social','Social Studies','hi','भूगोल','भारत की राजधानी कौन सी है?','multiple_choice','["नई दिल्ली","मुंबई","चेन्नई"]','{"answer":"नई दिल्ली"}','india-capital','Hindi starter practice; check the current state or board textbook.'),
('IN','India Hindi starter bank',2,6,9,'math','Mathematics','hi','जोड़','7 + 5 = ?','numeric','[]','{"answer":"12"}','addition-within-20','Hindi starter practice; check the current state or board textbook.'),

('BD','Bangladesh country-context starter bank',2,6,9,'social','Social Studies','en','Geography','What is the capital of Bangladesh?','multiple_choice','["Dhaka","Chattogram","Khulna"]','{"answer":"Dhaka"}','bangladesh-capital','Bangladesh context starter; check the current curriculum guide.'),
('BD','Bangladesh country-context starter bank',2,6,9,'math','Mathematics','en','Addition','What is 36 + 8?','numeric','[]','{"answer":"44"}','addition-with-regrouping','Bangladesh context starter; check the current curriculum guide.'),
('BD','Bangladesh Bangla starter bank',2,6,9,'social','Social Studies','bn','ভূগোল','বাংলাদেশের রাজধানী কী?','multiple_choice','["ঢাকা","চট্টগ্রাম","খুলনা"]','{"answer":"ঢাকা"}','bangladesh-capital','Bangla starter practice; check the current curriculum guide.'),
('BD','Bangladesh Bangla starter bank',2,6,9,'math','Mathematics','bn','যোগ','৮ + ৪ = কত?','numeric','[]','{"answer":"12"}','addition-within-20','Bangla starter practice; check the current curriculum guide.'),

('US','United States country-context starter bank',3,7,10,'social','Social Studies','en','Geography','What is the capital of the United States?','multiple_choice','["Washington, D.C.","New York City","Los Angeles"]','{"answer":"Washington, D.C."}','us-capital','United States context starter; check the current state standards.'),
('US','United States country-context starter bank',3,7,10,'math','Mathematics','en','Multiplication','What is 7 × 6?','numeric','[]','{"answer":"42"}','multiplication-facts','United States context starter; check the current state standards.'),

('GB','United Kingdom country-context starter bank',3,7,10,'social','Social Studies','en','Geography','What is the capital of the United Kingdom?','multiple_choice','["London","Cardiff","Edinburgh"]','{"answer":"London"}','uk-capital','United Kingdom context starter; check the current school curriculum.'),
('GB','United Kingdom country-context starter bank',3,7,10,'math','Mathematics','en','Fractions','What is one half of 18?','numeric','[]','{"answer":"9"}','halving-even-number','United Kingdom context starter; check the current school curriculum.'),

('AU','Australia country-context starter bank',3,7,10,'social','Social Studies','en','Geography','What is the capital of Australia?','multiple_choice','["Canberra","Sydney","Melbourne"]','{"answer":"Canberra"}','australia-capital','Australia context starter; check the current state or territory curriculum.'),
('AU','Australia country-context starter bank',3,7,10,'science','General Science','en','Living things','Which is a native Australian animal?','multiple_choice','["Kangaroo","Polar bear","Penguin of the Arctic"]','{"answer":"Kangaroo"}','animal-habitats','Australia context starter; check the current state or territory curriculum.'),

('CA','Canada country-context starter bank',3,7,10,'social','Social Studies','en','Geography','What is the capital of Canada?','multiple_choice','["Ottawa","Toronto","Vancouver"]','{"answer":"Ottawa"}','canada-capital','Canada context starter; check the current provincial curriculum.'),
('CA','Canada country-context starter bank',3,7,10,'science','General Science','en','Nature','Which tree leaf appears on the Canadian flag?','multiple_choice','["Maple leaf","Oak leaf","Palm leaf"]','{"answer":"Maple leaf"}','canada-maple-leaf','Canada context starter; check the current provincial curriculum.'),

('NG','Nigeria country-context starter bank',3,7,10,'social','Social Studies','en','Geography','What is the capital of Nigeria?','multiple_choice','["Abuja","Lagos","Kano"]','{"answer":"Abuja"}','nigeria-capital','Nigeria context starter; check the current state curriculum.'),
('NG','Nigeria country-context starter bank',3,7,10,'math','Mathematics','en','Multiplication','What is 8 × 7?','numeric','[]','{"answer":"56"}','multiplication-facts','Nigeria context starter; check the current state curriculum.'),

('SA','Saudi Arabia country-context starter bank',3,7,10,'social','Social Studies','en','Geography','What is the capital of Saudi Arabia?','multiple_choice','["Riyadh","Jeddah","Dammam"]','{"answer":"Riyadh"}','saudi-capital','Saudi Arabia context starter; check the current curriculum guide.'),
('SA','Saudi Arabia Arabic starter bank',3,7,10,'social','Social Studies','ar','الجغرافيا','ما عاصمة المملكة العربية السعودية؟','multiple_choice','["الرياض","جدة","الدمام"]','{"answer":"الرياض"}','saudi-capital','Arabic starter practice; check the current curriculum guide.'),

('AE','United Arab Emirates country-context starter bank',3,7,10,'social','Social Studies','en','Geography','What is the capital of the United Arab Emirates?','multiple_choice','["Abu Dhabi","Dubai","Sharjah"]','{"answer":"Abu Dhabi"}','uae-capital','United Arab Emirates context starter; check the current curriculum guide.'),
('AE','United Arab Emirates Arabic starter bank',3,7,10,'social','Social Studies','ar','الجغرافيا','ما عاصمة دولة الإمارات العربية المتحدة؟','multiple_choice','["أبوظبي","دبي","الشارقة"]','{"answer":"أبوظبي"}','uae-capital','Arabic starter practice; check the current curriculum guide.'),

('FR','France country-context starter bank',3,7,10,'social','Social Studies','en','Geography','What is the capital of France?','multiple_choice','["Paris","Lyon","Marseille"]','{"answer":"Paris"}','france-capital','France context starter; check the current national curriculum.'),
('FR','France French starter bank',3,7,10,'social','Social Studies','fr','Géographie','Quelle est la capitale de la France ?','multiple_choice','["Paris","Lyon","Marseille"]','{"answer":"Paris"}','france-capital','French starter practice; check the current national curriculum.'),

('DE','Germany country-context starter bank',3,7,10,'social','Social Studies','en','Geography','What is the capital of Germany?','multiple_choice','["Berlin","Hamburg","Munich"]','{"answer":"Berlin"}','germany-capital','Germany context starter; check the current state curriculum.'),
('DE','Germany German starter bank',3,7,10,'social','Social Studies','de','Geografie','Was ist die Hauptstadt von Deutschland?','multiple_choice','["Berlin","Hamburg","München"]','{"answer":"Berlin"}','germany-capital','German starter practice; check the current state curriculum.'),

('JP','Japan country-context starter bank',3,7,10,'social','Social Studies','en','Geography','What is the capital of Japan?','multiple_choice','["Tokyo","Osaka","Kyoto"]','{"answer":"Tokyo"}','japan-capital','Japan context starter; check the current national course of study.'),
('JP','Japan Japanese starter bank',3,7,10,'social','Social Studies','ja','地理','日本の首都はどこですか？','multiple_choice','["東京","大阪","京都"]','{"answer":"東京"}','japan-capital','Japanese starter practice; check the current national course of study.');
