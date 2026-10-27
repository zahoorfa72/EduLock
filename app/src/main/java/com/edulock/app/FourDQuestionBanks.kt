package com.edulock.app

private data class FourDQuestionSeed(val prompt: String, val options: List<String>, val answer: String, val explanation: String)
private fun q(prompt: String, choices: String, answer: String, explanation: String) =
    FourDQuestionSeed(prompt, choices.split("|"), answer, explanation)

object FourDQuestionBanks {
    private fun slug(text: String) = text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    private fun bank(category: String, subject: String, model: String, items: List<FourDQuestionSeed>,
        language: String = "en", country: String = "ZZ", conceptPrefix: String? = null
    ): List<ParentLearningTemplate> = items.mapIndexed { index, item ->
        val questionModel = modelForQuestion(subject, item.prompt, model, index)
        val track = conceptPrefix?.substringAfterLast(':')
        val conceptKey = if (track != null && conceptPrefix.startsWith("4d:book:faith:")) {
            "4d:$questionModel:faith:$track:${index + 1}"
        } else {
            "4d:$questionModel:${slug(subject)}-${index + 1}"
        }
        ParentLearningTemplate(
            title = "$subject • 4D discovery ${index + 1}", category = category, subjectTitle = subject,
            languageCode = language, questionType = "multiple_choice", prompt = item.prompt, options = item.options,
            answer = item.answer, conceptKey = conceptKey, solution = item.explanation, countryCode = country
        )
    }

    // Use a different model/animation for each generated question, while prioritising
    // the concept mentioned in its prompt. This keeps each preview tied to its question.
    private fun modelForQuestion(subject: String, prompt: String, fallback: String, index: Int): String {
        val p = prompt.lowercase()
        val n = subject.lowercase()
        return when (n) {
            "mathematics" -> when {
                "tesseract" in p || "four-dimensional" in p -> "tesseract"
                "cylinder" in p -> "cylinder"
                "prism" in p || "triangle" in p -> "prism"
                "sphere" in p || "round like a ball" in p -> "sphere"
                "cone" in p -> "cone"
                "cuboid" in p || "rectangular box" in p -> "cuboid"
                "volume" in p || "edge becomes twice" in p -> "cube-volume"
                "box" in p || "space does it hold" in p -> "box-volume"
                "add" in p || "subtract" in p || "number" in p || "count" in p -> "number-cubes"
                "pattern" in p -> "pattern-blocks"
                else -> listOf("geometry", "cube-volume", "cylinder", "prism", "sphere", "cube-scale", "cone", "cuboid", "box-volume", "tesseract")[index % 10]
            }
            "physics" -> when {
                "broken wire" in p || "goes out" in p -> "broken-circuit"
                "circuit" in p || "current" in p || "lamp" in p || "switch" in p || "wire" in p -> "circuit"
                "magnet" in p || "poles" in p -> "magnet"
                "inertia" in p || "greatest inertia" in p -> "inertia"
                "rough surface" in p -> "rough-surface"
                "friction" in p -> "friction"
                "unit measures force" in p || "unit measures" in p -> "force-meter"
                "accelerates" in p || "acceleration" in p -> "motion-cart"
                "energy store" in p || "lifted higher" in p -> "energy-ball"
                "force" in p || "motion" in p -> "force"
                else -> listOf("force", "friction", "force-meter", "motion-cart", "energy-ball", "circuit", "magnet", "broken-circuit", "inertia", "rough-surface")[index % 10]
            }
            "chemistry" -> when {
                "negative electric charge" in p || "negative charge" in p || "electron" in p -> "atom"
                "protons and neutrons" in p || "nucleus" in p -> "nucleus"
                "water molecule" in p || "h₂o" in p -> "molecule"
                "atomic number" in p || "particle determines" in p || "proton" in p -> "proton"
                "ph " in p || "ph" in p || "acidic" in p || "alkaline" in p -> "acid-base"
                "mixture" in p || "air contains" in p || "gases" in p -> "gas-mixture"
                "evaporation" in p || "water vapour" in p || "water vapor" in p -> "water-cycle"
                "periodic table" in p || "chemical elements" in p -> "periodic-table"
                "molecule" in p || "chemical bond" in p -> "molecule-vibration"
                "reaction" in p || "new substance" in p || "substance forms" in p || "reactant" in p -> "chemistry-lab"
                else -> listOf("atom", "nucleus", "molecule", "proton", "acid-base", "gas-mixture", "water-cycle", "periodic-table", "molecule-vibration", "chemistry-lab")[index % 10]
            }
            "biology" -> when {
                "membrane" in p -> "membrane"
                "root" in p || "soil" in p -> "roots"
                "photosynthesis" in p || "sunlight" in p -> "photosynthesis"
                "mitochond" in p || "energy converter" in p -> "mitochondria"
                "flower" in p || "pollinator" in p || "petal" in p -> "flower"
                "dna" in p || "genetic" in p || "inherited" in p -> "dna"
                "blood" in p || "circulatory" in p || "heart" in p -> "heart"
                "ecosystem" in p -> "ecosystem"
                "cell" in p || "living organisms" in p -> "cell"
                else -> listOf("cell", "membrane", "roots", "photosynthesis", "mitochondria", "cell-growth", "flower", "dna", "heart", "ecosystem")[index % 10]
            }
            "general science" -> when {
                "evaporation" in p || "vapour" in p || "vapor" in p -> "water-cycle"
                "condensation" in p || "cloud" in p -> "condensation"
                "conductor" in p || "electrical" in p || "copper" in p -> "circuit"
                "renewable" in p || "sunlight" in p -> "solar-energy"
                "sun" in p || "weather" in p -> "solar-system"
                "state of matter" in p || "fixed volume" in p -> "matter-states"
                "shadow" in p || "blocks light" in p -> "shadow-lab"
                "temperature" in p || "thermometer" in p -> "thermometer"
                "gravity" in p || "force" in p -> "force"
                "precipitation" in p || "returns water" in p -> "water-cycle"
                else -> listOf("water-cycle", "condensation", "circuit", "solar-energy", "solar-system", "matter-states", "shadow-lab", "thermometer", "force", "precipitation")[index % 10]
            }
            "astronomy" -> listOf("planet-size", "rings", "day-night", "mars", "solar-system", "axial-tilt", "moon", "planet-light", "orbit", "mercury")[index % 10]
            "geography" -> listOf("earth", "ocean", "map-scale", "climate", "contour-map", "compass", "equator", "plateau", "river", "island")[index % 10]
            "social studies" -> listOf("compass", "community", "city-rules", "map", "public-space", "culture", "globe", "public-service", "history-sources", "flag")[index % 10]
            "computer skills" -> listOf("power-source", "switch", "password-lock", "suspicious-link", "keyboard", "program-flow", "mouse", "software-update", "privacy-shield", "backup-drive")[index % 10]
            "health & safety" -> listOf("handwashing", "food-pyramid", "firstaid-burn", "lungs", "sleep-cycle", "emergency-kit", "water", "eye-break", "medicine-safety", "exercise")[index % 10]
            "art & design" -> listOf("primary-palette", "colour-mixing", "sphere", "perspective-grid", "triangle", "colour-wheel", "sketch", "texture", "symmetry", "palette")[index % 10]
            "drawing & colour" -> listOf("sketch", "contour", "perspective", "pencil", "hatching", "negative-space", "horizon", "shading", "proportions", "overlap")[index % 10]
            "english", "urdu", "sindhi", "hindi" -> listOf("story-book", "grammar", "word-puzzle", "sentence-builder", "punctuation", "reading-trail", "rhyme", "vocabulary", "dialogue", "dictionary")[index % 10]
            "islamic studies", "hindu studies", "christian studies", "sikh studies", "buddhist studies", "jewish studies", "jain studies", "world religions & ethics" ->
                listOf("faith-scroll", "faith-community", "faith-festival", "faith-story", "faith-kindness", "faith-symbol", "faith-practice", "faith-service", "faith-value", "faith-choice")[index % 10]
            else -> fallback
        }
    }

    // Kindergarten–Class 2 gets concrete, short-reading questions. Class 3–5 uses
    // the primary bank below. Parents can still create/edit questions for their own syllabus.
    private fun earlyGradeSeeds(subjectTitle: String): List<FourDQuestionSeed> = when (subjectTitle.lowercase()) {
        "mathematics" -> listOf(
            q("🔷 Which shape is round like a ball?", "Sphere|Cube|Triangle", "Sphere", "A sphere is round all around and has no corners."),
            q("🔺 How many sides has a triangle?", "3|2|4", "3", "A triangle has three straight sides."),
            q("🍎 What is 2 + 1?", "2|3|4", "3", "Two objects plus one more make three."),
            q("📦 Which solid has six square faces?", "Cube|Cone|Sphere", "Cube", "A cube has six square faces."),
            q("🧊 Which solid has two flat circular ends?", "Cylinder|Sphere|Pyramid", "Cylinder", "A cylinder has two round flat ends."),
            q("🍦 Which shape has a round base and one point?", "Cone|Cube|Sphere", "Cone", "A cone narrows from a circular base to one point."),
            q("🔢 Which number comes after 8?", "7|9|10", "9", "Nine comes after eight when counting."),
            q("🧱 Which object is shaped like a box?", "Cuboid|Sphere|Cone", "Cuboid", "A cuboid has rectangular faces like many boxes."),
            q("🧩 Which shape has three corners?", "Triangle|Circle|Cylinder", "Triangle", "A triangle has three corners called vertices."),
            q("🌀 What is the four-dimensional shape like a moving cube called?", "Tesseract|Circle|Cone", "Tesseract", "A tesseract is the four-dimensional analogue of a cube.")
        )
        "physics" -> listOf(
            q("⚽ What can a push or pull change about a moving ball?", "Its speed or direction|Its colour automatically|Its name", "Its speed or direction", "A force can change speed or direction."),
            q("🛝 What can make a sliding toy slow down?", "Friction|A shadow|Its colour", "Friction", "Friction acts against sliding between touching surfaces."),
            q("🧰 Which tool can show how hard you pull?", "Force meter|Thermometer|Compass", "Force meter", "A force meter measures force."),
            q("🚗 What helps a toy car speed up?", "A forward push|A quieter colour|A shorter name", "A forward push", "A force can make a toy car speed up."),
            q("🏀 What happens to a ball when you lift it higher?", "It gains stored energy|It loses its shape|It becomes a magnet", "It gains stored energy", "A lifted ball has more gravitational potential energy."),
            q("💡 What does a simple circuit need to light a bulb?", "A source and a closed path|A broken wire|Only a plastic spoon", "A source and a closed path", "A complete path and a battery let current flow."),
            q("🧲 Which magnet ends attract?", "North and south|North and north|South and south", "North and south", "Opposite magnetic poles attract."),
            q("🔌 What happens when a wire is disconnected from a bulb circuit?", "The bulb goes out|The bulb becomes a battery|The wire makes light", "The bulb goes out", "A broken path stops continuous current."),
            q("🛒 Which is harder to start moving, a heavy cart or a light toy?", "A heavy cart|The light toy always|Both have no mass", "A heavy cart", "More mass means greater inertia."),
            q("🧱 Why can a rough floor slow a toy car?", "It creates more friction|It turns off gravity|It removes the wheels", "It creates more friction", "Friction can slow objects moving across a surface.")
        )
        "chemistry" -> listOf(
            q("⚛️ Which tiny particle has a negative charge?", "Electron|Proton|Neutron", "Electron", "An electron has a negative electric charge."),
            q("🔴 Where are protons found in an atom?", "Nucleus|Outside the atom only|In the wire", "Nucleus", "The nucleus is at the centre of an atom."),
            q("💧 Which two kinds of atoms make water?", "Hydrogen and oxygen|Gold and iron|Carbon and copper", "Hydrogen and oxygen", "A water molecule contains hydrogen and oxygen."),
            q("➕ Which particle in the nucleus has a positive charge?", "Proton|Electron|Cloud", "Proton", "Protons are positively charged."),
            q("🧪 What does an indicator help show in a liquid?", "Whether it is acidic or basic|Its birthday|Its sound", "Whether it is acidic or basic", "Some indicators change colour in acids and bases."),
            q("🎈 Which gas is part of the air we breathe?", "Oxygen|Liquid iron|Wood", "Oxygen", "Air is a mixture of gases, including oxygen."),
            q("☀️ What changes liquid water into water vapour?", "Evaporation|Freezing|Breaking", "Evaporation", "Heat can turn liquid water into water vapour."),
            q("🧩 Where are chemical elements organised?", "Periodic table|Map of cities|Weather calendar", "Periodic table", "The periodic table organises chemical elements."),
            q("⚪ What is a molecule made from?", "Atoms joined together|Only shadows|Separate planets", "Atoms joined together", "Molecules contain atoms held together by chemical bonds."),
            q("🧪 In a virtual lab, what may show that a reaction happened?", "Bubbles or a colour change|The screen name changing|A shape disappearing from the menu", "Bubbles or a colour change", "Simulated reactions can show bubbles, colour changes or new material.")
        )
        "biology" -> listOf(
            q("🧬 Which part of an animal cell holds most DNA?", "Nucleus|Cell membrane|Petal", "Nucleus", "The nucleus contains most genetic material in an animal cell."),
            q("🚪 Which cell part controls what goes in and out?", "Cell membrane|Leaf|Bone", "Cell membrane", "The cell membrane controls movement into and out of the cell."),
            q("🌱 Which plant part takes in water from soil?", "Roots|Petals|Fruit", "Roots", "Roots absorb water and minerals from soil."),
            q("☀️ What do green plants use sunlight to help make?", "Sugars|Metal|Sand", "Sugars", "Photosynthesis helps plants make sugars using light energy."),
            q("🔋 Which cell structures release useful energy from food?", "Mitochondria|Shoelaces|Petals only", "Mitochondria", "Mitochondria help release usable energy from food."),
            q("🧫 What is the tiny basic unit of living things?", "Cell|Planet|Toy", "Cell", "Cells are the basic units of living organisms."),
            q("🌸 Which flower part often attracts bees?", "Petals|Roots|Stem only", "Petals", "Petals often attract pollinators with colour and scent."),
            q("🧬 What carries instructions passed from parents to offspring?", "DNA|Water|Starch only", "DNA", "DNA stores inherited genetic information."),
            q("❤️ Which organ pumps blood around the body?", "Heart|Stomach|Ear", "Heart", "The heart pumps blood through blood vessels."),
            q("🌳 What includes living things and the place they live?", "Ecosystem|One pebble only|A picture frame", "Ecosystem", "An ecosystem includes organisms and their environment.")
        )
        "general science" -> listOf(
            q("💧 What is liquid water turning into vapour called?", "Evaporation|Freezing|Falling", "Evaporation", "Evaporation changes liquid water into vapour."),
            q("☁️ What is vapour cooling into tiny water droplets called?", "Condensation|Melting|Breaking", "Condensation", "Condensation forms tiny liquid droplets."),
            q("🔌 Which material is often used to carry electricity in wires?", "Copper|Rubber|Dry paper", "Copper", "Copper is a good electrical conductor."),
            q("☀️ Which renewable energy comes from the Sun?", "Solar energy|Coal|Petrol", "Solar energy", "Solar energy comes from sunlight."),
            q("🌦️ What gives Earth most of the energy that drives weather?", "The Sun|A shadow|A compass", "The Sun", "Solar heating drives much of Earth's weather."),
            q("🧊 Which state of matter is ice?", "Solid|Liquid|Gas", "Solid", "Ice is water in its solid state."),
            q("🌑 What forms when an object blocks light?", "A shadow|A rainbow every time|A sound", "A shadow", "A shadow forms where an object blocks light."),
            q("🌡️ Which tool measures temperature?", "Thermometer|Ruler|Compass", "Thermometer", "A thermometer measures temperature."),
            q("🍎 What force pulls a dropped apple toward Earth?", "Gravity|Evaporation|Reflection", "Gravity", "Gravity attracts objects toward Earth."),
            q("🌧️ What is rain falling from clouds called?", "Precipitation|Evaporation|Melting", "Precipitation", "Rain is a type of precipitation.")
        )
        "astronomy" -> listOf(
            q("🪐 Which planet is the largest in our Solar System?", "Jupiter|Earth|Mercury", "Jupiter", "Jupiter is the largest planet."),
            q("💍 Which planet is famous for its bright rings?", "Saturn|Mars|Mercury", "Saturn", "Saturn has prominent rings."),
            q("🌍 What makes day change into night on Earth?", "Earth spins|The Sun switches off|Clouds stop time", "Earth spins", "Earth's rotation creates day and night."),
            q("🔴 Which planet is called the Red Planet?", "Mars|Venus|Neptune", "Mars", "Mars looks red because of iron-rich dust."),
            q("☀️ What is at the centre of our Solar System?", "The Sun|The Moon|Earth", "The Sun", "Planets orbit the Sun."),
            q("🌀 Which planet rotates with an unusually tilted axis?", "Uranus|Mercury|Mars", "Uranus", "Uranus has an extreme axial tilt."),
            q("🌙 What is a natural object orbiting a planet called?", "A moon|A cloud|A mountain", "A moon", "A natural satellite orbits a planet."),
            q("✨ Why do planets shine in our night sky?", "They reflect sunlight|They are all stars|They burn like fire", "They reflect sunlight", "Planets are mostly seen by reflected sunlight."),
            q("🛰️ What keeps planets moving around the Sun?", "Gravity and forward motion|A rope|Ocean waves", "Gravity and forward motion", "Gravity bends a planet's path while its motion carries it forward."),
            q("☀️ Which planet is closest to the Sun?", "Mercury|Venus|Earth", "Mercury", "Mercury is the innermost planet.")
        )
        "geography" -> listOf(
            q("🌍 What does a globe show?", "Earth|Only the Moon|A computer", "Earth", "A globe is a model of Earth."),
            q("🌊 Which is the largest ocean?", "Pacific Ocean|Indian Ocean|Arctic Ocean", "Pacific Ocean", "The Pacific is the largest ocean."),
            q("🗺️ What helps us compare map distance with real distance?", "Map scale|A colour name|A clock", "Map scale", "A map scale relates map measurements to real distances."),
            q("🌦️ What is the long-term pattern of weather called?", "Climate|One rainy minute|A single cloud", "Climate", "Climate describes weather patterns over long periods."),
            q("⛰️ What do contour lines on a map connect?", "Places of equal height|People with equal ages|Roads only", "Places of equal height", "Contour lines join points at the same elevation."),
            q("🧭 Which direction is opposite east?", "West|North|South", "West", "West is opposite east."),
            q("🌐 What imaginary line circles Earth's middle?", "Equator|Prime Meridian|A river", "Equator", "The equator divides Earth into the north and south halves."),
            q("🏞️ What is a high area of fairly flat land called?", "Plateau|Ocean|Valley only", "Plateau", "A plateau is an elevated area with a fairly level surface."),
            q("🏞️ Where does a river begin?", "Its source|Its widest point only|A desert every time", "Its source", "The source is where a river begins."),
            q("🏝️ What is land surrounded by water called?", "Island|Desert|Plateau", "Island", "An island is land surrounded by water.")
        )
        "social studies" -> listOf(
            q("🧭 What does a compass rose show on a map?", "Directions|Food groups|The time", "Directions", "A compass rose shows directions."),
            q("🏘️ What is a community?", "People who live or work together|One toy only|A planet", "People who live or work together", "Communities share places, activities or needs."),
            q("📋 Why do communities have rules?", "To help people stay safe and cooperate|To stop every game|To make all people identical", "To help people stay safe and cooperate", "Rules support safety and fair cooperation."),
            q("🗺️ What explains symbols on a map?", "The map legend|A pencil case|A clock", "The map legend", "A map legend explains symbols and colours."),
            q("🧹 Which action helps a shared public place?", "Keep it clean|Break benches|Leave rubbish everywhere", "Keep it clean", "Caring for shared spaces is a community responsibility."),
            q("🎉 What can culture include?", "Food, language and traditions|Only mountains|Only numbers", "Food, language and traditions", "Culture includes shared practices, knowledge and traditions."),
            q("🌐 What does a globe represent?", "Earth's shape|A computer screen|Only one city", "Earth's shape", "A globe models Earth's overall shape."),
            q("📚 What is a public library?", "A community service|A private password|A planet", "A community service", "Libraries provide books and learning resources to the public."),
            q("📜 Why do we look at old photographs and stories?", "To learn about the past|To change yesterday|To measure rain", "To learn about the past", "Historical sources can help us understand past events."),
            q("🏳️ What can a country's flag represent?", "A national symbol|A thermometer|A map scale", "A national symbol", "Flags often represent a country or community.")
        )
        "computer skills" -> listOf(
            q("🔋 What supplies power to a simple circuit?", "A battery or source|A mouse|Paper", "A battery or source", "A power source provides energy to a circuit."),
            q("🔘 What does a closed switch do?", "Completes the circuit path|Deletes every wire|Makes plastic conduct", "Completes the circuit path", "A closed switch completes the conducting path."),
            q("🔐 Which password is safer?", "A long unique passphrase|123456|Your name only", "A long unique passphrase", "Long, unique passwords are harder to guess."),
            q("🔗 What should you do with a strange link from a stranger?", "Do not open it; ask a trusted adult|Enter your password|Send it to everyone", "Do not open it; ask a trusted adult", "Suspicious links may be unsafe; check with a trusted adult."),
            q("⌨️ What does a keyboard do?", "Enters text and commands|Prints paper by itself|Cools a room", "Enters text and commands", "A keyboard is an input device."),
            q("💻 What is a computer program?", "Instructions for a computer|A kind of food|A planet's orbit", "Instructions for a computer", "A program contains instructions a computer can run."),
            q("🖱️ Which device moves a pointer on many computers?", "Mouse or touchpad|Speaker|Lamp", "Mouse or touchpad", "A mouse or touchpad is a pointing input device."),
            q("🔄 Why install trusted software updates?", "They can fix bugs and security problems|They make every password public|They remove the screen", "They can fix bugs and security problems", "Updates often correct bugs and security weaknesses."),
            q("🛡️ Which should stay private?", "Your home address and password|A public science fact|A maths answer", "Your home address and password", "Addresses and passwords should be protected."),
            q("💾 What is a backup?", "An extra copy for recovery|A screen colour|A keyboard button", "An extra copy for recovery", "Backups help recover data if the original is lost.")
        )
        "health & safety" -> listOf(
            q("🧼 What helps remove dirt and germs from hands?", "Soap and water|Paint|Sand only", "Soap and water", "Washing with soap and water helps remove dirt and germs."),
            q("🥗 What is a balanced diet?", "Different foods that provide needed nutrients|Only sweets|One food every day", "Different foods that provide needed nutrients", "Different foods supply different nutrients."),
            q("🔥 What should you do for a small heat burn?", "Tell an adult and cool it with running water|Rub with soil|Use an unknown chemical", "Tell an adult and cool it with running water", "Get adult help and cool a minor burn with clean cool running water."),
            q("🫁 Which organs help us breathe?", "Lungs|Stomach|Knees", "Lungs", "The lungs exchange oxygen and carbon dioxide."),
            q("🌙 Why is sleep important for children?", "It helps growth and learning|It replaces water|It prevents every illness", "It helps growth and learning", "Sleep supports growth, memory and recovery."),
            q("🚑 What should you do if someone is badly hurt?", "Get an adult and emergency help|Hide it|Try unknown medicine alone", "Get an adult and emergency help", "Serious injuries need a responsible adult and qualified help."),
            q("💧 What is a good drink for everyday hydration?", "Clean water|Only fizzy drinks|Paint water", "Clean water", "Clean drinking water helps keep the body hydrated."),
            q("👀 What can help your eyes during screen use?", "Take breaks and look away|Never blink|Hold the screen against your eyes", "Take breaks and look away", "Regular screen breaks may reduce eye strain."),
            q("💊 Who should help decide if a child takes medicine?", "A parent or qualified health professional|A stranger online|A game character", "A parent or qualified health professional", "Medicine and doses need responsible adult or professional guidance."),
            q("🏃 Which activity helps keep the body active?", "Age-appropriate play and movement|Sitting all day|Never walking", "Age-appropriate play and movement", "Regular age-appropriate movement supports health.")
        )
        "art & design" -> listOf(
            q("🎨 Which set is often taught as primary paint colours?", "Red, yellow and blue|Green, orange and purple|Black, grey and white", "Red, yellow and blue", "Traditional beginner paint lessons often use red, yellow and blue."),
            q("🟠 What do red and yellow paint often make?", "Orange|Green|Blue", "Orange", "Red and yellow commonly mix to make orange."),
            q("⚽ What helps a ball drawing look round?", "Light and shadow|Only a flat outline|No colour at all", "Light and shadow", "Shading and highlights suggest a rounded surface."),
            q("🏞️ What can perspective show in a picture?", "Depth and distance|A sound|A smell", "Depth and distance", "Perspective helps a flat picture suggest space."),
            q("🔺 Which shape has three straight sides?", "Triangle|Circle|Oval", "Triangle", "A triangle has three sides."),
            q("🎨 Which pair is opposite on a common colour wheel?", "Blue and orange|Blue and blue|Yellow and yellow", "Blue and orange", "Blue and orange are complementary on a common traditional colour wheel."),
            q("✏️ What is a sketch?", "A quick drawing to try an idea|A weather report|A computer password", "A quick drawing to try an idea", "A sketch helps plan an idea before adding details."),
            q("🧶 What can texture tell us about a surface?", "How it looks or feels|Its birthday|Its name only", "How it looks or feels", "Texture can suggest roughness, softness or smoothness."),
            q("🪞 What is symmetry?", "Parts match across a line|Every colour is the same|A type of paper", "Parts match across a line", "Symmetry means parts match in a balanced arrangement."),
            q("🖌️ Why choose a colour palette?", "To plan colours that work together|To measure weight|To remove every shape", "To plan colours that work together", "A palette helps plan colours for an artwork.")
        )
        "drawing & colour" -> listOf(
            q("✏️ What is a good first step when drawing a toy?", "Start with light simple shapes|Draw every tiny detail first|Colour the page black", "Start with light simple shapes", "Simple construction shapes help place the drawing."),
            q("🖊️ What does a contour line show?", "The edge of a shape|The sound of a colour|A person's age", "The edge of a shape", "Contour lines show important edges and boundaries."),
            q("🏠 How can a drawing make a nearby house look closer?", "Draw it larger in the scene|Always make it tiny|Remove all overlap", "Draw it larger in the scene", "Size and overlap help suggest distance."),
            q("✏️ Which tool is useful for a light first sketch?", "Pencil|Spoon|Thermometer", "Pencil", "A pencil allows light marks that can be changed."),
            q("〰️ What does hatching use to make shading?", "Repeated lines|Only dots of glue|Water only", "Repeated lines", "Hatching uses repeated lines to suggest tone."),
            q("⬜ What is negative space?", "The empty space around shapes|The darkest pencil|A glue bottle", "The empty space around shapes", "The empty spaces around shapes help us judge placement."),
            q("🌄 What does a horizon line help show?", "Eye level in a landscape|The page's weight|A sound", "Eye level in a landscape", "The horizon line is often connected to the viewer's eye level."),
            q("⚽ How can you make a shiny ball look round?", "Use a bright highlight and smooth shading|Use one flat line|Erase all light", "Use a bright highlight and smooth shading", "Highlights and smooth shading suggest a rounded shiny object."),
            q("📏 What can help fix a drawing that looks too wide?", "Compare its height and width|Press harder|Stop looking at it", "Compare its height and width", "Comparing proportions helps correct a sketch."),
            q("🧩 How does overlap help show depth?", "One shape hides part of another|All shapes must be separate|All shapes need equal size", "One shape hides part of another", "Overlap shows which object is in front.")
        )
        "english" -> listOf(
            q("📖 Which word names a thing?", "Book|Quickly|Blue", "Book", "A noun can name a person, place, thing or idea."),
            q("🏃 Which word tells an action?", "Run|Chair|Green", "Run", "A verb can tell an action."),
            q("🌞 Which word means the opposite of hot?", "Cold|Warm|Sunny", "Cold", "Cold is an opposite of hot."),
            q("❓ Which sentence asks a question?", "Where is my book?|My book is blue.|I have a book.", "Where is my book?", "A question asks for information."),
            q("🔤 Which letter comes after A?", "B|D|Z", "B", "B follows A in the English alphabet."),
            q("🐈 Which word rhymes with cat?", "Hat|Dog|Sun", "Hat", "Cat and hat share the same ending sound."),
            q("🧩 Which word completes: I ___ happy.", "am|are|is", "am", "We say 'I am happy.'"),
            q("🧱 Which word is an adjective in 'a red ball'?", "Red|Ball|A", "Red", "An adjective describes a noun."),
            q("✉️ What mark often ends a statement?", "Full stop|Question mark every time|Comma only", "Full stop", "A full stop commonly ends a statement."),
            q("📚 What helps you find a word's meaning?", "Dictionary|Compass|Thermometer", "Dictionary", "A dictionary explains words and their meanings.")
        )
        else -> emptyList()
    }
    private val maths = listOf(
        q("🚀 Space-builder mission: rotate the floating cube and count its square faces. How many are there?", "4|6|8", "6", "A cube has six square faces, twelve edges and eight vertices."),
        q("🧊 Ice-robot challenge: a cube-shaped ice block is 3 cm on each side. What is its volume?", "9 cm³|18 cm³|27 cm³", "27 cm³", "Volume = side × side × side = 3 × 3 × 3 = 27 cm³."),
        q("🎪 Toy-factory mystery: which shape has two round ends and one curved side?", "Cylinder|Cone|Cube", "Cylinder", "A cylinder has two circular ends joined by a curved surface."),
        q("🏕️ Build a tiny tent-shaped prism: how many corner points (vertices) does it have?", "5|6|8", "6", "A triangular prism has two triangular ends, giving six vertices."),
        q("🪐 Planet explorer: which perfectly round solid has no corners at all?", "Sphere|Cube|Triangular prism", "Sphere", "A sphere is round with no flat faces, straight edges or vertices."),
        q("🧱 Mega-builder mission: if every cube edge becomes twice as long, how many times bigger is its volume?", "2|4|8", "8", "Volume scales with the cube of edge length; 2³ = 8."),
        q("🍦 Ice-cream shop: which 3D shape has a round base that narrows to one tip?", "Cone|Cylinder|Cuboid", "Cone", "A cone has a circular base and a single apex."),
        q("📦 Robot delivery box: how many straight edges does a cuboid have?", "8|10|12", "12", "A cuboid has twelve edges."),
        q("🎁 Gift-box mission: a box is 4 cm long, 3 cm wide and 2 cm high. What space does it hold?", "9 cm³|24 cm³|36 cm³", "24 cm³", "Multiply length × width × height: 4 × 3 × 2 = 24 cm³."),
        q("🧊 4D explorer challenge: what is the four-dimensional analogue of a cube called?", "Tesseract|Cylinder|Sphere", "Tesseract", "A tesseract is the four-dimensional analogue of a cube; its projection can be explored as two linked cubes.")
    )
    private val physics = listOf(
        q("⚽ Space-arena challenge: kick the glowing ball. What can an unbalanced force change about its motion?", "Speed or direction|Mass always|The object's material automatically", "Speed or direction", "A net force changes velocity: speed, direction, or both."),
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
        q("💧 Water-world mission: build a water molecule from hydrogen and oxygen. Which formula is correct?", "H₂O|CO₂|NaCl", "H₂O", "Each water molecule contains two hydrogen atoms and one oxygen atom."),
        q("Which particle determines the atomic number of an element?", "Proton|Neutron|Electron in a neutral atom only", "Proton", "Atomic number is the number of protons in the nucleus."),
        q("A solution with pH 3 is generally what?", "Acidic|Neutral|Alkaline", "Acidic", "Values below pH 7 are acidic in the usual aqueous pH scale."),
        q("Which is a mixture rather than a single pure substance?", "Air|Pure oxygen gas|Distilled water", "Air", "Air contains several gases mixed together."),
        q("Which process changes liquid water to water vapour?", "Evaporation|Freezing|Condensation", "Evaporation", "Evaporation changes liquid water into water vapour at a surface."),
        q("What does the periodic table organise?", "Chemical elements|Only planets|Only mixtures", "Chemical elements", "Elements are arranged by atomic number and recurring properties."),
        q("What is a molecule?", "Atoms chemically bonded together|A visible grain of sand always|A type of force", "Atoms chemically bonded together", "A molecule is a group of atoms held together by chemical bonds."),
        q("Which observation can indicate a chemical reaction?", "A new substance forms|An object is moved|A shadow changes", "A new substance forms", "Chemical reactions rearrange particles and produce substances with new properties.")
    )
    private val biology = listOf(
        q("🔬 Mini-scientist mission: zoom into an animal cell. Which part keeps most of its DNA?", "Nucleus|Cell membrane|Cytoplasm only", "Nucleus", "The nucleus stores most genetic material in a typical animal cell."),
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
    private val drawing = listOf(
        q("Which first step helps place a character correctly on a page?", "Light simple shapes|Dark details immediately|Colouring the background first", "Light simple shapes", "Light shapes help plan proportion and placement before details."),
        q("What is a contour line used to show?", "The visible edge of a form|The sound of a colour|The weight of paper", "The visible edge of a form", "Contour lines describe the edges and important boundaries of a subject."),
        q("How can you make an object look nearer in a drawing?", "Draw it larger and lower in the scene|Always draw it smaller|Remove all overlap", "Draw it larger and lower in the scene", "Size, placement and overlap are common depth clues."),
        q("Which tool is useful for gently planning a sketch?", "A pencil|A wet sponge only|A ruler used as paint", "A pencil", "A pencil allows light construction marks that can be refined."),
        q("What does hatching create?", "Tone using repeated lines|A sound effect|A paper fold", "Tone using repeated lines", "Hatching uses lines to suggest shade and surface form."),
        q("What is negative space in a drawing?", "The empty area around or between shapes|The darkest pencil only|A type of glue", "The empty area around or between shapes", "Noticing empty spaces helps improve proportions and composition."),
        q("Why draw a horizon line in a landscape?", "To mark eye level and organise perspective|To show the page edge only|To measure temperature", "To mark eye level and organise perspective", "The horizon line is often related to the viewer's eye level."),
        q("Which technique can show a shiny sphere?", "A bright highlight with gradual shading|One flat colour only|A jagged outline everywhere", "A bright highlight with gradual shading", "Highlights and smooth tonal changes suggest a reflective rounded surface."),
        q("What should you do if the first sketch proportions look wrong?", "Compare shapes and adjust lightly|Press harder and hide the lines|Stop observing the subject", "Compare shapes and adjust lightly", "Comparing height, width and angles helps correct a drawing early."),
        q("How can overlapping shapes suggest depth?", "The front shape hides part of the shape behind it|Every shape must be separate|All outlines must be the same size", "The front shape hides part of the shape behind it", "Occlusion is a strong visual clue that one object is in front of another.")
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
        addAll(bank("Drawing & Colour", "Drawing & Colour", "flower", drawing))
        addAll(bank("English", "English", "book", english))
        addAll(bank("Urdu", "Urdu", "book", urdu, language = "ur", country = "PK"))
        addAll(bank("Sindhi", "Sindhi", "book", sindhi, language = "sd", country = "PK"))
        addAll(bank("Hindi", "Hindi", "book", hindi, language = "hi", country = "IN"))
        religions.forEach { (track, subject, seeds) ->
            addAll(bank("Religious Education", subject, "book", seeds, conceptPrefix = "4d:book:faith:$track"))
        }
    }
}
