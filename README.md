# Decide

A small, no-frills Android app for thinking through decisions with expected value.

For a decision you list your **options** (mutually exclusive: you pick exactly one). For each
option you add **outcomes**, and the app asks about each one. The questions follow Annie Duke's
*How to Decide*:

1. *What could happen?* Asked as a **premortem** ("imagine it went badly: what happened?") or a
   **backcast** ("imagine it went well: what happened?").
2. *Would that be good or bad for you, and how much would it affect your happiness a week, a month
   and a year later?* (the **Happiness Test**, 0–5 for each). This sets the outcome's score from
   -10 to +10. You can also set the score directly with a slider.
3. *How likely is it?* (a probability in %)

It then plots each option's outcome distribution as a probability histogram, works out the
**expected value** (EV), and ranks the options so you can see which has the highest EV and pick one.

| Compare options | One option | Adding an outcome |
|---|---|---|
| ![](docs/decision.png) | ![](docs/option.png) | ![](docs/outcome-dialog.png) |

## What it calculates

**Score from the Happiness Test.** With week, month and year ratings *w*, *m*, *y* (each 0–5),
each rating is weighted by the length of its period in days (7, 365/12 and 365), so a year counts
365/7 ≈ 52 times as much as a week:

size = (7·*w* + 30.4·*m* + 365·*y*) / 402.4 × 2, a number from 0 to 10.

Bad outcomes get a minus sign. So what matters is mostly the year rating. A disappointing meal
(1/0/0) scores -0.03, and a job you hate (5/4/2) scores -4.4.

For an option with outcomes *i* with score *sᵢ* and probability *pᵢ*:

- **Expected value**: Σ pᵢ·sᵢ
- **Upside / downside**: the part of the EV from good outcomes and the part from bad outcomes
  (they add up to the EV)
- **Chance of a bad outcome**: total probability of outcomes with a negative score
- **Worst / best case**
- **Spread**: standard deviation

**Probabilities.** As in the book, you only list the *reasonable* outcomes, so an option's
probabilities may add up to less than 100%. The unlisted remainder counts as neutral (score 0).
They can't add up to more than 100%, because the outcomes are mutually exclusive. The app won't
let you enter more than what's left.

**Notes the app adds** (from the book's chapter on deciding faster):

- **Close call**: when two or more options are within 1 point of the best EV. "When a decision is
  hard, that means it's easy": you can't be that wrong with any of them. A **Flip a coin** button
  picks one.
- **Freeroll**: when an option's worst outcome is no worse than -1 and it has something to gain.
  Decide fast, but beware of choices you'll repeat, where small costs add up.

Options are ranked by EV. The summary also points out the option with the lowest chance of a bad
outcome when that's a different one.

Data is stored locally on the phone (`decisions.json` in the app's private storage).

## Getting the APK

Every push runs the **Build APK** GitHub Actions workflow. Open the run under the repo's
*Actions* tab and download the `decide-apk` artifact, unzip it, and install `app-release.apk` on
your phone (you'll need to allow installing apps from unknown sources). It's signed with a debug
key, which is fine for personal use.

## Building locally

Requires JDK 17+ and the Android SDK (platform 35).

```sh
./gradlew testDebugUnitTest   # unit tests for the maths
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press Run.

## Code layout

- `model/Model.kt`: data classes and the `Analysis` maths (pure Kotlin, unit-tested)
- `model/DecisionStore.kt`: in-memory state + JSON persistence
- `ui/Screens.kt`: decision list, decision comparison, and option screens (Jetpack Compose)
- `ui/Histogram.kt`: the histogram, drawn on a Compose `Canvas`
- `ui/Dialogs.kt`: the question prompts

## Ideas for later

- Relax mutual exclusivity (combinations of options)
- Risk-aversion: utility curves instead of raw EV
- Probability ranges (best guess plus low/high, with the "shock test") and checking whether the ranking changes
- Your own word-to-percentage table ("likely" = 70%) for entering probabilities
- Base rates (the "outside view") next to your estimates
- Record what actually happened, to check how well-calibrated your estimates were
