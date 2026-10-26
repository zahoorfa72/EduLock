-- Add Kindergarten questions and starter sets in Urdu and Sindhi.
-- These are learning starters, not a claim of complete official board coverage.

insert into edulock.curriculum_questions
(country_code,curriculum_name,grade_level,age_min,age_max,subject_code,subject_name,language_code,topic,prompt,question_type,options,answer_key,concept_key,source_note)
values
('ZZ','International foundational starter bank',0,3,5,'math','Mathematics','en','Counting','Which number is one more than 2?','multiple_choice','["2","3","4"]','{"answer":"3"}','counting-to-3','Foundational Kindergarten practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',0,3,5,'math','Mathematics','en','Shapes','Which shape is round?','multiple_choice','["Circle","Square","Triangle"]','{"answer":"Circle"}','basic-shapes','Foundational Kindergarten practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',0,3,5,'english','English','en','Letters','Which letter starts the word “apple”?','multiple_choice','["A","B","C"]','{"answer":"A"}','initial-letter-sounds','Foundational Kindergarten practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',0,3,5,'english','English','en','Vocabulary','Which one is a color?','multiple_choice','["Blue","Chair","Run"]','{"answer":"Blue"}','basic-vocabulary','Foundational Kindergarten practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',0,3,5,'science','General Science','en','Nature','Which one grows in soil?','multiple_choice','["Plant","Spoon","Ball"]','{"answer":"Plant"}','nature','Foundational Kindergarten practice; not an official national syllabus.'),
('ZZ','International foundational starter bank',0,3,5,'science','General Science','en','Body','Which body part do we use to see?','multiple_choice','["Eyes","Ears","Feet"]','{"answer":"Eyes"}','five-senses','Foundational Kindergarten practice; not an official national syllabus.'),

('PK','Pakistan localized starter bank',1,5,8,'math','Mathematics','ur','جمع','2 + 1 کا جواب کیا ہے؟','multiple_choice','["2","3","4"]','{"answer":"3"}','addition-within-5','Urdu starter practice; verify against the child local board.'),
('PK','Pakistan localized starter bank',2,6,9,'math','Mathematics','ur','جمع','12 + 5 = ؟','numeric','[]','{"answer":"17"}','addition-within-20','Urdu starter practice; verify against the child local board.'),
('PK','Pakistan localized starter bank',3,7,10,'math','Mathematics','ur','ضرب','4 × 3 = ؟','numeric','[]','{"answer":"12"}','multiplication-facts','Urdu starter practice; verify against the child local board.'),
('PK','Pakistan localized starter bank',1,5,8,'english','English','ur','English letters','انگریزی حروف میں A کے بعد کون سا حرف آتا ہے؟','multiple_choice','["A","B","C"]','{"answer":"B"}','alphabet-order','Urdu-supported English practice; verify against the child local board.'),
('PK','Pakistan localized starter bank',2,6,9,'science','General Science','ur','حواس','ہم کس عضو سے سنتے ہیں؟','multiple_choice','["آنکھ","کان","ہاتھ"]','{"answer":"کان"}','five-senses','Urdu starter practice; verify against the child local board.'),
('PK','Pakistan localized starter bank',3,7,10,'science','General Science','ur','پانی','پانی جم کر کیا بنتا ہے؟','multiple_choice','["بھاپ","برف","ریت"]','{"answer":"برف"}','states-of-matter','Urdu starter practice; verify against the child local board.'),

('PK','Pakistan localized starter bank',1,5,8,'math','Mathematics','sd','جمع','2 + 1 جو جواب ڇا آهي؟','multiple_choice','["2","3","4"]','{"answer":"3"}','addition-within-5','Sindhi starter practice; verify against the child local board.'),
('PK','Pakistan localized starter bank',2,6,9,'math','Mathematics','sd','جمع','12 + 5 = ؟','numeric','[]','{"answer":"17"}','addition-within-20','Sindhi starter practice; verify against the child local board.'),
('PK','Pakistan localized starter bank',3,7,10,'math','Mathematics','sd','ضرب','4 × 3 = ؟','numeric','[]','{"answer":"12"}','multiplication-facts','Sindhi starter practice; verify against the child local board.'),
('PK','Pakistan localized starter bank',1,5,8,'english','English','sd','English letters','انگريزي اکرن ۾ A کان پوءِ ڪهڙو اکر اچي ٿو؟','multiple_choice','["A","B","C"]','{"answer":"B"}','alphabet-order','Sindhi-supported English practice; verify against the child local board.'),
('PK','Pakistan localized starter bank',2,6,9,'science','General Science','sd','حواس','اسان ڪهڙي عضوي سان ٻڌون ٿا؟','multiple_choice','["اکيون","ڪن","هٿ"]','{"answer":"ڪن"}','five-senses','Sindhi starter practice; verify against the child local board.'),
('PK','Pakistan localized starter bank',3,7,10,'science','General Science','sd','پاڻي','پاڻي ڄمي وڃي ته ڇا بڻجي ٿو؟','multiple_choice','["ٻاڦ","برف","ريت"]','{"answer":"برف"}','states-of-matter','Sindhi starter practice; verify against the child local board.');
