package com.edulock.app

/**
 * Safe, classroom-friendly content for parent-created question and activity templates.
 * These are demonstrations with observable outcomes, not a guarantee of identical results
 * on every material or in every environment.
 */
data class ParentLearningTemplate(
    val title: String,
    val category: String,
    val subjectTitle: String,
    val languageCode: String,
    val questionType: String,
    val prompt: String,
    val options: List<String>,
    val answer: String,
    val conceptKey: String,
    val solution: String,
    val materials: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val safety: String = "",
    // ZZ means a general starter; country-tagged examples are only shown for that selected country.
    val countryCode: String = "ZZ"
)

object LearningTemplates {
    val all: List<ParentLearningTemplate> = listOf(
        ParentLearningTemplate(
            title = "Addition story problem",
            category = "Mathematics",
            subjectTitle = "Mathematics",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "A library has 7 storybooks and receives 5 more. How many storybooks does it have now?",
            options = listOf("10", "11", "12", "13"),
            answer = "12",
            conceptKey = "addition-within-20",
            solution = "Step 1: Start with 7 books. Step 2: Add the 5 new books. 7 + 5 = 12. Answer: 12 storybooks."
        ),
        ParentLearningTemplate(
            title = "Multiplication groups",
            category = "Mathematics",
            subjectTitle = "Mathematics",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "There are 8 bags with 7 marbles in each bag. How many marbles are there altogether?",
            options = listOf("15", "48", "54", "56"),
            answer = "56",
            conceptKey = "multiplication-equal-groups",
            solution = "Equal groups use multiplication: 8 bags × 7 marbles per bag = 56 marbles. Check by adding 7 eight times."
        ),
        ParentLearningTemplate(
            title = "Area of a rectangle",
            category = "Mathematics",
            subjectTitle = "Mathematics",
            languageCode = "en",
            questionType = "numeric",
            prompt = "A rectangular garden is 7 metres long and 5 metres wide. What is its area in square metres? Enter the number only.",
            options = emptyList(),
            answer = "35",
            conceptKey = "geometry-rectangle-area",
            solution = "Area of a rectangle = length × width. 7 m × 5 m = 35 m². The numeric answer is 35."
        ),
        ParentLearningTemplate(
            title = "Equivalent fraction",
            category = "Mathematics",
            subjectTitle = "Mathematics",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "A pizza is cut into 4 equal slices. You eat 2 slices. Which fraction is equal to the part eaten?",
            options = listOf("1/4", "1/2", "2/3", "3/4"),
            answer = "1/2",
            conceptKey = "fractions-equivalence",
            solution = "The part eaten is 2/4. Divide the numerator and denominator by 2: 2/4 = 1/2."
        ),
        ParentLearningTemplate(
            title = "Paper helicopter: air resistance",
            category = "Physics",
            subjectTitle = "Physics",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "PRACTICAL ACTIVITY — PAPER HELICOPTER\nMaterials: one sheet of paper, pencil, ruler, paper clip (optional), and blunt scissors.\nSteps: 1) Make a paper helicopter with two top blades folded in opposite directions and a narrow body. 2) Add one paper clip at the bottom if needed. 3) Drop it from the same safe height three times; do not throw it. 4) Compare it with a crumpled paper ball.\nQuestion: Why will the helicopter often take longer to reach the floor than the crumpled paper ball?",
            options = listOf(
                "Its blades increase air resistance and make it rotate as it falls.",
                "Gravity stops acting on the helicopter.",
                "The paper has no mass.",
                "The helicopter creates upward electricity."
            ),
            answer = "Its blades increase air resistance and make it rotate as it falls.",
            conceptKey = "physics-air-resistance",
            solution = "Expected observation: the helicopter usually falls more slowly and rotates because its blades interact with air. Gravity still pulls it down; air resistance changes its motion. Results depend on the paper shape and drop height.",
            materials = listOf("Paper", "Ruler and pencil", "Blunt scissors", "Optional paper clip"),
            steps = listOf(
                "Cut and fold a simple paper helicopter; fold the two blades in opposite directions.",
                "Drop it from a fixed height in an open area. Do not throw it.",
                "Repeat three times and compare with a crumpled paper ball dropped from the same height.",
                "Record which falls more slowly and discuss the role of air resistance."
            ),
            safety = "Adult supervision for scissors. Drop from a low safe height away from stairs, fans, and faces."
        ),
        ParentLearningTemplate(
            title = "Toy car on a ramp: energy",
            category = "Physics",
            subjectTitle = "Physics",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "PRACTICAL ACTIVITY — RAMP AND TOY CAR\nMaterials: a toy car, a sturdy piece of cardboard, books, ruler, and tape.\nSteps: 1) Make a short ramp with the cardboard. 2) Mark one starting point. 3) Release the same car without pushing. 4) Repeat with the ramp raised by one book each time and measure how far the car rolls on a clear level floor.\nQuestion: What is the usual result as the starting height increases?",
            options = listOf(
                "The car usually travels farther because more gravitational potential energy can change into motion.",
                "The car always stops at exactly the same point.",
                "The car loses all its mass.",
                "Gravity reverses direction."
            ),
            answer = "The car usually travels farther because more gravitational potential energy can change into motion.",
            conceptKey = "physics-gravitational-potential-energy",
            solution = "A higher starting point gives the car more gravitational potential energy. Some becomes kinetic energy as it rolls. Friction and ramp shape affect the distance, so measure repeated trials rather than expecting exactly the same result every time.",
            materials = listOf("Toy car", "Sturdy cardboard", "Books", "Ruler and tape"),
            steps = listOf(
                "Build a stable ramp and mark the same start point.",
                "Release the car without pushing and measure the distance it rolls.",
                "Raise the ramp by one book and repeat three trials.",
                "Compare average distances and explain how height affects energy."
            ),
            safety = "Keep the ramp low and stable, use books that cannot topple, and test away from stairs, roads, and breakable objects."
        ),
        ParentLearningTemplate(
            title = "Rubber-band sound: vibration",
            category = "Physics",
            subjectTitle = "Physics",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "PRACTICAL ACTIVITY — SOUND AND VIBRATION\nMaterials: an empty sturdy box and a wide rubber band.\nSteps: 1) Stretch the rubber band around the box. 2) Gently pluck it. 3) Watch and listen. 4) Try a slightly tighter band only if an adult can change it safely.\nQuestion: What produces the sound when the rubber band is plucked?",
            options = listOf(
                "The rubber band vibrates and makes nearby air vibrate.",
                "The box creates sound without moving air.",
                "Sound is made by light.",
                "The rubber band stops vibrating before it makes sound."
            ),
            answer = "The rubber band vibrates and makes nearby air vibrate.",
            conceptKey = "physics-sound-vibration",
            solution = "The plucked band vibrates. It transfers pressure changes to the surrounding air, which travel to the ear as sound. A tighter band often produces a higher pitch, but the exact pitch depends on length, tension, and thickness.",
            materials = listOf("Empty sturdy box", "Wide rubber band"),
            steps = listOf(
                "Stretch the band safely around the box.",
                "Pluck it gently and listen while watching the band.",
                "If an adult safely changes the tension, compare the sounds.",
                "Describe how vibration creates sound."
            ),
            safety = "Do not stretch a damaged band near eyes or face; stop if it snaps or feels too tight."
        ),
        ParentLearningTemplate(
            title = "Red-cabbage indicator: acids and bases",
            category = "Chemistry",
            subjectTitle = "Chemistry",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "PRACTICAL ACTIVITY — RED-CABBAGE INDICATOR\nMaterials: cooled red-cabbage indicator, clear cups, water, lemon juice, and a small amount of baking soda dissolved in water. An adult prepares the indicator by steeping chopped red cabbage in hot water and letting it cool.\nSteps: 1) Pour equal amounts of indicator into separate cups. 2) Add a little water to the first, lemon juice to the second, and baking-soda solution to the third. 3) Observe and compare the colours. Do not taste anything.\nQuestion: What colour change is commonly seen when cabbage indicator is added to lemon juice?",
            options = listOf("Pink or red", "Blue-green", "Black", "No possible colour change"),
            answer = "Pink or red",
            conceptKey = "chemistry-ph-acids-bases",
            solution = "Red-cabbage pigment changes colour with pH. Lemon juice is acidic and commonly turns the indicator pink/red. Near-neutral water stays purple, and a mild baking-soda solution often shifts it toward blue/green. Exact shades depend on concentration and the cabbage.",
            materials = listOf("Red cabbage indicator", "Clear cups", "Water", "Lemon juice", "Baking soda and water"),
            steps = listOf(
                "An adult prepares cabbage indicator with hot water, then lets it cool completely.",
                "Add equal amounts of indicator to three labelled cups.",
                "Add water to one cup, a small amount of lemon juice to another, and dilute baking-soda solution to the third.",
                "Observe the colours and record which sample is acidic, near-neutral, or mildly basic."
            ),
            safety = "Use only food-grade materials listed here. Do not taste experiments. An adult handles hot water. Never mix the experiment with bleach, drain cleaner, toilet cleaner, or other cleaning products."
        ),
        ParentLearningTemplate(
            title = "Dissolving sugar: temperature and rate",
            category = "Chemistry",
            subjectTitle = "Chemistry",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "PRACTICAL ACTIVITY — DISSOLVING SUGAR\nMaterials: two clear cups, equal amounts of water (one cool and one warm, not hot), sugar, and a spoon.\nSteps: 1) Put the same amount of water in each cup. 2) Add one level teaspoon of sugar to each. 3) Stir each cup at the same rate and compare how long the visible crystals take to disappear. Do not drink the mixtures.\nQuestion: Under these conditions, which cup will usually dissolve the sugar faster?",
            options = listOf("The warm-water cup", "The cool-water cup", "Both always take exactly the same time", "Sugar cannot dissolve in water"),
            answer = "The warm-water cup",
            conceptKey = "chemistry-dissolving-temperature",
            solution = "Sugar usually dissolves faster in warmer water because particles move and interact more rapidly. Keep the amount of water, sugar, and stirring as similar as possible for a fair comparison. Temperature mostly affects the rate here; stirring and crystal size also matter.",
            materials = listOf("Two clear cups", "Cool water", "Warm—not hot—water", "Sugar", "Teaspoon"),
            steps = listOf(
                "An adult prepares equal volumes of cool and warm water.",
                "Add one level teaspoon of sugar to each cup.",
                "Stir both at the same rate and compare how long crystals remain visible.",
                "Repeat if needed and explain why temperature changes the rate."
            ),
            safety = "Use warm water only, not hot or boiling water. Do not drink the experiment mixtures; wipe up spills."
        ),
        ParentLearningTemplate(
            title = "Oil and water: mixtures",
            category = "Chemistry",
            subjectTitle = "Chemistry",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "PRACTICAL ACTIVITY — OIL AND WATER\nMaterials: a clear jar, water, a small amount of cooking oil, and food colouring (optional).\nSteps: 1) Add water to the jar. 2) Add a little oil. 3) Close the jar and gently swirl it, then let it stand.\nQuestion: After the jar stands still, what will usually happen?",
            options = listOf(
                "The oil forms a separate layer above the water.",
                "The oil permanently becomes water.",
                "The water disappears instantly.",
                "The two liquids always form one uniform layer."
            ),
            answer = "The oil forms a separate layer above the water.",
            conceptKey = "chemistry-mixtures-density",
            solution = "Oil and water are immiscible: they do not form one stable uniform liquid. Most cooking oils are less dense than water, so the oil layer usually sits above the water after swirling. Tiny droplets may temporarily disperse, then separate again.",
            materials = listOf("Clear jar", "Water", "Cooking oil", "Optional food colouring"),
            steps = listOf(
                "Fill the jar partway with water.",
                "Add a small amount of cooking oil.",
                "Gently swirl and observe the temporary droplets.",
                "Leave it to stand and record which layer is on top."
            ),
            safety = "Do not drink the mixture. Wipe oil spills promptly because they can make floors slippery. Never use fuel, solvents, or cleaning chemicals."
        )
    ) + listOf(
ParentLearningTemplate(
            title = "Percent discount",
            category = "Mathematics",
            subjectTitle = "Mathematics",
            languageCode = "en",
            questionType = "numeric",
            prompt = "A school bag costs 2,000 rupees and has a 10% discount. What is the discount amount in rupees? Enter the number only.",
            options = emptyList(),
            answer = "200",
            conceptKey = "percent-discount",
            solution = "10% of 2,000 = 0.10 × 2,000 = 200 rupees."
        ),
        ParentLearningTemplate(
            title = "Solve a simple equation",
            category = "Mathematics",
            subjectTitle = "Mathematics",
            languageCode = "en",
            questionType = "numeric",
            prompt = "Solve 3x + 4 = 19. What is x? Enter the number only.",
            options = emptyList(),
            answer = "5",
            conceptKey = "linear-equation-one-step",
            solution = "Subtract 4 from both sides: 3x = 15. Divide by 3: x = 5."
        ),
        ParentLearningTemplate(
            title = "Speed, distance and time",
            category = "Physics",
            subjectTitle = "Physics",
            languageCode = "en",
            questionType = "numeric",
            prompt = "A bicycle travels at 12 kilometres per hour for 2 hours. How far does it travel in kilometres?",
            options = emptyList(),
            answer = "24",
            conceptKey = "physics-speed-distance-time",
            solution = "Distance = speed × time = 12 km/h × 2 h = 24 km."
        ),
        ParentLearningTemplate(
            title = "Newton's third law",
            category = "Physics",
            subjectTitle = "Physics",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "When you push a wall, which statement best describes Newton's third law?",
            options = listOf("The wall pushes back with an equal and opposite force", "The wall has no force", "Gravity stops acting", "Force exists only while moving"),
            answer = "The wall pushes back with an equal and opposite force",
            conceptKey = "newtons-third-law",
            solution = "Forces occur in action–reaction pairs and act on different objects."
        ),
        ParentLearningTemplate(
            title = "Atoms and elements",
            category = "Chemistry",
            subjectTitle = "Chemistry",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "Which subatomic particle has a negative electric charge?",
            options = listOf("Electron", "Proton", "Neutron", "Nucleus"),
            answer = "Electron",
            conceptKey = "atomic-particles",
            solution = "Electrons carry negative charge, protons carry positive charge and neutrons have no electric charge."
        ),
        ParentLearningTemplate(
            title = "Conservation of mass",
            category = "Chemistry",
            subjectTitle = "Chemistry",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "In a closed system, what happens to total mass during a chemical reaction?",
            options = listOf("It stays the same", "It always doubles", "It always becomes zero", "It depends only on colour"),
            answer = "It stays the same",
            conceptKey = "conservation-of-mass",
            solution = "Atoms are rearranged in a chemical reaction; in a closed system total mass is conserved."
        ),
        ParentLearningTemplate(
            title = "Food chain",
            category = "Biology",
            subjectTitle = "Biology",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "In a simple grass → grasshopper → frog food chain, which organism is the primary consumer?",
            options = listOf("Grasshopper", "Grass", "Frog", "Sunlight"),
            answer = "Grasshopper",
            conceptKey = "food-chain-consumer",
            solution = "The grasshopper eats the producer (grass), so it is the primary consumer."
        ),
        ParentLearningTemplate(
            title = "Plant photosynthesis",
            category = "Biology",
            subjectTitle = "Biology",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "Which two substances do green plants mainly use as raw materials for photosynthesis?",
            options = listOf("Carbon dioxide and water", "Oxygen and sugar", "Sand and salt", "Nitrogen and oil"),
            answer = "Carbon dioxide and water",
            conceptKey = "photosynthesis-inputs",
            solution = "Using light energy, green plants use carbon dioxide and water to make sugars and release oxygen."
        ),
        ParentLearningTemplate(
            title = "3D Solar System: find the Red Planet",
            category = "General Science",
            subjectTitle = "General Science",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "Explore the moving solar system above. Tap the small rusty-red planet and guess its name. Which planet is known as the Red Planet?",
            options = listOf("Venus", "Mars", "Jupiter", "Neptune"),
            answer = "Mars",
            conceptKey = "solar-system-planet-id",
            solution = "Mars is called the Red Planet because iron minerals in its surface dust give it a reddish colour."
        ),
        ParentLearningTemplate(
            title = "Water cycle",
            category = "General Science",
            subjectTitle = "General Science",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "Which process changes liquid water into water vapour?",
            options = listOf("Evaporation", "Condensation", "Freezing", "Melting"),
            answer = "Evaporation",
            conceptKey = "water-cycle-evaporation",
            solution = "Evaporation changes liquid water into water vapour; condensation changes vapour back to liquid droplets."
        ),
        ParentLearningTemplate(
            title = "Parts of speech",
            category = "English",
            subjectTitle = "English",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "In the sentence 'The careful student wrote neatly,' which word is an adjective?",
            options = listOf("careful", "student", "wrote", "neatly"),
            answer = "careful",
            conceptKey = "english-adjectives",
            solution = "'Careful' describes the noun 'student', so it is an adjective."
        ),
        ParentLearningTemplate(
            title = "Reading inference",
            category = "English",
            subjectTitle = "English",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "A child takes an umbrella when the sky is dark and cloudy. What is the most reasonable inference?",
            options = listOf("Rain may be coming", "It is definitely midnight", "The umbrella creates clouds", "The child is underwater"),
            answer = "Rain may be coming",
            conceptKey = "english-inference",
            solution = "An inference uses clues and reasoning. Dark clouds and an umbrella suggest possible rain, but do not prove it."
        ),
        ParentLearningTemplate(
            title = "اردو: لفظ کا مخالف",
            category = "Urdu",
            subjectTitle = "Urdu",
            languageCode = "ur",
            questionType = "multiple_choice",
            prompt = "لفظ “خوش” کا مخالف کیا ہے؟",
            options = listOf("اداس", "خوبصورت", "تیز"),
            answer = "اداس",
            conceptKey = "urdu-antonyms",
            solution = "“خوش” کا مخالف لفظ “اداس” ہے۔"
        ),
        ParentLearningTemplate(
            title = "اردو: جملے میں اسم",
            category = "Urdu",
            subjectTitle = "Urdu",
            languageCode = "ur",
            questionType = "multiple_choice",
            prompt = "جملے “علی کتاب پڑھتا ہے۔” میں اسم کون سا ہے؟",
            options = listOf("علی", "پڑھتا", "ہے"),
            answer = "علی",
            conceptKey = "urdu-nouns",
            solution = "“علی” ایک شخص کا نام ہے، اس لیے یہ اسم ہے۔"
        ),
        ParentLearningTemplate(
            title = "سنڌي: ابتڙ لفظ",
            category = "Sindhi",
            subjectTitle = "Sindhi",
            languageCode = "sd",
            questionType = "multiple_choice",
            prompt = "لفظ “وڏو” جو ابتڙ ڇا آهي؟",
            options = listOf("ننڍو", "تيز", "سٺو"),
            answer = "ننڍو",
            conceptKey = "sindhi-opposites",
            solution = "“وڏو” جو ابتڙ لفظ “ننڍو” آهي۔"
        ),
        ParentLearningTemplate(
            title = "हिंदी: विलोम शब्द",
            category = "Hindi",
            subjectTitle = "Hindi",
            languageCode = "hi",
            questionType = "multiple_choice",
            prompt = "“खुश” का विलोम शब्द क्या है?",
            options = listOf("उदास", "सुंदर", "तेज़"),
            answer = "उदास",
            conceptKey = "hindi-antonyms",
            solution = "“खुश” का विलोम “उदास” है।"
        ),
        ParentLearningTemplate(
            title = "Pakistan: capital city",
            category = "Social Studies",
            subjectTitle = "Social Studies",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "What is the capital city of Pakistan?",
            options = listOf("Karachi", "Islamabad", "Lahore", "Peshawar"),
            answer = "Islamabad",
            conceptKey = "pakistan-capital",
            solution = "Islamabad is the capital of Pakistan. Karachi is the country's largest city."
        ),
        ParentLearningTemplate(
            title = "India: capital city",
            category = "Social Studies",
            subjectTitle = "Social Studies",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "What is the capital of India?",
            options = listOf("Mumbai", "New Delhi", "Kolkata", "Chennai"),
            answer = "New Delhi",
            conceptKey = "india-capital",
            solution = "New Delhi is the capital of India."
        ),
        ParentLearningTemplate(
            title = "Online privacy",
            category = "Computer Skills",
            subjectTitle = "Computer Skills",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "A stranger in a game asks a child for their home address. What is the safest response?",
            options = listOf("Do not share it and tell a trusted adult", "Send the address", "Post it publicly", "Share a password too"),
            answer = "Do not share it and tell a trusted adult",
            conceptKey = "online-privacy-address",
            solution = "Do not share personal details with strangers online. A trusted adult can help block or report unsafe contact."
        ),
        ParentLearningTemplate(
            title = "Strong passwords",
            category = "Computer Skills",
            subjectTitle = "Computer Skills",
            languageCode = "en",
            questionType = "multiple_choice",
            prompt = "Which is the safest password habit?",
            options = listOf("Use a unique long password and keep it private", "Reuse one short password everywhere", "Send it to a stranger", "Write it in a public post"),
            answer = "Use a unique long password and keep it private",
            conceptKey = "password-safety",
            solution = "Unique, long passwords reduce reuse risk. A password manager can help, and passwords should not be shared."
        )
    ) + listOf(
ParentLearningTemplate(
            title = "3D atom: locate the electron", category = "Chemistry", subjectTitle = "Chemistry",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Explore the 3D atom. Which negatively charged particle moves around the nucleus?",
            options = listOf("Electron", "Proton", "Neutron"), answer = "Electron",
            conceptKey = "3d:atom:atomic-structure",
            solution = "Electrons carry negative charge and occupy regions around an atom's nucleus."
        ),
        ParentLearningTemplate(
            title = "3D water molecule", category = "Chemistry", subjectTitle = "Chemistry",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Look at the 3D water molecule. Which elements make up water?",
            options = listOf("Hydrogen and oxygen", "Carbon and oxygen", "Sodium and chlorine"), answer = "Hydrogen and oxygen",
            conceptKey = "3d:molecule:water-molecule",
            solution = "A water molecule contains two hydrogen atoms bonded to one oxygen atom (H₂O)."
        ),
        ParentLearningTemplate(
            title = "3D geometric solids", category = "Mathematics", subjectTitle = "Mathematics",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Rotate the 3D solid. How many flat square faces does a cube have?",
            options = listOf("4", "6", "8"), answer = "6",
            conceptKey = "3d:geometry:cube-faces",
            solution = "A cube has six equal square faces, twelve edges and eight vertices."
        ),
        ParentLearningTemplate(
            title = "3D force and motion", category = "Physics", subjectTitle = "Physics",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Watch the moving ball and force arrows. What can an unbalanced force do to an object?",
            options = listOf("Change its motion", "Always remove its mass", "Stop all gravity"), answer = "Change its motion",
            conceptKey = "3d:force:unbalanced-force",
            solution = "A net force can change an object's speed, direction or both."
        ),
        ParentLearningTemplate(
            title = "3D animal cell", category = "Biology", subjectTitle = "Biology",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Explore the 3D animal cell. Which structure holds most of the cell's genetic material?",
            options = listOf("Nucleus", "Cell membrane", "Cytoplasm"), answer = "Nucleus",
            conceptKey = "3d:cell:nucleus",
            solution = "The nucleus stores most genetic material in a typical animal cell."
        ),
        ParentLearningTemplate(
            title = "3D Earth and atmosphere", category = "Geography", subjectTitle = "Geography",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Explore the rotating Earth. What causes day and night?",
            options = listOf("Earth's rotation", "Earth stopping in space", "The Moon turning off the Sun"), answer = "Earth's rotation",
            conceptKey = "3d:earth:day-and-night",
            solution = "Earth rotates on its axis, so a location faces toward the Sun during day and away from it during night."
        ),
        ParentLearningTemplate(
            title = "3D water cycle", category = "Environmental Science", subjectTitle = "Environmental Science",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Watch the animated water cycle. What process changes surface liquid water into vapour?",
            options = listOf("Evaporation", "Condensation", "Freezing"), answer = "Evaporation",
            conceptKey = "3d:water-cycle:evaporation",
            solution = "Sunlight supplies energy for evaporation. Vapour can later cool and condense into droplets."
        ),
        ParentLearningTemplate(
            title = "3D electric circuit", category = "Computer Skills", subjectTitle = "Computer Skills",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Follow the animated circuit. What is needed for current to flow continuously in a simple circuit?",
            options = listOf("A closed conducting path and a power source", "A broken wire only", "A plastic path with no source"),
            answer = "A closed conducting path and a power source",
            conceptKey = "3d:circuit:closed-circuit",
            solution = "A closed conducting path and a source of electrical energy allow current to flow."
        ),
        ParentLearningTemplate(
            title = "3D planetary rings", category = "Astronomy", subjectTitle = "Astronomy",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Explore the orbital model. Which planet has the most conspicuous ring system?",
            options = listOf("Saturn", "Mercury", "Earth"), answer = "Saturn",
            conceptKey = "3d:solar-system:planetary-rings",
            solution = "Saturn's broad, bright ring system is prominent, though other giant planets also have rings."
        ),
        ParentLearningTemplate(
            title = "3D colour and shape composition", category = "Art & Design", subjectTitle = "Art & Design",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Rotate the 3D shapes. Which set contains traditional primary colours in basic paint instruction?",
            options = listOf("Red, yellow and blue", "Green, orange and purple", "Black, grey and white"), answer = "Red, yellow and blue",
            conceptKey = "3d:geometry:primary-colours",
            solution = "Basic colour instruction often uses red, yellow and blue as primary paint colours; models differ by medium."
        ),
        ParentLearningTemplate(
            title = "3D breathing and lungs", category = "Health & Safety", subjectTitle = "Health & Safety",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Explore the body model. Which organs exchange oxygen and carbon dioxide during breathing?",
            options = listOf("Lungs", "Stomach", "Kidneys"), answer = "Lungs",
            conceptKey = "3d:cell:respiratory-system",
            solution = "Gas exchange takes place in the alveoli of the lungs."
        )
    ) + listOf(
ParentLearningTemplate(
            title = "3D atom: locate the electron", category = "Chemistry", subjectTitle = "Chemistry",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Explore the 3D atom. Which negatively charged particle moves around the nucleus?",
            options = listOf("Electron", "Proton", "Neutron"), answer = "Electron",
            conceptKey = "3d:atom:atomic-structure",
            solution = "Electrons carry negative charge and occupy regions around an atom's nucleus."
        ),
        ParentLearningTemplate(
            title = "3D water molecule", category = "Chemistry", subjectTitle = "Chemistry",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Look at the 3D water molecule. Which elements make up water?",
            options = listOf("Hydrogen and oxygen", "Carbon and oxygen", "Sodium and chlorine"), answer = "Hydrogen and oxygen",
            conceptKey = "3d:molecule:water-molecule",
            solution = "A water molecule contains two hydrogen atoms bonded to one oxygen atom (H₂O)."
        ),
        ParentLearningTemplate(
            title = "3D geometric solids", category = "Mathematics", subjectTitle = "Mathematics",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Rotate the 3D solid. How many flat square faces does a cube have?",
            options = listOf("4", "6", "8"), answer = "6",
            conceptKey = "3d:geometry:cube-faces",
            solution = "A cube has six equal square faces, twelve edges and eight vertices."
        ),
        ParentLearningTemplate(
            title = "3D force and motion", category = "Physics", subjectTitle = "Physics",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Watch the moving ball and force arrows. What can an unbalanced force do to an object?",
            options = listOf("Change its motion", "Always remove its mass", "Stop all gravity"), answer = "Change its motion",
            conceptKey = "3d:force:unbalanced-force",
            solution = "A net force can change an object's speed, direction or both."
        ),
        ParentLearningTemplate(
            title = "3D animal cell", category = "Biology", subjectTitle = "Biology",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Explore the 3D animal cell. Which structure holds most of the cell's genetic material?",
            options = listOf("Nucleus", "Cell membrane", "Cytoplasm"), answer = "Nucleus",
            conceptKey = "3d:cell:nucleus",
            solution = "The nucleus stores most genetic material in a typical animal cell."
        ),
        ParentLearningTemplate(
            title = "3D Earth and atmosphere", category = "Geography", subjectTitle = "Geography",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Explore the rotating Earth. What causes day and night?",
            options = listOf("Earth's rotation", "Earth stopping in space", "The Moon turning off the Sun"), answer = "Earth's rotation",
            conceptKey = "3d:earth:day-and-night",
            solution = "Earth rotates on its axis, so a location faces toward the Sun during day and away from it during night."
        ),
        ParentLearningTemplate(
            title = "3D water cycle", category = "Environmental Science", subjectTitle = "Environmental Science",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Watch the animated water cycle. What process changes surface liquid water into vapour?",
            options = listOf("Evaporation", "Condensation", "Freezing"), answer = "Evaporation",
            conceptKey = "3d:water-cycle:evaporation",
            solution = "Sunlight supplies energy for evaporation. Vapour can later cool and condense into droplets."
        ),
        ParentLearningTemplate(
            title = "3D electric circuit", category = "Computer Skills", subjectTitle = "Computer Skills",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Follow the animated circuit. What is needed for current to flow continuously in a simple circuit?",
            options = listOf("A closed conducting path and a power source", "A broken wire only", "A plastic path with no source"),
            answer = "A closed conducting path and a power source",
            conceptKey = "3d:circuit:closed-circuit",
            solution = "A closed conducting path and a source of electrical energy allow current to flow."
        ),
        ParentLearningTemplate(
            title = "3D planetary rings", category = "Astronomy", subjectTitle = "Astronomy",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Explore the orbital model. Which planet has the most conspicuous ring system?",
            options = listOf("Saturn", "Mercury", "Earth"), answer = "Saturn",
            conceptKey = "3d:solar-system:planetary-rings",
            solution = "Saturn's broad, bright ring system is prominent, though other giant planets also have rings."
        ),
        ParentLearningTemplate(
            title = "3D colour and shape composition", category = "Art & Design", subjectTitle = "Art & Design",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Rotate the 3D shapes. Which set contains traditional primary colours in basic paint instruction?",
            options = listOf("Red, yellow and blue", "Green, orange and purple", "Black, grey and white"), answer = "Red, yellow and blue",
            conceptKey = "3d:geometry:primary-colours",
            solution = "Basic colour instruction often uses red, yellow and blue as primary paint colours; models differ by medium."
        ),
        ParentLearningTemplate(
            title = "3D breathing and lungs", category = "Health & Safety", subjectTitle = "Health & Safety",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Explore the body model. Which organs exchange oxygen and carbon dioxide during breathing?",
            options = listOf("Lungs", "Stomach", "Kidneys"), answer = "Lungs",
            conceptKey = "3d:cell:respiratory-system",
            solution = "Gas exchange takes place in the alveoli of the lungs."
        ),
        ParentLearningTemplate(
            title = "Colour mixing: red and yellow", category = "Drawing & Colour", subjectTitle = "Drawing & Colour",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "In basic paint mixing, what colour do red and yellow make?",
            options = listOf("Orange", "Green", "Blue"), answer = "Orange",
            conceptKey = "art:colour-mixing-orange",
            solution = "Mixing red and yellow paint commonly makes orange. Try small amounts first and observe the shade."
        ),
        ParentLearningTemplate(
            title = "Colour families", category = "Drawing & Colour", subjectTitle = "Drawing & Colour",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which set contains traditional primary paint colours?",
            options = listOf("Red, yellow and blue", "Orange, green and purple", "Black, grey and brown"), answer = "Red, yellow and blue",
            conceptKey = "art:primary-paint-colours",
            solution = "Traditional beginner paint lessons often use red, yellow and blue as primary colours."
        ),
        ParentLearningTemplate(
            title = "Shape sketch challenge", category = "Drawing & Colour", subjectTitle = "Drawing & Colour",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which shape has three straight sides?",
            options = listOf("Triangle", "Circle", "Oval"), answer = "Triangle",
            conceptKey = "art:triangle-sketch",
            solution = "A triangle has three sides. Draw one, then colour each side a different colour."
        ),
        ParentLearningTemplate(
            title = "Light and shade", category = "Drawing & Colour", subjectTitle = "Drawing & Colour",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "What helps a drawing of a sphere look three-dimensional?",
            options = listOf("Light and shadow", "Only one flat line", "Removing all contrast"), answer = "Light and shadow",
            conceptKey = "art:light-and-shadow",
            solution = "Highlights and shadows help suggest a rounded surface and the direction of light."
        ),
        ParentLearningTemplate(
            title = "Islamic studies: sacred text", category = "Religious Education", subjectTitle = "Islamic Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "In Islam, which book is regarded as the sacred scripture?",
            options = listOf("Qur'an", "Torah", "Tripitaka"), answer = "Qur'an",
            conceptKey = "faith:islamic:sacred-text",
            solution = "The Qur'an is the sacred scripture of Islam. Parents and teachers can adapt explanations to the child's age."
        ),
        ParentLearningTemplate(
            title = "Islamic studies: Ramadan", category = "Religious Education", subjectTitle = "Islamic Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which month is associated with fasting from dawn to sunset by many Muslims?",
            options = listOf("Ramadan", "Shawwal", "Muharram"), answer = "Ramadan",
            conceptKey = "faith:islamic:ramadan",
            solution = "Ramadan is the month in which Muslims commonly observe fasting from dawn to sunset."
        ),
        ParentLearningTemplate(
            title = "Hindu studies: Diwali", category = "Religious Education", subjectTitle = "Hindu Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which festival is widely known as the festival of lights in Hindu traditions?",
            options = listOf("Diwali", "Vaisakhi", "Vesak"), answer = "Diwali",
            conceptKey = "faith:hindu:diwali",
            solution = "Diwali is widely known as the festival of lights and is celebrated in different ways across communities."
        ),
        ParentLearningTemplate(
            title = "Hindu studies: ahimsa", category = "Religious Education", subjectTitle = "Hindu Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "What does the Sanskrit term ahimsa commonly mean?",
            options = listOf("Non-harm or non-violence", "A type of musical instrument", "A season"), answer = "Non-harm or non-violence",
            conceptKey = "faith:hindu:ahimsa",
            solution = "Ahimsa is commonly understood as non-harm or non-violence and is important in several Indian traditions."
        ),
        ParentLearningTemplate(
            title = "Christian studies: scripture", category = "Religious Education", subjectTitle = "Christian Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which book is the central scripture of Christianity?",
            options = listOf("The Bible", "The Vedas", "The Guru Granth Sahib"), answer = "The Bible",
            conceptKey = "faith:christian:scripture",
            solution = "The Bible is the central scripture of Christianity."
        ),
        ParentLearningTemplate(
            title = "Christian studies: Easter", category = "Religious Education", subjectTitle = "Christian Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "According to Christian belief, what does Easter commemorate?",
            options = listOf("The resurrection of Jesus", "The birth of the Buddha", "The start of Ramadan"), answer = "The resurrection of Jesus",
            conceptKey = "faith:christian:easter",
            solution = "Easter commemorates the resurrection of Jesus in Christian belief."
        ),
        ParentLearningTemplate(
            title = "Sikh studies: scripture", category = "Religious Education", subjectTitle = "Sikh Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which scripture is central in Sikhism?",
            options = listOf("Guru Granth Sahib", "The Bible", "The Tripitaka"), answer = "Guru Granth Sahib",
            conceptKey = "faith:sikh:scripture",
            solution = "The Guru Granth Sahib is the central scripture of Sikhism."
        ),
        ParentLearningTemplate(
            title = "Sikh studies: langar", category = "Religious Education", subjectTitle = "Sikh Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "What is langar in Sikh tradition?",
            options = listOf("A community meal served freely", "A type of planet", "A maths symbol"), answer = "A community meal served freely",
            conceptKey = "faith:sikh:langar",
            solution = "Langar is a community kitchen or meal offered freely, emphasising service and shared dignity."
        ),
        ParentLearningTemplate(
            title = "Buddhist studies: teacher", category = "Religious Education", subjectTitle = "Buddhist Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Whose teachings are foundational to Buddhism?",
            options = listOf("The Buddha", "Guru Nanak", "Confucius only"), answer = "The Buddha",
            conceptKey = "faith:buddhist:teacher",
            solution = "Buddhism is based on the teachings of the Buddha."
        ),
        ParentLearningTemplate(
            title = "Buddhist studies: practice", category = "Religious Education", subjectTitle = "Buddhist Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which practice is commonly used by many Buddhists to develop attention and awareness?",
            options = listOf("Meditation", "Orbit drawing", "Circuit soldering"), answer = "Meditation",
            conceptKey = "faith:buddhist:meditation",
            solution = "Meditation is practised in many Buddhist traditions, with methods varying by school."
        ),
        ParentLearningTemplate(
            title = "Jewish studies: Torah", category = "Religious Education", subjectTitle = "Jewish Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which text is a central part of the Jewish Torah?",
            options = listOf("The Five Books of Moses", "The Qur'an", "The Tripitaka"), answer = "The Five Books of Moses",
            conceptKey = "faith:jewish:torah",
            solution = "The Torah refers especially to the Five Books of Moses and is central to Judaism."
        ),
        ParentLearningTemplate(
            title = "Jewish studies: Shabbat", category = "Religious Education", subjectTitle = "Jewish Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "What is Shabbat in Jewish tradition?",
            options = listOf("A weekly day of rest", "A planet", "A colour-mixing technique"), answer = "A weekly day of rest",
            conceptKey = "faith:jewish:shabbat",
            solution = "Shabbat is the weekly Jewish day of rest and observance."
        ),
        ParentLearningTemplate(
            title = "Pakistan: Eid al-Fitr", category = "Religious Education", subjectTitle = "Islamic Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which Muslim festival marks the end of Ramadan?",
            options = listOf("Eid al-Fitr", "Vaisakhi", "Diwali"), answer = "Eid al-Fitr",
            conceptKey = "faith:islamic:pk-eid-al-fitr",
            solution = "Eid al-Fitr marks the end of Ramadan. This is a starter question; families should match it to the child's school syllabus.",
            countryCode = "PK"
        ),
        ParentLearningTemplate(
            title = "Pakistan: Zakat", category = "Religious Education", subjectTitle = "Islamic Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "In Islamic teaching, what is Zakat primarily associated with?",
            options = listOf("Giving a required form of charity", "A planet's orbit", "A colour wheel"), answer = "Giving a required form of charity",
            conceptKey = "faith:islamic:pk-zakat",
            solution = "Zakat is a form of obligatory charity in Islam. Teachers may explain age-appropriate details and eligibility.",
            countryCode = "PK"
        ),
        ParentLearningTemplate(
            title = "Pakistan: Diwali among Hindu communities", category = "Religious Education", subjectTitle = "Hindu Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which Hindu festival is widely known as the festival of lights?",
            options = listOf("Diwali", "Vesak", "Shabbat"), answer = "Diwali",
            conceptKey = "faith:hindu:pk-diwali",
            solution = "Diwali is widely known as the festival of lights and is observed by Hindu communities in Pakistan and elsewhere.",
            countryCode = "PK"
        ),
        ParentLearningTemplate(
            title = "India: Vaisakhi and Sikh tradition", category = "Religious Education", subjectTitle = "Sikh Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which festival is especially important in Sikh history and is also associated with the spring harvest in Punjab?",
            options = listOf("Vaisakhi", "Shabbat", "Eid al-Fitr"), answer = "Vaisakhi",
            conceptKey = "faith:sikh:in-vaisakhi",
            solution = "Vaisakhi has important significance in Sikh history and is also connected with the harvest in Punjab.",
            countryCode = "IN"
        ),
        ParentLearningTemplate(
            title = "India: Jain Paryushan", category = "Religious Education", subjectTitle = "Jain Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which principle is central to Jain teaching and is often expressed as non-violence?",
            options = listOf("Ahimsa", "Orbit", "Evaporation"), answer = "Ahimsa",
            conceptKey = "faith:jain:in-ahimsa",
            solution = "Ahimsa, or non-violence/non-harm, is central to Jain ethics. This item is a general starter, not an official syllabus question.",
            countryCode = "IN"
        ),
        ParentLearningTemplate(
            title = "India: Hindu Diwali", category = "Religious Education", subjectTitle = "Hindu Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which festival is often called the festival of lights in Hindu traditions?",
            options = listOf("Diwali", "Vaisakhi", "Ramadan"), answer = "Diwali",
            conceptKey = "faith:hindu:in-diwali",
            solution = "Diwali is widely known as the festival of lights; practices vary across communities.",
            countryCode = "IN"
        ),
        ParentLearningTemplate(
            title = "India: Eid al-Fitr", category = "Religious Education", subjectTitle = "Islamic Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which festival marks the end of Ramadan for Muslims?",
            options = listOf("Eid al-Fitr", "Diwali", "Vaisakhi"), answer = "Eid al-Fitr",
            conceptKey = "faith:islamic:in-eid-al-fitr",
            solution = "Eid al-Fitr marks the end of Ramadan and is celebrated by Muslim communities in India and around the world.",
            countryCode = "IN"
        ),
        ParentLearningTemplate(
            title = "Jain studies: ahimsa", category = "Religious Education", subjectTitle = "Jain Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "Which value is central in Jain teachings?",
            options = listOf("Ahimsa (non-violence)", "Conquering a planet", "Mixing colours"), answer = "Ahimsa (non-violence)",
            conceptKey = "faith:jain:ahimsa",
            solution = "Ahimsa, or non-violence and non-harm, is a central principle of Jainism."
        ),
        ParentLearningTemplate(
            title = "Jain studies: living beings", category = "Religious Education", subjectTitle = "Jain Studies",
            languageCode = "en", questionType = "multiple_choice",
            prompt = "What does Jain teaching encourage toward living beings?",
            options = listOf("Care and non-harm", "Waste and harm", "Ignoring all living things"), answer = "Care and non-harm",
            conceptKey = "faith:jain:care-for-life",
            solution = "Jain traditions strongly emphasise non-violence and careful treatment of living beings."
        )
    )
}
