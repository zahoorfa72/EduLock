package com.edulock.app

private data class FourDQuestionSeed(val prompt: String, val options: List<String>, val answer: String, val explanation: String)
private fun q(prompt: String, choices: String, answer: String, explanation: String) =
    FourDQuestionSeed(prompt, choices.split("|"), answer, explanation)

object FourDQuestionBanks {
    private fun slug(text: String) = text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    private fun bank(category: String, subject: String, model: String, items: List<FourDQuestionSeed>,
        language: String = "en", country: String = "ZZ", conceptPrefix: String? = null
    ): List<ParentLearningTemplate> = items.mapIndexed { index, item ->
        ParentLearningTemplate(
            title = "$subject • 4D discovery ${index + 1}", category = category, subjectTitle = subject,
            languageCode = language, questionType = "multiple_choice", prompt = item.prompt, options = item.options,
            answer = item.answer,
            conceptKey = conceptPrefix?.let { "$it:${index + 1}" } ?: "4d:$model:${slug(subject)}-${index + 1}",
            solution = item.explanation,
            countryCode = country
        )
    }

    private val maths = listOf(
        q("Rotate the exploded cube. How many square faces does a cube have?", "4|6|8", "6", "A cube has six square faces, twelve edges and eight vertices."),
        q("What is the volume of a cube with side length 3 cm?", "9 cm³|18 cm³|27 cm³", "27 cm³", "Volume = side × side × side = 3 × 3 × 3 = 27 cm³."),
        q("Which solid has two circular faces and one curved surface?", "Cylinder|Cone|Cube", "Cylinder", "A cylinder has two circular ends joined by a curved surface."),
        q("How many vertices does a triangular prism have?", "5|6|8", "6", "A triangular prism has two triangular ends, giving six vertices."),
        q("Which solid has one curved surface and no vertices?", "Sphere|Cube|Triangular prism", "Sphere", "A sphere is round with no flat faces, straight edges or vertices."),
        q("If each cube edge doubles, by what factor does its volume change?", "2|4|8", "8", "Volume scales with the cube of edge length; 2³ = 8."),
        q("Which solid has a circular base that narrows to one vertex?", "Cone|Cylinder|Cuboid", "Cone", "A cone has a circular base and a single apex."),
        q("What is the total number of edges on a cuboid?", "8|10|12", "12", "A cuboid has twelve edges."),
        q("A cuboid is 4 cm long, 3 cm wide and 2 cm high. What is its volume?", "9 cm³|24 cm³|36 cm³", "24 cm³", "Multiply length × width × height: 4 × 3 × 2 = 24 cm³."),
        q("Which view helps you see the front, side and top of a solid at once?", "Isometric view|Single straight line|A dot only", "Isometric view", "An isometric drawing shows three dimensions in one view.")
    )
    private val physics = listOf(
        q("The 4D force model shows a ball. What can an unbalanced force change?", "Speed or direction|Mass always|The object's material automatically", "Speed or direction", "A net force changes velocity: speed, direction, or both."),
        q("Which force usually opposes sliding between two surfaces?", "Friction|Gravity only|Magnetic poles only", "Friction", "Friction acts against relative sliding at contacting surfaces."),
        q("Which unit measures force?", "Newton|Joule|Watt", "Newton", "Force is measured in newtons (N)."),
        q("A 2 kg object accelerates at 3 m/s². What is the net force?", "5 N|6 N|9 N", "6 N", "Using F = ma, 2 kg × 3 m/s² = 6 N."),
        q("Which energy store increases when a ball is lifted higher?", "Gravitational potential energy|Sound energy only|Chemical energy only", "Gravitational potential energy", "An object higher in a gravitational field has more gravitational potential energy."),
        q("What must a simple circuit have for continuous current?", "A closed conducting path and a source|A broken wire only|Only a bulb with no source", "A closed conducting path and a source", "A source and a closed conductive loop let current flow."),
        q("Which poles of two magnets attract?", "Opposite poles|Like poles|All poles repel", "Opposite poles", "North and south poles attract; like poles repel."),
        q("What happens to a lamp if its circuit wire is broken?", "The lamp goes out|It becomes a battery|Current increases forever", "The lamp goes out", "A break opens the circuit and stops continuous current."),
        q("Which object has the greatest inertia?", "A 10 kg cart|A 1 kg ball|A 100 g toy", "A 10 kg cart", "Inertia is resistance to changes in motion and increases with mass."),
        q("Why does a rough surface often slow a sliding block more?", "It creates greater friction|It removes all gravity|It reduces mass to zero", "It creates greater friction", "Rough contacting surfaces commonly produce more friction.")
    )
    private val chemistry = listOf(
        q("Which atomic particle has a negative electric charge?", "Electron|Proton|Neutron", "Electron", "Electrons have negative charge; protons are positive and neutrons have no net charge."),
        q("Where are protons and neutrons found in an atom?", "Nucleus|Outer electron region|Outside the atom only", "Nucleus", "The nucleus contains protons and neutrons."),
        q("What is the chemical formula for water?", "H₂O|CO₂|NaCl", "H₂O", "Each water molecule contains two hydrogen atoms and one oxygen atom."),
        q("Which particle determines the atomic number of an element?", "Proton|Neutron|Electron in a neutral atom only", "Proton", "Atomic number is the number of protons in the nucleus."),
        q("A solution with pH 3 is generally what?", "Acidic|Neutral|Alkaline", "Acidic", "Values below pH 7 are acidic in the usual aqueous pH scale."),
        q("Which is a mixture rather than a single pure substance?", "Air|Pure oxygen gas|Distilled water", "Air", "Air contains several gases mixed together."),
        q("Which process changes liquid water to water vapour?", "Evaporation|Freezing|Condensation", "Evaporation", "Evaporation changes liquid water into water vapour at a surface."),
        q("What does the periodic table organise?", "Chemical elements|Only planets|Only mixtures", "Chemical elements", "Elements are arranged by atomic number and recurring properties."),
        q("What is a molecule?", "Atoms chemically bonded together|A visible grain of sand always|A type of force", "Atoms chemically bonded together", "A molecule is a group of atoms held together by chemical bonds."),
        q("Which observation can indicate a chemical reaction?", "A new substance forms|An object is moved|A shadow changes", "A new substance forms", "Chemical reactions rearrange particles and produce substances with new properties.")
    )
    private val biology = listOf(
        q("Which part of a typical animal cell contains most of its DNA?", "Nucleus|Cell membrane|Cytoplasm only", "Nucleus", "The nucleus stores most genetic material in a typical animal cell."),
        q("What is the cell membrane's main role?", "Controls movement into and out of the cell|Makes sunlight|Stores every bone", "Controls movement into and out of the cell", "The cell membrane acts as a selective boundary."),
        q("Which plant part mainly absorbs water and minerals from soil?", "Roots|Petals|Fruit skin", "Roots", "Roots absorb water and minerals and help anchor the plant."),
        q("What do green plants use light energy for during photosynthesis?", "Making sugars from water and carbon dioxide|Turning oxygen into metal|Making soil", "Making sugars from water and carbon dioxide", "Photosynthesis stores light energy in sugars."),
        q("Which organelle is often described as the cell's energy converter?", "Mitochondrion|Cell wall|Nucleus only", "Mitochondrion", "Mitochondria release usable energy from food molecules."),
        q("What is the basic structural unit of living organisms?", "Cell|Planet|Crystal only", "Cell", "Cells are the basic units of life."),
        q("Which flower part commonly attracts pollinators with colour and scent?", "Petals|Roots|Stem only", "Petals", "Petals often help attract pollinators."),
        q("What carries inherited genetic instructions?", "DNA|Starch|Water", "DNA", "DNA stores genetic information used in growth and inheritance."),
        q("Which system transports blood through the human body?", "Circulatory system|Digestive system|Skeletal system only", "Circulatory system", "The heart and blood vessels circulate blood."),
        q("What is an ecosystem?", "Living organisms interacting with their environment|Only one rock|One animal without surroundings", "Living organisms interacting with their environment", "An ecosystem includes organisms and the non-living environment they interact with.")
    )
    private val generalScience = listOf(
        q("Which process turns water at the surface into vapour?", "Evaporation|Condensation|Freezing", "Evaporation", "Heat energy can change liquid water into water vapour."),
        q("What is water vapour changing into tiny liquid droplets called?", "Condensation|Melting|Sublimation", "Condensation", "Cooling vapour can form liquid droplets, such as in clouds."),
        q("Which material is usually a good electrical conductor?", "Copper|Rubber|Dry wood", "Copper", "Copper allows electric charge to move easily."),
        q("Which is a renewable energy source?", "Sunlight|Coal|Petroleum", "Sunlight", "Solar energy is replenished naturally by the Sun."),
        q("What is the main source of energy driving most weather on Earth?", "The Sun|The Moon's light|Ocean salt", "The Sun", "Uneven solar heating drives much of Earth's weather."),
        q("Which state of matter has a fixed volume but takes its container's shape?", "Liquid|Solid|Gas", "Liquid", "Liquids keep a nearly fixed volume but flow to fit their container."),
        q("What causes a shadow to form?", "An object blocks light|Sound disappears|Air becomes heavier", "An object blocks light", "A shadow forms where light is blocked from reaching a surface."),
        q("Which simple tool measures temperature?", "Thermometer|Compass|Ruler", "Thermometer", "A thermometer measures temperature."),
        q("What is a force that attracts objects with mass called?", "Gravity|Evaporation|Reflection", "Gravity", "Gravity is the attractive interaction between masses."),
        q("Which process returns water from clouds to Earth's surface?", "Precipitation|Condensation only|Evaporation", "Precipitation", "Rain and snow return water to the surface.")
    )
    private val astronomy = listOf(
        q("Which planet is the largest in our Solar System?", "Jupiter|Earth|Mercury", "Jupiter", "Jupiter is the largest planet in the Solar System."),
        q("Which planet has the most conspicuous ring system?", "Saturn|Mars|Mercury", "Saturn", "Saturn's broad icy rings are especially prominent."),
        q("What causes day and night on Earth?", "Earth's rotation|Earth stopping in orbit|The Moon switching off the Sun", "Earth's rotation", "A place faces the Sun during day and turns away during night."),
        q("Which planet is commonly called the Red Planet?", "Mars|Venus|Neptune", "Mars", "Iron-rich dust gives Mars much of its reddish appearance."),
        q("What is at the centre of our Solar System?", "The Sun|Earth|The Moon", "The Sun", "The planets orbit the Sun, which is a star."),
        q("Which planet is known for rotating on a very tilted axis?", "Uranus|Mercury|Mars", "Uranus", "Uranus has an extreme axial tilt."),
        q("What is a natural object that orbits a planet called?", "Moon or natural satellite|Comet tail|Star cluster", "Moon or natural satellite", "A natural satellite orbits a planet or another larger body."),
        q("Why do planets appear bright in the night sky?", "They reflect sunlight|They make their own light like stars|They are all burning", "They reflect sunlight", "Planets are seen mainly by reflected sunlight."),
        q("What keeps planets in orbit around the Sun?", "Gravity and forward motion|A solid rope|Earth's oceans", "Gravity and forward motion", "Gravity bends a planet's path while its motion carries it forward."),
        q("Which planet is closest to the Sun?", "Mercury|Venus|Earth", "Mercury", "Mercury is the innermost planet.")
    )
    private val geography = listOf(
        q("What causes day and night on the rotating Earth model?", "Earth's rotation|The Sun orbiting Earth every day|Clouds covering the Sun", "Earth's rotation", "Earth rotates once approximately every 24 hours."),
        q("Which is the largest ocean?", "Pacific Ocean|Indian Ocean|Arctic Ocean", "Pacific Ocean", "The Pacific Ocean is the largest by area."),
        q("What does a map scale help you calculate?", "Real distance from map distance|A country's age|Air temperature only", "Real distance from map distance", "A map scale relates measured map distances to real-world distances."),
        q("What is the long-term pattern of weather called?", "Climate|A single rain shower|Today's wind only", "Climate", "Climate describes weather patterns over long periods."),
        q("What do contour lines on a topographic map connect?", "Places of equal elevation|Cities of equal population|Only rivers", "Places of equal elevation", "A contour line joins points at the same elevation."),
        q("Which direction is opposite east?", "West|North|South", "West", "West is opposite east on a compass."),
        q("What is the imaginary line around Earth's middle called?", "Equator|Prime Meridian|Tropic of Capricorn only", "Equator", "The equator divides Earth into Northern and Southern Hemispheres."),
        q("Which feature is a large area of elevated, relatively flat land?", "Plateau|Delta only|Trench", "Plateau", "A plateau is an elevated area with a relatively level surface."),
        q("What is a river's source?", "Where it begins|Where it always ends at sea|The widest point only", "Where it begins", "The source is where a river begins."),
        q("What is an island?", "Land surrounded by water|A mountain surrounded only by trees|A dry riverbed", "Land surrounded by water", "An island is land surrounded by water.")
    )
    private val computer = listOf(
        q("Which component supplies energy to a simple electric circuit?", "Battery or power source|A switch alone|A broken wire", "Battery or power source", "A source provides electrical energy to the circuit."),
        q("What does a closed switch do in a simple circuit?", "Completes the conducting path|Removes every electron|Turns copper into plastic", "Completes the conducting path", "A closed switch connects the path so current can flow."),
        q("Which password is generally stronger?", "A long unique passphrase|123456|Your first name only", "A long unique passphrase", "Long unique passwords are harder to guess; do not reuse them."),
        q("What should you do with a suspicious link from a stranger?", "Do not open it; ask a trusted adult|Enter your password to test it|Forward it to everyone", "Do not open it; ask a trusted adult", "Unexpected links can be unsafe; verify them with a trusted adult."),
        q("What does a keyboard mainly do?", "Inputs text and commands|Prints paper|Supplies electricity to the wall", "Inputs text and commands", "A keyboard is an input device."),
        q("What does a computer program contain?", "Instructions for a computer|Only planet pictures|Only electric wires", "Instructions for a computer", "Programs describe steps a computer can execute."),
        q("Which device moves a pointer on many computers?", "Mouse or touchpad|Speaker|Microphone", "Mouse or touchpad", "A mouse or touchpad is a pointing input device."),
        q("Why install software updates from trusted sources?", "They can fix bugs and security weaknesses|They remove every need for passwords|They make every link safe", "They can fix bugs and security weaknesses", "Updates often correct known defects and security issues."),
        q("Which is private information that should not be posted publicly?", "Home address|A public science fact|A generic maths answer", "Home address", "Addresses, passwords and contact details should be protected."),
        q("What is a backup?", "An extra copy for recovery|A screen-brightness setting|A kind of keyboard", "An extra copy for recovery", "Backups help recover data if the original is lost or damaged.")
    )
    private val social = listOf(
        q("What does a compass rose on a map show?", "Directions|Population size|Rainfall only", "Directions", "A compass rose identifies directions such as north, south, east and west."),
        q("What is a community?", "People connected by place or shared interests|Only one building|A type of planet", "People connected by place or shared interests", "Communities form around places, activities or shared needs."),
        q("Why do communities use rules and laws?", "To support safety and fair cooperation|To stop all learning|To make every person identical", "To support safety and fair cooperation", "Rules help people coordinate behaviour and resolve conflicts."),
        q("What is a map legend used for?", "Explaining map symbols|Measuring a person's height|Naming every star", "Explaining map symbols", "A legend explains the symbols and colours on a map."),
        q("Which action is an example of civic responsibility?", "Keeping a shared public place clean|Damaging public property|Ignoring safety rules", "Keeping a shared public place clean", "Civic responsibility includes caring for shared spaces and respecting others."),
        q("What is culture?", "Shared practices, knowledge and traditions|Only a country's border line|Only a weather forecast", "Shared practices, knowledge and traditions", "Culture can include language, food, art, beliefs and customs."),
        q("What does a globe represent?", "Earth's spherical shape|The inside of a computer|Only the Pacific Ocean", "Earth's spherical shape", "A globe is a scale model of Earth's overall shape."),
        q("What is a public service?", "A service provided for community needs|A private password|A planet's orbit", "A service provided for community needs", "Examples include public libraries, sanitation and emergency services."),
        q("Why compare historical sources?", "To check context and different perspectives|To make every source identical|To avoid asking questions", "To check context and different perspectives", "Comparing sources helps identify context, agreement and disagreement."),
        q("What does a country's flag usually act as?", "A national symbol|A weather instrument|A map scale", "A national symbol", "Flags are symbols associated with a country or community.")
    )
    private val health = listOf(
        q("Which habit helps reduce the spread of germs?", "Wash hands with soap|Share dirty tissues|Touch eyes with unwashed hands", "Wash hands with soap", "Handwashing with soap helps remove germs and dirt."),
        q("What is a balanced diet?", "A variety of foods supplying needed nutrients|Only sweets|Only one food every day", "A variety of foods supplying needed nutrients", "Different foods supply different nutrients needed for growth and health."),
        q("What should you do first for a minor thermal burn?", "Cool it under clean cool running water|Rub with soil|Apply an unknown chemical", "Cool it under clean cool running water", "Cool running water is a common first-aid step; seek an adult's help."),
        q("Which organs exchange oxygen and carbon dioxide during breathing?", "Lungs|Stomach|Kidneys", "Lungs", "Gas exchange occurs in tiny air sacs called alveoli in the lungs."),
        q("Why is sleep important for children?", "Growth, memory and recovery|It replaces all food and water|It prevents every illness", "Growth, memory and recovery", "Sleep supports learning, mood, growth and health."),
        q("What should you do when a serious injury happens?", "Tell an adult and contact emergency services|Hide the injury|Try an unknown treatment alone", "Tell an adult and contact emergency services", "Serious injuries need urgent help from a responsible adult and qualified responders."),
        q("Which drink is usually best for everyday hydration?", "Clean drinking water|Energy drink only|Soda instead of water", "Clean drinking water", "Water is a suitable everyday drink for hydration."),
        q("What helps protect eyes during long screen use?", "Take breaks and look away regularly|Hold the screen very close forever|Never blink", "Take breaks and look away regularly", "Regular breaks can ease eye strain and encourage healthy screen habits."),
        q("Why should medicine only be taken with adult guidance?", "The wrong medicine or dose can be harmful|Every medicine is candy|More medicine always works better", "The wrong medicine or dose can be harmful", "A responsible adult and health professional can help ensure medicine is appropriate."),
        q("Which activity supports physical fitness?", "Age-appropriate active play|Sitting all day without breaks|Avoiding movement completely", "Age-appropriate active play", "Regular age-appropriate movement supports strength, balance and well-being.")
    )
    private val art = listOf(
        q("Which paint colours are traditionally taught as primary colours?", "Red, yellow and blue|Green, orange and purple|Black, grey and brown", "Red, yellow and blue", "Traditional beginner paint instruction often uses red, yellow and blue as primary colours."),
        q("In basic paint mixing, what colour do red and yellow make?", "Orange|Green|Blue", "Orange", "Red and yellow paint commonly mix to make orange."),
        q("What helps a sphere drawing look three-dimensional?", "Highlights and shadows|One flat outline only|Removing all contrast", "Highlights and shadows", "Light and shadow suggest a rounded surface and light direction."),
        q("What is perspective used for in drawing?", "Suggesting depth and distance|Measuring sound|Mixing water and oil", "Suggesting depth and distance", "Perspective cues help flat drawings suggest three-dimensional space."),
        q("Which shape has three straight sides?", "Triangle|Circle|Oval", "Triangle", "A triangle has three straight sides and three vertices."),
        q("What is a complementary colour pair in a common colour wheel example?", "Blue and orange|Blue and blue|Yellow and yellow", "Blue and orange", "Blue and orange are opposite on a common traditional colour wheel."),
        q("What is a sketch?", "A quick drawing to explore an idea|A chemical formula only|A weather map only", "A quick drawing to explore an idea", "Sketching helps plan shapes, layout and ideas before detailed work."),
        q("What does texture in art describe?", "How a surface looks or feels|Only an object's weight|Only paint's price", "How a surface looks or feels", "Artists use visual texture to suggest smoothness, roughness and other qualities."),
        q("What is symmetry?", "Matching parts across a line or around a centre|Random unrelated marks|A type of glue", "Matching parts across a line or around a centre", "Symmetry describes matching parts arranged around a line or centre."),
        q("Why do artists make a colour palette?", "To plan colours that work together|To erase every shape|To measure time", "To plan colours that work together", "A palette helps artists choose a harmonious or purposeful set of colours.")
    )
    private val english = listOf(
        q("Which word is a noun?", "Garden|Quickly|Bright", "Garden", "A noun names a person, place, thing or idea."),
        q("Which word is a verb in 'The child paints a flower'?", "Paints|Child|Flower", "Paints", "A verb expresses an action or state."),
        q("Choose the opposite of 'bright'.", "Dark|Shiny|Light", "Dark", "Dark is a common antonym of bright."),
        q("Which sentence has a question mark?", "Where is the blue book?|The book is blue.|Please bring the book.", "Where is the blue book?", "A direct question normally ends with a question mark."),
        q("Which word describes a noun?", "Adjective|Verb|Preposition only", "Adjective", "Adjectives describe nouns or pronouns."),
        q("Which word is a synonym of 'happy'?", "Glad|Angry|Sleepy", "Glad", "Glad and happy have similar meanings."),
        q("Which plural form is correct for 'box'?", "Boxes|Boxs|Boxies", "Boxes", "Many nouns ending in x form the plural with -es."),
        q("Which word rhymes with 'star'?", "Car|Tree|Book", "Car", "Star and car share a similar ending sound."),
        q("What is the main idea of a paragraph?", "The central point it communicates|The last letter only|A page number", "The central point it communicates", "The main idea is the key message supported by details."),
        q("Why reread a sentence after writing it?", "To check meaning, spelling and punctuation|To remove all meaning|To make every word uppercase", "To check meaning, spelling and punctuation", "Reviewing helps improve clarity and catch errors.")
    )
    private val urdu = listOf(
        q("لفظ 'خوش' کا عام متضاد کیا ہے؟", "اداس|تیز|کتاب", "اداس", "خوش کا عام متضاد اداس ہے۔"),
        q("لفظ 'دن' کا متضاد کیا ہے؟", "رات|صبح|روشنی", "رات", "دن اور رات ایک دوسرے کے متضاد ہیں۔"),
        q("ان میں سے اسم کون سا ہے؟", "کتاب|تیزی سے|خوبصورت", "کتاب", "اسم کسی شخص، جگہ، چیز یا خیال کا نام ہوتا ہے۔"),
        q("جملے کے آخر میں سوال کے لیے کون سی علامت آتی ہے؟", "؟|،|۔", "؟", "اردو میں سوالیہ جملے کے آخر میں سوالیہ نشان آتا ہے۔"),
        q("لفظ 'بڑا' کا متضاد کیا ہے؟", "چھوٹا|اونچا|لمبا", "چھوٹا", "بڑا اور چھوٹا متضاد الفاظ ہیں۔"),
        q("ان میں سے پھول کا نام کون سا ہے؟", "گلاب|میز|قلم", "گلاب", "گلاب ایک پھول ہے۔"),
        q("درست جمع کون سی ہے: کتاب؟", "کتابیں|کتابون|کتابا", "کتابیں", "کتاب کی عام جمع کتابیں ہے۔"),
        q("جملہ 'بچہ پڑھتا ہے' میں فعل کون سا ہے؟", "پڑھتا ہے|بچہ|ہے", "پڑھتا ہے", "فعل کام یا حالت کو ظاہر کرتا ہے۔"),
        q("لفظ 'پانی' کس کے لیے ہوتا ہے؟", "پینے کے مائع کے لیے|ایک رنگ کے لیے|ایک پہاڑ کے لیے", "پینے کے مائع کے لیے", "پانی زندگی کے لیے ضروری مائع ہے۔"),
        q("لفظ 'سورج' کس چیز کا نام ہے؟", "ایک ستارہ|ایک دریا|ایک شہر", "ایک ستارہ", "سورج ہمارے نظامِ شمسی کا ستارہ ہے۔")
    )
    private val sindhi = listOf(
        q("لفظ 'خوش' جو ابتڙ ڇا آهي؟", "اداس|تيز|ڪتاب", "اداس", "خوش جو عام ابتڙ اداس آهي۔"),
        q("انهن مان اسم ڪهڙو آهي؟", "ڪتاب|تيزيءَ سان|سهڻو", "ڪتاب", "اسم ڪنهن ماڻهو، جاءِ، شيءِ يا خيال جو نالو آهي۔"),
        q("سوال جي آخر ۾ ڪهڙي نشاني اچي ٿي؟", "؟|،|۔", "؟", "سواليا جملي جي آخر ۾ سواليا نشان لکبو آهي۔"),
        q("لفظ 'وڏو' جو ابتڙ ڇا آهي؟", "ننڍو|ڊگهو|اوچو", "ننڍو", "وڏو ۽ ننڍو ابتڙ لفظ آهن۔"),
        q("انهن مان گل ڪهڙو آهي؟", "گلاب|ميز|قلم", "گلاب", "گلاب هڪ گل آهي۔"),
        q("جملو 'ٻار پڙهي ٿو' ۾ فعل ڪهڙو آهي؟", "پڙهي ٿو|ٻار|۾", "پڙهي ٿو", "فعل ڪم يا حالت ٻڌائي ٿو۔"),
        q("پاڻي ڇو ضروري آهي؟", "زندگي لاءِ|صرف رنگ لاءِ|صرف آواز لاءِ", "زندگي لاءِ", "پاڻي ماڻهن، جانورن ۽ ٻوٽن لاءِ اهم آهي۔"),
        q("سج ڇا آهي؟", "ستارو|درياهه|شهر", "ستارو", "سج اسان جي نظامِ شمسي جو ستارو آهي۔"),
        q("ڪهڙي شيءِ پڙهڻ لاءِ استعمال ٿئي ٿي؟", "ڪتاب|جوتو|چمچو", "ڪتاب", "ڪتاب پڙهڻ ۽ سکڻ لاءِ هوندو آهي۔"),
        q("لفظ 'رات' جو ابتڙ ڇا آهي؟", "ڏينهن|اونداهي|آرام", "ڏينهن", "ڏينهن ۽ رات هڪ ٻئي جا ابتڙ آهن۔")
    )
    private val hindi = listOf(
        q("'खुश' का विलोम शब्द क्या है?", "उदास|तेज़|किताब", "उदास", "खुश का सामान्य विलोम उदास है।"),
        q("इनमें से संज्ञा कौन-सी है?", "किताब|जल्दी|सुंदर", "किताब", "संज्ञा किसी व्यक्ति, स्थान, वस्तु या विचार का नाम है।"),
        q("प्रश्न के अंत में कौन-सा चिह्न आता है?", "？|,|।", "？", "हिंदी में प्रश्न के अंत में प्रश्नवाचक चिह्न लगाया जाता है।"),
        q("'बड़ा' का विलोम क्या है?", "छोटा|लंबा|ऊँचा", "छोटा", "बड़ा और छोटा विलोम शब्द हैं।"),
        q("इनमें से फूल कौन-सा है?", "गुलाब|मेज़|कलम", "गुलाब", "गुलाब एक फूल है।"),
        q("'बच्चा पढ़ता है' में क्रिया कौन-सी है?", "पढ़ता है|बच्चा|है", "पढ़ता है", "क्रिया कार्य या अवस्था बताती है।"),
        q("पानी क्यों ज़रूरी है?", "जीवन के लिए|सिर्फ रंग के लिए|सिर्फ आवाज़ के लिए", "जीवन के लिए", "पानी मनुष्यों, जानवरों और पौधों के लिए आवश्यक है।"),
        q("सूर्य क्या है?", "एक तारा|एक नदी|एक शहर", "एक तारा", "सूर्य हमारे सौरमंडल का तारा है।"),
        q("पढ़ने के लिए किसका उपयोग होता है?", "किताब|जूता|चम्मच", "किताब", "किताब पढ़ने और सीखने के लिए होती है।"),
        q("'दिन' का विलोम क्या है?", "रात|सुबह|धूप", "रात", "दिन और रात विपरीत शब्द हैं।")
    )

    private val religions = listOf(
        Triple("islamic", "Islamic Studies", listOf(
            q("In Islam, which book is regarded as the sacred scripture?", "Qur'an|Torah|Tripitaka", "Qur'an", "The Qur'an is the sacred scripture of Islam."),
            q("Which month is associated with fasting from dawn to sunset by many Muslims?", "Ramadan|Shawwal|Muharram", "Ramadan", "Ramadan is the month in which Muslims commonly observe fasting."),
            q("What is zakat in Islam commonly understood as?", "Obligatory charitable giving|A planet|A colour", "Obligatory charitable giving", "Zakat is a form of obligatory giving to eligible recipients."),
            q("Which city is the direction of prayer associated with?", "Makkah|Delhi|Rome", "Makkah", "Muslims face the Kaaba in Makkah during salah."),
            q("What is the name for the daily ritual prayer in Islam?", "Salah|Langar|Shabbat", "Salah", "Salah is the daily ritual prayer in Islam."),
            q("Which celebration marks the end of Ramadan fasting?", "Eid al-Fitr|Diwali|Vesak", "Eid al-Fitr", "Eid al-Fitr is celebrated at the end of Ramadan."),
            q("What does 'salam' commonly mean in an Islamic greeting?", "Peace|Mountain|Book", "Peace", "As-salamu alaykum is a greeting wishing peace."),
            q("Which place of worship is associated with Muslims?", "Mosque|Church|Synagogue", "Mosque", "A mosque is a place where Muslims gather for prayer and community activities."),
            q("What is wudu before prayer?", "A ritual washing|A type of star|A school grade", "A ritual washing", "Wudu is ritual washing performed before prayer in many circumstances."),
            q("Which value is encouraged in many Islamic teachings?", "Charity and care for others|Wastefulness|Dishonesty", "Charity and care for others", "Charity, honesty and care for others are important themes in Islamic teachings.")
        )),
        Triple("hindu", "Hindu Studies", listOf(
            q("Which festival is widely known as the festival of lights in Hindu traditions?", "Diwali|Vaisakhi|Vesak", "Diwali", "Diwali is celebrated in diverse ways across Hindu communities and other traditions."),
            q("What does the Sanskrit term ahimsa commonly mean?", "Non-harm or non-violence|A planet|A musical note", "Non-harm or non-violence", "Ahimsa is an important ethical idea in several Indian traditions."),
            q("Which ancient texts are among Hinduism's sacred literature?", "Vedas|Modern maps|Weather charts", "Vedas", "The Vedas are among the most ancient and important Hindu scriptures."),
            q("What does yoga commonly include?", "Practices involving body, breath and attention|Only a circuit|Only a fruit", "Practices involving body, breath and attention", "Yoga traditions include diverse physical, breathing and contemplative practices."),
            q("Which river is considered sacred by many Hindus?", "Ganga|Amazon|Nile only", "Ganga", "The Ganga has deep spiritual and cultural importance for many Hindus."),
            q("What is puja?", "A form of worship or devotional offering|A planet's ring|A maths operator", "A form of worship or devotional offering", "Puja is a form of worship practised in many Hindu traditions."),
            q("Which epic includes the story of Rama?", "Ramayana|Odyssey|A modern atlas", "Ramayana", "The Ramayana is an epic central to many Hindu cultural traditions."),
            q("Which epic includes the Bhagavad Gita?", "Mahabharata|Iliad|Tripitaka", "Mahabharata", "The Bhagavad Gita appears within the Mahabharata."),
            q("What is prasad in many Hindu traditions?", "Food or offering shared after worship|An orbit path|A compass", "Food or offering shared after worship", "Prasad is an offering shared as blessed food in many traditions."),
            q("Which value is commonly associated with ahimsa?", "Avoiding harm|Breaking things for fun|Ignoring living beings", "Avoiding harm", "Ahimsa encourages non-harm and care toward living beings.")
        )),
        Triple("christian", "Christian Studies", listOf(
            q("Which book is the central scripture of Christianity?", "The Bible|The Vedas|The Tripitaka", "The Bible", "The Bible is the central collection of scriptures in Christianity."),
            q("What does Easter commemorate in Christian belief?", "The resurrection of Jesus|The start of the school year|The winter solstice", "The resurrection of Jesus", "Easter commemorates the resurrection of Jesus in Christian belief."),
            q("Which celebration commemorates the birth of Jesus?", "Christmas|Eid al-Fitr|Vesak", "Christmas", "Christmas celebrates the birth of Jesus in Christian tradition."),
            q("What is a common Christian place of worship called?", "Church|Mosque|Gurdwara", "Church", "Christians often gather for worship in churches."),
            q("Which value is central to many Christian teachings?", "Love of neighbour|Cruelty|Dishonesty", "Love of neighbour", "Love, compassion and service are important themes in many Christian teachings."),
            q("What is a parable?", "A short story used to teach a lesson|A planet|A colour wheel", "A short story used to teach a lesson", "Parables use stories to communicate ideas or moral lessons."),
            q("What is a Gospel in the New Testament?", "An account of Jesus' life and teaching|A country border|A circuit part", "An account of Jesus' life and teaching", "The four canonical Gospels describe Jesus' life and teachings."),
            q("What is prayer commonly used for in Christian life?", "Worship, thanks or seeking guidance|Measuring distance|Mixing paint", "Worship, thanks or seeking guidance", "Prayer takes different forms across Christian traditions."),
            q("Which symbol is strongly associated with Christianity?", "Cross|Crescent only|Dharma wheel only", "Cross", "The cross is a widely used symbol in Christianity."),
            q("What does forgiveness mean?", "Choosing to release resentment and seek peace|Pretending harm is acceptable|Forgetting safety rules", "Choosing to release resentment and seek peace", "Forgiveness is important in many Christian teachings, while safety and accountability still matter.")
        )),
        Triple("sikh", "Sikh Studies", listOf(
            q("Which scripture is central in Sikhism?", "Guru Granth Sahib|The Bible|The Tripitaka", "Guru Granth Sahib", "The Guru Granth Sahib is the central scripture in Sikhism."),
            q("What is langar in Sikh tradition?", "A community meal served freely|A planet's moon|A maths symbol", "A community meal served freely", "Langar is a free community kitchen or meal emphasising equality and service."),
            q("Who founded Sikhism?", "Guru Nanak|The Buddha|Confucius", "Guru Nanak", "Guru Nanak is the founder of Sikhism."),
            q("What does seva mean in Sikh tradition?", "Selfless service|A mountain range|A colour", "Selfless service", "Seva is selfless service to others."),
            q("Which place of worship is associated with Sikh communities?", "Gurdwara|Synagogue|Colosseum", "Gurdwara", "A gurdwara is a Sikh place of worship and community."),
            q("Which festival is especially important in Sikh history and marks the formation of the Khalsa?", "Vaisakhi|Halloween|Vesak", "Vaisakhi", "Vaisakhi commemorates the formation of the Khalsa in 1699."),
            q("What does equality mean in Sikh community practice?", "Every person deserves dignity|Only rich people may eat|People should never help", "Every person deserves dignity", "Equality and human dignity are central themes in Sikh teachings."),
            q("Which city is associated with the Golden Temple?", "Amritsar|Karachi|Kathmandu", "Amritsar", "The Harmandir Sahib, often called the Golden Temple, is in Amritsar."),
            q("What does sharing langar express?", "Service and equality|Competition for food|Keeping people apart", "Service and equality", "Langar welcomes people to share a meal regardless of background."),
            q("Which value is encouraged through honest work and sharing?", "Responsibility and generosity|Wastefulness|Dishonesty", "Responsibility and generosity", "Honest work, sharing and service are important Sikh values.")
        )),
        Triple("buddhist", "Buddhist Studies", listOf(
            q("Whose teachings are foundational to Buddhism?", "The Buddha|Guru Nanak|Confucius only", "The Buddha", "Buddhist traditions are based on the teachings of the Buddha."),
            q("Which practice is used by many Buddhists to develop awareness?", "Meditation|Drawing orbit lines|Circuit building", "Meditation", "Many Buddhist traditions include meditation, with methods varying by school."),
            q("What is compassion?", "Concern for others' suffering and well-being|Ignoring others|A planet", "Concern for others' suffering and well-being", "Compassion means caring about suffering and wishing to help."),
            q("What does Vesak commonly commemorate?", "Important events in the Buddha's life|The start of winter everywhere|The compass invention", "Important events in the Buddha's life", "Vesak is an important Buddhist festival linked to events in the Buddha's life."),
            q("What is the Dharma in Buddhist traditions?", "The Buddha's teaching|A type of rock|A computer cable", "The Buddha's teaching", "Dharma can refer to the Buddha's teachings and the path they describe."),
            q("What is mindfulness?", "Paying attention to present experience|Never noticing feelings|A type of fuel", "Paying attention to present experience", "Mindfulness is attentive awareness of present experience."),
            q("Which object is often used as a symbol of awakening in Buddhist art?", "Lotus|Traffic light|Compass needle", "Lotus", "The lotus is a common symbol in Buddhist art and many Asian traditions."),
            q("What does generosity mean?", "Sharing or giving to help others|Keeping everything unfairly|Damaging shared things", "Sharing or giving to help others", "Generosity is encouraged in many Buddhist communities."),
            q("What is the Sangha commonly understood as?", "The Buddhist community|A planet|A drawing tool", "The Buddhist community", "Sangha commonly refers to a Buddhist community, often especially the monastic community."),
            q("Why do some Buddhist teachings encourage non-harm?", "To reduce suffering|To encourage cruelty|To avoid learning", "To reduce suffering", "Reducing harm and suffering is an important theme in many Buddhist teachings.")
        )),
        Triple("jewish", "Jewish Studies", listOf(
            q("Which text is central to the Jewish Torah?", "The Five Books of Moses|The Qur'an|The Tripitaka", "The Five Books of Moses", "The Torah especially refers to the Five Books of Moses and is central to Judaism."),
            q("What is Shabbat in Jewish tradition?", "A weekly day of rest and observance|A planet|A colour technique", "A weekly day of rest and observance", "Shabbat is the Jewish weekly day of rest and observance."),
            q("Which festival is associated with the story of the Exodus?", "Passover|Diwali|Vesak", "Passover", "Passover commemorates the Exodus story in Jewish tradition."),
            q("What is a synagogue?", "A Jewish place of worship and community|A planet|A type of circuit", "A Jewish place of worship and community", "A synagogue is used for prayer, study and community gathering."),
            q("What does tzedakah commonly relate to?", "Giving and justice|A star's orbit|A colour palette", "Giving and justice", "Tzedakah is associated with charitable giving and justice."),
            q("Which language has a central role in many traditional Jewish prayers and texts?", "Hebrew|Only Latin|Only Japanese", "Hebrew", "Hebrew has a central role in many Jewish prayers and texts."),
            q("What is Hanukkah?", "A Jewish festival of lights|A type of map|A school code", "A Jewish festival of lights", "Hanukkah is an eight-day Jewish festival often called the festival of lights."),
            q("What is a mezuzah traditionally associated with?", "A Jewish home doorway|A telescope|A planet", "A Jewish home doorway", "A mezuzah case containing scriptural text is traditionally affixed to doorposts."),
            q("What is a common purpose of studying Torah?", "Learning scripture, interpretation and tradition|Measuring rainfall only|Practising a password", "Learning scripture, interpretation and tradition", "Torah study is an important part of Jewish religious and cultural life."),
            q("What does community support help people do?", "Care for one another|Avoid all responsibility|Prevent learning", "Care for one another", "Community care is important in many religious and cultural traditions.")
        )),
        Triple("jain", "Jain Studies", listOf(
            q("Which principle is especially important in Jain traditions?", "Ahimsa or non-violence|Wastefulness|Cruelty", "Ahimsa or non-violence", "Ahimsa and care toward living beings are central Jain principles."),
            q("What is an important Jain practice related to truth?", "Satya|Ignoring truth|Breaking objects", "Satya", "Satya means truthfulness and is one of the Jain ethical vows."),
            q("What does aparigraha commonly encourage?", "Non-possessiveness and limiting attachment|Collecting everything|Damaging living things", "Non-possessiveness and limiting attachment", "Aparigraha encourages limiting attachment and possessiveness."),
            q("What is Paryushan?", "An important Jain period of reflection and observance|A planet|A type of map", "An important Jain period of reflection and observance", "Paryushan is a significant period in Jain religious life."),
            q("Why is carefulness toward living beings valued in Jainism?", "To practise non-harm|To cause more harm|To avoid kindness", "To practise non-harm", "Jain ethics place strong emphasis on avoiding harm to living beings."),
            q("What does self-discipline help a person practise?", "Thoughtful choices and restraint|More waste|Dishonesty", "Thoughtful choices and restraint", "Self-discipline supports ethical decisions and mindful habits."),
            q("Which symbol is associated with Jain teachings about non-violence?", "A hand with a wheel|A traffic light|A compass", "A hand with a wheel", "A hand with a wheel is a familiar Jain symbol associated with ahimsa."),
            q("What is one aim of meditation in Jain practices?", "Calm attention and self-reflection|Making planets brighter|Changing the weather", "Calm attention and self-reflection", "Meditative practices can support self-awareness and spiritual reflection."),
            q("What does forgiveness help support?", "Repairing relationships and reducing anger|Making harm acceptable|Ignoring everyone", "Repairing relationships and reducing anger", "Forgiveness is important in Jain observances such as Kshamavani."),
            q("What is an example of a non-harmful everyday choice?", "Handle small living creatures carefully|Destroy a nest for fun|Waste food deliberately", "Handle small living creatures carefully", "Careful treatment of living beings expresses ahimsa.")
        )),
        Triple("other", "World Religions & Ethics", listOf(
            q("Why compare religious and cultural traditions respectfully?", "To understand similarities and differences fairly|To mock unfamiliar traditions|To assume everyone believes the same thing", "To understand similarities and differences fairly", "Respectful comparison helps learners understand diversity without stereotyping."),
            q("What is a sacred text?", "A text regarded as holy within a tradition|A weather report only|A blank page", "A text regarded as holy within a tradition", "Different traditions recognise different texts as sacred."),
            q("What is a place of worship?", "A place a community uses for religious observance|Only a shopping centre|A planet", "A place a community uses for religious observance", "Places of worship vary across traditions and communities."),
            q("What is a religious festival?", "A special observance celebrated by a community|A scientific unit|A type of rock", "A special observance celebrated by a community", "Festivals can include prayer, family gatherings, food and service."),
            q("Why can people within one religion have different customs?", "Traditions vary across regions and communities|Every family is identical|Culture never changes", "Traditions vary across regions and communities", "Religious practice is diverse and shaped by history, culture and community."),
            q("What is empathy?", "Trying to understand another person's experience|Making fun of others|Ignoring every difference", "Trying to understand another person's experience", "Empathy helps people understand and respond to others thoughtfully."),
            q("What is interfaith dialogue?", "Respectful conversation between faith communities|A planet|A coding error", "Respectful conversation between faith communities", "Interfaith dialogue can build understanding between communities."),
            q("What is a shared ethical value found in many traditions?", "Care for others|Cruelty|Dishonesty", "Care for others", "Many traditions discuss compassion, honesty, generosity or care, though beliefs vary."),
            q("Why ask a trusted parent or teacher about a tradition?", "They can explain it in a suitable context|Every online rumour is correct|Questions should never be asked", "They can explain it in a suitable context", "Trusted adults can help explain ideas accurately and respectfully."),
            q("What should a lesson avoid when discussing religion?", "Claiming everyone in a country follows one belief|Respectfully describing traditions|Using accurate sources", "Claiming everyone in a country follows one belief", "Country and family identities are diverse; religious affiliation should not be assumed.")
        ))
    )

    val all: List<ParentLearningTemplate> = buildList {
        addAll(bank("Mathematics", "Mathematics", "geometry", maths))
        addAll(bank("Physics", "Physics", "force", physics))
        addAll(bank("Chemistry", "Chemistry", "atom", chemistry))
        addAll(bank("Biology", "Biology", "cell", biology))
        addAll(bank("General Science", "General Science", "water-cycle", generalScience))
        addAll(bank("Astronomy", "Astronomy", "solar-system", astronomy))
        addAll(bank("Geography", "Geography", "earth", geography))
        addAll(bank("Social Studies", "Social Studies", "earth", social))
        addAll(bank("Computer Skills", "Computer Skills", "circuit", computer))
        addAll(bank("Health & Safety", "Health & Safety", "cell", health))
        addAll(bank("Art & Design", "Art & Design", "flower", art))
        addAll(bank("Drawing & Colour", "Drawing & Colour", "flower", art))
        addAll(bank("English", "English", "book", english))
        addAll(bank("Urdu", "Urdu", "book", urdu, language = "ur", country = "PK"))
        addAll(bank("Sindhi", "Sindhi", "book", sindhi, language = "sd", country = "PK"))
        addAll(bank("Hindi", "Hindi", "book", hindi, language = "hi", country = "IN"))
        religions.forEach { (track, subject, seeds) ->
            addAll(bank("Religious Education", subject, "book", seeds, conceptPrefix = "4d:book:faith:$track"))
        }
    }
}
