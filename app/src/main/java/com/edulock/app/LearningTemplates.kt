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
    val safety: String = ""
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
    )
}
